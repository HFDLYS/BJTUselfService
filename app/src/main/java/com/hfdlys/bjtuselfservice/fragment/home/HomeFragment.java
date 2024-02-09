package com.hfdlys.bjtuselfservice.fragment.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.hfdlys.bjtuselfservice.StudentAccountManager;
import com.hfdlys.bjtuselfservice.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        StudentAccountManager Instance = StudentAccountManager.getInstance();
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        final TextView textView = binding.textHome;
        final MaterialCardView CardView = binding.cardView;
        final ProgressBar loadingStatus = binding.loadingStatus;
        final TextView textMail = binding.textMail;
        final TextView textEcard = binding.textEcard;
        final TextView textNet = binding.textNet;
        homeViewModel.getStuInfo().observe(getViewLifecycleOwner(), studentInfo -> {
            String Introduce = "你好";
            textView.setText(Introduce);
        });

        homeViewModel.getStatus().observe(getViewLifecycleOwner(), status -> {
                String EcardBalance = "校园卡余额：" + status.EcardBalance;
                String NetBalance = "校园网余额：" + status.NetBalance;
                String NewMailCount = "新邮件：" + status.NewMailCount;
                if (status.EcardBalance < 20) {
                    EcardBalance += "，会不会不够用了";
                }
                if (!status.NewMailCount.equals("0")) {
                    NewMailCount += "，记得去看哦";
                }
                if (status.NetBalance.equals("0")) {
                    NetBalance += "，😱下个月要没网了";
                }
                textMail.setText(NewMailCount);
                textEcard.setText(EcardBalance);
                textNet.setText(NetBalance);
                loadingStatus.setVisibility(View.GONE);
        });

        homeViewModel.getIsLogin().observe(getViewLifecycleOwner(), aBoolean -> {
            if (aBoolean) {
                Instance.getStatus().thenAccept(status -> {
                    homeViewModel.setStatus(status);
                });
            } else {
            }
        });

    }
}