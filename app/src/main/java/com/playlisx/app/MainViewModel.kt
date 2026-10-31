package com.playlisx.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("playlisx", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val quickClient = client.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    var playlist by mutableStateOf<Playlist?>(null)
        private set
    var source by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var pending by mutableStateOf<Pending?>(null)
        private set
    var issues by mutableStateOf<List<Issue>>(emptyList())
        private set
    var deadUrls by mutableStateOf<Set<String>>(emptySet())
        private set
    var pingProgress by mutableStateOf("")
        private set
    var undoCount by mutableIntStateOf(0)
        private set
    var redoCount by mutableIntStateOf(0)
        private set
    var loadTick by mutableIntStateOf(0)
        private set

    var url by mutableStateOf(prefs.getString("lastUrl", "") ?: "")
    var rule by mutableStateOf(ReplaceRule())
    var tvg by mutableStateOf(TvgSettings())
    var notice by mutableStateOf(
        NoticeSettings(
            message = prefs.getString("n_msg", "") ?: "",
            group = prefs.getString("n_group", "Info") ?: "Info",
            logo = prefs.getString("n_logo", "") ?: "",
            url = prefs.getString("n_url", "http://127.0.0.1/notice") ?: "http://127.0.0.1/notice"
        )
    )
    var groupLogo by mutableStateOf(GroupLogoSettings())
    var sourceKind by mutableStateOf("FILE")
        private set
    var groupFilter by mutableStateOf<String?>(null)
    var search by mutableStateOf("")
    var showHidden by mutableStateOf(false)
    var textPage by mutableIntStateOf(0)
        private set
    var textDraft by mutableStateOf<String?>(null)

    var profiles by mutableStateOf(loadProfiles())
        private set
    var activeProfile by mutableIntStateOf(0)

    private val undoStack = ArrayDeque<Playlist>()
    private val redoStack = ArrayDeque<Playlist>()

    val current: ExportProfile
        get() = profiles.getOrElse(activeProfile) { profiles.first() }

    // ---------- loading ----------

    fun fetchUrl() {
        val u = url.trim()
        if (u.isEmpty()) {
            message = "Enter a playlist URL"
            return
        }
        busy = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { M3u.parse(download(u)) } }
            busy = false
            r.onSuccess {
                prefs.edit().putString("lastUrl", u).apply()
                load(it, Uri.parse(u).host ?: u, "URL")
            }.onFailure {
                message = "Download failed: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    private fun download(u: String): String {
        val req = Request.Builder().url(u).header("User-Agent", "PlaylisX/1.0 (Android)").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("Empty response")
        }
    }

    fun importUri(uri: Uri) {
        busy = true
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?: uri.lastPathSegment ?: "file"
                    M3u.parse(text) to name
                }
            }
            busy = false
            r.onSuccess { load(it.first, it.second, "FILE") }
                .onFailure { message = "Could not read file: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    private fun load(p: Playlist, label: String, kind: String) {
        if (p.channels.isEmpty()) {
            message = "No channels found. Is this an M3U/M3U8 playlist?"
            return
        }
        undoStack.clear()
        redoStack.clear()
        playlist = p
        source = label
        sourceKind = kind
        groupFilter = null
        search = ""
        deadUrls = emptySet()
        pingProgress = ""
        textPage = 0
        refresh()
        loadTick++
        message = "Loaded ${p.channels.size} channels"
    }

    private fun refresh() {
        textDraft = null
        playlist?.let { cur ->
            val gf = groupFilter
            if (gf != null && cur.channels.none { it.group == gf }) groupFilter = null
        }
        val total = playlist?.channels?.size ?: 0
        textPage = textPage.coerceIn(0, (TextPaging.pageCount(total) - 1).coerceAtLeast(0))
        undoCount = undoStack.size
        redoCount = redoStack.size
        val p = playlist
        issues = if (p == null) emptyList() else Validator.check(p)
        if (p != null && deadUrls.isNotEmpty()) {
            val urls = p.channels.map { it.url }.toHashSet()
            deadUrls = deadUrls.filter { it in urls }.toSet()
        }
    }

    // ---------- edits with preview / undo ----------

    private fun stageAsync(label: String, work: (Playlist) -> Playlist) {
        val p = playlist ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.Default) { runCatching { Diff.make(label, p, work(p)) } }
            busy = false
            r.onSuccess { pd ->
                if (pd == null) {
                    message = "Nothing would change"
                } else {
                    pending = pd
                }
            }.onFailure {
                message = "$label failed: ${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    fun previewReplace() {
        if (playlist == null) return
        if (rule.find.isEmpty()) {
            message = "Enter text to find"
            return
        }
        if (Rules.compile(rule) == null) {
            message = "Invalid pattern"
            return
        }
        val r = rule
        stageAsync("Find & replace") { Rules.replace(it, r) }
    }

    fun previewCleanup(preset: CleanupPreset) {
        stageAsync("Cleanup: ${preset.label}") { Cleanup.apply(it, preset) }
    }

    fun previewTvg() {
        val t = tvg
        stageAsync("TVG settings") { Rules.applyTvg(it, t) }
    }

    fun previewRemoveDead() {
        val dead = deadUrls
        stageAsync("Remove dead streams") { p -> p.copy(channels = p.channels.filter { it.url !in dead }) }
    }

    fun previewNotice() {
        val n = notice
        if (n.message.isBlank()) {
            message = "Enter the message text"
            return
        }
        if (n.url.isBlank()) {
            message = "Enter a stream URL"
            return
        }
        prefs.edit()
            .putString("n_msg", n.message)
            .putString("n_group", n.group)
            .putString("n_logo", n.logo)
            .putString("n_url", n.url)
            .apply()
        stageAsync("Billed message") { Rules.applyNotice(it, n) }
    }

    fun previewRemoveNotice() {
        val n = notice
        if (n.url.isBlank()) {
            message = "Enter the stream URL of the message entry"
            return
        }
        stageAsync("Remove message entry") { Rules.removeNotice(it, n) }
    }

    fun previewGroupLogo() {
        val g = groupLogo
        if (g.group.isBlank() || g.logo.isBlank()) {
            message = "Enter a group name and a logo URL"
            return
        }
        stageAsync("Group logo") { Rules.applyGroupLogo(it, g) }
    }

    fun goToTextPage(i: Int) {
        val total = playlist?.channels?.size ?: 0
        textPage = i.coerceIn(0, (TextPaging.pageCount(total) - 1).coerceAtLeast(0))
        textDraft = null
    }

    fun previewTextEdit(text: String) {
        val p = playlist ?: return
        val page = textPage
        val r = TextPaging.range(p.channels.size, page)
        val from = r.first
        val to = r.last + 1
        stageAsync("Text edit") { cur ->
            val parsed = M3u.parse(text)
            val hasHeader = text.lineSequence().any { it.trim().startsWith("#EXTM3U") }
            val merged = cur.channels.subList(0, from) + parsed.channels + cur.channels.subList(to, cur.channels.size)
            cur.copy(header = if (page == 0 && hasHeader) parsed.header else cur.header, channels = merged)
        }
    }

    // ---------- direct edits (single channel / group). No preview; Undo restores them ----------

    private fun commit(label: String, np: Playlist) {
        val cur = playlist ?: return
        undoStack.addLast(cur)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        playlist = np
        refresh()
        message = label
    }

    private fun withGroup(c: Channel, g: String): Channel {
        val n = g.trim()
        return c.copy(attrs = if (n.isEmpty()) c.attrs - "group-title" else c.attrs + ("group-title" to n))
    }

    private fun withHidden(c: Channel, hide: Boolean): Channel {
        val ex = c.extras.filter { it != HIDDEN_MARK }
        return c.copy(extras = if (hide) ex + HIDDEN_MARK else ex)
    }

    fun saveChannel(index: Int, ch: Channel) {
        val p = playlist ?: return
        val list = p.channels.toMutableList()
        if (index in list.indices) {
            list[index] = ch
            commit("Channel saved", p.copy(channels = list))
        } else {
            list.add(ch)
            commit("Channel added", p.copy(channels = list))
        }
    }

    fun duplicateChannel(index: Int) {
        val p = playlist ?: return
        val c = p.channels.getOrNull(index) ?: return
        val list = p.channels.toMutableList()
        list.add(index + 1, c.copy(name = c.name + " (copy)"))
        commit("Duplicated ${c.name}", p.copy(channels = list))
    }

    fun deleteChannel(index: Int) {
        val p = playlist ?: return
        val c = p.channels.getOrNull(index) ?: return
        commit("Deleted ${c.name} (Undo is in the top bar)", p.copy(channels = p.channels.filterIndexed { i, _ -> i != index }))
    }

    fun toggleHidden(index: Int) {
        val p = playlist ?: return
        val c = p.channels.getOrNull(index) ?: return
        val list = p.channels.toMutableList()
        list[index] = withHidden(c, !c.hidden)
        commit(if (c.hidden) "Channel unhidden" else "Channel hidden (left out of exports)", p.copy(channels = list))
    }

    fun moveToGroup(index: Int, group: String) {
        val p = playlist ?: return
        val c = p.channels.getOrNull(index) ?: return
        val list = p.channels.toMutableList()
        list[index] = withGroup(c, group)
        commit("Moved to ${group.trim().ifEmpty { "no group" }}", p.copy(channels = list))
    }

    fun setPosition(index: Int, position: Int) {
        val p = playlist ?: return
        if (index !in p.channels.indices) return
        val list = p.channels.toMutableList()
        val c = list.removeAt(index)
        list.add((position - 1).coerceIn(0, list.size), c)
        commit("Moved to position ${list.indexOf(c) + 1}", p.copy(channels = list))
    }

    fun renameGroup(old: String, new: String) {
        val p = playlist ?: return
        val n = new.trim()
        val wasSelected = groupFilter == old
        commit("Group renamed", p.copy(channels = p.channels.map { if (it.group == old) withGroup(it, n) else it }))
        if (wasSelected) groupFilter = n
    }

    fun deleteGroup(name: String) {
        val p = playlist ?: return
        commit("Group deleted", p.copy(channels = p.channels.filter { it.group != name }))
    }

    fun hideGroup(name: String, hide: Boolean) {
        val p = playlist ?: return
        commit(
            if (hide) "Group hidden (left out of exports)" else "Group shown",
            p.copy(channels = p.channels.map { if (it.group == name) withHidden(it, hide) else it })
        )
    }

    fun moveGroup(name: String, delta: Int, toTop: Boolean = false) {
        val p = playlist ?: return
        val order = p.channels.map { it.group }.distinct().toMutableList()
        val i = order.indexOf(name)
        if (i < 0) return
        val j = if (toTop) 0 else (i + delta).coerceIn(0, order.lastIndex)
        if (j == i) return
        order.removeAt(i)
        order.add(j, name)
        val rank = HashMap<String, Int>()
        order.forEachIndexed { k, g -> rank[g] = k }
        commit("Group moved", p.copy(channels = p.channels.sortedBy { rank[it.group] ?: 0 }))
    }

    fun checkUrl(u: String) {
        if (u.isBlank()) return
        message = "Checking stream..."
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder().url(u.trim()).header("User-Agent", "PlaylisX/1.0 (Android)").build()
                    quickClient.newCall(req).execute().use { it.code }
                }
            }
            message = r.fold(
                { if (it in 200..399) "Stream responds (HTTP $it)" else "Server answered HTTP $it" },
                { "Not responding: ${it.message ?: it.javaClass.simpleName}" }
            )
        }
    }

    fun applyPending() {
        val pd = pending ?: return
        playlist?.let {
            undoStack.addLast(it)
            if (undoStack.size > 30) undoStack.removeFirst()
        }
        redoStack.clear()
        playlist = pd.result
        pending = null
        refresh()
        message = "Applied: ${pd.label}"
    }

    fun cancelPending() {
        pending = null
    }

    fun undo() {
        val cur = playlist ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(cur)
        playlist = prev
        refresh()
    }

    fun redo() {
        val cur = playlist ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(cur)
        playlist = next
        refresh()
    }

    fun revalidate() {
        refresh()
    }

    // ---------- stream test ----------

    fun pingStreams() {
        val p = playlist ?: return
        val targets = p.channels.map { it.url }.filter { it.startsWith("http", ignoreCase = true) }.distinct().take(100)
        if (targets.isEmpty()) {
            message = "No http(s) streams to test"
            return
        }
        busy = true
        deadUrls = emptySet()
        pingProgress = "Testing 0/${targets.size}"
        val fast = client.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
        viewModelScope.launch {
            val gate = Semaphore(4)
            val dead = ConcurrentHashMap.newKeySet<String>()
            var done = 0
            coroutineScope {
                targets.map { u ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val ok = runCatching {
                                val req = Request.Builder().url(u).header("User-Agent", "PlaylisX/1.0 (Android)").build()
                                fast.newCall(req).execute().use { it.code in 200..399 }
                            }.getOrDefault(false)
                            if (!ok) dead.add(u)
                        }
                        withContext(Dispatchers.Main) {
                            done++
                            pingProgress = "Testing $done/${targets.size}"
                        }
                    }
                }.awaitAll()
            }
            deadUrls = dead.toSet()
            busy = false
            pingProgress = "Tested ${targets.size} streams"
            message = "${dead.size} of ${targets.size} streams not responding"
        }
    }

    // ---------- export ----------

    private fun safeName(n: String): String {
        val s = n.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "playlist.m3u" }
        return if (s.endsWith(".m3u", true) || s.endsWith(".m3u8", true)) s else "$s.m3u"
    }

    fun currentFileName(): String = safeName(current.fileName)

    private fun exportText(): String? = playlist?.let { Exporter.render(it, current) }

    fun copy(ctx: Context) {
        val text = exportText() ?: return
        if (text.length > 500_000) {
            message = "Too large for the clipboard. Use Share or Save."
            return
        }
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("PlaylisX", text))
            message = "Copied ${playlist?.channels?.size ?: 0} channels"
        } catch (e: Exception) {
            message = "Could not copy. Use Share or Save."
        }
    }

    fun share(ctx: Context) {
        val text = exportText() ?: return
        try {
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, currentFileName())
            f.writeText(text)
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "audio/x-mpegurl"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(i, "Share playlist"))
        } catch (e: Exception) {
            message = "Share failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun saveTo(uri: Uri) {
        val text = exportText() ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            message = if (ok) "Saved" else "Save failed"
        }
    }

    // ---------- profiles ----------

    private fun loadProfiles(): List<ExportProfile> {
        val custom = (prefs.getString("profiles", "") ?: "").lines().mapNotNull { Exporter.decode(it) }
        return Exporter.BUILT_IN + custom
    }

    private fun persistProfiles() {
        val text = profiles.filter { !it.builtIn }.joinToString("\n") { Exporter.encode(it) }
        prefs.edit().putString("profiles", text).apply()
    }

    fun saveProfile(d: ExportProfile) {
        var name = d.name.replace('|', ' ').trim()
        if (name.isEmpty()) return
        if (Exporter.BUILT_IN.any { it.name.equals(name, true) }) name += " (custom)"
        val np = d.copy(name = name, builtIn = false)
        val list = profiles.filterNot { !it.builtIn && it.name.equals(name, true) } + np
        profiles = list
        activeProfile = list.lastIndex
        persistProfiles()
        message = "Profile saved"
    }

    fun deleteProfile(i: Int) {
        val pr = profiles.getOrNull(i) ?: return
        if (pr.builtIn) return
        profiles = profiles.filterIndexed { idx, _ -> idx != i }
        activeProfile = 0
        persistProfiles()
    }
}
