package com.quickcommerce.order.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a phone number matches an accepted format.
 *
 * <p>Always accepts Zambian numbers.  Indian numbers are additionally
 * accepted when the {@code phone.accept-indian-numbers} flag is {@code true}
 * (controlled by the {@code ACCEPT_INDIAN_PHONE_NUMBERS} environment variable).
 *
 * <p>{@code null} / blank values pass this constraint — pair with
 * {@code @NotBlank} / {@code @NotNull} to enforce presence.
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "Must be a valid Zambian mobile number (e.g. 0977123456, +260977123456)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
