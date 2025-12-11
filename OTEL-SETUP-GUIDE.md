# Guia Completo de Observabilidade - OpenTelemetry + Grafana

## ✅ Correções Aplicadas

### 1. Prometheus
- ✅ Corrigidas permissões do arquivo `prometheus-alerts.yaml`
- ✅ Container reiniciado e funcionando

### 2. Tempo
- ✅ Configurado para escutar em todas as interfaces (`0.0.0.0:4317`)
- ✅ Container reiniciado e aceitando conexões

### 3. Microserviços (Producer e Consumer)
- ✅ Adicionada dependência `spring-boot-starter-actuator`
- ✅ Configurações OpenTelemetry completas:
  - Exportador OTLP configurado (HTTP na porta 4318)
  - Métricas habilitadas (intervalo de 10s)
  - Traces habilitadas (100% sampling)
  - Logs habilitados
  - **Instrumentações explicitamente habilitadas**:
    - spring-web
    - spring-webmvc
    - kafka
    - jdbc

### 4. Coletor OpenTelemetry
- ✅ Configurado corretamente para receber OTLP e enviar para:
  - Traces → Tempo (gRPC porta 4317)
  - Métricas → Prometheus (HTTP porta 9464)
  - Logs → Loki (HTTP porta 3100)

### 5. Grafana
- ✅ Datasources configurados:
  - Prometheus (métricas)
  - Tempo (traces)
  - Loki (logs)

---

## 🚀 Passo a Passo para Iniciar

### Opção 1: Usando o Script Automático

```bash
cd /home/wolf/Documentos/desenvolvimento/freestyle/spring-kafka-intermediate
./start-and-diagnose.sh
```

O script vai:
1. Verificar todos os containers da infraestrutura
2. Parar aplicações Java em execução
3. Instruir como iniciar as aplicações com logs detalhados
4. Fazer requisições de teste
5. Verificar se OpenTelemetry está funcionando

### Opção 2: Inicialização Manual

#### Terminal 1 - ms-producer
```bash
cd ms-producer
./mvnw spring-boot:run
```

#### Terminal 2 - ms-consumer
```bash
cd ms-consumer
./mvnw spring-boot:run
```

**IMPORTANTE**: Aguarde até ver a mensagem:
```
OpenTelemetry Spring Boot starter (2.20.1) has been started
Started ProducerApplication in X seconds
```

---

## 🧪 Testes

### 1. Verificar se aplicações estão UP
```bash
curl http://localhost:5050/actuator/health  # Producer
curl http://localhost:5051/actuator/health  # Consumer
```

### 2. Enviar requisições de teste
```bash
# Enviar 5 pagamentos
for i in {1..5}; do
  curl -X POST http://localhost:5050/api/payments/approved \
    -H "Content-Type: application/json" \
    -d "{\"paymentId\":\"pay-test-$RANDOM\",\"userId\":\"user-$i\",\"amount\":$((50 + $i * 10)).99,\"currency\":\"BRL\"}"
  echo ""
  sleep 1
done
```

### 3. Verificar Métricas (devem aparecer em ~10-20 segundos)
```bash
# Métricas do Producer
curl -s http://localhost:5050/actuator/metrics | jq '.names[] | select(contains("http"))'

# Verificar no Prometheus
curl -s "http://localhost:9090/api/v1/query?query=http_server_requests_seconds_count" | jq .
```

### 4. Verificar Traces

#### Via Grafana (Recomendado)
1. Abra http://localhost:3000 (admin/admin)
2. Vá em **Explore**
3. Selecione datasource **Tempo**
4. Clique em **Search**
5. Filtros sugeridos:
   - Service Name: `ms-producer` ou `ms-consumer`
   - Span Name: `GET /api/payments/approved`
   - Duration: > 0ms

#### Via API do Tempo
```bash
# Listar traces recentes
curl -s "http://localhost:3200/api/search?tags=service.name%3Dms-producer&limit=10" | jq .
```

---

## 🔍 Diagnóstico de Problemas

### Problema: Métricas não aparecem

**Checklist:**
1. Aplicação iniciou com sucesso?
   ```bash
   jps -l | grep -E "ProducerApplication|ConsumerApplication"
   ```

2. Prometheus está coletando?
   ```bash
   curl -s http://localhost:9090/api/v1/targets | jq -r '.data.activeTargets[] | "\(.job): \(.health)"'
   ```

3. Métricas estão sendo exportadas?
   ```bash
   curl -s http://localhost:9464/metrics | grep http_server
   ```

4. Logs da aplicação mostram erro OTEL?
   ```bash
   grep -i "opentelemetry\|error" logs/application.log
   ```

### Problema: Traces não aparecem

**Checklist:**
1. Coletor OTEL está recebendo traces?
   ```bash
   docker logs otel-collector --tail 50 | grep -i "trace\|span"
   ```

2. Tempo está rodando e recebendo?
   ```bash
   docker logs tempo --tail 30 | grep -i "trace\|span"
   docker exec tempo netstat -tln | grep 4317
   ```

3. Aplicação está gerando spans?
   - Procure por "span" nos logs da aplicação
   - Verifique se há requisições HTTP sendo feitas

4. Conexão entre coletor e Tempo está OK?
   ```bash
   docker logs otel-collector 2>&1 | grep -i "tempo.*error\|tempo.*failed"
   ```

### Problema: Consumer não exporta métricas

O consumer tem servidor web habilitado?
```bash
curl http://localhost:5051/actuator/health
```

Se retornar 404 ou erro de conexão, o consumer não tem servidor web ativo. Verifique se a dependência `spring-boot-starter-web` está no pom.xml.

---

## 📊 Dashboards no Grafana

### Métricas Disponíveis

#### JVM
- `jvm_memory_used_bytes` - Memória usada
- `jvm_gc_pause_seconds_count` - Contagem de GC
- `jvm_threads_live_threads` - Threads ativas

#### HTTP
- `http_server_requests_seconds_count` - Total de requests
- `http_server_requests_seconds_sum` - Tempo total de requests
- `http_server_requests_seconds_max` - Request mais lento

#### Kafka
- `kafka_producer_*` - Métricas do producer
- `kafka_consumer_*` - Métricas do consumer

#### Database
- `hikaricp_connections_active` - Conexões ativas
- `hikaricp_connections_pending` - Conexões pendentes

### Traces Disponíveis

- **HTTP Requests**: Cada request HTTP gera um trace
- **Kafka Messages**: Produção e consumo de mensagens
- **Database Queries**: Queries SQL
- **Métodos anotados**: Com `@WithSpan` ou `@Observed`

---

## 🔧 Configurações Importantes

### Intervalo de Exportação
- **Atual**: 10 segundos
- **Localização**: `application.yaml` → `otel.metrics.export.interval`
- **Recomendação**:
  - Dev: 10s (rápido para testes)
  - Prod: 60s (reduz overhead)

### Sampling de Traces
- **Atual**: 100% (todos os traces são capturados)
- **Localização**: `application.yaml` → `otel.traces.sampler.probability`
- **Recomendação**:
  - Dev: 1.0 (100%)
  - Prod: 0.1 (10%) ou menos

### Retenção de Dados

#### Tempo
- **Configuração**: `observability-infrastructure/docker-volume/tempo.yaml`
- **Retenção atual**: 1 hora
- **Localização**: `compactor.compaction.block_retention`

#### Prometheus
- **Retenção padrão**: 15 dias
- **Localização**: Volume Docker

---

## 📝 Logs Úteis

```bash
# Ver logs em tempo real
tail -f /tmp/producer.log  # Se iniciou com script
tail -f /tmp/consumer.log

# Ver apenas erros OTEL
grep -i "otel.*error" /tmp/producer.log

# Ver traces sendo exportados
grep -i "trace.*export\|exporting.*span" /tmp/producer.log

# Ver métricas sendo exportadas
grep -i "metric.*export" /tmp/producer.log
```

---

## ✅ Checklist Final

- [ ] Todos os containers estão UP (otel-collector, tempo, prometheus, loki, grafana)
- [ ] Aplicações iniciaram sem erros
- [ ] Mensagem "OpenTelemetry...started" aparece nos logs
- [ ] Endpoint /actuator/health retorna UP
- [ ] Métricas aparecem no Prometheus após ~20 segundos
- [ ] Traces aparecem no Tempo/Grafana após fazer requests
- [ ] Consumer consome mensagens do Kafka (verificar logs)

---

## 🆘 Ainda não funciona?

1. **Envie os logs**:
   ```bash
   # Coletar todos os logs relevantes
   docker logs otel-collector > /tmp/otel.log 2>&1
   docker logs tempo > /tmp/tempo.log 2>&1
   docker logs prometheus > /tmp/prometheus.log 2>&1
   grep -i "opentelemetry" /tmp/producer.log > /tmp/producer-otel.log
   ```

2. **Verificar versões**:
   ```bash
   java --version
   docker --version
   curl --version
   ```

3. **Reiniciar infraestrutura completa**:
   ```bash
   cd observability-infrastructure
   docker compose down
   docker compose up -d
   sleep 10
   docker ps  # Verificar se todos subiram
   ```

4. **Limpar e recompilar**:
   ```bash
   cd ms-producer
   ./mvnw clean package -DskipTests
   cd ../ms-consumer
   ./mvnw clean package -DskipTests
   ```
