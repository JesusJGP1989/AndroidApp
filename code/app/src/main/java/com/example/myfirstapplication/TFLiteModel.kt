package com.example.myfirstapplication

import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer

class TFLiteModel(assetManager: AssetManager, modelPath: String) {
    private var interpreter: Interpreter

    init {
        interpreter = Interpreter(loadModelFile(assetManager, modelPath))
    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(bitmap: Bitmap): FloatArray {
        // Preprocess the image to the required input format
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val inputBuffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4)
        inputBuffer.rewind()
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val px = resizedBitmap.getPixel(x, y)

                // Normalize pixel values to [0, 1]
                inputBuffer.putFloat(((px shr 16 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((px shr 8 and 0xFF) / 255.0f))
                inputBuffer.putFloat(((px and 0xFF) / 255.0f))
            }
        }

        // Output buffer
        val output = Array(1) { FloatArray(2) } // Adjust the output size as per your model
        interpreter.run(inputBuffer, output)

        return output[0]
    }
}
