package com.hfdlys.bjtuselfservice.fragment.evaluation.classroom;

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
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.databinding.FragmentClassroomBinding;
import com.hfdlys.bjtuselfservice.utils.Network;
import com.hfdlys.bjtuselfservice.web.ClassroomCapacityService;

public class ClassroomFragment extends Fragment {

    private ClassroomViewModel mViewModel;
    private FragmentClassroomBinding binding;

    public static ClassroomFragment newInstance() {
        return new ClassroomFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentClassroomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ClassroomViewModel.class);
        TextView buildingName = binding.title;
        TextView beginTime = binding.beginTime;
        TextView endTime = binding.endTime;
        ProgressBar progressBar = binding.loading;
        RecyclerView classroomRecyclerView = binding.classroomRecycler;
        progressBar.setVisibility(View.VISIBLE);
        String building = getArguments().getString("buildingName");
        classroomRecyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        if (building != null) {
            buildingName.setText(building);
            ClassroomCapacityService.getClassroomCapacity(building).thenAccept(data -> {
                getActivity().runOnUiThread(() -> {
                    if (data == null) {
                        Snackbar.make(view, "教室信息加载失败", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        progressBar.setVisibility(View.GONE);
                        return;
                    } else if (data.ClassroomList.size() == 0) {
                        Snackbar.make(view, "没有教室信息", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        progressBar.setVisibility(View.GONE);
                        return;
                    } else {
                        beginTime.setText("From: " + data.EffectiveDateStart);
                        endTime.setText("To: " + data.EffectiveDateEnd);
                        ClassroomAdapter adapter = new ClassroomAdapter(data.ClassroomList);
                        progressBar.setVisibility(View.GONE);
                        classroomRecyclerView.setAdapter(adapter);
                    }
                });
            });
        }
    }

}