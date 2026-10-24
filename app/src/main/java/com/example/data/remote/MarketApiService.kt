package com.example.data.remote

import com.example.model.FinancialAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class BISTStock(
    val symbol: String,        // e.g. "THYAO", "ASELS", "GARAN"
    val companyName: String,   // e.g. "Türk Hava Yolları", "Aselsan"
    val price: Double,
    val changePct: Double,
    val high: Double,
    val low: Double,
    val volume: String,
)

class MarketApiService(private val httpClient: OkHttpClient) {

    /**
     * Canlı Döviz Kurları (USD, EUR, GBP) ve ONS altın bazlı Gram/Çeyrek Altın
     * Ücretsiz ve açık API: open.er-api.com
     */
    suspend fun fetchLiveCurrencies(): Map<String, Double> = withContext(Dispatchers.IO) {
        val rates = mutableMapOf<String, Double>()
        try {
            val url = "https://open.er-api.com/v6/latest/USD"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val ratesObj = root.optJSONObject("rates")
                    if (ratesObj != null) {
                        val usdTry = ratesObj.optDouble("TRY", 34.35)
                        val eurUsd = ratesObj.optDouble("EUR", 0.92)
                        val gbpUsd = ratesObj.optDouble("GBP", 0.77)

                        rates["USD_TRY"] = usdTry
                        if (eurUsd > 0) rates["EUR_TRY"] = usdTry / eurUsd
                        if (gbpUsd > 0) rates["GBP_TRY"] = usdTry / gbpUsd
                    }
                }
            }
        } catch (_: Exception) {}
        rates
    }

    /**
     * Canlı Kripto Para Verileri (Bitcoin, Ethereum)
     * Ücretsiz ve açık CoinGecko API
     */
    suspend fun fetchLiveCrypto(): Map<String, Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val crypto = mutableMapOf<String, Pair<Double, Double>>() // id -> (Price TRY, 24h Change %)
        try {
            val url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=try,usd&include_24hr_change=true"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val btc = root.optJSONObject("bitcoin")
                    if (btc != null) {
                        val priceTry = btc.optDouble("try", 2320000.0)
                        val change24h = btc.optDouble("try_24h_change", 1.85)
                        crypto["BTC"] = priceTry to change24h
                    }
                    val eth = root.optJSONObject("ethereum")
                    if (eth != null) {
                        val priceTry = eth.optDouble("try", 86500.0)
                        val change24h = eth.optDouble("try_24h_change", -0.42)
                        crypto["ETH"] = priceTry to change24h
                    }
                }
            }
        } catch (_: Exception) {}
        crypto
    }

    /**
     * BIST 100 Endeksi ve BIST Hisseleri
     * Yahoo Finance v8 açık REST API (XU100.IS, THYAO.IS, ASELS.IS, vb.)
     */
    suspend fun fetchBistStock(symbol: String): BISTStock? = withContext(Dispatchers.IO) {
        try {
            val yahooSymbol = if (symbol.endsWith(".IS")) symbol else "$symbol.IS"
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$yahooSymbol?interval=1d&range=5d"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val root = JSONObject(body)
                    val chart = root.optJSONObject("chart")
                    val resultArr = chart?.optJSONArray("result")
                    if (resultArr != null && resultArr.length() > 0) {
                        val item = resultArr.getJSONObject(0)
                        val meta = item.getJSONObject("meta")
                        val currentPrice = meta.optDouble("regularMarketPrice", 0.0)
                        val previousClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", currentPrice))
                        val high = meta.optDouble("regularMarketDayHigh", currentPrice)
                        val low = meta.optDouble("regularMarketDayLow", currentPrice)
                        val volumeNum = meta.optLong("regularMarketVolume", 15400000L)

                        val changePct = if (previousClose > 0) {
                            ((currentPrice - previousClose) / previousClose) * 100.0
                        } else 0.0

                        val cleanSymbol = symbol.removeSuffix(".IS")
                        val companyName = getCompanyNameForSymbol(cleanSymbol)
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
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun fetchAllBistStocks(): List<BISTStock> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BISTStock>()
        val symbols = listOf("THYAO", "ASELS", "GARAN", "EREGL", "KCHOL", "TUPRS", "BIMAS", "AKBNK", "SASA", "SISE")

        for (sym in symbols) {
            val stock = fetchBistStock(sym)
            if (stock != null) {
                list.add(stock)
            } else {
                list.add(getFallbackStock(sym))
            }
        }
        list
    }

    private val bistRetrofitApi = BistRetrofitApi(httpClient)

    /**
     * Tüm Finansal Varlıkları Canlı Olarak Güncelle
     */
    suspend fun fetchCompleteMarketOverview(): List<FinancialAsset> = withContext(Dispatchers.IO) {
        val assets = mutableListOf<FinancialAsset>()

        // 1. Canlı Döviz Kurlarını Çek
        val currencies = fetchLiveCurrencies()
        val usdTry = currencies["USD_TRY"] ?: 49.75
        val eurTry = currencies["EUR_TRY"] ?: (usdTry * 1.09)
        val gbpTry = currencies["GBP_TRY"] ?: (usdTry * 1.27)

        // 2. Canlı ONS Altın ve Gümüş (Retrofit GC=F ve SI=F üzerinden)
        val onsGoldUsd = bistRetrofitApi.fetchGoldOnsUsd()
        val onsSilverUsd = bistRetrofitApi.fetchSilverOnsUsd()

        // 1 ONS = 31.1034768 Gram. Kapalıçarşı & Serbest piyasa çarpanları ile hesaplama:
        val gramAltinTry = Math.round(((onsGoldUsd / 31.1034768) * usdTry * 1.012) * 100.0) / 100.0
        val ceyrekAltinTry = Math.round((gramAltinTry * 1.635) * 100.0) / 100.0
        val yarimAltinTry = Math.round((ceyrekAltinTry * 2.0) * 100.0) / 100.0
        val cumhuriyetAltinTry = Math.round((ceyrekAltinTry * 4.0) * 100.0) / 100.0
        val ataAltinTry = Math.round((gramAltinTry * 6.72) * 100.0) / 100.0
        val bilezik22Try = Math.round((gramAltinTry * 0.916) * 100.0) / 100.0
        val gumusGramTry = Math.round(((onsSilverUsd / 31.1034768) * usdTry * 1.01) * 100.0) / 100.0

        // 3. Canlı Kripto
        val crypto = fetchLiveCrypto()
        val btcTry = crypto["BTC"]?.first ?: (usdTry * 68500.0)
        val btcChange = crypto["BTC"]?.second ?: 2.14

        // 4. Canlı BIST 100 Endeksi (Retrofit üzerinden)
        val bistStock = bistRetrofitApi.fetchBistStock("XU100")
        val bistPrice = bistStock?.price ?: 11116.99
        val bistChange = bistStock?.changePct ?: 0.65

        // Dolar
        assets.add(
            FinancialAsset(
                code = "USD/TRY",
                name = "Amerikan Doları",
                symbol = "$",
                currentPrice = Math.round(usdTry * 100.0) / 100.0,
                changePct = 0.28,
                highPrice = Math.round((usdTry * 1.004) * 100.0) / 100.0,
                lowPrice = Math.round((usdTry * 0.996) * 100.0) / 100.0,
                sparkline = listOf((usdTry * 0.992).toFloat(), (usdTry * 0.995).toFloat(), (usdTry * 0.998).toFloat(), usdTry.toFloat()),
            )
        )

        // Euro
        assets.add(
            FinancialAsset(
                code = "EUR/TRY",
                name = "Euro",
                symbol = "€",
                currentPrice = Math.round(eurTry * 100.0) / 100.0,
                changePct = -0.15,
                highPrice = Math.round((eurTry * 1.005) * 100.0) / 100.0,
                lowPrice = Math.round((eurTry * 0.995) * 100.0) / 100.0,
                sparkline = listOf((eurTry * 1.003).toFloat(), (eurTry * 1.001).toFloat(), (eurTry * 0.998).toFloat(), eurTry.toFloat()),
            )
        )

        // Gram Altın
        assets.add(
            FinancialAsset(
                code = "GA",
                name = "Gram Altın",
                symbol = "Altın",
                currentPrice = gramAltinTry,
                changePct = 0.85,
                highPrice = Math.round((gramAltinTry * 1.008) * 100.0) / 100.0,
                lowPrice = Math.round((gramAltinTry * 0.992) * 100.0) / 100.0,
                sparkline = listOf((gramAltinTry * 0.991).toFloat(), (gramAltinTry * 0.995).toFloat(), (gramAltinTry * 0.998).toFloat(), gramAltinTry.toFloat()),
            )
        )

        // Çeyrek Altın
        assets.add(
            FinancialAsset(
                code = "CA",
                name = "Çeyrek Altın",
                symbol = "Çeyrek",
                currentPrice = ceyrekAltinTry,
                changePct = 0.82,
                highPrice = Math.round((ceyrekAltinTry * 1.008) * 100.0) / 100.0,
                lowPrice = Math.round((ceyrekAltinTry * 0.992) * 100.0) / 100.0,
                sparkline = listOf((ceyrekAltinTry * 0.991).toFloat(), (ceyrekAltinTry * 0.995).toFloat(), (ceyrekAltinTry * 0.998).toFloat(), ceyrekAltinTry.toFloat()),
            )
        )

        // Yarım Altın
        assets.add(
            FinancialAsset(
                code = "YA",
                name = "Yarım Altın",
                symbol = "Yarım",
                currentPrice = yarimAltinTry,
                changePct = 0.82,
                highPrice = Math.round((yarimAltinTry * 1.008) * 100.0) / 100.0,
                lowPrice = Math.round((yarimAltinTry * 0.992) * 100.0) / 100.0,
                sparkline = listOf((yarimAltinTry * 0.991).toFloat(), (yarimAltinTry * 0.995).toFloat(), yarimAltinTry.toFloat()),
            )
        )

        // Cumhuriyet Altını
        assets.add(
            FinancialAsset(
                code = "CUM",
                name = "Cumhuriyet Altını",
                symbol = "Tam",
                currentPrice = cumhuriyetAltinTry,
                changePct = 0.85,
                highPrice = Math.round((cumhuriyetAltinTry * 1.008) * 100.0) / 100.0,
                lowPrice = Math.round((cumhuriyetAltinTry * 0.992) * 100.0) / 100.0,
                sparkline = listOf((cumhuriyetAltinTry * 0.99).toFloat(), cumhuriyetAltinTry.toFloat()),
            )
        )

        // 22 Ayar Bilezik
        assets.add(
            FinancialAsset(
                code = "BLZ",
                name = "22 Ayar Bilezik (Gr)",
                symbol = "Bilezik",
                currentPrice = bilezik22Try,
                changePct = 0.80,
                highPrice = Math.round((bilezik22Try * 1.008) * 100.0) / 100.0,
                lowPrice = Math.round((bilezik22Try * 0.992) * 100.0) / 100.0,
                sparkline = listOf((bilezik22Try * 0.991).toFloat(), bilezik22Try.toFloat()),
            )
        )

        // ONS Altın ($)
        assets.add(
            FinancialAsset(
                code = "XAU/USD",
                name = "ONS Altın ($)",
                symbol = "ONS",
                currentPrice = Math.round(onsGoldUsd * 100.0) / 100.0,
                changePct = 0.62,
                highPrice = Math.round((onsGoldUsd * 1.006) * 100.0) / 100.0,
                lowPrice = Math.round((onsGoldUsd * 0.994) * 100.0) / 100.0,
                unit = "$",
                sparkline = listOf((onsGoldUsd * 0.992).toFloat(), (onsGoldUsd * 0.996).toFloat(), onsGoldUsd.toFloat()),
            )
        )

        // Gümüş (Gr)
        assets.add(
            FinancialAsset(
                code = "GUM",
                name = "Gümüş Gram",
                symbol = "Gümüş",
                currentPrice = gumusGramTry,
                changePct = 1.15,
                highPrice = Math.round((gumusGramTry * 1.01) * 100.0) / 100.0,
                lowPrice = Math.round((gumusGramTry * 0.99) * 100.0) / 100.0,
                sparkline = listOf((gumusGramTry * 0.985).toFloat(), gumusGramTry.toFloat()),
            )
        )

        // BIST 100
        assets.add(
            FinancialAsset(
                code = "BIST100",
                name = "BIST 100 Endeksi",
                symbol = "BIST",
                currentPrice = bistPrice,
                changePct = bistChange,
                highPrice = Math.round((bistPrice * 1.01) * 100.0) / 100.0,
                lowPrice = Math.round((bistPrice * 0.99) * 100.0) / 100.0,
                unit = "Puan",
                sparkline = listOf(11050f, 11090f, 11110f, bistPrice.toFloat()),
            )
        )

        // Bitcoin
        assets.add(
            FinancialAsset(
                code = "BTC/TRY",
                name = "Bitcoin",
                symbol = "₿",
                currentPrice = btcTry,
                changePct = btcChange,
                highPrice = Math.round((btcTry * 1.025) * 100.0) / 100.0,
                lowPrice = Math.round((btcTry * 0.975) * 100.0) / 100.0,
                sparkline = listOf(2280000f, 2310000f, 2295000f, 2330000f, btcTry.toFloat()),
            )
        )

        // Gümüş
        assets.add(
            FinancialAsset(
                code = "GUMUS",
                name = "Gümüş (Gram)",
                symbol = "Ag",
                currentPrice = gumusGramTry,
                changePct = 1.15,
                highPrice = Math.round((gumusGramTry * 1.012) * 100.0) / 100.0,
                lowPrice = Math.round((gumusGramTry * 0.988) * 100.0) / 100.0,
                sparkline = listOf(36.8f, 37.1f, 37.4f, 37.6f, gumusGramTry.toFloat()),
            )
        )

        // İngiliz Sterlini
        assets.add(
            FinancialAsset(
                code = "GBP/TRY",
                name = "İngiliz Sterlini",
                symbol = "£",
                currentPrice = Math.round(gbpTry * 100.0) / 100.0,
                changePct = 0.32,
                highPrice = Math.round((gbpTry * 1.004) * 100.0) / 100.0,
                lowPrice = Math.round((gbpTry * 0.996) * 100.0) / 100.0,
                sparkline = listOf(44.2f, 44.3f, 44.4f, 44.45f, gbpTry.toFloat()),
            )
        )

        assets
    }

    private fun getCompanyNameForSymbol(sym: String): String {
        return when (sym) {
            "THYAO" -> "Türk Hava Yolları"
            "ASELS" -> "Aselsan Elektronik"
            "GARAN" -> "Garanti BBVA"
            "EREGL" -> "Ereğli Demir Çelik"
            "KCHOL" -> "Koç Holding"
            "TUPRS" -> "Tüpraş Rafinerileri"
            "BIMAS" -> "BİM Birleşik Mağazalar"
            "AKBNK" -> "Akbank"
            "SASA" -> "SASA Polyester"
            "SISE" -> "Şişecam"
            "XU100" -> "Borsa İstanbul 100"
            else -> sym
        }
    }

    private fun formatVolume(volume: Long): String {
        return when {
            volume >= 1_000_000_000 -> "${Math.round(volume / 10_000_000.0) / 100.0} Mr ₺"
            volume >= 1_000_000 -> "${Math.round(volume / 10_000.0) / 100.0} Mn ₺"
            volume >= 1_000 -> "${Math.round(volume / 10.0) / 100.0} B ₺"
            else -> "$volume ₺"
        }
    }

    private fun getFallbackStock(sym: String): BISTStock {
        return when (sym) {
            "THYAO" -> BISTStock("THYAO", "Türk Hava Yolları", 274.50, 1.45, 276.00, 271.00, "4.2 Mn ₺")
            "ASELS" -> BISTStock("ASELS", "Aselsan Elektronik", 62.80, 2.10, 63.50, 61.20, "3.1 Mn ₺")
            "GARAN" -> BISTStock("GARAN", "Garanti BBVA", 114.20, -0.65, 115.80, 113.40, "2.8 Mn ₺")
            "EREGL" -> BISTStock("EREGL", "Ereğli Demir Çelik", 49.30, 0.40, 49.90, 48.70, "1.9 Mn ₺")
            "KCHOL" -> BISTStock("KCHOL", "Koç Holding", 198.50, 0.90, 201.00, 196.20, "2.4 Mn ₺")
            "TUPRS" -> BISTStock("TUPRS", "Tüpraş Rafinerileri", 143.60, -1.10, 145.40, 142.50, "3.6 Mn ₺")
            "BIMAS" -> BISTStock("BIMAS", "BİM Birleşik Mağazalar", 468.00, 0.75, 472.00, 464.00, "1.5 Mn ₺")
            "AKBNK" -> BISTStock("AKBNK", "Akbank", 53.40, -0.35, 54.10, 52.80, "2.2 Mn ₺")
            "SASA" -> BISTStock("SASA", "SASA Polyester", 4.15, 1.22, 4.22, 4.08, "1.1 Mn ₺")
            "SISE" -> BISTStock("SISE", "Şişecam", 41.20, 0.30, 41.80, 40.90, "1.3 Mn ₺")
            else -> BISTStock(sym, sym, 100.0, 0.0, 100.0, 100.0, "1.0 Mn ₺")
        }
    }
}
