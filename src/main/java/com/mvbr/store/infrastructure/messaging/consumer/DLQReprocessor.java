package com.mvbr.store.infrastructure.messaging.consumer;

import com.mvbr.store.infrastructure.messaging.event.PaymentApprovedEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * =============================================================================================
 * DLQ REPROCESSOR - Consumidor dedicado para reprocessar mensagens da Dead Letter Queue
 * =============================================================================================
 *
 * OBJETIVO:
 * - Monitorar a DLQ (payment.approved.v1.dlq)
 * - Republicar mensagens de volta ao tópico original após correção de bugs
 * - Fornecer visibilidade e controle sobre mensagens que falharam
 *
 * QUANDO USAR:
 * - ✅ Após corrigir um bug no PaymentApprovedConsumer e fazer deploy
 * - ✅ Após serviço externo voltar a funcionar (ex: API de notificação)
 * - ✅ Para reprocessar mensagens que falharam por erro temporário
 *
 * QUANDO NÃO USAR (PERIGO!):
 * - ❌ Enquanto o bug que causou a falha ainda existe (cria loop infinito!)
 * - ❌ Para mensagens com dados inválidos que precisam ser corrigidos manualmente
 *
 * CONTROLE:
 * - Use a propriedade 'dlq.reprocessor.enabled' no application.properties
 * - Por padrão está DESABILITADO (false) para evitar loops acidentais
 *
 * =============================================================================================
 */
@Service
public class DLQReprocessor {

    // =============================================================================================
    // DEPENDÊNCIAS
    // =============================================================================================

    /**
     * KafkaTemplate CRITICAL para republicar mensagens
     * - Usa acks=all (máxima durabilidade)
     * - Idempotente (evita duplicatas)
     * - Retry automático em caso de falha
     */
    private final KafkaTemplate<String, Object> criticalKafkaTemplate;

    /**
     * Flag para habilitar/desabilitar o reprocessamento
     * - Controlado via application.properties: dlq.reprocessor.enabled=true/false
     * - DEFAULT: false (segurança - evita loops acidentais)
     */
    @Value("${dlq.reprocessor.enabled:false}")
    private boolean reprocessorEnabled;

    /**
     * Construtor com injeção de dependência
     *
     * @Qualifier("criticalKafkaTemplate") - Garante que usamos o template CRITICAL
     *                                       (mesmo nível de confiabilidade do producer original)
     */
    public DLQReprocessor(@Qualifier("criticalKafkaTemplate") KafkaTemplate<String, Object> criticalKafkaTemplate) {
        this.criticalKafkaTemplate = criticalKafkaTemplate;
    }

    // =============================================================================================
    // CONSUMER DA DLQ
    // =============================================================================================

    /**
     * Listener que monitora a DLQ e reprocessa mensagens
     *
     * CONFIGURAÇÕES:
     * - topics: "payment.approved.v1.dlq" - tópico da Dead Letter Queue
     * - groupId: "dlq-reprocessing-group" - grupo DIFERENTE do consumer original
     * - containerFactory: "defaultKafkaListenerContainerFactory" - Usa auto-commit (mais simples)
     * - autoStartup: "${dlq.reprocessor.enabled:false}" - Só inicia se enabled=true
     *
     * PARÂMETROS:
     * @Payload - O evento que falhou (deserializado automaticamente)
     * @Header - Headers adicionados automaticamente pelo DeadLetterPublishingRecoverer:
     *   - kafka_dlt-original-topic: Tópico de onde a mensagem veio
     *   - kafka_dlt-original-partition: Partição original
     *   - kafka_dlt-original-offset: Offset original
     *   - kafka_dlt-exception-fqcn: Nome completo da exceção (ex: java.lang.NullPointerException)
     *   - kafka_dlt-exception-message: Mensagem de erro
     *   - kafka_dlt-exception-stacktrace: Stack trace completo
     *   - kafka_receivedTopic: Tópico DLQ atual
     *   - kafka_receivedPartition: Partição DLQ
     *   - kafka_offset: Offset na DLQ
     *
     * IMPORTANTE:
     * - Este método SÓ EXECUTA se dlq.reprocessor.enabled=true
     * - Caso contrário, o listener nem inicia (@KafkaListener autoStartup)
     */
    @KafkaListener(
            topics = "payment.approved.v1.dlq",
            groupId = "dlq-reprocessing-group",
            containerFactory = "defaultKafkaListenerContainerFactory",
            autoStartup = "${dlq.reprocessor.enabled:false}"  // CRÍTICO: Só inicia se habilitado!
    )
    public void consumeFromDLQ(
            @Payload PaymentApprovedEvent event,

            // Headers do Spring Kafka (adicionados automaticamente na DLQ)
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String dlqTopic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer dlqPartition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long dlqOffset,

            // Headers customizados pelo DeadLetterPublishingRecoverer
            @Header(value = "kafka_dlt-original-topic", required = false) String originalTopic,
            @Header(value = "kafka_dlt-original-partition", required = false) Integer originalPartition,
            @Header(value = "kafka_dlt-original-offset", required = false) Long originalOffset,
            @Header(value = "kafka_dlt-exception-message", required = false) String exceptionMessage,
            @Header(value = "kafka_dlt-exception-fqcn", required = false) String exceptionClass
    ) {

        // =============================================================================================
        // PASSO 1: LOG DE CONTEXTO - Entender o que estamos reprocessando
        // =============================================================================================

        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    DLQ REPROCESSING STARTED                                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════╝");

        System.out.println("\n📍 DLQ LOCATION:");
        System.out.println("   Topic:     " + dlqTopic);
        System.out.println("   Partition: " + dlqPartition);
        System.out.println("   Offset:    " + dlqOffset);

        System.out.println("\n📜 ORIGINAL MESSAGE:");
        System.out.println("   Topic:     " + originalTopic);
        System.out.println("   Partition: " + originalPartition);
        System.out.println("   Offset:    " + originalOffset);

        System.out.println("\n❌ ERROR DETAILS:");
        System.out.println("   Exception: " + exceptionClass);
        System.out.println("   Message:   " + exceptionMessage);

        System.out.println("\n💳 PAYMENT EVENT:");
        System.out.println("   EventId:   " + event.eventId());
        System.out.println("   PaymentId: " + event.paymentId());
        System.out.println("   UserId:    " + event.userId());
        System.out.println("   Amount:    " + event.amount());
        System.out.println("   Status:    " + event.status());

        // =============================================================================================
        // PASSO 2: VALIDAÇÃO - Verificar se podemos reprocessar
        // =============================================================================================

        // Verificação extra: mesmo com autoStartup, validamos a flag
        if (!reprocessorEnabled) {
            System.err.println("\n⚠️  REPROCESSOR DISABLED - Message will NOT be republished");
            System.err.println("   To enable: set dlq.reprocessor.enabled=true in application.properties");
            return;
        }

        // Validação: Evento não pode ser nulo
        if (event == null) {
            System.err.println("\n❌ ERROR: Received null event from DLQ - skipping");
            return;  // Auto-commit vai consumir essa mensagem da DLQ
        }

        // Validação: Tópico original deve existir
        if (originalTopic == null || originalTopic.isBlank()) {
            System.err.println("\n❌ ERROR: Original topic not found in headers - cannot republish");
            return;
        }

        // =============================================================================================
        // PASSO 3: DECISÃO DE REPROCESSAMENTO
        // =============================================================================================

        /*
         * ESTRATÉGIAS POSSÍVEIS (escolha uma):
         *
         * ESTRATÉGIA A: Republicar SEMPRE (atual - mais simples)
         * - Assume que você corrigiu o bug antes de habilitar o reprocessor
         * - Risco: Se o bug ainda existe, cria loop infinito!
         *
         * ESTRATÉGIA B: Republicar apenas certos tipos de erro
         * - Ex: Apenas TimeoutException, SocketException (erros temporários)
         * - NullPointerException, ValidationException → Não republicar (bug no código)
         *
         * ESTRATÉGIA C: Republicar com aprovação manual
         * - Verificar em banco de dados se paymentId foi aprovado para reprocessamento
         * - Ops team marca manualmente via dashboard/API
         *
         * ESTRATÉGIA D: Republicar com transformação
         * - Aplicar correção nos dados antes de republicar
         * - Ex: Converter schema antigo para novo
         */

        // 🔹 ESTRATÉGIA A (ATUAL): Republicar sempre
        republishToOriginalTopic(event, originalTopic);

        // 🔹 ESTRATÉGIA B (EXEMPLO COMENTADO): Republicar apenas erros temporários
        // if (isTemporaryError(exceptionClass)) {
        //     System.out.println("\n✅ Temporary error detected - safe to republish");
        //     republishToOriginalTopic(event, originalTopic);
        // } else {
        //     System.err.println("\n⛔ Permanent error detected - requires manual intervention");
        //     sendToManualReview(event, exceptionMessage);
        // }

        // 🔹 ESTRATÉGIA C (EXEMPLO COMENTADO): Republicar com aprovação manual
        // if (isApprovedForReprocessing(event.paymentId())) {
        //     System.out.println("\n✅ Payment approved for reprocessing by ops team");
        //     republishToOriginalTopic(event, originalTopic);
        // } else {
        //     System.out.println("\n⏸️  Waiting for manual approval before reprocessing");
        // }
    }

    // =============================================================================================
    // MÉTODO AUXILIAR: REPUBLICAR PARA TÓPICO ORIGINAL
    // =============================================================================================

    /**
     * Republica o evento de volta ao tópico original
     *
     * DETALHES:
     * - Usa criticalKafkaTemplate (acks=all, idempotente)
     * - Particiona por userId (mesmo comportamento do producer original)
     * - Callback assíncrono para confirmar sucesso/falha
     *
     * @param event - Evento a ser republicado
     * @param originalTopic - Tópico de destino (ex: payment.approved.v1)
     */
    private void republishToOriginalTopic(PaymentApprovedEvent event, String originalTopic) {

        System.out.println("\n🔄 REPUBLISHING...");
        System.out.println("   Destination: " + originalTopic);
        System.out.println("   Partition Key: " + event.userId() + " (same as original)");

        try {
            // Envia mensagem de forma assíncrona
            // - Key: userId (garante que vai para mesma partição - ordenação preservada)
            // - Value: event (o evento completo)
            // - whenComplete: callback executado quando operação termina
            criticalKafkaTemplate.send(originalTopic, event.userId(), event)
                .whenComplete((result, exception) -> {

                    if (exception == null) {
                        // ✅ SUCESSO
                        System.out.println("\n╔════════════════════════════════════════════════════════════════════════╗");
                        System.out.println("║                  ✅ REPROCESSING SUCCESSFUL                             ║");
                        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");
                        System.out.println("   PaymentId: " + event.paymentId());
                        System.out.println("   Topic:     " + result.getRecordMetadata().topic());
                        System.out.println("   Partition: " + result.getRecordMetadata().partition());
                        System.out.println("   Offset:    " + result.getRecordMetadata().offset());
                        System.out.println("   Timestamp: " + result.getRecordMetadata().timestamp());
                        System.out.println("\n➡️  Message will be consumed again by PaymentApprovedConsumer\n");

                    } else {
                        // ❌ FALHA
                        System.err.println("\n╔════════════════════════════════════════════════════════════════════════╗");
                        System.err.println("║                  ❌ REPROCESSING FAILED                                 ║");
                        System.err.println("╚════════════════════════════════════════════════════════════════════════╝");
                        System.err.println("   PaymentId: " + event.paymentId());
                        System.err.println("   Error:     " + exception.getMessage());
                        System.err.println("\n⚠️  Message remains in DLQ - manual intervention required\n");

                        // IMPORTANTE: Lançar exceção aqui faria o DLQ reprocessor tentar novamente
                        // Como estamos usando auto-commit, a mensagem JÁ FOI commitada da DLQ
                        // Se quiser retry, use containerFactory com manual commit no DLQReprocessor
                    }
                });

        } catch (Exception e) {
            // Exceção síncrona (raro - geralmente é assíncrono)
            System.err.println("\n❌ SYNC ERROR while republishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =============================================================================================
    // MÉTODOS AUXILIARES OPCIONAIS (EXEMPLOS COMENTADOS)
    // =============================================================================================

    /**
     * ESTRATÉGIA B: Verifica se o erro é temporário (rede, timeout, etc.)
     *
     * Erros temporários são seguros para republicar automaticamente.
     * Erros permanentes (bugs) precisam correção no código primeiro.
     */
    @SuppressWarnings("unused")
    private boolean isTemporaryError(String exceptionClass) {
        if (exceptionClass == null) return false;

        // Lista de exceções consideradas "temporárias"
        return exceptionClass.contains("TimeoutException") ||
               exceptionClass.contains("SocketException") ||
               exceptionClass.contains("ConnectException") ||
               exceptionClass.contains("HttpServerErrorException");  // 5xx errors

        // Exceções "permanentes" (bugs no código):
        // - NullPointerException
        // - IllegalArgumentException
        // - JsonProcessingException
        // - ValidationException
        // Estas NÃO devem ser republicadas sem correção no código!
    }

    /**
     * ESTRATÉGIA C: Verifica se pagamento foi aprovado para reprocessamento
     *
     * Exemplo: Ops team marca pagamentos via dashboard/API
     * Consulta banco de dados para verificar flag
     */
    @SuppressWarnings("unused")
    private boolean isApprovedForReprocessing(String paymentId) {
        // EXEMPLO FICTÍCIO - você implementaria consultando banco de dados:
        // return reprocessingRepository.isApproved(paymentId);

        // Por enquanto, retorna false (precisa implementação real)
        return false;
    }

    /**
     * ESTRATÉGIA B/C: Envia mensagem para revisão manual
     *
     * Opções:
     * - Salvar em banco de dados para dashboard de ops
     * - Enviar email/Slack para equipe
     * - Criar ticket no Jira automaticamente
     */
    @SuppressWarnings("unused")
    private void sendToManualReview(PaymentApprovedEvent event, String errorReason) {
        System.out.println("\n📧 SENDING TO MANUAL REVIEW");
        System.out.println("   PaymentId: " + event.paymentId());
        System.out.println("   Reason: " + errorReason);

        // TODO: Implementar notificação real
        // - Salvar em tabela manual_review_queue
        // - Enviar email para ops@company.com
        // - Criar alerta no monitoring dashboard
    }

    // =============================================================================================
    // MÉTODOS UTILITÁRIOS
    // =============================================================================================

    /**
     * Método para verificar status do reprocessor
     * Útil para endpoints de health check ou admin
     */
    public boolean isEnabled() {
        return reprocessorEnabled;
    }

    /**
     * Getter para injeção em testes
     */
    public KafkaTemplate<String, Object> getKafkaTemplate() {
        return criticalKafkaTemplate;
    }
}