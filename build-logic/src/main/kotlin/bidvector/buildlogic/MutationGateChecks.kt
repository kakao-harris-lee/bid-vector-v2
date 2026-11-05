package bidvector.buildlogic

import java.io.File

/**
 * 「돌았는가」와 「통과했는가」는 다른 말이다 — 리포트의 부재·빈 파일을 먼저 가른다.
 * `cpdReportPresenceGate` 와 같은 축이고, 거기서처럼 이 사유는 **죽은 가지가 아니다**:
 * 리포트 경로를 `@InputFile` 로 받지 않기 때문에 실제로 여기까지 온다.
 */
private fun mutationReportPresenceViolation(
    moduleName: String,
    report: File,
): String? =
    when {
        !report.exists() -> "[$moduleName] mutation 리포트가 존재하지 않는다: ${report.path}"
        report.length() == 0L -> "[$moduleName] mutation 리포트가 비어 있다: ${report.path}"
        else -> null
    }

/**
 * mutation 래칫의 판정 전부 — Gradle·pitest 없이 값으로 선다.
 *
 * **세 축을 함께 재는 이유**: 축 하나만으로는 「대상을 줄여 점수를 유지하는」 경로가 열린다.
 * 점수만 보면 `targetClasses` 를 좁히거나 `excludedClasses` 를 더하는 것이 통과이고, 변이
 * 수만 보면 테스트를 지우는 것이 통과다. 넷째 단언(리포트의 클래스 ⊆ 그 모듈 main 산출물)은
 * 남의 모듈 산출물을 `mutableCodePaths` 로 끌어와 쉬운 변이로 점수를 올리는 경로를 닫는다.
 *
 * **pitest 자신의 `mutationThreshold` 는 쓰지 않는다.** 같은 수가 두 자리에 있으면 안 되고
 * (`sizeGate` 가 detekt `LongMethod` 를 비활성화한 자리와 같은 이유), 도구 옵션 하나를 0 으로
 * 돌리는 것이 게이트를 끄는 길이 되어서도 안 된다 — 판정은 리포트에서 우리가 재계산한다.
 */
internal fun mutationGateViolations(
    moduleName: String,
    report: File,
    ratchet: MutationRatchet,
    compiledClasses: Set<String>,
): List<String> {
    mutationReportPresenceViolation(moduleName, report)?.let { return listOf(it) }
    val parsed = MutationReport.parse(report)
    val foreign = parsed.mutatedClasses - compiledClasses
    return listOfNotNull(
        // 산출물이 비어 있는 상태는 「잴 것이 없다」가 아니라 배선이 깨진 것이다 — 통과로 두면
        // 산출물을 치우는 것이 게이트를 끄는 길이 된다.
        "[$moduleName] 변이 대상이 될 main 산출물이 없다 — 배선이 끊겼다"
            .takeIf { compiledClasses.isEmpty() },
        // 0/0 을 「전부 탐지」로 접지 않는다. 변이 0 은 이 축에서 먼저 걸린다.
        "[$moduleName] 변이 수 ${parsed.total} 이 래칫 ${ratchet.minMutations} 밑이다 — 변이 대상이 줄었다"
            .takeIf { parsed.total < ratchet.minMutations },
        // 정확한 정수 비교다 — 백분율로 내림하면 1점 미만의 하락이 가려진다.
        (
            "[$moduleName] mutation 점수 ${parsed.detected}/${parsed.total}(${parsed.scorePercent}%) 가 " +
                "래칫 ${ratchet.minScorePercent}% 밑이다"
        ).takeIf { parsed.detected * PERCENT_DENOMINATOR < ratchet.minScorePercent * parsed.total },
        "[$moduleName] 변이가 닿은 클래스 수 ${parsed.mutatedClasses.size} 가 래칫 ${ratchet.minMutatedClasses} 밑이다"
            .takeIf { parsed.mutatedClasses.size < ratchet.minMutatedClasses },
        "[$moduleName] 리포트가 이 모듈 main 산출물 밖의 클래스를 담았다: ${foreign.sorted()}"
            .takeIf { foreign.isNotEmpty() },
    )
}

/**
 * 「정책이 대상이라 한 모듈」과 「실제로 pitest convention 을 적용한 모듈」의 **양방향** 등식,
 * 그리고 그 집합이 **순수 도메인 안에 있는지**.
 *
 * 양방향인 이유: 한 방향만 재면 모듈에서 convention 적용을 빼는 것이 조용한 면제가 된다 —
 * task 가 사라지면 실패할 것도 없기 때문이다(`conventionCoverageGate` 와 같은 사유).
 *
 * 셋째 축(`layer.domain` 교차)은 **문면을 한 걸음으로 만든다.** 그것이 없으면 도메인 밖 모듈을
 * 정책에 적었을 때 이 게이트가 「convention 을 적용하지 않았다」로 붉고, 그 문면을 따라 적용하면
 * 구성 단계에서 **다른** 예외가 난다 — 두 걸음이라 읽는 사람이 엉뚱한 고침을 한다. 구성 단계
 * 차단(convention 의 `layer.domain` 가드)이 여전히 1차이고 이 축은 정책 쪽 오기를 집는다.
 */
internal fun mutationModuleCoverageViolations(
    expected: Set<String>,
    registered: Set<String>,
    domainModules: Set<String>,
): List<String> {
    val nonDomain = expected - domainModules
    // 도메인 밖 모듈은 convention 을 **적용할 수 없다**(구성 단계 가드가 던진다). 그러니 그
    // 모듈을 「적용하지 않았다」 축에도 세우면 같은 하나의 오기가 **두 줄**로 나오고, 먼저 읽히는
    // 줄이 엉뚱한 고침(적용해 보기)을 유도한다 — 그것이 이 축을 더한 이유 그 자체다.
    val missing = expected - registered - nonDomain
    val unexpected = registered - expected
    return listOfNotNull(
        (
            "정책이 순수 도메인 밖 모듈을 대상으로 들었다: ${nonDomain.sorted()} — " +
                "mutation 대상은 layer.domain 안에서만 고른다(ADR 0007 OPEN-ADR-07 D-1)"
        ).takeIf { nonDomain.isNotEmpty() },
        "정책이 대상으로 든 모듈이 pitest convention 을 적용하지 않았다: ${missing.sorted()}"
            .takeIf { missing.isNotEmpty() },
        "정책 밖 모듈이 pitest convention 을 적용했다: ${unexpected.sorted()}"
            .takeIf { unexpected.isNotEmpty() },
    )
}
