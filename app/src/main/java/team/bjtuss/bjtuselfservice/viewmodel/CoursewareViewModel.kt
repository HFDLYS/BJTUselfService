package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.jsonclass.Course
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.jsonclass.Node
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.generateCoursewareTree
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.getCourseList
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.getCoursewareCatalog


class CoursewareViewModel() : ViewModel() {
    //    private var coursewareList: List<CoursewareJsonType> = emptyList()
    private var _courseList: MutableStateFlow<List<Course>> =
        MutableStateFlow(emptyList())
    val courseList = _courseList.asStateFlow()

    private var _coursewareCatalogMap: MutableStateFlow<MutableMap<String, List<Node>>> =
        MutableStateFlow(mutableMapOf())
    val coursewareCatalogMap = _coursewareCatalogMap.asStateFlow()

    private var _coursewareTree: MutableStateFlow<MutableList<CoursewareNode>> =
        MutableStateFlow(mutableListOf())

    val coursewareTree = _coursewareTree.asStateFlow()


    init {
        viewModelScope.launch {
            _courseList.value = getCourseList()
            _courseList.value.forEach { course ->
                val coursewareCatalog = getCoursewareCatalog(course)
                _coursewareCatalogMap.value.put(course.name, coursewareCatalog)
                _coursewareTree.value.add(generateCoursewareTree(course))
                if (course.name == "数字信号处理") {

                    println(_coursewareTree.value)
                }
            }
        }
    }
}