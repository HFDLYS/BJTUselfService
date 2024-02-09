package com.hfdlys.bjtuselfservice.fragment.evaluation.classroom;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.web.ClassroomCapacityService.ClassroomCapacity;

import java.util.List;

public class ClassroomAdapter extends RecyclerView.Adapter<ClassroomAdapter.ViewHolder> {
    private List<ClassroomCapacity> classroomList;

    public ClassroomAdapter(List<ClassroomCapacity> classroomList) {
        this.classroomList = classroomList;
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

    @Override
    public void onBindViewHolder(@NonNull ClassroomAdapter.ViewHolder holder, int position) {
        ClassroomCapacity classroom = classroomList.get(position);
        String classroomName = classroom.RoomName;
        int occupied = classroom.Used;
        int total = classroom.Capacity;
        int percentage = (int) ((float) occupied / total * 10000);
        holder.classroomName.setText(classroomName);
        holder.classroomOccupied.setText(String.format("%d/%d", occupied, total));
        holder.percentageBar.setProgress(percentage);
        if (occupied == total) {
            holder.percentageBar.setVisibility(View.GONE);
            holder.errorText.setVisibility(View.VISIBLE);
            holder.errorText.setText("无法获取该教室信息");
        }
    }

    @Override
    public int getItemCount() {
        return classroomList.size();
    }
}
