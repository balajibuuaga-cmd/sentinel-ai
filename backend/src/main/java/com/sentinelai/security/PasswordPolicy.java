package com.sentinelai.security;

import java.util.regex.Pattern;

public final class PasswordPolicy {

    private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
    private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password.length() < 10) {
            throw new IllegalArgumentException("Password must be at least 10 characters long.");
        }
        if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
            throw new IllegalArgumentException("Password must contain at least one letter and one digit.");
        }
    }
}
