@file:OptIn(ExperimentalMaterial3Api::class)

package com.playlisx.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Danger = Color(0xFFFFB4AB)
private val GoldDark = Color(0xFF3A3000)

// =====================================================================================
// Small shared pieces
// =====================================================================================

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, strong: Boolean = false) {
    Box(
        modifier
            .background(if (strong) GoldDark else Panel2, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (strong) Gold else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LogoBox(url: String, size: Dp = 64.dp) {
    Box(
        Modifier.size(size).background(Panel2, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("TV", color = Muted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (url.isNotBlank()) {
            AsyncImage(
                model = url.trim(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
        }
    }
}

@Composable
fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) GoldDark else Panel, shape)
            .border(1.dp, if (selected) Gold else Color(0xFF3A3A3A), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, color = if (selected) Gold else Color.White, fontSize = 14.sp, maxLines = 1)
    }
}

/** A card with a tappable header (title, subtitle, filled-field badge, chevron) that expands. */
@Composable
fun Collapsible(
    title: String,
    subtitle: String,
    count: Int,
    startOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(subtitle, color = Muted, fontSize = 12.sp)
                }
                if (count > 0) {
                    Box(Modifier.background(GoldDark, CircleShape).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        Text("$count", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Muted
                )
            }
            if (open) {
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            }
        }
    }
}

/** Dropdown with a list of options. With editable = true the user can also type a custom value. */
@Composable
fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onChange: (String) -> Unit,
    editable: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (editable && value.isNotEmpty()) {
        options.filter { it.contains(value, ignoreCase = true) && it != value }
    } else {
        options
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onChange(it)
                expanded = true
            },
            readOnly = !editable,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        if (shown.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                shown.take(150).forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o) },
                        onClick = {
                            onChange(o)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ImportControls(vm: MainViewModel, onPickFile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(vm.url, { vm.url = it }, "Playlist URL (http/https)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.fetchUrl() }, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                Text("Fetch URL")
            }
            OutlinedButton(onClick = onPickFile, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                Text("Import file")
            }
        }
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("PlaylisX", text))
}

private fun openInPlayer(ctx: Context, url: String, vm: MainViewModel) {
    try {
        val i = Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(url.trim()), "video/*") }
        ctx.startActivity(Intent.createChooser(i, "Open in player"))
    } catch (e: ActivityNotFoundException) {
        vm.message = "No player app found"
    } catch (e: Exception) {
        vm.message = "Could not open: ${e.message ?: e.javaClass.simpleName}"
    }
}

// =====================================================================================
// Channels tab (list like a player app: logo, name, URL, #position and group chips)
// =====================================================================================

@Composable
fun ChannelCard(index: Int, c: Channel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoBox(c.attrs["tvg-logo"].orEmpty(), 64.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.name.ifEmpty { "(no name)" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (c.hidden) Muted else Color.White
            )
            Text(
                c.url,
                color = Muted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("#${index + 1}", strong = true)
                if (c.group.isNotEmpty()) Pill(c.group, Modifier.weight(1f, fill = false))
                if (c.hidden) Pill("hidden")
            }
        }
    }
}

@Composable
fun ChannelsTab(
    vm: MainViewModel,
    onOpenGroups: () -> Unit,
    onImport: () -> Unit,
    onPickFile: () -> Unit,
    onChannel: (Int) -> Unit,
    onAdd: () -> Unit
) {
    val p = vm.playlist
    if (p == null) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Load a playlist to start", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Fetch an M3U link or import a file from your phone.", color = Muted)
            Section("Playlist source") { ImportControls(vm, onPickFile) }
        }
        return
    }

    var searchOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val groups = remember(p) { p.channels.map { it.group }.filter { it.isNotEmpty() }.distinct() }
    val visible = remember(p, vm.groupFilter, vm.search, vm.showHidden) {
        val q = vm.search.trim()
        val g = vm.groupFilter
        val out = ArrayList<Int>()
        p.channels.forEachIndexed { i, c ->
            if (!vm.showHidden && c.hidden) return@forEachIndexed
            if (g != null && c.group != g) return@forEachIndexed
            if (q.isNotEmpty() && !c.name.contains(q, ignoreCase = true) && !c.url.contains(q, ignoreCase = true)) {
                return@forEachIndexed
            }
            out.add(i)
        }
        out
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill("${p.channels.size} channels", strong = true)
                Spacer(Modifier.width(6.dp))
                Pill(vm.sourceKind)
                if (visible.size != p.channels.size) {
                    Spacer(Modifier.width(6.dp))
                    Text("${visible.size} shown", color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    searchOpen = !searchOpen
                    if (!searchOpen) vm.search = ""
                }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Load another playlist") },
                            onClick = {
                                menuOpen = false
                                onImport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage groups") },
                            onClick = {
                                menuOpen = false
                                onOpenGroups()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (vm.showHidden) "Hide hidden channels" else "Show hidden channels") },
                            onClick = {
                                menuOpen = false
                                vm.showHidden = !vm.showHidden
                            }
                        )
                    }
                }
            }
            if (searchOpen) {
                OutlinedTextField(
                    value = vm.search,
                    onValueChange = { vm.search = it },
                    placeholder = { Text("Search name or URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { GroupChip("Groups", false, onOpenGroups) }
                item { GroupChip("All", vm.groupFilter == null) { vm.groupFilter = null } }
                items(groups) { g -> GroupChip(g, vm.groupFilter == g) { vm.groupFilter = g } }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (visible.isEmpty()) {
                    item { Text("No channels match.", color = Muted, modifier = Modifier.padding(16.dp)) }
                }
                items(visible, key = { it }) { idx ->
                    ChannelCard(idx, p.channels[idx]) { onChannel(idx) }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            containerColor = Gold,
            contentColor = Ink,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) { Icon(Icons.Default.Add, contentDescription = "Add channel") }
    }
}

// =====================================================================================
// Channel actions sheet
// =====================================================================================

@Composable
private fun SheetLabel(t: String) {
    Text(
        t,
        color = Gold,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun ActionGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).background(if (danger) Color(0xFF8E1B1B) else Panel2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (danger) Danger else Muted)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 17.sp, color = if (danger) Danger else Color.White, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Muted)
    }
}

@Composable
fun ChannelActionsSheet(vm: MainViewModel, index: Int, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val p = vm.playlist ?: return
    val c = p.channels.getOrNull(index) ?: return
    val ctx = LocalContext.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dialog by remember { mutableStateOf(0) }
    val groups = remember(p) { p.channels.map { it.group }.filter { it.isNotEmpty() }.distinct() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = Ink) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)
        ) {
            Text("Channel actions", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(c.name, fontSize = 16.sp)
            Text(c.url, color = Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)

            SheetLabel("PLAYBACK")
            ActionGroup {
                ActionRow(Icons.Default.Edit, "Edit") {
                    onEdit()
                    onDismiss()
                }
                ActionRow(Icons.Default.Refresh, "Check URL") {
                    vm.checkUrl(c.url)
                    onDismiss()
                }
                ActionRow(Icons.Default.PlayArrow, "Open in player...") {
                    openInPlayer(ctx, c.url, vm)
                    onDismiss()
                }
            }

            SheetLabel("ORGANIZE")
            ActionGroup {
                ActionRow(Icons.Default.Add, "Duplicate") {
                    vm.duplicateChannel(index)
                    onDismiss()
                }
                ActionRow(Icons.Default.ArrowForward, "Move to group") { dialog = 1 }
                ActionRow(Icons.Default.List, "Set position") { dialog = 2 }
            }

            SheetLabel("OTHER")
            ActionGroup {
                ActionRow(Icons.Default.Share, "Copy URL") {
                    copyText(ctx, c.url)
                    vm.message = "URL copied"
                    onDismiss()
                }
                ActionRow(Icons.Default.Close, if (c.hidden) "Unhide" else "Hide") {
                    vm.toggleHidden(index)
                    onDismiss()
                }
                ActionRow(Icons.Default.Delete, "Delete", danger = true) {
                    vm.deleteChannel(index)
                    onDismiss()
                }
            }
        }
    }

    if (dialog == 1) {
        var value by remember { mutableStateOf(c.group) }
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            containerColor = Panel,
            title = { Text("Move to group", color = Gold) },
            text = { DropdownField("Group", value, groups, { value = it }, editable = true) },
            confirmButton = {
                Button(onClick = {
                    vm.moveToGroup(index, value)
                    dialog = 0
                    onDismiss()
                }) { Text("Move") }
            },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Cancel") } }
        )
    }
    if (dialog == 2) {
        var value by remember { mutableStateOf((index + 1).toString()) }
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            containerColor = Panel,
            title = { Text("Set position", color = Gold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Position from 1 to ${p.channels.size}", color = Muted, fontSize = 13.sp)
                    Field(value, { v -> value = v.filter { ch -> ch.isDigit() } }, "Position")
                }
            },
            confirmButton = {
                Button(onClick = {
                    val n = value.toIntOrNull()
                    if (n != null) {
                        vm.setPosition(index, n)
                        dialog = 0
                        onDismiss()
                    }
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { dialog = 0 }) { Text("Cancel") } }
        )
    }
}

// =====================================================================================
// Groups screen
// =====================================================================================

data class GroupInfo(val name: String, val count: Int, val hidden: Boolean)

fun groupInfos(p: Playlist): List<GroupInfo> {
    val counts = LinkedHashMap<String, Int>()
    val hiddenCounts = HashMap<String, Int>()
    for (c in p.channels) {
        val g = c.group
        counts[g] = (counts[g] ?: 0) + 1
        if (c.hidden) hiddenCounts[g] = (hiddenCounts[g] ?: 0) + 1
    }
    return counts.map { (k, v) -> GroupInfo(k, v, (hiddenCounts[k] ?: 0) == v) }
}

@Composable
fun GroupsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val p = vm.playlist
    if (p == null) {
        Empty()
        return
    }
    val infos = remember(p) { groupInfos(p) }
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var deleteFor by remember { mutableStateOf<String?>(null) }
    val shown = infos.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Groups", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Tap a group to filter. Use the menu to rename, move, hide or delete.", color = Muted, fontSize = 12.sp)
            }
            Text("${infos.size}", color = Muted, modifier = Modifier.padding(end = 12.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search groups...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val sel = vm.groupFilter == null
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) GoldDark else Panel)
                        .border(1.dp, if (sel) Gold else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable {
                            vm.groupFilter = null
                            onBack()
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("All", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Pill("${p.channels.size}")
                }
            }
            items(shown, key = { it.name }) { g ->
                val sel = vm.groupFilter == g.name
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) GoldDark else Panel)
                        .border(1.dp, if (sel) Gold else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable {
                            vm.groupFilter = g.name
                            onBack()
                        }
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        g.name.ifEmpty { "(no group)" },
                        fontSize = 16.sp,
                        color = if (g.hidden) Muted else Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (g.hidden) {
                        Pill("hidden")
                        Spacer(Modifier.width(6.dp))
                    }
                    Pill("${g.count}")
                    Box {
                        IconButton(onClick = { menuFor = g.name }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Group menu")
                        }
                        DropdownMenu(expanded = menuFor == g.name, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Move to top") }, onClick = {
                                menuFor = null
                                vm.moveGroup(g.name, 0, toTop = true)
                            })
                            DropdownMenuItem(text = { Text("Move up") }, onClick = {
                                menuFor = null
                                vm.moveGroup(g.name, -1)
                            })
                            DropdownMenuItem(text = { Text("Move down") }, onClick = {
                                menuFor = null
                                vm.moveGroup(g.name, 1)
                            })
                            DropdownMenuItem(text = { Text("Rename group") }, onClick = {
                                menuFor = null
                                renameFor = g.name
                            })
                            DropdownMenuItem(text = { Text(if (g.hidden) "Show group" else "Hide group") }, onClick = {
                                menuFor = null
                                vm.hideGroup(g.name, !g.hidden)
                            })
                            DropdownMenuItem(text = { Text("Delete group", color = Danger) }, onClick = {
                                menuFor = null
                                deleteFor = g.name
                            })
                        }
                    }
                }
            }
        }
    }

    val rn = renameFor
    if (rn != null) {
        var value by remember(rn) { mutableStateOf(rn) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            containerColor = Panel,
            title = { Text("Rename group", color = Gold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Use the name of an existing group to merge them.", color = Muted, fontSize = 13.sp)
                    Field(value, { value = it }, "Group name")
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.renameGroup(rn, value)
                    renameFor = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("Cancel") } }
        )
    }
    val del = deleteFor
    if (del != null) {
        val n = infos.firstOrNull { it.name == del }?.count ?: 0
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            containerColor = Panel,
            title = { Text("Delete group", color = Gold) },
            text = { Text("Delete \"${del.ifEmpty { "(no group)" }}\" and its $n channels? You can undo this from the top bar.") },
            confirmButton = {
                Button(onClick = {
                    vm.deleteGroup(del)
                    deleteFor = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("Cancel") } }
        )
    }
}

// =====================================================================================
// Edit channel screen (Fields / Raw body)
// =====================================================================================

private fun filled(vararg s: String): Int = s.count { it.isNotBlank() }

@Composable
fun EditChannelScreen(vm: MainViewModel, index: Int, defaultGroup: String, onClose: () -> Unit) {
    val p = vm.playlist
    val base = p?.channels?.getOrNull(index)
    val groups = remember(p) { p?.channels?.map { it.group }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList() }
    var draft by remember {
        mutableStateOf(
            ChannelEdit.fromChannel(
                base ?: Channel(
                    name = "",
                    url = "",
                    attrs = if (defaultGroup.isNotEmpty()) mapOf("group-title" to defaultGroup) else emptyMap()
                )
            )
        )
    }
    var rawMode by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }

    fun save() {
        val ch = if (rawMode) ChannelEdit.parseRaw(raw) else ChannelEdit.toChannel(base, draft)
        if (ch == null || ch.name.isBlank() || ch.url.isBlank()) {
            vm.message = "Name and stream URL are required"
            return
        }
        vm.saveChannel(index, ch)
        onClose()
    }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (base == null) "New channel" else "Edit channel", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(draft.name.ifEmpty { "Untitled" }, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClose) { Text("Cancel") }
            Spacer(Modifier.width(4.dp))
            Button(onClick = { save() }) { Text("Save") }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!rawMode) {
                Button(onClick = { }) { Text("Fields") }
            } else {
                OutlinedButton(onClick = {
                    val c = ChannelEdit.parseRaw(raw)
                    if (c == null) {
                        vm.message = "Raw text is not a valid entry"
                    } else {
                        draft = ChannelEdit.fromChannel(c)
                        rawMode = false
                    }
                }) { Text("Fields") }
            }
            if (rawMode) {
                Button(onClick = { }) { Text("Raw body") }
            } else {
                OutlinedButton(onClick = {
                    raw = ChannelEdit.rawOf(ChannelEdit.toChannel(base, draft))
                    rawMode = true
                }) { Text("Raw body") }
            }
        }

        if (rawMode) {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                modifier = Modifier.fillMaxSize().padding(16.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            )
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Collapsible("Basic info", "Name, stream URL and group", filled(draft.name, draft.url, draft.group), startOpen = true) {
                    Field(draft.name, { draft = draft.copy(name = it) }, "Name *")
                    Field(draft.url, { draft = draft.copy(url = it) }, "Stream URL *")
                    DropdownField("Group", draft.group, groups, { draft = draft.copy(group = it) }, editable = true)
                }

                Collapsible(
                    "Catch-up / time shift", "Catch-up and timeshift on #EXTINF",
                    filled(draft.catchup, draft.catchupDays, draft.catchupSource, draft.timeshift)
                ) {
                    DropdownField(
                        "Catch-up type", draft.catchup,
                        listOf("default", "append", "shift", "flussonic", "xc", "vod"),
                        { draft = draft.copy(catchup = it) }, editable = true
                    )
                    Field(draft.catchupDays, { draft = draft.copy(catchupDays = it) }, "Catch-up days")
                    Field(draft.catchupSource, { draft = draft.copy(catchupSource = it) }, "Catch-up source URL")
                    Field(draft.timeshift, { draft = draft.copy(timeshift = it) }, "Timeshift")
                }

                Collapsible(
                    "TVG metadata", "EPG matching and logos",
                    filled(draft.tvgName, draft.tvgId, draft.tvgLogo, draft.country, draft.language),
                    startOpen = true
                ) {
                    Field(draft.tvgName, { draft = draft.copy(tvgName = it) }, "TVG Name")
                    Field(draft.tvgId, { draft = draft.copy(tvgId = it) }, "TVG ID")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(draft.tvgLogo, { draft = draft.copy(tvgLogo = it) }, "TVG Logo URL", Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        LogoBox(draft.tvgLogo, 60.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(draft.country, { draft = draft.copy(country = it) }, "Country", Modifier.weight(1f))
                        Field(draft.language, { draft = draft.copy(language = it) }, "Language", Modifier.weight(1f))
                    }
                }

                Collapsible(
                    "Advanced", "User-Agent, Referer, radio flag",
                    filled(draft.userAgent, draft.referrer) + (if (draft.radio) 1 else 0)
                ) {
                    Field(draft.userAgent, { draft = draft.copy(userAgent = it) }, "User-Agent")
                    Field(draft.referrer, { draft = draft.copy(referrer = it) }, "Referrer URL")
                    Check("Radio station", draft.radio, Modifier.fillMaxWidth()) { draft = draft.copy(radio = it) }
                }

                Collapsible(
                    "DRM / Kodi", "#KODIPROP - Widevine, ClearKey, Adaptive",
                    filled(draft.manifestType, draft.licenseType, draft.licenseKey, draft.drmLegacy, draft.serverCert, draft.mimeType, draft.kodiOther) +
                        (if (draft.kodiAdaptive) 1 else 0)
                ) {
                    Text("Writes #KODIPROP lines used by Kodi InputStream Adaptive and many IPTV players.", color = Muted, fontSize = 12.sp)
                    Check("inputstream.adaptive", draft.kodiAdaptive, Modifier.fillMaxWidth()) {
                        draft = draft.copy(kodiAdaptive = it)
                    }
                    DropdownField(
                        "Manifest type", draft.manifestType.ifEmpty { "None" },
                        listOf("None", "mpd", "hls", "ism"),
                        { draft = draft.copy(manifestType = if (it == "None") "" else it) }
                    )
                    DropdownField(
                        "License type", draft.licenseType,
                        listOf("org.w3.clearkey", "com.widevine.alpha", "com.microsoft.playready", "com.apple.fps.1_0"),
                        { draft = draft.copy(licenseType = it) }, editable = true
                    )
                    OutlinedTextField(
                        value = draft.licenseKey,
                        onValueChange = { draft = draft.copy(licenseKey = it) },
                        label = { Text("License key / URL") },
                        supportingText = { Text("Widevine URL or ClearKey kid:key (hex)") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Field(draft.drmLegacy, { draft = draft.copy(drmLegacy = it) }, "drm_legacy")
                    Field(draft.serverCert, { draft = draft.copy(serverCert = it) }, "Server certificate URL")
                    Field(draft.mimeType, { draft = draft.copy(mimeType = it) }, "mimetype")
                    OutlinedTextField(
                        value = draft.kodiOther,
                        onValueChange = { draft = draft.copy(kodiOther = it) },
                        label = { Text("Other #KODIPROP (one key=value per line)") },
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Collapsible(
                    "Custom #EXTINF tags",
                    "Any attribute on the #EXTINF line (e.g. tvg-rec, group-logo). An empty value removes the tag.",
                    draft.customTags.size
                ) {
                    draft.customTags.forEachIndexed { i, kv ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Field(
                                kv.first,
                                { nk -> draft = draft.copy(customTags = draft.customTags.toMutableList().also { l -> l[i] = nk to kv.second }) },
                                "Tag", Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Field(
                                kv.second,
                                { nv -> draft = draft.copy(customTags = draft.customTags.toMutableList().also { l -> l[i] = kv.first to nv }) },
                                "Value", Modifier.weight(1.4f)
                            )
                            IconButton(onClick = {
                                draft = draft.copy(customTags = draft.customTags.filterIndexed { idx, _ -> idx != i })
                            }) { Icon(Icons.Default.Delete, contentDescription = "Remove tag", tint = Danger) }
                        }
                    }
                    OutlinedButton(
                        onClick = { draft = draft.copy(customTags = draft.customTags + ("" to "")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add tag") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
