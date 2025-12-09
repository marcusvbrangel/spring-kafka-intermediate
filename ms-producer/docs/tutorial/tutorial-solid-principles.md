# Tutorial Definitivo: SOLID Principles - Os 5 Pilares do Design Orientado a Objetos

## 📋 Sumário

1. [O que é SOLID](#1-o-que-é-solid)
2. [S - Single Responsibility Principle](#2-s---single-responsibility-principle-srp)
3. [O - Open/Closed Principle](#3-o---openclosed-principle-ocp)
4. [L - Liskov Substitution Principle](#4-l---liskov-substitution-principle-lsp)
5. [I - Interface Segregation Principle](#5-i---interface-segregation-principle-isp)
6. [D - Dependency Inversion Principle](#6-d---dependency-inversion-principle-dip)
7. [SOLID na Prática (Projeto Real)](#7-solid-na-prática-projeto-real)
8. [Violações Comuns e Correções](#8-violações-comuns-e-correções)
9. [Checklist SOLID](#9-checklist-solid)
10. [Exercícios Práticos](#10-exercícios-práticos)

---

## 1. O que é SOLID

### Definição em 30 Segundos

**SOLID** é um acrônimo criado por Robert C. Martin (Uncle Bob) que representa **5 princípios fundamentais** para escrever código orientado a objetos **flexível, manutenível e testável**.

```
S - Single Responsibility Principle (SRP)
    "Uma classe deve ter uma, e somente uma, razão para mudar"

O - Open/Closed Principle (OCP)
    "Aberto para extensão, fechado para modificação"

L - Liskov Substitution Principle (LSP)
    "Subtipos devem ser substituíveis por seus tipos base"

I - Interface Segregation Principle (ISP)
    "Clientes não devem depender de interfaces que não usam"

D - Dependency Inversion Principle (DIP)
    "Dependa de abstrações, não de implementações concretas"
```

### Por Que SOLID é Importante?

```
❌ SEM SOLID
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentService {

    // ❌ Múltiplas responsabilidades
    public void processPayment(Payment payment) {
        // Validar
        if (payment.amount <= 0) throw new Exception("Invalid amount");

        // Calcular taxas
        double tax = payment.amount * 0.05;

        // Salvar no banco
        Connection conn = DriverManager.getConnection("jdbc:...");
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO...");
        stmt.executeUpdate();

        // Enviar email
        SmtpClient smtp = new SmtpClient("smtp.gmail.com");
        smtp.send("Payment processed!");

        // Publicar no Kafka
        KafkaProducer producer = new KafkaProducer(...);
        producer.send("payment.topic", payment);

        // Gerar PDF
        PdfGenerator pdf = new PdfGenerator();
        pdf.generate(payment);
    }
}

PROBLEMAS:
├─ ❌ 6 razões para mudar (validação, cálculo, DB, email, Kafka, PDF)
├─ ❌ Impossível testar isoladamente (precisa DB, SMTP, Kafka, tudo!)
├─ ❌ Altamente acoplado (conhece detalhes de SMTP, JDBC, Kafka)
├─ ❌ Impossível reusar partes (validação, cálculo)
├─ ❌ Difícil estender (adicionar novo tipo de pagamento)
└─ ❌ CÓDIGO IMPOSSÍVEL DE MANTER! 💥


✅ COM SOLID
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// S - Single Responsibility
public class Payment {
    public void validate() { /* só validação */ }
}

public class TaxCalculator {
    public BigDecimal calculate(Payment payment) { /* só cálculo */ }
}

// O - Open/Closed
public interface PaymentProcessor {
    void process(Payment payment);
}

public class CreditCardProcessor implements PaymentProcessor {
    public void process(Payment payment) { /* implementação */ }
}

// L - Liskov Substitution
PaymentProcessor processor = new CreditCardProcessor();
processor.process(payment);  // Funciona com qualquer implementação!

// I - Interface Segregation
public interface PaymentValidator {
    void validate(Payment payment);
}

public interface PaymentPersister {
    void save(Payment payment);
}

// D - Dependency Inversion
public class PaymentService {
    private final PaymentValidator validator;  // Abstração!
    private final PaymentPersister persister;  // Abstração!

    public PaymentService(PaymentValidator validator,
                         PaymentPersister persister) {
        this.validator = validator;
        this.persister = persister;
    }

    public void process(Payment payment) {
        validator.validate(payment);
        persister.save(payment);
    }
}

BENEFÍCIOS:
├─ ✅ Cada classe uma responsabilidade (fácil entender)
├─ ✅ Extensível sem modificar código existente
├─ ✅ Substituível (qualquer implementação funciona)
├─ ✅ Interfaces pequenas e focadas
├─ ✅ Baixo acoplamento (depende de abstrações)
└─ ✅ CÓDIGO PROFISSIONAL! ✨
```

---

## 2. S - Single Responsibility Principle (SRP)

### Definição

```
┌──────────────────────────────────────────────────┐
│   "Uma classe deve ter UMA, e somente UMA,       │
│    razão para mudar."                            │
│                                                  │
│   Em outras palavras:                            │
│   Uma classe = Uma responsabilidade              │
│   Uma classe = Um motivo para mudar              │
└──────────────────────────────────────────────────┘
```

### Exemplo Real: Violação do SRP

```java
// ❌ VIOLAÇÃO DO SRP - Múltiplas Responsabilidades

public class PaymentService {

    // Responsabilidade 1: Validação
    public boolean isValid(Payment payment) {
        if (payment.getAmount() == null) return false;
        if (payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) return false;
        if (payment.getCurrency() == null) return false;
        return true;
    }

    // Responsabilidade 2: Cálculo de taxas
    public BigDecimal calculateTax(Payment payment) {
        if (payment.getCurrency().equals("USD")) {
            return payment.getAmount().multiply(new BigDecimal("0.05"));
        } else if (payment.getCurrency().equals("BRL")) {
            return payment.getAmount().multiply(new BigDecimal("0.07"));
        }
        return BigDecimal.ZERO;
    }

    // Responsabilidade 3: Persistência
    public void save(Payment payment) {
        String sql = "INSERT INTO payment VALUES (?, ?, ?)";
        // ... JDBC code
    }

    // Responsabilidade 4: Notificação
    public void sendEmail(Payment payment) {
        String subject = "Payment Approved";
        String body = "Your payment of " + payment.getAmount() + " was approved.";
        // ... SMTP code
    }

    // Responsabilidade 5: Relatório
    public void generatePDF(Payment payment) {
        // ... PDF generation code
    }

    // Responsabilidade 6: Publicação de eventos
    public void publishEvent(Payment payment) {
        // ... Kafka producer code
    }
}

PROBLEMAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ MÚLTIPLAS RAZÕES PARA MUDAR:
   ├─ Regra de validação muda → mudar PaymentService
   ├─ Cálculo de taxa muda → mudar PaymentService
   ├─ Trocar PostgreSQL por MongoDB → mudar PaymentService
   ├─ Trocar SMTP por SendGrid → mudar PaymentService
   ├─ Trocar biblioteca PDF → mudar PaymentService
   └─ Trocar Kafka por RabbitMQ → mudar PaymentService

2. ❌ DIFÍCIL TESTAR:
   └─ Para testar validação, precisa de: DB, SMTP, Kafka, PDF lib

3. ❌ DIFÍCIL REUSAR:
   └─ Quero só cálculo de taxa em outro lugar? Impossível!

4. ❌ DIFÍCIL ENTENDER:
   └─ Classe com 500+ linhas, faz TUDO!

5. ❌ ALTO ACOPLAMENTO:
   └─ Conhece: JDBC, SMTP, Kafka, PDF lib (4 dependências!)
```

### Exemplo Real: Seguindo SRP

```java
// ✅ SEGUINDO SRP - Uma Responsabilidade por Classe

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      Responsabilidade 1: VALIDAÇÃO
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentValidator {

    /**
     * ÚNICA responsabilidade: validar Payment.
     * ÚNICA razão para mudar: regras de validação mudarem.
     */
    public void validate(Payment payment) {
        validateAmount(payment.getAmount());
        validateCurrency(payment.getCurrency());
        validatePaymentId(payment.getPaymentId());
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }

    private void validatePaymentId(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Payment ID is required");
        }
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      Responsabilidade 2: CÁLCULO DE TAXAS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class TaxCalculator {

    /**
     * ÚNICA responsabilidade: calcular taxas.
     * ÚNICA razão para mudar: fórmula de cálculo mudar.
     */
    public BigDecimal calculateTax(Payment payment) {
        return switch (payment.getCurrency()) {
            case "USD" -> payment.getAmount().multiply(new BigDecimal("0.05"));
            case "BRL" -> payment.getAmount().multiply(new BigDecimal("0.07"));
            case "EUR" -> payment.getAmount().multiply(new BigDecimal("0.04"));
            default -> BigDecimal.ZERO;
        };
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      Responsabilidade 3: PERSISTÊNCIA
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentRepository {

    private final JpaRepository<PaymentEntity, String> jpaRepository;

    /**
     * ÚNICA responsabilidade: persistir Payment.
     * ÚNICA razão para mudar: trocar banco de dados.
     */
    public Payment save(Payment payment) {
        PaymentEntity entity = toEntity(payment);
        PaymentEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    public Optional<Payment> findById(String paymentId) {
        return jpaRepository.findById(paymentId)
            .map(this::toDomain);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      Responsabilidade 4: NOTIFICAÇÃO EMAIL
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentEmailNotifier {

    private final EmailService emailService;

    /**
     * ÚNICA responsabilidade: enviar email de pagamento.
     * ÚNICA razão para mudar: template de email mudar.
     */
    public void notifyApproved(Payment payment) {
        String to = getUserEmail(payment.getUserId());
        String subject = "Payment Approved";
        String body = buildEmailBody(payment);

        emailService.send(to, subject, body);
    }

    private String buildEmailBody(Payment payment) {
        return String.format(
            "Your payment of %s %s was approved!",
            payment.getCurrency(),
            payment.getAmount()
        );
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      Responsabilidade 5: GERAÇÃO DE PDF
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentPdfGenerator {

    private final PdfLibrary pdfLibrary;

    /**
     * ÚNICA responsabilidade: gerar PDF de pagamento.
     * ÚNICA razão para mudar: formato do PDF mudar.
     */
    public byte[] generateReceipt(Payment payment) {
        return pdfLibrary.create()
            .addTitle("Payment Receipt")
            .addField("Payment ID", payment.getPaymentId())
            .addField("Amount", payment.getAmount().toString())
            .addField("Currency", payment.getCurrency())
            .build();
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      Responsabilidade 6: PUBLICAÇÃO DE EVENTOS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * ÚNICA responsabilidade: publicar eventos de pagamento.
     * ÚNICA razão para mudar: formato do evento ou broker mudar.
     */
    public void publishApproved(Payment payment) {
        PaymentApprovedEvent event = new PaymentApprovedEvent(
            payment.getPaymentId(),
            payment.getUserId(),
            payment.getAmount(),
            payment.getCurrency()
        );

        kafkaTemplate.send("payment.approved.v1", event);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ORQUESTRAÇÃO (Application Service)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentService {

    private final PaymentValidator validator;
    private final TaxCalculator taxCalculator;
    private final PaymentRepository repository;
    private final PaymentEmailNotifier emailNotifier;
    private final PaymentPdfGenerator pdfGenerator;
    private final PaymentEventPublisher eventPublisher;

    // Construtor com todas as dependências

    /**
     * ÚNICA responsabilidade: ORQUESTRAR o fluxo de aprovação.
     * ÚNICA razão para mudar: fluxo de aprovação mudar.
     *
     * NÃO faz: validação, cálculo, persistência, email, PDF, eventos.
     * SÓ faz: chamar quem faz!
     */
    @Transactional
    public Payment approvePayment(ApprovePaymentCommand command) {

        // 1. Criar Payment (Domain)
        Payment payment = new Payment(
            command.paymentId(),
            command.userId(),
            command.amount(),
            command.currency()
        );

        // 2. Validar (delegado)
        validator.validate(payment);

        // 3. Calcular taxa (delegado)
        BigDecimal tax = taxCalculator.calculateTax(payment);

        // 4. Aprovar (Domain)
        payment.approve();

        // 5. Persistir (delegado)
        Payment saved = repository.save(payment);

        // 6. Enviar email (delegado)
        emailNotifier.notifyApproved(saved);

        // 7. Gerar PDF (delegado)
        byte[] pdf = pdfGenerator.generateReceipt(saved);

        // 8. Publicar evento (delegado)
        eventPublisher.publishApproved(saved);

        return saved;
    }
}

BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ UMA RAZÃO PARA MUDAR (cada classe):
   ├─ PaymentValidator: só muda se validação mudar
   ├─ TaxCalculator: só muda se cálculo mudar
   ├─ PaymentRepository: só muda se banco mudar
   └─ ... cada uma isolada!

2. ✅ FÁCIL TESTAR:
   ├─ Testa PaymentValidator sem banco/email/kafka
   ├─ Testa TaxCalculator com valores simples
   └─ Cada classe = teste isolado!

3. ✅ FÁCIL REUSAR:
   ├─ Usar TaxCalculator em outro contexto? Fácil!
   ├─ Usar PaymentValidator em API diferente? Fácil!
   └─ Classes pequenas e focadas!

4. ✅ FÁCIL ENTENDER:
   ├─ PaymentValidator? Só validação!
   ├─ TaxCalculator? Só cálculo!
   └─ Nome da classe diz EXATAMENTE o que faz!

5. ✅ BAIXO ACOPLAMENTO:
   └─ Cada classe conhece MENOS coisas!
```

### Como Identificar Violação de SRP

```
PERGUNTAS MÁGICAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. "Esta classe faz MAIS DE UMA coisa?"
   ✅ Se SIM → viola SRP

2. "Quantos motivos esta classe tem para mudar?"
   ✅ Se > 1 → viola SRP

3. "Consigo descrever a classe sem usar 'E' ou 'OU'?"
   Exemplo: "Valida pagamento E envia email"
   ✅ Se tem 'E'/'OU' → viola SRP

4. "O nome da classe termina com -Manager, -Handler, -Util?"
   Exemplo: PaymentManager, DataHandler
   ⚠️ Alerta! Geralmente viola SRP (faz muita coisa)

5. "A classe tem mais de 200 linhas?"
   ⚠️ Possível violação (mas nem sempre)


SINAIS DE VIOLAÇÃO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ Muitos imports (classe conhece muita coisa)
❌ Muitos métodos públicos (faz muita coisa)
❌ Difícil nomear (PaymentThing, DataManager)
❌ Difícil testar (precisa mockar 10 dependências)
❌ God Class (classe que sabe/faz tudo)
```

---

## 3. O - Open/Closed Principle (OCP)

### Definição

```
┌──────────────────────────────────────────────────┐
│   "Classes devem estar ABERTAS para extensão,    │
│    mas FECHADAS para modificação."               │
│                                                  │
│   Em outras palavras:                            │
│   - Adicionar comportamento NOVO = SIM ✅        │
│   - Modificar código EXISTENTE = NÃO ❌          │
└──────────────────────────────────────────────────┘
```

### Exemplo Real: Violação do OCP

```java
// ❌ VIOLAÇÃO DO OCP - Precisa Modificar Código Existente

public class PaymentProcessor {

    /**
     * ❌ Problema: Para adicionar novo tipo de pagamento,
     *    precisa MODIFICAR este método!
     */
    public void process(Payment payment, String paymentType) {

        if (paymentType.equals("CREDIT_CARD")) {
            // Processar cartão de crédito
            validateCardNumber(payment.getCardNumber());
            chargeCreditCard(payment);

        } else if (paymentType.equals("DEBIT_CARD")) {
            // Processar cartão de débito
            validateCardNumber(payment.getCardNumber());
            chargeDebitCard(payment);

        } else if (paymentType.equals("PIX")) {
            // Processar PIX
            validatePixKey(payment.getPixKey());
            processPix(payment);

        } else if (paymentType.equals("BOLETO")) {
            // Processar Boleto
            generateBoleto(payment);
            sendBoletoByEmail(payment);

        } else if (paymentType.equals("PAYPAL")) {
            // ❌ NOVO TIPO! Precisa MODIFICAR código existente!
            validatePaypalAccount(payment.getPaypalEmail());
            processPaypal(payment);

        } else {
            throw new IllegalArgumentException("Unknown payment type");
        }
    }
}

PROBLEMAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ MODIFICAÇÃO CONSTANTE:
   └─ Novo tipo de pagamento? Modificar classe existente!
   └─ Risco de quebrar código que JÁ FUNCIONA!

2. ❌ DIFÍCIL TESTAR:
   └─ Testar PayPal = rodar TODO o método (com if/else)
   └─ Não testa isoladamente!

3. ❌ VIOLAÇÃO DO SRP:
   └─ Classe conhece TODOS os tipos de pagamento!
   └─ Múltiplas razões para mudar!

4. ❌ CRESCIMENTO INFINITO:
   └─ Cada novo tipo = mais linhas
   └─ Classe com 1000+ linhas!

5. ❌ MERGE CONFLICTS:
   └─ Dev 1 adiciona PayPal
   └─ Dev 2 adiciona ApplePay
   └─ MESMO arquivo, MESMO método!
   └─ Conflito de merge garantido! 💥
```

### Exemplo Real: Seguindo OCP

```java
// ✅ SEGUINDO OCP - Extensível sem Modificação

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ABSTRAÇÃO (Interface)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public interface PaymentProcessor {

    /**
     * Processar pagamento.
     *
     * Cada tipo de pagamento implementa esta interface.
     */
    void process(Payment payment);

    /**
     * Verificar se este processor suporta o payment.
     */
    boolean supports(Payment payment);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      IMPLEMENTAÇÕES CONCRETAS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Component
public class CreditCardProcessor implements PaymentProcessor {

    @Override
    public void process(Payment payment) {
        validateCardNumber(payment.getCardNumber());
        chargeCreditCard(payment);
    }

    @Override
    public boolean supports(Payment payment) {
        return payment.getPaymentType() == PaymentType.CREDIT_CARD;
    }
}

@Component
public class DebitCardProcessor implements PaymentProcessor {

    @Override
    public void process(Payment payment) {
        validateCardNumber(payment.getCardNumber());
        chargeDebitCard(payment);
    }

    @Override
    public boolean supports(Payment payment) {
        return payment.getPaymentType() == PaymentType.DEBIT_CARD;
    }
}

@Component
public class PixProcessor implements PaymentProcessor {

    @Override
    public void process(Payment payment) {
        validatePixKey(payment.getPixKey());
        processPix(payment);
    }

    @Override
    public boolean supports(Payment payment) {
        return payment.getPaymentType() == PaymentType.PIX;
    }
}

@Component
public class BoletoProcessor implements PaymentProcessor {

    @Override
    public void process(Payment payment) {
        generateBoleto(payment);
        sendBoletoByEmail(payment);
    }

    @Override
    public boolean supports(Payment payment) {
        return payment.getPaymentType() == PaymentType.BOLETO;
    }
}

// ✅ NOVO TIPO? Só criar nova classe! (não modifica código existente)
@Component
public class PaypalProcessor implements PaymentProcessor {

    @Override
    public void process(Payment payment) {
        validatePaypalAccount(payment.getPaypalEmail());
        processPaypal(payment);
    }

    @Override
    public boolean supports(Payment payment) {
        return payment.getPaymentType() == PaymentType.PAYPAL;
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ESTRATÉGIA (Strategy Pattern)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class PaymentService {

    private final List<PaymentProcessor> processors;

    /**
     * Spring injeta TODAS as implementações de PaymentProcessor!
     */
    public PaymentService(List<PaymentProcessor> processors) {
        this.processors = processors;
    }

    /**
     * ✅ Processar pagamento SEM if/else!
     * ✅ Adicionar novo tipo? NÃO modifica este código!
     */
    public void processPayment(Payment payment) {

        PaymentProcessor processor = processors.stream()
            .filter(p -> p.supports(payment))
            .findFirst()
            .orElseThrow(() -> new UnsupportedPaymentTypeException(
                "No processor found for payment type: " + payment.getPaymentType()
            ));

        processor.process(payment);
    }
}

BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ EXTENSÍVEL SEM MODIFICAÇÃO:
   └─ Novo tipo? Criar nova classe!
   └─ PaymentService NÃO muda!
   └─ Zero risco de quebrar código existente!

2. ✅ FÁCIL TESTAR:
   └─ Testa CreditCardProcessor isoladamente
   └─ Testa PixProcessor isoladamente
   └─ Cada um com seus próprios testes!

3. ✅ SEGUE SRP:
   └─ Cada processor = uma responsabilidade
   └─ CreditCardProcessor só sabe de cartão!

4. ✅ NÃO CRESCE:
   └─ PaymentService tem SEMPRE o mesmo tamanho
   └─ Novas classes criadas, não modificadas!

5. ✅ SEM MERGE CONFLICTS:
   └─ Dev 1 cria PaypalProcessor.java
   └─ Dev 2 cria ApplePayProcessor.java
   └─ Arquivos DIFERENTES!
   └─ Zero conflitos! ✅


COMO ADICIONAR NOVO TIPO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Criar nova classe: ApplePayProcessor
2. Implementar interface: PaymentProcessor
3. Adicionar @Component (Spring injeta automaticamente)
4. FIM! PaymentService funciona automaticamente!

// ✅ ZERO modificações em código existente!
@Component
public class ApplePayProcessor implements PaymentProcessor {

    @Override
    public void process(Payment payment) {
        // Lógica do ApplePay
    }

    @Override
    public boolean supports(Payment payment) {
        return payment.getPaymentType() == PaymentType.APPLE_PAY;
    }
}
```

### Quando Usar OCP

```
USE OCP QUANDO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ Comportamentos variam (diferentes tipos de pagamento)
✅ Espera-se adicionar novos casos no futuro
✅ Muitos if/else ou switch/case
✅ Estratégias diferentes para mesma operação
✅ Plugins ou extensões

EXEMPLOS REAIS:
├─ Diferentes métodos de pagamento (Cartão, PIX, Boleto)
├─ Diferentes formas de calcular frete (PAC, SEDEX, Express)
├─ Diferentes tipos de notificação (Email, SMS, Push)
├─ Diferentes formatos de exportação (PDF, Excel, CSV)
└─ Diferentes estratégias de desconto (Black Friday, Cupom, Fidelidade)


NÃO USE OCP QUANDO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ Comportamento é simples e NÃO varia
❌ Improvável adicionar novos casos
❌ Over-engineering para caso trivial

EXEMPLO:
// ❌ Não precisa de OCP (só 2 estados fixos)
if (payment.isApproved()) {
    return "APPROVED";
} else {
    return "REJECTED";
}
```

---

## 4. L - Liskov Substitution Principle (LSP)

### Definição

```
┌──────────────────────────────────────────────────┐
│   "Objetos de uma superclasse devem poder ser    │
│    substituídos por objetos de suas subclasses   │
│    SEM quebrar o programa."                      │
│                                                  │
│   Em outras palavras:                            │
│   Se S é subtipo de T, então objetos do tipo T  │
│   podem ser substituídos por objetos do tipo S.  │
└──────────────────────────────────────────────────┘
```

### Exemplo Real: Violação do LSP

```java
// ❌ VIOLAÇÃO DO LSP - Subtipo NÃO Substituível

public class Rectangle {
    protected int width;
    protected int height;

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getArea() {
        return width * height;
    }
}

// ❌ Quadrado herda de Retângulo
public class Square extends Rectangle {

    /**
     * ❌ Sobrescreve comportamento de forma INCOMPATÍVEL!
     * Quadrado tem lados iguais, então setWidth deve mudar height também.
     */
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width;  // ❌ Efeito colateral inesperado!
    }

    @Override
    public void setHeight(int height) {
        this.width = height;  // ❌ Efeito colateral inesperado!
        this.height = height;
    }
}

// Código que usa Rectangle
public class AreaCalculator {

    public static void testRectangle(Rectangle rectangle) {
        rectangle.setWidth(5);
        rectangle.setHeight(4);

        // Espera: 5 * 4 = 20
        int area = rectangle.getArea();
        System.out.println("Expected: 20, Got: " + area);

        assert area == 20;  // ❌ PASSA com Rectangle, FALHA com Square!
    }
}

// Teste
Rectangle rectangle = new Rectangle();
testRectangle(rectangle);  // ✅ Funciona! área = 20

Square square = new Square();
testRectangle(square);  // ❌ QUEBRA! área = 16 (4 * 4)
                        // Porque setHeight(4) mudou width também!

PROBLEMAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ NÃO SUBSTITUÍVEL:
   └─ Square NÃO pode substituir Rectangle!
   └─ Comportamento muda de forma inesperada!

2. ❌ QUEBRA EXPECTATIVAS:
   └─ Cliente espera: setWidth(5), getWidth() == 5
   └─ Square: setWidth(5) também muda height!

3. ❌ TESTES QUEBRAM:
   └─ Testes passam com Rectangle
   └─ FALHAM com Square (mesma função!)

4. ❌ PRECONDIÇÕES/POSCONDIÇÕES VIOLADAS:
   └─ Rectangle não garante width == height
   └─ Square força width == height
   └─ Contrato quebrado!
```

### Exemplo Real: Seguindo LSP

```java
// ✅ SEGUINDO LSP - Subtipos Substituíveis

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ABSTRAÇÃO COMUM
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public interface Shape {
    int getArea();
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      IMPLEMENTAÇÕES INDEPENDENTES
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class Rectangle implements Shape {
    private final int width;
    private final int height;

    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public int getArea() {
        return width * height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}

public class Square implements Shape {
    private final int side;

    public Square(int side) {
        this.side = side;
    }

    @Override
    public int getArea() {
        return side * side;
    }

    public int getSide() { return side; }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      USO (Polimorfismo Correto)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class AreaCalculator {

    /**
     * ✅ Funciona com QUALQUER Shape!
     * ✅ Rectangle, Square, Circle, Triangle...
     */
    public static int calculateTotalArea(List<Shape> shapes) {
        return shapes.stream()
            .mapToInt(Shape::getArea)
            .sum();
    }
}

// Teste
List<Shape> shapes = List.of(
    new Rectangle(5, 4),  // ✅ área = 20
    new Square(4),        // ✅ área = 16
    new Circle(3)         // ✅ área = 28 (π * 3²)
);

int total = AreaCalculator.calculateTotalArea(shapes);
// ✅ Funciona perfeitamente! total = 64

BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ TOTALMENTE SUBSTITUÍVEL:
   └─ Rectangle, Square, Circle implementam Shape
   └─ Qualquer um funciona em calculateTotalArea!

2. ✅ SEM EFEITOS COLATERAIS:
   └─ Cada classe é imutável (final fields)
   └─ Sem setters que causam surpresas!

3. ✅ TESTES PASSAM:
   └─ Teste com Rectangle = passa ✅
   └─ Teste com Square = passa ✅
   └─ Teste com qualquer Shape = passa ✅

4. ✅ CONTRATO RESPEITADO:
   └─ Todos implementam getArea()
   └─ Nenhum viola expectativas!
```

### Regras para Seguir LSP

```
CHECKLIST LSP:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ Subtipo NÃO deve fortalecer PRECONDIÇÕES
   ❌ Superclasse aceita amount >= 0
   ❌ Subclasse exige amount >= 10
   ✅ Subclasse também aceita amount >= 0

☐ Subtipo NÃO deve enfraquecer POSCONDIÇÕES
   ❌ Superclasse garante retorno != null
   ❌ Subclasse pode retornar null
   ✅ Subclasse também garante != null

☐ Subtipo NÃO deve lançar exceções NOVAS
   ❌ Superclasse não lança exceção
   ❌ Subclasse lança IllegalStateException
   ✅ Subclasse também não lança exceção

☐ Subtipo PODE adicionar comportamento
   ✅ Subclasse tem métodos extras (OK!)
   ✅ MAS não muda comportamento herdado!

☐ Invariantes da superclasse devem ser preservadas
   ✅ Se Rectangle garante width != height (pode)
   ✅ Subclasse DEVE manter isso!


SINAIS DE VIOLAÇÃO LSP:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

❌ Subtipo lança UnsupportedOperationException
   └─ Se não suporta, NÃO deve herdar!

❌ Subtipo faz override e retorna null (quando super != null)
   └─ Quebra contrato!

❌ Subtipo verifica tipo antes de usar
   └─ if (shape instanceof Square) { ... }
   └─ Se precisa checar tipo, LSP foi violado!

❌ Testes passam com superclasse, falham com subclasse
   └─ Indicador claro de violação!
```

---

## 5. I - Interface Segregation Principle (ISP)

### Definição

```
┌──────────────────────────────────────────────────┐
│   "Clientes não devem ser forçados a depender    │
│    de interfaces que não usam."                  │
│                                                  │
│   Em outras palavras:                            │
│   Muitas interfaces pequenas e específicas       │
│   são melhores que uma interface grande.         │
└──────────────────────────────────────────────────┘
```

### Exemplo Real: Violação do ISP

```java
// ❌ VIOLAÇÃO DO ISP - Interface FAT (gorda demais)

public interface PaymentService {

    // Métodos para pagamento
    void processPayment(Payment payment);
    void refundPayment(String paymentId);
    void cancelPayment(String paymentId);

    // Métodos para relatórios
    List<Payment> getAllPayments();
    List<Payment> getPaymentsByUser(String userId);
    Payment getPaymentById(String paymentId);
    byte[] generatePdfReport(String paymentId);
    byte[] generateExcelReport(String userId);

    // Métodos para notificação
    void sendEmailConfirmation(String paymentId);
    void sendSmsNotification(String paymentId);
    void sendPushNotification(String paymentId);

    // Métodos para analytics
    BigDecimal getTotalRevenue();
    Map<String, Long> getPaymentsByType();
    Map<String, BigDecimal> getRevenueByMonth();
}

// ❌ Implementação forçada a implementar TUDO
public class BasicPaymentProcessor implements PaymentService {

    @Override
    public void processPayment(Payment payment) {
        // ✅ Usa este método
    }

    @Override
    public void refundPayment(String paymentId) {
        // ✅ Usa este método
    }

    @Override
    public void cancelPayment(String paymentId) {
        // ✅ Usa este método
    }

    // ❌ NÃO precisa de relatórios, mas é FORÇADO a implementar!
    @Override
    public List<Payment> getAllPayments() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public List<Payment> getPaymentsByUser(String userId) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Payment getPaymentById(String paymentId) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public byte[] generatePdfReport(String paymentId) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public byte[] generateExcelReport(String userId) {
        throw new UnsupportedOperationException("Not supported");
    }

    // ❌ NÃO precisa de notificações, mas é FORÇADO a implementar!
    @Override
    public void sendEmailConfirmation(String paymentId) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void sendSmsNotification(String paymentId) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void sendPushNotification(String paymentId) {
        throw new UnsupportedOperationException("Not supported");
    }

    // ❌ NÃO precisa de analytics, mas é FORÇADO a implementar!
    @Override
    public BigDecimal getTotalRevenue() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Map<String, Long> getPaymentsByType() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Map<String, BigDecimal> getRevenueByMonth() {
        throw new UnsupportedOperationException("Not supported");
    }
}

PROBLEMAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ IMPLEMENTAÇÃO FORÇADA:
   └─ Implementa métodos que NÃO usa!
   └─ 80% do código = throw UnsupportedOperationException

2. ❌ ACOPLAMENTO DESNECESSÁRIO:
   └─ BasicPaymentProcessor depende de:
       • Lógica de relatórios (não usa)
       • Lógica de notificações (não usa)
       • Lógica de analytics (não usa)

3. ❌ DIFÍCIL MANTER:
   └─ Interface muda (adiciona método de relatório)
   └─ TODAS implementações precisam mudar!
   └─ Mesmo que não usem relatórios!

4. ❌ RUNTIME ERRORS:
   └─ Chamou método não implementado?
   └─ UnsupportedOperationException! 💥
   └─ Erro só em RUNTIME (não em compile time)

5. ❌ CONFUSO:
   └─ BasicPaymentProcessor tem método sendSms()?
   └─ Usuário tenta usar... BOOM! ❌
```

### Exemplo Real: Seguindo ISP

```java
// ✅ SEGUINDO ISP - Interfaces Pequenas e Específicas

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      INTERFACES SEGREGADAS (pequenas)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Interface 1: Só processamento de pagamento.
 */
public interface PaymentProcessor {
    void processPayment(Payment payment);
    void refundPayment(String paymentId);
    void cancelPayment(String paymentId);
}

/**
 * Interface 2: Só consultas de pagamento.
 */
public interface PaymentQuery {
    List<Payment> getAllPayments();
    List<Payment> getPaymentsByUser(String userId);
    Payment getPaymentById(String paymentId);
}

/**
 * Interface 3: Só geração de relatórios.
 */
public interface PaymentReportGenerator {
    byte[] generatePdfReport(String paymentId);
    byte[] generateExcelReport(String userId);
}

/**
 * Interface 4: Só notificações.
 */
public interface PaymentNotifier {
    void sendEmailConfirmation(String paymentId);
    void sendSmsNotification(String paymentId);
    void sendPushNotification(String paymentId);
}

/**
 * Interface 5: Só analytics.
 */
public interface PaymentAnalytics {
    BigDecimal getTotalRevenue();
    Map<String, Long> getPaymentsByType();
    Map<String, BigDecimal> getRevenueByMonth();
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      IMPLEMENTAÇÕES (implementam só o que precisam!)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * ✅ Implementa SÓ processamento (não precisa do resto!)
 */
@Service
public class BasicPaymentProcessor implements PaymentProcessor {

    @Override
    public void processPayment(Payment payment) {
        // ✅ Implementa o que USA
    }

    @Override
    public void refundPayment(String paymentId) {
        // ✅ Implementa o que USA
    }

    @Override
    public void cancelPayment(String paymentId) {
        // ✅ Implementa o que USA
    }

    // ✅ SEM métodos que não usa!
    // ✅ SEM throw UnsupportedOperationException!
}

/**
 * ✅ Implementa SÓ consultas (não precisa processar!)
 */
@Service
public class PaymentQueryService implements PaymentQuery {

    private final PaymentRepository repository;

    @Override
    public List<Payment> getAllPayments() {
        return repository.findAll();
    }

    @Override
    public List<Payment> getPaymentsByUser(String userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public Payment getPaymentById(String paymentId) {
        return repository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}

/**
 * ✅ Implementa SÓ relatórios (não processa nem notifica!)
 */
@Service
public class PaymentPdfReportGenerator implements PaymentReportGenerator {

    @Override
    public byte[] generatePdfReport(String paymentId) {
        // ✅ Implementa o que USA
    }

    @Override
    public byte[] generateExcelReport(String userId) {
        // ✅ Implementa o que USA
    }
}

/**
 * ✅ Implementa SÓ notificações email (não SMS nem Push!)
 */
@Service
public class EmailPaymentNotifier implements PaymentNotifier {

    @Override
    public void sendEmailConfirmation(String paymentId) {
        // ✅ Implementa email
    }

    @Override
    public void sendSmsNotification(String paymentId) {
        // Não usa SMS, mas interface obriga...
        // Solução: criar interface menor ainda!
    }

    @Override
    public void sendPushNotification(String paymentId) {
        // Não usa Push, mas interface obriga...
    }
}

// ✅ AINDA MELHOR: Segregar PaymentNotifier em 3 interfaces!
public interface EmailNotifier {
    void sendEmailConfirmation(String paymentId);
}

public interface SmsNotifier {
    void sendSmsNotification(String paymentId);
}

public interface PushNotifier {
    void sendPushNotification(String paymentId);
}

// Agora cada implementação escolhe o que implementar!
@Service
public class EmailPaymentNotifier implements EmailNotifier {
    @Override
    public void sendEmailConfirmation(String paymentId) {
        // ✅ SÓ email!
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      USO (Composição)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class PaymentOrchestrator {

    private final PaymentProcessor processor;
    private final PaymentQuery query;
    private final EmailNotifier emailNotifier;

    /**
     * ✅ Depende só do que USA!
     * ✅ Não conhece relatórios, analytics, SMS, Push!
     */
    public PaymentOrchestrator(PaymentProcessor processor,
                              PaymentQuery query,
                              EmailNotifier emailNotifier) {
        this.processor = processor;
        this.query = query;
        this.emailNotifier = emailNotifier;
    }

    @Transactional
    public void processAndNotify(String paymentId) {
        // Buscar payment
        Payment payment = query.getPaymentById(paymentId);

        // Processar
        processor.processPayment(payment);

        // Notificar
        emailNotifier.sendEmailConfirmation(paymentId);
    }
}

BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ SEM IMPLEMENTAÇÕES FORÇADAS:
   └─ Implementa SÓ o que usa!
   └─ Zero throw UnsupportedOperationException!

2. ✅ BAIXO ACOPLAMENTO:
   └─ Depende só de interfaces necessárias
   └─ PaymentOrchestrator não conhece analytics!

3. ✅ FÁCIL MANTER:
   └─ Adiciona método em PaymentAnalytics?
   └─ SÓ implementações de analytics mudam!
   └─ BasicPaymentProcessor = intocado!

4. ✅ COMPILE-TIME SAFETY:
   └─ Tenta chamar método que não existe?
   └─ Erro em COMPILE TIME! ✅
   └─ Não em runtime!

5. ✅ CLARO:
   └─ BasicPaymentProcessor só processa!
   └─ Não confunde usuários com métodos de relatório!
```

### Como Segregar Interfaces

```
PASSOS PARA SEGREGAR:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Identificar grupos de métodos por RESPONSABILIDADE
   └─ Métodos de processamento juntos
   └─ Métodos de consulta juntos
   └─ Métodos de notificação juntos

2. Criar interface separada para cada grupo
   └─ PaymentProcessor (processa)
   └─ PaymentQuery (consulta)
   └─ PaymentNotifier (notifica)

3. Implementações escolhem quais interfaces implementar
   └─ BasicProcessor implementa PaymentProcessor
   └─ QueryService implementa PaymentQuery

4. Clientes dependem SÓ da interface que usam
   └─ Controller depende de PaymentProcessor (não Query)
   └─ ReportService depende de PaymentQuery (não Processor)


TAMANHO IDEAL DE INTERFACE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ 1-5 métodos relacionados
✅ Uma responsabilidade coesa
✅ Nome descritivo (Processor, Query, Notifier)

❌ 10+ métodos (muito gorda!)
❌ Métodos de responsabilidades diferentes
❌ Nome genérico (Service, Manager, Handler)
```

---

## 6. D - Dependency Inversion Principle (DIP)

### Definição

```
┌──────────────────────────────────────────────────┐
│   "Módulos de alto nível não devem depender de   │
│    módulos de baixo nível. Ambos devem depender  │
│    de ABSTRAÇÕES."                               │
│                                                  │
│   "Abstrações não devem depender de detalhes.    │
│    Detalhes devem depender de abstrações."       │
└──────────────────────────────────────────────────┘
```

### Exemplo Real: Violação do DIP

```java
// ❌ VIOLAÇÃO DO DIP - Alto Nível Depende de Baixo Nível

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      BAIXO NÍVEL (Detalhes de Implementação)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class MySqlPaymentRepository {

    /**
     * ❌ Implementação concreta de MySQL.
     */
    public void save(Payment payment) {
        String sql = "INSERT INTO payment (payment_id, amount) VALUES (?, ?)";
        // ... MySQL-specific code
    }

    public Payment findById(String paymentId) {
        String sql = "SELECT * FROM payment WHERE payment_id = ?";
        // ... MySQL-specific code
        return payment;
    }
}

public class KafkaPaymentNotifier {

    /**
     * ❌ Implementação concreta de Kafka.
     */
    public void notify(Payment payment) {
        KafkaProducer<String, Object> producer = new KafkaProducer<>(...);
        producer.send("payment.approved.v1", payment);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ALTO NÍVEL (Lógica de Negócio)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentService {

    // ❌ Depende de implementações CONCRETAS!
    private final MySqlPaymentRepository repository;
    private final KafkaPaymentNotifier notifier;

    public ApprovePaymentService(MySqlPaymentRepository repository,
                                KafkaPaymentNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    @Transactional
    public void approvePayment(String paymentId) {
        // Lógica de negócio
        Payment payment = repository.findById(paymentId);
        payment.approve();
        repository.save(payment);
        notifier.notify(payment);
    }
}

PROBLEMAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ❌ ALTO ACOPLAMENTO:
   └─ Service conhece MySQL (detalhe de implementação)
   └─ Service conhece Kafka (detalhe de implementação)
   └─ Mudar banco? Service precisa mudar! 💥

2. ❌ IMPOSSÍVEL TROCAR TECNOLOGIA:
   └─ Quer MongoDB em vez de MySQL?
   └─ Service depende de MySqlPaymentRepository!
   └─ Precisa mudar Service! 💥

3. ❌ IMPOSSÍVEL TESTAR:
   └─ Como testar Service sem MySQL real?
   └─ Como testar Service sem Kafka real?
   └─ Impossível! Testes lentos e frágeis!

4. ❌ DIREÇÃO ERRADA DE DEPENDÊNCIA:
   └─ Alto nível (Service) → Baixo nível (MySQL)
   └─ Deveria ser ao contrário!

5. ❌ VIOLA LAYERED ARCHITECTURE:
   └─ Application Layer depende de Infrastructure
   └─ Quebra regra de dependência!


DIAGRAMA DE DEPENDÊNCIA (ERRADO):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌──────────────────────────┐
│ ApprovePaymentService    │  ← Alto Nível
│  (lógica de negócio)     │
└────────────┬─────────────┘
             │ depende ❌
             ↓
┌────────────▼─────────────┐
│ MySqlPaymentRepository   │  ← Baixo Nível
│  (detalhe de MySQL)      │
└──────────────────────────┘

┌────────────▼─────────────┐
│ KafkaPaymentNotifier     │  ← Baixo Nível
│  (detalhe de Kafka)      │
└──────────────────────────┘

❌ Alto nível depende de baixo nível!
❌ Lógica de negócio depende de tecnologias!
```

### Exemplo Real: Seguindo DIP

```java
// ✅ SEGUINDO DIP - Ambos Dependem de Abstração

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ABSTRAÇÕES (Interfaces)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * ✅ Port (Hexagonal Architecture).
 * Interface definida pelo ALTO NÍVEL (Domain/Application).
 */
public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(String paymentId);
}

/**
 * ✅ Port (Hexagonal Architecture).
 * Interface definida pelo ALTO NÍVEL (Domain/Application).
 */
public interface PaymentNotifier {
    void notify(Payment payment);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      ALTO NÍVEL (Lógica de Negócio)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentService {

    // ✅ Depende de ABSTRAÇÕES!
    private final PaymentRepository repository;  // Interface!
    private final PaymentNotifier notifier;      // Interface!

    public ApprovePaymentService(PaymentRepository repository,
                                PaymentNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    @Transactional
    public void approvePayment(String paymentId) {
        // ✅ Mesma lógica de negócio!
        // ✅ MAS não conhece MySQL nem Kafka!
        Payment payment = repository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        payment.approve();
        repository.save(payment);
        notifier.notify(payment);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      BAIXO NÍVEL (Adaptadores)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * ✅ Adapter (Hexagonal Architecture).
 * Implementa interface definida pelo alto nível!
 */
@Repository
public class MySqlPaymentRepositoryAdapter implements PaymentRepository {

    private final JpaRepository<PaymentEntity, String> jpaRepository;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = toEntity(payment);
        PaymentEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(String paymentId) {
        return jpaRepository.findById(paymentId)
            .map(this::toDomain);
    }
}

/**
 * ✅ Adapter (Hexagonal Architecture).
 * Implementa interface definida pelo alto nível!
 */
@Component
public class KafkaPaymentNotifierAdapter implements PaymentNotifier {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void notify(Payment payment) {
        PaymentApprovedEvent event = PaymentApprovedEvent.from(payment);
        kafkaTemplate.send("payment.approved.v1", event);
    }
}


BENEFÍCIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. ✅ BAIXO ACOPLAMENTO:
   └─ Service NÃO conhece MySQL!
   └─ Service NÃO conhece Kafka!
   └─ Service só conhece interfaces!

2. ✅ FÁCIL TROCAR TECNOLOGIA:
   └─ Trocar MySQL por MongoDB?
   └─ Criar MongoPaymentRepositoryAdapter!
   └─ Service = INTOCADO! ✅

3. ✅ TESTÁVEL:
   └─ Criar FakePaymentRepository (in-memory)!
   └─ Criar FakePaymentNotifier (sem Kafka)!
   └─ Testes rápidos! ⚡

4. ✅ DIREÇÃO CORRETA DE DEPENDÊNCIA:
   └─ Ambos dependem de PaymentRepository (interface)
   └─ Baixo nível implementa interface do alto nível!

5. ✅ SEGUE LAYERED ARCHITECTURE:
   └─ Application → Interface (Port)
   └─ Infrastructure implementa Interface (Adapter)
   └─ Application NÃO depende de Infrastructure! ✅


DIAGRAMA DE DEPENDÊNCIA (CORRETO):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

┌──────────────────────────┐
│ ApprovePaymentService    │  ← Alto Nível
│  (lógica de negócio)     │
└────────────┬─────────────┘
             │ depende ✅
             ↓
┌────────────▼─────────────┐
│ <<interface>>            │  ← Abstração (Port)
│ PaymentRepository        │
└────────────▲─────────────┘
             │ implementa ✅
             ↑
┌────────────┴─────────────┐
│ MySqlRepositoryAdapter   │  ← Baixo Nível (Adapter)
│  (detalhe de MySQL)      │
└──────────────────────────┘

┌──────────────────────────┐
│ <<interface>>            │  ← Abstração (Port)
│ PaymentNotifier          │
└────────────▲─────────────┘
             │ implementa ✅
             ↑
┌────────────┴─────────────┐
│ KafkaNotifierAdapter     │  ← Baixo Nível (Adapter)
│  (detalhe de Kafka)      │
└──────────────────────────┘

✅ Ambos dependem de abstrações!
✅ Alto nível define interface!
✅ Baixo nível implementa interface!
✅ Dependência INVERTIDA! 🔁
```

### Injeção de Dependência vs Inversão de Dependência

```
NÃO CONFUNDIR:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEPENDENCY INVERSION (DIP):
  └─ Princípio de DESIGN
  └─ Alto nível define interface
  └─ Baixo nível implementa interface
  └─ Ambos dependem de abstração

DEPENDENCY INJECTION (DI):
  └─ Padrão de IMPLEMENTAÇÃO
  └─ Dependências passadas via construtor/setter
  └─ Framework (Spring) injeta implementações
  └─ Facilita testar (mock dependencies)


EXEMPLO:

// DI = Dependency Injection (técnica)
@Service
public class PaymentService {

    private final PaymentRepository repository;

    // ✅ Injeção via construtor
    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }
}

// DIP = Dependency Inversion Principle (design)
// ✅ PaymentService depende de interface (PaymentRepository)
// ✅ MySqlRepositoryAdapter implementa interface
// ✅ Ambos dependem de abstração!


RELAÇÃO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DI é uma técnica que FACILITA implementar DIP!
```

---

## 7. SOLID na Prática (Projeto Real)

### Exemplo Completo: Sistema de Pagamento

Vamos ver como aplicar TODOS os 5 princípios SOLID em um sistema real!

```java
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      S - Single Responsibility
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ✅ Uma responsabilidade: validar Payment
public class PaymentValidator {
    public void validate(Payment payment) { /* ... */ }
}

// ✅ Uma responsabilidade: calcular taxas
public class TaxCalculator {
    public BigDecimal calculate(Payment payment) { /* ... */ }
}

// ✅ Uma responsabilidade: persistir Payment
public interface PaymentRepository {
    Payment save(Payment payment);
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      O - Open/Closed
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ✅ Extensível sem modificação
public interface PaymentProcessor {
    void process(Payment payment);
}

// ✅ Adiciona novo tipo SEM modificar código existente
public class CreditCardProcessor implements PaymentProcessor {
    public void process(Payment payment) { /* ... */ }
}

public class PixProcessor implements PaymentProcessor {
    public void process(Payment payment) { /* ... */ }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      L - Liskov Substitution
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

public class PaymentService {

    private final List<PaymentProcessor> processors;

    public void processPayment(Payment payment) {
        // ✅ Qualquer PaymentProcessor funciona!
        // ✅ Substituível!
        PaymentProcessor processor = findProcessor(payment);
        processor.process(payment);
    }
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      I - Interface Segregation
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

// ✅ Interface pequena: só processamento
public interface PaymentProcessor {
    void process(Payment payment);
}

// ✅ Interface pequena: só consultas
public interface PaymentQuery {
    Payment findById(String paymentId);
}

// ✅ Interface pequena: só notificações
public interface PaymentNotifier {
    void notify(Payment payment);
}

// Implementações escolhem quais interfaces implementar!


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      D - Dependency Inversion
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Service
public class ApprovePaymentService {

    // ✅ Depende de abstrações!
    private final PaymentRepository repository;  // Interface
    private final PaymentNotifier notifier;      // Interface

    public ApprovePaymentService(PaymentRepository repository,
                                PaymentNotifier notifier) {
        this.repository = repository;
        this.notifier = notifier;
    }

    public void approve(Payment payment) {
        repository.save(payment);
        notifier.notify(payment);
    }
}

// Implementações concretas (Infrastructure)
@Repository
public class MySqlPaymentRepository implements PaymentRepository {
    // Implementação MySQL
}

@Component
public class KafkaPaymentNotifier implements PaymentNotifier {
    // Implementação Kafka
}


// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
//      RESULTADO: CÓDIGO SOLID COMPLETO!
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ S: Cada classe uma responsabilidade
✅ O: Extensível com novos processors
✅ L: Qualquer processor substituível
✅ I: Interfaces pequenas e focadas
✅ D: Dependências invertidas
```

---

## 8. Violações Comuns e Correções

### Violação 1: God Class

```java
// ❌ ERRO
public class PaymentManager {
    public void validate() { }
    public void calculate() { }
    public void save() { }
    public void send() { }
    public void generatePdf() { }
    // ... 50 métodos
}

// ✅ CORRETO: Separar responsabilidades
public class PaymentValidator { }
public class TaxCalculator { }
public class PaymentRepository { }
public class PaymentNotifier { }
public class PdfGenerator { }
```

### Violação 2: if/else Infinitos

```java
// ❌ ERRO: Viola OCP
if (type == "A") { }
else if (type == "B") { }
else if (type == "C") { }
// ... 50 elses

// ✅ CORRETO: Strategy Pattern
interface Processor { }
class ProcessorA implements Processor { }
class ProcessorB implements Processor { }
```

### Violação 3: new Operator em Todo Lugar

```java
// ❌ ERRO: Viola DIP
public class Service {
    private Repository repo = new MySqlRepository();  // ❌ new!
}

// ✅ CORRETO: Injeção de dependência
public class Service {
    private final Repository repo;

    public Service(Repository repo) {  // ✅ Injetado!
        this.repo = repo;
    }
}
```

---

## 9. Checklist SOLID

```
ANTES DE COMMITAR CÓDIGO:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

☐ S: Cada classe tem UMA responsabilidade?
☐ S: Consigo descrever classe sem usar "E"?
☐ S: Classe tem menos de 200 linhas?

☐ O: Se adicionar comportamento, preciso modificar código?
☐ O: Usei interfaces/abstrações em vez de if/else?
☐ O: Código está extensível?

☐ L: Subtipos são substituíveis?
☐ L: Não lanço UnsupportedOperationException?
☐ L: Testes passam com todos os subtipos?

☐ I: Interfaces têm menos de 5 métodos?
☐ I: Interfaces são coesas?
☐ I: Implementações não jogam UnsupportedOperationException?

☐ D: Dependo de abstrações (interfaces)?
☐ D: Não uso "new" para dependências?
☐ D: Uso injeção de dependência?
```

---

## 10. Exercícios Práticos

### Exercício 1: Refatorar God Class

Refatore esta classe aplicando SRP:

```java
public class OrderManager {
    public void validateOrder(Order order) { }
    public void calculateTotal(Order order) { }
    public void applyDiscount(Order order) { }
    public void saveToDatabase(Order order) { }
    public void sendEmail(Order order) { }
    public void generateInvoice(Order order) { }
    public void publishEvent(Order order) { }
}
```

**Dica:** Criar classes: OrderValidator, PriceCalculator, OrderRepository, EmailNotifier, InvoiceGenerator, EventPublisher.

### Exercício 2: Implementar Strategy Pattern

Implemente diferentes estratégias de desconto seguindo OCP:

```java
// Crie interface DiscountStrategy e implementações:
// - NoDiscount (0%)
// - BlackFridayDiscount (50%)
// - CouponDiscount (valor fixo)
// - LoyaltyDiscount (baseado em pontos)
```

### Exercício 3: Aplicar DIP

Refatore para seguir DIP:

```java
public class OrderService {
    private MySqlOrderRepository repository = new MySqlOrderRepository();
    private SmtpEmailSender emailSender = new SmtpEmailSender();
}
```

**Dica:** Criar interfaces OrderRepository e EmailSender!

---

## Conclusão

Parabéns! 🎉 Você domina os SOLID Principles!

**O que você aprendeu:**
✅ S - Single Responsibility Principle
✅ O - Open/Closed Principle
✅ L - Liskov Substitution Principle
✅ I - Interface Segregation Principle
✅ D - Dependency Inversion Principle

**Lembre-se:**
> "SOLID não é sobre seguir regras rigidamente.
> É sobre escrever código que é fácil de entender, testar e manter."

**Próximos passos:**
1. Refatore código existente aplicando SOLID
2. Revise Pull Requests com olhar SOLID
3. Pratique com exercícios acima
4. Leia: "Clean Code" (Uncle Bob)

🚀 Agora construa software de qualidade com SOLID!
