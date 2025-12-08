# 🚀 Onboarding - Spring Kafka Microservices

## 👋 Bem-vindo!

Este documento orienta você a se familiarizar rapidamente com o projeto.

---

## 📋 Checklist de Início

### Primeira Vez no Projeto

- [ ] Li o README principal de cada microserviço
  - [ ] [ms-producer/README.md](ms-producer/README.md)
  - [ ] [ms-consumer/README.md](ms-consumer/README.md)

- [ ] Li os CLAUDE.md (guias para IA)
  - [ ] [ms-producer/CLAUDE.md](ms-producer/CLAUDE.md)
  - [ ] [ms-consumer/CLAUDE.md](ms-consumer/CLAUDE.md)

- [ ] **IMPORTANTE**: Li a Base de Conhecimento de Erros
  - [ ] [README da Base](ms-producer/docs/base-conhecimento-erros/README.md)
  - [ ] [Guia Rápido](ms-producer/docs/base-conhecimento-erros/GUIA-RAPIDO.md)
  - [ ] [Índice de Erros](ms-producer/docs/base-conhecimento-erros/INDICE.md)
  - [ ] [Erro #001 - Desserialização Crítica](ms-producer/docs/base-conhecimento-erros/001-CRITICO-deserializacao-kafka-outbox-pattern.md)

- [ ] Configurei ambiente local
  - [ ] Java 21 instalado
  - [ ] Docker + Docker Compose instalados
  - [ ] Maven wrapper funciona (`./mvnw --version`)

### Antes de Cada Sessão de Trabalho

- [ ] **SEMPRE consulte a Base de Conhecimento de Erros primeiro!**
  - Economize horas de debugging
  - Evite erros já resolvidos

- [ ] Verifique infraestrutura
  - [ ] `docker compose ps` - todos serviços rodando?
  - [ ] Kafka acessível (http://localhost:8089)
  - [ ] PostgreSQL acessível

- [ ] Compile ambos microserviços
  ```bash
  cd ms-producer && ./mvnw clean compile
  cd ../ms-consumer && ./mvnw clean compile
  ```

---

## 🎯 Arquitetura Rápida

### Fluxo End-to-End

```
POST /api/payments/approved (Postman)
       ↓
ms-producer (Spring Boot)
       ↓
ApprovePaymentService (@Transactional)
       ├─ Salva Payment no PostgreSQL
       └─ Salva OutboxEvent (PENDING)
       ↓
OutboxPublisher (Job a cada 5s)
       ├─ Busca eventos PENDING
       ├─ Deserializa com tipo correto
       └─ Publica no Kafka
       ↓
Kafka (3 brokers)
       ├─ Topic: payment.approved.v1
       └─ DLQ: payment.approved.v1.dlq
       ↓
ms-consumer (Spring Boot)
       ↓
PaymentApprovedConsumer (@KafkaListener)
       ├─ Deserializa evento
       ├─ Verifica idempotência (processed_events)
       ├─ Processa lógica de negócio
       ├─ Salva em processed_events
       └─ Commit manual do offset
```

### Padrões Implementados

1. **Outbox Pattern** (ms-producer)
   - Garante consistência DB + Kafka
   - At-least-once delivery
   - Ver: `docs/base-conhecimento-erros/001-CRITICO`

2. **Idempotency Pattern** (ms-consumer)
   - Tabela `processed_events`
   - Exactly-once semantics
   - Duplicatas são detectadas e ignoradas

3. **Dead Letter Queue**
   - Mensagens com erro vão para DLQ
   - Retry com exponential backoff (5x)
   - Reprocessamento manual ou automático

---

## 🔧 Comandos Essenciais

### Infraestrutura

```bash
# Iniciar tudo
docker compose up -d

# Ver logs
docker compose logs -f kafka-1
docker compose logs -f postgres

# Parar tudo
docker compose down

# Limpar volumes (CUIDADO!)
docker compose down -v
```

### Microserviços

```bash
# Producer
cd ms-producer
./mvnw spring-boot:run

# Consumer
cd ms-consumer
./mvnw spring-boot:run

# Build
./mvnw clean package -DskipTests
```

### Testes

```bash
# Enviar evento (Postman ou curl)
curl -X POST http://localhost:5050/api/payments/approved \
  -H "Content-Type: application/json" \
  -d '{
    "paymentId": "pgto-123",
    "userId": "user-456",
    "amount": 99.99,
    "currency": "BRL"
  }'
```

### Verificações

```sql
-- ms-producer: Verificar outbox
SELECT id, event_type, status, retry_count FROM outbox_event
ORDER BY created_at DESC LIMIT 5;

-- ms-consumer: Verificar processados
SELECT event_id, event_type, processed_at FROM processed_events
ORDER BY processed_at DESC LIMIT 5;
```

---

## 🚨 Erros Comuns (LEIA ISTO!)

### ❌ Erro #1: Mensagens na DLQ

**Sintoma**: Consumer não processa, mensagens vão para DLQ

**ANTES DE DEBUGAR**: Leia [Erro #001](ms-producer/docs/base-conhecimento-erros/001-CRITICO-deserializacao-kafka-outbox-pattern.md)

**Fix Rápido**:
1. Verifique se log `=== CONSUMER INVOKED ===` aparece
2. Se NÃO aparecer → Problema de desserialização
3. Veja seção "Solução Aplicada" no Erro #001

### ❌ Erro #2: Tabela `processed_events` Vazia

**Causa**: Consumer não está processando mensagens

**Fix**: Mesmo do Erro #1 (desserialização)

### ❌ Erro #3: OutboxPublisher Não Publica

**Sintoma**: Eventos ficam PENDING no outbox

**Verificar**:
```bash
# Ver se job está rodando
docker logs ms-producer | grep "Found .* pending outbox events"
```

---

## 📚 Documentação por Prioridade

### 🔴 OBRIGATÓRIO (leia antes de começar)

1. [Base de Conhecimento - Erro #001](ms-producer/docs/base-conhecimento-erros/001-CRITICO-deserializacao-kafka-outbox-pattern.md)
   - **Economiza ~3 horas de debugging**
   - Erro mais crítico já encontrado

2. [GUIA-RAPIDO.md](ms-producer/docs/base-conhecimento-erros/GUIA-RAPIDO.md)
   - Troubleshooting rápido
   - Árvore de decisão
   - Comandos úteis

### 🟠 IMPORTANTE (leia na primeira semana)

3. [CLAUDE.md - Producer](ms-producer/CLAUDE.md)
   - Arquitetura do producer
   - Outbox Pattern explicado

4. [CLAUDE.md - Consumer](ms-consumer/CLAUDE.md)
   - Arquitetura do consumer
   - Idempotency Pattern explicado

### 🟡 COMPLEMENTAR (consulte quando precisar)

5. [README - Base de Conhecimento](ms-producer/docs/base-conhecimento-erros/README.md)
   - Template para documentar novos erros
   - Como contribuir

6. [INDICE.md - Erros](ms-producer/docs/base-conhecimento-erros/INDICE.md)
   - Navegação por categoria
   - Busca por sintoma

---

## 🎓 Recursos de Aprendizado

### Padrões

- [Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Idempotent Consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html)
- [Dead Letter Queue](https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html)

### Tecnologias

- [Spring Kafka Docs](https://docs.spring.io/spring-kafka/docs/current/reference/html/)
- [Apache Kafka Docs](https://kafka.apache.org/documentation/)
- [PostgreSQL Docs](https://www.postgresql.org/docs/)

### Ferramentas

- Redpanda Console: http://localhost:8089
- API Producer: http://localhost:5050
- Consumer (sem API HTTP)

---

## 💡 Dicas Importantes

### ✅ DO (Faça)

- ✅ **SEMPRE consulte a Base de Conhecimento antes de debugar**
- ✅ Documente novos erros imediatamente (memória fresca!)
- ✅ Use os logs formatados para rastreamento
- ✅ Teste com Postman antes de automatizar
- ✅ Verifique ambos microserviços (producer E consumer)
- ✅ Consulte Redpanda Console para ver mensagens Kafka

### ❌ DON'T (Não faça)

- ❌ NUNCA use `Object.class` em `objectMapper.readValue()`
- ❌ NUNCA ignore mensagens na DLQ (investigue a causa!)
- ❌ NUNCA commite código sem testar localmente primeiro
- ❌ NUNCA pule a leitura do Erro #001 (economiza horas!)
- ❌ NUNCA force push para main/master
- ❌ NUNCA desabilite retry sem entender o impacto

---

## 🤝 Como Contribuir

### Encontrou um Bug?

1. **Documente IMEDIATAMENTE** na Base de Conhecimento
2. Use o [template](ms-producer/docs/base-conhecimento-erros/README.md#-template-para-novos-erros)
3. Atualize [INDICE.md](ms-producer/docs/base-conhecimento-erros/INDICE.md)
4. Seja específico: logs, causa raiz, solução

### Melhorou Algo?

1. Documente no README do microserviço
2. Atualize CLAUDE.md se mudou arquitetura
3. Crie PR descritivo

---

## 📊 Próximos Passos

### Semana 1
- [ ] Ambiente local funcionando
- [ ] Entendeu arquitetura básica
- [ ] Leu Base de Conhecimento de Erros
- [ ] Testou fluxo completo (Postman → Kafka → DB)

### Semana 2
- [ ] Implementou primeiro fix de bug
- [ ] Documentou aprendizados
- [ ] Entendeu padrões (Outbox, Idempotency, DLQ)

### Semana 3
- [ ] Contribuiu com novo erro documentado (se aplicável)
- [ ] Otimizou performance (se aplicável)
- [ ] Mentora novo membro (se aplicável)

---

## 🆘 Precisa de Ajuda?

1. **Primeiro**: Consulte [GUIA-RAPIDO.md](ms-producer/docs/base-conhecimento-erros/GUIA-RAPIDO.md)
2. **Segundo**: Busque sintoma no [INDICE.md](ms-producer/docs/base-conhecimento-erros/INDICE.md)
3. **Terceiro**: Leia documento completo do erro
4. **Quarto**: Se ainda não resolver, pergunte ao time (mas compartilhe o que já tentou!)

---

**Bem-vindo ao time! 🚀**

**Última Atualização**: 2025-12-07
