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
import kotlinx.coroutines.withContext
import team.bjtuss.bjtuselfservice.CaptchaModel.init
import team.bjtuss.bjtuselfservice.statemanager.AppEvent
import team.bjtuss.bjtuselfservice.statemanager.AppEventManager
import team.bjtuss.bjtuselfservice.statemanager.AppStateManager
import java.util.concurrent.atomic.AtomicInteger


object NetworkRequestQueue {
    const val MAX_CONCURRENT_JOBS = 2
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = Channel<NetworkRequest>(Channel.UNLIMITED)

    private val activeJobs = AtomicInteger(0)

//    private val _isBusy: MutableStateFlow<Boolean> = MutableStateFlow(false)
//    val isBusy = _isBusy.asStateFlow()


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
//            _isBusy.value = activeJobs.get() > 0
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