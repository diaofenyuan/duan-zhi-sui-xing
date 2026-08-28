package com.example.localai.feature.chat;

import com.example.localai.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文裁剪（纯 Java，可单测）：
 * 把完整对话历史压缩到 contextLength 预算内，规则可解释：
 *  1. 预算 = ctxLen * 0.55（其余留给回复本身）；
 *  2. 从最新一条开始保留，逐条累计占用（用户+助手成对计入），直到预算;
 *  3. 最新一条用户消息始终保留（超预算时对其文本截断）；
 *  4. 被丢弃的历史不会静默消失——返回 droppedCount，UI/引擎可记录。
 */
public final class ChatHistoryTrimmer {

    private static final double HISTORY_BUDGET = 0.55;

    private ChatHistoryTrimmer() {
    }

    public static final class Trimmed {
        public final List<ChatMessage> kept;
        public final int droppedCount;

        Trimmed(List<ChatMessage> kept, int droppedCount) {
            this.kept = kept;
            this.droppedCount = droppedCount;
        }
    }

    public static Trimmed truncate(List<ChatMessage> history, int contextLength) {
        if (history == null || history.isEmpty()) {
            return new Trimmed(new ArrayList<>(), 0);
        }
        int budgetTokens = (int) (Math.max(1, contextLength) * HISTORY_BUDGET);
        List<ChatMessage> kept = new ArrayList<>();
        int used = 0;
        int dropped = 0;

        for (int i = history.size() - 1; i >= 0; i--) {
            ChatMessage m = history.get(i);
            if (m.role != ChatMessage.ROLE_USER && m.role != ChatMessage.ROLE_BOT) {
                dropped++;
                continue;
            }
            int pairStart = i;
            if (m.role == ChatMessage.ROLE_BOT) {
                int j = i - 1;
                while (j >= 0 && history.get(j).role == ChatMessage.ROLE_BOT) {
                    j--;
                }
                if (j >= 0 && history.get(j).role == ChatMessage.ROLE_USER) {
                    pairStart = j;
                }
            }
            List<ChatMessage> pair = history.subList(pairStart, i + 1);
            int tokens = 0;
            for (ChatMessage pm : pair) {
                tokens += approxTokens(pm.text);
            }
            int remaining = budgetTokens - used;
            if (tokens <= remaining) {
                for (int k = 0; k < pair.size(); k++) {
                    kept.add(k, pair.get(k));
                }
                used += tokens;
                if (pairStart == 0) {
                    break;
                }
                i = pairStart;
                continue;
            }
            // 这一对放不下：
            // 1) 若它包含最新一条用户消息（i == size-1 或 pair 末项是 user），裁剪该消息并收尾；
            // 2) 否则整对丢弃，继续向更早的历史回退? 不：预算不足时较早的更小，
            //    直接丢弃并继续（等更小条目）
            ChatMessage lastPair = pair.get(pair.size() - 1);
            if (lastPair.role == ChatMessage.ROLE_USER && lastPair == history.get(history.size() - 1)) {
                ChatMessage clipped = new ChatMessage(ChatMessage.ROLE_USER,
                        truncateChars(lastPair.text, Math.max(8, remaining)));
                kept.add(0, clipped);
                dropped += history.size() - 1 - kept.size();
                break;
            }
            dropped += pair.size();
            i = pairStart;
        }
        if (kept.isEmpty()) {
            // 兜底：至少要能带上最新一条用户消息
            ChatMessage last = history.get(history.size() - 1);
            if (last.role == ChatMessage.ROLE_USER) {
                kept.add(new ChatMessage(ChatMessage.ROLE_USER,
                        truncateChars(last.text, Math.max(8, budgetTokens))));
            } else {
                kept.add(history.get(history.size() - 1));
            }
        }
        return new Trimmed(kept, dropped);
    }

    public static int approxTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chars = text.length();
        // 中文/全角字符约 1.5 字符一个 token，其余约 4 字符一个 token；
        // 混合输入保守取 /2。
        return Math.max(1, (chars + 1) / 2);
    }

    private static String truncateChars(String text, int budgetTokens) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int budgetChars = Math.max(0, budgetTokens * 2);
        if (text.length() <= budgetChars) {
            return text;
        }
        return text.substring(0, budgetChars) + "\n……（较早内容已按上下文上限裁剪）";
    }
}
