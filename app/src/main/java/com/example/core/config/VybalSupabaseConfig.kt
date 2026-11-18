package com.example.core.config

import com.example.BuildConfig

/**
 * Centralized configuration for the existing VYBAL Supabase project.
 * Credentials are injected via Secrets Gradle Plugin (.env / BuildConfig) and never hardcoded.
 */
object VybalSupabaseConfig {
    private const val PLACEHOLDER_URL = "YOUR_PROJECT_REF"
    private const val PLACEHOLDER_KEY = "YOUR_SUPABASE_ANON_KEY"

    val rawUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val anonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = rawUrl.startsWith("https://") &&
            !rawUrl.contains(PLACEHOLDER_URL, ignoreCase = true) &&
            anonKey.isNotBlank() &&
            !anonKey.contains(PLACEHOLDER_KEY, ignoreCase = true)

    val baseUrlWithSlash: String
        get() = if (isConfigured) "$rawUrl/" else "https://placeholder.supabase.co/"

    val projectHostDisplay: String
        get() = if (isConfigured) {
            rawUrl.removePrefix("https://").removePrefix("http://")
        } else {
            "Not configured"
        }
}

/**
 * Contract referencing existing VYBAL Supabase tables and storage buckets.
 * The client never creates, migrates, or duplicates schema objects.
 */
object VybalSchemaContract {
    const val TABLE_PROFILES = "profiles"
    const val TABLE_POSTS = "posts"
    const val TABLE_NOTIFICATIONS = "notifications"
    const val TABLE_MESSAGES = "messages"
    const val TABLE_POST_REACTIONS = "post_reactions"
    const val TABLE_COMMENTS = "comments"
    const val TABLE_COMMENT_LIKES = "comment_likes"
    const val TABLE_SAVED_COLLECTIONS = "saved_collections"
    const val TABLE_SAVED_POSTS = "saved_posts"
    const val TABLE_REPOSTS = "reposts"
    const val TABLE_FOLLOWS = "follows"
    const val TABLE_HIDDEN_POSTS = "hidden_posts"
    const val TABLE_POST_REPORTS = "post_reports"
    const val TABLE_STORIES = "stories"
    const val TABLE_VERIFICATION_REQUESTS = "verification_requests"
    const val TABLE_ADMIN_PERMISSIONS = "admin_permissions"
    const val TABLE_PLATFORM_ANNOUNCEMENTS = "platform_announcements"
    const val TABLE_PLATFORM_SETTINGS = "platform_settings"
    const val TABLE_ADMIN_AUDIT_LOGS = "admin_audit_logs"
    const val BUCKET_POSTS = "posts"
    const val DEFAULT_PAGE_SIZE = 15
    const val COMMENTS_PAGE_SIZE = 20
    const val PERMANENT_SUPER_ADMIN_EMAIL = "nasirandco001@gmail.com"
}
