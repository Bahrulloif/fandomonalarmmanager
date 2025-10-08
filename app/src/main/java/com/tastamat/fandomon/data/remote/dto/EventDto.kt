package com.tastamat.fandomon.data.remote.dto

import com.google.gson.annotations.SerializedName

data class EventDto(
    @SerializedName("id")
    val id: Long,
    @SerializedName("event_type")
    val eventType: String,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("message")
    val message: String?,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_name")
    val deviceName: String
)

data class StatusDto(
    @SerializedName("fandomon_running")
    val fandomonRunning: Boolean,
    @SerializedName("fandomat_running")
    val fandomatRunning: Boolean,
    @SerializedName("internet_connected")
    val internetConnected: Boolean,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("device_name")
    val deviceName: String
)
