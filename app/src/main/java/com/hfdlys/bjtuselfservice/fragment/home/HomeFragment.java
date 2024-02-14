package com.hfdlys.bjtuselfservice.fragment.home;

import android.app.AlertDialog;
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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.snackbar.Snackbar;
import com.hfdlys.bjtuselfservice.R;
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

        cardMail.setOnClickListener(v -> showDialog("新邮件", "要看看新邮件吗",
                (dialog, which) -> {
            NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.action_homeFragment_to_mailFragment);
        }));
        cardEcard.setOnClickListener(v -> showDialog("校园卡充值", "请注意，接下来即将转跳“完美校园”app\n确保自己已安装哦☺️",
                (dialog, which) -> {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.newcapec.mobile.ncp", "com.wanxiao.basebusiness.activity.SplashActivity"));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Snackbar.make(binding.getRoot(), "未找到“完美校园”app", Snackbar.LENGTH_LONG).show();
            }
        }));
        cardNet.setOnClickListener(v -> showDialog("校园网续费", "不好意思直接转跳微信成本还是太高，不过\n注意：以下操作需微信绑定学校企业号\n请分享至微信，后打开（莫吐槽🙏）哦",
                (dialog, which) -> {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "https://weixin.bjtu.edu.cn/pay/wap/network/recharge.html");
            Intent chooser = Intent.createChooser(shareIntent, "请选择：“微信：发送给朋友”");
            try {
                startActivity(chooser);
            } catch (Exception e) {
                Snackbar.make(binding.getRoot(), "未找到“微信”app？？？？", Snackbar.LENGTH_LONG).show();
            }
        }));

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
    private void showDialog(String title, String message, DialogInterface.OnClickListener positiveListener) {
        new AlertDialog.Builder(getContext(), R.style.AlertDialogStyle)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, positiveListener)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }
}