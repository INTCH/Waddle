package io.github.iefedev.waddle.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.iefedev.waddle.R
import io.github.iefedev.waddle.download.Downloads
import io.github.iefedev.waddle.download.Extras
import io.github.iefedev.waddle.engine.Entry
import io.github.iefedev.waddle.engine.Probe
import io.github.iefedev.waddle.engine.Quality
import io.github.iefedev.waddle.engine.Section
import io.github.iefedev.waddle.storage.Storage
import kotlin.math.roundToLong

/**
 * What the share panel and the in-app download screen ask before a download. [probe] arrives a moment
 * after the link: then a single video offers a time range, a multi-video link the list of its videos.
 */
class DownloadChoice(category: String, quality: Quality) {
    var name by mutableStateOf("")
    var category by mutableStateOf(category)
    var quality by mutableStateOf(quality)
    var probe by mutableStateOf<Probe?>(null)
        private set
    var section by mutableStateOf<Section?>(null)
    /** 1-based entry numbers to download, when [probe] has several. */
    var selected by mutableStateOf<Set<Int>>(emptySet())

    /** A new link (or none yet): the old video's range and selection don't carry over. */
    fun reset() {
        probe = null; section = null; selected = emptySet()
    }

    fun probed(p: Probe?) {
        probe = p
        section = null
        selected = p?.entries?.withIndex()?.filter { it.value.available }?.map { it.index + 1 }?.toSet().orEmpty()
    }

    val nothingSelected get() = probe?.multi == true && selected.isEmpty()

    fun enqueue(context: Context, url: String) {
        val p = probe
        val entries = p?.entries
        val items = if (entries != null && selected.size < entries.size) selected.sorted() else null
        Downloads.enqueue(
            context, url, name.ifBlank { p?.title.orEmpty() }, category, quality,
            Extras(section.takeIf { p?.multi != true }, p?.multi, items),
        )
    }
}

/**
 * Name field + one "folder · quality" row (opening a sheet with both pickers), then the range or the
 * video list once the link is probed. An empty name means "use the video's title".
 */
@Composable
fun DownloadOptions(choice: DownloadChoice, modifier: Modifier = Modifier) {
    var sheet by remember { mutableStateOf<OptionSheet?>(null) }
    val probe = choice.probe
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldCard(choice.name, { choice.name = it }, probe?.title ?: stringResource(R.string.hint_file_name), Lucide.Pencil)
        RowCard(onClick = { sheet = OptionSheet.FOLDER }) {
            Icon(Lucide.Folder, null, tint = Waddle.Ice, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(choice.category, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(choice.quality.summary(), color = Waddle.Muted, style = MaterialTheme.typography.bodyMedium)
            Icon(Lucide.ChevronRight, null, tint = Waddle.Muted)
        }
        val entries = probe?.entries
        val duration = probe?.durationSec
        if (entries != null) {
            RowCard(onClick = { sheet = OptionSheet.ENTRIES }) {
                Icon(Lucide.ListChecks, null, tint = Waddle.Ice, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(pluralStringResource(R.plurals.n_videos, entries.size, entries.size), Modifier.weight(1f))
                Text(
                    if (choice.selected.size == entries.size) stringResource(R.string.entries_all)
                    else stringResource(R.string.entries_selected, choice.selected.size),
                    color = if (choice.selected.isEmpty()) Waddle.ErrorText else Waddle.Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(Lucide.ChevronRight, null, tint = Waddle.Muted)
            }
        } else if (duration != null && duration > 1) {
            val s = choice.section
            RowCard(onClick = { sheet = OptionSheet.RANGE }) {
                Icon(Lucide.Scissors, null, tint = Waddle.Ice, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (s == null) stringResource(R.string.range_full) else stringResource(R.string.range_partial, s.label),
                    Modifier.weight(1f),
                )
                if (s == null) {
                    Text(Section.clock(duration), color = Waddle.Muted, style = MaterialTheme.typography.bodyMedium)
                    Icon(Lucide.ChevronRight, null, tint = Waddle.Muted)
                } else {
                    Icon(Lucide.X, stringResource(R.string.cd_download_full), tint = Waddle.Muted, modifier = Modifier.size(20.dp).clickable { choice.section = null })
                }
            }
        }
    }
    when (sheet) {
        OptionSheet.FOLDER -> OptionsSheet(choice.category, { choice.category = it }, choice.quality, { choice.quality = it }, onDismiss = { sheet = null })
        OptionSheet.RANGE -> RangeSheet(probe?.durationSec ?: 0, choice.section, { choice.section = it }, onDismiss = { sheet = null })
        OptionSheet.ENTRIES -> EntriesSheet(probe?.entries.orEmpty(), choice.selected, { choice.selected = it }, onDismiss = { sheet = null })
        null -> {}
    }
}

private enum class OptionSheet { FOLDER, RANGE, ENTRIES }

/** Start and end of the part to keep: a two-thumb slider plus typed m:ss fields, kept in step. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSheet(duration: Long, current: Section?, onSection: (Section?) -> Unit, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf((current?.start ?: 0L).toFloat()..(current?.end ?: duration).toFloat()) }
    var startText by remember { mutableStateOf(Section.clock(range.start.toLong())) }
    var endText by remember { mutableStateOf(Section.clock(range.endInclusive.toLong())) }
    val start = range.start.toLong()
    val end = range.endInclusive.toLong()
    val valid = end > start
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Waddle.Card,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.range_title), style = MaterialTheme.typography.titleMedium)
            RangeSlider(
                value = range,
                onValueChange = { r ->
                    range = r.start.roundToLong().toFloat()..r.endInclusive.roundToLong().toFloat()
                    startText = Section.clock(range.start.toLong())
                    endText = Section.clock(range.endInclusive.toLong())
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Waddle.Beak, activeTrackColor = Waddle.Beak, inactiveTrackColor = Waddle.Raised),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ClockField(stringResource(R.string.clip_start), startText, Modifier.weight(1f)) { t ->
                    startText = t
                    Section.parseClock(t)?.takeIf { it <= duration }?.let { range = it.toFloat()..range.endInclusive }
                }
                ClockField(stringResource(R.string.clip_end), endText, Modifier.weight(1f)) { t ->
                    endText = t
                    Section.parseClock(t)?.takeIf { it <= duration }?.let { range = range.start..it.toFloat() }
                }
            }
            Text(
                if (valid) stringResource(R.string.range_info, Section.clock(end - start))
                else stringResource(R.string.range_invalid),
                color = if (valid) Waddle.Muted else Waddle.ErrorText,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaddleButton(stringResource(R.string.range_full), { onSection(null); onDismiss() }, Modifier.weight(1f), primary = false)
                WaddleButton(stringResource(R.string.action_ok), {
                    if (!valid) return@WaddleButton
                    onSection(if (start == 0L && end >= duration) null else Section(start, end))
                    onDismiss()
                }, Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun ClockField(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    Column(modifier) {
        Text(label, color = Waddle.Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Surface(color = Waddle.Raised, contentColor = Waddle.Text, shape = MaterialTheme.shapes.medium) {
            BasicTextField(
                // Many number pads have no ':'; '.' and ',' type it instead.
                value, { t -> onValue(t.map { if (it == '.' || it == ',') ':' else it }.filter { it.isDigit() || it == ':' }.joinToString("")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Waddle.Text),
                cursorBrush = SolidColor(Waddle.Beak),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}

/** The videos of a carousel / playlist with a tick each; all ticked to start with. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntriesSheet(entries: List<Entry>, selected: Set<Int>, onSelected: (Set<Int>) -> Unit, onDismiss: () -> Unit) {
    val all = remember(entries) { entries.indices.map { it + 1 }.toSet() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Waddle.Card,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.entries_count, selected.size, entries.size), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Pill(stringResource(if (selected.size == entries.size) R.string.entries_select_none else R.string.entries_select_all), false, {
                    onSelected(if (selected.size == entries.size) emptySet() else all)
                }, Lucide.CheckCheck)
            }
            LazyColumn(Modifier.weight(1f, fill = false).padding(vertical = 8.dp)) {
                itemsIndexed(entries) { i, e ->
                    val n = i + 1
                    val on = n in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelected(if (on) selected - n else selected + n) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (on) Lucide.CircleCheck else Lucide.Circle, null,
                            tint = if (on) Waddle.Beak else Waddle.Muted, modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Box {
                            RemoteThumb(e.thumb, Modifier.size(width = 80.dp, height = 45.dp).clip(MaterialTheme.shapes.small))
                            e.durationSec?.let {
                                Text(
                                    Section.clock(it), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 3.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            e.title ?: stringResource(R.string.entry_fallback, n), Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = if (on) Waddle.Text else Waddle.Muted, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            WaddleButton(stringResource(R.string.action_ok), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Quality.summary(): String =
    if (audioOnly) stringResource(label) else stringResource(R.string.quality_summary_video, stringResource(label))

/** A flat card row; tappable when [onClick] is given. */
@Composable
fun RowCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Surface(
        color = Waddle.Card,
        contentColor = Waddle.Text,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

/** A single-line text field that looks like a card row, with a muted placeholder. */
@Composable
fun FieldCard(
    value: String, onValue: (String) -> Unit, placeholder: String, icon: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    RowCard {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Waddle.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value, onValue,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Waddle.Text),
                cursorBrush = SolidColor(Waddle.Beak),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (icon != null) Icon(icon, null, tint = Waddle.Muted, modifier = Modifier.size(20.dp))
        trailing?.invoke(this)
    }
}

/** Full-width button that squashes a little when pressed. [primary] = the orange action. */
@Composable
fun WaddleButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "press")
    Surface(
        onClick = onClick,
        interactionSource = press,
        color = if (primary) Waddle.Beak else Waddle.Card,
        contentColor = if (primary) Waddle.OnBeak else Waddle.Text,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.scale(s),
    ) {
        Box(Modifier.padding(vertical = 15.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/** Rounded pill; orange when [selected]. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Waddle.Beak else Waddle.Raised,
        contentColor = if (selected) Waddle.OnBeak else Color(0xFFC6D2E6),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun OptionsSheet(
    category: String,
    onCategory: (String) -> Unit,
    quality: Quality,
    onQuality: (Quality) -> Unit,
    onDismiss: () -> Unit,
) {
    var categories by remember { mutableStateOf(Storage.categories()) }
    var newDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Waddle.Card,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.options_folder), color = Waddle.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c -> Pill(c, c == category, { onCategory(c) }) }
                Pill(stringResource(R.string.action_new), false, { newDialog = true }, Lucide.Plus)
            }
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.options_quality), color = Waddle.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Quality.entries.forEach { q -> Pill(stringResource(q.label), q == quality, { onQuality(q) }) }
            }
            Spacer(Modifier.size(4.dp))
            WaddleButton(stringResource(R.string.action_ok), onDismiss, Modifier.fillMaxWidth())
        }
    }
    if (newDialog) {
        NewCategoryDialog(
            onDismiss = { newDialog = false },
            onCreate = { raw ->
                Storage.createCategory(raw)?.let { created ->
                    categories = Storage.categories()
                    onCategory(created)
                }
                newDialog = false
            },
        )
    }
}

@Composable
fun NewCategoryDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder_title)) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) })
        },
        confirmButton = {
            TextButton(onClick = { onCreate(text) }, enabled = Storage.sanitize(text).isNotEmpty()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
