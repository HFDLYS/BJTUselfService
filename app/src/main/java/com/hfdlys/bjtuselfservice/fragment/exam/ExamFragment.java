package com.hfdlys.bjtuselfservice.fragment.exam;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.Snackbar;
import com.hfdlys.bjtuselfservice.databinding.FragmentExamBinding;

public class ExamFragment extends Fragment {

    private ExamViewModel examViewModel;
    private FragmentExamBinding binding;
    public static ExamFragment newInstance() {
        return new ExamFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentExamBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        examViewModel = new ViewModelProvider(this).get(ExamViewModel.class);

        final RecyclerView recyclerView = binding.examRecycler;
        final ProgressBar progressBar = binding.loading;
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        examViewModel.getExamList().observe(getViewLifecycleOwner(), examSchedules -> {
            if (examSchedules == null) {
                Snackbar.make(view, "考试安排加载失败", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                progressBar.setVisibility(View.GONE);
            } else if (examSchedules.size() == 0) {
                Snackbar.make(view, "你好像还没有考试安排😮", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                progressBar.setVisibility(View.GONE);
            } else {
                Snackbar.make(view, "考试安排加载完成", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                recyclerView.setAdapter(new ExamAdapter(examSchedules));
                progressBar.setVisibility(View.GONE);
            }
        });
        examViewModel.loadExamList();
    }

}