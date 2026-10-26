package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.ui.components.CityDistrictPickerDialog
import com.example.ui.theme.CizgiColor

@Composable
fun HavaScreen(repository: CizgiRepository, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val weather by repository.weather.collectAsState()
    val prefs by repository.preferences.collectAsState()
    var picker by remember { mutableStateOf(false) }
    val location = listOfNotNull(prefs.selectedDistrict, prefs.selectedCity).filter(String::isNotBlank).joinToString(", ")
    LaunchedEffect(location) { repository.fetchLiveWeather(location) }
    if (picker) CityDistrictPickerDialog(
        currentCity = prefs.selectedCity, currentDistrict = prefs.selectedDistrict, onDismiss = { picker = false },
        onSelect = { city, district -> repository.selectCityAndDistrict(city, district); picker = false },
    )
    LazyColumn(
        modifier.fillMaxSize().testTag("hava_screen"), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack, Modifier.size(36.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Geri") }
                Text("Hava", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f).padding(start = 7.dp))
                IconButton({ repository.fetchLiveWeather(location) }, Modifier.size(36.dp)) { Icon(Icons.Outlined.Refresh, "Hava durumunu yenile", modifier = Modifier.size(20.dp)) }
            }
        }
        item {
            Surface(onClick = { picker = true }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("location_management_card")) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    Text(location, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, "Konum değiştir", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(CizgiColor.Blue, Color(0xFF8098F0))))) {
                Column(Modifier.fillMaxWidth().padding(25.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(weather.currentTempC?.let { "$it°" } ?: "–", color = Color.White, fontSize = 62.sp, lineHeight = 68.sp, fontWeight = FontWeight.Light, letterSpacing = (-3).sp)
                            Text(weather.condition, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(weatherIcon(weather.condition), null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(66.dp))
                    }
                    if (weather.daily.isNotEmpty()) {
                        val day = weather.daily.first()
                        Text("En yüksek " + day.maxTempC + "°  ·  En düşük " + day.minTempC + "°", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            val metrics = listOf(
                Triple(Icons.Outlined.Thermostat, "Hissedilen", weather.feelsLikeC?.let { "$it°" } ?: "–"),
                Triple(Icons.Outlined.Air, "Rüzgâr", weather.windKmh?.let { "$it km/sa" } ?: "–"),
                Triple(Icons.Outlined.WaterDrop, "Nem", weather.humidityPct?.let { "%$it" } ?: "–"),
                Triple(Icons.Outlined.WbSunny, "UV indeksi", weather.uvIndex?.toString() ?: "–"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (icon, title, value) ->
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(value, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (weather.hourly.isNotEmpty()) {
            item { Text("Saat saat", style = MaterialTheme.typography.titleLarge) }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    weather.hourly.forEach { hour ->
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(horizontal = 15.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(hour.hour, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(weatherIcon(hour.condition), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Text(hour.tempC.toString() + "°", style = MaterialTheme.typography.titleSmall)
                                Text("%" + hour.rainProbPct, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (weather.daily.isNotEmpty()) item { Text("Önümüzdeki günler", style = MaterialTheme.typography.titleLarge) }
        items(weather.daily) { day ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(day.dayName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Icon(weatherIcon(day.condition), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("%" + day.rainProbPct, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(day.minTempC.toString() + "°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(day.maxTempC.toString() + "°", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        item { Text("WeatherAPI · " + weather.lastUpdated, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun weatherIcon(condition: String): ImageVector {
    val text = condition.lowercase()
    return when {
        "kar" in text || "snow" in text -> Icons.Outlined.AcUnit
        "fırtına" in text || "gök gür" in text || "thunder" in text -> Icons.Outlined.Thunderstorm
        "yağmur" in text || "sağanak" in text || "rain" in text -> Icons.Outlined.Grain
        "bulut" in text || "kapalı" in text || "cloud" in text -> Icons.Outlined.Cloud
        "sis" in text || "pus" in text -> Icons.Outlined.BlurOn
        "açık" in text || "güneş" in text || "sun" in text || "clear" in text -> Icons.Outlined.WbSunny
        else -> Icons.Outlined.HourglassEmpty
    }
}
