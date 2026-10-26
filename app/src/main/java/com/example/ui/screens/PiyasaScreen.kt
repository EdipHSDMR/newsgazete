package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.data.remote.BISTStock
import com.example.model.FinancialAsset
import com.example.model.MarketCategory
import java.util.Locale
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val marketTabs = listOf("Genel", "BIST", "Döviz", "Altın & Emtia", "Kripto", "Takip")

@Composable
fun PiyasaScreen(
    repository: CizgiRepository,
    modifier: Modifier = Modifier,
) {
    val assets by repository.financialAssets.collectAsState()
    val bistStocks by repository.bistStocks.collectAsState()
    val marketStatusMessage by repository.marketStatusMessage.collectAsState()
    val context = LocalContext.current
    val watchlistPrefs = remember(context) { context.getSharedPreferences("market_watchlist", 0) }
    var favorites by remember {
        mutableStateOf(watchlistPrefs.getStringSet("codes", emptySet()).orEmpty().toSet())
    }
    var selectedTab by remember { mutableStateOf("Genel") }
    var searchText by remember { mutableStateOf("") }
    var showAllStocks by remember { mutableStateOf(false) }
    var showConverter by remember { mutableStateOf(false) }
    var converterAmount by remember { mutableStateOf("100") }

    LaunchedEffect(Unit) {
        repository.refreshMarkets()
        while (true) {
            delay(MARKET_SCREEN_REFRESH_MS)
            repository.refreshMarkets()
        }
    }

    val normalizedSearch = searchText.trim().lowercase(Locale.forLanguageTag("tr-TR"))
    val selectedAssets = remember(assets, favorites, selectedTab, normalizedSearch) {
        assets.filter { asset ->
            val categoryMatches = when (selectedTab) {
                "Döviz" -> asset.category == MarketCategory.CURRENCY
                "Altın & Emtia" -> asset.category == MarketCategory.METAL || asset.category == MarketCategory.COMMODITY
                "Kripto" -> asset.category == MarketCategory.CRYPTO
                "Takip" -> asset.code in favorites
                else -> selectedTab == "Genel"
            }
            val queryMatches = normalizedSearch.isBlank() ||
                asset.code.lowercase(Locale.ROOT).contains(normalizedSearch) ||
                asset.name.lowercase(Locale.forLanguageTag("tr-TR")).contains(normalizedSearch)
            categoryMatches && queryMatches
        }
    }
    val selectedStocks = remember(bistStocks, favorites, selectedTab, normalizedSearch) {
        bistStocks.filter { stock ->
            val categoryMatches = selectedTab == "Genel" || selectedTab == "BIST" ||
                (selectedTab == "Takip" && "BIST:${stock.symbol}" in favorites)
            val queryMatches = normalizedSearch.isBlank() ||
                stock.symbol.lowercase(Locale.ROOT).contains(normalizedSearch) ||
                stock.companyName.lowercase(Locale.forLanguageTag("tr-TR")).contains(normalizedSearch)
            categoryMatches && queryMatches
        }
    }
    val showStocks = selectedTab == "Genel" || selectedTab == "BIST" || selectedTab == "Takip"
    val stocksToShow = if (selectedTab == "Genel" && !showAllStocks) selectedStocks.take(4) else selectedStocks
    val converterAsset = assets.firstOrNull { it.code == "USD/TRY" }
    val convertedValue = converterAmount.replace(',', '.').toDoubleOrNull()?.let { amount ->
        converterAsset?.let { amount * it.currentPrice }
    }
    val toggleFavorite: (String) -> Unit = { code ->
        val next = favorites.toMutableSet().apply { if (!add(code)) remove(code) }.toSet()
        favorites = next
        watchlistPrefs.edit().putStringSet("codes", next).apply()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("piyasa_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("PİYASA TAKİP MERKEZİ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("Piyasalar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Kaynak saati veya günlük veri tarihi her kartta gösterilir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { repository.refreshMarkets(force = true) }, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Piyasayı şimdi yenile", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                marketTabs.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab; showAllStocks = false },
                        label = { Text(if (tab == "Takip") "$tab (${favorites.size})" else tab, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Varlık veya hisse ara") },
                shape = RoundedCornerShape(14.dp),
            )
        }

        if (marketStatusMessage.isNotBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = marketStatusMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        if (selectedTab == "Genel") {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("VERİ KAYNAKLARI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("TCMB kurları günlük gösterge değeridir. BIST ve diğer piyasa verileri kaynağın gecikmesine tabidir; uygulama gerçek zamanlı işlem kotasyonu iddiasında bulunmaz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth().clickable { showConverter = !showConverter },
                ) {
                    Text(if (showConverter) "−  Hızlı USD/TRY dönüştürücü" else "+  Hızlı USD/TRY dönüştürücü", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
            }
            item {
                AnimatedVisibility(visible = showConverter) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = converterAmount,
                            onValueChange = { value -> if (value.all { it.isDigit() || it == '.' || it == ',' }) converterAmount = value },
                            label = { Text("USD miktarı") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Text("=", style = MaterialTheme.typography.titleLarge)
                        Text(
                            convertedValue?.let { String.format(Locale.forLanguageTag("tr-TR"), "%.2f ₺", it) } ?: "Kur bekleniyor",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (showStocks && stocksToShow.isNotEmpty()) {
            item {
                SectionHeading(
                    title = "BIST",
                    detail = if (selectedTab == "Genel") "Öne çıkan hisseler" else "Endeksler ve izlenen hisseler",
                )
            }
            items(stocksToShow, key = { "BIST:${it.symbol}" }) { stock ->
                BISTStockCard(
                    stock = stock,
                    isFavorite = "BIST:${stock.symbol}" in favorites,
                    onFavoriteClick = { toggleFavorite("BIST:${stock.symbol}") },
                )
            }
            if (selectedTab == "Genel" && selectedStocks.size > stocksToShow.size) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { showAllStocks = true },
                    ) {
                        Text("BIST listesinin tamamını göster (${selectedStocks.size})", modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (selectedAssets.isNotEmpty()) {
            item {
                SectionHeading(
                    title = when (selectedTab) {
                        "Döviz" -> "Döviz kurları"
                        "Altın & Emtia" -> "Altın ve emtia"
                        "Kripto" -> "Kripto varlıklar"
                        "Takip" -> "Takip listen"
                        else -> "Döviz, altın, emtia ve kripto"
                    },
                    detail = "${selectedAssets.size} varlık",
                )
            }
            items(selectedAssets, key = { it.code }) { asset ->
                FinancialAssetCard(
                    asset = asset,
                    isFavorite = asset.code in favorites,
                    onFavoriteClick = { toggleFavorite(asset.code) },
                )
            }
        }

        if (selectedAssets.isEmpty() && (!showStocks || selectedStocks.isEmpty())) {
            item {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Text(
                        text = if (selectedTab == "Takip") "Takip listende henüz varlık yok. Bir karttaki ☆ simgesine dokunarak ekleyebilirsin." else "Bu bölüm için şu anda kaynak verisi alınamıyor. Bağlantını kontrol edip yenilemeyi dene.",
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BISTStockCard(
    stock: BISTStock,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stock.symbol, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(stock.companyName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${stock.source} · ${formatBistQuoteTime(stock.updatedAtEpochMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val quoteDetails = buildList {
                    if (stock.low != null && stock.high != null) add("Gün içi ${formatPrice(stock.low)} – ${formatPrice(stock.high)} ${stock.unit}")
                    if (stock.volume.isNotBlank()) add("Hacim ${stock.volume}")
                }.joinToString(" · ")
                if (quoteDetails.isNotBlank()) {
                    Text(quoteDetails, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatPrice(stock.price)} ${stock.unit}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                stock.changePct?.let { change ->
                    val positive = change >= 0.0
                    Text(
                        text = "${if (positive) "+" else ""}${formatPrice(change)}%",
                        color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(if (isFavorite) "★" else "☆", modifier = Modifier.padding(start = 10.dp).clickable(onClick = onFavoriteClick).padding(4.dp), fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
        }
            stock.sparkline.takeIf { it.size > 1 }?.let { MiniSparkline(it, Modifier.fillMaxWidth().height(26.dp).padding(top = 5.dp)) }
    }
}

@Composable
fun FinancialAssetCard(
    asset: FinancialAsset,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(asset.code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(asset.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${formatPrice(asset.currentPrice)} ${asset.unit}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    asset.changePct?.let { change ->
                        val positive = change >= 0.0
                        Text(
                            "${asset.changePeriodLabel} ${if (positive) "+" else ""}${formatPrice(change)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(if (isFavorite) "★" else "☆", modifier = Modifier.padding(start = 10.dp).clickable(onClick = onFavoriteClick).padding(4.dp), fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(asset.source.ifBlank { "Kaynak bilgisi yok" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(" · ${formatAssetQuoteTime(asset)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (asset.buyPrice != null && asset.sellPrice != null) {
                Text("Alış ${formatPrice(asset.buyPrice)} ₺ · Satış ${formatPrice(asset.sellPrice)} ₺", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            asset.quoteNote.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (asset.lowPrice != null && asset.highPrice != null) {
                Text("Gün içi ${formatPrice(asset.lowPrice)} – ${formatPrice(asset.highPrice)} ${asset.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            asset.sparkline.takeIf { it.size > 1 }?.let { MiniSparkline(it, Modifier.fillMaxWidth().height(26.dp).padding(top = 5.dp)) }
        }
    }
}

@Composable
private fun MiniSparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val points = values.filter(Float::isFinite)
    if (points.size < 2) return
    val color = if (points.last() >= points.first()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Canvas(modifier = modifier) {
        val minimum = points.minOrNull() ?: return@Canvas
        val maximum = points.maxOrNull() ?: return@Canvas
        val range = (maximum - minimum).takeIf { it > 0f } ?: 1f
        val verticalPadding = 3.dp.toPx()
        val chartHeight = (size.height - verticalPadding * 2).coerceAtLeast(1f)
        val coordinates = points.mapIndexed { index, value ->
            Offset(
                x = size.width * index / (points.lastIndex.coerceAtLeast(1)),
                y = size.height - verticalPadding - ((value - minimum) / range) * chartHeight,
            )
        }
        coordinates.zipWithNext().forEach { (start, end) ->
            drawLine(color = color, start = start, end = end, strokeWidth = 2.dp.toPx())
        }
    }
}

private fun formatPrice(value: Double): String = when {
    !value.isFinite() -> "—"
    kotlin.math.abs(value) >= 100_000 -> String.format(MARKET_LOCALE, "%,.0f", value)
    kotlin.math.abs(value) >= 1_000 -> String.format(MARKET_LOCALE, "%,.2f", value)
    kotlin.math.abs(value) >= 1 -> String.format(MARKET_LOCALE, "%.2f", value)
    else -> String.format(MARKET_LOCALE, "%.5f", value)
}

private val MARKET_LOCALE = Locale.forLanguageTag("tr-TR")

private fun formatBistQuoteTime(epochMs: Long): String {
    if (epochMs <= 0L) return "Kaynak saati yok"
    val zone = ZoneId.of("Europe/Istanbul")
    val updatedAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
    val now = ZonedDateTime.now(zone)
    val date = updatedAt.format(DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.forLanguageTag("tr-TR")))
    val tradingHours = now.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) &&
        now.toLocalTime() >= LocalTime.of(10, 0) && now.toLocalTime() < LocalTime.of(18, 10)
    return when {
        Duration.between(updatedAt, now).toDays() >= 7 -> "Eski kaynak verisi · $date"
        tradingHours && Duration.between(updatedAt, now).toMinutes() > 30 -> "Gecikmiş kaynak verisi · $date"
        !tradingHours -> "Piyasa kapalı · son kotasyon $date"
        else -> "$date kaynak saati"
    }
}

private fun formatAssetQuoteTime(asset: FinancialAsset): String {
    val epochMs = asset.updatedAtEpochMs
    if (epochMs <= 0L) return "Kaynak saati yok"
    val now = System.currentTimeMillis()
    val ageMs = (now - epochMs).coerceAtLeast(0L)
    if (asset.source == "TCMB Gösterge Kur") {
        val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("Europe/Istanbul"))
            .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("tr-TR")))
        val stale = ageMs > 5 * 24 * 60 * 60_000L
        return "Veri tarihi $date · günlük gösterge${if (stale) " · eski" else ""}"
    }
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of("Europe/Istanbul"))
        .format(DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.forLanguageTag("tr-TR")))
    val staleAfter = if (asset.category == MarketCategory.CRYPTO) 5 * 60_000L else 48 * 60 * 60_000L
    return if (ageMs > staleAfter) "$date · gecikmiş/eski kaynak verisi" else "$date kaynak saati"
}

private const val MARKET_SCREEN_REFRESH_MS = 90_000L
