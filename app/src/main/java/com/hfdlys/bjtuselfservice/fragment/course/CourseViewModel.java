package com.hfdlys.bjtuselfservice.fragment.course;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.StudentAccountManager;

import java.util.List;

public class CourseViewModel extends ViewModel {
    final private MutableLiveData<List<StudentAccountManager.Course>> courseList = new MutableLiveData<>();
    StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
    public LiveData<List<StudentAccountManager.Course>> getCourseList() {
        return courseList;
    }
    public void loadCourseList(boolean isCurrentTerm) {
        studentAccountManager.getCourseList(isCurrentTerm).thenAccept(courseList::postValue)
                .exceptionally(throwable -> {
                    if (throwable.toString().equals("Not loginAa")) {
                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
                            if (aBoolean) {
                                loadCourseList(isCurrentTerm);
                            }
                        });
                    } else if (throwable.toString().equals("Not login")) {
                        studentAccountManager.loginAa().thenAccept(aBoolean -> {
                            if (aBoolean) {
                                loadCourseList(isCurrentTerm);
                            }
                        });
                    } else {
                        courseList.postValue(null);
                    }
                    return null;
                });
    }
    public MutableLiveData<Boolean> getIsAaLogin() {
        return studentAccountManager.getIsAaLogin();
    }
}