package com.quickcommerce.order.util;

public final class PhoneUtils {

    private PhoneUtils() {}

    /**
     * Masks a phone number for safe display in logs and API responses.
     * E.g. "0971234567" → "097****567"
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }
}
