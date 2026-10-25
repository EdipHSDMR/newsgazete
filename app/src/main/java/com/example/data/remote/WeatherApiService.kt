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

    private fun parseWeatherApiResponse(requestedCity: String, jsonStr: String): WeatherInfo? {
        val root = JSONObject(jsonStr)
        val location = root.optJSONObject("location")
        val current = root.optJSONObject("current") ?: JSONObject()
        val forecast = root.optJSONObject("forecast") ?: JSONObject()
        val forecastDayArray = forecast.optJSONArray("forecastday")

        val resolvedCity = location?.optString("name")?.takeIf(String::isNotBlank) ?: requestedCity
        val tempC = current.finiteDouble("temp_c")?.toInt() ?: return null
        val feelsLikeC = current.finiteDouble("feelslike_c")?.toInt()
        val humidity = current.intInRange("humidity", 0..100)
        val windKmh = current.finiteDouble("wind_kph")?.toInt()
        val uv = current.finiteDouble("uv")?.toInt()

        val conditionObj = current.optJSONObject("condition")
        val rawCondition = conditionObj?.optString("text")?.takeIf(String::isNotBlank) ?: return null
        val condition = translateCondition(rawCondition)

        // Parse hourly forecast
        val hourlyList = mutableListOf<HourlyWeather>()
        val todayDay = forecastDayArray?.optJSONObject(0)?.optJSONObject("day")
        val rainChance = todayDay?.intInRange("daily_chance_of_rain", 0..100)

        if (forecastDayArray != null && forecastDayArray.length() > 0) {
            val todayObj = forecastDayArray.getJSONObject(0)
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
                    val hTemp = hObj.finiteDouble("temp_c")?.toInt() ?: continue
                    val hCondText = hObj.optJSONObject("condition")?.optString("text")?.takeIf(String::isNotBlank) ?: continue
                    val hCond = translateCondition(hCondText)
                    val hRain = hObj.intInRange("chance_of_rain", 0..100) ?: continue

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
                val maxT = dayObj.finiteDouble("maxtemp_c")?.toInt() ?: continue
                val minT = dayObj.finiteDouble("mintemp_c")?.toInt() ?: continue
                val rain = dayObj.intInRange("daily_chance_of_rain", 0..100) ?: continue
                val conditionText = dayObj.optJSONObject("condition")?.optString("text")?.takeIf(String::isNotBlank) ?: continue
                val dayCond = translateCondition(conditionText)

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

        val summary = listOfNotNull(
            "$resolvedCity genelinde $condition.",
            tempC.let { "Sıcaklık $it°C." },
            feelsLikeC?.let { "Hissedilen $it°C." },
            windKmh?.let { "Rüzgar $it km/s." },
        ).joinToString(" ")

        return WeatherInfo(
            cityName = resolvedCity,
            condition = condition,
            currentTempC = tempC,
            feelsLikeC = feelsLikeC,
            humidityPct = humidity,
            windKmh = windKmh,
            uvIndex = uv,
            rainChancePct = rainChance,
            summary = summary,
            hourly = hourlyList,
            daily = dailyList,
            lastUpdated = current.optString("last_updated").takeIf(String::isNotBlank) ?: "WeatherAPI",
        )
    }

    private fun JSONObject.finiteDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return optDouble(name, Double.NaN).takeIf(Double::isFinite)
    }

    private fun JSONObject.intInRange(name: String, range: IntRange): Int? {
        if (!has(name) || isNull(name)) return null
        return optInt(name, Int.MIN_VALUE).takeIf { it in range }
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
            else -> engText
        }
    }
}
