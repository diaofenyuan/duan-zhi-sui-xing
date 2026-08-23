package com.example.localai.model;

import java.util.ArrayList;
import java.util.List;

public class ChatSession {

    public final String id;
    public String title;
    public final String modelName;
    public String timeLabel;
    public final List<ChatMessage> messages = new ArrayList<>();

    public ChatSession(String id, String title, String modelName, String timeLabel) {
        this.id = id;
        this.title = title;
        this.modelName = modelName;
        this.timeLabel = timeLabel;
    }

    public void add(ChatMessage message) {
        messages.add(message);
    }

    public String preview() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage m = messages.get(i);
            if (m.role == ChatMessage.ROLE_BOT && m.text != null && !m.text.isEmpty()) {
                return m.text.replace("\n", " ");
            }
        }
        return "";
    }
}
