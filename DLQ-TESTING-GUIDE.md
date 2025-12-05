# DLQ (Dead Letter Queue) - Guia de Testes

## O que foi implementado?

Foi adicionada uma **Dead Letter Queue (DLQ)** ao perfil **CRITICAL** do consumidor Kafka.

### Configuração:
- **Tópico original**: `payment.approved.v1`
- **Tópico DLQ**: `payment.approved.v1.dlq`
- **Retries**: 5 tentativas
- **Backoff**: Exponencial (1s → 2s → 4s → 8s → 10s)
- **Comportamento**: Após 5 falhas, mensagem é enviada para DLQ automaticamente

### Localização do código:
- **Config DLQ**: `src/main/java/com/mvbr/store/infrastructure/config/kafka/KafkaConsumerConfig.java:85-101`
- **Consumer com commit manual**: `src/main/java/com/mvbr/store/infrastructure/messaging/consumer/PaymentApprovedConsumer.java`
- **DLQ Reprocessor (NOVO)**: `src/main/java/com/mvbr/store/infrastructure/messaging/consumer/DLQReprocessor.java`
- **Config do Reprocessor**: `src/main/resources/application.yaml` (propriedade `dlq.reprocessor.enabled`)

---

## Como testar a DLQ

### Pré-requisitos
```bash
# 1. Subir infraestrutura Kafka
docker compose up -d

# 2. Iniciar aplicação
./mvnw spring-boot:run
```

### Cenário 1: Erro de deserialização (event == null)

**1. Produzir mensagem inválida diretamente no Kafka:**

```bash
# Enviar JSON inválido para o tópico
echo '{"invalid": "json without required fields"}' | \
kafka-console-producer --broker-list localhost:9092 --topic payment.approved.v1
```

**Resultado esperado:**
- Consumer recebe `event == null`
- Lança `IllegalArgumentException`
- Tenta 5 vezes (backoff exponencial)
- Após 5 falhas → Envia para `payment.approved.v1.dlq`

**Logs esperados:**
```
===== DESERIALIZATION ERROR =====
Received null event - message will be retried or sent to DLQ
=================================

[retry 1/5] após 1s
[retry 2/5] após 2s
[retry 3/5] após 4s
[retry 4/5] após 8s
[retry 5/5] após 10s

===== SENDING TO DLQ =====
Original Topic: payment.approved.v1
DLQ Topic: payment.approved.v1.dlq
Reason: Failed to deserialize event - received null
==========================
```

---

### Cenário 2: Erro no processamento (RuntimeException)

**1. Modificar temporariamente o consumer para forçar erro:**

```java
// Em PaymentApprovedConsumer.java
public void handlePaymentApproved(PaymentApprovedEvent event, Acknowledgment acknowledgment) {
    if (event == null) {
        throw new IllegalArgumentException("Failed to deserialize event - received null");
    }

    try {
        System.out.println("Received event: " + event.eventId());

        // FORÇAR ERRO PARA TESTE
        if (event.userId().equals("user123")) {
            throw new RuntimeException("Simulated processing error");
        }

        // ... resto do código
    }
}
```

**2. Enviar evento válido:**

```bash
curl -X POST http://localhost:5050/api/payments/approved \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "pay-test-001",
    "userId": "user123",
    "amount": 100.50,
    "status": "APPROVED"
  }'
```

**Resultado esperado:**
- Consumer processa normalmente até detectar `userId == "user123"`
- Lança `RuntimeException`
- Tenta 5 vezes (backoff exponencial)
- Após 5 falhas → Envia para `payment.approved.v1.dlq`
- **NÃO faz commit** da mensagem original

---

### Cenário 3: Sucesso (mensagem processada corretamente)

**1. Enviar evento válido:**

```bash
curl -X POST http://localhost:5050/api/payments/approved \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "pay-success-001",
    "userId": "user999",
    "amount": 250.00,
    "status": "APPROVED"
  }'
```

**Resultado esperado:**
```
===== PAYMENT APPROVED EVENT RECEIVED =====
eventId:   <uuid>
paymentId: pay-success-001
userId:    user999
amount:    250.0
status:    APPROVED
timestamp: <timestamp>
===========================================

[COMMIT] Offset committed for eventId: <uuid>
```

- ✅ Mensagem processada
- ✅ Commit manual executado
- ✅ Próxima mensagem é consumida

---

## Monitoramento da DLQ

### 1. Via Redpanda Console (UI)
```bash
# Acessar no navegador
http://localhost:8089

# Navegar para Topics → payment.approved.v1.dlq
# Ver mensagens, headers, e metadados de erro
```

### 2. Via CLI do Kafka

**Listar tópicos:**
```bash
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list | grep dlq
```

**Consumir mensagens da DLQ:**
```bash
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.approved.v1.dlq \
  --from-beginning \
  --property print.headers=true \
  --property print.timestamp=true
```

**Headers importantes na DLQ:**
- `kafka_dlt-original-topic`: Tópico original
- `kafka_dlt-original-partition`: Partição original
- `kafka_dlt-original-offset`: Offset original
- `kafka_dlt-exception-fqcn`: Nome completo da exceção
- `kafka_dlt-exception-message`: Mensagem de erro
- `kafka_dlt-exception-stacktrace`: Stacktrace completo

---

## Reprocessamento de mensagens da DLQ

### Opção 1: Correção e Replay Manual

```bash
# 1. Consumir mensagens da DLQ e salvar em arquivo
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.approved.v1.dlq \
  --from-beginning > dlq-messages.json

# 2. Corrigir o bug no código
# 3. Republicar mensagens no tópico original

cat dlq-messages.json | \
docker exec -i kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic payment.approved.v1
```

### Opção 2: DLQReprocessor Automático (RECOMENDADO) ⭐

**Foi implementado um `DLQReprocessor` completo e bem documentado!**

Localização: `src/main/java/com/mvbr/store/infrastructure/messaging/consumer/DLQReprocessor.java`

#### Como funciona:

1. **Monitora a DLQ automaticamente**
2. **Lê headers de erro** (exceção, stacktrace, offset original)
3. **Republicar** mensagens de volta ao tópico original
4. **Controle via configuração** (enable/disable)

#### Passo a passo para usar:

**CENÁRIO: Você tem mensagens na DLQ e corrigiu o bug**

```bash
# 1. Verificar que há mensagens na DLQ
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.approved.v1.dlq \
  --from-beginning \
  --max-messages 5

# Saída exemplo:
# {"paymentId":"pay-123","userId":"user456","amount":100.00,...}
# {"paymentId":"pay-124","userId":"user789","amount":200.00,...}
```

```bash
# 2. CORRIGIR O BUG no código
# Exemplo: Adicionar null check no PaymentApprovedConsumer.java

# 3. FAZER DEPLOY da correção
./mvnw clean package
```

```yaml
# 4. HABILITAR o DLQReprocessor em application.yaml
dlq:
  reprocessor:
    enabled: true  # ⚠️ Mude de false para true
```

```bash
# 5. REINICIAR a aplicação
./mvnw spring-boot:run
```

**O que acontece agora:**

```
[Aplicação inicia]
   ↓
DLQReprocessor detecta enabled=true
   ↓
Listener da DLQ ativa automaticamente
   ↓
Consome mensagem da DLQ
   ↓
Lê headers de erro (exceção original, topic original, etc.)
   ↓
LOG: "╔════════ DLQ REPROCESSING STARTED ════════╗"
   ↓
Republica para payment.approved.v1 (tópico original)
   ↓
PaymentApprovedConsumer processa novamente (agora com bug corrigido!)
   ↓
✅ SUCESSO! Commit executado
   ↓
LOG: "╔════════ REPROCESSING SUCCESSFUL ════════╗"
```

#### Logs detalhados que você verá:

```
╔════════════════════════════════════════════════════════════════════════════╗
║                    DLQ REPROCESSING STARTED                                ║
╚════════════════════════════════════════════════════════════════════════════╝

📍 DLQ LOCATION:
   Topic:     payment.approved.v1.dlq
   Partition: 0
   Offset:    3

📜 ORIGINAL MESSAGE:
   Topic:     payment.approved.v1
   Partition: 0
   Offset:    127

❌ ERROR DETAILS:
   Exception: java.lang.NullPointerException
   Message:   Cannot invoke "String.toUpperCase()" because "description" is null

💳 PAYMENT EVENT:
   EventId:   550e8400-e29b-41d4-a716-446655440000
   PaymentId: pay-123
   UserId:    user456
   Amount:    100.50
   Status:    APPROVED

🔄 REPUBLISHING...
   Destination: payment.approved.v1
   Partition Key: user456 (same as original)

╔════════════════════════════════════════════════════════════════════════╗
║                  ✅ REPROCESSING SUCCESSFUL                             ║
╚════════════════════════════════════════════════════════════════════════╝
   PaymentId: pay-123
   Topic:     payment.approved.v1
   Partition: 0
   Offset:    250
   Timestamp: 1670432102345

➡️  Message will be consumed again by PaymentApprovedConsumer
```

#### IMPORTANTE - Evitar loops infinitos:

⚠️ **SEMPRE desabilite após reprocessamento:**

```yaml
# Após todas mensagens serem reprocessadas com sucesso:
dlq:
  reprocessor:
    enabled: false  # Voltar para false!
```

**Por quê?**
- Se o bug ainda existir: mensagem vai para DLQ → reprocessor republica → erro novamente → DLQ → loop infinito! 🔁
- Se deixar habilitado: Todas mensagens futuras com erro serão republicadas automaticamente (pode não ser o desejado)

#### Estratégias de Reprocessamento (veja no código):

O `DLQReprocessor.java` tem **3 estratégias comentadas** que você pode ativar:

**Estratégia A (ATUAL):** Republica sempre
```java
// Linha 181: Estratégia padrão - mais simples
republishToOriginalTopic(event, originalTopic);
```

**Estratégia B (OPCIONAL):** Republica apenas erros temporários
```java
// Linhas 183-190: Exemplo comentado
// Só republica TimeoutException, SocketException, etc.
// NullPointerException → Não republica (precisa correção de código)
if (isTemporaryError(exceptionClass)) {
    republishToOriginalTopic(event, originalTopic);
}
```

**Estratégia C (OPCIONAL):** Republica com aprovação manual
```java
// Linhas 192-197: Exemplo comentado
// Ops team marca paymentId em dashboard para republicar
if (isApprovedForReprocessing(event.paymentId())) {
    republishToOriginalTopic(event, originalTopic);
}
```

#### Vantagens do DLQReprocessor:

✅ **Automático** - Não precisa script manual
✅ **Headers preservados** - Vê exatamente qual foi o erro original
✅ **Logs detalhados** - Visibilidade total do que está acontecendo
✅ **Particionamento correto** - Usa `userId` como chave (ordem preservada)
✅ **Idempotente** - Usa `criticalKafkaTemplate` (sem duplicatas)
✅ **Código documentado** - 300+ linhas de comentários explicativos!

---

## Limpeza da DLQ

### Deletar tópico DLQ (cuidado!):
```bash
docker exec -it kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --delete \
  --topic payment.approved.v1.dlq
```

### Configurar retenção automática:
```bash
# Manter mensagens na DLQ por 7 dias apenas
docker exec -it kafka kafka-configs \
  --bootstrap-server localhost:9092 \
  --alter \
  --entity-type topics \
  --entity-name payment.approved.v1.dlq \
  --add-config retention.ms=604800000
```

---

## Troubleshooting

### DLQ não está sendo criada?
- Verifique se o `criticalKafkaTemplate` está configurado corretamente
- Confirme que o consumer está usando `criticalKafkaListenerContainerFactory`
- Verifique logs: `[SENDING TO DLQ]` deve aparecer após 5 falhas

### Mensagens não estão chegando na DLQ?
- Verifique se o retry está configurado (5 tentativas)
- Confirme que a exceção está sendo lançada corretamente
- Verifique se o `DeadLetterPublishingRecoverer` está configurado no `DefaultErrorHandler`

### Offset não está sendo commitado?
- Para mensagens com sucesso: `acknowledgment.acknowledge()` deve ser chamado
- Para mensagens com erro: NÃO chame `acknowledgment.acknowledge()`
- Mensagens que vão pra DLQ têm seu offset commitado automaticamente pelo framework

---

## Próximos passos (melhorias futuras)

1. **Alertas**: Configurar monitoramento para alertar quando mensagens chegam na DLQ
2. **Métricas**: Expor métricas Prometheus/Micrometer para DLQ
3. **Retry com delay**: Implementar retry com delay maior antes de enviar para DLQ
4. **DLQ secundária**: Criar DLQ-DLQ para mensagens que falham ao processar na DLQ
5. **Classificação de erros**: Enviar para DLQ apenas erros irrecuperáveis (não bugs temporários)