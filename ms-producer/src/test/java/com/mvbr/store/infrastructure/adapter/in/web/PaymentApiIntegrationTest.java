package com.mvbr.store.infrastructure.adapter.in.web;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * API Integration Tests using REST Assured.
 *
 * Tests REAL HTTP calls with full Spring Boot context.
 * Validates:
 * - HTTP status codes
 * - JSON response structure
 * - Error messages
 * - Request/Response contracts
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Payment API - Integration Tests (REST Assured)")
class PaymentApiIntegrationTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/payments";
    }

    // =======================================
    //      SUCCESS SCENARIOS (HTTP 200)
    // =======================================

    @Test
    @DisplayName("POST /approved - Should return 200 with valid payment data")
    void shouldReturn200WithValidPaymentData() {
        String requestBody = """
            {
                "paymentId": "pay_123",
                "userId": "user_456",
                "amount": 100.50,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("paymentId", equalTo("pay_123"))
            .body("userId", equalTo("user_456"))
            .body("amount", equalTo(100.50f))
            .body("currency", equalTo("USD"))
            .body("status", equalTo("APPROVED"))
            .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("POST /approved - Should accept minimum valid amount (0.01)")
    void shouldAcceptMinimumValidAmount() {
        String requestBody = """
            {
                "paymentId": "pay_min",
                "userId": "user_1",
                "amount": 0.01,
                "currency": "BRL"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(200)
            .body("amount", equalTo(0.01f))
            .body("currency", equalTo("BRL"));
    }

    @Test
    @DisplayName("POST /approved - Should accept maximum valid amount")
    void shouldAcceptMaximumValidAmount() {
        String requestBody = """
            {
                "paymentId": "pay_max",
                "userId": "user_1",
                "amount": 999999999.99,
                "currency": "EUR"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(200)
            .body("amount", equalTo(999999999.99f));
    }

    @Test
    @DisplayName("POST /approved - Should accept valid ISO 4217 currencies")
    void shouldAcceptValidCurrencies() {
        String[] validCurrencies = {"USD", "EUR", "BRL", "GBP", "JPY"};

        for (String currency : validCurrencies) {
            String requestBody = String.format("""
                {
                    "paymentId": "pay_%s",
                    "userId": "user_1",
                    "amount": 100.00,
                    "currency": "%s"
                }
                """, currency.toLowerCase(), currency);

            given()
                .contentType(ContentType.JSON)
                .body(requestBody)
            .when()
                .post("/approved")
            .then()
                .statusCode(200)
                .body("currency", equalTo(currency));
        }
    }

    // =======================================
    //      VALIDATION ERRORS (HTTP 400)
    // =======================================

    @Test
    @DisplayName("POST /approved - Should return 400 when paymentId is null")
    void shouldReturn400WhenPaymentIdIsNull() {
        String requestBody = """
            {
                "paymentId": null,
                "userId": "user_1",
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("status", equalTo(400))
            .body("error", equalTo("Bad Request"))
            .body("message", containsString("Validation failed"))
            .body("path", equalTo("/api/payments/approved"))
            .body("errors", notNullValue())
            .body("errors.size()", greaterThan(0))
            .body("errors.find { it.field == 'paymentId' }", notNullValue())
            .body("errors.find { it.field == 'paymentId' }.message", containsString("cannot be blank"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when paymentId is blank")
    void shouldReturn400WhenPaymentIdIsBlank() {
        String requestBody = """
            {
                "paymentId": "   ",
                "userId": "user_1",
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("status", equalTo(400))
            .body("errors.find { it.field == 'paymentId' }", notNullValue());
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when userId is null")
    void shouldReturn400WhenUserIdIsNull() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": null,
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'userId' }", notNullValue())
            .body("errors.find { it.field == 'userId' }.message", containsString("User ID cannot be"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when amount is null")
    void shouldReturn400WhenAmountIsNull() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": null,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'amount' }", notNullValue())
            .body("errors.find { it.field == 'amount' }.message", containsString("Amount cannot be null"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when amount is zero")
    void shouldReturn400WhenAmountIsZero() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": 0,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'amount' }", notNullValue());
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when amount is negative")
    void shouldReturn400WhenAmountIsNegative() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": -50.00,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'amount' }.message", containsString("positive"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when amount has more than 2 decimal places")
    void shouldReturn400WhenAmountHasMoreThan2DecimalPlaces() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": 100.123,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'amount' }.message", containsString("exactly 2 after"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when currency is null")
    void shouldReturn400WhenCurrencyIsNull() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": 100.00,
                "currency": null
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'currency' }", notNullValue())
            .body("errors.find { it.field == 'currency' }.message", containsString("Currency cannot be"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when currency is not 3 characters")
    void shouldReturn400WhenCurrencyIsNot3Characters() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": 100.00,
                "currency": "US"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'currency' }.message", containsString("exactly 3 characters"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when currency is not uppercase")
    void shouldReturn400WhenCurrencyIsNotUppercase() {
        String requestBody = """
            {
                "paymentId": "pay_1",
                "userId": "user_1",
                "amount": 100.00,
                "currency": "usd"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'currency' }.message", containsString("uppercase letters"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 with multiple validation errors")
    void shouldReturn400WithMultipleValidationErrors() {
        String requestBody = """
            {
                "paymentId": null,
                "userId": "",
                "amount": -10,
                "currency": "us"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("status", equalTo(400))
            .body("errors", notNullValue())
            .body("errors.size()", greaterThan(3))
            .body("message", containsString("Validation failed"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when JSON is malformed")
    void shouldReturn400WhenJsonIsMalformed() {
        String malformedJson = "{ invalid json }";

        given()
            .contentType(ContentType.JSON)
            .body(malformedJson)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("status", equalTo(400))
            .body("message", containsString("Malformed JSON"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when paymentId contains invalid characters")
    void shouldReturn400WhenPaymentIdContainsInvalidCharacters() {
        String requestBody = """
            {
                "paymentId": "pay@123!",
                "userId": "user_1",
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'paymentId' }.message", containsString("alphanumeric"));
    }

    @Test
    @DisplayName("POST /approved - Should return 400 when paymentId exceeds 100 characters")
    void shouldReturn400WhenPaymentIdExceeds100Characters() {
        String longPaymentId = "a".repeat(101);
        String requestBody = String.format("""
            {
                "paymentId": "%s",
                "userId": "user_1",
                "amount": 100.00,
                "currency": "USD"
            }
            """, longPaymentId);

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("errors.find { it.field == 'paymentId' }.message", containsString("between 1 and 100"));
    }

    // =======================================
    //      RESPONSE STRUCTURE VALIDATION
    // =======================================

    @Test
    @DisplayName("POST /approved - Should return complete response structure on success")
    void shouldReturnCompleteResponseStructureOnSuccess() {
        String requestBody = """
            {
                "paymentId": "pay_structure_test",
                "userId": "user_structure",
                "amount": 250.75,
                "currency": "EUR"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(200)
            .body("$", hasKey("paymentId"))
            .body("$", hasKey("userId"))
            .body("$", hasKey("amount"))
            .body("$", hasKey("currency"))
            .body("$", hasKey("status"))
            .body("$", hasKey("createdAt"))
            .body("paymentId", instanceOf(String.class))
            .body("userId", instanceOf(String.class))
            .body("amount", instanceOf(Number.class))
            .body("currency", instanceOf(String.class))
            .body("status", instanceOf(String.class))
            .body("createdAt", instanceOf(Number.class));
    }

    @Test
    @DisplayName("POST /approved - Should return complete error structure on validation failure")
    void shouldReturnCompleteErrorStructureOnValidationFailure() {
        String requestBody = """
            {
                "paymentId": null,
                "userId": "user_1",
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(400)
            .body("$", hasKey("timestamp"))
            .body("$", hasKey("status"))
            .body("$", hasKey("error"))
            .body("$", hasKey("message"))
            .body("$", hasKey("path"))
            .body("$", hasKey("errors"))
            .body("timestamp", notNullValue())
            .body("status", equalTo(400))
            .body("error", equalTo("Bad Request"))
            .body("path", equalTo("/api/payments/approved"))
            .body("errors[0]", hasKey("field"))
            .body("errors[0]", hasKey("message"));
    }

    // =======================================
    //      CONTENT TYPE VALIDATION
    // =======================================

    @Test
    @DisplayName("POST /approved - Should return correct Content-Type header")
    void shouldReturnCorrectContentTypeHeader() {
        String requestBody = """
            {
                "paymentId": "pay_headers",
                "userId": "user_1",
                "amount": 100.00,
                "currency": "USD"
            }
            """;

        given()
            .contentType(ContentType.JSON)
            .body(requestBody)
        .when()
            .post("/approved")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .header("Content-Type", containsString("application/json"));
    }
}
