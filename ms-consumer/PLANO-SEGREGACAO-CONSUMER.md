# Plano de Segregação - MS-CONSUMER

## Objetivo
Remover todos os componentes relacionados a **PRODUCER** do projeto ms-consumer, mantendo apenas o que é necessário para **CONSUMIR** eventos Kafka.

---

## 1. Análise do Estado Atual

### Arquivos que DEVEM PERMANECER (Consumer)
- ✅ `PaymentApprovedConsumer.java` - Consome eventos do Kafka
- ✅ `DLQReprocessor.java` - Reprocessa mensagens da DLQ
- ✅ `KafkaConsumerConfig.java` - Configurações de consumers
- ✅ `ProcessedEvent.java` - Entidade para rastreamento de idempotência
- ✅ `ProcessedEventRepository.java` - Repository para eventos processados
- ✅ `PaymentApprovedEvent.java` - Schema do evento (consumer precisa)
- ✅ `Payment.java` - Entidade de domínio (consumer pode processar)
- ✅ `PaymentStatus.java` - Enum de status
- ✅ `PaymentRepository.java` - Repository JPA (consumer pode persistir)
- ✅ `PaymentService.java` - Lógica de negócio (usado pelos consumers)

### Arquivos que DEVEM SER REMOVIDOS (Producer)
- ❌ `PaymentController.java` - REST endpoints (producer)
- ❌ `PaymentApprovedProducer.java` - Produz eventos Kafka
- ❌ `KafkaProducerConfig.java` - Configurações de producers
- ❌ `PaymentApprovedRequest.java` - DTO de entrada HTTP (producer)
- ❌ `PaymentRequestMapper.java` - Mapeia Request → Domain (producer)
- ❌ `PaymentEventMapper.java` - Mapeia Domain → Event (producer usa)

### ⚠️ Dependências do pom.xml
- ❓ `spring-boot-starter-web` - Pode ser removido (consumer não precisa de HTTP server)
- ✅ `spring-boot-starter-data-jpa` - MANTER (consumer persiste dados)
- ✅ `spring-kafka` - MANTER (consumer precisa)
- ✅ `flyway-core` - MANTER (migrations)
- ✅ `postgresql` - MANTER (banco de dados)

---

## 2. Estrutura Final Esperada

```
ms-consumer/
├── src/main/java/com/mvbr/store/
│   ├── StoreApplication.java
│   ├── application/
│   │   ├── controller/ [REMOVER PASTA INTEIRA]
│   │   │   └── PaymentController.java ❌
│   │   ├── dto/request/ [REMOVER PASTA INTEIRA]
│   │   │   └── PaymentApprovedRequest.java ❌
│   │   ├── mapper/ [REMOVER PASTA INTEIRA]
│   │   │   ├── PaymentRequestMapper.java ❌
│   │   │   └── PaymentEventMapper.java ❌
│   │   └── service/
│   │       └── PaymentService.java ✅
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Payment.java ✅
│   │   │   ├── PaymentStatus.java ✅
│   │   │   └── ProcessedEvent.java ✅
│   │   └── repository/
│   │       └── ProcessedEventRepository.java ✅
│   └── infrastructure/
│       ├── config/kafka/
│       │   ├── KafkaConsumerConfig.java ✅
│       │   └── KafkaProducerConfig.java ❌
│       ├── messaging/
│       │   ├── consumer/
│       │   │   ├── PaymentApprovedConsumer.java ✅
│       │   │   └── DLQReprocessor.java ✅
│       │   ├── producer/ [REMOVER PASTA INTEIRA]
│       │   │   └── PaymentApprovedProducer.java ❌
│       │   └── event/
│       │       └── PaymentApprovedEvent.java ✅
│       └── persistence/
│           └── PaymentRepository.java ✅
├── src/main/resources/
│   ├── application.yaml [LIMPAR configs de producer]
│   └── db/migration/
│       └── V1__create_initial_tables.sql ✅ (MANTER processed_events)
```

---

## 3. Passos de Execução

### FASE 1: Remover Classes Java (Producer)
1. ❌ Deletar `src/main/java/com/mvbr/store/application/controller/PaymentController.java`
2. ❌ Deletar pasta `src/main/java/com/mvbr/store/application/controller/`
3. ❌ Deletar `src/main/java/com/mvbr/store/application/dto/request/PaymentApprovedRequest.java`
4. ❌ Deletar pasta `src/main/java/com/mvbr/store/application/dto/request/`
5. ❌ Deletar pasta `src/main/java/com/mvbr/store/application/dto/` (se ficar vazia)
6. ❌ Deletar `src/main/java/com/mvbr/store/application/mapper/PaymentRequestMapper.java`
7. ❌ Deletar `src/main/java/com/mvbr/store/application/mapper/PaymentEventMapper.java`
8. ❌ Deletar pasta `src/main/java/com/mvbr/store/application/mapper/`
9. ❌ Deletar `src/main/java/com/mvbr/store/infrastructure/messaging/producer/PaymentApprovedProducer.java`
10. ❌ Deletar pasta `src/main/java/com/mvbr/store/infrastructure/messaging/producer/`
11. ❌ Deletar `src/main/java/com/mvbr/store/infrastructure/config/kafka/KafkaProducerConfig.java`

### FASE 2: Limpar Configurações (application.yaml)
14. 🔧 Alterar `spring.application.name` de `ms-producer` para `ms-consumer`
15. 🔧 Alterar `server.port` de `5050` para `6060` (diferente do producer)
16. 🔧 Alterar `spring.datasource.url` para `msstoreconsumer`
17. 🔧 Remover toda seção `spring.kafka.producer`
18. 🔧 Manter apenas configurações de `spring.kafka.consumer` e `dlq.reprocessor`

### FASE 3: Ajustar Migração SQL
19. ✅ Manter `V1__create_initial_tables.sql` como está (consumer precisa de processed_events)

### FASE 4: Atualizar Dependências (pom.xml)
20. 🔧 **OPCIONAL:** Remover `spring-boot-starter-web` (consumer não precisa de HTTP)
21. 🔧 **Se remover web:** Ajustar `StoreApplication.java` para não iniciar servidor web

### FASE 5: Atualizar PaymentService
22. 🔧 Revisar `PaymentService.java` - remover métodos que chamam producers (se houver)

### FASE 6: Criar/Atualizar Documentação
23. 🔧 Criar/Atualizar `CLAUDE.md` focado em consumer
24. ✅ Manter `DLQ-TESTING-GUIDE.md` (consumer usa DLQ)

### FASE 7: Validação Final
25. ✅ Executar `./mvnw clean compile` - Verificar compilação
26. ✅ Executar `./mvnw test` - Verificar testes
27. ✅ Revisar estrutura de pastas - Confirmar que não sobrou nada de producer

---

## 4. Impactos e Considerações

### ⚠️ Atenção
- O ms-consumer **não vai mais produzir eventos** - apenas consumir
- O ms-consumer **não terá endpoints HTTP** (a menos que mantenha spring-boot-starter-web)
- Tabela `processed_events` **DEVE PERMANECER** - essencial para idempotência
- Configurações de DLQ e retry **DEVEM PERMANECER** - consumidor precisa

### ✅ O que o ms-consumer fará após segregação
1. Consumir eventos Kafka dos tópicos configurados
2. Processar eventos com idempotência (rastrear em `processed_events`)
3. Persistir dados processados no banco PostgreSQL
4. Gerenciar DLQ (Dead Letter Queue) para mensagens com falha
5. Reprocessar mensagens da DLQ quando configurado

### ❌ O que o ms-consumer NÃO fará mais
1. Receber requisições HTTP REST
2. Produzir eventos Kafka
3. Expor endpoints HTTP

---

## 5. Arquivos de Configuração Ajustados

### application.yaml (Seções a manter)
```yaml
server:
  port: 6060  # Diferente do producer

spring:
  application:
    name: ms-consumer  # ALTERADO

  datasource:
    url: jdbc:postgresql://localhost:5432/msstoreconsumer  # ALTERADO
    username: postgres
    password: postgres

  jpa:
    show-sql: true
    hibernate:
      ddl-auto: none

  kafka:
    bootstrap-servers: localhost:9092
    # REMOVER toda seção producer

    consumer:
      auto-offset-reset: latest
      critical: {...}
      default: {...}
      fast: {...}

    error:
      retry: {...}

    topics:
      payment-approved: payment.approved.v1
      payment-approved-dlq: payment.approved.v1.dlq  # MANTER DLQ

dlq:
  reprocessor:
    enabled: false
```

### V1__create_initial_tables.sql (Versão Consumer)
```sql
-- Tabela payment (consumer pode processar/atualizar pagamentos)
CREATE TABLE payment (...);

-- Tabela processed_events (ESSENCIAL para consumer)
CREATE TABLE processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    event_type VARCHAR(100),
    processed_at TIMESTAMP NOT NULL,
    kafka_partition INTEGER,
    kafka_offset BIGINT
);
```

### pom.xml - Opção 1 (SEM servidor web)
```xml
<!-- REMOVER -->
<!-- <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency> -->

<!-- MANTER -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### pom.xml - Opção 2 (COM servidor web para health checks)
```xml
<!-- MANTER se quiser expor endpoints de health/metrics -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

## 6. Checklist Final

- [ ] Todos os arquivos de producer deletados
- [ ] Pasta `controller/` removida
- [ ] Pasta `dto/request/` removida
- [ ] Pasta `mapper/` removida (PaymentRequestMapper e PaymentEventMapper)
- [ ] Pasta `producer/` removida
- [ ] `KafkaProducerConfig.java` removido
- [ ] `application.yaml` ajustado (nome, porta, database, sem configs de producer)
- [ ] SQL migration mantido (com `processed_events`)
- [ ] `PaymentService.java` ajustado (sem chamadas a producers)
- [ ] Decidir sobre manter ou remover `spring-boot-starter-web`
- [ ] Compilação OK (`./mvnw clean compile`)
- [ ] Testes OK (`./mvnw test`)

---

## 7. Decisão: Manter ou Remover spring-boot-starter-web?

### Opção A: REMOVER (Consumer puro)
**Vantagens:**
- Menor footprint de memória
- Não expõe portas HTTP desnecessárias
- Mais seguro (menos superfície de ataque)

**Desvantagens:**
- Sem endpoints de health check
- Dificulta monitoring/observability

### Opção B: MANTER (Consumer com endpoints)
**Vantagens:**
- Pode expor endpoints de health/metrics
- Facilita integração com Kubernetes/Docker health checks
- Pode adicionar endpoints administrativos (trigger reprocessamento DLQ, etc.)

**Desvantagens:**
- Mais recursos consumidos
- Porta HTTP adicional

**RECOMENDAÇÃO:** Manter `spring-boot-starter-web` mas remover controllers de negócio.
Pode adicionar futuramente endpoints de health/admin se necessário.

---

## Conclusão

Após a execução deste plano, o **ms-consumer** será um microserviço focado exclusivamente em:
- Consumir eventos Kafka
- Processar eventos com idempotência
- Gerenciar DLQ e retry
- Persistir dados processados

Toda a lógica de receber HTTP e produzir eventos ficará no **ms-producer**.
