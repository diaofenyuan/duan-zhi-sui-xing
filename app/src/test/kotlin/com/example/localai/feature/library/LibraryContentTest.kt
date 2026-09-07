package com.example.localai.feature.library

import com.example.localai.data.room.SourceEntity
import org.junit.Assert.*
import org.junit.Test

class LibraryContentTest {
    @Test fun actionExtractionPreservesDeadlinesAndExcludesCompletedItems() {
        val statements = LibraryContent.actionStatements("张明负责整理数据，周五提交报告。王红已完成验收。无需准备材料。王红周六准备演示。")
        assertEquals(listOf("张明负责整理数据，周五提交报告", "王红周六准备演示"), statements.map { it.text })
    }
    private fun source(id: Long, name: String, pages: List<SourcePage>) = SourceEntity().apply {
        this.id = id; this.name = name; pagesJson = LibraryContent.gson.toJson(pages)
    }
    @Test fun retrievalKeepsExactSourcePageAndOffsets() {
        val original = "项目会议记录。".repeat(60) + "验收日期是九月二十日，负责人是张明。" + "下一轮另行安排。".repeat(60)
        val doc = source(7, "项目计划.pdf", listOf(SourcePage(2, original)))
        val found = LibraryContent.retrieve("项目验收日期是什么？", listOf(doc))
        assertTrue(found.any { it.excerpt.contains("九月二十日") })
        found.forEach {
            assertEquals(7L, it.sourceId); assertEquals(2, it.page)
            assertEquals(it.excerpt, original.substring(it.start, it.start + it.excerpt.length))
        }
    }
    @Test fun unrelatedQuestionHasNoEvidenceAndSelectionIsRespected() {
        val course = source(1, "课程", listOf(SourcePage(1, "实验报告周五提交。")))
        val travel = source(2, "旅行", listOf(SourcePage(1, "周六乘高铁前往南京。")))
        assertTrue(LibraryContent.retrieve("火星大气密度", listOf(course, travel)).isEmpty())
        assertTrue(LibraryContent.retrieve("高铁目的地", listOf(course)).isEmpty())
        assertEquals(2L, LibraryContent.retrieve("高铁目的地", listOf(travel)).first().sourceId)
    }
    @Test fun chineseEncodingsAreDecodedWithoutReplacementCharacters() {
        val chinese = "离线资料：项目截止日期为九月七日。"
        assertEquals(chinese, LibraryRepository.decodeText(chinese.toByteArray(Charsets.UTF_8)))
        assertEquals(chinese, LibraryRepository.decodeText(chinese.toByteArray(charset("GB18030"))))
        assertEquals(chinese, LibraryRepository.decodeText(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + chinese.toByteArray(Charsets.UTF_16LE)))
    }
    @Test fun exportPreservesVerbatimCitationsAndChecklistState() {
        val evidence = Citation(1, "会议记录", 3, "王红周四提交报告。", 0)
        val text = LibraryContent.export("工作清单", "- [x] 提交报告", listOf(evidence))
        assertTrue(text.contains("[x]")); assertTrue(text.contains("第 3 页")); assertTrue(text.contains(evidence.excerpt))
        assertEquals("提交报告", LibraryContent.checklist("1. 提交报告").first().text)
    }
}
