# Tutorial Definitivo: CQRS (Command Query Responsibility Segregation)

---

## 📋 Sumário

1. [O que é CQRS](#1-o-que-é-cqrs)
2. [Por Que Usar CQRS](#2-por-que-usar-cqrs)
3. [Commands vs Queries](#3-commands-vs-queries)
4. [Arquitetura CQRS](#4-arquitetura-cqrs)
5. [Implementação Passo a Passo](#5-implementação-passo-a-passo)
6. [CQRS com Event Sourcing](#6-cqrs-com-event-sourcing)
7. [Consistência Eventual](#7-consistência-eventual)
8. [Testando CQRS](#8-testando-cqrs)
9. [Cenários do Dia a Dia](#9-cenários-do-dia-a-dia)
10. [Armadilhas Comuns](#10-armadilhas-comuns)
11. [Checklist CQRS](#11-checklist-cqrs)
12. [Exercícios Práticos](#12-exercícios-práticos)

---

## 1. O que é CQRS

### Definição em 30 Segundos

**CQRS** (Command Query Responsibility Segregation) separa **operações de escrita** (Commands) de **operações de leitura** (Queries) usando **modelos diferentes**.

```
TRADICIONAL (CRUD):
  Mesmo modelo para leitura e escrita
  ┌──────────────┐
  │              │
  │   Payment    │  ← GET, POST, PUT, DELETE
  │   (Entity)   │
  │              │
  └──────────────┘


CQRS:
  Modelos SEPARADOS para escrita e leitura

  ESCRITA (Commands)              LEITURA (Queries)
  ┌──────────────┐               ┌──────────────┐
  │              │               │              │
  │   Payment    │               │ PaymentView  │
  │   (Write)    │  ─────────→  │   (Read)     │
  │              │   eventos     │              │
  └──────────────┘               └──────────────┘

  ✅ Modelos otimizados para cada necessidade
  ✅ Escala de escrita ≠ escala de leitura
  ✅ Banco de escrita ≠ banco de leitura
```

**Conceitos-chave:**

- **Command** = Operação que **muda estado** (CREATE, UPDATE, DELETE)
- **Query** = Operação que **retorna dados** sem alterar estado (READ)
- **Write Model** = Modelo otimizado para escrita (normalizando, validações)
- **Read Model** = Modelo otimizado para leitura (desnormalizado, rápido)
- **Eventual Consistency** = Write Model e Read Model sincronizam via eventos

**Em português claro:**

Ao invés de usar a mesma entidade `Payment` para salvar E consultar dados, você cria:
- Um modelo `PaymentCommand` para salvar/alterar
- Um modelo `PaymentQuery` para consultar

Eles são sincronizados via eventos (Kafka, por exemplo).

---

## 2. Por Que Usar CQRS

### Problema: Arquitetura CRUD Tradicional

```java
// ❌ CRUD TRADICIONAL - Um modelo para tudo

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;

    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;

    @OneToMany(mappedBy = "payment")
    private List<PaymentItem> items;  // ← Relacionamento pesado

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;  // ← JOIN custoso

    // ... getters/setters
}

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // ❌ Query LENTA: JOINs múltiplos
    @Query("SELECT p FROM Payment p " +
           "JOIN FETCH p.user " +
           "JOIN FETCH p.items " +
           "WHERE p.userId = :userId")
    List<Payment> findByUserIdWithDetails(UUID userId);
}

@RestController
public class PaymentController {

    @GetMapping("/api/payments/{userId}")
    public List<PaymentResponse> getUserPayments(@PathVariable UUID userId) {
        // ❌ PROBLEMA 1: Query pesada (JOINs)
        // ❌ PROBLEMA 2: Retorna dados que API não usa
        // ❌ PROBLEMA 3: Não pode cachear (sempre vai no banco)
        return paymentRepository.findByUserIdWithDetails(userId)
            .stream()
            .map(PaymentResponse::from)
            .collect(Collectors.toList());
    }

    @PostMapping("/api/payments")
    public PaymentResponse createPayment(@RequestBody CreatePaymentRequest request) {
        // ❌ PROBLEMA 4: Mesma entity para escrever
        // ❌ PROBLEMA 5: Validações misturadas com persistência
        Payment payment = new Payment();
        payment.setAmount(request.amount());
        // ...
        return PaymentResponse.from(paymentRepository.save(payment));
    }
}

PROBLEMAS REAIS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ PERFORMANCE DE LEITURA
   - Queries fazem JOINs pesados
   - N+1 queries (lazy loading)
   - Não pode desnormalizar (precisa normalizar para escrita)

2. ❌ IMPEDÂNCIA DE ESCRITA
   - Entity complexa (validações, relacionamentos)
   - Salvar = atualizar múltiplas tabelas (lento)

3. ❌ ESCALABILIDADE LIMITADA
   - Leitura e escrita no MESMO banco
   - Não pode escalar separadamente
   - Leitura (90% do tráfego) trava escrita

4. ❌ IMPOSSÍVEL OTIMIZAR PARA AMBOS
   - Normalização boa para escrita, ruim para leitura
   - Desnormalização boa para leitura, ruim para escrita
   - CONFLITO IRRECONCILIÁVEL!

5. ❌ CACHE DIFÍCIL
   - Entity muda frequentemente (escrita)
   - Cache invalida constantemente
   - Queries complexas = cache ineficaz
```

---

### Solução: CQRS

```java
// ✅ CQRS - Modelos SEPARADOS

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      WRITE MODEL (Comandos)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// Modelo otimizado para ESCRITA (normalizado, validações)
@Entity
@Table(name = "payment")
public class PaymentWriteModel {

    @Id
    private UUID id;

    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private LocalDateTime createdAt;

    // ✅ SEM relacionamentos (escrita rápida)
    // ✅ Validações de negócio no Domain
    // ✅ Normalizado (uma tabela)
}

// Command: intenção de mudar estado
public record CreatePaymentCommand(
    UUID userId,
    BigDecimal amount,
    String currency
) {}

// Command Handler: executa o comando
@Service
public class CreatePaymentCommandHandler {

    private final PaymentWriteRepository writeRepository;
    private final EventPublisher eventPublisher;

    @Transactional
    public PaymentCreatedEvent handle(CreatePaymentCommand command) {

        // 1. Validar
        validateCommand(command);

        // 2. Criar aggregate (Domain Model)
        Payment payment = Payment.create(
            command.userId(),
            command.amount(),
            command.currency()
        );

        // 3. Salvar (Write Model)
        PaymentWriteModel writeModel = toWriteModel(payment);
        writeRepository.save(writeModel);

        // 4. Publicar evento (para sincronizar Read Model)
        PaymentCreatedEvent event = PaymentCreatedEvent.from(payment);
        eventPublisher.publish(event);

        return event;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      READ MODEL (Queries)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// Modelo otimizado para LEITURA (desnormalizado, rápido)
@Document(collection = "payment_views")  // ← MongoDB para leitura!
public class PaymentReadModel {

    @Id
    private String id;

    // ✅ DESNORMALIZADO: todos dados em um documento
    private String userId;
    private String userName;        // ← Desnormalizado!
    private String userEmail;       // ← Desnormalizado!
    private BigDecimal amount;
    private String currency;
    private String status;

    // ✅ Dados pré-computados
    private String formattedAmount;  // ← "R$ 100,00" (já formatado)
    private String statusLabel;      // ← "Aprovado" (traduzido)

    private LocalDateTime createdAt;

    // ✅ Zero JOINs (tudo em um documento)
    // ✅ Query SUPER rápida
}

// Query: intenção de buscar dados
public record GetUserPaymentsQuery(
    UUID userId,
    int page,
    int size
) {}

// Query Handler: executa a consulta
@Service
public class GetUserPaymentsQueryHandler {

    private final PaymentReadRepository readRepository;

    public Page<PaymentReadModel> handle(GetUserPaymentsQuery query) {

        // ✅ Query RÁPIDA (sem JOINs, desnormalizado)
        // ✅ Pode cachear facilmente
        // ✅ Pode usar banco otimizado para leitura (MongoDB, Elasticsearch)

        Pageable pageable = PageRequest.of(query.page(), query.size());
        return readRepository.findByUserId(query.userId(), pageable);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      SINCRONIZAÇÃO (Event Handler)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// Escuta eventos e atualiza Read Model
@Component
public class PaymentEventHandler {

    private final PaymentReadRepository readRepository;
    private final UserService userService;

    @KafkaListener(topics = "payment.created.v1")
    public void handlePaymentCreated(PaymentCreatedEvent event) {

        // 1. Buscar dados complementares (User)
        User user = userService.findById(event.userId());

        // 2. Criar Read Model DESNORMALIZADO
        PaymentReadModel readModel = new PaymentReadModel();
        readModel.setId(event.paymentId().toString());
        readModel.setUserId(event.userId().toString());
        readModel.setUserName(user.getName());        // ← Desnormaliza
        readModel.setUserEmail(user.getEmail());      // ← Desnormaliza
        readModel.setAmount(event.amount());
        readModel.setCurrency(event.currency());
        readModel.setStatus(event.status());
        readModel.setFormattedAmount(formatAmount(event.amount()));  // ← Pré-computa
        readModel.setStatusLabel(translateStatus(event.status()));   // ← Pré-computa
        readModel.setCreatedAt(event.createdAt());

        // 3. Salvar no banco de leitura
        readRepository.save(readModel);
    }
}

BENEFÍCIOS REAIS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ PERFORMANCE DE LEITURA 10x-100x MAIS RÁPIDA
   ├─ Dados desnormalizados (sem JOINs)
   ├─ Dados pré-computados (formatações, traduções)
   ├─ Banco otimizado para leitura (MongoDB, Elasticsearch)
   └─ Cache agressivo (Read Model muda menos)

2. ✅ ESCRITA SIMPLES E RÁPIDA
   ├─ Entity normalizada (uma tabela)
   ├─ Sem relacionamentos complexos
   ├─ Validações isoladas (Domain)
   └─ Escrita não afeta leitura

3. ✅ ESCALABILIDADE INDEPENDENTE
   ├─ Escala banco de leitura separadamente (90% do tráfego)
   ├─ Escala banco de escrita separadamente (10% do tráfego)
   ├─ Réplicas de leitura SEM afetar escrita
   └─ Diferentes bancos (PostgreSQL write, MongoDB read)

4. ✅ OTIMIZAÇÃO ESPECÍFICA
   ├─ Write: normalizado, ACID, validações fortes
   ├─ Read: desnormalizado, eventual consistency, cache agressivo
   └─ Escolhe melhor ferramenta para cada lado

5. ✅ MODELOS INDEPENDENTES
   ├─ Write Model evolui sem quebrar queries
   ├─ Read Model evolui sem quebrar comandos
   ├─ Múltiplos Read Models (mobile, web, admin)
   └─ API versionada facilmente
```

---

### Comparação: CRUD vs CQRS

| Aspecto | CRUD Tradicional | CQRS |
|---------|------------------|------|
| **Modelo** | ❌ Um para tudo | ✅ Separado (Write + Read) |
| **Performance Leitura** | ❌ Lenta (JOINs) | ✅ Rápida (desnormalizado) |
| **Performance Escrita** | ⚠️ Média | ✅ Rápida (normalizado) |
| **Escalabilidade** | ❌ Acoplada (mesmo banco) | ✅ Independente (bancos separados) |
| **Cache** | ❌ Difícil | ✅ Fácil (Read Model estável) |
| **Complexidade** | ✅ Simples | ⚠️ Maior (sincronização) |
| **Consistência** | ✅ Forte (ACID) | ⚠️ Eventual |
| **Múltiplas Views** | ❌ Difícil | ✅ Fácil (múltiplos Read Models) |

---

## 3. Commands vs Queries

### Diferenças Fundamentais

```
┌────────────────────────────────────────────────────────────┐
│                    COMMANDS                                │
├────────────────────────────────────────────────────────────┤
│ ✅ Representam INTENÇÃO de mudar estado                    │
│ ✅ Verbos no IMPERATIVO (Create, Update, Delete)           │
│ ✅ Podem FALHAR (validações, regras de negócio)            │
│ ✅ Retornam SUCCESS/FAILURE (não dados)                    │
│ ✅ Geram EVENTOS (para sincronizar Read Model)             │
│ ✅ Modificam Write Model                                   │
│                                                            │
│ EXEMPLOS:                                                  │
│   • CreatePaymentCommand                                   │
│   • ApprovePaymentCommand                                  │
│   • CancelPaymentCommand                                   │
│   • RefundPaymentCommand                                   │
└────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────┐
│                     QUERIES                                │
├────────────────────────────────────────────────────────────┤
│ ✅ Representam INTENÇÃO de buscar dados                    │
│ ✅ Verbos no INFINITIVO (Get, Find, List)                  │
│ ✅ NUNCA falham (no máximo retornam vazio)                 │
│ ✅ Retornam DADOS (DTOs, View Models)                      │
│ ✅ NÃO geram eventos                                       │
│ ✅ Consultam Read Model                                    │
│ ✅ NÃO alteram estado (idempotentes)                       │
│                                                            │
│ EXEMPLOS:                                                  │
│   • GetPaymentByIdQuery                                    │
│   • GetUserPaymentsQuery                                   │
│   • SearchPaymentsQuery                                    │
│   • GetPaymentStatisticsQuery                              │
└────────────────────────────────────────────────────────────┘
```

### Anatomia de um Command

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      1. COMMAND (DTO imutável)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Command: representa a INTENÇÃO de criar um pagamento.
 *
 * CARACTERÍSTICAS:
 * - Imutável (record)
 * - Validações básicas (Bean Validation)
 * - SEM lógica de negócio (só dados)
 */
public record CreatePaymentCommand(

    @NotNull(message = "User ID is required")
    UUID userId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (ISO 4217)")
    String currency

) {
    // ✅ Factory method (opcional)
    public static CreatePaymentCommand of(UUID userId, BigDecimal amount, String currency) {
        return new CreatePaymentCommand(userId, amount, currency);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      2. COMMAND HANDLER (Lógica de execução)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Command Handler: executa o comando.
 *
 * RESPONSABILIDADES:
 * - Validar comando (regras de negócio)
 * - Criar/modificar Domain Model
 * - Persistir no Write Model
 * - Publicar eventos (para Read Model)
 */
@Service
public class CreatePaymentCommandHandler {

    private final PaymentWriteRepository writeRepository;
    private final EventPublisher eventPublisher;

    public CreatePaymentCommandHandler(
            PaymentWriteRepository writeRepository,
            EventPublisher eventPublisher
    ) {
        this.writeRepository = writeRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executar comando.
     *
     * @param command Comando a executar
     * @return Evento gerado (sucesso) ou Exception (falha)
     */
    @Transactional
    public PaymentCreatedEvent handle(CreatePaymentCommand command) {

        // 1. Validações de negócio
        validateBusinessRules(command);

        // 2. Criar Domain Model
        Payment payment = Payment.create(
            PaymentId.generate(),
            UserId.of(command.userId()),
            Money.of(command.amount(), command.currency())
        );

        // 3. Persistir no Write Model (banco de escrita)
        PaymentWriteModel writeModel = PaymentWriteModel.from(payment);
        writeRepository.save(writeModel);

        // 4. Criar evento
        PaymentCreatedEvent event = PaymentCreatedEvent.from(payment);

        // 5. Publicar evento (Kafka) para sincronizar Read Model
        eventPublisher.publish("payment.created.v1", event);

        // 6. Retornar evento (sucesso)
        return event;
    }

    private void validateBusinessRules(CreatePaymentCommand command) {
        // Exemplo: verificar se usuário pode criar pagamento
        // Exemplo: verificar limites de crédito
        // Exemplo: verificar se moeda é suportada
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      3. EVENTO (Resultado do comando)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Evento: algo que ACONTECEU (passado).
 *
 * CARACTERÍSTICAS:
 * - Imutável
 * - Verbos no PASSADO (Created, Approved, Cancelled)
 * - Carrega dados para sincronizar Read Model
 */
public record PaymentCreatedEvent(
    String eventId,
    UUID paymentId,
    UUID userId,
    BigDecimal amount,
    String currency,
    String status,
    long timestamp
) {
    public static PaymentCreatedEvent from(Payment payment) {
        return new PaymentCreatedEvent(
            UUID.randomUUID().toString(),
            payment.getId().value(),
            payment.getUserId().value(),
            payment.getAmount().value(),
            payment.getCurrency().code(),
            payment.getStatus().name(),
            Instant.now().toEpochMilli()
        );
    }
}
```

### Anatomia de uma Query

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      1. QUERY (DTO imutável)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Query: representa a INTENÇÃO de buscar pagamentos de um usuário.
 *
 * CARACTERÍSTICAS:
 * - Imutável (record)
 * - Parâmetros de filtro/paginação
 * - NÃO muda estado
 */
public record GetUserPaymentsQuery(

    @NotNull
    UUID userId,

    @Min(0)
    int page,

    @Min(1) @Max(100)
    int size,

    // Filtros opcionais
    Optional<PaymentStatus> status,
    Optional<LocalDate> startDate,
    Optional<LocalDate> endDate

) {
    public static GetUserPaymentsQuery of(UUID userId, int page, int size) {
        return new GetUserPaymentsQuery(
            userId,
            page,
            size,
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      2. QUERY HANDLER (Lógica de consulta)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Query Handler: executa a consulta.
 *
 * RESPONSABILIDADES:
 * - Buscar dados do Read Model
 * - Aplicar filtros/paginação
 * - Converter para DTO de resposta
 * - Cachear resultados (opcional)
 */
@Service
public class GetUserPaymentsQueryHandler {

    private final PaymentReadRepository readRepository;
    private final CacheManager cacheManager;

    public GetUserPaymentsQueryHandler(
            PaymentReadRepository readRepository,
            CacheManager cacheManager
    ) {
        this.readRepository = readRepository;
        this.cacheManager = cacheManager;
    }

    /**
     * Executar query.
     *
     * @param query Query a executar
     * @return Página de resultados
     */
    @Cacheable(value = "user-payments", key = "#query.userId + '-' + #query.page")
    public Page<PaymentDto> handle(GetUserPaymentsQuery query) {

        // 1. Criar Pageable
        Pageable pageable = PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

        // 2. Buscar do Read Model (MongoDB, cache, etc)
        Page<PaymentReadModel> readModels = readRepository.findByUserId(
            query.userId(),
            pageable
        );

        // 3. Converter para DTO
        return readModels.map(PaymentDto::from);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      3. DTO (Resposta)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * DTO de resposta (View Model).
 *
 * CARACTERÍSTICAS:
 * - Otimizado para API (campos formatados)
 * - Dados desnormalizados (sem precisar JOINs)
 * - Pode ter múltiplas representações (mobile, web)
 */
public record PaymentDto(
    String id,
    String userId,
    String userName,           // ← Desnormalizado
    String userEmail,          // ← Desnormalizado
    String formattedAmount,    // ← "R$ 100,00" (pré-computado)
    String currency,
    String status,
    String statusLabel,        // ← "Aprovado" (traduzido)
    LocalDateTime createdAt
) {
    public static PaymentDto from(PaymentReadModel readModel) {
        return new PaymentDto(
            readModel.getId(),
            readModel.getUserId(),
            readModel.getUserName(),
            readModel.getUserEmail(),
            readModel.getFormattedAmount(),
            readModel.getCurrency(),
            readModel.getStatus(),
            readModel.getStatusLabel(),
            readModel.getCreatedAt()
        );
    }
}
```

---

## 4. Arquitetura CQRS

### Arquitetura Simplificada (Single Database)

```
┌─────────────────────────────────────────────────────────────┐
│                    CLIENT (Frontend)                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY                              │
└──────────┬─────────────────────────────────────┬────────────┘
           │                                     │
           ▼                                     ▼
┌──────────────────────────┐      ┌──────────────────────────┐
│   WRITE SIDE (Commands)  │      │   READ SIDE (Queries)    │
├──────────────────────────┤      ├──────────────────────────┤
│                          │      │                          │
│  Command Controller      │      │  Query Controller        │
│         ↓                │      │         ↓                │
│  Command Handler         │      │  Query Handler           │
│         ↓                │      │         ↓                │
│  Domain Model            │      │  Read Repository         │
│         ↓                │      │                          │
│  Write Repository        │      │                          │
│         ↓                │      │                          │
│  Event Publisher         │      │                          │
│                          │      │                          │
└──────────┬───────────────┘      └────────────┬─────────────┘
           │                                   │
           │        ┌──────────────────────────┘
           │        │
           ▼        ▼
┌─────────────────────────────────────────────────────────────┐
│                    DATABASE (PostgreSQL)                    │
│                                                             │
│  ┌─────────────────┐          ┌─────────────────┐          │
│  │  payment        │          │  payment_view   │          │
│  │  (Write Table)  │          │  (Read Table)   │          │
│  │                 │          │                 │          │
│  │  - normalized   │          │  - denormalized │          │
│  │  - validations  │          │  - pre-computed │          │
│  └─────────────────┘          └─────────────────┘          │
└─────────────────────────────────────────────────────────────┘
           │
           │ (eventos via triggers ou application)
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│              EVENT HANDLER (atualiza Read Model)            │
└─────────────────────────────────────────────────────────────┘
```

### Arquitetura Avançada (Bancos Separados)

```
┌─────────────────────────────────────────────────────────────┐
│                    CLIENT (Frontend)                        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY                              │
└──────────┬─────────────────────────────────────┬────────────┘
           │                                     │
           ▼                                     ▼
┌──────────────────────────┐      ┌──────────────────────────┐
│   WRITE SIDE (Commands)  │      │   READ SIDE (Queries)    │
├──────────────────────────┤      ├──────────────────────────┤
│                          │      │                          │
│  Command Controller      │      │  Query Controller        │
│         ↓                │      │         ↓                │
│  Command Handler         │      │  Query Handler           │
│         ↓                │      │         ↓                │
│  Domain Model            │      │  Cache (Redis)           │
│         ↓                │      │         ↓                │
│  Write Repository        │      │  Read Repository         │
│         ↓                │      │                          │
│  Outbox Service          │      │                          │
│                          │      │                          │
└──────────┬───────────────┘      └────────────┬─────────────┘
           │                                   │
           ▼                                   ▼
┌─────────────────────────┐      ┌─────────────────────────┐
│  WRITE DATABASE         │      │  READ DATABASE          │
│  (PostgreSQL)           │      │  (MongoDB/Elasticsearch)│
│                         │      │                         │
│  - ACID                 │      │  - Desnormalizado       │
│  - Normalizado          │      │  - Rápido               │
│  - Consistência forte   │      │  - Cache agressivo      │
│  - Validações           │      │  - Réplicas             │
└──────────┬──────────────┘      └────────────┬────────────┘
           │                                   ▲
           ▼                                   │
┌─────────────────────────────────────────────┴───────────────┐
│                    KAFKA (Message Broker)                   │
│                                                             │
│  Topics:                                                    │
│    - payment.created.v1                                     │
│    - payment.approved.v1                                    │
│    - payment.cancelled.v1                                   │
└──────────┬──────────────────────────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────────────────────────┐
│         EVENT HANDLER (Consumer - atualiza Read Model)      │
│                                                             │
│  @KafkaListener                                             │
│  handlePaymentCreated() → atualiza MongoDB                  │
└─────────────────────────────────────────────────────────────┘


FLUXO DE ESCRITA:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. POST /api/payments (Command)
2. Command Handler valida e salva no PostgreSQL
3. Event publicado no Kafka (via Outbox)
4. Event Handler consome e atualiza MongoDB
5. Read Model sincronizado (eventual consistency)


FLUXO DE LEITURA:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. GET /api/payments/{userId} (Query)
2. Query Handler busca no Redis (cache)
3. Se não encontrar, busca no MongoDB
4. Retorna dados desnormalizados (rápido!)
```

---

## 5. Implementação Passo a Passo

### Passo 1: Estrutura de Pastas

```
src/main/java/com/mvbr/store/
│
├── application/
│   ├── command/                      ← COMMANDS (Write Side)
│   │   ├── CreatePaymentCommand.java
│   │   ├── ApprovePaymentCommand.java
│   │   └── CancelPaymentCommand.java
│   │
│   ├── handler/
│   │   ├── command/                  ← COMMAND HANDLERS
│   │   │   ├── CreatePaymentCommandHandler.java
│   │   │   ├── ApprovePaymentCommandHandler.java
│   │   │   └── CancelPaymentCommandHandler.java
│   │   │
│   │   └── query/                    ← QUERY HANDLERS
│   │       ├── GetPaymentByIdQueryHandler.java
│   │       ├── GetUserPaymentsQueryHandler.java
│   │       └── SearchPaymentsQueryHandler.java
│   │
│   └── query/                        ← QUERIES (Read Side)
│       ├── GetPaymentByIdQuery.java
│       ├── GetUserPaymentsQuery.java
│       └── SearchPaymentsQuery.java
│
├── domain/
│   └── model/
│       └── payment/
│           ├── Payment.java          ← Domain Model (Write)
│           ├── PaymentId.java
│           ├── Money.java
│           └── PaymentStatus.java
│
├── infrastructure/
│   ├── write/                        ← WRITE MODEL
│   │   ├── entity/
│   │   │   └── PaymentWriteModel.java
│   │   └── repository/
│   │       └── PaymentWriteRepository.java
│   │
│   ├── read/                         ← READ MODEL
│   │   ├── entity/
│   │   │   └── PaymentReadModel.java
│   │   └── repository/
│   │       └── PaymentReadRepository.java
│   │
│   ├── messaging/
│   │   ├── event/
│   │   │   ├── PaymentCreatedEvent.java
│   │   │   ├── PaymentApprovedEvent.java
│   │   │   └── PaymentCancelledEvent.java
│   │   │
│   │   ├── publisher/
│   │   │   └── EventPublisher.java
│   │   │
│   │   └── consumer/
│   │       └── PaymentEventHandler.java  ← Atualiza Read Model
│   │
│   └── outbox/
│       ├── OutboxEvent.java
│       ├── OutboxService.java
│       └── OutboxPublisher.java
│
└── presentation/
    └── controller/
        ├── PaymentCommandController.java  ← Commands
        └── PaymentQueryController.java    ← Queries
```

---

### Passo 2: Implementar Commands

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMMAND
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.command;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentCommand(

    @NotNull(message = "User ID is required")
    UUID userId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    String currency

) {}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMMAND HANDLER
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.handler.command;

import com.mvbr.store.application.command.CreatePaymentCommand;
import com.mvbr.store.domain.model.payment.*;
import com.mvbr.store.infrastructure.write.entity.PaymentWriteModel;
import com.mvbr.store.infrastructure.write.repository.PaymentWriteRepository;
import com.mvbr.store.infrastructure.messaging.event.PaymentCreatedEvent;
import com.mvbr.store.infrastructure.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CreatePaymentCommandHandler {

    private final PaymentWriteRepository writeRepository;
    private final OutboxService outboxService;

    public CreatePaymentCommandHandler(
            PaymentWriteRepository writeRepository,
            OutboxService outboxService
    ) {
        this.writeRepository = writeRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public PaymentCreatedEvent handle(CreatePaymentCommand command) {

        // 1. Criar Domain Model (validações executam aqui)
        Payment payment = Payment.create(
            PaymentId.generate(),
            UserId.of(command.userId()),
            Money.of(command.amount(), command.currency())
        );

        // 2. Converter Domain → Write Model
        PaymentWriteModel writeModel = new PaymentWriteModel(
            payment.getId().value(),
            payment.getUserId().value(),
            payment.getAmount().value(),
            payment.getCurrency().code(),
            payment.getStatus().name(),
            payment.getCreatedAt()
        );

        // 3. Salvar no banco de ESCRITA (PostgreSQL)
        writeRepository.save(writeModel);

        // 4. Criar evento
        PaymentCreatedEvent event = new PaymentCreatedEvent(
            UUID.randomUUID().toString(),
            payment.getId().value(),
            payment.getUserId().value(),
            payment.getAmount().value(),
            payment.getCurrency().code(),
            payment.getStatus().name(),
            Instant.now().toEpochMilli()
        );

        // 5. Salvar evento no OUTBOX (mesma transação)
        outboxService.save(
            "Payment",
            payment.getId().value().toString(),
            "PaymentCreated",
            event
        );

        // 6. Retornar evento (sucesso)
        return event;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      WRITE MODEL (Entity JPA)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.write.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class PaymentWriteModel {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Construtor padrão (JPA)
    protected PaymentWriteModel() {}

    public PaymentWriteModel(UUID id, UUID userId, BigDecimal amount,
                            String currency, String status, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      WRITE REPOSITORY
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.write.repository;

import com.mvbr.store.infrastructure.write.entity.PaymentWriteModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentWriteRepository extends JpaRepository<PaymentWriteModel, UUID> {
    // Spring Data JPA gera implementação automaticamente
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      CONTROLLER (Commands)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.presentation.controller;

import com.mvbr.store.application.command.CreatePaymentCommand;
import com.mvbr.store.application.handler.command.CreatePaymentCommandHandler;
import com.mvbr.store.infrastructure.messaging.event.PaymentCreatedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/payments")
public class PaymentCommandController {

    private final CreatePaymentCommandHandler createPaymentHandler;

    public PaymentCommandController(CreatePaymentCommandHandler createPaymentHandler) {
        this.createPaymentHandler = createPaymentHandler;
    }

    @PostMapping
    public ResponseEntity<PaymentCreatedEvent> createPayment(
            @Valid @RequestBody CreatePaymentCommand command) {

        // Executar comando
        PaymentCreatedEvent event = createPaymentHandler.handle(command);

        // Retornar evento (sucesso)
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }
}
```

---

### Passo 3: Implementar Queries

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      QUERY
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.query;

import javax.validation.constraints.*;
import java.util.UUID;

public record GetUserPaymentsQuery(

    @NotNull
    UUID userId,

    @Min(0)
    int page,

    @Min(1) @Max(100)
    int size

) {}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      QUERY HANDLER
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.handler.query;

import com.mvbr.store.application.query.GetUserPaymentsQuery;
import com.mvbr.store.infrastructure.read.entity.PaymentReadModel;
import com.mvbr.store.infrastructure.read.repository.PaymentReadRepository;
import com.mvbr.store.presentation.dto.PaymentDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class GetUserPaymentsQueryHandler {

    private final PaymentReadRepository readRepository;

    public GetUserPaymentsQueryHandler(PaymentReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    @Cacheable(value = "user-payments", key = "#query.userId + '-' + #query.page")
    public Page<PaymentDto> handle(GetUserPaymentsQuery query) {

        // 1. Criar Pageable
        Pageable pageable = PageRequest.of(
            query.page(),
            query.size(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );

        // 2. Buscar do Read Model (MongoDB ou tabela desnormalizada)
        Page<PaymentReadModel> readModels = readRepository.findByUserId(
            query.userId().toString(),
            pageable
        );

        // 3. Converter para DTO
        return readModels.map(PaymentDto::from);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      READ MODEL (MongoDB Document)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.read.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "payment_views")
public class PaymentReadModel {

    @Id
    private String id;

    private String userId;
    private String userName;           // ← Desnormalizado
    private String userEmail;          // ← Desnormalizado
    private BigDecimal amount;
    private String currency;
    private String formattedAmount;    // ← "R$ 100,00" (pré-computado)
    private String status;
    private String statusLabel;        // ← "Aprovado" (traduzido)
    private LocalDateTime createdAt;

    // Construtor padrão
    public PaymentReadModel() {}

    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getFormattedAmount() { return formattedAmount; }
    public void setFormattedAmount(String formattedAmount) {
        this.formattedAmount = formattedAmount;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      READ REPOSITORY
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.read.repository;

import com.mvbr.store.infrastructure.read.entity.PaymentReadModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentReadRepository extends MongoRepository<PaymentReadModel, String> {

    Page<PaymentReadModel> findByUserId(String userId, Pageable pageable);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DTO (Response)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.presentation.dto;

import com.mvbr.store.infrastructure.read.entity.PaymentReadModel;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentDto(
    String id,
    String userId,
    String userName,
    String userEmail,
    BigDecimal amount,
    String formattedAmount,
    String currency,
    String status,
    String statusLabel,
    LocalDateTime createdAt
) {
    public static PaymentDto from(PaymentReadModel readModel) {
        return new PaymentDto(
            readModel.getId(),
            readModel.getUserId(),
            readModel.getUserName(),
            readModel.getUserEmail(),
            readModel.getAmount(),
            readModel.getFormattedAmount(),
            readModel.getCurrency(),
            readModel.getStatus(),
            readModel.getStatusLabel(),
            readModel.getCreatedAt()
        );
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      CONTROLLER (Queries)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.presentation.controller;

import com.mvbr.store.application.query.GetUserPaymentsQuery;
import com.mvbr.store.application.handler.query.GetUserPaymentsQueryHandler;
import com.mvbr.store.presentation.dto.PaymentDto;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentQueryController {

    private final GetUserPaymentsQueryHandler getUserPaymentsHandler;

    public PaymentQueryController(GetUserPaymentsQueryHandler getUserPaymentsHandler) {
        this.getUserPaymentsHandler = getUserPaymentsHandler;
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Page<PaymentDto>> getUserPayments(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Criar query
        GetUserPaymentsQuery query = new GetUserPaymentsQuery(userId, page, size);

        // Executar query
        Page<PaymentDto> payments = getUserPaymentsHandler.handle(query);

        // Retornar dados
        return ResponseEntity.ok(payments);
    }
}
```

---

### Passo 4: Sincronizar Read Model (Event Handler)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT HANDLER (Sincroniza Read Model)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.messaging.consumer;

import com.mvbr.store.infrastructure.messaging.event.PaymentCreatedEvent;
import com.mvbr.store.infrastructure.read.entity.PaymentReadModel;
import com.mvbr.store.infrastructure.read.repository.PaymentReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Component
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    private final PaymentReadRepository readRepository;

    public PaymentEventHandler(PaymentReadRepository readRepository) {
        this.readRepository = readRepository;
    }

    @KafkaListener(topics = "payment.created.v1", groupId = "payment-read-model-updater")
    public void handlePaymentCreated(PaymentCreatedEvent event) {

        log.info("Received PaymentCreatedEvent: {}", event.paymentId());

        try {
            // 1. Criar Read Model DESNORMALIZADO
            PaymentReadModel readModel = new PaymentReadModel();
            readModel.setId(event.paymentId().toString());
            readModel.setUserId(event.userId().toString());

            // 2. Buscar dados do usuário (desnormalizar)
            // TODO: Buscar de cache ou serviço
            readModel.setUserName("User " + event.userId());  // Exemplo
            readModel.setUserEmail("user@example.com");       // Exemplo

            // 3. Setar dados do pagamento
            readModel.setAmount(event.amount());
            readModel.setCurrency(event.currency());
            readModel.setStatus(event.status());

            // 4. Pré-computar dados (formatar, traduzir)
            readModel.setFormattedAmount(formatAmount(event.amount(), event.currency()));
            readModel.setStatusLabel(translateStatus(event.status()));

            // 5. Timestamp
            readModel.setCreatedAt(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(event.timestamp()),
                ZoneId.systemDefault()
            ));

            // 6. Salvar no banco de LEITURA (MongoDB)
            readRepository.save(readModel);

            log.info("PaymentReadModel updated successfully: {}", event.paymentId());

        } catch (Exception e) {
            log.error("Failed to update PaymentReadModel: {}", e.getMessage(), e);
            // TODO: Enviar para DLQ ou retry
        }
    }

    private String formatAmount(BigDecimal amount, String currency) {
        Locale locale = "BRL".equals(currency) ? new Locale("pt", "BR") : Locale.US;
        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
        return formatter.format(amount);
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "PENDING" -> "Pendente";
            case "APPROVED" -> "Aprovado";
            case "CANCELLED" -> "Cancelado";
            default -> status;
        };
    }
}
```

---

## 6. CQRS com Event Sourcing

### Diferença: CQRS vs Event Sourcing

```
CQRS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  • Separa Commands (escrita) de Queries (leitura)
  • Write Model e Read Model DIFERENTES
  • Sincronização via eventos
  • PODE usar banco tradicional (PostgreSQL)

  Exemplo:
    Write Model: Salva estado ATUAL (status = APPROVED)
    Read Model: Consulta estado ATUAL


EVENT SOURCING:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  • Salva EVENTOS ao invés de estado
  • Estado ATUAL é reconstruído a partir dos eventos
  • Event Store: banco de EVENTOS (append-only)
  • SEMPRE usa CQRS (eventos → Read Model)

  Exemplo:
    Event Store:
      1. PaymentCreatedEvent
      2. PaymentApprovedEvent
      3. PaymentCancelledEvent  ← Estado atual = CANCELLED

    Estado ATUAL reconstruído:
      replay(eventos) → status = CANCELLED


COMBINAÇÃO: CQRS + Event Sourcing
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Write Side:
    • Salva EVENTOS (Event Store)
    • Não salva estado (só eventos)

  Read Side:
    • Projeta eventos → Read Model
    • Read Model tem estado ATUAL (desnormalizado)

  ✅ Melhor dos dois mundos!
```

### Exemplo: CQRS + Event Sourcing

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMMAND HANDLER (com Event Sourcing)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentCommandHandler {

    private final EventStore eventStore;

    @Transactional
    public void handle(ApprovePaymentCommand command) {

        // 1. Carregar eventos do Payment (Event Sourcing)
        List<PaymentEvent> events = eventStore.getEvents(command.paymentId());

        // 2. Reconstruir estado ATUAL (replay de eventos)
        Payment payment = Payment.fromEvents(events);

        // 3. Executar comando (lógica de negócio)
        payment.approve();

        // 4. Gerar novo evento
        PaymentApprovedEvent event = payment.getPendingEvents().get(0);

        // 5. Salvar evento no Event Store (append-only)
        eventStore.save(event);

        // 6. Publicar evento (para atualizar Read Model)
        eventPublisher.publish(event);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      EVENT STORE (banco de eventos)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Repository
public class EventStore {

    private final EventStoreRepository repository;

    public List<PaymentEvent> getEvents(UUID paymentId) {
        // Buscar TODOS eventos deste Payment
        return repository.findByAggregateIdOrderByVersionAsc(paymentId);
    }

    public void save(PaymentEvent event) {
        // Salvar evento (append-only, NUNCA deleta)
        EventStoreEntry entry = new EventStoreEntry(
            UUID.randomUUID(),
            event.getAggregateId(),
            event.getEventType(),
            event.getVersion(),
            serializeEvent(event),
            Instant.now()
        );

        repository.save(entry);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN MODEL (reconstruído de eventos)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Payment {

    private PaymentId id;
    private UserId userId;
    private Money amount;
    private PaymentStatus status;
    private int version;

    private List<PaymentEvent> pendingEvents = new ArrayList<>();

    /**
     * Reconstruir Payment a partir de eventos (Event Sourcing).
     */
    public static Payment fromEvents(List<PaymentEvent> events) {
        Payment payment = new Payment();

        // Replay de TODOS eventos (reconstrói estado)
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
                this.id = PaymentId.of(e.paymentId());
                this.userId = UserId.of(e.userId());
                this.amount = Money.of(e.amount(), e.currency());
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
    public void approve() {
        // Validação
        if (status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled payment");
        }

        // Criar evento
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            UUID.randomUUID().toString(),
            this.id.value(),
            Instant.now().toEpochMilli(),
            this.version + 1
        );

        // Aplicar evento (muda estado)
        apply(event);

        // Adicionar aos eventos pendentes
        pendingEvents.add(event);
    }

    public List<PaymentEvent> getPendingEvents() {
        return pendingEvents;
    }
}
```

**Ver tutorial `tutorial-event-sourcing.md` para detalhes completos.**

---

## 7. Consistência Eventual

### O Problema

```
CQRS usa Consistência EVENTUAL:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Write Model e Read Model NÃO sincronizam instantaneamente.
  Há um DELAY (latência do Kafka, processamento do evento).

  Exemplo:
    10:00:00.000 → POST /api/payments (Command)
    10:00:00.050 → Payment salvo no PostgreSQL (Write Model)
    10:00:00.100 → Evento publicado no Kafka
    10:00:00.200 → Consumer processa evento
    10:00:00.250 → Read Model atualizado (MongoDB)

    DELAY: 250ms entre Write e Read!

  Problema:
    10:00:00.100 → GET /api/payments/{id}
    ❌ Payment ainda NÃO está no Read Model!
    ❌ Retorna 404 (mas foi criado!)
```

### Soluções

#### Solução 1: Aceitar Eventual Consistency

```
✅ ACEITAR que Read Model tem delay

  Frontend:
    1. POST /api/payments (Command)
    2. Recebe PaymentCreatedEvent (com ID)
    3. Exibe "Pagamento criado com sucesso!"
    4. Usa dados do EVENTO (não faz GET imediatamente)
    5. Depois de 1-2s, faz GET /api/payments/{id}

  ✅ Usuário NÃO percebe delay
  ✅ Simples de implementar
  ✅ Escala bem
```

#### Solução 2: Query no Write Model (fallback)

```java
@Service
public class GetPaymentByIdQueryHandler {

    private final PaymentReadRepository readRepository;
    private final PaymentWriteRepository writeRepository;  // ← Fallback!

    public PaymentDto handle(GetPaymentByIdQuery query) {

        // 1. Tentar buscar no Read Model (rápido)
        Optional<PaymentReadModel> readModel = readRepository.findById(query.id());

        if (readModel.isPresent()) {
            return PaymentDto.from(readModel.get());
        }

        // 2. Fallback: buscar no Write Model (lento, mas consistente)
        Optional<PaymentWriteModel> writeModel = writeRepository.findById(query.id());

        if (writeModel.isPresent()) {
            // Converter Write Model → DTO (sem desnormalização)
            return PaymentDto.fromWriteModel(writeModel.get());
        }

        // 3. Não encontrado
        throw new PaymentNotFoundException(query.id());
    }
}
```

#### Solução 3: Sincronização Síncrona (não recomendado)

```java
// ❌ NÃO RECOMENDADO - perde benefícios do CQRS

@Service
public class CreatePaymentCommandHandler {

    @Transactional
    public PaymentCreatedEvent handle(CreatePaymentCommand command) {

        // 1. Salvar no Write Model
        writeRepository.save(writeModel);

        // 2. Atualizar Read Model SINCRONAMENTE
        // ❌ Perde escalabilidade (Write acoplado a Read)
        // ❌ Perde performance (duas escritas sequenciais)
        updateReadModelSync(writeModel);

        return event;
    }
}
```

---

## 8. Testando CQRS

### Teste 1: Command Handler

```java
@SpringBootTest
@Transactional
class CreatePaymentCommandHandlerTest {

    @Autowired
    private CreatePaymentCommandHandler handler;

    @Autowired
    private PaymentWriteRepository writeRepository;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    void shouldCreatePaymentAndPublishEvent() {
        // Given
        CreatePaymentCommand command = new CreatePaymentCommand(
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD"
        );

        // When
        PaymentCreatedEvent event = handler.handle(command);

        // Then
        // 1. Verifica Write Model foi salvo
        Optional<PaymentWriteModel> writeModel = writeRepository.findById(event.paymentId());
        assertThat(writeModel).isPresent();
        assertThat(writeModel.get().getAmount()).isEqualTo(new BigDecimal("100.00"));

        // 2. Verifica evento foi criado no Outbox
        List<OutboxEvent> outboxEvents = outboxRepository
            .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.get(0).getEventType()).isEqualTo("PaymentCreated");
    }
}
```

### Teste 2: Query Handler

```java
@SpringBootTest
class GetUserPaymentsQueryHandlerTest {

    @Autowired
    private GetUserPaymentsQueryHandler handler;

    @Autowired
    private PaymentReadRepository readRepository;

    @BeforeEach
    void setUp() {
        // Preparar Read Model (dados de teste)
        PaymentReadModel readModel = new PaymentReadModel();
        readModel.setId(UUID.randomUUID().toString());
        readModel.setUserId("user-123");
        readModel.setAmount(new BigDecimal("100.00"));
        readModel.setCurrency("USD");
        readModel.setFormattedAmount("$100.00");
        readModel.setStatus("PENDING");
        readModel.setStatusLabel("Pending");
        readModel.setCreatedAt(LocalDateTime.now());

        readRepository.save(readModel);
    }

    @Test
    void shouldReturnUserPayments() {
        // Given
        GetUserPaymentsQuery query = new GetUserPaymentsQuery(
            UUID.fromString("user-123"),
            0,
            20
        );

        // When
        Page<PaymentDto> payments = handler.handle(query);

        // Then
        assertThat(payments.getContent()).hasSize(1);
        assertThat(payments.getContent().get(0).formattedAmount()).isEqualTo("$100.00");
    }
}
```

### Teste 3: Event Handler (Sincronização)

```java
@SpringBootTest
class PaymentEventHandlerTest {

    @Autowired
    private PaymentEventHandler eventHandler;

    @Autowired
    private PaymentReadRepository readRepository;

    @Test
    void shouldUpdateReadModelWhenEventReceived() {
        // Given
        PaymentCreatedEvent event = new PaymentCreatedEvent(
            UUID.randomUUID().toString(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "USD",
            "PENDING",
            Instant.now().toEpochMilli()
        );

        // When
        eventHandler.handlePaymentCreated(event);

        // Then
        Optional<PaymentReadModel> readModel = readRepository.findById(
            event.paymentId().toString()
        );

        assertThat(readModel).isPresent();
        assertThat(readModel.get().getAmount()).isEqualTo(new BigDecimal("100.00"));
        assertThat(readModel.get().getFormattedAmount()).isEqualTo("$100.00");
        assertThat(readModel.get().getStatusLabel()).isEqualTo("Pending");
    }
}
```

---

## 9. Cenários do Dia a Dia

### Cenário 1: Alta Carga de Leitura

**Situação:**
Sistema com 90% queries (leituras) e 10% commands (escritas).

**Sem CQRS:**
```
❌ Banco sobrecarregado (leitura + escrita no mesmo lugar)
❌ Queries lentas (JOINs, normalizacão)
❌ Não pode escalar leitura sem escalar escrita
```

**Com CQRS:**
```
✅ Read Model em MongoDB (queries rápidas)
✅ Write Model em PostgreSQL (validações fortes)
✅ Escala Read Model com réplicas (3x, 5x, 10x)
✅ Write Model mantém 1 instância (10% do tráfego)
✅ Cache agressivo no Read Model (Redis)
```

---

### Cenário 2: Múltiplas Views

**Situação:**
Precisa de diferentes formatos de dados:
- Web: detalhes completos
- Mobile: resumo
- Admin: estatísticas

**Sem CQRS:**
```
❌ Mesma query para tudo (JOINs complexos)
❌ Retorna dados desnecessários (overhead)
❌ Lógica de formatação no frontend
```

**Com CQRS:**
```
✅ Múltiplos Read Models:
  • PaymentWebView (detalhes completos)
  • PaymentMobileView (resumo)
  • PaymentAdminView (estatísticas agregadas)

✅ Cada view otimizada para seu caso de uso
✅ Diferentes bancos se necessário (Elasticsearch para admin)
```

---

### Cenário 3: Relatórios Complexos

**Situação:**
Precisa gerar relatório: "Total de pagamentos aprovados por mês, por moeda".

**Sem CQRS:**
```
❌ Query pesada (GROUP BY, SUM, múltiplos JOINs)
❌ Lenta (processa milhões de linhas)
❌ Trava banco de escrita
```

**Com CQRS:**
```
✅ Read Model com dados PRÉ-AGREGADOS

Event Handler:
  @KafkaListener(topics = "payment.approved.v1")
  public void handlePaymentApproved(PaymentApprovedEvent event) {
      // Atualizar estatísticas agregadas
      PaymentStatsReadModel stats = statsRepository.findByMonthAndCurrency(
          event.month(),
          event.currency()
      );

      stats.incrementCount();
      stats.addAmount(event.amount());
      statsRepository.save(stats);
  }

Query rápida (dados já agregados):
  SELECT * FROM payment_stats WHERE month = '2024-01' AND currency = 'USD';
  ✅ Milissegundos!
```

---

## 10. Armadilhas Comuns

### Armadilha 1: Queries no Write Model

```java
// ❌ ERRADO - Query usando Write Model

@RestController
public class PaymentController {

    @GetMapping("/api/payments/{id}")
    public PaymentDto getPayment(@PathVariable UUID id) {
        // ❌ Busca no Write Model!
        PaymentWriteModel writeModel = writeRepository.findById(id).orElseThrow();

        // ❌ Perde benefícios do CQRS
        // ❌ Query lenta (normalizado)
        // ❌ Não pode cachear
        return PaymentDto.from(writeModel);
    }
}

// ✅ CORRETO - Query usando Read Model

@RestController
public class PaymentQueryController {

    @GetMapping("/api/payments/{id}")
    public PaymentDto getPayment(@PathVariable UUID id) {
        // ✅ Busca no Read Model!
        GetPaymentByIdQuery query = new GetPaymentByIdQuery(id);
        return queryHandler.handle(query);
    }
}
```

---

### Armadilha 2: Lógica de Negócio em Query

```java
// ❌ ERRADO - Lógica de negócio em Query Handler

@Service
public class GetUserPaymentsQueryHandler {

    public Page<PaymentDto> handle(GetUserPaymentsQuery query) {

        // ❌ Validação de negócio em Query!
        if (user.isBlocked()) {
            throw new UserBlockedException();
        }

        // ❌ Cálculo de negócio em Query!
        BigDecimal total = payments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ...
    }
}

// ✅ CORRETO - Query SÓ retorna dados

@Service
public class GetUserPaymentsQueryHandler {

    public Page<PaymentDto> handle(GetUserPaymentsQuery query) {

        // ✅ SÓ busca e retorna dados
        // ✅ Sem validações (não muda estado)
        // ✅ Sem cálculos (pré-computados no Read Model)

        return readRepository.findByUserId(query.userId(), pageable)
            .map(PaymentDto::from);
    }
}
```

---

### Armadilha 3: Sincronização Síncrona

```java
// ❌ ERRADO - Atualizar Read Model SINCRONAMENTE

@Service
public class CreatePaymentCommandHandler {

    @Transactional
    public void handle(CreatePaymentCommand command) {

        // Salvar Write Model
        writeRepository.save(writeModel);

        // ❌ Atualizar Read Model DIRETO (síncrono)
        readRepository.save(readModel);

        // PROBLEMAS:
        // ❌ Write acoplado a Read (perde escalabilidade)
        // ❌ Duas escritas sequenciais (lento)
        // ❌ Se Read falhar, Write falha (inconsistência)
    }
}

// ✅ CORRETO - Sincronização ASSÍNCRONA

@Service
public class CreatePaymentCommandHandler {

    @Transactional
    public void handle(CreatePaymentCommand command) {

        // 1. Salvar Write Model
        writeRepository.save(writeModel);

        // 2. Publicar evento (Kafka)
        eventPublisher.publish(event);

        // 3. Event Handler atualiza Read Model (assíncrono)
    }
}
```

---

## 11. Checklist CQRS

### ☐ ANTES DE IMPLEMENTAR

#### Entendimento
- [ ] Entendeu a diferença entre Command e Query?
- [ ] Sabe quando usar CQRS? (alta carga de leitura, múltiplas views)
- [ ] Entende Consistência Eventual?

#### Arquitetura
- [ ] Definiu Write Model (normalizado)?
- [ ] Definiu Read Model (desnormalizado)?
- [ ] Escolheu banco de escrita (PostgreSQL)?
- [ ] Escolheu banco de leitura (MongoDB, Elasticsearch)?

---

### ☐ IMPLEMENTAÇÃO

#### Commands
- [ ] Criou Commands (imutáveis, validações básicas)?
- [ ] Criou Command Handlers (validações de negócio)?
- [ ] Command Handler salva no Write Model?
- [ ] Command Handler publica eventos (Outbox)?
- [ ] Commands retornam eventos (não dados)?

#### Queries
- [ ] Criou Queries (imutáveis, parâmetros de filtro)?
- [ ] Criou Query Handlers (sem lógica de negócio)?
- [ ] Query Handler busca no Read Model?
- [ ] Queries NÃO modificam estado?
- [ ] Queries retornam DTOs (View Models)?

#### Write Model
- [ ] Entidade JPA normalizada?
- [ ] Validações de negócio no Domain?
- [ ] Repository salva apenas (sem leitura)?

#### Read Model
- [ ] Documento/Entity desnormalizado?
- [ ] Dados pré-computados (formatações)?
- [ ] Repository otimizado para leitura?
- [ ] Índices criados (performance)?

#### Sincronização
- [ ] Event Handler escuta eventos?
- [ ] Event Handler atualiza Read Model?
- [ ] Tratamento de falhas (DLQ)?
- [ ] Idempotência (não duplica dados)?

---

### ☐ TESTES

- [ ] Testou Command Handler (salva Write Model + Outbox)?
- [ ] Testou Query Handler (busca Read Model)?
- [ ] Testou Event Handler (atualiza Read Model)?
- [ ] Testou consistência eventual (delay)?

---

### ☐ PRODUÇÃO

#### Performance
- [ ] Cache em Read Model (Redis)?
- [ ] Réplicas de leitura?
- [ ] Índices otimizados?

#### Monitoramento
- [ ] Métrica de lag (Write → Read)?
- [ ] Alerta se lag > threshold?
- [ ] Monitoramento de eventos PENDING?

---

## 12. Exercícios Práticos

### Exercício 1: Identificar Violações

Analise o código e identifique problemas:

```java
@Service
public class PaymentService {

    private final PaymentRepository repository;

    // Método 1: Criar pagamento
    public Payment createPayment(CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setUserId(request.userId());
        payment.setAmount(request.amount());
        return repository.save(payment);
    }

    // Método 2: Buscar pagamentos
    public List<Payment> getUserPayments(UUID userId) {
        return repository.findByUserId(userId);
    }

    // Método 3: Calcular total
    public BigDecimal calculateTotal(UUID userId) {
        List<Payment> payments = repository.findByUserId(userId);
        return payments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

<details>
<summary><strong>📝 Resposta</strong></summary>

**Violações:**

1. ❌ **Não usa CQRS**
   - Mesmo método para escrita (`createPayment`) e leitura (`getUserPayments`)
   - Mesmo modelo (`Payment`) para tudo

2. ❌ **Query com lógica de negócio**
   - `calculateTotal()` faz cálculo (deveria ser pré-computado no Read Model)

3. ❌ **Não publica eventos**
   - `createPayment()` não publica evento
   - Read Model não sincroniza

4. ❌ **Entity anêmica**
   - `Payment` com setters (deveria ser imutável)
   - Validações ausentes

**Solução CQRS:**

```java
// ✅ COMMAND
@Service
public class CreatePaymentCommandHandler {

    @Transactional
    public PaymentCreatedEvent handle(CreatePaymentCommand command) {
        Payment payment = Payment.create(...);
        writeRepository.save(toWriteModel(payment));

        PaymentCreatedEvent event = PaymentCreatedEvent.from(payment);
        outboxService.save("Payment", payment.getId(), "PaymentCreated", event);

        return event;
    }
}

// ✅ QUERY
@Service
public class GetUserPaymentsQueryHandler {

    @Cacheable("user-payments")
    public Page<PaymentDto> handle(GetUserPaymentsQuery query) {
        // Busca Read Model (desnormalizado, com total pré-computado)
        return readRepository.findByUserId(query.userId(), pageable)
            .map(PaymentDto::from);
    }
}

// ✅ READ MODEL (total pré-computado)
@Document
public class UserPaymentsSummaryReadModel {
    private String userId;
    private List<PaymentSummary> payments;
    private BigDecimal total;  // ← Pré-computado!
}
```

</details>

---

## 🎯 Conclusão

**CQRS** revoluciona sistemas complexos ao separar escrita e leitura!

**O que você aprendeu:**
✅ Commands vs Queries (responsabilidades diferentes)
✅ Write Model (normalizado, validações) vs Read Model (desnormalizado, rápido)
✅ Sincronização via eventos (eventual consistency)
✅ Escalabilidade independente (bancos separados)
✅ Performance 10x-100x em leituras
✅ Múltiplas views otimizadas

**Lembre-se:**

- **Command** = Muda estado, retorna evento, pode falhar
- **Query** = Retorna dados, nunca muda estado, nunca falha
- **Write Model** = Normalizado, ACID, validações fortes
- **Read Model** = Desnormalizado, eventual consistency, rápido
- **Eventual Consistency** = Read Model sincroniza via eventos (delay aceitável)

**Regra de Ouro:**
```
NUNCA faça queries no Write Model!
NUNCA execute lógica de negócio em Query Handlers!
SEMPRE sincronize via eventos (assíncrono)!
```

---

**Próximos Passos:**
1. Leia `tutorial-event-sourcing.md` (complemento natural do CQRS)
2. Implemente CQRS no seu projeto
3. Configure cache (Redis) no Read Model
4. Monitore lag (Write → Read)

**Quando usar CQRS:**
✅ Alta carga de leitura (>70% do tráfego)
✅ Múltiplas views (web, mobile, admin)
✅ Relatórios complexos (agregações)
✅ Escalabilidade diferenciada (leitura ≠ escrita)
✅ Já usa Event-Driven Architecture

**Quando NÃO usar CQRS:**
❌ CRUD simples (poucos usuários)
❌ Leitura = Escrita (50/50)
❌ Não aceita eventual consistency
❌ Equipe pequena (complexidade adicional)

---

**Boa sorte na sua jornada com CQRS! 🚀**