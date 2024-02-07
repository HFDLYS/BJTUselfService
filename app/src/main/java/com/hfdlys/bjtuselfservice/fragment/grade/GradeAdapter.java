package com.hfdlys.bjtuselfservice.fragment.grade;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class GradeAdapter extends RecyclerView.Adapter<GradeAdapter.GradeViewHolder> {
    private List<StudentAccountManager.Grade> dataList;

    public GradeAdapter(List<StudentAccountManager.Grade> dataList) {
        this.dataList = dataList;
    }

    @NonNull
    @Override
    public GradeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.grade_item_card, parent, false);
        return new GradeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GradeViewHolder holder, int position) {
        holder.bind(dataList.get(position));

    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public class GradeViewHolder extends RecyclerView.ViewHolder {
        TextView gradeCourse, gradeYear, gradeScore;
        public GradeViewHolder(@NonNull View itemView) {
            super(itemView);
            gradeCourse = itemView.findViewById(R.id.grade_course);
            gradeYear = itemView.findViewById(R.id.grade_year);
            gradeScore = itemView.findViewById(R.id.grade_score);
        }

        public void bind(StudentAccountManager.Grade grade) {
            gradeCourse.setText(grade.courseName + "/(" + grade.courseGPA + ")" + grade.courseTeacher);
            gradeYear.setText(grade.courseYear);
            gradeScore.setText(grade.courseScore);
        }
    }
}
