package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CizgiTopBar(
    weatherTemp: String = "--°", weatherCity: String = "", unreadNotificationCount: Int = 0,
    onWeatherClick: () -> Unit, onSearchClick: () -> Unit, onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val date = LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale.forLanguageTag("tr-TR")))
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("çizgi.", color = MaterialTheme.colorScheme.onSurface, fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.2).sp)
            Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(end = 7.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onWeatherClick).testTag("topbar_weather_chip"),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(weatherTemp, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(weatherCity, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 58.dp))
            }
        }
        IconButton(onSearchClick, Modifier.size(48.dp).testTag("topbar_search_button")) {
            Icon(Icons.Outlined.Search, "Haber ara", modifier = Modifier.size(21.dp))
        }
        IconButton(onNotificationsClick, Modifier.size(48.dp).testTag("topbar_notifications_button")) {
            BadgedBox(badge = { if (unreadNotificationCount > 0) Badge { Text(unreadNotificationCount.coerceAtMost(99).toString()) } }) {
                Icon(Icons.Outlined.Notifications, "Bildirimler", modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onProfileClick, Modifier.size(48.dp).testTag("topbar_profile_button")) {
            Icon(Icons.Outlined.Tune, "Ayarlar", modifier = Modifier.size(20.dp))
        }
    }
}
