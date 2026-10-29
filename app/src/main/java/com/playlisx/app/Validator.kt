package com.playlisx.app

object Validator {
    private val schemes = setOf("http", "https", "rtmp", "rtmps", "rtsp", "udp", "rtp", "mms", "mmsh", "srt")

    fun check(p: Playlist): List<Issue> {
        val out = ArrayList<Issue>()
        p.warnings.take(20).forEach { out.add(Issue(Level.ERROR, it)) }
        if (p.warnings.size > 20) out.add(Issue(Level.ERROR, "...and ${p.warnings.size - 20} more structural warnings"))

        val badUrl = ArrayList<String>()
        var noName = 0
        var noId = 0
        var noLogo = 0
        var noGroup = 0
        val urls = HashMap<String, Int>()
        val names = HashMap<String, Int>()

        for (c in p.channels) {
            val scheme = c.url.substringBefore("://", "").lowercase()
            val rest = c.url.substringAfter("://", "")
            if (scheme !in schemes || rest.isBlank() || c.url.any { it.isWhitespace() }) {
                badUrl.add(c.name.ifEmpty { c.url })
            }
            if (c.name.isBlank()) noName++
            if (c.attrs["tvg-id"].isNullOrBlank()) noId++
            if (c.attrs["tvg-logo"].isNullOrBlank()) noLogo++
            if (c.group.isBlank()) noGroup++
            urls[c.url] = (urls[c.url] ?: 0) + 1
            val key = c.name.lowercase()
            names[key] = (names[key] ?: 0) + 1
        }

        if (badUrl.isNotEmpty()) {
            out.add(Issue(Level.ERROR, "${badUrl.size} channels with invalid or unsupported URLs, e.g. ${badUrl.take(3).joinToString()}"))
        }
        if (noName > 0) out.add(Issue(Level.ERROR, "$noName channels without a name"))

        var dupUrls = 0
        for (v in urls.values) dupUrls += v - 1
        if (dupUrls > 0) out.add(Issue(Level.WARN, "$dupUrls duplicate stream URLs"))
        val dupNames = names.count { it.key.isNotEmpty() && it.value > 1 }
        if (dupNames > 0) out.add(Issue(Level.WARN, "$dupNames channel names appear more than once"))

        if (noId > 0) out.add(Issue(Level.INFO, "$noId channels have no tvg-id (EPG will not match)"))
        if (noLogo > 0) out.add(Issue(Level.INFO, "$noLogo channels have no logo"))
        if (noGroup > 0) out.add(Issue(Level.INFO, "$noGroup channels have no group-title"))

        if (out.none { it.level != Level.INFO }) {
            out.add(0, Issue(Level.INFO, "No structural problems found"))
        }
        return out
    }
}
