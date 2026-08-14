package com.demar.vpn.ui.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.demar.vpn.R
import com.demar.vpn.data.account.AccountStore
import com.demar.vpn.data.account.VpnAccount
import com.demar.vpn.data.account.VpnAccountApi
import com.demar.vpn.data.account.VpnTraffic
import com.demar.vpn.data.auth.AuthException
import com.demar.vpn.data.auth.AuthRepository
import com.demar.vpn.data.auth.AuthSession
import com.demar.vpn.ui.components.ActiveTabContent
import com.demar.vpn.ui.components.DemarLinearProgress
import com.demar.vpn.ui.components.DemarSnackbar
import com.demar.vpn.ui.components.SettingsActionButton
import com.demar.vpn.ui.components.SettingsGroup
import com.demar.vpn.ui.components.StaticSheetDragHandle
import com.demar.vpn.ui.components.mainTabPadding
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val FamilySlots = 4
private const val FamilyOccupiedStub = 3

@Composable
fun AccountScreen(
  active: Boolean,
  session: AuthSession?,
  signingOut: Boolean,
  snackbarHost: SnackbarHostState,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ActiveTabContent(active, modifier) {
    val user = session ?: return@ActiveTabContent
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val account by AccountStore.account.collectAsStateWithLifecycle()
    val traffic by AccountStore.traffic.collectAsStateWithLifecycle()
    val loading by AccountStore.loading.collectAsStateWithLifecycle()
    var notifyBusy by remember { mutableStateOf(false) }
    var showNotify by rememberSaveable { mutableStateOf(false) }
    var showTraffic by rememberSaveable { mutableStateOf(false) }
    var showSignOutConfirm by rememberSaveable { mutableStateOf(false) }
    val notifyFailed = stringResource(R.string.account_notify_failed)
    val idCopied = stringResource(R.string.account_id_copied)
    val otherLabel = stringResource(R.string.account_traffic_other)
    val soon: () -> Unit = { scope.launch { DemarSnackbar.showSoon(snackbarHost, context) } }

    val rawPlan = account?.vpnSubscription ?: user.vpnSubscription
    val isFree = rawPlan.equals("free", ignoreCase = true)
    val plan = rawPlan?.replaceFirstChar { it.uppercase() }
      ?: stringResource(R.string.account_vpn_unknown)
    val expires = (account?.vpnExpiresAt ?: user.vpnExpiresAt)?.let { iso ->
      runCatching {
        val date = Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
        val formatted = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
        val days = ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), date).toInt()
        val relative = when {
          days > 0 -> pluralStringResource(R.plurals.account_in_days, days, days)
          else -> stringResource(R.string.account_expires_today)
        }
        stringResource(R.string.account_expires_with_days, formatted, relative)
      }.getOrNull()
    }

    fun setNotify(news: Boolean? = null, important: Boolean? = null) {
      val cur = account ?: return
      if (notifyBusy) return
      val next = cur.copy(
        notifyNews = news ?: cur.notifyNews,
        notifyImportant = important ?: cur.notifyImportant,
      )
      if (user.isLocal) {
        AccountStore.patchAccount { next }
        return
      }
      AccountStore.patchAccount { next }
      notifyBusy = true
      scope.launch {
        try {
          val updated = VpnAccountApi.updateNotify(
            user.userId,
            user.loginAt,
            notifyNews = news,
            notifyImportant = important,
          )
          AccountStore.patchAccount {
            it.copy(
              notifyNews = updated.notifyNews,
              notifyImportant = updated.notifyImportant,
            )
          }
        } catch (e: AuthException) {
          AccountStore.patchAccount { cur }
          if (e.isSessionInvalid) AuthRepository.clearLocal(context)
          else DemarSnackbar.showError(snackbarHost, notifyFailed)
        } catch (e: Exception) {
          AccountStore.patchAccount { cur }
          DemarSnackbar.showError(snackbarHost, notifyFailed)
        } finally {
          notifyBusy = false
        }
      }
    }

    val trafficProgress = traffic?.let { t ->
      if (t.limitBytes <= 0L) 0f
      else (t.total.toFloat() / t.limitBytes).coerceIn(0f, 1f)
    }
    val trafficSub = when {
      traffic == null && loading -> stringResource(R.string.loading)
      traffic == null -> stringResource(R.string.account_traffic_unavailable)
      else -> stringResource(
        R.string.account_traffic_preview,
        VpnAccountApi.formatBytes(traffic!!.total),
        VpnAccountApi.formatBytes(traffic!!.limitBytes),
        ((trafficProgress ?: 0f) * 100).toInt(),
      )
    }

    Column(
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .mainTabPadding(bottomExtra = 20.dp),
    ) {
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
          Icons.Filled.AccountCircle,
          contentDescription = null,
          modifier = Modifier.size(64.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(user.username, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
          stringResource(R.string.account_id, user.userId),
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
              clipboard.setText(AnnotatedString(user.userId))
              scope.launch { DemarSnackbar.showInfo(snackbarHost, idCopied) }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
      Spacer(Modifier.height(32.dp))

      SettingsGroup {
        SubscriptionCard(
          plan = plan,
          expires = if (isFree) null else expires,
          isFree = isFree,
          loading = loading && account == null,
          onPurchase = soon,
        )
        val progress = trafficProgress
        val trafficFull = progress != null && progress >= 1f
        val cs = MaterialTheme.colorScheme
        SettingsActionButton(
          Icons.Filled.DataUsage,
          stringResource(R.string.account_traffic_title),
          trafficSub,
          onClick = { showTraffic = true },
          danger = trafficFull,
          extra = if (progress == null) null else {
            {
              Spacer(Modifier.height(8.dp))
              DemarLinearProgress(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (trafficFull) cs.error else cs.primary,
                trackColor = if (trafficFull) cs.error.copy(alpha = 0.22f) else cs.surfaceVariant,
              )
            }
          },
        )
        FamilySubscriptionCard(onClick = soon)
        SettingsActionButton(
          Icons.Filled.Notifications,
          stringResource(R.string.account_notify_title),
          stringResource(R.string.account_notify_sub),
          onClick = { showNotify = true },
        )
        SettingsActionButton(
          Icons.AutoMirrored.Filled.Logout,
          stringResource(if (signingOut) R.string.loading else R.string.sign_out),
          stringResource(R.string.sign_out_sub),
          onClick = if (signingOut) null else ({ showSignOutConfirm = true }),
        )
      }
    }

    if (showNotify) {
      NotifySheet(
        account = account,
        loading = loading,
        notifyBusy = notifyBusy,
        onDismiss = { showNotify = false },
        onSetNotify = ::setNotify,
      )
    }
    if (showTraffic) {
      TrafficSheet(
        traffic = traffic,
        loading = loading,
        otherLabel = otherLabel,
        onDismiss = { showTraffic = false },
      )
    }
    if (showSignOutConfirm) {
      SignOutConfirmDialog(
        onDismiss = { showSignOutConfirm = false },
        onConfirm = {
          showSignOutConfirm = false
          onSignOut()
        },
      )
    }
  }
}

@Composable
private fun SubscriptionCard(
  plan: String,
  expires: String?,
  isFree: Boolean,
  loading: Boolean,
  onPurchase: () -> Unit,
) {
  val cs = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(12.dp)
  Column(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, cs.outline.copy(alpha = 0.2f), shape)
      .padding(16.dp),
  ) {
    if (loading) {
      CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
    } else {
      SubscriptionMetaRow(Icons.Filled.WorkspacePremium, stringResource(R.string.account_plan), plan)
      if (expires != null) {
        Spacer(Modifier.height(12.dp))
        SubscriptionMetaRow(Icons.Filled.CalendarMonth, stringResource(R.string.account_expires), expires)
      }
      Spacer(Modifier.height(16.dp))
      Button(
        onClick = onPurchase,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        shape = RoundedCornerShape(12.dp),
      ) {
        Text(
          stringResource(if (isFree) R.string.account_buy else R.string.account_extend),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }
  }
}

@Composable
private fun SubscriptionMetaRow(icon: ImageVector, label: String, value: String) {
  val cs = MaterialTheme.colorScheme
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(cs.primary.copy(alpha = 0.1f))
        .padding(8.dp),
    ) {
      Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(24.dp))
    }
    Spacer(Modifier.width(16.dp))
    Column {
      Text(label, style = MaterialTheme.typography.titleMedium)
      Text(value, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
  }
}

@Composable
private fun FamilySubscriptionCard(onClick: () -> Unit) {
  val cs = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(12.dp)
  Column(
    Modifier
      .fillMaxWidth()
      .clip(shape)
      .border(1.dp, cs.outline.copy(alpha = 0.2f), shape)
      .clickable(onClick = onClick)
      .padding(16.dp),
  ) {
    Text(stringResource(R.string.account_family_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
      stringResource(R.string.account_family_sub),
      style = MaterialTheme.typography.bodySmall,
      color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(cs.surfaceContainerHighest)
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(R.string.account_family_free),
          style = MaterialTheme.typography.bodySmall,
          color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          repeat(FamilyOccupiedStub) { FamilySlotAvatar(occupied = true) }
          repeat(FamilySlots - FamilyOccupiedStub) { FamilySlotAvatar(occupied = false) }
        }
      }
      Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = cs.primary.copy(alpha = 0.15f),
        contentColor = cs.primary,
      ) {
        Text(
          stringResource(R.string.account_family_change),
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun FamilySlotAvatar(occupied: Boolean) {
  val cs = MaterialTheme.colorScheme
  if (occupied) {
    Box(
      Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(cs.surfaceVariant),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Person,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        tint = cs.onSurfaceVariant.copy(alpha = 0.55f),
      )
    }
  } else {
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
    val strokeColor = cs.outline.copy(alpha = 0.55f)
    Canvas(Modifier.size(36.dp)) {
      drawCircle(
        color = strokeColor,
        radius = size.minDimension / 2f - 1.dp.toPx(),
        center = Offset(size.width / 2f, size.height / 2f),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dash),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignOutConfirmDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
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
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          stringResource(R.string.sign_out_confirm_title),
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          stringResource(R.string.sign_out_confirm_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
          onClick = onConfirm,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(stringResource(R.string.sign_out), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
          shape = RoundedCornerShape(16.dp),
        ) {
          Text(stringResource(R.string.cancel), style = MaterialTheme.typography.titleMedium)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotifySheet(
  account: VpnAccount?,
  loading: Boolean,
  notifyBusy: Boolean,
  onDismiss: () -> Unit,
  onSetNotify: (news: Boolean?, important: Boolean?) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
    Column(
      Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      StaticSheetDragHandle()
      when {
        account == null && loading -> Text(
          stringResource(R.string.loading),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        account != null -> {
          NotifyRow(
            stringResource(R.string.account_notify_news),
            account.notifyNews,
            !notifyBusy,
          ) { onSetNotify(it, null) }
          Spacer(Modifier.height(8.dp))
          NotifyRow(
            stringResource(R.string.account_notify_important),
            account.notifyImportant,
            !notifyBusy,
          ) { onSetNotify(null, it) }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrafficSheet(
  traffic: VpnTraffic?,
  loading: Boolean,
  otherLabel: String,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = null) {
    Column(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      StaticSheetDragHandle()
      when {
        traffic == null && loading -> Text(
          stringResource(R.string.loading),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        traffic == null -> Text(
          stringResource(R.string.account_traffic_unavailable),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> TrafficPieChart(
          servers = traffic.servers,
          otherLabel = otherLabel,
          centerPrimary = VpnAccountApi.formatBytes(traffic.total),
        )
      }
    }
  }
}

@Composable
private fun NotifyRow(
  label: String,
  checked: Boolean,
  enabled: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
  }
}
