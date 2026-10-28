package com.example.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.model.DataSourceDiagnostic
import com.example.ui.theme.CizgiColor
import com.example.util.CizgiNotificationHelper
import com.example.util.ReaderSettings
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PRIVACY_POLICY_URL = "https://github.com/EdipHSDMR/newsgazete/blob/main/docs/privacy-policy.md"

@Composable
fun ProfilScreen(
    repository: CizgiRepository,
    onNavigateTopics: () -> Unit,
    onNavigateSports: () -> Unit,
    onNavigateWeather: () -> Unit,
    onNavigateNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onNavigatePersonalize: () -> Unit = {},
) {
    val prefs by repository.preferences.collectAsState()
    val diagnostics by repository.sourceDiagnostics.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showQuietHoursDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingNotificationToggle by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingNotificationToggle
        if (granted) when (pending) {
            "breaking" -> repository.setBreakingAlerts(true)
            "matches" -> repository.setMatchAlerts(true)
            "topics" -> repository.setTopicAlerts(true)
            "digest" -> repository.setDailyDigest(true)
        }
        pendingNotificationToggle = null
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val success = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(repository.exportBackupJson().toByteArray(Charsets.UTF_8))
                } != null
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (success) "Yedek kaydedildi." else "Yedek kaydedilemedi.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            val imported = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    repository.importBackupJson(reader.readText())
                } == true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, if (imported) "Yedek geri yüklendi." else "Dosya tanınmadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun applyNotificationToggle(name: String, enabled: Boolean) {
        when (name) {
            "breaking" -> repository.setBreakingAlerts(enabled)
            "matches" -> repository.setMatchAlerts(enabled)
            "topics" -> repository.setTopicAlerts(enabled)
            "digest" -> repository.setDailyDigest(enabled)
        }
    }
    fun setNotificationToggle(name: String, enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= 33 && !CizgiNotificationHelper.hasNotificationPermission(context)) {
            pendingNotificationToggle = name
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else applyNotificationToggle(name, enabled)
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
                SettingNavigationRow(icon = Icons.Default.AccessibilityNew, title = "Akışımı kişiselleştir", subtitle = "Konu ve takım seçimlerini birlikte düzenle", onClick = onNavigatePersonalize)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    subtitle = ReaderSettings.fontSizes[prefs.fontSize] ?: "Standart",
                    onClick = { showFontSizeDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(title = "Yüksek kontrast", subtitle = "Yazıları ve sınırları daha belirgin göster", checked = prefs.highContrast, onCheckedChange = repository::setHighContrast)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(title = "Hareketi azalt", subtitle = "Geçiş ve yükleme animasyonlarını sadeleştir", checked = prefs.reduceMotion, onCheckedChange = repository::setReduceMotion)
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
                if (prefs.matchAlerts) {
                    SettingSwitchRow(title = "Maç başlangıcı", subtitle = "Seçili maç ve takımlar için", checked = prefs.matchStartAlerts, onCheckedChange = repository::setMatchStartAlerts)
                    SettingSwitchRow(title = "Skor ve maç sonucu", subtitle = "Futbol ve basketbol karşılaşmaları için", checked = prefs.matchScoreAlerts, onCheckedChange = repository::setMatchScoreAlerts)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                SettingSwitchRow(title = "Takip ettiğim konular", subtitle = "Seçtiğin konu ve takımlarla ilgili yeni haberler", checked = prefs.topicAlerts, onCheckedChange = { setNotificationToggle("topics", it) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(title = "Günün kısa turu", subtitle = "Günde bir kez kaynaklardan derlenen gündem", checked = prefs.dailyDigest, onCheckedChange = { setNotificationToggle("digest", it) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingSwitchRow(title = "Hava uyarıları", subtitle = "Yüksek yağış, don ve sıcak hava uyarıları", checked = prefs.weatherAlerts, onCheckedChange = repository::setWeatherAlerts)
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

        item { SettingSectionHeader("VERİ KAYNAKLARI") }
        item {
            SettingCard {
                if (diagnostics.isEmpty()) {
                    Text("Kaynak durumu ilk yenilemeden sonra burada görünür.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    diagnostics.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DataSourceRow(item)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = repository::refreshAll, modifier = Modifier.fillMaxWidth()) { Text("Kaynakları yenile") }
            }
        }

        item { SettingSectionHeader("YEDEKLEME VE TAŞIMA") }
        item {
            SettingCard {
                SettingNavigationRow(
                    icon = Icons.Default.Security,
                    title = "Ayarları ve kayıtları dışa aktar",
                    subtitle = "Takipler, alarm seviyeleri ve kayıtlı haberler cihazda dosyalanır",
                    onClick = { exportLauncher.launch("cizgi-yedek.json") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingNavigationRow(
                    icon = Icons.Default.Info,
                    title = "Yedekten geri yükle",
                    subtitle = "Daha önce dışa aktardığın JSON dosyasını seç",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
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
                    icon = Icons.Default.Security,
                    title = "Gizlilik politikası",
                    subtitle = "Verilerin nasıl işlendiğini görüntüle",
                    onClick = { showPrivacyDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            title = { Text("Yazı boyutu") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Haberleri rahatça okuyabileceğin boyutu seç. Cihazının yazı boyutu ayarı da korunur.", style = MaterialTheme.typography.bodyMedium)
                    ReaderSettings.fontSizes.forEach { (key, label) ->
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
            text = { Text("Kayıtlı haberler, okuma ve arama geçmişi, bildirim kutusu ve yerel haber önbelleği temizlenecek. Devam etmek istiyor musunuz?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.clearSavedStories()
                        repository.clearSearchHistory()
                        repository.clearReadingHistory()
                        repository.clearNotifications()
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

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Gizlilik Politikası") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Çizgi kullanıcı hesabı, reklam SDK'sı veya GPS izni kullanmaz.")
                    Text("Takip tercihleri, kayıtlı haberler, okuma konumu ve alarm ayarları bu cihazda tutulur. İsteğe bağlı JSON yedeği yalnızca Android belge seçicisinde seçtiğiniz yere yazılır.")
                    Text("Hava durumu için seçtiğiniz il/ilçe adı, hava durumu sağlayıcılarına gönderilir. GPS konumu alınmaz. Haber, spor ve piyasa verileri de ilgili yayıncı ve veri sağlayıcılarından yüklenir; bu hizmetler standart ağ bilgilerini kendi politikalarına göre işleyebilir.")
                    Text("Uygulama kişisel tercihleri satmaz. Verileri ve Önbelleği Temizle seçeneği cihazdaki kayıtları siler. Dışa aktarılan yedek dosyalarını seçtiğiniz konumdan ayrıca silebilirsiniz.")
                    Text("Güncel web politikası için GitHub sayfasını açabilirsiniz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Anladım") }
            },
            dismissButton = {
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))) }
                }) { Text("Web sayfası") }
            },
        )
    }


}

@Composable
private fun DataSourceRow(item: DataSourceDiagnostic) {
    val healthy = item.lastSuccessEpochMs > 0L && item.consecutiveFailures == 0
    val recent = item.lastAttemptEpochMs > 0L && System.currentTimeMillis() - item.lastAttemptEpochMs < 2 * 60 * 60_000L
    val stateColor = when {
        healthy && recent -> MaterialTheme.colorScheme.primary
        item.consecutiveFailures > 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stateLabel = when {
        item.consecutiveFailures > 0 && item.lastSuccessEpochMs > 0L -> "Son denemede sorun"
        item.lastSuccessEpochMs == 0L -> "Henüz veri alınmadı"
        !recent -> "Eski veri"
        else -> "Çalışıyor"
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(stateColor))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.source, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = stateColor)
            }
            Text(item.message.ifBlank { "Son kontrol: ${diagnosticTime(item.lastAttemptEpochMs)}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.lastSuccessEpochMs > 0L) Text("Son başarılı bağlantı: ${diagnosticTime(item.lastSuccessEpochMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun diagnosticTime(timestamp: Long): String {
    if (timestamp <= 0L) return "—"
    val minutes = ((System.currentTimeMillis() - timestamp) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> "az önce"
        minutes < 60 -> "$minutes dk önce"
        minutes < 1_440 -> "${minutes / 60} sa önce"
        else -> java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.of("Europe/Istanbul"))
            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
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
