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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class WeatherApiService(
    private val httpClient: OkHttpClient,
) {
    private data class GeoPoint(val name: String, val latitude: Double, val longitude: Double)

    private val weatherCache = ConcurrentHashMap<String, Pair<Long, WeatherInfo>>()
    private val geocodingCache = ConcurrentHashMap<String, GeoPoint>()
    private val weatherCacheTtlMs = 15L * 60L * 1_000L

    suspend fun fetchWeather(cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
        val cacheKey = cityName.trim().lowercase(Locale.forLanguageTag("tr-TR"))
        weatherCache[cacheKey]
            ?.takeIf { System.currentTimeMillis() - it.first < weatherCacheTtlMs }
            ?.second
            ?.let { return@withContext it }

        // Prefer the free, keyless forecast source so normal refreshes do not depend on a cloud proxy.
        tryFetchOpenMeteo(cityName)?.let {
            weatherCache[cacheKey] = System.currentTimeMillis() to it
            return@withContext it
        }

        val sanitized = sanitizeTurkishChars(cityName)
        val queries = linkedSetOf(cityName, sanitized)
        if (cityName.contains(",")) queries += sanitizeTurkishChars(cityName.substringAfter(",").trim())
        for (query in queries) {
            tryFetchWeatherApi(query)?.let {
                weatherCache[cacheKey] = System.currentTimeMillis() to it
                return@withContext it
            }
        }

        null
    }

    private fun tryFetchWeatherApi(query: String): WeatherInfo? = try {
        val encodedCity = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        // WeatherAPI's no-cost tier includes a three-day forecast.
        val url = "${ApiGatewayConfig.BASE_URL}/v1/weather?q=$encodedCity&days=3&aqi=no&alerts=no"
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

    /** Keyless Open-Meteo fallback keeps weather available without a paid cloud proxy. */
    private fun tryFetchOpenMeteo(requestedCity: String): WeatherInfo? = try {
        // A district may be passed as "Kadıköy, İstanbul". Resolve by its city to
        // avoid a same-named small place elsewhere in Türkiye.
        val city = requestedCity.substringAfterLast(",").trim().ifBlank { requestedCity.trim() }
        val location = resolveOpenMeteoLocation(city) ?: return null
        val lat = String.format(Locale.US, "%.5f", location.latitude)
        val lon = String.format(Locale.US, "%.5f", location.longitude)
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,uv_index" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=7&timezone=auto&wind_speed_unit=kmh"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = response.body?.string() ?: return null
            parseOpenMeteoResponse(location, json)
        }
    } catch (_: Exception) {
        null
    }

    private fun resolveOpenMeteoLocation(city: String): GeoPoint? {
        val key = city.trim().lowercase(Locale.forLanguageTag("tr-TR"))
        geocodingCache[key]?.let { return it }
        val encodedCity = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8.toString())
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encodedCity&count=1&language=tr&countryCode=TR"
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        val point = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val result = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
            val latitude = result.optDouble("latitude", Double.NaN)
            val longitude = result.optDouble("longitude", Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite()) return null
            GeoPoint(result.optString("name").ifBlank { city }, latitude, longitude)
        }
        geocodingCache[key] = point
        return point
    }

    private fun parseOpenMeteoResponse(location: GeoPoint, json: String): WeatherInfo? {
        val root = JSONObject(json)
        val current = root.optJSONObject("current") ?: return null
        val hourly = root.optJSONObject("hourly") ?: JSONObject()
        val daily = root.optJSONObject("daily") ?: JSONObject()
        val temperature = current.finiteDouble("temperature_2m")?.roundToInt() ?: return null
        val feelsLike = current.finiteDouble("apparent_temperature")?.roundToInt()
        val humidity = current.intInRange("relative_humidity_2m", 0..100)
        val wind = current.finiteDouble("wind_speed_10m")?.roundToInt()
        val uv = current.finiteDouble("uv_index")?.roundToInt()
        val condition = translateWeatherCode(current.optInt("weather_code", -1))
        val currentTime = current.optString("time")

        val hourlyTimes = hourly.optJSONArray("time")
        val hourlyTemperatures = hourly.optJSONArray("temperature_2m")
        val hourlyRain = hourly.optJSONArray("precipitation_probability")
        val hourlyCodes = hourly.optJSONArray("weather_code")
        val startHour = if (hourlyTimes == null) 0 else (0 until hourlyTimes.length())
            .firstOrNull { hourlyTimes.optString(it) >= currentTime } ?: 0
        val hourlyList = mutableListOf<HourlyWeather>()
        if (hourlyTimes != null && hourlyTemperatures != null && hourlyCodes != null) {
            for (index in startHour until minOf(hourlyTimes.length(), hourlyTemperatures.length(), hourlyCodes.length()) step 3) {
                val time = hourlyTimes.optString(index)
                val temp = hourlyTemperatures.optDouble(index, Double.NaN)
                if (!temp.isFinite()) continue
                hourlyList += HourlyWeather(
                    hour = time.substringAfter("T").take(5),
                    tempC = temp.roundToInt(),
                    condition = translateWeatherCode(hourlyCodes.optInt(index, -1)),
                    rainProbPct = hourlyRain?.optInt(index, 0)?.coerceIn(0, 100) ?: 0,
                )
            }
        }

        val dailyTimes = daily.optJSONArray("time")
        val dailyCodes = daily.optJSONArray("weather_code")
        val dailyMax = daily.optJSONArray("temperature_2m_max")
        val dailyMin = daily.optJSONArray("temperature_2m_min")
        val dailyRain = daily.optJSONArray("precipitation_probability_max")
        val dailyList = mutableListOf<DailyWeather>()
        if (dailyTimes != null && dailyCodes != null && dailyMax != null && dailyMin != null) {
            val count = minOf(dailyTimes.length(), dailyCodes.length(), dailyMax.length(), dailyMin.length())
            for (index in 0 until count) {
                val maxTemp = dailyMax.optDouble(index, Double.NaN)
                val minTemp = dailyMin.optDouble(index, Double.NaN)
                if (!maxTemp.isFinite() || !minTemp.isFinite()) continue
                val date = dailyTimes.optString(index)
                val dayName = when (index) {
                    0 -> "Bugün"
                    1 -> "Yarın"
                    else -> runCatching {
                        LocalDate.parse(date).dayOfWeek
                            .getDisplayName(TextStyle.FULL, Locale.forLanguageTag("tr-TR"))
                            .replaceFirstChar { it.titlecase(Locale.forLanguageTag("tr-TR")) }
                    }.getOrDefault("Gün ${index + 1}")
                }
                dailyList += DailyWeather(
                    dayName = dayName,
                    condition = translateWeatherCode(dailyCodes.optInt(index, -1)),
                    maxTempC = maxTemp.roundToInt(),
                    minTempC = minTemp.roundToInt(),
                    rainProbPct = dailyRain?.optInt(index, 0)?.coerceIn(0, 100) ?: 0,
                )
            }
        }

        val rainChance = hourlyRain?.optInt(startHour, -1)?.takeIf { it in 0..100 }
        val summary = listOfNotNull(
            "${location.name} genelinde $condition.",
            "Sıcaklık ${temperature}°C.",
            feelsLike?.let { "Hissedilen ${it}°C." },
            wind?.let { "Rüzgar $it km/s." },
        ).joinToString(" ")

        return WeatherInfo(
            cityName = location.name,
            condition = condition,
            currentTempC = temperature,
            feelsLikeC = feelsLike,
            humidityPct = humidity,
            windKmh = wind,
            uvIndex = uv,
            rainChancePct = rainChance,
            summary = summary,
            hourly = hourlyList,
            daily = dailyList,
            lastUpdated = "Open-Meteo",
        )
    }

    private fun translateWeatherCode(code: Int): String = when (code) {
        0, 1 -> "Güneşli ve Açık"
        2 -> "Parçalı Bulutlu"
        3 -> "Bulutlu"
        45, 48 -> "Sisli"
        51, 53, 55, 56, 57 -> "Çiseleyen Yağmur"
        61, 63, 65, 66, 67 -> "Yağmurlu"
        71, 73, 75, 77, 85, 86 -> "Kar Yağışlı"
        80, 81, 82 -> "Sağanak Yağmurlu"
        95, 96, 99 -> "Gök Gürültülü Fırtına"
        else -> "Hava durumu"
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

