package com.example.localai.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import com.example.localai.MainActivity
import com.example.localai.data.ServiceLocator

class TaskFragment : LibraryUi() {
    private lateinit var model: TaskViewModel
    private lateinit var title: EditText
    private lateinit var input: EditText
    private lateinit var output: EditText
    private lateinit var status: TextView
    private lateinit var sourceText: TextView
    private lateinit var resultBox: LinearLayout
    private lateinit var evidenceBox: LinearLayout
    private lateinit var runButton: com.google.android.material.button.MaterialButton
    private lateinit var workspaceButton: com.google.android.material.button.MaterialButton
    private var rendering = false
    private var itemSignature = ""
    private var evidenceSignature = ""
    private var exportText = ""
    private var restoredId = 0L
    private lateinit var inputLabel: TextView
    private lateinit var lengths: RadioGroup
    private val saveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autosave = Runnable { if (::model.isInitialized) model.save() }
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val resolver = requireContext().applicationContext.contentResolver
            val text = exportText
            ServiceLocator.library()!!.execute({ resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("无法写入文件") }) { r ->
                r.onSuccess { message("已导出") }.onFailure { failure(it) }
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        model = ViewModelProvider(this)[TaskViewModel::class.java]
        exportText = state?.getString("export").orEmpty()
        restoredId = state?.getLong("savedResult") ?: 0
    }
    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putString("export", exportText); out.putLong("savedResult", model.record.id) }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        itemSignature = ""; evidenceSignature = ""
        val root = page(TaskViewModel.kindName(arguments?.getString("kind") ?: "summary"))
        title = field("结果标题"); body.addView(title)
        workspaceButton = button("保存位置：收件箱 ▾") { chooseWorkspace() }; body.addView(workspaceButton)
        sourceText = label("", secondary = true); body.addView(sourceText)
        val label = if (arguments?.getString("kind") == "qa") "问题" else "原文"
        inputLabel = label(label, 18f); body.addView(inputLabel)
        input = field(if (label == "问题") "针对所选资料提问" else "输入或粘贴需要处理的文字", 4)
        body.addView(input)
        val kind = arguments?.getString("kind") ?: "summary"
        run {
            lengths = RadioGroup(requireContext()).apply { orientation = RadioGroup.HORIZONTAL }
            listOf("简短", "适中", "详细").forEachIndexed { i, text ->
                lengths.addView(RadioButton(requireContext()).apply { id = View.generateViewId(); this.text = text; isChecked = i == model.length; setOnClickListener { model.length = i } })
            }
            body.addView(lengths)
        }
        status = label("正在读取…", secondary = true); status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; body.addView(status)
        runButton = button("开始生成") {
            if (model.running) model.stop() else if (model.record.output.isNotBlank()) {
                AlertDialog.Builder(requireContext()).setTitle("重新生成？").setMessage("将替换当前结果和清单勾选状态，请先保存或导出需要保留的内容。")
                    .setNegativeButton("取消", null).setPositiveButton("重新生成") { _, _ -> model.generate() }.show()
            } else model.generate()
        }; body.addView(runButton)
        runButton.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.localai.R.color.md_primary))
        runButton.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.example.localai.R.color.md_on_primary))
        body.addView(divider()); body.addView(label("结果", 18f))
        output = field("生成结果会出现在这里，可直接编辑", 5); body.addView(output)
        resultBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }; body.addView(resultBox)
        evidenceBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }; body.addView(evidenceBox)
        val actions = LinearLayout(requireContext())
        actions.addView(button("保存") { model.save() }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("复制") {
            requireContext().getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(model.record.title, export()))
            message("已复制")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("导出") {
            exportText = export(); exporter.launch(model.record.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "任务结果" } + ".txt")
        }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(actions)
        body.addView(button("将结果整理成待办") {
            if (model.record.output.isBlank()) message("请先生成内容") else (activity as? MainActivity)?.push(create("todo", model.record.workspaceId, input = model.record.output))
        })
        body.addView(button("围绕这些资料继续提问") {
            if (model.sources.isEmpty()) {
                val original = model.record.input
                if (original.isBlank()) message("请先填写原文") else ServiceLocator.library()!!.importText(model.record.workspaceId, model.record.title + " · 原文", original) { r ->
                    r.onSuccess { if (view != null) (activity as? MainActivity)?.push(create("qa", model.record.workspaceId, longArrayOf(it))) }.onFailure { failure(it) }
                }
            } else (activity as? MainActivity)?.push(create("qa", model.record.workspaceId, model.sources.map { it.id }.toLongArray()))
        })
        title.addTextChangedListener(watcher { model.edit(title = title.text.toString()) })
        input.addTextChangedListener(watcher { model.edit(input = input.text.toString()) })
        output.addTextChangedListener(watcher { model.edit(output = output.text.toString()) })
        return root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        model.changes.observe(viewLifecycleOwner) { render() }
        model.initialize(arguments?.getString("kind") ?: "summary", arguments?.getLong("workspace") ?: 0,
            arguments?.getLongArray("sources") ?: longArrayOf(), arguments?.getString("input").orEmpty(), if (restoredId > 0) restoredId else arguments?.getLong("result") ?: 0)
    }
    private fun render() {
        rendering = true
        val record = model.record
        pageTitle.text = TaskViewModel.kindName(record.kind)
        inputLabel.text = if (record.kind == "qa") "问题" else "原文"
        input.hint = if (record.kind == "qa") "针对所选资料提问" else "输入或粘贴需要处理的文字"
        lengths.visibility = if (record.kind == "summary") View.VISIBLE else View.GONE
        for (i in 0 until lengths.childCount) lengths.getChildAt(i).isEnabled = !model.running
        if (title.text.toString() != record.title) title.setText(record.title)
        if (input.text.toString() != record.input) input.setText(record.input)
        if (output.text.toString() != record.output) output.setText(record.output)
        input.isEnabled = model.ready && !model.running && (model.sources.isEmpty() || record.kind == "qa")
        input.visibility = if (model.sources.isNotEmpty() && record.kind != "qa") View.GONE else View.VISIBLE
        inputLabel.visibility = input.visibility
        output.isEnabled = !model.running
        workspaceButton.isEnabled = model.ready && !model.running
        workspaceButton.text = "保存位置：${model.workspaceName} ▾"
        sourceText.text = if (model.sources.isEmpty()) "内容仅在本机处理" else "已选择：" + model.sources.joinToString("、") { it.name }
        val statusLabel = model.status + if (model.saveState.isBlank() || model.running) "" else "\n${model.saveState}"
        if (status.text.toString() != statusLabel) status.text = statusLabel
        runButton.isEnabled = model.ready
        runButton.visibility = if (record.kind == "chat") View.GONE else View.VISIBLE
        runButton.text = if (model.running) "停止生成" else if (record.output.isEmpty()) "开始生成" else "重新生成"
        output.visibility = if (record.kind == "todo" && !model.running) View.GONE else View.VISIBLE
        val signature = "${record.kind}:${model.running}:${model.items.size}"
        if (signature != itemSignature) {
            itemSignature = signature; resultBox.removeAllViews()
            if (record.kind == "todo" && !model.running) {
                if (model.items.isEmpty()) resultBox.addView(label("还没有待办，可生成清单或手动添加。", secondary = true))
                model.items.forEachIndexed { i, item ->
                    val row = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER_VERTICAL }
                    row.addView(CheckBox(requireContext()).apply { isChecked = item.checked; contentDescription = "完成第 ${i + 1} 项"; setOnCheckedChangeListener { _, checked -> model.editItem(i, checked = checked); scheduleSave() } })
                    row.addView(field("待办内容").apply { setText(item.text); addTextChangedListener(watcher { model.editItem(i, text = text.toString()) }) }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(button("删") { model.removeItem(i); scheduleSave() }, LinearLayout.LayoutParams(dp(48), -2))
                    resultBox.addView(row)
                }
                resultBox.addView(button("＋ 添加待办") { model.addItem(); scheduleSave() })
            }
        }
        val evidence = record.citationsJson
        if (evidenceSignature != evidence) {
            evidenceSignature = evidence; evidenceBox.removeAllViews()
            if (model.citations.isNotEmpty()) {
                evidenceBox.addView(label("参考原文 · 点击核对", 18f))
                model.citations.forEachIndexed { i, c -> evidenceBox.addView(button("[${i + 1}] ${c.name} · 第 ${c.page} 页\n${c.excerpt.take(70)}") {
                    (activity as? MainActivity)?.push(SourceFragment.create(c.sourceId, c))
                }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }) }
            }
        }
        rendering = false
    }
    private fun watcher(action: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { if (!rendering && model.ready) { action(); scheduleSave() } }
    }
    private fun scheduleSave() { saveHandler.removeCallbacks(autosave); saveHandler.postDelayed(autosave, 800) }
    private fun chooseWorkspace() {
        ServiceLocator.library()!!.workspaces { r ->
            if (view == null) return@workspaces
            r.onSuccess { list -> AlertDialog.Builder(requireContext()).setTitle("保存到工作区").setItems(list.map { it.title }.toTypedArray()) { _, index ->
                model.moveWorkspace(list[index].id, list[index].title)
            }.show() }.onFailure { failure(it) }
        }
    }
    private fun export() = LibraryContent.export(model.record.title, model.record.output, model.citations)
    override fun onPause() {
        if (activity?.isChangingConfigurations != true) model.leavePage()
        super.onPause()
    }
    override fun onStop() { saveHandler.removeCallbacks(autosave); model.save(); super.onStop() }
    companion object {
        fun create(kind: String, workspace: Long = 0, sources: LongArray = longArrayOf(), input: String = "") = TaskFragment().apply {
            arguments = Bundle().apply { putString("kind", kind); putLong("workspace", workspace); putLongArray("sources", sources); putString("input", input) }
        }
        fun open(id: Long) = TaskFragment().apply { arguments = Bundle().apply { putLong("result", id) } }
    }
}
