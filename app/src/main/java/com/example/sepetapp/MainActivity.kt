package com.example.sepetapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sepetapp.ui.theme.SepetAppTheme

class MainActivity : ComponentActivity() {

    // Sepet kodları ve durumları için sabit veri yapısı (Map)
    private val cartData = mapOf(
        "SEPET001" to "Durum: Dolu",
        "SEPET002" to "Durum: Boş",
        "SEPET003" to "Durum: Kullanımda",
        "SEPET004" to "Durum: Boş"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SepetAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SepetScreen(
                        modifier = Modifier.padding(innerPadding),
                        cartData = cartData
                    )
                }
            }
        }
    }
}

@Composable
fun SepetScreen(modifier: Modifier = Modifier, cartData: Map<String, String>) {
    var cartIdInput by remember { mutableStateOf("") }
    var cartInfo by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isButtonEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = cartIdInput,
            onValueChange = {
                cartIdInput = it
                // Kullanıcı yazmaya başladığında mesajları temizle
                errorMessage = null
                cartInfo = null
                isButtonEnabled = true
            },
            label = { Text("Sepet Kodunu Girin") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val enteredCode = cartIdInput.trim().uppercase()
                if (cartData.containsKey(enteredCode)) {
                    cartInfo = cartData[enteredCode]
                    errorMessage = null
                } else {
                    errorMessage = "Sepet kodu bulunamadı!"
                    cartInfo = null
                }
                isButtonEnabled = false // Butonu devre dışı bırak
            },
            enabled = isButtonEnabled && cartIdInput.isNotBlank()
        ) {
            Text("Sepeti Göster")
        }

        Spacer(modifier = Modifier.height(24.dp))

        cartInfo?.let {
            Text(
                text = it,
                fontSize = 18.sp,
                color = Color.Black
            )
        }

        errorMessage?.let {
            Text(
                text = it,
                fontSize = 16.sp,
                color = Color.Red
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SepetScreenPreview() {
    SepetAppTheme {
        SepetScreen(
            cartData = mapOf(
                "SEPET001" to "Durum: Dolu",
                "SEPET002" to "Durum: Boş"
            )
        )
    }
}