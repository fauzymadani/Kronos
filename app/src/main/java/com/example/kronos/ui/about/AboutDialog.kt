package com.example.kronos.ui.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.kronos.R
import com.example.kronos.databinding.DialogAboutBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AboutDialog : BottomSheetDialogFragment() {
    private var _binding: DialogAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = DialogAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        binding.aboutVersion.text = getString(R.string.about_version, info.versionName ?: "-")
        binding.licenseLink.setOnClickListener { showLicense() }
    }

    private fun showLicense() {
        try {
            val text = resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
            val body = TextView(requireContext()).apply {
                setText(text)
                setPadding(48, 48, 48, 48)
            }
            body.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            val scroll = ScrollView(requireContext()).apply { addView(body) }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.about_license_title)
                .setView(scroll)
                .setPositiveButton(R.string.about_close, null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "License file missing", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
