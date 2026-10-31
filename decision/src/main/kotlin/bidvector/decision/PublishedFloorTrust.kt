package bidvector.decision

import bidvector.sharedkernel.EffectiveDatedPolicy
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.FloorRate
import bidvector.sharedkernel.FloorRateOrigin
import bidvector.sharedkernel.Rate
import bidvector.sharedkernel.Resolution
import java.math.BigDecimal

/**
 * 게시 하한율 신뢰 정책(DEC-10, 운영자 결정 2026-10-24 — D-DEC10-1). 밴드는 기존
 * [PlausibilityBand] 를 재사용한다 — `FloorOverrideValidation`(운영자 override 축)과 **같은
 * 타입이고 다른 정책 슬롯**이다. 두 축을 한 값으로 합치지 않는다(`data-dictionary.md` §1.4.3
 * 의 밴드 인벤토리가 축이 같고 값이 다른 밴드를 셋이나 세며 통합을 금지하는 것과 같은 규율).
 */
data class PublishedFloorTrustPolicyData(
    val plausibilityBand: PlausibilityBand,
)

/**
 * 출하 정본 — 밴드 **[0.30, 0.995] 양 끝 포함**. 값의 소유는 이 slice 다(D-DEC10-1):
 * `OPEN-DEC-10` 은 DEC-03 법정 하한율 표 값 쪽에만 남는다.
 *
 * 근거는 실데이터다(`data-dictionary.md` §1.4.3 **B6** · `capability-map.md` DEC-10 분류 근거) —
 * 상한을 1 미만으로 두는 이유는 하한율 `1.00000` 이 라이브 47건(열린 공고 32건) 실재하며 「예정가
 * 전액 이상 투찰」이라 하한이 성립하지 않기 때문이고, 하한을 더 올리지 못하는 이유는 0.5 미만
 * 값 3건이 표기 형태상 진짜 게시값과 구분되지 않기 때문이다.
 *
 * Python 백테스트(`ml-engine/policy/strategy-backtest-v1.yaml` 의 `floor.rate_band_*`)와 **값이
 * 같아야 한다** — 그 등식은 `app` 의 값 일치 test 가 잰다(사전 등록 실험이라 Python 쪽 사유
 * 합침은 바꾸지 않는다).
 */
val PUBLISHED_FLOOR_TRUST_POLICY: EffectiveDatedPolicy<PublishedFloorTrustPolicyData> =
    EffectiveDatedPolicy(
        source =
            "M6/G1-DEC10 — 개연 밴드 [0.30, 0.995] 양 끝 포함" +
                "(운영자 결정 2026-10-24, D-DEC10-1), 근거는 data-dictionary §1.4.3 B6",
        entries =
            listOf(
                EffectiveFrom.Initial to
                    PublishedFloorTrustPolicyData(
                        PlausibilityBand(BigDecimal("0.30"), BigDecimal("0.995")),
                    ),
            ),
    )

/**
 * 게이트를 통과한 게시 하한율 — **원값과 다른 타입이다**(DEC-10 무조건 acceptance 넷째
 * 「게이트가 걸린 값과 안 걸린 값이 다른 이름·다른 타입이다」).
 *
 * 생성 경로는 [trustPublishedFloorRate] **하나**다 — `internal` 생성자 +
 * `@ConsistentCopyVisibility`(`Rate`·`AppliedFloor`·`FloorShortfall.Measured` 와 같은 처방).
 * `copy` 도 그 가시성을 따라가므로 「근거는 그대로 두고 값만 갈아끼우는」 길이 없다.
 *
 * 공개 읽기는 [rate] **하나**다 — 원값([value])은 `internal` 이라 모듈 밖에서는 게이트 통과
 * 값에서 원값 `FloorRate` 를 되꺼낼 수 없다. 그래서 가격 경로가 이 모듈 밖에 있을 때
 * (평가 margin) 원값을 손에 쥘 자리가 아예 없다. 같은 모듈의 DEC-04 커널은 shared-kernel 의
 * 산술에 넘기려고 [value] 를 읽고, **그 한 쌍이 허용 목록의 가격 쪽 전부**다.
 */
@ConsistentCopyVisibility
data class PlausibleFloorRate internal constructor(
    internal val value: FloorRate,
) {
    val rate: Rate get() = value.rate
}

/**
 * 게이트 판정(D-DEC10-2) — 네 가지이고 `null` 이 없다. 소비자는 소진 `when` 으로만 마주친다.
 *
 * [Trusted] 만 `internal` 생성자다(`FloorOverrideOutcome.Accepted` 관례 — 「이 값은 밴드 안이라
 * 게이트를 통과했다」는 주장은 이 함수를 거쳐야만 낼 수 있다). 나머지는 **사유 있는 실패**라
 * 공개다 — 어디서든 만들 수 있고, 가격이 서지 않는 값이라 위조할 것이 없다.
 *
 * **값을 고치지 않는다**(`published_floor_rate.py` 승계) — [Implausible] 은 원값과 밴드를 그대로
 * 나른다. 접어 올리거나 밴드 끝으로 클램프하지 않는다.
 */
sealed interface PublishedFloorTrust {
    @ConsistentCopyVisibility
    data class Trusted internal constructor(
        val rate: PlausibleFloorRate,
    ) : PublishedFloorTrust

    /** 게시 하한율이 없는 공고는 실재한다 — 실패가 아니라 **출처 부재**다. */
    data object Absent : PublishedFloorTrust

    data class Implausible(
        val rate: FloorRate,
        val band: PlausibilityBand,
    ) : PublishedFloorTrust

    /**
     * 이 게이트가 **심판이 아닌 값** — `FloorRateOrigin.StatutoryTable`(법정 하한율 표)이다.
     * 통과도 개연 불가도 아니다: B6 신뢰 밴드는 게시값에만 걸리고(`data-dictionary.md` §1.4.3)
     * 표 값은 `effectiveFrom` 으로 고른다. 오늘 그 값의 생성 자리는 0 이고, DEC-03 해석기가
     * 생기는 날 **그 slice 가 자기 판정을 들어야 한다**는 사실이 이 가지로 남는다. 가격 경로는
     * 통과 값만 받으므로 이 값으로는 가격이 서지 않는다(설계 검토 우회 7 — origin 위장).
     */
    data class OriginNotJudged(
        val rate: FloorRate,
    ) : PublishedFloorTrust
}

/**
 * 가격 경로가 게시값을 **제외한** 사유(계수 어휘, D-DEC10-5). 열거형인 이유는 집계가 이
 * 집합을 키로 세기 때문이다 — 사유가 늘면 집계와 로그 줄의 소진 `when` 이 함께 깨진다.
 *
 * 부재([PublishedFloorTrust.Absent])는 **제외가 아니다** — 공고에 값이 없는 것은 원문 사실이고
 * 게이트가 걸러낸 것이 아니다. 둘을 한 계수로 합치면 원문 품질을 관측할 수 없다.
 */
enum class PublishedFloorExclusion {
    IMPLAUSIBLE,
    ORIGIN_NOT_JUDGED,
}

/** 판정에서 계수 사유를 읽는다 — 통과·부재는 제외가 아니라 `null` 이다. */
fun PublishedFloorTrust.exclusion(): PublishedFloorExclusion? =
    when (this) {
        is PublishedFloorTrust.Trusted -> null
        PublishedFloorTrust.Absent -> null
        is PublishedFloorTrust.Implausible -> PublishedFloorExclusion.IMPLAUSIBLE
        is PublishedFloorTrust.OriginNotJudged -> PublishedFloorExclusion.ORIGIN_NOT_JUDGED
    }

/**
 * DEC-10 게이트 — **[PlausibleFloorRate] 의 유일한 생성 경로**다. 밴드를 스스로 짓지 않는다
 * (정책을 인자로 받는다, `FloorOverrideValidation` 과 같은 규율).
 *
 * origin 분기는 소진 `when` 이다 — `FloorRateOrigin` 에 세 번째 variant 가 생기면 이 함수가
 * 컴파일되지 않는다. 「모르는 출처를 통과로 접는」 `else` 가 없다.
 */
fun trustPublishedFloorRate(
    rate: FloorRate?,
    policy: Resolution.Resolved<PublishedFloorTrustPolicyData>,
): PublishedFloorTrust {
    if (rate == null) return PublishedFloorTrust.Absent
    return when (rate.origin) {
        is FloorRateOrigin.NoticeValue -> judgedAgainstBand(rate, policy.value.plausibilityBand)
        is FloorRateOrigin.StatutoryTable -> PublishedFloorTrust.OriginNotJudged(rate)
    }
}

/** 경계는 양 끝 포함이고 비교는 `BigDecimal` 의 값 비교다 — 척도(`0.30` vs `0.3000`)에 둔감하다. */
private fun judgedAgainstBand(
    rate: FloorRate,
    band: PlausibilityBand,
): PublishedFloorTrust =
    if (band.contains(rate.rate.fraction)) {
        PublishedFloorTrust.Trusted(PlausibleFloorRate(rate))
    } else {
        PublishedFloorTrust.Implausible(rate, band)
    }
