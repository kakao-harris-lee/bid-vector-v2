package bidvector.settlement

import bidvector.sharedkernel.Resolution
import java.time.LocalDate

/**
 * test 가 쓰는 기준일. 출하 정책은 `EffectiveFrom.Initial` 한 version 이라 어떤 날짜로도
 * 같은 값이 나온다 — 날짜 자체에 도메인 의미는 없고, 기준일 없이 정책을 적용하지 않는다는
 * `EffectiveDatedPolicy` 규율을 test 도 지키기 위한 값이다.
 */
private val TEST_REFERENCE_DATE: LocalDate = LocalDate.of(2026, 10, 21)

/** 출하 정책 인스턴스 — test 는 이것을 주입해 「출하 값 위에서」 잰다. */
internal fun shippedAwardMatchingPolicy(): AwardMatchingPolicyData {
    val resolution = AWARD_MATCHING_POLICY.resolve(TEST_REFERENCE_DATE)
    check(resolution is Resolution.Resolved) { "출하 상호 매칭 정책이 해석되지 않았다: $resolution" }
    return resolution.value
}

/** 모듈 안이라 `of` 를 부를 수 있다 — 모듈 밖 경로는 컴파일 실패 하네스가 따로 잰다. */
internal fun normalizedNameOf(
    value: String,
    policy: AwardMatchingPolicyData,
): NormalizedCompanyName {
    val normalization = normalizeCompanyName(value, policy)
    check(normalization is CompanyNameNormalization.Normalized) { "정규화 결과가 비었다" }
    return normalization.name
}
