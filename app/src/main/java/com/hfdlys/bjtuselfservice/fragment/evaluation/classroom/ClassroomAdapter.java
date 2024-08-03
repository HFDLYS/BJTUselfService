package com.hfdlys.bjtuselfservice.fragment.evaluation.classroom;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.constant.ApiConstant;
import com.hfdlys.bjtuselfservice.web.ClassroomCapacityService.ClassroomCapacity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class ClassroomAdapter extends RecyclerView.Adapter<ClassroomAdapter.ViewHolder> {
    private List<ClassroomCapacity> classroomList;
    private String buildingName;

    public ClassroomAdapter(List<ClassroomCapacity> classroomList, String buildingName) {
        this.classroomList = classroomList;
        this.buildingName = buildingName;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView classroomName;
        private TextView classroomOccupied;
        private ProgressBar percentageBar;
        private TextView errorText;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            classroomName = itemView.findViewById(R.id.classroom_name);
            classroomOccupied = itemView.findViewById(R.id.classroom_occupied);
            percentageBar = itemView.findViewById(R.id.occupy_percent);
            errorText = itemView.findViewById(R.id.text_view_placeholder);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.classroom_item_card, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onBindViewHolder(@NonNull ClassroomAdapter.ViewHolder holder, int position) {
        ClassroomCapacity classroom = classroomList.get(position);
        String classroomName = classroom.RoomName;
        int occupied = classroom.Used;
        int total = classroom.Capacity;
        holder.itemView.setOnClickListener(v -> {
            Dialog dialog = new Dialog(v.getContext());
            dialog.setContentView(R.layout.dialog_classroom);
            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));


            WebView webView = dialog.findViewById(R.id.classroomWebView);
            WebSettings settings = webView.getSettings();
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setJavaScriptEnabled(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }
            });
            String postData = "buildi=" + buildingName + "&classrooms=" + classroomName;
            byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
            webView.postUrl(ApiConstant.CLASSROOM_VIEW_URL, postDataBytes);
            dialog.show();
        });
        int percentage = (int) ((float) occupied / total * 10000);
        holder.classroomName.setText(classroomName);
        holder.classroomOccupied.setText(String.format("%d/%d", occupied, total));
        holder.percentageBar.setProgress(percentage);
        if (occupied == total) {
            holder.percentageBar.setVisibility(View.GONE);
            holder.errorText.setVisibility(View.VISIBLE);
            holder.errorText.setText("无法获取该教室信息");
        } else {
            holder.percentageBar.setVisibility(View.VISIBLE);
            holder.errorText.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return classroomList.size();
    }
}
