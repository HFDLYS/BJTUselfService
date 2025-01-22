package team.bjtuss.bjtuselfservice.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NetworkRequestQueue {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val queue = Channel<NetworkRequest>(Channel.UNLIMITED)

    init {
        scope.launch {
            processQueue()
        }
    }

    data class NetworkRequest(
        val id: String,
        val operation: suspend () -> Result<Any>,
        val deferred: CompletableDeferred<Result<Any>>
    )

    private suspend fun processQueue() {
        //相当于while(true){
        //  val request = queue.receive()
        // }
        //是一个死循环的语法糖

        for (request in queue) {
            try {
                mutex.withLock {
                    val result = request.operation()
                    request.deferred.complete(result)
                }
            } catch (e: Exception) {
                // 记录异常
                println("Error processing request ${request.id}: ${e.message}")
                request.deferred.complete(Result.failure(e))
            }
        }
    }

    suspend fun <T> enqueue(
        id: String,
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

        val request = NetworkRequest(id, wrappedOperation, deferred)
        queue.send(request)

        return deferred.await() as Result<T>
    }

    fun shutdown() {
        queue.close()
        scope.cancel()
    }
}