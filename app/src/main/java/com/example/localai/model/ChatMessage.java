package com.example.localai.model;

public class ChatMessage {

    public static final int ROLE_USER = 0;
    public static final int ROLE_BOT = 1;

    public final int role;
    public String text;

    public ChatMessage(int role, String text) {
        this.role = role;
        this.text = text;
    }
}
