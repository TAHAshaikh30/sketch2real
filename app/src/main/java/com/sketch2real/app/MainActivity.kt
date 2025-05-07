package com.sketch2real.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val APP_VERSION = "1.0.0"
    }

    private lateinit var inputImageView: ImageView
    private lateinit var outputImageView: ImageView
    private lateinit var captureButton: Button
    private lateinit var importButton: Button
    private lateinit var convertButton: Button
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private lateinit var progressBar: ProgressBar

    private var inputBitmap: Bitmap? = null
    private var outputBitmap: Bitmap? = null
    private var currentPhotoPath: String = ""
    private var imageUri: Uri? = null

    private val modelConverter by lazy { FaceConverter(this) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            // All permissions granted
        } else {
            Toast.makeText(this, "Permissions required to use camera and storage", Toast.LENGTH_LONG).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageUri?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            loadImageFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        checkAndRequestPermissions()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About ${getString(R.string.app_name)}")
            .setMessage(
                "Version: $APP_VERSION\n\n" +
                "An AI-powered application that converts sketch faces to realistic human faces with natural coloring.\n\n" +
                "This app uses machine learning to transform simple sketches into photorealistic faces with proper skin tones."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun initViews() {
        inputImageView = findViewById(R.id.inputImageView)
        outputImageView = findViewById(R.id.outputImageView)
        captureButton = findViewById(R.id.captureButton)
        importButton = findViewById(R.id.importButton)
        convertButton = findViewById(R.id.convertButton)
        saveButton = findViewById(R.id.saveButton)
        shareButton = findViewById(R.id.shareButton)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        captureButton.setOnClickListener {
            dispatchTakePictureIntent()
        }

        importButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        convertButton.setOnClickListener {
            convertSketchToRealFace()
        }

        saveButton.setOnClickListener {
            saveImageToGallery()
        }

        shareButton.setOnClickListener {
            shareImage()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun dispatchTakePictureIntent() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.also {
            imageUri = FileProvider.getUriForFile(
                this,
                "com.sketch2real.app.fileprovider",
                it
            )
            takePictureLauncher.launch(imageUri)
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            inputBitmap = BitmapFactory.decodeStream(inputStream)
            inputImageView.setImageBitmap(inputBitmap)
            convertButton.isEnabled = true
            outputImageView.setImageBitmap(null)
            outputBitmap = null
            saveButton.isEnabled = false
            shareButton.isEnabled = false
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun convertSketchToRealFace() {
        inputBitmap?.let { bitmap ->
            progressBar.visibility = View.VISIBLE
            convertButton.isEnabled = false
            
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // Preprocess the sketch for better conversion results
                    val preprocessedBitmap = withContext(Dispatchers.IO) {
                        ImageUtils.preprocessSketch(bitmap)
                    }
                    
                    // Show the preprocessed image as input
                    inputImageView.setImageBitmap(preprocessedBitmap)
                    
                    // Convert the preprocessed sketch to a real face
                    outputBitmap = withContext(Dispatchers.IO) {
                        modelConverter.convertSketchToRealFace(preprocessedBitmap)
                    }
                    
                    outputBitmap?.let {
                        outputImageView.setImageBitmap(it)
                        saveButton.isEnabled = true
                        shareButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Error converting image: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    progressBar.visibility = View.GONE
                    convertButton.isEnabled = true
                }
            }
        } ?: run {
            Toast.makeText(this, "Please select or capture a sketch first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery() {
        outputBitmap?.let { bitmap ->
            val filename = "Sketch2Real_${System.currentTimeMillis()}.jpg"
            var fos: FileOutputStream? = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    try {
                        fos = contentResolver.openOutputStream(uri) as FileOutputStream
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
                    } finally {
                        fos?.close()
                    }
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val image = File(imagesDir, filename)
                try {
                    fos = FileOutputStream(image)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                    
                    // Add to gallery
                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    mediaScanIntent.data = Uri.fromFile(image)
                    sendBroadcast(mediaScanIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
                } finally {
                    fos?.close()
                }
            }
        } ?: run {
            Toast.makeText(this, "No converted image to save", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        outputBitmap?.let { bitmap ->
            try {
                val cachePath = File(cacheDir, "images")
                cachePath.mkdirs()
                val file = File(cachePath, "shared_image.jpg")
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.close()
                
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "com.sketch2real.app.fileprovider",
                    file
                )
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    type = "image/jpeg"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Share converted image"))
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to share image", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No converted image to share", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        modelConverter.close()
    }
}
