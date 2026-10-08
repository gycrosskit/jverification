package android.app

open class Activity {
    val applicationContext: Activity get() = this
    var isFinishing = false
    var isDestroyed = false
}
