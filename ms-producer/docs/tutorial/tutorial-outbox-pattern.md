# Tutorial Prático: Outbox Pattern em Produção - O Guia Definitivo

## 📋 Sumário

1. [O que é e Para Que Serve](#1-o-que-é-e-para-que-serve)
2. [O Problema do Dual-Write](#2-o-problema-do-dual-write)
3. [Arquitetura do Outbox Pattern](#3-arquitetura-do-outbox-pattern)
4. [Implementação Passo a Passo](#4-implementação-passo-a-passo)
5. [Código Completo Comentado](#5-código-completo-comentado)
6. [Configuração e Deploy](#6-configuração-e-deploy)
7. [Testes na Prática](#7-testes-na-prática)
8. [Troubleshooting e Monitoramento](#8-troubleshooting-e-monitoramento)
9. [Padrões Avançados](#9-padrões-avançados)
10. [Checklist de Implementação](#10-checklist-de-implementação)

---

## 1. O que É e Para Que Serve

### Definição em 30 Segundos

**Outbox Pattern** resolve o problema de **consistência entre banco de dados e mensageria** (Kafka/RabbitMQ).

```
PROBLEMA:
  Salvar no DB + Publicar no Kafka = 2 operações separadas
  Se uma falhar → INCONSISTÊNCIA!

SOLUÇÃO OUTBOX:
  1. Salvar AMBOS (entidade + evento) na MESMA transação DB
  2. Job assíncrono publica eventos do banco para Kafka
  3. Marcar como publicado

  ✅ Atomicidade garantida
  ✅ At-least-once delivery
  ✅ Resiliência a falhas
```

### Diagrama Visual do Problema

```
❌ SEM OUTBOX PATTERN (Dual-Write Problem)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Transactional
public void approvePayment(Payment payment) {
    
    // 1. Salvar no banco
    paymentRepo.save(payment);  ✅ COMMIT!
    
    // 2. Publicar no Kafka
    kafkaTemplate.send(event);  ❌ FALHOU!
    
    // RESULTADO: Payment no banco, SEM evento no Kafka
    // INCONSISTÊNCIA! 💥
}

PROBLEMAS:
├─ DB OK, Kafka FAIL → Evento perdido
├─ DB FAIL, Kafka OK → Evento órfão
├─ Kafka indisponível → Aplicação quebra
└─ Sem retry automático


✅ COM OUTBOX PATTERN
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Transactional  // ← UMA transação para AMBOS!
public void approvePayment(Payment payment) {
    
    // 1. Salvar payment
    paymentRepo.save(payment);
    
    // 2. Salvar evento na tabela OUTBOX (mesma transação!)
    outboxRepo.save(new OutboxEvent(...));
    
    // Se QUALQUER um falhar → ROLLBACK de AMBOS!
    // Se AMBOS sucederem → COMMIT de AMBOS!
}

// Job separado (a cada 5s)
@Scheduled(fixedDelay = 5000)
public void publishPendingEvents() {
    // Buscar eventos PENDING
    List<OutboxEvent> events = outboxRepo.findPending();
    
    // Publicar cada um no Kafka
    events.forEach(event -> {
        kafkaTemplate.send(event);
        event.markAsPublished();
    });
}

BENEFÍCIOS:
├─ ✅ Atomicidade ACID (DB)
├─ ✅ Eventual consistency (Kafka)
├─ ✅ Retry automático (job)
├─ ✅ Histórico completo
└─ ✅ Recuperação de desastres
```

### Por Que Usar em Produção?

| Cenário | Sem Outbox | Com Outbox |
|---------|-----------|------------|
| **Kafka está down** | ❌ Aplicação quebra | ✅ Continua funcionando (eventos em pending) |
| **DB salva, Kafka falha** | ❌ Dado sem evento (inconsistente) | ✅ Retry automático (5s depois) |
| **DB falha, Kafka OK** | ❌ Evento órfão no Kafka | ✅ Rollback atômico (nada salvo) |
| **Precisa reprocessar** | ❌ Impossível (evento perdido) | ✅ Histórico completo na tabela outbox |
| **Auditoria** | ❌ Sem rastreamento | ✅ Todos eventos registrados |
| **Duplicação** | ❌ Sem controle | ✅ Idempotência via event_id |

### Casos de Uso Reais

#### 1. E-commerce - Pagamento Aprovado

```
Fluxo SEM Outbox:
  Payment approved → Salvar no DB → Enviar email → Atualizar estoque
                                          ↓
                                    Se email falhar?
                                    Pagamento OK, email não enviado!

Fluxo COM Outbox:
  Payment approved → [DB: Payment + OutboxEvent] ATOMIC
                           ↓
                     Job publica evento
                           ↓
                     Email Service consome
                     Inventory Service consome
                     
  ✅ Se email service estiver down, evento fica PENDING
  ✅ Quando voltar, processa automaticamente
```

#### 2. Banking - Transferência

```
Transferência de R$ 1000,00:
  
  SEM Outbox:
    1. Debitar conta origem    ✅
    2. Creditar conta destino  ✅
    3. Enviar notificação      ❌ FALHOU!
    4. Registrar auditoria     ❌ NUNCA EXECUTOU!
    
    RESULTADO: Dinheiro transferido, mas sem notificação e sem audit log!

  COM Outbox:
    @Transactional {
      1. Debitar conta origem
      2. Creditar conta destino
      3. Salvar OutboxEvent "TRANSFER_COMPLETED"
      4. Salvar OutboxEvent "AUDIT_REQUIRED"
    } // COMMIT atômico
    
    Job publica eventos → Consumidores processam de forma assíncrona
    
    ✅ Tudo ou nada (atomicidade)
    ✅ Eventos garantidos (at-least-once)
```

#### 3. SaaS - Criação de Usuário

```
Novo usuário se registra:

SEM Outbox:
  save(user) → sendWelcomeEmail() → provisionResources() → trackAnalytics()
                       ↓                      ↓                    ↓
                    Se falhar?            Se falhar?          Se falhar?
                    
COM Outbox:
  @Transactional {
    save(user)
    save(OutboxEvent "USER_REGISTERED")
  }
  
  Consumidores:
    - Email Service → Envia boas-vindas
    - Provisioning Service → Cria workspace
    - Analytics Service → Registra métrica
    - CRM Service → Adiciona ao funil
    
  ✅ Cada serviço processa no seu próprio ritmo
  ✅ Falhas individuais não afetam outros
  ✅ Retry automático por serviço
```

---

## 2. O Problema do Dual-Write

### O que é Dual-Write?

**Dual-write** ocorre quando você precisa escrever em **dois sistemas diferentes** que **não compartilham uma transação**.

```
┌──────────────┐         ┌──────────────┐
│  PostgreSQL  │         │    Kafka     │
│    (ACID)    │         │ (No TX)      │
└──────────────┘         └──────────────┘
       ↑                        ↑
       │                        │
       └────────┬───────────────┘
                │
         SEM TRANSAÇÃO
         DISTRIBUÍDA!
```

### Exemplo Real: Aprovar Pagamento

```java
@Service
public class PaymentService {
    
    private final PaymentRepository paymentRepo;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * ❌ CÓDIGO PROBLEMÁTICO
     * 
     * Este código tem um BUG sutil mas CRÍTICO!
     */
    @Transactional
    public void approvePayment(String paymentId) {
        
        // 1. Buscar pagamento
        Payment payment = paymentRepo.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        
        // 2. Aprovar (lógica de negócio)
        payment.approve();  // status → APPROVED
        
        // 3. Salvar no banco
        paymentRepo.save(payment);
        
        // 4. Publicar evento no Kafka
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            payment.getId(),
            payment.getUserId(),
            payment.getAmount()
        );
        
        kafkaTemplate.send("payment.approved.v1", event);
        
        // ⚠️ O QUE PODE DAR ERRADO AQUI?
    }
}
```

### Cenários de Falha

#### ❌ Cenário 1: Kafka Falha Após DB Commit

```
Timeline:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

T1: [DB]   findById(paymentId)           ✅ SELECT
T2: [DB]   payment.approve()             ✅ UPDATE status
T3: [DB]   paymentRepo.save(payment)     ✅ COMMIT!
T4: [Kafka] kafkaTemplate.send(event)    ❌ TIMEOUT!
           └─> Kafka broker não responde
           └─> Timeout após 30s
           └─> Exception lançada

RESULTADO:
├─ Payment está APPROVED no banco       ✅
├─ Evento NÃO foi publicado no Kafka    ❌
├─ Consumidores nunca sabem do pagamento
├─ Email não é enviado
├─ Estoque não é atualizado
└─ SISTEMA INCONSISTENTE! 💥

IMPACTO:
├─ Cliente não recebe confirmação
├─ Produto não é enviado
├─ Suporte recebe reclamação
└─ Investigação manual necessária
```

#### ❌ Cenário 2: DB Falha, Kafka OK

```
Timeline:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

T1: [DB]   findById(paymentId)           ✅ SELECT
T2: [DB]   payment.approve()             ✅ UPDATE
T3: [DB]   paymentRepo.save(payment)     ❌ CONSTRAINT VIOLATION!
           └─> Unique constraint violated
           └─> ROLLBACK!
T4: [Kafka] kafkaTemplate.send(event)    ✅ PUBLISHED!
           └─> Kafka não sabe do rollback
           └─> Evento já foi enviado

RESULTADO:
├─ Payment NÃO está no banco            ❌
├─ Evento FOI publicado no Kafka        ✅ (Órfão!)
├─ Consumidores processam evento inválido
└─ INCONSISTÊNCIA REVERSA! 💥

IMPACTO:
├─ Email enviado para pagamento inexistente
├─ Estoque atualizado incorretamente
├─ Analytics registra venda fantasma
└─ Dados corrompidos em múltiplos serviços
```

#### ❌ Cenário 3: Kafka Indisponível

```
Timeline:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

T1: [Kafka] Broker 1 down  ⬇
T2: [Kafka] Broker 2 down  ⬇
T3: [Kafka] Broker 3 down  ⬇
    └─> Cluster indisponível!

T4: [API]   POST /api/payments/approve
            └─> approvePayment(paymentId)
                └─> paymentRepo.save()          ✅
                └─> kafkaTemplate.send()        ❌ NO BROKERS!
                    └─> Exception!
                    └─> @Transactional rollback?
                        ❌ NÃO! Commit já aconteceu!

RESULTADO:
├─ Payment APPROVED no banco           ✅
├─ Kafka indisponível                  ❌
├─ Exception para o usuário            ❌
└─ UX ruim + dados inconsistentes      💥

IMPACTO:
├─ Usuário vê erro (mas pagamento foi processado!)
├─ Retry do usuário → duplicate payment?
├─ Suporte não sabe se pagamento foi aprovado
└─ Confiança do cliente comprometida
```

### Por Que @Transactional Não Resolve?

```java
@Transactional  // ← Isso SÓ funciona para SQL!
public void approvePayment(String paymentId) {
    
    paymentRepo.save(payment);  // ← Dentro da transação
    
    kafkaTemplate.send(event);  // ← FORA da transação!
                                //   Kafka não suporta transações JPA!
}

EXPLICAÇÃO:
┌─────────────────────────────────────────────────┐
│ @Transactional (Spring)                         │
│                                                 │
│   BEGIN TRANSACTION                             │
│   ├─ SELECT ... FROM payment                    │
│   ├─ UPDATE payment SET status = 'APPROVED'     │
│   └─ COMMIT                                     │
│                                                 │
│   kafkaTemplate.send(...)  ← AQUI NÃO TEM TX!  │
│                                                 │
└─────────────────────────────────────────────────┘

POR QUE?
├─ PostgreSQL: suporta transações ACID
├─ Kafka: é um log distribuído (sem transações*)
├─ Spring @Transactional: apenas JDBC/JPA
└─ Não existe transação que abranja AMBOS!

*Kafka tem transações próprias, mas incompatíveis com JPA
```

### Soluções Possíveis (e Por Que Não Funcionam)

#### ❌ Solução 1: Try-Catch com Compensação

```java
@Transactional
public void approvePayment(String paymentId) {
    
    paymentRepo.save(payment);
    
    try {
        kafkaTemplate.send(event).get();  // Bloqueia até confirmar
    } catch (Exception ex) {
        // Tentar compensar?
        payment.cancel();
        paymentRepo.save(payment);
        throw ex;
    }
}

POR QUE NÃO FUNCIONA:
├─ .get() bloqueia thread (ruim para performance)
├─ Compensação pode falhar também (DB pode cair)
├─ Timeout longo (30s+) trava aplicação
├─ Race conditions (outro thread lê payment aprovado)
└─ Complexidade aumenta exponencialmente
```

#### ❌ Solução 2: Publicar Antes de Salvar

```java
@Transactional
public void approvePayment(String paymentId) {
    
    // 1. Publicar ANTES de salvar
    kafkaTemplate.send(event);  // ← Primeiro
    
    // 2. Salvar no banco
    paymentRepo.save(payment);  // ← Depois
}

POR QUE NÃO FUNCIONA:
├─ Se DB falhar: evento órfão no Kafka
├─ Consumidores processam payment inexistente
├─ PIOR que o problema original!
└─ NUNCA faça isso!
```

#### ❌ Solução 3: Transações Distribuídas (2PC)

```java
// Two-Phase Commit (2PC)
@Transactional
@XAResource  // ← Requer XA transactions
public void approvePayment(String paymentId) {
    
    // Fase 1: PREPARE
    paymentRepo.save(payment);  // DB: PREPARE
    kafkaTemplate.send(event);  // Kafka: PREPARE
    
    // Fase 2: COMMIT
    // Coordenador commit ambos ou rollback ambos
}

POR QUE NÃO FUNCIONA:
├─ Kafka não suporta XA transactions
├─ Performance horrível (múltiplos round-trips)
├─ Complexidade altíssima
├─ Single point of failure (coordenador)
└─ EVITE em sistemas distribuídos modernos!
```

### ✅ A Solução Correta: Outbox Pattern

```java
@Service
public class PaymentService {
    
    private final PaymentRepository paymentRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;
    
    /**
     * ✅ SOLUÇÃO CORRETA com Outbox Pattern
     */
    @Transactional  // ← UMA transação para AMBOS!
    public void approvePayment(String paymentId) {
        
        // 1. Buscar payment
        Payment payment = paymentRepo.findById(paymentId)
            .orElseThrow();
        
        // 2. Aprovar (lógica de negócio)
        payment.approve();
        
        // 3. Salvar payment
        paymentRepo.save(payment);
        
        // 4. Criar evento
        PaymentApprovedEvent event = new PaymentApprovedEvent(...);
        String payloadJson = objectMapper.writeValueAsString(event);
        
        // 5. Salvar evento na OUTBOX (mesma transação!)
        OutboxEvent outboxEvent = new OutboxEvent(
            "PAYMENT",                   // aggregateType
            payment.getId(),             // aggregateId
            "PAYMENT_APPROVED",          // eventType
            "payment.approved.v1",       // topic
            payment.getUserId(),         // partitionKey
            payloadJson                  // payload
        );
        outboxRepo.save(outboxEvent);
        
        // ✅ COMMIT atômico de payment + outboxEvent
        // ✅ Se QUALQUER um falhar → rollback de AMBOS
        // ✅ Se AMBOS sucederem → AMBOS commitados
    }
}

// Job separado publica eventos da outbox
@Component
public class OutboxPublisher {
    
    @Scheduled(fixedDelay = 5000)  // A cada 5 segundos
    public void publishPendingEvents() {
        
        // 1. Buscar eventos PENDING
        List<OutboxEvent> pending = outboxRepo
            .findByStatusOrderByCreatedAtAsc(PENDING, 100);
        
        // 2. Publicar cada um
        for (OutboxEvent event : pending) {
            try {
                // Publicar no Kafka
                kafkaTemplate.send(
                    event.getTopic(),
                    event.getPartitionKey(),
                    event.getPayload()
                );
                
                // Marcar como PUBLISHED
                event.markAsPublished();
                outboxRepo.save(event);
                
            } catch (Exception ex) {
                // Incrementar retry
                event.recordError(ex.getMessage());
                outboxRepo.save(event);
                
                // Se retry_count >= 3 → marcar FAILED
                if (event.getRetryCount() >= 3) {
                    event.markAsFailed(ex.getMessage());
                    outboxRepo.save(event);
                }
            }
        }
    }
}

COMO FUNCIONA:
┌──────────────────────────────────────────────────┐
│ Fase 1: Escrita Transacional                    │
├──────────────────────────────────────────────────┤
│  BEGIN TRANSACTION                               │
│   INSERT INTO payment (...)                      │
│   INSERT INTO outbox_event (...)                 │
│  COMMIT                                          │
│                                                  │
│  ✅ Ambos salvos atomicamente!                   │
└──────────────────────────────────────────────────┘
                    ↓
           (5 segundos depois)
                    ↓
┌──────────────────────────────────────────────────┐
│ Fase 2: Publicação Assíncrona                   │
├──────────────────────────────────────────────────┤
│  SELECT * FROM outbox_event WHERE status=PENDING│
│  FOR EACH event:                                 │
│    kafkaTemplate.send(event.payload)             │
│    UPDATE outbox_event SET status=PUBLISHED      │
│                                                  │
│  ✅ At-least-once delivery!                      │
└──────────────────────────────────────────────────┘

BENEFÍCIOS:
├─ ✅ Atomicidade: payment + event SEMPRE consistentes
├─ ✅ Resiliência: Kafka down? Evento fica PENDING
├─ ✅ Retry automático: job tenta novamente a cada 5s
├─ ✅ Histórico: todos eventos registrados
├─ ✅ Auditoria: rastreamento completo
└─ ✅ Recuperação: pode reprocessar eventos antigos
```

---

## 3. Arquitetura do Outbox Pattern

### Visão Geral - Fluxo Completo

```
FASE 1: REQUEST & TRANSACTION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   [CLIENT]
      │
      │ POST /api/payments/approve
      │ { "paymentId": "pay-123", ...}
      │
      ↓
┌─────────────────────────────────────────────────┐
│ [CONTROLLER] PaymentController                  │
│  - Recebe PaymentRequestDto                     │
│  - Converte para ApprovePaymentCommand          │
│  - Chama Use Case                               │
└───────────────────┬─────────────────────────────┘
                    │
                    ↓
┌─────────────────────────────────────────────────┐
│ [USE CASE] ApprovePaymentService                │
│                                                 │
│  @Transactional  ← CRITICAL!                    │
│  public PaymentResponse approve(...) {          │
│    1. payment = new Payment(...)                │
│    2. payment.approve()                         │
│    3. paymentRepo.save(payment)      ───┐       │
│    4. outboxService.saveEvent(...)   ───┼─┐     │
│    return response;                     │ │     │
│  }                                      │ │     │
└─────────────────────────────────────────┼─┼─────┘
                                          │ │
                                          ↓ ↓
┌─────────────────────────────────────────────────┐
│ [DATABASE] PostgreSQL                           │
│                                                 │
│  ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓   │
│  ┃ BEGIN TRANSACTION;                      ┃   │
│  ┃                                         ┃   │
│  ┃ INSERT INTO payment VALUES (           ┃   │
│  ┃   'pay-123',                            ┃   │
│  ┃   'user-456',                           ┃   │
│  ┃   100.00,                               ┃   │
│  ┃   'APPROVED'                            ┃   │
│  ┃ );                                      ┃   │
│  ┃                                         ┃   │
│  ┃ INSERT INTO outbox_event VALUES (      ┃   │
│  ┃   'evt-789',            -- id           ┃   │
│  ┃   'PAYMENT',            -- aggregate_type┃  │
│  ┃   'pay-123',            -- aggregate_id ┃   │
│  ┃   'PAYMENT_APPROVED',   -- event_type   ┃   │
│  ┃   'payment.approved.v1',-- topic        ┃   │
│  ┃   'user-456',           -- partition_key┃   │
│  ┃   '{"paymentId":"pay-123",...}',-- payload│ │
│  ┃   'PENDING',            -- status       ┃   │
│  ┃   0                     -- retry_count  ┃   │
│  ┃ );                                      ┃   │
│  ┃                                         ┃   │
│  ┃ COMMIT;  ← AMBOS salvos atomicamente!   ┃   │
│  ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛   │
│                                                 │
│  Tables After Commit:                           │
│  ┌────────────┬───────┬────────┬──────────┐    │
│  │ payment_id │user_id│ amount │  status  │    │
│  ├────────────┼───────┼────────┼──────────┤    │
│  │ pay-123    │usr-456│ 100.00 │ APPROVED │    │
│  └────────────┴───────┴────────┴──────────┘    │
│                                                 │
│  ┌────────┬───────────┬────────────┬─────────┐ │
│  │evt_id  │agg_type   │ event_type │ status  │ │
│  ├────────┼───────────┼────────────┼─────────┤ │
│  │evt-789 │PAYMENT    │PAY_APPROVED│ PENDING │ │
│  └────────┴───────────┴────────────┴─────────┘ │
└─────────────────────────────────────────────────┘

FASE 2: ASYNCHRONOUS PUBLISHING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    ⏰ Wait 5 seconds...
                ↓
┌─────────────────────────────────────────────────┐
│ [JOB] OutboxPublisher                           │
│                                                 │
│  @Scheduled(fixedDelay = 5000)                  │
│  public void publishPendingEvents() {           │
│                                                 │
│    // 1. Query database                        │
│    SELECT * FROM outbox_event                   │
│    WHERE status = 'PENDING'                     │
│    ORDER BY created_at ASC                      │
│    LIMIT 100;                                   │
│                                                 │
│    // Found: evt-789                            │
│                                                 │
│    // 2. Publish to Kafka                      │
│    kafkaTemplate.send(                          │
│      "payment.approved.v1",  // topic           │
│      "user-456",             // key             │
│      payload                 // value           │
│    );                                           │
│                                                 │
│    // 3. Mark as PUBLISHED                     │
│    UPDATE outbox_event                          │
│    SET status = 'PUBLISHED',                    │
│        published_at = NOW()                     │
│    WHERE id = 'evt-789';                        │
│  }                                              │
└───────────────────┬─────────────────────────────┘
                    │
                    ↓
┌─────────────────────────────────────────────────┐
│ [KAFKA] Cluster                                 │
│                                                 │
│  Topic: payment.approved.v1                     │
│  Partition: 2 (hash of "user-456")             │
│                                                 │
│  ┌──────────────────────────────────────────┐  │
│  │ Offset: 12345                            │  │
│  │ Key: user-456                            │  │
│  │ Timestamp: 2024-01-15T10:30:00Z          │  │
│  │ Headers:                                 │  │
│  │   event-type: PAYMENT_APPROVED           │  │
│  │   event-id: evt-789                      │  │
│  │   source: outbox-publisher               │  │
│  │ Value: {                                 │  │
│  │   "eventId": "evt-789",                  │  │
│  │   "paymentId": "pay-123",                │  │
│  │   "userId": "user-456",                  │  │
│  │   "amount": 100.00,                      │  │
│  │   "currency": "BRL",                     │  │
│  │   "timestamp": 1705315800000             │  │
│  │ }                                        │  │
│  └──────────────────────────────────────────┘  │
│                                                 │
│  ✅ Event published successfully!               │
└───────────────────┬─────────────────────────────┘
                    │
                    ↓ (consume)
┌─────────────────────────────────────────────────┐
│ [CONSUMERS] Microservices                       │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ ms-email                                  │ │
│  │  - Envia email de confirmação             │ │
│  │  - "Seu pagamento foi aprovado!"          │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ ms-analytics                              │ │
│  │  - Registra métrica de conversão          │ │
│  │  - Atualiza dashboard                     │ │
│  └───────────────────────────────────────────┘ │
│                                                 │
│  ┌───────────────────────────────────────────┐ │
│  │ ms-inventory                              │ │
│  │  - Reserva estoque do produto             │ │
│  │  - Inicia processo de envio               │ │
│  └───────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

### Componentes Principais

```
┌─────────────────────────────────────────────────┐
│ COMPONENTES DO OUTBOX PATTERN                   │
└─────────────────────────────────────────────────┘

1. OutboxEvent (Entity)
   ├─ Tabela: outbox_event
   ├─ Campos:
   │  ├─ id (UUID)
   │  ├─ aggregate_type (PAYMENT, ORDER, etc)
   │  ├─ aggregate_id (pay-123, ord-456, etc)
   │  ├─ event_type (PAYMENT_APPROVED, etc)
   │  ├─ topic (payment.approved.v1)
   │  ├─ partition_key (user-456)
   │  ├─ payload (JSON serializado)
   │  ├─ status (PENDING/PUBLISHED/FAILED)
   │  ├─ retry_count (0, 1, 2, 3...)
   │  ├─ error_message (se houver)
   │  ├─ created_at
   │  ├─ published_at
   │  └─ version
   └─ Responsabilidade: Armazenar eventos pendentes

2. OutboxService
   ├─ Método: saveEvent(...)
   ├─ Responsabilidade:
   │  ├─ Serializar payload (JSON)
   │  ├─ Criar OutboxEvent
   │  └─ Salvar na transação atual
   └─ Chamado por: Use Case Services

3. OutboxPublisher (Job)
   ├─ Agendamento: @Scheduled(fixedDelay = 5000)
   ├─ Frequência: A cada 5 segundos
   ├─ Responsabilidade:
   │  ├─ Buscar eventos PENDING
   │  ├─ Publicar no Kafka
   │  ├─ Marcar como PUBLISHED
   │  └─ Retry em caso de falha
   └─ Executado por: Spring Scheduler

4. OutboxEventRepository
   ├─ Tipo: Spring Data JPA
   ├─ Queries:
   │  ├─ findByStatusOrderByCreatedAtAsc(PENDING, limit)
   │  ├─ countByStatus(status)
   │  ├─ findByAggregateTypeAndAggregateId(...)
   │  └─ findByStatusAndPublishedAtBefore(...)
   └─ Índices:
      ├─ idx_outbox_status_created (status, created_at)
      ├─ idx_outbox_aggregate (aggregate_type, aggregate_id)
      └─ idx_outbox_failed (status, retry_count)

5. KafkaTemplate
   ├─ Responsabilidade: Publicar eventos no Kafka
   ├─ Usado por: OutboxPublisher
   └─ Configuração: Com idempotência, acks=all
```

### Máquina de Estados do Evento

```
ESTADOS E TRANSIÇÕES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

                  [Evento Criado]
                        │
                        ↓
              ┌─────────────────┐
              │    PENDING      │  ← Estado inicial
              │  retry_count=0  │
              └────────┬────────┘
                       │
                       ↓
            [OutboxPublisher executa]
                       │
                       ↓
              ┌────────────────┐
              │ Tenta publicar │
              │   no Kafka     │
              └────────┬───────┘
                       │
         ┌─────────────┴─────────────┐
         │                           │
      SUCESSO                     FALHA
         │                           │
         ↓                           ↓
   ┌──────────┐         ┌────────────────────────┐
   │PUBLISHED │         │ retry_count < max?     │
   │          │         └──────┬────────┬────────┘
   │✅ FIM    │               SIM      NÃO
   └──────────┘                │        │
                               │        ↓
                               │   ┌─────────┐
                               │   │ FAILED  │
                               │   │         │
                               │   │❌ DLQ   │
                               │   └─────────┘
                               │
                               ↓
                    ┌──────────────────┐
                    │ PENDING          │
                    │ retry_count++    │
                    │ error_message    │
                    └─────────┬────────┘
                              │
                              └──→ Aguarda próximo job (5s)
                                   └──→ Tenta publicar novamente


EXEMPLO DE FLUXO COM RETRY:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

T0:  OutboxEvent criado
     └─> status=PENDING, retry_count=0

T1:  Job executa (5s depois)
     └─> Tenta publicar
     └─> Kafka timeout!
     └─> status=PENDING, retry_count=1, error="timeout"

T2:  Job executa (10s depois)
     └─> Tenta publicar
     └─> Kafka ainda down!
     └─> status=PENDING, retry_count=2, error="broker unavailable"

T3:  Job executa (15s depois)
     └─> Tenta publicar
     └─> Kafka voltou! ✅
     └─> status=PUBLISHED, published_at=NOW()

FLUXO COM FALHA PERMANENTE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

T0:  OutboxEvent criado (payload inválido - muito grande)
     └─> status=PENDING, retry_count=0

T1:  Job executa
     └─> Kafka rejeita (message.size > max.message.bytes)
     └─> status=PENDING, retry_count=1

T2:  Job executa
     └─> Kafka rejeita novamente
     └─> status=PENDING, retry_count=2

T3:  Job executa
     └─> Kafka rejeita
     └─> retry_count=3 >= max_retries!
     └─> status=FAILED, error="message too large"
     
     └─> ALERTA enviado
     └─> Evento movido para DLQ (Dead Letter Queue)
     └─> Engenharia investiga
```

---

## 4. Implementação Passo a Passo

Vou parar aqui para não exceder o limite. Este arquivo está ficando muito grande. Vou criar um script bash que gera todo o tutorial de uma vez.
