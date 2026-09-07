package com.example.localai.feature.settings

import android.os.Bundle
import android.view.*
import com.example.localai.MainActivity
import com.example.localai.R
import com.example.localai.core.compatibility.CompatibilityEngine
import com.example.localai.core.device.DeviceProfiler
import com.example.localai.data.ServiceLocator
import com.example.localai.feature.download.DownloadRepository
import com.example.localai.feature.library.LibraryUi
import com.example.localai.feature.market.MarketModels
import com.example.localai.feature.market.ModelDetailFragment

class DeviceAdviceFragment : LibraryUi(), DownloadRepository.Listener {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View = page("适合这台设备")
    override fun onResume() { super.onResume(); ServiceLocator.downloads()?.register(this); render() }
    override fun onPause() { ServiceLocator.downloads()?.unregister(this); super.onPause() }
    override fun onDownloadsChanged() { if (view != null) render() }
    override fun onCatalogChanged() { if (view != null) render() }
    private fun render() {
        body.removeAllViews()
        val p = DeviceProfiler.collect(requireContext())
        body.addView(label("${p.ramLabel()} 内存 · ${p.storageLabel()} 剩余空间", 18f))
        val pressure = DeviceAdvice.pressure(requireContext())
        if (pressure.isNotEmpty()) body.addView(label(pressure))
        body.addView(label("希望怎样使用", 18f))
        val prefs = requireContext().getSharedPreferences(InferencePolicy.PREFS, 0)
        val mode = prefs.getString(InferencePolicy.KEY_MODE, "auto")
        listOf("auto" to "响应优先", "balanced" to "质量优先", "saver" to "省电").forEach { (key, title) ->
            body.addView(button((if (mode == key) "✓ " else "") + title) {
                prefs.edit().putString(InferencePolicy.KEY_MODE, key).apply(); render()
            })
        }
        val device = CompatibilityEngine.DeviceSnapshot(p.sdkInt, p.abis?.firstOrNull().orEmpty(), p.storageFreeMb * 1024 * 1024, p.ramTotalMb * 1024 * 1024)
        val models = MarketModels.map(ServiceLocator.downloads()?.catalogView(), device)
        val recommendation = DeviceAdvice.recommend(models, mode, p.ramAvailMb)
        body.addView(divider()); body.addView(label("设备估算", 18f))
        if (recommendation != null) {
            body.addView(label(com.example.localai.common.ModelDisplay.name(recommendation.name), 20f))
            body.addView(label("${recommendation.quant} · ${recommendation.sizeLabel}\n估算运行内存 ${recommendation.estimatedPeakMb} MB。" +
                if (mode == "balanced") "较大模型通常更适合整理复杂资料，速度可能较慢。" else "优先选择较小模型，减少等待和内存占用。", secondary = true))
            val installed = com.example.localai.core.inference.ApprovedModels.isInstalled(requireContext(), recommendation.id)
            body.addView(button(if (installed) "使用这个模型" else "查看推荐模型") {
                if (installed) (activity as? MainActivity)?.openChatWithModel(recommendation.id)
                else (activity as? MainActivity)?.push(ModelDetailFragment.newInstance(recommendation.id))
            })
        } else body.addView(label("当前没有满足内存余量的推荐模型。可以先关闭其他应用，或查看模型列表中的具体要求。", secondary = true))
        body.addView(label("推荐根据系统内存、剩余空间和模型大小估算，不是速度测试。切换使用偏好不会自动下载或替换当前模型。", secondary = true))
        body.addView(button("查看全部模型") { (activity as? MainActivity)?.openTab(R.id.nav_market) })
        body.addView(divider()); body.addView(label("最近一次本机实测", 18f)); body.addView(label(DeviceAdvice.measurement(requireContext()), secondary = true))
    }
}
