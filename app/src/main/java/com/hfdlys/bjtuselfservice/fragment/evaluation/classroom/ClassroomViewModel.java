package com.hfdlys.bjtuselfservice.fragment.evaluation.classroom;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.hfdlys.bjtuselfservice.web.ClassroomCapacityService;

public class ClassroomViewModel extends ViewModel {
    private MutableLiveData<String> selectedBuilding = new MutableLiveData<>();
    private MutableLiveData<ClassroomCapacityService.BuildingInfo> buildingInfo = new MutableLiveData<>();
    public void selectBuilding(String buildingName) {
        selectedBuilding.postValue(buildingName);
    }

    public void setBuildingInfo(ClassroomCapacityService.BuildingInfo buildingInfo) {
        this.buildingInfo.postValue(buildingInfo);
    }

    public MutableLiveData<String> getSelectedBuilding() {
        return selectedBuilding;
    }

    public MutableLiveData<ClassroomCapacityService.BuildingInfo> getBuildingInfo() {
        return buildingInfo;
    }
}