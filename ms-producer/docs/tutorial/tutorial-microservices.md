# Tutorial Definitivo: Arquitetura de Microserviços

---

## 📋 Sumário

1. [O que são Microserviços](#1-o-que-são-microserviços)
2. [Monolito vs Microserviços](#2-monolito-vs-microserviços)
3. [Quando Usar Microserviços](#3-quando-usar-microserviços)
4. [Características Fundamentais](#4-características-fundamentais)
5. [Comunicação entre Microserviços](#5-comunicação-entre-microserviços)
6. [Padrões Essenciais](#6-padrões-essenciais)
7. [Implementação com Spring Boot](#7-implementação-com-spring-boot)
8. [Containerização e Orquestração](#8-containerização-e-orquestração)
9. [Gerenciamento de Dados](#9-gerenciamento-de-dados)
10. [Observabilidade](#10-observabilidade)
11. [Segurança](#11-segurança)
12. [Testes em Microserviços](#12-testes-em-microserviços)
13. [Desafios e Armadilhas](#13-desafios-e-armadilhas)
14. [Checklist de Microserviços](#14-checklist-de-microserviços)
15. [Exercícios Práticos](#15-exercícios-práticos)

---

## 1. O que são Microserviços

### Definição em 30 Segundos

**Microserviços** é uma arquitetura onde a aplicação é **decomposta** em **serviços pequenos e independentes**, cada um executando em seu **próprio processo** e se comunicando via **APIs leves** (HTTP/REST, gRPC, mensageria).

```
MONOLITO:
  Uma aplicação GRANDE, tudo junto

  ┌─────────────────────────────────────────┐
  │                                         │
  │         MONOLITO                        │
  │                                         │
  │  ┌──────────┐  ┌──────────┐            │
  │  │ Users    │  │ Products │            │
  │  └──────────┘  └──────────┘            │
  │  ┌──────────┐  ┌──────────┐            │
  │  │ Orders   │  │ Payments │            │
  │  └──────────┘  └──────────┘            │
  │                                         │
  │  Tudo no MESMO processo                 │
  │  Tudo no MESMO banco de dados           │
  │  Deploy TUDO junto                      │
  │                                         │
  └─────────────────────────────────────────┘


MICROSERVIÇOS:
  Múltiplos serviços PEQUENOS, independentes

  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │   User       │   │   Product    │   │   Order      │
  │   Service    │   │   Service    │   │   Service    │
  ├──────────────┤   ├──────────────┤   ├──────────────┤
  │ API REST     │   │ API REST     │   │ API REST     │
  │ Database     │   │ Database     │   │ Database     │
  │ Próprio      │   │ Próprio      │   │ Próprio      │
  └──────────────┘   └──────────────┘   └──────────────┘
         │                  │                   │
         └──────────────────┴───────────────────┘
                            │
                     Comunicação via
                     HTTP, gRPC, Kafka

  ✅ Cada serviço = processo separado
  ✅ Cada serviço = banco separado
  ✅ Deploy independente
  ✅ Escala independente
```

**Conceitos-chave:**

- **Serviço** = Unidade deployável independente
- **Bounded Context** = Limite lógico de um serviço (DDD)
- **API Gateway** = Ponto de entrada único para clientes
- **Service Discovery** = Serviços se encontram dinamicamente
- **Resiliência** = Falhas isoladas (Circuit Breaker)
- **Observabilidade** = Logs, métricas, tracing distribuído

**Em português claro:**

Ao invés de ter UMA aplicação gigante com tudo junto (users, products, orders, payments), você divide em VÁRIOS serviços pequenos, cada um responsável por uma parte específica do negócio. Cada serviço roda independente, tem seu próprio banco, e conversa com outros via APIs.

---

## 2. Monolito vs Microserviços

### Comparação Visual Completa

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
         ARQUITETURA MONOLÍTICA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

                 CLIENT
                   │
                   ▼
         ┌─────────────────────┐
         │    LOAD BALANCER    │
         └─────────────────────┘
                   │
         ┌─────────┴─────────┐
         ▼                   ▼
    ┌─────────┐         ┌─────────┐
    │ APP     │         │ APP     │
    │ Instance│         │ Instance│
    │   1     │         │   2     │
    └─────────┘         └─────────┘
         │                   │
         └─────────┬─────────┘
                   ▼
         ┌─────────────────────┐
         │  BANCO DE DADOS     │
         │  (Único, Shared)    │
         └─────────────────────┘

Estrutura Interna de CADA Instância:
┌─────────────────────────────────────────┐
│           MONOLITO.jar                  │
├─────────────────────────────────────────┤
│  com.company.app                        │
│    ├── controller/                      │
│    │   ├── UserController              │
│    │   ├── ProductController           │
│    │   ├── OrderController             │
│    │   └── PaymentController           │
│    │                                    │
│    ├── service/                         │
│    │   ├── UserService                 │
│    │   ├── ProductService              │
│    │   ├── OrderService                │
│    │   └── PaymentService              │
│    │                                    │
│    └── repository/                      │
│        ├── UserRepository              │
│        ├── ProductRepository           │
│        ├── OrderRepository             │
│        └── PaymentRepository           │
│                                         │
│  TUDO no MESMO PROCESSO                 │
│  TUDO no MESMO CODEBASE                 │
│  TUDO deployado JUNTO                   │
└─────────────────────────────────────────┘

CARACTERÍSTICAS:
✅ Simples de desenvolver (um projeto)
✅ Simples de testar (tudo junto)
✅ Simples de deployar (um artefato)
✅ Transações ACID (mesmo banco)
✅ Latência baixa (chamadas locais)

❌ Difícil de escalar (escala TUDO ou NADA)
❌ Deploy arriscado (tudo ou nada)
❌ Tecnologia única (uma linguagem, um framework)
❌ Acoplamento alto (tudo conectado)
❌ Time grande = conflitos no código


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
      ARQUITETURA DE MICROSERVIÇOS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

                 CLIENT
                   │
                   ▼
         ┌─────────────────────┐
         │    API GATEWAY      │
         │  (Roteamento)       │
         └─────────────────────┘
                   │
         ┌─────────┼─────────┬─────────┐
         ▼         ▼         ▼         ▼
    ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
    │  USER   │ │ PRODUCT │ │  ORDER  │ │ PAYMENT │
    │ SERVICE │ │ SERVICE │ │ SERVICE │ │ SERVICE │
    ├─────────┤ ├─────────┤ ├─────────┤ ├─────────┤
    │ API     │ │ API     │ │ API     │ │ API     │
    │ Lógica  │ │ Lógica  │ │ Lógica  │ │ Lógica  │
    └─────────┘ └─────────┘ └─────────┘ └─────────┘
         │         │         │         │
         ▼         ▼         ▼         ▼
    ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
    │  USER   │ │ PRODUCT │ │  ORDER  │ │ PAYMENT │
    │   DB    │ │   DB    │ │   DB    │ │   DB    │
    └─────────┘ └─────────┘ └─────────┘ └─────────┘

         │         │         │         │
         └─────────┴─────────┴─────────┘
                     │
              ┌──────┴──────┐
              │   KAFKA     │  ← Comunicação assíncrona
              │ (Mensageria)│
              └─────────────┘

CARACTERÍSTICAS:
✅ Escala INDEPENDENTE (só Order Service, por ex)
✅ Deploy INDEPENDENTE (só Payment Service)
✅ Tecnologia HETEROGÊNEA (Java, Python, Go)
✅ Times AUTÔNOMOS (cada time = um serviço)
✅ Falhas ISOLADAS (User down ≠ Payment down)
✅ Evolução GRADUAL (migra aos poucos)

❌ Complexidade OPERACIONAL (N serviços)
❌ Consistência EVENTUAL (dados distribuídos)
❌ Latência de REDE (chamadas remotas)
❌ Testes COMPLEXOS (integração entre serviços)
❌ Debugging DIFÍCIL (logs distribuídos)
❌ Transações DISTRIBUÍDAS (Saga pattern)
```

### Tabela Comparativa Detalhada

| Aspecto | Monolito | Microserviços |
|---------|----------|---------------|
| **Estrutura** | Uma aplicação | Múltiplos serviços independentes |
| **Deployment** | Tudo junto (1 artefato) | Independente (N artefatos) |
| **Escalabilidade** | Vertical (escala TUDO) | Horizontal (escala serviço específico) |
| **Banco de Dados** | Único, compartilhado | Um por serviço (database per service) |
| **Tecnologia** | Única (ex: Java) | Heterogênea (Java, Python, Go, etc) |
| **Desenvolvimento** | ✅ Simples (um projeto) | ⚠️ Complexo (N projetos) |
| **Testes** | ✅ Simples (tudo local) | ⚠️ Complexo (integração distribuída) |
| **Deploy** | ⚠️ Arriscado (tudo ou nada) | ✅ Seguro (deploy independente) |
| **Latência** | ✅ Baixa (chamadas locais) | ⚠️ Maior (rede) |
| **Consistência** | ✅ ACID (transações) | ⚠️ Eventual (distribuída) |
| **Falhas** | ❌ Cascata (tudo cai junto) | ✅ Isoladas (resiliência) |
| **Times** | ⚠️ Todos no mesmo código | ✅ Autônomos (cada serviço) |
| **Complexidade** | ✅ Baixa | ❌ Alta |
| **Manutenção** | ⚠️ Difícil quando grande | ✅ Fácil (serviços pequenos) |
| **Onboarding** | ✅ Rápido (um projeto) | ⚠️ Lento (N serviços) |

---

## 3. Quando Usar Microserviços

### ✅ Quando USAR Microserviços

```
1. ESCALABILIDADE DIFERENCIADA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cenário:
  - User Service: 100 req/s
  - Product Service: 1000 req/s  ← 10x mais!
  - Order Service: 500 req/s

Com Microserviços:
  ✅ Product Service: 10 instâncias
  ✅ Order Service: 5 instâncias
  ✅ User Service: 1 instância
  ✅ Escala SÓ o que precisa (economia!)

Com Monolito:
  ❌ Precisa escalar TUDO (10 instâncias)
  ❌ User e Order desperdiçam recursos


2. TIMES MÚLTIPLOS E AUTÔNOMOS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cenário:
  - 50+ desenvolvedores
  - 5 squads (User, Product, Order, Payment, Shipping)

Com Microserviços:
  ✅ Cada squad = um serviço
  ✅ Deploy independente (não espera outros squads)
  ✅ Tecnologia escolhida pelo squad
  ✅ Sem conflitos no código (repos separados)

Com Monolito:
  ❌ Todos no MESMO código (merge hell)
  ❌ Deploy coordenado (espera todos squads)
  ❌ Tecnologia única (imposta para todos)


3. DEPLOY FREQUENTE E SEGURO
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cenário:
  - Deploy múltiplas vezes ao dia
  - CI/CD maduro

Com Microserviços:
  ✅ Deploy Payment Service às 10h
  ✅ Deploy User Service às 14h
  ✅ Deploy Order Service às 16h
  ✅ Falha isolada (só Payment afetado)

Com Monolito:
  ❌ Deploy TUDO de uma vez
  ❌ Bug em Payment = rollback TUDO
  ❌ Deploy arriscado (downtime)


4. TECNOLOGIAS HETEROGÊNEAS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cenário:
  - Recomendação de produtos: Python (ML)
  - Pagamentos: Java (transacional)
  - Notificações: Go (concorrência)

Com Microserviços:
  ✅ Recommendation Service: Python
  ✅ Payment Service: Java
  ✅ Notification Service: Go
  ✅ Ferramenta certa para problema certo

Com Monolito:
  ❌ Uma tecnologia para TUDO


5. EVOLUÇÃO GRADUAL (MIGRAÇÃO)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cenário:
  - Monolito legado gigante
  - Quer modernizar aos poucos

Com Microserviços:
  ✅ Extrai Payment Service (novo)
  ✅ Extrai User Service (novo)
  ✅ Monolito reduz gradualmente
  ✅ Migração sem big bang

Com Monolito:
  ❌ Reescrita completa (anos!)
  ❌ Big bang (arriscado)
```

### ❌ Quando NÃO Usar Microserviços

```
1. STARTUP/MVP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Problema:
  - Time pequeno (2-5 devs)
  - Produto ainda validando (pivot frequente)
  - Recursos limitados

Microserviços:
  ❌ Overhead operacional alto
  ❌ Complexidade desnecessária
  ❌ Time gasta tempo com infraestrutura

✅ RECOMENDAÇÃO: Comece com MONOLITO MODULAR
  - Módulos bem separados (preparado para split)
  - Deploy simples
  - Migra para microserviços QUANDO crescer


2. EQUIPE PEQUENA
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Problema:
  - 5-10 desenvolvedores
  - Sem DevOps dedicado

Microserviços:
  ❌ 10+ serviços = overhead
  ❌ CI/CD complexo (N pipelines)
  ❌ Monitoramento distribuído (difícil)

✅ RECOMENDAÇÃO: Monolito até ter ~20+ devs


3. APLICAÇÃO SIMPLES (CRUD)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Problema:
  - CRUD simples (backoffice)
  - Tráfego baixo (< 1000 req/s)
  - Poucas funcionalidades

Microserviços:
  ❌ Overengineering
  ❌ Complexidade > benefício

✅ RECOMENDAÇÃO: Monolito simples


4. FORTE ACOPLAMENTO DE NEGÓCIO
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Problema:
  - Funcionalidades fortemente acopladas
  - Impossível separar bounded contexts
  - Transações ACID obrigatórias

Microserviços:
  ❌ Separação artificial (aumenta complexidade)
  ❌ Chamadas de rede desnecessárias
  ❌ Consistência difícil

✅ RECOMENDAÇÃO: Monolito (ou monolito modular)


5. SEM MATURIDADE EM DEVOPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Problema:
  - Sem CI/CD
  - Sem containerização
  - Sem monitoramento
  - Sem automação

Microserviços:
  ❌ Impossível gerenciar N serviços manualmente
  ❌ Deploy manual = pesadelo

✅ RECOMENDAÇÃO: Invista em DevOps ANTES de microserviços
```

### Regra de Ouro

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  "Não use microserviços porque está na moda.               │
│   Use quando os BENEFÍCIOS superarem a COMPLEXIDADE."      │
│                                                             │
│  BENEFÍCIOS:                                                │
│    • Escalabilidade diferenciada                            │
│    • Deploy independente                                    │
│    • Times autônomos                                        │
│    • Resiliência                                            │
│                                                             │
│  COMPLEXIDADE:                                              │
│    • Distribuição (rede, latência)                          │
│    • Consistência eventual                                  │
│    • Debugging distribuído                                  │
│    • Testes complexos                                       │
│                                                             │
│  SE: complexidade > benefícios                              │
│  ENTÃO: NÃO use microserviços!                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Características Fundamentais

### 1. Independência de Deploy

```
CADA SERVIÇO deployado INDEPENDENTEMENTE

┌─────────────────────────────────────────────────────────────┐
│  DIA 1: Deploy Payment Service v2.0                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  08:00 → Desenvolver Payment Service v2.0                   │
│  10:00 → Testes unitários                                   │
│  12:00 → Merge PR                                           │
│  14:00 → CI/CD: build + deploy                              │
│  14:30 → Payment Service v2.0 em PRODUÇÃO ✅                │
│                                                             │
│  Outros serviços:                                           │
│    ✅ User Service v1.5 (não mudou)                         │
│    ✅ Order Service v3.2 (não mudou)                        │
│    ✅ Product Service v1.0 (não mudou)                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘

BENEFÍCIOS:
✅ Deploy rápido (só Payment)
✅ Rollback fácil (só Payment)
✅ Sem coordenação com outros times
✅ Deploy múltiplas vezes ao dia
```

### 2. Escalabilidade Independente

```
ESCALA SÓ O QUE PRECISA

Cenário: Black Friday (alta demanda)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Normal:
  User Service:    1 instância  (100 req/s)
  Product Service: 2 instâncias (500 req/s)
  Order Service:   3 instâncias (1000 req/s)
  Payment Service: 2 instâncias (500 req/s)

Black Friday:
  User Service:    1 instância  ← Não mudou (demanda estável)
  Product Service: 10 instâncias ← 5x (busca de produtos explode)
  Order Service:   15 instâncias ← 5x (pedidos explodem)
  Payment Service: 8 instâncias  ← 4x (pagamentos explodem)

✅ Escala SÓ Product, Order, Payment
✅ User Service economiza recursos (não precisa)
✅ Custo otimizado
```

### 3. Banco de Dados por Serviço

```
DATABASE PER SERVICE

┌──────────────────────────────────────────────────────────┐
│  CADA SERVIÇO TEM SEU PRÓPRIO BANCO                      │
└──────────────────────────────────────────────────────────┘

  ┌────────────┐        ┌────────────┐        ┌────────────┐
  │   User     │        │  Product   │        │   Order    │
  │  Service   │        │  Service   │        │  Service   │
  └─────┬──────┘        └─────┬──────┘        └─────┬──────┘
        │                     │                      │
        ▼                     ▼                      ▼
  ┌────────────┐        ┌────────────┐        ┌────────────┐
  │   users    │        │  products  │        │   orders   │
  │     DB     │        │     DB     │        │     DB     │
  │            │        │            │        │            │
  │ PostgreSQL │        │ PostgreSQL │        │  MongoDB   │
  └────────────┘        └────────────┘        └────────────┘

REGRAS:
✅ Serviço acessa SÓ seu banco
❌ Order Service NÃO pode fazer JOIN em users table
❌ User Service NÃO pode acessar products DB

Se Order precisa de dados de User:
  → Chama User Service API (não acessa DB direto)


BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ TECNOLOGIA HETEROGÊNEA
   - User: PostgreSQL (relacional)
   - Product: PostgreSQL (relacional)
   - Order: MongoDB (documentos)
   - Search: Elasticsearch (busca)

2. ✅ ESCALA INDEPENDENTE
   - Product DB: réplicas de leitura
   - Order DB: sharding por customer_id

3. ✅ MUDANÇAS ISOLADAS
   - Migração User DB (PostgreSQL 12 → 15)
   - Outros serviços NÃO afetados

4. ✅ FALHAS ISOLADAS
   - Product DB down
   - Order/User continuam funcionando
```

### 4. Comunicação via APIs Leves

```
COMUNICAÇÃO VIA HTTP/REST, gRPC, MENSAGERIA

┌─────────────────────────────────────────────────────────────┐
│              COMUNICAÇÃO SÍNCRONA (HTTP/gRPC)               │
└─────────────────────────────────────────────────────────────┘

Order Service precisa validar User:

  ┌────────────┐                       ┌────────────┐
  │   Order    │                       │    User    │
  │  Service   │                       │  Service   │
  └─────┬──────┘                       └─────┬──────┘
        │                                    │
        │  GET /api/users/user-123           │
        │ ──────────────────────────────────>│
        │                                    │
        │  200 OK {name: "John"}             │
        │ <──────────────────────────────────│
        │                                    │
        ▼                                    ▼

  ✅ Request/Response (síncrono)
  ✅ Baixa latência (milissegundos)
  ⚠️ Acoplamento temporal (Order espera User)


┌─────────────────────────────────────────────────────────────┐
│           COMUNICAÇÃO ASSÍNCRONA (Mensageria/Kafka)         │
└─────────────────────────────────────────────────────────────┘

Order criado → Notificar Payment:

  ┌────────────┐                       ┌────────────┐
  │   Order    │                       │  Payment   │
  │  Service   │                       │  Service   │
  └─────┬──────┘                       └─────┬──────┘
        │                                    │
        │  Publish OrderCreatedEvent         │
        │ ──────────────────────>            │
        │        KAFKA                       │
        │                         Subscribe  │
        │                         <──────────│
        │                                    │
        ▼                                    ▼

  ✅ Fire-and-forget (assíncrono)
  ✅ Desacoplamento (Order não espera Payment)
  ✅ Resiliência (Payment down = evento fica no Kafka)
  ⚠️ Consistência eventual
```

### 5. Falhas Isoladas (Resiliência)

```
CIRCUIT BREAKER PATTERN

┌─────────────────────────────────────────────────────────────┐
│  Product Service CAIU                                       │
└─────────────────────────────────────────────────────────────┘

SEM Circuit Breaker:
  Order Service → chama Product Service
  Product Service → DOWN (timeout 30s)
  Order Service → timeout, retry
  Product Service → DOWN (timeout 30s)
  Order Service → timeout, retry
  Product Service → DOWN (timeout 30s)

  ❌ Order Service TRAVA (esperando Product)
  ❌ Threads bloqueadas
  ❌ Order Service CAI (cascade failure)


COM Circuit Breaker:
  Order Service → chama Product Service
  Product Service → DOWN (timeout)
  Circuit Breaker → ABRE (detecta falha)

  Próximas chamadas:
    Order Service → Circuit Breaker
    Circuit Breaker → ABERTO (fail-fast)
    Order Service → responde IMEDIATAMENTE
                    "Produto indisponível"

  ✅ Order Service continua funcionando
  ✅ Resposta rápida (não espera timeout)
  ✅ Falha ISOLADA (Product down ≠ Order down)


  Depois de N segundos:
    Circuit Breaker → HALF-OPEN (testa Product)
    Product Service → UP
    Circuit Breaker → FECHA (volta ao normal)
```

---

## 5. Comunicação entre Microserviços

### Síncrona vs Assíncrona

```
┌─────────────────────────────────────────────────────────────┐
│              COMUNICAÇÃO SÍNCRONA (REST/gRPC)               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Cliente → Service A → Service B → Service C               │
│              ↓          ↓          ↓                        │
│           ESPERA     ESPERA     ESPERA                      │
│                                                             │
│  CARACTERÍSTICAS:                                           │
│    ✅ Request/Response imediato                             │
│    ✅ Baixa latência (ms)                                   │
│    ✅ Fácil de implementar                                  │
│    ❌ Acoplamento temporal (espera resposta)                │
│    ❌ Propagação de falhas (timeout cascade)                │
│    ❌ Não escala bem (blocking I/O)                         │
│                                                             │
│  QUANDO USAR:                                               │
│    • Precisa resposta IMEDIATA (validação)                  │
│    • Query de dados (GET)                                   │
│    • Latência crítica                                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────┐
│          COMUNICAÇÃO ASSÍNCRONA (Kafka/RabbitMQ)            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Service A → publica evento → KAFKA                         │
│                                  ↓                          │
│                        Service B, C subscrevem              │
│                        (processam quando puderem)           │
│                                                             │
│  CARACTERÍSTICAS:                                           │
│    ✅ Fire-and-forget (não espera)                          │
│    ✅ Desacoplamento total                                  │
│    ✅ Resiliência (evento persiste)                         │
│    ✅ Escala bem (non-blocking)                             │
│    ❌ Consistência eventual                                 │
│    ❌ Debugging complexo (eventos distribuídos)             │
│                                                             │
│  QUANDO USAR:                                               │
│    • Não precisa resposta IMEDIATA                          │
│    • Notificações, processamento em background              │
│    • Integração entre bounded contexts                      │
│    • Eventos de domínio (OrderCreated, PaymentApproved)     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Exemplo Prático: Order → Payment

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMUNICAÇÃO SÍNCRONA (REST)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// Order Service
@Service
public class CreateOrderService {

    private final PaymentServiceClient paymentClient;  // ← REST client

    @Transactional
    public Order createOrder(CreateOrderRequest request) {

        // 1. Criar order
        Order order = new Order(request);
        orderRepository.save(order);

        // 2. Chamar Payment Service (SÍNCRONO)
        try {
            PaymentResponse payment = paymentClient.createPayment(
                new CreatePaymentRequest(order.getTotal())
            );

            order.setPaymentId(payment.getId());
            orderRepository.save(order);

        } catch (FeignException e) {
            // ❌ Payment Service DOWN
            // ❌ Order NÃO foi criado (rollback)
            throw new PaymentServiceUnavailableException();
        }

        return order;
    }
}

// Payment Service Client (Feign)
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    @PostMapping("/api/payments")
    PaymentResponse createPayment(@RequestBody CreatePaymentRequest request);
}

PROBLEMAS:
  ❌ Order Service DEPENDE de Payment Service
  ❌ Payment Service down = Order Service não funciona
  ❌ Latência alta (duas chamadas de rede sequenciais)


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      COMUNICAÇÃO ASSÍNCRONA (Kafka)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// Order Service
@Service
public class CreateOrderService {

    private final OutboxService outboxService;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {

        // 1. Criar order
        Order order = new Order(request);
        orderRepository.save(order);

        // 2. Publicar evento (ASSÍNCRONO)
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal()
        );

        outboxService.save("Order", order.getId(), "OrderCreated", event);

        // ✅ Retorna IMEDIATAMENTE (não espera Payment)
        return order;
    }
}

// Payment Service (Consumer)
@Component
public class OrderEventHandler {

    @KafkaListener(topics = "order.created.v1")
    public void handleOrderCreated(OrderCreatedEvent event) {

        // Payment Service processa QUANDO PUDER
        Payment payment = createPayment(event);
        paymentRepository.save(payment);

        // Publica evento de volta
        PaymentCreatedEvent paymentEvent = new PaymentCreatedEvent(
            payment.getId(),
            event.getOrderId()
        );

        kafkaTemplate.send("payment.created.v1", paymentEvent);
    }
}

BENEFÍCIOS:
  ✅ Order Service NÃO depende de Payment Service
  ✅ Payment Service down = evento fica no Kafka (processa depois)
  ✅ Resiliência total
  ✅ Escala independente
```

---

## 6. Padrões Essenciais

### 1. API Gateway

```
API GATEWAY = Ponto de entrada ÚNICO para clientes

┌─────────────────────────────────────────────────────────────┐
│                          CLIENTE                            │
│                      (Mobile, Web)                          │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            │ TODAS requisições passam aqui
                            ▼
                  ┌─────────────────────┐
                  │    API GATEWAY      │
                  │  (Spring Cloud      │
                  │   Gateway)          │
                  └─────────┬───────────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ▼                  ▼                  ▼
    ┌─────────┐        ┌─────────┐        ┌─────────┐
    │  User   │        │ Product │        │  Order  │
    │ Service │        │ Service │        │ Service │
    └─────────┘        └─────────┘        └─────────┘

RESPONSABILIDADES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ ROTEAMENTO
   GET /api/users/*      → User Service
   GET /api/products/*   → Product Service
   GET /api/orders/*     → Order Service

2. ✅ AUTENTICAÇÃO/AUTORIZAÇÃO
   - Valida JWT token
   - Se inválido: 401 Unauthorized
   - Se válido: encaminha para serviço

3. ✅ RATE LIMITING
   - Limita 100 req/s por cliente
   - Previne DDoS

4. ✅ CACHING
   - Cache de respostas (Redis)
   - Reduz carga nos serviços

5. ✅ LOAD BALANCING
   - Product Service tem 5 instâncias
   - Gateway distribui carga (round-robin)

6. ✅ CIRCUIT BREAKER
   - Product Service down
   - Gateway retorna fallback

7. ✅ LOGGING/MONITORING
   - Log centralizado
   - Métricas (latência, erros)


IMPLEMENTAÇÃO (Spring Cloud Gateway):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // User Service
            .route("user-service", r -> r
                .path("/api/users/**")
                .filters(f -> f
                    .circuitBreaker(config -> config
                        .setName("userServiceCB")
                        .setFallbackUri("forward:/fallback/users")
                    )
                )
                .uri("lb://user-service")  // Load balanced
            )

            // Product Service
            .route("product-service", r -> r
                .path("/api/products/**")
                .filters(f -> f
                    .requestRateLimiter(config -> config
                        .setRateLimiter(redisRateLimiter())
                    )
                )
                .uri("lb://product-service")
            )

            .build();
    }
}
```

### 2. Service Discovery

```
SERVICE DISCOVERY = Serviços se encontram DINAMICAMENTE

┌─────────────────────────────────────────────────────────────┐
│              SEM SERVICE DISCOVERY                          │
└─────────────────────────────────────────────────────────────┘

Order Service precisa chamar Payment Service:

  application.yml (Order Service):
    payment-service:
      url: http://payment-service-01.prod.com:8080  ← IP fixo

  PROBLEMAS:
    ❌ IP fixo (e se Payment mudar de IP?)
    ❌ Não sabe se Payment está UP ou DOWN
    ❌ Não sabe quantas instâncias de Payment existem
    ❌ Load balancing manual


┌─────────────────────────────────────────────────────────────┐
│              COM SERVICE DISCOVERY (Eureka)                 │
└─────────────────────────────────────────────────────────────┘

                    ┌─────────────────────┐
                    │  EUREKA SERVER      │
                    │ (Service Registry)  │
                    └──────────┬──────────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
         ▼                     ▼                     ▼
    ┌─────────┐          ┌─────────┐          ┌─────────┐
    │  User   │          │ Product │          │ Payment │
    │ Service │          │ Service │          │ Service │
    │ (3 inst)│          │ (5 inst)│          │ (2 inst)│
    └─────────┘          └─────────┘          └─────────┘
         │                     │                     │
         └─────────────────────┴─────────────────────┘
                               │
                    REGISTRAM-SE no Eureka
                    (heartbeat a cada 30s)


FLUXO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Payment Service INICIA
   → Registra-se no Eureka Server
   → Nome: "payment-service"
   → IPs: [192.168.1.10:8080, 192.168.1.11:8080]

2. Order Service precisa chamar Payment
   → Pergunta ao Eureka: "Onde está payment-service?"
   → Eureka responde: [192.168.1.10:8080, 192.168.1.11:8080]
   → Order Service escolhe uma instância (load balancing)

3. Payment Service CAI (uma instância)
   → Eureka detecta (sem heartbeat)
   → Remove da lista
   → Order Service NÃO chama instância morta


IMPLEMENTAÇÃO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// Eureka Server
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}

// Payment Service (Eureka Client)
@SpringBootApplication
@EnableDiscoveryClient
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

application.yml:
  spring:
    application:
      name: payment-service  ← Nome no Eureka
  eureka:
    client:
      service-url:
        defaultZone: http://localhost:8761/eureka/

// Order Service (chama Payment via Eureka)
@Service
public class OrderService {

    @Autowired
    private RestTemplate restTemplate;  // Com @LoadBalanced

    public void createOrder(Order order) {
        // ✅ "payment-service" resolve dinamicamente via Eureka
        String url = "http://payment-service/api/payments";

        PaymentResponse payment = restTemplate.postForObject(
            url,
            new CreatePaymentRequest(order.getTotal()),
            PaymentResponse.class
        );
    }
}

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced  // ← Habilita Service Discovery
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

### 3. Circuit Breaker (Resilience4j)

```
CIRCUIT BREAKER = Previne chamadas a serviços que estão falhando

┌─────────────────────────────────────────────────────────────┐
│              ESTADOS DO CIRCUIT BREAKER                     │
└─────────────────────────────────────────────────────────────┘

  ┌─────────────┐
  │   CLOSED    │  ← Estado normal (chamadas fluem)
  │  (Normal)   │
  └──────┬──────┘
         │
         │ Falhas > threshold (ex: 50%)
         │
         ▼
  ┌─────────────┐
  │    OPEN     │  ← Circuito ABERTO (fail-fast)
  │  (Aberto)   │     NÃO chama serviço
  └──────┬──────┘     Retorna fallback IMEDIATAMENTE
         │
         │ Após timeout (ex: 60s)
         │
         ▼
  ┌─────────────┐
  │ HALF-OPEN   │  ← Testa se serviço voltou
  │ (Meio-Abre) │     Permite N chamadas (ex: 3)
  └──────┬──────┘
         │
         ├─ Sucesso → volta para CLOSED
         └─ Falha   → volta para OPEN


IMPLEMENTAÇÃO (Resilience4j):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class OrderService {

    private final PaymentServiceClient paymentClient;

    @CircuitBreaker(name = "payment-service", fallbackMethod = "createOrderFallback")
    public Order createOrder(CreateOrderRequest request) {

        // Chamar Payment Service
        PaymentResponse payment = paymentClient.createPayment(...);

        // ...
    }

    // Fallback: executado quando Circuit Breaker ABRE
    public Order createOrderFallback(CreateOrderRequest request, Exception ex) {

        // Criar Order SEM payment (processar depois)
        Order order = new Order(request);
        order.setPaymentStatus("PENDING");

        orderRepository.save(order);

        // Publicar evento para processar payment depois
        publishEvent(new OrderCreatedEvent(order.getId()));

        return order;
    }
}

application.yml:
  resilience4j:
    circuitbreaker:
      instances:
        payment-service:
          failure-rate-threshold: 50        # 50% de falhas = OPEN
          wait-duration-in-open-state: 60s  # Espera 60s antes de HALF-OPEN
          permitted-number-of-calls-in-half-open-state: 3  # 3 chamadas de teste
          sliding-window-size: 10           # Janela de 10 chamadas
```

### 4. Distributed Tracing

```
DISTRIBUTED TRACING = Rastrear requisição através de MÚLTIPLOS serviços

┌─────────────────────────────────────────────────────────────┐
│  PROBLEMA: Requisição passa por 5 serviços                  │
│            Onde está o gargalo?                             │
└─────────────────────────────────────────────────────────────┘

Cliente → API Gateway → User Service → Order Service
                                            ↓
                                        Product Service
                                            ↓
                                        Payment Service

Requisição demorou 2 segundos. ONDE?
  ❌ User Service?
  ❌ Order Service?
  ❌ Product Service?
  ❌ Payment Service?

SEM TRACING: Impossível saber!


┌─────────────────────────────────────────────────────────────┐
│  SOLUÇÃO: Distributed Tracing (Zipkin/Jaeger)              │
└─────────────────────────────────────────────────────────────┘

Trace ID: abc-123 (mesma requisição)

Span 1: API Gateway       (50ms)
  │
  ├─ Span 2: User Service    (100ms)
  │
  ├─ Span 3: Order Service   (200ms)
  │   │
  │   ├─ Span 4: Product Service (800ms)  ← GARGALO!
  │   │
  │   └─ Span 5: Payment Service  (850ms) ← GARGALO!
  │
  Total: 2000ms

✅ Identifica EXATAMENTE onde está lento (Product + Payment)


IMPLEMENTAÇÃO (Spring Cloud Sleuth + Zipkin):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>

application.yml:
  spring:
    sleuth:
      sampler:
        probability: 1.0  # 100% das requisições (dev)
    zipkin:
      base-url: http://localhost:9411

// Logs automáticos com Trace ID
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public Order createOrder(CreateOrderRequest request) {

        // Sleuth adiciona Trace ID automaticamente nos logs
        log.info("Creating order for user {}", request.getUserId());
        // [order-service,abc-123,def-456,true] Creating order for user...
        //                ↑ Trace ID (mesmo em todos serviços)

        // ...
    }
}

// Zipkin UI: http://localhost:9411
// → Visualiza trace completo (timeline de todos spans)
```

---

## 7. Implementação com Spring Boot

### Estrutura de um Microserviço

```
payment-service/
│
├── pom.xml
├── Dockerfile
├── docker-compose.yml
│
└── src/
    ├── main/
    │   ├── java/com/company/payment/
    │   │   ├── PaymentServiceApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── KafkaConfig.java
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── RestTemplateConfig.java
    │   │   │
    │   │   ├── controller/
    │   │   │   └── PaymentController.java
    │   │   │
    │   │   ├── service/
    │   │   │   └── PaymentService.java
    │   │   │
    │   │   ├── domain/
    │   │   │   └── Payment.java
    │   │   │
    │   │   ├── repository/
    │   │   │   └── PaymentRepository.java
    │   │   │
    │   │   ├── client/
    │   │   │   └── OrderServiceClient.java  ← Feign client
    │   │   │
    │   │   ├── messaging/
    │   │   │   ├── producer/
    │   │   │   │   └── PaymentEventProducer.java
    │   │   │   └── consumer/
    │   │   │       └── OrderEventConsumer.java
    │   │   │
    │   │   └── exception/
    │   │       └── PaymentNotFoundException.java
    │   │
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/
    │           └── V001__create_payment_table.sql
    │
    └── test/
        └── java/com/company/payment/
            ├── PaymentServiceTest.java
            └── PaymentControllerTest.java
```

### Configuração Básica

```yaml
# application.yml

spring:
  application:
    name: payment-service  # Nome do serviço

  # Database
  datasource:
    url: jdbc:postgresql://localhost:5432/payment_db
    username: payment_user
    password: ${DB_PASSWORD}

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  # Kafka
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: payment-service
      auto-offset-reset: earliest
    producer:
      acks: all
      retries: 3

# Server
server:
  port: 8082

# Eureka (Service Discovery)
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 30

# Actuator (Health check)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

# Circuit Breaker
resilience4j:
  circuitbreaker:
    instances:
      order-service:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        sliding-window-size: 10

# Distributed Tracing
  sleuth:
    sampler:
      probability: 1.0
  zipkin:
    base-url: http://localhost:9411

# Logging
logging:
  level:
    com.company.payment: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
```

### Application Class

```java
package com.company.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient      // ← Service Discovery (Eureka)
@EnableFeignClients         // ← REST clients (Feign)
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

### Controller

```java
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {

        Payment payment = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PaymentResponse.from(payment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {

        Payment payment = paymentService.getPayment(id);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    // Health check (usado por Kubernetes/Docker)
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
```

### Service com Circuit Breaker

```java
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderServiceClient orderServiceClient;
    private final PaymentEventProducer eventProducer;

    @Transactional
    public Payment createPayment(CreatePaymentRequest request) {

        // 1. Validar Order (chama Order Service via Feign)
        Order order = getOrderWithCircuitBreaker(request.getOrderId());

        // 2. Criar Payment
        Payment payment = new Payment(request);
        payment.setOrderId(order.getId());
        paymentRepository.save(payment);

        // 3. Publicar evento
        eventProducer.publishPaymentCreated(payment);

        return payment;
    }

    @CircuitBreaker(name = "order-service", fallbackMethod = "getOrderFallback")
    private Order getOrderWithCircuitBreaker(UUID orderId) {
        return orderServiceClient.getOrder(orderId);
    }

    // Fallback: executado quando Order Service está down
    private Order getOrderFallback(UUID orderId, Exception ex) {
        // Criar Order vazio ou buscar do cache
        return new Order(orderId, BigDecimal.ZERO);
    }
}
```

### Feign Client

```java
@FeignClient(name = "order-service")  // ← Resolve via Eureka
public interface OrderServiceClient {

    @GetMapping("/api/orders/{id}")
    Order getOrder(@PathVariable("id") UUID id);
}
```

---

## 8. Containerização e Orquestração

### Dockerfile

```dockerfile
# payment-service/Dockerfile

# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Criar usuário não-root (segurança)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar JAR
COPY --from=build /app/target/payment-service.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8082/api/payments/health || exit 1

# Porta
EXPOSE 8082

# JVM options
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Executar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### Docker Compose (Ambiente Local)

```yaml
# docker-compose.yml

version: '3.8'

services:

  # Service Discovery
  eureka-server:
    image: eureka-server:latest
    ports:
      - "8761:8761"
    networks:
      - microservices-network

  # API Gateway
  api-gateway:
    image: api-gateway:latest
    ports:
      - "8080:8080"
    environment:
      EUREKA_URL: http://eureka-server:8761/eureka/
    depends_on:
      - eureka-server
    networks:
      - microservices-network

  # User Service
  user-service:
    build: ./user-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: user-db
      DB_PASSWORD: user_pass
      EUREKA_URL: http://eureka-server:8761/eureka/
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - user-db
      - eureka-server
      - kafka
    networks:
      - microservices-network

  user-db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: user_db
      POSTGRES_USER: user_user
      POSTGRES_PASSWORD: user_pass
    volumes:
      - user-db-data:/var/lib/postgresql/data
    networks:
      - microservices-network

  # Payment Service
  payment-service:
    build: ./payment-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: payment-db
      DB_PASSWORD: payment_pass
      EUREKA_URL: http://eureka-server:8761/eureka/
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - payment-db
      - eureka-server
      - kafka
    networks:
      - microservices-network

  payment-db:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: payment_db
      POSTGRES_USER: payment_user
      POSTGRES_PASSWORD: payment_pass
    volumes:
      - payment-db-data:/var/lib/postgresql/data
    networks:
      - microservices-network

  # Kafka
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    networks:
      - microservices-network

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    networks:
      - microservices-network

  # Monitoring
  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"
    networks:
      - microservices-network

networks:
  microservices-network:
    driver: bridge

volumes:
  user-db-data:
  payment-db-data:
```

### Kubernetes (Produção)

```yaml
# payment-service-deployment.yml

apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  labels:
    app: payment-service
spec:
  replicas: 3  # 3 instâncias
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
    spec:
      containers:
      - name: payment-service
        image: company/payment-service:1.0.0
        ports:
        - containerPort: 8082
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: DB_HOST
          value: "payment-db-service"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: payment-db-secret
              key: password
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka-service:9092"

        # Resource limits
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"

        # Liveness probe (reinicia se falhar)
        livenessProbe:
          httpGet:
            path: /api/payments/health
            port: 8082
          initialDelaySeconds: 60
          periodSeconds: 10

        # Readiness probe (remove do load balancer se falhar)
        readinessProbe:
          httpGet:
            path: /api/payments/health
            port: 8082
          initialDelaySeconds: 30
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: payment-service
spec:
  selector:
    app: payment-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8082
  type: ClusterIP  # Interno (só outros serviços acessam)

---
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
        averageUtilization: 70  # Escala se CPU > 70%
```

---

## 9. Gerenciamento de Dados

### Database per Service

```
┌─────────────────────────────────────────────────────────────┐
│  REGRA: Cada serviço TEM SEU PRÓPRIO banco de dados         │
└─────────────────────────────────────────────────────────────┘

  ✅ User Service → users_db
  ✅ Product Service → products_db
  ✅ Order Service → orders_db
  ✅ Payment Service → payments_db

  ❌ Order Service NÃO pode fazer JOIN em users_db
  ❌ Payment Service NÃO pode acessar orders_db diretamente


COMO OBTER DADOS DE OUTROS SERVIÇOS?
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Opção 1: Chamada Síncrona (API REST)
  Order Service precisa de dados do User:
    → GET /api/users/{userId}
    → Retorna: {name: "John", email: "john@..."}

  ⚠️ Problema: Latência (chamada de rede)
  ⚠️ Problema: Acoplamento (Order depende de User estar UP)


Opção 2: Replicação de Dados (Event-Driven)
  User Service publica evento:
    → UserCreatedEvent {userId, name, email}

  Order Service ESCUTA e REPLICA:
    → Salva em tabela local: user_cache

  Order Service usa dados LOCAIS:
    → SELECT * FROM user_cache WHERE user_id = ?

  ✅ Sem latência (dados locais)
  ✅ Sem acoplamento (Order não depende de User)
  ⚠️ Consistência eventual (dados podem estar desatualizados)


Opção 3: CQRS (Read Model específico)
  Order Service tem Read Model desnormalizado:
    order_view {
      order_id,
      user_id,
      user_name,    ← DESNORMALIZADO
      user_email,   ← DESNORMALIZADO
      product_name, ← DESNORMALIZADO
      total_amount
    }

  Event Handler atualiza order_view quando:
    - OrderCreatedEvent → insere na order_view
    - UserUpdatedEvent → atualiza user_name na order_view
    - ProductUpdatedEvent → atualiza product_name na order_view

  Query:
    SELECT * FROM order_view WHERE order_id = ?
    ✅ UMA query (não JOIN)
    ✅ Rápido
```

### Saga Pattern (Transações Distribuídas)

```
PROBLEMA: Transação entre MÚLTIPLOS serviços
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Criar Order:
  1. Order Service → criar order
  2. Payment Service → processar pagamento
  3. Inventory Service → reservar estoque
  4. Shipping Service → criar envio

E se Payment FALHAR?
  → Order foi criado (Order Service)
  → Payment falhou (Payment Service)
  → INCONSISTÊNCIA!

Com banco único (monolito):
  @Transactional → ROLLBACK de TUDO

Com microserviços (bancos separados):
  ❌ NÃO tem transação distribuída (2PC é ruim)
  ✅ USA SAGA PATTERN


SAGA PATTERN: Choreography
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Cada serviço ESCUTA eventos e PUBLICA próximo passo:

  Order Service:
    1. Cria Order (status = PENDING)
    2. Publica: OrderCreatedEvent

  Payment Service (escuta OrderCreatedEvent):
    1. Processa pagamento
    2. SE SUCESSO: publica PaymentApprovedEvent
    3. SE FALHA: publica PaymentFailedEvent

  Inventory Service (escuta PaymentApprovedEvent):
    1. Reserva estoque
    2. SE SUCESSO: publica InventoryReservedEvent
    3. SE FALHA: publica InventoryFailedEvent

  Order Service (escuta InventoryReservedEvent):
    1. Atualiza order (status = CONFIRMED)

  Order Service (escuta PaymentFailedEvent OU InventoryFailedEvent):
    1. COMPENSA: cancela order (status = CANCELLED)


FLUXO FELIZ:
  OrderCreatedEvent → PaymentApprovedEvent → InventoryReservedEvent → Order CONFIRMED

FLUXO ERRO (Payment falha):
  OrderCreatedEvent → PaymentFailedEvent → Order CANCELLED

FLUXO ERRO (Inventory falha):
  OrderCreatedEvent → PaymentApprovedEvent → InventoryFailedEvent
    → RefundPaymentEvent (compensação)
    → Order CANCELLED


IMPLEMENTAÇÃO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// Order Service
@Service
public class CreateOrderSaga {

    @Transactional
    public Order createOrder(CreateOrderRequest request) {

        // 1. Criar order (PENDING)
        Order order = new Order(request);
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);

        // 2. Publicar evento (inicia saga)
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId(),
            order.getTotal()
        );
        eventPublisher.publish("order.created.v1", event);

        return order;
    }

    @KafkaListener(topics = "payment.approved.v1")
    public void handlePaymentApproved(PaymentApprovedEvent event) {

        // Payment OK → continua saga
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();
        order.setPaymentId(event.getPaymentId());
        orderRepository.save(order);

        // Próximo passo: reservar estoque
        // (Inventory Service escuta payment.approved.v1)
    }

    @KafkaListener(topics = "payment.failed.v1")
    public void handlePaymentFailed(PaymentFailedEvent event) {

        // Payment FALHOU → COMPENSAÇÃO (cancela order)
        Order order = orderRepository.findById(event.getOrderId()).orElseThrow();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason("Payment failed: " + event.getReason());
        orderRepository.save(order);
    }
}

// Payment Service
@Component
public class PaymentSaga {

    @KafkaListener(topics = "order.created.v1")
    public void handleOrderCreated(OrderCreatedEvent event) {

        try {
            // Processar pagamento
            Payment payment = processPayment(event);

            // SUCESSO → publica PaymentApprovedEvent
            PaymentApprovedEvent approved = new PaymentApprovedEvent(
                payment.getId(),
                event.getOrderId()
            );
            eventPublisher.publish("payment.approved.v1", approved);

        } catch (InsufficientFundsException ex) {

            // FALHA → publica PaymentFailedEvent
            PaymentFailedEvent failed = new PaymentFailedEvent(
                event.getOrderId(),
                "Insufficient funds"
            );
            eventPublisher.publish("payment.failed.v1", failed);
        }
    }
}
```

---

## 10. Observabilidade

### Três Pilares da Observabilidade

```
┌─────────────────────────────────────────────────────────────┐
│  1. LOGS (O que aconteceu?)                                 │
└─────────────────────────────────────────────────────────────┘

Logs estruturados (JSON):

{
  "timestamp": "2024-01-10T10:15:30Z",
  "level": "ERROR",
  "service": "payment-service",
  "trace_id": "abc-123",  ← IMPORTANTE: mesmo ID em todos serviços
  "message": "Payment failed: insufficient funds",
  "user_id": "user-456",
  "payment_id": "pay-789"
}

Agregação centralizada (ELK Stack):
  - Elasticsearch: armazena logs
  - Logstash: processa logs
  - Kibana: visualiza logs

Query no Kibana:
  trace_id:"abc-123"
  → Mostra TODOS logs desta requisição (todos serviços)


┌─────────────────────────────────────────────────────────────┐
│  2. MÉTRICAS (Como está performando?)                       │
└─────────────────────────────────────────────────────────────┘

Métricas expostas via Prometheus:

# HELP payment_requests_total Total de requisições
# TYPE payment_requests_total counter
payment_requests_total{method="POST",status="200"} 15423
payment_requests_total{method="POST",status="500"} 23

# HELP payment_request_duration_seconds Latência
# TYPE payment_request_duration_seconds histogram
payment_request_duration_seconds_bucket{le="0.1"} 12000
payment_request_duration_seconds_bucket{le="0.5"} 14000
payment_request_duration_seconds_bucket{le="1.0"} 15000

Grafana dashboards:
  - Latência p50, p95, p99
  - Taxa de erro (4xx, 5xx)
  - Throughput (req/s)
  - CPU, memória, threads


┌─────────────────────────────────────────────────────────────┐
│  3. TRACES (Como a requisição fluiu?)                       │
└─────────────────────────────────────────────────────────────┘

Distributed Tracing (Zipkin/Jaeger):

Trace ID: abc-123
  │
  ├─ Span: API Gateway (20ms)
  │
  ├─ Span: Order Service (150ms)
  │   │
  │   ├─ Span: User Service (50ms)
  │   │
  │   └─ Span: Payment Service (800ms)  ← GARGALO
  │       │
  │       └─ Span: Payment DB (750ms)    ← Causa raiz
  │
  Total: 1020ms

✅ Identifica gargalos
✅ Vê fluxo completo
✅ Correlaciona com logs (mesmo trace_id)
```

### Implementação

```java
// Logs estruturados (Logback + Logstash encoder)

// logback-spring.xml
<configuration>
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>logstash-server:5000</destination>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>trace_id</includeMdcKeyName>
            <includeMdcKeyName>user_id</includeMdcKeyName>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="LOGSTASH" />
    </root>
</configuration>

// Código
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public Payment createPayment(CreatePaymentRequest request) {

        // Adicionar contexto aos logs (MDC)
        MDC.put("user_id", request.getUserId().toString());
        MDC.put("payment_id", payment.getId().toString());

        log.info("Creating payment for user {}", request.getUserId());
        // → Aparece no Kibana com trace_id, user_id, payment_id

        // ...
    }
}


// Métricas (Micrometer + Prometheus)

@Service
public class PaymentService {

    private final MeterRegistry meterRegistry;
    private final Counter paymentCounter;

    public PaymentService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Criar contador
        this.paymentCounter = Counter.builder("payment_requests_total")
            .tag("service", "payment-service")
            .description("Total payment requests")
            .register(meterRegistry);
    }

    @Timed(value = "payment_request_duration", description = "Payment request duration")
    public Payment createPayment(CreatePaymentRequest request) {

        // Incrementar contador
        paymentCounter.increment();

        // @Timed registra latência automaticamente

        // ...
    }
}

// Expor métricas em /actuator/prometheus
management:
  endpoints:
    web:
      exposure:
        include: prometheus


// Traces (Spring Cloud Sleuth + Zipkin)

// Configuração automática (Spring Boot)
spring:
  sleuth:
    sampler:
      probability: 0.1  # 10% das requisições (produção)
  zipkin:
    base-url: http://zipkin-server:9411

// Sleuth adiciona trace_id automaticamente:
// - Logs
// - HTTP headers (X-B3-TraceId)
// - Kafka headers
```

---

## 11. Segurança

### Autenticação e Autorização

```
┌─────────────────────────────────────────────────────────────┐
│              OAUTH 2.0 + JWT                                │
└─────────────────────────────────────────────────────────────┘

  1. Cliente → API Gateway → POST /oauth/token
     {username, password}

  2. API Gateway → Auth Server (Keycloak, Auth0)
     → Valida credenciais

  3. Auth Server → API Gateway → JWT Token
     {
       "sub": "user-123",
       "roles": ["USER", "ADMIN"],
       "exp": 1704880000
     }

  4. Cliente usa JWT em TODAS requisições:
     Authorization: Bearer eyJhbGciOiJIUzI1NiIs...

  5. API Gateway valida JWT:
     ✅ Assinatura válida?
     ✅ Não expirou?
     ✅ Tem role necessário?

  6. API Gateway → Service (passa user_id)
     X-User-Id: user-123

  7. Service NÃO precisa validar token (confia no Gateway)


IMPLEMENTAÇÃO (Spring Security + OAuth2):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// API Gateway (valida token)
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http) {

        return http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwkSetUri("http://auth-server/.well-known/jwks.json"))
            )
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/public/**").permitAll()
                .pathMatchers("/api/admin/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .build();
    }
}

// Payment Service (confia no Gateway)
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {

        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/payments/**").authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt())
            .build();
    }
}

@RestController
public class PaymentController {

    @PostMapping("/api/payments")
    public Payment createPayment(
            @AuthenticationPrincipal Jwt jwt,  // ← JWT do Gateway
            @RequestBody CreatePaymentRequest request) {

        String userId = jwt.getSubject();  // "user-123"
        List<String> roles = jwt.getClaimAsStringList("roles");

        // ...
    }
}
```

### Service-to-Service Authentication

```
SERVIÇOS INTERNOS também precisam autenticação

┌─────────────────────────────────────────────────────────────┐
│  OPÇÃO 1: mTLS (Mutual TLS)                                 │
└─────────────────────────────────────────────────────────────┘

Order Service → Payment Service:
  1. Order Service apresenta CERTIFICADO
  2. Payment Service valida certificado
  3. Se válido: aceita requisição

✅ Segurança alta (criptografia)
✅ Não precisa tokens
⚠️ Complexidade (gestão de certificados)


┌─────────────────────────────────────────────────────────────┐
│  OPÇÃO 2: Service Tokens (JWT)                              │
└─────────────────────────────────────────────────────────────┘

Order Service → Payment Service:
  1. Order Service pega token (Auth Server)
     client_credentials grant
  2. Passa token na requisição
  3. Payment Service valida token

✅ Simples
⚠️ Overhead (validação de token)


┌─────────────────────────────────────────────────────────────┐
│  OPÇÃO 3: Service Mesh (Istio)                              │
└─────────────────────────────────────────────────────────────┘

Istio gerencia autenticação AUTOMATICAMENTE:
  - mTLS transparente (services não sabem)
  - Políticas de acesso (Order pode chamar Payment)

✅ Transparente (services não mudam)
✅ Seguro (mTLS automático)
⚠️ Complexidade (deploy Istio)
```

---

## 12. Testes em Microserviços

### Pirâmide de Testes

```
              ▲
             ╱ ╲
            ╱   ╲
           ╱  E2E ╲         ← POUCOS (lento, frágil)
          ╱───────╲
         ╱         ╲
        ╱ INTEGRAÇÃO ╲      ← MÉDIO (testa integração)
       ╱─────────────╲
      ╱               ╲
     ╱  TESTES UNITÁRIOS ╲   ← MUITOS (rápido, confiável)
    ╱───────────────────╲
   ╱                     ╲
```

### 1. Testes Unitários

```java
// Testa lógica de negócio ISOLADA

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderServiceClient orderServiceClient;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void shouldCreatePayment() {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest(
            UUID.randomUUID(),
            new BigDecimal("100.00")
        );

        Order order = new Order(request.getOrderId(), new BigDecimal("100.00"));
        when(orderServiceClient.getOrder(any())).thenReturn(order);

        when(paymentRepository.save(any())).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        // When
        Payment payment = paymentService.createPayment(request);

        // Then
        assertThat(payment).isNotNull();
        assertThat(payment.getAmount()).isEqualTo(new BigDecimal("100.00"));

        verify(paymentRepository, times(1)).save(any());
    }
}
```

### 2. Testes de Integração

```java
// Testa integração com banco real (Testcontainers)

@SpringBootTest
@Testcontainers
class PaymentServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("payment_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void shouldSavePaymentToDatabase() {
        // Given
        CreatePaymentRequest request = new CreatePaymentRequest(
            UUID.randomUUID(),
            new BigDecimal("100.00")
        );

        // When
        Payment payment = paymentService.createPayment(request);

        // Then
        Optional<Payment> saved = paymentRepository.findById(payment.getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getAmount()).isEqualTo(new BigDecimal("100.00"));
    }
}
```

### 3. Contract Tests (Pact)

```java
// Garante que Order Service e Payment Service têm contrato compatível

// Payment Service (Provider)
@SpringBootTest
@Provider("payment-service")
@PactBroker(url = "http://pact-broker:9292")
class PaymentServiceContractTest {

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPacts(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("payment exists")
    void paymentExists() {
        // Setup: criar payment no banco
        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("100.00"));
        paymentRepository.save(payment);
    }
}

// Order Service (Consumer)
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "payment-service")
class OrderServiceContractTest {

    @Pact(consumer = "order-service")
    RequestResponsePact createPaymentPact(PactDslWithProvider builder) {
        return builder
            .given("payment exists")
            .uponReceiving("a request to get payment")
            .path("/api/payments/123")
            .method("GET")
            .willRespondWith()
            .status(200)
            .body(newJsonBody(body -> {
                body.uuid("id", UUID.fromString("123"));
                body.decimalType("amount", 100.00);
            }).build())
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "createPaymentPact")
    void testGetPayment(MockServer mockServer) {
        // Order Service chama Payment Service (mock)
        PaymentServiceClient client = new PaymentServiceClient(mockServer.getUrl());
        Payment payment = client.getPayment(UUID.fromString("123"));

        assertThat(payment.getAmount()).isEqualTo(new BigDecimal("100.00"));
    }
}
```

### 4. End-to-End Tests

```java
// Testa fluxo COMPLETO (todos serviços)

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
class CreateOrderE2ETest {

    @Container
    static DockerComposeContainer<?> environment = new DockerComposeContainer<>(
        new File("docker-compose-test.yml")
    )
        .withExposedService("api-gateway", 8080)
        .withExposedService("user-service", 8081)
        .withExposedService("payment-service", 8082)
        .withExposedService("order-service", 8083);

    @LocalServerPort
    private int port;

    @Test
    void shouldCreateOrderEndToEnd() {

        // 1. Criar User
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "name": "John Doe",
                    "email": "john@example.com"
                }
                """)
        .when()
            .post("http://localhost:" + port + "/api/users")
        .then()
            .statusCode(201);

        // 2. Criar Order
        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                    "userId": "user-123",
                    "productId": "product-456",
                    "quantity": 2,
                    "totalAmount": 100.00
                }
                """)
        .when()
            .post("http://localhost:" + port + "/api/orders")
        .then()
            .statusCode(201)
            .body("status", equalTo("PENDING"));

        // 3. Aguardar processamento assíncrono (Payment)
        await().atMost(10, SECONDS).untilAsserted(() -> {

            // Verificar que Payment foi criado
            given()
                .contentType(ContentType.JSON)
            .when()
                .get("http://localhost:" + port + "/api/payments?orderId=order-789")
            .then()
                .statusCode(200)
                .body("status", equalTo("APPROVED"));
        });

        // 4. Verificar que Order foi atualizado
        given()
        .when()
            .get("http://localhost:" + port + "/api/orders/order-789")
        .then()
            .statusCode(200)
            .body("status", equalTo("CONFIRMED"));
    }
}
```

---

## 13. Desafios e Armadilhas

### Armadilha 1: Microserviços Demais (Nano-services)

```
❌ ERRADO - Serviço para CADA entidade

  - UserService
  - UserAddressService      ← MUITO granular!
  - UserPhoneService         ← MUITO granular!
  - UserPreferencesService   ← MUITO granular!

PROBLEMAS:
  ❌ Overhead de rede (chamadas entre nano-services)
  ❌ Complexidade operacional (deploy, monitor N serviços)
  ❌ Transações complexas (saga entre 4 serviços para criar user)


✅ CORRETO - Serviço por BOUNDED CONTEXT

  - UserService (user + address + phone + preferences)
    → Tudo relacionado a User em UM serviço

REGRA DE OURO:
  "Comece com serviços MAIORES e divida QUANDO necessário"
```

### Armadilha 2: Banco Compartilhado

```
❌ ERRADO - Múltiplos serviços acessam MESMO banco

  ┌──────────┐        ┌──────────┐
  │  Order   │        │ Payment  │
  │ Service  │        │ Service  │
  └─────┬────┘        └─────┬────┘
        │                   │
        └────────┬──────────┘
                 ▼
          ┌────────────┐
          │  orders_db │  ← SHARED!
          │            │
          │ - orders   │
          │ - payments │
          └────────────┘

PROBLEMAS:
  ❌ Acoplamento (mudança no schema afeta AMBOS)
  ❌ Não pode migrar banco independentemente
  ❌ Violação do Database per Service


✅ CORRETO - Banco por serviço

  ┌──────────┐        ┌──────────┐
  │  Order   │        │ Payment  │
  │ Service  │        │ Service  │
  └─────┬────┘        └─────┬────┘
        │                   │
        ▼                   ▼
  ┌────────────┐    ┌────────────┐
  │  orders_db │    │ payments_db│
  └────────────┘    └────────────┘
```

### Armadilha 3: Chamadas Síncronas em Cascata

```
❌ ERRADO - Chamadas síncronas em cascata

  Cliente → API Gateway
              ↓
          Order Service (100ms)
              ↓ GET /users/{id}
          User Service (150ms)
              ↓ GET /products/{id}
          Product Service (200ms)
              ↓ GET /inventory/{id}
          Inventory Service (150ms)

  Total: 600ms (latência SOMADA)
  ❌ Lento
  ❌ Acoplamento temporal
  ❌ Falha em cascata


✅ CORRETO - Assíncrono ou CQRS

Opção 1: Assíncrono
  Cliente → Order Service (cria order PENDING)
            ↓ publica OrderCreatedEvent
          KAFKA
            ↓
          Múltiplos serviços processam PARALELO

Opção 2: CQRS (dados desnormalizados)
  Order Service tem Read Model com TUDO:
    order_view {
      order_id,
      user_name,      ← DESNORMALIZADO
      product_name,   ← DESNORMALIZADO
      inventory_qty   ← DESNORMALIZADO
    }

  Query: SELECT * FROM order_view WHERE order_id = ?
  ✅ UMA query (não chamadas em cascata)
  ✅ Rápido (50ms)
```

### Armadilha 4: Não Ter Monitoring/Observability

```
❌ ERRADO - Deploy microserviços SEM monitoring

PROBLEMAS:
  ❌ Bug: "Sistema lento"
     → Qual serviço?
     → Não sei (não tem métricas)

  ❌ Erro: "Requisição falhou"
     → Onde falhou?
     → Não sei (logs distribuídos, sem trace_id)

  ❌ Deploy novo: "Sistema quebrou"
     → Qual versão? Qual serviço?
     → Não sei (não tem rollback automático)


✅ CORRETO - Monitoring ANTES de microserviços

OBRIGATÓRIO:
  ✅ Distributed Tracing (Zipkin/Jaeger)
  ✅ Logs centralizados (ELK)
  ✅ Métricas (Prometheus + Grafana)
  ✅ Alertas (PagerDuty, Slack)
  ✅ Health checks (Kubernetes liveness/readiness)

REGRA:
  "Não vá para produção com microserviços sem observability completa"
```

---

## 14. Checklist de Microserviços

### ☐ ANTES DE COMEÇAR

- [ ] Entendeu quando USAR microserviços?
- [ ] Entendeu quando NÃO usar?
- [ ] Time tem maturidade DevOps?
- [ ] Tem CI/CD configurado?
- [ ] Tem monitoring/observability?

### ☐ ARQUITETURA

- [ ] Definiu bounded contexts (DDD)?
- [ ] Cada serviço tem responsabilidade clara?
- [ ] Serviços são independentes (deploy/escala)?
- [ ] Database per service?
- [ ] Comunicação assíncrona (eventos)?

### ☐ PADRÕES

- [ ] API Gateway configurado?
- [ ] Service Discovery (Eureka/Consul)?
- [ ] Circuit Breaker (Resilience4j)?
- [ ] Distributed Tracing (Zipkin)?
- [ ] Saga Pattern (transações distribuídas)?

### ☐ INFRAESTRUTURA

- [ ] Docker configurado?
- [ ] Kubernetes/Docker Compose?
- [ ] CI/CD pipelines (Jenkins/GitHub Actions)?
- [ ] Logs centralizados (ELK)?
- [ ] Métricas (Prometheus + Grafana)?

### ☐ SEGURANÇA

- [ ] Autenticação (OAuth2 + JWT)?
- [ ] Service-to-service auth (mTLS)?
- [ ] Secrets management (Vault)?
- [ ] Rate limiting?

### ☐ DADOS

- [ ] Database per service?
- [ ] Saga Pattern implementado?
- [ ] Event Sourcing (se aplicável)?
- [ ] CQRS (Read Models)?

### ☐ TESTES

- [ ] Testes unitários (>80% coverage)?
- [ ] Testes de integração (Testcontainers)?
- [ ] Contract tests (Pact)?
- [ ] E2E tests (críticos)?

### ☐ PRODUÇÃO

- [ ] Health checks configurados?
- [ ] Auto-scaling (HPA)?
- [ ] Backup e recovery?
- [ ] Disaster recovery plan?
- [ ] Runbooks (incidentes comuns)?

---

## 15. Exercícios Práticos

### Exercício 1: Identificar Violações

Analise a arquitetura:

```
CENÁRIO:
  - Order Service acessa DIRETAMENTE users_db (banco do User Service)
  - Payment Service chama Order Service (síncrono) antes de salvar payment
  - Sem Circuit Breaker
  - Logs em arquivos locais (cada serviço)
```

<details>
<summary><strong>📝 Resposta</strong></summary>

**Violações:**

1. ❌ **Violação Database per Service**
   - Order Service acessa users_db diretamente
   - Deveria chamar User Service API ou ter dados replicados

2. ❌ **Acoplamento Síncrono**
   - Payment depende de Order (síncrono)
   - Deveria ser assíncrono (evento)

3. ❌ **Sem Circuit Breaker**
   - Payment cai se Order cair
   - Deveria ter Circuit Breaker

4. ❌ **Logs Distribuídos**
   - Logs locais = impossível correlacionar
   - Deveria ter logs centralizados (ELK) + trace_id

**Solução:**
```java
// ✅ Order Service chama User Service
@Service
public class OrderService {

    private final UserServiceClient userClient;  // Feign

    @CircuitBreaker(name = "user-service", fallbackMethod = "fallback")
    public Order createOrder(CreateOrderRequest request) {

        User user = userClient.getUser(request.getUserId());

        // ...
    }
}

// ✅ Payment Service assíncrono
@Component
public class PaymentEventHandler {

    @KafkaListener(topics = "order.created.v1")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // Processa pagamento assincronamente
    }
}

// ✅ Logs com trace_id (Sleuth)
spring:
  sleuth:
    enabled: true
  zipkin:
    base-url: http://zipkin:9411
```

</details>

---

## 🎯 Conclusão

**Microserviços** revolucionam como você constrói sistemas distribuídos!

**O que você aprendeu:**
✅ Quando usar (e quando NÃO usar)
✅ Características fundamentais
✅ Comunicação (síncrona vs assíncrona)
✅ Padrões essenciais (Gateway, Discovery, Circuit Breaker)
✅ Implementação completa (Spring Boot + Docker + Kubernetes)
✅ Dados distribuídos (Saga, CQRS)
✅ Observabilidade (Logs, Métricas, Traces)
✅ Segurança (OAuth2, mTLS)
✅ Testes (Unit, Integration, Contract, E2E)

**Lembre-se:**

- **Microserviços NÃO são bala de prata**
- **Complexidade > Benefício = NÃO use**
- **Database per Service** (sempre!)
- **Comunicação Assíncrona** (eventos)
- **Observabilidade** (obrigatória!)
- **DevOps Maturity** (pré-requisito)

**Regra de Ouro:**
```
Comece com MONOLITO MODULAR.
Migre para microserviços QUANDO:
  - Time > 20 devs
  - Deploy frequente
  - Escala diferenciada
  - Maturidade DevOps
```

---

**Próximos Passos:**
1. Leia `tutorial-migracao-monolito-microservicos.md`
2. Implemente ambiente local (Docker Compose)
3. Configure observability (Zipkin + ELK + Prometheus)
4. Pratique com projeto real

**Boa sorte na sua jornada com Microserviços! 🚀**
