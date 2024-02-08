package com.hfdlys.bjtuselfservice;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;


import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.hfdlys.bjtuselfservice.databinding.ActivityMainBinding;
import com.hfdlys.bjtuselfservice.fragment.login.ui.login.LoginFragment;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Model.init(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        SharedPreferences Pref = getSharedPreferences("StuAccount", Context.MODE_PRIVATE);

        String StuId = Pref.getString("StuId", null);
        String StuPwd = Pref.getString("StuPwd", null);
        StudentAccountManager Instance = StudentAccountManager.getInstance();
        if (StuId != null && StuPwd != null) {
            Dialog loadingDialog = new Dialog(this);
            loadingDialog.setContentView(R.layout.dialog_loading);
            loadingDialog.setCancelable(false);
            loadingDialog.show();
            Instance.init(StuId, StuPwd).thenAccept( isLogin -> {
                if (isLogin) {
                    Snackbar.make(binding.appBarMain.fab, "欢迎回来," + Instance.getStuName() + "同学！"
                                , Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                    loadingDialog.dismiss();
                } else {
                    Snackbar.make(binding.appBarMain.fab, "预料之外的，登录失败？你再试试"
                                    , Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                    LoginFragment loginFragment = new LoginFragment();
                    loginFragment.show(getSupportFragmentManager(), "login");
                    loadingDialog.dismiss();
                }
            });
        } else {
            LoginFragment loginFragment = new LoginFragment();
            loginFragment.show(getSupportFragmentManager(), "login");
        }

        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Snackbar.make(view, "💤💤💤"
                                , Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        DrawerLayout drawer = binding.drawerLayout;


        NavigationView navigationView = binding.navView;
        TextView username = binding.navView.getHeaderView(0).findViewById(R.id.header_text_name);
        TextView stuId = binding.navView.getHeaderView(0).findViewById(R.id.header_text_mail);
        Instance.getStudentInfo().observe(this, studentInfo -> {
            if (studentInfo != null) {
                username.setText(studentInfo.stuName);
                stuId.setText(studentInfo.stuId + "@bjtu.edu.cn");
            }
        });
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_grade, R.id.nav_exam, R.id.nav_course, R.id.nav_building)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.action_settings).setOnMenuItemClickListener(item -> {
            LoginFragment loginFragment = new LoginFragment();
            loginFragment.show(getSupportFragmentManager(), "login");
            return true;
        });
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}