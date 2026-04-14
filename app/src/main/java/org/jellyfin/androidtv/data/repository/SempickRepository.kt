package org.jellyfin.androidtv.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.jellyfin.androidtv.auth.repository.SessionRepository
import org.jellyfin.androidtv.ui.sempick.SempickResponse
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import timber.log.Timber
import java.net.URLEncoder

interface SempickRepository {
	suspend fun getItems(submittedSemSequences: List<String>): Result<SempickResponse>
}

class SempickRepositoryImpl(
	private val apiClient: ApiClient,
	private val sessionRepository: SessionRepository,
	private val okHttpFactory: OkHttpFactory,
	private val httpClientOptions: HttpClientOptions,
) : SempickRepository {
	private val json = Json { ignoreUnknownKeys = true }

	override suspend fun getItems(submittedSemSequences: List<String>): Result<SempickResponse> {
		val session = sessionRepository.currentSession.value
			?: return Result.failure(IllegalStateException("No active session"))
		val baseUrl = apiClient.baseUrl
			?: return Result.failure(IllegalStateException("No server URL configured"))

		val sequencesJson = URLEncoder.encode(json.encodeToString(submittedSemSequences), "UTF-8")
		val url = "$baseUrl/Sempick/Items?Limit=100000&userId=${session.userId}&submittedSemSequences=$sequencesJson"
		val authHeader = "Emby UserId=\"${session.userId}\", Client=\"Jellyfin Android TV\", " +
			"Device=\"AndroidTV\", DeviceId=\"sempick-androidtv\", Version=\"1.0\", " +
			"Token=\"${session.accessToken}\""

		return try {
			val client = okHttpFactory.createClient(httpClientOptions)
			val request = Request.Builder()
				.url(url)
				.header("X-Emby-Authorization", authHeader)
				.build()

			val body = withContext(Dispatchers.IO) {
				client.newCall(request).execute().use { response ->
					if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${response.message}")
					response.body?.string() ?: throw IllegalStateException("Empty response from server")
				}
			}

			Result.success(json.decodeFromString<SempickResponse>(body))
		} catch (e: Exception) {
			Timber.e(e, "Sempick API call failed")
			Result.failure(e)
		}
	}
}
