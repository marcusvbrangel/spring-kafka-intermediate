# Outbox Pattern - Implementação Completa

## Visão Geral

O **Outbox Pattern** resolve o problema de **dual-write** (escrita dupla) ao garantir consistência entre banco de dados e mensageria (Kafka).

### Problema que resolve

Quando você precisa:
1. Salvar uma entidade no banco de dados (ex: Payment)
2. Publicar um evento no Kafka (ex: PaymentApprovedEvent)

**Sem Outbox Pattern:**
- Se salvar no DB e falhar ao publicar no Kafka → Inconsistência
- Se publicar no Kafka e falhar ao salvar no DB → Inconsistência
- Não há transação distribuída entre DB e Kafka

**Com Outbox Pattern:**
- Tudo acontece em uma única transação ACID do banco de dados
- Evento é publicado de forma assíncrona e garantida

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                   TRANSAÇÃO ATÔMICA                          │
│                                                               │
│  1. Salva Payment                                            │
│  2. Salva OutboxEvent (mesma transação)                      │
│                                                               │
│  → Se falhar, ambos fazem rollback                           │
│  → Se suceder, ambos são commitados                          │
└─────────────────────────────────────────────────────────────┘
                           ↓
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              JOB ASSÍNCRONO (OutboxPublisher)                │
│                                                               │
│  1. Busca eventos PENDING (a cada 5 segundos)               │
│  2. Publica no Kafka                                         │
│  3. Marca como PUBLISHED                                     │
│                                                               │
│  → Retry automático em caso de falha                         │
│  → At-least-once delivery garantido                          │
└─────────────────────────────────────────────────────────────┘
```

## Componentes

### 1. OutboxEvent (Entidade JPA)

**Localização:** `infrastructure/adapter/out/outbox/OutboxEvent.java`

Tabela que armazena eventos pendentes:

```java
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    private String id;                // UUID único
    private String aggregateType;      // PAYMENT, ORDER, etc
    private String aggregateId;        // ID da entidade (paymentId)
    private String eventType;          // PAYMENT_APPROVED, etc
    private String topic;              // Tópico Kafka
    private String partitionKey;       // Chave de particionamento
    private String payload;            // JSON do evento
    private OutboxEventStatus status;  // PENDING, PUBLISHED, FAILED
    private Integer retryCount;        // Contador de tentativas
    private String errorMessage;       // Mensagem de erro
    private Instant createdAt;         // Data de criação
    private Instant publishedAt;       // Data de publicação
    private Integer version;           // Versão do schema
}
```

**Estados:**
- `PENDING`: Aguardando publicação
- `PUBLISHED`: Publicado com sucesso
- `FAILED`: Falhou após múltiplas tentativas

### 2. OutboxService

**Localização:** `infrastructure/adapter/out/outbox/OutboxService.java`

Responsável por salvar eventos no outbox:

```java
@Service
public class OutboxService {

    @Transactional
    public OutboxEvent saveEvent(
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String partitionKey,
        Object eventPayload
    ) {
        // Serializa payload para JSON
        String payloadJson = objectMapper.writeValueAsString(eventPayload);

        // Cria e salva OutboxEvent
        OutboxEvent event = new OutboxEvent(
            aggregateType, aggregateId, eventType,
            topic, partitionKey, payloadJson
        );

        return outboxRepository.save(event);
    }
}
```

### 3. OutboxPublisher (Scheduled Job)

**Localização:** `infrastructure/adapter/out/outbox/OutboxPublisher.java`

Job assíncrono que publica eventos no Kafka:

```java
@Component
public class OutboxPublisher {

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:5000}")
    public void publishPendingEvents() {
        // 1. Busca eventos PENDING (batch)
        List<OutboxEvent> pending = outboxRepository
            .findByStatusOrderByCreatedAtAsc(PENDING, batchSize);

        // 2. Publica cada evento no Kafka
        for (OutboxEvent event : pending) {
            publishEvent(event);
        }
    }

    @Transactional
    protected void publishEvent(OutboxEvent event) {
        // Cria ProducerRecord com headers
        ProducerRecord<String, Object> record = new ProducerRecord<>(
            event.getTopic(),
            event.getPartitionKey(),
            deserializePayload(event.getPayload())
        );

        // Adiciona headers
        record.headers().add("event-type", event.getEventType());
        record.headers().add("event-id", event.getId());

        // Publica no Kafka
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                handlePublishError(event, ex);
            } else {
                outboxService.markAsPublished(event.getId());
            }
        });
    }
}
```

### 4. OutboxEventRepository

**Localização:** `infrastructure/adapter/out/outbox/OutboxEventRepository.java`

Repository com queries otimizadas:

```java
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    // Busca eventos PENDING com limit (usado pelo job)
    @Query("SELECT e FROM OutboxEvent e " +
           "WHERE e.status = :status " +
           "ORDER BY e.createdAt ASC " +
           "LIMIT :limit")
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
        OutboxEventStatus status, int limit
    );

    // Conta eventos por status (métricas)
    long countByStatus(OutboxEventStatus status);

    // Busca eventos antigos para cleanup
    List<OutboxEvent> findByStatusAndPublishedAtBefore(
        OutboxEventStatus status, Instant publishedBefore
    );
}
```

### 5. Migration SQL

**Localização:** `resources/db/migration/V2__create_outbox_table.sql`

```sql
CREATE TABLE outbox_event (
    id VARCHAR(36) PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    version INT NOT NULL DEFAULT 1
);

-- Índice para buscar eventos pendentes (crítico para performance)
CREATE INDEX idx_outbox_status_created
ON outbox_event(status, created_at);

-- Índice para buscar eventos por agregado (debugging)
CREATE INDEX idx_outbox_aggregate
ON outbox_event(aggregate_type, aggregate_id);

-- Índice para eventos falhados (monitoring)
CREATE INDEX idx_outbox_failed
ON outbox_event(status, retry_count)
WHERE status = 'FAILED';
```

## Uso no Código

### Exemplo: ApprovePaymentService

```java
@Service
public class ApprovePaymentService implements ApprovePaymentUseCase {

    private final PaymentRepositoryPort paymentRepository;
    private final OutboxService outboxService;

    @Value("${kafka.topics.payment-approved}")
    private String paymentApprovedTopic;

    @Transactional  // ← CRÍTICO: garante atomicidade
    @Override
    public PaymentResponse approvePayment(ApprovePaymentCommand command) {

        // 1. Criar e validar domínio
        PaymentDomain payment = new PaymentDomain(
            command.paymentId(),
            command.userId(),
            command.amount(),
            command.currency()
        );

        // 2. Lógica de negócio
        payment.markApproved();

        // 3. Salvar no banco
        PaymentDomain saved = paymentRepository.save(payment);

        // 4. Criar evento
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            UUID.randomUUID().toString(),
            saved.getPaymentId(),
            saved.getUserId(),
            saved.getAmount(),
            saved.getCurrency(),
            saved.getStatus().name(),
            Instant.now().toEpochMilli()
        );

        // 5. Salvar no OUTBOX (mesma transação!)
        outboxService.saveEvent(
            "PAYMENT",                    // aggregateType
            saved.getPaymentId(),         // aggregateId
            "PAYMENT_APPROVED",           // eventType
            paymentApprovedTopic,         // topic
            saved.getUserId(),            // partitionKey
            event                         // payload
        );

        // 6. Retornar resposta
        return new PaymentResponse(saved);
    }
}
```

## Configuração

### application.yaml

```yaml
# Outbox Pattern Configuration
outbox:
  publisher:
    # Frequência do job (ms)
    fixed-delay: 5000

    # Batch size (eventos por execução)
    batch-size: 100

    # Max retries antes de marcar FAILED
    max-retries: 3

# Spring Task Scheduling
spring.task:
  scheduling:
    pool:
      size: 2  # Threads para scheduled tasks
```

### @EnableScheduling

**ProducerApplication.java:**

```java
@SpringBootApplication
@EnableScheduling  // ← NECESSÁRIO para @Scheduled funcionar
public class ProducerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProducerApplication.class, args);
    }
}
```

## Características

### Garantias

✅ **Atomicidade**: Payment + OutboxEvent salvos na mesma transação
✅ **Consistência**: Se transação falhar, nada é salvo
✅ **At-least-once delivery**: Evento será publicado (eventualmente)
✅ **Ordering**: Eventos processados em ordem de criação (FIFO)
✅ **Retry automático**: Até 3 tentativas por padrão
✅ **Rastreabilidade**: Histórico completo de eventos

### Limitações

⚠️ **Eventual consistency**: Evento não é publicado imediatamente (5s de delay)
⚠️ **At-least-once (não exactly-once)**: Evento pode ser publicado mais de 1x
⚠️ **Cleanup necessário**: Eventos PUBLISHED antigos precisam ser removidos

## Monitoramento

### Métricas importantes

```java
// Total de eventos pendentes
long pending = outboxPublisher.getPendingEventsCount();

// Total de eventos falhados (alerta!)
long failed = outboxPublisher.getFailedEventsCount();

// Buscar eventos falhados
List<OutboxEvent> failedEvents = outboxRepository
    .findByStatus(OutboxEventStatus.FAILED);
```

### Logs

```
INFO  OutboxService - Outbox event saved: id=xxx, type=PAYMENT_APPROVED
INFO  OutboxPublisher - Found 5 pending outbox events to publish
INFO  OutboxPublisher - Event published to Kafka: id=xxx, partition=2, offset=123
WARN  OutboxPublisher - Outbox event publish error, will retry: id=xxx, retryCount=1/3
ERROR OutboxPublisher - Outbox event FAILED after 3 retries: id=xxx
```

## Troubleshooting

### Eventos ficam PENDING para sempre

**Causas:**
- `@EnableScheduling` não configurado
- Job não está rodando
- Kafka está fora do ar

**Solução:**
- Verificar logs do OutboxPublisher
- Verificar conectividade com Kafka
- Aumentar `fixed-delay` se necessário

### Muitos eventos FAILED

**Causas:**
- Kafka indisponível
- Payload inválido (serialização falhou)
- Tópico não existe

**Solução:**
- Verificar erro em `error_message` no banco
- Verificar configuração do Kafka
- Reprocessar manualmente se necessário

### Performance ruim

**Causas:**
- `batch-size` muito pequeno
- `fixed-delay` muito longo
- Índices faltando

**Solução:**
- Aumentar `batch-size` (ex: 500)
- Diminuir `fixed-delay` (ex: 1000ms)
- Verificar índices no PostgreSQL

## Próximas Melhorias

### 1. Cleanup Job

Remover eventos PUBLISHED antigos:

```java
@Scheduled(cron = "0 0 2 * * *")  // 2am daily
public void cleanupOldEvents() {
    Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
    List<OutboxEvent> old = outboxRepository
        .findByStatusAndPublishedAtBefore(PUBLISHED, cutoff);
    outboxRepository.deleteAll(old);
}
```

### 2. Dead Letter Queue (DLQ)

Eventos FAILED devem ir para DLQ:

```java
if (event.isFailed()) {
    kafkaTemplate.send("outbox.dlq", event.getPayload());
}
```

### 3. Metrics & Alerting

Integrar com Prometheus/Grafana:

```java
@Gauge(name = "outbox.pending.events")
public long pendingEventsMetric() {
    return outboxService.countByStatus(PENDING);
}

@Counter(name = "outbox.failed.events")
public void incrementFailedCounter() {
    // Incrementa quando evento falha
}
```

## Referências

- [Outbox Pattern - Microservices.io](https://microservices.io/patterns/data/transactional-outbox.html)
- [Implementing the Outbox Pattern - Debezium](https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/)
- [Spring Scheduling Reference](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
