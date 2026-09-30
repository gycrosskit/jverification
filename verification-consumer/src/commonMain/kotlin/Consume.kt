import io.github.gycrosskit.jverification.*

suspend fun verify(client: JVerificationClient): VerificationStatus = client.prepare(false).status
fun redacted(token: String) = VerificationResult(VerificationStatus.TOKEN, 6000, token).toString()
