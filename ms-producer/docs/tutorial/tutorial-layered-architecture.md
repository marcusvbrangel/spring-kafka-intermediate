# Tutorial Definitivo: Layered Architecture (Arquitetura em Camadas)

## 📋 Sumário

1. [O que é Layered Architecture](#1-o-que-é-layered-architecture)
2. [Por Que Usar Camadas](#2-por-que-usar-camadas)
3. [As 4 Camadas Fundamentais](#3-as-4-camadas-fundamentais)
4. [Regra de Dependência](#4-regra-de-dependência)
5. [Implementação Passo a Passo](#5-implementação-passo-a-passo)
6. [Separação de Responsabilidades](#6-separação-de-responsabilidades)
7. [DTOs vs Domain Models](#7-dtos-vs-domain-models)
8. [Testes por Camada](#8-testes-por-camada)
9. [Armadilhas Comuns](#9-armadilhas-comuns)
10. [Checklist de Arquitetura](#10-checklist-de-arquitetura)

---

## 1. O que é Layered Architecture

### Definição em 30 Segundos

**Layered Architecture** (Arquitetura em Camadas) organiza o código em **camadas horizontais**, onde cada camada tem uma **responsabilidade específica** e **não conhece detalhes** das camadas inferiores.

```
┌─────────────────────────────────────────────┐
│           PRESENTATION LAYER                │  ← Controllers, APIs, UI
│         (Camada de Apresentação)            │
├─────────────────────────────────────────────┤
│           APPLICATION LAYER                 │  ← Use Cases, Services
│           (Camada de Aplicação)             │
├─────────────────────────────────────────────┤
│             DOMAIN LAYER                    │  ← Regras de Negócio
│            (Camada de Domínio)              │
├─────────────────────────────────────────────┤
│          INFRASTRUCTURE LAYER               │  ← DB, Kafka, APIs externas
│        (Camada de Infraestrutura)           │
└─────────────────────────────────────────────┘

PRINCÍPIO FUNDAMENTAL:
  ↓ Dependências fluem de CIMA para BAIXO
  ↓ Camadas superiores conhecem as inferiores
  ↑ Camadas inferiores NÃO conhecem as superiores
```

### Comparação Visual

```
❌ SEM ARQUITETURA EM CAMADAS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

                 ┌─────────────┐
                 │  CAOS TOTAL │
                 └──────┬──────┘
                        │
    ┌───────────────────┼───────────────────┐
    │                   │                   │
    ↓                   ↓                   ↓
Controller ←→ Repository ←→ Kafka ←→ Domain ←→ DTO

PROBLEMAS:
├─ Tudo conhece tudo (alto acoplamento)
├─ Controller fala diretamente com DB
├─ Repository conhece Controller
├─ Kafka acessa Domain diretamente
├─ Impossível testar isoladamente
├─ Mudança em uma parte quebra tudo
└─ BAGUNÇA! 💥


✅ COM ARQUITETURA EM CAMADAS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌────────────────────────────────────────────┐
│  PRESENTATION LAYER                        │
│  └─ Controller                             │
│     └─ Recebe HTTP Request                 │
│     └─ Converte para Command/Query         │
└──────────────────┬─────────────────────────┘
                   ↓ (chama)
┌────────────────────────────────────────────┐
│  APPLICATION LAYER                         │
│  └─ Use Case / Service                     │
│     └─ Orquestra lógica de aplicação       │
│     └─ Chama Domain + Infrastructure       │
└──────────────────┬─────────────────────────┘
                   ↓ (usa)
┌────────────────────────────────────────────┐
│  DOMAIN LAYER                              │
│  └─ Domain Models (Payment, Order)         │
│     └─ Regras de negócio PURAS             │
│     └─ Não depende de NADA!                │
└────────────────────────────────────────────┘
                   ↑ (conhece)
┌──────────────────┴─────────────────────────┐
│  INFRASTRUCTURE LAYER                      │
│  └─ Repository (JPA)                       │
│  └─ Kafka Producer                         │
│  └─ External APIs                          │
└────────────────────────────────────────────┘

BENEFÍCIOS:
├─ ✅ Baixo acoplamento (cada camada independente)
├─ ✅ Alta coesão (responsabilidades claras)
├─ ✅ Testável (testa cada camada isolada)
├─ ✅ Manutenível (mudanças localizadas)
├─ ✅ Substituível (troca DB sem afetar Domain)
└─ ✅ Escalável (entende onde adicionar código)
```

---

## 2. Por Que Usar Camadas

### Problema Real: Código Sem Camadas

```java
// ❌ TUDO MISTURADO - CÓDIGO REAL QUE VOCÊ VÊ POR AÍ

@RestController
public class PaymentController {

    // ❌ Controller conhece detalhes de infraestrutura!
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private KafkaTemplate kafkaTemplate;

    @PostMapping("/api/payments/approve")
    public ResponseEntity<?> approvePayment(@RequestBody Map<String, Object> request) {

        // ❌ Controller fazendo validação de negócio!
        String paymentId = (String) request.get("paymentId");
        if (paymentId == null || paymentId.isEmpty()) {
            return ResponseEntity.badRequest().body("Invalid payment ID");
        }

        BigDecimal amount = new BigDecimal((String) request.get("amount"));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Invalid amount");
        }

        // ❌ Controller fazendo SQL direto!
        String sql = "INSERT INTO payment (payment_id, amount, status) VALUES (?, ?, ?)";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, paymentId);
        query.setParameter(2, amount);
        query.setParameter(3, "APPROVED");
        query.executeUpdate();

        // ❌ Controller publicando no Kafka!
        Map<String, Object> event = new HashMap<>();
        event.put("paymentId", paymentId);
        event.put("amount", amount);
        kafkaTemplate.send("payment.approved.v1", event);

        // ❌ Controller montando resposta manualmente!
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Payment approved");
        response.put("paymentId", paymentId);

        return ResponseEntity.ok(response);
    }
}

PROBLEMAS REAIS DESSE CÓDIGO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ TESTABILIDADE ZERO
   - Como testar sem banco real?
   - Como testar sem Kafka real?
   - Teste = subir TUDO (lento, frágil)

2. ❌ IMPOSSÍVEL TROCAR TECNOLOGIA
   - Quer trocar PostgreSQL por MongoDB?
   - Precisa mudar CONTROLLER! 💥
   - Quer trocar Kafka por RabbitMQ?
   - Controller quebra! 💥

3. ❌ REGRAS DE NEGÓCIO ESPALHADAS
   - Validação no Controller
   - Se outro endpoint precisa validar?
   - DUPLICAR código! (Copy-paste hell)

4. ❌ ALTO ACOPLAMENTO
   - Controller → EntityManager (JPA)
   - Controller → KafkaTemplate (Kafka)
   - Controller → SQL (PostgreSQL)
   - Mudar 1 coisa = quebra N lugares

5. ❌ MANUTENÇÃO IMPOSSÍVEL
   - Onde está lógica de negócio? (espalhada)
   - Como achar todos os lugares que usam Payment? (grep!)
   - Novo dev entra no time? (vai chorar)

6. ❌ VIOLAÇÃO DE RESPONSABILIDADE ÚNICA (SRP)
   - Controller faz: validação, SQL, Kafka, resposta HTTP
   - Se SQL mudar → Controller muda
   - Se Kafka mudar → Controller muda
   - Se validação mudar → Controller muda
   - Se response mudar → Controller muda
   - MÚLTIPLAS RAZÕES PARA MUDAR! 💥
```

### Solução: Código COM Camadas

```java
// ✅ ARQUITETURA EM CAMADAS - CÓDIGO PROFISSIONAL

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      PRESENTATION LAYER (Camada 1)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ApprovePaymentService approvePaymentService;

    public PaymentController(ApprovePaymentService approvePaymentService) {
        this.approvePaymentService = approvePaymentService;
    }

    @PostMapping("/approve")
    public ResponseEntity<PaymentResponse> approvePayment(
            @Valid @RequestBody ApprovePaymentRequest request) {

        // ✅ Controller SÓ faz:
        // 1. Recebe HTTP
        // 2. Converte DTO → Command
        // 3. Chama Use Case
        // 4. Converte Response → HTTP

        ApprovePaymentCommand command = new ApprovePaymentCommand(
            request.paymentId(),
            request.userId(),
            request.amount(),
            request.currency()
        );

        PaymentDomain payment = approvePaymentService.approvePayment(command);

        PaymentResponse response = PaymentResponse.from(payment);

        return ResponseEntity.ok(response);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      APPLICATION LAYER (Camada 2)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    @Transactional
    public PaymentDomain approvePayment(ApprovePaymentCommand command) {

        // ✅ Service SÓ faz:
        // 1. Orquestra fluxo de aplicação
        // 2. Chama Domain (regras de negócio)
        // 3. Chama Infrastructure (persistência)

        // Criar domínio (regras de negócio executam no construtor)
        PaymentDomain payment = new PaymentDomain(
            command.paymentId(),
            command.userId(),
            command.amount(),
            command.currency()
        );

        // Aprovar (lógica de domínio)
        payment.approve();

        // Persistir
        PaymentDomain saved = paymentRepository.save(payment);

        // Criar evento para Outbox
        PaymentApprovedEvent event = PaymentApprovedEvent.from(saved);
        outboxService.saveEvent("PAYMENT", saved.getPaymentId(),
                               "PAYMENT_APPROVED", "payment.approved.v1",
                               saved.getUserId(), event);

        return saved;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN LAYER (Camada 3)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentDomain {

    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;

    public PaymentDomain(String paymentId, String userId,
                        BigDecimal amount, String currency) {

        // ✅ Domain SÓ faz:
        // 1. Regras de negócio PURAS
        // 2. Validações
        // 3. Invariantes
        // 4. NÃO conhece DB, Kafka, HTTP, NADA!

        validatePaymentId(paymentId);
        validateUserId(userId);
        validateAmount(amount);
        validateCurrency(currency);

        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency.toUpperCase();
        this.status = PaymentStatus.PENDING;
    }

    public void approve() {
        if (status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled payment");
        }
        this.status = PaymentStatus.APPROVED;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    // ... outras validações
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE LAYER (Camada 4)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {

    // ✅ Infrastructure SÓ faz:
    // 1. Acesso a recursos externos (DB, Kafka, APIs)
    // 2. Conversão Domain ↔ Entity
    // 3. NÃO tem lógica de negócio!

    // Spring Data JPA gera implementação automaticamente
}

@Component
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEvent saveEvent(String aggregateType, String aggregateId,
                                 String eventType, String topic,
                                 String partitionKey, Object payload) {
        // Serializar e salvar evento
        String payloadJson = objectMapper.writeValueAsString(payload);

        OutboxEvent event = new OutboxEvent(
            aggregateType, aggregateId, eventType,
            topic, partitionKey, payloadJson
        );

        return repository.save(event);
    }
}


BENEFÍCIOS REAIS DESSE CÓDIGO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ TESTABILIDADE 100%
   ├─ Testa Domain sem DB/Kafka (unit test puro)
   ├─ Testa Service com mocks (isola infraestrutura)
   ├─ Testa Controller com REST Assured
   └─ Cada camada = teste específico

2. ✅ TROCA FÁCIL DE TECNOLOGIA
   ├─ PostgreSQL → MongoDB? Só muda Infrastructure!
   ├─ Kafka → RabbitMQ? Só muda Infrastructure!
   ├─ Domain e Application = INTACTOS!
   └─ Zero impacto nas regras de negócio

3. ✅ REGRAS DE NEGÓCIO CENTRALIZADAS
   ├─ TODA lógica está em PaymentDomain
   ├─ Impossível esquecer validação (construtor)
   ├─ Reuso automático (um só lugar)
   └─ Manutenção simples (mudou em um lugar)

4. ✅ BAIXO ACOPLAMENTO
   ├─ Controller só conhece Service
   ├─ Service conhece Domain + Infrastructure
   ├─ Domain NÃO conhece ninguém (zero deps!)
   └─ Infrastructure conhece Domain (conversões)

5. ✅ MANUTENÇÃO FÁCIL
   ├─ Lógica de negócio? → Domain
   ├─ Orquestração? → Application
   ├─ HTTP? → Presentation
   ├─ DB/Kafka? → Infrastructure
   └─ TUDO tem lugar certo!

6. ✅ SINGLE RESPONSIBILITY PRINCIPLE
   ├─ Controller: HTTP (uma razão para mudar)
   ├─ Service: Orquestração (uma razão para mudar)
   ├─ Domain: Regras de negócio (uma razão para mudar)
   ├─ Infrastructure: DB/Kafka (uma razão para mudar)
   └─ Cada classe = UMA responsabilidade!
```

---

## 3. As 4 Camadas Fundamentais

### Visão Geral das Camadas

```
┌────────────────────────────────────────────────────────┐
│                  PRESENTATION LAYER                    │
│                (Camada de Apresentação)                │
├────────────────────────────────────────────────────────┤
│ Responsabilidade:                                      │
│  • Receber requisições externas (HTTP, gRPC, CLI)      │
│  • Converter DTO → Command/Query                       │
│  • Converter Domain → DTO Response                     │
│  • Validações de ENTRADA (formato, required)           │
│  • Tratamento de exceções HTTP                         │
│                                                        │
│ O QUE TEM:                                             │
│  • Controllers (@RestController)                       │
│  • DTOs de Request/Response                            │
│  • Exception Handlers (@ControllerAdvice)              │
│  • Mappers (DTO ↔ Command)                             │
│                                                        │
│ O QUE NÃO TEM:                                         │
│  ❌ Lógica de negócio                                  │
│  ❌ Acesso ao banco de dados                           │
│  ❌ Publicação no Kafka                                │
│  ❌ Regras de validação de domínio                     │
└────────────────────────────────────────────────────────┘
                         ↓ chama
┌────────────────────────────────────────────────────────┐
│                  APPLICATION LAYER                     │
│                 (Camada de Aplicação)                  │
├────────────────────────────────────────────────────────┤
│ Responsabilidade:                                      │
│  • Orquestrar casos de uso (Use Cases)                 │
│  • Coordenar Domain + Infrastructure                   │
│  • Gerenciar transações (@Transactional)               │
│  • Converter Domain → Events                           │
│  • Lógica de APLICAÇÃO (não de negócio!)               │
│                                                        │
│ O QUE TEM:                                             │
│  • Services (@Service)                                 │
│  • Commands/Queries (CQRS)                             │
│  • Use Case interfaces                                 │
│  • Application DTOs                                    │
│                                                        │
│ O QUE NÃO TEM:                                         │
│  ❌ Regras de negócio (delega ao Domain)               │
│  ❌ SQL direto (delega ao Repository)                  │
│  ❌ Kafka direto (delega ao Producer/Outbox)           │
│  ❌ Validações de domínio (Domain faz isso)            │
└────────────────────────────────────────────────────────┘
                         ↓ usa
┌────────────────────────────────────────────────────────┐
│                    DOMAIN LAYER                        │
│                  (Camada de Domínio)                   │
├────────────────────────────────────────────────────────┤
│ Responsabilidade:                                      │
│  • Regras de negócio PURAS                             │
│  • Validações de domínio (invariantes)                 │
│  • Estado e comportamento (Domain Models)              │
│  • Linguagem ubíqua (termos do negócio)                │
│  • NÃO DEPENDE DE NADA!                                │
│                                                        │
│ O QUE TEM:                                             │
│  • Domain Models (Payment, Order, Product)             │
│  • Value Objects (Money, Address, Email)               │
│  • Enums (PaymentStatus, OrderStatus)                  │
│  • Domain Exceptions                                   │
│  • Domain Services (lógica entre agregados)            │
│                                                        │
│ O QUE NÃO TEM:                                         │
│  ❌ Anotações JPA (@Entity, @Table)                    │
│  ❌ Anotações Spring (@Service, @Component)            │
│  ❌ Dependências externas (Jackson, Kafka, etc)        │
│  ❌ Conhecimento de infraestrutura                     │
└────────────────────────────────────────────────────────┘
                         ↑ conhece
┌────────────────────────────────────────────────────────┐
│                INFRASTRUCTURE LAYER                    │
│              (Camada de Infraestrutura)                │
├────────────────────────────────────────────────────────┤
│ Responsabilidade:                                      │
│  • Acesso a recursos EXTERNOS                          │
│  • Persistência (Database)                             │
│  • Mensageria (Kafka, RabbitMQ)                        │
│  • APIs externas (REST clients)                        │
│  • Conversão Domain ↔ Entity/DTO                       │
│                                                        │
│ O QUE TEM:                                             │
│  • Repositories (Spring Data JPA)                      │
│  • Entities (@Entity, @Table)                          │
│  • Kafka Producers/Consumers                           │
│  • Configuration (@Configuration)                      │
│  • Mappers (Domain ↔ Entity)                           │
│  • External API clients                                │
│                                                        │
│ O QUE NÃO TEM:                                         │
│  ❌ Regras de negócio                                  │
│  ❌ Validações de domínio                              │
│  ❌ Lógica de orquestração (Application faz)           │
│  ❌ HTTP Controllers (Presentation faz)                │
└────────────────────────────────────────────────────────┘
```

### Comparação: O Que Vai em Cada Camada

```
CENÁRIO: Aprovar um Pagamento
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌─────────────────────────────────────────────┐
│ PRESENTATION                                │
├─────────────────────────────────────────────┤
│ ✅ Receber POST /api/payments/approve       │
│ ✅ Validar JSON válido                      │
│ ✅ @Valid paymentId, amount (Bean Valid)    │
│ ✅ Converter Request → Command              │
│ ✅ Chamar Service                           │
│ ✅ Converter Domain → Response              │
│ ✅ Retornar HTTP 200/400/500                │
│                                             │
│ ❌ Validar se amount > 0? NÃO! (Domain faz) │
│ ❌ Salvar no banco? NÃO! (Infra faz)        │
│ ❌ Publicar Kafka? NÃO! (Infra faz)         │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ APPLICATION                                 │
├─────────────────────────────────────────────┤
│ ✅ Receber Command                          │
│ ✅ @Transactional (começar transação)       │
│ ✅ Criar PaymentDomain (chama construtor)   │
│ ✅ Chamar payment.approve()                 │
│ ✅ Chamar repository.save(payment)          │
│ ✅ Chamar outboxService.saveEvent(...)      │
│ ✅ Retornar PaymentDomain                   │
│                                             │
│ ❌ Validar paymentId? NÃO! (Domain faz)     │
│ ❌ Fazer SQL? NÃO! (Repository faz)         │
│ ❌ Serializar JSON? NÃO! (Outbox faz)       │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ DOMAIN                                      │
├─────────────────────────────────────────────┤
│ ✅ Validar paymentId not null/blank         │
│ ✅ Validar amount > 0                       │
│ ✅ Validar currency ISO 4217                │
│ ✅ Converter currency para uppercase        │
│ ✅ Definir status inicial = PENDING         │
│ ✅ Método approve() (PENDING → APPROVED)    │
│ ✅ Regra: não aprova se CANCELLED           │
│                                             │
│ ❌ Saber que vai para PostgreSQL? NÃO!      │
│ ❌ Saber que vai para Kafka? NÃO!           │
│ ❌ Ter @Entity ou @Table? NÃO!              │
│ ❌ Conhecer JSON ou HTTP? NÃO!              │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│ INFRASTRUCTURE                              │
├─────────────────────────────────────────────┤
│ ✅ Converter PaymentDomain → PaymentEntity │
│ ✅ Mapear campos (@Column, @Id)             │
│ ✅ INSERT INTO payment VALUES (...)         │
│ ✅ INSERT INTO outbox_event VALUES (...)    │
│ ✅ Serializar Event para JSON (Jackson)     │
│ ✅ Publicar no Kafka (KafkaTemplate)        │
│ ✅ Configurar DataSource, EntityManager     │
│                                             │
│ ❌ Validar amount > 0? NÃO! (Domain já fez) │
│ ❌ Lógica de aprovação? NÃO! (Domain faz)   │
│ ❌ Orquestrar transação? NÃO! (App faz)     │
└─────────────────────────────────────────────┘
```

---

## 4. Regra de Dependência

### A Regra Mais Importante

```
┌────────────────────────────────────────────┐
│   REGRA DE DEPENDÊNCIA (Dependency Rule)   │
└────────────────────────────────────────────┘

  As dependências só podem apontar PARA DENTRO (ou para baixo)

  ┌─────────────────────────────────┐
  │      PRESENTATION               │
  │      (pode depender de:         │
  │       Application + Domain)     │
  └───────────────┬─────────────────┘
                  ↓ PODE
  ┌───────────────▼─────────────────┐
  │      APPLICATION                │
  │      (pode depender de:         │
  │       Domain + Infrastructure)  │
  └───────────────┬─────────────────┘
                  ↓ PODE
  ┌───────────────▼─────────────────┐
  │      DOMAIN                     │
  │      (NÃO depende de NADA!)     │
  │      (só Java puro + libs util) │
  └─────────────────────────────────┘
                  ↑ PODE conhecer
  ┌───────────────┴─────────────────┐
  │      INFRASTRUCTURE             │
  │      (pode depender de:         │
  │       Domain)                   │
  └─────────────────────────────────┘

SETAS:
  ✅ → Para dentro/baixo (PERMITIDO)
  ❌ ← Para fora/cima (PROIBIDO!)


POR QUE ESSA REGRA?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. DOMAIN = NÚCLEO
   └─ Regras de negócio não mudam com tecnologia
   └─ Se Domain depende de Kafka, e Kafka muda...
   └─ ... você QUEBRA as regras de negócio! 💥

2. MUDANÇAS LOCALIZADAS
   └─ Mudar DB: só Infrastructure muda
   └─ Domain continua funcionando!
   └─ Application continua funcionando!

3. TESTABILIDADE
   └─ Testa Domain sem nenhuma dependência externa
   └─ Testes rápidos (milissegundos)
   └─ Sem mocks (testa POJO puro)

4. REUSO
   └─ Domain pode ser usado em:
       • API REST
       • CLI
       • Batch Jobs
       • gRPC
   └─ Porque Domain NÃO conhece HTTP/CLI/gRPC!
```

### Exemplos de Violação

```java
// ❌ VIOLAÇÃO 1: Domain depende de Infrastructure

package com.mvbr.store.domain.model;

import org.springframework.data.annotation.Id;  // ❌ ERRO!
import javax.persistence.Entity;  // ❌ ERRO!
import javax.persistence.Table;   // ❌ ERRO!

@Entity  // ❌ Domain não pode ter @Entity!
@Table(name = "payment")  // ❌ Domain não conhece DB!
public class PaymentDomain {

    @Id  // ❌ Domain não sabe o que é ID do JPA!
    private String paymentId;

    // ...
}

POR QUE ESTÁ ERRADO?
├─ Domain agora depende de JPA (javax.persistence)
├─ Se trocar JPA por MongoDB: quebra Domain!
├─ Se usar Domain em CLI: precisa JPA no classpath!
├─ Testes precisam carregar JPA (lento!)
└─ Domain deixou de ser PURO!


// ❌ VIOLAÇÃO 2: Domain depende de Application

package com.mvbr.store.domain.model;

import com.mvbr.store.application.service.PaymentService;  // ❌ ERRO!

public class PaymentDomain {

    private final PaymentService paymentService;  // ❌ ERRO!

    public void approve() {
        // ❌ Domain chamando Service de aplicação!
        paymentService.notifyUser(this.userId);
    }
}

POR QUE ESTÁ ERRADO?
├─ Domain agora depende de camada superior!
├─ Dependência INVERTIDA (deveria ser ao contrário)
├─ Domain não pode existir sem Application
└─ Impossível testar Domain isoladamente!


// ❌ VIOLAÇÃO 3: Domain depende de Presentation

package com.mvbr.store.domain.model;

import com.mvbr.store.application.dto.PaymentResponse;  // ❌ ERRO!

public class PaymentDomain {

    // ❌ Domain retornando DTO de API!
    public PaymentResponse toResponse() {
        return new PaymentResponse(this.paymentId, this.amount);
    }
}

POR QUE ESTÁ ERRADO?
├─ Domain conhece formato de resposta HTTP!
├─ Se mudar API: quebra Domain!
├─ Se usar Domain em CLI: DTO não faz sentido!
└─ Presentation → Domain (não o contrário!)


// ✅ CORRETO: Domain PURO

package com.mvbr.store.domain.model;

// ✅ Zero imports de frameworks!
import java.math.BigDecimal;
import java.time.Instant;

public class PaymentDomain {

    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;

    public PaymentDomain(String paymentId, String userId,
                        BigDecimal amount, String currency) {
        // ✅ Só regras de negócio PURAS
        validatePaymentId(paymentId);
        validateAmount(amount);

        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency.toUpperCase();
        this.status = PaymentStatus.PENDING;
    }

    public void approve() {
        // ✅ Lógica de negócio SEM dependências
        if (this.status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled payment");
        }
        this.status = PaymentStatus.APPROVED;
    }

    // ✅ Só getters (sem setters - imutabilidade)
    public String getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
}

POR QUE ESTÁ CORRETO?
├─ ✅ Zero dependências de frameworks
├─ ✅ Pode rodar em qualquer contexto (Web, CLI, Batch)
├─ ✅ Testa com JUnit puro (sem Spring)
├─ ✅ Troca banco sem afetar Domain
└─ ✅ Regras de negócio isoladas e protegidas!
```

---

## 5. Implementação Passo a Passo

### PASSO 1: Criar Estrutura de Pastas

```
src/main/java/com/mvbr/store/
│
├── application/                    ← APPLICATION LAYER
│   ├── service/                   ← Use Cases
│   │   └── ApprovePaymentService.java
│   ├── dto/
│   │   ├── request/               ← DTOs de entrada
│   │   │   └── ApprovePaymentRequest.java
│   │   └── response/              ← DTOs de saída
│   │       └── PaymentResponse.java
│   └── command/                   ← Commands (CQRS)
│       └── ApprovePaymentCommand.java
│
├── domain/                         ← DOMAIN LAYER
│   └── model/
│       ├── payment/
│       │   ├── Payment.java       ← Domain Model (PURO!)
│       │   └── PaymentStatus.java ← Enum
│       └── order/
│           ├── Order.java
│           └── OrderStatus.java
│
├── infrastructure/                 ← INFRASTRUCTURE LAYER
│   ├── adapter/
│   │   └── out/
│   │       ├── persistence/
│   │       │   ├── entity/
│   │       │   │   └── PaymentEntity.java  ← JPA Entity
│   │       │   ├── mapper/
│   │       │   │   └── PaymentMapper.java  ← Domain ↔ Entity
│   │       │   └── PaymentJpaRepository.java
│   │       ├── messaging/
│   │       │   └── producer/
│   │       │       └── PaymentProducer.java
│   │       └── outbox/
│   │           ├── OutboxEvent.java
│   │           ├── OutboxService.java
│   │           └── OutboxPublisher.java
│   ├── config/
│   │   ├── kafka/
│   │   │   └── KafkaProducerConfig.java
│   │   └── database/
│   │       └── DatabaseConfig.java
│   └── messaging/
│       └── event/
│           └── PaymentApprovedEvent.java
│
└── presentation/                   ← PRESENTATION LAYER
    └── controller/
        ├── PaymentController.java
        └── exception/
            └── GlobalExceptionHandler.java


REGRAS DE ORGANIZAÇÃO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. PRESENTATION nunca importa INFRASTRUCTURE diretamente
   ✅ Presentation → Application → Infrastructure

2. DOMAIN nunca importa NADA das outras camadas
   ✅ Domain só importa java.*, libs utilitárias (Apache Commons)

3. INFRASTRUCTURE pode importar DOMAIN
   ✅ Para fazer conversões (Entity → Domain)

4. APPLICATION importa DOMAIN + INFRASTRUCTURE
   ✅ Para orquestrar Use Cases
```

### PASSO 2: Implementar Domain (Camada Mais Interna)

**Sempre começe pelo DOMAIN!** É a camada mais importante.

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN LAYER - PaymentStatus.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.domain.model.payment;

/**
 * Estados possíveis de um Payment.
 *
 * Domain Layer - NÃO depende de nada!
 */
public enum PaymentStatus {
    PENDING,    // Aguardando aprovação
    APPROVED,   // Aprovado
    CANCELLED   // Cancelado
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      DOMAIN LAYER - Payment.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.domain.model.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * Payment - Domínio Rico (Domain Model).
 *
 * REGRAS:
 * - Imutável (final fields)
 * - Validações no construtor (fail-fast)
 * - Sem dependências externas (POJO puro)
 * - Comportamento + Estado
 */
public class Payment {

    private final String paymentId;
    private final String userId;
    private final BigDecimal amount;
    private final String currency;
    private PaymentStatus status;
    private final Instant createdAt;

    /**
     * Construtor - TODAS as validações aqui!
     */
    public Payment(String paymentId, String userId,
                   BigDecimal amount, String currency) {

        // Validações de domínio (regras de negócio)
        validatePaymentId(paymentId);
        validateUserId(userId);
        validateAmount(amount);
        validateCurrency(currency);

        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = normalizeCurrency(currency);
        this.status = PaymentStatus.PENDING;  // Estado inicial
        this.createdAt = Instant.now();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      COMPORTAMENTO (Métodos de Negócio)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Aprovar pagamento (transição de estado).
     *
     * REGRA: Não pode aprovar se já cancelado.
     */
    public void approve() {
        if (this.status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                "Cannot approve a cancelled payment"
            );
        }

        this.status = PaymentStatus.APPROVED;
    }

    /**
     * Cancelar pagamento (transição de estado).
     *
     * REGRA: Não pode cancelar se já aprovado.
     */
    public void cancel() {
        if (this.status == PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                "Cannot cancel an approved payment"
            );
        }

        this.status = PaymentStatus.CANCELLED;
    }

    /**
     * Verificar se pagamento foi aprovado.
     */
    public boolean isApproved() {
        return this.status == PaymentStatus.APPROVED;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      VALIDAÇÕES (Regras de Domínio)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void validatePaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException(
                "Payment ID cannot be null or blank"
            );
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                "User ID cannot be null or blank"
            );
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException(
                "Amount cannot be null"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Amount must be greater than zero"
            );
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException(
                "Currency cannot be null or blank"
            );
        }

        try {
            // Validar se é código ISO 4217 válido
            Currency.getInstance(currency.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                "Invalid currency code: " + currency
            );
        }
    }

    private String normalizeCurrency(String currency) {
        return currency.toUpperCase();  // USD, BRL, EUR
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //      GETTERS (SEM setters - imutabilidade)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public String getPaymentId() { return paymentId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
```

### PASSO 3: Implementar Infrastructure (Adaptadores)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE - PaymentEntity.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.adapter.out.persistence.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PaymentEntity - JPA Entity (Infrastructure).
 *
 * IMPORTANTE:
 * - Esta classe SÓ existe na camada Infrastructure
 * - Domain NÃO conhece esta classe!
 * - Mapper converte: PaymentEntity ↔ Payment (Domain)
 */
@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    @Column(name = "payment_id", nullable = false, length = 100)
    private String paymentId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Construtor padrão (JPA exige)
    protected PaymentEntity() {}

    // Construtor completo
    public PaymentEntity(String paymentId, String userId, BigDecimal amount,
                        String currency, PaymentStatus status, Instant createdAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters e Setters (JPA usa)
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE - PaymentMapper.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.adapter.out.persistence.mapper;

import com.mvbr.store.domain.model.payment.Payment;  // ← Domain
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;  // ← Infra
import org.springframework.stereotype.Component;

/**
 * Mapper: converte PaymentEntity ↔ Payment (Domain).
 *
 * Infrastructure conhece Domain (pode importar).
 * Domain NÃO conhece Infrastructure (não pode importar).
 */
@Component
public class PaymentMapper {

    /**
     * Converter Domain → Entity (para salvar no DB).
     */
    public PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus(),
            payment.getCreatedAt()
        );
    }

    /**
     * Converter Entity → Domain (ao buscar do DB).
     */
    public Payment toDomain(PaymentEntity entity) {
        // Reconstruir Domain a partir da Entity
        Payment payment = new Payment(
            entity.getPaymentId(),
            entity.getUserId(),
            entity.getAmount(),
            entity.getCurrency()
        );

        // Se status não é PENDING, precisa mudar
        if (entity.getStatus() == PaymentStatus.APPROVED) {
            payment.approve();
        } else if (entity.getStatus() == PaymentStatus.CANCELLED) {
            payment.cancel();
        }

        return payment;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INFRASTRUCTURE - PaymentRepository.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.infrastructure.adapter.out.persistence;

import com.mvbr.store.domain.model.payment.Payment;
import com.mvbr.store.infrastructure.adapter.out.persistence.entity.PaymentEntity;
import com.mvbr.store.infrastructure.adapter.out.persistence.mapper.PaymentMapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * PaymentRepository - Abstração sobre JPA.
 *
 * Interface do Domain (Port), implementação na Infrastructure (Adapter).
 */
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentMapper mapper;

    public PaymentRepositoryImpl(PaymentJpaRepository jpaRepository,
                                 PaymentMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        // Converter Domain → Entity
        PaymentEntity entity = mapper.toEntity(payment);

        // Salvar no banco
        PaymentEntity saved = jpaRepository.save(entity);

        // Converter Entity → Domain e retornar
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return jpaRepository.findById(paymentId)
            .map(mapper::toDomain);  // Entity → Domain
    }
}

// Interface JPA (detalhe de implementação)
interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String> {
    // Spring Data JPA gera implementação automaticamente
}
```

### PASSO 4: Implementar Application (Use Cases)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      APPLICATION - ApprovePaymentCommand.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.command;

import java.math.BigDecimal;

/**
 * Command: representa a intenção de aprovar um pagamento.
 *
 * CQRS - Command Query Responsibility Segregation
 */
public record ApprovePaymentCommand(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency
) {}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      APPLICATION - ApprovePaymentService.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.service;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.domain.model.payment.Payment;
import com.mvbr.store.infrastructure.adapter.out.persistence.PaymentRepository;
import com.mvbr.store.infrastructure.adapter.out.outbox.OutboxService;
import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * ApprovePaymentService - Use Case (Application Layer).
 *
 * Responsabilidade:
 * - Orquestrar o fluxo de aprovação
 * - Coordenar Domain + Infrastructure
 * - Gerenciar transação
 * - NÃO tem regras de negócio (delega ao Domain)
 */
@Service
public class ApprovePaymentService {

    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    public ApprovePaymentService(PaymentRepository paymentRepository,
                                OutboxService outboxService) {
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
    }

    /**
     * Aprovar pagamento (Use Case).
     *
     * Fluxo:
     * 1. Criar Payment (Domain) - validações executam
     * 2. Aprovar (Domain) - regras de negócio
     * 3. Salvar (Infrastructure)
     * 4. Criar evento para Outbox (Infrastructure)
     */
    @Transactional
    public Payment approvePayment(ApprovePaymentCommand command) {

        // 1. Criar Domain Model (validações executam aqui!)
        Payment payment = new Payment(
            command.paymentId(),
            command.userId(),
            command.amount(),
            command.currency()
        );

        // 2. Executar lógica de negócio (Domain)
        payment.approve();  // PENDING → APPROVED

        // 3. Persistir (Infrastructure)
        Payment savedPayment = paymentRepository.save(payment);

        // 4. Criar evento para Outbox (garantia de publicação)
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            generateEventId(),
            savedPayment.getPaymentId(),
            savedPayment.getUserId(),
            savedPayment.getAmount(),
            savedPayment.getCurrency(),
            savedPayment.getStatus().name(),
            Instant.now().toEpochMilli()
        );

        outboxService.saveEvent(
            "PAYMENT",                   // aggregateType
            savedPayment.getPaymentId(), // aggregateId
            "PAYMENT_APPROVED",          // eventType
            "payment.approved.v1",       // topic
            savedPayment.getUserId(),    // partitionKey
            event                        // payload
        );

        return savedPayment;
    }

    private String generateEventId() {
        return "evt-" + java.util.UUID.randomUUID();
    }
}
```

### PASSO 5: Implementar Presentation (API REST)

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      PRESENTATION - ApprovePaymentRequest.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.dto.request;

import javax.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO de Request (Presentation Layer).
 *
 * Validações de FORMATO (Bean Validation).
 * Validações de NEGÓCIO ficam no Domain!
 */
public record ApprovePaymentRequest(

    @NotBlank(message = "Payment ID is required")
    @Size(min = 5, max = 100, message = "Payment ID must be between 5 and 100 characters")
    String paymentId,

    @NotBlank(message = "User ID is required")
    String userId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters (ISO 4217)")
    String currency
) {}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      PRESENTATION - PaymentResponse.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.application.dto.response;

import com.mvbr.store.domain.model.payment.Payment;
import java.math.BigDecimal;

/**
 * DTO de Response (Presentation Layer).
 *
 * Converte Domain → JSON para API REST.
 */
public record PaymentResponse(
    String paymentId,
    String userId,
    BigDecimal amount,
    String currency,
    String status
) {

    /**
     * Factory method: cria Response a partir do Domain.
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getStatus().name()
        );
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      PRESENTATION - PaymentController.java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

package com.mvbr.store.presentation.controller;

import com.mvbr.store.application.command.ApprovePaymentCommand;
import com.mvbr.store.application.dto.request.ApprovePaymentRequest;
import com.mvbr.store.application.dto.response.PaymentResponse;
import com.mvbr.store.application.service.ApprovePaymentService;
import com.mvbr.store.domain.model.payment.Payment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * PaymentController - REST API (Presentation Layer).
 *
 * Responsabilidade:
 * - Receber HTTP Request
 * - Validar formato (@Valid)
 * - Converter DTO → Command
 * - Chamar Service (Application)
 * - Converter Domain → Response DTO
 * - Retornar HTTP Response
 *
 * NÃO faz:
 * - Lógica de negócio (Domain faz)
 * - Acesso ao banco (Infrastructure faz)
 * - Publicar Kafka (Infrastructure faz)
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final ApprovePaymentService approvePaymentService;

    public PaymentController(ApprovePaymentService approvePaymentService) {
        this.approvePaymentService = approvePaymentService;
    }

    /**
     * POST /api/payments/approve
     *
     * Aprovar um pagamento.
     */
    @PostMapping("/approve")
    public ResponseEntity<PaymentResponse> approvePayment(
            @Valid @RequestBody ApprovePaymentRequest request) {

        // 1. Converter DTO → Command
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            request.paymentId(),
            request.userId(),
            request.amount(),
            request.currency()
        );

        // 2. Executar Use Case (Application Layer)
        Payment payment = approvePaymentService.approvePayment(command);

        // 3. Converter Domain → Response DTO
        PaymentResponse response = PaymentResponse.from(payment);

        // 4. Retornar HTTP 200 OK
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
```

---

## 6. Separação de Responsabilidades

### O Que Cada Camada NUNCA Deve Fazer

```
┌─────────────────────────────────────────────────────┐
│ PRESENTATION                                        │
├─────────────────────────────────────────────────────┤
│ ❌ NUNCA fazer:                                     │
│   • Acessar banco de dados diretamente             │
│   • Publicar no Kafka                              │
│   • Validações de regras de negócio                │
│   • Cálculos de domínio                            │
│   • Conhecer JPA, SQL, Kafka                       │
│                                                     │
│ ✅ SEMPRE fazer:                                    │
│   • Validações de formato (@Valid)                 │
│   • Conversão DTO → Command                        │
│   • Conversão Domain → Response                    │
│   • Tratamento de exceções HTTP                    │
│   • Chamar Application Layer                       │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ APPLICATION                                         │
├─────────────────────────────────────────────────────┤
│ ❌ NUNCA fazer:                                     │
│   • Validações de domínio (Domain faz)             │
│   • Conhecer detalhes de HTTP/JSON                 │
│   • SQL direto                                     │
│   • Kafka direto (usar Outbox Pattern)             │
│                                                     │
│ ✅ SEMPRE fazer:                                    │
│   • Orquestrar fluxo de Use Case                   │
│   • Gerenciar transações (@Transactional)          │
│   • Coordenar Domain + Infrastructure              │
│   • Converter entre camadas se necessário          │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ DOMAIN                                              │
├─────────────────────────────────────────────────────┤
│ ❌ NUNCA fazer:                                     │
│   • Depender de frameworks (Spring, JPA, Jackson)  │
│   • Conhecer HTTP, JSON, SQL, Kafka                │
│   • Ter anotações de infraestrutura                │
│   • Chamar Services ou Repositories                │
│                                                     │
│ ✅ SEMPRE fazer:                                    │
│   • Regras de negócio PURAS                        │
│   • Validações de invariantes                      │
│   • Comportamento + Estado (Domain Model)          │
│   • Ser testável sem nenhuma dependência           │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ INFRASTRUCTURE                                      │
├─────────────────────────────────────────────────────┤
│ ❌ NUNCA fazer:                                     │
│   • Regras de negócio                              │
│   • Validações de domínio                          │
│   • Lógica de orquestração (Application faz)       │
│                                                     │
│ ✅ SEMPRE fazer:                                    │
│   • Acesso a recursos externos (DB, Kafka, APIs)   │
│   • Conversão Domain ↔ Entity                      │
│   • Configurações de frameworks                    │
│   • Implementação de Ports (Repository, etc)       │
└─────────────────────────────────────────────────────┘
```

---

## 7. DTOs vs Domain Models

### Por Que NÃO Usar Domain Diretamente na API?

```java
// ❌ ERRO COMUM: Expor Domain na API

@RestController
public class PaymentController {

    @PostMapping("/api/payments")
    public Payment createPayment(@RequestBody Payment payment) {
        // ❌ Domain diretamente no @RequestBody!
        return paymentService.save(payment);
    }
}

PROBLEMAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ ACOPLAMENTO
   - API acoplada ao Domain
   - Mudar Domain = quebra contrato da API
   - Cliente externo depende de estrutura interna

2. ❌ SEGURANÇA
   - Cliente pode mandar campos que não deveria
   - Exemplo: {"paymentId": "...", "status": "APPROVED"}
   - Domain aceita = bypass de validações!

3. ❌ VERSIONAMENTO
   - API v1 usa Domain V1
   - Domain evolui para V2 (novo campo)
   - API quebra para clientes antigos! 💥

4. ❌ FLEXIBILIDADE
   - API precisa de formato diferente do Domain
   - Exemplo: API retorna "amount" em centavos, Domain usa BigDecimal
   - Impossível adaptar!

5. ❌ VALIDAÇÕES DUPLICADAS
   - Domain valida regras de negócio
   - API precisa validar formato (@NotNull, @Size)
   - Mistura responsabilidades!


// ✅ CORRETO: DTO para API, Domain internamente

@RestController
public class PaymentController {

    @PostMapping("/api/payments")
    public PaymentResponse createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {

        // ✅ DTO na entrada (API)
        // ✅ Converte DTO → Domain
        // ✅ Domain internamente
        // ✅ Converte Domain → DTO na saída

        Payment payment = new Payment(
            request.paymentId(),
            request.userId(),
            request.amount(),
            request.currency()
        );

        Payment saved = paymentService.save(payment);

        return PaymentResponse.from(saved);
    }
}

BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ DESACOPLAMENTO
   - API independente do Domain
   - Domain muda sem quebrar API
   - Contrato da API estável

2. ✅ SEGURANÇA
   - DTO só tem campos permitidos
   - Impossível mandar "status" diretamente
   - Domain sempre em estado consistente

3. ✅ VERSIONAMENTO
   - API v1 → DTO v1 → Domain (versão atual)
   - API v2 → DTO v2 → Domain (versão atual)
   - Múltiplas versões de API, um Domain!

4. ✅ FLEXIBILIDADE
   - DTO pode ter formato diferente
   - DTO.amount (centavos) → Domain.amount (BigDecimal)
   - Adaptação na conversão

5. ✅ SEPARAÇÃO DE RESPONSABILIDADES
   - DTO: validações de FORMATO (@NotNull, @Size)
   - Domain: validações de NEGÓCIO (amount > 0, currency ISO)
   - Cada um faz seu papel!
```

### Quando Usar DTO vs Domain

```
USE DTO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ API REST (Request/Response)
✅ Mensageria Kafka (Events)
✅ Comunicação entre microservices
✅ Serialização JSON/XML
✅ Camada de Presentation


USE DOMAIN:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Lógica de negócio (Application + Domain Layer)
✅ Validações de regras
✅ Cálculos e comportamento
✅ Testes unitários (POJO puro)
✅ Nunca expor para fora do sistema!


CONVERSÕES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

API Request (DTO)
    ↓ converte
Command (Application)
    ↓ usa para criar
Domain Model (Domain)
    ↓ processa regras
Domain Model atualizado
    ↓ converte
Response DTO (API)
```

---

## 8. Testes por Camada

### Testar DOMAIN (Unit Tests - Mais Rápidos)

```java
/**
 * Testes de DOMAIN LAYER.
 *
 * Características:
 * - SEM Spring (@SpringBootTest)
 * - SEM banco de dados
 * - SEM Kafka
 * - POJO puro (milissegundos para rodar)
 * - Testa REGRAS DE NEGÓCIO isoladas
 */
class PaymentTest {

    @Test
    @DisplayName("Should create payment with valid data")
    void shouldCreatePaymentWithValidData() {
        // Given
        String paymentId = "pay-123";
        String userId = "user-456";
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "USD";

        // When
        Payment payment = new Payment(paymentId, userId, amount, currency);

        // Then
        assertThat(payment.getPaymentId()).isEqualTo(paymentId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("Should throw exception when amount is zero")
    void shouldThrowExceptionWhenAmountIsZero() {
        // When/Then
        assertThatThrownBy(() ->
            new Payment("pay-123", "user-456", BigDecimal.ZERO, "USD")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Amount must be greater than zero");
    }

    @Test
    @DisplayName("Should approve payment when status is PENDING")
    void shouldApprovePaymentWhenStatusIsPending() {
        // Given
        Payment payment = new Payment("pay-123", "user-456",
                                     new BigDecimal("100.00"), "USD");

        // When
        payment.approve();

        // Then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.isApproved()).isTrue();
    }

    @Test
    @DisplayName("Should not approve cancelled payment")
    void shouldNotApproveCancelledPayment() {
        // Given
        Payment payment = new Payment("pay-123", "user-456",
                                     new BigDecimal("100.00"), "USD");
        payment.cancel();

        // When/Then
        assertThatThrownBy(() -> payment.approve())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Cannot approve a cancelled payment");
    }
}

BENEFÍCIOS:
├─ ⚡ Rápido (milissegundos)
├─ ✅ Sem dependências externas
├─ ✅ Testa regras de negócio puras
└─ ✅ Confiança total no Domain
```

### Testar APPLICATION (Integration Tests com Mocks)

```java
/**
 * Testes de APPLICATION LAYER.
 *
 * Características:
 * - USA Mockito para simular Infrastructure
 * - NÃO usa banco real (mock Repository)
 * - NÃO usa Kafka real (mock Outbox)
 * - Testa ORQUESTRAÇÃO do Use Case
 */
@ExtendWith(MockitoExtension.class)
class ApprovePaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private ApprovePaymentService service;

    @Test
    @DisplayName("Should approve payment and save event to outbox")
    void shouldApprovePaymentAndSaveEventToOutbox() {
        // Given
        ApprovePaymentCommand command = new ApprovePaymentCommand(
            "pay-123", "user-456", new BigDecimal("100.00"), "USD"
        );

        Payment savedPayment = new Payment(
            command.paymentId(), command.userId(),
            command.amount(), command.currency()
        );
        savedPayment.approve();

        when(paymentRepository.save(any(Payment.class)))
            .thenReturn(savedPayment);

        // When
        Payment result = service.approvePayment(command);

        // Then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);

        // Verificar que salvou no repository
        verify(paymentRepository, times(1)).save(any(Payment.class));

        // Verificar que criou evento no outbox
        verify(outboxService, times(1)).saveEvent(
            eq("PAYMENT"),
            eq("pay-123"),
            eq("PAYMENT_APPROVED"),
            eq("payment.approved.v1"),
            eq("user-456"),
            any(PaymentApprovedEvent.class)
        );
    }
}

BENEFÍCIOS:
├─ ✅ Testa orquestração (chamou Repository? Outbox?)
├─ ✅ Sem banco real (usa mocks)
├─ ✅ Rápido (segundos)
└─ ✅ Isola camada Application
```

### Testar PRESENTATION (API Integration Tests)

```java
/**
 * Testes de PRESENTATION LAYER.
 *
 * Características:
 * - USA @SpringBootTest ou @WebMvcTest
 * - REST Assured para testar API
 * - Mock Application Layer (não precisa banco)
 * - Testa contrato da API (HTTP)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @MockBean
    private ApprovePaymentService approvePaymentService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("Should return 200 OK when payment is approved")
    void shouldReturn200WhenPaymentIsApproved() {
        // Given
        Payment payment = new Payment("pay-123", "user-456",
                                     new BigDecimal("100.00"), "USD");
        payment.approve();

        when(approvePaymentService.approvePayment(any()))
            .thenReturn(payment);

        // When/Then
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "paymentId": "pay-123",
                    "userId": "user-456",
                    "amount": 100.00,
                    "currency": "USD"
                }
                """)
        .when()
            .post("/api/payments/approve")
        .then()
            .statusCode(200)
            .body("paymentId", equalTo("pay-123"))
            .body("status", equalTo("APPROVED"));
    }

    @Test
    @DisplayName("Should return 400 when amount is negative")
    void shouldReturn400WhenAmountIsNegative() {
        // When/Then
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "paymentId": "pay-123",
                    "userId": "user-456",
                    "amount": -100.00,
                    "currency": "USD"
                }
                """)
        .when()
            .post("/api/payments/approve")
        .then()
            .statusCode(400)
            .body("errors[0].field", equalTo("amount"))
            .body("errors[0].message", containsString("greater than zero"));
    }
}

BENEFÍCIOS:
├─ ✅ Testa contrato da API (JSON, HTTP codes)
├─ ✅ Validações de formato (@Valid)
├─ ✅ Conversão DTO → Command → Response
└─ ✅ Garante que API funciona corretamente
```

---

## 9. Armadilhas Comuns

### ❌ Armadilha 1: Anemic Domain (Domínio Anêmico)

```java
// ❌ ERRADO - Domain só com getters/setters

public class Payment {
    private String paymentId;
    private PaymentStatus status;

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String id) { this.paymentId = id; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
}

// Lógica vaza para Service
@Service
public class PaymentService {
    public void approve(Payment payment) {
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled");
        }
        payment.setStatus(PaymentStatus.APPROVED);  // ❌ Lógica fora do Domain!
    }
}

// ✅ CORRETO - Domain com comportamento

public class Payment {
    private final String paymentId;
    private PaymentStatus status;

    // SEM setters! (imutabilidade)

    public void approve() {
        // ✅ Lógica DENTRO do Domain
        if (this.status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot approve cancelled");
        }
        this.status = PaymentStatus.APPROVED;
    }
}

@Service
public class PaymentService {
    public void approve(Payment payment) {
        payment.approve();  // ✅ Domain faz o trabalho!
    }
}
```

### ❌ Armadilha 2: Domain Depende de Infrastructure

```java
// ❌ ERRADO - Domain com @Entity

@Entity  // ❌ Anotação JPA no Domain!
@Table(name = "payment")
public class Payment {
    @Id  // ❌ Domain não pode depender de JPA!
    private String paymentId;
}

// ✅ CORRETO - Separar Domain e Entity

// Domain (puro)
public class Payment {
    private final String paymentId;
    // ... sem anotações!
}

// Entity (Infrastructure)
@Entity
@Table(name = "payment")
class PaymentEntity {
    @Id
    private String paymentId;
}

// Mapper converte Payment ↔ PaymentEntity
```

### ❌ Armadilha 3: Controller com Lógica de Negócio

```java
// ❌ ERRADO - Controller com validação de negócio

@RestController
public class PaymentController {

    @PostMapping("/api/payments")
    public Payment create(@RequestBody PaymentRequest request) {
        // ❌ Validação de negócio no Controller!
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }

        // ❌ Cálculo no Controller!
        BigDecimal total = request.amount().add(request.tax());

        // ...
    }
}

// ✅ CORRETO - Controller só orquestra

@RestController
public class PaymentController {

    @PostMapping("/api/payments")
    public PaymentResponse create(@Valid @RequestBody PaymentRequest request) {
        // ✅ Só converte e chama Service
        Payment payment = service.create(request);
        return PaymentResponse.from(payment);
    }
}
```

---

## 10. Checklist de Arquitetura

```
ANTES DE CRIAR UMA CLASSE:
☐ Qual camada ela pertence? (Presentation/Application/Domain/Infrastructure)
☐ Qual a ÚNICA responsabilidade dela?
☐ Ela depende de camadas superiores? (❌ proibido!)
☐ Ela está no pacote correto?

DOMAIN LAYER:
☐ Domain não tem anotações de framework? (@Entity, @Service, @Component)
☐ Domain não importa nada de outras camadas?
☐ Domain só importa java.* e libs utilitárias?
☐ Validações estão no construtor/métodos?
☐ Comportamento está junto com estado?
☐ Não tem setters (imutabilidade)?

APPLICATION LAYER:
☐ Service orquestra, não tem regras de negócio?
☐ Service chama Domain para lógica?
☐ Service chama Infrastructure para persistência?
☐ @Transactional está no Service?

PRESENTATION LAYER:
☐ Controller não acessa Repository diretamente?
☐ Controller não tem lógica de negócio?
☐ DTOs são usados (não Domain direto)?
☐ @Valid está nos DTOs?

INFRASTRUCTURE LAYER:
☐ Entity está separada de Domain?
☐ Mapper converte Entity ↔ Domain?
☐ Repository retorna Domain (não Entity)?
☐ Configurações estão aqui?
```

---

## Conclusão

Parabéns! 🎉 Você domina Layered Architecture!

**O que você aprendeu:**
✅ As 4 camadas fundamentais
✅ Regra de dependência (só para dentro/baixo)
✅ Domain puro sem dependências
✅ Separação de responsabilidades
✅ DTOs vs Domain Models
✅ Como testar cada camada
✅ Armadilhas comuns

**Lembre-se:**
> "Arquitetura é sobre separar o que muda do que não muda."

- Domain = não muda (regras de negócio)
- Infrastructure = muda (tecnologias)

**Próximos passos:**
1. Refatore código existente aplicando camadas
2. Crie novos Use Cases seguindo o padrão
3. Estude Hexagonal Architecture (evolução natural)
4. Leia: "Clean Architecture" (Uncle Bob)

🚀 Agora construa sistemas escaláveis e manuteníveis com Arquitetura em Camadas!
