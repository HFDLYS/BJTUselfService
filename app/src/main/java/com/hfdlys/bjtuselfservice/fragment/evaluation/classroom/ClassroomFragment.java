package com.hfdlys.bjtuselfservice.fragment.evaluation.classroom;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;

import android.graphics.drawable.ColorDrawable;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
        CardView sortCard = binding.sortCard;
        TextView sortBy = binding.sortBy;
        RecyclerView classroomRecyclerView = binding.classroomRecycler;
        progressBar.setVisibility(View.VISIBLE);
        String building = getArguments().getString("buildingName");
        List<ClassroomCapacityService.ClassroomCapacity> classroomList = new ArrayList<>();
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
                        ClassroomAdapter adapter = new ClassroomAdapter(data.ClassroomList, building);
                        classroomList.clear();
                        classroomList.addAll(data.ClassroomList);
                        progressBar.setVisibility(View.GONE);
                        classroomRecyclerView.setAdapter(adapter);
                    }
                });
            });
        }

        sortCard.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(v.getContext());
            builder.setTitle("选择排序方式");
            builder.setItems(new String[]{"按教室名称排序", "按教室占比排序", "按教室总人数排序"}, (dialog, which) -> {
                switch (which) {
                    case 0:
                        classroomList.sort(Comparator.comparing(o -> o.RoomName));
                        sortBy.setText("教室名");
                        break;
                    case 1:
                        classroomList.sort((o1, o2) -> o1.Used * o2.Capacity - o2.Used * o1.Capacity);
                        sortBy.setText("占比");
                        break;
                    case 2:
                        classroomList.sort((o1, o2) -> {
                            if (o1.Used == o2.Used) return o1.Capacity - o2.Capacity;
                            return o1.Used - o2.Used;
                        });
                        sortBy.setText("总人数");
                        break;
                }
                ClassroomAdapter adapter = new ClassroomAdapter(classroomList, building);
                classroomRecyclerView.setAdapter(adapter);
            });
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(e -> {
                Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(R.drawable.dialog_background);
            });
            dialog.show();
        });
    }

}