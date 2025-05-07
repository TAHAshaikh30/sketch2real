package com.sketch2real.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FaceConverter(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 256 // Standard size for pix2pix models
    
    init {
        try {
            val model = loadModelFile()
            val options = Interpreter.Options()
            interpreter = Interpreter(model, options)
            android.util.Log.i("FaceConverter", "TensorFlow Lite model loaded successfully")
            
            // Check if we're using the placeholder model (which is a text file, not a valid TFLite model)
            // This will throw an exception when trying to use it for inference
            if (model.capacity() < 1000) { // Real TFLite models are typically much larger
                android.util.Log.w("FaceConverter", "Loaded model appears to be a placeholder. Using mock conversion instead.")
                interpreter = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Log that we'll be using mock conversion instead
            android.util.Log.w("FaceConverter", "Failed to load TFLite model. Using mock conversion instead.", e)
            interpreter = null
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val assetManager = context.assets
        val modelPath = "sketch2real.tflite"
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun convertSketchToRealFace(sketchBitmap: Bitmap): Bitmap {
        // If interpreter is null (model not loaded), use mock conversion
        if (interpreter == null) {
            return mockConversion(sketchBitmap)
        }
        
        try {
            // Preprocess the input bitmap
            val inputBitmap = Bitmap.createScaledBitmap(sketchBitmap, inputSize, inputSize, true)
            val inputBuffer = convertBitmapToByteBuffer(inputBitmap)
            
            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            outputBuffer.order(ByteOrder.nativeOrder())
            
            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Convert output buffer to bitmap
            return convertByteBufferToBitmap(outputBuffer)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to mock conversion if inference fails
            return mockConversion(sketchBitmap)
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        
        for (pixel in pixels) {
            // Extract RGB values and normalize to [-1, 1]
            val r = (Color.red(pixel) / 127.5f) - 1
            val g = (Color.green(pixel) / 127.5f) - 1
            val b = (Color.blue(pixel) / 127.5f) - 1
            
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        
        return byteBuffer
    }

    private fun convertByteBufferToBitmap(byteBuffer: ByteBuffer): Bitmap {
        byteBuffer.rewind()
        
        val bitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(inputSize * inputSize)
        
        for (i in 0 until inputSize * inputSize) {
            // Get RGB values from buffer and denormalize from [-1, 1] to [0, 255]
            val r = ((byteBuffer.float + 1) * 127.5f).toInt().coerceIn(0, 255)
            val g = ((byteBuffer.float + 1) * 127.5f).toInt().coerceIn(0, 255)
            val b = ((byteBuffer.float + 1) * 127.5f).toInt().coerceIn(0, 255)
            
            pixels[i] = Color.rgb(r, g, b)
        }
        
        bitmap.setPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        return bitmap
    }

    // For demonstration purposes, we'll create a mock conversion when the model is not available
    private fun mockConversion(sketchBitmap: Bitmap): Bitmap {
        // This is a more sophisticated placeholder that simulates realistic skin tones
        // In a real app, this would be replaced by the actual model inference
        val outputBitmap = sketchBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = outputBitmap.width
        val height = outputBitmap.height
        
        // Define skin tone palette (from light to dark)
        val skinTones = arrayOf(
            Color.rgb(255, 224, 196), // Very light skin
            Color.rgb(241, 194, 167), // Light skin
            Color.rgb(224, 172, 138), // Medium light skin
            Color.rgb(198, 134, 94),  // Medium skin
            Color.rgb(141, 85, 53),   // Medium dark skin
            Color.rgb(94, 58, 40)     // Dark skin
        )
        
        // Choose a random skin tone as base
        val baseSkinTone = skinTones[kotlin.random.Random.nextInt(skinTones.size)]
        val baseR = Color.red(baseSkinTone)
        val baseG = Color.green(baseSkinTone)
        val baseB = Color.blue(baseSkinTone)
        
        // Define colors for different facial features
        val lipColor = Color.rgb(
            (baseR * 0.9).toInt().coerceIn(0, 255),
            (baseG * 0.5).toInt().coerceIn(0, 255),
            (baseB * 0.5).toInt().coerceIn(0, 255)
        )
        
        val eyeColor = Color.rgb(80, 120, 180) // Blue eyes
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = outputBitmap.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                
                // Apply different colors based on grayscale value
                when {
                    gray > 230 -> { // Very light areas (highlights)
                        outputBitmap.setPixel(x, y, Color.rgb(
                            (baseR * 1.1).toInt().coerceIn(0, 255),
                            (baseG * 1.1).toInt().coerceIn(0, 255),
                            (baseB * 1.1).toInt().coerceIn(0, 255)
                        ))
                    }
                    gray > 180 -> { // Light areas (main skin)
                        outputBitmap.setPixel(x, y, baseSkinTone)
                    }
                    gray > 120 -> { // Medium areas (shadows on skin)
                        outputBitmap.setPixel(x, y, Color.rgb(
                            (baseR * 0.8).toInt(),
                            (baseG * 0.8).toInt(),
                            (baseB * 0.8).toInt()
                        ))
                    }
                    gray > 80 -> { // Darker areas (features like eyebrows, nose shadow)
                        // Check if it might be lips based on position (middle-bottom of face)
                        val isLipArea = y > height * 0.6 && y < height * 0.8 && 
                                       x > width * 0.3 && x < width * 0.7
                        
                        if (isLipArea) {
                            outputBitmap.setPixel(x, y, lipColor)
                        } else {
                            outputBitmap.setPixel(x, y, Color.rgb(
                                (baseR * 0.6).toInt(),
                                (baseG * 0.6).toInt(),
                                (baseB * 0.6).toInt()
                            ))
                        }
                    }
                    gray > 40 -> { // Very dark areas (outlines, hair)
                        // Check if it might be eyes based on position
                        val isEyeArea = y > height * 0.3 && y < height * 0.5 && 
                                      ((x > width * 0.25 && x < width * 0.45) || 
                                       (x > width * 0.55 && x < width * 0.75))
                        
                        if (isEyeArea && gray > 50) {
                            outputBitmap.setPixel(x, y, eyeColor)
                        } else {
                            outputBitmap.setPixel(x, y, Color.rgb(
                                (baseR * 0.4).toInt(),
                                (baseG * 0.4).toInt(),
                                (baseB * 0.4).toInt()
                            ))
                        }
                    }
                    else -> { // Darkest areas (deep shadows, hair)
                        outputBitmap.setPixel(x, y, Color.rgb(30, 20, 20))
                    }
                }
            }
        }
        
        return outputBitmap
    }

    /**
     * Checks if the app is using the real TensorFlow Lite model or the mock conversion
     * @return true if using the real model, false if using mock conversion
     */
    fun isUsingRealModel(): Boolean {
        return interpreter != null
    }
    
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
