package com.local.smsmail;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Logic {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}$");
    public static boolean email(String value) { return value != null && value.length() <= 254 && EMAIL.matcher(value).matches() && !value.contains(".."); }
    public static boolean host(String value) { return value != null && value.length() <= 253 && value.matches("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?") && !value.contains(".."); }
    public static String id(String sender, long timestamp, String body) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            byte[] bytes = hash.digest((sender.length() + ":" + sender + ":" + timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static boolean matches(String sender, String body, String senderRule, String keyword) {
        boolean number = senderRule.trim().isEmpty();
        for (String rule : senderRule.split("[,，\\n]")) if (!rule.trim().isEmpty() && sender.trim().equals(rule.trim())) number = true;
        return number && (keyword.trim().isEmpty() || body.contains(keyword));
    }
    public static String masked(String sender) {
        return sender.length() > 5 ? sender.substring(0, 3) + "•••" + sender.substring(sender.length()-2) : sender;
    }
    private Logic() {}
}
