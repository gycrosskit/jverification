import io.github.gycrosskit.jverification.*

suspend fun verify(client: JVerificationClient): VerificationStatus = client.prepare(false).status
suspend fun authenticate(client: JVerificationClient, hideLoading: () -> Unit) = client.authenticate(true, opened = hideLoading)
fun redacted(token: String) = VerificationResult(VerificationStatus.TOKEN, 6000, token).toString()
