package com.playlisx.app

/** All editable fields of one channel, as plain strings so the edit screen can bind to them. */
data class ChannelDraft(
    val name: String = "",
    val url: String = "",
    val group: String = "",
    val catchup: String = "",
    val catchupDays: String = "",
    val catchupSource: String = "",
    val timeshift: String = "",
    val tvgName: String = "",
    val tvgId: String = "",
    val tvgLogo: String = "",
    val country: String = "",
    val language: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    val radio: Boolean = false,
    val kodiAdaptive: Boolean = false,
    val manifestType: String = "",
    val licenseType: String = "",
    val licenseKey: String = "",
    val drmLegacy: String = "",
    val serverCert: String = "",
    val mimeType: String = "",
    val kodiOther: String = "",
    val customTags: List<Pair<String, String>> = emptyList(),
    val otherExtras: List<String> = emptyList()
)

object ChannelEdit {
    private val known = setOf(
        "group-title", "tvg-name", "tvg-id", "tvg-logo", "tvg-country", "tvg-language",
        "catchup", "catchup-days", "catchup-source", "timeshift", "radio"
    )
    private const val KODI = "#KODIPROP:"
    private const val VLC = "#EXTVLCOPT:"

    fun fromChannel(c: Channel): ChannelDraft {
        val a = c.attrs
        var ua = ""
        var ref = ""
        var adaptive = false
        var manifest = ""
        var licType = ""
        var licKey = ""
        var legacy = ""
        var cert = ""
        var mime = ""
        val kodiOther = ArrayList<String>()
        val rest = ArrayList<String>()

        for (line in c.extras) {
            if (line.startsWith(VLC, ignoreCase = true)) {
                val kv = line.substring(VLC.length)
                val k = kv.substringBefore('=').trim().lowercase()
                val v = kv.substringAfter('=', "")
                if (k == "http-user-agent") ua = v
                else if (k == "http-referrer" || k == "http-referer") ref = v
                else rest.add(line)
            } else if (line.startsWith(KODI, ignoreCase = true)) {
                val kv = line.substring(KODI.length)
                val k = kv.substringBefore('=').trim().lowercase()
                val v = kv.substringAfter('=', "")
                when (k) {
                    "inputstream" -> {
                        if (v.trim().equals("inputstream.adaptive", ignoreCase = true)) adaptive = true
                        else kodiOther.add(kv)
                    }
                    "inputstream.adaptive.manifest_type" -> manifest = v.trim()
                    "inputstream.adaptive.license_type" -> licType = v.trim()
                    "inputstream.adaptive.license_key" -> licKey = v
                    "inputstream.adaptive.drm_legacy" -> legacy = v
                    "inputstream.adaptive.server_certificate" -> cert = v
                    "mimetype" -> mime = v
                    else -> kodiOther.add(kv)
                }
            } else {
                rest.add(line)
            }
        }

        val custom = a.filter { it.key !in known }.map { it.key to it.value }
        val radio = a["radio"].orEmpty().trim().lowercase().let { it == "true" || it == "1" }

        return ChannelDraft(
            name = c.name,
            url = c.url,
            group = a["group-title"].orEmpty(),
            catchup = a["catchup"].orEmpty(),
            catchupDays = a["catchup-days"].orEmpty(),
            catchupSource = a["catchup-source"].orEmpty(),
            timeshift = a["timeshift"].orEmpty(),
            tvgName = a["tvg-name"].orEmpty(),
            tvgId = a["tvg-id"].orEmpty(),
            tvgLogo = a["tvg-logo"].orEmpty(),
            country = a["tvg-country"].orEmpty(),
            language = a["tvg-language"].orEmpty(),
            userAgent = ua,
            referrer = ref,
            radio = radio,
            kodiAdaptive = adaptive,
            manifestType = manifest,
            licenseType = licType,
            licenseKey = licKey,
            drmLegacy = legacy,
            serverCert = cert,
            mimeType = mime,
            kodiOther = kodiOther.joinToString("\n"),
            customTags = custom,
            otherExtras = rest
        )
    }

    fun toChannel(base: Channel?, d: ChannelDraft): Channel {
        val attrs = LinkedHashMap<String, String>()
        fun put(k: String, v: String) {
            if (v.isNotBlank()) attrs[k] = v.trim()
        }
        put("tvg-id", d.tvgId)
        put("tvg-name", d.tvgName)
        put("tvg-logo", d.tvgLogo)
        put("tvg-country", d.country)
        put("tvg-language", d.language)
        put("group-title", d.group)
        put("catchup", d.catchup)
        put("catchup-days", d.catchupDays)
        put("catchup-source", d.catchupSource)
        put("timeshift", d.timeshift)
        if (d.radio) attrs["radio"] = "true"
        for ((k, v) in d.customTags) {
            val kk = k.trim()
            if (kk.isNotEmpty() && v.isNotBlank()) attrs[kk] = v.trim()
        }

        val extras = ArrayList<String>()
        if (d.userAgent.isNotBlank()) extras.add(VLC + "http-user-agent=" + d.userAgent.trim())
        if (d.referrer.isNotBlank()) extras.add(VLC + "http-referrer=" + d.referrer.trim())
        if (d.kodiAdaptive) extras.add(KODI + "inputstream=inputstream.adaptive")
        fun kodi(k: String, v: String) {
            if (v.isNotBlank()) extras.add(KODI + k + "=" + v.trim())
        }
        kodi("inputstream.adaptive.manifest_type", d.manifestType)
        kodi("inputstream.adaptive.license_type", d.licenseType)
        kodi("inputstream.adaptive.license_key", d.licenseKey)
        kodi("inputstream.adaptive.drm_legacy", d.drmLegacy)
        kodi("inputstream.adaptive.server_certificate", d.serverCert)
        kodi("mimetype", d.mimeType)
        for (line in d.kodiOther.lines()) {
            val t = line.trim()
            if (t.isNotEmpty()) extras.add(if (t.startsWith("#")) t else KODI + t)
        }
        extras.addAll(d.otherExtras)

        return Channel(
            duration = base?.duration ?: "-1",
            attrs = attrs,
            name = d.name.trim(),
            url = d.url.trim(),
            extras = extras
        )
    }

    /** The channel as raw playlist lines (#EXTINF, extra lines, URL). */
    fun rawOf(c: Channel): String = Exporter.renderChannels(listOf(c), Exporter.FULL).trimEnd()

    fun parseRaw(text: String): Channel? = M3u.parse("#EXTM3U\n" + text).channels.firstOrNull()
}
