package com.hfdlys.bjtuselfservice.fragment.grade;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import com.google.android.material.card.MaterialCardView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.ProgressBar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.snackbar.Snackbar;
import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.databinding.FragmentGradeBinding;

public class GradeFragment extends Fragment {

    private GradeViewModel gradeViewModel;
    private FragmentGradeBinding binding;
    public static GradeFragment newInstance() {
        return new GradeFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGradeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        gradeViewModel = new ViewModelProvider(this).get(GradeViewModel.class);

        final RecyclerView recyclerView = binding.gradeRecycler;
        final ProgressBar progressBar = binding.loading;
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        gradeViewModel.getGradeList().observe(getViewLifecycleOwner(), grades -> {
            if (grades == null) {
                Snackbar.make(view, "成绩加载失败", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                progressBar.setVisibility(View.GONE);
                return;
            } else if (grades.size() == 0) {
                Snackbar.make(view, "你好像还没有成绩", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                progressBar.setVisibility(View.GONE);
                return;
            }
            Snackbar.make(view, "成绩加载完成", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            progressBar.setVisibility(View.GONE);
            recyclerView.setAdapter(new GradeAdapter(grades));
        });
    }
}