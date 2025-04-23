package team.bjtuss.bjtuselfservice.controller

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.CaptchaModel.init
import team.bjtuss.bjtuselfservice.statemanager.AppEvent
import team.bjtuss.bjtuselfservice.statemanager.AppEventManager
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException


object NetworkRequestQueue123 {
    const val MAX_CONCURRENT_JOBS = 2
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = Channel<NetworkRequest>(Channel.UNLIMITED)

    private val activeJobs = AtomicInteger(0)


    init {
        repeat(MAX_CONCURRENT_JOBS) {
            scope.launch {
                AppStateManager.awaitLoginState()
                processQueue()
            }
        }
    }

    data class NetworkRequest(
        val operation: suspend () -> Result<Any>,
        val deferred: CompletableDeferred<Result<Any>>
    )

    private suspend fun processQueue() {
        for (request in queue) {
            processRequestAfterLogin(request)
        }
    }

    private suspend fun processRequestAfterLogin(request: NetworkRequest) {
        AppStateManager.awaitLoginState()

        try {
            activeJobs.incrementAndGet()
//            if (activeJobs.get() > 0) {
//                AppEventManager.sendEvent(AppEvent.DataSyncRequest)
//            }
            val result = request.operation()
            request.deferred.complete(result)
        } catch (e: Exception) {
            Log.e("NetworkRequestQueue", "Error processing request", e)
            request.deferred.complete(Result.failure(e))
        } finally {
            if (activeJobs.decrementAndGet() == 0) {
                AppEventManager.sendEvent(AppEvent.DataSyncCompleted)

            }
        }
    }

    suspend fun <T> enqueue(
        operation: suspend () -> T
    ): Result<T> {
        val deferred = CompletableDeferred<Result<Any>>()
        val wrappedOperation = suspend {
            try {
                @Suppress("UNCHECKED_CAST")
                Result.success(operation() as Any)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val request = NetworkRequest(wrappedOperation, deferred)
        queue.send(request)
        return deferred.await() as Result<T>
    }


    fun shutdown() {
        queue.close()
        scope.cancel()
    }
}
//}object NetworkRequestQueue {
//    const val MAX_CONCURRENT_JOBS = 2
//    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//    private val queue = Channel<NetworkRequest>(Channel.UNLIMITED)
//
//    private val activeJobs = AtomicInteger(0)
//
//
//    init {
//        repeat(MAX_CONCURRENT_JOBS) {
//            scope.launch {
//                AppStateManager.awaitLoginState()
//                processQueue()
//            }
//        }
//    }
//
//    data class NetworkRequest(
//        val operation: suspend () -> Result<Any>,
//        val deferred: CompletableDeferred<Result<Any>>
//    )
//
//    private suspend fun processQueue() {
//        for (request in queue) {
//            processRequestAfterLogin(request)
//        }
//    }
//
//    private suspend fun processRequestAfterLogin(request: NetworkRequest) {
//        AppStateManager.awaitLoginState()
//
//        try {
//            activeJobs.incrementAndGet()
////            if (activeJobs.get() > 0) {
////                AppEventManager.sendEvent(AppEvent.DataSyncRequest)
////            }
//            val result = request.operation()
//            request.deferred.complete(result)
//        } catch (e: Exception) {
//            Log.e("NetworkRequestQueue", "Error processing request", e)
//            request.deferred.complete(Result.failure(e))
//        } finally {
//            if (activeJobs.decrementAndGet() == 0) {
//                AppEventManager.sendEvent(AppEvent.DataSyncCompleted)
//
//            }
//        }
//    }
//
//    suspend fun <T> enqueue(
//        operation: suspend () -> T
//    ): Result<T> {
//        val deferred = CompletableDeferred<Result<Any>>()
//        val wrappedOperation = suspend {
//            try {
//                @Suppress("UNCHECKED_CAST")
//                Result.success(operation() as Any)
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//        val request = NetworkRequest(wrappedOperation, deferred)
//        queue.send(request)
//        return deferred.await() as Result<T>
//    }
//
//
//    fun shutdown() {
//        queue.close()
//        scope.cancel()
//    }
//}

object NetworkRequestQueue {
    const val MAX_CONCURRENT_JOBS_LOW = 2

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 两个队列
    private val lowPriorityQueue = Channel<NetworkRequest>(Channel.UNLIMITED)
    private val highPriorityQueue = Channel<NetworkRequest>(Channel.UNLIMITED)

    // 跟踪两个队列的活跃作业数
    private val activeLowPriorityJobs = AtomicInteger(0)
    private val activeHighPriorityJobs = AtomicInteger(0)

    // 用于同步状态的锁
    private val syncLock = Mutex()

    // 跟踪正在处理的请求，按名称索引
    private val activeRequests = ConcurrentHashMap<String, CompletableDeferred<Result<Any>>>()

    init {
        // 初始化低优先级队列处理器（有限并发）
        repeat(MAX_CONCURRENT_JOBS_LOW) {
            ioScope.launch {
                AppStateManager.awaitLoginState()
                processLowPriorityQueue()
            }
        }

        // 初始化高优先级队列处理器（无限并发）
        ioScope.launch {
            AppStateManager.awaitLoginState()
            processHighPriorityQueue()
        }
    }

    data class NetworkRequest(
        val name: String,
        val operation: suspend () -> Result<Any>,
        val deferred: CompletableDeferred<Result<Any>>
    )

    private suspend fun processLowPriorityQueue() {
        for (request in lowPriorityQueue) {
            processRequest(request, activeLowPriorityJobs)
        }
    }

    private suspend fun processHighPriorityQueue() {
        for (request in highPriorityQueue) {
            // 为每个高优先级请求启动新协程，实现无限并发
            ioScope.launch {
                processRequest(request, activeHighPriorityJobs)
            }
        }
    }

    private suspend fun processRequest(request: NetworkRequest, jobCounter: AtomicInteger) {
        AppStateManager.awaitLoginState()

        try {
            jobCounter.incrementAndGet()
            checkSyncState(true) // 开始任务，可能需要触发状态更新

            println("处理任务：${request.name}")
            val result = request.operation()
            request.deferred.complete(result)

        } catch (e: Exception) {
            Log.e("NetworkRequestQueue", "Error processing request: ${request.name}", e)
            request.deferred.complete(Result.failure(e))
        } finally {
            // 任务完成后从活跃请求映射中移除
            activeRequests.remove(request.name)

            jobCounter.decrementAndGet()
            checkSyncState(false) // 结束任务，检查是否需要发送完成事件
        }
    }

    private suspend fun checkSyncState(isStarting: Boolean) {
        syncLock.withLock {
            val totalActiveJobs = activeLowPriorityJobs.get() + activeHighPriorityJobs.get()
            println(totalActiveJobs)
            if (!isStarting && totalActiveJobs == 0) {
                // 最后一个任务结束
                AppEventManager.sendEvent(AppEvent.DataSyncCompleted)
            }
        }
    }

    suspend fun <T> enqueue(name: String, operation: suspend () -> T): Result<T> {
        return enqueueInternal(name, operation, lowPriorityQueue)
    }

    suspend fun <T> enqueueHighPriority(name: String, operation: suspend () -> T): Result<T> {
        return enqueueInternal(name, operation, highPriorityQueue)
    }

    private suspend fun <T> enqueueInternal(
        name: String,
        operation: suspend () -> T,
        targetQueue: Channel<NetworkRequest>
    ): Result<T> {
        // 检查是否已经有同名请求在处理
        val existingRequest = activeRequests[name]
        if (existingRequest != null) {
            Log.d("NetworkRequestQueue", "复用已有请求: $name")
            return existingRequest.await() as Result<T>
        }

        val deferred = CompletableDeferred<Result<Any>>()
        val wrappedOperation = suspend {
            try {
                @Suppress("UNCHECKED_CAST")
                Result.success(operation() as Any)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // 将新请求添加到活跃请求映射
        activeRequests[name] = deferred

        val request = NetworkRequest(name, wrappedOperation, deferred)
        targetQueue.send(request)
        return deferred.await() as Result<T>
    }

    // 检查某个名称的请求是否正在处理中
    fun isRequestActive(name: String): Boolean {
        return activeRequests.containsKey(name)
    }

    // 取消特定名称的请求
    fun cancelRequest(name: String) {
        activeRequests[name]?.completeExceptionally(CancellationException("Request $name was cancelled"))
        activeRequests.remove(name)
    }

    fun shutdown() {
        // 取消所有活跃请求
        activeRequests.forEach { (name, deferred) ->
            deferred.completeExceptionally(CancellationException("Queue shutdown"))
        }
        activeRequests.clear()

        lowPriorityQueue.close()
        highPriorityQueue.close()
        ioScope.cancel()
    }
}