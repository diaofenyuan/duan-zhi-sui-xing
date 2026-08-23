package com.example.localai.mock;

import com.example.localai.model.BenchEntry;
import com.example.localai.model.ChatMessage;
import com.example.localai.model.ChatSession;
import com.example.localai.model.DownloadTask;
import com.example.localai.model.ModelInfo;

import java.util.ArrayList;
import java.util.List;

/** P1 演示数据仓库：纯 Java，无 Android 依赖，便于单元测试与后续替换为真实数据层。 */
public final class MockStore {

    public static final List<ModelInfo> MODELS = new ArrayList<>();
    public static final List<DownloadTask> TASKS = new ArrayList<>();
    public static final List<ChatSession> SESSIONS = new ArrayList<>();

    /** 跨页面导航用的暂存状态。 */
    public static String pendingPrefill;
    public static ChatSession pendingSession;
    public static String currentModelId = "qwen3-4b";

    private static int sessionSeq = 100;

    static {
        seedModels();
        seedTasks();
        seedSessions();
    }

    private MockStore() {
    }

    private static void seedModels() {
        MODELS.add(new ModelInfo("qwen3-4b", "Qwen3-4B-Instruct", "通义千问",
                "4B", 4.0, "Q4_K_M", "2.4 GB", 2_400_000_000L, "32K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("中文", "多语言"), "Apache-2.0",
                "当前中文综合能力最强的开源小模型之一，指令遵循稳定，适合作为日常默认对话模型。",
                ModelInfo.COMPAT_RECOMMENDED, "", 320, 14.2, 3100,
                "2026-08-18", 0, true));
        MODELS.add(new ModelInfo("llama3.2-3b", "Llama-3.2-3B-Instruct", "Meta",
                "3B", 3.0, "Q4_K_M", "1.9 GB", 1_900_000_000L, "128K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("英文"), "Llama 3.2 Community",
                "Meta 官方小模型，长上下文窗口大，英文任务表现出色；建议将上下文降至 8K 获得更佳内存余量。",
                ModelInfo.COMPAT_RUNNABLE, "上下文较长时内存余量偏紧，建议在设置中限制为 8K。",
                380, 12.6, 2700,
                "2026-08-10", 1, true));
        MODELS.add(new ModelInfo("ds-r1-1.5b", "DeepSeek-R1-Distill-1.5B", "DeepSeek",
                "1.5B", 1.5, "Q4_K_M", "1.1 GB", 1_100_000_000L, "32K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("中文", "英文"), "MIT",
                "推理型蒸馏模型，数学与逻辑链路清晰，体积小速度快，入门机型也能流畅运行。",
                ModelInfo.COMPAT_RECOMMENDED, "", 260, 18.9, 1600,
                "2026-07-30", 2, false));
        MODELS.add(new ModelInfo("qwen-coder-1.5b", "Qwen2.5-Coder-1.5B-Instruct", "通义千问",
                "1.5B", 1.5, "Q4_K_M", "1.0 GB", 1_000_000_000L, "16K",
                ModelInfo.TASK_CODE, ModelInfo.langs("多语言"), "Apache-2.0",
                "代码补全与解释专用模型，支持主流编程语言，适合配合笔记类应用做本地代码问答。",
                ModelInfo.COMPAT_RECOMMENDED, "", 300, 16.4, 1700,
                "2026-06-21", 3, false));
        MODELS.add(new ModelInfo("gemma-3-1b", "Gemma-3-1B-IT", "Google",
                "1B", 1.0, "Q4_0", "0.8 GB", 800_000_000L, "32K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("多语言"), "Gemma Terms of Use",
                "Google 轻量指令模型，多语言均衡；长时间生成在部分机型可能触发温控降频。",
                ModelInfo.COMPAT_HIGH_LOAD, "入门机型长时间生成可能触发温控降频，建议控制单轮输出长度。",
                240, 11.8, 1200,
                "2026-05-28", 1, false));
        MODELS.add(new ModelInfo("phi-3.5-mini", "Phi-3.5-mini-instruct", "Microsoft",
                "3.8B", 3.8, "Q4_K_M", "2.3 GB", 2_300_000_000L, "128K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("多语言"), "MIT",
                "微软轻量高密度模型，推理能力强；但对运行内存要求较高。",
                ModelInfo.COMPAT_UNSUPPORTED, "可用运行内存低于最低要求（需 ≥ 3.0 GB 连续空间）。",
                0, 0, 0,
                "2026-05-12", 2, false));
        MODELS.add(new ModelInfo("yi-1.5-9b", "Yi-1.5-9B-Chat", "零一万物",
                "9B", 9.0, "Q4_K_M", "5.3 GB", 5_300_000_000L, "32K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("中文", "英文"), "Apache-2.0",
                "中英双语高质量对话模型，知识面广；仅建议旗舰机型在高电量状态尝试。",
                ModelInfo.COMPAT_HIGH_LOAD, "峰值内存接近 8 GB 设备上限，生成速度明显下降。",
                520, 6.8, 6200,
                "2026-04-02", 0, false));
        MODELS.add(new ModelInfo("smollm2-360m", "SmolLM2-360M-Instruct", "HuggingFaceTB",
                "0.36B", 0.36, "Q8_0", "0.4 GB", 400_000_000L, "8K",
                ModelInfo.TASK_TEXT, ModelInfo.langs("英文"), "Apache-2.0",
                "超小体积极速模型，响应几乎即时，适合演示、草稿与简单问答场景。",
                ModelInfo.COMPAT_RECOMMENDED, "", 180, 24.5, 600,
                "2026-03-15", 3, false));
    }

    private static void seedTasks() {
        TASKS.add(new DownloadTask("t-qwen-coder", "qwen-coder-1.5b",
                "Qwen2.5-Coder-1.5B-Instruct", 1_000_000_000L, 380_000_000L,
                DownloadTask.State.DOWNLOADING));
        TASKS.add(new DownloadTask("t-gemma", "gemma-3-1b",
                "Gemma-3-1B-IT", 800_000_000L, 496_000_000L,
                DownloadTask.State.PAUSED));
        DownloadTask failed = new DownloadTask("t-yi", "yi-1.5-9b",
                "Yi-1.5-9B-Chat", 5_300_000_000L, 5_300_000_000L,
                DownloadTask.State.FAILED);
        failed.failReason = "SHA-256 校验失败";
        TASKS.add(failed);
    }

    private static void seedSessions() {
        ChatSession s1 = new ChatSession(nextSessionId(), "解释一下 KV Cache 的作用",
                "Qwen3-4B-Instruct", "昨天 21:34");
        s1.add(new ChatMessage(ChatMessage.ROLE_USER, "用通俗的语言解释一下 KV Cache 的作用"));
        s1.add(new ChatMessage(ChatMessage.ROLE_BOT,
                "KV Cache 就像考试时的草稿纸：模型每读一个字，就把“已经理解的内容”记在草稿上。\n"
                        + "下次再读后面的字时不用从头回忆，直接看草稿就能继续，所以生成速度会快很多。"));
        s1.add(new ChatMessage(ChatMessage.ROLE_USER, "那它为什么占用显存？"));
        s1.add(new ChatMessage(ChatMessage.ROLE_BOT, "因为草稿要一直留着：上下文越长、层数越多，草稿就越大。这也是长对话更吃内存的主要原因。"));
        SESSIONS.add(s1);

        ChatSession s2 = new ChatSession(nextSessionId(), "优化这段布局嵌套",
                "Llama-3.2-3B-Instruct", "前天 14:02");
        s2.add(new ChatMessage(ChatMessage.ROLE_USER, "帮我看看这段 Android 布局嵌套太深怎么优化"));
        s2.add(new ChatMessage(ChatMessage.ROLE_BOT, "可以用 ConstraintLayout 把多层 LinearLayout 拍平：先确定锚点视图，再用 barrier 处理不定宽文本…"));
        SESSIONS.add(s2);

        ChatSession s3 = new ChatSession(nextSessionId(), "写一首关于离线 AI 的短诗",
                "Qwen3-4B-Instruct", "3 天前");
        s3.add(new ChatMessage(ChatMessage.ROLE_USER, "写一首关于离线 AI 的短诗"));
        s3.add(new ChatMessage(ChatMessage.ROLE_BOT, "没有信号的地方\n思想仍在掌心生长\n一部手机，一座图书馆"));
        SESSIONS.add(s3);

        ChatSession s4 = new ChatSession(nextSessionId(), "Rust 与 Go 嵌入移动端的差异",
                "DeepSeek-R1-Distill-1.5B", "2026-08-15");
        s4.add(new ChatMessage(ChatMessage.ROLE_USER, "Rust 和 Go 在移动端嵌入方面各有什么优劣？"));
        s4.add(new ChatMessage(ChatMessage.ROLE_BOT, "先说结论：极致控制与体积选 Rust，开发效率与并发选 Go……"));
        s4.add(new ChatMessage(ChatMessage.ROLE_USER, "那 llama.cpp 为什么用 C++ 实现？"));
        s4.add(new ChatMessage(ChatMessage.ROLE_BOT, "因为它需要直接控制 SIMD 指令、内存布局和线程调度，这些是性能敏感推理的根基。"));
        SESSIONS.add(s4);
    }

    public static String nextSessionId() {
        return "s-" + (sessionSeq++);
    }

    public static ModelInfo modelById(String id) {
        for (ModelInfo m : MODELS) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    public static List<ModelInfo> installedModels() {
        List<ModelInfo> result = new ArrayList<>();
        for (ModelInfo m : MODELS) {
            if (m.installed) {
                result.add(m);
            }
        }
        return result;
    }

    public static ModelInfo currentModel() {
        ModelInfo current = modelById(currentModelId);
        if (current != null && current.installed) {
            return current;
        }
        List<ModelInfo> installed = installedModels();
        if (!installed.isEmpty()) {
            currentModelId = installed.get(0).id;
            return installed.get(0);
        }
        currentModelId = null;
        return null;
    }

    public static List<BenchEntry> benchmarks() {
        List<BenchEntry> result = new ArrayList<>();
        for (ModelInfo m : installedModels()) {
            if (m.tps > 0) {
                result.add(new BenchEntry(m.name, m.ttftMs, m.tps, m.memMb / 1000.0));
            }
        }
        return result;
    }
}
