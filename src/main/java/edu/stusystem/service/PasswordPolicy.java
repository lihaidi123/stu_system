package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import org.springframework.http.HttpStatus;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validate(String password, String username) {
        if (password == null || password.length() < 8 || password.length() > 20 || password.equalsIgnoreCase(username)) {
            throw invalid();
        }
        int categories = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) categories++;
        if (password.chars().anyMatch(Character::isUpperCase)) categories++;
        if (password.chars().anyMatch(Character::isDigit)) categories++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) categories++;
        if (categories < 3) throw invalid();
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "密码须为8至20位且包含大小写字母、数字、特殊字符中的至少三类");
    }
}
