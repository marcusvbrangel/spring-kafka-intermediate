# 📚 Base de Conhecimento - Erros e Soluções

## 🎯 Propósito

Esta base de conhecimento documenta **todos os erros** encontrados durante o desenvolvimento e manutenção dos microserviços `ms-producer` e `ms-consumer`. O objetivo é:

1. **Prevenir repetição de erros**: Documentar problemas já resolvidos
2. **Acelerar debugging**: Ter referência rápida para sintomas conhecidos
3. **Transferência de conhecimento**: Facilitar onboarding de novos desenvolvedores
4. **Histórico auditável**: Rastrear evolução e decisões técnicas

---

## 📋 Convenções de Nomenclatura

Cada documento de erro segue o padrão:

```
{número}-{severidade}-{resumo-do-erro}.md
```

### Níveis de Severidade

- **CRITICO**: Sistema completamente quebrado, zero funcionalidade
- **ALTO**: Funcionalidade principal afetada, workaround possível
- **MEDIO**: Funcionalidade secundária afetada
- **BAIXO**: Melhoria, otimização, ou problema cosmético

### Exemplos

```
001-CRITICO-deserializacao-kafka-outbox-pattern.md
002-ALTO-deadlock-transacao-outbox-publisher.md
003-MEDIO-performance-lenta-queries-processed-events.md
004-BAIXO-typo-logs-consumer.md
```

---

## 📝 Template para Novos Erros

Ao documentar um novo erro, use este template:

```markdown
# ERRO {TIPO} #{NÚMERO}: {Título Descritivo}

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

**Data do Incidente**: YYYY-MM-DD
**Severidade**: 🔴 CRÍTICA / 🟠 ALTA / 🟡 MÉDIA / 🟢 BAIXA
**Tempo para Resolução**: X horas
**Microserviços Afetados**: ms-producer, ms-consumer

**Problema**: [Descrição em 1-2 frases]

**Causa Raiz**: [Descrição em 1-2 frases]

**Status**: ✅ RESOLVIDO / 🔄 EM ANDAMENTO / ❌ NÃO RESOLVIDO

---

## 🔍 Sintomas Observados

### Logs
```
[Cole aqui exemplos de logs relevantes]
```

### Comportamento Observado
- [ ] Sintoma 1
- [ ] Sintoma 2
- [ ] Sintoma 3

### Métricas Afetadas
- CPU: X%
- Memória: Y GB
- Latência: Z ms
- Throughput: N msgs/s

---

## 🧬 Causa Raiz

[Descrição detalhada da causa raiz]

### Código Problemático (ANTES)
```java
// Cole o código errado aqui
```

### Por que falhou?
1. Razão 1
2. Razão 2
3. Razão 3

---

## 💥 Impacto

### Impacto Técnico
- ✅ Item 1
- ✅ Item 2

### Impacto de Negócio
- 🔴 Item 1
- 🔴 Item 2

### Se Estivesse em Produção
- 🔴 Consequência 1
- 🔴 Consequência 2

---

## ✅ Solução Aplicada

### Fix #1: [Nome do Fix]

**Arquivo**: `caminho/do/arquivo.java`

**Mudança**:
```java
// ANTES (ERRADO)
código antigo

// DEPOIS (CORRETO)
código novo
```

**Benefícios**:
- ✅ Benefício 1
- ✅ Benefício 2

---

## 📁 Arquivos Modificados

### ms-producer
1. **Arquivo.java** (`caminho/`)
   - ✅ Mudança 1 (linha X)
   - ✅ Mudança 2 (linha Y)

### ms-consumer
1. **Arquivo.java** (`caminho/`)
   - ✅ Mudança 1 (linha X)

---

## 🛡️ Como Evitar no Futuro

### Regras de Ouro

#### 1. **Regra 1**

❌ **ERRADO**:
```java
// código errado
```

✅ **CORRETO**:
```java
// código correto
```

### Checklist para Prevenção

- [ ] Item 1
- [ ] Item 2
- [ ] Item 3

---

## 📅 Timeline da Investigação

### Fase 1: Nome (tempo)
- ✅ Ação 1
- ✅ Ação 2
- ❌ Resultado: descrição

### Fase 2: Nome (tempo)
- ✅ Ação 1
- 🔴 **CAUSA RAIZ ENCONTRADA**: descrição

**Tempo Total**: X horas

---

## 🎓 Lições Aprendidas

### Técnicas
1. Lição 1
2. Lição 2

### Processuais
1. Lição 1
2. Lição 2

---

## 📚 Referências

- [Link 1](url)
- [Link 2](url)

---

## ⚠️ Alertas para o Futuro

Se você ver estes sintomas novamente:

🚨 **ALERTA #1**: Sintoma X
→ Provável: Causa Y
→ Ação: Fazer Z

---

**Data de Criação**: YYYY-MM-DD
**Última Atualização**: YYYY-MM-DD
**Autor**: Nome
**Revisado por**: Nome
```

---

## 🗂️ Índice de Erros Documentados

| # | Severidade | Título | Data | Status |
|---|------------|--------|------|--------|
| 001 | 🔴 CRÍTICA | [Desserialização Kafka com Outbox Pattern](001-CRITICO-deserializacao-kafka-outbox-pattern.md) | 2025-12-07 | ✅ RESOLVIDO |
| ... | | | | |

---

## 🔍 Busca Rápida por Categoria

### Por Tecnologia
- **Kafka**: #001
- **PostgreSQL**:
- **Spring Boot**:
- **Docker**:

### Por Componente
- **Outbox Pattern**: #001
- **Consumer**: #001
- **Producer**: #001
- **DLQ**: #001

### Por Sintoma
- **Mensagens na DLQ**: #001
- **Desserialização**: #001
- **Performance**:
- **Deadlock**:

---

## 📊 Estatísticas

- **Total de erros documentados**: 1
- **Erros críticos**: 1
- **Erros resolvidos**: 1
- **Tempo médio de resolução**: 3 horas

---

## 🤝 Como Contribuir

Ao encontrar um novo erro:

1. **Crie um novo arquivo** seguindo a convenção de nomenclatura
2. **Use o template** acima
3. **Seja detalhado**: quanto mais informação, melhor
4. **Atualize este README**: adicione o erro na tabela de índice
5. **Adicione tags de busca**: facilite encontrar erros similares no futuro

### Informações Obrigatórias

- ✅ Sintomas observados (logs, comportamento)
- ✅ Causa raiz (não só os sintomas!)
- ✅ Solução aplicada (código antes/depois)
- ✅ Arquivos modificados (com linhas específicas)
- ✅ Como evitar no futuro (regras de ouro)

### Informações Opcionais mas Recomendadas

- Timeline da investigação
- Impacto de negócio
- Lições aprendidas
- Alertas para o futuro
- Referências externas

---

## 🎯 Objetivos

Esta base de conhecimento deve:

- ✅ Reduzir tempo de debugging em 50%+
- ✅ Prevenir 90%+ de erros recorrentes
- ✅ Servir como material de onboarding
- ✅ Documentar decisões técnicas importantes

---

**Última Atualização**: 2025-12-07
**Mantida por**: Equipe de Desenvolvimento
