package io.github.iefedev.waddle.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.annotation.StringRes
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.Prefs
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.storage.Library
import io.github.iefedev.waddle.storage.Media
import io.github.iefedev.waddle.storage.MediaInfo
import io.github.iefedev.waddle.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Folders → files browser over Waddle/Downloads; the disk is the source of truth. */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    BackHandler(enabled = open != null || query.isNotEmpty()) { if (open != null) open = null else query = "" }

    // A tap that no tile or button takes (empty space) drops the search field's focus and the keyboard.
    val focusManager = LocalFocusManager.current
    val m = modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
    val cat = open
    if (cat == null) GalleryHome(query, { query = it }, refresh, onOpen = { open = it }, onChanged = { refresh++ }, modifier = m)
    else FileGrid(cat, refresh, onBack = { open = null }, onChanged = { refresh++ }, modifier = m)
}

@Composable
private fun GalleryHome(
    query: String, onQuery: (String) -> Unit, refresh: Int,
    onOpen: (String) -> Unit, onChanged: () -> Unit, modifier: Modifier,
) {
    var newDialog by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        SearchField(query, onQuery, Modifier.fillMaxWidth().padding(12.dp, 12.dp, 12.dp, 0.dp))
        if (query.isBlank()) CategoryGrid(refresh, onOpen, onNew = { newDialog = true }, onChanged = onChanged)
        else SearchResults(query, refresh, onChanged)
    }
    if (newDialog) {
        NewCategoryDialog(onDismiss = { newDialog = false }, onCreate = {
            Storage.createCategory(it); newDialog = false; onChanged()
        })
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier, autoFocus: Boolean = false) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        query, onQuery,
        modifier.focusRequester(focus),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Lucide.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Lucide.X, stringResource(R.string.cd_clear)) }
        },
        shape = RoundedCornerShape(28.dp),
    )
}

/** 40 dp search field that fits the folder header without making it taller than the 48 dp icon buttons. */
@Composable
private fun HeaderSearchField(query: String, onQuery: (String) -> Unit, onBlur: () -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        query, onQuery,
        modifier.height(40.dp).focusRequester(focus).onFocusChanged {
            if (it.isFocused) wasFocused = true else if (wasFocused) onBlur()
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().border(1.dp, colors.primary, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Search, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    if (query.isEmpty()) Text(stringResource(R.string.search_in_folder), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    inner()
                }
                if (query.isNotEmpty()) {
                    Icon(Lucide.X, stringResource(R.string.cd_clear), Modifier.size(20.dp).clickable { onQuery("") }, tint = colors.onSurfaceVariant)
                }
            }
        },
    )
}

/**
 * Case- and accent-insensitive form, so "insta" finds "Instagram" and "cafe" finds "Café". The
 * dotless i (U+0131) has no decomposition, so it is folded to a plain i by hand.
 */
private fun fold(s: String) =
    Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(Char(0x131), 'i')

private val COMBINING_MARKS = Regex("""\p{Mn}+""")

/** Filename or embedded title contains [query]; blocking (may read the file's tags). */
private fun List<File>.search(context: android.content.Context, query: String): List<File> {
    val q = fold(query.trim())
    if (q.isEmpty()) return this
    return filter { f -> q in fold(f.nameWithoutExtension) || Media.info(context, f).title?.let { q in fold(it) } == true }
}

@Composable
private fun SearchResults(query: String, refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val hits by produceState<List<File>?>(null, query, refresh) {
        delay(200)
        value = withContext(Dispatchers.IO) {
            Storage.categories().flatMap { Storage.files(it) }.search(context, query).sortedByDescending { it.lastModified() }
        }
    }
    MediaGrid(hits, empty = stringResource(R.string.no_results), onChanged = onChanged)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryGrid(refresh: Int, onOpen: (String) -> Unit, onNew: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<Triple<FolderAction, String, Int>?>(null) }
    // Last listing first, so coming back to the tab or out of a folder never draws an empty grid.
    val cats by produceState(Library.folders.orEmpty(), refresh) {
        value = withContext(Dispatchers.IO) { Library.listFolders() }
    }
    LazyVerticalGrid(
        GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(cats, key = { it.first }) { (name, files) ->
            Box {
                Column(Modifier.combinedClickable(onClick = { onOpen(name) }, onLongClick = { menuFor = name })) {
                    // Newest first on disk; the cover shows the earliest downloads.
                    FolderCover(files.takeLast(4).reversed())
                    Text(
                        name, Modifier.padding(top = 6.dp, start = 2.dp),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(expanded = menuFor == name, onDismissRequest = { menuFor = null }) {
                    FolderAction.entries.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(stringResource(a.label)) },
                            leadingIcon = { Icon(a.icon(), null) },
                            onClick = { menuFor = null; action = Triple(a, name, files.size) },
                        )
                    }
                }
            }
        }
        item(key = "+new") {
            Column(Modifier.clickable(onClick = onNew)) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.FolderPlus, null, tint = MaterialTheme.colorScheme.primary) }
                Text(stringResource(R.string.new_folder_title), Modifier.padding(top = 6.dp, start = 2.dp), style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    action?.let { (a, name, count) ->
        val done = { ok: Boolean ->
            if (!ok) Toast.makeText(context, R.string.toast_folder_action_failed, Toast.LENGTH_SHORT).show()
            action = null; onChanged()
        }
        when (a) {
            FolderAction.RENAME -> RenameDialog(name, onDismiss = { action = null }) { newName ->
                val renamed = Storage.renameCategory(context, name, newName)
                if (renamed != null && Prefs.lastCategory(context) == name) Prefs.setLastCategory(context, renamed)
                done(renamed != null)
            }
            FolderAction.MERGE -> MoveDialog(name, onDismiss = { action = null }) { target ->
                if (Prefs.lastCategory(context) == name) Prefs.setLastCategory(context, target)
                done(Storage.mergeCategory(context, name, target))
            }
            FolderAction.DELETE -> AlertDialog(
                onDismissRequest = { action = null },
                title = { Text(stringResource(R.string.delete_folder_title, name)) },
                text = {
                    Text(
                        if (count == 0) stringResource(R.string.delete_folder_empty)
                        else pluralStringResource(R.plurals.delete_folder_body, count, count),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { done(Storage.deleteCategory(context, name)) }) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = { TextButton(onClick = { action = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** Square collage of up to four files: 1 full, 2 side by side, 3 big + two stacked, 4 as 2×2. */
@Composable
private fun FolderCover(files: List<File>) {
    val gap = Arrangement.spacedBy(2.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val cell = Modifier.fillMaxSize()
        when (files.size) {
            0 -> Icon(Lucide.Folder, null, tint = MaterialTheme.colorScheme.primary)
            1 -> Thumb(files[0], cell)
            2 -> Row(cell, horizontalArrangement = gap) { files.forEach { Thumb(it, Modifier.weight(1f).fillMaxHeight()) } }
            3 -> Row(cell, horizontalArrangement = gap) {
                Thumb(files[0], Modifier.weight(1f).fillMaxHeight(), small = Library.coverCellIsSmall(3, 0))
                Column(Modifier.weight(1f), verticalArrangement = gap) {
                    Thumb(files[1], Modifier.weight(1f).fillMaxWidth(), small = Library.coverCellIsSmall(3, 1))
                    Thumb(files[2], Modifier.weight(1f).fillMaxWidth(), small = Library.coverCellIsSmall(3, 2))
                }
            }
            else -> Column(cell, verticalArrangement = gap) {
                files.chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f), horizontalArrangement = gap) {
                        pair.forEach { Thumb(it, Modifier.weight(1f).fillMaxHeight(), small = true) }
                    }
                }
            }
        }
    }
}

private enum class FolderAction(@param:StringRes val label: Int, val icon: @Composable () -> ImageVector) {
    RENAME(R.string.action_rename, { Lucide.Pencil }),
    MERGE(R.string.action_merge, { Lucide.FolderInput }),
    DELETE(R.string.action_delete, { Lucide.Trash }),
}

@Composable
private fun FileGrid(category: String, refresh: Int, onBack: () -> Unit, onChanged: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    var searching by rememberSaveable(category) { mutableStateOf(false) }
    var query by rememberSaveable(category) { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; query = "" }
    val files by produceState(Library.files[category], category, refresh, query) {
        if (query.isNotBlank()) delay(200)
        value = withContext(Dispatchers.IO) { Library.listFiles(category).search(context, query) }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Lucide.ArrowLeft, stringResource(R.string.cd_back)) }
            if (searching) {
                // Losing focus with nothing typed goes back to the title + search button; with a query the
                // field stays so the filtered results keep their label.
                HeaderSearchField(query, { query = it }, onBlur = { if (query.isBlank()) searching = false }, Modifier.weight(1f).padding(end = 8.dp))
            } else {
                Text(category, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { searching = true }) { Icon(Lucide.Search, stringResource(R.string.search_in_folder)) }
            }
        }
        if (query.isBlank()) MediaGrid(files, empty = stringResource(R.string.folder_empty), emptyMood = Mood.Peek, onChanged = onChanged)
        else MediaGrid(files, empty = stringResource(R.string.no_results), onChanged = onChanged)
    }
}

private const val ROW_HEIGHT = 140f
private const val GAP = 3f

/** One justified row: [items] indices, their [height] in dp; [full] rows stretch to the edge. */
private data class JRow(val items: IntRange, val height: Float, val full: Boolean)

/**
 * Google Photos-style rows: tiles keep their aspect ratio and each full row is scaled to the
 * width, breaking where the row height lands closest to [target].
 */
private fun justify(aspects: List<Float>, width: Float, target: Float, gap: Float): List<JRow> {
    val rows = mutableListOf<JRow>()
    var start = 0
    var sum = 0f
    fun heightOf(s: Float, n: Int) = (width - gap * (n - 1)) / s
    for (i in aspects.indices) {
        val a = aspects[i]
        sum += a
        val n = i - start + 1
        if (sum * target + gap * (n - 1) < width) continue
        val with = heightOf(sum, n)
        if (n > 1 && kotlin.math.abs(heightOf(sum - a, n - 1) - target) < kotlin.math.abs(with - target)) {
            rows += JRow(start until i, heightOf(sum - a, n - 1), true)
            start = i
            sum = a
        } else {
            rows += JRow(start..i, with, true)
            start = i + 1
            sum = 0f
        }
    }
    if (start < aspects.size) rows += JRow(start until aspects.size, target, false)
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(files: List<File>?, empty: String, onChanged: () -> Unit, emptyMood: Mood? = null) {
    val context = LocalContext.current
    // Drawn straight from memory when every file's metadata is already cached; the disk only for the rest.
    fun fromMemory(fs: List<File>?) = fs?.map { f -> f to (Media.cachedInfo(f) ?: return null) }
    val entries by produceState(fromMemory(files), files) {
        val fs = files ?: return@produceState
        value = fromMemory(fs) ?: withContext(Dispatchers.IO) { fs.map { it to Media.info(context, it) }.also { Media.save(context) } }
    }
    // Long press opens the file's menu; its "Select" starts selecting, and while anything is selected a tap toggles instead of opening.
    var chosen by remember { mutableStateOf<Set<File>>(emptySet()) }
    var menuFor by remember { mutableStateOf<File?>(null) }
    var action by remember { mutableStateOf<Pair<FileAction, List<File>>?>(null) }
    val list = entries
    // Files that left the list (moved, deleted, filtered out by search) drop out of the selection.
    val selected = remember(chosen, list) { list?.let { l -> l.map { it.first }.filter { it in chosen }.toSet() }.orEmpty() }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { chosen = emptySet() }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                allSelected = list != null && selected.size == list.size,
                onClose = { chosen = emptySet() },
                onAll = { chosen = if (list != null && selected.size == list.size) emptySet() else list.orEmpty().map { it.first }.toSet() },
                onShare = { context.startActivity(Storage.shareIntent(context, selected.toList())) },
                onRename = if (selected.size == 1) ({ action = FileAction.RENAME to selected.toList() }) else null,
                onMove = { action = FileAction.MOVE to selected.toList() },
                onDelete = { action = FileAction.DELETE to selected.toList() },
            )
        }
        when {
            list == null -> {}
            list.isEmpty() && emptyMood != null -> Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Penguin(emptyMood, 96.dp)
                Text(empty, color = Waddle.Muted)
            }
            list.isEmpty() -> Text(empty, Modifier.padding(16.dp))
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val pad = 8f
                val rows = remember(list, maxWidth) {
                    justify(list.map { it.second.aspect.coerceIn(0.5f, 2.4f) }, maxWidth.value - 2 * pad, ROW_HEIGHT, GAP)
                }
                LazyColumn(
                    contentPadding = PaddingValues(pad.dp),
                    verticalArrangement = Arrangement.spacedBy(GAP.dp),
                ) {
                    items(rows, key = { list[it.items.first].first.absolutePath }) { row ->
                        Row(Modifier.fillMaxWidth().height(row.height.dp), horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                            for (i in row.items) {
                                val (f, info) = list[i]
                                // The last tile of a full row absorbs rounding so the right edge stays flush.
                                val size = if (row.full && i == row.items.last) Modifier.weight(1f)
                                else Modifier.width((info.aspect.coerceIn(0.5f, 2.4f) * row.height).dp)
                                val on = f in selected
                                Box(size.fillMaxHeight()) {
                                    Tile(
                                        f, info, if (selecting) on else null,
                                        Modifier.fillMaxSize().combinedClickable(
                                            onClick = {
                                                if (selecting) chosen = if (on) selected - f else selected + f
                                                else context.startActivity(Storage.openIntent(context, f))
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (selecting) chosen = selected + f else menuFor = f
                                            },
                                        ),
                                    )
                                    DropdownMenu(expanded = menuFor == f, onDismissRequest = { menuFor = null }) {
                                        DropdownMenuItem(
                                            text = { Text(f.nameWithoutExtension, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                            onClick = {}, enabled = false,
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_select)) },
                                            leadingIcon = { Icon(Lucide.ListChecks, null) },
                                            onClick = { menuFor = null; chosen = setOf(f) },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_share)) },
                                            leadingIcon = { Icon(Lucide.Share, null) },
                                            onClick = { menuFor = null; context.startActivity(Storage.shareIntent(context, f)) },
                                        )
                                        FileAction.entries.forEach { a ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(a.label)) },
                                                leadingIcon = { Icon(a.icon(), null) },
                                                onClick = { menuFor = null; action = a to listOf(f) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    action?.let { (a, files) ->
        val done = { ok: Boolean ->
            if (!ok) Toast.makeText(context, R.string.toast_some_failed, Toast.LENGTH_SHORT).show()
            action = null; chosen = emptySet(); onChanged()
        }
        when (a) {
            FileAction.RENAME -> files.singleOrNull()?.let { f ->
                RenameDialog(f.nameWithoutExtension, onDismiss = { action = null }) { done(Storage.rename(context, f, it) != null) }
            }
            // Search results can span folders: only a folder every file is already in is left out.
            FileAction.MOVE -> MoveDialog(files.map { it.parentFile?.name.orEmpty() }.distinct().singleOrNull().orEmpty(), onDismiss = { action = null }) { target ->
                done(files.map { Storage.move(context, it, target) }.all { it != null })
            }
            FileAction.DELETE -> AlertDialog(
                onDismissRequest = { action = null },
                title = {
                    Text(
                        if (files.size == 1) stringResource(R.string.delete_file_title)
                        else stringResource(R.string.delete_files_title, files.size),
                    )
                },
                text = {
                    Text(
                        if (files.size == 1) stringResource(R.string.delete_file_body, files[0].name)
                        else stringResource(R.string.delete_files_body, files.size),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { done(files.map { Storage.delete(context, it) }.all { it }) }) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = { TextButton(onClick = { action = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

private enum class FileAction(@param:StringRes val label: Int, val icon: @Composable () -> ImageVector) {
    RENAME(R.string.action_rename, { Lucide.Pencil }),
    MOVE(R.string.action_move, { Lucide.FolderInput }),
    DELETE(R.string.action_delete, { Lucide.Trash }),
}

/** Above the grid while files are selected; rename only shows for exactly one. */
@Composable
private fun SelectionBar(
    count: Int, allSelected: Boolean,
    onClose: () -> Unit, onAll: () -> Unit, onShare: () -> Unit,
    onRename: (() -> Unit)?, onMove: () -> Unit, onDelete: () -> Unit,
) {
    Surface(color = Waddle.Card, contentColor = Waddle.Text, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(8.dp, 4.dp, 8.dp, 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Lucide.X, stringResource(R.string.cd_end_selection)) }
            Text(stringResource(R.string.n_selected, count), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onAll) {
                Icon(
                    Lucide.CheckCheck,
                    stringResource(if (allSelected) R.string.cd_deselect_all else R.string.cd_select_all),
                    tint = if (allSelected) Waddle.Beak else Waddle.Text,
                )
            }
            IconButton(onClick = onShare) { Icon(Lucide.Share, stringResource(R.string.action_share)) }
            if (onRename != null) IconButton(onClick = onRename) { Icon(Lucide.Pencil, stringResource(R.string.action_rename)) }
            IconButton(onClick = onMove) { Icon(Lucide.FolderInput, stringResource(R.string.action_move)) }
            IconButton(onClick = onDelete) { Icon(Lucide.Trash, stringResource(R.string.action_delete)) }
        }
    }
}

/** [selected] null = not selecting; otherwise a tick in the corner, and an orange frame when on. */
@Composable
private fun Tile(file: File, info: MediaInfo, selected: Boolean?, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(modifier.clip(shape).then(if (selected == true) Modifier.border(3.dp, Waddle.Beak, shape) else Modifier)) {
        Thumb(file, Modifier.fillMaxSize(), audioIcon = 36.dp)
        if (selected != null) {
            Icon(
                if (selected) Lucide.CircleCheck else Lucide.Circle, null,
                tint = if (selected) Waddle.Beak else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                    .background(Color.Black.copy(alpha = 0.35f), androidx.compose.foundation.shape.CircleShape),
            )
        }
        // Name bottom-left, duration bottom-right, over a fade so both read on bright frames.
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(6.dp, 14.dp, 6.dp, 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                file.nameWithoutExtension, Modifier.weight(1f),
                color = Color.White, style = MaterialTheme.typography.labelMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (info.durationMs > 0) {
                Text(
                    formatDuration(info.durationMs), Modifier.padding(start = 4.dp),
                    color = Color.White, style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    // Whole name selected, so typing replaces it and arrow keys still allow editing.
    var field by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                field, { field = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(field.text) }, enabled = Storage.sanitize(field.text).isNotEmpty()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MoveDialog(current: String, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    val targets = remember { Storage.categories().filter { it != current } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            if (targets.isEmpty()) Text(stringResource(R.string.move_no_targets))
            Column {
                targets.forEach { t ->
                    TextButton(onClick = { onMove(t) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Lucide.Folder, null)
                        Text(t, Modifier.padding(start = 8.dp).weight(1f))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Cropped thumbnail. Audio is dimmed with a note in the middle, so it never reads as a video. */
@Composable
private fun Thumb(file: File, modifier: Modifier, audioIcon: Dp = 24.dp, small: Boolean = false) {
    val context = LocalContext.current
    val bmp by produceState(Media.cachedThumb(file, small), file.absolutePath, file.lastModified()) {
        if (value == null) value = withContext(Dispatchers.IO) { Media.thumb(context, file, small) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b: Bitmap? = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (Storage.mimeOf(file) == "audio/*") {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Lucide.Music, stringResource(R.string.cd_audio), Modifier.size(audioIcon), tint = Color.White)
            }
        }
    }
}
