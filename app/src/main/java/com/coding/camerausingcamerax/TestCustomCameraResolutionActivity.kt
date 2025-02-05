package com.coding.camerausingcamerax

import android.content.ContentValues
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionFilter
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.coding.camerausingcamerax.databinding.ActivityTestCustomCameraResolutionBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TestCustomCameraResolutionActivity : AppCompatActivity() {
    private val testCustomCameraResolutionBinding: ActivityTestCustomCameraResolutionBinding by lazy {
        ActivityTestCustomCameraResolutionBinding.inflate(layoutInflater)
    }


    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf(
            android.Manifest.permission.CAMERA,
        )
    } else {
        arrayListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var cameraSelector: CameraSelector
    private var mLastHandledOrientation = 0

    private var orientationEventListener: OrientationEventListener? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var selectedResolution: Size? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(testCustomCameraResolutionBinding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (checkMultiplePermission()) {
            startCamera()
        }

        testCustomCameraResolutionBinding.captureIB.setOnClickListener {
            takePhoto()
        }

    }

    private fun setAspectRatio(ratio: String) {
        testCustomCameraResolutionBinding.previewView.layoutParams =
            testCustomCameraResolutionBinding.previewView.layoutParams.apply {
                if (this is ConstraintLayout.LayoutParams) {
                    dimensionRatio = ratio
                }
            }
    }

    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()
        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(
                    this, permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                listPermissionNeeded.add(permission)
            }
        }
        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, listPermissionNeeded.toTypedArray(), multiplePermissionId
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == multiplePermissionId) {
            if (grantResults.isNotEmpty()) {
                var isGrant = true
                for (element in grantResults) {
                    if (element == PackageManager.PERMISSION_DENIED) {
                        isGrant = false
                    }
                }
                if (isGrant) {
                    // here all permission granted successfully
                    startCamera()
                } else {
                    var someDenied = false
                    for (permission in permissions) {
                        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                                this, permission
                            )
                        ) {
                            if (ActivityCompat.checkSelfPermission(
                                    this, permission
                                ) == PackageManager.PERMISSION_DENIED
                            ) {
                                someDenied = true
                            }
                        }
                    }
                    if (someDenied) {
                        // here app Setting open because all permission is not granted
                        // and permanent denied
                        appSettingOpen(this)
                    } else {
                        // here warning permission show
                        warningPermissionDialog(this) { _: DialogInterface, which: Int ->
                            when (which) {
                                DialogInterface.BUTTON_POSITIVE -> checkMultiplePermission()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            Log.e("CameraResolutionsTAG", "startCamera() selectedResolution: $selectedResolution")
            cameraProvider = cameraProviderFuture.get()
            val resolutions = getSizeResolutions(cameraProvider)
            Log.e("CameraResolutionsTAG", "getAvailableResolutions resolutions: $resolutions")
            setupResolutionSpinner(resolutions)
            selectedResolution = resolutions.firstOrNull() ?: Size(800, 480)
            Log.e(
                "CameraResolutionsTAG",
                "getAvailableResolutions selectedResolution: $selectedResolution"
            )
            bindCameraUserCases()
        }, ContextCompat.getMainExecutor(this))
    }


    private fun bindCameraUserCases() {
        val rotatedResolution = getRotatedResolution(
            selectedResolution!!,
            testCustomCameraResolutionBinding.previewView.display.rotation
        )
        val resolutionFilter = object : ResolutionFilter {
            override fun filter(
                supportedSizes: List<Size>,
                rotationDegrees: Int
            ): List<Size?> {
                // Only return the selected resolution in the list
                if (supportedSizes.contains(rotatedResolution)) {
                    return listOf(rotatedResolution)
                }
                return supportedSizes
            }
        }
        val resolutionSelector = ResolutionSelector.Builder().setResolutionFilter(
            resolutionFilter
        ).build()


        val preview = Preview.Builder()
            .setResolutionSelector(
                resolutionSelector
            )
            .build().also {
                it.surfaceProvider = testCustomCameraResolutionBinding.previewView.surfaceProvider
            }

        Log.e(
            "CameraResolutionsTAG",
            "ImageCapture.Builder() selectedResolution: $rotatedResolution"
        )
        imageCapture = ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(testCustomCameraResolutionBinding.previewView.display.rotation)
            .build()
        Log.e(
            "CameraResolutionsTAG",
            "ImageCapture.Builder() imageCapture.resolutionInfo?.resolution: ${imageCapture.resolutionInfo?.resolution}"
        )
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                // Monitors orientation values to determine the target rotation value
                val myRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = myRotation
                val currOrient = when (orientation) {
                    in 75..134 -> ORIENT_LANDSCAPE_RIGHT
                    in 225..289 -> ORIENT_LANDSCAPE_LEFT
                    else -> ORIENT_PORTRAIT
                }
                if (currOrient != mLastHandledOrientation) {
                    val degrees = when (currOrient) {
                        ORIENT_LANDSCAPE_LEFT -> 90
                        ORIENT_LANDSCAPE_RIGHT -> -90
                        else -> 0
                    }

                    testCustomCameraResolutionBinding.captureIB.animate()
                        .rotation(degrees.toFloat()).start()
                    mLastHandledOrientation = currOrient
                }
            }
        }
        orientationEventListener?.enable()

        try {
            cameraProvider.unbindAll()

            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture
            )
            camera.cameraInfo.cameraState.observe(this) { cameraState ->
                val type = cameraState.type
                val error = cameraState.error

                if (error == null && type == CameraState.Type.OPEN) {
                    val resolutionInfo = imageCapture.resolutionInfo
                    if (resolutionInfo != null) {
                        val (width, height) = resolutionInfo.resolution.run { width to height }
                        Log.e(
                            "CameraResolutionsTAG",
                            "cameraState: Real resolution: ${width}x${height}"
                        )
                    } else {
                        Log.e("CameraResolutionsTAG", "cameraState: resolutionInfo is still null")
                    }
                }
            }
            setUpZoomTapToFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRotatedResolution(resolution: Size, rotationDegrees: Int): Size {
        return if (rotationDegrees == Surface.ROTATION_0 || rotationDegrees == Surface.ROTATION_180) {
            Size(resolution.height, resolution.width)
        } else {
            Size(resolution.width, resolution.height)
        }
    }

    private fun setUpZoomTapToFocus() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(this, listener)

        testCustomCameraResolutionBinding.previewView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = testCustomCameraResolutionBinding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                    .build()
//                val x = event.x
//                val y = event.y
//
//                val focusCircle = RectF(x-50,y-50, x+50,y+50)
//
//                mainBinding.focusCircleView.focusCircle = focusCircle
//                mainBinding.focusCircleView.invalidate()
                camera.cameraControl.startFocusAndMetering(action)

                view.performClick()
            }
            true
        }
    }

    private fun takePhoto() {
        val fileName = SimpleDateFormat(
            "yyyy_MM_dd HH_mm_ss", Locale.getDefault()
        ).format(System.currentTimeMillis()) + " ${selectedResolution!!.width} x ${selectedResolution!!.height}" + ".jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }

        val imageUri: Uri?
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            // Android 10+ (Scoped Storage)
            Log.e("CameraResolutionsTAG", "Saving in Scoped Storage")

            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Images")
            imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        } else {
            // Android 9 and Below (Direct File Path)
            val state = Environment.getExternalStorageState()
            if (state == Environment.MEDIA_MOUNTED) {
                val imageFolder = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "Images"
                )

                if (!imageFolder.exists()) {
                    imageFolder.mkdirs() // Ensure the directory exists
                }

                val imageFile = File(imageFolder, fileName)
                imageUri = Uri.fromFile(imageFile)
            } else {
                Log.e("CameraResolutionsTAG", "Storage is not mounted")
                return
            }
        }

        if (imageUri == null) {
            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
            return
        }
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = (lensFacing == CameraSelector.LENS_FACING_FRONT)
        }

        val outputOption = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            ImageCapture.OutputFileOptions.Builder(
                contentResolver, imageUri, contentValues
            ).setMetadata(metadata).build()
        } else {
            val imageFolder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Images"
            )

            if (!imageFolder.exists()) {
                imageFolder.mkdirs() // Ensure the directory exists
            }

            val imageFile = File(imageFolder, fileName)
            ImageCapture.OutputFileOptions.Builder(imageFile).setMetadata(metadata).build()
        }
        Log.e(
            "CameraResolutionsTAG",
            "imageCapture.takePicture imageCapture.resolutionInfo?.resolution: ${imageCapture.resolutionInfo?.resolution}"
        )


        imageCapture.takePicture(outputOption,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                @RequiresApi(Build.VERSION_CODES.N)
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    val savedUri = outputFileResults.savedUri ?: return
                    val inputStream = contentResolver.openInputStream(savedUri) ?: return
                    val exif = ExifInterface(inputStream)
                    inputStream.close()
                    val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1)
                    val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1)
                    Log.e("CameraResolutionsTAG", "ExifInterface width: $width height: $height")

                    val message = "Photo Capture Succeeded: ${outputFileResults.savedUri}"
                    Toast.makeText(
                        this@TestCustomCameraResolutionActivity, message, Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    Toast.makeText(
                        this@TestCustomCameraResolutionActivity,
                        exception.message.toString(),
                        Toast.LENGTH_LONG
                    ).show()
                }

            })
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun getSizeResolutions(cameraProvider: ProcessCameraProvider): List<Size> {
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val cameraInfo = cameraProvider.bindToLifecycle(
            this@TestCustomCameraResolutionActivity,
            cameraSelector
        ).cameraInfo
        val camera2CameraInfo = Camera2CameraInfo.from(cameraInfo)
        val characteristics = camera2CameraInfo.getCameraCharacteristic(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )
        val sizes = characteristics?.getOutputSizes(ImageFormat.JPEG)
        sizes?.forEach { size ->
            Log.e("CameraResolutions", "Available size: ${size.width} x ${size.height}")
        }
        return sizes?.toList() ?: emptyList()
    }

    private fun setupResolutionSpinner(resolutions: List<Size>) {
        Log.e("CameraResolutionsTAG", "setupResolutionSpinner resolutions: $resolutions")
        val adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item,
            resolutions.map { "${it.width} x ${it.height}" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        testCustomCameraResolutionBinding.resolutionSpinner.adapter = adapter

        testCustomCameraResolutionBinding.resolutionSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, view: View?, position: Int, id: Long
                ) {
                    val newResolution = resolutions[position]
                    if (selectedResolution != newResolution) {
                        selectedResolution = resolutions[position]
                        bindCameraUserCases()
                    }
                    Log.e(
                        "CameraResolutionsTAG",
                        "onItemSelectedListener selectedResolution: $selectedResolution"
                    )
                }

                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
    }

    override fun onPause() {
        orientationEventListener?.disable()
        super.onPause()
    }
}