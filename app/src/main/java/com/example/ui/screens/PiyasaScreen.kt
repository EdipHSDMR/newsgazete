package com.example.ui.screens

import com.example.ui.theme.MotionVisibility
import com.example.ui.theme.LocalReducedMotion
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.CizgiRepository
import com.example.data.remote.BISTStock
import com.example.model.FinancialAsset
import com.example.model.MarketCategory
import com.example.ui.theme.CizgiColor
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val marketTabs = listOf("Genel", "BIST", "Döviz", "Altın & Emtia", "Kripto", "Takip")
private val MARKET_LOCALE = Locale.forLanguageTag("tr-TR")

@Composable
fun PiyasaScreen(repository: CizgiRepository, modifier: Modifier = Modifier) {
    val assets by repository.financialAssets.collectAsState()
    val stocks by repository.bistStocks.collectAsState()
    val error by repository.marketStatusMessage.collectAsState()
    val context = LocalContext.current
    val store = remember { context.getSharedPreferences("market_watchlist", 0) }
    var favorites by remember { mutableStateOf(store.getStringSet("codes", emptySet()).orEmpty().toSet()) }
    var tab by rememberSaveable { mutableStateOf("Genel") }
    var query by rememberSaveable { mutableStateOf("") }
    var info by remember { mutableStateOf(false) }
    var converter by remember { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { repository.refreshMarkets(); delay(90_000L) }
        }
    }
    fun favorite(code: String) {
        favorites = favorites.toMutableSet().apply { if (!add(code)) remove(code) }.toSet()
        store.edit().putStringSet("codes", favorites).apply()
    }
    val matchedAssets = remember(assets, tab, query, favorites) {
        assets.filter { asset ->
            val category = when (tab) {
                "Genel" -> true; "Döviz" -> asset.category == MarketCategory.CURRENCY
                "Altın & Emtia" -> asset.category in setOf(MarketCategory.METAL, MarketCategory.COMMODITY)
                "Kripto" -> asset.category == MarketCategory.CRYPTO; "Takip" -> asset.code in favorites
                else -> false
            }
            category && (query.isBlank() || asset.code.contains(query, true) || asset.name.contains(query, true))
        }
    }
    val matchedStocks = remember(stocks, tab, query, favorites) {
        stocks.filter { stock -> (tab in setOf("Genel", "BIST") || tab == "Takip" && "BIST:" + stock.symbol in favorites) &&
            (query.isBlank() || stock.symbol.contains(query, true) || stock.companyName.contains(query, true)) }
    }
    if (info) AlertDialog(
        onDismissRequest = { info = false }, title = { Text("Veri kaynakları") },
        text = { Text("TCMB kurları günlük gösterge değeridir. BIST ve diğer varlıkların kotasyonları sağlayıcının gecikmesine tabidir. Kaynak ile veri tarihi her satırda görünür; hesaplanan değerler ayrıca belirtilir.") },
        confirmButton = { TextButton({ info = false }) { Text("Tamam") } },
    )
    LazyColumn(
        modifier.fillMaxSize().testTag("piyasa_screen"), contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Piyasalar", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton({ info = true }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Info, "Veri kaynakları", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton({ repository.refreshMarkets(force = true) }, Modifier.size(32.dp)) { Icon(Icons.Outlined.Refresh, "Piyasayı yenile", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                marketTabs.forEach { label ->
                    Surface(
                        color = if (tab == label) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable { tab = label },
                    ) { Text(label, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp), style = MaterialTheme.typography.labelMedium, color = if (tab == label) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item {
            OutlinedTextField(
                query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Varlık veya hisse ara", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(19.dp)) },
                shape = RoundedCornerShape(17.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
            )
        }
        if (error.isNotBlank()) item {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                Text(error, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if (tab == "Genel" && assets.any { it.code == "USD/TRY" }) item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth().then(if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize())) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { converter = !converter }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CurrencyExchange, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        Text("Kur dönüştürücü", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(if (converter) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "Dönüştürücüyü aç", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                    MotionVisibility(converter) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(amount, { next -> if (next.all { it.isDigit() || it in ".," }) amount = next }, modifier = Modifier.weight(1f), label = { Text("USD") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(13.dp))
                            val rate = assets.firstOrNull { it.code == "USD/TRY" }?.currentPrice
                            Text(amount.replace(',', '.').toDoubleOrNull()?.let { value -> rate?.let { formatPrice(value * it) + " ₺" } } ?: "–", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (matchedStocks.isNotEmpty()) {
            item { SectionHeading("BIST", if (tab == "Genel") "Endeksler ve hisseler" else matchedStocks.size.toString() + " enstrüman") }
            items(if (tab == "Genel" && query.isBlank()) matchedStocks.take(4) else matchedStocks, key = { "BIST:" + it.symbol }) {
                BISTStockCard(it, isFavorite = "BIST:" + it.symbol in favorites, onFavoriteClick = { favorite("BIST:" + it.symbol) })
            }
            if (tab == "Genel" && matchedStocks.size > 4) item { TextButton({ tab = "BIST" }) { Text("Tüm BIST enstrümanları") } }
        }
        if (matchedAssets.isNotEmpty()) {
            item { SectionHeading(if (tab == "Genel") "Döviz, emtia ve kripto" else tab, matchedAssets.size.toString() + " varlık") }
            items(matchedAssets, key = { it.code }) { asset ->
                FinancialAssetCard(asset, isFavorite = asset.code in favorites, onFavoriteClick = { favorite(asset.code) })
            }
        }
        if (matchedStocks.isEmpty() && matchedAssets.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if (tab == "Takip") Icons.Outlined.StarBorder else Icons.Outlined.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (tab == "Takip") "Takip listen henüz boş." else "Bu aramada varlık bulunamadı.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BISTStockCard(stock: BISTStock, modifier: Modifier = Modifier, isFavorite: Boolean = false, onFavoriteClick: () -> Unit = {}) {
    var expanded by remember(stock.symbol) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(19.dp), modifier = modifier.fillMaxWidth().then(if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize()).clickable { expanded = !expanded }) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuoteRow(stock.symbol, stock.companyName, stock.price, stock.unit, stock.changePct, isFavorite, onFavoriteClick)
            Text(stock.source + " · " + formatBistQuoteTime(stock.updatedAtEpochMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stock.sparkline.size > 1) MiniSparkline(stock.sparkline, Modifier.fillMaxWidth().height(25.dp))
            MotionVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (stock.low != null && stock.high != null) Text("Gün içi " + formatPrice(stock.low) + " – " + formatPrice(stock.high) + " " + stock.unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (stock.volume.isNotBlank()) Text("Hacim " + stock.volume, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun FinancialAssetCard(asset: FinancialAsset, modifier: Modifier = Modifier, isFavorite: Boolean = false, onFavoriteClick: () -> Unit = {}) {
    var expanded by remember(asset.code) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(19.dp), modifier = modifier.fillMaxWidth().then(if (LocalReducedMotion.current) Modifier else Modifier.animateContentSize()).clickable { expanded = !expanded }) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuoteRow(asset.code, asset.name, asset.currentPrice, asset.unit, asset.changePct, isFavorite, onFavoriteClick)
            Text(asset.source + " · " + formatAssetQuoteTime(asset), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (asset.isEstimated && asset.quoteNote.isNotBlank()) Text(asset.quoteNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (asset.sparkline.size > 1) MiniSparkline(asset.sparkline, Modifier.fillMaxWidth().height(25.dp))
            MotionVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (asset.buyPrice != null && asset.sellPrice != null) Text("Alış " + formatPrice(asset.buyPrice) + " ₺ · Satış " + formatPrice(asset.sellPrice) + " ₺", style = MaterialTheme.typography.labelSmall)
                    if (asset.lowPrice != null && asset.highPrice != null) Text("Gün içi " + formatPrice(asset.lowPrice) + " – " + formatPrice(asset.highPrice) + " " + asset.unit, style = MaterialTheme.typography.labelSmall)
                    if (!asset.isEstimated && asset.quoteNote.isNotBlank()) Text(asset.quoteNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun QuoteRow(code: String, name: String, price: Double, unit: String, change: Double?, favorite: Boolean, onFavorite: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(code, style = MaterialTheme.typography.titleSmall)
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(formatPrice(price) + " " + unit, style = MaterialTheme.typography.titleSmall)
            change?.let { Text((if (it >= 0) "+" else "") + formatPrice(it) + "%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (it >= 0) CizgiColor.Green else CizgiColor.Red) }
        }
        IconButton(onFavorite, Modifier.size(27.dp)) { Icon(if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, "Takip listesini değiştir", modifier = Modifier.size(19.dp), tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun MiniSparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val points = values.filter(Float::isFinite)
    if (points.size < 2) return
    val color = if (points.last() >= points.first()) CizgiColor.Green else CizgiColor.Red
    Canvas(modifier) {
        val min = points.minOrNull() ?: return@Canvas
        val max = points.maxOrNull() ?: return@Canvas
        val range = (max - min).takeIf { it > 0f } ?: 1f
        points.mapIndexed { index, value -> Offset(size.width * index / maxOf(1, points.lastIndex), size.height - 3.dp.toPx() - (value - min) / range * (size.height - 6.dp.toPx())) }
            .zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 1.5.dp.toPx()) }
    }
}

private fun formatPrice(value: Double): String = when {
    !value.isFinite() -> "–"
    kotlin.math.abs(value) >= 100_000 -> String.format(MARKET_LOCALE, "%,.0f", value)
    kotlin.math.abs(value) >= 1_000 -> String.format(MARKET_LOCALE, "%,.2f", value)
    kotlin.math.abs(value) >= 1 -> String.format(MARKET_LOCALE, "%.2f", value)
    else -> String.format(MARKET_LOCALE, "%.5f", value)
}

private fun formatBistQuoteTime(epochMs: Long): String {
    if (epochMs <= 0) return "Kaynak saati yok"
    val zone = ZoneId.of("Europe/Istanbul")
    val updated = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
    val now = ZonedDateTime.now(zone)
    val date = updated.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", MARKET_LOCALE))
    val open = now.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && now.toLocalTime() >= LocalTime.of(10, 0) && now.toLocalTime() < LocalTime.of(18, 10)
    return when {
        Duration.between(updated, now).toDays() >= 7 -> "$date · eski veri"
        open && Duration.between(updated, now).toMinutes() > 30 -> "$date · gecikmeli"
        !open -> "$date · piyasa kapalı"
        else -> date
    }
}
private fun formatAssetQuoteTime(asset: FinancialAsset): String {
    if (asset.updatedAtEpochMs <= 0) return "Kaynak saati yok"
    val updated = Instant.ofEpochMilli(asset.updatedAtEpochMs).atZone(ZoneId.of("Europe/Istanbul"))
    val age = (System.currentTimeMillis() - asset.updatedAtEpochMs).coerceAtLeast(0)
    if (asset.source == "TCMB Gösterge Kur") return updated.format(DateTimeFormatter.ofPattern("d MMM", MARKET_LOCALE)) + " · günlük gösterge" + if (age > 5 * 86_400_000L) " · eski veri" else ""
    val date = updated.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", MARKET_LOCALE))
    return date + if (age > if (asset.category == MarketCategory.CRYPTO) 5 * 60_000L else 48 * 60 * 60_000L) " · eski veri" else ""
}
