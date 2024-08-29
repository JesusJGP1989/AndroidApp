package com.example.myfirstapplication

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.myfirstapplication.ui.theme.MyFirstApplicationTheme
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var tfliteModel: TFLiteModel
    private lateinit var photoFile: File
    private lateinit var currentPhotoPath: String

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            dispatchTakePictureIntent()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
            val resultText = evaluateImage(bitmap)
            showClassificationResult(bitmap, resultText)
            saveImageToMediaStore(bitmap)
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                val bitmap = loadImageFromUri(imageUri)
                val resultText = evaluateImage(bitmap)
                showClassificationResult(bitmap, resultText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            tfliteModel = TFLiteModel(assets, "mobilenetv3small_persea_mite_detector.tflite")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        showMainView()
    }

    private fun showMainView() {
        setContent {
            MyFirstApplicationTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MainViewButtons(
                        onTakePictureClick = {
                            if (ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.CAMERA
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                dispatchTakePictureIntent()
                            }
                        },
                        onLaunchGalleryClick = { launchGallery() }
                    )
                }
            }
        }
    }

    private fun showClassificationResult(bitmap: Bitmap, resultText: String) {
        setContent {
            MyFirstApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Content(bitmap = bitmap, resultText = resultText, onReturnClick = {
                        showMainView()
                    })
                }
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            try {
                photoFile = createImageFile()
            } catch (ex: IOException) {
                Toast.makeText(this, "Error creating file", Toast.LENGTH_SHORT).show()
            }
            if (::photoFile.isInitialized) {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.example.myfirstapplication.fileprovider",
                    photoFile
                )
                currentPhotoPath = photoFile.absolutePath
                takePictureLauncher.launch(photoURI)
            }
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun saveImageToMediaStore(bitmap: Bitmap) {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        }
    }

    private fun evaluateImage(bitmap: Bitmap): String {
        val result = tfliteModel.predict(bitmap)
        var maxIndex = -1
        var maxProb = -1f
        for (i in result.indices) {
            if (result[i] > maxProb) {
                maxProb = result[i]
                maxIndex = i
            }
        }
        return if (maxIndex == 0) "Healthy" else "Plague"
    }

    private fun loadImageFromUri(imageUri: Uri): Bitmap {
        val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
        return BitmapFactory.decodeStream(inputStream)
    }

    private fun launchGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }
}

@Composable
fun Content(bitmap: Bitmap?, resultText: String, onReturnClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
        } else {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text("No Image", color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(text = "Classified as: $resultText")

        // Styled button to return to the main view
        Button(
            onClick = onReturnClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .padding(top = 16.dp)
                .height(50.dp)
                .fillMaxWidth(0.8f) // Adjust button width
        ) {
            Text("Return to Main View", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun MainViewButtons(
    onTakePictureClick: () -> Unit,
    onLaunchGalleryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween, // Distributes space between elements
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f)) // Takes up available space and pushes buttons to the bottom

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Styled "Take Picture" button
            Button(
                onClick = onTakePictureClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth(0.8f) // Adjust button width
            ) {
                Text("Take Picture", style = MaterialTheme.typography.bodyLarge)
            }

            // Styled "Launch Gallery" button
            Button(
                onClick = onLaunchGalleryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .height(50.dp)
                    .fillMaxWidth(0.8f) // Adjust button width
            ) {
                Text("Launch Gallery", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyFirstApplicationTheme {
        Content(
            bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888),
            resultText = "Healthy",
            onReturnClick = {}
        )
    }
}
