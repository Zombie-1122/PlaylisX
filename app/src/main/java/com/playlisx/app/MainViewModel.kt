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

    var url by mutableStateOf(prefs.getString("lastUrl", "") ?: "")
    var rule by mutableStateOf(ReplaceRule())
    var tvg by mutableStateOf(TvgSettings())

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
                load(it, Uri.parse(u).host ?: u)
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
            r.onSuccess { load(it.first, it.second) }
                .onFailure { message = "Could not read file: ${it.message ?: it.javaClass.simpleName}" }
        }
    }

    private fun load(p: Playlist, label: String) {
        if (p.channels.isEmpty()) {
            message = "No channels found. Is this an M3U/M3U8 playlist?"
            return
        }
        undoStack.clear()
        redoStack.clear()
        playlist = p
        source = label
        deadUrls = emptySet()
        pingProgress = ""
        refresh()
        message = "Loaded ${p.channels.size} channels"
    }

    private fun refresh() {
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

    private fun stage(label: String, result: Playlist) {
        val p = playlist ?: return
        val pd = Diff.make(label, p, result)
        if (pd == null) message = "Nothing would change" else pending = pd
    }

    fun previewReplace() {
        val p = playlist ?: return
        if (rule.find.isEmpty()) {
            message = "Enter text to find"
            return
        }
        if (Rules.compile(rule) == null) {
            message = "Invalid pattern"
            return
        }
        stage("Find & replace", Rules.replace(p, rule))
    }

    fun previewCleanup(preset: CleanupPreset) {
        val p = playlist ?: return
        stage("Cleanup: ${preset.label}", Cleanup.apply(p, preset))
    }

    fun previewTvg() {
        val p = playlist ?: return
        stage("TVG settings", Rules.applyTvg(p, tvg))
    }

    fun previewRemoveDead() {
        val p = playlist ?: return
        stage("Remove dead streams", p.copy(channels = p.channels.filter { it.url !in deadUrls }))
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
