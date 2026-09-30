package bidvector.procurement

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
