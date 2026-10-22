package com.focustask.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focustask.core.BlockFit
import com.focustask.core.Span
import com.focustask.data.AppData
import com.focustask.data.BLOCK_COLORS
import com.focustask.ui.theme.Palette

/** The temporary block of a tap on an empty spot: one hour, until it is saved or dropped. */
data class Ghost(val column: Int, val startMin: Int, val endMin: Int) {
    companion object {
        /** The whole hour that contains [minute]. */
        fun hourAt(column: Int, minute: Int): Ghost {
            val start = (minute / 60 * 60).coerceIn(0, 23 * 60)
            return Ghost(column, start, start + 60)
        }

        /** The hour at [minute], made smaller where it would run into other blocks of the day; null when there is no room. */
        fun fitted(column: Int, minute: Int, others: List<Span>): Ghost? {
            val h = hourAt(column, minute)
            val (s, e) = BlockFit.room(h.startMin, minute, 60, others) ?: return null
            return Ghost(column, s, e)
        }
    }
}

fun clockText(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** What the temporary block shows inside the grid. */
@Composable
fun GhostContent(ghost: Ghost, listTitle: String?, color: Color, wide: Boolean) {
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(if (wide) 14.dp else 12.dp))
            .background(color.copy(alpha = 0.72f))
            .padding(horizontal = if (wide) 12.dp else 0.dp, vertical = if (wide) 5.dp else 0.dp),
    ) {
        if (wide) Column {
            Text(listTitle ?: "New block", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${clockText(ghost.startMin)} to ${clockText(ghost.endMin)}", color = Color.White, fontSize = 13.sp, maxLines = 1)
        }
    }
}

/**
 * The quick settings under the grid while a temporary block is shown: pick the list, save.
 * Everything else (days, color, exact times) is behind "More options".
 */
@Composable
fun QuickBlockPanel(
    data: AppData, p: Palette, title: String, note: String, listId: Long?,
    onList: (Long) -> Unit, onNewList: () -> Unit,
    onSave: () -> Unit, onCancel: () -> Unit,
    /** The "More options" button; null leaves it out. */
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(color = p.surface, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(note, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ContainedButton("Cancel", p, onClick = onCancel, modifier = Modifier.padding(end = 8.dp))
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                data.lists.forEach { l ->
                    val on = l.id == listId
                    Row(
                        Modifier.testTag("list-chip").height(44.dp).clip(CircleShape)
                            .background(if (on) p.accent else p.chip)
                            .clickable { onList(l.id) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(12.dp).clip(CircleShape)
                                .background(Color(BLOCK_COLORS[data.colorOfListIndex(l.id) % BLOCK_COLORS.size]))
                                .then(if (on) Modifier.border(1.5.dp, p.onAccent, CircleShape) else Modifier),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(l.title, color = if (on) p.onAccent else p.ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                    }
                }
                Row(
                    Modifier.height(44.dp).clip(CircleShape).background(p.chip).clickable(onClick = onNewList).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = p.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New list", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onMore != null) ContainedButton("More options", p, onClick = onMore)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSave, enabled = listId != null,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                ) { Text(if (listId == null) "Pick a list" else "Save", fontWeight = FontWeight.Bold) }
            }
        }
    }
}


/** Shown while several blocks are selected (after a long press): what is selected, and Delete / Cancel. */
@Composable
fun SelectionBar(count: Int, p: Palette, onDelete: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = p.surface, shape = RoundedCornerShape(16.dp), modifier = modifier.fillMaxWidth().testTag("selection-bar")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$count selected", color = p.ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Tap blocks to add or remove them", color = p.muted, fontSize = 12.sp)
            }
            ContainedButton("Cancel", p, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            ContainedButton("Delete", p, tone = ButtonTone.DANGER, onClick = onDelete, modifier = Modifier.testTag("selection-delete"))
        }
    }
}
