package com.example.ui.components

import com.example.ui.theme.MotionVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.BISTStock
import com.example.model.*
import com.example.ui.theme.CizgiColor
import com.example.ui.theme.LocalReducedMotion
import java.text.NumberFormat
import java.util.Locale

fun Modifier.pressable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(if (pressed && !reduced) 0.975f else 1f, if (reduced) snap() else spring(stiffness = 550f), label = "press")
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun SourceImage(model: String, contentDescription: String?, contentScale: ContentScale = ContentScale.Crop, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val request = remember(model, context, reduced) { ImageRequest.Builder(context).data(model).crossfade(if (reduced) 0 else 220).build() }
    AsyncImage(model = request, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)
}

@Composable
fun MansetHeroCard(story: Story, onClick: () -> Unit, onSaveToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(276.dp).clip(RoundedCornerShape(26.dp))
            .background(CizgiColor.Navy).pressable(onClick).testTag("headline_card"),
    ) {
        if (story.imageUrl.isNotBlank()) SourceImage(
            model = story.imageUrl, contentDescription = story.title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.04f), Color(0xFF0C1427).copy(alpha = 0.38f), Color(0xFF0C1427).copy(alpha = 0.96f)))))
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.White.copy(alpha = 0.94f), shape = RoundedCornerShape(10.dp)) {
                Text(story.topic, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = CizgiColor.Navy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Surface(color = Color.Black.copy(alpha = 0.18f), shape = CircleShape) {
                IconButton(onSaveToggle, Modifier.size(48.dp)) {
                    Icon(if (story.isSaved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder, "Haberi kaydet", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(story.title, color = Color.White, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(CizgiColor.Gold))
                Text(story.publisher + "  ·  " + story.publishedAt, color = Color(0xFFD8DEEB), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun MansetCompactCard(story: Story, onClick: () -> Unit, onSaveToggle: () -> Unit, modifier: Modifier = Modifier) =
    MansetHeroCard(story, onClick, onSaveToggle, modifier)

@Composable
fun NewsFeedCard(story: Story, onClick: () -> Unit, onSaveToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp),
        modifier = modifier.fillMaxWidth().pressable(onClick).testTag("story_card_" + story.id),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(story.topic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(story.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                }
                if (story.imageUrl.isNotBlank()) SourceImage(
                    model = story.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(92.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(story.publisher + " · " + story.publishedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onSaveToggle, Modifier.size(28.dp)) {
                    Icon(if (story.isSaved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder, if (story.isSaved) "Kayıtlardan çıkar" else "Kaydet", tint = if (story.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun ModernNewsCard(story: Story, onClick: () -> Unit, onSaveToggle: () -> Unit, onShareClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().pressable(onClick)) {
        Column {
            if (story.imageUrl.isNotBlank()) {
                Box(Modifier.fillMaxWidth().height(178.dp)) {
                    SourceImage(story.imageUrl, contentDescription = story.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), shape = RoundedCornerShape(9.dp), modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                        Text(story.topic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(story.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (story.summary.isNotBlank() && !story.summary.equals(story.title, true)) {
                    Text(story.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(story.publisher + " · " + story.publishedAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onShareClick, Modifier.size(30.dp)) { Icon(Icons.Outlined.Share, "Paylaş", modifier = Modifier.size(17.dp)) }
                    IconButton(onSaveToggle, Modifier.size(30.dp)) { Icon(if (story.isSaved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder, "Kaydet", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
fun GununOzetiCard(points: List<String>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(22.dp), modifier = modifier.fillMaxWidth().then(if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize()).clickable { expanded = !expanded }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                Column(Modifier.weight(1f)) {
                    Text("Günün kısa turu", style = MaterialTheme.typography.titleSmall)
                    Text(points.size.toString() + " gelişmede gündem", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, if (expanded) "Özeti kapat" else "Günün özetini aç", tint = MaterialTheme.colorScheme.primary)
            }
            MotionVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    points.forEachIndexed { index, point ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text((index + 1).toString().padStart(2, '0'), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            Text(point, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniMatchCard(match: SportsMatch, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth().pressable(onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(match.competition, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (match.status == MatchStatus.LIVE) Box(Modifier.padding(end = 5.dp).size(5.dp).clip(CircleShape).background(CizgiColor.Red))
                Text(match.statusLabel, style = MaterialTheme.typography.labelSmall, color = if (match.status == MatchStatus.LIVE) CizgiColor.Red else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(match.homeTeam, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (match.status == MatchStatus.UPCOMING) match.kickoffTime else (match.homeScore?.toString() ?: "–") + " : " + (match.awayScore?.toString() ?: "–"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(match.awayTeam, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val alpha = if (LocalReducedMotion.current) .6f else {
        val transition = rememberInfiniteTransition(label = "loading")
        val animated by transition.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "skeleton")
        animated
    }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(125.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
            Box(Modifier.fillMaxWidth(0.9f).height(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
            Box(Modifier.fillMaxWidth(0.65f).height(13.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)))
        }
    }
}

@Composable
fun PublisherAvatar(publisher: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(publisher.take(2).uppercase(Locale.forLanguageTag("tr-TR")), color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BistWatchlistCard(stocks: List<BISTStock>, financialAssets: List<FinancialAsset>, onViewMarketsClick: () -> Unit, modifier: Modifier = Modifier) {
    val quotes = buildList {
        stocks.filter { it.price > 0 }.take(1).forEach { add(Triple(it.symbol, it.price, it.changePct)) }
        financialAssets.filter { it.currentPrice > 0 }.distinctBy { it.code }.take(3 - size).forEach { add(Triple(it.code, it.currentPrice, it.changePct)) }
    }
    if (quotes.isEmpty()) return
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("tr-TR")).apply { maximumFractionDigits = 2 }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(21.dp), modifier = modifier.fillMaxWidth().pressable(onViewMarketsClick)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Piyasalara bakış", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.NorthEast, "Piyasalara git", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                quotes.forEach { (code, price, change) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(format.format(price), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        change?.let { Text((if (it >= 0) "+" else "") + format.format(it) + "%", color = if (it >= 0) CizgiColor.Green else CizgiColor.Red, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniWeatherCard(weather: WeatherInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth().pressable(onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(weather.cityName, style = MaterialTheme.typography.titleSmall)
                Text(weather.condition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(weather.currentTempC?.let { "$it°" } ?: "–", style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
fun KompaktHavaKarti(weather: WeatherInfo, onClick: () -> Unit, onCitySelect: (String) -> Unit, modifier: Modifier = Modifier) =
    MiniWeatherCard(weather, onClick, modifier)
