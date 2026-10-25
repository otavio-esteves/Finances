package br.com.otavioesteves.finances.ui.screens.home

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/** Keeps prefetched pages composed while suspending their visible-screen data collectors. */
internal class PageLifecycleOwner(
    private val parent: Lifecycle,
    private var active: Boolean
) : LifecycleOwner, LifecycleEventObserver {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    init { update() }

    fun setActive(value: Boolean) {
        active = value
        update()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) { update() }

    private fun update() {
        val parentState = parent.currentState
        registry.currentState = if (active || parentState < Lifecycle.State.CREATED) parentState
            else minOf(parentState, Lifecycle.State.CREATED)
    }

    fun dispose() {
        // LifecycleRegistry cannot jump directly from INITIALIZED to DESTROYED.
        if (registry.currentState == Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.CREATED
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
