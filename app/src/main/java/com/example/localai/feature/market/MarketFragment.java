package com.example.localai.feature.market;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.MainActivity;
import com.example.localai.R;
import com.example.localai.common.widget.EmptyStateView;
import com.example.localai.core.compatibility.CompatibilityEngine;
import com.example.localai.core.device.DeviceProfiler;
import com.example.localai.data.ServiceLocator;
import com.example.localai.feature.download.DownloadRepository;
import com.example.localai.mock.Filters;
import com.example.localai.model.ModelInfo;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** 市场首页：搜索、三维筛选、精选横幅、模型列表；数据源为签名目录（真实），空/加载/错误态完整。 */
public class MarketFragment extends Fragment {

    private ModelAdapter adapter;
    private final DownloadRepository.Listener repositoryListener = new DownloadRepository.Listener() {
        @Override
        public void onDownloadsChanged() {
        }

        @Override
        public void onCatalogChanged() {
            refreshFromRepository();
        }
    };

    private String query = "";
    private String taskFilter = Filters.TASK_ALL;
    private String langFilter = Filters.LANG_ALL;
    private String sizeFilter = Filters.SIZE_ALL;

    private View emptyView;
    private View progressView;
    private View heroCard;
    private RecyclerView listView;
    private android.widget.TextView countView;
    private final List<ModelInfo> all = new ArrayList<>();
    private DownloadRepository repository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_market, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        repository = ServiceLocator.downloads();
        adapter = new ModelAdapter(model -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity())
                        .push(ModelDetailFragment.newInstance(model.id));
            }
        });

        emptyView = view.findViewById(R.id.empty);
        progressView = view.findViewById(R.id.progress);
        heroCard = view.findViewById(R.id.card_hero);
        listView = view.findViewById(R.id.list);
        countView = view.findViewById(R.id.text_count);

        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
        listView.setAdapter(adapter);
        emptyView.setVisibility(View.GONE);

        // 精选横幅：点击进入第一条目录模型（目录为空时隐藏）
        view.findViewById(R.id.card_hero).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                String heroId = heroModelId();
                if (heroId != null) {
                    ((MainActivity) getActivity()).push(ModelDetailFragment.newInstance(heroId));
                }
            }
        });

        // 搜索
        android.widget.EditText input = view.findViewById(R.id.input_search);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                applyFilter();
            }
        });

        // 任务筛选
        ChipGroup chipTask = view.findViewById(R.id.chip_task);
        chipTask.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                taskFilter = Filters.TASK_ALL;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chip_task_text) {
                    taskFilter = ModelInfo.TASK_TEXT;
                } else if (id == R.id.chip_task_code) {
                    taskFilter = ModelInfo.TASK_CODE;
                } else {
                    taskFilter = Filters.TASK_ALL;
                }
            }
            applyFilter();
        });

        // 语言筛选
        ChipGroup chipLang = view.findViewById(R.id.chip_lang);
        chipLang.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                langFilter = Filters.LANG_ALL;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chip_lang_zh) {
                    langFilter = "中文";
                } else if (id == R.id.chip_lang_en) {
                    langFilter = "英文";
                } else if (id == R.id.chip_lang_multi) {
                    langFilter = "多语言";
                } else {
                    langFilter = Filters.LANG_ALL;
                }
            }
            applyFilter();
        });

        // 规格筛选
        ChipGroup chipSize = view.findViewById(R.id.chip_size);
        chipSize.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                sizeFilter = Filters.SIZE_ALL;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chip_size_le2) {
                    sizeFilter = Filters.SIZE_LE2;
                } else if (id == R.id.chip_size_mid) {
                    sizeFilter = Filters.SIZE_MID;
                } else if (id == R.id.chip_size_gt8) {
                    sizeFilter = Filters.SIZE_GT8;
                } else {
                    sizeFilter = Filters.SIZE_ALL;
                }
            }
            applyFilter();
        });

        // 空态操作：清除筛选 / 目录错误时重试
        ((EmptyStateView) emptyView).setOnActionClickListener(() -> {
            if (repository != null && repository.catalogView().isError()) {
                repository.refreshCatalog();
                return;
            }
            input.setText("");
            chipTask.check(R.id.chip_task_all);
            chipLang.check(R.id.chip_lang_all);
            chipSize.check(R.id.chip_size_all);
        });

        if (repository != null) {
            repository.register(repositoryListener);
            refreshFromRepository();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFromRepository();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (repository != null) {
            repository.unregister(repositoryListener);
        }
    }

    private String heroModelId() {
        for (ModelInfo m : all) {
            return m.id;
        }
        return null;
    }

    /** 从仓库目录视图刷新：LOADING/ERROR/READY 三态。 */
    private void refreshFromRepository() {
        if (adapter == null || getContext() == null || repository == null) {
            return;
        }
        DownloadRepository.CatalogView view = repository.catalogView();
        if (view.isLoading()) {
            all.clear();
            listView.setVisibility(View.GONE);
            emptyView.setVisibility(View.GONE);
            heroCard.setVisibility(View.GONE);
            progressView.setVisibility(View.VISIBLE);
            return;
        }
        progressView.setVisibility(View.GONE);

        if (view.isError()) {
            all.clear();
            listView.setVisibility(View.GONE);
            heroCard.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            ((EmptyStateView) emptyView).setMessages(
                    "目录加载失败",
                    view.error == null ? "请检查网络后重试" : view.error,
                    "重试");
            return;
        }

        all.clear();
        all.addAll(MarketModels.map(view, deviceSnapshot()));
        heroCard.setVisibility(all.isEmpty() ? View.GONE : View.VISIBLE);
        if (!all.isEmpty()) {
            ModelInfo hero = all.get(0);
            TextView heroTitle = heroCard.findViewById(R.id.hero_title);
            heroTitle.setText(hero.name);
            TextView heroDesc = heroCard.findViewById(R.id.hero_desc);
            heroDesc.setText(hero.paramsLabel + " 参数 · 上下文 " + hero.contextLabel
                    + " · " + ModelAdapter.compatLabel(hero.compat));
        }
        applyFilter();
    }

    private void applyFilter() {
        if (adapter == null || getContext() == null) {
            return;
        }
        List<ModelInfo> result = Filters.apply(all, query, taskFilter, langFilter, sizeFilter);
        adapter.submit(result);
        countView.setText(getString(R.string.market_count_fmt, result.size()));

        boolean empty = result.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty && !all.isEmpty()) {
            ((EmptyStateView) emptyView).setMessages(
                    "没有匹配的模型",
                    "清除筛选后查看全部 " + all.size() + " 个模型",
                    "清除筛选");
        }
    }

    private CompatibilityEngine.DeviceSnapshot deviceSnapshot() {
        DeviceProfiler.Profile p = DeviceProfiler.collect(requireContext());
        return new CompatibilityEngine.DeviceSnapshot(
                p.sdkInt,
                p.abis.isEmpty() ? "" : p.abis.get(0),
                p.storageFreeMb * 1024 * 1024,
                p.ramTotalMb * 1024 * 1024);
    }
}
