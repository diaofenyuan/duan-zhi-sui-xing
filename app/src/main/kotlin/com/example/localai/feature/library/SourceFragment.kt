package com.example.localai.feature.library

import android.os.Bundle
import android.view.*
import com.example.localai.data.ServiceLocator

class SourceFragment : LibraryUi() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = page("资料原文")
    override fun onViewCreated(view: View, state: Bundle?) {
        val citation = arguments?.getString("citation")?.let { LibraryContent.gson.fromJson(it, Citation::class.java) }
        ServiceLocator.library()!!.source(requireArguments().getLong("id")) { result ->
            if (this.view == null) return@source
            result.onSuccess { source ->
                body.removeAllViews()
                if (source == null) {
                    body.addView(label("原资料已删除，以下是保存时的引用快照。", secondary = true))
                    citation?.let { body.addView(label("${it.name} · 第 ${it.page} 页", 18f)); body.addView(label(it.excerpt).apply { setTextIsSelectable(true) }) }
                } else {
                    body.addView(label(source.name, 20f))
                    if (source.type == "ocr") body.addView(label("来源为图片识别后保存的文字。请结合原图核对，尤其是数字与专有名词。", secondary = true))
                    val pages = LibraryContent.pages(source)
                    val ordered = if (citation == null) pages else pages.sortedBy { if (it.number == citation.page) 0 else it.number }
                    ordered.forEach { page ->
                        body.addView(label("第 ${page.number} 页", 16f, true))
                        val text = label(page.text).apply { setTextIsSelectable(true) }
                        if (citation != null && citation.page == page.number) {
                            val start = citation.start.coerceIn(0, page.text.length)
                            val end = (start + citation.excerpt.length).coerceAtMost(page.text.length)
                            text.text = android.text.SpannableString(page.text).apply { setSpan(android.text.style.BackgroundColorSpan(0x5544AA88), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                        }
                        body.addView(text); body.addView(divider())
                        if (citation != null && citation.page == page.number) text.post {
                            if (this.view == null) return@post
                            val line = text.layout?.getLineForOffset(citation.start.coerceIn(0, page.text.length)) ?: 0
                            (body.parent as? android.widget.ScrollView)?.scrollTo(0,
                                text.top + (text.layout?.getLineTop(line) ?: 0))
                        }
                    }
                }
            }.onFailure { failure(it) }
        }
    }
    companion object {
        fun create(id: Long, citation: Citation? = null) = SourceFragment().apply { arguments = Bundle().apply {
            putLong("id", id); if (citation != null) putString("citation", LibraryContent.gson.toJson(citation))
        } }
    }
}
