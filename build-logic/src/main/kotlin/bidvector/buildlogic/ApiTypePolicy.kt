package bidvector.buildlogic

import java.io.File

/**
 * public domain API 타입 표면의 금지 목록 — `config/quality/api-type-policy.properties` 의
 * `api.forbidden.types` 하나를 읽는다. `architecture-policy.properties` 와는 다른 축이라
 * 별도 파일이다(설계 검토 부록 §1 「정책의 자리」) — 경계 목록과 이 목록은 서로 다른 판을 센다.
 */
internal class ApiTypePolicy private constructor(
    private val forbidden: Set<String>,
) {
    /**
     * [name] 이 금지 목록과 겹치면 그 엔트리를 돌려준다. 완전수식이면 정확히 대조하고,
     * 수식 없는 단순 이름이면 어떤 엔트리의 마지막 세그먼트와 같은지 본다(`Double` →
     * `kotlin.Double`) — 단순 이름은 별도 키로 적지 않고 엔트리에서 유도한다.
     */
    fun forbids(name: String): String? =
        when {
            name in forbidden -> name
            '.' !in name -> forbidden.firstOrNull { it.substringAfterLast('.') == name }
            else -> null
        }

    companion object {
        fun load(file: File): ApiTypePolicy = ApiTypePolicy(readPolicy(file).requireList("api.forbidden.types").toSet())
    }
}
