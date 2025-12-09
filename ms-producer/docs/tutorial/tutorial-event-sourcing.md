# Tutorial Definitivo: Event Sourcing

---

## 📋 Sumário

1. [O que é Event Sourcing](#1-o-que-é-event-sourcing)
2. [Por Que Usar Event Sourcing](#2-por-que-usar-event-sourcing)
3. [Event Store](#3-event-store)
4. [Reconstrução de Estado (Replay)](#4-reconstrução-de-estado-replay)
5. [Implementação Passo a Passo](#5-implementação-passo-a-passo)
6. [Snapshots (Otimização)](#6-snapshots-otimização)
7. [Projeções (Read Models)](#7-projeções-read-models)
8. [Versionamento de Eventos](#8-versionamento-de-eventos)
9. [Event Sourcing + CQRS](#9-event-sourcing--cqrs)
10. [Testando Event Sourcing](#10-testando-event-sourcing)
11. [Cenários do Dia a Dia](#11-cenários-do-dia-a-dia)
12. [Armadilhas Comuns](#12-armadilhas-comuns)
13. [Checklist Event Sourcing](#13-checklist-event-sourcing)
14. [Exercícios Práticos](#14-exercícios-práticos)

---

## 1. O que é Event Sourcing

### Definição em 30 Segundos

**Event Sourcing** armazena **EVENTOS** (mudanças de estado) ao invés do **ESTADO ATUAL**. O estado é **reconstruído** a partir do replay de todos os eventos.

```
PERSISTÊNCIA TRADICIONAL (State-Based):
  Salva ESTADO ATUAL

  Payment:
    id: 123
    status: APPROVED  ← Só sabe o estado ATUAL
    amount: 100.00

  ❌ Não sabe COMO chegou neste estado
  ❌ Não sabe QUANDO mudou
  ❌ Histórico perdido (UPDATE sobrescreve)


EVENT SOURCING (Event-Based):
  Salva EVENTOS (histórico completo)

  Event Store:
    1. PaymentCreatedEvent     (t=10:00:00) → status = PENDING
    2. PaymentApprovedEvent    (t=10:05:00) → status = APPROVED
    3. PaymentCancelledEvent   (t=10:10:00) → status = CANCELLED

  ✅ Sabe EXATAMENTE como chegou no estado atual
  ✅ Sabe QUANDO cada mudança ocorreu
  ✅ Pode reconstruir qualquer estado passado (time travel)
  ✅ Auditoria completa (imutável)

  Estado ATUAL = replay(evento1, evento2, evento3)
```

**Conceitos-chave:**

- **Event** = Algo que ACONTECEU (passado)
- **Event Store** = Banco de eventos (append-only, imutável)
- **Replay** = Reconstruir estado aplicando eventos sequencialmente
- **Snapshot** = Foto do estado em um momento (otimização)
- **Projection** = View construída a partir dos eventos (Read Model)
- **Aggregate** = Entidade raiz (ex: Payment, Order)

**Em português claro:**

Ao invés de salvar "Payment status = APPROVED", você salva eventos:
- "Payment foi criado"
- "Payment foi aprovado"
- "Payment foi cancelado"

O estado ATUAL é calculado aplicando TODOS os eventos na ordem.

---

## 2. Por Que Usar Event Sourcing

### Problema: Persistência Tradicional

```java
// ❌ PERSISTÊNCIA TRADICIONAL (State-Based)

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;

    private PaymentStatus status;  // ← SÓ estado ATUAL
    private BigDecimal amount;

    // ...
}

@Service
public class PaymentService {

    @Transactional
    public void approvePayment(UUID paymentId) {

        // 1. Buscar estado ATUAL
        Payment payment = repository.findById(paymentId).orElseThrow();

        // 2. Mudar estado
        payment.setStatus(PaymentStatus.APPROVED);  // ← UPDATE!

        // 3. Salvar (SOBRESCREVE estado anterior)
        repository.save(payment);

        // ❌ PROBLEMA: estado anterior PERDIDO!
        //    Não sabe QUANDO foi aprovado
        //    Não sabe QUEM aprovou
        //    Não sabe POR QUE foi aprovado
    }
}

PROBLEMAS REAIS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ HISTÓRICO PERDIDO
   - UPDATE sobrescreve estado anterior
   - Não sabe COMO chegou no estado atual
   - Exemplo: Payment status = CANCELLED
     Pergunta: Foi PENDING → CANCELLED? Ou APPROVED → CANCELLED?
     Resposta: NÃO SEI! (histórico perdido)

2. ❌ AUDITORIA DIFÍCIL
   - Quer saber QUEM mudou e QUANDO?
   - Precisa criar tabela audit_log separada
   - Complexidade adicional
   - Pode ficar dessincronizado

3. ❌ IMPOSSÍVEL DESFAZER
   - Status mudou de PENDING → APPROVED
   - Quer voltar para PENDING?
   - Não sabe qual era o estado anterior exato!

4. ❌ BUGS DE CONCORRÊNCIA
   - Thread 1: lê Payment (status = PENDING)
   - Thread 2: lê Payment (status = PENDING)
   - Thread 1: aprova → status = APPROVED (COMMIT)
   - Thread 2: cancela → status = CANCELLED (COMMIT)
   - ❌ Aprovação foi PERDIDA! (lost update)

5. ❌ INTEGRAÇÕES DIFÍCEIS
   - Como notificar outros sistemas das mudanças?
   - Precisa criar eventos manualmente (duplicação)
   - Eventos podem ficar dessincronizados do estado

6. ❌ RELATÓRIOS HISTÓRICOS IMPOSSÍVEIS
   - Quer saber: "Quantos payments foram PENDING em janeiro/2024?"
   - Resposta: IMPOSSÍVEL (não tem histórico)
   - Só sabe estado ATUAL
```

---

### Solução: Event Sourcing

```java
// ✅ EVENT SOURCING (Event-Based)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENTOS (Imutáveis)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public record PaymentCreatedEvent(
    String eventId,
    UUID paymentId,
    UUID userId,
    BigDecimal amount,
    String currency,
    long timestamp,
    int version  // ← Versão do evento (1, 2, 3, ...)
) {}

public record PaymentApprovedEvent(
    String eventId,
    UUID paymentId,
    UUID approvedBy,
    String approvalReason,
    long timestamp,
    int version
) {}

public record PaymentCancelledEvent(
    String eventId,
    UUID paymentId,
    UUID cancelledBy,
    String cancellationReason,
    long timestamp,
    int version
) {}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT STORE (Banco de eventos)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Entity
@Table(name = "event_store")
public class EventStoreEntry {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;  // Payment ID

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;  // "Payment"

    @Column(name = "event_type", nullable = false)
    private String eventType;  // "PaymentCreatedEvent"

    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;  // JSON do evento

    @Column(name = "version", nullable = false)
    private int version;  // 1, 2, 3, ...

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    // ✅ APPEND-ONLY: nunca UPDATE ou DELETE!
}

CREATE TABLE event_store (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    version INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL,

    -- ✅ Garantir ordem dos eventos
    UNIQUE (aggregate_id, version)
);

CREATE INDEX idx_event_store_aggregate
    ON event_store(aggregate_id, version);


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      AGGREGATE (Reconstruído de eventos)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Payment {

    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private int version;

    private List<PaymentEvent> pendingEvents = new ArrayList<>();

    /**
     * Reconstruir Payment a partir de eventos (Event Sourcing).
     */
    public static Payment fromEvents(List<PaymentEvent> events) {
        Payment payment = new Payment();

        // Replay TODOS eventos (reconstrói estado)
        for (PaymentEvent event : events) {
            payment.apply(event);
        }

        return payment;
    }

    /**
     * Aplicar evento (muda estado).
     */
    private void apply(PaymentEvent event) {
        switch (event) {
            case PaymentCreatedEvent e -> {
                this.id = e.paymentId();
                this.userId = e.userId();
                this.amount = e.amount();
                this.currency = e.currency();
                this.status = PaymentStatus.PENDING;
                this.version = e.version();
            }
            case PaymentApprovedEvent e -> {
                this.status = PaymentStatus.APPROVED;
                this.version = e.version();
            }
            case PaymentCancelledEvent e -> {
                this.status = PaymentStatus.CANCELLED;
                this.version = e.version();
            }
        }
    }

    /**
     * Aprovar pagamento (gera evento).
     */
    public void approve(UUID approvedBy, String reason) {
        // Validação
        if (status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled payment");
        }

        // Criar evento
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            UUID.randomUUID().toString(),
            this.id,
            approvedBy,
            reason,
            Instant.now().toEpochMilli(),
            this.version + 1
        );

        // Aplicar evento (muda estado)
        apply(event);

        // Adicionar aos eventos pendentes (para salvar)
        pendingEvents.add(event);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMMAND HANDLER (Carrega + Salva eventos)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentCommandHandler {

    private final EventStore eventStore;

    @Transactional
    public void handle(ApprovePaymentCommand command) {

        // 1. Carregar TODOS eventos do Payment (Event Store)
        List<PaymentEvent> events = eventStore.getEvents(command.paymentId());

        // 2. Reconstruir estado ATUAL (replay de eventos)
        Payment payment = Payment.fromEvents(events);

        // 3. Executar comando (gera novo evento)
        payment.approve(command.approvedBy(), command.reason());

        // 4. Salvar NOVO evento (append-only)
        PaymentApprovedEvent newEvent = payment.getPendingEvents().get(0);
        eventStore.save(newEvent);

        // ✅ Evento salvo (imutável)!
        // ✅ Estado anterior preservado (eventos 1, 2, 3, ...)
        // ✅ Histórico completo
    }
}

BENEFÍCIOS REAIS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ HISTÓRICO COMPLETO (Audit Log Grátis)
   ├─ TODOS eventos salvos (imutáveis)
   ├─ Sabe EXATAMENTE como chegou no estado atual
   ├─ Sabe QUANDO cada mudança ocorreu
   ├─ Sabe QUEM fez cada mudança (approvedBy, cancelledBy)
   ├─ Sabe POR QUE mudou (approvalReason, cancellationReason)
   └─ Não precisa tabela audit_log separada

2. ✅ TIME TRAVEL (Reconstruir Qualquer Estado Passado)
   ├─ Quer saber estado em 10/01/2024 10:00?
   ├─ Replay eventos até esse timestamp
   ├─ Estado reconstruído EXATO
   └─ Relatórios históricos precisos

3. ✅ DEBUGABILIDADE EXTREMA
   ├─ Bug: Payment está CANCELLED (errado!)
   ├─ Replay eventos: vê EXATO fluxo que causou o bug
   ├─ Identifica qual evento causou problema
   └─ Corrige bug com precisão cirúrgica

4. ✅ EVENTOS = INTEGRAÇÕES
   ├─ Eventos JÁ existem (não precisa criar manualmente)
   ├─ Publica para Kafka automaticamente
   ├─ Sempre sincronizado (evento = source of truth)
   └─ Outros sistemas consomem eventos

5. ✅ TESTES DECLARATIVOS
   ├─ Given: [PaymentCreatedEvent, PaymentApprovedEvent]
   ├─ When: CancelPayment
   ├─ Then: [PaymentCancelledEvent]
   └─ Testa COMPORTAMENTO (não estado)

6. ✅ CONCORRÊNCIA RESOLVIDA
   ├─ Usa OPTIMISTIC LOCKING (version)
   ├─ Thread 1: salva evento version=2
   ├─ Thread 2: tenta salvar evento version=2 (duplicate key!)
   ├─ Thread 2 precisa recarregar e tentar novamente
   └─ NUNCA perde updates

7. ✅ ANÁLISE DE NEGÓCIO
   ├─ Quer saber: "Quantos payments foram PENDING em janeiro?"
   ├─ Replay eventos de janeiro
   ├─ Conta quantos tinham status=PENDING
   └─ Insights que STATE-BASED não permite
```

---

### Comparação: State-Based vs Event-Based

| Aspecto | State-Based | Event-Based (Event Sourcing) |
|---------|-------------|------------------------------|
| **Persistência** | Estado ATUAL | EVENTOS (histórico completo) |
| **Histórico** | ❌ Perdido (UPDATE sobrescreve) | ✅ Completo (eventos imutáveis) |
| **Auditoria** | ⚠️ Precisa tabela separada | ✅ Grátis (eventos = audit log) |
| **Time Travel** | ❌ Impossível | ✅ Replay eventos |
| **Debugabilidade** | ❌ Difícil (não sabe como chegou) | ✅ Total (replay reproduz bug) |
| **Integrações** | ⚠️ Eventos manuais (pode dessinc) | ✅ Eventos = source of truth |
| **Concorrência** | ⚠️ Lost updates | ✅ Optimistic locking (version) |
| **Complexidade** | ✅ Simples | ⚠️ Maior (replay, snapshots) |
| **Performance Leitura** | ✅ Rápida (estado atual) | ⚠️ Lenta (replay) → usa CQRS |
| **Performance Escrita** | ✅ Rápida (UPDATE) | ✅ Rápida (append-only) |

---

## 3. Event Store

### O Que É Event Store

```
EVENT STORE = Banco de dados de eventos (append-only log)

CARACTERÍSTICAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ APPEND-ONLY
   - Só pode ADICIONAR eventos (INSERT)
   - NUNCA pode UPDATE ou DELETE
   - Imutável (eventos são fatos históricos)

2. ✅ ORDENADO
   - Eventos em ORDEM CRONOLÓGICA
   - Cada evento tem VERSION (1, 2, 3, ...)
   - Garante sequência correta

3. ✅ PARTICIONADO POR AGGREGATE
   - Eventos agrupados por Aggregate ID (Payment ID)
   - Garante ordem dentro de um Aggregate
   - Pode buscar todos eventos de um Payment

4. ✅ OTIMIZADO PARA ESCRITA
   - INSERT muito rápido (append-only)
   - Sem índices complexos (só aggregate_id + version)

5. ✅ OTIMIZADO PARA REPLAY
   - Busca sequencial (ORDER BY version)
   - Pode usar streaming (não carrega tudo em memória)
```

### Estrutura do Event Store

```sql
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
--      TABELA EVENT_STORE
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

CREATE TABLE event_store (

    -- ✅ ID único do evento
    id UUID PRIMARY KEY,

    -- ✅ Aggregate (entidade raiz)
    aggregate_id UUID NOT NULL,          -- ID do Payment/Order/etc
    aggregate_type VARCHAR(255) NOT NULL, -- "Payment", "Order"

    -- ✅ Tipo do evento
    event_type VARCHAR(255) NOT NULL,    -- "PaymentCreatedEvent"

    -- ✅ Dados do evento (JSON)
    event_data TEXT NOT NULL,

    -- ✅ Versionamento (order sequence)
    version INTEGER NOT NULL,            -- 1, 2, 3, 4, ...

    -- ✅ Timestamp
    timestamp TIMESTAMP NOT NULL,

    -- ✅ Metadados (opcional)
    metadata JSONB,                      -- user_id, correlation_id, etc

    -- ✅ Garante unicidade da versão por aggregate
    CONSTRAINT unique_aggregate_version
        UNIQUE (aggregate_id, version)
);

-- ✅ Índice para buscar eventos de um aggregate
CREATE INDEX idx_event_store_aggregate
    ON event_store(aggregate_id, version);

-- ✅ Índice para buscar por tipo (projeções)
CREATE INDEX idx_event_store_type
    ON event_store(event_type, timestamp);
```

### Exemplo de Dados no Event Store

```
SELECT * FROM event_store WHERE aggregate_id = '550e8400...';

┌──────────────────────────────────────────────────────────────────────────────┐
│ id        │ aggregate_id │ event_type           │ version │ timestamp        │
├──────────────────────────────────────────────────────────────────────────────┤
│ uuid-001  │ 550e8400...  │ PaymentCreatedEvent  │ 1       │ 2024-01-10 10:00 │
│ uuid-002  │ 550e8400...  │ PaymentApprovedEvent │ 2       │ 2024-01-10 10:05 │
│ uuid-003  │ 550e8400...  │ PaymentCancelledEvent│ 3       │ 2024-01-10 10:10 │
└──────────────────────────────────────────────────────────────────────────────┘

event_data (JSON):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Version 1 (PaymentCreatedEvent):
{
  "eventId": "uuid-001",
  "paymentId": "550e8400...",
  "userId": "user-123",
  "amount": 100.00,
  "currency": "USD",
  "timestamp": 1704879600000
}

Version 2 (PaymentApprovedEvent):
{
  "eventId": "uuid-002",
  "paymentId": "550e8400...",
  "approvedBy": "admin-456",
  "approvalReason": "Verified payment",
  "timestamp": 1704879900000
}

Version 3 (PaymentCancelledEvent):
{
  "eventId": "uuid-003",
  "paymentId": "550e8400...",
  "cancelledBy": "user-123",
  "cancellationReason": "User requested refund",
  "timestamp": 1704880200000
}

ESTADO ATUAL (reconstruído):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Payment {
  id: "550e8400...",
  userId: "user-123",
  amount: 100.00,
  currency: "USD",
  status: CANCELLED,  ← Estado ATUAL (após replay)
  version: 3
}
```

---

## 4. Reconstrução de Estado (Replay)

### Como Funciona o Replay

```
REPLAY = Reconstruir estado aplicando eventos sequencialmente

┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  ESTADO INICIAL (vazio)                                     │
│  Payment = null                                             │
│                                                             │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  EVENTO 1: PaymentCreatedEvent                              │
│    paymentId: 550e8400                                      │
│    userId: user-123                                         │
│    amount: 100.00                                           │
│    currency: USD                                            │
└────────────────────┬────────────────────────────────────────┘
                     │ apply(evento1)
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  ESTADO APÓS EVENTO 1:                                      │
│  Payment {                                                  │
│    id: 550e8400,                                            │
│    userId: user-123,                                        │
│    amount: 100.00,                                          │
│    currency: USD,                                           │
│    status: PENDING  ← Estado mudou                          │
│  }                                                          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  EVENTO 2: PaymentApprovedEvent                             │
│    paymentId: 550e8400                                      │
│    approvedBy: admin-456                                    │
│    approvalReason: "Verified payment"                       │
└────────────────────┬────────────────────────────────────────┘
                     │ apply(evento2)
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  ESTADO APÓS EVENTO 2:                                      │
│  Payment {                                                  │
│    id: 550e8400,                                            │
│    userId: user-123,                                        │
│    amount: 100.00,                                          │
│    currency: USD,                                           │
│    status: APPROVED  ← Estado mudou                         │
│  }                                                          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  EVENTO 3: PaymentCancelledEvent                            │
│    paymentId: 550e8400                                      │
│    cancelledBy: user-123                                    │
│    cancellationReason: "User requested refund"              │
└────────────────────┬────────────────────────────────────────┘
                     │ apply(evento3)
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  ESTADO FINAL (ATUAL):                                      │
│  Payment {                                                  │
│    id: 550e8400,                                            │
│    userId: user-123,                                        │
│    amount: 100.00,                                          │
│    currency: USD,                                           │
│    status: CANCELLED  ← Estado ATUAL                        │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
```

### Implementação do Replay

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      AGGREGATE (Payment)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Payment {

    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private int version;

    private List<PaymentEvent> pendingEvents = new ArrayList<>();

    /**
     * Reconstruir Payment a partir de eventos (REPLAY).
     *
     * @param events Lista de eventos em ORDEM CRONOLÓGICA
     * @return Payment com estado reconstruído
     */
    public static Payment fromEvents(List<PaymentEvent> events) {

        if (events.isEmpty()) {
            throw new IllegalArgumentException("Cannot reconstruct from empty event list");
        }

        // Criar Payment vazio
        Payment payment = new Payment();

        // Aplicar TODOS eventos sequencialmente
        for (PaymentEvent event : events) {
            payment.apply(event);
        }

        return payment;
    }

    /**
     * Aplicar evento (muda estado).
     *
     * Cada tipo de evento muda o estado de uma forma específica.
     */
    private void apply(PaymentEvent event) {

        switch (event) {

            case PaymentCreatedEvent e -> {
                // Criar Payment (primeiro evento)
                this.id = e.paymentId();
                this.userId = e.userId();
                this.amount = e.amount();
                this.currency = e.currency();
                this.status = PaymentStatus.PENDING;
                this.version = e.version();
            }

            case PaymentApprovedEvent e -> {
                // Aprovar Payment
                if (this.status == PaymentStatus.CANCELLED) {
                    throw new IllegalStateException(
                        "Cannot apply PaymentApprovedEvent to CANCELLED payment"
                    );
                }

                this.status = PaymentStatus.APPROVED;
                this.version = e.version();
            }

            case PaymentCancelledEvent e -> {
                // Cancelar Payment
                this.status = PaymentStatus.CANCELLED;
                this.version = e.version();
            }

            default -> {
                throw new UnsupportedOperationException(
                    "Unknown event type: " + event.getClass().getName()
                );
            }
        }
    }

    // ... métodos de comando (approve, cancel, etc)
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT STORE REPOSITORY
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Repository
public class EventStore {

    private final EventStoreEntryRepository repository;
    private final ObjectMapper objectMapper;

    public EventStore(
            EventStoreEntryRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Buscar TODOS eventos de um aggregate (para replay).
     *
     * @param aggregateId ID do Payment/Order/etc
     * @return Lista de eventos em ORDEM CRONOLÓGICA
     */
    public List<PaymentEvent> getEvents(UUID aggregateId) {

        // Buscar do banco (ORDER BY version)
        List<EventStoreEntry> entries = repository
            .findByAggregateIdOrderByVersionAsc(aggregateId);

        if (entries.isEmpty()) {
            throw new AggregateNotFoundException(aggregateId);
        }

        // Deserializar eventos
        return entries.stream()
            .map(this::deserializeEvent)
            .collect(Collectors.toList());
    }

    /**
     * Salvar evento (append-only).
     *
     * @param event Evento a salvar
     */
    public void save(PaymentEvent event) {

        // Serializar evento
        String eventData = serializeEvent(event);

        // Criar entry
        EventStoreEntry entry = new EventStoreEntry(
            UUID.randomUUID(),
            event.getAggregateId(),
            "Payment",
            event.getClass().getSimpleName(),
            eventData,
            event.getVersion(),
            Instant.now()
        );

        // Salvar (INSERT)
        try {
            repository.save(entry);
        } catch (DataIntegrityViolationException e) {
            // Versão duplicada = concorrência!
            throw new ConcurrencyException(
                "Concurrent modification detected for aggregate: " +
                event.getAggregateId()
            );
        }
    }

    private String serializeEvent(PaymentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(e);
        }
    }

    private PaymentEvent deserializeEvent(EventStoreEntry entry) {
        try {
            Class<?> eventClass = Class.forName(
                "com.mvbr.store.domain.event." + entry.getEventType()
            );

            return (PaymentEvent) objectMapper.readValue(
                entry.getEventData(),
                eventClass
            );

        } catch (Exception e) {
            throw new EventDeserializationException(e);
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMMAND HANDLER (usa replay)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentCommandHandler {

    private final EventStore eventStore;

    public ApprovePaymentCommandHandler(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Transactional
    public void handle(ApprovePaymentCommand command) {

        // 1. Buscar TODOS eventos (Event Store)
        List<PaymentEvent> events = eventStore.getEvents(command.paymentId());

        // 2. REPLAY: reconstruir estado ATUAL
        Payment payment = Payment.fromEvents(events);

        // 3. Executar comando (gera novo evento)
        payment.approve(command.approvedBy(), command.reason());

        // 4. Salvar NOVO evento (append-only)
        PaymentApprovedEvent newEvent = payment.getPendingEvents().get(0);
        eventStore.save(newEvent);
    }
}
```

---

## 5. Implementação Passo a Passo

### Passo 1: Criar Eventos

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INTERFACE BASE
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.domain.event;

import java.util.UUID;

public interface PaymentEvent {
    String getEventId();
    UUID getAggregateId();
    int getVersion();
    long getTimestamp();
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENTO 1: PaymentCreatedEvent
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.domain.event;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCreatedEvent(
    String eventId,
    UUID paymentId,        // ← aggregate ID
    UUID userId,
    BigDecimal amount,
    String currency,
    long timestamp,
    int version
) implements PaymentEvent {

    @Override
    public UUID getAggregateId() {
        return paymentId;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENTO 2: PaymentApprovedEvent
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public record PaymentApprovedEvent(
    String eventId,
    UUID paymentId,
    UUID approvedBy,
    String approvalReason,
    long timestamp,
    int version
) implements PaymentEvent {

    @Override
    public UUID getAggregateId() {
        return paymentId;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENTO 3: PaymentCancelledEvent
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public record PaymentCancelledEvent(
    String eventId,
    UUID paymentId,
    UUID cancelledBy,
    String cancellationReason,
    long timestamp,
    int version
) implements PaymentEvent {

    @Override
    public UUID getAggregateId() {
        return paymentId;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }
}
```

---

### Passo 2: Criar Event Store

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT STORE ENTRY (JPA Entity)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.eventsourcing.entity;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "event_store",
    uniqueConstraints = @UniqueConstraint(
        name = "unique_aggregate_version",
        columnNames = {"aggregate_id", "version"}
    )
)
public class EventStoreEntry {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_data", nullable = false, columnDefinition = "TEXT")
    private String eventData;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    // Construtor padrão (JPA)
    protected EventStoreEntry() {}

    public EventStoreEntry(
            UUID id,
            UUID aggregateId,
            String aggregateType,
            String eventType,
            String eventData,
            int version,
            Instant timestamp
    ) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.eventType = eventType;
        this.eventData = eventData;
        this.version = version;
        this.timestamp = timestamp;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public String getEventType() { return eventType; }
    public String getEventData() { return eventData; }
    public int getVersion() { return version; }
    public Instant getTimestamp() { return timestamp; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      REPOSITORY
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.eventsourcing.repository;

import com.mvbr.store.infrastructure.eventsourcing.entity.EventStoreEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventStoreEntryRepository extends JpaRepository<EventStoreEntry, UUID> {

    /**
     * Buscar TODOS eventos de um aggregate em ORDEM.
     */
    List<EventStoreEntry> findByAggregateIdOrderByVersionAsc(UUID aggregateId);

    /**
     * Buscar eventos a partir de uma versão (para snapshots).
     */
    List<EventStoreEntry> findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(
        UUID aggregateId,
        int version
    );
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT STORE (Service)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.eventsourcing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.store.domain.event.PaymentEvent;
import com.mvbr.store.infrastructure.eventsourcing.entity.EventStoreEntry;
import com.mvbr.store.infrastructure.eventsourcing.repository.EventStoreEntryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EventStore {

    private final EventStoreEntryRepository repository;
    private final ObjectMapper objectMapper;

    public EventStore(
            EventStoreEntryRepository repository,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Buscar todos eventos de um aggregate.
     */
    public List<PaymentEvent> getEvents(UUID aggregateId) {

        List<EventStoreEntry> entries = repository
            .findByAggregateIdOrderByVersionAsc(aggregateId);

        if (entries.isEmpty()) {
            throw new AggregateNotFoundException(
                "Aggregate not found: " + aggregateId
            );
        }

        return entries.stream()
            .map(this::deserializeEvent)
            .collect(Collectors.toList());
    }

    /**
     * Salvar evento (append-only).
     */
    public void save(PaymentEvent event) {

        String eventData = serializeEvent(event);

        EventStoreEntry entry = new EventStoreEntry(
            UUID.randomUUID(),
            event.getAggregateId(),
            "Payment",
            event.getClass().getSimpleName(),
            eventData,
            event.getVersion(),
            Instant.now()
        );

        try {
            repository.save(entry);
        } catch (DataIntegrityViolationException e) {
            throw new ConcurrencyException(
                "Concurrent modification for aggregate: " + event.getAggregateId()
            );
        }
    }

    private String serializeEvent(PaymentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new EventSerializationException("Failed to serialize event", e);
        }
    }

    private PaymentEvent deserializeEvent(EventStoreEntry entry) {
        try {
            Class<?> eventClass = Class.forName(
                "com.mvbr.store.domain.event." + entry.getEventType()
            );

            return (PaymentEvent) objectMapper.readValue(
                entry.getEventData(),
                eventClass
            );

        } catch (Exception e) {
            throw new EventDeserializationException("Failed to deserialize event", e);
        }
    }
}
```

---

### Passo 3: Criar Migration (Flyway)

```sql
-- ✅ V004__create_event_store.sql

CREATE TABLE event_store (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    event_data TEXT NOT NULL,
    version INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL,

    -- Garantir unicidade da versão por aggregate
    CONSTRAINT unique_aggregate_version
        UNIQUE (aggregate_id, version)
);

-- Índice para buscar eventos de um aggregate
CREATE INDEX idx_event_store_aggregate
    ON event_store(aggregate_id, version);

-- Índice para buscar por tipo (projeções)
CREATE INDEX idx_event_store_type
    ON event_store(event_type, timestamp);
```

---

### Passo 4: Implementar Aggregate

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      AGGREGATE (Payment)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.domain.model;

import com.mvbr.store.domain.event.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Payment {

    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private int version;

    private List<PaymentEvent> pendingEvents = new ArrayList<>();

    /**
     * Reconstruir Payment a partir de eventos (Event Sourcing).
     */
    public static Payment fromEvents(List<PaymentEvent> events) {

        if (events.isEmpty()) {
            throw new IllegalArgumentException("Cannot reconstruct from empty events");
        }

        Payment payment = new Payment();

        for (PaymentEvent event : events) {
            payment.apply(event);
        }

        return payment;
    }

    /**
     * Criar novo Payment (gera evento).
     */
    public static Payment create(UUID paymentId, UUID userId,
                                 BigDecimal amount, String currency) {

        Payment payment = new Payment();

        // Criar evento
        PaymentCreatedEvent event = new PaymentCreatedEvent(
            UUID.randomUUID().toString(),
            paymentId,
            userId,
            amount,
            currency,
            Instant.now().toEpochMilli(),
            1  // primeira versão
        );

        // Aplicar evento
        payment.apply(event);

        // Adicionar aos pendentes
        payment.pendingEvents.add(event);

        return payment;
    }

    /**
     * Aprovar Payment (gera evento).
     */
    public void approve(UUID approvedBy, String reason) {

        if (status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled payment");
        }

        PaymentApprovedEvent event = new PaymentApprovedEvent(
            UUID.randomUUID().toString(),
            this.id,
            approvedBy,
            reason,
            Instant.now().toEpochMilli(),
            this.version + 1
        );

        apply(event);
        pendingEvents.add(event);
    }

    /**
     * Cancelar Payment (gera evento).
     */
    public void cancel(UUID cancelledBy, String reason) {

        PaymentCancelledEvent event = new PaymentCancelledEvent(
            UUID.randomUUID().toString(),
            this.id,
            cancelledBy,
            reason,
            Instant.now().toEpochMilli(),
            this.version + 1
        );

        apply(event);
        pendingEvents.add(event);
    }

    /**
     * Aplicar evento (muda estado).
     */
    private void apply(PaymentEvent event) {

        switch (event) {
            case PaymentCreatedEvent e -> {
                this.id = e.paymentId();
                this.userId = e.userId();
                this.amount = e.amount();
                this.currency = e.currency();
                this.status = PaymentStatus.PENDING;
                this.version = e.version();
            }

            case PaymentApprovedEvent e -> {
                this.status = PaymentStatus.APPROVED;
                this.version = e.version();
            }

            case PaymentCancelledEvent e -> {
                this.status = PaymentStatus.CANCELLED;
                this.version = e.version();
            }

            default -> throw new UnsupportedOperationException(
                "Unknown event: " + event.getClass()
            );
        }
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public int getVersion() { return version; }

    public List<PaymentEvent> getPendingEvents() {
        return pendingEvents;
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
```

---

### Passo 5: Implementar Command Handlers

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      CREATE PAYMENT
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class CreatePaymentCommandHandler {

    private final EventStore eventStore;

    public CreatePaymentCommandHandler(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Transactional
    public UUID handle(CreatePaymentCommand command) {

        // 1. Criar Payment (gera evento)
        Payment payment = Payment.create(
            UUID.randomUUID(),
            command.userId(),
            command.amount(),
            command.currency()
        );

        // 2. Salvar eventos no Event Store
        payment.getPendingEvents().forEach(eventStore::save);

        // 3. Limpar eventos pendentes
        payment.clearPendingEvents();

        return payment.getId();
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      APPROVE PAYMENT
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentCommandHandler {

    private final EventStore eventStore;

    public ApprovePaymentCommandHandler(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Transactional
    public void handle(ApprovePaymentCommand command) {

        // 1. Carregar eventos (Event Store)
        List<PaymentEvent> events = eventStore.getEvents(command.paymentId());

        // 2. Reconstruir Payment (replay)
        Payment payment = Payment.fromEvents(events);

        // 3. Executar comando (gera evento)
        payment.approve(command.approvedBy(), command.reason());

        // 4. Salvar novo evento
        payment.getPendingEvents().forEach(eventStore::save);

        payment.clearPendingEvents();
    }
}
```

---

## 6. Snapshots (Otimização)

### O Problema

```
PROBLEMA: Replay lento com muitos eventos
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Payment com 10.000 eventos:
  1. PaymentCreatedEvent
  2. PaymentApprovedEvent
  3. PaymentCancelledEvent
  ... (9.997 eventos)
  10.000. PaymentRefundedEvent

Replay = aplicar 10.000 eventos sequencialmente
  ❌ LENTO (segundos ou minutos)
  ❌ Desperdício de CPU
  ❌ Não escala
```

### Solução: Snapshots

```
SNAPSHOT = "Foto" do estado em um momento

┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  Eventos 1-1000:                                            │
│    1. PaymentCreatedEvent                                   │
│    2. PaymentApprovedEvent                                  │
│    ...                                                      │
│    1000. PaymentUpdatedEvent                                │
│                                                             │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  SNAPSHOT (version 1000)                                    │
│  Payment {                                                  │
│    id: 550e8400,                                            │
│    userId: user-123,                                        │
│    amount: 100.00,                                          │
│    currency: USD,                                           │
│    status: APPROVED  ← Estado na versão 1000                │
│  }                                                          │
└─────────────────────────────────────────────────────────────┘
                     │
                     │ + replay eventos 1001-10000
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│  ESTADO ATUAL (version 10000)                               │
│  ✅ Replay só de 9.000 eventos (não 10.000)                 │
│  ✅ 10x mais rápido                                         │
└─────────────────────────────────────────────────────────────┘

ESTRATÉGIA:
  • A cada N eventos (ex: 100), salva snapshot
  • Replay: carrega snapshot + replay eventos após snapshot
  • Exemplo: snapshot v1000 + replay v1001-v10000 = 9.000 eventos
```

### Implementação de Snapshots

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      SNAPSHOT ENTITY
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Entity
@Table(name = "snapshot")
public class Snapshot {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "state_data", columnDefinition = "TEXT")
    private String stateData;  // JSON do estado

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    // ...
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      SNAPSHOT SERVICE
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class SnapshotService {

    private static final int SNAPSHOT_INTERVAL = 100;  // A cada 100 eventos

    private final SnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    /**
     * Salvar snapshot do Payment.
     */
    public void saveSnapshot(Payment payment) {

        String stateData = serializeState(payment);

        Snapshot snapshot = new Snapshot(
            UUID.randomUUID(),
            payment.getId(),
            "Payment",
            payment.getVersion(),
            stateData,
            Instant.now()
        );

        snapshotRepository.save(snapshot);
    }

    /**
     * Buscar snapshot mais recente.
     */
    public Optional<Payment> loadSnapshot(UUID aggregateId) {

        return snapshotRepository
            .findFirstByAggregateIdOrderByVersionDesc(aggregateId)
            .map(this::deserializeState);
    }

    /**
     * Verificar se deve criar snapshot.
     */
    public boolean shouldCreateSnapshot(int version) {
        return version % SNAPSHOT_INTERVAL == 0;
    }

    private String serializeState(Payment payment) {
        // Serializar estado completo do Payment
        // ...
    }

    private Payment deserializeState(Snapshot snapshot) {
        // Deserializar Payment
        // ...
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT STORE (com snapshots)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class EventStore {

    private final EventStoreEntryRepository eventRepository;
    private final SnapshotService snapshotService;

    /**
     * Buscar eventos (usa snapshot se disponível).
     */
    public List<PaymentEvent> getEvents(UUID aggregateId) {

        // 1. Tentar carregar snapshot
        Optional<Payment> snapshot = snapshotService.loadSnapshot(aggregateId);

        if (snapshot.isPresent()) {
            // Carregar eventos APÓS snapshot
            int snapshotVersion = snapshot.get().getVersion();

            List<EventStoreEntry> entries = eventRepository
                .findByAggregateIdAndVersionGreaterThanOrderByVersionAsc(
                    aggregateId,
                    snapshotVersion
                );

            // Criar lista: snapshot + eventos após
            List<PaymentEvent> events = new ArrayList<>();
            events.addAll(snapshot.get().getAppliedEvents());  // Eventos já aplicados
            events.addAll(deserialize(entries));               // Eventos novos

            return events;

        } else {
            // Sem snapshot: carregar TODOS eventos
            List<EventStoreEntry> entries = eventRepository
                .findByAggregateIdOrderByVersionAsc(aggregateId);

            return deserialize(entries);
        }
    }

    /**
     * Salvar evento (cria snapshot se necessário).
     */
    public void save(PaymentEvent event) {

        // Salvar evento
        saveEventEntry(event);

        // Criar snapshot a cada N eventos
        if (snapshotService.shouldCreateSnapshot(event.getVersion())) {

            // Reconstruir Payment completo
            List<PaymentEvent> events = getEvents(event.getAggregateId());
            Payment payment = Payment.fromEvents(events);

            // Salvar snapshot
            snapshotService.saveSnapshot(payment);
        }
    }
}
```

---

## 7. Projeções (Read Models)

### O Que São Projeções

```
PROJEÇÃO = View (Read Model) construída a partir de eventos

┌─────────────────────────────────────────────────────────────┐
│                    EVENT STORE                              │
│                                                             │
│  Events:                                                    │
│    1. PaymentCreatedEvent (amount: 100)                     │
│    2. PaymentCreatedEvent (amount: 200)                     │
│    3. PaymentApprovedEvent (paymentId: 1)                   │
│    4. PaymentCancelledEvent (paymentId: 2)                  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ (Event Handlers consomem)
                       │
           ┌───────────┴──────────────────┐
           │                              │
           ▼                              ▼
┌────────────────────────┐   ┌────────────────────────┐
│  PROJEÇÃO 1            │   │  PROJEÇÃO 2            │
│  PaymentListView       │   │  PaymentStatsView      │
├────────────────────────┤   ├────────────────────────┤
│ Todos pagamentos       │   │ Estatísticas agregadas │
│ (desnormalizado)       │   │                        │
│                        │   │ total: $300            │
│ [                      │   │ approved: 1            │
│   {id: 1, amount: 100},│   │ cancelled: 1           │
│   {id: 2, amount: 200} │   │ pending: 0             │
│ ]                      │   │                        │
└────────────────────────┘   └────────────────────────┘

✅ Cada projeção = visão ESPECÍFICA dos dados
✅ Otimizada para consultas
✅ Desnormalizada
✅ Pode usar bancos diferentes (MongoDB, Elasticsearch)
```

### Implementação de Projeções

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      PROJEÇÃO 1: PaymentListView
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Document(collection = "payment_list_view")
public class PaymentListView {

    @Id
    private String id;
    private String userId;
    private String userName;
    private BigDecimal amount;
    private String currency;
    private String formattedAmount;
    private String status;
    private LocalDateTime createdAt;

    // ...
}

@Component
public class PaymentListViewProjection {

    private final PaymentListViewRepository repository;

    @KafkaListener(topics = "payment.created.v1")
    public void handlePaymentCreated(PaymentCreatedEvent event) {

        PaymentListView view = new PaymentListView();
        view.setId(event.paymentId().toString());
        view.setUserId(event.userId().toString());
        view.setAmount(event.amount());
        view.setCurrency(event.currency());
        view.setStatus("PENDING");
        view.setCreatedAt(LocalDateTime.ofInstant(
            Instant.ofEpochMilli(event.timestamp()),
            ZoneId.systemDefault()
        ));

        repository.save(view);
    }

    @KafkaListener(topics = "payment.approved.v1")
    public void handlePaymentApproved(PaymentApprovedEvent event) {

        PaymentListView view = repository.findById(event.paymentId().toString())
            .orElseThrow();

        view.setStatus("APPROVED");
        repository.save(view);
    }

    @KafkaListener(topics = "payment.cancelled.v1")
    public void handlePaymentCancelled(PaymentCancelledEvent event) {

        PaymentListView view = repository.findById(event.paymentId().toString())
            .orElseThrow();

        view.setStatus("CANCELLED");
        repository.save(view);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      PROJEÇÃO 2: PaymentStatsView
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Document(collection = "payment_stats_view")
public class PaymentStatsView {

    @Id
    private String id;  // Ex: "2024-01-USD"

    private String month;
    private String currency;
    private BigDecimal totalAmount;
    private long totalCount;
    private long approvedCount;
    private long cancelledCount;
    private long pendingCount;

    // ...
}

@Component
public class PaymentStatsViewProjection {

    private final PaymentStatsViewRepository repository;

    @KafkaListener(topics = "payment.created.v1")
    public void handlePaymentCreated(PaymentCreatedEvent event) {

        String month = extractMonth(event.timestamp());
        String id = month + "-" + event.currency();

        PaymentStatsView stats = repository.findById(id)
            .orElse(new PaymentStatsView(id, month, event.currency()));

        stats.incrementTotal(event.amount());
        stats.incrementPending();

        repository.save(stats);
    }

    @KafkaListener(topics = "payment.approved.v1")
    public void handlePaymentApproved(PaymentApprovedEvent event) {

        // Buscar o Payment original para saber mês e moeda
        // Atualizar estatísticas: pendingCount--, approvedCount++
        // ...
    }
}
```

---

## 8. Versionamento de Eventos

### O Problema

```
EVOLUÇÃO DE EVENTOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Hoje (V1):
  PaymentCreatedEvent {
    paymentId: UUID,
    userId: UUID,
    amount: BigDecimal,
    currency: String
  }

Amanhã (V2): Precisa adicionar campo "merchantId"
  PaymentCreatedEvent {
    paymentId: UUID,
    userId: UUID,
    amount: BigDecimal,
    currency: String,
    merchantId: UUID  ← NOVO!
  }

PROBLEMA:
  Event Store tem eventos V1 (sem merchantId)
  Código novo espera V2 (com merchantId)
  ❌ Quebra ao fazer replay!
```

### Solução 1: Upcasting (Conversão)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENTO V1 (antigo)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public record PaymentCreatedEventV1(
    String eventId,
    UUID paymentId,
    UUID userId,
    BigDecimal amount,
    String currency,
    long timestamp,
    int version
) implements PaymentEvent {}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENTO V2 (novo)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public record PaymentCreatedEventV2(
    String eventId,
    UUID paymentId,
    UUID userId,
    BigDecimal amount,
    String currency,
    UUID merchantId,  // ← NOVO campo
    long timestamp,
    int version
) implements PaymentEvent {}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      UPCASTER (Converte V1 → V2)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentCreatedEventUpcaster {

    /**
     * Converter V1 → V2 (adiciona merchantId default).
     */
    public static PaymentCreatedEventV2 upcast(PaymentCreatedEventV1 v1) {

        return new PaymentCreatedEventV2(
            v1.eventId(),
            v1.paymentId(),
            v1.userId(),
            v1.amount(),
            v1.currency(),
            getDefaultMerchantId(),  // ← merchantId default para eventos antigos
            v1.timestamp(),
            v1.version()
        );
    }

    private static UUID getDefaultMerchantId() {
        // Retornar ID default (ex: merchant padrão da época)
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT STORE (com upcasting)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class EventStore {

    private PaymentEvent deserializeEvent(EventStoreEntry entry) {

        String eventType = entry.getEventType();

        // Identificar versão do evento
        if ("PaymentCreatedEventV1".equals(eventType)) {
            // Deserializar V1
            PaymentCreatedEventV1 v1 = deserializeV1(entry);

            // Upcast para V2
            return PaymentCreatedEventUpcaster.upcast(v1);

        } else if ("PaymentCreatedEventV2".equals(eventType)) {
            // Deserializar V2 diretamente
            return deserializeV2(entry);

        } else {
            throw new UnsupportedEventVersionException(eventType);
        }
    }
}
```

### Solução 2: Campos Opcionais

```java
// ✅ SOLUÇÃO 2: Campos OPCIONAIS (mais simples)

public record PaymentCreatedEvent(
    String eventId,
    UUID paymentId,
    UUID userId,
    BigDecimal amount,
    String currency,
    Optional<UUID> merchantId,  // ← OPCIONAL (pode ser vazio)
    long timestamp,
    int version
) implements PaymentEvent {

    /**
     * Factory para eventos antigos (sem merchantId).
     */
    public static PaymentCreatedEvent withoutMerchant(
            UUID paymentId, UUID userId,
            BigDecimal amount, String currency) {

        return new PaymentCreatedEvent(
            UUID.randomUUID().toString(),
            paymentId,
            userId,
            amount,
            currency,
            Optional.empty(),  // ← Sem merchantId
            Instant.now().toEpochMilli(),
            1
        );
    }

    /**
     * Factory para eventos novos (com merchantId).
     */
    public static PaymentCreatedEvent withMerchant(
            UUID paymentId, UUID userId,
            BigDecimal amount, String currency,
            UUID merchantId) {

        return new PaymentCreatedEvent(
            UUID.randomUUID().toString(),
            paymentId,
            userId,
            amount,
            currency,
            Optional.of(merchantId),  // ← Com merchantId
            Instant.now().toEpochMilli(),
            1
        );
    }
}

// Código que usa o evento:
payment.apply(event);

if (event.merchantId().isPresent()) {
    // Evento novo (V2)
    this.merchantId = event.merchantId().get();
} else {
    // Evento antigo (V1) - usa default
    this.merchantId = getDefaultMerchantId();
}
```

---

## 9. Event Sourcing + CQRS

### A Combinação Perfeita

```
EVENT SOURCING + CQRS = Arquitetura Ideal
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

WRITE SIDE:
  • Event Sourcing (salva eventos)
  • Command Handlers (carregam + salvam eventos)
  • Event Store (banco de eventos append-only)

READ SIDE:
  • CQRS (queries em Read Model)
  • Projeções (construídas a partir de eventos)
  • Read Model (desnormalizado, rápido)

SINCRONIZAÇÃO:
  • Eventos do Event Store → Kafka
  • Event Handlers → Atualizam Read Model


┌─────────────────────────────────────────────────────────────┐
│                    CLIENT                                   │
└──────────┬──────────────────────────────────────┬───────────┘
           │                                      │
           ▼                                      ▼
┌──────────────────────────┐      ┌──────────────────────────┐
│   WRITE SIDE             │      │   READ SIDE              │
│   (Event Sourcing)       │      │   (CQRS)                 │
├──────────────────────────┤      ├──────────────────────────┤
│                          │      │                          │
│  Command Controller      │      │  Query Controller        │
│         ↓                │      │         ↓                │
│  Command Handler         │      │  Query Handler           │
│         ↓                │      │         ↓                │
│  Aggregate (Payment)     │      │  Read Repository         │
│  - load from events      │      │                          │
│  - apply logic           │      │  ✅ Desnormalizado       │
│  - generate new event    │      │  ✅ Rápido               │
│         ↓                │      │  ✅ Cacheable            │
│  Event Store             │      │                          │
│  (append-only)           │      │                          │
│                          │      │                          │
└──────────┬───────────────┘      └────────────┬─────────────┘
           │                                   │
           ▼                                   │
┌─────────────────────────────────────────────┴───────────────┐
│                    KAFKA (Events)                           │
└──────────┬──────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│         EVENT HANDLER (Projections)                         │
│         - Consome eventos                                   │
│         - Atualiza Read Model                               │
└─────────────────────────────────────────────────────────────┘


BENEFÍCIOS DA COMBINAÇÃO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Histórico completo (Event Sourcing)
✅ Queries rápidas (CQRS Read Model)
✅ Escalabilidade (Write e Read separados)
✅ Auditoria grátis (eventos imutáveis)
✅ Múltiplas views (projeções)
✅ Time travel (replay eventos)
✅ Debugabilidade total
```

---

## 10. Testando Event Sourcing

### Teste 1: Aggregate (Comportamento)

```java
// ✅ TESTE DECLARATIVO (Given-When-Then com eventos)

class PaymentTest {

    @Test
    void shouldApprovePayment() {
        // Given: Payment criado e pendente
        PaymentCreatedEvent created = new PaymentCreatedEvent(
            "evt-1",
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD",
            Instant.now().toEpochMilli(),
            1
        );

        Payment payment = Payment.fromEvents(List.of(created));

        // When: Aprovar
        UUID approvedBy = UUID.randomUUID();
        payment.approve(approvedBy, "Verified");

        // Then: Evento gerado
        List<PaymentEvent> events = payment.getPendingEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PaymentApprovedEvent.class);

        PaymentApprovedEvent approved = (PaymentApprovedEvent) events.get(0);
        assertThat(approved.approvedBy()).isEqualTo(approvedBy);
        assertThat(approved.approvalReason()).isEqualTo("Verified");
    }

    @Test
    void shouldNotApproveCancelledPayment() {
        // Given: Payment cancelado
        PaymentCreatedEvent created = new PaymentCreatedEvent(...);
        PaymentCancelledEvent cancelled = new PaymentCancelledEvent(...);

        Payment payment = Payment.fromEvents(List.of(created, cancelled));

        // When/Then: Aprovar deve falhar
        assertThatThrownBy(() -> payment.approve(UUID.randomUUID(), "reason"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot approve cancelled payment");
    }
}
```

### Teste 2: Event Store

```java
@SpringBootTest
@Transactional
class EventStoreTest {

    @Autowired
    private EventStore eventStore;

    @Test
    void shouldSaveAndRetrieveEvents() {
        // Given: Eventos
        UUID paymentId = UUID.randomUUID();

        PaymentCreatedEvent event1 = new PaymentCreatedEvent(
            "evt-1", paymentId, UUID.randomUUID(),
            new BigDecimal("100.00"), "USD",
            Instant.now().toEpochMilli(), 1
        );

        PaymentApprovedEvent event2 = new PaymentApprovedEvent(
            "evt-2", paymentId, UUID.randomUUID(), "Verified",
            Instant.now().toEpochMilli(), 2
        );

        // When: Salvar
        eventStore.save(event1);
        eventStore.save(event2);

        // Then: Buscar
        List<PaymentEvent> events = eventStore.getEvents(paymentId);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(PaymentCreatedEvent.class);
        assertThat(events.get(1)).isInstanceOf(PaymentApprovedEvent.class);
    }

    @Test
    void shouldThrowExceptionOnConcurrentModification() {
        // Given: Mesmo payment, mesma versão
        UUID paymentId = UUID.randomUUID();

        PaymentApprovedEvent event1 = new PaymentApprovedEvent(
            "evt-1", paymentId, UUID.randomUUID(), "reason1",
            Instant.now().toEpochMilli(), 2
        );

        PaymentApprovedEvent event2 = new PaymentApprovedEvent(
            "evt-2", paymentId, UUID.randomUUID(), "reason2",
            Instant.now().toEpochMilli(), 2  // ← Mesma versão!
        );

        // When/Then
        eventStore.save(event1);

        assertThatThrownBy(() -> eventStore.save(event2))
            .isInstanceOf(ConcurrencyException.class);
    }
}
```

### Teste 3: Command Handler (Integration)

```java
@SpringBootTest
@Transactional
class ApprovePaymentCommandHandlerTest {

    @Autowired
    private ApprovePaymentCommandHandler handler;

    @Autowired
    private EventStore eventStore;

    @Test
    void shouldApprovePaymentAndSaveEvent() {
        // Given: Payment criado
        UUID paymentId = UUID.randomUUID();

        PaymentCreatedEvent created = new PaymentCreatedEvent(
            "evt-1", paymentId, UUID.randomUUID(),
            new BigDecimal("100.00"), "USD",
            Instant.now().toEpochMilli(), 1
        );

        eventStore.save(created);

        // When: Aprovar
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            paymentId,
            UUID.randomUUID(),
            "Verified payment"
        );

        handler.handle(command);

        // Then: Evento salvo
        List<PaymentEvent> events = eventStore.getEvents(paymentId);

        assertThat(events).hasSize(2);
        assertThat(events.get(1)).isInstanceOf(PaymentApprovedEvent.class);
    }
}
```

---

## 11. Cenários do Dia a Dia

### Cenário 1: Auditoria Completa

**Situação:**
Auditor pergunta: "Quem aprovou o pagamento PAY-123 e quando?"

**Sem Event Sourcing:**
```
❌ Banco tem: status = APPROVED
❌ NÃO sabe quem aprovou
❌ NÃO sabe quando aprovou
❌ Precisa confiar em logs (podem estar incompletos)
```

**Com Event Sourcing:**
```sql
SELECT * FROM event_store
WHERE aggregate_id = 'PAY-123'
ORDER BY version;

┌────────────────────────────────────────────────────────────┐
│ version │ event_type           │ event_data                │
├────────────────────────────────────────────────────────────┤
│ 1       │ PaymentCreatedEvent  │ {...}                     │
│ 2       │ PaymentApprovedEvent │ {                         │
│         │                      │   "approvedBy": "adm-456",│
│         │                      │   "timestamp": 1704879900,│
│         │                      │   "reason": "Verified"    │
│         │                      │ }                         │
└────────────────────────────────────────────────────────────┘

✅ RESPOSTA EXATA:
   Aprovado por: admin-456
   Em: 2024-01-10 10:05:00
   Razão: "Verified payment"
```

---

### Cenário 2: Debug de Bug

**Situação:**
Bug: Payment está CANCELLED mas deveria estar APPROVED.

**Sem Event Sourcing:**
```
❌ Estado atual: CANCELLED
❌ NÃO sabe como chegou nesse estado
❌ Impossível reproduzir o bug
❌ Precisa adivinhar o que aconteceu
```

**Com Event Sourcing:**
```java
// 1. Buscar eventos
List<PaymentEvent> events = eventStore.getEvents("PAY-123");

// 2. Replay eventos (passo a passo)
for (PaymentEvent event : events) {
    System.out.println(event);
}

/*
Output:
  1. PaymentCreatedEvent (status → PENDING)
  2. PaymentApprovedEvent (status → APPROVED)  ← BUG: Por que foi aprovado?
  3. PaymentCancelledEvent (status → CANCELLED)

  BUG IDENTIFICADO:
    Evento 2 (PaymentApprovedEvent) foi criado INCORRETAMENTE.
    Razão: Validação de crédito não foi executada.

  FIX: Adicionar validação de crédito antes de aprovar.
*/

✅ Bug reproduzido com precisão cirúrgica
✅ Causa raiz identificada
✅ Fix implementado
```

---

### Cenário 3: Relatório Histórico

**Situação:**
Quer saber: "Quantos payments estavam PENDING em janeiro/2024?"

**Sem Event Sourcing:**
```
❌ Banco só tem estado ATUAL
❌ IMPOSSÍVEL responder
❌ Não tem dados históricos
```

**Com Event Sourcing:**
```java
// Replay eventos até janeiro/2024
Instant cutoffDate = Instant.parse("2024-01-31T23:59:59Z");

List<Payment> payments = new ArrayList<>();

for (UUID paymentId : allPaymentIds) {
    // Buscar eventos até cutoff date
    List<PaymentEvent> events = eventStore.getEventsUntil(paymentId, cutoffDate);

    // Reconstruir estado naquela data
    Payment payment = Payment.fromEvents(events);

    if (payment.getStatus() == PaymentStatus.PENDING) {
        payments.add(payment);
    }
}

System.out.println("Payments PENDING em janeiro/2024: " + payments.size());

✅ Resposta PRECISA
✅ Baseada em dados históricos reais
✅ Time travel!
```

---

## 12. Armadilhas Comuns

### Armadilha 1: Eventos Grandes Demais

```java
// ❌ ERRADO - Evento com dados desnecessários

public record PaymentCreatedEvent(
    String eventId,
    UUID paymentId,
    UUID userId,
    BigDecimal amount,
    String currency,

    // ❌ Dados desnecessários!
    User user,                  // ← Objeto completo (nome, email, etc)
    List<PaymentItem> items,    // ← Lista de itens (pode ser enorme)
    byte[] invoicePdf           // ← PDF de 5MB!
) implements PaymentEvent {}

PROBLEMAS:
  ❌ Evento muito grande (MBs)
  ❌ Lento para serializar/deserializar
  ❌ Event Store enorme
  ❌ Replay lento


// ✅ CORRETO - Evento com dados mínimos

public record PaymentCreatedEvent(
    String eventId,
    UUID paymentId,
    UUID userId,          // ← SÓ ID (não objeto completo)
    BigDecimal amount,
    String currency
) implements PaymentEvent {}

BENEFÍCIOS:
  ✅ Evento pequeno (KBs)
  ✅ Rápido para serializar
  ✅ Event Store compacto
  ✅ Replay rápido
```

---

### Armadilha 2: Modificar Eventos Antigos

```java
// ❌ ERRADO - Modificar evento que já foi salvo

// Event Store:
//   version 1: PaymentCreatedEvent (amount: 100)
//   version 2: PaymentApprovedEvent

// ERRO: Mudar evento version 1 (amount: 100 → 200)
eventStore.update(paymentId, 1, newEventData);  // ❌ NUNCA FAZER ISSO!

PROBLEMAS:
  ❌ Violação do Event Sourcing (eventos são IMUTÁVEIS)
  ❌ Histórico corrompido
  ❌ Replay gera estado errado
  ❌ Auditoria inválida


// ✅ CORRETO - Criar NOVO evento (correção)

// Event Store:
//   version 1: PaymentCreatedEvent (amount: 100)
//   version 2: PaymentApprovedEvent
//   version 3: PaymentAmountCorrectedEvent (newAmount: 200)  ← NOVO evento

// Replay:
//   v1: amount = 100
//   v2: status = APPROVED
//   v3: amount = 200  ← Corrigido

✅ Histórico preservado
✅ Auditoria mostra CORREÇÃO
✅ Eventos imutáveis
```

---

### Armadilha 3: Não Usar Snapshots

```java
// ❌ ERRADO - Replay de 100.000 eventos (LENTO)

Payment payment = Payment.fromEvents(eventStore.getEvents(paymentId));
// ❌ Demora minutos!


// ✅ CORRETO - Usa snapshot

Optional<Payment> snapshot = snapshotService.loadSnapshot(paymentId);

if (snapshot.isPresent()) {
    // Carregar snapshot (v50000) + eventos após (v50001-v100000)
    Payment payment = snapshot.get();
    List<PaymentEvent> eventsAfter = eventStore.getEventsAfter(paymentId, 50000);
    eventsAfter.forEach(payment::apply);
} else {
    // Sem snapshot: replay completo
    Payment payment = Payment.fromEvents(eventStore.getEvents(paymentId));
}

✅ Replay de 50.000 eventos (não 100.000)
✅ 2x mais rápido
```

---

## 13. Checklist Event Sourcing

### ☐ ANTES DE IMPLEMENTAR

#### Entendimento
- [ ] Entendeu a diferença entre State-Based e Event-Based?
- [ ] Sabe quando usar Event Sourcing?
- [ ] Entende Replay de eventos?
- [ ] Conhece Snapshots (otimização)?

#### Arquitetura
- [ ] Definiu Aggregates (Payment, Order, etc)?
- [ ] Definiu Eventos (Created, Approved, Cancelled)?
- [ ] Escolheu banco para Event Store (PostgreSQL)?
- [ ] Planejou Snapshots (a cada N eventos)?

---

### ☐ IMPLEMENTAÇÃO

#### Eventos
- [ ] Criou eventos imutáveis (records)?
- [ ] Eventos têm version (optimistic locking)?
- [ ] Eventos têm timestamp?
- [ ] Eventos têm aggregateId?

#### Event Store
- [ ] Criou tabela event_store?
- [ ] Constraint UNIQUE (aggregate_id, version)?
- [ ] Índice (aggregate_id, version)?
- [ ] EventStoreRepository implementado?

#### Aggregate
- [ ] Método fromEvents() (replay)?
- [ ] Método apply() (muda estado)?
- [ ] Comandos geram eventos (não mudam estado direto)?
- [ ] pendingEvents lista (eventos a salvar)?

#### Command Handlers
- [ ] Carrega eventos (Event Store)?
- [ ] Reconstrói Aggregate (replay)?
- [ ] Executa comando (gera evento)?
- [ ] Salva novo evento (append-only)?

#### Snapshots
- [ ] SnapshotService implementado?
- [ ] Cria snapshot a cada N eventos?
- [ ] Event Store usa snapshot (se disponível)?

---

### ☐ TESTES

- [ ] Testou Aggregate.fromEvents() (replay)?
- [ ] Testou comandos (geram eventos corretos)?
- [ ] Testou Event Store (salva e busca eventos)?
- [ ] Testou concorrência (version conflict)?
- [ ] Testou Snapshots (performance)?

---

### ☐ PRODUÇÃO

#### Performance
- [ ] Snapshots configurados?
- [ ] Índices criados (aggregate_id, version)?
- [ ] Paginação no replay (streaming)?

#### Versionamento
- [ ] Estratégia para evoluir eventos?
- [ ] Upcasting implementado (se necessário)?
- [ ] Campos opcionais (se aplicável)?

#### Monitoramento
- [ ] Métrica de tamanho do Event Store?
- [ ] Alerta se replay > threshold?
- [ ] Monitoramento de Snapshots?

---

## 14. Exercícios Práticos

### Exercício 1: Identificar Violações

Analise o código e identifique problemas:

```java
@Service
public class PaymentService {

    private final PaymentRepository repository;

    @Transactional
    public void approvePayment(UUID paymentId) {

        // Buscar Payment
        Payment payment = repository.findById(paymentId).orElseThrow();

        // Mudar estado
        payment.setStatus(PaymentStatus.APPROVED);

        // Salvar (UPDATE)
        repository.save(payment);
    }
}
```

<details>
<summary><strong>📝 Resposta</strong></summary>

**Violações:**

1. ❌ **State-Based (não Event-Based)**
   - Salva estado ATUAL (status = APPROVED)
   - NÃO salva evento (PaymentApprovedEvent)
   - Histórico perdido

2. ❌ **UPDATE (não append-only)**
   - UPDATE sobrescreve estado anterior
   - Não sabe estado anterior (PENDING ou outro?)

3. ❌ **Sem auditoria**
   - Não sabe QUEM aprovou
   - Não sabe QUANDO aprovou
   - Não sabe POR QUE aprovou

4. ❌ **Sem versionamento**
   - Concorrência = lost updates
   - Thread 1 e 2 podem sobrescrever mutuamente

**Solução Event Sourcing:**

```java
@Service
public class ApprovePaymentCommandHandler {

    private final EventStore eventStore;

    @Transactional
    public void handle(ApprovePaymentCommand command) {

        // 1. Carregar eventos
        List<PaymentEvent> events = eventStore.getEvents(command.paymentId());

        // 2. Reconstruir Payment (replay)
        Payment payment = Payment.fromEvents(events);

        // 3. Executar comando (gera evento)
        payment.approve(command.approvedBy(), command.reason());

        // 4. Salvar evento (append-only)
        PaymentApprovedEvent event = payment.getPendingEvents().get(0);
        eventStore.save(event);

        // ✅ Evento salvo (imutável)
        // ✅ Histórico completo (quem, quando, por quê)
        // ✅ Versionamento (optimistic locking)
    }
}
```

</details>

---

## 🎯 Conclusão

**Event Sourcing** transforma como você persiste dados!

**O que você aprendeu:**
✅ State-Based vs Event-Based (eventos = source of truth)
✅ Event Store (banco append-only, imutável)
✅ Replay (reconstruir estado aplicando eventos)
✅ Snapshots (otimização para replay rápido)
✅ Projeções (Read Models construídos de eventos)
✅ Versionamento (evoluir eventos sem quebrar)
✅ Event Sourcing + CQRS (combinação perfeita)

**Lembre-se:**

- **Eventos** = Fatos históricos (imutáveis, passado)
- **Event Store** = Append-only (INSERT, nunca UPDATE/DELETE)
- **Replay** = Reconstruir estado aplicando eventos sequencialmente
- **Snapshot** = Foto do estado (otimização)
- **Projeção** = View construída de eventos (Read Model)
- **Versionamento** = Evoluir eventos com upcasting ou campos opcionais

**Regra de Ouro:**
```
NUNCA modifique ou delete eventos!
Eventos são FATOS históricos IMUTÁVEIS!
Para corrigir: crie NOVO evento (compensação)
```

---

**Próximos Passos:**
1. Leia `tutorial-cqrs.md` (complemento natural)
2. Implemente Event Sourcing no seu projeto
3. Configure Snapshots (a cada 100 eventos)
4. Crie Projeções (Read Models)

**Quando usar Event Sourcing:**
✅ Precisa auditoria completa (quem, quando, por quê)
✅ Precisa histórico (time travel)
✅ Precisa debugabilidade total
✅ Integrações via eventos (já existem!)
✅ Análise de negócio (replay histórico)

**Quando NÃO usar Event Sourcing:**
❌ CRUD simples (poucos eventos)
❌ Não precisa auditoria
❌ Performance crítica (leitura)
❌ Equipe pequena (complexidade)

---

**Boa sorte na sua jornada com Event Sourcing! 🚀**