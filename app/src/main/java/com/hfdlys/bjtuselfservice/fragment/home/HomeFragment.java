package com.hfdlys.bjtuselfservice.fragment.home;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;
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
        final ProgressBar loadingStatus = binding.loadingStatus;
        final TextView textMail = binding.textMail;
        final TextView textEcard = binding.textEcard;
        final TextView textNet = binding.textNet;

        final CardView cardMail = binding.cardMail;
        final CardView cardEcard = binding.cardEcard;
        final CardView cardNet = binding.cardNet;

        homeViewModel.getStuInfo().observe(getViewLifecycleOwner(), studentInfo -> {
            String Introduce = studentInfo.stuName + "同学\n" +
                    "\t您好，\n" +
                    "\t欢迎使用交大自由行，这里是你的微型个人信息中心，你可以在这里查看你的成绩、考试安排、校园卡余额、校园网余额、新邮件等信息。\n" +
                    "你可以在左侧的菜单中选择你想要查看的信息。\n" +
                    "\t虽然很简陋，但是...!" +
                    "\t祝你使用愉快!";
            textView.setText(Introduce);
        });

        cardMail.setOnClickListener(v -> {

                });
        cardEcard.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("即将转跳至“完美校园”app......");
            builder.setMessage("确定转跳吗，同时确保你已经下载“完美校园”app。");
            builder.setPositiveButton("是是是是，我马上照搬", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName("com.newcapec.mobile.ncp", "com.wanxiao.basebusiness.activity.SplashActivity"));
                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Snackbar.make(binding.getRoot(), "未找到“完美校园”app", Snackbar.LENGTH_LONG).show();
                    }
                }});
            AlertDialog dialog = builder.create();
            dialog.show();
                });
        cardNet.setOnClickListener(v -> {

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
                Instance.getStatus().thenAccept(homeViewModel::setStatus);
            }
        });

    }
}