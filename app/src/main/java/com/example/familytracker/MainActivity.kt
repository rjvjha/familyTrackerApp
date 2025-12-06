package com.example.familytracker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.familytracker.location.LocationService
import com.example.familytracker.location.TdLibManager
import com.example.familytracker.location.TdLibManager.AuthState
import com.example.familytracker.receiver.BootRestartReceiver
import com.example.familytracker.ui.theme.FamilyTrackerTheme
import kotlin.apply

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle device restart flag from boot notification
        val deviceRestarted = intent?.getBooleanExtra(BootRestartReceiver.EXTRA_DEVICE_RESTARTED, false) ?: false

        setContent {
            FamilyTrackerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    LocationTrackingScreen(
                        modifier = Modifier.padding(innerPadding),
                        deviceRestarted = deviceRestarted
                    )
                }
            }
        }
    }
}

@Composable
fun ModernGradientBackground() {
    val primaryColor = MaterialTheme.colorScheme.primaryContainer
    val backgroundColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current

    // Check if drawable resource exists
    val hasBackgroundImage = remember {
        try {
            context.resources.getIdentifier("family_background1", "drawable", context.packageName) != 0
        } catch (e: Exception) {
            false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (hasBackgroundImage) {
            Image(
                painter = painterResource(id = R.drawable.family_background1),
                contentDescription = "Family Background",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RectangleShape),
                contentScale = ContentScale.FillBounds,
                alpha = 0.95f
            )
        } else {
            // Fallback to gradient if image not found
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.4f),
                                backgroundColor
                            ),
                            startY = 0f,
                            endY = 1200f
                        )
                    )
            )
        }
    }
}

@Composable
fun HeroIcon(authState: AuthState, isTracking: Boolean) {
    val (icon, tint) = when {
        isTracking -> Icons.Rounded.PinDrop to MaterialTheme.colorScheme.secondary
        authState == AuthState.READY -> Icons.Rounded.Shield to MaterialTheme.colorScheme.error
        else -> Icons.Rounded.Map to MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Status Icon",
            modifier = Modifier.size(56.dp),
            tint = tint
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LocationTrackingScreen(modifier: Modifier = Modifier, deviceRestarted: Boolean = false) {
    val context = LocalContext.current
    val authState by TdLibManager.authState.collectAsState()
    val targetChatId by TdLibManager.targetChatId.collectAsState()

    // UI State
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var targetUsername by remember { mutableStateOf("") }
    var isResolvingUsername by remember { mutableStateOf(false) }

    val sharedPref = context.getSharedPreferences("tracking_prefs", Context.MODE_PRIVATE)
    val tdlibSharedPref = context.getSharedPreferences(TdLibManager.PREFS_NAME, Context.MODE_PRIVATE)
    var isTracking by remember {
        mutableStateOf(sharedPref.getBoolean("is_tracking", false))
    }
    var familyPersonName by remember {
        mutableStateOf(sharedPref.getString("family_person_name", "") ?: "")
    }

    // Foreground location permissions
    val foregroundPermissions = remember {
        mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
    // Launcher for background location permission (Android 10+)
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = perms[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false
            if (backgroundGranted) {
                startLocationService(context, familyPersonName)
            } else {
                Toast.makeText(context, "Background location permission is required for continuous tracking", Toast.LENGTH_LONG).show()
                // Still start service with foreground location only
                startLocationService(context, familyPersonName)
            }
        }
    }

    // Launcher for foreground permissions
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fineLocationGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted && coarseLocationGranted) {
            // Foreground permissions granted, now check for background
            checkBackgroundLocation(context, familyPersonName, backgroundPermissionLauncher)
        } else {
            Toast.makeText(context, "Location permissions are required for tracking", Toast.LENGTH_LONG).show()
        }
    }


    // Persist familyPersonName to SharedPreferences whenever it changes
    LaunchedEffect(familyPersonName) {
        if (familyPersonName.isNotEmpty()) {
            sharedPref.edit().putString("family_person_name", familyPersonName).apply()
        }
    }

    // Restart LocationService if it was tracking before (e.g., after device restart)
    LaunchedEffect(Unit) {
        if (isTracking && familyPersonName.isNotEmpty()) {
            val hasForeground = foregroundPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            
            if (hasForeground) {
                // Foreground permissions already granted, check for background
                checkBackgroundLocation(context, familyPersonName, backgroundPermissionLauncher)
            } else {
                // Request foreground permissions first
                foregroundPermissionLauncher.launch(foregroundPermissions)
            }
            isTracking = true
            sharedPref.edit().putBoolean("is_tracking", true).apply()
        }
    }

    // Handle device restart notification click
    LaunchedEffect(deviceRestarted) {
        if (deviceRestarted && isTracking && familyPersonName.isNotEmpty()) {
            val hasForeground = foregroundPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            
            if (hasForeground) {
                // Foreground permissions already granted, check for background
                checkBackgroundLocation(context, familyPersonName, backgroundPermissionLauncher)
                Toast.makeText(context, "Location tracking resumed", Toast.LENGTH_SHORT).show()
            } else {
                // Request foreground permissions first
                foregroundPermissionLauncher.launch(foregroundPermissions)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ModernGradientBackground()
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- HEADER SECTION ---
            HeroIcon(authState, isTracking)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Family Tracker",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            val statusText = when {
                isTracking -> "Share Locations with Family"
                authState == AuthState.READY -> "Secure Connection Established"
                else -> "Setup Secure Connection"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(40.dp))

            // --- MAIN CONTENT SURFACE ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = authState,
                        transitionSpec = {
                            fadeIn() + slideInVertically { height -> height } togetherWith
                                    fadeOut() + slideOutVertically { height -> -height }
                        }
                    ) { state ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            when (state) {
                                AuthState.UNKNOWN, AuthState.WAIT_PARAMETERS -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(48.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 4.dp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Initializing Engine...", style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                AuthState.WAIT_PHONE_NUMBER -> {
                                    Text("Verify Telegram Account", style = MaterialTheme.typography.headlineSmall)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    ModernTextField(
                                        value = phoneNumber,
                                        onValueChange = { phoneNumber = it },
                                        label = "Phone Number",
                                        icon = Icons.Default.Phone,
                                        keyboardType = KeyboardType.Phone,
                                        placeholder = "+919876543210"
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    PrimaryButton(text = "Send Code") {
                                        TdLibManager.sendPhoneNumber(phoneNumber)
                                    }
                                }

                                AuthState.WAIT_CODE -> {
                                    Text("Security Code", style = MaterialTheme.typography.headlineSmall)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    ModernTextField(
                                        value = code,
                                        onValueChange = { code = it },
                                        label = "Enter Code",
                                        icon = Icons.Default.Lock,
                                        keyboardType = KeyboardType.Number
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    PrimaryButton(text = "Verify") {
                                        TdLibManager.sendAuthCode(code)
                                    }
                                }

                                AuthState.WAIT_PASSWORD -> {
                                    Text("Two-Step Verification", style = MaterialTheme.typography.headlineSmall)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    ModernTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = "Cloud Password",
                                        icon = Icons.Default.VpnKey,
                                        isPassword = true
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    PrimaryButton(text = "Unlock") {
                                        TdLibManager.sendPassword(password)
                                    }
                                }

                                AuthState.READY -> {
                                    if (targetChatId == 0L) {
                                        Text("Select Recipient", style = MaterialTheme.typography.headlineSmall)
                                        Text(
                                            "Who will receive the location?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        ModernTextField(
                                            value = targetUsername,
                                            onValueChange = { targetUsername = it },
                                            label = "Username (e.g. @jack)",
                                            icon = Icons.Default.Person
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))

                                        if (isResolvingUsername) {
                                            CircularProgressIndicator()
                                        } else {
                                            PrimaryButton(text = "Connect") {
                                                if (targetUsername.isNotBlank()) {
                                                    isResolvingUsername = true
                                                    TdLibManager.resolveUsername(
                                                        targetUsername,
                                                        onSuccess = {
                                                            isResolvingUsername = false
                                                            Toast.makeText(context, "Connected!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onError = {
                                                            isResolvingUsername = false
                                                            Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // TRACKING DASHBOARD
                                        if (!isTracking) {
                                            ModernTextField(
                                                value = familyPersonName,
                                                onValueChange = { familyPersonName = it },
                                                label = "Give yourself a name!",
                                                icon = Icons.Default.Face
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))

                                            PrimaryButton(text = "Start Sharing Location") {
                                                if (familyPersonName.isNotBlank()) {
                                                    val hasForeground = foregroundPermissions.all {
                                                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                                    }
                                                    
                                                    if (hasForeground) {
                                                        // Foreground permissions already granted, check for background
                                                        checkBackgroundLocation(context, familyPersonName, backgroundPermissionLauncher)
                                                    } else {
                                                        // Request foreground permissions first
                                                        foregroundPermissionLauncher.launch(foregroundPermissions)
                                                    }
                                                    sharedPref.edit().putBoolean("is_tracking", true).apply()
                                                    isTracking = true
                                                } else {
                                                    Toast.makeText(context, "Enter name first", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } else {
                                            Text(
                                                "Tracking is Active",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                "Sharing as $familyPersonName",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(modifier = Modifier.height(24.dp))

                                            Button(
                                                onClick = {
                                                    stopLocationService(context)
                                                    isTracking = false
                                                    sharedPref.edit().putBoolean("is_tracking", false).apply()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(56.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Text("Stop Sharing", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            TextButton(onClick = { TdLibManager.setTargetChat(0L) }) {
                                                Text("Change Recipient")
                                            }
                                            TextButton(onClick = { TdLibManager.logOut() }) {
                                                Text("Log Out", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }

                                AuthState.LOGGING_OUT -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Secure Logout...")
                                    }
                                    isTracking = false
                                    sharedPref.edit { putBoolean("is_tracking", false) }
                                    sharedPref.edit { clear() }
                                    tdlibSharedPref.edit { clear() }
                                    stopLocationService(context)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- REUSABLE MODERN COMPONENTS ---

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) { { Text(placeholder, color = MaterialTheme.colorScheme.outlineVariant) } } else null,
        leadingIcon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun startLocationService(context: Context, familyPersonName: String) {
    val intent = Intent(context, LocationService::class.java).apply {
        action = LocationService.ACTION_START
        putExtra("EXTRA_FAMILY_PERSON_NAME", familyPersonName)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    Toast.makeText(context, "Tracking started for $familyPersonName", Toast.LENGTH_SHORT).show()
}

fun stopLocationService(context: Context) {
    val intent = Intent(context, LocationService::class.java).apply {
        action = LocationService.ACTION_STOP
    }
    context.startService(intent)
    Toast.makeText(context, "Location Service Stopped", Toast.LENGTH_SHORT).show()
}

/**
 * Helper function to check and request background location permission.
 * Implements the check-first pattern:
 * 1. Check if we already have background location permission
 * 2. If yes, start the service
 * 3. If no, request it (on Android 10+)
 *
 * Handles version differences:
 * - Below Android 10: Background is granted with Fine Location
 * - Android 10 (Q): Can request with other permissions
 * - Android 11+ (R+): Must request separately
 */
fun checkBackgroundLocation(
    context: Context,
    familyPersonName: String,
    backgroundPermissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10 and above require this permission
        val backgroundPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        
        if (ContextCompat.checkSelfPermission(context, backgroundPermission) != PackageManager.PERMISSION_GRANTED) {
            // Background location not granted yet - request it
            // On Android 11+, this will open a system dialog taking the user to App Settings
            backgroundPermissionLauncher.launch(arrayOf(backgroundPermission))
            Toast.makeText(context, "Choose Allow all the time required for continuous tracking", Toast.LENGTH_LONG).show()
        } else {
            // Background location already granted
            startLocationService(context, familyPersonName)
        }
    } else {
        // Below Android 10, background is granted with Fine Location
        startLocationService(context, familyPersonName)
    }
}