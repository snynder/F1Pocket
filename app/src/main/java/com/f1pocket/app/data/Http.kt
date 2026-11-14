package com.f1pocket.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Year
import java.util.concurrent.TimeUnit

class HttpException(val code: Int) : IOException("HTTP $code")

/** OpenF1 refuses free requests from 30 min before to 30 min after a live session. */
class LiveWindowException : IOException("live_window")

object Http {
    const val UA = "F1Pocket-Android/1.0"
    lateinit var client: OkHttpClient
        private set
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val lastCall = HashMap<String, Long>()
    private val lock = Mutex()

    fun init(context: Context) {
        if (::client.isInitialized) return
        client = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 80L * 1024 * 1024))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val res = chain.proceed(req)
                val url = req.url.toString()
                val maxAge = cacheSeconds(url)
                if (maxAge > 0 && res.isSuccessful) {
                    res.newBuilder().header("Cache-Control", "public, max-age=$maxAge").removeHeader("Pragma").build()
                } else res
            }
            .build()
    }

    /** How long each source can be reused from the disk cache. Old seasons never change. */
    private fun cacheSeconds(url: String): Int {
        val year = Year.now().value
        return when {
            url.contains("jolpi.ca") -> {
                val y = Regex("/((19|20)\\d\\d)/").find(url)?.groupValues?.get(1)?.toIntOrNull()
                when {
                    y != null && y < year -> 7 * 86400
                    y == null && !url.contains("current") -> 86400 // all-time lists change at most once a race
                    else -> 600
                }
            }
            url.contains("githubusercontent") -> 6 * 3600
            url.contains("open-meteo") -> 1800
            url.contains("api.openf1.org") -> if (url.contains("session_key=latest")) 60 else 3600
            else -> 0
        }
    }

    private fun spacing(host: String): Long = when {
        host.contains("openf1") -> 400
        host.contains("jolpi.ca") -> 280
        else -> 0
    }

    suspend fun getText(url: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        val gap = spacing(host)
        var attempt = 0
        while (true) {
            if (gap > 0) lock.withLock {
                val wait = (lastCall[host] ?: 0L) + gap - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                lastCall[host] = System.currentTimeMillis()
            }
            val (code, body) = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(url).header("User-Agent", UA).build()
                client.newCall(req).execute().use { r -> r.code to (r.body?.string() ?: "") }
            }
            when {
                code == 401 && host.contains("openf1") -> throw LiveWindowException()
                (code == 429 || code >= 500) && attempt < 4 -> {
                    attempt++
                    delay(1500L * attempt)
                }
                code in 200..299 -> return body.removePrefix("﻿")
                else -> throw HttpException(code)
            }
        }
    }

    suspend fun getJson(url: String): JsonElement {
        val text = getText(url)
        return if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
    }
}
