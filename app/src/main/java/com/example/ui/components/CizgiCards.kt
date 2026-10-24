package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.SportsMatch
import com.example.model.Story
import com.example.model.WeatherInfo
import com.example.ui.theme.CizgiColor

@Composable
fun MansetHeroCard(
    story: Story,
    onClick: () -> Unit,
    onSaveToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CizgiColor.BorderLight, CizgiColor.BorderLight.copy(alpha = 0.5f)))),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("manset_card_${story.id}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Hero Image Container with Badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(CizgiColor.NavyDark),
            ) {
                AsyncImage(
                    model = story.imageUrl,
                    contentDescription = story.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Top Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // MANŞET Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CizgiColor.Navy,
                        ) {
                            Text(
                                text = "MANŞET",
                                color = CizgiColor.Gold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }

                        if (story.isBreaking) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CizgiColor.Gold,
                            ) {
                                Text(
                                    text = "SON DAKİKA",
                                    color = CizgiColor.Navy,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    // Topic Chip
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                    ) {
                        Text(
                            text = story.topic.uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Text Content Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 26.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = story.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(12.dp))

                // Metadata Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = story.publisher,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = " · ${story.publishedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (story.sourceCount > 1) {
                        Text(
                            text = " · ${story.sourceCount} kaynak",
                            style = MaterialTheme.typography.bodySmall,
                            color = CizgiColor.Blue,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = onSaveToggle,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (story.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (story.isSaved) "Kaydedildi" else "Kaydet",
                            tint = if (story.isSaved) CizgiColor.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewsFeedCard(
    story: Story,
    onClick: () -> Unit,
    onSaveToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CizgiColor.BorderLight, CizgiColor.BorderLight.copy(alpha = 0.5f)))),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("story_card_${story.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = story.topic.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    if (story.isBreaking) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = CizgiColor.GoldContainer,
                        ) {
                            Text(
                                text = "SON DAKİKA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                ),
                                color = CizgiColor.Navy,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Text(
                        text = story.publishedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = story.publisher,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )

                    if (story.sourceCount > 1) {
                        Text(
                            text = " · ${story.sourceCount} kaynak",
                            style = MaterialTheme.typography.labelSmall,
                            color = CizgiColor.Blue,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = onSaveToggle,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (story.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (story.isSaved) "Kaydedildi" else "Kaydet",
                            tint = if (story.isSaved) CizgiColor.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Right thumbnail image
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CizgiColor.SurfaceVariant),
            ) {
                if (story.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = story.imageUrl,
                        contentDescription = story.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = story.topic.take(3).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GununOzetiCard(
    points: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CizgiColor.BorderLight, CizgiColor.BorderLight.copy(alpha = 0.5f)))),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(CizgiColor.Navy),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("5", color = CizgiColor.Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Column {
                        Text(
                            text = "GÜNÜN 5 MADDELİK ÖZETİ",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Gündemin en can alıcı noktaları",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Daralt" else "Genişlet",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    points.forEachIndexed { index, point ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KompaktHavaKarti(
    weather: WeatherInfo,
    onClick: () -> Unit,
    onCitySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCitySelectorOpen by remember { mutableStateOf(false) }
    val quickCities = listOf("İstanbul", "Ankara", "İzmir", "Bursa", "Antalya", "Trabzon", "Diyarbakır")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    CizgiColor.BorderLight,
                    CizgiColor.Navy.copy(alpha = 0.15f)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("kompakt_hava_karti"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top Row: Location with Pin + Live Status Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Location Button / Chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable { isCitySelectorOpen = !isCitySelectorOpen }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Konum",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = weather.cityName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (isCitySelectorOpen) "▲" else "▼",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                // Live Pulsing Dot + Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32)),
                    )
                    Text(
                        text = "CANLI • WeatherAPI",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        ),
                        color = Color(0xFF2E7D32),
                    )
                }
            }

            // Expandable City Quick Selector Row
            if (isCitySelectorOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    quickCities.forEach { city ->
                        val isSelected = weather.cityName.equals(city, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                onCitySelect(city)
                                isCitySelectorOpen = false
                            }
                        ) {
                            Text(
                                text = city,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            // Main Weather Info: Big Temp + Condition Icon + Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = when {
                            weather.condition.contains("Güneşli", ignoreCase = true) -> "☀️"
                            weather.condition.contains("Yağmur", ignoreCase = true) -> "🌧️"
                            weather.condition.contains("Bulutlu", ignoreCase = true) -> "⛅"
                            weather.condition.contains("Kar", ignoreCase = true) -> "❄️"
                            weather.condition.contains("Fırtına", ignoreCase = true) -> "⛈️"
                            else -> "🌤️"
                        },
                        fontSize = 36.sp,
                    )
                    Column {
                        Text(
                            text = if (weather.lastUpdated == "Veri bekleniyor") "--°C" else "${weather.currentTempC}°C",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = weather.condition,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = if (weather.lastUpdated == "Veri bekleniyor") "Hissedilen: --°C" else "Hissedilen: ${weather.feelsLikeC}°C",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "24s Tahmin ›",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CizgiColor.Blue,
                    )
                }
            }

            // Bottom Metrics Bar: Rain % | Wind km/h | Humidity %
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("💧", fontSize = 12.sp)
                    Text(
                        text = "Yağış: %${weather.rainChancePct}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text("•", color = CizgiColor.BorderLight)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("💨", fontSize = 12.sp)
                    Text(
                        text = "Rüzgar: ${weather.windKmh} km/s",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text("•", color = CizgiColor.BorderLight)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🌫️", fontSize = 12.sp)
                    Text(
                        text = "Nem: %${weather.humidityPct}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun MiniWeatherCard(
    weather: WeatherInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CizgiColor.BorderLight, CizgiColor.BorderLight.copy(alpha = 0.5f)))),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("mini_weather_card"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🌤️", fontSize = 24.sp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (weather.lastUpdated == "Veri bekleniyor") "--°" else "${weather.currentTempC}°",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = weather.cityName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = if (weather.lastUpdated == "Veri bekleniyor") weather.condition else "${weather.condition} · Yağış ihtimali %${weather.rainChancePct}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Hava Durumu ›",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CizgiColor.Blue,
            )
        }
    }
}

@Composable
fun MiniMatchCard(
    match: SportsMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(CizgiColor.BorderLight, CizgiColor.BorderLight.copy(alpha = 0.5f)))),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("mini_match_card"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⚽", fontSize = 20.sp)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${match.homeTeam} vs ${match.awayTeam}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (match.status == com.example.model.MatchStatus.LIVE) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = CizgiColor.Gold,
                            ) {
                                Text(
                                    text = match.statusLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CizgiColor.Navy,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    val scoreText = if (match.homeScore != null && match.awayScore != null) {
                        "Skor: ${match.homeScore} - ${match.awayScore} (${match.statusLabel})"
                    } else {
                        "Başlama Saati: ${match.kickoffTime}"
                    }

                    Text(
                        text = "${match.competition} · $scoreText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Maç Merkezi ›",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = CizgiColor.Blue,
            )
        }
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CizgiColor.Rule.copy(alpha = alpha)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CizgiColor.Rule.copy(alpha = alpha)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CizgiColor.Rule.copy(alpha = alpha)),
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CizgiColor.Rule.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
fun PublisherAvatar(
    publisher: String,
    modifier: Modifier = Modifier,
) {
    val (initials, bgColor) = when {
        publisher.contains("Anadolu", ignoreCase = true) -> "AA" to CizgiColor.Navy
        publisher.contains("BBC", ignoreCase = true) -> "BBC" to Color(0xFFB91C1C)
        publisher.contains("Reuters", ignoreCase = true) -> "REU" to Color(0xFFD97706)
        publisher.contains("TRT", ignoreCase = true) -> "TRT" to Color(0xFF0284C7)
        publisher.contains("Bloomberg", ignoreCase = true) -> "BLM" to Color(0xFF2563EB)
        publisher.contains("Webrazzi", ignoreCase = true) -> "WEB" to Color(0xFF059669)
        publisher.contains("NTV", ignoreCase = true) -> "NTV" to Color(0xFF7C3AED)
        else -> publisher.take(2).uppercase() to CizgiColor.Navy
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (initials.length > 2) 9.sp else 11.sp,
        )
    }
}

/**
 * Modern, görsel ağırlıklı kart tabanlı haber bileşeni.
 * Material 3 standartlarında görsel yerleşimi, okuma süresi tahmini,
 * haber kaynağı ikonu, paylaş ve kaydet butonlarını barındırır.
 */
@Composable
fun ModernNewsCard(
    story: Story,
    onClick: () -> Unit,
    onSaveToggle: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(CizgiColor.BorderLight, CizgiColor.BorderLight.copy(alpha = 0.5f))
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("modern_news_card_${story.id}"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Görsel Ağırlıklı Üst Bölüm (16:9 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(CizgiColor.NavyDark),
            ) {
                if (story.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = story.imageUrl,
                        contentDescription = story.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "ÇİZGİ · ${story.topic.uppercase()}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                // Gradient Scrim for readable badges
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                ),
                            )
                        ),
                )

                // Üst Badgeler: Kategori ve Okuma Süresi
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Kategori ve Varsa Son Dakika Etiketi
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CizgiColor.Navy.copy(alpha = 0.9f),
                        ) {
                            Text(
                                text = story.topic.uppercase(),
                                color = CizgiColor.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }

                        if (story.isBreaking) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CizgiColor.Gold,
                            ) {
                                Text(
                                    text = "SON DAKİKA",
                                    color = CizgiColor.Navy,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Okuma Süresi Tahmini Rozeti
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("⏱️", fontSize = 11.sp)
                            Text(
                                text = "${story.readTimeMin} dk okuma",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // 2. Alt İçerik Bölümü
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Kaynak İkonu ve Yayıncı Başlığı
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PublisherAvatar(publisher = story.publisher)

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = story.publisher,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "· ${story.publishedAt}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (story.sourceCount > 1) {
                            Text(
                                text = "${story.sourceCount} kaynak tarafından doğrulandı",
                                style = MaterialTheme.typography.labelSmall,
                                color = CizgiColor.Blue,
                            )
                        }
                    }
                }

                // Başlık
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        lineHeight = 25.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                // Özet
                Text(
                    text = story.summary,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 21.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(4.dp))

                // Alt Butonlar: Detay Linki, Paylaş ve Kaydet Butonları
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Haberi İncele ›",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = CizgiColor.Blue,
                        ),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Paylaş Butonu (Material 3 standardında en az 48dp dokunma alanı)
                        IconButton(
                            onClick = onShareClick,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("share_button_${story.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Haberi Paylaş",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        // Kaydet Butonu
                        IconButton(
                            onClick = onSaveToggle,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("save_button_${story.id}"),
                        ) {
                            Icon(
                                imageVector = if (story.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (story.isSaved) "Kaydedildi" else "Kaydet",
                                tint = if (story.isSaved) CizgiColor.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Ana Ekran için Retrofit Destekli Canlı BIST & Emtia Takip Listesi Widget'ı
 */
@Composable
fun BistWatchlistCard(
    stocks: List<com.example.data.remote.BISTStock>,
    financialAssets: List<com.example.model.FinancialAsset>,
    onViewMarketsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(CizgiColor.BorderLight, CizgiColor.BorderLight.copy(alpha = 0.5f))
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("bist_watchlist_widget"),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF16A34A)),
                    )
                    Text(
                        text = "BORSA İSTANBUL & PİYASA TAKİBİ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "CANLI RETROFIT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = "Tüm Piyasalar ›",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.clickable(onClick = onViewMarketsClick),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Horizontal Scrolling Watchlist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Key Commodities (Altın, Dolar)
                val gramAltin = financialAssets.firstOrNull { it.code == "GA" }
                val ceyrekAltin = financialAssets.firstOrNull { it.code == "CA" }
                val usd = financialAssets.firstOrNull { it.code == "USD/TRY" }

                if (gramAltin != null) {
                    BistMiniItem(
                        symbol = "GRAM ALTIN",
                        sub = "24K Has",
                        priceStr = String.format(java.util.Locale.US, "%,.2f ₺", gramAltin.currentPrice),
                        changePct = gramAltin.changePct,
                        onClick = onViewMarketsClick,
                    )
                }

                if (ceyrekAltin != null) {
                    BistMiniItem(
                        symbol = "ÇEYREK",
                        sub = "Darphane",
                        priceStr = String.format(java.util.Locale.US, "%,.2f ₺", ceyrekAltin.currentPrice),
                        changePct = ceyrekAltin.changePct,
                        onClick = onViewMarketsClick,
                    )
                }

                if (usd != null) {
                    BistMiniItem(
                        symbol = "USD/TRY",
                        sub = "Dolar",
                        priceStr = String.format(java.util.Locale.US, "%.2f ₺", usd.currentPrice),
                        changePct = usd.changePct,
                        onClick = onViewMarketsClick,
                    )
                }

                // BIST Stocks from Retrofit
                stocks.forEach { stock ->
                    BistMiniItem(
                        symbol = stock.symbol,
                        sub = stock.companyName,
                        priceStr = if (stock.symbol == "XU100") {
                            String.format(java.util.Locale.US, "%,.2f", stock.price)
                        } else {
                            String.format(java.util.Locale.US, "%.2f ₺", stock.price)
                        },
                        changePct = stock.changePct,
                        onClick = onViewMarketsClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BistMiniItem(
    symbol: String,
    sub: String,
    priceStr: String,
    changePct: Double?,
    onClick: () -> Unit,
) {
    val isPositive = (changePct ?: 0.0) >= 0.0
    val trendColor = when {
        changePct == null -> MaterialTheme.colorScheme.onSurfaceVariant
        isPositive -> Color(0xFF16A34A)
        else -> Color(0xFFDC2626)
    }
    val trendBg = if (changePct == null) {
        MaterialTheme.colorScheme.surfaceVariant
    } else if (isPositive) {
        Color(0xFFDCFCE7)
    } else {
        Color(0xFFFEE2E2)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                )
            )
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(1.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = trendBg,
                ) {
                    Text(
                        text = changePct?.let { if (isPositive) "▲ +${String.format(java.util.Locale.US, "%.2f", it)}%" else "▼ ${String.format(java.util.Locale.US, "%.2f", it)}%" } ?: "—",
                        color = trendColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = priceStr,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
