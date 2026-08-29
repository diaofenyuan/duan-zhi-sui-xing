package com.example.localai.model

/**
 * 会话消息（角色 + 文本）。文本为可变的流式累积缓冲。
 * 因 text 在流式生成期间被原地追加，且 Java 调用方按字段访问，故使用普通类 + @JvmField
 * 而非 data class，以保持与 Java 版完全一致的对象同一性（identity equals）与字段访问语义。
 */
class ChatMessage(
    @JvmField val role: Int,
    @JvmField var text: String
) {
    companion object {
        @JvmField val ROLE_USER = 0
        @JvmField val ROLE_BOT = 1
    }
}
