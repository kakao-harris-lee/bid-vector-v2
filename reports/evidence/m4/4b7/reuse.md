# M4/4B-7 재활용 — N/A 사유

이 slice는 Kotlin 서비스 층(workflow·adapters) 구현이다. 「V2 재활용 우선 방침」(운영자 지시
2026-08-22)은 **Python ML engine**(성숙한 기존 bid-vector ML 코드 — LightGBM/KDE, training/
serving)의 재활용을 대상으로 하며 M5(ml-implementer) 소관이다. 이 slice는 ml-engine 소스를
건드리지 않는다(scope.md 레인 절 — "Python 레인과 소스 겹침 0") — 그래서 Python 재활용
판단은 **N/A**다.

## 대신 실측한 것 — Kotlin 내부 재활용(중복 금지 원칙의 적용)

바퀴 재발명 금지 원칙은 Python 대상이 아니어도 유효하다 — 이 slice가 실제로 재활용한 기존
V2 Kotlin 자산:

- `JdbcNoticeRepository.find`·`JdbcOpeningResultRepository.find`(기존 ResultSet 복원 경로) —
  `JdbcCompetitionSampleSource`가 손으로 다시 짜지 않고 그대로 호출한다.
- `Money.toProtoMoney()`(`MoneyMapping.kt`) — `ReserveDrawObservation.reservePrices`(`List<BaseAmount>`)
  → proto `repeated Money` 변환에 재사용한다(새 Money 변환 함수를 만들지 않았다).
- `ProvenanceRules.judgeRow`(1D 커널, `decision`) — D-4B7-8이 두 번째 분류기를 만들지 않고
  기존 first-match 커널을 그대로 부른다.
- `OpportunityAnalysis`의 기존 `Step`/`andThen` guard 체인 관례 — `SampleEligibility.kt`의
  `Check`/`andThen`이 같은 처방을 새 자리에 반복한다(패턴 재사용, 코드 재사용은 아니다 —
  타입이 다르다).
- `PersistenceTestSupport`(Testcontainers 하네스) — `JdbcCompetitionSampleSourceTest`가
  새 컨테이너·새 하네스를 만들지 않고 기존 것을 그대로 상속한다.

## 조사했으나 채택하지 않은 것

- `decision.ProvenancePolicyData`의 운영 정본 — 존재하지 않는다(M1/1D는 타입만 세우고 값
  채택을 미뤘다). 새로 만들지 않고 legacy 값을 그대로 옮긴 잠정 인스턴스를 `workflow`
  쪽에 두었다(`policy-values.md` §3, `SAMPLE_PROVENANCE_POLICY`) — `decision` 모듈은
  in_scope 밖이라 손대지 않았다.
