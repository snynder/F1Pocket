package com.racepocket.app.data

import java.util.Locale
import java.util.TimeZone

/** Fixed tables. Taken from the Pit Wall site; review once a season. */
object Static {

    val TEAM_COLORS = mapOf(
        "red_bull" to 0xFF3671C6, "ferrari" to 0xFFE8002D, "mercedes" to 0xFF27F4D2, "mclaren" to 0xFFFF8000,
        "aston_martin" to 0xFF229971, "alpine" to 0xFF0093CC, "williams" to 0xFF64C4FF, "rb" to 0xFF6692FF,
        "alphatauri" to 0xFF5E8FAA, "sauber" to 0xFF52E252, "alfa" to 0xFFC92D4B, "haas" to 0xFFB6BABD,
        "audi" to 0xFFBB0A30, "cadillac" to 0xFFC9A227, "racing_point" to 0xFFF596C8, "renault" to 0xFFFFD500,
        "toro_rosso" to 0xFF469BFF, "force_india" to 0xFFF596C8, "lotus_f1" to 0xFFFFB800, "manor" to 0xFFED1C24
    )

    fun teamColor(id: String?): Long = TEAM_COLORS[id] ?: 0xFF8A929C

    /** Length of each circuit, km. */
    val LENGTH_KM = mapOf(
        "albert_park" to 5.278, "shanghai" to 5.451, "suzuka" to 5.807, "bahrain" to 5.412, "jeddah" to 6.174,
        "miami" to 5.412, "imola" to 4.909, "monaco" to 3.337, "catalunya" to 4.657, "villeneuve" to 4.361,
        "red_bull_ring" to 4.318, "silverstone" to 5.891, "spa" to 7.004, "hungaroring" to 4.381, "zandvoort" to 4.259,
        "monza" to 5.793, "madring" to 5.416, "sepang" to 5.543, "baku" to 6.003, "marina_bay" to 4.940,
        "americas" to 5.513, "rodriguez" to 4.304, "interlagos" to 4.309, "vegas" to 6.201, "losail" to 5.419,
        "yas_marina" to 5.281
    )

    val DEMAND_KEYS = listOf("df", "br", "tr", "la", "ab", "st", "ev", "gr")

    /** Pirelli's demands (1-5) per circuit, from the Pit Wall site. Keys: df br tr la ab st ev gr. */
    val DEMANDS: Map<String, Map<String, Int?>> = mapOf(
        "albert_park" to d(3, 2, 2, 3, 2, 3, 3, 2), "shanghai" to d(3, 4, 3, 4, 3, 4, 5, 3),
        "suzuka" to d(4, 2, 3, 5, 4, 5, 3, 4), "miami" to d(null, 3, 3, 3, 3, 3, 5, 3),
        "villeneuve" to d(null, 5, 4, 1, 1, 3, 5, 2), "monaco" to d(null, 2, 5, 1, 1, 1, 5, 2),
        "catalunya" to d(null, 3, 3, 5, 4, 5, 3, 2), "red_bull_ring" to d(null, 3, 2, 3, 3, 3, 3, 3),
        "silverstone" to d(null, 2, 3, 5, 2, 5, 2, 4), "spa" to d(null, 4, 4, 5, 3, 5, 3, 3),
        "hungaroring" to d(null, 3, 4, 3, 3, 3, 4, 3), "zandvoort" to d(null, 3, 3, 4, 3, 5, 4, 3),
        "monza" to d(null, 4, 3, 2, 2, 3, 4, 3), "madring" to d(null, 3, 3, 4, 2, 3, 5, 4),
        "baku" to d(null, 4, 5, 1, 1, 3, 5, 2), "sepang" to d(null, 4, 3, 4, 4, 4, 4, 2),
        "marina_bay" to d(null, 5, 4, 2, 3, 2, 5, 3), "americas" to d(null, 3, 4, 4, 3, 4, 4, 3),
        "rodriguez" to d(null, 3, 3, 2, 2, 2, 5, 2), "interlagos" to d(4, 3, 3, 3, 2, 3, 5, 3),
        "yas_marina" to d(3, 4, 4, 3, 3, 3, 4, 3), "losail" to d(4, 3, 3, 5, 2, 5, 4, 3)
    )
    private fun d(vararg v: Int?): Map<String, Int?> = DEMAND_KEYS.zip(v.toList()).toMap()

    /** TV / streaming per country. Review every season. */
    val BROADCAST: Map<String, List<String>> = buildMap {
        put("US", listOf("Apple TV")); put("CA", listOf("RDS", "RDS 2", "TSN", "Noovo"))
        put("GB", listOf("Sky Sports F1", "Channel 4")); put("IE", listOf("Sky Sports F1"))
        put("BR", listOf("TV Globo", "sportv")); put("AU", listOf("Fox Sports", "Kayo", "Network 10"))
        put("IN", listOf("FanCode")); put("JP", listOf("Fuji TV")); put("CN", listOf("Tencent", "Shanghai TV", "Guangdong TV"))
        put("AT", listOf("ServusTV", "ORF", "Sky")); put("DE", listOf("Sky Deutschland", "RTL"))
        put("IT", listOf("Sky Italia", "TV8")); put("FR", listOf("Canal+")); put("ES", listOf("DAZN")); put("AD", listOf("DAZN"))
        put("NL", listOf("Viaplay")); put("BE", listOf("RTBF", "Telenet")); put("LU", listOf("RTL")); put("HU", listOf("M4 Sport"))
        put("SG", listOf("beIN SPORTS")); put("TH", listOf("beIN SPORTS"))
        listOf("AE", "SA", "QA", "KW", "BH", "OM", "JO", "LB", "EG", "TR").forEach { put(it, listOf("beIN SPORTS")) }
        listOf("ZA", "NG", "KE", "GH", "TZ", "UG", "ZM", "ZW", "NA", "BW", "MZ").forEach { put(it, listOf("SuperSport")) }
        listOf("AR", "CL", "CO", "PE", "UY", "PY", "BO", "EC", "VE", "CR", "PA", "GT", "HN", "SV", "NI", "DO").forEach { put(it, listOf("ESPN")) }
    }
    val FALLBACK_BROADCAST = listOf("F1 TV Pro")

    private val TZ_COUNTRY = mapOf(
        "America/Sao_Paulo" to "BR", "America/Bahia" to "BR", "America/Fortaleza" to "BR", "America/Recife" to "BR",
        "America/Manaus" to "BR", "America/Belem" to "BR", "America/Cuiaba" to "BR", "America/Campo_Grande" to "BR",
        "America/Toronto" to "CA", "America/Montreal" to "CA", "America/Vancouver" to "CA", "America/Edmonton" to "CA",
        "America/Winnipeg" to "CA", "America/Halifax" to "CA", "America/New_York" to "US", "America/Chicago" to "US",
        "America/Denver" to "US", "America/Los_Angeles" to "US", "Europe/London" to "GB", "Europe/Dublin" to "IE",
        "Europe/Lisbon" to "PT", "Europe/Madrid" to "ES", "Europe/Paris" to "FR", "Europe/Berlin" to "DE",
        "Europe/Rome" to "IT", "Europe/Amsterdam" to "NL", "Europe/Brussels" to "BE", "Europe/Vienna" to "AT",
        "Europe/Budapest" to "HU", "America/Argentina/Buenos_Aires" to "AR", "America/Santiago" to "CL",
        "America/Bogota" to "CO", "America/Lima" to "PE", "America/Mexico_City" to "MX", "Australia/Sydney" to "AU",
        "Australia/Melbourne" to "AU", "Asia/Kolkata" to "IN", "Asia/Tokyo" to "JP", "Asia/Shanghai" to "CN",
        "Asia/Singapore" to "SG", "Asia/Dubai" to "AE", "Asia/Qatar" to "QA", "Africa/Johannesburg" to "ZA"
    )

    /** Country guessed from the device: time zone first, then the locale. */
    fun deviceCountry(): String =
        TZ_COUNTRY[TimeZone.getDefault().id] ?: Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"

    fun countryName(iso2: String): String = Locale("", iso2).getDisplayCountry(Locale.getDefault()).ifBlank { iso2 }

    /** All-time records that the app does not recompute (kept up to date by hand). */
    val TRACK_TURNS = mapOf(
        "albert_park" to 14, "shanghai" to 16, "suzuka" to 18, "bahrain" to 15, "jeddah" to 27, "miami" to 19,
        "villeneuve" to 14, "monaco" to 19, "catalunya" to 14, "red_bull_ring" to 10, "silverstone" to 18, "spa" to 19,
        "hungaroring" to 14, "zandvoort" to 14, "monza" to 11, "madring" to 22, "baku" to 20, "marina_bay" to 19,
        "americas" to 20, "rodriguez" to 17, "interlagos" to 15, "vegas" to 17, "losail" to 16, "yas_marina" to 16,
        "sepang" to 15, "imola" to 19
    )
}

object Flags {
    fun emoji(iso2: String?): String {
        if (iso2 == null || iso2.length != 2) return ""
        val up = iso2.uppercase(Locale.ROOT)
        if (!up.all { it in 'A'..'Z' }) return ""
        return String(Character.toChars(0x1F1E6 + (up[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (up[1] - 'A')))
    }

    fun nationality(n: String?): String = emoji(NAT[n])
    fun country(c: String?): String = emoji(COUNTRY[c])
    fun countryIso(c: String?): String? = COUNTRY[c]
    fun ioc(code: String?): String = emoji(IOC[code])

    private val NAT = mapOf(
        "British" to "GB", "Dutch" to "NL", "Australian" to "AU", "Monegasque" to "MC", "Italian" to "IT",
        "Spanish" to "ES", "French" to "FR", "Thai" to "TH", "New Zealander" to "NZ", "German" to "DE",
        "Canadian" to "CA", "Argentine" to "AR", "Argentinian" to "AR", "Brazilian" to "BR", "Mexican" to "MX",
        "Finnish" to "FI", "American" to "US", "Japanese" to "JP", "Chinese" to "CN", "Danish" to "DK",
        "Austrian" to "AT", "Swiss" to "CH", "Belgian" to "BE", "Swedish" to "SE", "Polish" to "PL",
        "Russian" to "RU", "Venezuelan" to "VE", "Colombian" to "CO", "Indian" to "IN", "Indonesian" to "ID",
        "Irish" to "IE", "Portuguese" to "PT", "Hungarian" to "HU", "South African" to "ZA", "Malaysian" to "MY",
        "Chilean" to "CL", "Uruguayan" to "UY", "Czech" to "CZ", "Liechtensteiner" to "LI", "Rhodesian" to "ZW",
        "East German" to "DE", "Hong Kong" to "HK", "Emirati" to "AE"
    )
    private val COUNTRY = mapOf(
        "Australia" to "AU", "China" to "CN", "Japan" to "JP", "Bahrain" to "BH", "Saudi Arabia" to "SA",
        "USA" to "US", "United States" to "US", "Canada" to "CA", "Monaco" to "MC", "Spain" to "ES",
        "Austria" to "AT", "UK" to "GB", "United Kingdom" to "GB", "Great Britain" to "GB", "Belgium" to "BE",
        "Hungary" to "HU", "Netherlands" to "NL", "Italy" to "IT", "Azerbaijan" to "AZ", "Singapore" to "SG",
        "Mexico" to "MX", "Brazil" to "BR", "Qatar" to "QA", "UAE" to "AE", "United Arab Emirates" to "AE",
        "Portugal" to "PT", "France" to "FR", "Germany" to "DE", "Russia" to "RU", "Turkey" to "TR",
        "Malaysia" to "MY", "Korea" to "KR", "India" to "IN", "Argentina" to "AR", "South Africa" to "ZA",
        "Sweden" to "SE", "Switzerland" to "CH", "Morocco" to "MA", "Vietnam" to "VN"
    )
    private val IOC = mapOf(
        "GBR" to "GB", "NED" to "NL", "AUS" to "AU", "MON" to "MC", "ITA" to "IT", "ESP" to "ES", "FRA" to "FR",
        "THA" to "TH", "NZL" to "NZ", "GER" to "DE", "CAN" to "CA", "ARG" to "AR", "BRA" to "BR", "MEX" to "MX",
        "FIN" to "FI", "USA" to "US", "JPN" to "JP", "CHN" to "CN", "DEN" to "DK", "SUI" to "CH", "BEL" to "BE"
    )
}
