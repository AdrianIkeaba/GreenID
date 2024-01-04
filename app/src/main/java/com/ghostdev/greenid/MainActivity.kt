package com.ghostdev.greenid

import android.Manifest
import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ghostdev.greenid.databinding.ActivityMainBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraView: PreviewView
    private lateinit var shutterButton: Button
    private lateinit var previewView: ImageView
    private lateinit var bitmap: Bitmap
    private lateinit var generativeModel: GenerativeModel
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                // Handle permission denial
                Toast.makeText(applicationContext, "Camera access is required to use this app", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraView = binding.cameraView
        shutterButton = binding.takePicture
        previewView = binding.previewImage

        val apiKey = com.ghostdev.greenid.BuildConfig.apiKey

        generativeModel = GenerativeModel(
            modelName = "gemini-pro-vision",
            apiKey = apiKey
        )


        if (allPermissionsGranted()) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    @OptIn(DelicateCoroutinesApi::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Set up the preview use case and attach it to the preview view
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind any previous bind of use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))

        //Click listener
        shutterButton.setOnClickListener {
            bitmap = cameraView.bitmap!!
            cameraView.visibility = View.GONE
            previewView.visibility = View.VISIBLE
            flashPreview()

            //Convert Bitmap to PNG and compress to < 3MB
            val outputFile = File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "compressed_image.png"
            )

            try {
                val outputStream = FileOutputStream(outputFile)

                // Compress the bitmap into PNG format
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)

                // Check the size of the compressed image
                val fileSizeInKB = outputFile.length() / 1024
                val fileSizeInMB = fileSizeInKB / 1024

                // Check if the size is less than 3MB
                if (fileSizeInMB < 3) {
                    // The compressed image is within the desired size limit
                    // You can use the outputFile for further operations or display
                    previewView.setImageURI(outputFile.toUri())
                    binding.progressBarLoading.visibility = View.VISIBLE
                    GlobalScope.launch {
                        processImage()
                    }
                } else {
                    // Handle the case where the compressed image exceeds 3MB
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }

        }
    }
    private fun flashPreview() {
        val flashAnimator = ObjectAnimator.ofFloat(previewView, "alpha", 1f, 0f, 1f)
        flashAnimator.duration = 70 // 0.5 seconds
        flashAnimator.interpolator = AccelerateDecelerateInterpolator()

        flashAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {}

            override fun onAnimationCancel(animation: Animator) {}

            override fun onAnimationRepeat(animation: Animator) {}
        })

        // Trigger the flash animation
        flashAnimator.start()
    }

    private suspend  fun processImage() {
        val inputContent = content {
            image(bitmap)
            text("What specie of plant is this?. Give the name of the plant, its characteristics, botanical name, where it is native to, where it can be found and some fun facts about it if any. If it is not a plant say, \"I recognise plants, not whatever this is 🙄\".")

        }
        val response = generativeModel.generateContent(inputContent)

        withContext(Dispatchers.Main) {
            binding.resultText.visibility = View.VISIBLE
            binding.resultText.text = response.text
            binding.progressBarLoading.visibility = View.GONE
        }
    }
}