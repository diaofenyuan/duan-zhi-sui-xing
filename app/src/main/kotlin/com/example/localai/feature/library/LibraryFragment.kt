package com.example.localai.feature.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.example.localai.MainActivity
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.*

class LibraryFragment : LibraryUi() {
    private val repository get() = ServiceLocator.library()!!
    private var workspaceId = 0L
    private var workspaces = emptyList<WorkspaceEntity>()
    private val selected = linkedSetOf<Long>()
    private lateinit var workspaceButton: com.google.android.material.button.MaterialButton
    private lateinit var content: LinearLayout
    private var importing = false
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && workspaceId > 0) {
            importing = true; message("正在本机提取文字…")
            repository.importFile(workspaceId, uri) { result ->
                importing = false
                result.onSuccess { selected.add(it); if (view != null) reload() }.onFailure { failure(it) }
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        workspaceId = state?.getLong("workspace") ?: 0
        state?.getLongArray("selected")?.forEach { selected.add(it) }
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out); out.putLong("workspace", workspaceId); out.putLongArray("selected", selected.toLongArray())
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = page("资料与工作区")
        workspaceButton = button("选择工作区") { chooseWorkspace() }
        body.addView(workspaceButton)
        body.addView(label("资料、提问和成果保存在本机。勾选资料后开始任务。", secondary = true))
        val actions = LinearLayout(requireContext())
        actions.addView(button("导入文件") { if (!importing && workspaceId > 0) picker.launch(arrayOf("text/plain", "application/pdf")) }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("粘贴文字") { paste() }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(actions)
        body.addView(button("识别图片文字") { (activity as? MainActivity)?.push(OcrFragment.create(workspaceId)) })
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        body.addView(content)
        return root
    }
    override fun onResume() { super.onResume(); reload() }
    private fun reload() {
        repository.workspaces { result ->
            if (view == null) return@workspaces
            result.onSuccess { list ->
                workspaces = list
                if (list.none { it.id == workspaceId }) workspaceId = list.first().id
                workspaceButton.text = list.first { it.id == workspaceId }.title + " ▾"
                repository.contents(workspaceId) { loaded ->
                    if (view == null) return@contents
                    loaded.onSuccess { render(it.first, it.second) }.onFailure { failure(it) }
                }
            }.onFailure { failure(it) }
        }
    }
    private fun chooseWorkspace() {
        val names = workspaces.map { it.title }.toMutableList().apply { add("＋ 新建工作区"); add("删除当前工作区…") }
        AlertDialog.Builder(requireContext()).setTitle("工作区").setItems(names.toTypedArray()) { _, index ->
            when {
                index < workspaces.size -> { workspaceId = workspaces[index].id; selected.clear(); reload() }
                index == workspaces.size -> {
                    val input = field("课程、项目或旅行名称")
                    AlertDialog.Builder(requireContext()).setTitle("新建工作区").setView(input)
                        .setNegativeButton("取消", null).setPositiveButton("创建") { _, _ ->
                            repository.addWorkspace(input.text.toString()) { r -> r.onSuccess { workspaceId = it; selected.clear(); if (view != null) reload() }.onFailure { failure(it) } }
                        }.show()
                }
                else -> AlertDialog.Builder(requireContext()).setTitle("删除工作区？")
                    .setMessage("此工作区内的资料、任务结果和保存的对话将一并删除，无法恢复。")
                    .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> repository.deleteWorkspace(workspaceId) { r -> r.onSuccess { workspaceId = 0; selected.clear(); if (view != null) reload() }.onFailure { failure(it) } } }.show()
            }
        }.show()
    }
    private fun paste() {
        if (workspaceId <= 0) return
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), 0, dp(24), 0) }
        val name = field("资料名称")
        val text = field("粘贴需要保存的文字", 5)
        box.addView(name); box.addView(text)
        AlertDialog.Builder(requireContext()).setTitle("添加文字资料").setView(box).setNegativeButton("取消", null)
            .setPositiveButton("保存", null).create().also { dialog ->
                dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    repository.importText(workspaceId, name.text.toString(), text.text.toString()) { r ->
                        r.onSuccess { selected.add(it); dialog.dismiss(); if (view != null) reload() }.onFailure { failure(it) }
                    }
                } }; dialog.show()
            }
    }
    private fun render(sources: List<SourceEntity>, results: List<TaskResultEntity>) {
        content.removeAllViews(); selected.retainAll(sources.map { it.id }.toSet())
        content.addView(label("资料  ·  ${sources.size}", 18f))
        if (sources.isEmpty()) content.addView(label("还没有资料\n支持 TXT 和文字 PDF，也可以直接粘贴。", secondary = true))
        sources.forEach { source ->
            val row = LinearLayout(requireContext()).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(android.widget.CheckBox(requireContext()).apply {
                contentDescription = "选择 ${source.name}"; isChecked = source.id in selected
                setOnCheckedChangeListener { _, checked -> if (checked) selected.add(source.id) else selected.remove(source.id) }
            })
            row.addView(entry(source.name, "${source.charCount} 字 · ${LibraryContent.pages(source).size} 页") {
                (activity as? MainActivity)?.push(SourceFragment.create(source.id))
            }.apply { setOnLongClickListener { row.performLongClick() } }, LinearLayout.LayoutParams(0, -2, 1f))
            row.setOnLongClickListener {
                AlertDialog.Builder(requireContext()).setTitle("删除“${source.name}”？").setMessage("已保存结果中的引用快照会保留。")
                    .setNegativeButton("取消", null).setPositiveButton("删除") { _, _ -> repository.deleteSource(source.id) { r -> r.onSuccess { if (view != null) reload() }.onFailure { failure(it) } } }.show(); true
            }
            content.addView(row)
        }
        val taskRow = LinearLayout(requireContext())
        listOf("qa" to "资料问答", "summary" to "摘要", "todo" to "待办").forEach { (kind, name) ->
            taskRow.addView(button(name) {
                if (selected.isEmpty()) message("请先勾选资料")
                else (activity as? MainActivity)?.push(TaskFragment.create(kind, workspaceId, selected.toLongArray()))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(taskRow); content.addView(divider()); content.addView(label("已保存  ·  ${results.size}", 18f))
        if (results.isEmpty()) content.addView(label("摘要、清单和提问结果会留在这里。", secondary = true))
        results.forEach { item ->
            val state = when (item.status) { "complete" -> "已保存"; "limited" -> "达到输出上限，可继续整理"; else -> "草稿 · 可继续编辑" }
            content.addView(entry(item.title.ifBlank { TaskViewModel.kindName(item.kind) }, "${TaskViewModel.kindName(item.kind)} · $state") {
                (activity as? MainActivity)?.push(TaskFragment.open(item.id))
            }.apply {
                setOnLongClickListener {
                    AlertDialog.Builder(requireContext()).setTitle("删除此结果？").setNegativeButton("取消", null)
                        .setPositiveButton("删除") { _, _ -> repository.deleteResult(item.id) { r -> r.onSuccess { if (view != null) reload() }.onFailure { failure(it) } } }.show(); true
                }
            })
        }
    }
}
