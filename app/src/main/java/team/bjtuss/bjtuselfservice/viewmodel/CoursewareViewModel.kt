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

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val gson = Gson()
            try {
                val json = DataStoreRepository.getCoursewareJson().first()
                val type = object : TypeToken<List<CoursewareNode>>() {}.type
                if (json.isNotBlank()) {
                    try {
                        _coursewareRootNodeList.value = gson.fromJson(json, type) ?: emptyList()
                    } catch (_: Exception) {
                        Log.w("Courseware", "Ignoring invalid courseware cache")
                    }
                }
                val list = getCourseList().map { generateCoursewareRootNode(it) }
                // Commit a complete successful response, including a valid empty list.
                DataStoreRepository.setCoursewareJson(gson.toJson(list))
                _coursewareRootNodeList.value = list
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w("Courseware", "Courseware refresh failed; keeping cached data")
                _errorMessage.value = "课件加载失败，请重试；若仍失败，请重新登录"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
