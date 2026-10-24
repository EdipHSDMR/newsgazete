package com.example.data.remote

import com.example.model.DailyWeather
import com.example.model.HourlyWeather
import com.example.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import com.example.BuildConfig

class WeatherApiService(
    private val httpClient: OkHttpClient,
    private val apiKeyProvider: () -> String = { "" },
) {

    // WeatherAPI key: Priority 1: User-entered key in app settings, Priority 2: BuildConfig / .env, Priority 3: Fallback
    private fun getApiKey(): String {
        val userKey = apiKeyProvider().trim()
        if (userKey.isNotBlank()) return userKey
        val buildKey = BuildConfig.WEATHER_API_KEY.trim()
        if (buildKey.isNotBlank()) return buildKey
        return "ab0580d539ff4d409a3191011262310"
    }

    suspend fun fetchWeather(cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        // 1. Try original city query
        var result = tryFetchWeatherApi(apiKey, cityName, days = 5)
        if (result != null) return@withContext result

        // 2. Try sanitized ASCII name (WeatherAPI works best with ascii e.g. Istanbul, Diyarbakir)
        val sanitized = sanitizeTurkishChars(cityName)
        if (sanitized != cityName) {
            result = tryFetchWeatherApi(apiKey, sanitized, days = 3)
            if (result != null) return@withContext result
        }

        // 3. If district was included ("Kadikoy, Istanbul"), try just main city
        if (cityName.contains(",")) {
            val mainCity = cityName.substringAfter(",").trim()
            result = tryFetchWeatherApi(apiKey, sanitizeTurkishChars(mainCity), days = 3)
            if (result != null) return@withContext result
        }

        null
    }

    private fun tryFetchWeatherApi(apiKey: String, query: String, days: Int): WeatherInfo? {
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val url = "https://api.weatherapi.com/v1/forecast.json?key=$apiKey&q=$encoded&days=$days&aqi=no&alerts=no"

            val request = Request.Builder()
                .url(url)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val jsonStr = response.body?.string() ?: return null
            return parseWeatherApiResponse(query, jsonStr)
        } catch (_: Exception) {
            return null
        }
    }

    private fun sanitizeTurkishChars(input: String): String {
        return input
            .replace("İ", "I")
            .replace("ı", "i")
            .replace("ğ", "g")
            .replace("Ğ", "G")
            .replace("ü", "u")
            .replace("Ü", "U")
            .replace("ş", "s")
            .replace("Ş", "S")
            .replace("ö", "o")
            .replace("Ö", "O")
            .replace("ç", "c")
            .replace("Ç", "C")
            .trim()
    }

    private fun parseWeatherApiResponse(requestedCity: String, jsonStr: String): WeatherInfo {
        val root = JSONObject(jsonStr)
        val location = root.optJSONObject("location")
        val current = root.optJSONObject("current") ?: JSONObject()
        val forecast = root.optJSONObject("forecast") ?: JSONObject()
        val forecastDayArray = forecast.optJSONArray("forecastday")

        val resolvedCity = location?.optString("name") ?: requestedCity
        val tempC = current.optDouble("temp_c", 18.0).toInt()
        val feelsLikeC = current.optDouble("feelslike_c", tempC.toDouble()).toInt()
        val humidity = current.optInt("humidity", 60)
        val windKmh = current.optDouble("wind_kph", 15.0).toInt()
        val uv = current.optDouble("uv", 3.0).toInt()

        val conditionObj = current.optJSONObject("condition")
        val rawCondition = conditionObj?.optString("text", "Parçalı Bulutlu") ?: "Parçalı Bulutlu"
        val condition = translateCondition(rawCondition)

        // Parse hourly forecast
        val hourlyList = mutableListOf<HourlyWeather>()
        var rainChance = 10

        if (forecastDayArray != null && forecastDayArray.length() > 0) {
            val todayObj = forecastDayArray.getJSONObject(0)
            val todayDay = todayObj.optJSONObject("day")
            rainChance = todayDay?.optInt("daily_chance_of_rain", 10) ?: 10

            val hourArray = todayObj.optJSONArray("hour")
            if (hourArray != null) {
                for (h in 0 until minOf(24, hourArray.length()) step 3) {
                    val hObj = hourArray.getJSONObject(h)
                    val timeStr = hObj.optString("time", "")
                    val hourLabel = timeStr.substringAfter(" ").take(5).ifBlank { "${h}:00" }
                    val hTemp = hObj.optDouble("temp_c", 18.0).toInt()
                    val hCond = translateCondition(hObj.optJSONObject("condition")?.optString("text", "") ?: "")
                    val hRain = hObj.optInt("chance_of_rain", 10)

                    hourlyList.add(
                        HourlyWeather(
                            hour = hourLabel,
                            tempC = hTemp,
                            condition = hCond,
                            rainProbPct = hRain,
                        )
                    )
                }
            }
        }

        // Parse daily forecast
        val dailyList = mutableListOf<DailyWeather>()
        val dayNames = listOf("Bugün", "Yarın", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

        if (forecastDayArray != null) {
            for (d in 0 until forecastDayArray.length()) {
                val fObj = forecastDayArray.getJSONObject(d)
                val dayObj = fObj.optJSONObject("day") ?: continue
                val maxT = dayObj.optDouble("maxtemp_c", 20.0).toInt()
                val minT = dayObj.optDouble("mintemp_c", 12.0).toInt()
                val rain = dayObj.optInt("daily_chance_of_rain", 15)
                val dayCond = translateCondition(dayObj.optJSONObject("condition")?.optString("text", "") ?: "")

                val dayName = if (d < dayNames.size) dayNames[d] else "Gün ${d + 1}"
                dailyList.add(
                    DailyWeather(
                        dayName = dayName,
                        condition = dayCond,
                        maxTempC = maxT,
                        minTempC = minT,
                        rainProbPct = rain,
                    )
                )
            }
        }

        return WeatherInfo(
            cityName = resolvedCity,
            condition = condition,
            currentTempC = tempC,
            feelsLikeC = feelsLikeC,
            humidityPct = humidity,
            windKmh = windKmh,
            uvIndex = uv,
            rainChancePct = rainChance,
            summary = "$resolvedCity genelinde $condition. Sıcaklık $tempC°C, hissedilen $feelsLikeC°C. Rüzgar $windKmh km/s hızında esiyor.",
            hourly = hourlyList,
            daily = dailyList,
            lastUpdated = "WeatherAPI Canlı",
        )
    }

    private fun translateCondition(engText: String): String {
        val lower = engText.lowercase()
        return when {
            lower.contains("sunny") || lower.contains("clear") -> "Güneşli ve Açık"
            lower.contains("partly cloudy") -> "Parçalı Bulutlu"
            lower.contains("cloudy") || lower.contains("overcast") -> "Bulutlu"
            lower.contains("mist") || lower.contains("fog") -> "Sisli"
            lower.contains("patchy rain") || lower.contains("drizzle") -> "Çiseleyen Yağmur"
            lower.contains("thunder") -> "Gök Gürültülü Fırtına"
            lower.contains("snow") || lower.contains("blizzard") -> "Kar Yağışlı"
            lower.contains("rain") || lower.contains("shower") -> "Sağanak Yağmurlu"
            engText.isBlank() -> "Açık"
            else -> engText
        }
    }
}
