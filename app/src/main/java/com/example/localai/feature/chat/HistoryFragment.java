package com.example.localai.feature.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.MainActivity;
import com.example.localai.R;
import com.example.localai.common.Fmt;
import com.example.localai.common.widget.EmptyStateView;
import com.example.localai.data.ServiceLocator;
import com.example.localai.data.room.ConversationEntity;

import java.util.ArrayList;
import java.util.List;

/** 会话历史页：Room 持久化的真实会话列表；点击恢复、删除、空态。 */
public class HistoryFragment extends Fragment {

    private final List<ConversationEntity> sessions = new ArrayList<>();
    private RecyclerView listView;
    private EmptyStateView emptyView;
    private ChatRepository repository;
    private final ChatRepository.Listener listener = this::refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = ServiceLocator.chat();
        listView = view.findViewById(R.id.list);
        emptyView = view.findViewById(R.id.empty);
        listView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireContext()));
        listView.setAdapter(new SessionAdapter());

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).getOnBackPressedDispatcher().onBackPressed();
            }
        });
        refresh();
        if (repository != null) {
            repository.register(listener);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (repository != null) {
            repository.unregister(listener);
        }
    }

    private void refresh() {
        if (repository != null) {
            repository.refresh();
            sessions.clear();
            sessions.addAll(repository.conversations());
        }
        if (listView == null || listView.getAdapter() == null) {
            return;
        }
        listView.getAdapter().notifyDataSetChanged();
        emptyView.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(sessions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String modelLabel(ConversationEntity entity) {
        if (entity.modelId == null || entity.modelId.isEmpty()) {
            return "本地模型";
        }
        return entity.modelId;
    }

    private class SessionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_session, parent, false);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View itemView = holder.itemView;
            final ConversationEntity session = sessions.get(position);

            ((View) itemView.findViewById(R.id.icon_bg)).setBackgroundResource(
                    PickerAdapter.grad(position % 4));
            TextView iconText = itemView.findViewById(R.id.text_icon);
            String title = session.title == null || session.title.isEmpty() ? "会话" : session.title;
            iconText.setText(String.valueOf(Character.toUpperCase(title.charAt(0))));
            ((TextView) itemView.findViewById(R.id.text_title)).setText(title);
            ((TextView) itemView.findViewById(R.id.text_meta)).setText(
                    getString(R.string.session_meta2_fmt,
                            modelLabel(session),
                            Fmt.timeLabel(session.updatedAt, System.currentTimeMillis())));
            ((TextView) itemView.findViewById(R.id.text_preview)).setText("");

            itemView.setOnClickListener(v -> {
                android.util.Log.d("p4history", "item clicked id=" + session.id);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openConversation(session.id);
                }
            });
            itemView.findViewById(R.id.btn_more).setOnClickListener(v -> {
                android.util.Log.d("p4history", "more clicked id=" + session.id);
                new AlertDialog.Builder(requireContext())
                        .setTitle(title)
                        .setMessage(R.string.dialog_clear_sessions_msg)
                        .setPositiveButton(R.string.action_delete, (d, w) -> {
                            if (repository != null) {
                                repository.deleteConversation(session.id);
                                refresh();
                            }
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }
    }
}
