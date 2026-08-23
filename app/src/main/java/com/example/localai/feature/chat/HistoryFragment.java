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
import com.example.localai.common.widget.EmptyStateView;
import com.example.localai.mock.MockStore;
import com.example.localai.model.ChatSession;

import java.util.ArrayList;
import java.util.List;

/** 会话历史页：查看/进入会话、删除会话、空态。 */
public class HistoryFragment extends Fragment {

    private final List<ChatSession> sessions = new ArrayList<>();
    private RecyclerView listView;
    private EmptyStateView emptyView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
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
    }

    private void refresh() {
        sessions.clear();
        sessions.addAll(MockStore.SESSIONS);
        if (listView.getAdapter() != null) {
            listView.getAdapter().notifyDataSetChanged();
        }
        emptyView.setVisibility(sessions.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(sessions.isEmpty() ? View.GONE : View.VISIBLE);
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
            final ChatSession session = sessions.get(position);

            ((View) itemView.findViewById(R.id.icon_bg)).setBackgroundResource(
                    PickerAdapter.grad(position % 4));
            TextView iconText = itemView.findViewById(R.id.text_icon);
            iconText.setText(session.title.isEmpty() ? "对" :
                    String.valueOf(Character.toUpperCase(session.title.charAt(0))));
            ((TextView) itemView.findViewById(R.id.text_title)).setText(session.title);
            ((TextView) itemView.findViewById(R.id.text_meta)).setText(
                    getString(R.string.session_meta_fmt,
                            session.modelName + " · " + session.timeLabel,
                            session.messages.size()));
            String preview = session.preview();
            ((TextView) itemView.findViewById(R.id.text_preview))
                    .setText(preview.isEmpty() ? "…" : preview);

            itemView.setOnClickListener(v -> {
                MockStore.pendingSession = session;
                openTab(R.id.nav_chat);
            });
            itemView.findViewById(R.id.btn_more).setOnClickListener(v ->
                    new AlertDialog.Builder(requireContext())
                            .setTitle(session.title)
                            .setMessage(R.string.dialog_clear_sessions_msg)
                            .setPositiveButton(R.string.action_delete, (d, w) -> {
                                MockStore.SESSIONS.remove(session);
                                refresh();
                            })
                            .setNegativeButton(R.string.action_cancel, null)
                            .show());
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }
    }

    private void openTab(int tabId) {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            while (activity.getSupportFragmentManager().getBackStackEntryCount() > 0) {
                activity.getSupportFragmentManager().popBackStackImmediate();
            }
            activity.openTab(tabId);
        }
    }
}
