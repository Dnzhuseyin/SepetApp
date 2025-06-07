package com.example.sepetapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

// --- Data Models ---
enum class SepetDurumu {
    BOS, DOLU, KULLANIMDA
}

data class Sepet(
    val id: String,
    var durum: SepetDurumu,
    val items: MutableList<String> = mutableListOf()
)

// --- Main Activity ---
class MainActivity : ComponentActivity() {

    private val sepetler = mutableStateMapOf(
        "SEPET001" to Sepet(id = "SEPET001", durum = SepetDurumu.DOLU, items = mutableListOf("Elma", "Ekmek")),
        "SEPET002" to Sepet(id = "SEPET002", durum = SepetDurumu.BOS),
        "SEPET003" to Sepet(id = "SEPET003", durum = SepetDurumu.KULLANIMDA, items = mutableListOf("Süt")),
        "SEPET004" to Sepet(id = "SEPET004", durum = SepetDurumu.BOS)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SepetAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SepetAppNavigator(sepetler)
                }
            }
        }
    }
}

// --- Navigation ---
@Composable
fun SepetAppNavigator(sepetler: MutableMap<String, Sepet>) {
    var seciliSepetId by remember { mutableStateOf<String?>(null) }

    AnimatedContent(targetState = seciliSepetId, label = "navigation") { targetId ->
        if (targetId == null) {
            QrCodeScannerScreen(
                onSepetBulundu = { id ->
                    if (sepetler.containsKey(id)) {
                        seciliSepetId = id
                    }
                }
            )
        } else {
            val sepet = sepetler[targetId]!!
            DetayEkrani(
                sepet = sepet,
                onGeriDon = { seciliSepetId = null },
                onUrunEkle = { urunAdi ->
                    sepet.items.add(urunAdi)
                    sepet.durum = SepetDurumu.DOLU
                },
                onUrunSil = { urunAdi ->
                    sepet.items.remove(urunAdi)
                    if (sepet.items.isEmpty()) {
                        sepet.durum = SepetDurumu.BOS
                    }
                }
            )
        }
    }
}

// --- QR Code Scanner ---
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        imageProxy.image?.let { image ->
            val inputImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { qrCode ->
                        onQrCodeScanned(qrCode)
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }
}

@Composable
fun QrCodeScannerScreen(onSepetBulundu: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { context ->
                    val previewView = PreviewView(context)
                    val preview = Preview.Builder().build()
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(previewView.width, previewView.height))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    imageAnalysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        QrCodeAnalyzer { result ->
                            onSepetBulundu(result)
                        }
                    )
                    try {
                        cameraProviderFuture.get().bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lütfen kamera iznini verin.",
                    textAlign = TextAlign.Center
                )
            }
        }
        Text(
            text = "Lütfen sepetin QR kodunu okutun.",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )
    }
}


// --- Detail Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetayEkrani(
    sepet: Sepet,
    onGeriDon: () -> Unit,
    onUrunEkle: (String) -> Unit,
    onUrunSil: (String) -> Unit
) {
    var eklenecekUrun by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sepet Detayı: ${sepet.id}") },
                navigationIcon = {
                    IconButton(onClick = onGeriDon) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri Dön")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Sepet Durumu
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Durum: ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = sepet.durum.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = when (sepet.durum) {
                            SepetDurumu.BOS -> Color(0xFF4CAF50)
                            SepetDurumu.DOLU -> Color(0xFFF44336)
                            SepetDurumu.KULLANIMDA -> Color(0xFFFF9800)
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ürün Ekleme Alanı
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = eklenecekUrun,
                    onValueChange = { eklenecekUrun = it },
                    label = { Text("Yeni Ürün Ekle") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (eklenecekUrun.isNotBlank()) {
                            onUrunEkle(eklenecekUrun)
                            eklenecekUrun = ""
                        }
                    },
                    enabled = eklenecekUrun.isNotBlank(),
                    modifier = Modifier
                        .size(56.dp)
                        .padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ürün Ekle", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ürün Listesi
            Text("İçindekiler", style = MaterialTheme.typography.titleLarge)
            
            if (sepet.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Bu sepet boş.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sepet.items) { urun ->
                        UrunSatiri(urun = urun, onUrunSil = { onUrunSil(urun) })
                    }
                }
            }
        }
    }
}

@Composable
fun UrunSatiri(urun: String, onUrunSil: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = urun, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onUrunSil) {
                Icon(Icons.Default.Delete, contentDescription = "Ürünü Sil", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// --- Previews ---
@Preview(showBackground = true)
@Composable
fun AramaEkraniPreview() {
    SepetAppTheme {
       Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
           Text("QR Code Scanner will be here.")
       }
    }
}

@Preview(showBackground = true)
@Composable
fun DetayEkraniPreview() {
    SepetAppTheme {
        val previewSepet = Sepet(
            id = "SEPET-PREVIEW",
            durum = SepetDurumu.DOLU,
            items = mutableListOf("Süt", "Ekmek", "Peynir")
        )
        DetayEkrani(sepet = previewSepet, onGeriDon = {}, onUrunEkle = {}, onUrunSil = {})
    }
}