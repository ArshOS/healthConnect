package com.example.healthconnect

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class HealthUiState(
    val sdkStatus: Int = HealthConnectClient.SDK_UNAVAILABLE,
    val isLoading: Boolean = false,
    val error: String? = null,
    val heartRate: Int? = null,
    val steps: Long? = null,
    val sleepHours: Double? = null,
    val permissionsGranted: Boolean = false,
)

class HealthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    fun checkPermissions() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val status = HealthConnectClient.getSdkStatus(context)
            _uiState.update { it.copy(sdkStatus = status) }

            if (status == HealthConnectClient.SDK_AVAILABLE) {
                val client = HealthConnectClient.getOrCreate(context)
                val granted = client.permissionController.getGrantedPermissions()
                val hasAll = granted.containsAll(permissions)

                _uiState.update { it.copy(permissionsGranted = hasAll) }

                if (hasAll) {
                    loadHealthData()
                }
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        val hasAll = granted.containsAll(permissions)
        _uiState.update {
            it.copy(permissionsGranted = hasAll)
        }

        if (hasAll) {
            loadHealthData()
        }
    }

    fun loadHealthData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val context = getApplication<Application>()
                if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Health Connect is not available on this device"
                        )
                    }
                    return@launch
                }

                val client = HealthConnectClient.getOrCreate(context)

                val now = Instant.now()
                val startOfDay = ZonedDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val last24Hours = now.minus(24, ChronoUnit.HOURS)

                val heartRate = readLatestHeartRate(client, last24Hours, now)
                val steps = readTodaySteps(client, startOfDay, now)
                val sleep = readLastNightSleep(client)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        heartRate = heartRate,
                        steps = steps,
                        sleepHours = sleep
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load health data: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    private suspend fun readLatestHeartRate(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Int? {
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response.records.lastOrNull()?.samples?.lastOrNull()?.beatsPerMinute?.toInt()
    }

    private suspend fun readTodaySteps(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Long? {
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response[StepsRecord.COUNT_TOTAL]
    }

    private suspend fun readLastNightSleep(client: HealthConnectClient): Double? {
        val now = Instant.now()
        val yesterday = now.minus(24, ChronoUnit.HOURS)

        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(yesterday, now)
            )
        )

        return response.records.lastOrNull()?.let { session ->
            Duration.between(session.startTime, session.endTime).toMinutes() / 60.0
        }
    }
}