package com.example.autoaction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start the detection service
        val intent = android.content.Intent(this, ScreenshotDetectionService::class.java)
        startService(intent)
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    var permissionsGranted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    
    val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        launcher.launch(permissions)
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "AutoAction Active", style = MaterialTheme.typography.headlineMedium)
        if (permissionsGranted) {
            Text(text = "Permissions granted. Listening for screenshots...", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(text = "Please grant permissions to proceed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}
