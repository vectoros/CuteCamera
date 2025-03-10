package com.example.cutecamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.Guideline
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private val TAG: String = "MainActivity"
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private var outputDirectory: File? = null

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.insetsController?.hide(WindowInsets.Type.systemBars())
        setContentView(R.layout.activity_main)
        adjustUILayout(resources.configuration.orientation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 请求权限
        requestCameraPermission()

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        findViewById<ImageButton>(R.id.capture_button).setOnClickListener { takePhoto() }
        findViewById<ImageButton>(R.id.gallery_button).setOnClickListener { openGallery() }
        findViewById<ImageButton>(R.id.settings_button).setOnClickListener { openSettings() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        adjustUILayout(newConfig.orientation)
    }

    private fun adjustUILayout(orientation: Int) {
        val constraintLayout = findViewById<ConstraintLayout>(R.id.main) // ConstraintLayout 根布局
        val guideline = findViewById<Guideline>(R.id.guideline)
        val buttonLayout = findViewById<LinearLayout>(R.id.button_layout)
        val guidelineParams = guideline.layoutParams as ConstraintLayout.LayoutParams

        val constraintSet = ConstraintSet()

        constraintSet.clone(constraintLayout) // 复制当前约束

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏模式：按钮靠右 & 垂直排列
            guidelineParams.orientation = ConstraintLayout.LayoutParams.VERTICAL
            guidelineParams.guidePercent = 0.85f // 右侧 85% 位置
            buttonLayout.orientation = LinearLayout.VERTICAL

            constraintSet.clear(R.id.button_layout, ConstraintSet.TOP)  // 清除顶部约束
            constraintSet.clear(R.id.button_layout, ConstraintSet.START)  // 清除顶部约束
            constraintSet.clear(R.id.button_layout, ConstraintSet.END)  // 清除顶部约束
            constraintSet.connect(
                R.id.button_layout,
                ConstraintSet.LEFT,
                R.id.guideline,
                ConstraintSet.RIGHT
            ) // 右对齐
            constraintSet.connect(
                R.id.button_layout,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constraintSet.connect(
                R.id.button_layout,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP
            ) // 顶部对齐
            constraintSet.connect(
                R.id.button_layout,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM
            ) // 顶部对齐
            constraintSet.constrainWidth(
                R.id.button_layout,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ) // 宽度适应内容
            constraintSet.constrainHeight(
                R.id.button_layout,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            ) // 高度填充

        } else {
            // 竖屏模式：按钮底部 & 水平排列
            guidelineParams.orientation = ConstraintLayout.LayoutParams.HORIZONTAL
            guidelineParams.guidePercent = 0.85f // 底部 85% 位置
            buttonLayout.orientation = LinearLayout.HORIZONTAL

            constraintSet.clear(R.id.button_layout, ConstraintSet.TOP)
            constraintSet.connect(
                R.id.button_layout,
                ConstraintSet.TOP,
                R.id.guideline,
                ConstraintSet.BOTTOM
            ) // 贴底部
            constraintSet.connect(
                R.id.button_layout,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            ) // 左右居中
            constraintSet.connect(
                R.id.button_layout,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            constraintSet.constrainWidth(
                R.id.button_layout,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            ) // 宽度填充
            constraintSet.constrainHeight(
                R.id.button_layout,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ) // 高度适应内容
        }

        guideline.layoutParams = guidelineParams
        constraintSet.applyTo(constraintLayout) // 应用约束
    }

    private fun requestCameraPermission() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()  // 用户允许，启动相机
            } else {
                Toast.makeText(this, "相机权限被拒绝，请手动开启", Toast.LENGTH_SHORT).show()
            }
        }

        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera() // 已授权，直接启动相机
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = findViewById<PreviewView>(R.id.view_finder).surfaceProvider
            }
            imageCapture = ImageCapture.Builder().build()

            val orientationEventListener = object : OrientationEventListener(this as Context) {
                override fun onOrientationChanged(orientation: Int) {
                    // Monitors orientation values to determine the target rotation value
                    val rotation: Int = when (orientation) {
                        in 45..134 -> Surface.ROTATION_270
                        in 135..224 -> Surface.ROTATION_180
                        in 225..314 -> Surface.ROTATION_90
                        else -> Surface.ROTATION_0
                    }

                    imageCapture.targetRotation = rotation
                }
            }
            orientationEventListener.enable()


            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "相机启动失败", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/CuteCamera") // 指定图库目录
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                this.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object :
            ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                Toast.makeText(applicationContext, "照片已保存到图库", Toast.LENGTH_SHORT).show()
            }

            override fun onError(exception: ImageCaptureException) {
                Toast.makeText(
                    applicationContext,
                    "保存失败: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "image/*"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun openSettings() {
        Toast.makeText(this, "打开设置界面", Toast.LENGTH_SHORT).show()
    }

    private fun getOutputDirectory(): File {
        val mediaDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return mediaDir ?: filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}