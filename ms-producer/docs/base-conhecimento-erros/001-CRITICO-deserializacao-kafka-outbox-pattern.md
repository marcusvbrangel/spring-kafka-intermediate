# ERRO CRÍTICO #001: Desserialização Kafka com Outbox Pattern

## 📋 Índice
- [Resumo Executivo](#resumo-executivo)
- [Sintomas Observados](#sintomas-observados)
- [Causa Raiz](#causa-raiz)
- [Impacto](#impacto)
- [Solução Aplicada](#solução-aplicada)
- [Arquivos Modificados](#arquivos-modificados)
- [Como Evitar no Futuro](#como-evitar-no-futuro)
- [Timeline da Investigação](#timeline-da-investigação)

---

## 📊 Resumo Executivo

**Data do Incidente**: 2025-12-07
**Severidade**: 🔴 CRÍTICA
**Tempo para Resolução**: ~3 horas de debugging intenso
**Microserviços Afetados**: ms-producer, ms-consumer

**Problema**: Mensagens Kafka eram enviadas para DLQ imediatamente após recepção pelo consumer, sem invocar o método `@KafkaListener`. A tabela `processed_events` permanecia vazia, indicando que nenhum evento era processado com sucesso.

**Causa Raiz**: Incompatibilidade de tipos durante serialização/desserialização JSON entre producer e consumer devido ao uso incorreto do Outbox Pattern.

**Status**: ✅ RESOLVIDO

---

## 🔍 Sintomas Observados

### Consumer (ms-consumer)

```log
Received: 1 records
===== SENDING TO DLQ =====
Original Topic: payment.approved.v1
DLQ Topic: payment.approved.v1.dlq
Reason: Listener failed
==========================
```

**Evidências**:
1. Log `=== CONSUMER INVOKED ===` **NUNCA APARECIA**
2. Mensagens iam direto para DLQ sem passar pelo método do listener
3. Tabela `processed_events` estava **VAZIA**
4. Nenhum erro de stack trace visível (erro silencioso)
5. ErrorHandlingDeserializer estava capturando exceção antes do método ser invocado

### Producer (ms-producer)

```log
Event published successfully to Kafka: id=..., partition=1, offset=4
Outbox event marked as PUBLISHED: id=...
```

**Evidências**:
1. Outbox salvava eventos corretamente (status: PENDING)
2. OutboxPublisher publicava no Kafka com sucesso
3. Kafka confirmava recebimento (partition, offset)
4. Producer considerava tudo OK ✅

### Redpanda Console

- Mensagem visível no tópico `payment.approved.v1`
- Mensagem também presente em `payment.approved.v1.dlq`
- JSON parecia correto visualmente, mas tinha problema de tipo

---

## 🧬 Causa Raiz

### Problema #1: OutboxPublisher desserializava como `Object.class`

**Arquivo**: `ms-producer/src/main/java/com/mvbr/store/infrastructure/adapter/out/outbox/OutboxPublisher.java`

**Código ERRADO (antes)**:
```java
// Linha ~120 (versão antiga)
Object payload = objectMapper.readValue(
    outboxEvent.getPayload(),
    Object.class  // ❌ ERRO: Cria LinkedHashMap em vez de PaymentApprovedEvent
);
```

**O que acontecia**:
1. `objectMapper.readValue(..., Object.class)` retornava `LinkedHashMap<String, Object>`
2. Kafka `JsonSerializer` serializava o LinkedHashMap
3. JSON no Kafka não tinha informação de tipo (type headers)
4. Consumer tentava desserializar mas não sabia qual classe usar
5. ErrorHandlingDeserializer capturava erro e enviava para DLQ

### Problema #2: Consumer não tinha tipo padrão configurado

**Arquivo**: `ms-consumer/src/main/java/com/mvbr/store/infrastructure/config/kafka/KafkaConsumerConfig.java`

**Configuração ERRADA (antes)**:
```java
// Linha ~96-98 (versão antiga)
props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);  // ❌ Esperava headers que não existiam
// ❌ FALTAVA: JsonDeserializer.VALUE_DEFAULT_TYPE
```

**O que acontecia**:
1. Consumer esperava headers de tipo (`__TypeId__`) no JSON
2. Esses headers não existiam (porque producer serializou LinkedHashMap)
3. Sem tipo padrão configurado, JsonDeserializer não sabia qual classe instanciar
4. Desserialização falhava silenciosamente

### Problema #3: Headers Kafka usavam ID errado

**Arquivo**: `ms-producer/src/main/java/com/mvbr/store/infrastructure/adapter/out/outbox/OutboxPublisher.java`

**Código ERRADO (antes)**:
```java
// Linha ~136-138 (versão antiga)
record.headers().add(new RecordHeader(
    "event-id",
    outboxEvent.getId().getBytes(StandardCharsets.UTF_8)  // ❌ ID da tabela outbox
));
```

**O que acontecia**:
1. Header `event-id` usava `OutboxEvent.id` (ID da tabela de infraestrutura)
2. Consumer salvava `PaymentApprovedEvent.eventId` (ID do domínio)
3. IDs não batiam, dificultando rastreabilidade

---

## 💥 Impacto

### Impacto Técnico
- ✅ **0% de mensagens processadas com sucesso**
- ✅ **100% de mensagens na DLQ**
- ✅ Sistema completamente não-funcional para eventos Kafka
- ✅ Outbox Pattern funcionando apenas pela metade (só producer)

### Impacto de Observabilidade
- ❌ Logs não mostravam causa raiz (erro silencioso)
- ❌ Difícil rastreamento (IDs inconsistentes)
- ❌ Debugging extremamente demorado

### Se Estivesse em Produção
- 🔴 **Perda total de eventos críticos** (pagamentos aprovados não processados)
- 🔴 **DLQ lotando rapidamente** (crescimento infinito)
- 🔴 **Idempotência quebrada** (tabela `processed_events` vazia)
- 🔴 **Alertas disparando** mas sem visibilidade da causa

---

## ✅ Solução Aplicada

### Fix #1: OutboxPublisher - Desserialização tipada

**Arquivo**: `ms-producer/.../OutboxPublisher.java`

**Mudança**:
```java
// ANTES (ERRADO)
Object payload = objectMapper.readValue(
    outboxEvent.getPayload(),
    Object.class  // ❌ LinkedHashMap
);

// DEPOIS (CORRETO)
Object payload = deserializePayload(outboxEvent);

// Novo método adicionado:
private Object deserializePayload(OutboxEvent outboxEvent) throws Exception {
    return switch (outboxEvent.getEventType()) {
        case "PAYMENT_APPROVED" -> objectMapper.readValue(
                outboxEvent.getPayload(),
                PaymentApprovedEvent.class  // ✅ Tipo correto
        );
        // Adicione novos tipos aqui conforme necessário
        default -> throw new IllegalArgumentException(
                "Unknown event type: " + outboxEvent.getEventType()
        );
    };
}
```

**Benefícios**:
- ✅ Payload deserializado como `PaymentApprovedEvent` (tipo correto)
- ✅ JSON serializado com estrutura correta
- ✅ Fácil adicionar novos tipos de eventos

### Fix #2: Consumer - Tipo padrão configurado

**Arquivo**: `ms-consumer/.../KafkaConsumerConfig.java`

**Mudança**:
```java
// ANTES (ERRADO)
props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);  // ❌

// DEPOIS (CORRETO)
props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);  // ✅
props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
          "com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent");  // ✅

// Em criticalConsumerFactory():
props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
          "com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent");
```

**Benefícios**:
- ✅ Consumer sabe qual classe usar para desserialização
- ✅ Não depende de type headers
- ✅ Desserialização funciona corretamente

### Fix #3: Headers Kafka - ID de domínio

**Arquivo**: `ms-producer/.../OutboxPublisher.java`

**Mudança**:
```java
// ANTES (ERRADO)
record.headers().add(new RecordHeader(
    "event-id",
    outboxEvent.getId().getBytes(StandardCharsets.UTF_8)  // ❌ ID da tabela
));

// DEPOIS (CORRETO)
String domainEventId = extractEventId(payload, outboxEvent.getEventType());
record.headers().add(new RecordHeader(
    "event-id",
    domainEventId.getBytes(StandardCharsets.UTF_8)  // ✅ ID do domínio
));

// Novo método adicionado:
private String extractEventId(Object payload, String eventType) {
    return switch (eventType) {
        case "PAYMENT_APPROVED" -> ((PaymentApprovedEvent) payload).eventId();
        default -> throw new IllegalArgumentException(
                "Unknown event type for eventId extraction: " + eventType
        );
    };
}
```

**Benefícios**:
- ✅ Header `event-id` usa mesmo ID que consumer salva em `processed_events`
- ✅ Rastreabilidade end-to-end perfeita
- ✅ Logs consistentes entre producer e consumer

### Fix #4: Logs formatados (bonus)

**Arquivos**:
- `ms-producer/.../OutboxService.java`
- `ms-producer/.../OutboxPublisher.java`
- `ms-consumer/.../PaymentApprovedConsumer.java`

**Mudança**: Adicionados logs em formato de etiqueta para fácil rastreamento:

```java
log.info("\n" +
    "=================================================================\n" +
    "                  📤 OUTBOX → KAFKA PUBLISHER                    \n" +
    "=================================================================\n" +
    "  Outbox ID (Tabela):  {}\n" +
    "  Event ID (Domínio):  {}\n" +
    "  Event Type:          {}\n" +
    "  ...\n" +
    "=================================================================",
    outboxEvent.getId(),
    domainEventId,
    outboxEvent.getEventType()
);
```

**Benefícios**:
- ✅ Fácil identificação visual nos logs
- ✅ Diferenciação clara entre Outbox ID e Event ID
- ✅ Rastreamento end-to-end facilitado
- ✅ Debugging muito mais rápido

---

## 📁 Arquivos Modificados

### ms-producer

1. **OutboxPublisher.java** (`infrastructure/adapter/out/outbox/`)
   - ✅ Adicionado método `deserializePayload()` (linhas 209-221)
   - ✅ Adicionado método `extractEventId()` (linhas 233-242)
   - ✅ Modificado `publishEvent()` para usar desserialização tipada (linha 121)
   - ✅ Modificado headers Kafka para usar Event ID de domínio (linha 124, 140)
   - ✅ Adicionados logs formatados (linhas 124-141, 174-185, 191-208)

2. **OutboxService.java** (`infrastructure/adapter/out/outbox/`)
   - ✅ Adicionados logs formatados em `saveEvent()` (linhas 79-100)
   - ✅ Adicionados logs formatados para erros (linhas 108-121)

### ms-consumer

1. **KafkaConsumerConfig.java** (`infrastructure/config/kafka/`)
   - ✅ Alterado `USE_TYPE_INFO_HEADERS` para `false` (linha 98)
   - ✅ Adicionado `VALUE_DEFAULT_TYPE` em `criticalConsumerFactory()` (linhas 116-117)
   - ✅ Mantido `TYPE_MAPPINGS` para compatibilidade (linhas 99-100)

2. **PaymentApprovedConsumer.java** (`infrastructure/messaging/consumer/`)
   - ✅ Adicionados logs formatados para recepção (linhas 66-89)
   - ✅ Adicionados logs formatados para duplicatas (linhas 97-113)
   - ✅ Adicionados logs formatados para sucesso (linhas 156-176)
   - ✅ Adicionados logs formatados para erros (linhas 49-60)

---

## 🛡️ Como Evitar no Futuro

### Regras de Ouro

#### 1. **NUNCA use `Object.class` em `objectMapper.readValue()`**

❌ **ERRADO**:
```java
Object payload = objectMapper.readValue(json, Object.class);
```

✅ **CORRETO**:
```java
PaymentApprovedEvent event = objectMapper.readValue(json, PaymentApprovedEvent.class);
```

#### 2. **SEMPRE configure `VALUE_DEFAULT_TYPE` no consumer**

❌ **ERRADO**:
```java
// Configuração sem tipo padrão
props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);
```

✅ **CORRETO**:
```java
props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
          "com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent");
```

#### 3. **SEMPRE use IDs de domínio em headers Kafka**

❌ **ERRADO**:
```java
// Usando ID de infraestrutura
record.headers().add(new RecordHeader("event-id", outboxEvent.getId().getBytes()));
```

✅ **CORRETO**:
```java
// Usando ID de domínio (do payload)
String eventId = ((PaymentApprovedEvent) payload).eventId();
record.headers().add(new RecordHeader("event-id", eventId.getBytes()));
```

#### 4. **SEMPRE teste com logs detalhados primeiro**

✅ **OBRIGATÓRIO**:
- Adicione logs no início do método `@KafkaListener` para confirmar invocação
- Use logs formatados para rastreabilidade
- Teste desserialização antes de implementar lógica de negócio

#### 5. **SEMPRE valide a estrutura do JSON no Kafka**

✅ **VALIDAÇÃO**:
```bash
# Use Redpanda Console ou kafka-console-consumer para verificar JSON
# Certifique-se que:
# 1. Estrutura do JSON match com a classe do evento
# 2. Tipos dos campos estão corretos (BigDecimal, Long, etc)
# 3. Não há campos extras ou faltando
```

### Checklist para Novos Eventos

Quando adicionar um novo tipo de evento:

- [ ] Criar classe `XxxEvent` (record com todos os campos necessários)
- [ ] Adicionar case em `OutboxPublisher.deserializePayload()`
- [ ] Adicionar case em `OutboxPublisher.extractEventId()`
- [ ] Configurar `VALUE_DEFAULT_TYPE` no consumer (se necessário)
- [ ] Testar com Postman/curl antes de ir para produção
- [ ] Verificar logs formatados aparecem corretamente
- [ ] Confirmar que `processed_events` é populado
- [ ] Verificar que DLQ NÃO recebe mensagens

---

## 📅 Timeline da Investigação

### Fase 1: Identificação do Problema (30 min)
- ✅ User reportou: mensagens na DLQ, `processed_events` vazia
- ✅ Confirmado: log `=== CONSUMER INVOKED ===` não aparecia
- ✅ Hipótese inicial: problema de desserialização

### Fase 2: Investigação do Consumer (45 min)
- ✅ Leitura de `KafkaConsumerConfig.java`
- ✅ Leitura de `PaymentApprovedConsumer.java`
- ✅ Identificado: `USE_TYPE_INFO_HEADERS=true` mas headers não existiam
- ✅ Tentativa de fix: adicionar `TYPE_MAPPINGS`
- ❌ Resultado: ainda não funcionou

### Fase 3: Investigação do Producer (60 min)
- ✅ Leitura de `ApprovePaymentService.java` (evento criado corretamente)
- ✅ Leitura de `OutboxPublisher.java`
- 🔴 **CAUSA RAIZ ENCONTRADA**: `readValue(..., Object.class)` → LinkedHashMap
- ✅ Fix aplicado: desserialização tipada

### Fase 4: Correção do Consumer (15 min)
- ✅ Adicionado `VALUE_DEFAULT_TYPE`
- ✅ Alterado `USE_TYPE_INFO_HEADERS` para `false`

### Fase 5: Correção dos Headers (15 min)
- ✅ Criado método `extractEventId()`
- ✅ Headers Kafka agora usam Event ID de domínio

### Fase 6: Melhoria de Logs (30 min)
- ✅ Logs formatados adicionados em ambos microserviços
- ✅ Rastreabilidade end-to-end implementada

### Fase 7: Validação Final (15 min)
- ✅ Teste com Postman
- ✅ Confirmado: mensagem processada com sucesso
- ✅ Confirmado: `processed_events` populado
- ✅ Confirmado: nenhuma mensagem na DLQ

**Tempo Total**: ~3 horas

---

## 🎓 Lições Aprendidas

### Técnicas

1. **Outbox Pattern exige desserialização tipada**
   - Não confie em `Object.class` quando usar ObjectMapper
   - Use switch/case para mapear `eventType` → Class

2. **JsonDeserializer precisa saber o tipo**
   - Configure `VALUE_DEFAULT_TYPE` sempre
   - Não dependa de type headers se não estiver enviando

3. **Logs são críticos para debugging**
   - Erro silencioso é o pior tipo de erro
   - Logs formatados aceleram investigação em 10x

4. **IDs precisam ser consistentes**
   - Diferencie claramente: ID de infraestrutura vs ID de domínio
   - Headers Kafka devem refletir IDs de domínio para rastreabilidade

### Processuais

1. **Sempre teste com logs debug primeiro**
   - Adicione log no início do método para confirmar invocação
   - Só depois implemente lógica de negócio

2. **Valide ambos os lados (producer + consumer)**
   - Problema pode estar em qualquer lado
   - Não assuma que producer está correto só porque "publica com sucesso"

3. **Use ferramentas de observabilidade**
   - Redpanda Console
   - Logs estruturados
   - Métricas (processed_events table growth)

---

## 📚 Referências

- [Spring Kafka - JsonDeserializer Configuration](https://docs.spring.io/spring-kafka/docs/current/reference/html/#serdes)
- [Outbox Pattern - Microservices.io](https://microservices.io/patterns/data/transactional-outbox.html)
- [Jackson ObjectMapper - Type Handling](https://github.com/FasterXML/jackson-docs/wiki/JacksonPolymorphicDeserialization)

---

## ⚠️ Alertas para o Futuro

Se você ver estes sintomas novamente:

🚨 **ALERTA #1**: Mensagens indo para DLQ sem stack trace
→ Provável: Erro de desserialização silencioso
→ Ação: Verificar `JsonDeserializer` configuration

🚨 **ALERTA #2**: Log `=== CONSUMER INVOKED ===` não aparece
→ Provável: ErrorHandlingDeserializer capturando erro antes do método
→ Ação: Verificar tipo do payload e configuração do deserializer

🚨 **ALERTA #3**: Tabela `processed_events` vazia mas eventos no Kafka
→ Provável: Consumer não está processando mensagens
→ Ação: Verificar logs do consumer e DLQ topic

🚨 **ALERTA #4**: IDs não batem entre logs do producer e consumer
→ Provável: Headers usando ID errado
→ Ação: Verificar se headers usam Event ID de domínio

---

**Data de Criação**: 2025-12-07
**Última Atualização**: 2025-12-07
**Autor**: Claude Code (debugging session)
**Revisado por**: User (validação em produção simulada)
