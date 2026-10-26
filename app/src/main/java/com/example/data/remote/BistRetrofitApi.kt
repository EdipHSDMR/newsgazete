package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * BIST ve Finans verilerini gerçek zamanlı çekebilecek Retrofit API Servis Arayüzü
 */
interface BistRetrofitService {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("interval") interval: String = "1d",
        @Query("range") range: String = "5d",
    ): ResponseBody
}

/**
 * Retrofit Tabanlı BIST & Emtia Takip Servisi
 */
class BistRetrofitApi(private val httpClient: OkHttpClient) {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://query1.finance.yahoo.com/")
        .client(httpClient)
        .build()

    private val service: BistRetrofitService = retrofit.create(BistRetrofitService::class.java)

    /**
     * BIST 100 hissesini veya endeksini Retrofit servisi üzerinden çeker
     */
    suspend fun fetchBistStock(symbol: String): BISTStock? = withContext(Dispatchers.IO) {
        try {
            val yahooSymbol = if (symbol.endsWith(".IS")) symbol else "$symbol.IS"
            val responseBody = service.getChart(yahooSymbol)
            val jsonStr = responseBody.string()
            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                val chart = root.optJSONObject("chart")
                val resultArr = chart?.optJSONArray("result")
                if (resultArr != null && resultArr.length() > 0) {
                    val item = resultArr.getJSONObject(0)
                    val meta = item.getJSONObject("meta")
                    val currentPrice = meta.optDouble("regularMarketPrice", 0.0)
                    if (currentPrice <= 0.0) return@withContext null
                    val previousClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", currentPrice))
                    val high = meta.optDouble("regularMarketDayHigh", currentPrice)
                    val low = meta.optDouble("regularMarketDayLow", currentPrice)
                    val volumeNum = meta.optLong("regularMarketVolume", 0L)

                    val changePct = if (previousClose > 0) {
                        ((currentPrice - previousClose) / previousClose) * 100.0
                    } else 0.0

                    val cleanSymbol = symbol.removeSuffix(".IS")
                    val companyName = getCompanyName(cleanSymbol)
                    val formattedVolume = formatVolume(volumeNum)

                    return@withContext BISTStock(
                        symbol = cleanSymbol,
                        companyName = companyName,
                        price = Math.round(currentPrice * 100.0) / 100.0,
                        changePct = Math.round(changePct * 100.0) / 100.0,
                        high = Math.round(high * 100.0) / 100.0,
                        low = Math.round(low * 100.0) / 100.0,
                        volume = formattedVolume,
                    )
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Gerçek ONS Altın Fiyatını (USD) Retrofit üzerinden GC=F sembolü ile çeker
     */
    suspend fun fetchGoldOnsUsd(): Double? = withContext(Dispatchers.IO) {
        try {
            val responseBody = service.getChart("GC=F", interval = "1d", range = "1d")
            val jsonStr = responseBody.string()
            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                val chart = root.optJSONObject("chart")
                val resultArr = chart?.optJSONArray("result")
                if (resultArr != null && resultArr.length() > 0) {
                    val meta = resultArr.getJSONObject(0).getJSONObject("meta")
                    val price = meta.optDouble("regularMarketPrice", 0.0)
                    if (price > 1000.0) return@withContext price
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Gerçek ONS Gümüş Fiyatını (USD) Retrofit üzerinden SI=F sembolü ile çeker
     */
    suspend fun fetchSilverOnsUsd(): Double? = withContext(Dispatchers.IO) {
        try {
            val responseBody = service.getChart("SI=F", interval = "1d", range = "1d")
            val jsonStr = responseBody.string()
            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                val chart = root.optJSONObject("chart")
                val resultArr = chart?.optJSONArray("result")
                if (resultArr != null && resultArr.length() > 0) {
                    val meta = resultArr.getJSONObject(0).getJSONObject("meta")
                    val price = meta.optDouble("regularMarketPrice", 0.0)
                    if (price > 10.0) return@withContext price
                }
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Ana Ekran ve Takip Listesi için BIST 100 ve Popüler Hisseler Listesi
     */
    suspend fun fetchBistWatchlist(): List<BISTStock> = withContext(Dispatchers.IO) {
        val symbols = listOf("XU100", "THYAO", "ASELS", "GARAN", "EREGL", "KCHOL", "TUPRS", "BIMAS", "AKBNK", "SASA", "SISE")
        val result = mutableListOf<BISTStock>()

        for (sym in symbols) {
            val stock = fetchBistStock(sym)
            if (stock != null) {
                result.add(stock)
            }
        }
        result
    }

    private fun getCompanyName(symbol: String): String {
        return when (symbol) {
            "XU100" -> "BIST 100 Endeksi"
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
            else -> "$symbol A.Ş."
        }
    }

    private fun formatVolume(volume: Long): String {
        return when {
            volume >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1fB", volume / 1_000_000_000.0)
            volume >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", volume / 1_000_000.0)
            volume >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", volume / 1_000.0)
            else -> volume.toString()
        }
    }

}

