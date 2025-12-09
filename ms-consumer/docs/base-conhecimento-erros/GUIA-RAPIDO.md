# 🚀 Guia Rápido - Troubleshooting

## ⚡ Problemas Comuns e Soluções Rápidas

Use este guia para diagnóstico rápido. Para detalhes completos, consulte o documento específico do erro.

---

## 🔴 Mensagens Indo para DLQ

### Sintoma
```log
===== SENDING TO DLQ =====
Reason: Listener failed
```

### Diagnóstico Rápido
1. Verifique se log `=== CONSUMER INVOKED ===` aparece
   - **NÃO aparece** → Erro de desserialização (#001)
   - **Aparece** → Erro na lógica de negócio

### Solução
- **Se desserialização**: Ver [#001](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
- **Se lógica**: Verifique stack trace no log do consumer

---

## 🟠 Tabela `processed_events` Vazia

### Sintoma
- Mensagens publicadas no Kafka ✅
- Consumer recebendo mensagens ✅
- Tabela `processed_events` vazia ❌

### Diagnóstico Rápido
```sql
-- Verificar se tabela existe
SELECT COUNT(*) FROM processed_events;

-- Verificar DLQ
-- Se DLQ tem mensagens → Problema de processamento
```

### Solução
1. Verifique logs do consumer
2. Verifique se mensagens estão na DLQ
3. Se DLQ cheia → Ver [#001](001-CRITICO-deserializacao-kafka-outbox-pattern.md)

---

## 🟡 Performance Lenta no Consumer

### Sintoma
- Consumer lag aumentando
- Throughput baixo
- CPU/Memória normais

### Diagnóstico Rápido
```bash
# Verificar consumer lag
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group payment-service-approved-group
```

### Possíveis Causas
- Concurrency muito baixa (aumentar em `application.yaml`)
- Queries lentas (verificar índices)
- Transações longas (otimizar lógica)

---

## 🟢 Como Usar Esta Base

### Ao Encontrar um Erro Novo

1. **Documente IMEDIATAMENTE** (memória fresca!)
2. Use o [template do README](README.md#-template-para-novos-erros)
3. Seja específico: logs, código, causa raiz
4. Adicione no índice do README

### Ao Depurar um Erro

1. **Busque sintomas** neste guia rápido
2. **Leia documento completo** do erro correspondente
3. **Siga a solução** passo a passo
4. **Atualize** se encontrar informações novas

---

## 📊 Checklist de Debugging

### Antes de Começar
- [ ] Li os logs completos (não só as últimas linhas)
- [ ] Verifiquei ambos microserviços (producer E consumer)
- [ ] Consultei esta base de conhecimento
- [ ] Reproduzi o erro localmente

### Durante Debugging
- [ ] Anotei timestamps dos eventos relevantes
- [ ] Salvei logs completos em arquivo
- [ ] Testei hipóteses uma por vez
- [ ] Documentei tentativas que NÃO funcionaram

### Após Resolução
- [ ] Documentei a solução nesta base
- [ ] Atualizei índice no README
- [ ] Testei que fix realmente funciona
- [ ] Criei testes para prevenir regressão (se aplicável)

---

## 🎯 Comandos Úteis

### Kafka

```bash
# Ver mensagens em tópico
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payment.approved.v1 --from-beginning

# Ver mensagens na DLQ
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic payment.approved.v1.dlq --from-beginning

# Consumer groups
kafka-consumer-groups --bootstrap-server localhost:9092 --list
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group payment-service-approved-group

# Reset offset (CUIDADO!)
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group payment-service-approved-group --reset-offsets \
  --to-earliest --execute --topic payment.approved.v1
```

### PostgreSQL

```bash
# Conectar ao banco ms-producer
docker exec -it postgres psql -U postgres -d ms-producer

# Conectar ao banco ms-consumer
docker exec -it postgres psql -U postgres -d ms-consumer
```

```sql
-- Verificar outbox events
SELECT id, event_type, status, retry_count, created_at
FROM outbox_event
ORDER BY created_at DESC
LIMIT 10;

-- Verificar processed events
SELECT event_id, event_type, processed_at, kafka_partition, kafka_offset
FROM processed_events
ORDER BY processed_at DESC
LIMIT 10;

-- Verificar eventos pendentes
SELECT COUNT(*), status
FROM outbox_event
GROUP BY status;

-- Verificar eventos com retry
SELECT * FROM outbox_event
WHERE retry_count > 0
ORDER BY created_at DESC;
```

### Docker

```bash
# Logs do producer
docker logs -f ms-producer --tail 100

# Logs do consumer
docker logs -f ms-consumer --tail 100

# Logs do Kafka
docker logs -f kafka-1 --tail 100

# Reiniciar tudo
docker compose down && docker compose up -d
```

### Maven

```bash
# Clean build producer
cd ms-producer && ./mvnw clean compile

# Clean build consumer
cd ms-consumer && ./mvnw clean compile

# Build ambos
./mvnw clean package -DskipTests
```

---

## 🔍 Árvore de Decisão

```
Mensagem não processada?
├─ Está na DLQ?
│  ├─ SIM
│  │  └─ Log "CONSUMER INVOKED" aparece?
│  │     ├─ NÃO → Erro desserialização (#001)
│  │     └─ SIM → Erro lógica negócio
│  └─ NÃO
│     └─ Mensagem no Kafka?
│        ├─ SIM → Consumer não está rodando
│        └─ NÃO → Producer falhou
│
Consumer lag alto?
├─ CPU/Memória altas?
│  ├─ SIM → Problema de recurso
│  └─ NÃO → Concurrency baixa ou queries lentas
│
Outbox com eventos PENDING?
├─ OutboxPublisher rodando?
│  ├─ NÃO → Iniciar aplicação
│  └─ SIM → Verificar logs de erro
```

---

## 📚 Links Úteis

### Documentação Oficial
- [Spring Kafka Docs](https://docs.spring.io/spring-kafka/docs/current/reference/html/)
- [Kafka Docs](https://kafka.apache.org/documentation/)
- [PostgreSQL Docs](https://www.postgresql.org/docs/)

### Ferramentas
- Redpanda Console: http://localhost:8089
- Kafka REST Admin: Ver docker-compose.yaml

### Padrões Implementados
- [Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Idempotent Consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html)

---

**Última Atualização**: 2025-12-07
