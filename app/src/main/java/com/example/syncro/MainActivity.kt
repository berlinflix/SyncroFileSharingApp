package com.example.syncro

import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.syncro.ui.theme.SyncroTheme

class MainActivity : ComponentActivity() {

    // Helper to trigger navigation after file is picked
    private var onFilePicked: (() -> Unit)? = null

    // 📂 Unified File Picker
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult

        val inputStream = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
        val fileBytes = inputStream.readBytes()
        inputStream.close()

        val fileName = FileUtils.getFileNameFromUri(this, uri)

        // 1. Save file to state
        TransferState.pendingFile = Pair(fileName, fileBytes)

        // 2. Trigger navigation
        onFilePicked?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Screen State Management
            var screen by remember { mutableStateOf("start") }
            val transferRole by TransferState.role.collectAsState()
            var targetDeviceName by remember { mutableStateOf("") }
            val context = LocalContext.current

            SyncroTheme {
                Box(modifier = Modifier.fillMaxSize()) {

                    // Background Animation
                    AnimatedBackground()

                    when {
                        // 1. SENDING PROGRESS
                        transferRole == TransferRole.SENDER -> {
                            TransferProgressScreen(
                                role = TransferRole.SENDER,
                                targetName = targetDeviceName,
                                onBack = {
                                    TransferState.reset()
                                    screen = "start"
                                },
                                onOpenFile = {
                                    TransferState.reset()
                                    screen = "start"
                                }
                            )
                        }

                        // 2. RECEIVING PROGRESS
                        transferRole == TransferRole.RECEIVER -> {
                            TransferProgressScreen(
                                role = TransferRole.RECEIVER,
                                targetName = "Sender",
                                onBack = {
                                    TransferState.reset()
                                    screen = "start"
                                },
                                onOpenFile = {
                                    val uri = TransferState.receivedFileUri.value
                                    if (uri != null) openFile(uri)
                                }
                            )
                        }

                        // 3. DEVICE SELECTION (Scanning)
                        screen == "devices" -> {
                            DisposableEffect(Unit) {
                                LocalDiscovery.startListening(this@MainActivity)
                                onDispose { LocalDiscovery.stopListening() }
                            }

                            DeviceSelectionScreen(
                                onDeviceSelected = { device ->
                                    targetDeviceName = device.name
                                    val fileData = TransferState.pendingFile
                                    if (fileData != null) {
                                        LocalSender.sendFile(
                                            receiverIp = device.ip,
                                            fileName = fileData.first,
                                            fileBytes = fileData.second
                                        )
                                    }
                                },
                                onBack = { screen = "mode_select" } // Go back to mode selection
                            )
                        }

                        // 4. MODE SELECTION (Local vs Internet)
                        screen == "mode_select" -> {
                            SendModeSelectionScreen(
                                onLocalSelected = {
                                    // Set the callback to go to "devices" after picking
                                    onFilePicked = { screen = "devices" }
                                    // Launch the file picker
                                    filePicker.launch("*/*")
                                },
                                onInternetSelected = {
                                    Toast.makeText(context, "Secure Internet Transfer coming soon!", Toast.LENGTH_SHORT).show()
                                },
                                onBack = { screen = "start" }
                            )
                        }

                        // 5. START SCREEN
                        screen == "start" -> StartScreen(
                            onSend = {
                                screen = "mode_select" // Go to mode selection first
                            },
                            onReceive = { screen = "receive" }
                        )

                        // 6. RECEIVE WAITING
                        screen == "receive" -> {
                            LaunchedEffect(Unit) {
                                LocalDiscovery.startBroadcasting()
                                LocalReceiver.startReceiving(this@MainActivity)
                            }
                            ReceiveScreen(
                                onBack = {
                                    LocalDiscovery.stopBroadcasting()
                                    LocalReceiver.stop()
                                    screen = "start"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun openFile(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val mimeType = contentResolver.getType(uri)
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }
}

// ==========================================
// 👇 UI COMPONENTS
// ==========================================

@Composable
fun AnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg_anim")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val offsetX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetX"
    )

    Image(
        painter = painterResource(id = R.drawable.download), // Ensure 'download' image exists
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
            }
    )
}

// 🆕 UPDATED: Ultra-Minimal "Space" Start Screen
@Composable
fun StartScreen(onSend: () -> Unit, onReceive: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 🔥 TITLE UPDATE: Thin, Wide, Ethereal
        Text(
            text = "SYNCRO",
            style = TextStyle(
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Thin, // 👈 KEY: Thin font for minimal look
                letterSpacing = 12.sp,        // 👈 KEY: Wide spacing
                shadow = Shadow(              // Subtle blue glow
                    color = Color(0xFF3B82F6).copy(alpha = 0.5f),
                    offset = Offset(0f, 0f),
                    blurRadius = 16f
                )
            )
        )

        Spacer(Modifier.height(16.dp))

        // Subtitle
        Text(
            text = "UNIVERSAL FILE SHARE",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
            letterSpacing = 4.sp, // Wide spacing to match title
            fontWeight = FontWeight.Light
        )

        Spacer(Modifier.height(100.dp))

        // Minimal Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Send Button
            MenuButton(
                text = "Send",
                icon = Icons.Default.ArrowUpward,
                onClick = onSend
            )

            // Receive Button
            MenuButton(
                text = "Receive",
                icon = Icons.Default.ArrowDownward,
                onClick = onReceive
            )
        }
    }
}

// 🆕 Helper Component for minimal buttons
@Composable
fun MenuButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0x22FFFFFF)) // Glassy, semi-transparent background
                .border(BorderStroke(1.dp, Color.White), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SendModeSelectionScreen(
    onLocalSelected: () -> Unit,
    onInternetSelected: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Transfer Mode", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(40.dp))

        // Local Network Button
        OutlinedButton(
            onClick = onLocalSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x223B82F6),
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFF3B82F6))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Local Network", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Fast • Offline • No Data", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Secure Internet Button
        OutlinedButton(
            onClick = onInternetSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x2210B981),
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color(0xFF10B981))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Secure Internet", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text("AES-256 Encrypted • (Coming Soon)", fontSize = 12.sp, color = Color.LightGray)
            }
        }

        Spacer(Modifier.height(40.dp))
        TextButton(onClick = onBack) { Text("Back", color = Color.Gray) }
    }
}

// 🆕 RADAR SCANNER ANIMATION COMPONENT
@Composable
fun RadarScanningAnimation() {
    val infiniteTransition = rememberInfiniteTransition("radar")

    // Rotation for the scanner tail
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    // Pulsing circle effect
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        // 1. Static & Pulsing Rings
        Canvas(modifier = Modifier.size(200.dp)) {
            val maxRadius = size.minDimension / 2

            // Static Rings (Grid)
            drawCircle(Color.White.copy(alpha = 0.1f), radius = maxRadius, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.1f), radius = maxRadius * 0.66f, style = Stroke(width = 2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.1f), radius = maxRadius * 0.33f, style = Stroke(width = 2.dp.toPx()))

            // Pulsing Ring (Expands & Fades)
            drawCircle(
                color = Color(0xFF3B82F6).copy(alpha = 1f - pulse),
                radius = maxRadius * pulse,
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // 2. Rotating Radar Sweep
        Canvas(modifier = Modifier
            .size(200.dp)
            .graphicsLayer { rotationZ = angle }
        ) {
            val maxRadius = size.minDimension / 2

            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color(0xFF3B82F6).copy(alpha = 0.5f) // Glowing Blue Tail
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = true,
                topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
            )
        }

        // 3. Center Wifi Icon
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

// 🆕 UPDATED RECEIVE SCREEN (Uses Radar Animation)
@Composable
fun ReceiveScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 👇 Replaced Static Icon with Radar Animation
        RadarScanningAnimation()

        Spacer(Modifier.height(40.dp))

        Text("Waiting for sender...", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Your device is visible to others", color = Color.Gray, fontSize = 14.sp)

        Spacer(Modifier.height(40.dp))
        TextButton(onClick = onBack) { Text("Back", color = Color.Gray) }
    }
}

@Composable
fun TransferProgressScreen(
    role: TransferRole,
    targetName: String,
    onBack: () -> Unit,
    onOpenFile: () -> Unit
) {
    val progress by TransferState.progress.collectAsState()
    val completed by TransferState.completed.collectAsState()
    val context = LocalContext.current

    // GIF Loader Configuration
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {

        // 1. Top Info
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (role == TransferRole.SENDER) "Sending to..." else "Receiving from...",
                color = Color.Gray,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = targetName.ifEmpty { "Unknown Device" },
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 2. Middle GIF Animation
        if (!completed) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Image(
                    painter = rememberAsyncImagePainter(
                        ImageRequest.Builder(context)
                            .data(R.drawable.transfer_anim)
                            .build(),
                        imageLoader = imageLoader
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp)
                )
            }
        }

        // 3. Bottom Progress Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .background(Color(0xFF020617), shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "$progress%", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = progress / 100f,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF3B82F6),
                trackColor = Color.DarkGray
            )
            Spacer(Modifier.height(24.dp))

            if (completed) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onBack) { Text("Close") }
                    if (role == TransferRole.RECEIVER) {
                        Button(onClick = onOpenFile) { Text("Open File") }
                    }
                }
            } else {
                Text("Please wait, keeping screen on...", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}