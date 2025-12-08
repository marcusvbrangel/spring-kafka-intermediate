# Plano de Segregação - MS-PRODUCER

## Objetivo
Remover todos os componentes relacionados a **CONSUMER** do projeto ms-producer, mantendo apenas o que é necessário para **PRODUZIR** eventos Kafka.

---

## 1. Análise do Estado Atual

### Arquivos que DEVEM PERMANECER (Producer)
- ✅ `PaymentController.java` - REST endpoints que iniciam o fluxo
- ✅ `PaymentService.java` - Lógica de negócio
- ✅ `PaymentApprovedProducer.java` - Produz eventos para Kafka
- ✅ `PaymentApprovedEvent.java` - Schema do evento
- ✅ `KafkaProducerConfig.java` - Configurações de producers
- ✅ `PaymentApprovedRequest.java` - DTO de entrada
- ✅ `PaymentRequestMapper.java` - Mapeia Request → Domain
- ✅ `PaymentEventMapper.java` - Mapeia Domain → Event
- ✅ `Payment.java` - Entidade de domínio
- ✅ `PaymentStatus.java` - Enum de status
- ✅ `PaymentRepository.java` - Repository JPA (producer precisa persistir)

### Arquivos que DEVEM SER REMOVIDOS (Consumer)
- ❌ `PaymentApprovedConsumer.java` - Consome eventos Kafka
- ❌ `DLQReprocessor.java` - Reprocessa DLQ (consumer)
- ❌ `KafkaConsumerConfig.java` - Configurações de consumers
- ❌ `ProcessedEvent.java` - Entidade para idempotência (consumer)
- ❌ `ProcessedEventRepository.java` - Repository de eventos processados (consumer)
- ❌ Migração `V1__create_initial_tables.sql` - Remover tabela `processed_events`

---

## 2. Estrutura Final Esperada

```
ms-producer/
├── src/main/java/com/mvbr/store/
│   ├── StoreApplication.java
│   ├── application/
│   │   ├── controller/
│   │   │   └── PaymentController.java ✅
│   │   ├── dto/request/
│   │   │   └── PaymentApprovedRequest.java ✅
│   │   ├── mapper/
│   │   │   ├── PaymentRequestMapper.java ✅
│   │   │   └── PaymentEventMapper.java ✅
│   │   └── service/
│   │       └── PaymentService.java ✅
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Payment.java ✅
│   │   │   └── PaymentStatus.java ✅
│   │   └── repository/ [REMOVER]
│   │       ├── ProcessedEventRepository.java ❌
│   ├── infrastructure/
│   │   ├── config/kafka/
│   │   │   ├── KafkaProducerConfig.java ✅
│   │   │   └── KafkaConsumerConfig.java ❌
│   │   ├── messaging/
│   │   │   ├── producer/
│   │   │   │   └── PaymentApprovedProducer.java ✅
│   │   │   ├── consumer/ [REMOVER PASTA INTEIRA]
│   │   │   │   ├── PaymentApprovedConsumer.java ❌
│   │   │   │   └── DLQReprocessor.java ❌
│   │   │   └── event/
│   │   │       └── PaymentApprovedEvent.java ✅
│   │   └── persistence/
│   │       └── PaymentRepository.java ✅
│   └── domain/model/
│       └── ProcessedEvent.java ❌
├── src/main/resources/
│   ├── application.yaml [LIMPAR configs de consumer]
│   └── db/migration/
│       └── V1__create_initial_tables.sql [ATUALIZAR - remover processed_events]
└── docs/ [LIMPAR arquivos relacionados a DLQ/Consumer]
    ├── CLAUDE.md [ATUALIZAR]
    └── DLQ-TESTING-GUIDE.md ❌
```

---

## 3. Passos de Execução

### FASE 1: Remover Classes Java (Consumer)
1. ❌ Deletar `src/main/java/com/mvbr/store/infrastructure/messaging/consumer/PaymentApprovedConsumer.java`
2. ❌ Deletar `src/main/java/com/mvbr/store/infrastructure/messaging/consumer/DLQReprocessor.java`
3. ❌ Deletar pasta `src/main/java/com/mvbr/store/infrastructure/messaging/consumer/`
4. ❌ Deletar `src/main/java/com/mvbr/store/infrastructure/config/kafka/KafkaConsumerConfig.java`
5. ❌ Deletar `src/main/java/com/mvbr/store/domain/model/ProcessedEvent.java`
6. ❌ Deletar `src/main/java/com/mvbr/store/domain/repository/ProcessedEventRepository.java`
7. ❌ Deletar pasta `src/main/java/com/mvbr/store/domain/repository/` (se ficar vazia)

### FASE 2: Limpar Configurações (application.yaml)
9. 🔧 Remover toda seção `spring.kafka.consumer` do `application.yaml`
10. 🔧 Remover seção `dlq.reprocessor` do `application.yaml`
11. 🔧 Remover `spring.kafka.topics.payment-approved-dlq` (DLQ não é usada por producer)
12. 🔧 Manter apenas configurações de `spring.kafka.producer` e `spring.kafka.topics` (sem DLQ)

### FASE 3: Ajustar Migração SQL
13. 🔧 Editar `src/main/resources/db/migration/V1__create_initial_tables.sql`
    - Remover `CREATE TABLE processed_events`
    - Manter apenas `CREATE TABLE payments`

### FASE 4: Limpar Dependências (pom.xml)
14. 🔍 Verificar se há dependências exclusivas de consumer (provável que não)
15. 🔧 Manter todas as dependências (JPA, Kafka, Postgres) pois producer também persiste dados

### FASE 5: Atualizar Documentação
16. ❌ Deletar `DLQ-TESTING-GUIDE.md` (relacionado a consumer)
17. 🔧 Atualizar `CLAUDE.md`:
    - Remover seções sobre Consumer Configuration
    - Remover seções sobre DLQ
    - Remover seções sobre `ProcessedEvent`
    - Focar apenas em Producer patterns
18. 🔧 Limpar pasta `docs/` se houver arquivos relacionados a consumer

### FASE 6: Validação Final
19. ✅ Executar `./mvnw clean compile` - Verificar compilação
20. ✅ Executar `./mvnw test` - Verificar testes
21. ✅ Revisar estrutura de pastas - Confirmar que não sobrou nada de consumer

---

## 4. Impactos e Considerações

### ⚠️ Atenção
- O ms-producer **não vai mais consumir eventos** - apenas produzir
- Tabela `processed_events` não será criada (consumer que vai usar)
- Configurações de DLQ e retry são de consumer - podem ser removidas
- **PaymentRepository permanece** - producer precisa salvar payments no banco

### ✅ O que o ms-producer fará após segregação
1. Receber requisições REST em `/api/payments/approved`
2. Validar e persistir `Payment` no banco de dados (PostgreSQL)
3. Produzir eventos Kafka (`PaymentApprovedEvent`, `PaymentNotificationEvent`)
4. Responder ao cliente HTTP com sucesso/erro

### ❌ O que o ms-producer NÃO fará mais
1. Consumir eventos Kafka
2. Processar DLQ
3. Armazenar eventos processados (idempotência)
4. Retry de mensagens falhas

---

## 5. Arquivos de Configuração Ajustados

### application.yaml (Seções a manter)
```yaml
server:
  port: 5050

spring:
  application:
    name: ms-producer
  datasource:
    url: jdbc:postgresql://localhost:5432/msstoreproducer
    username: postgres
    password: postgres
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: none

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      critical: {...}
      default: {...}
      fast: {...}
    topics:
      payment-approved: payment.approved.v1
      # Remover payment-approved-dlq
```

### V1__create_initial_tables.sql (Versão Producer)
```sql
-- Apenas a tabela payments
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- processed_events removida (consumer que vai usar)
```

---

## 6. Checklist Final

- [ ] Todos os arquivos de consumer deletados
- [ ] Pasta `consumer/` removida
- [ ] `KafkaConsumerConfig.java` removido
- [ ] `ProcessedEvent.java` e `ProcessedEventRepository.java` removidos
- [ ] `application.yaml` limpo (sem configs de consumer/DLQ)
- [ ] SQL migration ajustado (sem `processed_events`)
- [ ] `CLAUDE.md` atualizado
- [ ] `DLQ-TESTING-GUIDE.md` removido
- [ ] Compilação OK (`./mvnw clean compile`)
- [ ] Testes OK (`./mvnw test`)

---

## Conclusão

Após a execução deste plano, o **ms-producer** será um microserviço focado exclusivamente em:
- Receber requisições HTTP
- Validar e persistir dados
- Produzir eventos Kafka

Toda a lógica de consumo, DLQ e idempotência ficará no **ms-consumer**.