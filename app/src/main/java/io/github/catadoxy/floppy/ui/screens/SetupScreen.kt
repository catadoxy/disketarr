package io.github.catadoxy.floppy.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.catadoxy.floppy.viewmodel.SetupUiState
import io.github.catadoxy.floppy.viewmodel.SetupViewModel

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
) {
    val viewModel: SetupViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var baseUrl by remember { mutableStateOf(viewModel.baseUrl) }
    var apiToken by remember { mutableStateOf(viewModel.apiToken) }

    LaunchedEffect(uiState) {
        // Reflect the normalized URL back into the field after the check completes.
        if (uiState !is SetupUiState.Testing) {
            baseUrl = viewModel.baseUrl
        }
        if (uiState is SetupUiState.Success) {
            onSetupComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Connect Floppy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Connect to your server to browse and sync your library.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = {
                baseUrl = it
                viewModel.baseUrl = it
            },
            label = { Text("Server URL") },
            placeholder = { Text("192.168.1.10:8000 or https://floppy.example.com") },
            supportingText = {
                Text("HTTP and HTTPS are supported. LAN addresses default to HTTP.")
            },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = apiToken,
            onValueChange = {
                apiToken = it
                viewModel.apiToken = it
            },
            label = { Text("API token") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { viewModel.saveAndTest() },
            enabled = uiState !is SetupUiState.Testing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            if (uiState is SetupUiState.Testing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 6.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = if (uiState is SetupUiState.Testing) "Connecting…" else "Connect",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (uiState is SetupUiState.Error) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    text = (uiState as SetupUiState.Error).message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "Create an App token in Floppy under Settings → Integrations → App tokens. The default Tracking permissions are sufficient.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

