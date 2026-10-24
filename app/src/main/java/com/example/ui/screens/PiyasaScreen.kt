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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CizgiRepository
import com.example.data.remote.BISTStock
import com.example.model.FinancialAsset
import com.example.ui.theme.CizgiColor
import com.example.util.CizgiNotificationHelper

@Composable
fun PiyasaScreen(
    repository: CizgiRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assets by repository.financialAssets.collectAsState()
    val bistStocks by repository.bistStocks.collectAsState()

    var selectedCategory by remember { mutableStateOf("Tümü") }
    val categories = listOf("Tümü", "Borsa (BIST)", "Döviz", "Altın & Emtia", "Kripto")

    // Currency converter state
    var converterAmount by remember { mutableStateOf("100") }
    var selectedConverterAsset by remember { mutableStateOf("USD/TRY") }

    // Fetch live market data on launch
    LaunchedEffect(Unit) {
        repository.refreshMarkets()
    }

    val filteredAssets = remember(assets, selectedCategory) {
        when (selectedCategory) {
            "Döviz" -> assets.filter { it.code.contains("TRY") && !it.code.contains("BTC") && !it.code.contains("ETH") && !it.code.contains("GA") && !it.code.contains("CA") }
            "Altın & Emtia" -> assets.filter { it.code in listOf("GA", "CA", "GUMUS") }
            "Kripto" -> assets.filter { it.code.contains("BTC") || it.code.contains("ETH") }
            "Borsa (BIST)" -> assets.filter { it.code == "BIST100" }
            else -> assets
        }
    }

    val activeConverterAsset = remember(assets, selectedConverterAsset) {
        assets.firstOrNull { it.code == selectedConverterAsset } ?: assets.firstOrNull() ?: FinancialAsset("USD/TRY", "Dolar", "$", 34.32, 0.28, 34.40, 34.20)
    }

    val convertedValue = remember(converterAmount, activeConverterAsset) {
        val amount = converterAmount.toDoubleOrNull() ?: 0.0
        val total = amount * activeConverterAsset.currentPrice
        String.format(java.util.Locale.US, "%,.2f", total)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("piyasa_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Header Bar: Title + Live Market Status + Refresh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "FİNANS VE BORSA MERKEZİ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF16A34A).copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "BIST: CANLI VERİ",
                                color = Color(0xFF16A34A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "Canlı Piyasalar",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(
                        onClick = {
                            repository.refreshMarkets()
                            val usd = assets.firstOrNull { it.code == "USD/TRY" }?.currentPrice ?: 34.32
                            val ga = assets.firstOrNull { it.code == "GA" }?.currentPrice ?: 3020.0
                            CizgiNotificationHelper.sendMarketNotification(
                                context,
                                "Piyasalar Güncellendi",
                                "Dolar: $usd ₺ · Gram Altın: $ga ₺ · BIST 100 takibi devrede"
                            )
                        },
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Piyasayı Yenile",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // 2. Interactive Currency & Gold Converter Box
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CurrencyExchange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "HIZLI DÖVİZ VE ALTIN ÇEVİRİCİ",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        Text(
                            text = "Canlı Kur",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Asset Choice Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf("USD/TRY" to "Dolar", "EUR/TRY" to "Euro", "GA" to "Gram Altın", "CA" to "Çeyrek", "BTC/TRY" to "Bitcoin").forEach { (code, name) ->
                            val isSel = selectedConverterAsset == code
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedConverterAsset = code },
                                label = { Text(name, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }

                    // Input & Result Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = converterAmount,
                            onValueChange = { if (it.all { ch -> ch.isDigit() || ch == '.' }) converterAmount = it },
                            label = { Text("Miktar") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )

                        Text("=", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(56.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$convertedValue ₺",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Category Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { cat ->
                    val isSel = selectedCategory == cat
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedCategory = cat },
                        label = {
                            Text(
                                text = cat,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
            }
        }

        // 4. Borsa İstanbul (BIST 100 & Hisseler) Bölümü
        if (selectedCategory == "Tümü" || selectedCategory == "Borsa (BIST)") {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "BORSA İSTANBUL (BIST)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${bistStocks.size} Hisse Takipte",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (bistStocks.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "BIST hisse verileri güncelleniyor...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            bistStocks.forEach { stock ->
                                BISTStockCard(stock = stock)
                            }
                        }
                    }
                }
            }
        }

        // 5. Döviz, Altın & Kripto Varlık Listesi
        if (selectedCategory != "Borsa (BIST)") {
            item {
                Text(
                    text = "DÖVİZ, ALTIN VE KRİPTO",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(filteredAssets, key = { it.code }) { asset ->
                FinancialAssetCard(asset = asset)
            }
        }
    }
}

@Composable
fun BISTStockCard(
    stock: BISTStock,
    modifier: Modifier = Modifier,
) {
    val isPositive = stock.changePct >= 0
    val trendColor = if (isPositive) Color(0xFF16A34A) else Color(0xFFDC2626)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Symbol & Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stock.symbol.take(4),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column {
                    Text(
                        text = stock.symbol,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stock.companyName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // Price & Percent Change
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${stock.price} ₺",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = trendColor.copy(alpha = 0.14f),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = trendColor,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "${if (isPositive) "+" else ""}${stock.changePct}%",
                            color = trendColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                }

                Text(
                    text = "Hacim: ${stock.volume}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun FinancialAssetCard(
    asset: FinancialAsset,
    modifier: Modifier = Modifier,
) {
    val changePct = asset.changePct
    val isPositive = (changePct ?: 0.0) >= 0.0
    val trendColor = when {
        changePct == null -> MaterialTheme.colorScheme.onSurfaceVariant
        isPositive -> Color(0xFF16A34A)
        else -> Color(0xFFDC2626)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(MaterialTheme.colorScheme.outlineVariant)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Icon + Code + Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = asset.symbol,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column {
                    Text(
                        text = asset.code,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Right: Price & Percent Change
            Column(horizontalAlignment = Alignment.End) {
                val formattedPrice = when {
                    asset.code.contains("BTC") || asset.currentPrice > 100000 -> String.format(java.util.Locale.US, "%,.0f", asset.currentPrice)
                    asset.currentPrice > 1000 -> String.format(java.util.Locale.US, "%,.2f", asset.currentPrice)
                    else -> String.format(java.util.Locale.US, "%.2f", asset.currentPrice)
                }

                Text(
                    text = "$formattedPrice ${asset.unit}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (changePct != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = trendColor.copy(alpha = 0.14f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = trendColor,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "${if (isPositive) "+" else ""}${changePct}%",
                                color = trendColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Değişim verisi yok",
                        color = trendColor,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                    )
                }

                Text(
                    text = if (asset.lowPrice != null && asset.highPrice != null) "G: ${asset.lowPrice} - ${asset.highPrice}" else "Gün içi aralık yok",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
