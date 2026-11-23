package com.liquidtv.app.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Streaming
import retrofit2.http.Url

interface IptvApiService {
    @GET
    @Streaming
    suspend fun fetchPlaylist(
        @Url url: String,
        @HeaderMap headers: Map<String, String> = emptyMap(),
    ): Response<ResponseBody>

    @GET
    @Streaming
    suspend fun fetchText(
        @Url url: String,
        @HeaderMap headers: Map<String, String> = emptyMap(),
    ): Response<ResponseBody>
}
