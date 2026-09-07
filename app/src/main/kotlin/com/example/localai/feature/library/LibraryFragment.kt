package com.example.localai.feature.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity
import androidx.core.content.ContextCompat
import com.example.localai.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
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
    private var showingResults = false
    private var sources = emptyList<SourceEntity>()
    private var results = emptyList<TaskResultEntity>()
    private lateinit var tabs: TabLayout
    private lateinit var selectionBar: LinearLayout
    private lateinit var selectionCount: TextView
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
        showingResults = state?.getBoolean("showing_results") ?: false
        state?.getLongArray("selected")?.forEach { selected.add(it) }
    }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out); out.putLong("workspace", workspaceId); out.putLongArray("selected", selected.toLongArray())
        out.putBoolean("showing_results", showingResults)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val root = page(getString(R.string.library_title)) as LinearLayout
        pageTitle.setTypeface(null, android.graphics.Typeface.BOLD)
        val toolbar = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER_VERTICAL }
        workspaceButton = button("选择工作区") { chooseWorkspace() }.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setIconResource(R.drawable.ic_expand_more)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
        }
        toolbar.addView(workspaceButton, LinearLayout.LayoutParams(0, -2, 1f))
        val addButton = MaterialButton(requireContext()).apply {
            text = getString(R.string.library_add)
            minHeight = dp(48)
            cornerRadius = dp(12)
            setIconResource(R.drawable.ic_add)
            setOnClickListener { chooseImport() }
        }
        toolbar.addView(addButton)
        body.addView(toolbar)
        body.addView(label(getString(R.string.library_local_hint), 12f, true))
        tabs = TabLayout(requireContext()).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            addTab(newTab().setText("资料"))
            addTab(newTab().setText("已保存"))
            getTabAt(if (showingResults) 1 else 0)?.select()
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    showingResults = tab.position == 1
                    renderContent()
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
        body.addView(tabs)
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        body.addView(content)
        // 操作栏留在滚动区外，长资料列表也能直接开始任务。
        selectionBar = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            visibility = View.GONE
        }
        val selectionHeader = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER_VERTICAL }
        selectionCount = label("", 13f)
        selectionCount.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        selectionHeader.addView(selectionCount, LinearLayout.LayoutParams(0, -2, 1f))
        selectionHeader.addView(button(getString(R.string.library_clear_selection)) { selected.clear(); renderContent() })
        selectionBar.addView(selectionHeader)
        val taskRow = LinearLayout(requireContext())
        listOf("qa" to "资料问答", "summary" to "摘要", "todo" to "待办").forEach { (kind, name) ->
            taskRow.addView(button(name) {
                if (selected.isNotEmpty()) {
                    (activity as? MainActivity)?.push(TaskFragment.create(kind, workspaceId, selected.toLongArray()))
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        selectionBar.addView(taskRow)
        root.addView(selectionBar)
        return root
    }
    override fun onResume() { super.onResume(); reload() }
    private fun reload() {
        repository.workspaces { result ->
            if (view == null) return@workspaces
            result.onSuccess { list ->
                workspaces = list
                if (list.none { it.id == workspaceId }) workspaceId = list.first().id
                workspaceButton.text = list.first { it.id == workspaceId }.title
                val requestedWorkspace = workspaceId
                repository.contents(requestedWorkspace) { loaded ->
                    // 切换工作区后丢弃旧请求，避免旧资料覆盖当前选择。
                    if (view == null || workspaceId != requestedWorkspace) return@contents
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
    private fun chooseImport() {
        if (importing || workspaceId <= 0) return
        AlertDialog.Builder(requireContext()).setTitle(R.string.library_add)
            .setItems(arrayOf("导入文件（TXT、PDF）", "粘贴文字", "识别图片文字")) { _, index ->
                showingResults = false
                tabs.getTabAt(0)?.select()
                when (index) {
                    0 -> picker.launch(arrayOf("text/plain", "application/pdf"))
                    1 -> paste()
                    2 -> (activity as? MainActivity)?.push(OcrFragment.create(workspaceId))
                }
            }.setNegativeButton("取消", null).show()
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
        this.sources = sources
        this.results = results
        selected.retainAll(sources.map { it.id }.toSet())
        tabs.getTabAt(0)?.text = "资料 ${sources.size}"
        tabs.getTabAt(1)?.text = "已保存 ${results.size}"
        renderContent()
    }

    private fun updateSelection() {
        selectionCount.text = getString(R.string.library_selected, selected.size)
        selectionBar.visibility = if (selected.isNotEmpty() && !showingResults) View.VISIBLE else View.GONE
    }

    private fun renderContent() {
        content.removeAllViews()
        updateSelection()
        if (showingResults) {
            renderResults()
            return
        }
        content.addView(label(getString(R.string.library_select_hint), 12f, true))
        if (sources.isEmpty()) {
            content.addView(label("还没有资料", 20f).apply { setPadding(0, dp(32), 0, dp(8)) })
            content.addView(label("添加一份文件或文字，把零散信息整理成摘要与清单。", secondary = true))
        }
        sources.forEach { source ->
            val row = LinearLayout(requireContext()).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(android.widget.CheckBox(requireContext()).apply {
                contentDescription = "选择 ${source.name}"; isChecked = source.id in selected
                minWidth = dp(48); minHeight = dp(48)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(source.id) else selected.remove(source.id)
                    updateSelection()
                }
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
    }

    private fun renderResults() {
        if (results.isEmpty()) {
            content.addView(label("还没有整理结果", 20f).apply { setPadding(0, dp(32), 0, dp(8)) })
            content.addView(label("选择资料后开始任务，摘要、清单和问答结果会保存在这里。", secondary = true))
        }
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
