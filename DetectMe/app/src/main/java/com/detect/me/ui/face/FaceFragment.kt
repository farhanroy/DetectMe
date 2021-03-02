package com.detect.me.ui.face

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.detect.me.camerax.CameraManager
import com.detect.me.databinding.FragmentFaceBinding
import com.detect.me.mlkit.vision.VisionType

class FaceFragment : Fragment() {

    private lateinit var binding: FragmentFaceBinding
    private lateinit var cameraManager: CameraManager
    private val visionType = VisionType.Face

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentFaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createCameraManager()
        cameraManager.changeAnalyzer(visionType)
        cameraManager.startCamera()
    }

    private fun createCameraManager() {
        cameraManager = CameraManager(
            requireContext(),
            binding.previewViewFinder,
            this,
            binding.graphicOverlayFinder
        )
    }

}