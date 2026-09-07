package com.example.localai.feature.library

import com.example.localai.data.room.SourceEntity
import com.google.gson.Gson
import kotlin.math.ln

data class SourcePage(val number: Int, val text: String)
data class Citation(val sourceId: Long, val name: String, val page: Int, val excerpt: String, val start: Int)
data class ChecklistItem(var text: String, var checked: Boolean = false)

/** 确定性检索与原文定位；模型只收到命中的片段，无权改写引用内容。 */
object LibraryContent {
    val gson = Gson()
    fun pages(source: SourceEntity): List<SourcePage> =
        gson.fromJson(source.pagesJson, Array<SourcePage>::class.java)?.toList().orEmpty()

    fun terms(text: String): Set<String> {
        val words = Regex("[a-z0-9]+|[\\p{IsHan}]+").findAll(text.lowercase())
        val stop = setOf("什么", "如何", "这个", "那个", "资料", "请问", "一下", "根据", "进行", "哪些", "是否", "可以", "我们", "的", "是")
        return words.flatMap { match ->
            val s = match.value
            if (s.first().code in 0x3400..0x9fff && s.length > 1) s.windowed(2).asSequence()
            else sequenceOf(s)
        }.filter { it !in stop && it.length > 1 }.toSet()
    }

    fun chunks(source: SourceEntity, size: Int = 420): List<Citation> = pages(source).flatMap { page ->
        buildList {
            var offset = 0
            while (offset < page.text.length) {
                val end = (offset + size).coerceAtMost(page.text.length)
                val text = page.text.substring(offset, end)
                if (text.isNotBlank()) add(Citation(source.id, source.name, page.number, text, offset))
                if (end == page.text.length) break
                offset = end - 60
            }
        }
    }

    fun retrieve(question: String, sources: List<SourceEntity>, limit: Int = 3): List<Citation> {
        val query = terms(question)
        if (query.isEmpty()) return emptyList()
        val corpus = sources.flatMap { chunks(it) }
        val bag = corpus.map { terms(it.excerpt) }
        val ranked = corpus.indices.mapNotNull { i ->
            val overlap = query.intersect(bag[i])
            if (overlap.isEmpty()) null else {
                val score = overlap.sumOf { term -> ln(1.0 + corpus.size.toDouble() / (1 + bag.count { term in it })) }
                i to score
            }
        }.sortedByDescending { it.second }
        // 避免同一段的重叠窗口占满上下文。
        val chosen = mutableListOf<Citation>()
        for ((i, _) in ranked) {
            val item = corpus[i]
            if (chosen.none { it.sourceId == item.sourceId && it.page == item.page && kotlin.math.abs(it.start - item.start) < 300 }) chosen.add(item)
            if (chosen.size >= limit) break
        }
        return chosen
    }

    fun checklist(text: String): List<ChecklistItem> = text.lineSequence()
        .map { it.trim().replace(Regex("^(?:[-*•]|\\d+[.、)）])\\s*(?:\\[[ xX]\\])?\\s*"), "") }
        .filter { it.isNotBlank() }.map { ChecklistItem(it) }.toList()

    fun actionStatements(text: String): List<ChecklistItem> {
        val action = Regex("负责|需要|务必|请(?:于|在|将|先)|提交|交付|安排|准备|完成|截止|验收|提醒")
        val negated = Regex("无需|不必|不用|取消|已经完成|已完成|已提交|已验收")
        return text.split(Regex("[。！？\\n]+"))
            .map { it.trim().replace(Regex("^[-*•]\\s*"), "") }
            .filter { it.length in 4..500 && action.containsMatchIn(it) && !negated.containsMatchIn(it) }
            .distinct().map { ChecklistItem(it) }
    }

    fun export(title: String, output: String, citations: List<Citation>): String = buildString {
        append(title).append("\n\n").append(output)
        if (citations.isNotEmpty()) {
            append("\n\n参考原文\n")
            citations.forEachIndexed { i, c -> append("[${i + 1}] ${c.name} · 第 ${c.page} 页\n${c.excerpt}\n\n") }
        }
    }
}
