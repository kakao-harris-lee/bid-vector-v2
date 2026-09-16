# M4/4B-8 재활용 — N/A 사유(Python) + Kotlin 내부 재활용 실측

## Python ML engine 재활용 — N/A

「V2 재활용 우선 방침」(운영자 지시 2026-08-22)은 Python ML engine(M5, ml-implementer 소관)
대상이다. 이 slice는 `procurement`·`workflow`·`adapters`(Kotlin)만 만지고 `ml-engine/`
소스를 건드리지 않는다(scope.md in_scope — Python 없음) — Python 재활용 판단은 4B-7과 같은
이유로 **N/A**다.

## Kotlin 내부 재활용(중복 금지 원칙의 적용)

- **`ProvenanceRules.judgeRow`(1D 커널, `decision`)** — D-4B8-1이 두 번째 분류기를 만들지
  않고 4B-7이 이미 쓰는 first-match 커널을 대상 공고에도 그대로 부른다. 새 규칙표·새
  판정 함수 0건.
- **`provenanceLabelFor`(`SampleConversion.kt`, 4B-7 신설)** — 대상용 별도 함수를 새로
  만들지 않고 `opening` 인자를 `OpeningResult?`로 일반화해 표본·대상이 같은 함수를
  공유한다(D-4B8-2). "표본 라벨 함수"와 "대상 라벨 함수" 두 벌을 두는 안(설계 검토가
  「열거형(피함)」으로 명시한 경로)을 채택하지 않았다.
- **`resolvedOrNull`(`OpportunityAnalysis.kt`, 기존 제네릭 helper)** — `SAMPLE_PROVENANCE_POLICY`
  resolve에 새 함수를 쓰지 않고, `opportunity`·`derivation`·`priority` 셋이 이미 쓰는
  같은 helper를 그대로 재사용한다(네 번째 호출).
- **`@ConsistentCopyVisibility` + `private constructor` + 단일 팩토리 관례**
  (`shared-kernel/Money.kt`의 `BidAmount`·`Rate.kt`의 `Rate`) — `CategoryCode`가 같은
  처방을 그대로 적용한다. 새 불변식 강제 패턴을 만들지 않았다.
- **`OpportunityAnalysisFixtures.kt`의 `testNoticeWithMoney`(4B-7이 이미 확장해 둔 fixture)**
  — 새 notice 빌더를 만들지 않고 `estimatedAmountWon` 선택 인자 하나를 추가해 SuspectRatio
  규칙표 test가 그 값을 쓴다(기본값 `null`은 기존 fixture와 바이트 동일 — 4B-7이
  `businessCategory` 인자를 추가할 때 쓴 것과 같은 확장 관례).
- **`SampleEligibilityTest`의 `testNotice`/`testOpening` 관례** — `JdbcCompetitionSampleSourceTest`의
  `seedNotice`/`query` 헬퍼가 이미 원문 문자열을 받아 도메인 값으로 조립하는 형태였으므로,
  정규화 test는 그 헬퍼에 선택 인자(`categoryCode`)만 추가해 재사용했다(새 헬퍼 0건).

## 조사했으나 채택하지 않은 것

- **대상 전용 라벨 함수 신설** — D-4B8-1이 명시적으로 금지(「두 번째 분류기 금지」). 설계
  검토 (1) 표가 이미 기각.
- **`CategoryCode` 정규화를 `String` 확장 함수로 두고 도메인 타입 밖에서 쓰게 하는 안** —
  우회 (11) 방어(문자열은 권한이 없다)와 충돌해 기각. `normalizeCategoryKey`는 public이지만
  포트 인자 자리는 여전히 `CategoryCode` 타입만 받는다.
- **`ResolvedPolicies`를 쪼개 `provenancePolicy`만 별도 파라미터로 넘기는 안**(값 객체에
  안 담고 함수 인자로 직접 전달) — 기존 세 정책과 같은 자리(같은 data class 필드)에 두는
  것이 D-4B8-5의 "같은 singleton·같은 조립 지점" 취지에 더 맞아 채택하지 않았다.
