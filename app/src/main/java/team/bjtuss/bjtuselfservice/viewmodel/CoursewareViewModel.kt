package team.bjtuss.bjtuselfservice.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.jsonclass.CoursewareNode
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.generateCoursewareRootNode
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.getCourseList

class CoursewareViewModel : ViewModel() {
    private val _coursewareRootNodeList = MutableStateFlow<List<CoursewareNode>>(emptyList())
    val coursewareRootNodeList = _coursewareRootNodeList.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

            if (json != "" && json != "[]") {
                _coursewareRootNodeList.value = gson.fromJson(json, type) ?: mutableListOf()
            }
            val courses = runCatching { getCourseList() }
                .onFailure { Log.e("Courseware", "getCourseList failed", it) }
                .getOrDefault(emptyList())
            if (courses.isEmpty()) {
                // Network/session failure must not erase the last good cache.
                Log.w("Courseware", "course list empty; keeping cached courseware")
                return@launch
            }
            val list = mutableListOf<CoursewareNode>()
            courses.forEach { course ->
                list.add(
                    generateCoursewareRootNode(
                        course
                    )
                )
            }
        }
    }
}
