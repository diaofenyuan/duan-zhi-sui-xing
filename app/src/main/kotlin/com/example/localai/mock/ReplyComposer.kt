package com.example.localai.mock

/** 纯文本回复生成器：按关键词返回固定文案，供流式模拟使用（可单元测试）。 */
object ReplyComposer {

    @JvmStatic
    fun build(prompt: String?): String {
        val p = (prompt ?: "").lowercase()

        if (p.contains("你好") || p.contains("hello") || p.contains("hi")
            || p.contains("你是谁") || p.contains("介绍")) {
            return "你好！我是一个完全运行在你手机本地的对话模型。\n" +
                    "我的所有推理都在这台设备上完成，对话内容不会上传到任何服务器。\n" +
                    "有什么可以帮你的吗？"
        }
        if (p.contains("kv") || p.contains("缓存")) {
            return "KV Cache 可以理解成模型的“草稿纸”：\n\n" +
                    "1. 模型每处理一个词，就把中间结果记在草稿上；\n" +
                    "2. 生成下一个词时直接复用草稿，不用从头再算；\n" +
                    "3. 代价是草稿会随上下文变长而变大，这也是长对话更占内存的原因。"
        }
        if (p.contains("诗")) {
            return "没有信号的地方\n" +
                    "思想仍在掌心生长\n" +
                    "一部手机，一座图书馆\n\n" +
                    "——愿每一次离线的相遇，都同样聪慧。"
        }
        if (p.contains("代码") || p.contains("java") || p.contains("函数")
            || p.contains("code") || p.contains("下载")) {
            return "好的，给你一个思路示例：\n\n" +
                    "1. 用 HttpURLConnection 打开连接并请求 Range 头实现断点续传；\n" +
                    "2. 边读边写入临时文件，定期落盘进度；\n" +
                    "3. 下载完成后先做 SHA-256 校验，再原子重命名到目标目录。\n\n" +
                    "完整实现将在后续版本中提供，当前为演示数据。"
        }
        if (p.contains("量化")) {
            return "模型量化就是把高精度的权重（如 FP16）压缩成更低的精度（如 INT4）：\n\n" +
                    "· 体积更小：4GB → 约 2.4GB；\n" +
                    "· 速度更快：内存带宽压力降低；\n" +
                    "· 精度略降：多数日常场景几乎无感。\n\n" +
                    "常见格式如 Q4_K_M 是体积与质量的平衡选择。"
        }
        return "收到！这是来自本地模型的演示回复。\n\n" +
                "在正式版本中，我会基于 llama.cpp 在你的手机上真实生成内容；\n" +
                "当前 P1 阶段用于演示流式输出、停止与界面交互。\n\n" +
                "你可以试试问我“什么是 KV Cache”，或者让我写一首诗。"
    }

    /** 将回复切成小片段，模拟 token 流。 */
    @JvmStatic
    fun chunk(reply: String): Array<String> {
        val parts = Array((reply.length + 1) / 2) { "" }
        var j = 0
        var i = 0
        while (i < reply.length) {
            parts[j] = reply.substring(i, minOf(reply.length, i + 2))
            i += 2
            j++
        }
        return parts
    }
}
