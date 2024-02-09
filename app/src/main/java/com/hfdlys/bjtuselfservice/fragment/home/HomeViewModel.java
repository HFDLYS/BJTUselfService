package com.hfdlys.bjtuselfservice.fragment.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.StudentAccountManager;
import com.hfdlys.bjtuselfservice.StudentAccountManager.Status;

public class HomeViewModel extends ViewModel {

    MutableLiveData<Status> status = new MutableLiveData<>();
    StudentAccountManager studentAccountManager = StudentAccountManager.getInstance();
    public HomeViewModel() {

    }
    public MutableLiveData<Status> getStatus() {
        return status;
    }

    public void setStatus(Status s) {
        status.postValue(s);
    }

    public MutableLiveData<StudentAccountManager.StudentInfo> getStuInfo() {
        return studentAccountManager.getStudentInfo();
    }
    public MutableLiveData<Boolean> getIsLogin() {
        return studentAccountManager.getIsMisLogin();
    }
}