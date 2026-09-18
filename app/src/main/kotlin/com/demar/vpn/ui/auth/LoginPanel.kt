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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.demar.vpn.R
import com.demar.vpn.data.auth.AuthApi
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.auth.EmailConfirmResult
import com.demar.vpn.data.auth.ReviewAuth
import com.demar.vpn.ui.components.IconLinkChip
import com.demar.vpn.ui.components.openAuthUrl
import kotlinx.coroutines.launch

private enum class LoginStep { Email, Verify, Totp }

private fun String.isValidEmail(): Boolean =
  isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(this).matches()

@Composable
fun LoginPanel(onSuccess: () -> Unit, modifier: Modifier = Modifier) {
  var step by rememberSaveable { mutableStateOf(LoginStep.Email) }
  var email by rememberSaveable { mutableStateOf("") }
  var registering by rememberSaveable { mutableStateOf(false) }
  var showRegisterDialog by rememberSaveable { mutableStateOf(false) }
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  var challengeToken by rememberSaveable { mutableStateOf<String?>(null) }
  var otpClearNonce by rememberSaveable { mutableStateOf(0) }
  var busy by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val invalidEmail = stringResource(R.string.login_error_invalid_email)
  val invalidCode = stringResource(R.string.login_error_invalid_code)
  val genericError = stringResource(R.string.login_error_generic)

  fun purpose(): String = if (registering) "register" else "login"

  fun runAuth(block: suspend () -> Unit) {
    if (busy) return
    busy = true
    error = null
    scope.launch {
      try {
        block()
      } catch (e: AuthException) {
        error = e.message?.takeIf { it.isNotBlank() } ?: genericError
        otpClearNonce++
      } catch (_: Exception) {
        error = genericError
        otpClearNonce++
      } finally {
        busy = false
      }
    }
  }

  fun sendOtp(forcePurpose: String? = null) {
    val resolvedPurpose = forcePurpose ?: purpose()
    runAuth {
      try {
        AuthRepository.sendEmail(email, resolvedPurpose)
        showRegisterDialog = false
        step = LoginStep.Verify
      } catch (e: AuthException) {
        if (resolvedPurpose == "login" && e.statusCode == 404 && e.hint == "register") {
          showRegisterDialog = true
          return@runAuth
        }
        throw e
      }
    }
  }

  val title = when (step) {
    LoginStep.Email -> stringResource(R.string.login_sheet_title)
    LoginStep.Verify -> stringResource(R.string.login_otp_sent)
    LoginStep.Totp -> stringResource(R.string.login_totp)
  }

  if (showRegisterDialog) {
    RegisterAccountDialog(
      email = email.trim(),
      busy = busy,
      onCreate = {
        registering = true
        sendOtp("register")
      },
      onFixEmail = {
        showRegisterDialog = false
        registering = false
      },
      onDismiss = {
        if (!busy) {
          showRegisterDialog = false
          registering = false
        }
      },
    )
  }

  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
          busy = busy,
          onEmailChange = {
            email = it
            error = null
            registering = false
            showRegisterDialog = false
          },
          onSubmit = {
            if (!email.isValidEmail()) {
              error = invalidEmail
              return@EmailStep
            }
            if (ReviewAuth.matches(email)) {
              runAuth {
                AuthRepository.signInLocal(context)
                onSuccess()
              }
              return@EmailStep
            }
            sendOtp()
          },
        )
        LoginStep.Verify, LoginStep.Totp -> OtpStep(
          stepKey = s,
          error = error,
          busy = busy,
          clearNonce = otpClearNonce,
          onCodeChange = { error = null },
          onComplete = { code ->
            runAuth {
              when (s) {
                LoginStep.Verify -> when (val result = AuthRepository.confirmEmail(context, email, code)) {
                  is EmailConfirmResult.NeedsTotp -> {
                    challengeToken = result.challengeToken
                    step = LoginStep.Totp
                  }
                  is EmailConfirmResult.Ready -> onSuccess()
                }
                LoginStep.Totp -> {
                  val token = challengeToken ?: throw AuthException(invalidCode)
                  AuthRepository.verifyTotp(context, token, code)
                  onSuccess()
                }
                else -> Unit
              }
            }
          },
          onResend = if (s == LoginStep.Verify) ({
            runAuth { AuthRepository.sendEmail(email, purpose()) }
          }) else null,
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
      IconLinkChip(
        R.drawable.ic_brand_telegram,
        onClick = { openAuthUrl(context, AuthApi.telegramOAuthStartUrl) },
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterAccountDialog(
  email: String,
  busy: Boolean,
  onCreate: () -> Unit,
  onFixEmail: () -> Unit,
  onDismiss: () -> Unit,
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 28.dp),
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          stringResource(R.string.login_create_account_title),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          stringResource(R.string.login_create_account_message, email),
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(
          onClick = onCreate,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
          enabled = !busy,
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(
            stringResource(R.string.login_create_account_confirm),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
          )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
          onClick = onFixEmail,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
          enabled = !busy,
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(
            stringResource(R.string.login_create_account_fix_email),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun EmailStep(
  email: String,
  error: String?,
  busy: Boolean,
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
      enabled = !busy,
      isError = error != null,
      supportingText = {
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
    Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
      Text(stringResource(R.string.login_continue))
    }
  }
}

@Composable
private fun OtpStep(
  stepKey: LoginStep,
  error: String?,
  busy: Boolean,
  clearNonce: Int,
  onCodeChange: (String) -> Unit,
  onComplete: (String) -> Unit,
  onResend: (() -> Unit)? = null,
) {
  Column {
    key(stepKey) {
      OtpInput(
        onComplete = { if (!busy) onComplete(it) },
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
      TextButton(onClick = onResend, modifier = Modifier.align(Alignment.CenterHorizontally), enabled = !busy) {
        Text(stringResource(R.string.login_resend))
      }
    }
  }
}
