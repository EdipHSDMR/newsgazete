package com.example.ui.screens

import android.app.TimePickerDialog
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
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Topic
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
) {
    val prefs by repository.preferences.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showQuietHoursDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profil_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header Profile Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CizgiColor.BorderLight)),
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
                            .size(52.dp)
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
                            text = "Misafir Kullanıcı",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Tercihleriniz bu cihazda güvenle saklanır",
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
                    title = "Takip Edilen Konular",
                    subtitle = "${prefs.followedTopics.size} konu seçili",
                    onClick = onNavigateTopics,
                )
                HorizontalDivider(color = CizgiColor.BorderLight.copy(alpha = 0.5f))
                SettingNavigationRow(
                    icon = Icons.Default.SportsSoccer,
                    title = "Takım ve Lig Takibi",
                    subtitle = "${prefs.followedTeams.size} takım takip ediliyor",
                    onClick = onNavigateSports,
                )
                HorizontalDivider(color = CizgiColor.BorderLight.copy(alpha = 0.5f))
                SettingNavigationRow(
                    icon = Icons.Default.LocationOn,
                    title = "Hava Durumu Şehri",
                    subtitle = prefs.selectedCity,
                    onClick = onNavigateWeather,
                )
            }
        }

        // Section: Görünüm & Okuma
        item {
            SettingSectionHeader("GÖRÜNÜM VE OKUMA DİLİ")
        }

        item {
            SettingCard {
                SettingNavigationRow(
                    icon = Icons.Default.Brightness4,
                    title = "Tema Seçimi",
                    subtitle = when (prefs.themeMode) {
                        "dark" -> "Koyu Tema"
                        "system" -> "Sistem Varsayılanı"
                        else -> "Açık Tema (Varsayılan)"
                    },
                    onClick = { showThemeDialog = true },
                )
                HorizontalDivider(color = CizgiColor.BorderLight.copy(alpha = 0.5f))
                SettingNavigationRow(
                    icon = Icons.Default.Topic,
                    title = "Okuma Yazı Boyutu",
                    subtitle = if (prefs.fontSize == "large") "Büyük (18sp)" else "Normal (16sp)",
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
                HorizontalDivider(color = CizgiColor.BorderLight.copy(alpha = 0.5f))
                SettingSwitchRow(
                    title = "Son Dakika Bildirimleri",
                    subtitle = "Önemli editoryal gelişmeler anlık iletilir",
                    checked = prefs.breakingAlerts,
                    onCheckedChange = repository::setBreakingAlerts,
                )
                HorizontalDivider(color = CizgiColor.BorderLight.copy(alpha = 0.5f))
                SettingSwitchRow(
                    title = "Maç Başlangıç ve Gol Alarmları",
                    subtitle = "Takip ettiğin takımların maçlarında",
                    checked = prefs.matchAlerts,
                    onCheckedChange = repository::setMatchAlerts,
                )
                HorizontalDivider(color = CizgiColor.BorderLight.copy(alpha = 0.5f))
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
                HorizontalDivider(color = CizgiColor.BorderLight.copy(alpha = 0.5f))
                SettingNavigationRow(
                    icon = Icons.Default.Info,
                    title = "Çizgi Hakkında & Editoryal Standartlar",
                    subtitle = "Sürüm 1.0.0 · Bağımsız habercilik",
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
                    listOf("normal" to "Normal (16sp)", "large" to "Büyük (18sp)").forEach { (key, label) ->
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
            title = { Text("ÇİZGİ / Editoryal Standartlar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Çizgi, haber okuruna gürültüden uzak, güvenilir ve bağlamı anlaşılır bir gündem sunmak için geliştirilmiştir.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "• Kaynak şeffaflığı: Haberin kaynağı ve varsa diğer kaynaklarla karşılaştırması gösterilir; kaynak sayısı habere göre değişir.\n• Reklamsız haber akışı.\n• Gizlilik: Konum ve ilgi alanları cihazında tutulur.",
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
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CizgiColor.BorderLight)),
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )

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

