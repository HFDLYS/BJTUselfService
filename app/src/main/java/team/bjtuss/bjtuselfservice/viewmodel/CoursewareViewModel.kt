package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.generateCoursewareRootNode
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.getCourseList


class CoursewareViewModel() : ViewModel() {
    private var _coursewareRootNodeList: MutableStateFlow<MutableList<CoursewareNode>> =
        MutableStateFlow(mutableListOf())
    val coursewareRootNodeList = _coursewareRootNodeList.asStateFlow()


    init {
        viewModelScope.launch {
            val gson = Gson()
            val json = DataStoreRepository.getCoursewareJson().first()
            val type = object : TypeToken<MutableList<CoursewareNode>>() {}.type

            if (json != "" && json != "[]") {
                _coursewareRootNodeList.value = gson.fromJson(json, type) ?: mutableListOf()
            }
            val list = mutableListOf<CoursewareNode>()
            getCourseList().forEach { course ->
                list.add(
                    generateCoursewareRootNode(
                        course
                    )
                )
            }
            _coursewareRootNodeList.value = list

            DataStoreRepository.setCoursewareJson(
                gson.toJson(coursewareRootNodeList.value)
            )
        }
    }
}