import io.github.gycrosskit.jverification.*
import io.github.gycrosskit.jverification.kuikly.registerJVerificationModule
import android.app.Activity
import cn.jiguang.verifysdk.api.JVerifyUIConfig
fun registerReceiver(renderer: com.tencent.kuikly.core.render.android.IKuiklyRenderExport, activity: Activity, key: String, ui: () -> JVerifyUIConfig) {
    renderer.registerJVerificationModule { AndroidJVerificationDriver(activity, key, ui) }
}
