package com.hfdlys.bjtuselfservice.fragment.exam;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class ExamViewModel extends ViewModel {
    final private MutableLiveData<List<StudentAccountManager.ExamSchedule>> examList = new MutableLiveData<>();
    StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
    public LiveData<List<StudentAccountManager.ExamSchedule>> getExamList() {
        return examList;
    }
    public void loadExamList() {
        studentAccountManager.getExamSchedule().thenAccept(examList::postValue)
                .exceptionally(throwable -> {
                    if (throwable.toString().equals("Not loginAa")) {
                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
                            if (aBoolean) {
                                loadExamList();
                            }
                        });
                    } else if (throwable.toString().equals("Not login")) {
                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
                            if (aBoolean) {
                                loadExamList();
                            }
                        });
                    } else {
                        examList.postValue(null);
                    }
                    return null;
                });
    }
    public MutableLiveData<Boolean> getIsAaLogin() {
        return studentAccountManager.getIsAaLogin();
    }
}