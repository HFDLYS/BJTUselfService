package com.hfdlys.bjtuselfservice.fragment.grade;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class GradeViewModel extends ViewModel {
    final private MutableLiveData<List<StudentAccountManager.Grade>> gradeList = new MutableLiveData<>();
    public LiveData<List<StudentAccountManager.Grade>> getGradeList() {
        return gradeList;
    }
    public void loadGradeList() {
        StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
        studentAccountManager.getGrade().thenAccept(gradeList::postValue);
    }
}