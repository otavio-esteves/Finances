package br.com.otavioesteves.finances.ui.screens.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageLifecycleOwnerTest {
    @Test
    fun prefetchedPageOnlyCollectsWhenVisibleAndNeverOutlivesActivity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = object : LifecycleOwner {
                val registry = LifecycleRegistry(this)
                override val lifecycle: Lifecycle get() = registry
            }
            parent.registry.currentState = Lifecycle.State.RESUMED
            val page = PageLifecycleOwner(parent.lifecycle, active = false)
            parent.lifecycle.addObserver(page)
            assertEquals(Lifecycle.State.CREATED, page.lifecycle.currentState)
            page.setActive(true)
            assertEquals(Lifecycle.State.RESUMED, page.lifecycle.currentState)
            page.setActive(false)
            assertEquals(Lifecycle.State.CREATED, page.lifecycle.currentState)
            parent.registry.currentState = Lifecycle.State.CREATED
            page.setActive(true)
            assertEquals(Lifecycle.State.CREATED, page.lifecycle.currentState)
            parent.registry.currentState = Lifecycle.State.STARTED
            assertEquals(Lifecycle.State.STARTED, page.lifecycle.currentState)
            parent.registry.currentState = Lifecycle.State.DESTROYED
            assertEquals(Lifecycle.State.DESTROYED, page.lifecycle.currentState)
            parent.lifecycle.removeObserver(page)
            page.dispose()
        }
    }
}
