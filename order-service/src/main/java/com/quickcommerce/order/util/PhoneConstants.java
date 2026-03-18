package com.quickcommerce.order.util;

/**
 * Shared phone number regex constants used by {@link PhoneValidator}.
 *
 * <p>Zambian numbers: Airtel (097x/077x), MTN (096x/076x), Zamtel (095x).
 * The regex accepts the full E.164 form (260…), local 0-prefix, or bare local digits.
 *
 * <p>Indian numbers: any 10-digit number starting with 6–9 (covers Jio, Airtel IN, Vi, BSNL etc.).
 * Indian support is toggled by {@code phone.accept-indian-numbers} — enabled during
 * development/testing from India, disabled when the app goes live in Zambia.
 */
public final class PhoneConstants {

    /** Zambian mobile number — E.164 (260…), 0-prefix, or bare local digits. First local digit 7 or 9. */
    public static final String ZAMBIAN_REGEX = "^(\\+?260|0)?[79]\\d{8}$";

    /** Indian mobile number — 10-digit, first digit 6–9, optional +91 or 0 prefix. */
    public static final String INDIAN_REGEX  = "^(\\+91|0)?[6-9]\\d{9}$";

    private PhoneConstants() {}
}
