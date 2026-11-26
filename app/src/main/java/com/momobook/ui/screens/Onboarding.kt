package com.momobook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momobook.ui.components.MomoLogo
import com.momobook.ui.theme.Brand

@Composable
fun OnboardingScreen(onGrant: () -> Unit, onSkip: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Navy, Color(0xFF0A0E16))))
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            MomoLogo(size = 112.dp)
            Spacer(Modifier.height(28.dp))
            Text("MomoBook", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your mobile money and bank SMS, journaled automatically.\nSee where every shilling goes.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = .72f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Feature(Icons.Rounded.Sms, "Reads your mobile money and bank SMS", "Deposits, payments and fees are picked up automatically.")
            Feature(Icons.Rounded.Lock, "Stays on your phone", "Everything is stored locally. Nothing is uploaded.")
            Feature(Icons.Rounded.History, "Never lose history", "Each SMS is copied into your journal, even if you delete it later.")
            Spacer(Modifier.weight(1.2f))
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand.Yellow, contentColor = Brand.Ink),
            ) {
                Icon(Icons.Rounded.Sms, null); Spacer(Modifier.width(10.dp))
                Text("Allow SMS access", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSkip) {
                Text("Paste a message instead", color = Color.White.copy(alpha = .8f))
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .08f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = Brand.Yellow) }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .65f))
        }
    }
}
