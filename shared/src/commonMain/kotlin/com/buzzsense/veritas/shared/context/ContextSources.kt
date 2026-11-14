package com.buzzsense.veritas.shared.context

import com.buzzsense.veritas.shared.model.RiskLevel
import com.buzzsense.veritas.shared.model.Weather
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Today's weather. City is fixed to San Juan; the app never requests GPS (guardrail "No precise location"). */
interface WeatherSource {
    suspend fun today(): Weather
}

/**
 * Open-Meteo, no API key needed. Only fixed city coordinates go out; no audio,
 * no device location. The [http] client must have ContentNegotiation + JSON installed.
 */
class OpenMeteoWeather(private val http: HttpClient) : WeatherSource {

    override suspend fun today(): Weather {
        val res: OpenMeteoResponse = http.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", SAN_JUAN_LAT)
            parameter("longitude", SAN_JUAN_LON)
            parameter("current", "temperature_2m,relative_humidity_2m,precipitation")
            parameter("timezone", "America/Puerto_Rico")
        }.body()
        return Weather(
            city = "San Juan, PR",
            temperatureC = res.current.temperature,
            relativeHumidityPct = res.current.humidity,
            precipitationMm = res.current.precipitation,
            observedAt = res.current.time,
        )
    }

    @Serializable
    private data class OpenMeteoResponse(val current: Current)

    @Serializable
    private data class Current(
        val time: String,
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("relative_humidity_2m") val humidity: Double,
        val precipitation: Double,
    )

    companion object {
        const val SAN_JUAN_LAT = 18.4655
        const val SAN_JUAN_LON = -66.1057
    }
}

/**
 * Stretch: DengAI San Juan weekly forecast mapped to a level. The agent only ever
 * receives the level, never the raw case count. The demo is a replay of a past season.
 */
fun interface ForecastRiskSource {
    suspend fun currentLevel(): RiskLevel?
}

/** Default until the forecast model ships (first thing cut if Sunday runs short). */
val NoForecast = ForecastRiskSource { null }

/** Replays a past season week by week: "if BuzzAlert had run during this season". */
class ReplayForecastRisk(private val weeklyLevels: List<RiskLevel>, private val weekIndex: () -> Int) : ForecastRiskSource {
    override suspend fun currentLevel(): RiskLevel? = weeklyLevels.getOrNull(weekIndex())
}
