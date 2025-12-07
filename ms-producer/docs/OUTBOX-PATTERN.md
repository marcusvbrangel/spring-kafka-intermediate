# Outbox Pattern - Documentação

## 📋 O que é o Outbox Pattern?

O **Outbox Pattern** é um padrão de design que garante consistência entre o banco de dados e sistemas de mensageria (como Kafka), resolvendo o problema do **dual-write**.

## 🎯 Problema Resolvido

### Dual-Write Problem

Quando você precisa:
1. Salvar dados no banco de dados
2. Publicar evento no Kafka

**Problema:** São duas operações independentes que podem falhar de formas diferentes:

```
❌ CENÁRIO 1: Banco OK, Kafka FALHA
- Payment salvo no banco
- Evento NÃO publicado no Kafka
- Resultado: Inconsistência! Consumer nunca saberá do payment

❌ CENÁRIO 2: Kafka OK, Banco FALHA
- Evento publicado no Kafka
- Payment NÃO salvo (rollback da transação)
- Resultado: Inconsistência! Evento órfão no Kafka

✅ SOLUÇÃO: OUTBOX PATTERN
- Payment + OutboxEvent salvos na MESMA transação
- Job assíncrono publica eventos do outbox no Kafka
- Resultado: Consistência garantida!
```

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                    HTTP REQUEST                             │
│                    POST /api/payments/approved              │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              PaymentWebController                           │
│              (Infrastructure - REST Adapter)                │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              ApprovePaymentService                          │
│              (Application - Use Case)                       │
│                                                             │
│  @Transactional                                             │
│  1. Create PaymentDomain                                    │
│  2. payment.markApproved()                                  │
│  3. paymentRepository.save(payment)      ◄─── DB            │
│  4. outboxService.saveEvent(event)       ◄─── DB            │
│                                                             │
│  ✅ AMBOS salvos na MESMA transação atomicamente!          │
└─────────────────────────────────────────────────────────────┘
                         │
                         │ (payment + outbox_event salvos)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   DATABASE (PostgreSQL)                     │
│                                                             │
│  ┌─────────────────┐    ┌──────────────────────┐           │
│  │  payment        │    │  outbox_event        │           │
│  ├─────────────────┤    ├──────────────────────┤           │
│  │ id              │    │ id                   │           │
│  │ user_id         │    │ aggregate_type       │           │
│  │ amount          │    │ aggregate_id         │           │
│  │ currency        │    │ event_type           │           │
│  │ status=APPROVED │    │ topic                │           │
│  │ created_at      │    │ partition_key        │           │
│  └─────────────────┘    │ payload (JSON)       │           │
│                         │ status=PENDING  ◄────┐           │
│                         │ retry_count=0        │           │
│                         │ created_at           │           │
│                         └──────────────────────┘           │
└─────────────────────────────────────────────────────────────┘
                                  │
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────┐
│              OutboxPublisher (Scheduled Job)                │
│              Runs every 5 seconds                           │
│                                                             │
│  @Scheduled(fixedDelay=5000)                                │
│  1. SELECT * FROM outbox_event                              │
│     WHERE status=PENDING                                    │
│     ORDER BY created_at ASC                                 │
│     LIMIT 100                                               │
│                                                             │
│  2. For each event:                                         │
│     - kafkaTemplate.send(event)                             │
│     - If success: UPDATE status=PUBLISHED                   │
│     - If error: INCREMENT retry_count                       │
│     - If retry_count > 3: UPDATE status=FAILED              │
└────────────────────────┬────────────────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                      KAFKA                                  │
│                                                             │
│  Topic: payment.approved.v1                                 │
│  ┌─────────────────────────────────────────┐               │
│  │ Partition 0                             │               │
│  │ ┌─────────────────────────────────────┐ │               │
│  │ │ PaymentApprovedEvent                │ │               │
│  │ │ - eventId                           │ │               │
│  │ │ - paymentId                         │ │               │
│  │ │ - userId (partition key)            │ │               │
│  │ │ - amount                            │ │               │
│  │ │ - currency                          │ │               │
│  │ │ - status                            │ │               │
│  │ └─────────────────────────────────────┘ │               │
│  └─────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
                  ms-consumer (outro serviço)
```

## 📁 Estrutura de Arquivos

```
src/main/
├── java/com/mvbr/store/
│   ├── application/service/
│   │   └── ApprovePaymentService.java      # Usa OutboxService
│   │
│   └── infrastructure/adapter/out/
│       └── outbox/
│           ├── OutboxEvent.java            # JPA Entity
│           ├── OutboxEventStatus.java      # Enum (PENDING, PUBLISHED, FAILED)
│           ├── OutboxEventRepository.java  # Spring Data JPA
│           ├── OutboxService.java          # Business logic
│           └── OutboxPublisher.java        # Scheduled job
│
└── resources/
    ├── application.yaml                    # Configurações outbox
    └── db/migration/
        └── V2__create_outbox_table.sql     # Migration Flyway
```

## 🔧 Configurações

### application.yaml

```yaml
# Outbox Pattern Configuration
outbox:
  publisher:
    fixed-delay: 5000      # Job roda a cada 5 segundos
    batch-size: 100        # Processa até 100 eventos por vez
    max-retries: 3         # Tenta até 3x antes de marcar como FAILED

# Spring Task Scheduling (required for @Scheduled)
spring.task:
  scheduling:
    pool:
      size: 2              # Número de threads para scheduled tasks
```

### Environment Variables

```bash
# Frequência do publisher (ms)
OUTBOX_PUBLISHER_FIXED_DELAY=5000

# Tamanho do batch
OUTBOX_PUBLISHER_BATCH_SIZE=100

# Máximo de retries
OUTBOX_PUBLISHER_MAX_RETRIES=3
```

## 📊 Schema do Banco de Dados

```sql
CREATE TABLE outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,           -- Ex: 'PAYMENT'
    aggregate_id VARCHAR(36) NOT NULL,             -- ID da entidade (payment_id)
    event_type VARCHAR(100) NOT NULL,              -- Ex: 'PAYMENT_APPROVED'
    topic VARCHAR(100) NOT NULL,                   -- Tópico Kafka
    partition_key VARCHAR(100) NOT NULL,           -- Chave de partição (userId)
    payload TEXT NOT NULL,                         -- JSON do evento
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    version INT NOT NULL DEFAULT 1
);

-- Índices
CREATE INDEX idx_outbox_status_created ON outbox_event(status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_event(aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_failed ON outbox_event(status, retry_count) WHERE status = 'FAILED';
```

## 🔄 Fluxo Completo

### 1. Request HTTP chega

```http
POST /api/payments/approved
Content-Type: application/json

{
  "paymentId": "pay-123",
  "userId": "user-456",
  "amount": 100.00,
  "currency": "BRL"
}
```

### 2. ApprovePaymentService executa

```java
@Transactional
public PaymentResponse approvePayment(ApprovePaymentCommand command) {
    // 1. Criar domain
    PaymentDomain payment = new PaymentDomain(...);
    payment.markApproved();

    // 2. Salvar payment
    PaymentDomain savedPayment = paymentRepository.save(payment);

    // 3. Criar evento
    PaymentApprovedEvent event = new PaymentApprovedEvent(...);

    // 4. Salvar no OUTBOX (mesma transação!)
    outboxService.saveEvent(
        "PAYMENT",                    // aggregateType
        savedPayment.getPaymentId(),  // aggregateId
        "PAYMENT_APPROVED",           // eventType
        "payment.approved.v1",        // topic
        savedPayment.getUserId(),     // partitionKey
        event                         // payload
    );

    return new PaymentResponse(...);
}
```

### 3. Banco de Dados após commit

```sql
-- Tabela payment
INSERT INTO payment VALUES ('pay-123', 'user-456', 100.00, 'BRL', 'APPROVED', ...);

-- Tabela outbox_event
INSERT INTO outbox_event VALUES (
    'event-789',
    'PAYMENT',
    'pay-123',
    'PAYMENT_APPROVED',
    'payment.approved.v1',
    'user-456',
    '{"eventId":"...","paymentId":"pay-123",...}',
    'PENDING',
    0,
    null,
    '2024-01-01 10:00:00',
    null,
    1
);
```

### 4. OutboxPublisher processa (5 segundos depois)

```java
@Scheduled(fixedDelay=5000)
public void publishPendingEvents() {
    // Buscar eventos PENDING
    List<OutboxEvent> events = repository.findByStatus(PENDING, 100);

    for (OutboxEvent event : events) {
        try {
            // Publicar no Kafka
            kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload());

            // Marcar como PUBLISHED
            event.markAsPublished();
            repository.save(event);

        } catch (Exception e) {
            // Incrementar retry
            event.incrementRetryCount();

            if (event.getRetryCount() > maxRetries) {
                event.markAsFailed(e.getMessage());
            }

            repository.save(event);
        }
    }
}
```

### 5. Evento publicado no Kafka

```json
// Topic: payment.approved.v1
// Partition Key: user-456
// Headers:
//   - event-type: PAYMENT_APPROVED
//   - event-id: event-789
//   - aggregate-id: pay-123
//   - source: outbox-publisher

{
  "eventId": "event-789",
  "paymentId": "pay-123",
  "userId": "user-456",
  "amount": 100.00,
  "currency": "BRL",
  "status": "APPROVED",
  "timestamp": 1704106800000
}
```

### 6. Outbox atualizado

```sql
UPDATE outbox_event
SET status = 'PUBLISHED',
    published_at = '2024-01-01 10:00:05'
WHERE id = 'event-789';
```

## ⚡ Vantagens

### 1. Atomicidade Garantida

✅ **Payment e OutboxEvent salvos juntos**
- Se transaction commit → ambos salvos
- Se transaction rollback → nada salvo
- Impossível ter payment sem evento ou evento sem payment

### 2. Consistência Eventual

✅ **Evento será publicado (eventually)**
- Job assíncrono processa eventos PENDING
- Retry automático em caso de falha
- At-least-once delivery garantido

### 3. Resiliência

✅ **Sistema tolerante a falhas**
- Kafka offline? Eventos ficam no outbox
- Kafka volta? Publisher retoma publicação
- Não perde eventos

### 4. Observabilidade

✅ **Rastreabilidade completa**
- Todos os eventos registrados no banco
- Status de cada evento (PENDING, PUBLISHED, FAILED)
- Retry count e error messages
- Audit trail completo

### 5. Performance

✅ **Request HTTP retorna rápido**
- Não espera publicação Kafka
- Apenas salva no banco (rápido)
- Publicação assíncrona em background

## 🎯 Garantias

| Garantia | Explicação |
|----------|------------|
| **Atomicidade** | Payment + OutboxEvent salvos na mesma transação |
| **Consistência** | Se payment existe → evento será publicado |
| **Durabilidade** | Eventos persistidos no banco (não se perdem) |
| **At-least-once** | Evento pode ser publicado mais de 1x (idempotência no consumer!) |
| **Ordering** | Por userId (partition key) |

## ⚠️ Considerações

### 1. Não é "At-most-once"

❗ **Evento PODE ser publicado múltiplas vezes**
- Job pode falhar APÓS publicar mas ANTES de marcar como PUBLISHED
- Consumer DEVE implementar idempotência!

### 2. Latência

❗ **Não é tempo real**
- Delay de 5 segundos (configurável)
- Para real-time, ajustar `fixed-delay` para 100-500ms

### 3. Limpeza de Eventos

❗ **Tabela outbox cresce indefinidamente**
- Implementar job de cleanup de eventos PUBLISHED antigos
- Exemplo: deletar eventos > 7 dias

```java
@Scheduled(cron = "0 0 2 * * *") // Todo dia às 2am
public void cleanupOldEvents() {
    Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
    repository.deleteByStatusAndPublishedAtBefore(PUBLISHED, sevenDaysAgo);
}
```

### 4. Eventos FAILED

❗ **Monitoramento necessário**
- Configurar alerta se `count(status=FAILED) > threshold`
- Investigar causa raiz
- Reprocessar manualmente se necessário

## 📈 Monitoramento

### Métricas Importantes

```java
// Quantidade de eventos pendentes (deve ser baixo)
long pending = outboxService.countByStatus(PENDING);

// Quantidade de eventos falhados (deve ser zero)
long failed = outboxService.countByStatus(FAILED);

// Alertar se:
// - pending > 1000 (backlog crescente)
// - failed > 0 (problema crítico)
```

### Queries Úteis

```sql
-- Eventos pendentes há mais de 1 minuto (possível problema)
SELECT * FROM outbox_event
WHERE status = 'PENDING'
AND created_at < NOW() - INTERVAL '1 minute'
ORDER BY created_at DESC;

-- Eventos falhados (requerem atenção)
SELECT * FROM outbox_event
WHERE status = 'FAILED'
ORDER BY created_at DESC;

-- Estatísticas por status
SELECT status, COUNT(*) as count
FROM outbox_event
GROUP BY status;
```

## 🚀 Como Testar

### 1. Subir infraestrutura

```bash
docker compose up -d
```

### 2. Criar payment

```bash
curl -X POST http://localhost:5050/api/payments/approved \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "pay-123",
    "userId": "user-456",
    "amount": 100.00,
    "currency": "BRL"
  }'
```

### 3. Verificar banco

```sql
-- Payment salvo
SELECT * FROM payment WHERE payment_id = 'pay-123';

-- Evento no outbox (PENDING)
SELECT * FROM outbox_event WHERE aggregate_id = 'pay-123';
```

### 4. Aguardar 5 segundos

```sql
-- Evento agora PUBLISHED
SELECT * FROM outbox_event WHERE aggregate_id = 'pay-123';
-- status deve ser 'PUBLISHED'
-- published_at deve estar preenchido
```

### 5. Verificar Kafka

```bash
# Redpanda Console: http://localhost:8089
# Ou via kafka-console-consumer:
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.approved.v1 \
  --from-beginning
```

## 📚 Referências

- [Outbox Pattern - Microservices.io](https://microservices.io/patterns/data/transactional-outbox.html)
- [Implementing the Outbox Pattern - DZone](https://dzone.com/articles/implementing-the-outbox-pattern)
- [Transactional Outbox - Chris Richardson](https://chrisrichardson.net/post/microservices/patterns/2020/06/08/why-eventuate-local.html)

## ✅ Conclusão

O **Outbox Pattern** implementado neste projeto garante:

✅ Consistência entre banco de dados e Kafka
✅ At-least-once delivery
✅ Resiliência a falhas
✅ Rastreabilidade completa
✅ Performance (request HTTP rápido)

**Pronto para produção!** 🚀
