package com.shellbox.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shellbox.ui.terminal.TerminalFont
import com.shellbox.ui.terminal.TerminalFontDefaults
import com.shellbox.ui.terminal.TerminalSettingsStore
import com.shellbox.ui.terminal.TerminalTypefaceCache
import com.shellbox.ui.theme.Blue40
import com.shellbox.ui.theme.Blue90
import com.shellbox.ui.theme.Blue95
import kotlin.math.roundToInt

/**
 * Standalone settings screen for configuring the terminal's appearance:
 * font family (system + 3 bundled monospace fonts) and font size.
 * Changes are written through [TerminalSettingsStore] immediately and
 * persist across app restarts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { TerminalSettingsStore.getInstance(context) }
    val fontSize by settingsStore.fontSize.collectAsState()
    val selectedFont by settingsStore.font.collectAsState()

    // Local slider state lets the thumb move smoothly; committed on release.
    var sliderValue by remember(fontSize) { mutableFloatStateOf(fontSize) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("终端外观设置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // ---------------------------------------------------------
            // Live preview — renders a sample terminal line using the
            // currently selected font + size so changes are felt instantly.
            // ---------------------------------------------------------
            PreviewCard(fontSizeSp = sliderValue, font = selectedFont)

            Spacer(Modifier.height(28.dp))

            // ---------------------------------------------------------
            // Font size
            // ---------------------------------------------------------
            SectionHeader("字体大小")
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${sliderValue.roundToInt()}sp",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Blue40,
                    modifier = Modifier.width(48.dp)
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { settingsStore.setFontSize(sliderValue) },
                    valueRange = TerminalFontDefaults.MIN_SIZE..TerminalFontDefaults.MAX_SIZE,
                    steps = (TerminalFontDefaults.MAX_SIZE - TerminalFontDefaults.MIN_SIZE).toInt() - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = Blue40,
                        activeTrackColor = Blue40,
                        inactiveTrackColor = Blue90
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${TerminalFontDefaults.MIN_SIZE.roundToInt()}sp",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${TerminalFontDefaults.MAX_SIZE.roundToInt()}sp",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(28.dp))

            // ---------------------------------------------------------
            // Font family
            // ---------------------------------------------------------
            SectionHeader("终端字体")
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(TerminalFont.entries.toList()) { font ->
                    FontOptionCard(
                        font = font,
                        isSelected = font == selectedFont,
                        onClick = { settingsStore.setFont(font) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PreviewCard(fontSizeSp: Float, font: TerminalFont) {
    val context = LocalContext.current
    val typeface = remember(font) { TerminalTypefaceCache.resolve(context, font) }
    val composeFontFamily = remember(typeface) {
        androidx.compose.ui.text.font.FontFamily(typeface)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF27C93F)))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "root@shellbox:~# echo \"Hello, World!\"",
                color = Color(0xFF8AE234),
                fontSize = fontSizeSp.sp,
                fontFamily = composeFontFamily
            )
            Text(
                "Hello, World! 你好，世界！",
                color = Color(0xFFEEEEEC),
                fontSize = fontSizeSp.sp,
                fontFamily = composeFontFamily
            )
            Text(
                "root@shellbox:~# _",
                color = Color(0xFF8AE234),
                fontSize = fontSizeSp.sp,
                fontFamily = composeFontFamily
            )
        }
    }
}

@Composable
private fun FontOptionCard(
    font: TerminalFont,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val typeface = remember(font) { TerminalTypefaceCache.resolve(context, font) }
    val composeFontFamily = remember(typeface) {
        androidx.compose.ui.text.font.FontFamily(typeface)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Blue95 else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) Blue40 else Color(0xFFE5E5EA)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    font.displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = if (isSelected) Blue40 else Color.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "0123 abc",
                    fontFamily = composeFontFamily,
                    fontSize = 13.sp,
                    color = if (isSelected) Blue40 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Blue40),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
