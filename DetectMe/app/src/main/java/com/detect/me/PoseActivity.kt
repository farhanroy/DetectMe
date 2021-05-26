package com.detect.me

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import com.detect.me.camerax.PoseGraphicOverlay
import com.detect.me.camerax.VisionImageProcessor
import com.detect.me.mlkit.vision.barcode.BarcodeScannerProcessor
import com.detect.me.mlkit.vision.pose.PoseDetectorProcessor
import com.detect.me.mlkit.vision.text.TextRecognitionProcessor
import com.detect.me.utils.PreferenceUtils
import com.detect.me.utils.PreferenceUtils.getCameraXTargetResolution
import com.detect.me.utils.PreferenceUtils.getObjectDetectorOptionsForLivePreview
import com.detect.me.utils.PreferenceUtils.getPoseDetectorOptionsForLivePreview
import com.detect.me.utils.PreferenceUtils.isCameraLiveViewportEnabled
import com.detect.me.utils.PreferenceUtils.shouldPoseDetectionRescaleZForVisualization
import com.detect.me.utils.PreferenceUtils.shouldPoseDetectionRunClassification
import com.detect.me.utils.PreferenceUtils.shouldPoseDetectionVisualizeZ
import com.detect.me.utils.PreferenceUtils.shouldShowPoseDetectionInFrameLikelihoodLivePreview
import com.google.android.gms.common.annotation.KeepName
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.face.FaceDetectorOptions


@KeepName
@RequiresApi(VERSION_CODES.LOLLIPOP)
class PoseActivity() : AppCompatActivity(),
    OnRequestPermissionsResultCallback, OnItemSelectedListener,
    CompoundButton.OnCheckedChangeListener {
    private var previewView: PreviewView? = null
    private var graphicOverlay: PoseGraphicOverlay? = null

    @Nullable
    private var cameraProvider: ProcessCameraProvider? = null

    @Nullable
    private var previewUseCase: Preview? = null

    @Nullable
    private var analysisUseCase: ImageAnalysis? = null

    @Nullable
    private var imageProcessor: VisionImageProcessor? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var selectedModel = OBJECT_DETECTION
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraSelector: CameraSelector? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
            Toast.makeText(
                applicationContext,
                "CameraX is only supported on SDK version >=21. Current SDK version is "
                        + VERSION.SDK_INT,
                Toast.LENGTH_LONG
            )
                .show()
            return
        }
        if (savedInstanceState != null) {
            selectedModel = savedInstanceState.getString(STATE_SELECTED_MODEL, OBJECT_DETECTION)
        }
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        setContentView(R.layout.activity_pose)
        previewView = findViewById(R.id.preview_view)
        if (previewView == null) {
            Log.d(TAG, "previewView is null")
        }
        graphicOverlay = findViewById(R.id.graphic_overlay)
        if (graphicOverlay == null) {
            Log.d(TAG, "graphicOverlay is null")
        }
        val options: MutableList<String> = ArrayList()
        options.add(POSE_DETECTION)

        ViewModelProvider(this, AndroidViewModelFactory.getInstance(application))
            .get(CameraXViewModel::class.java)
            .processCameraProvider
            .observe(
                this
            ) { provider ->
                cameraProvider = provider
                if (allPermissionsGranted()) {
                    bindAllCameraUseCases()
                }
            }
        if (!allPermissionsGranted()) {
            runtimePermissions
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString(STATE_SELECTED_MODEL, selectedModel)
    }

    @Synchronized
    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        // An item was selected. You can retrieve the selected item using
        // parent.getItemAtPosition(pos)
        selectedModel = parent.getItemAtPosition(pos).toString()
        Log.d(TAG, "Selected model: $selectedModel")
        bindAnalysisUseCase()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
        // Do nothing.
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (cameraProvider == null) {
            return
        }
        val newLensFacing =
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        val newCameraSelector = CameraSelector.Builder().requireLensFacing(newLensFacing).build()
        try {
            if (cameraProvider!!.hasCamera(newCameraSelector)) {
                Log.d(TAG, "Set facing to $newLensFacing")
                lensFacing = newLensFacing
                cameraSelector = newCameraSelector
                bindAllCameraUseCases()
                return
            }
        } catch (e: CameraInfoUnavailableException) {
            // Falls through
        }
        Toast.makeText(
            applicationContext,
            "This device does not have lens with facing: $newLensFacing",
            Toast.LENGTH_SHORT
        )
            .show()
    }

    public override fun onResume() {
        super.onResume()
        bindAllCameraUseCases()
    }

    override fun onPause() {
        super.onPause()
        if (imageProcessor != null) {
            imageProcessor!!.stop()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (imageProcessor != null) {
            imageProcessor!!.stop()
        }
    }

    private fun bindAllCameraUseCases() {
        if (cameraProvider != null) {
            // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            bindAnalysisUseCase()
        }
    }

    private fun bindPreviewUseCase() {
        if (!isCameraLiveViewportEnabled(this)) {
            return
        }
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }
        val builder: Preview.Builder = Preview.Builder()
        val targetResolution: Size? = getCameraXTargetResolution(this, lensFacing)
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }
        previewUseCase = builder.build()
        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)
        cameraSelector?.let {
            cameraProvider!!.bindToLifecycle( /* lifecycleOwner= */this,
                it, previewUseCase)
        }
    }

    private fun bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }
        if (imageProcessor != null) {
            imageProcessor!!.stop()
        }
        try {
            val poseDetectorOptions = getPoseDetectorOptionsForLivePreview(this)
            val shouldShowInFrameLikelihood =
                shouldShowPoseDetectionInFrameLikelihoodLivePreview(this)
            val visualizeZ = shouldPoseDetectionVisualizeZ(this)
            val rescaleZ = shouldPoseDetectionRescaleZForVisualization(this)
            val runClassification = shouldPoseDetectionRunClassification(this)
            imageProcessor = PoseDetectorProcessor(
                this,
                poseDetectorOptions,
                shouldShowInFrameLikelihood,
                visualizeZ,
                rescaleZ,
                runClassification,  /* isStreamMode = */
                true
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Can not create image processor: $selectedModel", e
            )
            Toast.makeText(
                applicationContext,
                "Can not create image processor: " + e.localizedMessage,
                Toast.LENGTH_LONG
            )
                .show()
            return
        }
        val builder = ImageAnalysis.Builder()
        val targetResolution: Size? = getCameraXTargetResolution(this, lensFacing)
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }
        analysisUseCase = builder.build()
        needUpdateGraphicOverlayImageSourceInfo = true
        analysisUseCase!!.setAnalyzer( // imageProcessor.processImageProxy will use another thread to run the detection underneath,
            // thus we can just runs the analyzer itself on main thread.
            ContextCompat.getMainExecutor(this),
            { imageProxy: ImageProxy ->
                if (needUpdateGraphicOverlayImageSourceInfo) {
                    val isImageFlipped: Boolean =
                        lensFacing == CameraSelector.LENS_FACING_FRONT
                    val rotationDegrees: Int = imageProxy.getImageInfo().getRotationDegrees()
                    if (rotationDegrees == 0 || rotationDegrees == 180) {
                        graphicOverlay?.setImageSourceInfo(
                            imageProxy.getWidth(), imageProxy.getHeight(), isImageFlipped
                        )
                    } else {
                        graphicOverlay?.setImageSourceInfo(
                            imageProxy.getHeight(), imageProxy.getWidth(), isImageFlipped
                        )
                    }
                    needUpdateGraphicOverlayImageSourceInfo = false
                }
                try {
                    imageProcessor!!.processImageProxy(imageProxy, graphicOverlay)
                } catch (e: MlKitException) {
                    Log.e(
                        TAG,
                        "Failed to process image. Error: " + e.getLocalizedMessage()
                    )
                    Toast.makeText(
                        getApplicationContext(),
                        e.getLocalizedMessage(),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            })
        cameraProvider!!.bindToLifecycle( /* lifecycleOwner= */this,
            (cameraSelector)!!, analysisUseCase
        )
    }

    private val requiredPermissions: Array<String?>
        private get() {
            try {
                val info = this.packageManager
                    .getPackageInfo(this.packageName, PackageManager.GET_PERMISSIONS)
                val ps = info.requestedPermissions
                return if (ps != null && ps.size > 0) {
                    ps
                } else {
                    arrayOfNulls(0)
                }
            } catch (e: Exception) {
                return arrayOfNulls(0)
            }
        }

    private fun allPermissionsGranted(): Boolean {
        for (permission: String? in requiredPermissions) {
            if (!isPermissionGranted(this, permission)) {
                return false
            }
        }
        return true
    }

    private val runtimePermissions: Unit
        private get() {
            val allNeededPermissions: MutableList<String?> = ArrayList()
            for (permission: String? in requiredPermissions) {
                if (!isPermissionGranted(this, permission)) {
                    allNeededPermissions.add(permission)
                }
            }
            if (!allNeededPermissions.isEmpty()) {
                ActivityCompat.requestPermissions(
                    this, allNeededPermissions.toTypedArray(), PERMISSION_REQUESTS
                )
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        Log.i(TAG, "Permission granted!")
        if (allPermissionsGranted()) {
            bindAllCameraUseCases()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private val TAG = "CameraXLivePreview"
        private val PERMISSION_REQUESTS = 1
        private val OBJECT_DETECTION = "Object Detection"
        private val OBJECT_DETECTION_CUSTOM = "Custom Object Detection"
        private val CUSTOM_AUTOML_OBJECT_DETECTION = "Custom AutoML Object Detection (Flower)"
        private val FACE_DETECTION = "Face Detection"
        private val TEXT_RECOGNITION = "Text Recognition"
        private val BARCODE_SCANNING = "Barcode Scanning"
        private val IMAGE_LABELING = "Image Labeling"
        private val IMAGE_LABELING_CUSTOM = "Custom Image Labeling (Birds)"
        private val CUSTOM_AUTOML_LABELING = "Custom AutoML Image Labeling (Flower)"
        private val POSE_DETECTION = "Pose Detection"
        private val SELFIE_SEGMENTATION = "Selfie Segmentation"
        private val STATE_SELECTED_MODEL = "selected_model"
        private fun isPermissionGranted(context: Context, permission: String?): Boolean {
            if ((ContextCompat.checkSelfPermission(context, (permission)!!)
                        == PackageManager.PERMISSION_GRANTED)
            ) {
                Log.i(TAG, "Permission granted: $permission")
                return true
            }
            Log.i(TAG, "Permission NOT granted: $permission")
            return false
        }
    }
}