package com.example.syncro.ui.screens
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onSend: () -> Unit,
    onReceive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // 🔹 App Name (Centered, Stylish)
        Text(
            text = "Syncro",
            fontSize = 55.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.5.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Secure File Sharing",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 🔹 Send Button
        ActionCard(
            title = "Send",
            icon = Icons.Default.Send,
            onClick = onSend
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 Receive Button
        ActionCard(
            title = "Receive",
            icon = Icons.Default.Download,
            onClick = onReceive
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 🔹 Signature Text
        Text(
            text = "Made by Nalle Ninjas",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp,
            color = Color.White
        )
    }
}
@Composable
private fun ActionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF1F5F9)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,

                modifier = Modifier.size(28.dp),
                tint = Color(0xFF334155)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

