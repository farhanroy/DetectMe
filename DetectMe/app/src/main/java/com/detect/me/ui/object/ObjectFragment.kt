package com.detect.me.ui.`object`

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.detect.me.camerax.CameraManager
import com.detect.me.databinding.FragmentObjectBinding
import com.detect.me.mlkit.vision.VisionType

class ObjectFragment : Fragment() {

    private lateinit var binding: FragmentObjectBinding
    private lateinit var cameraManager: CameraManager
    private val visionType = VisionType.Object

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentObjectBinding.inflate(inflater, container, false)
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