package com.example.healthconnect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.healthconnect.ui.theme.HealthConnectTheme
import java.util.Locale

@Composable
fun HealthScreen(
    modifier: Modifier = Modifier,
    viewModel: HealthViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.onPermissionsResult(granted)
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    HealthScreenContent(
        uiState = uiState,
        onGrantPermissions = { permissionLauncher.launch(viewModel.permissions) },
        onRefreshData = { viewModel.loadHealthData() },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreenContent(
    uiState: HealthUiState,
    onGrantPermissions: () -> Unit,
    onRefreshData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Health Connect Dashboard",
                    fontWeight = FontWeight.Bold
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.sdkStatus == HealthConnectClient.SDK_UNAVAILABLE -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Health Connect Unavailable",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Health Connect is not supported or unavailable on this device.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            uiState.sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Update Required",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Health Connect app requires an update or needs to be installed from the Play Store.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            !uiState.permissionsGranted -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Permissions Needed",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Allow access to Heart Rate, Steps, and Sleep data to view your health metrics.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onGrantPermissions) {
                            Text("Grant Health Permissions")
                        }
                    }
                }
            }

            else -> {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                } else {
                    uiState.error?.let { err ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = err,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    HealthMetricCard(
                        title = "Steps Today",
                        value = uiState.steps?.let { "$it steps" } ?: "No data available"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HealthMetricCard(
                        title = "Latest Heart Rate",
                        value = uiState.heartRate?.let { "$it bpm" } ?: "No data available"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    HealthMetricCard(
                        title = "Sleep (Last Night)",
                        value = uiState.sleepHours?.let {
                            String.format(Locale.getDefault(), "%.1f hrs", it)
                        } ?: "No data available"
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = onRefreshData,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Data")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HealthScreenPreview() {
    HealthConnectTheme {
        HealthScreenContent(
            uiState = HealthUiState(
                sdkStatus = HealthConnectClient.SDK_AVAILABLE,
                permissionsGranted = true,
                isLoading = false,
                heartRate = 72,
                steps = 8450L,
                sleepHours = 7.5
            ),
            onGrantPermissions = {},
            onRefreshData = {}
        )
    }
}
