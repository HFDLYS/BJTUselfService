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
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.StudentAccountManager;
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
        final TextView gradeInfo = binding.gradeInfo;
        final TextView greetInfo = binding.greetInfo;
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        gradeViewModel.getGradeList().observe(getViewLifecycleOwner(), grades -> {
            if (grades == null) {
                Snackbar.make(view, "成绩加载失败", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                progressBar.setVisibility(View.GONE);
                return;
            } else if (grades.size() == 0) {
                Snackbar.make(view, "你好像还没有成绩😮", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                progressBar.setVisibility(View.GONE);
                return;
            }
            Snackbar.make(view, "成绩加载完成", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            double allCredit = 0;
            double allScore = 0;
            for (StudentAccountManager.Grade grade : grades) {
                try {
                    allCredit += Double.parseDouble(grade.courseCredits);
                    allScore += Double.parseDouble(grade.courseScore.split(",")[1]) * Double.parseDouble(grade.courseCredits);
                } catch (Exception ignored) {
                }
            }
            if (allCredit == 0) {
                gradeInfo.setText("成绩好像都没出来哦~\n");
                progressBar.setVisibility(View.GONE);
                recyclerView.setAdapter(new GradeAdapter(grades));
                return;
            }
            double gpa = allScore / allCredit;
            String info = "您的加权平均分是" + String.format("%.1f", gpa) + "\n";
            gradeInfo.setText(info);
            String greeting;
            if (gpa >= 90) {
                greeting = "😮这位学霸太猛了";
            } else if (gpa >= 80) {
                greeting = "🥹鼓足干劲，力争上游，多快好省地，加油吧！！！";
            } else if (gpa >= 70) {
                greeting = "🫡不错哦，继续努力";
            } else if (gpa >= 60) {
                greeting = "☺️得加把劲了，但或许已经够了？";
            } else {
                greeting = "😱😱😱同学你真得加油了啊";
            }
            greetInfo.setVisibility(View.VISIBLE);
            greetInfo.setText(greeting);
            progressBar.setVisibility(View.GONE);
            recyclerView.setAdapter(new GradeAdapter(grades));
        });
        if (Boolean.TRUE.equals(gradeViewModel.getIsAaLogin().getValue())) {
            gradeViewModel.loadGradeList();
        } else {
            gradeViewModel.getIsAaLogin().observe(getViewLifecycleOwner(), isLogin -> {
                if (isLogin) {
                    gradeViewModel.loadGradeList();
                }
            });
        }
    }
}