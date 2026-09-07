package com.example.localai.feature.market

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.common.ModelDisplay
import com.example.localai.common.widget.EmptyStateView
import com.example.localai.core.compatibility.CompatibilityEngine
import com.example.localai.core.device.DeviceProfiler
import com.example.localai.core.inference.ApprovedModels
import com.example.localai.feature.settings.InferencePolicy
import com.example.localai.data.ServiceLocator
import com.example.localai.feature.download.DownloadRepository
import com.example.localai.mock.Filters
import com.example.localai.model.ModelInfo
import com.google.android.material.chip.ChipGroup
import java.util.ArrayList

/** 市场首页：搜索、三维筛选、精选横幅、模型列表；数据源为签名目录（真实），空/加载/错误态完整。 */
class MarketFragment : Fragment() {

    private lateinit var adapter: ModelAdapter
    private val repositoryListener = object : DownloadRepository.Listener {
        override fun onDownloadsChanged() {
        }

        override fun onCatalogChanged() {
            refreshFromRepository()
        }
    }

    private var query = ""
    private var taskFilter: String = Filters.TASK_ALL
    private var langFilter: String = Filters.LANG_ALL
    private var sizeFilter: String = Filters.SIZE_ALL

    private lateinit var emptyView: View
    private lateinit var progressView: View
    private lateinit var heroCard: View
    private lateinit var listView: RecyclerView
    private lateinit var countView: TextView
    private val all = ArrayList<ModelInfo>()
    private var repository: DownloadRepository? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_market, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repository = ServiceLocator.downloads()
        adapter = ModelAdapter { model ->
            if (activity is MainActivity) {
                (activity as MainActivity).push(ModelDetailFragment.newInstance(model.id))
            }
        }

        emptyView = view.findViewById(R.id.empty)
        progressView = view.findViewById(R.id.progress)
        heroCard = view.findViewById(R.id.card_hero)
        listView = view.findViewById(R.id.list)
        countView = view.findViewById(R.id.text_count)

        listView.layoutManager = LinearLayoutManager(requireContext())
        listView.adapter = adapter
        listView.addItemDecoration(com.google.android.material.divider.MaterialDividerItemDecoration(
            requireContext(), LinearLayoutManager.VERTICAL).apply {
            dividerInsetStart = (24 * resources.displayMetrics.density).toInt()
            dividerInsetEnd = dividerInsetStart
            dividerColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.outline)
            isLastItemDecorated = false
        })
        emptyView.visibility = View.GONE

        // 精选横幅：点击进入第一条目录模型（目录为空时隐藏）
        view.findViewById<View>(R.id.card_hero).setOnClickListener {
            if (activity is MainActivity) {
                val heroId = heroModelId()
                if (heroId != null) {
                    (activity as MainActivity).push(ModelDetailFragment.newInstance(heroId))
                }
            }
        }

        val advancedFilters = view.findViewById<View>(R.id.filters_advanced)
        view.findViewById<View>(R.id.btn_filters).setOnClickListener {
            advancedFilters.visibility = if (advancedFilters.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // 搜索
        val input = view.findViewById<EditText>(R.id.input_search)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                query = s.toString()
                applyFilter()
            }
        })

        // 任务筛选
        val chipTask = view.findViewById<ChipGroup>(R.id.chip_task)
        chipTask.setOnCheckedStateChangeListener { _, checkedIds ->
            taskFilter = if (checkedIds.isEmpty()) {
                Filters.TASK_ALL
            } else {
                when (checkedIds[0]) {
                    R.id.chip_task_text -> ModelInfo.TASK_TEXT
                    R.id.chip_task_code -> ModelInfo.TASK_CODE
                    else -> Filters.TASK_ALL
                }
            }
            applyFilter()
        }

        // 语言筛选
        val chipLang = view.findViewById<ChipGroup>(R.id.chip_lang)
        chipLang.setOnCheckedStateChangeListener { _, checkedIds ->
            langFilter = if (checkedIds.isEmpty()) {
                Filters.LANG_ALL
            } else {
                when (checkedIds[0]) {
                    R.id.chip_lang_zh -> "中文"
                    R.id.chip_lang_en -> "英文"
                    R.id.chip_lang_multi -> "多语言"
                    else -> Filters.LANG_ALL
                }
            }
            applyFilter()
        }

        // 规格筛选
        val chipSize = view.findViewById<ChipGroup>(R.id.chip_size)
        chipSize.setOnCheckedStateChangeListener { _, checkedIds ->
            sizeFilter = if (checkedIds.isEmpty()) {
                Filters.SIZE_ALL
            } else {
                when (checkedIds[0]) {
                    R.id.chip_size_le2 -> Filters.SIZE_LE2
                    R.id.chip_size_mid -> Filters.SIZE_MID
                    R.id.chip_size_gt8 -> Filters.SIZE_GT8
                    else -> Filters.SIZE_ALL
                }
            }
            applyFilter()
        }

        // 空态操作：清除筛选 / 目录错误时重试
        (emptyView as EmptyStateView).setOnActionClickListener {
            if (repository != null && repository!!.catalogView().isError()) {
                repository!!.refreshCatalog()
                return@setOnActionClickListener
            }
            input.setText("")
            chipTask.check(R.id.chip_task_all)
            chipLang.check(R.id.chip_lang_all)
            chipSize.check(R.id.chip_size_all)
        }

        repository?.let {
            it.register(repositoryListener)
            refreshFromRepository()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshFromRepository()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repository?.unregister(repositoryListener)
    }

    private fun heroModelId(): String? {
        val mode = requireContext().getSharedPreferences(InferencePolicy.PREFS, 0).getString(InferencePolicy.KEY_MODE, "auto")
        return com.example.localai.feature.settings.DeviceAdvice.recommend(all, mode,
            DeviceProfiler.collect(requireContext()).ramAvailMb)?.id
    }

    /** 从仓库目录视图刷新：LOADING/ERROR/READY 三态。 */
    private fun refreshFromRepository() {
        if (!::adapter.isInitialized || context == null || repository == null) {
            return
        }
        val catalogView = repository!!.catalogView()
        if (catalogView.isLoading()) {
            all.clear()
            listView.visibility = View.GONE
            emptyView.visibility = View.GONE
            heroCard.visibility = View.GONE
            progressView.visibility = View.VISIBLE
            return
        }
        progressView.visibility = View.GONE

        if (catalogView.isError()) {
            all.clear()
            listView.visibility = View.GONE
            heroCard.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            (emptyView as EmptyStateView).setMessages(
                "目录加载失败",
                catalogView.error ?: "请检查网络后重试",
                "重试")
            return
        }

        all.clear()
        all.addAll(MarketModels.map(catalogView, deviceSnapshot()) { item ->
            ApprovedModels.byId(item.modelId)?.let { InferencePolicy.current(requireContext(), it).contextLength.toLong() }
                ?: minOf(item.contextLength, 2048L)
        })
        heroCard.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        val recommended = all.firstOrNull { it.id == heroModelId() }
        if (recommended != null) {
            val hero = recommended
            val heroTitle = heroCard.findViewById<TextView>(R.id.hero_title)
            heroTitle.text = ModelDisplay.name(hero.name)
            val heroDesc = heroCard.findViewById<TextView>(R.id.hero_desc)
            heroDesc.text = hero.paramsLabel + " · " + hero.sizeLabel + " · 根据设备估算"
        }
        applyFilter()
    }

    private fun applyFilter() {
        if (!::adapter.isInitialized || context == null) {
            return
        }
        val filtered = query.isNotBlank() || taskFilter != Filters.TASK_ALL ||
                langFilter != Filters.LANG_ALL || sizeFilter != Filters.SIZE_ALL
        // 小屏和大字体优先展示可操作的模型列表，推荐条不占据首屏。
        val roomy = resources.configuration.screenHeightDp >= 640 && resources.configuration.fontScale <= 1.2f
        heroCard.visibility = if (heroModelId() != null && !filtered && roomy) View.VISIBLE else View.GONE
        view?.findViewById<View>(R.id.btn_filters)?.contentDescription = getString(
            if (langFilter != Filters.LANG_ALL || sizeFilter != Filters.SIZE_ALL)
                R.string.action_filters_active else R.string.action_filters)
        val result = Filters.apply(all, query, taskFilter, langFilter, sizeFilter)
        adapter.submit(result)
        countView.text = getString(R.string.market_count_fmt, result.size)

        val empty = result.isEmpty()
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
        listView.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty && all.isNotEmpty()) {
            (emptyView as EmptyStateView).setMessages(
                "没有匹配的模型",
                "清除筛选后查看全部 " + all.size + " 个模型",
                "清除筛选")
        }
    }

    private fun deviceSnapshot(): CompatibilityEngine.DeviceSnapshot {
        val p = DeviceProfiler.collect(requireContext())
        val abis = p.abis
        return CompatibilityEngine.DeviceSnapshot(
            p.sdkInt,
            if (abis == null || abis.isEmpty()) "" else abis[0],
            p.storageFreeMb * 1024 * 1024,
            p.ramTotalMb * 1024 * 1024)
    }
}
