package br.com.otavioesteves.finances.data.ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns one reusable resource and serializes use, replacement and disposal. */
internal class SerializedResource<K, T>(private val close: (T) -> Unit) {
    private val mutex = Mutex()
    private var resource: T? = null
    private var currentKey: K? = null

    suspend fun <R> use(key: () -> K, create: (K) -> T, block: suspend (T) -> R): R = mutex.withLock {
        val requestedKey = key()
        if (resource == null || currentKey != requestedKey) {
            closeCurrent()
            resource = create(requestedKey)
            currentKey = requestedKey
        }
        block(requireNotNull(resource))
    }

    suspend fun <R> invalidate(block: suspend () -> R): R = mutex.withLock {
        closeCurrent()
        block()
    }

    private fun closeCurrent() {
        val previous = resource
        resource = null
        currentKey = null
        previous?.let(close)
    }
}
