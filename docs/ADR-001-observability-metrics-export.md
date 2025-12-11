# ADR-001: Exportação de Métricas de Observabilidade (HikariCP, Kafka, Hibernate)

## Status
**ACEITO** - 2025-12-11

## Contexto

### Problema Identificado

Após implementar a observabilidade com OpenTelemetry nos microserviços `ms-producer` e `ms-consumer`, identificamos que **traces e logs** estão funcionando corretamente, mas **métricas específicas de infraestrutura** não estão sendo exportadas para o Prometheus/Grafana.

### Métricas Funcionando ✅

- **Traces**: ✅ FUNCIONANDO
  - HTTP requests
  - SQL queries (SELECT, INSERT, UPDATE, DELETE)
  - Kafka producers/consumers
  - Métodos internos (@Scheduled, etc.)

- **Logs**: ✅ FUNCIONANDO
  - Logs estruturados no Loki
  - Correlação com traces via trace_id

- **Métricas JVM Básicas**: ✅ FUNCIONANDO
  - `jvm_memory_used_bytes`
  - `jvm_gc_duration_seconds`
  - `jvm_thread_count`
  - `jvm_cpu_recent_utilization_ratio`

- **Métricas HTTP**: ✅ FUNCIONANDO
  - `http_server_request_duration_seconds`
  - Request count, latências

### Métricas NÃO Exportadas ❌

#### 1. HikariCP (Connection Pool)
Dashboards afetados: **Database & Hibernate Performance**

Métricas esperadas mas ausentes:
```
hikaricp_connections_active
hikaricp_connections_idle
hikaricp_connections_pending
hikaricp_connections_max
hikaricp_connections_min
hikaricp_connection_acquire_time_bucket (P95/P99)
hikaricp_connection_usage_time_bucket
```

**Impacto**: Não conseguimos monitorar:
- Pool de conexões (vazamentos, esgotamento)
- Tempo de aquisição de conexões
- Performance do banco de dados

#### 2. Kafka Consumer/Producer Metrics
Dashboards afetados: **Microservices E2E Observability**

Métricas esperadas mas ausentes:
```
kafka_consumer_group_lag{group="ms-consumer"}
kafka_consumer_records_consumed_total
kafka_producer_record_send_total
kafka_consumer_fetch_latency_avg
```

**Impacto**: Não conseguimos monitorar:
- Consumer lag (atraso no processamento)
- Throughput (mensagens/segundo)
- Latência de consumo/produção
- Health do Kafka

#### 3. Hibernate/JDBC Metrics
Dashboards afetados: **Database & Hibernate Performance**

Métricas esperadas mas ausentes:
```
hibernate_statements_executed_total
hibernate_query_execution_max_time
jdbc_connection_pool_usage
```

**Impacto**: Limitado - os **traces SQL** funcionam e dão visibilidade das queries

### Análise Técnica

#### Por que o OpenTelemetry não exporta essas métricas?

O **OpenTelemetry Java Agent** (versão 2.20.1) foca em **tracing distribuído** e exporta apenas métricas JVM básicas por padrão. A instrumentação automática gera:

1. **Traces** para:
   - HTTP (server/client)
   - JDBC/SQL
   - Kafka (producer/consumer)
   - gRPC, Redis, MongoDB, etc.

2. **Métricas** apenas para:
   - JVM (heap, GC, threads)
   - HTTP server (latências, contadores)

3. **NÃO exporta métricas** de:
   - Connection pools (HikariCP, C3P0, etc.)
   - Kafka internals (lag, throughput)
   - Hibernate statistics
   - Spring Boot metrics específicas

**Referência**: https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/supported-libraries.md

## Opções Consideradas

### Opção 1: Spring Boot Actuator + Micrometer (RECOMENDADA) ✅

**Descrição:**
- Usar Spring Boot Actuator (já configurado) com Micrometer
- Expor endpoint `/actuator/prometheus` nos microserviços
- Prometheus scrape diretamente das aplicações
- Coexistir com OpenTelemetry (traces/logs)

**Vantagens:**
- ✅ **Zero código adicional** - só configuração
- ✅ **Métricas nativas do Spring Boot**:
  - HikariCP (pool de conexões)
  - Kafka (consumer lag, throughput)
  - Hibernate (queries, cache)
  - JVM detalhadas
- ✅ **Actuator já está configurado** nos dois microserviços
- ✅ **Amplamente utilizado** na indústria (padrão de fato)
- ✅ **Documentação extensa** e comunidade ativa
- ✅ **Baixa complexidade** de implementação

**Desvantagens:**
- ⚠️ Prometheus precisa scrape em 2 endpoints extras (ms-producer:5050, ms-consumer:5051)
- ⚠️ Duas fontes de métricas (OpenTelemetry + Micrometer)

**Esforço:** 🟢 BAIXO (1-2 horas)
- Ajustar `application.yaml` (habilitar métricas)
- Atualizar `prometheus.yaml` (adicionar scrape configs)
- Reiniciar serviços

**Implementação:**
```yaml
# application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
```

```yaml
# prometheus.yaml
scrape_configs:
  - job_name: 'ms-producer'
    scrape_interval: 10s
    static_configs:
      - targets: ['localhost:5050']
    metrics_path: '/actuator/prometheus'

  - job_name: 'ms-consumer'
    scrape_interval: 10s
    static_configs:
      - targets: ['localhost:5051']
    metrics_path: '/actuator/prometheus'
```

---

### Opção 2: OpenTelemetry Metrics + Instrumentação Manual

**Descrição:**
- Configurar OpenTelemetry para exportar métricas customizadas
- Criar MeterProvider e registrar métricas manualmente
- Enviar via OTLP Collector → Prometheus

**Vantagens:**
- ✅ Fonte única de telemetria (OpenTelemetry)
- ✅ Consistência (traces + metrics + logs no mesmo formato)

**Desvantagens:**
- ❌ **Código adicional** necessário (boilerplate)
- ❌ **Complexidade alta** - APIs baixo nível
- ❌ **Falta de métricas nativas** do HikariCP/Kafka (precisa instrumentar manualmente)
- ❌ **Manutenção complexa** - atualizar sempre que libs mudarem
- ❌ **Documentação limitada** para Java

**Esforço:** 🔴 ALTO (2-3 dias)

**Exemplo de código necessário:**
```java
@Configuration
public class MetricsConfig {
    @Bean
    public MeterRegistry meterRegistry(OpenTelemetry openTelemetry) {
        return OpenTelemetryMeterRegistry.builder(openTelemetry).build();
    }

    @Bean
    public HikariConfigMXBean hikariMetrics(HikariDataSource dataSource) {
        // Instrumentação manual do HikariCP
        // ... muitas linhas de código
    }
}
```

---

### Opção 3: Exporters Externos (Kafka Exporter + JMX Exporter)

**Descrição:**
- Deploy de exporters standalone:
  - **Kafka Exporter** para métricas Kafka
  - **JMX Exporter** (sidecar) para HikariCP via JMX

**Vantagens:**
- ✅ Desacoplado das aplicações
- ✅ Não impacta performance dos microserviços

**Desvantagens:**
- ❌ **Infraestrutura adicional** (mais containers)
- ❌ **Complexidade operacional** (mais componentes para gerenciar)
- ❌ **JMX Exporter** requer sidecar em cada microserviço
- ❌ **Kafka Exporter** requer acesso ao cluster Kafka
- ❌ **Não resolve métricas de Hibernate**

**Esforço:** 🟡 MÉDIO (1 dia)

---

## Decisão

**ESCOLHIDA: Opção 1 - Spring Boot Actuator + Micrometer**

### Justificativa

1. **Pragmatismo**: Actuator já está configurado, só precisa habilitar métricas
2. **Padrão da Indústria**: 90% dos projetos Spring Boot usam Actuator + Prometheus
3. **Métricas Nativas**: HikariCP, Kafka, Hibernate já expõem métricas via Micrometer
4. **Baixo Risco**: Solução testada e estável
5. **Facilidade de Manutenção**: Documentação extensa, comunidade grande
6. **Convivência com OpenTelemetry**:
   - OpenTelemetry → Traces + Logs (força dele)
   - Micrometer → Metrics (força dele)
   - Ambos funcionam perfeitamente juntos

### Arquitetura Final

```
┌─────────────────────┐
│   ms-producer       │
│   (localhost:5050)  │
├─────────────────────┤
│ OpenTelemetry Agent │ ──→ Traces/Logs ──→ OTLP Collector ──→ Tempo/Loki
│ Spring Actuator     │ ──→ Metrics     ──→ Prometheus (scrape)
└─────────────────────┘
                            ↓
                    ┌───────────────┐
                    │  Grafana      │
                    │  Dashboard    │
                    └───────────────┘

┌─────────────────────┐
│   ms-consumer       │
│   (localhost:5051)  │
├─────────────────────┤
│ OpenTelemetry Agent │ ──→ Traces/Logs ──→ OTLP Collector ──→ Tempo/Loki
│ Spring Actuator     │ ──→ Metrics     ──→ Prometheus (scrape)
└─────────────────────┘
```

### Fluxo de Telemetria

| Tipo      | Origem           | Destino         | Via              |
|-----------|------------------|-----------------|------------------|
| Traces    | OpenTelemetry    | Tempo           | OTLP Collector   |
| Logs      | OpenTelemetry    | Loki            | OTLP Collector   |
| Metrics   | Micrometer       | Prometheus      | HTTP Scrape      |

## Consequências

### Positivas ✅

1. **Dashboards Funcionais**:
   - HikariCP Connection Pool (ativo/idle/pending)
   - Kafka Consumer Lag
   - Kafka Throughput
   - SQL Query Performance
   - Error rates

2. **Visibilidade Completa**:
   - Traces → Debug distribuído
   - Logs → Troubleshooting
   - Metrics → Health/Performance

3. **Zero Impacto de Performance**:
   - Métricas já estão sendo coletadas (HikariCP, Kafka libs)
   - Actuator apenas expõe via HTTP

4. **Facilidade de Manutenção**:
   - Configuração declarativa (YAML)
   - Sem código customizado

### Negativas / Trade-offs ⚠️

1. **Duas Fontes de Métricas**:
   - OpenTelemetry: JVM básico + HTTP
   - Micrometer: HikariCP + Kafka + Hibernate
   - **Mitigação**: Aceitável, cada ferramenta no seu uso ideal

2. **Scrape Adicional**:
   - Prometheus precisa scrape em 2 endpoints extras
   - **Mitigação**: Configuração trivial, overhead mínimo

3. **Consistência de Labels**:
   - OpenTelemetry usa `service.name`
   - Micrometer usa `application` tag
   - **Mitigação**: Configurar tags consistentes via `management.metrics.tags`

## Implementação

### Checklist

- [ ] Ajustar `application.yaml` nos dois microserviços
- [ ] Atualizar `prometheus.yaml` (scrape configs)
- [ ] Reiniciar Prometheus
- [ ] Reiniciar microserviços
- [ ] Validar métricas no Prometheus
- [ ] Validar dashboards no Grafana
- [ ] Documentar métricas disponíveis

### Validação

**Métricas esperadas no Prometheus:**
```promql
# HikariCP
hikaricp_connections_active{application="ms-producer"}
hikaricp_connections_idle{application="ms-producer"}

# Kafka Consumer
kafka_consumer_fetch_manager_records_lag_max{application="ms-consumer"}
kafka_consumer_coordinator_commit_latency_avg_ms{application="ms-consumer"}

# Hibernate
hibernate_statements{application="ms-producer",entity="all"}
```

**Dashboards a validar:**
1. Database & Hibernate Performance
   - HikariCP Connections
   - Connection Acquire Time
   - SQL Queries (traces já funcionam)

2. Microservices E2E Observability
   - Kafka Consumer Lag
   - Kafka Throughput
   - Error Count

## Referências

- [Spring Boot Actuator - Production Ready Features](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer - Application Metrics](https://micrometer.io/docs)
- [Prometheus - Spring Boot Integration](https://prometheus.io/docs/instrumenting/exporters/#software-exposing-prometheus-metrics)
- [OpenTelemetry Java - Metrics](https://opentelemetry.io/docs/instrumentation/java/manual/#metrics)
- [HikariCP - Metrics](https://github.com/brettwooldridge/HikariCP/wiki/Dropwizard-Metrics)

## Histórico

| Data       | Autor          | Alteração                    |
|------------|----------------|------------------------------|
| 2025-12-11 | Claude Sonnet  | Criação inicial do ADR       |

---

**Nota**: Este ADR pode ser revisado se surgirem novos requisitos ou limitações técnicas durante a implementação.
