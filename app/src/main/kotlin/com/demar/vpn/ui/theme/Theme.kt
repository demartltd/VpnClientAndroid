package com.demar.vpn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

private val DemarShapes = Shapes(
  extraSmall = RoundedCornerShape(12.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(16.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DemarTheme(content: @Composable () -> Unit) {
  val colorScheme = rememberDynamicColorScheme(
    seedColor = DemarSeed,
    isDark = true,
    style = PaletteStyle.TonalSpot,
  )
  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    shapes = DemarShapes,
    content = content,
  )
}
