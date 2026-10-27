package com.veliora.data.api

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.successOrNull
import com.clerk.api.session.fetchToken
import com.veliora.BuildConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Builds the shared OkHttp client and Retrofit instance.
 *
 * Every /v1 request carries the current Clerk session JWT. The token is
 * fetched through the Clerk SDK's session cache, so refreshes are handled by
 * Clerk rather than by this app.
 */
object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        if (!request.url.encodedPath.startsWith("/v1/")) {
            return@Interceptor chain.proceed(request)
        }

        val token = runBlocking {
            val result = Clerk.activeSession?.fetchToken()
            result?.successOrNull()?.jwt
        }
        if (token.isNullOrBlank()) {
            throw IOException("Not signed in with Clerk. Sign in and try again.")
        }

        chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                // The backend stores only these coarse values for an account
                // count. Never send email, name, or other identity fields here.
                .header("X-Veliora-Platform", "android")
                .header("X-Veliora-App-Version", BuildConfig.VERSION_NAME)
                .build(),
        )
    }

    private fun baseBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)

    private val okHttp: OkHttpClient by lazy {
        baseBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * SSE keeps the connection open with periodic heartbeats from the server,
     * so it must not inherit the normal 30-second read timeout.
     */
    private val sseHttp: OkHttpClient by lazy {
        baseBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    val api: VelioraApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL.trimEnd('/') + "/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(VelioraApi::class.java)
    }

    val sseClient: OkHttpClient get() = sseHttp
}
