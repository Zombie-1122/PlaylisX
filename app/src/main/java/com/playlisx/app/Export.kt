package com.playlisx.app

data class ExportProfile(
    val name: String,
    val logos: Boolean = true,
    val tvgIds: Boolean = true,
    val tvgNames: Boolean = true,
    val groups: Boolean = true,
    val extras: Boolean = true,
    val other: Boolean = true,
    val fileName: String = "playlist.m3u",
    val builtIn: Boolean = false
)

object Exporter {
    val BUILT_IN = listOf(
        ExportProfile("Full", fileName = "playlist.m3u", builtIn = true),
        ExportProfile("No logos", logos = false, fileName = "playlist_nologos.m3u", builtIn = true),
        ExportProfile("TV box lite", logos = false, extras = false, other = false, fileName = "playlist_lite.m3u", builtIn = true),
        ExportProfile(
            "Minimal", logos = false, tvgIds = false, tvgNames = false, groups = false,
            extras = false, other = false, fileName = "playlist_min.m3u", builtIn = true
        )
    )

    fun render(p: Playlist, e: ExportProfile): String {
        val sb = StringBuilder()
        sb.append(p.header.ifBlank { "#EXTM3U" }).append('\n')
        val keep: (String) -> Boolean = { k ->
            when (k) {
                "tvg-logo" -> e.logos
                "tvg-id" -> e.tvgIds
                "tvg-name" -> e.tvgNames
                "group-title" -> e.groups
                else -> e.other
            }
        }
        for (c in p.channels) {
            sb.append(M3u.extinf(c, keep)).append('\n')
            if (e.extras) c.extras.forEach { sb.append(it).append('\n') }
            sb.append(c.url).append('\n')
        }
        return sb.toString()
    }

    private fun clean(s: String) = s.replace('|', ' ').replace('\n', ' ').trim()
    private fun b(x: Boolean) = if (x) "1" else "0"

    fun encode(p: ExportProfile): String = listOf(
        clean(p.name), b(p.logos), b(p.tvgIds), b(p.tvgNames), b(p.groups), b(p.extras), b(p.other), clean(p.fileName)
    ).joinToString("|")

    fun decode(s: String): ExportProfile? {
        val f = s.split("|")
        if (f.size != 8 || f[0].isBlank()) return null
        return ExportProfile(
            name = f[0], logos = f[1] == "1", tvgIds = f[2] == "1", tvgNames = f[3] == "1",
            groups = f[4] == "1", extras = f[5] == "1", other = f[6] == "1", fileName = f[7]
        )
    }
}
