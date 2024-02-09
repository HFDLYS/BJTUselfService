package com.hfdlys.bjtuselfservice.fragment.evaluation.building;

import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.google.android.material.snackbar.Snackbar;
import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.databinding.FragmentBuildingBinding;
import com.hfdlys.bjtuselfservice.fragment.evaluation.classroom.ClassroomFragment;
import com.hfdlys.bjtuselfservice.fragment.evaluation.classroom.ClassroomViewModel;

import java.util.Arrays;
import java.util.List;

public class BuildingFragment extends Fragment {

    private BuildingViewModel mViewModel;
    private FragmentBuildingBinding binding;

    public static BuildingFragment newInstance() {
        return new BuildingFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBuildingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(BuildingViewModel.class);
        RecyclerView buildingRecyclerView = binding.buildingRecycler;
        buildingRecyclerView.setLayoutManager(new LinearLayoutManager(this.getContext()));
        List<String> buildingList = Arrays.asList(
                "第十七号教学楼",
                "思源楼",
                "思源西楼",
                "思源东楼",
                "第九教学楼",
                "第八教学楼",
                "第五教学楼",
                "逸夫教学楼",
                "机械楼",
                "东区二教",
                "东区一教"
        );
        buildingRecyclerView.setAdapter(new BuildingAdapter(buildingList, buildingName -> {
            ViewModelProvider viewModelProvider = new ViewModelProvider(requireActivity());
            Bundle bundle = new Bundle();
            bundle.putString("buildingName", buildingName);
            Snackbar.make(view, "正在前往:" + buildingName, Snackbar.LENGTH_SHORT).show();
            NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.action_buildingFragment_to_classroomFragment, bundle);
        }));
    }

}