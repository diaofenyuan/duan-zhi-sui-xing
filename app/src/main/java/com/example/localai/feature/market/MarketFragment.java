package com.example.localai.feature.market;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.MainActivity;
import com.example.localai.R;
import com.example.localai.mock.Filters;
import com.example.localai.mock.MockStore;
import com.example.localai.model.ModelInfo;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

/** 市场首页：搜索、三维筛选、精选横幅、模型列表；全部基于模拟数据。 */
public class MarketFragment extends Fragment {

    private ModelAdapter adapter;
    private final Handler handler = new Handler();

    private String query = "";
    private String taskFilter = Filters.TASK_ALL;
    private String langFilter = Filters.LANG_ALL;
    private String sizeFilter = Filters.SIZE_ALL;
    private boolean firstLoad = true;

    private View emptyView;
    private View progressView;
    private RecyclerView listView;
    private android.widget.TextView countView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_market, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        adapter = new ModelAdapter(model -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity())
                        .push(ModelDetailFragment.newInstance(model.id));
            }
        });

        emptyView = view.findViewById(R.id.empty);
        progressView = view.findViewById(R.id.progress);
        listView = view.findViewById(R.id.list);
        countView = view.findViewById(R.id.text_count);

        listView.setLayoutManager(new LinearLayoutManager(requireContext()));
        listView.setAdapter(adapter);
        emptyView.setVisibility(View.GONE);

        // 精选横幅
        view.findViewById(R.id.card_hero).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).push(ModelDetailFragment.newInstance("qwen3-4b"));
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

        // 空态操作：清除筛选
        ((com.example.localai.common.widget.EmptyStateView) emptyView)
                .setOnActionClickListener(() -> {
                    input.setText("");
                    chipTask.check(R.id.chip_task_all);
                    chipLang.check(R.id.chip_lang_all);
                    chipSize.check(R.id.chip_size_all);
                });

        // 首次进入展示加载态（模拟）
        if (firstLoad) {
            firstLoad = false;
            handler.postDelayed(() -> {
                progressView.setVisibility(View.GONE);
                applyFilter();
            }, 500);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyFilter();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private void applyFilter() {
        if (adapter == null || getContext() == null) {
            return;
        }
        List<ModelInfo> result = Filters.apply(
                MockStore.MODELS, query, taskFilter, langFilter, sizeFilter);
        adapter.submit(result);
        countView.setText(getString(R.string.market_count_fmt, MockStore.MODELS.size()));

        boolean empty = result.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        listView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
