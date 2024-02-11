package com.hfdlys.bjtuselfservice.fragment.grade;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class GradeViewModel extends ViewModel {
    final private MutableLiveData<List<StudentAccountManager.Grade>> gradeList = new MutableLiveData<>();
    StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
    public LiveData<List<StudentAccountManager.Grade>> getGradeList() {
        return gradeList;
    }
    public void loadGradeList() {
        studentAccountManager.getGrade().thenAccept(gradeList::postValue)
                .exceptionally(throwable -> {
                    if (throwable.toString().equals("Not loginAa")) {
                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
                            if (aBoolean) {
                                loadGradeList();
                            }
                        });
                    } else if (throwable.toString().equals("Not login")) {
                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
                            if (aBoolean) {
                                loadGradeList();
                            }
                        });
                    } else {
                        gradeList.postValue(null);
                    }
                    return null;
                });
    }
    public MutableLiveData<Boolean> getIsAaLogin() {
        return studentAccountManager.getIsAaLogin();
    }
}