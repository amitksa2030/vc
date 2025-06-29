package virtual.camera.app.view.poc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import virtual.camera.app.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceSwapPocActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var outputImageView: android.widget.ImageView // Added
    private lateinit var btnSelectPhoto: Button
    private var sourceFace: com.google.mlkit.vision.face.Face? = null
    private var sourceBitmap: android.graphics.Bitmap? = null // Added

    // OpenCV Loader
    private val openCVLoaderCallback = object : org.opencv.android.BaseLoaderCallback(this) { // Added
        override fun onManagerConnected(status: Int) {
            when (status) {
                org.opencv.android.LoaderCallbackInterface.SUCCESS -> {
                    Log.i(TAG, "OpenCV loaded successfully via Manager.")
                    if (allPermissionsGranted()) { // If permissions already granted, can proceed
                        startCamera()
                    }
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(this, "Camera permission not granted.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // Permissions were granted, now initialize OpenCV.
                // initOpenCV will call startCamera if OpenCV loads successfully.
                initOpenCV()
            }
        }

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    sourceBitmap = android.provider.MediaStore.Images.Media.getBitmap(this.contentResolver, it) // Store bitmap
                    sourceBitmap?.let { bmp ->
                        val image = InputImage.fromBitmap(bmp, 0)
                        val faceDetectorOptions = FaceDetectorOptions.Builder() // Re-init options, or make it a member
                            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                            .build()
                        val faceDetector = FaceDetection.getClient(faceDetectorOptions)

                        faceDetector.process(image)
                            .addOnSuccessListener { faces ->
                                if (faces.isNotEmpty()) {
                                    sourceFace = faces[0]
                                    Toast.makeText(this, "Source photo processed. Face ready.", Toast.LENGTH_SHORT).show()
                                    Log.d(TAG, "Source photo face: bounds=${sourceFace?.boundingBox}")
                                } else {
                                    sourceFace = null
                                    sourceBitmap = null // Clear bitmap if no face
                                    Toast.makeText(this, "No face detected in source photo.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                sourceFace = null
                                sourceBitmap = null // Clear bitmap on failure
                                Toast.makeText(this, "Failed to process source photo.", Toast.LENGTH_SHORT).show()
                                Log.e(TAG, "Source photo processing failed", e)
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading source image: ", e)
                    sourceBitmap = null // Clear bitmap on error
                    Toast.makeText(this, "Error loading image.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_swap_poc)

        previewView = findViewById(R.id.preview_view_poc)
        outputImageView = findViewById(R.id.image_view_output_poc) // Initialize ImageView
        btnSelectPhoto = findViewById(R.id.btn_select_photo_poc)
        btnSelectPhoto.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            initOpenCV() // Check OpenCV before starting camera
        } else {
            requestPermissions()
        }
    }

    private fun initOpenCV() { // Added
        if (org.opencv.android.OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully (sync).")
            // Call onManagerConnected to proceed, which might call startCamera
            openCVLoaderCallback.onManagerConnected(org.opencv.android.LoaderCallbackInterface.SUCCESS)
        } else {
            Log.i(TAG, "OpenCV not found locally, trying to load via manager.")
            org.opencv.android.OpenCVLoader.initAsync(org.opencv.android.OpenCVLoader.OPENCV_VERSION_3_4_0, this, openCVLoaderCallback)
        }
    }

    override fun onResume() { // Added onResume for async loader if needed
        super.onResume()
        // If not loaded by initDebug and manager is not connected yet (e.g. app was paused)
        // then try to load with manager.
        // However, the initOpenCV() called from onCreate or permission result should handle most cases.
        if (!org.opencv.android.OpenCVLoader.initDebug()) {
             if (allPermissionsGranted()){ // Only if permissions are there
                Log.d(TAG, "Resuming and OpenCV not loaded via initDebug, ensuring initAsync is attempted if necessary.")
                // Re-calling initAsync might be problematic if already in progress.
                // A more robust solution would use a flag to check if initAsync was already called.
                // For PoC, we assume initOpenCV from onCreate/permissions handles it.
                // If needed, one could call:
                // org.opencv.android.OpenCVLoader.initAsync(org.opencv.android.OpenCVLoader.OPENCV_VERSION_3_4_0, this, openCVLoaderCallback);
             }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val faceDetectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()
            val mlKitFaceDetector = FaceDetection.getClient(faceDetectorOptions)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888) // Crucial for Mat conversion
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null && org.opencv.android.OpenCVLoader.initDebug()) { // Check OpenCV is loaded
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            mlKitFaceDetector.process(image)
                                .addOnSuccessListener { faces ->
                                    val targetFace = if (faces.isNotEmpty()) faces[0] else null

                                    if (targetFace != null && sourceFace != null && sourceBitmap != null) {
                                        try {
                                            val processingBitmap = processFaceSwap(sourceBitmap!!, sourceFace!!, imageProxy, targetFace)
                                            runOnUiThread { outputImageView.setImageBitmap(processingBitmap) }
                                        } catch(e: Exception) {
                                            Log.e(TAG, "Error during face swap processing", e)
                                            runOnUiThread { outputImageView.setImageBitmap(null) } // Clear on error
                                        }
                                    } else {
                                        // Log reasons if swap isn't happening
                                        if (targetFace == null) Log.d(TAG, "No target face in camera feed for swap.")
                                        if (sourceFace == null) Log.d(TAG, "No source face selected/processed for swap.")
                                        if (sourceBitmap == null) Log.d(TAG, "Source bitmap is null for swap.")
                                        runOnUiThread { outputImageView.setImageBitmap(null) } // Clear output
                                    }
                                    imageProxy.close()
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Face detection failed on camera feed", e)
                                    runOnUiThread { outputImageView.setImageBitmap(null) } // Clear on error
                                    imageProxy.close()
                                }
                        } else {
                            if (mediaImage == null) Log.d(TAG, "MediaImage is null.")
                            if (!org.opencv.android.OpenCVLoader.initDebug()) Log.d(TAG, "OpenCV not loaded for analysis.")
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processFaceSwap(
        sourceBmp: android.graphics.Bitmap,
        srcFace: com.google.mlkit.vision.face.Face,
        targetImageProxy: androidx.camera.core.ImageProxy,
        tgtFace: com.google.mlkit.vision.face.Face
    ): android.graphics.Bitmap {

        // 1. Convert source Bitmap to Mat
        val srcMat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(sourceBmp, srcMat)
        org.opencv.imgproc.Imgproc.cvtColor(srcMat, srcMat, org.opencv.imgproc.Imgproc.COLOR_RGBA2RGB)


        // 2. Convert target ImageProxy to Mat (YUV to RGB)
        // This YUV conversion is complex and error-prone; a library or more robust method is usually better.
        // For PoC, a simplified approach:
        val yBuffer = targetImageProxy.planes[0].buffer
        val uBuffer = targetImageProxy.planes[1].buffer
        val vBuffer = targetImageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize) // Approximate size, exact depends on strides

        // Order of get() and plane indices matter for specific YUV formats (NV21, YV12, I420)
        // This is a common simplified (potentially problematic) way for NV21-like data.
        yBuffer.get(nv21, 0, ySize)
        // For NV21, V plane comes first in the UV interleaved part, then U.
        // planes[1] is U, planes[2] is V for YUV_420_888 typically.
        // The exact copying needs to respect pixelStride and rowStride.
        // This simplified copy here will likely be incorrect for many devices.
        // A proper conversion would iterate through strides.
        // For PoC, we'll try a common pattern, but expect issues here.

        // Simplified (and likely incorrect for robust general use) UV plane copy:
        var uvIndex = ySize
        val vArray = ByteArray(vBuffer.capacity())
        vBuffer.get(vArray)
        val uArray = ByteArray(uBuffer.capacity())
        uBuffer.get(uArray)

        for (i in 0 until targetImageProxy.width * targetImageProxy.height / 4) {
            if (uvIndex + 1 < nv21.size && i < vArray.size && i < uArray.size) {
                 nv21[uvIndex++] = vArray[i] // V
                 nv21[uvIndex++] = uArray[i] // U
            } else break
        }


        val yuvMat = org.opencv.core.Mat(targetImageProxy.height + targetImageProxy.height / 2, targetImageProxy.width, org.opencv.core.CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)
        val targetMatRgb = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.cvtColor(yuvMat, targetMatRgb, org.opencv.imgproc.Imgproc.COLOR_YUV2RGB_NV21)
        yuvMat.release()

        val rotationDegrees = targetImageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val rotationCode = getCvRotationCode(rotationDegrees)
            if (rotationCode != -1) org.opencv.core.Core.rotate(targetMatRgb, targetMatRgb, rotationCode)
        }


        // 3. Extract landmarks (simplified: 3 points)
        val srcLandmarkLeftEye = srcFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
        val srcLandmarkRightEye = srcFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
        val srcLandmarkNoseBase = srcFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE)?.position

        val tgtLandmarkLeftEye = tgtFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
        val tgtLandmarkRightEye = tgtFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
        val tgtLandmarkNoseBase = tgtFace.getLandmark(com.google.mlkit.vision.face.FaceLandmark.NOSE_BASE)?.position

        if (srcLandmarkLeftEye == null || srcLandmarkRightEye == null || srcLandmarkNoseBase == null ||
            tgtLandmarkLeftEye == null || tgtLandmarkRightEye == null || tgtLandmarkNoseBase == null) {
            Log.w(TAG, "Not all required landmarks found for transform.")
            val outputBitmap = android.graphics.Bitmap.createBitmap(targetMatRgb.cols(), targetMatRgb.rows(), android.graphics.Bitmap.Config.ARGB_8888)
            org.opencv.android.Utils.matToBitmap(targetMatRgb, outputBitmap) // Return original target
            srcMat.release(); targetMatRgb.release()
            return outputBitmap
        }

        val srcPointsList = listOf(
            org.opencv.core.Point(srcLandmarkLeftEye.x.toDouble(), srcLandmarkLeftEye.y.toDouble()),
            org.opencv.core.Point(srcLandmarkRightEye.x.toDouble(), srcLandmarkRightEye.y.toDouble()),
            org.opencv.core.Point(srcLandmarkNoseBase.x.toDouble(), srcLandmarkNoseBase.y.toDouble())
        )
        val tgtPointsList = listOf(
            org.opencv.core.Point(tgtLandmarkLeftEye.x.toDouble(), tgtLandmarkLeftEye.y.toDouble()),
            org.opencv.core.Point(tgtLandmarkRightEye.x.toDouble(), tgtLandmarkRightEye.y.toDouble()),
            org.opencv.core.Point(tgtLandmarkNoseBase.x.toDouble(), tgtLandmarkNoseBase.y.toDouble())
        )
        val srcPoints = org.opencv.core.MatOfPoint2f(); srcPoints.fromList(srcPointsList)
        val tgtPoints = org.opencv.core.MatOfPoint2f(); tgtPoints.fromList(tgtPointsList)

        // 4. Get affine transform (more suitable for 3 points)
        val transform = org.opencv.imgproc.Imgproc.getAffineTransform(srcPoints, tgtPoints)

        // 5. Warp source face
        val warpedSrcMat = org.opencv.core.Mat()
        org.opencv.imgproc.Imgproc.warpAffine(srcMat, warpedSrcMat, transform, targetMatRgb.size())

        // 6. Create mask from target face contour (convex hull for simplicity)
        val mask = org.opencv.core.Mat.zeros(targetMatRgb.size(), org.opencv.core.CvType.CV_8UC1)
        val targetFaceMLKitContour = tgtFace.getContour(com.google.mlkit.vision.face.FaceContour.FACE)
        if (targetFaceMLKitContour != null) {
            val targetContourCVPoints = targetFaceMLKitContour.points.map { org.opencv.core.Point(it.x.toDouble(), it.y.toDouble()) }
            if (targetContourCVPoints.isNotEmpty()) {
                val matOfPoint = org.opencv.core.MatOfPoint(); matOfPoint.fromList(targetContourCVPoints)
                val hull = org.opencv.core.MatOfInt()
                org.opencv.imgproc.Imgproc.convexHull(matOfPoint, hull)
                val hullPointsList = mutableListOf<org.opencv.core.Point>()
                for(i in 0 until hull.rows()){
                    val index = hull.get(i,0)[0].toInt()
                    if (index >=0 && index < targetContourCVPoints.size) {
                        hullPointsList.add(targetContourCVPoints[index])
                    }
                }
                if (hullPointsList.isNotEmpty()) {
                    val matOfHullPoints = org.opencv.core.MatOfPoint(); matOfHullPoints.fromList(hullPointsList)
                    org.opencv.imgproc.Imgproc.fillConvexPoly(mask, matOfHullPoints, org.opencv.core.Scalar(255.0))
                    matOfHullPoints.release()
                }
                matOfPoint.release(); hull.release()
            }
        } else { // Fallback to bounding box if no contour
            val rect = tgtFace.boundingBox
            org.opencv.imgproc.Imgproc.rectangle(mask,
                org.opencv.core.Point(rect.left.toDouble(), rect.top.toDouble()),
                org.opencv.core.Point(rect.right.toDouble(), rect.bottom.toDouble()),
                org.opencv.core.Scalar(255.0), -1)
        }

        // 7. Blend (simple copy with mask)
        val outputMat = org.opencv.core.Mat()
        targetMatRgb.copyTo(outputMat) // Initialize output with target frame
        warpedSrcMat.copyTo(outputMat, mask) // Copy warped source face where mask is non-zero

        // 8. Convert output Mat to Bitmap
        val outputBitmap = android.graphics.Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), android.graphics.Bitmap.Config.ARGB_8888)
        org.opencv.android.Utils.matToBitmap(outputMat, outputBitmap)

        // Release Mats
        srcMat.release(); targetMatRgb.release(); srcPoints.release(); tgtPoints.release()
        transform.release(); warpedSrcMat.release(); mask.release(); outputMat.release()

        return outputBitmap
    }

     private fun getCvRotationCode(rotationDegrees: Int): Int { // Added
        return when (rotationDegrees) {
            0 -> -1 // No rotation needed by Core.rotate if image is already upright
            90 -> org.opencv.core.Core.ROTATE_90_CLOCKWISE
            180 -> org.opencv.core.Core.ROTATE_180
            270 -> org.opencv.core.Core.ROTATE_90_COUNTERCLOCKWISE
            else -> -1 // Should not happen, or handle as error
        }
    }

    private fun requestPermissions() {
        requestPermissionsLauncher.launch(REQUIRED_PERMISSIONS) // Corrected to requestPermissionsLauncher
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "FaceSwapPocActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
