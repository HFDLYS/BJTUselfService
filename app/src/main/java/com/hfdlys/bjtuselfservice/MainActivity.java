package com.hfdlys.bjtuselfservice;

import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;


import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;

import com.hfdlys.bjtuselfservice.databinding.ActivityMainBinding;
import com.hfdlys.bjtuselfservice.fragment.login.ui.login.LoginFragment;
import com.hfdlys.bjtuselfservice.Model;
import com.hfdlys.bjtuselfservice.web.NetworkDataManager;
import com.hfdlys.bjtuselfservice.utils.Utils.InMemoryCookieJar;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import okhttp3.OkHttpClient;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Model.init(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        LoginFragment loginFragment = new LoginFragment();
        loginFragment.show(getSupportFragmentManager(), "login");
        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Snackbar.make(view, "Replace with your own action"
                                , Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
        DrawerLayout drawer = binding.drawerLayout;


        NavigationView navigationView = binding.navView;
        TextView username = binding.navView.getHeaderView(0).findViewById(R.id.header_text_name);
        TextView stuId = binding.navView.getHeaderView(0).findViewById(R.id.header_text_mail);
        StudentAccountManager.getInstance().getStudentInfo().observe(this, studentInfo -> {
            if (studentInfo != null) {
                username.setText(studentInfo.stuName);
                stuId.setText(studentInfo.stuId + "@bjtu.edu.cn");
            }

        });
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_grade, R.id.nav_exam, R.id.nav_course)
                .setOpenableLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}