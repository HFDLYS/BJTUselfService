package com.hfdlys.bjtuselfservice.fragment.grade;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class GradeViewModel extends ViewModel {
    private MutableLiveData<List<StudentAccountManager.Grade>> gradeList;
    public LiveData<List<StudentAccountManager.Grade>> getGradeList() {
        if (gradeList == null) {
            gradeList = new MutableLiveData<>();
            loadGradeList();
        }
        return gradeList;
    }
    private void loadGradeList() {
        StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
        studentAccountManager.getGrade().thenAccept(grades -> {
            gradeList.postValue(grades);
        });
    }
}