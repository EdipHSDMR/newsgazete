package com.example.data.remote

import android.util.Xml
import com.example.model.FinancialAsset
import com.example.model.MarketCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

data class BISTStock(
    val symbol: String,
    val companyName: String,
    val price: Double,
    val changePct: Double?,
    val high: Double?,
    val low: Double?,
    val volume: String,
    val updatedAtEpochMs: Long = 0L,
    val source: String = "Yahoo Finance",
    val sparkline: List<Float> = emptyList(),
    val unit: String = "₺",
)

/** Market feeds are public, keyless sources; quotes retain their provider timestamp and caveats. */
class MarketApiService(private val httpClient: OkHttpClient) {
    private data class CachedAssets(val fetchedAtMs: Long, val assets: List<FinancialAsset>)

    private val yahooCache = ConcurrentHashMap<String, Pair<Long, FinancialAsset>>()
    private val sourceErrors = ConcurrentHashMap<String, String>()
    @Volatile private var currencyCache: CachedAssets? = null
    @Volatile private var cryptoCache: CachedAssets? = null

    fun availabilityWarnings(): List<String> = buildList {
        if (sourceErrors.containsKey("TCMB")) add(sourceErrors.getValue("TCMB"))
        if (sourceErrors.containsKey("CoinGecko")) add(sourceErrors.getValue("CoinGecko"))
        val unavailableCommodities = COMMODITIES.count { sourceErrors.containsKey(it.symbol) }
        if (unavailableCommodities > 0) {
            add("Yahoo Finance $unavailableCommodities altın/emtia kotasyonunu alamadı; son başarılı fiyatlar korunuyor.")
        }
    }

    suspend fun fetchCompleteMarketOverview(force: Boolean = false): List<FinancialAsset> = withContext(Dispatchers.IO) {
        coroutineScope {
            val currenciesJob = async { fetchCurrencies(force) }
            val cryptoJob = async { fetchCrypto(force) }
            val currencies = currenciesJob.await()
            val commoditiesJob = async { fetchCommodities(force, currencies) }
            currencies + commoditiesJob.await() + cryptoJob.await()
        }
    }

    private suspend fun fetchCurrencies(force: Boolean): List<FinancialAsset> {
        currencyCache?.takeIf { !force && System.currentTimeMillis() - it.fetchedAtMs < CURRENCY_CACHE_MS }
            ?.let { return it.assets }
        return try {
            val request = Request.Builder()
                .url(TCMB_URL)
                .header("Accept", "application/xml,text/xml")
                .header("User-Agent", USER_AGENT)
                .build()
            val xml = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("TCMB HTTP ${response.code}")
                response.body?.string().orEmpty().ifBlank { error("TCMB boş yanıt verdi") }
            }
            val assets = parseTcmbCurrencies(xml)
            if (assets.isNotEmpty()) {
                currencyCache = CachedAssets(System.currentTimeMillis(), assets)
                sourceErrors.remove("TCMB")
            } else {
                sourceErrors["TCMB"] = "TCMB yanıtında desteklenen döviz kurları bulunamadı."
            }
            assets.ifEmpty { currencyCache?.assets.orEmpty() }
        } catch (_: Exception) {
            sourceErrors["TCMB"] = "TCMB döviz verisine erişilemiyor; son başarılı veri korunuyor."
            currencyCache?.assets.orEmpty()
        }
    }

    private fun parseTcmbCurrencies(xml: String): List<FinancialAsset> {
        val parser = Xml.newPullParser()
        parser.setInput(xml.reader())
        var event = parser.eventType
        var currentCode = ""
        var currentName = ""
        var unit = 1.0
        var buying: Double? = null
        var selling: Double? = null
        var previousBuying: Double? = null
        var previousSelling: Double? = null
        var tag = ""
        var dateEpochMs = 0L
        val output = mutableListOf<FinancialAsset>()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "Tarih_Date" -> dateEpochMs = parseTcmbDate(parser.getAttributeValue(null, "Date").orEmpty())
                        "Currency" -> {
                            currentCode = parser.getAttributeValue(null, "CurrencyCode").orEmpty()
                            currentName = parser.getAttributeValue(null, "CurrencyName").orEmpty()
                            unit = parser.getAttributeValue(null, "Unit")?.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
                            buying = null
                            selling = null
                            previousBuying = null
                            previousSelling = null
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text.trim().replace(',', '.').toDoubleOrNull()
                    if (value != null) when (tag) {
                        "ForexBuying" -> buying = value / unit
                        "ForexSelling" -> selling = value / unit
                        "PreviousDayForexBuying" -> previousBuying = value / unit
                        "PreviousDayForexSelling" -> previousSelling = value / unit
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "Currency") {
                    val buy = buying?.takeIf { it > 0.0 && it.isFinite() }
                    val sell = selling?.takeIf { it > 0.0 && it.isFinite() }
                    val displayRate = when {
                        buy != null && sell != null -> (buy + sell) / 2.0
                        sell != null -> sell
                        else -> buy
                    }
                    val previousRate = when {
                        previousBuying != null && previousSelling != null -> (previousBuying!! + previousSelling!!) / 2.0
                        previousSelling != null -> previousSelling
                        else -> previousBuying
                    }
                    if (currentCode in SUPPORTED_CURRENCIES && displayRate != null) {
                        output += FinancialAsset(
                            code = "$currentCode/TRY",
                            name = currentName.ifBlank { currencyName(currentCode) },
                            symbol = currencySymbol(currentCode),
                            currentPrice = displayRate,
                            changePct = previousRate?.takeIf { it > 0.0 }?.let { ((displayRate - it) / it) * 100.0 },
                            unit = "₺",
                            category = MarketCategory.CURRENCY,
                            source = "TCMB Gösterge Kur",
                            updatedAtEpochMs = dateEpochMs,
                            quoteNote = "TCMB gösterge kuru; alış/satış fiyatı bankaya göre değişebilir.",
                            buyPrice = buy,
                            sellPrice = sell,
                            changePeriodLabel = "Günlük",
                        )
                    }
                    currentCode = ""
                    currentName = ""
                    tag = ""
                }
            }
            event = parser.next()
        }
        return output
    }

    private suspend fun fetchCrypto(force: Boolean): List<FinancialAsset> {
        cryptoCache?.takeIf { !force && System.currentTimeMillis() - it.fetchedAtMs < CRYPTO_CACHE_MS }
            ?.let { return it.assets }
        return try {
            val ids = CRYPTO_ASSETS.joinToString(",") { it.id }
            val request = Request.Builder()
                .url("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=try&include_24hr_change=true&include_last_updated_at=true")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            val json = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("CoinGecko HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
            val assets = CRYPTO_ASSETS.mapNotNull { definition ->
                val quote = json.optJSONObject(definition.id) ?: return@mapNotNull null
                val price = quote.optDouble("try", 0.0)
                if (price <= 0.0 || !price.isFinite()) return@mapNotNull null
                val change = quote.optDouble("try_24h_change", Double.NaN).takeIf { it.isFinite() }
                val updated = quote.optLong("last_updated_at", 0L).let { if (it > 0L) it * 1000L else 0L }
                FinancialAsset(
                    code = "${definition.symbol}/TRY",
                    name = definition.name,
                    symbol = definition.mark,
                    currentPrice = price,
                    changePct = change,
                    unit = "₺",
                    category = MarketCategory.CRYPTO,
                    source = "CoinGecko",
                    updatedAtEpochMs = updated,
                    quoteNote = "24 saatlik değişim. Fiyatlar gösterge niteliğindedir.",
                    changePeriodLabel = "24 sa.",
                )
            }
            if (assets.isNotEmpty()) {
                cryptoCache = CachedAssets(System.currentTimeMillis(), assets)
                sourceErrors.remove("CoinGecko")
            } else {
                sourceErrors["CoinGecko"] = "CoinGecko yanıtında izlenen kripto varlık bulunamadı."
            }
            assets.ifEmpty { cryptoCache?.assets.orEmpty() }
        } catch (_: Exception) {
            sourceErrors["CoinGecko"] = "CoinGecko verisine erişilemiyor; son başarılı veri korunuyor."
            cryptoCache?.assets.orEmpty()
        }
    }

    private suspend fun fetchCommodities(force: Boolean, currencies: List<FinancialAsset>): List<FinancialAsset> = coroutineScope {
        val semaphore = Semaphore(4)
        val fetched = COMMODITIES.map { definition ->
            async(Dispatchers.IO) { semaphore.withPermit { fetchYahooAsset(definition, force) } }
        }.awaitAll().filterNotNull().associateBy { it.code }.toMutableMap()

        val gold = fetched["XAU/USD"]
        val usdTry = currencies.firstOrNull { it.code == "USD/TRY" }
        if (gold != null && usdTry != null) {
            val gramPrice = gold.currentPrice / TROY_OUNCE_GRAMS * usdTry.currentPrice
            if (gramPrice.isFinite() && gramPrice > 0.0) {
                fetched["GRAM-ALTIN-REF"] = FinancialAsset(
                    code = "GRAM-ALTIN-REF",
                    name = "Gram altın (hesaplanan)",
                    symbol = "Au",
                    currentPrice = gramPrice,
                    unit = "₺/gr",
                    category = MarketCategory.METAL,
                    source = "Yahoo Finance + TCMB",
                    updatedAtEpochMs = listOf(gold.updatedAtEpochMs, usdTry.updatedAtEpochMs).filter { it > 0L }.minOrNull() ?: 0L,
                    quoteNote = "Ons altın ve TCMB kurundan hesaplanan referans; kuyumcu satış fiyatı değildir.",
                    isEstimated = true,
                )
            }
        }
        fetched.values.toList()
    }

    private suspend fun fetchYahooAsset(definition: CommodityDefinition, force: Boolean): FinancialAsset? {
        yahooCache[definition.symbol]?.takeIf { !force && System.currentTimeMillis() - it.first < YAHOO_CACHE_MS }
            ?.let { return it.second }
        return try {
            val request = Request.Builder()
                .url("https://query1.finance.yahoo.com/v8/finance/chart/${definition.symbol}?interval=1d&range=5d")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
            val json = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Yahoo Finance HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
            }
            val result = json.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
                ?: return null.also { sourceErrors[definition.symbol] = "Yahoo Finance ${definition.name} verisi bulunamadı." }
            val meta = result.optJSONObject("meta")
                ?: return null.also { sourceErrors[definition.symbol] = "Yahoo Finance ${definition.name} yanıtı eksik." }
            val price = meta.optDouble("regularMarketPrice", 0.0)
            if (price <= 0.0 || !price.isFinite()) {
                sourceErrors[definition.symbol] = "Yahoo Finance ${definition.name} fiyatı sağlayıcı yanıtında yok."
                return null
            }
            val previous = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", 0.0))
            val high = meta.optDouble("regularMarketDayHigh", 0.0).takeIf { it > 0.0 && it.isFinite() }
            val low = meta.optDouble("regularMarketDayLow", 0.0).takeIf { it > 0.0 && it.isFinite() }
            val change = if (previous > 0.0) ((price - previous) / previous) * 100.0 else null
            val updated = meta.optLong("regularMarketTime", 0L).let { if (it > 0L) it * 1000L else 0L }
            val sparkline = chartSparkline(result)
            val quoteNote = when (meta.optString("marketState").uppercase(Locale.ROOT)) {
                "REGULAR" -> "Kaynak piyasayı açık bildiriyor; gecikme süresi belirtilmiyor."
                "CLOSED" -> "Kaynak piyasayı kapalı bildiriyor; son kotasyon gösteriliyor."
                "PRE", "PREPRE" -> "Kaynak piyasa öncesi işlem durumunu bildiriyor."
                "POST", "POSTPOST" -> "Kaynak piyasa sonrası işlem durumunu bildiriyor."
                else -> "Kaynağın bildirdiği son piyasa saati; gecikme süresi belirtilmiyor."
            }
            FinancialAsset(
                code = definition.code,
                name = definition.name,
                symbol = definition.mark,
                currentPrice = price,
                changePct = change,
                highPrice = high,
                lowPrice = low,
                unit = definition.unit,
                category = definition.category,
                source = "Yahoo Finance",
                updatedAtEpochMs = updated,
                quoteNote = quoteNote,
                changePeriodLabel = "Günlük",
                sparkline = sparkline,
            ).also {
                yahooCache[definition.symbol] = System.currentTimeMillis() to it
                sourceErrors.remove(definition.symbol)
            }
        } catch (_: Exception) {
            sourceErrors[definition.symbol] = "Yahoo Finance ${definition.name} verisine erişilemiyor; son başarılı veri korunuyor."
            yahooCache[definition.symbol]?.second
        }
    }

    private fun parseTcmbDate(value: String): Long = runCatching {
        SimpleDateFormat("dd.MM.yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Europe/Istanbul")
        }.parse(value)?.time ?: 0L
    }.getOrDefault(0L)

    private fun chartSparkline(result: JSONObject): List<Float> {
        val closes = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?.optJSONArray("close") ?: return emptyList()
        return (0 until closes.length()).mapNotNull { index ->
            closes.optDouble(index, Double.NaN).takeIf { it.isFinite() && it > 0.0 }?.toFloat()
        }.takeLast(12)
    }

    private fun currencyName(code: String): String = when (code) {
        "USD" -> "ABD Doları"
        "EUR" -> "Euro"
        "GBP" -> "İngiliz Sterlini"
        "CHF" -> "İsviçre Frangı"
        "JPY" -> "Japon Yeni"
        "CAD" -> "Kanada Doları"
        "AUD" -> "Avustralya Doları"
        "CNY" -> "Çin Yuanı"
        "AED" -> "BAE Dirhemi"
        "SAR" -> "Suudi Arabistan Riyali"
        else -> code
    }

    private fun currencySymbol(code: String): String = when (code) {
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "CHF" -> "Fr"
        "JPY" -> "¥"
        "CNY" -> "¥"
        "AED" -> "د.إ"
        "SAR" -> "﷼"
        else -> code
    }

    private data class CryptoDefinition(val id: String, val symbol: String, val name: String, val mark: String)
    private data class CommodityDefinition(
        val symbol: String,
        val code: String,
        val name: String,
        val mark: String,
        val unit: String,
        val category: MarketCategory,
    )

    private companion object {
        const val TCMB_URL = "https://www.tcmb.gov.tr/kurlar/today.xml"
        const val USER_AGENT = "Newsgazete/1.0 (market-data; Android)"
        const val CURRENCY_CACHE_MS = 4 * 60 * 60_000L
        const val CRYPTO_CACHE_MS = 60_000L
        const val YAHOO_CACHE_MS = 5 * 60_000L
        const val TROY_OUNCE_GRAMS = 31.1034768
        val SUPPORTED_CURRENCIES = setOf("USD", "EUR", "GBP", "CHF", "JPY", "CAD", "AUD", "CNY", "AED", "SAR")
        val CRYPTO_ASSETS = listOf(
            CryptoDefinition("bitcoin", "BTC", "Bitcoin", "₿"),
            CryptoDefinition("ethereum", "ETH", "Ethereum", "Ξ"),
            CryptoDefinition("tether", "USDT", "Tether", "₮"),
            CryptoDefinition("binancecoin", "BNB", "BNB", "◆"),
            CryptoDefinition("solana", "SOL", "Solana", "◎"),
            CryptoDefinition("usd-coin", "USDC", "USD Coin", "$"),
            CryptoDefinition("ripple", "XRP", "XRP", "✕"),
            CryptoDefinition("dogecoin", "DOGE", "Dogecoin", "Ð"),
            CryptoDefinition("tron", "TRX", "TRON", "T"),
            CryptoDefinition("cardano", "ADA", "Cardano", "₳"),
            CryptoDefinition("avalanche-2", "AVAX", "Avalanche", "▲"),
            CryptoDefinition("chainlink", "LINK", "Chainlink", "⬡"),
            CryptoDefinition("toncoin", "TON", "Toncoin", "💎"),
            CryptoDefinition("shiba-inu", "SHIB", "Shiba Inu", "🐕"),
            CryptoDefinition("polkadot", "DOT", "Polkadot", "●"),
        )
        val COMMODITIES = listOf(
            CommodityDefinition("GC=F", "XAU/USD", "Ons altın", "Au", "$/ons", MarketCategory.METAL),
            CommodityDefinition("SI=F", "XAG/USD", "Ons gümüş", "Ag", "$/ons", MarketCategory.METAL),
            CommodityDefinition("BZ=F", "Brent petrol", "Brent petrol", "Brent", "$/varil", MarketCategory.COMMODITY),
            CommodityDefinition("CL=F", "WTI petrol", "WTI petrol", "WTI", "$/varil", MarketCategory.COMMODITY),
            CommodityDefinition("HG=F", "Bakır", "Bakır vadeli", "Cu", "$/lb", MarketCategory.COMMODITY),
            CommodityDefinition("NG=F", "Doğal gaz", "Doğal gaz vadeli", "NG", "$/MMBtu", MarketCategory.COMMODITY),
        )
    }
}
