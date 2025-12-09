# Tutorial Definitivo: Migrando Monolito para Microserviços

## 📚 Sumário

1. [Definição em 30 Segundos](#definição-em-30-segundos)
2. [Por Que Migrar (E Por Que NÃO Migrar)](#por-que-migrar)
3. [Avaliando Seu Monolito](#avaliando-seu-monolito)
4. [Estratégias de Migração](#estratégias-de-migração)
5. [Padrões de Decomposição](#padrões-de-decomposição)
6. [Migração de Dados](#migração-de-dados)
7. [Processo Passo a Passo](#processo-passo-a-passo)
8. [Anti-Corruption Layer](#anti-corruption-layer)
9. [Gerenciando Ambos os Sistemas](#gerenciando-ambos-os-sistemas)
10. [Estratégias de Teste Durante Migração](#estratégias-de-teste)
11. [Rollback e Planos B](#rollback-e-planos-b)
12. [Migração de Infraestrutura](#migração-de-infraestrutura)
13. [Comunicação e Sincronização](#comunicação-e-sincronização)
14. [Armadilhas Comuns](#armadilhas-comuns)
15. [Casos Reais](#casos-reais)
16. [Checklist de Migração](#checklist-de-migração)
17. [Exercícios Práticos](#exercícios-práticos)

---

## Definição em 30 Segundos

**Migração de Monolito para Microserviços** é o processo incremental e controlado de decompor uma aplicação monolítica existente em serviços menores, independentes e autônomos. A migração usa padrões como **Strangler Fig** para substituir gradualmente funcionalidades do monolito sem reescrever todo o sistema de uma vez, minimizando riscos e permitindo rollback.

**Princípio-Chave:** Migre incrementalmente, não faça Big Bang rewrite.

---

## 1. Por Que Migrar (E Por Que NÃO Migrar) {#por-que-migrar}

### 1.1. Sinais de Que Você DEVE Migrar

#### ✅ Problema 1: Deploy Arriscado e Infrequente

**Sintoma:**
- Deploys acontecem 1x/mês ou menos
- Cada deploy é um evento traumático
- Rollback é complicado
- "Deploy Friday" é proibido

**Por que microserviços ajudam:**
- Deploy independente de cada serviço
- Rollback isolado
- Deploy contínuo possível

```
ANTES (Monolito):
┌─────────────────────────────────────┐
│         MONOLITO                    │
│  ┌──────┐ ┌──────┐ ┌──────┐        │
│  │Users │ │Orders│ │Pay.. │        │
│  └──────┘ └──────┘ └──────┘        │
│                                     │
│  Bug no módulo Users                │
│  → TODAS features param de ser      │
│    deployadas até o bug ser fixado  │
└─────────────────────────────────────┘

DEPOIS (Microserviços):
┌──────────┐  ┌──────────┐  ┌──────────┐
│  User    │  │  Order   │  │ Payment  │
│ Service  │  │ Service  │  │ Service  │
└──────────┘  └──────────┘  └──────────┘
     ↓             ↓             ↓
   Bug aqui    Deploy OK     Deploy OK
  (bloqueado)  (continua)   (continua)
```

#### ✅ Problema 2: Escalabilidade Não Uniforme

**Sintoma:**
- Módulo de relatórios consome 80% da CPU
- Mas você precisa escalar TODO o monolito
- Custo de infraestrutura alto

**Por que microserviços ajudam:**
- Escale apenas o serviço que precisa

```
ANTES:
┌─────────────────────────────────┐
│  MONOLITO (4GB RAM, 2 CPUs)     │  ← Precisa escalar TUDO
│  ┌─────┐ ┌─────┐ ┌──────────┐  │     para atender relatórios
│  │Users│ │Order│ │ Reports  │  │
│  │(10%)│ │(10%)│ │  (80%)   │  │
│  └─────┘ └─────┘ └──────────┘  │
└─────────────────────────────────┘
        ↓ Scale horizontal
┌─────────────────────────────────┐
│  MONOLITO (4GB RAM, 2 CPUs) #1  │
└─────────────────────────────────┘
┌─────────────────────────────────┐
│  MONOLITO (4GB RAM, 2 CPUs) #2  │  ← Custo dobrado
└─────────────────────────────────┘     Mas users/orders não precisam

DEPOIS:
┌──────┐ ┌──────┐ ┌────────────────────┐
│Users │ │Order │ │ Reports Service    │
│(1 GB)│ │(1 GB)│ │ (8GB, 4 CPUs) × 3  │ ← Scale só o que precisa
└──────┘ └──────┘ └────────────────────┘
```

#### ✅ Problema 3: Times Grandes com Conflitos

**Sintoma:**
- 50+ desenvolvedores no mesmo repositório
- Merge hell diário
- "Quem quebrou a build?"
- Conflitos de dependências

**Por que microserviços ajudam:**
- Cada time possui seu serviço
- Repositórios independentes
- APIs como contratos

#### ✅ Problema 4: Tecnologias Legadas Travam Evolução

**Sintoma:**
- "Não podemos atualizar o Java porque o módulo X não é compatível"
- Bibliotecas desatualizadas por medo
- Débito técnico crescente

**Por que microserviços ajudam:**
- Cada serviço escolhe sua stack
- Migre tecnologias incrementalmente

---

### 1.2. Sinais de Que Você NÃO DEVE Migrar

#### ❌ Situação 1: Time Pequeno

**Se você tem:**
- Menos de 15 desenvolvedores
- 1-2 times
- Deploy funciona bem

**Então:**
- Microserviços vão adicionar complexidade desnecessária
- Foque em modularizar o monolito (Modular Monolith)

#### ❌ Situação 2: Problema é Código, Não Arquitetura

```java
// ❌ PROBLEMA: Código ruim
@Service
public class OrderService {
    // 5000 linhas de código
    // God class
    // Sem testes
    // Lógica de negócio misturada com infraestrutura

    public void processOrder() {
        // 500 linhas aqui
    }
}
```

**Microserviços NÃO vão resolver:**
- Código mal escrito
- Falta de testes
- Lógica de negócio confusa

**Solução:** Refatore o código DENTRO do monolito primeiro.

#### ❌ Situação 3: Você Não Tem DevOps Maduro

**Se você NÃO tem:**
- CI/CD automatizado
- Monitoramento robusto
- Containerização (Docker)
- Orquestração (Kubernetes ou similar)
- Experiência com sistemas distribuídos

**Então:**
- Microserviços vão criar caos
- Você vai ter 10 monolitos mal gerenciados

#### ❌ Situação 4: Pressão de Prazo

```
❌ ERRADO:
"Precisamos entregar feature X em 1 mês.
 Vamos migrar para microserviços ao mesmo tempo!"

✅ CORRETO:
"Precisamos entregar feature X em 1 mês.
 Vamos entregar no monolito e planejar migração depois."
```

**Regra:** Nunca migre sob pressão de prazo.

---

### 1.3. Checklist: Devo Migrar?

Responda SIM/NÃO:

- [ ] Meu monolito tem mais de 100k linhas de código?
- [ ] Temos mais de 15 desenvolvedores?
- [ ] Deploy é arriscado e infrequente (< 1x/semana)?
- [ ] Precisamos escalar partes específicas do sistema?
- [ ] Temos CI/CD maduro?
- [ ] Temos monitoramento distribuído (logs, métricas, traces)?
- [ ] Time tem experiência com Docker/Kubernetes?
- [ ] Temos pelo menos 6 meses para migração gradual?
- [ ] Nosso domínio tem bounded contexts claros?

**Resultado:**
- **8-9 SIM:** Migre agora
- **5-7 SIM:** Migre com planejamento cuidadoso
- **< 5 SIM:** NÃO migre ainda, prepare a infraestrutura primeiro

---

## 2. Avaliando Seu Monolito {#avaliando-seu-monolito}

Antes de migrar, você precisa **mapear** seu monolito.

### 2.1. Mapeamento de Dependências

#### Ferramenta: Dependency Graph

```bash
# Gere gráfico de dependências
mvn dependency:tree > dependencies.txt

# Ou use ferramentas visuais
# - JDepend
# - Structure101
# - SonarQube
```

**Exemplo de resultado:**

```
┌────────────────────────────────────────┐
│         MONOLITO ATUAL                 │
│                                        │
│  ┌──────────────────────────────┐     │
│  │     UserController           │     │
│  └─────────┬────────────────────┘     │
│            ↓                           │
│  ┌──────────────────────────────┐     │
│  │     UserService              │     │
│  └─────────┬────────────────────┘     │
│            ↓                           │
│  ┌──────────────────────────────┐     │
│  │     UserRepository           │     │
│  │     OrderRepository  ← ❌     │     │  ← Acoplamento!
│  │     PaymentRepository ← ❌    │     │
│  └──────────────────────────────┘     │
└────────────────────────────────────────┘
```

**Problema identificado:** UserService acessa diretamente OrderRepository e PaymentRepository.

**Ação antes de migrar:** Refatorar para usar APIs/Services.

---

### 2.2. Identificando Bounded Contexts

Use **Domain-Driven Design** para identificar contextos.

#### Técnica: Event Storming

**Passo 1:** Liste todos os eventos de domínio

```
Eventos do Sistema:
- UserRegistered
- UserLoggedIn
- OrderCreated
- OrderApproved
- OrderShipped
- PaymentCreated
- PaymentApproved
- PaymentFailed
- InventoryReserved
- InventoryReleased
```

**Passo 2:** Agrupe por contexto

```
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│  USER CONTEXT       │  │  ORDER CONTEXT      │  │  PAYMENT CONTEXT    │
│                     │  │                     │  │                     │
│ - UserRegistered    │  │ - OrderCreated      │  │ - PaymentCreated    │
│ - UserLoggedIn      │  │ - OrderApproved     │  │ - PaymentApproved   │
│                     │  │ - OrderShipped      │  │ - PaymentFailed     │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘

┌─────────────────────┐
│ INVENTORY CONTEXT   │
│                     │
│ - InventoryReserved │
│ - InventoryReleased │
└─────────────────────┘
```

**Resultado:** 4 microserviços candidatos.

---

### 2.3. Analisando Tráfego e Performance

#### Métricas Importantes

```java
// Adicione métricas no monolito ANTES de migrar
@RestController
public class OrderController {

    private final MeterRegistry registry;

    @PostMapping("/orders")
    @Timed(value = "order.create", description = "Time to create order")
    public Order createOrder(@RequestBody OrderRequest request) {
        // ...
    }
}
```

**Analise:**
- Quais endpoints têm mais tráfego?
- Quais operações são mais lentas?
- Onde estão os gargalos?

```
Resultado da Análise:
┌─────────────────────────────────────┐
│ Endpoint         │ Req/s │ Latência │
├─────────────────────────────────────┤
│ POST /orders     │  100  │  200ms   │ ← Migre primeiro (alto volume)
│ GET /orders/{id} │  500  │   50ms   │ ← Migre depois (leitura rápida)
│ POST /users      │    5  │  100ms   │ ← Migre por último (baixo volume)
└─────────────────────────────────────┘
```

**Decisão:** Migre módulos de **alto volume** primeiro para obter benefícios de escalabilidade rapidamente.

---

### 2.4. Identificando Acoplamentos

#### Tipos de Acoplamento

**1. Acoplamento de Código (Direto)**

```java
// ❌ ALTO ACOPLAMENTO
@Service
public class OrderService {

    @Autowired
    private PaymentRepository paymentRepository; // ← Acesso direto!

    public void createOrder(Order order) {
        // Cria order
        orderRepository.save(order);

        // Acessa diretamente dados de Payment
        Payment payment = paymentRepository.findByOrderId(order.getId());
    }
}
```

**2. Acoplamento de Banco de Dados (Shared Database)**

```sql
-- ❌ PROBLEMA: Queries JOIN entre contextos
SELECT o.*, p.*, u.*
FROM orders o
JOIN payments p ON o.id = p.order_id
JOIN users u ON o.user_id = u.id;
```

**3. Acoplamento de Transação**

```java
// ❌ PROBLEMA: Transação distribuída no monolito
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order);           // Tabela orders
    inventoryRepository.reserve(order);     // Tabela inventory
    paymentRepository.create(order);        // Tabela payments

    // Tudo ou nada - difícil de separar
}
```

#### Matriz de Acoplamento

Crie uma matriz para visualizar:

```
        │ User │ Order │ Payment │ Inventory │
────────┼──────┼───────┼─────────┼───────────┤
User    │  -   │   2   │    1    │     0     │
Order   │  3   │   -   │    5    │     4     │
Payment │  1   │   4   │    -    │     0     │
Inventory│  0  │   3   │    0    │     -     │

Legenda:
0 = Sem acoplamento
1-2 = Acoplamento baixo (fácil de quebrar)
3-4 = Acoplamento médio (requer refatoração)
5+ = Acoplamento alto (difícil de separar)
```

**Análise:**
- Order ↔ Payment: Acoplamento alto (5) - Atenção especial na migração
- Order ↔ Inventory: Acoplamento médio (4) - Refatorar antes de separar

---

## 3. Estratégias de Migração {#estratégias-de-migração}

### 3.1. Strangler Fig Pattern (Recomendado)

O **Strangler Fig Pattern** é a estratégia mais segura: você gradualmente "estrangula" o monolito substituindo funcionalidades por microserviços.

#### Como Funciona

```
FASE 1: Sistema Original
┌─────────────────────────┐
│      MONOLITO           │
│  ┌─────┐ ┌─────┐       │
│  │Users│ │Orders│       │
│  └─────┘ └─────┘       │
└─────────────────────────┘

FASE 2: Adiciona Proxy (API Gateway)
         ┌─────────────┐
         │ API Gateway │
         └──────┬──────┘
                ↓
┌─────────────────────────┐
│      MONOLITO           │
│  ┌─────┐ ┌─────┐       │
│  │Users│ │Orders│       │
│  └─────┘ └─────┘       │
└─────────────────────────┘

FASE 3: Extrai primeiro microserviço
         ┌─────────────┐
         │ API Gateway │
         └──┬───────┬──┘
            │       │
            ↓       ↓
     ┌──────────┐  ┌─────────────────┐
     │  Order   │  │   MONOLITO      │
     │ Service  │  │  ┌─────┐        │
     └──────────┘  │  │Users│        │
                   │  └─────┘        │
                   └─────────────────┘

FASE 4: Continua extraindo
         ┌─────────────┐
         │ API Gateway │
         └──┬──────┬───┘
            │      │
            ↓      ↓
     ┌──────────┐ ┌──────────┐
     │  Order   │ │   User   │
     │ Service  │ │ Service  │
     └──────────┘ └──────────┘

FASE 5: Monolito vazio (opcional: desligar)
```

#### Implementação Passo a Passo

**Passo 1: Configurar API Gateway**

```yaml
# application.yml (Spring Cloud Gateway)
spring:
  cloud:
    gateway:
      routes:
        # Rota para ORDERS → Microserviço (novo)
        - id: order-service
          uri: http://order-service:8081
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderServiceCircuitBreaker
                fallbackUri: forward:/fallback/orders

        # Rota para USERS → Monolito (ainda)
        - id: monolith-users
          uri: http://monolith:8080
          predicates:
            - Path=/api/users/**

        # Rota padrão → Monolito
        - id: monolith-default
          uri: http://monolith:8080
          predicates:
            - Path=/**
```

**Passo 2: Extrair primeiro serviço (Order)**

```java
// ===== NOVO: Order Service =====
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @RequestBody OrderRequest request
    ) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.ok(new OrderResponse(order));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(new OrderResponse(order));
    }
}

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userClient; // ← Cliente HTTP para User no monolito
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. Valida usuário (chama monolito via HTTP)
        UserResponse user = userClient.getUser(request.getUserId());

        // 2. Cria order
        Order order = Order.create(
            OrderId.generate(),
            UserId.of(request.getUserId()),
            Money.of(request.getAmount(), "BRL")
        );

        // 3. Salva no banco do Order Service (database isolado)
        orderRepository.save(order);

        // 4. Publica evento
        kafkaTemplate.send(
            "order.created.v1",
            new OrderCreatedEvent(order.getId(), order.getUserId(), order.getAmount())
        );

        return order;
    }
}

// Cliente Feign para chamar User no Monolito
@FeignClient(name = "user-service", url = "${monolith.url}")
public interface UserServiceClient {

    @GetMapping("/api/users/{id}")
    UserResponse getUser(@PathVariable UUID id);
}
```

**Passo 3: Atualizar Monolito para Delegar**

```java
// ===== MONOLITO: OrderController (versão antiga) =====
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final RestTemplate restTemplate;

    @Value("${order.service.url}")
    private String orderServiceUrl;

    // ✅ DELEGAÇÃO: Encaminha para novo serviço
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
        @RequestBody OrderRequest request
    ) {
        // Chama o novo Order Service
        return restTemplate.postForEntity(
            orderServiceUrl + "/api/orders",
            request,
            OrderResponse.class
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        // Chama o novo Order Service
        return restTemplate.getForEntity(
            orderServiceUrl + "/api/orders/" + id,
            OrderResponse.class
        );
    }
}
```

**Passo 4: Migração de Dados (Dual Write)**

```java
// DURANTE A TRANSIÇÃO: Escreve em AMBOS os bancos
@Service
public class OrderService {

    private final OrderRepository newOrderRepository;      // Novo banco
    private final LegacyOrderRepository legacyRepository;  // Banco do monolito

    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = Order.create(...);

        // 1. Salva no NOVO banco (Order Service)
        newOrderRepository.save(order);

        // 2. Salva no banco LEGADO (Monolito) - TEMPORÁRIO
        if (dualWriteEnabled) {
            legacyRepository.save(order);
        }

        return order;
    }
}
```

**Passo 5: Validação com Feature Toggle**

```java
@Service
public class OrderService {

    @Value("${feature.use-new-order-service}")
    private boolean useNewService;

    public Order createOrder(OrderRequest request) {
        if (useNewService) {
            return newOrderService.createOrder(request);
        } else {
            return legacyOrderService.createOrder(request);
        }
    }
}
```

```properties
# application.properties
feature.use-new-order-service=false  # ← Começa desligado

# Depois de testes em produção:
feature.use-new-order-service=true   # ← Liga gradualmente (canary)
```

---

### 3.2. Branch by Abstraction

Técnica para refatorar código SEM quebrar o sistema.

#### Exemplo: Migrar Autenticação

**Estado Atual:**

```java
// ❌ CÓDIGO LEGADO: Autenticação hardcoded
@Service
public class OrderService {

    public Order createOrder(OrderRequest request, String sessionId) {
        // Valida sessão direto no banco
        Session session = sessionRepository.findById(sessionId);
        if (session == null || session.isExpired()) {
            throw new UnauthorizedException();
        }

        User user = session.getUser();
        // ...
    }
}
```

**Passo 1: Criar Abstração**

```java
// Nova interface
public interface AuthenticationService {
    User authenticate(String token);
}

// Implementação LEGADA (usa sessões)
@Service
@ConditionalOnProperty(name = "auth.provider", havingValue = "legacy")
public class LegacyAuthService implements AuthenticationService {

    private final SessionRepository sessionRepository;

    @Override
    public User authenticate(String sessionId) {
        Session session = sessionRepository.findById(sessionId);
        if (session == null || session.isExpired()) {
            throw new UnauthorizedException();
        }
        return session.getUser();
    }
}

// Implementação NOVA (usa JWT + microserviço)
@Service
@ConditionalOnProperty(name = "auth.provider", havingValue = "jwt")
public class JwtAuthService implements AuthenticationService {

    private final UserServiceClient userClient;
    private final JwtValidator jwtValidator;

    @Override
    public User authenticate(String jwtToken) {
        Claims claims = jwtValidator.validate(jwtToken);
        UUID userId = UUID.fromString(claims.getSubject());
        return userClient.getUser(userId);
    }
}
```

**Passo 2: Usar Abstração**

```java
// ✅ REFATORADO: Usa abstração
@Service
public class OrderService {

    private final AuthenticationService authService; // ← Abstração

    public Order createOrder(OrderRequest request, String token) {
        // Não sabe se é sessão ou JWT!
        User user = authService.authenticate(token);
        // ...
    }
}
```

**Passo 3: Trocar Implementação via Config**

```properties
# application.properties

# FASE 1: Usa legado
auth.provider=legacy

# FASE 2: Testa novo (canary)
auth.provider=jwt

# FASE 3: Remove implementação legada (código)
```

---

### 3.3. Parallel Run (Execução Paralela)

Execute **AMBOS** sistemas (monolito e microserviço) e compare resultados.

#### Implementação

```java
@Service
public class OrderService {

    private final LegacyOrderService legacyService;
    private final NewOrderService newService;
    private final MetricRegistry metrics;

    public Order createOrder(OrderRequest request) {
        // 1. SEMPRE executa no legado (produção)
        Order legacyResult = legacyService.createOrder(request);

        // 2. PARALELO: Executa no novo (shadow mode)
        CompletableFuture.runAsync(() -> {
            try {
                Order newResult = newService.createOrder(request);

                // 3. COMPARA resultados
                if (!legacyResult.equals(newResult)) {
                    metrics.counter("order.mismatch").increment();
                    log.error("Mismatch detected: legacy={}, new={}",
                        legacyResult, newResult);
                }
            } catch (Exception e) {
                metrics.counter("order.new-service-error").increment();
                log.error("New service failed", e);
            }
        });

        // 4. Retorna resultado do LEGADO (sem risco)
        return legacyResult;
    }
}
```

**Vantagens:**
- Valida novo serviço com tráfego real
- Zero risco (sempre retorna legado)
- Detecta divergências antes do switch

**Desvantagens:**
- Dobra carga (CPU, banco)
- Cuidado com side effects (não executar ações duplicadas)

---

## 4. Padrões de Decomposição {#padrões-de-decomposição}

### 4.1. Decomposição por Subdomínio (DDD)

Use **Bounded Contexts** do Domain-Driven Design.

#### Exemplo: E-commerce

```
┌─────────────────────────────────────────────────────────────┐
│                    E-COMMERCE MONOLITO                      │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              USER MANAGEMENT                         │  │
│  │  - Registro                                          │  │
│  │  - Login/Logout                                      │  │
│  │  - Perfil                                            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              CATALOG                                 │  │
│  │  - Produtos                                          │  │
│  │  - Categorias                                        │  │
│  │  - Busca                                             │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              ORDER MANAGEMENT                        │  │
│  │  - Criar pedido                                      │  │
│  │  - Calcular frete                                    │  │
│  │  - Tracking                                          │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              PAYMENT                                 │  │
│  │  - Processar pagamento                               │  │
│  │  - Estornos                                          │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              INVENTORY                               │  │
│  │  - Estoque                                           │  │
│  │  - Reservas                                          │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘

                         ↓ DECOMPOSIÇÃO

┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│  User   │  │ Catalog │  │  Order  │  │ Payment │  │Inventory│
│ Service │  │ Service │  │ Service │  │ Service │  │ Service │
└─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
```

#### Critérios de Decomposição

**1. Cada contexto deve ter:**
- Linguagem ubíqua própria
- Time dono
- Modelo de domínio independente

**2. Exemplo: "Customer" significa coisas diferentes**

```java
// User Service: Customer = Credenciais + Perfil
@Entity
public class Customer {
    private UUID id;
    private String email;
    private String password;
    private String name;
}

// Order Service: Customer = Endereço + Histórico de Compras
@Entity
public class Customer {
    private UUID id;
    private String shippingAddress;
    private List<OrderId> orderHistory;
}

// Payment Service: Customer = Informações de Pagamento
@Entity
public class Customer {
    private UUID id;
    private String creditCardToken;
    private PaymentMethod preferredMethod;
}
```

---

### 4.2. Decomposição por Capacidade de Negócio

Identifique **capacidades** que a empresa oferece.

#### Exemplo: Sistema Bancário

```
Capacidades de Negócio:
1. Gestão de Contas (Account Management)
2. Transferências (Transfer)
3. Pagamentos (Payment)
4. Investimentos (Investment)
5. Empréstimos (Loan)

        ↓ CADA CAPACIDADE = 1 MICROSERVIÇO

┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│ Account │  │Transfer │  │ Payment │  │Investment│ │  Loan   │
│ Service │  │ Service │  │ Service │  │ Service  │ │ Service │
└─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
```

---

### 4.3. Decomposição por Caso de Uso

Separe serviços baseado em **casos de uso** específicos.

#### Exemplo: Sistema de Pedidos

```
Casos de Uso:
- Criar Pedido
- Aprovar Pedido
- Cancelar Pedido
- Consultar Pedido
- Gerar Relatórios de Pedidos ← Alto volume, leitura intensiva

        ↓ SEPARAR LEITURA vs ESCRITA

┌─────────────────┐          ┌─────────────────┐
│ Order Command   │          │  Order Query    │
│    Service      │─────────▶│    Service      │
│                 │  events  │                 │
│ (Write Model)   │          │ (Read Model)    │
└─────────────────┘          └─────────────────┘
     PostgreSQL                   MongoDB
    (Normalizado)              (Denormalizado
                                 + Agregado)
```

**Vantagem:** Query Service pode escalar independentemente.

---

## 5. Migração de Dados {#migração-de-dados}

### 5.1. O Problema do Shared Database

```
❌ ANTI-PATTERN: Shared Database
┌─────────┐  ┌─────────┐  ┌─────────┐
│  User   │  │  Order  │  │ Payment │
│ Service │  │ Service │  │ Service │
└────┬────┘  └────┬────┘  └────┬────┘
     │            │            │
     └────────────┼────────────┘
                  ↓
          ┌───────────────┐
          │   DATABASE    │
          │               │
          │ users         │
          │ orders        │
          │ payments      │
          └───────────────┘

PROBLEMAS:
- Acoplamento de schema
- Não pode escalar independentemente
- Deploy acoplado (migrations)
- Sem ownership claro
```

**Solução:** Database per Service.

---

### 5.2. Estratégia: Database per Service

```
✅ CORRETO: Database per Service
┌─────────┐  ┌─────────┐  ┌─────────┐
│  User   │  │  Order  │  │ Payment │
│ Service │  │ Service │  │ Service │
└────┬────┘  └────┬────┘  └────┬────┘
     │            │            │
     ↓            ↓            ↓
┌─────────┐  ┌─────────┐  ┌─────────┐
│  User   │  │  Order  │  │ Payment │
│   DB    │  │   DB    │  │   DB    │
└─────────┘  └─────────┘  └─────────┘
```

#### Passos para Migração

**Passo 1: Identificar Tabelas por Contexto**

```sql
-- MONOLITO: Banco único
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255),
    password VARCHAR(255)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID,  -- ← Foreign Key para users
    amount DECIMAL
);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID,  -- ← Foreign Key para orders
    status VARCHAR(50)
);
```

**Análise:**
- `users` → User Service DB
- `orders` → Order Service DB
- `payments` → Payment Service DB

**PROBLEMA:** Foreign keys entre contextos!

---

**Passo 2: Quebrar Foreign Keys**

```sql
-- ❌ ANTES (Monolito)
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),  -- ← FK
    amount DECIMAL
);

-- ✅ DEPOIS (Order Service DB)
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID,  -- ← Sem FK! Apenas referência lógica
    amount DECIMAL
);
```

**Validação move para aplicação:**

```java
@Service
public class OrderService {

    private final UserServiceClient userClient;

    public Order createOrder(OrderRequest request) {
        // Valida user via HTTP (não via FK)
        try {
            userClient.getUser(request.getUserId());
        } catch (UserNotFoundException e) {
            throw new InvalidOrderException("User not found");
        }

        // Cria order
        Order order = Order.create(...);
        orderRepository.save(order);

        return order;
    }
}
```

---

**Passo 3: Migrar Dados (Split Database)**

##### Abordagem 1: Stop-the-World (Downtime)

```bash
# 1. Para aplicação
systemctl stop monolith

# 2. Backup do banco
pg_dump monolith > backup.sql

# 3. Cria novos bancos
createdb user_service_db
createdb order_service_db
createdb payment_service_db

# 4. Migra tabelas
psql user_service_db < migrate_users.sql
psql order_service_db < migrate_orders.sql
psql payment_service_db < migrate_payments.sql

# 5. Inicia microserviços
docker-compose up -d
```

**Desvantagem:** Downtime (pode ser horas para grandes bancos).

---

##### Abordagem 2: Zero-Downtime (Dual Write)

**FASE 1: Dual Write**

```java
@Service
public class OrderService {

    private final OrderRepository newOrderRepository;      // Novo banco
    private final LegacyOrderRepository legacyRepository;  // Banco antigo

    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = Order.create(...);

        // 1. Salva no banco LEGADO (monolito ainda lê daqui)
        legacyRepository.save(order);

        // 2. Salva no banco NOVO (microserviço lê daqui)
        newOrderRepository.save(order);

        return order;
    }
}
```

**FASE 2: Migração em Background**

```java
@Service
public class DataMigrationService {

    @Scheduled(fixedDelay = 1000) // Roda a cada 1s
    public void migrateOrders() {
        // Busca orders que ainda não foram migradas
        List<LegacyOrder> pendingOrders = legacyRepository
            .findByMigratedFalse(PageRequest.of(0, 100));

        for (LegacyOrder legacyOrder : pendingOrders) {
            // Converte e salva no novo banco
            Order newOrder = convert(legacyOrder);
            newOrderRepository.save(newOrder);

            // Marca como migrada
            legacyOrder.setMigrated(true);
            legacyRepository.save(legacyOrder);
        }
    }
}
```

**FASE 3: Validação**

```java
@Service
public class DataValidationService {

    public void validateMigration() {
        long legacyCount = legacyRepository.count();
        long newCount = newOrderRepository.count();

        if (legacyCount != newCount) {
            throw new MigrationException(
                "Count mismatch: legacy=" + legacyCount + ", new=" + newCount
            );
        }

        // Valida sample de registros
        List<LegacyOrder> sample = legacyRepository.findSample(1000);
        for (LegacyOrder legacy : sample) {
            Order newOrder = newOrderRepository.findById(legacy.getId());
            if (!equals(legacy, newOrder)) {
                throw new MigrationException("Data mismatch for id=" + legacy.getId());
            }
        }
    }
}
```

**FASE 4: Switch (Feature Toggle)**

```java
@Service
public class OrderService {

    @Value("${feature.read-from-new-db}")
    private boolean readFromNewDb;

    public Order getOrder(UUID id) {
        if (readFromNewDb) {
            return newOrderRepository.findById(id);  // ← Novo
        } else {
            return legacyRepository.findById(id);     // ← Legado
        }
    }
}
```

```properties
# Gradualmente liga novo banco
feature.read-from-new-db=false  # 0% tráfego
feature.read-from-new-db=true   # 100% tráfego (depois de validar)
```

**FASE 5: Remover Dual Write**

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository; // Apenas novo banco

    @Transactional
    public Order createOrder(OrderRequest request) {
        Order order = Order.create(...);
        orderRepository.save(order); // ← Apenas novo
        return order;
    }
}
```

---

### 5.3. Lidando com Queries JOIN

**PROBLEMA:** Como fazer queries que antes usavam JOIN?

```sql
-- ❌ IMPOSSÍVEL em microserviços (bancos separados)
SELECT o.id, o.amount, u.name, u.email
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE u.email = 'user@example.com';
```

#### Solução 1: API Composition

```java
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userClient;

    public List<OrderWithUserResponse> getOrdersWithUser(String email) {
        // 1. Busca user por email (chama User Service)
        UserResponse user = userClient.getUserByEmail(email);

        // 2. Busca orders do user (local)
        List<Order> orders = orderRepository.findByUserId(user.getId());

        // 3. Combina resultados
        return orders.stream()
            .map(order -> new OrderWithUserResponse(
                order.getId(),
                order.getAmount(),
                user.getName(),
                user.getEmail()
            ))
            .toList();
    }
}
```

**Desvantagem:** Múltiplas chamadas HTTP (latência).

---

#### Solução 2: CQRS com Read Model Denormalizado

```java
// Event Handler no Order Service
@Service
public class OrderEventHandler {

    private final OrderReadModelRepository readModelRepository;

    @KafkaListener(topics = "user.updated.v1")
    public void handleUserUpdated(UserUpdatedEvent event) {
        // Atualiza read model com dados do user
        List<OrderReadModel> orders = readModelRepository
            .findByUserId(event.getUserId());

        for (OrderReadModel order : orders) {
            order.setUserName(event.getName());
            order.setUserEmail(event.getEmail());
            readModelRepository.save(order);
        }
    }
}

// Read Model (denormalizado)
@Entity
public class OrderReadModel {
    @Id
    private UUID id;
    private UUID userId;
    private BigDecimal amount;

    // ✅ Dados denormalizados do User
    private String userName;
    private String userEmail;
}
```

**Query rápida:**

```java
@Repository
public interface OrderReadModelRepository extends JpaRepository<OrderReadModel, UUID> {

    // ✅ Query local (sem JOIN entre serviços)
    List<OrderReadModel> findByUserEmail(String email);
}
```

---

## 6. Processo Passo a Passo {#processo-passo-a-passo}

### 6.1. Checklist Completo de Migração

#### FASE 1: Preparação (2-4 semanas)

- [ ] **Mapear dependências** do monolito (diagramas)
- [ ] **Identificar bounded contexts** (DDD)
- [ ] **Analisar acoplamentos** (matriz de acoplamento)
- [ ] **Definir ordem de migração** (serviços menos acoplados primeiro)
- [ ] **Configurar infraestrutura**:
  - [ ] Docker/Kubernetes
  - [ ] CI/CD pipelines
  - [ ] Monitoramento distribuído (Zipkin, Prometheus, Grafana)
  - [ ] API Gateway (Spring Cloud Gateway)
  - [ ] Service Discovery (Eureka)
- [ ] **Treinar time** em microserviços
- [ ] **Criar repositórios** para cada microserviço

---

#### FASE 2: Primeiro Microserviço (4-6 semanas)

**Escolha um serviço de baixo risco:**
- Baixo acoplamento
- Não crítico para negócio
- Bom caso de uso para aprendizado

**Passos:**

- [ ] **Extrair código** do monolito
  ```bash
  # Cria novo projeto
  mkdir order-service
  cd order-service
  spring init --dependencies=web,data-jpa,kafka order-service
  ```

- [ ] **Configurar banco independente**
  ```yaml
  # docker-compose.yml
  services:
    order-db:
      image: postgres:15
      environment:
        POSTGRES_DB: order_service
        POSTGRES_USER: order_user
        POSTGRES_PASSWORD: order_pass
  ```

- [ ] **Implementar cliente HTTP** para comunicação com monolito
  ```java
  @FeignClient(name = "monolith", url = "${monolith.url}")
  public interface MonolithClient {
      @GetMapping("/api/users/{id}")
      UserResponse getUser(@PathVariable UUID id);
  }
  ```

- [ ] **Configurar rota no API Gateway**
  ```yaml
  spring:
    cloud:
      gateway:
        routes:
          - id: order-service
            uri: http://order-service:8081
            predicates:
              - Path=/api/orders/**
  ```

- [ ] **Implementar Dual Write** (se necessário)
  ```java
  // Salva em ambos bancos durante transição
  legacyRepository.save(order);
  newRepository.save(order);
  ```

- [ ] **Testes**:
  - [ ] Testes unitários
  - [ ] Testes de integração (Testcontainers)
  - [ ] Testes de contrato (Pact)
  - [ ] Testes end-to-end

- [ ] **Deploy em ambiente de staging**

- [ ] **Validação com Feature Toggle**
  ```properties
  feature.use-new-order-service=false
  ```

- [ ] **Deploy em produção (canary)**
  - [ ] 10% tráfego → novo serviço
  - [ ] Monitorar métricas (latência, erros)
  - [ ] 50% tráfego
  - [ ] 100% tráfego

- [ ] **Remover código legado** do monolito (quando estável)

---

#### FASE 3: Migração em Escala (3-12 meses)

Repita processo para cada serviço:

**Mês 1-2:**
- [ ] Extrair serviço #2 (ex: Payment Service)

**Mês 3-4:**
- [ ] Extrair serviço #3 (ex: User Service)

**Mês 5-6:**
- [ ] Extrair serviço #4 (ex: Inventory Service)

**Mês 7-8:**
- [ ] Migrar dados (database split)
- [ ] Implementar CQRS onde necessário

**Mês 9-10:**
- [ ] Implementar Saga Pattern para transações distribuídas
- [ ] Implementar Circuit Breaker em todos serviços

**Mês 11-12:**
- [ ] Otimizar comunicação (cache, batch requests)
- [ ] Desligar monolito (se aplicável)

---

### 6.2. Exemplo Real: Migração de Payment

#### Estado Inicial: Payment no Monolito

```java
// ===== MONOLITO =====
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderRepository orderRepository; // ← Acoplamento!

    @PostMapping
    public PaymentResponse createPayment(@RequestBody PaymentRequest request) {
        // Busca order diretamente (mesmo banco)
        Order order = orderRepository.findById(request.getOrderId())
            .orElseThrow();

        // Cria payment
        Payment payment = paymentService.createPayment(order);

        return new PaymentResponse(payment);
    }
}
```

---

#### Passo 1: Refatorar Monolito (Preparação)

```java
// ===== MONOLITO (REFATORADO) =====
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderService orderService; // ← Usa serviço, não repository

    @PostMapping
    public PaymentResponse createPayment(@RequestBody PaymentRequest request) {
        // ✅ Usa abstração (fácil de substituir depois)
        Order order = orderService.getOrder(request.getOrderId());

        Payment payment = paymentService.createPayment(order);

        return new PaymentResponse(payment);
    }
}

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }
}
```

**Commit:** "Refactor: Use OrderService abstraction in PaymentController"

---

#### Passo 2: Extrair Payment Service

```java
// ===== NOVO: Payment Service =====
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
        @RequestBody PaymentRequest request
    ) {
        Payment payment = paymentService.createPayment(request);
        return ResponseEntity.ok(new PaymentResponse(payment));
    }
}

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderClient; // ← HTTP client
    private final KafkaTemplate<String, PaymentCreatedEvent> kafkaTemplate;

    @Transactional
    public Payment createPayment(PaymentRequest request) {
        // 1. Busca order via HTTP (Order Service ou Monolito)
        OrderResponse order = orderClient.getOrder(request.getOrderId());

        // 2. Valida
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidPaymentException("Order not pending");
        }

        // 3. Cria payment
        Payment payment = Payment.create(
            PaymentId.generate(),
            OrderId.of(request.getOrderId()),
            Money.of(order.getAmount(), "BRL")
        );

        // 4. Salva (banco isolado)
        paymentRepository.save(payment);

        // 5. Publica evento
        kafkaTemplate.send(
            "payment.created.v1",
            new PaymentCreatedEvent(payment.getId(), payment.getOrderId())
        );

        return payment;
    }
}

// Cliente HTTP
@FeignClient(name = "order-service", url = "${order.service.url}")
public interface OrderServiceClient {

    @GetMapping("/api/orders/{id}")
    OrderResponse getOrder(@PathVariable UUID id);
}
```

**Configuração:**

```yaml
# application.yml (Payment Service)
spring:
  datasource:
    url: jdbc:postgresql://payment-db:5432/payment_service
    username: payment_user
    password: payment_pass

  kafka:
    bootstrap-servers: kafka:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

order:
  service:
    url: http://api-gateway:8080  # ← Chama via Gateway
```

---

#### Passo 3: Atualizar API Gateway

```yaml
# application.yml (API Gateway)
spring:
  cloud:
    gateway:
      routes:
        # NOVO: Rota para Payment Service
        - id: payment-service
          uri: http://payment-service:8082
          predicates:
            - Path=/api/payments/**
          filters:
            - name: CircuitBreaker
              args:
                name: paymentCircuitBreaker
                fallbackUri: forward:/fallback/payments

        # Rota para Order (pode ser monolito ou microserviço)
        - id: order-service
          uri: http://order-service:8081
          predicates:
            - Path=/api/orders/**

        # Fallback → Monolito
        - id: monolith
          uri: http://monolith:8080
          predicates:
            - Path=/**
```

---

#### Passo 4: Feature Toggle no Monolito

```java
// ===== MONOLITO (com toggle) =====
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Value("${feature.use-new-payment-service}")
    private boolean useNewService;

    @Autowired
    private PaymentService legacyPaymentService;

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping
    public PaymentResponse createPayment(@RequestBody PaymentRequest request) {

        if (useNewService) {
            // ✅ Delega para novo serviço
            return restTemplate.postForObject(
                "http://payment-service:8082/api/payments",
                request,
                PaymentResponse.class
            );
        } else {
            // ❌ Usa código legado
            Payment payment = legacyPaymentService.createPayment(request);
            return new PaymentResponse(payment);
        }
    }
}
```

**Configuração (staging):**

```properties
# application.properties
feature.use-new-payment-service=true  # ← Testa novo serviço
```

**Configuração (produção - início):**

```properties
feature.use-new-payment-service=false  # ← Ainda usa legado
```

---

#### Passo 5: Canary Release

**Dia 1:**
```properties
feature.use-new-payment-service=false  # 0% tráfego
```

**Dia 2: (monitorar métricas)**
```properties
feature.use-new-payment-service=true   # 10% tráfego (via load balancer)
```

**Dia 3:**
```properties
# Se sem erros → 50% tráfego
```

**Dia 4:**
```properties
# Se sem erros → 100% tráfego
```

**Monitoramento:**

```java
@RestController
public class PaymentHealthController {

    @GetMapping("/actuator/health")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    @GetMapping("/actuator/metrics")
    public MetricsResponse metrics() {
        return new MetricsResponse(
            paymentRepository.count(),
            successRate,
            avgLatency
        );
    }
}
```

**Dashboard Grafana:**
```
Payment Service Metrics:
- Total Payments: 1,234
- Success Rate: 99.8%
- Avg Latency: 120ms
- Error Rate: 0.2%
```

**Se tudo OK:**
- [ ] Remove código legado do monolito
- [ ] Remove feature toggle
- [ ] Payment Service agora é produção

---

## 7. Anti-Corruption Layer {#anti-corruption-layer}

O **Anti-Corruption Layer (ACL)** protege seu novo microserviço de conceitos legados do monolito.

### 7.1. O Problema

```java
// ❌ PROBLEMA: Microserviço usa modelo do monolito
@Service
public class OrderService {

    private final LegacyUserClient legacyClient;

    public Order createOrder(OrderRequest request) {
        // Chama monolito e recebe modelo legado
        LegacyUser legacyUser = legacyClient.getUser(request.getUserId());

        // ❌ Código poluído com conceitos legados
        String userName = legacyUser.getFirstName() + " " + legacyUser.getLastName();
        String status = legacyUser.getStatus(); // "A" = Active, "I" = Inactive (????)

        if (!"A".equals(status)) {
            throw new InvalidUserException();
        }

        // ...
    }
}
```

**Problemas:**
- Modelo legado vaza para novo serviço
- Código fica acoplado a conceitos antigos
- Dificulta evolução

---

### 7.2. Solução: ACL com Adapter

```java
// ===== ADAPTER (Anti-Corruption Layer) =====
@Component
public class LegacyUserAdapter {

    private final LegacyUserClient legacyClient;

    // ✅ Converte modelo legado → modelo novo
    public User getUser(UUID userId) {
        LegacyUser legacyUser = legacyClient.getUser(userId);

        // Traduz conceitos legados
        return new User(
            legacyUser.getId(),
            toFullName(legacyUser),
            toUserStatus(legacyUser.getStatus())
        );
    }

    private String toFullName(LegacyUser legacyUser) {
        return legacyUser.getFirstName() + " " + legacyUser.getLastName();
    }

    private UserStatus toUserStatus(String legacyStatus) {
        return switch (legacyStatus) {
            case "A" -> UserStatus.ACTIVE;
            case "I" -> UserStatus.INACTIVE;
            case "B" -> UserStatus.BLOCKED;
            default -> throw new IllegalArgumentException("Unknown status: " + legacyStatus);
        };
    }
}

// ===== SERVIÇO LIMPO =====
@Service
public class OrderService {

    private final LegacyUserAdapter userAdapter; // ← Usa adapter

    public Order createOrder(OrderRequest request) {
        // ✅ Recebe modelo limpo (novo)
        User user = userAdapter.getUser(request.getUserId());

        // ✅ Código limpo
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidUserException("User is not active");
        }

        // ...
    }
}
```

**Vantagem:** Quando o monolito for substituído, basta trocar o adapter.

---

### 7.3. ACL para Eventos

```java
// Evento legado (formato antigo)
public record LegacyPaymentEvent(
    String paymentId,          // ← String (deveria ser UUID)
    String orderId,
    Double amount,             // ← Double (não use para dinheiro!)
    String currency,
    String status              // ← "APPROVED", "REJECTED" (String)
) {}

// ===== ADAPTER =====
@Component
public class PaymentEventAdapter {

    public PaymentApprovedEvent toPaymentApprovedEvent(LegacyPaymentEvent legacy) {
        return new PaymentApprovedEvent(
            UUID.fromString(legacy.paymentId()),
            UUID.fromString(legacy.orderId()),
            BigDecimal.valueOf(legacy.amount()),  // ← Converte para BigDecimal
            Currency.getInstance(legacy.currency()),
            PaymentStatus.valueOf(legacy.status())
        );
    }
}

// ===== CONSUMER =====
@Service
public class PaymentEventConsumer {

    private final PaymentEventAdapter adapter;

    @KafkaListener(topics = "legacy.payment.events")
    public void handleLegacyPayment(LegacyPaymentEvent legacyEvent) {
        // ✅ Converte para modelo novo
        PaymentApprovedEvent event = adapter.toPaymentApprovedEvent(legacyEvent);

        // Processa com modelo limpo
        processPayment(event);
    }
}
```

---

## 8. Gerenciando Ambos os Sistemas {#gerenciando-ambos-os-sistemas}

Durante a migração, você terá **2 sistemas rodando em paralelo**.

### 8.1. Arquitetura de Transição

```
FASE DE TRANSIÇÃO:
                      ┌─────────────┐
                      │ API Gateway │
                      └──────┬──────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ↓                    ↓                    ↓
  ┌──────────┐         ┌──────────┐       ┌─────────────────┐
  │  Order   │         │ Payment  │       │   MONOLITO      │
  │ Service  │         │ Service  │       │  ┌──────────┐   │
  └──────────┘         └──────────┘       │  │   User   │   │
        ↓                    ↓             │  │ Inventory│   │
  ┌──────────┐         ┌──────────┐       │  │ Shipping │   │
  │ Order DB │         │ Payment  │       │  └──────────┘   │
  └──────────┘         │   DB     │       └─────────────────┘
                       └──────────┘              ↓
                                          ┌─────────────┐
                                          │ Monolith DB │
                                          └─────────────┘

COMUNICAÇÃO:
- Order Service → User (via HTTP para monolito)
- Payment Service → Order Service (via HTTP)
- Monolith → Order Service (via HTTP)
```

---

### 8.2. Gerenciamento de Configuração

Use **Spring Cloud Config** para gerenciar configurações centralizadas.

```yaml
# config-server/application.yml
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/company/config-repo
          search-paths: '{application}'
```

**Estrutura de configurações:**

```
config-repo/
├── order-service.yml
├── payment-service.yml
├── monolith.yml
└── gateway.yml
```

**Exemplo: order-service.yml**

```yaml
# config-repo/order-service.yml
spring:
  datasource:
    url: jdbc:postgresql://order-db:5432/order_service
    username: ${DB_USER}
    password: ${DB_PASS}

# URLs de outros serviços
services:
  user:
    url: http://monolith:8080  # ← Ainda no monolito
  payment:
    url: http://payment-service:8082  # ← Já migrado
  inventory:
    url: http://monolith:8080  # ← Ainda no monolito

# Feature toggles
features:
  dual-write-enabled: true  # ← Escreve em ambos bancos
  read-from-new-db: true    # ← Lê do novo banco
```

**Vantagem:** Atualiza configs sem redeploy.

---

### 8.3. Versionamento de APIs

Durante a transição, mantenha **2 versões** da API.

```java
// ===== API v1 (Legada - mantém compatibilidade) =====
@RestController
@RequestMapping("/api/v1/orders")
public class OrderControllerV1 {

    @PostMapping
    public OrderResponseV1 createOrder(@RequestBody OrderRequestV1 request) {
        // Mantém formato antigo para clientes existentes
        Order order = orderService.createOrder(request);
        return new OrderResponseV1(order);
    }
}

// ===== API v2 (Nova - melhorias) =====
@RestController
@RequestMapping("/api/v2/orders")
public class OrderControllerV2 {

    @PostMapping
    public OrderResponseV2 createOrder(@RequestBody OrderRequestV2 request) {
        // Novo formato com mais campos
        Order order = orderService.createOrder(request);
        return new OrderResponseV2(order);
    }
}
```

**Response V1 (compatibilidade):**

```json
{
  "orderId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 100.50,
  "status": "PENDING"
}
```

**Response V2 (nova):**

```json
{
  "orderId": "123e4567-e89b-12d3-a456-426614174000",
  "userId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": {
    "value": 100.50,
    "currency": "BRL"
  },
  "status": "PENDING",
  "createdAt": "2025-12-09T10:00:00Z",
  "items": [
    { "productId": "...", "quantity": 2 }
  ]
}
```

**Deprecação gradual:**

```java
@RestController
@RequestMapping("/api/v1/orders")
@Deprecated // ← Marca como deprecated
public class OrderControllerV1 {

    @PostMapping
    public ResponseEntity<OrderResponseV1> createOrder(
        @RequestBody OrderRequestV1 request
    ) {
        // Adiciona header de deprecação
        return ResponseEntity.ok()
            .header("X-API-Deprecated", "true")
            .header("X-API-Sunset", "2026-06-01")  // ← Data de desativação
            .header("Link", "</api/v2/orders>; rel=\"successor-version\"")
            .body(new OrderResponseV1(order));
    }
}
```

---

## 9. Estratégias de Teste Durante Migração {#estratégias-de-teste}

### 9.1. Testes de Compatibilidade

Valide que o novo serviço é compatível com o legado.

```java
@SpringBootTest
public class PaymentServiceCompatibilityTest {

    @Autowired
    private PaymentService newPaymentService;

    @Autowired
    private LegacyPaymentService legacyService;

    @Test
    public void shouldProduceSameResultAsLegacy() {
        // Arrange
        PaymentRequest request = new PaymentRequest(
            orderId,
            Money.of(100.00, "BRL")
        );

        // Act
        Payment newResult = newPaymentService.createPayment(request);
        Payment legacyResult = legacyService.createPayment(request);

        // Assert
        assertThat(newResult.getId()).isEqualTo(legacyResult.getId());
        assertThat(newResult.getAmount()).isEqualTo(legacyResult.getAmount());
        assertThat(newResult.getStatus()).isEqualTo(legacyResult.getStatus());
    }
}
```

---

### 9.2. Contract Testing com Pact

Garanta que serviços se comunicam corretamente.

```java
// ===== CONSUMER (Order Service) =====
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service")
public class OrderServicePaymentContractTest {

    @Pact(consumer = "order-service")
    public RequestResponsePact createPaymentPact(PactDslWithProvider builder) {
        return builder
            .given("order exists")
            .uponReceiving("create payment request")
                .path("/api/payments")
                .method("POST")
                .body(new PactDslJsonBody()
                    .uuid("orderId", "123e4567-e89b-12d3-a456-426614174000")
                    .decimalType("amount", 100.50))
            .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                    .uuid("paymentId")
                    .stringValue("status", "PENDING"))
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPaymentPact")
    public void testCreatePayment(MockServer mockServer) {
        // Configura client para usar mock
        paymentClient.setBaseUrl(mockServer.getUrl());

        // Testa contrato
        PaymentResponse response = paymentClient.createPayment(...);

        assertThat(response.getStatus()).isEqualTo("PENDING");
    }
}

// ===== PROVIDER (Payment Service) =====
@Provider("payment-service")
@PactBroker(url = "http://pact-broker:9292")
public class PaymentServiceContractTest {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("order exists")
    public void orderExists() {
        // Setup: Cria order no banco de testes
        orderRepository.save(new Order(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            ...
        ));
    }
}
```

---

### 9.3. Shadow Testing

Execute novo serviço em "modo sombra" sem afetar produção.

```java
@Service
public class OrderService {

    private final LegacyOrderService legacyService;
    private final NewOrderService newService;

    @Value("${shadow.testing.enabled}")
    private boolean shadowTestingEnabled;

    public Order createOrder(OrderRequest request) {
        // 1. SEMPRE executa legado (produção)
        Order result = legacyService.createOrder(request);

        // 2. Se shadow testing habilitado, executa novo em paralelo
        if (shadowTestingEnabled) {
            CompletableFuture.runAsync(() -> {
                try {
                    Order shadowResult = newService.createOrder(request);

                    // Compara resultados (não afeta produção)
                    comparator.compare(result, shadowResult);

                } catch (Exception e) {
                    log.warn("Shadow service failed", e);
                    metrics.counter("shadow.failures").increment();
                }
            });
        }

        // 3. Retorna resultado do LEGADO
        return result;
    }
}
```

---

## 10. Rollback e Planos B {#rollback-e-planos-b}

### 10.1. Estratégias de Rollback

#### Rollback Nível 1: Feature Toggle

```java
@Value("${feature.use-new-payment-service}")
private boolean useNewService;

public Payment createPayment(PaymentRequest request) {
    if (useNewService) {
        return newPaymentService.createPayment(request);
    } else {
        return legacyPaymentService.createPayment(request); // ← Rollback instantâneo
    }
}
```

**Vantagem:** Rollback em **segundos** (apenas muda config).

---

#### Rollback Nível 2: Traffic Shifting (Kubernetes)

```yaml
# k8s/payment-service.yml
apiVersion: v1
kind: Service
metadata:
  name: payment-service
spec:
  selector:
    app: payment
    version: v2  # ← Muda para v1 se precisar rollback
  ports:
    - port: 8082
      targetPort: 8082
```

**Rollback:**

```bash
# Reverte para versão antiga
kubectl set image deployment/payment-service payment=payment:v1

# Valida
kubectl rollout status deployment/payment-service
```

---

#### Rollback Nível 3: Database Rollback

```sql
-- Se migração de dados falhou, reverte
BEGIN;

-- 1. Restaura dados do backup
COPY payments FROM '/backup/payments_backup.csv' WITH CSV;

-- 2. Valida
SELECT COUNT(*) FROM payments;

-- 3. Commit se OK
COMMIT;
```

**Automação:**

```bash
#!/bin/bash
# rollback-database.sh

echo "Rolling back Payment Service database..."

# Para serviço
kubectl scale deployment/payment-service --replicas=0

# Restaura backup
pg_restore -d payment_service /backups/payment_service_$(date +%Y%m%d).dump

# Valida
psql -d payment_service -c "SELECT COUNT(*) FROM payments;"

# Reinicia serviço
kubectl scale deployment/payment-service --replicas=3
```

---

### 10.2. Circuit Breaker para Resiliência

Use **Resilience4j** para isolar falhas.

```java
@Service
public class OrderService {

    private final PaymentServiceClient paymentClient;

    @CircuitBreaker(
        name = "payment-service",
        fallbackMethod = "createOrderWithoutPayment"
    )
    @Retry(name = "payment-service", fallbackMethod = "createOrderWithoutPayment")
    public Order createOrder(OrderRequest request) {
        // Tenta criar payment
        PaymentResponse payment = paymentClient.createPayment(...);

        // Cria order com payment confirmado
        Order order = Order.create(...);
        order.setPaymentId(payment.getId());

        return orderRepository.save(order);
    }

    // ✅ FALLBACK: Payment Service está down
    public Order createOrderWithoutPayment(OrderRequest request, Exception ex) {
        log.warn("Payment Service unavailable, creating order with pending payment", ex);

        // Cria order SEM payment (será processado depois)
        Order order = Order.create(...);
        order.setPaymentStatus(PaymentStatus.PENDING);

        orderRepository.save(order);

        // Publica evento para processar payment depois
        kafkaTemplate.send(
            "order.payment-pending.v1",
            new OrderPaymentPendingEvent(order.getId())
        );

        return order;
    }
}
```

**Configuração:**

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      payment-service:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3

  retry:
    instances:
      payment-service:
        maxAttempts: 3
        waitDuration: 1s
```

---

### 10.3. Plano de Contingência Completo

```markdown
# PLANO DE ROLLBACK - Payment Service

## Cenário 1: Serviço com erros (< 1 hora)
**Sintomas:** Error rate > 5%
**Ação:**
1. Desabilitar feature toggle: `feature.use-new-payment-service=false`
2. Monitorar por 5 minutos
3. Investigar logs

## Cenário 2: Serviço instável (1-4 horas)
**Sintomas:** Error rate > 10%, latência > 2s
**Ação:**
1. Rollback Kubernetes: `kubectl rollout undo deployment/payment-service`
2. Escalar monolito: `kubectl scale deployment/monolith --replicas=5`
3. Postmortem meeting

## Cenário 3: Perda de dados (> 4 horas)
**Sintomas:** Dados inconsistentes, transações perdidas
**Ação:**
1. PARAR TODOS os deployments
2. Restaurar backup de database
3. Validar integridade dos dados
4. Comunicar stakeholders
5. Análise forense completa

## Contacts
- On-call engineer: +55 11 99999-9999
- Database admin: +55 11 88888-8888
- CTO: +55 11 77777-7777
```

---

## 11. Migração de Infraestrutura {#migração-de-infraestrutura}

### 11.1. De VM para Containers

**ANTES: VMs**

```
┌─────────────────────────────────┐
│   VM 1 (4GB RAM, 2 vCPUs)       │
│   - Monolito                    │
│   - PostgreSQL                  │
│   - Nginx                       │
└─────────────────────────────────┘
```

**DEPOIS: Containers**

```
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Container 1  │ │ Container 2  │ │ Container 3  │
│ Order Service│ │Payment Service│ │ PostgreSQL   │
│ (512MB)      │ │ (512MB)      │ │ (2GB)        │
└──────────────┘ └──────────────┘ └──────────────┘
```

#### Dockerfile

```dockerfile
# Dockerfile (Payment Service)
FROM eclipse-temurin:21-jre-alpine

# Adiciona usuário não-root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copia JAR
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

# Configurações JVM
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]
```

#### Docker Compose (Desenvolvimento)

```yaml
# docker-compose.yml
version: '3.8'

services:
  order-service:
    build: ./order-service
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://order-db:5432/order_service
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - order-db
      - kafka

  payment-service:
    build: ./payment-service
    ports:
      - "8082:8082"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://payment-db:5432/payment_service
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - payment-db
      - kafka

  order-db:
    image: postgres:15
    environment:
      POSTGRES_DB: order_service
      POSTGRES_USER: order_user
      POSTGRES_PASSWORD: order_pass
    volumes:
      - order-data:/var/lib/postgresql/data

  payment-db:
    image: postgres:15
    environment:
      POSTGRES_DB: payment_service
      POSTGRES_USER: payment_user
      POSTGRES_PASSWORD: payment_pass
    volumes:
      - payment-data:/var/lib/postgresql/data

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

volumes:
  order-data:
  payment-data:
```

---

### 11.2. Kubernetes (Produção)

#### Deployment

```yaml
# k8s/payment-service-deployment.yml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  labels:
    app: payment
spec:
  replicas: 3
  selector:
    matchLabels:
      app: payment
  template:
    metadata:
      labels:
        app: payment
        version: v1
    spec:
      containers:
      - name: payment
        image: company/payment-service:1.0.0
        ports:
        - containerPort: 8082
        env:
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: payment-db-secret
              key: url
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: payment-db-secret
              key: password
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 5
```

#### Service

```yaml
# k8s/payment-service-service.yml
apiVersion: v1
kind: Service
metadata:
  name: payment-service
spec:
  selector:
    app: payment
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8082
  type: ClusterIP
```

#### HorizontalPodAutoscaler

```yaml
# k8s/payment-service-hpa.yml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

#### Deploy

```bash
# Cria namespace
kubectl create namespace production

# Aplica configurações
kubectl apply -f k8s/payment-service-deployment.yml -n production
kubectl apply -f k8s/payment-service-service.yml -n production
kubectl apply -f k8s/payment-service-hpa.yml -n production

# Valida
kubectl get pods -n production
kubectl get svc -n production
kubectl get hpa -n production
```

---

## 12. Comunicação e Sincronização {#comunicação-e-sincronização}

### 12.1. Saga Pattern para Transações Distribuídas

**PROBLEMA:** Como criar Order + Payment + Inventory em transação atômica?

```
❌ IMPOSSÍVEL: Transação distribuída
@Transactional  // ← Não funciona entre microserviços!
public void createOrder(OrderRequest request) {
    orderService.createOrder(request);
    paymentService.createPayment(request);
    inventoryService.reserveItems(request);
}
```

**SOLUÇÃO:** Saga Pattern (Choreography).

```
✅ SAGA: Orquestração via eventos
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ POST /orders
       ↓
┌─────────────────────────────────────────┐
│         Order Service                   │
│ 1. Cria order (status=PENDING)          │
│ 2. Publica OrderCreatedEvent            │
└──────┬──────────────────────────────────┘
       │ OrderCreatedEvent
       ↓
┌─────────────────────────────────────────┐
│         Payment Service                 │
│ 3. Cria payment                         │
│ 4. Publica PaymentApprovedEvent ou      │
│    PaymentFailedEvent                   │
└──────┬──────────────────────────────────┘
       │ PaymentApprovedEvent
       ↓
┌─────────────────────────────────────────┐
│         Inventory Service               │
│ 5. Reserva items                        │
│ 6. Publica InventoryReservedEvent ou    │
│    InventoryFailedEvent                 │
└──────┬──────────────────────────────────┘
       │ InventoryReservedEvent
       ↓
┌─────────────────────────────────────────┐
│         Order Service                   │
│ 7. Atualiza order (status=CONFIRMED)    │
└─────────────────────────────────────────┘

SE FALHAR:
┌─────────────────────────────────────────┐
│         Payment Service                 │
│ Publica PaymentFailedEvent              │
└──────┬──────────────────────────────────┘
       │ PaymentFailedEvent
       ↓
┌─────────────────────────────────────────┐
│         Order Service                   │
│ Cancela order (status=CANCELLED)        │
│ Publica OrderCancelledEvent             │
└──────┬──────────────────────────────────┘
       │ OrderCancelledEvent
       ↓
┌─────────────────────────────────────────┐
│         Inventory Service               │
│ Libera items reservados                 │
└─────────────────────────────────────────┘
```

#### Implementação

**Order Service:**

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. Cria order (status PENDING)
        Order order = Order.create(
            OrderId.generate(),
            UserId.of(request.getUserId()),
            Money.of(request.getAmount(), "BRL")
        );
        order.setStatus(OrderStatus.PENDING);

        // 2. Salva
        orderRepository.save(order);

        // 3. Publica evento
        kafkaTemplate.send(
            "order.created.v1",
            new OrderCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getAmount()
            )
        );

        return order;
    }

    // Listener para PaymentApprovedEvent
    @KafkaListener(topics = "payment.approved.v1")
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        // Aguarda InventoryReservedEvent para confirmar
        // (não faz nada aqui)
    }

    // Listener para InventoryReservedEvent
    @KafkaListener(topics = "inventory.reserved.v1")
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        // ✅ SUCESSO: Confirma order
        Order order = orderRepository.findByIdOrThrow(event.getOrderId());
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        log.info("Order confirmed: {}", order.getId());
    }

    // Listener para PaymentFailedEvent
    @KafkaListener(topics = "payment.failed.v1")
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // ❌ FALHA: Cancela order
        Order order = orderRepository.findByIdOrThrow(event.getOrderId());
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Publica evento de cancelamento
        kafkaTemplate.send(
            "order.cancelled.v1",
            new OrderCancelledEvent(order.getId())
        );

        log.warn("Order cancelled due to payment failure: {}", order.getId());
    }
}
```

**Payment Service:**

```java
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "order.created.v1")
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            // Cria payment
            Payment payment = Payment.create(
                PaymentId.generate(),
                OrderId.of(event.getOrderId()),
                Money.of(event.getAmount(), "BRL")
            );

            // Processa pagamento (integração com gateway)
            boolean approved = paymentGateway.process(payment);

            if (approved) {
                payment.approve();
                paymentRepository.save(payment);

                // ✅ SUCESSO: Publica evento
                kafkaTemplate.send(
                    "payment.approved.v1",
                    new PaymentApprovedEvent(
                        payment.getId(),
                        event.getOrderId()
                    )
                );
            } else {
                payment.reject();
                paymentRepository.save(payment);

                // ❌ FALHA: Publica evento
                kafkaTemplate.send(
                    "payment.failed.v1",
                    new PaymentFailedEvent(
                        payment.getId(),
                        event.getOrderId(),
                        "Payment rejected by gateway"
                    )
                );
            }
        } catch (Exception e) {
            // ❌ ERRO: Publica evento de falha
            kafkaTemplate.send(
                "payment.failed.v1",
                new PaymentFailedEvent(
                    null,
                    event.getOrderId(),
                    e.getMessage()
                )
            );
        }
    }
}
```

**Inventory Service:**

```java
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "payment.approved.v1")
    @Transactional
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        try {
            // Reserva items
            Inventory inventory = inventoryRepository.findByOrderId(event.getOrderId());
            inventory.reserve();
            inventoryRepository.save(inventory);

            // ✅ SUCESSO: Publica evento
            kafkaTemplate.send(
                "inventory.reserved.v1",
                new InventoryReservedEvent(
                    inventory.getId(),
                    event.getOrderId()
                )
            );
        } catch (OutOfStockException e) {
            // ❌ FALHA: Publica evento
            kafkaTemplate.send(
                "inventory.failed.v1",
                new InventoryFailedEvent(
                    event.getOrderId(),
                    "Out of stock"
                )
            );
        }
    }

    // Listener para OrderCancelledEvent (compensação)
    @KafkaListener(topics = "order.cancelled.v1")
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        // Libera items reservados
        Inventory inventory = inventoryRepository.findByOrderId(event.getOrderId());
        if (inventory != null) {
            inventory.release();
            inventoryRepository.save(inventory);

            log.info("Inventory released for cancelled order: {}", event.getOrderId());
        }
    }
}
```

---

## 13. Armadilhas Comuns {#armadilhas-comuns}

### 13.1. Nano-services (Serviços Pequenos Demais)

```
❌ ERRADO: Nano-services
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ GetUser     │  │ CreateUser  │  │ DeleteUser  │
│ Service     │  │ Service     │  │ Service     │
└─────────────┘  └─────────────┘  └─────────────┘

PROBLEMAS:
- Overhead de rede absurdo
- Complexidade desnecessária
- Deploy nightmare

✅ CORRETO: Bounded Context
┌─────────────────────────────────┐
│       User Service              │
│  - GetUser                      │
│  - CreateUser                   │
│  - DeleteUser                   │
│  - UpdateUser                   │
└─────────────────────────────────┘
```

**Regra:** Um microserviço deve representar um **bounded context**, não uma função.

---

### 13.2. Shared Database

```
❌ ANTI-PATTERN: Shared Database
┌─────────┐  ┌─────────┐
│  Order  │  │ Payment │
│ Service │  │ Service │
└────┬────┘  └────┬────┘
     │            │
     └─────┬──────┘
           ↓
    ┌─────────────┐
    │  DATABASE   │
    └─────────────┘

PROBLEMAS:
- Acoplamento de schema
- Migrações arriscadas
- Sem ownership
```

**Solução:** Database per Service + API para comunicação.

---

### 13.3. Falta de Monitoramento Distribuído

```
❌ PROBLEMA: Logs isolados
Order Service:   [INFO] Order created: 123
Payment Service: [INFO] Payment processing
Inventory Service: [ERROR] Out of stock

Impossível correlacionar!

✅ SOLUÇÃO: Distributed Tracing (Zipkin)
[TRACE_ID: abc123] Order Service:   Order created: 123
[TRACE_ID: abc123] Payment Service: Payment processing
[TRACE_ID: abc123] Inventory Service: ERROR Out of stock
                   ↑ Mesma transação!
```

**Implementação:**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
```

```yaml
# application.yml
spring:
  sleuth:
    sampler:
      probability: 1.0  # 100% das requests (reduzir em prod)
  zipkin:
    base-url: http://zipkin:9411
```

---

### 13.4. Migração Big Bang

```
❌ ERRADO: Big Bang
Sexta 23h: Para monolito
Sábado: Migra TUDO
Domingo: Liga microserviços
Segunda: 🔥🔥🔥 (tudo quebrado)

✅ CORRETO: Incremental
Mês 1: Migra Order Service (10% tráfego)
Mês 2: 50% tráfego
Mês 3: 100% tráfego
Mês 4: Migra Payment Service
...
```

---

### 13.5. Não Planejar Rollback

```
❌ PROBLEMA:
"Migramos tudo para microserviços.
 Agora está dando erro mas não sabemos voltar."

✅ SOLUÇÃO:
- Feature toggles
- Blue/Green deployment
- Database backups automáticos
- Plano de rollback documentado
```

---

## 14. Casos Reais {#casos-reais}

### 14.1. Netflix: De Monolito para Microserviços

**Contexto:**
- 2008: Monolito Java (aplicação única)
- 2009: Migração para AWS começou
- 2011: Primeiros microserviços em produção
- 2016: 700+ microserviços

**Estratégia:**
1. **Strangler Pattern** gradual
2. **API Gateway** (Zuul)
3. **Service Discovery** (Eureka)
4. **Circuit Breaker** (Hystrix)

**Resultado:**
- Deploy 1000x/dia
- Escala global
- 99.99% uptime

---

### 14.2. Amazon: Migração para SOA (Service-Oriented Architecture)

**Contexto:**
- 2001: Monolito "Obidos"
- Problema: Times não conseguiam deployar independentemente

**Estratégia:**
- CEO Jeff Bezos mandou: "Todos os times devem expor dados via APIs"
- "Quem não fizer será demitido"
- Migração forçada em 1 ano

**Resultado:**
- 2002: SOA completo
- Base para AWS (2006)

---

### 14.3. Uber: Migração de Monolito Python para Microserviços

**Contexto:**
- 2012: Monolito Python/PostgreSQL
- Problema: Escalabilidade + Deploy lento

**Estratégia:**
1. **Identificar domínios**: Trips, Payments, Matching, Maps
2. **Migrar serviços críticos** primeiro (Matching)
3. **Database sharding** por cidade
4. **Event-driven** (Kafka)

**Resultado:**
- 2016: 2200+ microserviços
- Latência de matching: 500ms → 50ms

---

## 15. Checklist de Migração {#checklist-de-migração}

### Antes de Começar

- [ ] Time tem experiência com microserviços?
- [ ] Infraestrutura (Docker/K8s) está pronta?
- [ ] CI/CD está automatizado?
- [ ] Monitoramento distribuído configurado?
- [ ] Stakeholders estão alinhados?
- [ ] Há pelo menos 6 meses de prazo?

### Durante a Migração

- [ ] Mapeou dependências do monolito?
- [ ] Identificou bounded contexts?
- [ ] Definiu ordem de migração?
- [ ] Configurou API Gateway?
- [ ] Implementou Service Discovery?
- [ ] Criou adapters (Anti-Corruption Layer)?
- [ ] Configurou feature toggles?
- [ ] Implementou Circuit Breakers?
- [ ] Configurou Distributed Tracing?
- [ ] Criou planos de rollback?
- [ ] Migrou dados (database per service)?
- [ ] Implementou Saga para transações distribuídas?
- [ ] Configurou autoscaling?
- [ ] Criou dashboards de monitoramento?
- [ ] Documentou APIs (OpenAPI/Swagger)?

### Após Migração

- [ ] Removeu código legado do monolito?
- [ ] Desligou monolito (se aplicável)?
- [ ] Realizou postmortem?
- [ ] Documentou lições aprendidas?
- [ ] Treinou time em manutenção?

---

## 16. Exercícios Práticos {#exercícios-práticos}

### Exercício 1: Identificar Bounded Contexts

**Cenário:** Você tem um monolito de e-commerce com as seguintes funcionalidades:

```
Funcionalidades:
1. Registro de usuários
2. Login/Logout
3. Catálogo de produtos
4. Busca de produtos
5. Carrinho de compras
6. Checkout
7. Processamento de pagamento
8. Envio de e-mails
9. Cálculo de frete
10. Rastreamento de pedidos
11. Gestão de estoque
12. Relatórios de vendas
13. Cupons de desconto
```

**Tarefa:** Identifique os bounded contexts e proponha uma decomposição em microserviços.

**Solução:**

```
BOUNDED CONTEXTS:

1. User Context
   - Registro
   - Login/Logout
   → User Service

2. Catalog Context
   - Produtos
   - Busca
   → Catalog Service

3. Order Context
   - Carrinho
   - Checkout
   - Rastreamento
   → Order Service

4. Payment Context
   - Processamento de pagamento
   - Cupons de desconto
   → Payment Service

5. Shipping Context
   - Cálculo de frete
   → Shipping Service

6. Inventory Context
   - Gestão de estoque
   → Inventory Service

7. Notification Context
   - E-mails
   - SMS
   → Notification Service

8. Analytics Context
   - Relatórios
   → Analytics Service (Read Model)
```

---

### Exercício 2: Implementar Strangler Pattern

**Cenário:** Você tem um endpoint legado no monolito:

```java
// MONOLITO
@GetMapping("/api/orders/{id}")
public OrderResponse getOrder(@PathVariable UUID id) {
    Order order = orderRepository.findById(id).orElseThrow();
    User user = userRepository.findById(order.getUserId()).orElseThrow();
    Payment payment = paymentRepository.findByOrderId(id).orElseThrow();

    return new OrderResponse(order, user, payment);
}
```

**Tarefa:** Migre para microserviços usando Strangler Pattern.

**Solução:**

```java
// PASSO 1: Extrair Order Service
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final UserServiceClient userClient;
    private final PaymentServiceClient paymentClient;

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        // Busca order (local)
        Order order = orderRepository.findById(id).orElseThrow();

        // Busca user (HTTP - ainda no monolito)
        UserResponse user = userClient.getUser(order.getUserId());

        // Busca payment (HTTP - ainda no monolito)
        PaymentResponse payment = paymentClient.getPaymentByOrderId(id);

        return new OrderResponse(order, user, payment);
    }
}

// PASSO 2: API Gateway roteamento
spring:
  cloud:
    gateway:
      routes:
        - id: order-service
          uri: http://order-service:8081
          predicates:
            - Path=/api/orders/**

        - id: monolith
          uri: http://monolith:8080
          predicates:
            - Path=/**

// PASSO 3: Monolito delega (temporário)
@GetMapping("/api/orders/{id}")
public OrderResponse getOrder(@PathVariable UUID id) {
    // Delega para novo serviço
    return restTemplate.getForObject(
        "http://order-service:8081/api/orders/" + id,
        OrderResponse.class
    );
}

// PASSO 4: Remove código legado do monolito (quando estável)
```

---

### Exercício 3: Implementar Saga Pattern

**Cenário:** Criar pedido que envolve 3 serviços: Order, Payment, Inventory.

**Tarefa:** Implemente Saga Pattern com compensação.

**Solução:**

```java
// Order Service
@Service
public class OrderService {

    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. Cria order (PENDING)
        Order order = Order.create(...);
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);

        // 2. Publica evento
        kafkaTemplate.send("order.created.v1", new OrderCreatedEvent(order));

        return order;
    }

    @KafkaListener(topics = "inventory.reserved.v1")
    public void handleInventoryReserved(InventoryReservedEvent event) {
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
    }

    @KafkaListener(topics = "payment.failed.v1")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        // COMPENSAÇÃO: Cancela order
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        kafkaTemplate.send("order.cancelled.v1", new OrderCancelledEvent(order));
    }
}

// Payment Service
@Service
public class PaymentService {

    @KafkaListener(topics = "order.created.v1")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            Payment payment = processPayment(event);
            kafkaTemplate.send("payment.approved.v1", new PaymentApprovedEvent(payment));
        } catch (Exception e) {
            kafkaTemplate.send("payment.failed.v1", new PaymentFailedEvent(event.getOrderId()));
        }
    }
}

// Inventory Service
@Service
public class InventoryService {

    @KafkaListener(topics = "payment.approved.v1")
    public void handlePaymentApproved(PaymentApprovedEvent event) {
        try {
            reserveInventory(event.getOrderId());
            kafkaTemplate.send("inventory.reserved.v1", new InventoryReservedEvent(event.getOrderId()));
        } catch (OutOfStockException e) {
            kafkaTemplate.send("inventory.failed.v1", new InventoryFailedEvent(event.getOrderId()));
        }
    }

    @KafkaListener(topics = "order.cancelled.v1")
    public void handleOrderCancelled(OrderCancelledEvent event) {
        // COMPENSAÇÃO: Libera estoque
        releaseInventory(event.getOrderId());
    }
}
```

---

## Conclusão

Migrar de monolito para microserviços é uma jornada **longa, complexa e arriscada**. Mas se feita corretamente, com:

✅ Planejamento cuidadoso
✅ Migração incremental (Strangler Pattern)
✅ Database per Service
✅ Anti-Corruption Layer
✅ Feature Toggles
✅ Monitoramento distribuído
✅ Planos de rollback
✅ Saga Pattern para transações

Você conseguirá obter:

🎯 Deploy independente
🎯 Escalabilidade granular
🎯 Times autônomos
🎯 Resiliência
🎯 Evolução tecnológica

**Lembre-se:** Microserviços não são uma bala de prata. Só migre se os benefícios superarem a complexidade.

**Boa migração! 🚀**
