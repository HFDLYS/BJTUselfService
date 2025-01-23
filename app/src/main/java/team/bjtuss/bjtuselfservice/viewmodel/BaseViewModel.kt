package team.bjtuss.bjtuselfservice.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.dao.BaseDao
import team.bjtuss.bjtuselfservice.entity.BaseEntity

sealed class DataChange<T> {
    data class Added<T>(val items: List<T>) : DataChange<T>()
    data class Modified<T>(val items: List<Pair<T, T>>) : DataChange<T>()
    data class Deleted<T>(val items: List<T>) : DataChange<T>()
}

interface DataSyncManager<T> {
    suspend fun detectChanges(networkData: List<T>, localData: List<T>): List<DataChange<T>>
    suspend fun applyChanges(changes: List<DataChange<T>>)
}

class DefaultDataSyncManager<T : BaseEntity>(
    private val dao: BaseDao<T>,
    private val identitySelector: (T) -> Any
) : DataSyncManager<T> {

    override suspend fun detectChanges(
        networkData: List<T>,
        localData: List<T>
    ): List<DataChange<T>> {
        val changes = mutableListOf<DataChange<T>>()

        val networkMap = networkData.associateBy(identitySelector)
        val localMap = localData.associateBy(identitySelector)

        // 检测新增
        val added = networkData.filter { identitySelector(it) !in localMap.keys }
        if (added.isNotEmpty()) changes.add(DataChange.Added(added))

        // 检测修改
        val modified = networkData.filter { item ->
            val key = identitySelector(item)
            val localItem = localMap[key]
            if (localItem != null) {
                item.id = localItem.id
            } else {
                return@filter false
            }
            item != localMap[key]
        }.map {
            it to localMap[identitySelector(it)]!!
        }
        if (modified.isNotEmpty()) changes.add(DataChange.Modified(modified))

        // 检测删除
        val deleted = localData.filter { identitySelector(it) !in networkMap.keys }
        if (deleted.isNotEmpty()) changes.add(DataChange.Deleted(deleted))

        return changes
    }

    override suspend fun applyChanges(changes: List<DataChange<T>>) {
        withContext(Dispatchers.IO) {
            changes.forEach { change ->
                when (change) {
                    is DataChange.Added -> change.items.forEach {
                        dao.insert(it)
                    }

                    is DataChange.Modified -> change.items.forEach {
                        dao.update(it.first)
                    }

                    is DataChange.Deleted -> change.items.forEach {
                        dao.delete(it)
                    }
                }
            }
        }
    }
}

interface BaseViewModel<T : BaseEntity> {
    fun loadDataAndDetectChanges()
    fun syncDataAndClearChange()
}


abstract class BaseSyncViewModel<T : BaseEntity>(
    private val dataSyncManager: DataSyncManager<T>
) : ViewModel(), BaseViewModel<T> {

    protected val _changeList = MutableStateFlow<List<DataChange<T>>>(mutableListOf())
    val changeList: StateFlow<List<DataChange<T>>> = _changeList.asStateFlow()



    // 数据加载与变更检测
    override fun loadDataAndDetectChanges() {
        viewModelScope.launch {
            val networkData = fetchNetworkData()
            val localData = fetchLocalData()
            val changes = dataSyncManager.detectChanges(networkData, localData)
            _changeList.value = changes
        }
    }

    // 数据同步与清理变更
    override fun syncDataAndClearChange() {

        viewModelScope.launch {
            dataSyncManager.applyChanges(_changeList.value)
            _changeList.value = emptyList()
        }
    }

    // 供子类实现：从网络获取数据
    protected abstract suspend fun fetchNetworkData(): List<T>

    // 供子类实现：从本地数据库获取数据
    protected abstract suspend fun fetchLocalData(): List<T>
}