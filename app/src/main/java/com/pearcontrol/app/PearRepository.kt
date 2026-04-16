package com.pearcontrol.app

import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PearRepository(baseUrl: String) {
    private val authToken = MutableStateFlow<String?>(null)

    private val api: PearApiService

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                authToken.value?.takeIf { it.isNotBlank() }?.let {
                    builder.header("Authorization", "Bearer $it")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        api = retrofit.create(PearApiService::class.java)
    }

    fun setToken(token: String) {
        authToken.value = token
    }

    suspend fun authenticate(id: String): Result<String> {
        return runCatching {
            val response = api.auth(id)
            if (!response.isSuccessful) {
                throw IllegalStateException("Auth failed (${response.code()})")
            }
            response.body()?.accessToken ?: throw IllegalStateException("Auth token not returned")
        }
    }

    suspend fun fetchSong(): Result<SongResponse?> {
        return runCatching {
            val response = api.getSong()
            if (response.code() == 204) {
                return@runCatching null
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("Song request failed (${response.code()})")
            }
            response.body()
        }
    }

    suspend fun fetchVolume(): Result<VolumeStateResponse> = callBody(
        call = { api.getVolumeState() },
        errorLabel = "Volume request"
    )

    suspend fun fetchLikeState(): Result<LikeStateResponse> = callBody(
        call = { api.getLikeState() },
        errorLabel = "Like state request"
    )

    suspend fun fetchRepeatMode(): Result<RepeatModeResponse> = callBody(
        call = { api.getRepeatMode() },
        errorLabel = "Repeat mode request"
    )

    suspend fun fetchShuffleState(): Result<BooleanStateResponse> = callBody(
        call = { api.getShuffleState() },
        errorLabel = "Shuffle state request"
    )

    suspend fun fetchFullscreenState(): Result<BooleanStateResponse> = callBody(
        call = { api.getFullscreenState() },
        errorLabel = "Fullscreen state request"
    )

    suspend fun previous(): Result<Unit> = callUnit { api.previous() }
    suspend fun next(): Result<Unit> = callUnit { api.next() }
    suspend fun play(): Result<Unit> = callUnit { api.play() }
    suspend fun pause(): Result<Unit> = callUnit { api.pause() }
    suspend fun togglePlay(): Result<Unit> = callUnit { api.togglePlay() }
    suspend fun like(): Result<Unit> = callUnit { api.like() }
    suspend fun dislike(): Result<Unit> = callUnit { api.dislike() }
    suspend fun toggleMute(): Result<Unit> = callUnit { api.toggleMute() }
    suspend fun shuffle(): Result<Unit> = callUnit { api.shuffle() }
    suspend fun setFullscreen(state: Boolean): Result<Unit> = callUnit { api.setFullscreen(FullscreenRequest(state)) }
    suspend fun switchRepeat(iteration: Int): Result<Unit> = callUnit { api.switchRepeat(IterationRequest(iteration)) }
    suspend fun seekTo(seconds: Double): Result<Unit> = callUnit { api.seekTo(SecondsRequest(seconds)) }
    suspend fun goBack(seconds: Double): Result<Unit> = callUnit { api.goBack(SecondsRequest(seconds)) }
    suspend fun goForward(seconds: Double): Result<Unit> = callUnit { api.goForward(SecondsRequest(seconds)) }
    suspend fun setVolume(volume: Double): Result<Unit> = callUnit { api.setVolume(VolumeRequest(volume)) }

    private suspend fun <T> callBody(
        call: suspend () -> Response<T>,
        errorLabel: String
    ): Result<T> {
        return runCatching {
            val response = call()
            if (!response.isSuccessful) {
                throw IllegalStateException("$errorLabel failed (${response.code()})")
            }
            response.body() ?: throw IllegalStateException("$errorLabel returned empty body")
        }
    }

    private suspend fun callUnit(call: suspend () -> Response<Unit>): Result<Unit> {
        return runCatching {
            val response = call()
            if (!response.isSuccessful) {
                throw IllegalStateException("Command failed (${response.code()})")
            }
        }
    }
}
