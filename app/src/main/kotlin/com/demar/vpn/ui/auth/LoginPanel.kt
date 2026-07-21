package com.demar.vpn.ui.auth

import android.util.Patterns
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.demar.vpn.R
import com.demar.vpn.ui.components.IconLinkChip
import com.demar.vpn.ui.components.StaticSheetDragHandle
import com.demar.vpn.ui.components.openSocialLink

private enum class LoginStep { Email, Verify, Totp }

private const val MockInvalidOtp = "000000"

private fun String.isValidEmail(): Boolean =
  isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(this).matches()

@Composable
fun LoginPanel(onSuccess: () -> Unit, modifier: Modifier = Modifier) {
  var step by rememberSaveable { mutableStateOf(LoginStep.Email) }
  var email by rememberSaveable { mutableStateOf("") }
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  var otpClearNonce by rememberSaveable { mutableStateOf(0) }
  val context = LocalContext.current
  val invalidEmail = stringResource(R.string.login_error_invalid_email)
  val invalidCode = stringResource(R.string.login_error_invalid_code)

  val title = when (step) {
    LoginStep.Email -> stringResource(R.string.login_sheet_title)
    LoginStep.Verify -> stringResource(R.string.login_otp_sent)
    LoginStep.Totp -> stringResource(R.string.login_totp)
  }

  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    StaticSheetDragHandle()
    Text(
      title,
      modifier = Modifier.fillMaxWidth(),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.titleLarge,
    )
    if (step == LoginStep.Verify) {
      Spacer(Modifier.height(8.dp))
      Text(
        email,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(Modifier.height(16.dp))
    AnimatedContent(step, label = "login_step") { s ->
      when (s) {
        LoginStep.Email -> EmailStep(
          email = email,
          error = error,
          onEmailChange = {
            email = it
            error = null
          },
          onSubmit = {
            if (!email.isValidEmail()) {
              error = invalidEmail
            } else {
              error = null
              step = LoginStep.Verify
            }
          },
        )
        LoginStep.Verify, LoginStep.Totp -> OtpStep(
          stepKey = s,
          error = error,
          clearNonce = otpClearNonce,
          onCodeChange = { error = null },
          onComplete = { code ->
            if (code == MockInvalidOtp) {
              error = invalidCode
              otpClearNonce++
            } else {
              error = null
              if (s == LoginStep.Verify) step = LoginStep.Totp else onSuccess()
            }
          },
          onResend = if (s == LoginStep.Verify) ({}) else null,
        )
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
private fun EmailStep(
  email: String,
  error: String?,
  onEmailChange: (String) -> Unit,
  onSubmit: () -> Unit,
) {
  Column {
    OutlinedTextField(
      value = email,
      onValueChange = onEmailChange,
      modifier = Modifier.fillMaxWidth(),
      placeholder = { Text(stringResource(R.string.login_email_hint)) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
      singleLine = true,
      isError = error != null,
      supportingText = {
        // Reserve one line so layout/padding stay stable with or without error.
        Text(
          error.orEmpty(),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          minLines = 1,
        )
      },
    )
    Spacer(Modifier.height(4.dp))
    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.login_continue))
    }
  }
}

@Composable
private fun OtpStep(
  stepKey: LoginStep,
  error: String?,
  clearNonce: Int,
  onCodeChange: (String) -> Unit,
  onComplete: (String) -> Unit,
  onResend: (() -> Unit)? = null,
) {
  Column {
    key(stepKey) {
      OtpInput(
        onComplete = onComplete,
        onCodeChange = onCodeChange,
        clearNonce = clearNonce,
      )
    }
    Text(
      error.orEmpty(),
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
      minLines = 1,
    )
    if (onResend != null) {
      TextButton(onClick = onResend, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text(stringResource(R.string.login_resend))
      }
    }
  }
}
