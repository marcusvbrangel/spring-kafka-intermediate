#!/bin/bash

echo "================================"
echo "OTEL Diagnostic and Startup Script"
echo "================================"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check infrastructure
echo -e "${YELLOW}[1/8] Checking infrastructure containers...${NC}"
CONTAINERS_STATUS=$(docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "otel-collector|tempo|prometheus|loki|grafana")
echo "$CONTAINERS_STATUS"
echo ""

# Check OTEL Collector
echo -e "${YELLOW}[2/8] Checking OTEL Collector health...${NC}"
OTEL_STATUS=$(docker ps --filter "name=otel-collector" --format "{{.Status}}")
if [[ $OTEL_STATUS == *"Up"* ]]; then
    echo -e "${GREEN}✓ OTEL Collector is running${NC}"
else
    echo -e "${RED}✗ OTEL Collector is NOT running${NC}"
fi
echo ""

# Check Tempo
echo -e "${YELLOW}[3/8] Checking Tempo health...${NC}"
TEMPO_STATUS=$(docker ps --filter "name=tempo" --format "{{.Status}}")
if [[ $TEMPO_STATUS == *"Up"* ]]; then
    echo -e "${GREEN}✓ Tempo is running${NC}"
    # Check if Tempo is listening on 4317
    TEMPO_PORT=$(docker exec tempo netstat -tln 2>/dev/null | grep 4317)
    if [[ -n "$TEMPO_PORT" ]]; then
        echo -e "${GREEN}✓ Tempo is listening on port 4317${NC}"
    else
        echo -e "${RED}✗ Tempo is NOT listening on port 4317${NC}"
    fi
else
    echo -e "${RED}✗ Tempo is NOT running${NC}"
fi
echo ""

# Check Prometheus
echo -e "${YELLOW}[4/8] Checking Prometheus health...${NC}"
PROM_STATUS=$(docker ps --filter "name=prometheus" --format "{{.Status}}")
if [[ $PROM_STATUS == *"Up"* ]]; then
    echo -e "${GREEN}✓ Prometheus is running${NC}"
else
    echo -e "${RED}✗ Prometheus is NOT running${NC}"
fi
echo ""

# Check PostgreSQL
echo -e "${YELLOW}[5/8] Checking PostgreSQL databases...${NC}"
DB_CHECK=$(docker exec postgres psql -U postgres -c "\l" 2>/dev/null | grep -E "ms-producer|ms-consumer")
if [[ -n "$DB_CHECK" ]]; then
    echo -e "${GREEN}✓ Databases ms-producer and ms-consumer exist${NC}"
else
    echo -e "${RED}✗ Databases NOT found${NC}"
fi
echo ""

# Kill existing Java processes
echo -e "${YELLOW}[6/8] Stopping existing applications...${NC}"
JAVA_PIDS=$(jps -l | grep -E "ProducerApplication|ConsumerApplication" | awk '{print $1}')
if [[ -n "$JAVA_PIDS" ]]; then
    echo "Killing PIDs: $JAVA_PIDS"
    echo "$JAVA_PIDS" | xargs kill -9 2>/dev/null
    sleep 2
    echo -e "${GREEN}✓ Stopped existing applications${NC}"
else
    echo "No running applications found"
fi
echo ""

# Start applications
echo -e "${YELLOW}[7/8] Starting applications...${NC}"
echo ""
echo "Open TWO separate terminals and run:"
echo ""
echo -e "${GREEN}Terminal 1 (ms-producer):${NC}"
echo "cd /home/wolf/Documentos/desenvolvimento/freestyle/spring-kafka-intermediate/ms-producer"
echo "OTEL_LOGS_EXPORTER=logging OTEL_METRICS_EXPORTER=logging,otlp OTEL_TRACES_EXPORTER=logging,otlp ./mvnw spring-boot:run | tee /tmp/producer.log"
echo ""
echo -e "${GREEN}Terminal 2 (ms-consumer):${NC}"
echo "cd /home/wolf/Documentos/desenvolvimento/freestyle/spring-kafka-intermediate/ms-consumer"
echo "OTEL_LOGS_EXPORTER=logging OTEL_METRICS_EXPORTER=logging,otlp OTEL_TRACES_EXPORTER=logging,otlp ./mvnw spring-boot:run | tee /tmp/consumer.log"
echo ""
echo "Wait for applications to start (look for 'Started ProducerApplication' and 'Started ConsumerApplication')"
echo ""

# Wait for user
read -p "Press ENTER after both applications have started..."

# Check if apps are running
echo -e "${YELLOW}[8/8] Verifying applications...${NC}"
PRODUCER_PID=$(jps -l | grep ProducerApplication | awk '{print $1}')
CONSUMER_PID=$(jps -l | grep ConsumerApplication | awk '{print $1}')

if [[ -n "$PRODUCER_PID" ]]; then
    echo -e "${GREEN}✓ Producer is running (PID: $PRODUCER_PID)${NC}"
else
    echo -e "${RED}✗ Producer is NOT running${NC}"
fi

if [[ -n "$CONSUMER_PID" ]]; then
    echo -e "${GREEN}✓ Consumer is running (PID: $CONSUMER_PID)${NC}"
else
    echo -e "${RED}✗ Consumer is NOT running${NC}"
fi
echo ""

# Test endpoints
echo -e "${YELLOW}Testing application endpoints...${NC}"
PRODUCER_HEALTH=$(curl -s http://localhost:5050/actuator/health 2>&1)
if [[ $PRODUCER_HEALTH == *"UP"* ]]; then
    echo -e "${GREEN}✓ Producer health endpoint is UP${NC}"
else
    echo -e "${RED}✗ Producer health endpoint failed: $PRODUCER_HEALTH${NC}"
fi

CONSUMER_HEALTH=$(curl -s http://localhost:5051/actuator/health 2>&1)
if [[ $CONSUMER_HEALTH == *"UP"* ]]; then
    echo -e "${GREEN}✓ Consumer health endpoint is UP${NC}"
else
    echo -e "${RED}✗ Consumer health endpoint failed: $CONSUMER_HEALTH${NC}"
fi
echo ""

# Make test requests
echo -e "${YELLOW}Making test requests...${NC}"
echo "Sending 5 payment requests..."
for i in {1..5}; do
    RESPONSE=$(curl -s -X POST http://localhost:5050/api/payments/approved \
        -H "Content-Type: application/json" \
        -d "{\"paymentId\":\"pay-test-$RANDOM\",\"userId\":\"user-123\",\"amount\":99.99,\"currency\":\"BRL\"}" 2>&1)
    if [[ $RESPONSE == *"error"* ]] || [[ $RESPONSE == *"Failed"* ]]; then
        echo -e "${RED}✗ Request $i failed: $RESPONSE${NC}"
    else
        echo -e "${GREEN}✓ Request $i sent successfully${NC}"
    fi
    sleep 1
done
echo ""

# Check logs for OTEL
echo -e "${YELLOW}Checking for OpenTelemetry initialization...${NC}"
if [[ -f /tmp/producer.log ]]; then
    OTEL_INIT=$(grep -i "opentelemetry.*started" /tmp/producer.log | tail -1)
    if [[ -n "$OTEL_INIT" ]]; then
        echo -e "${GREEN}✓ Producer: $OTEL_INIT${NC}"
    else
        echo -e "${RED}✗ Producer: OpenTelemetry initialization message not found${NC}"
    fi
fi

if [[ -f /tmp/consumer.log ]]; then
    OTEL_INIT=$(grep -i "opentelemetry.*started" /tmp/consumer.log | tail -1)
    if [[ -n "$OTEL_INIT" ]]; then
        echo -e "${GREEN}✓ Consumer: $OTEL_INIT${NC}"
    else
        echo -e "${RED}✗ Consumer: OpenTelemetry initialization message not found${NC}"
    fi
fi
echo ""

# Check for traces in logs
echo -e "${YELLOW}Checking for trace exports in logs...${NC}"
echo "Waiting 15 seconds for exports..."
sleep 15

if [[ -f /tmp/producer.log ]]; then
    TRACE_EXPORT=$(grep -i "trace.*export\|span.*export" /tmp/producer.log | tail -3)
    if [[ -n "$TRACE_EXPORT" ]]; then
        echo -e "${GREEN}✓ Producer is exporting traces:${NC}"
        echo "$TRACE_EXPORT"
    else
        echo -e "${YELLOW}⚠ Producer: No trace exports found in logs yet${NC}"
    fi
fi
echo ""

# Final recommendations
echo "================================"
echo -e "${YELLOW}Next Steps:${NC}"
echo "================================"
echo "1. Check Grafana: http://localhost:3000 (admin/admin)"
echo "2. Go to Explore → Select 'Tempo' datasource"
echo "3. Run query to search for traces"
echo "4. Check metrics: Select 'Prometheus' datasource and search for 'http_server_requests'"
echo ""
echo "If traces still don't appear:"
echo "1. Check /tmp/producer.log for OTEL trace exports"
echo "2. Check /tmp/consumer.log for OTEL trace exports"
echo "3. Run: docker logs otel-collector --tail 50 | grep -i trace"
echo "4. Run: docker logs tempo --tail 50"
echo ""
