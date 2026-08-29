package com.example.localai.feature.download

import android.os.Bundle
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.common.Fmt
import com.example.localai.common.widget.EmptyStateView
import com.example.localai.data.ServiceLocator
import com.example.localai.data.room.DownloadEntity
import com.example.localai.data.room.DownloadState
import com.example.localai.data.room.ModelEntity
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale

/**
 * 下载管理页（P2 真实化）：
 * 模型目录（签名验证通过的目录数据 + 同步状态）-> 进行中任务（真实进度/速度/状态）->
 * 已安装（真实安装记录与删除）。存储概览为 StatFs 实测值，不再使用写死的演示数据。
 */
class DownloadsFragment : Fragment(), DownloadRepository.Listener {

    private lateinit var adapter: DownloadAdapter
    private lateinit var catalogAdapter: CatalogAdapter
    private lateinit var installedAdapter: InstalledAdapter

    private lateinit var summaryView: TextView
    private lateinit var storageText: TextView
    private lateinit var storageBar: LinearProgressIndicator
    private lateinit var catalogStatus: TextView
    private lateinit var catalogHeader: View
    private lateinit var activeLabel: View
    private lateinit var installedLabel: View
    private lateinit var emptyView: EmptyStateView

    private val installed = ArrayList<ModelEntity>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_downloads, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        summaryView = view.findViewById(R.id.text_summary)
        storageText = view.findViewById(R.id.storage_text)
        storageBar = view.findViewById(R.id.storage_bar)
        catalogStatus = view.findViewById(R.id.catalog_status)
        catalogHeader = view.findViewById(R.id.catalog_header)
        activeLabel = view.findViewById(R.id.label_active)
        installedLabel = view.findViewById(R.id.label_installed)
        emptyView = view.findViewById(R.id.empty)

        adapter = DownloadAdapter(object : DownloadAdapter.Actions {
            override fun onPauseResume(task: DownloadEntity) {
                if (DownloadState.PAUSED == task.state) {
                    ServiceLocator.downloads()?.resume(task.taskId)
                } else {
                    ServiceLocator.downloads()?.pause(task.taskId)
                }
            }

            override fun onCancel(task: DownloadEntity) {
                ServiceLocator.downloads()?.cancel(task.taskId)
            }

            override fun onRetry(task: DownloadEntity) {
                ServiceLocator.downloads()?.retry(task.taskId)
            }
        })

        val listActive = view.findViewById<RecyclerView>(R.id.list_active)
        listActive.layoutManager = LinearLayoutManager(requireContext())
        listActive.adapter = adapter

        catalogAdapter = CatalogAdapter { item ->
            ServiceLocator.downloads()?.enqueue(item.modelId!!) { ok, message ->
                val root = this@DownloadsFragment.view
                if (root == null) {
                    return@enqueue
                }
                Snackbar.make(root,
                    if (ok) getString(R.string.snackbar_queued_real) else (message ?: ""),
                    Snackbar.LENGTH_SHORT).show()
            }
        }
        val listCatalog = view.findViewById<RecyclerView>(R.id.list_catalog)
        listCatalog.layoutManager = LinearLayoutManager(requireContext())
        listCatalog.adapter = catalogAdapter
        view.findViewById<View>(R.id.btn_sync_catalog).setOnClickListener {
            ServiceLocator.downloads()?.refreshCatalog()
        }

        installedAdapter = InstalledAdapter()
        val listInstalled = view.findViewById<RecyclerView>(R.id.list_installed)
        listInstalled.layoutManager = LinearLayoutManager(requireContext())
        listInstalled.adapter = installedAdapter

        emptyView.setOnActionClickListener {
            if (activity is MainActivity) {
                (activity as MainActivity).openTab(R.id.nav_market)
            }
        }

        refreshAll()
    }

    override fun onStart() {
        super.onStart()
        ServiceLocator.downloads()?.register(this)
    }

    override fun onStop() {
        super.onStop()
        ServiceLocator.downloads()?.unregister(this)
    }

    override fun onDownloadsChanged() {
        if (view == null) {
            return
        }
        refreshActive()
        refreshInstalled()
        refreshStorage()
    }

    override fun onCatalogChanged() {
        if (view == null) {
            return
        }
        refreshCatalog()
    }

    private fun refreshAll() {
        refreshStorage()
        refreshCatalog()
        refreshActive()
        refreshInstalled()
    }

    private fun refreshStorage() {
        val statFs = StatFs(requireContext().filesDir.absolutePath)
        val total = statFs.totalBytes
        val available = statFs.availableBytes
        val used = maxOf(0, total - available)
        val usedPercent = if (total <= 0) 0 else (used * 100 / total).toInt()
        storageBar.progress = usedPercent
        storageText.text = getString(R.string.storage_text_fmt,
            Fmt.humanBytes(used), Fmt.humanBytes(total), Fmt.humanBytes(available))
    }

    private fun refreshCatalog() {
        val repository = ServiceLocator.downloads() ?: return
        val catalogView = repository.catalogView()
        if (catalogView.isLoading()) {
            catalogStatus.setText(R.string.catalog_status_loading)
            catalogStatus.setTextColor(requireContext().getColor(R.color.text_tertiary))
        } else if (catalogView.isError()) {
            catalogStatus.setText(R.string.catalog_status_error)
            catalogStatus.setTextColor(requireContext().getColor(R.color.status_danger))
        } else {
            catalogStatus.text = getString(R.string.catalog_status_fmt, catalogView.models.size)
            catalogStatus.setTextColor(requireContext().getColor(R.color.text_tertiary))
        }
        catalogAdapter.submit(catalogView.models)
        catalogHeader.visibility = View.VISIBLE
        updateEmptyState()
    }

    private fun refreshActive() {
        val repository = ServiceLocator.downloads() ?: return
        adapter.submit(repository.tasks())
        var activeCount = 0
        for (t in repository.tasks()) {
            if (DownloadState.isActive(t.entity.state)) {
                activeCount++
            }
        }
        val statFs = StatFs(requireContext().filesDir.absolutePath)
        val free = statFs.availableBytes
        summaryView.text = getString(R.string.dl_summary_fmt, activeCount, Fmt.humanBytes(free))
        updateEmptyState()
    }

    private fun refreshInstalled() {
        val repository = ServiceLocator.downloads() ?: return
        installed.clear()
        installed.addAll(repository.installed())
        installedAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val repository = ServiceLocator.downloads()
        val noTasks = repository == null ||
                (adapter.itemCount == 0 && installed.isEmpty() && catalogAdapter.itemCount == 0)
        emptyView.visibility = if (noTasks) View.VISIBLE else View.GONE
        activeLabel.visibility = if (adapter.itemCount == 0) View.GONE else View.VISIBLE
        installedLabel.visibility = if (installed.isEmpty()) View.GONE else View.VISIBLE
    }

    /** 已安装模型行（真实 ModelEntity + 真实删除）。 */
    private inner class InstalledAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = layoutInflater.inflate(R.layout.item_installed, parent, false)
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val itemView = holder.itemView
            val model = installed[position]
            val displayName = if (model.displayName == null) model.modelId else model.displayName

            itemView.findViewById<View>(R.id.icon_bg)
                .setBackgroundResource(ModelAdapterGrad.grad(displayName.hashCode()))
            itemView.findViewById<TextView>(R.id.text_icon).text =
                letterOf(displayName).toString()
            itemView.findViewById<TextView>(R.id.text_name).text = displayName
            itemView.findViewById<TextView>(R.id.text_meta).text =
                getString(R.string.installed_meta_fmt,
                    Fmt.humanBytes(model.sizeBytes) + " · " + model.quantization,
                    SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(Date(model.installedAt)))

            itemView.findViewById<View>(R.id.btn_chat).setOnClickListener { openTab(R.id.nav_chat) }
            itemView.findViewById<View>(R.id.btn_delete).setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_delete_model_title)
                    .setMessage(R.string.dialog_delete_model_msg)
                    .setPositiveButton(R.string.dialog_delete_model_ok) { _, _ ->
                        ServiceLocator.downloads()?.deleteModel(model.modelId, model.version) { ok, message ->
                            val root = view
                            if (root == null) {
                                return@deleteModel
                            }
                            Snackbar.make(root, if (ok)
                                getString(R.string.toast_deleted_real) else (message ?: ""),
                                Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        }

        override fun getItemCount(): Int = installed.size
    }

    private fun openTab(tabId: Int) {
        if (activity is MainActivity) {
            (activity as MainActivity).openTab(tabId)
        }
    }

    companion object {
        private fun letterOf(name: String?): Char {
            if (name == null || name.isEmpty()) {
                return '?'
            }
            val c = name[0]
            return Character.toUpperCase(c)
        }
    }

    /** 渐变资源选择（避免循环依赖 market 包）。 */
    private class ModelAdapterGrad {
        companion object {
            @JvmStatic
            fun grad(index: Int): Int {
                return when (Math.floorMod(index, 4)) {
                    1 -> R.drawable.grad_b
                    2 -> R.drawable.grad_c
                    3 -> R.drawable.grad_d
                    else -> R.drawable.grad_a
                }
            }
        }
    }
}
