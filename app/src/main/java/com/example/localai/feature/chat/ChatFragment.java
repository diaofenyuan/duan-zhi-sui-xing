package com.example.localai.feature.chat;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.MainActivity;
import com.example.localai.R;
import com.example.localai.core.inference.ApprovedModels;
import com.example.localai.mock.MockStore;
import com.example.localai.model.ChatMessage;
import com.example.localai.model.ChatSession;
import com.example.localai.model.ModelInfo;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/** 本地对话页：流式输出（演示/真实推理双引擎）、停止、复制/重试/删除、模型切换与会话保存。 */
public class ChatFragment extends Fragment implements ChatEngine.StreamListener {

    private MessageAdapter adapter;
    private ChatEngine engine;

    private RecyclerView messagesView;
    private View emptyWrap;
    private EditText inputView;
    private com.google.android.material.button.MaterialButton btnSend;
    private TextView modelTitle;
    private View statusDot;

    private ChatMessage streamingBot;
    private int botPosition = -1;
    private boolean generating = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        engine = ChatEngineProvider.create(requireContext(), MockStore.currentModelId);
        adapter = new MessageAdapter(this::showMessageMenu);

        messagesView = view.findViewById(R.id.messages);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        lm.setStackFromEnd(true);
        messagesView.setLayoutManager(lm);
        messagesView.setAdapter(adapter);

        emptyWrap = view.findViewById(R.id.empty_wrap);
        inputView = view.findViewById(R.id.input);
        btnSend = view.findViewById(R.id.btn_send);
        modelTitle = view.findViewById(R.id.text_model);
        statusDot = view.findViewById(R.id.status_dot);

        btnSend.setOnClickListener(v -> {
            if (generating) {
                engine.stop();
                return;
            }
            sendInput();
        });
        inputView.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!generating) {
                    refreshSendState(s.toString().trim().isEmpty());
                }
            }
        });

        view.findViewById(R.id.btn_history).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).push(new HistoryFragment());
            }
        });
        view.findViewById(R.id.btn_switch).setOnClickListener(this::showModelPicker);

        refreshHeader();
        refreshSendState(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        consumePendingNavigation();
        refreshHeader();
    }

    @Override
    public void onDestroyView() {
        if (engine != null) {
            engine.release();
        }
        super.onDestroyView();
    }

    private void consumePendingNavigation() {
        if (MockStore.pendingSession != null) {
            adapter.submit(new ArrayList<>(MockStore.pendingSession.messages));
            MockStore.pendingSession = null;
            updateEmptyState();
        }
        if (!TextUtils.isEmpty(MockStore.pendingPrefill)) {
            inputView.setText(MockStore.pendingPrefill);
            inputView.setSelection(inputView.getText().length());
            MockStore.pendingPrefill = null;
            refreshSendState(false);
        }
    }

    private void showModelPicker(View anchor) {
        List<ModelInfo> installed = new ArrayList<>(MockStore.installedModels());
        for (ModelInfo approved : ApprovedModels.installedAsModelInfos(requireContext())) {
            if (MockStore.modelById(approved.id) == null) {
                installed.add(approved);
            }
        }
        ModelInfo current = MockStore.currentModel();
        new ModelPickerSheet(installed,
                current == null ? "" : current.id,
                model -> {
                    MockStore.currentModelId = model.id;
                    engine.release();
                    engine = ChatEngineProvider.create(requireContext(), model.id);
                    refreshHeader();
                    Snackbar.make(requireView(), model.name, Snackbar.LENGTH_SHORT).show();
                }).show(getParentFragmentManager(), "picker");
    }

    private void refreshHeader() {
        ApprovedModels.Approved approved = resolveApproved();
        if (approved != null) {
            String state = generating ? getString(R.string.chat_generating)
                    : getString(R.string.chat_ready);
            modelTitle.setText(approved.displayName + " · " + engine.modeLabel() + " · " + state);
            statusDot.setBackgroundResource(R.drawable.bg_dot);
            return;
        }
        ModelInfo model = MockStore.currentModel();
        if (model == null) {
            modelTitle.setText(R.string.chat_no_model);
            statusDot.setBackgroundResource(R.drawable.bg_dot);
            return;
        }
        String state = generating ? getString(R.string.chat_generating)
                : getString(R.string.chat_ready);
        modelTitle.setText(model.name + " · " + engine.modeLabel() + " · " + state);
    }

    /** 当前选中的批准模型（已安装且文件存在）；否则 null。 */
    private ApprovedModels.Approved resolveApproved() {
        ApprovedModels.Approved approved = ApprovedModels.byId(MockStore.currentModelId);
        if (approved != null && ApprovedModels.isInstalled(requireContext(), approved.modelId)) {
            return approved;
        }
        return null;
    }

    private void refreshSendState(boolean emptyInput) {
        btnSend.setIconResource(generating ? R.drawable.ic_stop : R.drawable.ic_send);
        btnSend.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(),
                        generating ? R.color.status_danger : R.color.md_primary)));
    }

    private void sendInput() {
        String text = inputView.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (resolveApproved() == null && MockStore.currentModel() == null) {
            Snackbar.make(messagesView, R.string.picker_empty, Snackbar.LENGTH_SHORT).show();
            return;
        }
        inputView.setText("");
        adapter.items().add(new ChatMessage(ChatMessage.ROLE_USER, text));
        adapter.notifyItemInserted(adapter.getItemCount() - 1);
        scrollToBottom();
        updateEmptyState();

        generating = true;
        refreshSendState(false);
        refreshHeader();
        engine.start(historySnapshot(), this);
    }

    /** 当前会话的完整消息列表（含刚加入的用户消息）。 */
    private List<ChatMessage> historySnapshot() {
        List<ChatMessage> history = new ArrayList<>();
        for (Object o : adapter.items()) {
            if (o instanceof ChatMessage) {
                history.add((ChatMessage) o);
            }
        }
        return history;
    }

    @Override
    public void onThinking() {
        adapter.items().add(MessageAdapter.TYPING);
        botPosition = adapter.getItemCount() - 1;
        adapter.notifyItemInserted(botPosition);
        scrollToBottom();
    }

    @Override
    public void onDelta(String delta) {
        if (streamingBot == null) {
            if (botPosition >= 0 && botPosition < adapter.items().size()) {
                adapter.items().remove(botPosition);
                adapter.notifyItemRemoved(botPosition);
            }
            streamingBot = new ChatMessage(ChatMessage.ROLE_BOT, delta);
            adapter.items().add(streamingBot);
            botPosition = adapter.getItemCount() - 1;
            adapter.notifyItemInserted(botPosition);
        } else {
            streamingBot.text += delta;
            adapter.updateLast();
        }
        scrollToBottom();
    }

    @Override
    public void onFinished(boolean stopped) {
        if (stopped) {
            if (streamingBot != null) {
                streamingBot.text += "\n（已停止生成）";
                adapter.updateLast();
            } else if (botPosition >= 0 && botPosition < adapter.items().size()) {
                adapter.items().remove(botPosition);
                adapter.notifyItemRemoved(botPosition);
            }
        }
        streamingBot = null;
        botPosition = -1;
        generating = false;
        refreshSendState(inputView.getText().toString().trim().isEmpty());
        refreshHeader();
        saveCurrentConversation();
    }

    @Override
    public void onError(int code, String message) {
        if (botPosition >= 0 && botPosition < adapter.items().size()) {
            adapter.items().remove(botPosition);
            adapter.notifyItemRemoved(botPosition);
        }
        adapter.items().add(new ChatMessage(ChatMessage.ROLE_BOT,
                "生成失败：" + message + "（" + code + "）"));
        adapter.notifyItemInserted(adapter.getItemCount() - 1);
        scrollToBottom();
        streamingBot = null;
        botPosition = -1;
        generating = false;
        refreshSendState(inputView.getText().toString().trim().isEmpty());
        refreshHeader();
        saveCurrentConversation();
    }

    private void saveCurrentConversation() {
        String modelName;
        ApprovedModels.Approved approved = resolveApproved();
        if (approved != null) {
            modelName = approved.displayName;
        } else {
            ModelInfo model = MockStore.currentModel();
            if (model == null) {
                return;
            }
            modelName = model.name;
        }
        ChatSession session = findOrCreateSession(modelName);
        session.messages.clear();
        for (Object o : adapter.items()) {
            if (o instanceof ChatMessage) {
                session.messages.add((ChatMessage) o);
            }
        }
        if (!session.messages.isEmpty()) {
            ChatMessage first = session.messages.get(0);
            session.title = first.text.length() > 24
                    ? first.text.substring(0, 24) : first.text;
            session.timeLabel = "刚刚";
        }
    }

    private ChatSession findOrCreateSession(String modelName) {
        if (!adapter.items().isEmpty()) {
            Object firstObj = adapter.items().get(0);
            if (firstObj instanceof ChatMessage) {
                String firstText = ((ChatMessage) firstObj).text;
                for (ChatSession s : MockStore.SESSIONS) {
                    if (!s.messages.isEmpty()
                            && s.messages.get(0).text.equals(firstText)) {
                        return s;
                    }
                }
            }
        }
        ChatSession created = new ChatSession(
                MockStore.nextSessionId(), modelName, modelName, "刚刚");
        MockStore.SESSIONS.add(0, created);
        return created;
    }

    private void showMessageMenu(ChatMessage message, int position) {
        PopupMenu menu = new PopupMenu(requireContext(), messagesView);
        menu.getMenuInflater().inflate(R.menu.menu_message, menu.getMenu());
        menu.getMenu().findItem(R.id.action_retry).setVisible(
                message.role == ChatMessage.ROLE_USER && !generating);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_copy) {
                copyToClipboard(message.text);
            } else if (id == R.id.action_delete_msg) {
                adapter.items().remove(position);
                adapter.notifyDataSetChanged();
                updateEmptyState();
            } else if (id == R.id.action_retry) {
                retryFrom(message, position);
            }
            return true;
        });
        menu.show();
    }

    private void retryFrom(ChatMessage userMessage, int position) {
        while (adapter.items().size() > position) {
            adapter.items().remove(adapter.items().size() - 1);
        }
        adapter.notifyDataSetChanged();
        generating = true;
        refreshSendState(false);
        refreshHeader();
        engine.start(historySnapshot(), this);
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("message", text));
        Snackbar.make(messagesView, R.string.toast_copied, Snackbar.LENGTH_SHORT).show();
    }

    private void updateEmptyState() {
        emptyWrap.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void scrollToBottom() {
        messagesView.post(() -> messagesView.smoothScrollToPosition(
                Math.max(0, adapter.getItemCount() - 1)));
    }
}
