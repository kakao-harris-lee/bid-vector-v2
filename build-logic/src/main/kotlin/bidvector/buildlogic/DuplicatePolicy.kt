package bidvector.buildlogic

import java.io.File

/** `duplicate-policy.properties`의 값 해석 — CPD 확장·task 배선의 유일한 정본 읽기 지점. */
internal class DuplicatePolicy(
    private val values: Map<String, String>,
) {
    val language: String get() = values.requireValue("language")
    val minimumTokenCount: Int get() = values.requireInt("minimumTokenCount")
    val toolVersion: String get() = values.requireValue("toolVersion")

    /** `mode=observe`만 관찰 모드다 — 그 밖의 값은 실패 모드로 읽는다(닫히는 쪽으로 튼다). */
    val ignoreFailures: Boolean get() = values.requireValue("mode") == "observe"

    companion object {
        fun load(file: File): DuplicatePolicy = DuplicatePolicy(readPolicy(file))
    }
}
