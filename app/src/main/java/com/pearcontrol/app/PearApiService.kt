package com.pearcontrol.app

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class AuthResponse(
    val accessToken: String
)

data class SongResponse(
    val title: String?,
    val artist: String?,
    val views: Double?,
    val uploadDate: String?,
    val imageSrc: String?,
    val isPaused: Boolean?,
    val songDuration: Double?,
    val elapsedSeconds: Double?,
    val url: String?,
    val album: String?,
    val videoId: String?,
    val playlistId: String?,
    val mediaType: String?
)

data class VolumeStateResponse(
    val state: Double,
    val isMuted: Boolean
)

data class LikeStateResponse(
    val state: String?
)

data class RepeatModeResponse(
    val mode: String?
)

data class BooleanStateResponse(
    val state: Boolean?
)

data class SecondsRequest(
    val seconds: Double
)

data class IterationRequest(
    val iteration: Int
)

data class VolumeRequest(
    val volume: Double
)

data class FullscreenRequest(
    val state: Boolean
)

interface PearApiService {
    @POST("auth/{id}")
    suspend fun auth(@Path("id") id: String): Response<AuthResponse>

    @POST("api/v1/previous")
    suspend fun previous(): Response<Unit>

    @POST("api/v1/next")
    suspend fun next(): Response<Unit>

    @POST("api/v1/play")
    suspend fun play(): Response<Unit>

    @POST("api/v1/pause")
    suspend fun pause(): Response<Unit>

    @POST("api/v1/toggle-play")
    suspend fun togglePlay(): Response<Unit>

    @POST("api/v1/like")
    suspend fun like(): Response<Unit>

    @POST("api/v1/dislike")
    suspend fun dislike(): Response<Unit>

    @POST("api/v1/seek-to")
    suspend fun seekTo(@Body request: SecondsRequest): Response<Unit>

    @POST("api/v1/go-back")
    suspend fun goBack(@Body request: SecondsRequest): Response<Unit>

    @POST("api/v1/go-forward")
    suspend fun goForward(@Body request: SecondsRequest): Response<Unit>

    @GET("api/v1/shuffle")
    suspend fun getShuffleState(): Response<BooleanStateResponse>

    @POST("api/v1/shuffle")
    suspend fun shuffle(): Response<Unit>

    @GET("api/v1/repeat-mode")
    suspend fun getRepeatMode(): Response<RepeatModeResponse>

    @POST("api/v1/switch-repeat")
    suspend fun switchRepeat(@Body request: IterationRequest): Response<Unit>

    @POST("api/v1/volume")
    suspend fun setVolume(@Body request: VolumeRequest): Response<Unit>

    @GET("api/v1/volume")
    suspend fun getVolumeState(): Response<VolumeStateResponse>

    @POST("api/v1/fullscreen")
    suspend fun setFullscreen(@Body request: FullscreenRequest): Response<Unit>

    @GET("api/v1/fullscreen")
    suspend fun getFullscreenState(): Response<BooleanStateResponse>

    @POST("api/v1/toggle-mute")
    suspend fun toggleMute(): Response<Unit>

    @GET("api/v1/song")
    suspend fun getSong(): Response<SongResponse>

    @GET("api/v1/like-state")
    suspend fun getLikeState(): Response<LikeStateResponse>
}
