package bidvector.archfixture.violating.collection

/**
 * D-6F9-1 우회 1 위반 표본(verifier r1 F-1 재현 MV2)의 **대상 타입** — 실제 재현은 production `BusinessDivision` 의
 * companion 에 파생 함수를 더한 것이었다. production 타입에 멤버를 심을 수는 없으므로 같은 모양의 enum 을 fixture 로
 * 두고 **같은 규칙 값**(허용 집합 비움)을 이 타입에 적용한다. 열거된 멤버 목록으로는 [ofOperationPath] 가 시야 밖이고
 * 그 안의 `entries` 순회는 타입 자신이라 제외된다 — 그래서 이전 판이 조용했다.
 */
enum class RogueDivisionLikeEnum(
    val label: String,
) {
    ROGUE_CONSTRUCTION("공사"),
    ROGUE_SERVICE("용역"),
    ;

    companion object {
        fun ofOperationPath(operationPath: String): RogueDivisionLikeEnum? =
            entries.firstOrNull { operationPath.endsWith(it.name) }
    }
}

/** 위 파생을 바깥에서 부르는 자리 — 값 획득 축이 보는 것은 이 호출자다. */
class RogueDivisionFromCompanionDerivation {
    fun divisionOf(operationPath: String): RogueDivisionLikeEnum? = RogueDivisionLikeEnum.ofOperationPath(operationPath)
}
