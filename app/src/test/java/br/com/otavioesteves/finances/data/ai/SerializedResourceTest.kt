package br.com.otavioesteves.finances.data.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SerializedResourceTest {
    private class Resource { var closed = false }

    @Test
    fun reusesRuntimeUntilModelChangesOrItIsReleased() = runTest {
        val owner = SerializedResource<String, Resource> { it.closed = true }
        var loads = 0
        fun create(key: String): Resource { loads++; return Resource() }
        val first = owner.use({ "model-a" }, ::create) { it }
        assertSame(first, owner.use({ "model-a" }, ::create) { it })
        assertEquals(1, loads)
        val second = owner.use({ "model-b" }, ::create) { it }
        assertTrue(first.closed)
        assertFalse(second.closed)
        owner.invalidate { }
        assertTrue(second.closed)
        assertNotSame(second, owner.use({ "model-b" }, ::create) { it })
        assertEquals(3, loads)
    }

    @Test
    fun replacementWaitsForInferenceAndQueuedUseReadsCurrentModel() = runTest {
        val owner = SerializedResource<String, Resource> { it.closed = true }
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var model = "old"
        lateinit var first: Resource
        val inference = async {
            owner.use({ model }, { Resource() }) {
                first = it
                entered.complete(Unit)
                finish.await()
                assertFalse(it.closed)
            }
        }
        entered.await()
        val replace = async { owner.invalidate { model = "new" } }
        val next = async { owner.use({ model }, { key -> assertEquals("new", key); Resource() }) { it } }
        testScheduler.runCurrent()
        assertFalse(replace.isCompleted)
        assertFalse(next.isCompleted)
        finish.complete(Unit)
        inference.await()
        replace.await()
        assertTrue(first.closed)
        assertFalse(next.await().closed)
    }

    @Test
    fun failedInitializationCanBeRetriedAndCancellationReleasesLock() = runTest {
        val owner = SerializedResource<String, Resource> { it.closed = true }
        assertTrue(runCatching { owner.use({ "model" }, { error("load failed") }) { } }.isFailure)
        val resource = owner.use({ "model" }, { Resource() }) { it }
        assertTrue(runCatching { owner.use({ "model" }, { Resource() }) { throw CancellationException() } }.exceptionOrNull() is CancellationException)
        owner.invalidate { }
        assertTrue(resource.closed)
    }
}
