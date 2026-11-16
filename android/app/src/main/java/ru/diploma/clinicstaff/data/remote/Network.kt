package ru.diploma.clinicstaff.data.remote

import android.content.Context
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

class TokenHolder {
    @Volatile var token: String? = null
}
object Network {
    /** Проверяет, что адрес API действительно задан, а не оставлен шаблонным. */
    fun isBaseUrlConfigured(baseUrl: String): Boolean {
        val value = baseUrl.trim()
        if (value.isBlank() || value.contains("example.com", ignoreCase = true)) return false
        return runCatching {
            val uri = java.net.URI(value)
            val schemeOk = uri.scheme.equals("https", true) ||
                (uri.scheme.equals("http", true) && (uri.host == "10.0.2.2" || uri.host == "localhost" || uri.host == "127.0.0.1"))
            schemeOk && !uri.host.isNullOrBlank() && uri.path.orEmpty().endsWith("/api/v1/") && uri.rawQuery == null && uri.rawFragment == null
        }.getOrDefault(false)
    }

    fun api(context: Context, baseUrl: String, tokenHolder: TokenHolder): ApiService {
        // Retrofit требует корректный baseUrl даже в автономном DEMO-режиме.
        // Этот адрес является заглушкой и никогда не используется для запросов:
        // все сетевые операции предварительно проверяют apiConfigured.
        val safeBaseUrl = if (isBaseUrlConfigured(baseUrl)) baseUrl else "https://api-not-configured.invalid/"
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    tokenHolder.token?.let { header("Authorization", "Bearer $it") }
                    header("Accept", "application/json")
                }.build()
                val start=System.nanoTime()
                DebugLogStore.log("HTTP","REQ","${request.method} ${request.url.encodedPath}")
                try {
                    val response=chain.proceed(request)
                    val ms=(System.nanoTime()-start)/1_000_000
                    DebugLogStore.log(if(response.isSuccessful) "INFO" else "WARN","HTTP","${response.code} ${request.method} ${request.url.encodedPath} · ${ms}ms")
                    response
                } catch (e: Exception) {
                    val ms=(System.nanoTime()-start)/1_000_000
                    DebugLogStore.log("ERROR","HTTP","${request.method} ${request.url.encodedPath} · ${e.javaClass.simpleName}: ${e.message} · ${ms}ms")
                    throw e
                }
            }.build()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return Retrofit.Builder().baseUrl(safeBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(ApiService::class.java)
    }
}
