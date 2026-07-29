package team.bjtuss.bjtuselfservice.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import team.bjtuss.bjtuselfservice.database.AppDatabase
import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.entity.GradeSelectionRecord
import team.bjtuss.bjtuselfservice.repository.DatabaseRepository
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.repository.NetworkRepository
import team.bjtuss.bjtuselfservice.utils.gradeDataNeedsSync
import team.bjtuss.bjtuselfservice.utils.gradeIdsForSelectionRecords
import team.bjtuss.bjtuselfservice.utils.selectionRecordsExcludingSemesters
import team.bjtuss.bjtuselfservice.utils.selectionRecordsForGradeIdsPreservingUnmatched
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong


internal fun shouldFinalizeGradeSelectionClear(
    clearingStudentId: String?,
    activeStudentId: String?,
    clearingGeneration: Long,
    currentGeneration: Long,
): Boolean {
    return clearingStudentId == activeStudentId &&
            clearingGeneration == currentGeneration
}

internal fun shouldReloadGradeSelectionsAfterClearFailure(
    clearingStudentId: String?,
    activeStudentId: String?,
    clearingGeneration: Long,
    currentGeneration: Long,
): Boolean {
    return clearingStudentId != null &&
            shouldFinalizeGradeSelectionClear(
                clearingStudentId = clearingStudentId,
                activeStudentId = activeStudentId,
                clearingGeneration = clearingGeneration,
                currentGeneration = currentGeneration,
            )
}


class GradeViewModel(
    private val savedStateHandle: SavedStateHandle,
) : BaseSyncViewModel<GradeEntity>(
    dataSyncManager = DefaultDataSyncManager<GradeEntity>(
        AppDatabase.getInstance().gradeEntityDao()
    ) { it.courseName }
) {

    val gradeList: StateFlow<List<GradeEntity>> = DatabaseRepository.gradeList
    private val _selectedGradeIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedGradeIds: StateFlow<Set<Int>> = _selectedGradeIds.asStateFlow()
    val selectionUiResetGeneration: StateFlow<Long> =
        savedStateHandle.getStateFlow(SELECTION_UI_RESET_GENERATION_KEY, 0L)

    private var selectionLoadJob: Job? = null
    private val selectionPersistenceMutex = Mutex()
    private val selectionPersistenceGeneration = AtomicLong(0)
    private val latestSelectionRecordsByStudent =
        ConcurrentHashMap<String, List<GradeSelectionRecord>>()
    private var activeStudentId: String? = null
    private var storedSelectionRecords: List<GradeSelectionRecord> = emptyList()
    private var selectionRecordsLoaded = false
    private var selectionGradeDataReady = false
    private var selectionGradeSyncPending = false
    private var selectionGradeSyncStudentId: String? = null

    init {
        viewModelScope.launch {
            gradeList.collectLatest { grades ->
                reconcileSelectedGrades(grades)
            }
        }
        viewModelScope.launch {
            changeList.collectLatest { changes ->
                if (changes.isEmpty()) {
                    if (
                        selectionGradeSyncPending &&
                        selectionGradeSyncStudentId == activeStudentId
                    ) {
                        selectionGradeSyncPending = false
                        reconcileSelectedGrades(DatabaseRepository.getGradeList())
                    }
                    return@collectLatest
                }
            }
        }
    }

    override suspend fun fetchNetworkData(): List<GradeEntity> {
        val syncStudentId = activeStudentId
        val grades = NetworkRepository.getGradeList()
        if (syncStudentId != null && activeStudentId == syncStudentId) {
            val localGrades = DatabaseRepository.getGradeList()
            selectionGradeSyncStudentId = syncStudentId
            selectionGradeDataReady = true
            selectionGradeSyncPending = gradeDataNeedsSync(grades, localGrades)
            if (!selectionGradeSyncPending) {
                reconcileSelectedGrades(localGrades)
            }
        }
        return grades
    }

    override suspend fun fetchLocalData(): List<GradeEntity> {
        return DatabaseRepository.getGradeList()
    }

    fun setGradeSelected(gradeId: Int, selected: Boolean) {
        requestSelectedGradeIdsUpdate(
            transform = { selectedGradeIds, _ ->
                if (selected) {
                    selectedGradeIds + gradeId
                } else {
                    selectedGradeIds - gradeId
                }
            }
        )
    }

    fun selectGrades(gradeIds: Set<Int>) {
        requestSelectedGradeIdsUpdate(
            transform = { selectedGradeIds, _ ->
                selectedGradeIds + gradeIds
            }
        )
    }

    fun deselectGradesInSemesters(semesters: Set<String>) {
        requestSelectedGradeIdsUpdate(
            transform = { selectedGradeIds, grades ->
                val gradeIdsInSemesters = grades
                    .filter { it.tag in semesters }
                    .map { it.id }
                    .toSet()
                selectedGradeIds - gradeIdsInSemesters
            },
            storedRecordsTransform = { records ->
                selectionRecordsExcludingSemesters(records, semesters)
            },
        )
    }

    fun clearSelectedGrades(onCommitted: () -> Unit) {
        requestSelectedGradeIdsUpdate(
            transform = { _, _ -> emptySet() },
            storedRecordsTransform = { emptyList() },
            onCommitted = onCommitted,
        )
    }

    fun activateStudentSelections(studentId: String) {
        val normalizedStudentId = studentId.trim()
        if (normalizedStudentId.isEmpty()) {
            deactivateStudentSelections()
            return
        }
        if (activeStudentId == normalizedStudentId) {
            return
        }
        if (activeStudentId != null) {
            requestSelectionUiReset()
        }

        selectionLoadJob?.cancel()
        val activationGeneration = selectionPersistenceGeneration.incrementAndGet()
        activeStudentId = normalizedStudentId
        storedSelectionRecords = emptyList()
        selectionRecordsLoaded = false
        selectionGradeDataReady = false
        selectionGradeSyncPending = false
        selectionGradeSyncStudentId = null
        _selectedGradeIds.value = emptySet()

        launchGradeSelectionLoad(
            studentId = normalizedStudentId,
            generation = activationGeneration,
        )
    }

    private fun launchGradeSelectionLoad(
        studentId: String,
        generation: Long,
    ) {
        selectionLoadJob = viewModelScope.launch {
            val loadedRecords = selectionPersistenceMutex.withLock {
                latestSelectionRecordsByStudent[studentId]
                    ?: DataStoreRepository.getGradeSelections(studentId).also {
                        latestSelectionRecordsByStudent[studentId] = it
                    }
            }
            if (
                activeStudentId != studentId ||
                selectionPersistenceGeneration.get() != generation
            ) {
                return@launch
            }
            storedSelectionRecords = loadedRecords
            selectionRecordsLoaded = true
            reconcileSelectedGrades(gradeList.value)
        }
    }

    fun deactivateStudentSelections() {
        requestSelectionUiReset()
        selectionPersistenceGeneration.incrementAndGet()
        selectionLoadJob?.cancel()
        selectionLoadJob = null
        activeStudentId = null
        storedSelectionRecords = emptyList()
        selectionRecordsLoaded = false
        selectionGradeDataReady = false
        selectionGradeSyncPending = false
        selectionGradeSyncStudentId = null
        _selectedGradeIds.value = emptySet()
    }

    private fun requestSelectionUiReset() {
        savedStateHandle[SELECTION_UI_RESET_GENERATION_KEY] =
            selectionUiResetGeneration.value + 1L
    }

    suspend fun clearAllPersistedGradeSelections() {
        val clearingStudentId = activeStudentId
        val clearingGeneration =
            selectionPersistenceGeneration.incrementAndGet()
        selectionLoadJob?.cancel()
        selectionLoadJob = null
        selectionRecordsLoaded = false

        var clearSucceeded = false
        try {
            selectionPersistenceMutex.withLock {
                latestSelectionRecordsByStudent.clear()
                DataStoreRepository.clearAllGradeSelections()
                storedSelectionRecords = emptyList()
                _selectedGradeIds.value = emptySet()
            }
            clearSucceeded = true
        } finally {
            val currentGeneration = selectionPersistenceGeneration.get()
            if (
                clearSucceeded &&
                shouldFinalizeGradeSelectionClear(
                    clearingStudentId = clearingStudentId,
                    activeStudentId = activeStudentId,
                    clearingGeneration = clearingGeneration,
                    currentGeneration = currentGeneration,
                )
            ) {
                selectionRecordsLoaded = clearingStudentId != null
            } else if (
                !clearSucceeded &&
                shouldReloadGradeSelectionsAfterClearFailure(
                    clearingStudentId = clearingStudentId,
                    activeStudentId = activeStudentId,
                    clearingGeneration = clearingGeneration,
                    currentGeneration = currentGeneration,
                )
            ) {
                launchGradeSelectionLoad(
                    studentId = clearingStudentId!!,
                    generation = clearingGeneration,
                )
            }
        }
    }

    private fun requestSelectedGradeIdsUpdate(
        transform: (Set<Int>, List<GradeEntity>) -> Set<Int>,
        storedRecordsTransform: (
            (List<GradeSelectionRecord>) -> List<GradeSelectionRecord>
        ) = { it },
        onCommitted: () -> Unit = {},
    ) {
        val studentId = activeStudentId ?: return
        val persistenceGeneration = selectionPersistenceGeneration.get()
        val loadJob = selectionLoadJob

        viewModelScope.launch {
            loadJob?.join()
            val committed = selectionPersistenceMutex.withLock {
                if (
                    activeStudentId != studentId ||
                    selectionPersistenceGeneration.get() != persistenceGeneration ||
                    !selectionRecordsLoaded
                ) {
                    return@withLock false
                }

                val grades = gradeList.value
                val validGradeIds = grades.map { it.id }.toSet()
                val baseStoredRecords =
                    storedRecordsTransform(storedSelectionRecords)
                val currentSelectedGradeIds = (
                        _selectedGradeIds.value +
                                gradeIdsForSelectionRecords(
                                    grades,
                                    baseStoredRecords,
                                )
                        ) intersect validGradeIds
                val updatedSelectedGradeIds =
                    transform(currentSelectedGradeIds, grades) intersect validGradeIds
                val records = selectionRecordsForGradeIdsPreservingUnmatched(
                    grades = grades,
                    storedRecords = baseStoredRecords,
                    selectedGradeIds = updatedSelectedGradeIds,
                )

                try {
                    DataStoreRepository.setGradeSelections(studentId, records)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(
                        "GradeViewModel",
                        "Unable to persist grade selections for $studentId",
                        exception,
                    )
                    return@withLock false
                }

                if (
                    activeStudentId != studentId ||
                    selectionPersistenceGeneration.get() != persistenceGeneration
                ) {
                    return@withLock false
                }

                latestSelectionRecordsByStudent[studentId] = records
                storedSelectionRecords = records
                _selectedGradeIds.value = updatedSelectedGradeIds
                true
            }
            if (committed) {
                onCommitted()
            }
        }
    }

    private fun reconcileSelectedGrades(grades: List<GradeEntity>) {
        if (
            !selectionRecordsLoaded ||
            !selectionGradeDataReady ||
            selectionGradeSyncPending ||
            selectionGradeSyncStudentId != activeStudentId
        ) {
            return
        }
        if (grades.isEmpty()) {
            _selectedGradeIds.value = emptySet()
            return
        }

        val currentGradeIds = grades.map { it.id }.toSet()
        val survivingSelectedIds = _selectedGradeIds.value intersect currentGradeIds
        val restoredSelectedIds =
            gradeIdsForSelectionRecords(grades, storedSelectionRecords)
        val reconciledSelectedIds = survivingSelectedIds + restoredSelectedIds
        _selectedGradeIds.value = reconciledSelectedIds

        val normalizedRecords = selectionRecordsForGradeIdsPreservingUnmatched(
            grades = grades,
            storedRecords = storedSelectionRecords,
            selectedGradeIds = reconciledSelectedIds,
        )
        if (normalizedRecords != storedSelectionRecords) {
            storedSelectionRecords = normalizedRecords
            activeStudentId?.let { studentId ->
                scheduleSelectionPersistence(studentId, normalizedRecords)
            }
        }
    }

    private fun scheduleSelectionPersistence(
        studentId: String,
        records: List<GradeSelectionRecord>,
    ) {
        val persistenceGeneration = selectionPersistenceGeneration.get()
        latestSelectionRecordsByStudent[studentId] = records
        viewModelScope.launch {
            selectionPersistenceMutex.withLock {
                if (
                    activeStudentId != studentId ||
                    selectionPersistenceGeneration.get() != persistenceGeneration
                ) {
                    return@withLock
                }
                try {
                    DataStoreRepository.setGradeSelections(
                        studentId,
                        latestSelectionRecordsByStudent[studentId].orEmpty(),
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(
                        "GradeViewModel",
                        "Unable to normalize grade selections for $studentId",
                        exception,
                    )
                }
            }
        }
    }

    private companion object {
        const val SELECTION_UI_RESET_GENERATION_KEY =
            "grade_selection_ui_reset_generation"
    }
}

//class GradeViewModel() : ViewModel() {
//
//    val gradeList: StateFlow<List<GradeEntity>> = DatabaseRepository.gradeList
//
//    private val _gradeChange = MutableStateFlow<List<DataChange<GradeEntity>>>(mutableListOf())
//    val gradeChange: StateFlow<List<DataChange<GradeEntity>>> = _gradeChange.asStateFlow()
//
//    private val gradeEntityDao = AppDatabase.getInstance().gradeEntityDao()
//
//    private val dataSyncManager =
//        DefaultDataSyncManager<GradeEntity>(gradeEntityDao) { it.courseName }
//
//
//    init {
//        loadDataAndDetectChanges()
//    }
//
//
//    fun loadDataAndDetectChanges() {
//
//        viewModelScope.launch {
//            val networkData = NetWorkRepository.getGradeList()
//            val localData = DatabaseRepository.getGradeList()
//            val changes = dataSyncManager.detectChanges(networkData, localData)
//            _gradeChange.value = changes
//
//        }
//    }
//
//    fun syncDataAndClearChange() {
//        viewModelScope.launch {
//            dataSyncManager.applyChanges(_gradeChange.value)
//            _gradeChange.value = emptyList()
//        }
//    }
//}
