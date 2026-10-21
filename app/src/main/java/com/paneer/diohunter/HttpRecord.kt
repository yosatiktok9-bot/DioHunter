package com.paneer.diohunter

import java.util.UUID

data class HttpRecord(
    val id: String = UUID.randomUUID().toString(),
    val ts: Long = System.currentTimeMillis(),
    val method: String,
    val scheme: String,
    val host: String,
    val path: String,
    val query: String?,
    val status: Int?,
    val reqHeaders: Map<String, String>,
    val respHeaders: Map<String, String>,
    val reqBodyPreview: String?,
    val respBodyPreview: String?,
    val durationMs: Long?,
    val packageHint: String?
) {
    val url: String
        get() = buildString {
            append(scheme).append("://").append(host)
            if (path.isNotEmpty()) append(path) else append("/")
            if (!query.isNullOrEmpty()) append("?").append(query)
        }

    fun matches(filter: String): Boolean {
        if (filter.isBlank()) return true
        val f = filter.lowercase()
        return method.lowercase().contains(f)
                || host.lowercase().contains(f)
                || path.lowercase().contains(f)
                || (query?.lowercase()?.contains(f) == true)
                || url.lowercase().contains(f)
                || (packageHint?.lowercase()?.contains(f) == true)
    }
}
