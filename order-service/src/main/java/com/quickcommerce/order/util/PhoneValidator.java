package com.quickcommerce.order.util;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Spring-managed {@link ConstraintValidator} for the {@link ValidPhone} annotation.
 *
 * <p>Because this is a {@code @Component}, Spring Boot's {@code LocalValidatorFactoryBean}
 * injects it automatically — which means {@code @Value} works here, letting us
 * read runtime configuration instead of relying on compile-time constants.
 */
@Component
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    @Value("${phone.accept-indian-numbers:true}")
    private boolean acceptIndianNumbers;

    @Override
    public boolean isValid(String phone, ConstraintValidatorContext ctx) {
        if (phone == null || phone.isBlank()) {
            return true; // @NotBlank / @NotNull on the field enforce presence
        }

        if (phone.matches(PhoneConstants.ZAMBIAN_REGEX)) {
            return true;
        }

        if (acceptIndianNumbers && phone.matches(PhoneConstants.INDIAN_REGEX)) {
            return true;
        }

        if (ctx != null) {
            ctx.disableDefaultConstraintViolation();
            String msg = acceptIndianNumbers
                    ? "Must be a valid Zambian (e.g. 0977123456, +260977123456) or Indian (e.g. 9876543210) mobile number"
                    : "Must be a valid Zambian mobile number (e.g. 0977123456, +260977123456)";
            ctx.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
        }
        return false;
    }
}
