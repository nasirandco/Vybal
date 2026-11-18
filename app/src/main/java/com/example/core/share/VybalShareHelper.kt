package com.example.core.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.VybalPost

object VybalShareHelper {
    const val APP_LIVE_URL = "https://ais-pre-byr5jz7urrrzandtjwrhgg-466400087499.europe-west3.run.app"

    fun buildPostLink(postId: String): String = "https://vybal.app/post/$postId"

    fun buildSharePayload(post: VybalPost, authorHandle: String): String {
        val handle = authorHandle.ifBlank { post.userId.take(8) }
        val snippet = post.text.take(140).let { if (post.text.length > 140) "$it…" else it }
        val link = buildPostLink(post.id)
        return buildString {
            append("@$handle on VYBAL")
            if (snippet.isNotBlank()) {
                append(": \"")
                append(snippet)
                append("\"")
            }
            append("\n")
            append(link)
        }
    }

    fun launchNativeShare(context: Context, post: VybalPost, authorHandle: String): Boolean {
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Post on VYBAL")
                putExtra(Intent.EXTRA_TEXT, buildSharePayload(post, authorHandle))
            }
            val chooser = Intent.createChooser(sendIntent, "Share VYBAL post")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun copyPostLink(context: Context, postId: String): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("VYBAL Post Link", buildPostLink(postId)))
            true
        }.getOrDefault(false)
    }

    fun openAppInstallPage(context: Context): Boolean {
        return runCatching {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(APP_LIVE_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            true
        }.getOrDefault(false)
    }

    fun shareAppLink(context: Context): Boolean {
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Get VYBAL")
                putExtra(Intent.EXTRA_TEXT, "Open and install VYBAL on your phone:\n$APP_LIVE_URL")
            }
            val chooser = Intent.createChooser(sendIntent, "Share VYBAL app")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun copyAppLink(context: Context): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("VYBAL App Link", APP_LIVE_URL))
            true
        }.getOrDefault(false)
    }
}
