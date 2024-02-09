package com.hfdlys.bjtuselfservice.fragment.exam;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class ExamViewModel extends ViewModel {
    final private MutableLiveData<List<StudentAccountManager.ExamSchedule>> examList = new MutableLiveData<>();
    public LiveData<List<StudentAccountManager.ExamSchedule>> getExamList() {
        return examList;
    }
    public void loadExamList() {
        StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
        studentAccountManager.getExamSchedule().thenAccept(examList::postValue);
    }
}