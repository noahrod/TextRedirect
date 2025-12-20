package com.thenoahnoah.textredirect

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.thenoahnoah.textredirect.ui.theme.TextRedirectTheme

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required for SMS forwarding", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AppLogger.i("MainActivity", "App started")
        
        googleSignInClient = GoogleSignIn.getClient(this, GmailApiHelper.getGoogleSignInOptions(this))

        setContent {
            TextRedirectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MessageForwardingScreen(
                        onRequestPermissions = { requestPermissions() },
                        googleSignInClient = googleSignInClient
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.GET_ACCOUNTS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun MessageForwardingScreen(
    onRequestPermissions: () -> Unit,
    googleSignInClient: GoogleSignInClient
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Logs", "About")

    Scaffold(
        topBar = {
            Column {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 3.dp
                ) {
                    Text(
                        text = "TextRedirect",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 50.dp, bottom = 16.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeTab(onRequestPermissions, googleSignInClient)
                1 -> LogsTab()
                2 -> AboutTab()
            }
        }
    }
}

@Composable
fun HomeTab(
    onRequestPermissions: () -> Unit,
    googleSignInClient: GoogleSignInClient
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("TextRedirectPrefs", Context.MODE_PRIVATE)
    val gmailHelper = remember { GmailApiHelper(context) }

    var isServiceEnabled by remember {
        mutableStateOf(prefs.getBoolean("service_enabled", false))
    }

    var hasPermissions by remember {
        mutableStateOf(checkPermissions(context))
    }

    var isSignedIn by remember {
        mutableStateOf(gmailHelper.isSignedIn())
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d("MainActivity", "Sign in successful: ${account?.email}")
            Log.d("MainActivity", "Granted scopes: ${account?.grantedScopes}")

            // Force UI update
            isSignedIn = true

            Toast.makeText(context, "Signed in successfully as ${account?.email}", Toast.LENGTH_SHORT).show()
        } catch (e: ApiException) {
            Log.e("MainActivity", "Sign in failed with status code: ${e.statusCode}", e)
            val errorMessage = when (e.statusCode) {
                10 -> "Developer Error: Check your OAuth 2.0 setup.\n" +
                      "1. Create Android OAuth client in Google Cloud Console\n" +
                      "2. Use SHA-1: 06:AA:BC:A2:34:A5:8B:F1:61:22:35:AB:A9:21:4B:46:12:46:7B:CF\n" +
                      "3. Package: com.thenoahnoah.textredirect\n" +
                      "4. Enable Gmail API\n" +
                      "5. Wait 5-10 minutes after setup"
                12501 -> "Sign in cancelled"
                7 -> "Network error"
                else -> "Sign in failed: Error ${e.statusCode}"
            }
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }
    
    // Recheck permissions and sign-in status
    LaunchedEffect(Unit) {
        hasPermissions = checkPermissions(context)
        isSignedIn = gmailHelper.isSignedIn()
        
        // Start monitoring service if already enabled
        if (isServiceEnabled && isSignedIn && hasPermissions) {
            val serviceIntent = Intent(context, SmsMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_email),
            contentDescription = "Email Icon",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "TextRedirect",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Forward SMS/RCS messages to your Gmail",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Sign-in status card
        if (!isSignedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ Not Signed In",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You need to sign in with Google to forward SMS/RCS messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            // Request Gmail scope explicitly before sign-in
                            val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(
                                    com.google.android.gms.common.api.Scope("https://www.googleapis.com/auth/gmail.send")
                                )
                                .build()
                            val signInClient = GoogleSignIn.getClient(context as ComponentActivity, signInOptions)
                            signInLauncher.launch(signInClient.signInIntent)
                        }
                    ) {
                        Text("Sign in with Google")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Show user email if signed in
            gmailHelper.getUserEmail()?.let { email ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Signed in as:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SMS/RCS Forwarding",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Switch(
                        checked = isServiceEnabled,
                        enabled = isSignedIn && hasPermissions,
                        onCheckedChange = { enabled ->
                            val currentPermissions = checkPermissions(context)
                            hasPermissions = currentPermissions
                            
                            if (!isSignedIn) {
                                signInLauncher.launch(googleSignInClient.signInIntent)
                            } else if (!currentPermissions) {
                                onRequestPermissions()
                            } else {
                                isServiceEnabled = enabled
                                prefs.edit().putBoolean("service_enabled", enabled).apply()
                                
                                if (enabled) {
                                    AppLogger.i("MainActivity", "SMS forwarding enabled")
                                } else {
                                    AppLogger.i("MainActivity", "SMS forwarding disabled")
                                }
                                
                                // Start or stop the monitoring service
                                val serviceIntent = Intent(context, SmsMonitorService::class.java)
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent)
                                    } else {
                                        context.startService(serviceIntent)
                                    }
                                } else {
                                    context.stopService(serviceIntent)
                                }
                                
                                Toast.makeText(
                                    context,
                                    if (enabled) "SMS forwarding enabled" else "SMS forwarding disabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when {
                        !isSignedIn -> "Please sign in with Google to enable forwarding."
                        !hasPermissions -> "Please grant required permissions."
                        isServiceEnabled -> "✓ Service is active. SMS messages will be forwarded to your Gmail."
                        else -> "Service is inactive. Enable to start forwarding SMS messages."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!hasPermissions) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Permissions")
            }
        }
        
        if (isSignedIn) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = {
                    googleSignInClient.signOut().addOnCompleteListener {
                        isSignedIn = false
                        isServiceEnabled = false
                        prefs.edit().putBoolean("service_enabled", false).apply()
                        Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Out")
            }
        }
    }
}

@Composable
fun LogsTab() {
    val logs = AppLogger.logs
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity Logs (${logs.size})",
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = { AppLogger.clear() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Clear")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No logs yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs) { log ->
                    LogEntryCard(log)
                }
            }
        }
    }
}

@Composable
fun LogEntryCard(log: LogEntry) {
    val backgroundColor = when (log.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
        LogLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    
    val contentColor = when (log.level) {
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        LogLevel.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
        LogLevel.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = log.tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Text(
                    text = log.formattedTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun AboutTab() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_dialog_info),
            contentDescription = "Info Icon",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "TextRedirect",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Version 0.80",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                
            ) {
                Text(
                    text = "Developed by",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Noe Rodriguez",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Forward SMS/RCS messages to Gmail",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/noahrod"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFDD00),
                contentColor = Color.Black
            )
        ) {
            Text(
                text = "☕ Buy Me a Coffee",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "If you find this app helpful, consider supporting its development!",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun checkPermissions(context: Context): Boolean {
    val requiredPermissions = mutableListOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.GET_ACCOUNTS
    )
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    
    return requiredPermissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}

@Preview(showBackground = true)
@Composable
fun LogsTabPreview() {
    // Add some sample logs
    AppLogger.i("MessageNotifListener", "New message notification received")
    AppLogger.i("MessageNotifListener", "Forwarding message from +1234567890")
    AppLogger.i("MessageForwardingService", "✓ Message forwarded successfully to user@gmail.com")
    AppLogger.d("MessageNotifListener", "Skipping duplicate message")
    AppLogger.e("MessageForwardingService", "Failed to forward message: Network error")
    
    TextRedirectTheme {
        LogsTab()
    }
}

@Preview(showBackground = true)
@Composable
fun LogEntryPreview() {
    TextRedirectTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogEntryCard(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = LogLevel.INFO,
                    tag = "MainActivity",
                    message = "SMS forwarding enabled"
                )
            )
            LogEntryCard(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = LogLevel.ERROR,
                    tag = "MessageForwardingService",
                    message = "Failed to forward message: Network error"
                )
            )
            LogEntryCard(
                LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = LogLevel.DEBUG,
                    tag = "SmsMonitor",
                    message = "Skipping duplicate message"
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mockGoogleSignInClient = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
    )
    
    TextRedirectTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MessageForwardingScreen(
                onRequestPermissions = {},
                googleSignInClient = mockGoogleSignInClient
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AboutTabPreview() {
    TextRedirectTheme {
        AboutTab()
    }
}
