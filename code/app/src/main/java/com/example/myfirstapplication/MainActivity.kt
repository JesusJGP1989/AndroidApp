package com.example.myfirstapplication

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var tfliteModel: TFLiteModel
    private lateinit var photoFile: File
    private lateinit var currentPhotoPath: String

    // Register the launcher at the top level of your activity
    private val requestPermissionLauncher = registerForActivityResult(
        RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            dispatchTakePictureIntent()
        } else {
            // Handle permission denial
        }
    }

    // Register the launcher at the top level of your activity
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
            // Process the bitmap with the TensorFlow Lite model
            val resultText = evaluateImage(bitmap)
            setContent {
                MyFirstApplicationTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Content(bitmap = bitmap, resultText = resultText)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the TFLite model
        try {
            tfliteModel = TFLiteModel(assets, "mobilenetv3small_persea_mite_detector.tflite")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        setContent {
            MyFirstApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var imageUri by remember { mutableStateOf<Uri?>(null) }
                    var resultText by remember { mutableStateOf("No result") }

                    val context = LocalContext.current

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            } else {
                                dispatchTakePictureIntent()
                            }
                        }) {
                            Text(text = "Take Picture")
                        }

                        imageUri?.let {
                            val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
                            Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
                            Text(text = resultText)
                        }
                    }
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
                // Handle error
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
}

@Composable
fun Content(bitmap: Bitmap, resultText: String) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
        Text(text = resultText)
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyFirstApplicationTheme {
        Content(
            bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888),
            resultText = "Healthy"
        )
    }
}
