package com.playlisx.app

data class Channel(
    val duration: String = "-1",
    val attrs: Map<String, String> = emptyMap(),
    val name: String,
    val url: String,
    val extras: List<String> = emptyList()
) {
    val group: String get() = attrs["group-title"].orEmpty()
}

data class Playlist(
    val header: String = "#EXTM3U",
    val channels: List<Channel> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class Sample(val before: String, val after: String?)

data class Pending(
    val label: String,
    val result: Playlist,
    val summary: String,
    val samples: List<Sample>,
    val moreCount: Int
)

enum class Level { ERROR, WARN, INFO }

data class Issue(val level: Level, val text: String)
