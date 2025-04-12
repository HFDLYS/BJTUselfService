package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.entity.CoursewareCourseEntity
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
//            _courseList.value = getCourseList()
            val json2 = DataStoreRepository.getCoursewareJson().first()
            val type = object : TypeToken<MutableList<CoursewareNode>>() {}.type
            _coursewareRootNodeList.value = gson.fromJson(json2, type) ?: mutableListOf()


            val list = mutableListOf<CoursewareNode>()
            getCourseList().forEach { course ->
                list.add(
                    generateCoursewareRootNode(
                        CoursewareCourseEntity(
                            fz_id = course.fz_id,
                            course_num = course.course_num,
                            xq_code = course.xq_code,
                            name = course.name
                        )
                    )
                )
            }
            _coursewareRootNodeList.value = list
            val json = gson.toJson(coursewareRootNodeList.value)
            DataStoreRepository.setCoursewareJson(
                json
            )

        }
    }
}