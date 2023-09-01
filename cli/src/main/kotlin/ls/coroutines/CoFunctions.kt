package ls.coroutines

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Semaphore

/**
 * Maps all items of an [Iterable] with a given [concurrencyLevel] in parallel. The item ordering is kept.
 */
inline fun <T, R> Iterable<T>.mapInOrder(concurrencyLevel: Int, crossinline block: suspend (T) -> R): Flow<R> {
    val semaphore = Semaphore(concurrencyLevel)
    return channelFlow {
        forEach {
            semaphore.acquire()
            send(async { block(it) })
        }
    }.map { it.await() }.onEach { semaphore.release() }
}
