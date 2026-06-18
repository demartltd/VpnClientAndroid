package com.demar.vpn.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.demar.vpn.R
import com.demar.vpn.ui.components.IconLinkChip
import com.demar.vpn.ui.components.openSocialLink

private enum class LoginStep { Email, Verify, Totp }

@Composable
fun LoginPanel(onSuccess: () -> Unit, modifier: Modifier = Modifier) {
  var step by rememberSaveable { mutableStateOf(LoginStep.Email) }
  var email by rememberSaveable { mutableStateOf("") }
  var busy by rememberSaveable { mutableStateOf(false) }

  val context = LocalContext.current
  Column(modifier.fillMaxWidth()) {
    AnimatedContent(step, label = "login_step") { s ->
      when (s) {
        LoginStep.Email -> EmailStep(
          email = email,
          onEmailChange = { email = it },
          busy = busy,
          onSubmit = { step = LoginStep.Verify },
        )
        LoginStep.Verify -> VerifyStep(
          email = email,
          busy = busy,
          onComplete = { step = LoginStep.Totp },
          onResend = {},
        )
        LoginStep.Totp -> TotpStep(busy = busy, onComplete = onSuccess)
      }
    }
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      HorizontalDivider(Modifier.weight(1f))
      Text(
        stringResource(R.string.login_more_methods),
        modifier = Modifier.padding(horizontal = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      HorizontalDivider(Modifier.weight(1f))
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
      IconLinkChip(R.drawable.ic_brand_telegram, onClick = { openSocialLink(context, "https://t.me/demargames") })
    }
  }
}

@Composable
private fun EmailStep(email: String, onEmailChange: (String) -> Unit, busy: Boolean, onSubmit: () -> Unit) {
  Column {
    OutlinedTextField(
      value = email,
      onValueChange = onEmailChange,
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text(stringResource(R.string.login_email_hint)) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
      Text(if (busy) stringResource(R.string.login_sending) else stringResource(R.string.login_continue))
    }
  }
}

@Composable
private fun VerifyStep(email: String, busy: Boolean, onComplete: () -> Unit, onResend: () -> Unit) {
  Column {
    Text(stringResource(R.string.login_otp_sent, email), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OtpField(enabled = !busy, onComplete = onComplete)
    TextButton(onClick = onResend, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
      Text(stringResource(R.string.login_resend))
    }
  }
}

@Composable
private fun TotpStep(busy: Boolean, onComplete: () -> Unit) {
  Column {
    Text(stringResource(R.string.login_totp), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    OtpField(enabled = !busy, onComplete = onComplete)
  }
}

@Composable
private fun OtpField(enabled: Boolean, onComplete: () -> Unit) {
  var code by rememberSaveable { mutableStateOf("") }
  OutlinedTextField(
    value = code,
    onValueChange = {
      code = it.filter(Char::isDigit).take(6)
      if (code.length == 6) onComplete()
    },
    enabled = enabled,
    modifier = Modifier.fillMaxWidth(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    singleLine = true,
    textStyle = MaterialTheme.typography.headlineSmall,
  )
}
