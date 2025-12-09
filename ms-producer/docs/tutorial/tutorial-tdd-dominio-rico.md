# Tutorial Definitivo: TDD com Domínio Rico - Do Zero ao Mestre

## 📋 Sumário

1. [O que é TDD e Por Que Usar](#1-o-que-é-tdd-e-por-que-usar)
2. [O Ciclo Red-Green-Refactor](#2-o-ciclo-red-green-refactor)
3. [Domínio Rico vs Domínio Anêmico](#3-domínio-rico-vs-domínio-anêmico)
4. [Preparação do Ambiente](#4-preparação-do-ambiente)
5. [Implementação Passo a Passo - Classe Order](#5-implementação-passo-a-passo---classe-order)
6. [Regras de Negócio Avançadas](#6-regras-de-negócio-avançadas)
7. [Refatoração e Melhoria Contínua](#7-refatoração-e-melhoria-contínua)
8. [Checklist TDD](#8-checklist-tdd)
9. [Armadilhas Comuns e Como Evitar](#9-armadilhas-comuns-e-como-evitar)
10. [Exercícios Práticos](#10-exercícios-práticos)

---

## 1. O que é TDD e Por Que Usar

### Definição em 30 Segundos

**TDD (Test-Driven Development)** é uma técnica onde você escreve **TESTES ANTES** do código de produção.

```
FLUXO TRADICIONAL (sem TDD):
  1. Escrever código
  2. Executar aplicação
  3. Testar manualmente
  4. Corrigir bugs
  5. Escrever testes (talvez...)

FLUXO TDD:
  1. Escrever teste (que falha) 🔴 RED
  2. Escrever código mínimo (teste passa) 🟢 GREEN
  3. Refatorar (melhorar código) 🔵 REFACTOR
  4. Repetir...

  ✅ Design emergente
  ✅ Código testável por design
  ✅ Menos bugs
  ✅ Confiança para refatorar
```

### Diagrama Visual do TDD

```
❌ SEM TDD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Pensar na solução
     ↓
2. Escrever MUITO código
     ↓
3. Testar manualmente
     ↓
4. Bug encontrado! 💥
     ↓
5. Debugar por horas...
     ↓
6. Consertar
     ↓
7. Outro bug aparece! 💥
     ↓
8. Mais debugging...
     ↓
9. Código difícil de testar
     ↓
10. "Depois eu escrevo os testes" (nunca escreve)

PROBLEMAS:
├─ Feedback lento (descobrir bugs tarde)
├─ Código difícil de testar
├─ Medo de refatorar (pode quebrar algo)
├─ Cobertura de testes baixa
└─ Acumula dívida técnica


✅ COM TDD
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Pensar em UM comportamento pequeno
     ↓
2. Escrever teste para esse comportamento
     ↓
3. Executar teste → FALHA 🔴 (esperado!)
     ↓
4. Escrever código MÍNIMO para passar
     ↓
5. Executar teste → PASSA 🟢 (yes!)
     ↓
6. Refatorar se necessário 🔵
     ↓
7. Todos testes passam 🟢
     ↓
8. Repetir para próximo comportamento

BENEFÍCIOS:
├─ ✅ Feedback IMEDIATO (segundos)
├─ ✅ Código testável por design
├─ ✅ Confiança para refatorar
├─ ✅ Cobertura de testes 100%
├─ ✅ Documentação viva (testes)
├─ ✅ Menos bugs em produção
└─ ✅ Design mais limpo
```

### Por Que TDD com Domínio Rico?

| Aspecto | Sem TDD | Com TDD |
|---------|---------|---------|
| **Design** | ❌ Código acoplado, difícil testar | ✅ Design emergente, testável |
| **Bugs** | ❌ Encontrados tarde (produção) | ✅ Encontrados ANTES de escrever código |
| **Refatoração** | ❌ Medo de quebrar (sem testes) | ✅ Confiança total (testes garantem) |
| **Documentação** | ❌ Desatualizada ou inexistente | ✅ Testes são documentação viva |
| **Velocidade inicial** | ⚡ Rápido no começo | 🐢 Mais lento no começo |
| **Velocidade no longo prazo** | 🐢 Muito lento (debugging) | ⚡ Muito rápido (sem bugs) |
| **Cobertura** | ❌ 20-40% (se tiver) | ✅ 90-100% |

### Exemplo Real: Por Que TDD Salva Vidas

#### Cenário: Sistema de E-commerce

```
SEM TDD:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Dev escreveu classe Order com 15 métodos
  ↓
Subiu para produção
  ↓
Cliente relata: "Consegui comprar com desconto de 150%!"
  ↓
Bug crítico: total ficou NEGATIVO!
  ↓
Hotfix urgente às 3h da manhã
  ↓
Corrigiu... mas quebrou outra coisa
  ↓
Rollback! 💥
  ↓
Cliente perdido
  ↓
Dinheiro perdido

CAUSA RAIZ:
  Ninguém testou o cenário: "desconto > 100%"


COM TDD:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Dev escreveu teste:
   "não deve permitir desconto maior que 100%"
     ↓
2. Teste FALHOU 🔴 (código nem existe ainda)
     ↓
3. Dev implementou validação
     ↓
4. Teste PASSOU 🟢
     ↓
5. Deploy tranquilo
     ↓
6. Cliente tenta desconto 150%
     ↓
7. Sistema rejeita com erro claro
     ↓
8. Bug nunca chega a produção! ✅

RESULTADO:
  ✅ Bug encontrado em 10 segundos (no teste)
  ✅ Não foi para produção
  ✅ Cliente feliz
  ✅ Dev dormiu bem
```

---

## 2. O Ciclo Red-Green-Refactor

### O Coração do TDD

```
          ┌──────────────────────────────────────┐
          │                                      │
          │       CICLO RED-GREEN-REFACTOR       │
          │                                      │
          └──────────────────────────────────────┘

                    🔴 RED
                     ↓
         Escrever teste que FALHA
         (comportamento ainda não existe)
                     ↓
                 Executar teste
                     ↓
                "Test Failed" ❌
                     ↓
                     │
                     ↓
                  🟢 GREEN
                     ↓
         Escrever código MÍNIMO
         (fazer teste passar)
                     ↓
                 Executar teste
                     ↓
                "Test Passed" ✅
                     ↓
                     │
                     ↓
                  🔵 REFACTOR
                     ↓
         Melhorar código
         (manter testes passando)
                     ↓
             Executar TODOS os testes
                     ↓
              "All Tests Passed" ✅
                     ↓
                     │
                     └──────→ Próximo comportamento
                              (volta para 🔴 RED)
```

### Regras de Ouro do TDD

#### 🔴 RED - Escrever Teste que Falha

```java
// REGRA 1: Não escreva código de produção SEM um teste falhando

@Test
@DisplayName("Should create order with valid data")
void shouldCreateOrderWithValidData() {
    // Given
    String orderId = "ord-123";
    String customerId = "cust-456";

    // When
    Order order = new Order(orderId, customerId);

    // Then
    assertThat(order.getOrderId()).isEqualTo(orderId);
    assertThat(order.getCustomerId()).isEqualTo(customerId);
}

// Executar teste → FALHA 🔴
// Erro: "Cannot find symbol: class Order"
//
// ✅ CORRETO! O teste DEVE falhar!
// ❌ Se o teste passar SEM código, está errado!
```

**Por que precisa falhar?**
- Garante que o teste está testando algo
- Se passar sem código, o teste é inútil!
- Exemplo: teste com lógica errada, sempre passa

#### 🟢 GREEN - Fazer Teste Passar (código mínimo)

```java
// REGRA 2: Escreva o MÍNIMO de código para passar

// ❌ ERRADO - Código demais
public class Order {
    private String orderId;
    private String customerId;
    private List<OrderItem> items;
    private OrderStatus status;
    private BigDecimal total;
    private LocalDateTime createdAt;

    // ... 10 métodos que não são necessários AGORA

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
}

// ✅ CORRETO - Só o necessário
public class Order {
    private final String orderId;
    private final String customerId;

    public Order(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
}

// Executar teste → PASSA 🟢
```

**Por que código mínimo?**
- Evita over-engineering
- Cada linha de código é justificada por um teste
- YAGNI (You Aren't Gonna Need It)

#### 🔵 REFACTOR - Melhorar Código

```java
// REGRA 3: Refatore mantendo testes VERDES

// Antes da refatoração (funciona, mas não está ótimo)
public class Order {
    private final String orderId;
    private final String customerId;

    public Order(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
}

// Depois da refatoração (adicionar validações)
public class Order {
    private final String orderId;
    private final String customerId;

    public Order(String orderId, String customerId) {
        validateOrderId(orderId);
        validateCustomerId(customerId);

        this.orderId = orderId;
        this.customerId = customerId;
    }

    private void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }
    }

    private void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
}

// Executar TODOS os testes → TODOS PASSAM 🟢
```

**Por que refatorar?**
- Melhorar design sem mudar comportamento
- Eliminar duplicação
- Tornar código mais legível
- Testes garantem que não quebrou nada!

### Exemplo Completo: Um Ciclo Inteiro

```
PASSO 1: 🔴 RED - Teste para validação
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Test
@DisplayName("Should throw exception when order ID is null")
void shouldThrowExceptionWhenOrderIdIsNull() {
    // When/Then
    assertThatThrownBy(() -> new Order(null, "cust-456"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Order ID cannot be null or blank");
}

// Executar → FALHA 🔴
// Erro: NullPointerException (validação não existe)


PASSO 2: 🟢 GREEN - Implementar validação
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public Order(String orderId, String customerId) {
    // Código MÍNIMO para passar
    if (orderId == null || orderId.isBlank()) {
        throw new IllegalArgumentException("Order ID cannot be null or blank");
    }

    this.orderId = orderId;
    this.customerId = customerId;
}

// Executar → PASSA 🟢


PASSO 3: 🔵 REFACTOR - Extrair método
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public Order(String orderId, String customerId) {
    validateOrderId(orderId);  // ← Extraiu para método

    this.orderId = orderId;
    this.customerId = customerId;
}

private void validateOrderId(String orderId) {
    if (orderId == null || orderId.isBlank()) {
        throw new IllegalArgumentException("Order ID cannot be null or blank");
    }
}

// Executar todos testes → TODOS PASSAM 🟢

// Agora pronto para próximo comportamento!
```

---

## 3. Domínio Rico vs Domínio Anêmico

### O que é Domínio Anêmico (Anti-Pattern)?

```java
// ❌ DOMÍNIO ANÊMICO - NÃO FAÇA ISSO!
// Classe sem comportamento, apenas getters/setters

public class Order {
    private String orderId;
    private String customerId;
    private OrderStatus status;
    private BigDecimal total;

    // Apenas getters e setters (JavaBean)
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
}

// LÓGICA DE NEGÓCIO VAZA PARA SERVIÇO
@Service
public class OrderService {

    public void confirmOrder(Order order) {
        // ❌ Validações no serviço (deveria estar no domínio)
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot confirm cancelled order");
        }

        // ❌ Lógica de negócio no serviço
        order.setStatus(OrderStatus.CONFIRMED);

        // ❌ Cálculos no serviço
        BigDecimal total = calculateTotal(order);
        order.setTotal(total);

        orderRepository.save(order);
    }

    // ❌ Lógica de domínio em serviço de aplicação
    private BigDecimal calculateTotal(Order order) {
        // ... cálculos complexos aqui
    }
}

PROBLEMAS:
├─ Classe Order não tem comportamento (só dados)
├─ Lógica de negócio espalhada (Service, Controller, etc)
├─ Difícil de testar (precisa mock de tudo)
├─ Fácil quebrar regras de negócio (qualquer um muda o estado)
├─ Não reflete conceitos do domínio
└─ Violação de Tell, Don't Ask
```

### O que é Domínio Rico (Correto!)

```java
// ✅ DOMÍNIO RICO - FAÇA ISSO!
// Classe com comportamento e regras de negócio

public class Order {
    private final String orderId;
    private final String customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private BigDecimal total;

    // Construtor com validações
    public Order(String orderId, String customerId) {
        validateOrderId(orderId);
        validateCustomerId(customerId);

        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;  // Estado inicial
        this.items = new ArrayList<>();
        this.total = BigDecimal.ZERO;
    }

    // ✅ Comportamento: Adicionar item (com regras de negócio)
    public void addItem(String productId, int quantity, BigDecimal price) {
        validateCanAddItem();  // Regra: só pode adicionar em DRAFT
        validateQuantity(quantity);
        validatePrice(price);

        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);
        recalculateTotal();  // Mantém invariante: total sempre correto
    }

    // ✅ Comportamento: Confirmar pedido (transição de estado)
    public void confirm() {
        validateCanConfirm();  // Regra: só confirma se tem itens

        this.status = OrderStatus.CONFIRMED;
    }

    // ✅ Comportamento: Cancelar pedido
    public void cancel() {
        validateCanCancel();  // Regra: não cancela se já completado

        this.status = OrderStatus.CANCELLED;
    }

    // ✅ Validações de regras de negócio (encapsuladas)
    private void validateCanAddItem() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException(
                "Can only add items to DRAFT orders"
            );
        }
    }

    private void validateCanConfirm() {
        if (items.isEmpty()) {
            throw new IllegalStateException(
                "Cannot confirm order without items"
            );
        }
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException(
                "Only DRAFT orders can be confirmed"
            );
        }
    }

    private void validateCanCancel() {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException(
                "Cannot cancel completed order"
            );
        }
    }

    // ✅ Cálculos encapsulados
    private void recalculateTotal() {
        this.total = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Getters (SEM setters - imutabilidade parcial)
    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public List<OrderItem> getItems() { return List.copyOf(items); }  // Cópia defensiva
}

// SERVIÇO AGORA É SIMPLES
@Service
public class OrderService {

    public void confirmOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow();

        // ✅ Toda lógica está no domínio
        order.confirm();  // Validações e regras dentro do Order!

        orderRepository.save(order);
    }
}

BENEFÍCIOS:
├─ ✅ Lógica de negócio CENTRALIZADA no domínio
├─ ✅ Regras impossíveis de violar (encapsuladas)
├─ ✅ Fácil de testar (testa Order isoladamente)
├─ ✅ Reflete linguagem do negócio (Ubiquitous Language)
├─ ✅ Invariantes sempre válidas (total sempre correto)
├─ ✅ Tell, Don't Ask (order.confirm(), não getters/setters)
└─ ✅ Single Responsibility (Order cuida de suas regras)
```

### Comparação Lado a Lado

| Aspecto | Domínio Anêmico ❌ | Domínio Rico ✅ |
|---------|-------------------|----------------|
| **Lógica de Negócio** | Espalhada (Service, Controller) | Centralizada (Domínio) |
| **Testabilidade** | Difícil (precisa mocks) | Fácil (POJO puro) |
| **Validações** | Esquecidas ou duplicadas | Garantidas (construtor) |
| **Estado** | Qualquer um muda (setters) | Controlado (métodos) |
| **Coesão** | Baixa (lógica espalhada) | Alta (tudo junto) |
| **Acoplamento** | Alto (depende de Service) | Baixo (autocontido) |
| **Manutenibilidade** | Difícil (buscar lógica) | Fácil (um só lugar) |

---

## 4. Preparação do Ambiente

### Estrutura de Pastas

```
ms-producer/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── mvbr/
│   │               └── store/
│   │                   └── domain/
│   │                       └── model/
│   │                           └── order/
│   │                               ├── Order.java         ← Domínio Rico
│   │                               ├── OrderItem.java     ← Value Object
│   │                               └── OrderStatus.java   ← Enum
│   └── test/
│       └── java/
│           └── com/
│               └── mvbr/
│                   └── store/
│                       └── domain/
│                           └── model/
│                               └── order/
│                                   └── OrderTest.java  ← Testes TDD
└── pom.xml
```

### Dependências Necessárias (já existem no projeto)

```xml
<!-- pom.xml -->
<dependencies>
    <!-- JUnit 5 (para testes) -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- AssertJ (assertions fluentes) -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Mockito (se precisar mocks) -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Comandos Maven Úteis

```bash
# Executar TODOS os testes
./mvnw test

# Executar testes de uma classe específica
./mvnw test -Dtest=OrderTest

# Executar um teste específico
./mvnw test -Dtest=OrderTest#shouldCreateOrderWithValidData

# Executar testes com coverage
./mvnw clean test jacoco:report

# Executar testes em modo watch (rerun automático)
./mvnw test -Dsurefire.rerunFailingTestsCount=0 -Dsurefire.forkCount=1
```

---

## 5. Implementação Passo a Passo - Classe Order

### Visão Geral do que Vamos Construir

```
ORDEM (Order) - Domínio Rico
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Atributos:
├── orderId (String, obrigatório)
├── customerId (String, obrigatório)
├── status (OrderStatus: DRAFT, CONFIRMED, CANCELLED, COMPLETED)
├── items (List<OrderItem>)
├── discount (BigDecimal, 0-100%)
└── total (BigDecimal, calculado)

Comportamentos:
├── Criar pedido (construtor com validações)
├── Adicionar item (com validações)
├── Remover item
├── Aplicar desconto (máx 100%)
├── Confirmar pedido (DRAFT → CONFIRMED)
├── Cancelar pedido (não pode se COMPLETED)
└── Completar pedido (CONFIRMED → COMPLETED)

Regras de Negócio:
├── Order ID não pode ser null/blank
├── Customer ID não pode ser null/blank
├── Só pode adicionar itens em DRAFT
├── Não pode confirmar sem itens
├── Não pode cancelar se COMPLETED
├── Desconto entre 0% e 100%
├── Total = (subtotal dos itens) - desconto
└── Quantidade de item > 0
```

### PASSO 1: 🔴 Primeiro Teste - Criar Order Vazio

Vamos começar DO ZERO. Ainda não temos NADA, nem a classe Order!

#### 1.1: Escrever o Teste (RED 🔴)

```java
// src/test/java/com/mvbr/store/domain/model/order/OrderTest.java
package com.mvbr.store.domain.model.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes TDD para Order (Domínio Rico).
 *
 * Vamos construir PASSO A PASSO, seguindo Red-Green-Refactor!
 */
@DisplayName("Order - Domain Model Tests")
class OrderTest {

    // ===============================================
    //      PASSO 1: Criar Order com Dados Válidos
    // ===============================================

    @Test
    @DisplayName("Should create order with valid order ID and customer ID")
    void shouldCreateOrderWithValidData() {
        // Given
        String orderId = "ord-123";
        String customerId = "cust-456";

        // When
        Order order = new Order(orderId, customerId);

        // Then
        assertThat(order).isNotNull();
        assertThat(order.getOrderId()).isEqualTo(orderId);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
    }
}
```

#### 1.2: Executar o Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO ESPERADO:
# ❌ Test shouldCreateOrderWithValidData() FAILED
#    Compilation error: Cannot find symbol: class Order
```

**IMPORTANTE:** O teste DEVE falhar! Se passar, algo está errado!

#### 1.3: Escrever Código Mínimo (GREEN 🟢)

Agora vamos criar a classe Order com o MÍNIMO para passar o teste:

```java
// src/main/java/com/mvbr/store/domain/model/order/Order.java
package com.mvbr.store.domain.model.order;

/**
 * Order - Domínio Rico (construído com TDD).
 */
public class Order {

    private final String orderId;
    private final String customerId;

    public Order(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
```

#### 1.4: Executar Teste Novamente (deve PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO ESPERADO:
# ✅ Test shouldCreateOrderWithValidData() PASSED
```

**Parabéns! Completou seu primeiro ciclo TDD! 🎉**

```
✅ Ciclo 1 Completo:
   🔴 RED:    Teste falhou (Order não existia)
   🟢 GREEN:  Código mínimo (Order criada)
   🔵 REFACTOR: (não necessário ainda)
```

---

### PASSO 2: 🔴 Validar Order ID Não Nulo

Agora vamos adicionar uma regra de negócio: Order ID não pode ser null!

#### 2.1: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should throw exception when order ID is null")
void shouldThrowExceptionWhenOrderIdIsNull() {
    // Given
    String orderId = null;
    String customerId = "cust-456";

    // When/Then
    assertThatThrownBy(() -> new Order(orderId, customerId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Order ID cannot be null or blank");
}
```

#### 2.2: Executar Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ❌ Test shouldThrowExceptionWhenOrderIdIsNull() FAILED
#    Expected: IllegalArgumentException
#    But was: NullPointerException
```

#### 2.3: Implementar Validação (GREEN 🟢)

```java
// Modificar Order.java

public class Order {

    private final String orderId;
    private final String customerId;

    public Order(String orderId, String customerId) {
        // ✅ Adicionar validação
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }

        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
```

#### 2.4: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ shouldCreateOrderWithValidData() PASSED
# ✅ shouldThrowExceptionWhenOrderIdIsNull() PASSED
#
# 2 tests passed ✅
```

---

### PASSO 3: 🔴 Validar Order ID Não Vazio/Blank

Vamos adicionar mais um caso: Order ID não pode ser vazio ou apenas espaços!

#### 3.1: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should throw exception when order ID is blank")
void shouldThrowExceptionWhenOrderIdIsBlank() {
    // When/Then
    assertThatThrownBy(() -> new Order("   ", "cust-456"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Order ID cannot be null or blank");
}
```

#### 3.2: Executar Teste (pode PASSAR ou FALHAR)

```bash
./mvnw test -Dtest=OrderTest#shouldThrowExceptionWhenOrderIdIsBlank

# RESULTADO:
# ✅ Test PASSED!
#
# Por quê? Porque já implementamos isBlank() no passo anterior!
```

**LIÇÃO IMPORTANTE:** Às vezes o teste passa na primeira (porque já implementamos parte da lógica). Isso é OK! O teste ainda é útil como **documentação** e **regressão**.

---

### PASSO 4: 🔴 Validar Customer ID

Agora vamos fazer o mesmo para Customer ID!

#### 4.1: Escrever Testes (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should throw exception when customer ID is null")
void shouldThrowExceptionWhenCustomerIdIsNull() {
    // When/Then
    assertThatThrownBy(() -> new Order("ord-123", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Customer ID cannot be null or blank");
}

@Test
@DisplayName("Should throw exception when customer ID is blank")
void shouldThrowExceptionWhenCustomerIdIsBlank() {
    // When/Then
    assertThatThrownBy(() -> new Order("ord-123", "   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Customer ID cannot be null or blank");
}
```

#### 4.2: Executar Testes (devem FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ shouldCreateOrderWithValidData() PASSED
# ✅ shouldThrowExceptionWhenOrderIdIsNull() PASSED
# ✅ shouldThrowExceptionWhenOrderIdIsBlank() PASSED
# ❌ shouldThrowExceptionWhenCustomerIdIsNull() FAILED
#    Expected: IllegalArgumentException
#    But was: NullPointerException
# ❌ shouldThrowExceptionWhenCustomerIdIsBlank() FAILED
#    Expected: IllegalArgumentException
#    But was: IllegalArgumentException with message "Order ID cannot be null or blank"
```

#### 4.3: Implementar Validação (GREEN 🟢)

```java
// Modificar Order.java

public class Order {

    private final String orderId;
    private final String customerId;

    public Order(String orderId, String customerId) {
        // Validar Order ID
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }

        // ✅ Validar Customer ID
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }

        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
```

#### 4.4: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 5 tests passed
```

---

### PASSO 5: 🔵 REFACTOR - Extrair Métodos de Validação

Agora temos duplicação no construtor. Vamos refatorar!

#### 5.1: Extrair Métodos (REFACTOR 🔵)

```java
// Modificar Order.java

public class Order {

    private final String orderId;
    private final String customerId;

    public Order(String orderId, String customerId) {
        validateOrderId(orderId);    // ← Extraiu
        validateCustomerId(customerId);  // ← Extraiu

        this.orderId = orderId;
        this.customerId = customerId;
    }

    // ✅ Métodos de validação extraídos
    private void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }
    }

    private void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
```

#### 5.2: Executar Testes (garantir que não quebrou nada)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 5 tests passed
#
# Código está MELHOR e testes continuam VERDES!
```

---

### PASSO 6: 🔴 Adicionar Status Inicial (DRAFT)

Order deve ter um status inicial = DRAFT!

#### 6.1: Criar Enum OrderStatus

Primeiro, criar o enum:

```java
// src/main/java/com/mvbr/store/domain/model/order/OrderStatus.java
package com.mvbr.store.domain.model.order;

/**
 * Status possíveis de uma Order.
 */
public enum OrderStatus {
    DRAFT,      // Pedido sendo criado
    CONFIRMED,  // Pedido confirmado
    CANCELLED,  // Pedido cancelado
    COMPLETED   // Pedido completado (entregue)
}
```

#### 6.2: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should initialize order with DRAFT status")
void shouldInitializeOrderWithDraftStatus() {
    // Given/When
    Order order = new Order("ord-123", "cust-456");

    // Then
    assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
}
```

#### 6.3: Executar Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest#shouldInitializeOrderWithDraftStatus

# RESULTADO:
# ❌ Test FAILED
#    Compilation error: Cannot find symbol: method getStatus()
```

#### 6.4: Implementar Status (GREEN 🟢)

```java
// Modificar Order.java

public class Order {

    private final String orderId;
    private final String customerId;
    private OrderStatus status;  // ← Novo campo

    public Order(String orderId, String customerId) {
        validateOrderId(orderId);
        validateCustomerId(customerId);

        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;  // ← Estado inicial
    }

    private void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }
    }

    private void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    // ✅ Novo getter
    public OrderStatus getStatus() {
        return status;
    }
}
```

#### 6.5: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 6 tests passed
```

---

### PASSO 7: 🔴 Adicionar Items ao Pedido

Agora vamos criar OrderItem e adicionar ao pedido!

#### 7.1: Criar OrderItem (Value Object)

```java
// src/main/java/com/mvbr/store/domain/model/order/OrderItem.java
package com.mvbr.store.domain.model.order;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * OrderItem - Value Object.
 * Representa um item do pedido (produto + quantidade + preço).
 */
public class OrderItem {

    private final String productId;
    private final int quantity;
    private final BigDecimal price;

    public OrderItem(String productId, int quantity, BigDecimal price) {
        this.productId = productId;
        this.quantity = quantity;
        this.price = price;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    // Calcular subtotal do item
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItem that = (OrderItem) o;
        return quantity == that.quantity &&
               Objects.equals(productId, that.productId) &&
               Objects.equals(price, that.price);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, quantity, price);
    }
}
```

#### 7.2: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

import java.math.BigDecimal;

@Test
@DisplayName("Should add item to order")
void shouldAddItemToOrder() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    String productId = "prod-789";
    int quantity = 2;
    BigDecimal price = new BigDecimal("50.00");

    // When
    order.addItem(productId, quantity, price);

    // Then
    assertThat(order.getItems()).hasSize(1);

    OrderItem item = order.getItems().get(0);
    assertThat(item.getProductId()).isEqualTo(productId);
    assertThat(item.getQuantity()).isEqualTo(quantity);
    assertThat(item.getPrice()).isEqualByComparingTo(price);
}
```

#### 7.3: Executar Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest#shouldAddItemToOrder

# RESULTADO:
# ❌ Test FAILED
#    Compilation error: Cannot find symbol: method addItem(...)
```

#### 7.4: Implementar addItem() (GREEN 🟢)

```java
// Modificar Order.java

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Order {

    private final String orderId;
    private final String customerId;
    private OrderStatus status;
    private final List<OrderItem> items;  // ← Novo campo

    public Order(String orderId, String customerId) {
        validateOrderId(orderId);
        validateCustomerId(customerId);

        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();  // ← Inicializar
    }

    // ✅ Novo método: adicionar item
    public void addItem(String productId, int quantity, BigDecimal price) {
        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);
    }

    private void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order ID cannot be null or blank");
        }
    }

    private void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be null or blank");
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    // ✅ Novo getter
    public List<OrderItem> getItems() {
        return items;
    }
}
```

#### 7.5: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 7 tests passed
```

---

### PASSO 8: 🔴 Validar Quantidade do Item

Quantidade deve ser maior que zero!

#### 8.1: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should throw exception when item quantity is zero or negative")
void shouldThrowExceptionWhenQuantityIsInvalid() {
    // Given
    Order order = new Order("ord-123", "cust-456");

    // When/Then - Zero
    assertThatThrownBy(() ->
        order.addItem("prod-789", 0, new BigDecimal("50.00"))
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quantity must be greater than zero");

    // When/Then - Negative
    assertThatThrownBy(() ->
        order.addItem("prod-789", -5, new BigDecimal("50.00"))
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Quantity must be greater than zero");
}
```

#### 8.2: Executar Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest#shouldThrowExceptionWhenQuantityIsInvalid

# RESULTADO:
# ❌ Test FAILED
#    Expected: IllegalArgumentException
#    But was: successful execution
```

#### 8.3: Implementar Validação (GREEN 🟢)

```java
// Modificar Order.java

public void addItem(String productId, int quantity, BigDecimal price) {
    // ✅ Validar quantidade
    if (quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be greater than zero");
    }

    OrderItem item = new OrderItem(productId, quantity, price);
    this.items.add(item);
}
```

#### 8.4: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 8 tests passed
```

---

**Continuando no próximo bloco...**

## 6. Regras de Negócio Avançadas

### PASSO 9: 🔴 Só Pode Adicionar Item em DRAFT

Regra: Só pode adicionar itens se o pedido estiver em DRAFT!

#### 9.1: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should not allow adding item when order is not DRAFT")
void shouldNotAllowAddingItemWhenNotDraft() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 1, new BigDecimal("10.00"));
    order.confirm();  // Muda para CONFIRMED

    // When/Then
    assertThatThrownBy(() ->
        order.addItem("prod-2", 1, new BigDecimal("20.00"))
    )
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Can only add items to DRAFT orders");
}
```

#### 9.2: Executar Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest#shouldNotAllowAddingItemWhenNotDraft

# RESULTADO:
# ❌ Test FAILED
#    Compilation error: Cannot find symbol: method confirm()
```

#### 9.3: Implementar confirm() e Validação (GREEN 🟢)

```java
// Modificar Order.java

public void addItem(String productId, int quantity, BigDecimal price) {
    // ✅ Validar status
    if (status != OrderStatus.DRAFT) {
        throw new IllegalStateException("Can only add items to DRAFT orders");
    }

    if (quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be greater than zero");
    }

    OrderItem item = new OrderItem(productId, quantity, price);
    this.items.add(item);
}

// ✅ Novo método: confirmar pedido
public void confirm() {
    this.status = OrderStatus.CONFIRMED;
}
```

#### 9.4: Executar Testes

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 9 tests passed
```

---

### PASSO 10: 🔴 Não Pode Confirmar Pedido Vazio

Regra: Pedido precisa ter pelo menos 1 item para ser confirmado!

#### 10.1: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should not confirm order without items")
void shouldNotConfirmOrderWithoutItems() {
    // Given
    Order order = new Order("ord-123", "cust-456");

    // When/Then
    assertThatThrownBy(() -> order.confirm())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot confirm order without items");
}
```

#### 10.2: Executar Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest#shouldNotConfirmOrderWithoutItems

# RESULTADO:
# ❌ Test FAILED
#    Expected: IllegalStateException
#    But was: successful execution
```

#### 10.3: Implementar Validação (GREEN 🟢)

```java
// Modificar Order.java

public void confirm() {
    // ✅ Validar que tem itens
    if (items.isEmpty()) {
        throw new IllegalStateException("Cannot confirm order without items");
    }

    this.status = OrderStatus.CONFIRMED;
}
```

#### 10.4: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 10 tests passed
```

---

### PASSO 11: 🔴 Calcular Total do Pedido

Pedido deve calcular o total automaticamente!

#### 11.1: Escrever Teste (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should calculate total from items")
void shouldCalculateTotalFromItems() {
    // Given
    Order order = new Order("ord-123", "cust-456");

    // When
    order.addItem("prod-1", 2, new BigDecimal("50.00"));  // 100.00
    order.addItem("prod-2", 3, new BigDecimal("30.00"));  // 90.00

    // Then
    BigDecimal expectedTotal = new BigDecimal("190.00");
    assertThat(order.getTotal()).isEqualByComparingTo(expectedTotal);
}
```

#### 11.2: Executar Teste (deve FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest#shouldCalculateTotalFromItems

# RESULTADO:
# ❌ Test FAILED
#    Compilation error: Cannot find symbol: method getTotal()
```

#### 11.3: Implementar Cálculo (GREEN 🟢)

```java
// Modificar Order.java

public class Order {

    private final String orderId;
    private final String customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private BigDecimal total;  // ← Novo campo

    public Order(String orderId, String customerId) {
        validateOrderId(orderId);
        validateCustomerId(customerId);

        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();
        this.total = BigDecimal.ZERO;  // ← Inicializar
    }

    public void addItem(String productId, int quantity, BigDecimal price) {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Can only add items to DRAFT orders");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);

        // ✅ Recalcular total após adicionar item
        recalculateTotal();
    }

    // ✅ Método privado para recalcular total
    private void recalculateTotal() {
        this.total = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void confirm() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot confirm order without items");
        }

        this.status = OrderStatus.CONFIRMED;
    }

    // Getters...

    // ✅ Novo getter
    public BigDecimal getTotal() {
        return total;
    }
}
```

#### 11.4: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 11 tests passed
```

---

### PASSO 12: 🔴 Aplicar Desconto

Vamos adicionar desconto (0-100%)!

#### 12.1: Escrever Testes (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should apply discount to order")
void shouldApplyDiscountToOrder() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 2, new BigDecimal("50.00"));  // 100.00

    // When
    order.applyDiscount(new BigDecimal("10.00"));  // 10% de desconto

    // Then
    BigDecimal expectedTotal = new BigDecimal("90.00");  // 100 - 10
    assertThat(order.getTotal()).isEqualByComparingTo(expectedTotal);
}

@Test
@DisplayName("Should not allow discount greater than 100 percent")
void shouldNotAllowDiscountGreaterThan100Percent() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 1, new BigDecimal("100.00"));

    // When/Then
    assertThatThrownBy(() ->
        order.applyDiscount(new BigDecimal("150.00"))
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Discount cannot be greater than subtotal");
}

@Test
@DisplayName("Should not allow negative discount")
void shouldNotAllowNegativeDiscount() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 1, new BigDecimal("100.00"));

    // When/Then
    assertThatThrownBy(() ->
        order.applyDiscount(new BigDecimal("-10.00"))
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Discount cannot be negative");
}
```

#### 12.2: Executar Testes (devem FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ❌ 3 tests FAILED
#    Compilation error: Cannot find symbol: method applyDiscount(...)
```

#### 12.3: Implementar applyDiscount() (GREEN 🟢)

```java
// Modificar Order.java

public class Order {

    private final String orderId;
    private final String customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private BigDecimal discount;  // ← Novo campo
    private BigDecimal total;

    public Order(String orderId, String customerId) {
        validateOrderId(orderId);
        validateCustomerId(customerId);

        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();
        this.discount = BigDecimal.ZERO;  // ← Inicializar
        this.total = BigDecimal.ZERO;
    }

    // ✅ Novo método: aplicar desconto
    public void applyDiscount(BigDecimal discount) {
        // Validar desconto negativo
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }

        // Calcular subtotal
        BigDecimal subtotal = calculateSubtotal();

        // Validar desconto maior que subtotal
        if (discount.compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Discount cannot be greater than subtotal");
        }

        this.discount = discount;
        recalculateTotal();
    }

    // ✅ Método para calcular subtotal (sem desconto)
    private BigDecimal calculateSubtotal() {
        return items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ✅ Modificar para incluir desconto
    private void recalculateTotal() {
        BigDecimal subtotal = calculateSubtotal();
        this.total = subtotal.subtract(this.discount);
    }

    public void addItem(String productId, int quantity, BigDecimal price) {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Can only add items to DRAFT orders");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);
        recalculateTotal();
    }

    public void confirm() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot confirm order without items");
        }

        this.status = OrderStatus.CONFIRMED;
    }

    // Getters...

    public BigDecimal getTotal() {
        return total;
    }

    // ✅ Novo getter
    public BigDecimal getDiscount() {
        return discount;
    }
}
```

#### 12.4: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 14 tests passed
```

---

### PASSO 13: 🔴 Cancelar Pedido

Último comportamento: cancelar pedido!

#### 13.1: Escrever Testes (RED 🔴)

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should cancel DRAFT order")
void shouldCancelDraftOrder() {
    // Given
    Order order = new Order("ord-123", "cust-456");

    // When
    order.cancel();

    // Then
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
}

@Test
@DisplayName("Should cancel CONFIRMED order")
void shouldCancelConfirmedOrder() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 1, new BigDecimal("100.00"));
    order.confirm();

    // When
    order.cancel();

    // Then
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
}

@Test
@DisplayName("Should not cancel COMPLETED order")
void shouldNotCancelCompletedOrder() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 1, new BigDecimal("100.00"));
    order.confirm();
    order.complete();  // Completar pedido

    // When/Then
    assertThatThrownBy(() -> order.cancel())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot cancel completed order");
}
```

#### 13.2: Executar Testes (devem FALHAR 🔴)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ❌ 3 tests FAILED
#    Compilation error: Cannot find symbol: method cancel()
#    Compilation error: Cannot find symbol: method complete()
```

#### 13.3: Implementar cancel() e complete() (GREEN 🟢)

```java
// Modificar Order.java

public void confirm() {
    if (items.isEmpty()) {
        throw new IllegalStateException("Cannot confirm order without items");
    }

    if (status != OrderStatus.DRAFT) {
        throw new IllegalStateException("Only DRAFT orders can be confirmed");
    }

    this.status = OrderStatus.CONFIRMED;
}

// ✅ Novo método: cancelar pedido
public void cancel() {
    if (status == OrderStatus.COMPLETED) {
        throw new IllegalStateException("Cannot cancel completed order");
    }

    this.status = OrderStatus.CANCELLED;
}

// ✅ Novo método: completar pedido
public void complete() {
    if (status != OrderStatus.CONFIRMED) {
        throw new IllegalStateException("Only CONFIRMED orders can be completed");
    }

    this.status = OrderStatus.COMPLETED;
}
```

#### 13.4: Executar Testes (TODOS devem PASSAR 🟢)

```bash
./mvnw test -Dtest=OrderTest

# RESULTADO:
# ✅ 17 tests passed ✅
```

---

## 7. Refatoração e Melhoria Contínua

### REFACTOR: Cópia Defensiva na Lista de Items

Atualmente, `getItems()` expõe a lista interna (mutável)!

```java
// ❌ PROBLEMA ATUAL
public List<OrderItem> getItems() {
    return items;  // Retorna lista mutável!
}

// Cliente pode fazer:
order.getItems().clear();  // ❌ Quebra o encapsulamento!
```

#### Escrever Teste para Garantir Imutabilidade

```java
// Adicionar no OrderTest.java

@Test
@DisplayName("Should return defensive copy of items list")
void shouldReturnDefensiveCopyOfItems() {
    // Given
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 1, new BigDecimal("100.00"));

    // When
    List<OrderItem> items = order.getItems();

    // Then
    assertThatThrownBy(() -> items.clear())
        .isInstanceOf(UnsupportedOperationException.class);
}
```

#### Corrigir com Cópia Defensiva

```java
// Modificar Order.java

public List<OrderItem> getItems() {
    return List.copyOf(items);  // ✅ Retorna cópia imutável!
}
```

---

## 8. Checklist TDD

Use este checklist em CADA ciclo:

```
ANTES DE ESCREVER TESTE:
☐ Identifiquei o PRÓXIMO comportamento mais simples?
☐ Estou pensando em UM comportamento por vez (não múltiplos)?

AO ESCREVER TESTE (RED 🔴):
☐ Teste está claro e fácil de entender?
☐ Usei @DisplayName descritivo?
☐ Teste falha pelo motivo CERTO?
☐ Se passar sem código, algo está errado!

AO ESCREVER CÓDIGO (GREEN 🟢):
☐ Escrevi o MÍNIMO para passar?
☐ Evitei over-engineering?
☐ Não adicionei "extras" não testados?
☐ Todos os testes passam?

AO REFATORAR (REFACTOR 🔵):
☐ Eliminei duplicação?
☐ Melhorei legibilidade?
☐ Todos os testes continuam passando?
☐ Não mudei comportamento (só estrutura)?
```

---

## 9. Armadilhas Comuns e Como Evitar

### ❌ Armadilha 1: Testar Implementação, Não Comportamento

```java
// ❌ ERRADO - Testa COMO (implementação)
@Test
void shouldCallRepositorySaveMethod() {
    verify(repository).save(any());  // Testando MOCK!
}

// ✅ CORRETO - Testa O QUE (comportamento)
@Test
void shouldSaveOrderWithCorrectData() {
    Order saved = repository.save(order);
    assertThat(saved.getOrderId()).isEqualTo("ord-123");
}
```

### ❌ Armadilha 2: Testes Grandes Demais

```java
// ❌ ERRADO - Testa TUDO de uma vez
@Test
void shouldHandleCompleteOrderLifecycle() {
    Order order = new Order("ord-123", "cust-456");
    order.addItem("prod-1", 1, new BigDecimal("100.00"));
    order.applyDiscount(new BigDecimal("10.00"));
    order.confirm();
    order.complete();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    assertThat(order.getTotal()).isEqualByComparingTo(new BigDecimal("90.00"));
}

// ✅ CORRETO - Um teste = Um comportamento
@Test
void shouldConfirmOrder() { /* ... */ }

@Test
void shouldApplyDiscount() { /* ... */ }

@Test
void shouldCompleteOrder() { /* ... */ }
```

### ❌ Armadilha 3: Pular o RED

```java
// ❌ ERRADO - Escrever código SEM teste falhar primeiro
// Você escreveu o código e depois criou teste que já passa!

// ✅ CORRETO - SEMPRE ver o teste FALHAR primeiro!
// 1. Escrever teste
// 2. Ver FALHAR (RED 🔴)
// 3. Escrever código
// 4. Ver PASSAR (GREEN 🟢)
```

---

## 10. Exercícios Práticos

### Exercício 1: Remover Item do Pedido

Implemente o método `removeItem(String productId)` seguindo TDD:

**Requisitos:**
- Só pode remover se pedido estiver DRAFT
- Deve recalcular total após remover
- Lançar exceção se productId não existir

**Passos:**
1. Escrever teste para remover item com sucesso
2. Escrever teste para erro se não DRAFT
3. Escrever teste para erro se product não existe
4. Implementar código mínimo
5. Refatorar se necessário

### Exercício 2: Limite de Desconto por Cliente VIP

Adicione suporte para clientes VIP (desconto até 50%) e normais (desconto até 10%):

**Requisitos:**
- Criar enum `CustomerType` (VIP, NORMAL)
- Order deve ter `CustomerType`
- `applyDiscount()` deve respeitar limites

**Dica:** Comece com teste para cliente NORMAL!

### Exercício 3: Total Mínimo para Confirmar

Pedido só pode ser confirmado se total >= R$ 50,00:

**Requisitos:**
- Validar no método `confirm()`
- Lançar exceção com mensagem clara

---

## Conclusão

Parabéns! 🎉 Você aprendeu TDD com Domínio Rico!

**O que você domina agora:**
✅ Ciclo Red-Green-Refactor
✅ Escrever testes ANTES do código
✅ Criar domínio rico com regras de negócio
✅ Validações encapsuladas
✅ Design emergente
✅ Confiança para refatorar

**Próximos passos:**
1. Pratique com os exercícios acima
2. Aplique TDD em features reais do projeto
3. Experimente com outros domínios (Payment, Product, etc)
4. Leia: "Test Driven Development: By Example" (Kent Beck)

**Lembre-se:**
> "TDD is not about testing. TDD is about design."
> — Kent Beck

Agora vá e construa software robusto com TDD! 🚀
