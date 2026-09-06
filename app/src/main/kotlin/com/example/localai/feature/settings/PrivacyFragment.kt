package com.example.localai.feature.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.localai.R

/** 说明随 APK 提供，不加载网页；使用 Fragment 返回栈保存重建后的阅读位置。 */
class PrivacyFragment : Fragment(R.layout.fragment_privacy) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
