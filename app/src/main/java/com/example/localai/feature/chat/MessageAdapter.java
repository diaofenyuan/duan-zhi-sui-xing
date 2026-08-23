package com.example.localai.feature.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.R;
import com.example.localai.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息列表适配器：三种条目 —— 用户消息、模型消息、“正在思考”占位。
 * 列表元素为 Object：ChatMessage 或 TYPING 哨兵。
 */
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final Object TYPING = new Object();

    private static final int TYPE_USER = 0;
    private static final int TYPE_BOT = 1;
    private static final int TYPE_TYPING = 2;

    public interface OnMessageLongClick {
        void onMessageLongClick(ChatMessage message, int position);
    }

    private final List<Object> items = new ArrayList<>();
    private final OnMessageLongClick longClickListener;

    public MessageAdapter(OnMessageLongClick longClickListener) {
        this.longClickListener = longClickListener;
    }

    public List<Object> items() {
        return items;
    }

    public void submit(List<Object> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object o = items.get(position);
        if (o == TYPING) {
            return TYPE_TYPING;
        }
        return ((ChatMessage) o).role == ChatMessage.ROLE_USER ? TYPE_USER : TYPE_BOT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_USER:
                return new TextVH(inflater.inflate(R.layout.item_message_user, parent, false));
            case TYPE_BOT:
                return new TextVH(inflater.inflate(R.layout.item_message_assistant, parent, false));
            default:
                return new RecyclerView.ViewHolder(
                        inflater.inflate(R.layout.item_typing, parent, false)) {
                };
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TextVH) {
            ChatMessage message = (ChatMessage) items.get(position);
            ((TextVH) holder).text.setText(message.text);
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onMessageLongClick(message, position);
                }
                return true;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 局部刷新最后一条（流式输出时避免整表重绘）。 */
    public void updateLast() {
        if (!items.isEmpty()) {
            notifyItemChanged(items.size() - 1);
        }
    }

    static class TextVH extends RecyclerView.ViewHolder {

        final TextView text;

        TextVH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text);
        }
    }
}
