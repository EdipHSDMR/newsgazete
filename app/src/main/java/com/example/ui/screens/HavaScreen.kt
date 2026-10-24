package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.data.CizgiSeedData
import com.example.ui.theme.CizgiColor

@Composable
fun HavaScreen(
    repository: CizgiRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val weather by repository.weather.collectAsState()
    val isRefreshing by repository.isRefreshing.collectAsState()

    val prefs by repository.preferences.collectAsState()
    var showCityPicker by remember { mutableStateOf(false) }

    // Fetch live data immediately on screen load
    LaunchedEffect(prefs.selectedCity, prefs.selectedDistrict) {
        val q = if (!prefs.selectedDistrict.isNullOrBlank()) "${prefs.selectedDistrict}, ${prefs.selectedCity}" else prefs.selectedCity
        repository.fetchLiveWeather(q)
    }

    if (showCityPicker) {
        com.example.ui.components.CityDistrictPickerDialog(
            currentCity = prefs.selectedCity,
            currentDistrict = prefs.selectedDistrict,
            onDismiss = { showCityPicker = false },
            onSelect = { city, district ->
                repository.selectCityAndDistrict(city, district)
                showCityPicker = false
            }
        )
    }

    val displayName = if (!prefs.selectedDistrict.isNullOrBlank()) {
        "${prefs.selectedDistrict}, ${prefs.selectedCity}"
    } else {
        weather.cityName
    }



    fun getWeatherEmoji(condition: String): String {
        val lower = condition.lowercase()
        return when {
            lower.contains("kar") || lower.contains("blizzard") || lower.contains("buz") -> "❄️"
            lower.contains("fırtına") || lower.contains("şimşek") || lower.contains("gök gürültü") -> "⛈️"
            lower.contains("sağanak") || lower.contains("yağmur") || lower.contains("çise") -> "🌧️"
            lower.contains("sis") || lower.contains("pus") || lower.contains("duman") -> "🌫️"
            lower.contains("parçalı") || lower.contains("az bulutlu") -> "⛅"
            lower.contains("bulut") || lower.contains("kapalı") -> "☁️"
            lower.contains("güneş") || lower.contains("açık") -> "☀️"
            else -> "🌤️"
        }
    }

    val heroEmoji = remember(weather.condition) { getWeatherEmoji(weather.condition) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("hava_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Column {
                        Text(
                            text = "METEOROLOJİ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Hava Durumu",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // City switch chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { showCityPicker = true },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "▾",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = {
                            val q = if (!prefs.selectedDistrict.isNullOrBlank()) "${prefs.selectedDistrict}, ${prefs.selectedCity}" else weather.cityName
                            repository.fetchLiveWeather(q)
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        // Konum ve İlçe Yönetim Kartı
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_management_card"),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "SEÇİLİ KONUM & İLÇE HAVA DURUMU",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (!prefs.selectedDistrict.isNullOrBlank()) "${prefs.selectedDistrict} İlçesi, ${prefs.selectedCity}" else "${prefs.selectedCity} (Merkez)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        // Search and Change Dialog Button
                        Button(
                            onClick = { showCityPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp),
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("İlçe Değiştir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Horizontal quick district chips for the selected city
                    val availableDistricts = remember(prefs.selectedCity) {
                        com.example.data.TurkishLocations.getDistrictsForCity(prefs.selectedCity)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${prefs.selectedCity} İlçeleri (1 Tıkla Değiştir):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // "Tüm İl" chip
                            val isAllCity = prefs.selectedDistrict.isNullOrBlank()
                            FilterChip(
                                selected = isAllCity,
                                onClick = { repository.selectCityAndDistrict(prefs.selectedCity, null) },
                                label = { Text("Tüm İl") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )

                            // Districts
                            availableDistricts.forEach { district ->
                                val isSelected = prefs.selectedDistrict.equals(district, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { repository.selectCityAndDistrict(prefs.selectedCity, district) },
                                    label = { Text(district) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Cities Horizontal Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Büyükşehirler Hızlı Geçiş:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("İstanbul", "Ankara", "İzmir", "Bursa", "Antalya", "Trabzon", "Diyarbakır", "Eskişehir", "Gaziantep", "Samsun", "Konya").forEach { city ->
                        val isSelected = prefs.selectedCity == city && prefs.selectedDistrict.isNullOrBlank()
                        FilterChip(
                            selected = isSelected,
                            onClick = { repository.selectCityAndDistrict(city, null) },
                            label = { Text(city) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
        }

        // Hero Weather Card (Dynamic Gradient Background)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF132B45),
                                Color(0xFF0F1E30),
                                Color(0xFF0A1624),
                            )
                        )
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    ),
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF16A34A).copy(alpha = 0.25f),
                                ) {
                                    Text(
                                        text = "CANLI",
                                        color = Color(0xFF4ADE80),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = weather.condition,
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF93C5FD)),
                            )
                        }
                        Text(heroEmoji, fontSize = 48.sp)
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (weather.lastUpdated == "Veri bekleniyor") "--°" else "${weather.currentTempC}°",
                            fontSize = 68.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            lineHeight = 68.sp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.padding(bottom = 6.dp)) {
                            Text(
                                text = "Hissedilen: ${weather.feelsLikeC}°C",
                                style = MaterialTheme.typography.bodySmall.copy(color = CizgiColor.Gold, fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = "Yağış İhtimali: %${weather.rainChancePct}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0)),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = weather.summary,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0)),
                    )

                    Spacer(Modifier.height(16.dp))

                    // 4 Environmental Metric Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.09f))
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MetricItem("Nem", "%${weather.humidityPct}")
                        MetricItem("Rüzgar", "${weather.windKmh} km/s")
                        MetricItem("UV İndeks", "${weather.uvIndex}")
                        MetricItem("Kaynak", "WeatherAPI")
                    }
                }
            }
        }

        // Hourly Forecast Section (Dynamic Condition Emojis)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SAATLİK HAVA TAHMİNİ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.primary,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    weather.hourly.forEach { hourItem ->
                        val itemEmoji = getWeatherEmoji(hourItem.condition)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                            modifier = Modifier.width(80.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = hourItem.hour,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(itemEmoji, fontSize = 22.sp)
                                Text(
                                    text = "${hourItem.tempC}°",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "%${hourItem.rainProbPct}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7-Day Forecast Section (Dynamic Condition & Temp Curves)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "7 GÜNLÜK HAVA TAHMİNİ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.primary,
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        weather.daily.forEach { dayItem ->
                            val dayEmoji = getWeatherEmoji(dayItem.condition)
                            val progressRatio = ((dayItem.maxTempC - dayItem.minTempC).toFloat() / 15f).coerceIn(0.2f, 1f)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = dayItem.dayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.width(90.dp),
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.width(100.dp),
                                ) {
                                    Text(dayEmoji, fontSize = 18.sp)
                                    Text(
                                        text = "%${dayItem.rainProbPct}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "${dayItem.minTempC}°",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    LinearProgressIndicator(
                                        progress = { progressRatio },
                                        modifier = Modifier
                                            .width(55.dp)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = CizgiColor.Gold,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )

                                    Text(
                                        text = "${dayItem.maxTempC}°",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Attribution Note
        item {
            Text(
                text = if (weather.lastUpdated == "Veri bekleniyor") "Güncel hava verisi bekleniyor." else "Hava tahminleri WeatherAPI üzerinden alınır. Son güncelleme: ${weather.lastUpdated}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF94A3B8))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
