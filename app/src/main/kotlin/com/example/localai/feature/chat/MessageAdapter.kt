package com.example.localai.feature.chat

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.R
import com.example.localai.model.ChatMessage
import java.util.ArrayList

/**
 * 消息列表适配器：三种条目 —— 用户消息、模型消息、"正在思考"占位。
 * 列表元素为 Object：ChatMessage 或 TYPING 哨兵。
 */
class MessageAdapter(private val longClickListener: OnMessageLongClick?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun interface OnMessageLongClick {
        fun onMessageLongClick(message: ChatMessage, position: Int)
    }

    private val items = ArrayList<Any>()

    fun items(): MutableList<Any> = items

    fun submit(newItems: List<Any>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        val o = items[position]
        if (o === TYPING) {
            return TYPE_TYPING
        }
        return if ((o as ChatMessage).role == ChatMessage.ROLE_USER) TYPE_USER else TYPE_BOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> TextVH(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_BOT -> TextVH(inflater.inflate(R.layout.item_message_assistant, parent, false))
            else -> TypingVH(inflater.inflate(R.layout.item_typing, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TextVH) {
            val message = items[position] as ChatMessage
            holder.text.text = message.text
            holder.itemView.setOnLongClickListener {
                longClickListener?.onMessageLongClick(message, position)
                true
            }
        } else {
            holder.itemView.setOnLongClickListener(null)
            if (holder is TypingVH) {
                holder.start()
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is TypingVH) {
            holder.stop()
        }
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size

    /** 局部刷新最后一条（流式输出时避免整表重绘）。 */
    fun updateLast() {
        if (items.isNotEmpty()) {
            notifyItemChanged(items.size - 1)
        }
    }

    private class TextVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.text)
    }

    /** "正在思考"占位：三个点错峰呼吸闪烁。 */
    private class TypingVH(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val anims = arrayOfNulls<ObjectAnimator>(3)

        init {
            val ids = intArrayOf(R.id.dot1, R.id.dot2, R.id.dot3)
            for (i in ids.indices) {
                val dot = itemView.findViewById<View>(ids[i])
                val a = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.25f, 1f)
                a.duration = 550
                a.repeatCount = ObjectAnimator.INFINITE
                a.repeatMode = ObjectAnimator.REVERSE
                a.startDelay = i * 180L
                anims[i] = a
            }
        }

        fun start() {
            for (a in anims) {
                if (a != null && !a.isStarted) {
                    a.start()
                }
            }
        }

        fun stop() {
            for (a in anims) {
                a?.cancel()
            }
        }
    }

    companion object {
        @JvmField val TYPING: Any = Any()

        private const val TYPE_USER = 0
        private const val TYPE_BOT = 1
        private const val TYPE_TYPING = 2
    }
}
