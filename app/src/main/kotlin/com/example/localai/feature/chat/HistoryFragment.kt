package com.example.localai.feature.chat

import android.os.Bundle
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
import com.example.localai.data.room.ConversationEntity
import com.example.localai.feature.download.DownloadRepository
import java.util.ArrayList

/** 会话历史页：Room 持久化的真实会话列表；点击恢复、删除、空态。 */
class HistoryFragment : Fragment() {

    private val sessions = ArrayList<ConversationEntity>()
    private var listView: RecyclerView? = null
    private var emptyView: EmptyStateView? = null
    private var repository: ChatRepository? = null
    private val listener = ChatRepository.Listener { refresh() }
    private val catalogListener = object : DownloadRepository.Listener {
        override fun onDownloadsChanged() {}
        override fun onCatalogChanged() { refresh() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = ServiceLocator.chat()
        listView = view.findViewById(R.id.list)
        emptyView = view.findViewById(R.id.empty)
        listView!!.layoutManager = LinearLayoutManager(requireContext())
        listView!!.adapter = SessionAdapter()

        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            if (activity is MainActivity) {
                (activity as MainActivity).onBackPressedDispatcher.onBackPressed()
            }
        }
        refresh()
        repository?.register(listener)
        ServiceLocator.downloads()?.register(catalogListener)
        repository?.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repository?.unregister(listener)
        ServiceLocator.downloads()?.unregister(catalogListener)
        listView = null
        emptyView = null
    }

    private fun refresh() {
        repository?.let {
            sessions.clear()
            sessions.addAll(it.conversations())
        }
        val lv = listView ?: return
        if (lv.adapter == null) {
            return
        }
        lv.adapter!!.notifyDataSetChanged()
        emptyView?.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        lv.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun modelLabel(entity: ConversationEntity): String {
        if (entity.modelId == null || entity.modelId.isEmpty()) {
            return "本地模型"
        }
        return ServiceLocator.downloads()?.catalogView()?.models
            ?.firstOrNull { it.modelId == entity.modelId }?.displayName
            ?.takeIf { it.isNotBlank() } ?: entity.modelId
    }

    private inner class SessionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = layoutInflater.inflate(R.layout.item_session, parent, false)
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val itemView = holder.itemView
            val session = sessions[position]

            itemView.findViewById<View>(R.id.icon_bg)
                .setBackgroundResource(PickerAdapter.grad(position % 4))
            val iconText = itemView.findViewById<TextView>(R.id.text_icon)
            val title = if (session.title == null || session.title.isEmpty()) "会话" else session.title
            iconText.text = ConversationText.initial(title)
            itemView.findViewById<TextView>(R.id.text_title).text = title
            itemView.findViewById<TextView>(R.id.text_meta).text =
                getString(R.string.session_meta2_fmt,
                    modelLabel(session),
                    Fmt.timeLabel(session.updatedAt, System.currentTimeMillis()))
            itemView.findViewById<TextView>(R.id.text_preview).text = ""

            itemView.setOnClickListener {
                if (activity is MainActivity) {
                    (activity as MainActivity).openConversation(session.id)
                }
            }
            itemView.findViewById<View>(R.id.btn_more).setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage(R.string.dialog_delete_session_msg)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        repository?.let {
                            it.deleteConversation(session.id) { error ->
                                if (error != null) view?.let { target ->
                                    com.google.android.material.snackbar.Snackbar.make(target,
                                        "删除失败：$error", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
            }
        }

        override fun getItemCount(): Int = sessions.size
    }
}
