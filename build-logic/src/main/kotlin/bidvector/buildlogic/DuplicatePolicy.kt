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

    /**
     * `mode=fail`일 때 **실패로 셀 수 있는** source set 이름(운영자 결정 2026-09-06, D-6
     * `limit.type.members.source-sets`와 같은 이름 관례). 기본값이 없다 — 키 부재는 정책
     * 오류이지 「전부 관찰」로 조용히 넓어지지 않는다.
     */
    val failSourceSets: List<String> get() = values.requireList("fail.source-sets")

    companion object {
        fun load(file: File): DuplicatePolicy = DuplicatePolicy(readPolicy(file))
    }
}
