package com.example.ui.screens

import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.ui.theme.CizgiColor
import com.example.util.CizgiNotificationHelper
import java.time.LocalTime
import java.util.Locale

@Composable
fun ProfilScreen(
    repository: CizgiRepository,
    onNavigateTopics: () -> Unit,
    onNavigateSports: () -> Unit,
    onNavigateWeather: () -> Unit,
    onNavigateNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val prefs by repository.preferences.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showQuietHoursDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var pendingNotificationToggle by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingNotificationToggle
        if (granted && pending == "breaking") repository.setBreakingAlerts(true)
        if (granted && pending == "matches") repository.setMatchAlerts(true)
        pendingNotificationToggle = null
    }

    fun setNotificationToggle(name: String, enabled: Boolean) {
        if (!enabled) {
            if (name == "breaking") repository.setBreakingAlerts(false) else repository.setMatchAlerts(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !CizgiNotificationHelper.hasNotificationPermission(context)
        ) {
            pendingNotificationToggle = name
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else if (name == "breaking") {
            repository.setBreakingAlerts(true)
        } else {
            repository.setMatchAlerts(true)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profil_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Ç",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Senin Çizgin",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "${prefs.followedTopics.size} konu · ${prefs.followedTeams.size} takım takip ediliyor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Section: Takipler ve Kişiselleştirme
        item {
            SettingSectionHeader("TAKİP VE KİŞİSELLEŞTİRME")
        }

        item {
            SettingCard {
                SettingNavigationRow(
                    icon = Icons.Default.Topic,
                    title = "İlgi alanları",
                    subtitle = "${prefs.followedTopics.size} konu seçili",
                    onClick = onNavigateTopics,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingNavigationRow(
                    icon = Icons.Default.SportsSoccer,
                    title = "Takım ve lig takibi",
                    subtitle = "${prefs.followedTeams.size} takım takip ediliyor",
                    onClick = onNavigateSports,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingNavigationRow(
                    icon = Icons.Default.LocationOn,
                    title = "Hava konumu",
                    subtitle = prefs.selectedCity,
                    onClick = onNavigateWeather,
                )
            }
        }

        // Section: Görünüm & Okuma
        item {
            SettingSectionHeader("GÖRÜNÜM VE OKUMA")
        }

        item {
            SettingCard {
                SettingNavigationRow(
                    icon = Icons.Default.Brightness4,
                    title = "Görünüm",
                    subtitle = when (prefs.themeMode) {
                        "dark" -> "Koyu Tema"
                        "system" -> "Sistem Varsayılanı"
                        else -> "Açık Tema (Varsayılan)"
                    },
                    onClick = { showThemeDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingNavigationRow(
                    icon = Icons.Default.TextFields,
                    title = "Yazı boyutu",
                    subtitle = if (prefs.fontSize == "large") "Büyük" else "Standart",
                    onClick = { showFontSizeDialog = true },
                )
            }
        }

        // Section: Bildirimler ve Sessiz Saatler
        item {
            SettingSectionHeader("BİLDİRİMLER VE SESSİZ SAATLER")
        }

        item {
            SettingCard {
                SettingNavigationRow(
                    icon = Icons.Default.Notifications,
                    title = "Bildirim Merkezi",
                    subtitle = "Geçmiş bildirimler ve arşiv",
                    onClick = onNavigateNotifications,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "Son dakika bildirimleri",
                    subtitle = "Önemli gelişmelerden haberdar ol",
                    checked = prefs.breakingAlerts,
                    onCheckedChange = { setNotificationToggle("breaking", it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "Maç ve gol bildirimleri",
                    subtitle = "Takip ettiğin takımların maçlarında",
                    checked = prefs.matchAlerts,
                    onCheckedChange = { setNotificationToggle("matches", it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(
                    title = "Sessiz Saatler (${prefs.quietHoursStart} - ${prefs.quietHoursEnd})",
                    subtitle = "Başlığa dokunup saatleri düzenle; bu aralıkta telefon bildirimleri susar",
                    checked = prefs.quietHoursEnabled,
                    onCheckedChange = { repository.setQuietHours(it) },
                    onTitleClick = { showQuietHoursDialog = true },
                )
            }
        }

        // Section: Gizlilik ve Hakkında
        item {
            SettingSectionHeader("GİZLİLİK VE VERİ")
        }

        item {
            SettingCard {
                SettingNavigationRow(
                    icon = Icons.Default.Delete,
                    title = "Verileri ve Önbelleği Temizle",
                    subtitle = "Kayıtlı haberleri ve cihazdaki haber önbelleğini temizle",
                    onClick = { showClearDataDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingNavigationRow(
                    icon = Icons.Default.Info,
                    title = "Çizgi hakkında",
                    subtitle = "Sürüm ${com.example.BuildConfig.VERSION_NAME}",
                    onClick = { showAboutDialog = true },
                )
            }
        }
    }

    // Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Tema Seçimi") },
            text = {
                Column {
                    listOf("light" to "Açık Tema (Varsayılan)", "dark" to "Koyu Tema", "system" to "Sistem Teması").forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.setThemeMode(key)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = prefs.themeMode == key,
                                onClick = {
                                    repository.setThemeMode(key)
                                    showThemeDialog = false
                                },
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Kapat") }
            },
        )
    }

    // Font Size Dialog
    if (showFontSizeDialog) {
        AlertDialog(
            onDismissRequest = { showFontSizeDialog = false },
            title = { Text("Yazı Boyutu") },
            text = {
                Column {
                    listOf("normal" to "Standart", "large" to "Büyük").forEach { (key, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.setFontSize(key)
                                    showFontSizeDialog = false
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(
                                selected = prefs.fontSize == key,
                                onClick = {
                                    repository.setFontSize(key)
                                    showFontSizeDialog = false
                                },
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFontSizeDialog = false }) { Text("Kapat") }
            },
        )
    }

    if (showQuietHoursDialog) {
        AlertDialog(
            onDismissRequest = { showQuietHoursDialog = false },
            title = { Text("Sessiz Saatleri Ayarla") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bu saatlerde telefon bildirimi gönderilmez; uygulama içi bildirim geçmişi korunur.")
                    TextButton(
                        onClick = {
                            val start = runCatching { LocalTime.parse(prefs.quietHoursStart) }.getOrDefault(LocalTime.of(23, 0))
                            TimePickerDialog(context, { _, hour, minute ->
                                repository.setQuietHours(
                                    prefs.quietHoursEnabled,
                                    String.format(Locale.ROOT, "%02d:%02d", hour, minute),
                                    prefs.quietHoursEnd,
                                )
                            }, start.hour, start.minute, true).show()
                        },
                    ) { Text("Başlangıç: ${prefs.quietHoursStart}") }
                    TextButton(
                        onClick = {
                            val end = runCatching { LocalTime.parse(prefs.quietHoursEnd) }.getOrDefault(LocalTime.of(7, 0))
                            TimePickerDialog(context, { _, hour, minute ->
                                repository.setQuietHours(
                                    prefs.quietHoursEnabled,
                                    prefs.quietHoursStart,
                                    String.format(Locale.ROOT, "%02d:%02d", hour, minute),
                                )
                            }, end.hour, end.minute, true).show()
                        },
                    ) { Text("Bitiş: ${prefs.quietHoursEnd}") }
                }
            },
            confirmButton = { TextButton(onClick = { showQuietHoursDialog = false }) { Text("Tamam") } },
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Verileri Temizle") },
            text = { Text("Kayıtlı haberleriniz, arama geçmişiniz ve yerel önbellek temizlenecektir. Devam etmek istiyor musunuz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.clearSavedStories()
                        repository.clearSearchHistory()
                        repository.clearLocalNewsData()
                        showClearDataDialog = false
                    },
                ) {
                    Text("Temizle", color = CizgiColor.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Vazgeç") }
            },
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Çizgi Hakkında") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Haber, spor ve piyasa gelişmelerini tek yerde takip edebilirsin.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Haberin yayıncısı haber başlığının altında görünür. Şehir ve takip tercihlerin bu cihazda saklanır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Anladım") }
            },
        )
    }


}

@Composable
private fun SettingSectionHeader(title: String) {
    Text(
        text = title.lowercase(Locale.forLanguageTag("tr-TR")).replaceFirstChar { it.uppercaseChar() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTitleClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp)
                .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
