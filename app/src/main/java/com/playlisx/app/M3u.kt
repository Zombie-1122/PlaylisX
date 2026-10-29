package com.playlisx.app

object M3u {
    private val attrRegex = Regex("([A-Za-z0-9_\\-]+)=\"([^\"]*)\"")

    fun parse(text: String): Playlist {
        var header = "#EXTM3U"
        var sawHeader = false
        val warnings = ArrayList<String>()
        val channels = ArrayList<Channel>()
        var inf: String? = null
        var infLine = 0
        val extras = ArrayList<String>()
        var n = 0

        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            n++
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXTM3U")) {
                header = l
                sawHeader = true
            } else if (l.startsWith("#EXTINF")) {
                if (inf != null) warnings.add("Line $infLine: #EXTINF has no stream URL")
                inf = l
                infLine = n
                extras.clear()
            } else if (l.startsWith("#")) {
                if (inf != null) extras.add(l)
            } else {
                val cur = inf
                if (cur == null) {
                    warnings.add("Line $n: URL without #EXTINF")
                    channels.add(Channel(name = l.substringAfterLast('/'), url = l))
                } else {
                    channels.add(buildChannel(cur, l, extras.toList()))
                }
                inf = null
                extras.clear()
            }
        }
        if (inf != null) warnings.add("Line $infLine: #EXTINF has no stream URL")
        if (!sawHeader) warnings.add("Missing #EXTM3U header")
        return Playlist(header, channels, warnings)
    }

    private fun buildChannel(inf: String, url: String, extras: List<String>): Channel {
        val body = inf.substringAfter(':', "")
        var inQuote = false
        var comma = -1
        for (i in body.indices) {
            val c = body[i]
            if (c == '"') inQuote = !inQuote
            else if (c == ',' && !inQuote) {
                comma = i
                break
            }
        }
        val head = if (comma >= 0) body.substring(0, comma) else body
        val name = if (comma >= 0) body.substring(comma + 1).trim() else ""
        val duration = head.trim().takeWhile { !it.isWhitespace() }.ifEmpty { "-1" }
        val attrs = LinkedHashMap<String, String>()
        attrRegex.findAll(head).forEach { attrs[it.groupValues[1]] = it.groupValues[2] }
        return Channel(duration, attrs, name, url, extras)
    }

    fun extinf(c: Channel, keep: (String) -> Boolean = { true }): String {
        val sb = StringBuilder("#EXTINF:").append(c.duration)
        for ((k, v) in c.attrs) {
            if (keep(k)) sb.append(' ').append(k).append("=\"").append(v.replace("\"", "'")).append('"')
        }
        sb.append(',').append(c.name)
        return sb.toString()
    }
}
