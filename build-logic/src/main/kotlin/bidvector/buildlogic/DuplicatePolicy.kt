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
     * **Type-2 정규화**(`OPEN-CPD-GATE-RENAME-BYPASS`) — 중복 판정이 식별자·리터럴 **글자**에
     * 의존하지 않게 한다. verifier 실측: 축어 복사판은 `cpdCheck` exit 1 인데 **변수명만** 바꿔도 0,
     * **리터럴만** 바꿔도 0 이었다. 참/거짓만 받는다 — 알 수 없는 값을 거짓으로 읽으면 판정이 조용히
     * 앞 판으로 돌아간다. 판정식 자체를 바꾼 복제(Type-3)는 이 축이 닫지 못한다(알려진 한계).
     */
    val ignoreIdentifiers: Boolean get() = values.requireBoolean("normalize.identifiers")

    val ignoreLiterals: Boolean get() = values.requireBoolean("normalize.literals")

    /**
     * `mode=fail`일 때 **실패로 셀 수 있는** source set 이름(D-6
     * `limit.type.members.source-sets`와 같은 이름 관례). 기본값이 없다 — 키 부재는 정책
     * 오류이지 「전부 관찰」로 조용히 넓어지지 않는다. **빈 값도 같은 정책 오류다** —
     * `requireList`는 빈 문자열을 빈 리스트로 낼 뿐 거부하지 않아,
     * `fail.source-sets=`(값 없이 키만 남김)가 `cpdCheck`의 source 를 통째로 비우고
     * `expectedSource`도 같이 비워 프레즌스 게이트가 침묵으로 통과했다(전 모듈 실패 0).
     * 키 부재만 막던 문면의 틈을 여기서 마저 닫는다.
     */
    val failSourceSets: List<String>
        get() =
            values.requireList("fail.source-sets").also {
                require(it.isNotEmpty()) {
                    "정책 키 'fail.source-sets' 의 값이 비어 있다 — 최소 하나의 source set 이 필요하다"
                }
            }

    companion object {
        fun load(file: File): DuplicatePolicy = DuplicatePolicy(readPolicy(file))
    }
}
