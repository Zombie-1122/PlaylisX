package com.playlisx.app

data class ReplaceRule(
    val find: String = "",
    val replace: String = "",
    val regex: Boolean = false,
    val ignoreCase: Boolean = true,
    val inName: Boolean = true,
    val inGroup: Boolean = true,
    val inTvgName: Boolean = true,
    val inLogo: Boolean = false
)

data class TvgSettings(
    val syncTvgName: Boolean = false,
    val genTvgId: Boolean = false,
    val overwriteIds: Boolean = false,
    val removeLogos: Boolean = false,
    val logoBase: String = "",
    val groupPrefix: String = "",
    val groupOverride: String = "",
    val epgUrl: String = ""
)

object Rules {
    fun compile(r: ReplaceRule): Regex? = try {
        if (r.find.isEmpty()) null else {
            val opts = if (r.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            if (r.regex) Regex(r.find, opts) else Regex(Regex.escape(r.find), opts)
        }
    } catch (e: Exception) {
        null
    }

    fun replace(p: Playlist, r: ReplaceRule): Playlist {
        val re = compile(r) ?: return p
        val rep = if (r.regex) r.replace else Regex.escapeReplacement(r.replace)
        fun sub(s: String): String = try {
            re.replace(s, rep)
        } catch (e: Exception) {
            s
        }
        return p.copy(channels = p.channels.map { c ->
            var a = c.attrs
            fun upd(k: String) {
                val v = a[k]
                if (v != null) a = a + (k to sub(v))
            }
            if (r.inGroup) upd("group-title")
            if (r.inTvgName) upd("tvg-name")
            if (r.inLogo) upd("tvg-logo")
            c.copy(name = if (r.inName) sub(c.name) else c.name, attrs = a)
        })
    }

    private fun slug(s: String): String {
        val x = s.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')
        return x.ifEmpty { "channel" }
    }

    private fun setHeaderEpg(h: String, url: String): String {
        val cleaned = h.replace(Regex("\\s*(url-tvg|x-tvg-url)=\"[^\"]*\""), "")
        return "$cleaned url-tvg=\"$url\""
    }

    fun applyTvg(p: Playlist, s: TvgSettings): Playlist {
        val header = if (s.epgUrl.isNotBlank()) setHeaderEpg(p.header, s.epgUrl.trim()) else p.header
        val ch = p.channels.map { c ->
            var a = c.attrs
            if (s.syncTvgName) a = a + ("tvg-name" to c.name)
            if (s.genTvgId && (s.overwriteIds || a["tvg-id"].isNullOrBlank())) {
                a = a + ("tvg-id" to slug(c.name))
            }
            if (s.removeLogos) {
                a = a - "tvg-logo"
            } else if (s.logoBase.isNotBlank()) {
                val file = (a["tvg-logo"] ?: "").substringAfterLast('/').ifBlank { slug(c.name) + ".png" }
                a = a + ("tvg-logo" to s.logoBase.trim().trimEnd('/') + "/" + file)
            }
            if (s.groupOverride.isNotBlank()) a = a + ("group-title" to s.groupOverride.trim())
            if (s.groupPrefix.isNotEmpty()) a = a + ("group-title" to s.groupPrefix + (a["group-title"] ?: ""))
            c.copy(attrs = a)
        }
        return p.copy(header = header, channels = ch)
    }
}

enum class CleanupPreset(val label: String, val desc: String) {
    LIGHT("Light", "Trim spaces, drop entries with no URL, remove duplicate URLs"),
    STANDARD("Standard", "Light + strip [tags], tidy names and groups, fill empty groups"),
    DEEP("Deep", "Standard + remove HD/FHD/4K words, country prefixes, name duplicates, sort A-Z")
}

object Cleanup {
    private val tags = Regex("\\[[^\\]]*]|\\{[^}]*}")
    private val pipePrefix = Regex("^\\s*\\|[^|]{1,8}\\|\\s*")
    private val codePrefix = Regex("^[A-Z]{2,3}\\s*[:|]\\s+")
    private val quality = Regex("\\b(UHD|FHD|HD|SD|4K|8K|HEVC|H\\.?26[45])\\b", RegexOption.IGNORE_CASE)
    private val emptyParens = Regex("\\(\\s*\\)")
    private val spaces = Regex("\\s+")

    private fun tidy(s: String): String = spaces.replace(s, " ").trim().trim(' ', '-', '|', ':')

    fun apply(p: Playlist, preset: CleanupPreset): Playlist {
        val deep = preset == CleanupPreset.DEEP
        val full = preset != CleanupPreset.LIGHT

        var list = p.channels.filter { it.url.isNotBlank() }.map { c ->
            var n = c.name
            var g = c.group
            if (full) {
                n = tags.replace(n, " ")
                g = tags.replace(g, " ")
            }
            if (deep) {
                n = pipePrefix.replace(n.trim(), "")
                n = codePrefix.replace(n.trim(), "")
                n = quality.replace(n, " ")
                n = emptyParens.replace(n, " ")
                g = pipePrefix.replace(g.trim(), "")
                g = quality.replace(g, " ")
            }
            n = tidy(n)
            g = tidy(g)
            if (full && g.isEmpty()) g = "Uncategorized"
            var a = c.attrs
            if (g.isNotEmpty() || a.containsKey("group-title")) a = a + ("group-title" to g)
            c.copy(name = n.ifEmpty { c.name.trim() }, attrs = a)
        }

        val seen = HashSet<String>()
        list = list.filter { seen.add(it.url.trim()) }

        if (deep) {
            val seen2 = HashSet<String>()
            list = list.filter { seen2.add((it.name + "\u0000" + it.group).lowercase()) }
            list = list.sortedWith(compareBy<Channel>({ it.group.lowercase() }, { it.name.lowercase() }))
        }
        return p.copy(channels = list)
    }
}

object Diff {
    private fun short(s: String) = s.removePrefix("#EXTINF:").take(160)

    fun make(label: String, old: Playlist, new: Playlist): Pending? {
        if (old.channels == new.channels && old.header == new.header) return null
        val oldL = old.channels.map { M3u.extinf(it) }
        val newL = new.channels.map { M3u.extinf(it) }
        val oldSet = oldL.toHashSet()
        val newSet = newL.toHashSet()
        val gone = oldL.filter { it !in newSet }
        val added = newL.filter { it !in oldSet }
        val removed = old.channels.size - new.channels.size
        val reordered = oldL.filter { it in newSet }.distinct() != newL.filter { it in oldSet }.distinct()

        val parts = ArrayList<String>()
        if (added.isNotEmpty()) parts.add("${added.size} edited")
        if (removed > 0) parts.add("$removed removed")
        if (reordered) parts.add("re-sorted")
        if (old.header != new.header) parts.add("header updated")
        parts.add("${old.channels.size} \u2192 ${new.channels.size} channels")

        val samples = ArrayList<Sample>()
        val pairCount = minOf(gone.size, added.size)
        for (i in 0 until minOf(6, pairCount)) samples.add(Sample(short(gone[i]), short(added[i])))
        if (pairCount == 0) for (g in gone.take(6)) samples.add(Sample(short(g), null))
        val total = if (pairCount > 0) pairCount else gone.size
        return Pending(label, new, parts.joinToString(" \u00B7 "), samples, (total - samples.size).coerceAtLeast(0))
    }
}
