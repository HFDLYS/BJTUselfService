package com.hfdlys.bjtuselfservice.fragment.mail;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.StudentAccountManager;
import com.hfdlys.bjtuselfservice.databinding.FragmentMailBinding;

import com.hfdlys.bjtuselfservice.utils.WebViewUtil;

public class MailFragment extends Fragment {

    private FragmentMailBinding binding;

    public static com.hfdlys.bjtuselfservice.fragment.course.CourseFragment newInstance() {
        return new com.hfdlys.bjtuselfservice.fragment.course.CourseFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentMailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        WebView webView = binding.webView;
        WebSettings settings = webView.getSettings();
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });
        StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
        WebViewUtil.syncCookiesFromOkHttpToWebView("https://mis.bjtu.edu.cn/", studentAccountManager.getClient());
        webView.loadUrl("https://mis.bjtu.edu.cn/module/module/26/");
        super.onViewCreated(view, savedInstanceState);
    }

}