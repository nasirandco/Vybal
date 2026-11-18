package com.example.data.auth

object AuthValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,24}$")

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return "Email address is required."
        if (!EMAIL_REGEX.matches(trimmed)) return "Enter a valid email address."
        return null
    }

    fun validatePassword(password: String): String? {
        if (password.isEmpty()) return "Password is required."
        if (password.length < 6) return "Password must be at least 6 characters."
        return null
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        if (confirmPassword.isEmpty()) return "Please confirm your password."
        if (password != confirmPassword) return "Passwords do not match."
        return null
    }

    fun validateUsername(username: String): String? {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return "Username is required."
        if (!USERNAME_REGEX.matches(trimmed)) {
            return "Username must be 3-24 characters (letters, numbers, or underscores)."
        }
        return null
    }

    fun mapSupabaseAuthError(rawMessage: String?, httpCode: Int): String {
        val lower = rawMessage.orEmpty().lowercase()
        return when {
            lower.contains("invalid login credentials") || lower.contains("invalid_credentials") ->
                "Incorrect email or password. Please try again."
            lower.contains("email not confirmed") ->
                "Please verify your email address before signing in."
            lower.contains("user already registered") || lower.contains("already been registered") ->
                "An account with this email already exists. Try signing in instead."
            lower.contains("rate limit") || httpCode == 429 ->
                "Too many attempts. Please wait a moment before trying again."
            lower.contains("weak password") ->
                "Choose a stronger password with at least 6 characters."
            !rawMessage.isNullOrBlank() -> rawMessage
            else -> "Authentication request failed ($httpCode)."
        }
    }
}
