package com.paneer.diohunter

/**
 * Parses plaintext HTTP/1.x from a TCP byte stream.
 * HTTPS is recorded as TLS CONNECT-style metadata when SNI is visible;
 * full body capture for TLS needs user CA (documented in README).
 */
object HttpParser {

    data class Partial(
        var method: String = "",
        var path: String = "",
        var query: String? = null,
        var host: String = "",
        var scheme: String = "http",
        var reqHeaders: MutableMap<String, String> = linkedMapOf(),
        var respHeaders: MutableMap<String, String> = linkedMapOf(),
        var status: Int? = null,
        var reqBody: StringBuilder = StringBuilder(),
        var respBody: StringBuilder = StringBuilder(),
        var isResponse: Boolean = false,
        var complete: Boolean = false,
        var startMs: Long = System.currentTimeMillis()
    )

    fun tryParseRequest(bytes: ByteArray): Partial? {
        val text = bytes.toString(Charsets.ISO_8859_1)
        val headerEnd = text.indexOf("\r\n\r\n")
        if (headerEnd < 0) return null
        val lines = text.substring(0, headerEnd).split("\r\n")
        if (lines.isEmpty()) return null
        val reqLine = lines[0]
        val parts = reqLine.split(" ")
        if (parts.size < 2) return null
        val method = parts[0].uppercase()
        if (method !in setOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "CONNECT")) {
            return null
        }
        var pathFull = parts[1]
        var query: String? = null
        val qIdx = pathFull.indexOf('?')
        if (qIdx >= 0) {
            query = pathFull.substring(qIdx + 1)
            pathFull = pathFull.substring(0, qIdx)
        }
        val headers = linkedMapOf<String, String>()
        for (i in 1 until lines.size) {
            val c = lines[i].indexOf(':')
            if (c > 0) {
                headers[lines[i].substring(0, c).trim()] = lines[i].substring(c + 1).trim()
            }
        }
        val host = headers["Host"] ?: headers["host"] ?: ""
        val body = if (headerEnd + 4 < text.length) text.substring(headerEnd + 4) else ""
        return Partial(
            method = method,
            path = pathFull,
            query = query,
            host = host,
            scheme = "http",
            reqHeaders = headers,
            reqBody = StringBuilder(body.take(64_000))
        )
    }

    fun tryParseResponse(bytes: ByteArray, into: Partial): Boolean {
        val text = bytes.toString(Charsets.ISO_8859_1)
        val headerEnd = text.indexOf("\r\n\r\n")
        if (headerEnd < 0) return false
        val lines = text.substring(0, headerEnd).split("\r\n")
        if (lines.isEmpty() || !lines[0].startsWith("HTTP/")) return false
        val statusParts = lines[0].split(" ")
        into.status = statusParts.getOrNull(1)?.toIntOrNull()
        for (i in 1 until lines.size) {
            val c = lines[i].indexOf(':')
            if (c > 0) {
                into.respHeaders[lines[i].substring(0, c).trim()] = lines[i].substring(c + 1).trim()
            }
        }
        val body = if (headerEnd + 4 < text.length) text.substring(headerEnd + 4) else ""
        into.respBody.append(body.take(64_000))
        into.complete = true
        return true
    }

    /** TLS ClientHello SNI extraction (best-effort). */
    fun extractSni(bytes: ByteArray): String? {
        try {
            if (bytes.size < 43) return null
            if (bytes[0] != 0x16.toByte()) return null
            var i = 5
            if (i >= bytes.size || bytes[i] != 0x01.toByte()) return null
            i += 4
            i += 2 + 32
            if (i >= bytes.size) return null
            val sessionLen = bytes[i].toInt() and 0xff
            i += 1 + sessionLen
            if (i + 2 > bytes.size) return null
            val csLen = ((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)
            i += 2 + csLen
            if (i >= bytes.size) return null
            val compLen = bytes[i].toInt() and 0xff
            i += 1 + compLen
            if (i + 2 > bytes.size) return null
            val extLen = ((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)
            i += 2
            val extEnd = i + extLen
            while (i + 4 <= extEnd && i + 4 <= bytes.size) {
                val type = ((bytes[i].toInt() and 0xff) shl 8) or (bytes[i + 1].toInt() and 0xff)
                val len = ((bytes[i + 2].toInt() and 0xff) shl 8) or (bytes[i + 3].toInt() and 0xff)
                i += 4
                if (type == 0) {
                    var j = i + 2
                    if (j + 3 <= i + len && j + 3 <= bytes.size) {
                        val nameType = bytes[j].toInt() and 0xff
                        val nameLen = ((bytes[j + 1].toInt() and 0xff) shl 8) or (bytes[j + 2].toInt() and 0xff)
                        j += 3
                        if (nameType == 0 && j + nameLen <= bytes.size) {
                            return String(bytes, j, nameLen, Charsets.UTF_8)
                        }
                    }
                    return null
                }
                i += len
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun toRecord(p: Partial, packageHint: String?): HttpRecord {
        val reqPreview = p.reqBody.toString().take(8000).ifBlank { null }
        val respPreview = p.respBody.toString().take(8000).ifBlank { null }
        return HttpRecord(
            method = p.method.ifBlank { "TLS" },
            scheme = p.scheme,
            host = p.host,
            path = p.path.ifBlank { "/" },
            query = p.query,
            status = p.status,
            reqHeaders = p.reqHeaders.toMap(),
            respHeaders = p.respHeaders.toMap(),
            reqBodyPreview = reqPreview,
            respBodyPreview = respPreview,
            durationMs = System.currentTimeMillis() - p.startMs,
            packageHint = packageHint
        )
    }
}
