@file:OptIn(ExperimentalMaterial3Api::class)

package com.playlisx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlaylisXTheme { App() } }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val m = vm.message
        if (m != null) {
            snack.showSnackbar(m)
            vm.message = null
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.saveTo(uri)
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopBar(vm) },
        bottomBar = {
            NavigationBar(containerColor = Panel) {
                val items = listOf(
                    "Import" to Icons.Default.Add,
                    "Edit" to Icons.Default.Edit,
                    "Check" to Icons.Default.Check,
                    "Export" to Icons.Default.Share
                )
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> ImportTab(vm) { openFile.launch(arrayOf("*/*")) }
                1 -> EditTab(vm)
                2 -> CheckTab(vm)
                else -> ExportTab(vm) { saveFile.launch(vm.currentFileName()) }
            }
        }
    }

    val pd = vm.pending
    if (pd != null) PendingDialog(pd, onApply = { vm.applyPending() }, onCancel = { vm.cancelPending() })
}

@Composable
fun TopBar(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            buildAnnotatedString {
                append("Playlis")
                withStyle(SpanStyle(color = Gold)) { append("X") }
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { vm.undo() }, enabled = vm.undoCount > 0) { Text("Undo") }
        TextButton(onClick = { vm.redo() }, enabled = vm.redoCount > 0) { Text("Redo") }
    }
}

// ---------- small building blocks ----------

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun Check(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp)
    }
}

@Composable
fun Empty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Import a playlist first.", color = Muted)
    }
}

@Composable
fun ChannelRow(c: Channel) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(
            c.name.ifEmpty { "(no name)" },
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOf(c.group, c.url).filter { it.isNotEmpty() }.joinToString("  \u00B7  "),
            color = Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- tabs ----------

@Composable
fun ImportTab(vm: MainViewModel, onPick: () -> Unit) {
    val p = vm.playlist
    val groupCount = remember(p) {
        p?.channels?.map { it.group }?.filter { it.isNotEmpty() }?.toSet()?.size ?: 0
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Section("Playlist source") {
                Field(vm.url, { vm.url = it }, "Playlist URL (http/https)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.fetchUrl() }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                        Text("Fetch URL")
                    }
                    OutlinedButton(onClick = onPick, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                        Text("Import file")
                    }
                }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (p != null) {
            item {
                Section("Loaded") {
                    Text(vm.source)
                    Text("${p.channels.size} channels \u00B7 $groupCount groups", color = Muted)
                }
            }
            items(p.channels.take(100)) { c -> ChannelRow(c) }
            if (p.channels.size > 100) {
                item { Text("Showing first 100 of ${p.channels.size}", color = Muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun EditTab(vm: MainViewModel) {
    if (vm.playlist == null) {
        Empty()
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section("Find & replace") {
            Field(vm.rule.find, { vm.rule = vm.rule.copy(find = it) }, "Find")
            Field(vm.rule.replace, { vm.rule = vm.rule.copy(replace = it) }, "Replace with")
            Row {
                Check("Regex", vm.rule.regex, Modifier.weight(1f)) { vm.rule = vm.rule.copy(regex = it) }
                Check("Ignore case", vm.rule.ignoreCase, Modifier.weight(1f)) { vm.rule = vm.rule.copy(ignoreCase = it) }
            }
            Text("Apply to", color = Muted, fontSize = 12.sp)
            Row {
                Check("Names", vm.rule.inName, Modifier.weight(1f)) { vm.rule = vm.rule.copy(inName = it) }
                Check("Groups", vm.rule.inGroup, Modifier.weight(1f)) { vm.rule = vm.rule.copy(inGroup = it) }
            }
            Row {
                Check("TVG name", vm.rule.inTvgName, Modifier.weight(1f)) { vm.rule = vm.rule.copy(inTvgName = it) }
                Check("Logo URL", vm.rule.inLogo, Modifier.weight(1f)) { vm.rule = vm.rule.copy(inLogo = it) }
            }
            Button(onClick = { vm.previewReplace() }, modifier = Modifier.fillMaxWidth()) {
                Text("Preview changes")
            }
        }

        Section("Smart cleanup") {
            CleanupPreset.values().forEach { pr ->
                OutlinedButton(onClick = { vm.previewCleanup(pr) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(pr.label, fontWeight = FontWeight.Bold)
                        Text(pr.desc, fontSize = 12.sp, color = Muted)
                    }
                }
            }
        }

        Section("TVG settings") {
            Check("Set tvg-name from channel name", vm.tvg.syncTvgName, Modifier.fillMaxWidth()) {
                vm.tvg = vm.tvg.copy(syncTvgName = it)
            }
            Check("Generate tvg-id where missing", vm.tvg.genTvgId, Modifier.fillMaxWidth()) {
                vm.tvg = vm.tvg.copy(genTvgId = it)
            }
            Check("Overwrite existing tvg-id", vm.tvg.overwriteIds, Modifier.fillMaxWidth()) {
                vm.tvg = vm.tvg.copy(overwriteIds = it)
            }
            Check("Remove all logos", vm.tvg.removeLogos, Modifier.fillMaxWidth()) {
                vm.tvg = vm.tvg.copy(removeLogos = it)
            }
            Field(vm.tvg.logoBase, { vm.tvg = vm.tvg.copy(logoBase = it) }, "Logo base URL (rewrites tvg-logo)")
            Field(vm.tvg.groupPrefix, { vm.tvg = vm.tvg.copy(groupPrefix = it) }, "Group prefix (e.g. MyTV | )")
            Field(vm.tvg.groupOverride, { vm.tvg = vm.tvg.copy(groupOverride = it) }, "Set every group to")
            Field(vm.tvg.epgUrl, { vm.tvg = vm.tvg.copy(epgUrl = it) }, "EPG / XMLTV URL (url-tvg)")
            Button(onClick = { vm.previewTvg() }, modifier = Modifier.fillMaxWidth()) {
                Text("Preview changes")
            }
        }
    }
}

@Composable
fun IssueRow(i: Issue) {
    val col = when (i.level) {
        Level.ERROR -> MaterialTheme.colorScheme.error
        Level.WARN -> Gold
        Level.INFO -> Muted
    }
    val tag = when (i.level) {
        Level.ERROR -> "ERROR"
        Level.WARN -> "WARN"
        Level.INFO -> "INFO"
    }
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(tag, color = col, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        Text(i.text, fontSize = 13.sp)
    }
}

@Composable
fun CheckTab(vm: MainViewModel) {
    if (vm.playlist == null) {
        Empty()
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Section("Validator") {
                val e = vm.issues.count { it.level == Level.ERROR }
                val w = vm.issues.count { it.level == Level.WARN }
                val n = vm.issues.count { it.level == Level.INFO }
                Text("$e errors \u00B7 $w warnings \u00B7 $n notes", color = Muted)
                OutlinedButton(onClick = { vm.revalidate() }) { Text("Re-run") }
            }
        }
        items(vm.issues) { IssueRow(it) }
        item {
            Section("Stream test") {
                Text(
                    "Tests up to 100 unique http(s) streams, 4 at a time. Some providers limit simultaneous connections, so use with care.",
                    color = Muted,
                    fontSize = 12.sp
                )
                Button(onClick = { vm.pingStreams() }, enabled = !vm.busy) { Text("Test streams") }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (vm.pingProgress.isNotEmpty()) Text(vm.pingProgress, fontSize = 13.sp)
                if (vm.deadUrls.isNotEmpty()) {
                    Text("${vm.deadUrls.size} not responding", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.previewRemoveDead() }) { Text("Preview removal") }
                }
            }
        }
    }
}

@Composable
fun ExportTab(vm: MainViewModel, onSave: () -> Unit) {
    val p = vm.playlist
    if (p == null) {
        Empty()
        return
    }
    val ctx = LocalContext.current
    val profile = vm.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Section("Export profile") {
            vm.profiles.forEachIndexed { i, pr ->
                Row(
                    Modifier.fillMaxWidth().clickable { vm.activeProfile = i },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = vm.activeProfile == i, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(pr.name, modifier = Modifier.weight(1f))
                    if (!pr.builtIn) TextButton(onClick = { vm.deleteProfile(i) }) { Text("Delete") }
                }
            }
        }

        Section("Output") {
            Text("${p.channels.size} channels \u00B7 ${vm.currentFileName()}", color = Muted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.copy(ctx) }, modifier = Modifier.weight(1f)) { Text("Copy") }
                Button(onClick = { vm.share(ctx) }, modifier = Modifier.weight(1f)) { Text("Share") }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            }
        }

        Section("Preview") {
            val sample = remember(p, profile) { Exporter.render(p.copy(channels = p.channels.take(3)), profile) }
            Text(
                sample,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Muted,
                maxLines = 14,
                overflow = TextOverflow.Ellipsis
            )
        }

        var draft by remember(profile) {
            mutableStateOf(if (profile.builtIn) profile.copy(name = "") else profile)
        }
        Section("Customize profile") {
            Field(draft.name, { draft = draft.copy(name = it) }, "Profile name")
            Field(draft.fileName, { draft = draft.copy(fileName = it) }, "File name (e.g. mylist.m3u)")
            Row {
                Check("Logos", draft.logos, Modifier.weight(1f)) { draft = draft.copy(logos = it) }
                Check("tvg-id", draft.tvgIds, Modifier.weight(1f)) { draft = draft.copy(tvgIds = it) }
            }
            Row {
                Check("tvg-name", draft.tvgNames, Modifier.weight(1f)) { draft = draft.copy(tvgNames = it) }
                Check("Groups", draft.groups, Modifier.weight(1f)) { draft = draft.copy(groups = it) }
            }
            Row {
                Check("Extra lines", draft.extras, Modifier.weight(1f)) { draft = draft.copy(extras = it) }
                Check("Other attrs", draft.other, Modifier.weight(1f)) { draft = draft.copy(other = it) }
            }
            OutlinedButton(
                onClick = { vm.saveProfile(draft) },
                enabled = draft.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save profile") }
        }
    }
}

@Composable
fun PendingDialog(p: Pending, onApply: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Panel,
        title = { Text(p.label, color = Gold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(p.summary, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                p.samples.forEach { s ->
                    Text("- ${s.before}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted)
                    if (s.after != null) {
                        Text("+ ${s.after}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Gold)
                    } else {
                        Text("+ (removed)", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFFF5252))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (p.moreCount > 0) Text("...and ${p.moreCount} more", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = onApply) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
