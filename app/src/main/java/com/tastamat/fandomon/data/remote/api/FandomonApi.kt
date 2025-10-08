package com.tastamat.fandomon.data.remote.api

import com.tastamat.fandomon.data.remote.dto.EventDto
import com.tastamat.fandomon.data.remote.dto.StatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface FandomonApi {

    @POST("events")
    suspend fun sendEvent(
        @Header("Authorization") apiKey: String,
        @Body event: EventDto
    ): Response<Unit>

    @POST("status")
    suspend fun sendStatus(
        @Header("Authorization") apiKey: String,
        @Body status: StatusDto
    ): Response<Unit>
}
