import android.app.Activity
import cn.jiguang.verifysdk.api.JVerifyUIConfig
import io.github.gycrosskit.jverification.AndroidJVerificationDriver

fun driver(activity: Activity, key: String, ui: () -> JVerifyUIConfig) = AndroidJVerificationDriver(activity, key, ui)
