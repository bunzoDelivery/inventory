package com.quickcommerce.order.util;

import com.quickcommerce.order.domain.MobileNetwork;
import com.quickcommerce.order.payment.dto.InitiatePaymentRequest;
import com.quickcommerce.order.dto.CreateOrderRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PhoneValidator} / {@link ValidPhone}.
 *
 * Uses {@link ReflectionTestUtils} to inject the {@code acceptIndianNumbers} flag
 * without requiring a full Spring context, keeping these tests fast.
 *
 * Two nested contexts:
 *  - {@link IndianNumbersAllowed}  — flag=true  (testing from India)
 *  - {@link IndianNumbersBlocked}  — flag=false (Zambia-only production)
 */
class PhoneValidatorTest {

    private Validator beanValidator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        beanValidator = factory.getValidator();
    }

    private PhoneValidator validatorWith(boolean acceptIndian) {
        PhoneValidator v = new PhoneValidator();
        ReflectionTestUtils.setField(v, "acceptIndianNumbers", acceptIndian);
        return v;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Null / blank contract
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("null returns true — presence enforced by @NotBlank on the field")
    void nullReturnsTrue() {
        assertThat(validatorWith(true).isValid(null, null)).isTrue();
        assertThat(validatorWith(false).isValid(null, null)).isTrue();
    }

    @Test
    @DisplayName("blank string returns true — presence enforced by @NotBlank on the field")
    void blankReturnsTrue() {
        assertThat(validatorWith(true).isValid("   ", null)).isTrue();
        assertThat(validatorWith(false).isValid("   ", null)).isTrue();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Zambian numbers — valid regardless of the flag
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Zambian numbers (always accepted)")
    class ZambianNumbers {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "0977123456",       // Airtel ZM, 0-prefix
                "0967123456",       // MTN ZM, 0-prefix
                "+260977123456",    // E.164 full, Airtel
                "+260967123456",    // E.164 full, MTN
                "260977123456",     // E.164 without leading +
                "977123456",        // bare local Airtel (9 digits after prefix drop)
                "967123456",        // bare local MTN
        })
        void areValidWhenFlagTrue(String phone) {
            assertThat(validatorWith(true).isValid(phone, null))
                    .as("Expected '%s' to be valid (flag=true)", phone)
                    .isTrue();
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "0977123456",
                "+260977123456",
                "260967123456",
                "977123456",
        })
        void areValidWhenFlagFalse(String phone) {
            assertThat(validatorWith(false).isValid(phone, null))
                    .as("Expected '%s' to be valid (flag=false)", phone)
                    .isTrue();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Indian numbers — accepted only when flag=true
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Indian numbers")
    class IndianNumbers {

        @ParameterizedTest(name = "[{0}] accepted when flag=true")
        @ValueSource(strings = {
                "9876543210",       // bare 10-digit Jio/Airtel IN
                "+919876543210",    // E.164 with + prefix
                "09876543210",      // 0-prefix
                "6543210987",       // starts with 6
                "7654321098",       // starts with 7
                "8765432109",       // starts with 8
        })
        void areValidWhenFlagTrue(String phone) {
            assertThat(validatorWith(true).isValid(phone, null))
                    .as("Expected '%s' to be valid (flag=true)", phone)
                    .isTrue();
        }

        @ParameterizedTest(name = "[{0}] rejected when flag=false")
        @ValueSource(strings = {
                "9876543210",
                "+919876543210",
                "6543210987",
        })
        void areRejectedWhenFlagFalse(String phone) {
            assertThat(validatorWith(false).isValid(phone, null))
                    .as("Expected '%s' to be invalid (flag=false)", phone)
                    .isFalse();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Invalid numbers — rejected regardless of flag
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid numbers (always rejected)")
    class InvalidNumbers {

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "12345",             // too short
                "000000000000000",   // too long
                "abc12345678",       // non-numeric
                "+447911123456",     // UK number
                "+12125551234",      // US number
                "5123456789",        // Indian — starts with 5 (not valid Indian prefix)
        })
        void areRejectedWhenFlagTrue(String phone) {
            assertThat(validatorWith(true).isValid(phone, null))
                    .as("Expected '%s' to be invalid (flag=true)", phone)
                    .isFalse();
        }

        @ParameterizedTest(name = "[{0}]")
        @ValueSource(strings = {
                "12345",
                "+447911123456",
                "abc12345678",
        })
        void areRejectedWhenFlagFalse(String phone) {
            assertThat(validatorWith(false).isValid(phone, null))
                    .as("Expected '%s' to be invalid (flag=false)", phone)
                    .isFalse();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DTO-level bean validation (annotation wired into fields)
    // Note: the default PhoneValidator built by Validation.buildDefaultValidatorFactory()
    // won't have Spring @Value injection, so acceptIndianNumbers defaults to the field
    // default (false). We test the structural wiring here, not the flag logic.
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("@ValidPhone annotation on DTOs (structural wiring)")
    class DtoAnnotationWiring {

        @Test
        @DisplayName("InitiatePaymentRequest: valid Zambian number passes validation")
        void initiatePaymentRequest_zambianPhone_passes() {
            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setPaymentPhone("0977123456");
            req.setMobileNetwork(MobileNetwork.AIRTEL);

            Set<ConstraintViolation<InitiatePaymentRequest>> violations = beanValidator.validate(req);

            assertThat(violations)
                    .extracting(cv -> cv.getPropertyPath().toString())
                    .doesNotContain("paymentPhone");
        }

        @Test
        @DisplayName("InitiatePaymentRequest: blank phone triggers @NotBlank")
        void initiatePaymentRequest_blankPhone_triggersNotBlank() {
            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setPaymentPhone("");
            req.setMobileNetwork(MobileNetwork.AIRTEL);

            Set<ConstraintViolation<InitiatePaymentRequest>> violations = beanValidator.validate(req);

            assertThat(violations)
                    .extracting(cv -> cv.getPropertyPath().toString())
                    .contains("paymentPhone");
        }

        @Test
        @DisplayName("InitiatePaymentRequest: missing mobileNetwork triggers @NotNull")
        void initiatePaymentRequest_missingNetwork_triggersNotNull() {
            InitiatePaymentRequest req = new InitiatePaymentRequest();
            req.setPaymentPhone("0977123456");

            Set<ConstraintViolation<InitiatePaymentRequest>> violations = beanValidator.validate(req);

            assertThat(violations)
                    .extracting(cv -> cv.getPropertyPath().toString())
                    .contains("mobileNetwork");
        }

        @Test
        @DisplayName("CreateOrderRequest.DeliveryRequest: valid Zambian delivery phone passes")
        void deliveryRequest_zambianPhone_passes() {
            CreateOrderRequest.DeliveryRequest delivery = new CreateOrderRequest.DeliveryRequest();
            delivery.setPhone("0977123456");
            delivery.setLatitude(-15.4);
            delivery.setLongitude(28.3);
            delivery.setAddress("10 Cairo Rd, Lusaka");

            Set<ConstraintViolation<CreateOrderRequest.DeliveryRequest>> violations =
                    beanValidator.validate(delivery);

            assertThat(violations)
                    .extracting(cv -> cv.getPropertyPath().toString())
                    .doesNotContain("phone");
        }

        @Test
        @DisplayName("CreateOrderRequest.DeliveryRequest: missing phone triggers @NotNull")
        void deliveryRequest_nullPhone_triggersNotNull() {
            CreateOrderRequest.DeliveryRequest delivery = new CreateOrderRequest.DeliveryRequest();
            delivery.setPhone(null);
            delivery.setLatitude(-15.4);
            delivery.setLongitude(28.3);
            delivery.setAddress("10 Cairo Rd, Lusaka");

            Set<ConstraintViolation<CreateOrderRequest.DeliveryRequest>> violations =
                    beanValidator.validate(delivery);

            assertThat(violations)
                    .extracting(cv -> cv.getPropertyPath().toString())
                    .contains("phone");
        }
    }
}
