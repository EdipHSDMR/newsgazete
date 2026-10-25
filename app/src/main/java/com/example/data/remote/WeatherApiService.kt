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
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.nio.charset.StandardCharsets

class WeatherApiService(
    private val httpClient: OkHttpClient,
) {

    suspend fun fetchWeather(cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
        val sanitized = sanitizeTurkishChars(cityName)
        val queries = linkedSetOf(cityName, sanitized)
        if (cityName.contains(",")) queries += sanitizeTurkishChars(cityName.substringAfter(",").trim())
        for (query in queries) {
            tryFetchWeatherApi(query)?.let { return@withContext it }
        }
        null
    }

    private fun tryFetchWeatherApi(query: String): WeatherInfo? = try {
        val encodedCity = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "${ApiGatewayConfig.BASE_URL}/v1/weather?q=$encodedCity&days=5&aqi=no&alerts=no"
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val jsonStr = response.body?.string() ?: return null
            val current = JSONObject(jsonStr).optJSONObject("current") ?: return null
            if (!current.has("temp_c")) return null
            parseWeatherApiResponse(query, jsonStr)
        }
    } catch (_: Exception) {
        null
    }

    private fun sanitizeTurkishChars(input: String): String = input
        .replace("İ", "I").replace("ı", "i")
        .replace("ğ", "g").replace("Ğ", "G")
        .replace("ü", "u").replace("Ü", "U")
        .replace("ş", "s").replace("Ş", "S")
        .replace("ö", "o").replace("Ö", "O")
        .replace("ç", "c").replace("Ç", "C")
        .trim()

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
                val currentEpoch = current.optLong("last_updated_epoch", 0L)
                val futureHours = (0 until hourArray.length()).filter { index ->
                    hourArray.getJSONObject(index).optLong("time_epoch", 0L) >= currentEpoch
                }
                for (position in futureHours.indices step 3) {
                    val h = futureHours[position]
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
        if (forecastDayArray != null) {
            for (d in 0 until forecastDayArray.length()) {
                val fObj = forecastDayArray.getJSONObject(d)
                val dayObj = fObj.optJSONObject("day") ?: continue
                val maxT = dayObj.optDouble("maxtemp_c", 20.0).toInt()
                val minT = dayObj.optDouble("mintemp_c", 12.0).toInt()
                val rain = dayObj.optInt("daily_chance_of_rain", 15)
                val dayCond = translateCondition(dayObj.optJSONObject("condition")?.optString("text", "") ?: "")

                val dayName = when (d) {
                    0 -> "Bugün"
                    1 -> "Yarın"
                    else -> runCatching {
                        LocalDate.parse(fObj.optString("date"))
                            .dayOfWeek
                            .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("tr-TR"))
                            .replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) }
                    }.getOrDefault("Gün ${d + 1}")
                }
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
            lastUpdated = current.optString("last_updated", "WeatherAPI Canlı"),
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
