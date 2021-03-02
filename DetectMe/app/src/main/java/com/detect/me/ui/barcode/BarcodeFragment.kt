package com.detect.me.ui.barcode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.detect.me.camerax.CameraManager
import com.detect.me.databinding.FragmentBarcodeBinding

class BarcodeFragment : Fragment() {

    private lateinit var binding: FragmentBarcodeBinding
    private lateinit var cameraManager: CameraManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentBarcodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        createCameraManager()
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