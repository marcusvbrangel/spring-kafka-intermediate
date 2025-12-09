# 🗂️ Índice Visual - Base de Conhecimento de Erros

## 📌 Como Usar Este Índice

Este índice organiza erros por **categoria**, facilitando encontrar soluções rapidamente.

**Estrutura**:
- 🔴 **CRÍTICO**: Sistema completamente quebrado
- 🟠 **ALTO**: Funcionalidade principal afetada
- 🟡 **MÉDIO**: Funcionalidade secundária afetada
- 🟢 **BAIXO**: Melhorias, otimizações

---

## 📚 Por Categoria

### 🔌 Kafka

#### Desserialização
- 🔴 [#001 - Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - **Sintoma**: Mensagens na DLQ, consumer não invocado
  - **Causa**: `Object.class` em `objectMapper.readValue()`
  - **Fix**: Desserialização tipada + `VALUE_DEFAULT_TYPE`

#### Producer
- _(Nenhum erro documentado ainda)_

#### Consumer
- 🔴 [#001 - Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md)

#### DLQ (Dead Letter Queue)
- 🔴 [#001 - Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md)

---

### 🗄️ PostgreSQL

#### Transações
- _(Nenhum erro documentado ainda)_

#### Performance
- _(Nenhum erro documentado ainda)_

#### Índices
- _(Nenhum erro documentado ainda)_

---

### 🏗️ Padrões Arquiteturais

#### Outbox Pattern
- 🔴 [#001 - Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - **Lição**: NUNCA use `Object.class` em desserialização
  - **Regra**: Sempre mapeie `eventType` → `Class`

#### Idempotency Pattern
- 🔴 [#001 - Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - **Contexto**: Tabela `processed_events` ficava vazia
  - **Fix**: Resolver desserialização primeiro

---

### 🔧 Spring Boot

#### Configuração
- 🔴 [#001 - Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - **Config**: `JsonDeserializer.VALUE_DEFAULT_TYPE`
  - **Config**: `JsonDeserializer.USE_TYPE_INFO_HEADERS`

#### Serialização/Desserialização
- 🔴 [#001 - Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - **Jackson**: `ObjectMapper.readValue()` com tipos corretos
  - **Spring Kafka**: ErrorHandlingDeserializer

---

### 🐳 Docker / Infraestrutura

#### Docker Compose
- _(Nenhum erro documentado ainda)_

#### Networking
- _(Nenhum erro documentado ainda)_

---

## 🔍 Por Sintoma

### "Mensagens na DLQ"
→ 🔴 [#001 - Desserialização Kafka](001-CRITICO-deserializacao-kafka-outbox-pattern.md)

### "Consumer não invocado"
→ 🔴 [#001 - Desserialização Kafka](001-CRITICO-deserializacao-kafka-outbox-pattern.md)

### "Tabela processed_events vazia"
→ 🔴 [#001 - Desserialização Kafka](001-CRITICO-deserializacao-kafka-outbox-pattern.md)

### "Erro silencioso (sem stack trace)"
→ 🔴 [#001 - Desserialização Kafka](001-CRITICO-deserializacao-kafka-outbox-pattern.md)

### "Consumer lag crescendo"
→ _(Nenhum erro documentado ainda - adicione aqui!)_

### "OutboxPublisher não publica"
→ _(Nenhum erro documentado ainda - adicione aqui!)_

---

## 📊 Por Componente

### ms-producer

#### OutboxService
- 🔴 [#001 - Desserialização](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - Logs formatados adicionados

#### OutboxPublisher
- 🔴 [#001 - Desserialização](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - `deserializePayload()` adicionado
  - `extractEventId()` adicionado
  - Headers Kafka corrigidos

#### ApprovePaymentService
- _(Nenhum erro documentado ainda)_

---

### ms-consumer

#### KafkaConsumerConfig
- 🔴 [#001 - Desserialização](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - `VALUE_DEFAULT_TYPE` adicionado
  - `USE_TYPE_INFO_HEADERS` alterado

#### PaymentApprovedConsumer
- 🔴 [#001 - Desserialização](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - Logs formatados adicionados

---

## ⏱️ Por Tempo de Resolução

### Rápido (< 1 hora)
- _(Nenhum erro documentado ainda)_

### Moderado (1-3 horas)
- 🔴 [#001 - Desserialização Kafka](001-CRITICO-deserializacao-kafka-outbox-pattern.md) (~3 horas)

### Demorado (> 3 horas)
- _(Nenhum erro documentado ainda - evite chegar aqui!)_

---

## 🎯 Por Impacto de Negócio

### Zero Funcionalidade
- 🔴 [#001 - Desserialização Kafka](001-CRITICO-deserializacao-kafka-outbox-pattern.md)
  - Sistema completamente quebrado
  - 0% de mensagens processadas

### Funcionalidade Degradada
- _(Nenhum erro documentado ainda)_

### Apenas Performance
- _(Nenhum erro documentado ainda)_

---

## 📈 Estatísticas

| Métrica | Valor |
|---------|-------|
| **Total de erros** | 1 |
| **Críticos** | 1 (100%) |
| **Altos** | 0 (0%) |
| **Médios** | 0 (0%) |
| **Baixos** | 0 (0%) |
| **Resolvidos** | 1 (100%) |
| **Em andamento** | 0 (0%) |
| **Tempo médio** | 3 horas |

---

## 🚀 Início Rápido

**Nova sessão de debugging?**

1. **Leia primeiro**: [GUIA-RAPIDO.md](GUIA-RAPIDO.md)
2. **Busque sintomas**: Neste índice ↑
3. **Leia documento completo**: Do erro específico
4. **Aplique fix**: Seguindo passo a passo
5. **Documente**: Se encontrar algo novo

**Encontrou erro novo?**

1. **Use o template**: [README.md](README.md#-template-para-novos-erros)
2. **Documente tudo**: Sintomas, causa raiz, solução
3. **Atualize índices**: README.md e este INDICE.md
4. **Adicione tags**: Para facilitar busca futura

---

## 🔗 Links Importantes

- [📚 README Principal](README.md) - Overview e template
- [⚡ Guia Rápido](GUIA-RAPIDO.md) - Troubleshooting rápido
- [🗂️ Este Índice](INDICE.md) - Navegação por categoria

---

**Última Atualização**: 2025-12-07
**Total de Documentos**: 3 (README + GUIA-RAPIDO + 001-CRITICO)
