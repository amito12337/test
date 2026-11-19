package com.focuslock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.ui.theme.FocusColors

@Composable
fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FocusColors.Surface, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(title.uppercase(), color = FocusColors.Muted, fontSize = 12.sp, letterSpacing = 1.5.sp)
        }
        content()
    }
}

@Composable
fun StatusRow(
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String,
    optional: Boolean = false,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StatusDot(ok = ok, optional = optional)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = FocusColors.Text, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(detail, color = FocusColors.Muted, fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (!ok) {
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun StatusDot(ok: Boolean, optional: Boolean) {
    val color = when {
        ok -> FocusColors.Accent
        optional -> FocusColors.Muted
        else -> FocusColors.Error
    }
    Box(
        Modifier
            .size(28.dp)
            .then(
                if (ok) Modifier.background(color, CircleShape)
                else Modifier.border(2.dp, color, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (ok) Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color(0xFF06140E), modifier = Modifier.size(18.dp))
    }
}

/** No copy/paste/cut/select-all menu. */
object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden
    override fun hide() = Unit
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}

/** Clipboard that is always empty, so Ctrl+V / paste shortcuts insert nothing. */
object NoClipboard : ClipboardManager {
    override fun getText(): AnnotatedString? = null
    override fun setText(annotatedString: AnnotatedString) = Unit
    override fun hasText(): Boolean = false
}
