package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.ConcurrentHashMap

interface BistRetrofitService {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "5d",
    ): ResponseBody
}

/** Keyless Yahoo Finance chart client for the BIST watchlist. */
class BistRetrofitApi(private val httpClient: OkHttpClient) {
    private data class CachedStock(val fetchedAtMs: Long, val stock: BISTStock)

    private val service = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(httpClient)
        .build()
        .create(BistRetrofitService::class.java)
    private val stockCache = ConcurrentHashMap<String, CachedStock>()
    @Volatile var lastErrorMessage: String = ""
        private set

    suspend fun fetchBistStock(symbol: String, force: Boolean = false): BISTStock? = withContext(Dispatchers.IO) {
        val cleanSymbol = symbol.removeSuffix(".IS")
        stockCache[cleanSymbol]?.takeIf { !force && System.currentTimeMillis() - it.fetchedAtMs < CACHE_MS }
            ?.let { return@withContext it.stock }
        try {
            val jsonString = service.getChart("$cleanSymbol.IS").use(ResponseBody::string)
            if (jsonString.isBlank()) {
                lastErrorMessage = "Yahoo Finance BIST verisi için boş yanıt verdi."
                return@withContext stockCache[cleanSymbol]?.stock
            }
            val root = JSONObject(jsonString)
            val result = root.optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
                ?: run {
                    lastErrorMessage = "Yahoo Finance BIST kotasyonunu sağlayıcı yanıtında bulamadı."
                    return@withContext stockCache[cleanSymbol]?.stock
                }
            val meta = result.optJSONObject("meta") ?: run {
                lastErrorMessage = "Yahoo Finance BIST kotasyonu için kaynak zamanı veya fiyat bilgisi yok."
                return@withContext stockCache[cleanSymbol]?.stock
            }
            val price = meta.optDouble("regularMarketPrice", 0.0)
            if (price <= 0.0 || !price.isFinite()) {
                lastErrorMessage = "Yahoo Finance BIST fiyatı sağlayıcı yanıtında yok."
                return@withContext stockCache[cleanSymbol]?.stock
            }

            val previousClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price))
            val dayHigh = meta.optDouble("regularMarketDayHigh", 0.0).takeIf { it > 0.0 && it.isFinite() }
            val dayLow = meta.optDouble("regularMarketDayLow", 0.0).takeIf { it > 0.0 && it.isFinite() }
            val volume = meta.optLong("regularMarketVolume", 0L)
            val sourceTime = meta.optLong("regularMarketTime", 0L).let { if (it > 0L) it * 1000L else 0L }
            val sparkline = chartSparkline(result)
            val stock = BISTStock(
                symbol = cleanSymbol,
                companyName = getCompanyName(cleanSymbol),
                price = round2(price),
                changePct = if (previousClose > 0.0) round2((price - previousClose) / previousClose * 100.0) else null,
                high = dayHigh?.let(::round2),
                low = dayLow?.let(::round2),
                volume = volume.takeIf { it > 0L }?.let(::formatVolume).orEmpty(),
                updatedAtEpochMs = sourceTime,
                source = "Yahoo Finance",
                sparkline = sparkline,
                unit = if (cleanSymbol.startsWith("XU")) "puan" else "₺",
            )
            stockCache[cleanSymbol] = CachedStock(System.currentTimeMillis(), stock)
            stock
        } catch (_: Exception) {
            lastErrorMessage = "Yahoo Finance BIST kotasyonlarına erişilemiyor; son başarılı veriler korunuyor."
            stockCache[cleanSymbol]?.stock
        }
    }

    suspend fun fetchBistWatchlist(force: Boolean = false): List<BISTStock> = withContext(Dispatchers.IO) {
        lastErrorMessage = ""
        val symbols = WATCHLIST
        val semaphore = Semaphore(4)
        coroutineScope {
            symbols.map { symbol -> async { semaphore.withPermit { fetchBistStock(symbol, force) } } }
                .awaitAll()
                .filterNotNull()
        }
    }

    suspend fun fetchGoldOnsUsd(): Double? = fetchFuturesPrice("GC=F", 1000.0)
    suspend fun fetchSilverOnsUsd(): Double? = fetchFuturesPrice("SI=F", 10.0)

    private suspend fun fetchFuturesPrice(symbol: String, minimum: Double): Double? = withContext(Dispatchers.IO) {
        runCatching {
            service.getChart(symbol, interval = "1d", range = "1d").use(ResponseBody::string)
                .let(::JSONObject)
                .optJSONObject("chart")?.optJSONArray("result")?.optJSONObject(0)
                ?.optJSONObject("meta")?.optDouble("regularMarketPrice", 0.0)
                ?.takeIf { it > minimum && it.isFinite() }
        }.getOrNull()
    }

    private fun getCompanyName(symbol: String): String = when (symbol) {
        "XU100" -> "BIST 100 Endeksi"
        "XU030" -> "BIST 30 Endeksi"
        "THYAO" -> "Türk Hava Yolları"
        "ASELS" -> "Aselsan"
        "GARAN" -> "Garanti BBVA"
        "EREGL" -> "Ereğli Demir Çelik"
        "KCHOL" -> "Koç Holding"
        "TUPRS" -> "Tüpraş"
        "BIMAS" -> "BİM Mağazalar"
        "AKBNK" -> "Akbank"
        "SASA" -> "SASA Polyester"
        "SISE" -> "Şişecam"
        "YKBNK" -> "Yapı Kredi"
        "ISCTR" -> "İş Bankası C"
        "FROTO" -> "Ford Otosan"
        "TOASO" -> "Tofaş"
        "ENKAI" -> "Enka İnşaat"
        "ASTOR" -> "Astor Enerji"
        "AEFES" -> "Anadolu Efes"
        "PGSUS" -> "Pegasus"
        else -> "$symbol A.Ş."
    }

    private fun formatVolume(volume: Long): String = when {
        volume >= 1_000_000_000 -> "${round2(volume / 1_000_000_000.0)} Mr adet"
        volume >= 1_000_000 -> "${round2(volume / 1_000_000.0)} Mn adet"
        volume >= 1_000 -> "${round2(volume / 1_000.0)} B adet"
        else -> "$volume adet"
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    private fun chartSparkline(result: JSONObject): List<Float> {
        val closes = result.optJSONObject("indicators")
            ?.optJSONArray("quote")
            ?.optJSONObject(0)
            ?.optJSONArray("close") ?: return emptyList()
        return (0 until closes.length()).mapNotNull { index ->
            closes.optDouble(index, Double.NaN).takeIf { it.isFinite() && it > 0.0 }?.toFloat()
        }.takeLast(12)
    }

    private companion object {
        const val CACHE_MS = 5 * 60_000L
        val WATCHLIST = listOf(
            "XU100", "XU030", "THYAO", "ASELS", "GARAN", "EREGL", "KCHOL", "TUPRS", "BIMAS", "AKBNK",
            "SASA", "SISE", "YKBNK", "ISCTR", "FROTO", "TOASO", "ENKAI", "ASTOR", "AEFES", "PGSUS",
        )
    }
}
