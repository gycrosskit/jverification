package android.os

class Looper private constructor() {
    companion object {
        private val main = Looper()
        private val queue = ArrayDeque<() -> Unit>()
        fun getMainLooper(): Looper = main
        fun myLooper(): Looper = main
        fun post(action: () -> Unit) { queue.addLast(action) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst()() }
    }
}
class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper) {
    fun post(action: () -> Unit) { Looper.post(action) }
}
