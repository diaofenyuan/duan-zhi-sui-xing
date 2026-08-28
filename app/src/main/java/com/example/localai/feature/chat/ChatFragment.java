package com.example.localai.feature.chat;

import android.content.Context;
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
import com.example.localai.data.ServiceLocator;
import com.example.localai.data.room.ModelEntity;
import com.example.localai.model.ChatMessage;
import com.example.localai.model.ModelInfo;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * 本地对话页：流式输出（演示/真实推理双引擎）、停止、复制/重试/删除、
 * 模型切换（真实已安装模型）与会话持久化（Room：新建/自动保存/恢复）。
 */
public class ChatFragment extends Fragment implements ChatEngine.StreamListener {

    private static final String PREFS = "localai_chat";
    private static final String KEY_MODEL = "current_model";

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

    /** 当前会话 id（<=0 表示尚未持久化的新会话）。 */
    private long conversationId = 0;
    private String currentModelId = "";
    private boolean pendingModelSwitch = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        currentModelId = prefs().getString(KEY_MODEL, "");
        engine = ChatEngineProvider.create(requireContext(), currentModelId);
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
        view.findViewById(R.id.btn_new).setOnClickListener(v -> startNewConversation());
        view.findViewById(R.id.btn_switch).setOnClickListener(this::showModelPicker);

        refreshHeader();
        refreshSendState(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        consumePendingNavigation();
        ensureModelSelected();
        refreshHeader();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        // Tab 切换走 show/hide，不触发 onResume；重新可见时需刷新模型状态
        if (!hidden) {
            consumePendingNavigation();
            ensureModelSelected();
            refreshHeader();
        }
    }

    @Override
    public void onDestroyView() {
        if (engine != null) {
            engine.release();
        }
        super.onDestroyView();
    }

    private void consumePendingNavigation() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            long pendingConversation = activity.consumePendingConversation();
            if (pendingConversation > 0) {
                loadConversation(pendingConversation);
            }
            String pendingModel = activity.consumePendingChatModel();
            if (pendingModel != null && !pendingModel.equals(currentModelId)) {
                setCurrentModel(pendingModel);
            }
            String prefill = activity.consumeChatPrefill();
            if (prefill != null) {
                // 详情页示例指令：如果是新会话，预填输入框
                if (adapter.items().isEmpty()) {
                    inputView.setText(prefill);
                    inputView.setSelection(inputView.getText().length());
                    refreshSendState(false);
                }
            }
        }
    }

    private void loadConversation(long id) {
        ChatRepository repository = ServiceLocator.chat();
        if (repository == null) {
            return;
        }
        repository.loadMessages(id, (messages, error) -> {
            if (!isAdded()) {
                return;
            }
            adapter.submit(new ArrayList<>());
            if (messages != null) {
                for (ChatMessage m : messages) {
                    adapter.items().add(m);
                }
                adapter.notifyDataSetChanged();
            }
            conversationId = id;
            updateEmptyState();
            refreshHeader();
        });
    }

    private void startNewConversation() {
        adapter.submit(new ArrayList<>());
        conversationId = 0;
        updateEmptyState();
    }

    private void showModelPicker(View anchor) {
        List<ModelInfo> installed = installedModelInfos();
        ModelInfo current = installedModelInfo(currentModelId);
        if (current == null && !installed.isEmpty()) {
            current = installed.get(0);
        }
        new ModelPickerSheet(installed,
                current == null ? "" : current.id,
                model -> setCurrentModel(model.id)).show(getParentFragmentManager(), "picker");
    }

    /** 真实已安装模型：Room installed_models ∪ 批准模型文件存在。 */
    private List<ModelInfo> installedModelInfos() {
        List<ModelInfo> result = new ArrayList<>();
        List<ModelEntity> installed = ServiceLocator.downloads() == null
                ? new ArrayList<ModelEntity>() : ServiceLocator.downloads().installed();
        for (ModelEntity entity : installed) {
            result.add(entityToInfo(entity));
        }
        if (ApprovedModels.isInstalled(requireContext(), ApprovedModels.SMOLLM_135M.modelId)) {
            boolean present = false;
            for (ModelInfo m : result) {
                if (m.id.equals(ApprovedModels.SMOLLM_135M.modelId)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                result.add(approvedToInfo());
            }
        }
        return result;
    }

    private ModelInfo installedModelInfo(String modelId) {
        for (ModelInfo m : installedModelInfos()) {
            if (m.id.equals(modelId)) {
                return m;
            }
        }
        return null;
    }

    private ModelInfo entityToInfo(ModelEntity entity) {
        return new ModelInfo(
                entity.modelId, entity.displayName == null ? entity.modelId : entity.displayName,
                entity.publisher == null ? "" : entity.publisher,
                "0.1B", 0.1, entity.quantization == null ? "" : entity.quantization,
                "0 MB", entity.sizeBytes, "1K", ModelInfo.TASK_TEXT,
                ModelInfo.langs("英文"), entity.licenseSpdx == null ? "" : entity.licenseSpdx,
                "", ModelInfo.COMPAT_RECOMMENDED, "", 0, 0, 0, "", 0, true);
    }

    private ModelInfo approvedToInfo() {
        return ApprovedModels.installedAsModelInfos(requireContext()).get(0);
    }

    private void setCurrentModel(String modelId) {
        applyModel(modelId, true);
    }

    /** 从未选过模型时自动选中第一个已安装模型，避免已安装却提示“未安装模型”。 */
    private void ensureModelSelected() {
        if (currentModelId != null && !currentModelId.isEmpty()) {
            return;
        }
        List<ModelInfo> installed = installedModelInfos();
        if (installed.isEmpty()) {
            return;
        }
        applyModel(installed.get(0).id, false);
    }

    private void applyModel(String modelId, boolean announce) {
        currentModelId = modelId;
        prefs().edit().putString(KEY_MODEL, modelId).apply();
        engine.release();
        engine = ChatEngineProvider.create(requireContext(), modelId);
        refreshHeader();
        if (announce) {
            ModelInfo model = installedModelInfo(modelId);
            if (model != null) {
                Snackbar.make(requireView(), model.name, Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void refreshHeader() {
        if (currentModelId == null || currentModelId.isEmpty()) {
            modelTitle.setText(R.string.chat_no_model);
            setStatusDot(R.color.status_warn);
            return;
        }
        ApprovedModels.Approved approved = resolveApproved();
        String state = generating ? getString(R.string.chat_generating)
                : getString(R.string.chat_ready);
        String label = engine == null ? "" : engine.modeLabel();
        if (approved != null) {
            modelTitle.setText(approved.displayName + " · " + label + " · " + state);
        } else {
            ModelInfo model = installedModelInfo(currentModelId);
            if (model == null) {
                modelTitle.setText(R.string.chat_no_model);
            } else {
                modelTitle.setText(model.name + " · " + label + " · " + state);
            }
        }
        setStatusDot(resolveApproved() != null ? R.color.status_success : R.color.status_warn);
    }

    /** 头部状态点：按语义着色（就绪绿 / 未安装琥珀）。 */
    private void setStatusDot(int colorRes) {
        statusDot.setBackgroundResource(R.drawable.bg_dot);
        statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), colorRes)));
    }

    /** 当前选中的批准模型（已安装且文件存在）；否则 null。 */
    private ApprovedModels.Approved resolveApproved() {
        ApprovedModels.Approved approved = ApprovedModels.byId(currentModelId);
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
        if (currentModelId == null || currentModelId.isEmpty()) {
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
        persistConversation();
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
        persistConversation();
    }

    /** 全量覆盖保存会话（新会话自动创建并回填 id）。 */
    private void persistConversation() {
        ChatRepository repository = ServiceLocator.chat();
        if (repository == null) {
            return;
        }
        repository.saveConversation(conversationId, currentModelId, deriveTitle(), historySnapshot(),
                new ChatRepository.ConversationSavedCallback() {
                    @Override
                    public void onSaved(long id) {
                        conversationId = id;
                    }

                    @Override
                    public void onError(String message) {
                        Snackbar.make(requireView(),
                                "会话保存失败：" + message, Snackbar.LENGTH_SHORT).show();
                    }
                });
        repository.refresh();
    }

    private String deriveTitle() {
        for (Object o : adapter.items()) {
            if (o instanceof ChatMessage) {
                String text = ((ChatMessage) o).text;
                if (text != null && !text.isEmpty()) {
                    return text.length() > 24 ? text.substring(0, 24) : text;
                }
            }
        }
        return "";
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
                persistConversation();
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

    private android.content.SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
