package com.example.localai.feature.market

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.core.compatibility.CompatibilityEngine
import com.example.localai.core.device.DeviceProfiler
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.feature.settings.InferencePolicy
import com.example.localai.data.ServiceLocator
import com.example.localai.feature.download.DownloadRepository
import com.example.localai.model.ModelInfo
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

/** 模型详情页：真实目录数据（签名 Manifest）+ 兼容性结论 + 下载/开始对话 + 已批准/演示标识。 */
class ModelDetailFragment : Fragment() {

    private var modelId: String? = null
    private var currentItem: DownloadRepository.CatalogItem? = null
    private val repositoryListener = object : DownloadRepository.Listener {
        override fun onDownloadsChanged() {
            refreshFromRepository()
        }

        override fun onCatalogChanged() {
            refreshFromRepository()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_model_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        modelId = arguments?.getString(ARG_MODEL_ID)
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            if (activity is MainActivity) {
                (activity as MainActivity).onBackPressedDispatcher.onBackPressed()
            }
        }

        ServiceLocator.downloads()?.register(repositoryListener)
        refreshFromRepository()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ServiceLocator.downloads()?.unregister(repositoryListener)
    }

    private fun refreshFromRepository() {
        val repository = ServiceLocator.downloads()
        if (repository == null) {
            showUnavailable(view, "下载服务未就绪")
            return
        }
        val catalogView = repository.catalogView()
        var item: DownloadRepository.CatalogItem? = null
        if (catalogView.models != null) {
            for (candidate in catalogView.models) {
                if (candidate.modelId == modelId) {
                    item = candidate
                    break
                }
            }
        }
        if (item == null) {
            showUnavailable(view, "目录尚未加载该模型（刷新后重试）")
            return
        }
        currentItem = item
        bind(item)
    }

    private fun showUnavailable(view: View?, message: String) {
        if (view == null || !isAdded) {
            return
        }
        val compatReason = view.findViewById<TextView>(R.id.compat_reason)
        compatReason.text = message
    }

    private fun bind(item: DownloadRepository.CatalogItem) {
        if (view == null || !isAdded) {
            return
        }
        val view = view ?: return

        // 头图
        val codeTask = item.tasks?.contains(ModelInfo.TASK_CODE) == true
        val tag = view.findViewById<TextView>(R.id.hero_tag)
        tag.text = if (codeTask) "代码助手" else "文本对话"
        val status = view.findViewById<TextView>(R.id.hero_status)
        status.text = if (item.isApproved()) "离线模型" else "模型不可用"
        status.visibility = View.VISIBLE
        val statusBg = if (item.isApproved()) R.color.status_success_container else R.color.status_warn_container
        val statusText = if (item.isApproved()) R.color.status_success else R.color.status_warn
        status.setBackgroundResource(statusBg)
        status.setTextColor(ContextCompat.getColor(requireContext(), statusText))

        view.findViewById<TextView>(R.id.hero_name).text = item.displayName
        view.findViewById<TextView>(R.id.hero_publisher).text =
            (item.publisher ?: "") + " · " + item.updatedAt + " 更新"
        view.findViewById<TextView>(R.id.hero_param).text =
            MarketModels.paramsLabel(item.parameterCount) + " 参数"
        view.findViewById<TextView>(R.id.hero_quant).text = item.quantization
        view.findViewById<TextView>(R.id.hero_ctx).text =
            "上下文 " + MarketModels.contextLabel(item.contextLength)

        // 三格数据（性能未实测：显示待真机基线）
        view.findViewById<TextView>(R.id.val_size).text = MarketModels.sizeLabel(item.sizeBytes)
        view.findViewById<TextView>(R.id.val_speed).text = "待测"
        view.findViewById<TextView>(R.id.val_ttft).text = "待测"

        // 适配结论（CompatibilityEngine，估算）
        val runtimeContext = ApprovedModels.byId(item.modelId)?.let {
            InferencePolicy.current(requireContext(), it).contextLength.toLong()
        } ?: minOf(item.contextLength, 2048L)
        val constraints = CompatibilityEngine.ModelConstraints(
            item.minAndroidApi, item.abis, item.sizeBytes, runtimeContext, item.parameterCount)
        val snapshot = deviceSnapshot()
        val result = CompatibilityEngine.evaluate(snapshot, constraints, requireDownloadSpace = !item.installed)

        val compatTitle = view.findViewById<TextView>(R.id.compat_title)
        val compatReason = view.findViewById<TextView>(R.id.compat_reason)
        val compatIcon = view.findViewById<ImageView>(R.id.compat_icon)
        compatTitle.text = ModelAdapter.compatLabel(result.level) +
                (if (result.isRecommended()) " · 本机可流畅运行" else "")
        compatTitle.setTextColor(ContextCompat.getColor(requireContext(),
            ModelAdapter.compatTextRes(result.level)))
        compatIcon.setBackgroundResource(ModelAdapter.compatBgRes(result.level))
        val negative = ModelInfo.COMPAT_UNSUPPORTED == result.level ||
                ModelInfo.COMPAT_HIGH_LOAD == result.level
        compatIcon.setImageResource(if (negative) R.drawable.ic_warning else R.drawable.ic_check_circle)
        compatIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), ModelAdapter.compatTextRes(result.level)))
        val reason = if (result.reasons.isEmpty())
            "硬约束满足，短基准测试通过，内存与温控余量充足（估算值）。"
        else result.reasons.joinToString("；")
        compatReason.text = "按当前 $runtimeContext tokens 上下文估算。$reason"

        // 简介
        view.findViewById<TextView>(R.id.text_desc).text = item.description

        // 详细信息
        val meta = view.findViewById<LinearLayout>(R.id.meta_container)
        meta.removeAllViews()
        addMetaRow(meta, getString(R.string.meta_license), item.licenseSpdx, true)
        addMetaRow(meta, getString(R.string.meta_quant),
            item.quantization + " · GGUF", false)
        addMetaRow(meta, getString(R.string.meta_template),
            item.chatTemplate ?: "chatml", false)
        addMetaRow(meta, getString(R.string.meta_source),
            item.sourceUrl ?: "", false)
        addMetaRow(meta, getString(R.string.meta_updated), item.updatedAt, false)

        // 示例指令 → 带模型打开聊天并预填
        val toChat = View.OnClickListener { v ->
            if (activity is MainActivity) {
                val a = activity as MainActivity
                a.setChatPrefill((v as TextView).text.toString())
                a.openChatWithModel(item.modelId!!)
            }
        }
        view.findViewById<View>(R.id.sample_1).setOnClickListener(toChat)
        view.findViewById<View>(R.id.sample_2).setOnClickListener(toChat)
        view.findViewById<View>(R.id.sample_3).setOnClickListener(toChat)

        // 主操作按钮
        val note = view.findViewById<TextView>(R.id.btn_note)
        val action = view.findViewById<MaterialButton>(R.id.btn_action)
        action.isEnabled = true
        action.alpha = 1f
        if (item.installed) {
            action.setText(R.string.btn_open_chat)
            note.text = if (item.isApproved())
                "已安装 · 点击开始本地对话"
            else "此模型不可用于对话，请重新下载支持的模型"
            action.setOnClickListener {
                if (activity is MainActivity) {
                    (activity as MainActivity).openChatWithModel(item.modelId!!)
                }
            }
        } else if (result.isUnsupported()) {
            action.setText(R.string.btn_unsupported)
            action.isEnabled = false
            action.alpha = 0.5f
            note.text = reason
        } else {
            action.text = getString(R.string.btn_download_fmt, MarketModels.sizeLabel(item.sizeBytes))
            note.setText(R.string.download_note_fmt)
            action.setOnClickListener {
                val repository = ServiceLocator.downloads()
                if (repository == null) {
                    Snackbar.make(view, "下载服务未就绪", Snackbar.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                repository.enqueue(item.modelId!!) { ok, message ->
                    if (!isAdded || view == null) {
                        return@enqueue
                    }
                    Snackbar.make(view, if (ok)
                        getString(R.string.snackbar_queued_real) else (message ?: ""),
                        Snackbar.LENGTH_SHORT)
                        .setAction(R.string.snackbar_view) { openTab(R.id.nav_download) }
                        .show()
                }
            }
        }
    }

    private fun deviceSnapshot(): CompatibilityEngine.DeviceSnapshot {
        val p = DeviceProfiler.collect(requireContext())
        val abis = p.abis
        return CompatibilityEngine.DeviceSnapshot(
            p.sdkInt,
            if (abis == null || abis.isEmpty()) "" else abis[0],
            p.storageFreeMb * 1024 * 1024,
            p.ramTotalMb * 1024 * 1024)
    }

    private fun addMetaRow(parent: LinearLayout, label: String, value: String?, first: Boolean) {
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val top = if (first) 0 else (10 * resources.displayMetrics.density).toInt()
        row.setPadding(0, top, 0, 0)

        val labelView = TextView(requireContext())
        labelView.textSize = 12f
        labelView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
        labelView.text = label
        row.addView(labelView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val valueView = TextView(requireContext())
        valueView.textSize = 13.5f
        valueView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        valueView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        valueView.text = value
        valueView.maxLines = 1
        valueView.ellipsize = android.text.TextUtils.TruncateAt.END
        valueView.gravity = Gravity.END
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        valueView.setPadding((16 * resources.displayMetrics.density).toInt(), 0, 0, 0)
        row.addView(valueView, lp)

        parent.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun openTab(tabId: Int) {
        if (activity is MainActivity) {
            val a = activity as MainActivity
            while (a.supportFragmentManager.backStackEntryCount > 0) {
                a.supportFragmentManager.popBackStackImmediate()
            }
            a.openTab(tabId)
        }
    }

    companion object {
        private const val ARG_MODEL_ID = "model_id"

        @JvmStatic
        fun newInstance(modelId: String): ModelDetailFragment {
            val args = Bundle()
            args.putString(ARG_MODEL_ID, modelId)
            val f = ModelDetailFragment()
            f.arguments = args
            return f
        }
    }
}
