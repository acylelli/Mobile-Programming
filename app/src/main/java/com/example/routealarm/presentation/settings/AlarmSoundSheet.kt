package com.example.routealarm.presentation.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import com.example.routealarm.R
import com.example.routealarm.alarm.sound.AlarmSoundPlayer
import com.example.routealarm.core.designsystem.component.AppBottomSheet
import com.example.routealarm.core.designsystem.component.AppSwitch
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.RadioDot
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.UserPreferences

private const val VOLUME_STEP = 5

/**
 * 알람음 시트: 소리 종류(휴대폰 벨소리 고르기 포함), 볼륨, 점점 크게, 미리 듣기.
 * 소리 종류는 고르는 즉시 저장하고, 볼륨은 슬라이더에서 손을 뗄 때 저장한다.
 */
@Composable
internal fun AlarmSoundSheet(
    prefs: UserPreferences,
    onSound: (AlarmSound) -> Unit,
    onCustomSound: (uri: String, title: String?) -> Unit,
    onVolume: (Int) -> Unit,
    onGradual: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewPlayer = remember { AlarmSoundPlayer(context) }
    var previewing by remember { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(prefs.alarmVolumePercent) }
    DisposableEffect(previewPlayer) { onDispose { previewPlayer.stop() } }

    fun stopPreview() {
        previewPlayer.stop()
        previewing = false
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            ?: return@rememberLauncherForActivityResult
        val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        onCustomSound(uri.toString(), title)
    }

    AppBottomSheet(onDismiss = { stopPreview(); onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.settings_alarm_sound), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
            Spacer(Modifier.height(8.dp))

            Column(Modifier.selectableGroup()) {
                listOf(AlarmSound.DEFAULT_ALARM, AlarmSound.GENTLE, AlarmSound.CUSTOM, AlarmSound.SILENT).forEach { sound ->
                    val isCustom = sound == AlarmSound.CUSTOM
                    SoundOption(
                        label = stringResource(soundLabelRes(sound)),
                        detail = if (isCustom) prefs.customSoundTitle ?: stringResource(R.string.settings_sound_custom_pick) else null,
                        selected = prefs.alarmSound == sound,
                        onClick = {
                            stopPreview()
                            if (isCustom) {
                                picker.launch(ringtonePickerIntent(prefs.customSoundUri))
                            } else {
                                onSound(sound)
                            }
                        },
                    )
                }
            }

            val silent = prefs.alarmSound == AlarmSound.SILENT
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_volume), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.settings_volume_format, volume), style = MaterialTheme.typography.titleMedium, color = colors.accentText)
            }
            Slider(
                value = volume.toFloat(),
                onValueChange = {
                    volume = ((it / VOLUME_STEP).toInt() * VOLUME_STEP).coerceIn(UserPreferences.ALARM_VOLUME_RANGE)
                    previewPlayer.updateVolume(volume)
                },
                onValueChangeFinished = { onVolume(volume) },
                valueRange = UserPreferences.ALARM_VOLUME_RANGE.first.toFloat()..UserPreferences.ALARM_VOLUME_RANGE.last.toFloat(),
                steps = (UserPreferences.ALARM_VOLUME_RANGE.last - UserPreferences.ALARM_VOLUME_RANGE.first) / VOLUME_STEP - 1,
                enabled = !silent,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.dotted,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.testTag("alarm_volume_slider"),
            )
            Paragraph(stringResource(R.string.settings_volume_body), style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(Radius.tile).clickable(role = Role.Switch) { onGradual(!prefs.gradualVolume) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_gradual_volume), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                    Paragraph(stringResource(R.string.settings_gradual_volume_body), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                AppSwitch(checked = prefs.gradualVolume, onCheckedChange = onGradual)
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = stringResource(if (previewing) R.string.settings_preview_stop else R.string.settings_preview_play),
                    onClick = {
                        if (previewing) {
                            stopPreview()
                        } else {
                            previewing = previewPlayer.play(prefs.copy(alarmVolumePercent = volume), scope)
                        }
                    },
                    enabled = !silent,
                    modifier = Modifier.weight(1f).height(60.dp),
                )
                PrimaryButton(
                    stringResource(R.string.common_confirm),
                    onClick = {
                        stopPreview()
                        if (volume != prefs.alarmVolumePercent) onVolume(volume)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SoundOption(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun ringtonePickerIntent(existing: String?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing?.toUri())

internal fun soundLabelRes(sound: AlarmSound): Int = when (sound) {
    AlarmSound.DEFAULT_ALARM -> R.string.settings_sound_default
    AlarmSound.GENTLE -> R.string.settings_sound_gentle
    AlarmSound.CUSTOM -> R.string.settings_sound_custom
    AlarmSound.SILENT -> R.string.settings_sound_silent
}
