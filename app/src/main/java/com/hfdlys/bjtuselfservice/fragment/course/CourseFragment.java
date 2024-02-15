package com.hfdlys.bjtuselfservice.fragment.course;

import static androidx.appcompat.content.res.AppCompatResources.getDrawable;
import static com.hfdlys.bjtuselfservice.utils.Utils.generateRandomColor;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.hfdlys.bjtuselfservice.R;
import com.hfdlys.bjtuselfservice.StudentAccountManager;
import com.hfdlys.bjtuselfservice.databinding.FragmentCourseBinding;

import java.util.Objects;

public class CourseFragment extends Fragment {

    private CourseViewModel CourseViewModel;

    public static CourseFragment newInstance() {
        return new CourseFragment();
    }
    FragmentCourseBinding binding;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCourseBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    TextView termLabel;
    SwitchMaterial switchTerm;
    GridLayout courseGrid;
    ProgressBar loading;
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CourseViewModel = new ViewModelProvider(this).get(CourseViewModel.class);
        termLabel = binding.TermLabel;
        switchTerm = binding.switchTerm;
        courseGrid = binding.gridLayoutCourse;
        loading = binding.loading;
        loading.setVisibility(View.VISIBLE);
        termLabel.setText(!switchTerm.isChecked() ? "本学期课表" : "选课课表");


        switchTerm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                termLabel.setText(!isChecked ? "本学期课表" : "选课课表");
                loadTermData(isChecked);
            }
        });
        loadTermData(switchTerm.isChecked());
        CourseViewModel.getCourseList().observe(getViewLifecycleOwner(), courseList -> {
            if (courseList == null) {
                Snackbar.make(view, "课表加载失败😭", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                loading.setVisibility(View.GONE);
            } else {
                initCourseGrid();
                for (int i = 0; i < courseList.size(); i++) {
                    if (i % 8 == 0) continue;
                    int row = i / 8 + 1;
                    int col = i % 8;
                    if (courseList.get(i) != null) addCardToGrid(courseList.get(i), row, col);
                    else addDashedBorderToGrid(row, col);
                }
            }
            loading.setVisibility(View.GONE);
        });
    }

    private void loadTermData(boolean isChecked) {
        loading.setVisibility(View.VISIBLE);
        CourseViewModel.loadCourseList(isChecked);
    }
    // 这里需要写在Const类中，但是我偷懒了🤫
    String[] weekDays = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
    String[] courseTimes = {"第1节\n[08:00\n09:50]", "第2节\n[10:10\n12:00]", "第3节\n[12:10\n14:00]", "第4节\n[14:10\n16:00]", "第5节\n[16:20\n18:10]", "第6节\n[19:00\n20:50]", "第7节\n[21:00\n21:50]"};

    private void initCourseGrid() {
        // 初始化课表，表头信息属于是
        courseGrid.removeAllViews();
        for (int i = 0; i < weekDays.length; i++) {
            addHeaderToGrid(weekDays[i], 0, i + 1, true);
        }
        for (int j = 0; j < courseTimes.length; j++) {
            addHeaderToGrid(courseTimes[j], j + 1, 0, false);
        }
    }

    private void addHeaderToGrid(String text, int row, int col, boolean isHeader) {
        Context context = getContext();
        CardView cardView = new CardView(context);
        cardView.setRadius(8);
        cardView.setCardBackgroundColor(Color.parseColor("#D3D3D3"));
        cardView.setLayoutParams(new CardView.LayoutParams(CardView.LayoutParams.WRAP_CONTENT, CardView.LayoutParams.WRAP_CONTENT));
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setGravity(Gravity.CENTER);
        textView.setLayoutParams(new CardView.LayoutParams(CardView.LayoutParams.MATCH_PARENT, CardView.LayoutParams.MATCH_PARENT));
        cardView.addView(textView);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.setGravity(Gravity.FILL);

        if (isHeader) {
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(col, 1f);
            params.rowSpec = GridLayout.spec(row);
        } else {
            params.width = 0;
            params.height = 0;
            params.columnSpec = GridLayout.spec(col, 1f);
            params.rowSpec = GridLayout.spec(row, 1f);
        }
        courseGrid.addView(cardView, params);
    }

    private void addCardToGrid(StudentAccountManager.Course course, int row, int col) {
        Context context = getContext();
        CardView cardView = new CardView(context);
        cardView.setRadius(8);
        String colorRGB = generateRandomColor(course.CourseId);
        cardView.setCardBackgroundColor(Color.parseColor(colorRGB));
        cardView.setLayoutParams(new CardView.LayoutParams(CardView.LayoutParams.WRAP_CONTENT, CardView.LayoutParams.WRAP_CONTENT));
        cardView.setClickable(true);
        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        cardView.setForeground(getDrawable(getContext(), outValue.resourceId));
        TextView textView = new TextView(context);
        textView.setText(course.CourseName);
        textView.setGravity(Gravity.CENTER);
        textView.setLayoutParams(new CardView.LayoutParams(CardView.LayoutParams.MATCH_PARENT, CardView.LayoutParams.MATCH_PARENT));
        cardView.addView(textView);
        cardView.setOnClickListener(v -> {
            Dialog dialog = new Dialog(context);
            dialog.setContentView(R.layout.dialog_course);
            TextView courseId = dialog.findViewById(R.id.course_id);
            TextView courseName = dialog.findViewById(R.id.course_name);
            TextView courseTeacher = dialog.findViewById(R.id.course_teacher);
            TextView courseTime = dialog.findViewById(R.id.course_time);
            TextView coursePlace = dialog.findViewById(R.id.course_place);
            courseId.setText(course.CourseId);
            courseName.setText(course.CourseName);
            courseTeacher.setText("教师名：" + course.CourseTeacher);
            courseTime.setText(course.CourseTime);
            coursePlace.setText("上课地点：" + course.CoursePlace);
            courseId.setTextColor(Color.parseColor(colorRGB));
            courseName.setTextColor(Color.parseColor(colorRGB));
            courseTeacher.setTextColor(Color.parseColor(colorRGB));
            courseTime.setTextColor(Color.parseColor(colorRGB));
            coursePlace.setTextColor(Color.parseColor(colorRGB));
            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.show();
        });
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.setGravity(Gravity.FILL);
        params.width = 0;
        params.height = 0;
        params.columnSpec = GridLayout.spec(col, 1f);
        params.rowSpec = GridLayout.spec(row, 1f);
        courseGrid.addView(cardView, params);
    }

    private void addDashedBorderToGrid(int row, int col) {
        Context context = getContext();
        View view = new View(context);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setBackground(ContextCompat.getDrawable(context, R.drawable.dashed_border));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.setGravity(Gravity.FILL);
        params.width = 0;
        params.height = 0;
        params.columnSpec = GridLayout.spec(col, 1f);
        params.rowSpec = GridLayout.spec(row, 1f);
        courseGrid.addView(view, params);
    }
}