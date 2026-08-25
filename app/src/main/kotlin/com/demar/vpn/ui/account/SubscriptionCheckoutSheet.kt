package com.demar.vpn.ui.account

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.demar.vpn.R
import com.demar.vpn.data.account.PayApi
import com.demar.vpn.data.account.PayCatalog
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.auth.AuthSession
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.StaticSheetDragHandle
import com.demar.vpn.ui.components.openAuthUrl
import kotlinx.coroutines.launch

private val Periods = listOf("week", "month", "quarter", "year")
private val PeriodBadges = mapOf("quarter" to "−20%", "year" to "−33%")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionCheckoutSheet(
  intent: CheckoutIntent,
  session: AuthSession,
  currentPlan: String?,
  snackbarHost: SnackbarHostState,
  onCompare: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val cs = MaterialTheme.colorScheme

  var catalog by remember { mutableStateOf<PayCatalog?>(null) }
  var tier by remember { mutableStateOf(intent.defaultTier(currentPlan)) }
  var period by remember { mutableStateOf("month") }
  var currency by remember { mutableStateOf("RUB") }
  var paying by remember { mutableStateOf(false) }
  var loadingPlans by remember { mutableStateOf(true) }
  var plansError by remember { mutableStateOf<String?>(null) }
  var promoCode by remember { mutableStateOf("") }
  var appliedCode by remember { mutableStateOf<String?>(null) }
  var discountPercent by remember { mutableIntStateOf(0) }
  var showPromoInput by remember { mutableStateOf(false) }
  var showPromoResult by remember { mutableStateOf(false) }
  var promoResultMessage by remember { mutableStateOf("") }

  val loadFailed = stringResource(R.string.pay_plans_load_failed)
  val retrySoon = stringResource(R.string.pay_retry_in_3s)
  val promoApplied = stringResource(R.string.promo_applied)
  val promoNotFound = stringResource(R.string.promo_not_found)
  val promoAlreadyUsed = stringResource(R.string.promo_already_used)
  val promoBlocked = stringResource(R.string.promo_blocked_by_other)
  val promoExpired = stringResource(R.string.promo_expired)

  LaunchedEffect(intent) {
    tier = intent.defaultTier(currentPlan)
    if (catalog != null) {
      loadingPlans = false
      return@LaunchedEffect
    }
    loadingPlans = true
    plansError = null
    try {
      catalog = PayApi.fetchPlans()
    } catch (_: Exception) {
      plansError = loadFailed
      DemarSnackbar.showError(snackbarHost, loadFailed)
    } finally {
      loadingPlans = false
    }
  }

  fun promoMessage(result: String, fallback: String): String = when (result) {
    "applied" -> promoApplied
    "not_found" -> promoNotFound
    "already_used" -> promoAlreadyUsed
    "blocked_by_other" -> promoBlocked
    "expired" -> promoExpired
    else -> fallback.ifBlank { promoNotFound }
  }

  fun applyPromo() {
    val code = promoCode.trim()
    promoCode = ""
    if (code.isEmpty()) return
    scope.launch {
      try {
        val result = VpnAccountApi.applyPromo(session.userId, session.loginAt, code)
        promoResultMessage = promoMessage(result.result, result.message)
        if (result.isApplied) {
          appliedCode = code.uppercase()
          discountPercent = result.discountPercent ?: 0
        }
      } catch (e: AuthException) {
        promoResultMessage = if (e.statusCode == 429) retrySoon else (e.message ?: promoNotFound)
      } catch (e: Exception) {
        promoResultMessage = e.message ?: promoNotFound
      }
      showPromoResult = true
    }
  }

  fun pay() {
    if (paying) return
    paying = true
    scope.launch {
      try {
        val checkout = PayApi.create(
          userId = session.userId,
          loginAt = session.loginAt,
          tier = tier,
          period = period,
          currency = currency,
          code = appliedCode,
        )
        onDismiss()
        openAuthUrl(context, checkout.payUrl)
      } catch (e: AuthException) {
        when {
          e.isSessionInvalid -> AuthRepository.clearLocal(context)
          e.statusCode == 429 -> DemarSnackbar.showInfo(snackbarHost, retrySoon)
          else -> DemarSnackbar.showError(snackbarHost, e.message ?: loadFailed)
        }
      } catch (e: Exception) {
        DemarSnackbar.showError(snackbarHost, e.message ?: loadFailed)
      } finally {
        paying = false
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    dragHandle = { StaticSheetDragHandle() },
  ) {
    Column(
      Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp)
        .padding(bottom = 24.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.account_subscription),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.weight(1f),
        )
        CurrencyCircles(currency) { currency = it }
      }
      Spacer(Modifier.height(14.dp))
      TierSegmentBar(tier) { tier = it }
      Spacer(Modifier.height(14.dp))
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Periods.forEach { p ->
          PeriodRow(
            periodId = p,
            selected = period == p,
            priceLabel = catalog?.price(tier, p, currency)?.let { PayApi.formatPrice(it, currency) },
            onClick = { period = p },
          )
        }
      }
      Spacer(Modifier.height(14.dp))
      val major = catalog?.price(tier, period, currency)
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        when {
          major != null -> {
            Text(
              if (discountPercent > 0) PayApi.discountedDisplay(major, discountPercent, currency)
              else PayApi.formatPrice(major, currency),
              modifier = Modifier.alignByBaseline(),
              style = MaterialTheme.typography.headlineMedium,
              color = cs.onSurface,
            )
            if (discountPercent > 0) {
              Text(
                PayApi.formatPrice(major, currency),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
              )
            }
          }
          loadingPlans -> Text(
            stringResource(R.string.loading),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
          )
        }
      }
      Spacer(Modifier.height(14.dp))
      Row(
        Modifier
          .clip(MaterialTheme.shapes.extraSmall)
          .clickable { showPromoInput = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          Icons.Filled.LocalOffer,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = cs.primary,
        )
        Text(
          appliedCode?.let { "$it (−$discountPercent%)" }
            ?: stringResource(R.string.settings_promo_code),
          style = MaterialTheme.typography.labelLarge,
          color = cs.primary,
        )
      }
      plansError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error)
      }
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
          onClick = onCompare,
          modifier = Modifier
            .weight(1f)
            .height(48.dp),
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            stringResource(R.string.pay_compare_plans),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
          )
        }
        Button(
          onClick = ::pay,
          enabled = !paying && !loadingPlans && catalog != null,
          modifier = Modifier
            .weight(1f)
            .height(48.dp),
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            stringResource(if (paying) R.string.loading else R.string.pay_action),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }

  if (showPromoInput) {
    CheckoutPromoDialog(
      code = promoCode,
      onCodeChange = { raw ->
        promoCode = raw.uppercase().filter { it.isLetterOrDigit() }
      },
      onDismiss = {
        showPromoInput = false
        promoCode = ""
      },
      onApply = {
        showPromoInput = false
        applyPromo()
      },
    )
  }
  if (showPromoResult) {
    AlertDialog(
      onDismissRequest = { showPromoResult = false },
      confirmButton = {
        TextButton(onClick = { showPromoResult = false }) {
          Text(stringResource(R.string.ok))
        }
      },
      text = { Text(promoResultMessage) },
    )
  }
}

@Composable
private fun CurrencyCircles(selected: String, onSelect: (String) -> Unit) {
  val cs = MaterialTheme.colorScheme
  Row(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(cs.surfaceContainerHighest.copy(alpha = 0.85f))
      .padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    listOf("RUB" to "₽", "EUR" to "€").forEach { (id, label) ->
      val on = selected == id
      Box(
        Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(if (on) cs.onSurface else Color.Transparent)
          .clickable { onSelect(id) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.titleMedium,
          color = if (on) cs.onPrimary else cs.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TierSegmentBar(selected: String, onSelect: (String) -> Unit) {
  val cs = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.small
  val view = LocalView.current
  val options = remember { listOf("plus" to "Plus", "vip" to "VIP") }
  val optionIds = remember(options) { options.map { it.first } }
  val selectedState = rememberUpdatedState(selected)
  Row(
    Modifier
      .fillMaxWidth()
      .height(40.dp)
      .clip(shape)
      .background(cs.surfaceContainerHighest)
      .border(1.dp, cs.outline.copy(alpha = 0.2f), shape)
      .pointerInput(optionIds) {
        // iOS CheckoutSegmentBar: DragGesture(minimumDistance: 0)
        awaitEachGesture {
          val down = awaitFirstDown()
          var lastId: String? = null
          fun pick(x: Float) {
            val w = size.width.toFloat()
            if (w <= 0f || optionIds.isEmpty()) return
            val index = ((x / w) * optionIds.size).toInt().coerceIn(0, optionIds.lastIndex)
            val id = optionIds[index]
            if (id == lastId) return
            lastId = id
            if (id == selectedState.value) return
            onSelect(id)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
          }
          pick(down.position.x)
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            pick(change.position.x)
            change.consume()
          }
        }
      },
  ) {
    options.forEach { (id, title) ->
      val on = selected == id
      Box(
        Modifier
          .weight(1f)
          .fillMaxSize()
          .background(if (on) cs.primary.copy(alpha = 0.18f) else Color.Transparent),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          title,
          style = MaterialTheme.typography.titleSmall,
          color = if (on) cs.onSurface else cs.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun PeriodRow(
  periodId: String,
  selected: Boolean,
  priceLabel: String?,
  onClick: () -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val shape = MaterialTheme.shapes.medium
  Row(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .background(if (selected) cs.primary.copy(alpha = 0.14f) else cs.surfaceContainerHighest)
      .border(
        1.dp,
        if (selected) cs.primary.copy(alpha = 0.45f) else cs.outlineVariant.copy(alpha = 0.35f),
        shape,
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(periodLabel(periodId), style = MaterialTheme.typography.titleMedium)
    PeriodBadges[periodId]?.let { badge ->
      Spacer(Modifier.size(8.dp))
      Text(
        badge,
        style = MaterialTheme.typography.labelMedium,
        color = cs.primary,
        modifier = Modifier
          .clip(MaterialTheme.shapes.extraSmall)
          .background(cs.primary.copy(alpha = 0.15f))
          .padding(horizontal = 8.dp, vertical = 2.dp),
      )
    }
    Spacer(Modifier.weight(1f))
    if (priceLabel != null) {
      Text(
        priceLabel,
        style = MaterialTheme.typography.titleMedium,
        color = cs.onSurfaceVariant,
        textAlign = TextAlign.End,
      )
    }
  }
}

@Composable
private fun periodLabel(periodId: String): String = stringResource(
  when (periodId) {
    "week" -> R.string.pay_period_week
    "month" -> R.string.pay_period_month
    "quarter" -> R.string.pay_period_quarter
    "year" -> R.string.pay_period_year
    else -> R.string.pay_period_month
  },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutPromoDialog(
  code: String,
  onCodeChange: (String) -> Unit,
  onDismiss: () -> Unit,
  onApply: () -> Unit,
) {
  BasicAlertDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 28.dp),
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 6.dp,
    ) {
      Column(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          stringResource(R.string.promo_enter_code),
          style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          stringResource(R.string.pay_promo_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
          value = code,
          onValueChange = onCodeChange,
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("DEMAR10") },
          singleLine = true,
          shape = MaterialTheme.shapes.small,
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            keyboardType = KeyboardType.Ascii,
          ),
        )
        Spacer(Modifier.height(28.dp))
        Button(
          onClick = onApply,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            stringResource(R.string.pay_promo_apply),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
          )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
          shape = MaterialTheme.shapes.small,
        ) {
          Text(
            stringResource(R.string.cancel),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }
}
