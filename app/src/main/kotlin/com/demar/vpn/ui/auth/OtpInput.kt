package com.demar.vpn.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OtpInput(
  onComplete: () -> Unit,
  modifier: Modifier = Modifier,
  length: Int = 6,
) {
  var code by rememberSaveable { mutableStateOf("") }
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  BasicTextField(
    value = code,
    onValueChange = {
      code = it.filter(Char::isDigit).take(length)
      if (code.length == length) onComplete()
    },
    modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    singleLine = true,
    cursorBrush = SolidColor(Color.Transparent),
    textStyle = TextStyle(color = Color.Transparent),
    decorationBox = {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        repeat(length) { index ->
          OtpCell(
            digit = code.getOrNull(index)?.toString(),
            focused = code.length == index,
          )
        }
      }
    },
  )
}

@Composable
private fun OtpCell(digit: String?, focused: Boolean) {
  val shape = RoundedCornerShape(8.dp)
  val borderColor = when {
    focused -> MaterialTheme.colorScheme.primary
    digit != null -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.outlineVariant
  }
  Box(
    Modifier
      .width(44.dp)
      .height(52.dp)
      .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
      .border(1.dp, borderColor, shape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = digit.orEmpty(),
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
  }
}
