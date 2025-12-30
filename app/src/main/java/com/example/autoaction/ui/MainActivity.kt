package com.example.autoaction.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.autoaction.service.ScreenshotService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoActionTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun AutoActionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFBB86FC),
            secondary = Color(0xFF03DAC5),
            background = Color(0xFF121212),
            surface = Color(0xFF1E1E1E),
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(checkPermissions(context)) }

    // Launcher for permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all granted
        val allGranted = permissions.values.all { it }
        hasPermissions = allGranted
        if (allGranted) {
            startService(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (hasPermissions) {
                YouAreSetScreen()
                LaunchedEffect(Unit) {
                     startService(context)
                }
            } else {
                SetupScreen(
                    onRequestPermissions = {
                        val permissionsToRequest = mutableListOf<String>()
                        
                        // Storage
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                        } else {
                            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }

                        // Notifications
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    }
                )
            }
        }
    }
}

@Composable
fun SetupScreen(onRequestPermissions: () -> Unit) {
    Text(
        text = "AutoAction",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Zero friction. Detected screenshots, relevant actions.",
        textAlign = TextAlign.Center,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(48.dp))
    Button(
        onClick = onRequestPermissions,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text("Enable AutoAction", fontSize = 18.sp)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Requires Storage access to see screenshots and Notification access to show actions.",
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

@Composable
fun YouAreSetScreen() {
    Icon(
        imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle, // Using Icons.Default requires material-icons-extended usually or basic. CheckCircle is in basic usually? No, it's in Filled.
        // Actually better to handle icon dependency safely or use text if unsure. 
        // Composing material3 usually includes basic icons.
        contentDescription = "Success",
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(64.dp)
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "You're Set!",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "AutoAction is running in the background.\nTake a screenshot to test locally.",
        textAlign = TextAlign.Center,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    )
}

fun checkPermissions(context: android.content.Context): Boolean {
    val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
    
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    return storage && notifications
}

fun startService(context: android.content.Context) {
    val intent = Intent(context, ScreenshotService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
