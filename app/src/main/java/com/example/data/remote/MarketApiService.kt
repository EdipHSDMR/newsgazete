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
                        val usdTry = ratesObj.optDouble("TRY", 0.0)
                        val eurUsd = ratesObj.optDouble("EUR", 0.0)
                        val gbpUsd = ratesObj.optDouble("GBP", 0.0)

                        if (usdTry > 0.0 && usdTry.isFinite()) rates["USD_TRY"] = usdTry
                        if (usdTry > 0.0 && eurUsd > 0.0 && eurUsd.isFinite()) rates["EUR_TRY"] = usdTry / eurUsd
                        if (usdTry > 0.0 && gbpUsd > 0.0 && gbpUsd.isFinite()) rates["GBP_TRY"] = usdTry / gbpUsd
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
                        val priceTry = btc.optDouble("try", 0.0)
                        if (priceTry > 0.0 && priceTry.isFinite()) {
                            val change24h = btc.optDouble("try_24h_change", 0.0)
                            crypto["BTC"] = priceTry to change24h
                        }
                    }
                    val eth = root.optJSONObject("ethereum")
                    if (eth != null) {
                        val priceTry = eth.optDouble("try", 0.0)
                        if (priceTry > 0.0 && priceTry.isFinite()) {
                            val change24h = eth.optDouble("try_24h_change", 0.0)
                            crypto["ETH"] = priceTry to change24h
                        }
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
                        if (currentPrice <= 0.0) return@withContext null
                        val previousClose = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", currentPrice))
                        val high = meta.optDouble("regularMarketDayHigh", currentPrice)
                        val low = meta.optDouble("regularMarketDayLow", currentPrice)
                        val volumeNum = meta.optLong("regularMarketVolume", 0L)

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

        fun addAsset(
            code: String,
            name: String,
            symbol: String,
            price: Double,
            changePct: Double = 0.0,
            unit: String = "₺",
            high: Double = price,
            low: Double = price,
        ) {
            if (price > 0.0 && price.isFinite()) {
                assets.add(
                    FinancialAsset(
                        code = code,
                        name = name,
                        symbol = symbol,
                        currentPrice = price,
                        changePct = changePct,
                        highPrice = high,
                        lowPrice = low,
                        unit = unit,
                    )
                )
            }
        }

        val currencies = fetchLiveCurrencies()
        val usdTry = currencies["USD_TRY"]
        val eurTry = currencies["EUR_TRY"]
        val gbpTry = currencies["GBP_TRY"]

        usdTry?.let { addAsset("USD/TRY", "Amerikan Doları", "$", it) }
        eurTry?.let { addAsset("EUR/TRY", "Euro", "€", it) }
        gbpTry?.let { addAsset("GBP/TRY", "İngiliz Sterlini", "£", it) }

        val goldUsd = bistRetrofitApi.fetchGoldOnsUsd()
        val silverUsd = bistRetrofitApi.fetchSilverOnsUsd()
        val gramGoldTry = if (goldUsd != null && usdTry != null) {
            (goldUsd / 31.1034768) * usdTry
        } else null
        val gramSilverTry = if (silverUsd != null && usdTry != null) {
            (silverUsd / 31.1034768) * usdTry
        } else null

        gramGoldTry?.let { gram ->
            val quarter = gram * 1.635
            addAsset("GA", "Gram Altın", "Altın", gram)
            addAsset("CA", "Çeyrek Altın", "Çeyrek", quarter)
            addAsset("YA", "Yarım Altın", "Yarım", quarter * 2.0)
            addAsset("CUM", "Cumhuriyet Altını", "Tam", quarter * 4.0)
            addAsset("BLZ", "22 Ayar Bilezik (Gr)", "Bilezik", gram * 0.916)
        }
        goldUsd?.let { addAsset("XAU/USD", "ONS Altın ($)", "ONS", it, unit = "$") }
        gramSilverTry?.let {
            addAsset("GUM", "Gümüş Gram", "Gümüş", it)
            addAsset("GUMUS", "Gümüş (Gram)", "Ag", it)
        }
        silverUsd?.let { addAsset("XAG/USD", "ONS Gümüş ($)", "ONS", it, unit = "$") }

        fetchLiveCrypto().forEach { (code, quote) ->
            val (price, change) = quote
            val asset = when (code) {
                "BTC" -> Triple("BTC/TRY", "Bitcoin", "₿")
                "ETH" -> Triple("ETH/TRY", "Ethereum", "Ξ")
                else -> null
            }
            if (asset != null) {
                addAsset(asset.first, asset.second, asset.third, price, change)
            }
        }

        val bist = bistRetrofitApi.fetchBistStock("XU100")
        if (bist != null) {
            addAsset(
                code = "BIST100",
                name = "BIST 100 Endeksi",
                symbol = "BIST",
                price = bist.price,
                changePct = bist.changePct,
                unit = "Puan",
                high = bist.high,
                low = bist.low,
            )
        }

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

}
