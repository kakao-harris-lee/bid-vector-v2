package bidvector.buildlogic

import java.io.File

/**
 * 비율을 백분율로 옮기는 분모. 정책 값이 아니라 「퍼센트」의 정의라 정책 데이터에 두지 않는다.
 *
 * **`PERCENT_SCALE…` 로 부르지 않는다** — legacy 의 `PERCENT_SCALE_THRESHOLD` 는 값 크기로
 * 단위를 되짚는 **금지 경로**의 이름이고(ADR 0002 D-4 · regression ledger), 그 이름을 닮으면
 * 금지 경로를 찾는 grep 이 이 상수를 집는다.
 */
internal const val PERCENT_DENOMINATOR = 100

/** 한 모듈의 mutation 래칫 — 세 축 전부 **실측값**이고, 내리는 것은 명시적 정책 변경이다. */
internal data class MutationRatchet(
    val minScorePercent: Int,
    val minMutations: Int,
    val minMutatedClasses: Int,
)

/**
 * `mutation-policy.properties` 의 값 해석 — pitest 배선과 래칫 게이트의 유일한 정본 읽기 지점.
 *
 * **도구 버전은 이 파일에 없다.** `gradle/libs.versions.toml` 이 든다 — 같은 수를 두 자리에
 * 두지 않는다. 여기 있는 것은 카탈로그가 가질 수 없는 값(대상 모듈 집합·래칫·실행 폭)뿐이다.
 */
internal class MutationPolicy(
    private val values: Map<String, String>,
) {
    /**
     * pitest 를 켜는 모듈. **빈 값은 「대상 없음」이 아니라 정책 오류다** —
     * 비면 `mutationModuleCoverageViolations` 의 양변이 함께 비어 전 모듈이 빠진 상태가
     * 침묵으로 통과한다(`DuplicatePolicy.failSourceSets` 가 막은 것과 같은 틈).
     */
    val modules: List<String>
        get() =
            values.requireList("mutation.modules").also {
                require(it.isNotEmpty()) { "정책 키 'mutation.modules' 의 값이 비어 있다 — 최소 하나의 모듈이 필요하다" }
            }

    /**
     * 정책 **판**. 게이트 리포트가 이 값을 함께 적어 「어느 판이 판정했는가」가 남는다
     * (`SizeGateTask` 관례). 읽는 쪽이 없으면 형식이 바뀌는 날 조용히 지나간다 — `requireValue`
     * 라 키가 사라지면 그 자리에서 깨진다.
     */
    val version: String get() = values.requireValue("policy.version")

    val threads: Int get() = values.requireInt("threads")

    /** 판정은 `mutations.xml` 로만 선다 — XML 을 끄는 것은 게이트를 끄는 것과 같다. */
    val outputFormats: List<String>
        get() =
            values.requireList("output.formats").also {
                require(it.contains("XML")) { "정책 키 'output.formats' 에 XML 이 없다 — 래칫 판정이 설 근거가 사라진다" }
            }

    /**
     * **세 축 모두 양수를 요구한다.** 0 은 키가 **있으므로** 「래칫 키가 없다」 검사를 통과하면서
     * 그 축을 무력화한다 — 세 축을 한꺼번에 0 으로 두면 게이트가 남는 방어는 diff 가시성 하나다.
     * 실측에서 나온 값은 정의상 양수이므로 이 요구가 정당한 값을 막지 않는다.
     */
    fun ratchet(module: String): MutationRatchet {
        val score = requireRatchetInt(module, "score.min-percent")
        require(score in 1..PERCENT_DENOMINATOR) {
            "정책 키 'ratchet.$module.score.min-percent' 의 값이 1..$PERCENT_DENOMINATOR 밖이다: $score"
        }
        return MutationRatchet(
            minScorePercent = score,
            minMutations = requirePositiveRatchetInt(module, "mutations.min"),
            minMutatedClasses = requirePositiveRatchetInt(module, "classes.min"),
        )
    }

    private fun requirePositiveRatchetInt(
        module: String,
        axis: String,
    ): Int =
        requireRatchetInt(module, axis).also {
            require(it > 0) { "정책 키 'ratchet.$module.$axis' 가 양수가 아니다: $it — 0 은 그 축을 무력화한다" }
        }

    /**
     * 래칫 키의 부재는 「임계 없음」이 아니라 조용한 면제다 — 모듈을 목록에 더하고 래칫을
     * 빼는 것이 가장 싼 우회이므로 거기서 멈춘다.
     */
    private fun requireRatchetInt(
        module: String,
        axis: String,
    ): Int {
        val key = "ratchet.$module.$axis"
        if (!values.containsKey(key)) {
            error("모듈 '$module' 이 mutation.modules 에 있으나 정책 키 '$key' 가 없다")
        }
        return values.requireInt(key)
    }

    companion object {
        fun load(file: File): MutationPolicy = MutationPolicy(readPolicy(file))
    }
}
