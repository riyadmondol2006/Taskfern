package com.riyadmondol2006.taskfern.widget

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Collects "kind changed" events and flushes them together once things go quiet: each event restarts a [quietMillis]
 * timer, but a flush never waits more than [maxWaitMillis] after the first pending event, so a steady stream of
 * writes (an import, a bulk edit) still refreshes regularly. The flush runs in its own coroutine, so a new event
 * never cancels an update that is already being drawn.
 */
class UpdateCoalescer(
    private val scope: CoroutineScope,
    private val quietMillis: Long = 300,
    private val maxWaitMillis: Long = 1_500,
    private val now: () -> Long = System::currentTimeMillis,
    private val flush: suspend (Set<String>) -> Unit,
) {
    private val lock = Any()
    private val pending = linkedSetOf<String>()
    private var firstPendingAt = 0L
    private var timer: Job? = null

    fun submit(kinds: Collection<String>) {
        if (kinds.isEmpty()) return
        synchronized(lock) {
            if (pending.isEmpty()) firstPendingAt = now()
            pending += kinds
            timer?.cancel()
            val waited = now() - firstPendingAt
            val wait = minOf(quietMillis, (maxWaitMillis - waited).coerceAtLeast(0))
            timer = scope.launch {
                delay(wait)
                val batch = take() ?: return@launch
                scope.launch { flush(batch) }
            }
        }
    }

    /** Drops pending [kinds] because the caller is refreshing them right now. */
    fun discard(kinds: Collection<String>) {
        synchronized(lock) {
            pending -= kinds.toSet()
            if (pending.isEmpty()) {
                timer?.cancel()
                timer = null
            }
        }
    }

    private fun take(): Set<String>? = synchronized(lock) {
        if (pending.isEmpty()) return null
        val batch = pending.toSet()
        pending.clear()
        timer = null
        batch
    }
}
