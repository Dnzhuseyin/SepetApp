package com.example.sepetapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sepetapp.ui.theme.*
import com.google.firebase.FirebaseApp
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    private val repository = SepetRepository()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Firebase'i initialize et
        FirebaseApp.initializeApp(this)
        
        enableEdgeToEdge()
        setContent {
            SepetAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SepetAppScreen(repository = repository)
                }
            }
        }
    }
}

// --- Main Screen with Firebase Integration ---
@Composable
fun SepetAppScreen(repository: SepetRepository) {
    val sepetler = remember { mutableStateMapOf<String, Sepet>() }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Firebase'den sepet verilerini dinle
    LaunchedEffect(repository) {
        try {
            repository.getSepetlerFlow().collectLatest { firestoreSepetler ->
                sepetler.clear()
                sepetler.putAll(firestoreSepetler)
                isLoading = false
                
                // Eğer hiç sepet yoksa demo verileri ekle
                if (firestoreSepetler.isEmpty()) {
                    scope.launch {
                        repository.initializeDemoData()
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "Veri yüklenirken hata: ${e.message}"
            isLoading = false
        }
    }

    // Loading durumu
    if (isLoading) {
        LoadingScreen()
        return
    }

    // Error durumu
    errorMessage?.let { error ->
        ErrorScreen(
            message = error,
            onRetry = {
                errorMessage = null
                isLoading = true
            }
        )
        return
    }

    // Ana navigasyon
    SepetAppNavigator(
        sepetler = sepetler,
        onUrunEkle = { sepetId, urunAdi ->
            scope.launch {
                val result = repository.addItemToSepet(sepetId, urunAdi)
                if (result.isFailure) {
                    errorMessage = "Ürün eklenirken hata: ${result.exceptionOrNull()?.message}"
                }
            }
        },
        onUrunSil = { sepetId, urunAdi ->
            scope.launch {
                val result = repository.removeItemFromSepet(sepetId, urunAdi)
                if (result.isFailure) {
                    errorMessage = "Ürün silinirken hata: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    )
}

// --- Loading Screen ---
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GradientStart, GradientEnd)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sepet verileri yükleniyor...",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// --- Error Screen ---
@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(StatusError.copy(alpha = 0.1f), StatusError.copy(alpha = 0.05f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = StatusError
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Bir Hata Oluştu",
                style = MaterialTheme.typography.headlineSmall,
                color = StatusError,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusError
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tekrar Dene")
            }
        }
    }
}

// --- Navigation ---
@Composable
fun SepetAppNavigator(
    sepetler: MutableMap<String, Sepet>,
    onUrunEkle: (String, String) -> Unit,
    onUrunSil: (String, String) -> Unit
) {
    var seciliSepetId by remember { mutableStateOf<String?>(null) }
    var showNotFoundDialog by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = seciliSepetId,
        transitionSpec = {
            if (targetState != null) {
                // Scanner to Detail
                slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
            } else {
                // Detail to Scanner
                slideInHorizontally { -it } + fadeIn() togetherWith
                slideOutHorizontally { it } + fadeOut()
            }.using(SizeTransform(clip = false))
        },
        label = "navigation"
    ) { targetId ->
        if (targetId == null) {
            QrCodeScannerScreen(
                onSepetBulundu = { id ->
                    if (sepetler.containsKey(id)) {
                        seciliSepetId = id
                    } else {
                        showNotFoundDialog = true
                    }
                }
            )
        } else {
            val sepet = sepetler[targetId]
            if (sepet != null) {
                DetayEkrani(
                    sepet = sepet,
                    onGeriDon = { seciliSepetId = null },
                    onUrunEkle = { urunAdi ->
                        onUrunEkle(targetId, urunAdi)
                    },
                    onUrunSil = { urunAdi ->
                        onUrunSil(targetId, urunAdi)
                    }
                )
            } else {
                // Sepet bulunamadı, geri dön
                LaunchedEffect(Unit) {
                    seciliSepetId = null
                }
            }
        }
    }

    // Sepet bulunamadı dialog'u
    if (showNotFoundDialog) {
        AlertDialog(
            onDismissRequest = { showNotFoundDialog = false },
            title = { 
                Text(
                    "Sepet Bulunamadı",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text("Bu QR kod'a ait sepet bulunamadı. Lütfen geçerli bir sepet QR kodu okutun.") 
            },
            confirmButton = {
                TextButton(
                    onClick = { showNotFoundDialog = false }
                ) {
                    Text("TAMAM")
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
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
                    val preview = CameraXPreview.Builder().build()
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
            // Modern permission request screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(GradientStart, GradientEnd)
                        )
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Kamera İzni Gerekli",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "QR kodlarını okumak için kamera iznine ihtiyacımız var",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = GradientStart
                    ),
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Kamera İznini Ver",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        // Modern header overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
                .padding(top = 40.dp, bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "QR Kod Okutun",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sepetinizin üzerindeki QR kodu telefonunuza okutun",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        // Scanning overlay frame (decorative)
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.Center)
                    .border(
                        3.dp,
                        Color.White,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                // Corner indicators
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd
                ).forEach { alignment ->
                    Box(
                        modifier = Modifier
                            .align(alignment)
                            .size(24.dp)
                            .background(
                                AccentGreen,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
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
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Sepet Detayı",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            sepet.id,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onGeriDon,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Geri Dön",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(
                    Brush.horizontalGradient(
                        listOf(GreenPrimary, BluePrimary)
                    )
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(20.dp)
        ) {
            // Sepet Durumu Kartı
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when (sepet.durum) {
                                    SepetDurumu.BOS -> Brush.horizontalGradient(
                                        listOf(StatusSuccess, StatusSuccess.copy(alpha = 0.7f))
                                    )
                                    SepetDurumu.DOLU -> Brush.horizontalGradient(
                                        listOf(StatusError, StatusError.copy(alpha = 0.7f))
                                    )
                                    SepetDurumu.KULLANIMDA -> Brush.horizontalGradient(
                                        listOf(StatusWarning, StatusWarning.copy(alpha = 0.7f))
                                    )
                                }
                            )
                            .padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Sepet Durumu",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (sepet.durum) {
                                        SepetDurumu.BOS -> "Boş"
                                        SepetDurumu.DOLU -> "Dolu"
                                        SepetDurumu.KULLANIMDA -> "Kullanımda"
                                    },
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(
                                        Color.White.copy(alpha = if (sepet.durum == SepetDurumu.KULLANIMDA) pulseAlpha else 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (sepet.durum) {
                                        SepetDurumu.BOS -> Icons.Default.ShoppingBasket
                                        SepetDurumu.DOLU -> Icons.Default.Inventory
                                        SepetDurumu.KULLANIMDA -> Icons.Default.ShoppingCart
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Ürün Ekleme Kartı
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Yeni Ürün Ekle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            OutlinedTextField(
                                value = eklenecekUrun,
                                onValueChange = { eklenecekUrun = it },
                                label = { Text("Ürün adı girin") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GreenPrimary,
                                    focusedLabelColor = GreenPrimary
                                ),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocalGroceryStore,
                                        contentDescription = null,
                                        tint = GreenPrimary
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            FloatingActionButton(
                                onClick = {
                                    if (eklenecekUrun.isNotBlank()) {
                                        onUrunEkle(eklenecekUrun)
                                        eklenecekUrun = ""
                                    }
                                },
                                modifier = Modifier.size(56.dp),
                                containerColor = if (eklenecekUrun.isNotBlank()) GreenPrimary else TextTertiary,
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Ürün Ekle",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Ürün Listesi Başlığı
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sepet İçeriği",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${sepet.items.size} ürün",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        modifier = Modifier
                            .background(
                                GreenPrimary.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            // Ürün Listesi
            if (sepet.items.isEmpty()) {
                item {
                    EmptyBasketCard()
                }
            } else {
                items(sepet.items.size) { index ->
                    val urun = sepet.items[index]
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(300, delayMillis = index * 50)
                        ) + fadeIn(animationSpec = tween(300, delayMillis = index * 50))
                    ) {
                        UrunSatiri(
                            urun = urun,
                            onUrunSil = { onUrunSil(urun) },
                            index = index
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyBasketCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingBasket,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = TextTertiary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Sepet Boş",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Text(
                text = "Henüz sepete ürün eklenmemiş",
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun UrunSatiri(urun: String, onUrunSil: () -> Unit, index: Int = 0) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(150), label = "scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = !isPressed
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AccentGreen.copy(alpha = 0.05f),
                            AccentBlue.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(AccentGreen, AccentBlue)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = urun,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Sepetteki ürün",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            FloatingActionButton(
                onClick = onUrunSil,
                modifier = Modifier.size(48.dp),
                containerColor = StatusError.copy(alpha = 0.1f),
                contentColor = StatusError,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 4.dp
                )
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Ürünü Sil",
                    modifier = Modifier.size(20.dp)
                )
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