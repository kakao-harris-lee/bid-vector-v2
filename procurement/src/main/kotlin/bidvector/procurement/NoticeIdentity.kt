package bidvector.procurement

import java.time.Instant

/**
 * 관측에서 **공고 식별자만** 세운다(M6/6G D-6G-11) — 표본틀을 만드는 수집 갈래가 쓴다. 세우지 못하면
 * `null` 이다.
 *
 * **이 함수가 따로 있는 이유**는 호출부가 원문 키를 이름 붙이지 않게 하기 위해서다. 표본틀은 목록 응답
 * 에서 (공고번호, 차수)만 필요한데, 그 값을 얻으려고 use case 가 `RawKey`·계약 레지스트리를 이름 붙이면
 * 「수집 use case 는 원문 필드를 직접 읽지 않는다」는 구조 게이트가 무너진다. 키를 고르는 일은 계약을
 * 소유한 이 모듈 안에 남고, 밖으로 나가는 것은 canonical 값뿐이다.
 *
 * [canonicalize] 를 대신 부르지 않는 이유: 그것은 **공고 목록 축** 항목을 겨눈 변환이라 개찰 축 응답에
 * 걸면 식별자 밖의 슬롯 판정까지 함께 돌아 이 자리에 필요 없는 탈락을 만든다.
 *
 * `Canonicalize.kt` 에서 분리한 파일이다(detekt 파일당 함수 상한, `KonepsTruncationCause.kt` 분리와 같은
 * 전례) — 관심사가 새로 생긴 것은 아니다.
 */
fun noticeIdIn(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
): NoticeId? = (resolvedNoticeId(observation, policy.fieldContracts) as? IdentityOutcome.Resolved)?.id

/**
 * 관측에서 **개찰 일시만** 세운다(M6/G1-C03) — 조회 가치 술어의 age-gate 앵커다. [noticeIdIn] 과 같은
 * 자리에 같은 이유로 있다: 표본틀이 원문 키·계약 레지스트리를 이름 붙이면 「수집 use case 는 원문 필드를
 * 직접 읽지 않는다」는 구조 게이트가 무너진다. 키를 고르는 일은 계약을 소유한 이 모듈에 남는다.
 *
 * **부재와 해석 실패를 한 값(`null`)으로 접는다.** 둘 다 「이 공고의 개찰 시각을 모른다」이고 게이트에는
 * 같은 처분이다 — 가르면 호출부에 같은 분기가 두 벌 생기고, 그 둘을 다르게 다루는 길이 열린다(한쪽만
 * 조용히 조회하는 길이 그것이다). 항목 단위 탈락 사유로 그 둘을 가르는 [instantFrom] 은 그대로 둔다.
 */
fun openingInstantIn(
    observation: RawNoticeObservation,
    policy: KonepsCollectionPolicyData,
): Instant? =
    (instantFrom(observation, policy, FieldConcept.OPENING_SCHEDULED_AT) as? InstantResolutionOutcome.Resolved)
        ?.instant
