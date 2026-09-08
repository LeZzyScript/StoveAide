package com.example.stoveaide.utils

data class PasswordValidationResult(
    val hasMinLength: Boolean = false,
    val hasUppercase: Boolean = false,
    val hasLowercase: Boolean = false,
    val hasDigit: Boolean = false,
    val hasSpecialChar: Boolean = false
) {
    val isValid: Boolean
        get() = hasMinLength && hasUppercase && hasLowercase && hasDigit && hasSpecialChar
}

object PasswordValidator {
    private const val MIN_LENGTH = 8
    private val SPECIAL_CHARS_REGEX = Regex("[^a-zA-Z0-9]")

    fun validate(password: String): PasswordValidationResult {
        return PasswordValidationResult(
            hasMinLength = password.length >= MIN_LENGTH,
            hasUppercase = password.any { it.isUpperCase() },
            hasLowercase = password.any { it.isLowerCase() },
            hasDigit = password.any { it.isDigit() },
            hasSpecialChar = SPECIAL_CHARS_REGEX.containsMatchIn(password)
        )
    }

    fun getErrorMessage(result: PasswordValidationResult): String? {
        if (result.isValid) return null
        val missing = mutableListOf<String>()
        if (!result.hasMinLength) missing.add("at least 8 characters")
        if (!result.hasUppercase) missing.add("1 uppercase letter")
        if (!result.hasLowercase) missing.add("1 lowercase letter")
        if (!result.hasDigit) missing.add("1 number")
        if (!result.hasSpecialChar) missing.add("1 symbol/special character")
        return "Password must include: " + missing.joinToString(", ")
    }
}
