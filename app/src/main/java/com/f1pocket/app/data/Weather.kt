package com.f1pocket.app.data

import java.time.LocalDate

data class DayWeather(val date: LocalDate, val code: Int, val max: Double?, val min: Double?, val rain: Int?)

/** Open-Meteo: free forecast, up to 16 days ahead. */
object Weather {
    suspend fun forecast(lat: Double, lon: Double, from: LocalDate, to: LocalDate): List<DayWeather>? {
        val today = LocalDate.now()
        if (to.isBefore(today) || from.isAfter(today.plusDays(15))) return null
        val start = if (from.isBefore(today)) today else from
        val end = if (to.isAfter(today.plusDays(15))) today.plusDays(15) else to
        val j = Http.getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&start_date=$start&end_date=$end"
        ).child("daily")
        val dates = j.child("time").arr()
        return dates.indices.map { i ->
            DayWeather(
                LocalDate.parse(dates[i].str()),
                j.child("weather_code").arr().getOrNull(i).int() ?: 0,
                j.child("temperature_2m_max").arr().getOrNull(i).dbl(),
                j.child("temperature_2m_min").arr().getOrNull(i).dbl(),
                j.child("precipitation_probability_max").arr().getOrNull(i).int()
            )
        }
    }
}
