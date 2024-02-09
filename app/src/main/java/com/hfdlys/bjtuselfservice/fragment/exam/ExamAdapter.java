package com.hfdlys.bjtuselfservice.fragment.exam;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class ExamAdapter extends RecyclerView.Adapter<ExamAdapter.ExamViewHolder> {
    private List<StudentAccountManager.ExamSchedule> dataList;

    public ExamAdapter(List<StudentAccountManager.ExamSchedule> dataList) {
        this.dataList = dataList;
    }

    @NonNull
    @Override
    public ExamViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.exam_item_card, parent, false);
        return new ExamViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExamViewHolder holder, int position) {
        holder.bind(dataList.get(position));

    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public class ExamViewHolder extends RecyclerView.ViewHolder {
        TextView examType, examCourse, examTime, examStatus, examDetail;
        public ExamViewHolder(@NonNull View itemView) {
            super(itemView);
            examType = itemView.findViewById(R.id.exam_type);
            examCourse = itemView.findViewById(R.id.course_name);
            examTime = itemView.findViewById(R.id.time_place);
            examStatus = itemView.findViewById(R.id.way_to_exam);
            examDetail = itemView.findViewById(R.id.exam_detail);
        }

        public void bind(StudentAccountManager.ExamSchedule examSchedule) {
            examType.setText("考试类型:" + examSchedule.ExamType);
            examCourse.setText(examSchedule.CourseName);
            examTime.setText(examSchedule.ExamTimeAndPlace);
            examStatus.setText("备注:" + examSchedule.ExamStatus);
            examDetail.setText(examSchedule.Detail);
        }
    }
}
