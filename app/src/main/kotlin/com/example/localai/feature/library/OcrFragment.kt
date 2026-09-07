package com.example.localai.feature.library

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.example.localai.MainActivity
import com.example.localai.data.ServiceLocator

class OcrFragment : LibraryUi() {
    private lateinit var model: OcrViewModel
    private lateinit var preview: ImageView
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var choose: com.google.android.material.button.MaterialButton
    private lateinit var run: com.google.android.material.button.MaterialButton
    private lateinit var rotate: com.google.android.material.button.MaterialButton
    private var workspace = 0L
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.load(uri) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        model = ViewModelProvider(this)[OcrViewModel::class.java]
        workspace = arguments?.getLong("workspace") ?: 0
        if (model.text.isBlank()) model.text = state?.getString("text").orEmpty()
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("text", model.text.take(40_000)) }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = page("图片文字")
        body.addView(label("适合清晰的横排中文、英文。图片和识别内容只在本机处理。", secondary = true))
        choose = button("选择图片") { picker.launch(arrayOf("image/*")) }; body.addView(choose)
        preview = ImageView(requireContext()).apply {
            adjustViewBounds = true; maxHeight = dp(240); scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "待识别图片预览"
        }; body.addView(preview, LinearLayout.LayoutParams(-1, -2))
        rotate = button("向右旋转 90°") { model.rotate() }; body.addView(rotate)
        status = label(model.status, secondary = true); status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; body.addView(status)
        run = button("识别文字") {
            if (model.running) model.cancel() else if (model.text.isNotBlank()) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("重新识别？").setMessage("将替换当前编辑的识别结果。")
                    .setNegativeButton("取消", null).setPositiveButton("重新识别") { _, _ -> model.recognize() }.show()
            } else model.recognize()
        }; body.addView(run)
        body.addView(divider()); body.addView(label("核对并编辑", 18f))
        editor = field("识别后可在这里修正文字", 6); body.addView(editor)
        editor.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { model.text = s.toString() }
        })
        body.addView(button("保存为资料") { save() })
        body.addView(button("整理成摘要") {
            if (model.running) message("请等待识别完成")
            else if (model.text.isBlank()) message("请先识别或输入文字")
            else (activity as? MainActivity)?.push(TaskFragment.create("summary", workspace, input = model.text))
        })
        return root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        model.changes.observe(viewLifecycleOwner) {
            preview.setImageBitmap(model.bitmap)
            status.text = model.status; choose.isEnabled = !model.running
            rotate.isEnabled = !model.running && model.bitmap != null
            run.isEnabled = model.bitmap != null; run.text = if (model.running) "停止识别" else "识别文字"
            editor.isEnabled = !model.running
            if (editor.text.toString() != model.text) editor.setText(model.text)
        }
        ServiceLocator.library()!!.workspaces { r -> r.onSuccess { spaces -> if (spaces.none { it.id == workspace }) workspace = spaces.first().id }.onFailure { failure(it) } }
    }
    private fun save() {
        if (model.running) { message("请等待识别完成"); return }
        if (model.text.isBlank() || workspace <= 0) { message("请先识别或输入文字"); return }
        val title = field("资料名称").apply { setText("图片文字") }
        androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle("保存识别文字").setView(title)
            .setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
                ServiceLocator.library()!!.importRecognizedText(workspace, title.text.toString(), model.text) { r ->
                    r.onSuccess { if (view != null) (activity as? MainActivity)?.push(SourceFragment.create(it)) }.onFailure { failure(it) }
                }
            }.show()
    }
    companion object { fun create(workspace: Long) = OcrFragment().apply { arguments = Bundle().apply { putLong("workspace", workspace) } } }
}
