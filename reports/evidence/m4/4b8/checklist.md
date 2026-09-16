# M4/4B-8 checklist — 대상 공고 요청 축 라벨 + 공종 코드 정규화 정본

## 착수 실측(scope.md 「착수 조사 실측」)

- `git diff --name-only 6f9da09..845e29b | grep '\.kt$'` — 빈 출력(Kotlin 변경 0, 다른 레인
  merge와 겹침 없음).
- `grep -rn "CategoryCode(" --include='*.kt' .`(구현 전) — production 2(`Canonicalize.kt`·
  `NoticeReconstruction.kt`)·전체 27(패키지 미구분 원시 카운트, scope.md 표와 일치). 패키지로
  가른 실제 `bidvector.procurement.CategoryCode(` 직접 생성부는 이 둘뿐이고, 나머지는
  `bidvector.strategy.CategoryCode`(무관 타입, `strategy/Text.kt`)이거나 grep 우연 일치
  (`clearCategoryCode()`)다 — 이 구분이 D-4B8-3 구현 범위를 정했다.

## D-4B8-1~5 근거(함수·test 이름)

| 결정 | 구현 | test |
| --- | --- | --- |
| D-4B8-1 — 대상 라벨 = 표본과 같은 분류기(`ProvenanceRules.judgeRow`), `DerivedYega`는 개찰 부재로 구조적 불가 | `predictionRequestFor`(`PredictionFacts.kt`)가 `provenanceLabelFor(notice, opening = null, resolvedBaseAmount.amount, policies.provenancePolicy)`를 호출 — `BaseAmountProvenance.Unknown` 상수 삭제 | `PredictionFactsTest`「대상 라벨 — 추정가격이 없어 SuspectRatio는 불가하고 기초금액이 정수라 Clean」·「... SuspectRatio」·「... 기초금액이 0이면 ... Unknown」·「predictionRequestFor 라벨은 Unknown 상수가 아니다」 |
| D-4B8-2 — `provenanceLabelFor`의 `opening` 인자를 `OpeningResult?`로 일반화(표본은 non-null, 대상은 null) | `SampleConversion.kt` `provenanceLabelFor(notice, opening: OpeningResult?, original, policy)` — `winningAmount`/`winningRate`가 `opening?.` 로 nullable 전파 | `PredictionFactsTest`「대상 라벨 — DerivedYega는 opening이 있어야만 매치한다(변이)」(같은 데이터·같은 정책, `opening` 유무만 바꿔 `DerivedYega` ↔ `Clean` 대조) + 4B-7 `SampleEligibilityTest`(opening 비-null 기존 test 17건 전부 재통과 — `sampleOf` 회귀 없음) |
| D-4B8-3 — `CategoryCode`는 `private` 생성자(`@ConsistentCopyVisibility`) + `of(raw)` 단일 생성 경로, `normalizeCategoryKey(raw) = trim + 문자 단위 lowercase` | `BusinessCategory.kt` — `CategoryCode.of`, `normalizeCategoryKey` | `BusinessCategoryTest` 7건(strip+lower·이미 정규화됨·빈 문자열 거부·공백만 거부·전각 공백·순수 함수·`of`를 거쳐야 함) |
| D-4B8-4 — 정규화 적용 자리(수집 canonicalize 쓰기 + persistence 복원 읽기, SQL은 정확 일치 유지) | `Canonicalize.kt` `businessCategoryFrom`이 `CategoryCode.of(code)` · `NoticeReconstruction.kt` `businessCategoryOf`가 `CategoryCode.of(code)`(관용) · `JdbcCompetitionSampleSource`의 `SELECT_COMPETITION_SAMPLE_CANDIDATES` 무변경 | `JdbcCompetitionSampleSourceTest`「공종 코드가 대소문자·공백만 다르면 같은 표본으로 조회된다 — 정규화 일치(D-4B8-4)」(쓰기 `"A01"`·조회 `"  a01 "`로 정규화 후 일치 실증) + 기존 「공종이 다르면 후보에서 빠진다」(정확 일치 유지 회귀 없음) + `git diff`로 `SELECT_COMPETITION_SAMPLE_CANDIDATES` 문자열 무변경 확인(아래 rollback.md 목록 참고 — `JdbcCompetitionSampleSource.kt` 자체는 in_scope 밖) |
| D-4B8-5 — `OpportunityPolicyData`·정책 값 무변경, 라벨 정책은 4B-7 `SAMPLE_PROVENANCE_POLICY` singleton 재사용(대상·표본 같은 version) | `OpportunityAnalysis.ResolvedPolicies`에 `provenancePolicy: Resolution.Resolved<ProvenancePolicyData>` 필드 추가, `resolvePolicies`가 `SAMPLE_PROVENANCE_POLICY`를 다른 세 정책과 같은 자리에서 `resolvedOrNull`로 resolve | `PredictionFactsTest.testPolicies`가 `SAMPLE_PROVENANCE_POLICY.entries.single().second`를 직접 참조(같은 singleton) + `git diff -- workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleEligibility.kt`가 빈 출력(정책 값 자체 무변경 — `SAMPLE_PROVENANCE_POLICY` 선언부는 이 slice의 in_scope 밖) |

## 우회 (1)~(12) 대응표

| 우회 | 방어 | 실측 |
| --- | --- | --- |
| (1) 대상 공고를 `Clean`으로 위조 | `predictionRequestFor`가 라벨 상수를 대입하지 않고 `provenanceLabelFor` 호출 결과를 그대로 싣는다(공급 측 상수 없음 — 코드에 `BaseAmountProvenance.Unknown`·`.Clean` 등 리터럴이 없다) | `PredictionFactsTest`「predictionRequestFor 라벨은 Unknown 상수가 아니다」(같은 추정가격 없음 입력이면 `Clean`, SuspectRatio 조건이면 `SuspectRatio` — 입력에 따라 갈린다) |
| (2) `DerivedYega`를 개찰 없이 흉내 | `predictionRequestFor`가 `provenanceLabelFor`를 `opening = null`로만 호출 — 낙찰 입력을 실을 통로 자체가 시그니처에 없다 | `PredictionFactsTest`「DerivedYega는 opening이 있어야만 매치한다」— 정책을 `DerivedYega` 우선으로 바꿔도(운영 순서가 아니라 test 전용) 같은 행에서 opening 유무만으로 `DerivedYega`/`Clean`이 갈림(변이) |
| (3) `CategoryCode`를 정규화 안 된 값으로 직접 생성 | `private constructor` + `@ConsistentCopyVisibility`(`copy()`도 폐쇄) — 컴파일 거부. 이 gate는 컴파일 실패라 test로 상주하지 않는다(`gate.tests.procurement`의 `NoticeIdTest`/`NoticeRound` 폐쇄와 같은 관례) | `grep -rn "CategoryCode(" procurement adapters workflow app`(구현 후) — `bidvector.procurement.CategoryCode(` 직접 생성부는 `BusinessCategory.kt`의 `of()` 자신 하나뿐(companion object가 private 생성자에 접근하는 유일한 자리), 나머지 전부 `bidvector.strategy.CategoryCode`(무관 타입) |
| (4) SQL에서 `LOWER(TRIM())`로 두 번째 규칙 | 금지(설계 결정) — `JdbcCompetitionSampleSource.kt`는 이 slice의 in_scope 밖이라 손대지 않았다 | `git diff --stat`(아래 rollback.md 목록)에 `JdbcCompetitionSampleSource.kt`·`Sql.kt`가 없음 — SQL 문자열 순변경 0 |
| (5) 정규화 뒤 빈 키(`"  "`) | `of` 내부에서 정규화 후 `require(value.isNotBlank())`(기존 메시지 유지) | `BusinessCategoryTest`「빈 문자열은 거부된다」·「공백만 있는 문자열은 정규화 뒤 빈 키라 거부된다」 |
| (6) wire에 정규화 전 값을 실음 | `CategoryCode`는 `of()`로만 생성되므로 인스턴스 자체가 항상 정규화된 `value`를 가진다(불변식) — wire 코덱(`RequestMapping.kt`)은 이미 있는 `CategoryCode.value`를 읽기만 한다 | 컴파일 구조(생성 경로 유일성) — `RequestMapping.kt`는 이 slice의 in_scope 밖(무변경, `git diff`로 확인) |
| (7) 대소문자만 다른 두 공종이 하나로 접힘 | 설계 결정 — Python `normalize_feature_key`도 같은 규칙(별칭 없음)이라 Kotlin·Python이 이미 정합. 별도 방어를 두지 않는다 | `docs/discovery/data-dictionary.md` §6.3.1(팀장 기록, `dfaff1a`)·이 문서 「알려진 제한」 절 |
| (8) 라벨 정책 version이 표본과 대상에서 다름 | `OpportunityAnalysis.resolvePolicies`와 `JdbcCompetitionSampleSource.resolveProvenancePolicy` 둘 다 `SAMPLE_PROVENANCE_POLICY`(같은 top-level singleton)를 참조 — 새 인스턴스를 만들지 않는다 | `git diff -- workflow/src/main/kotlin/bidvector/workflow/evaluation/SampleEligibility.kt`(정책 선언 파일) 빈 출력 — singleton 자체 무변경. `PredictionFactsTest.testPolicies`가 그 singleton을 직접 참조 |
| (9) `NoticeReconstruction`이 정규화 전 저장 행을 읽어 `of`로 정규화하면 도메인 객체는 맞지만 SQL 조회는 그 행을 못 찾는다 | 백필하지 않는다(운영 데이터 없음) — `OPEN-4B8-CATEGORY-BACKFILL`로 명시 | 알려진 제한 절 |
| (10) test 픽스처가 대문자로 값을 만들고 저장에는 다른 형태를 쓰면 조회 불일치가 test에서 안 드러날 위험 | 모든 픽스처가 `of()`를 통해 만든 값을 저장·조회 양쪽에 그대로 쓴다(실제 쓰기 경로와 같은 함수) | `JdbcCompetitionSampleSourceTest`의 `seedNotice`·`query` 헬퍼가 원문 문자열을 받아 내부에서 `CategoryCode.of()`를 부른다(값을 미리 정규화해 넘기지 않는다) — 새 test가 대소문자·공백이 다른 두 원문("A01" 저장 / "  a01 " 조회)으로 이 경로를 직접 실증 |
| (11) `normalizeCategoryKey`를 다른 모듈이 임의 문자열에 써서 `CategoryCode` 없이 유통 | 조회 축(`CompetitionSampleQuery.categoryCode`)·요청 축(`BidPredictionRequest.categoryCode`)이 모두 `CategoryCode` 타입이지 `String`이 아니다 — `normalizeCategoryKey`가 낸 문자열 자체는 그 자리에 들어갈 권한이 없다(타입) | 컴파일 구조(`Ports.kt`·`BidPredictionRequest.kt`, 이 slice의 in_scope 밖·무변경) — `normalizeCategoryKey`는 `BusinessCategoryTest`에서 순수 함수로만 검증(포트 인자 자리에 쓰이지 않음) |
| (12) 라벨을 `OpportunityAnalysis`가 계산해 `PredictionFacts.kt`에 넘김 | `provenanceLabelFor` 호출은 `predictionRequestFor`(`PredictionFacts.kt`) 안에 있다 — `OpportunityAnalysis.kt`는 정책(`provenancePolicy`)만 resolve해 값으로 넘길 뿐 라벨을 계산하지 않는다. `PredictionFacts.kt`는 여전히 port·Clock을 읽지 않는다(정책도 값으로 받는다) | 코드 구조(`predictionRequestFor` 함수 본문) — `PredictionFacts.kt`의 import 목록에 port·Clock 타입이 없음(무변경 KDoc 전제 유지) |

## (2b) 값 획득 축 실측

| 표면 | 처분 | 실측 |
| --- | --- | --- |
| `CategoryCode.of`(public 팩토리)·`normalizeCategoryKey`(public 순수 함수) | 연다 — 생성 권한은 기존과 같고 형태만 강제 | `CategoryCode.of` 호출부는 production 2(`Canonicalize.kt`·`NoticeReconstruction.kt`) + test 20(procurement 7·adapters 6·app 2·workflow 5 — workflow 5는 `SampleEligibilityTest`(2, 표본 라벨)·`OpportunityAnalysisTest`(3, `bidvector.procurement.CategoryCode.of` 전체 한정 — 같은 파일의 `strategy.CategoryCode` 호출과 구별)) = 총 22, 구현 전 원시 카운트(scope.md 27, `bidvector.strategy.CategoryCode`·grep 우연 일치 포함)와 대조해 실제 대상 15(test)+2(production)만 전환했음을 재확인. `normalizeCategoryKey`의 유일한 호출부는 `CategoryCode.of`(companion)와 `BusinessCategoryTest`(직접 test) 둘뿐 — 다른 모듈이 이 함수를 포트 인자로 쓰지 않는다(우회 (11)) |
| `provenanceLabelFor(opening: OpeningResult?)` | 가시성 무변경(`internal`, 파일 스코프 — 같은 패키지 `workflow.evaluation`만 호출) | 호출부: `sampleOf`(`SampleConversion.kt`, opening 비-null) · `predictionRequestFor`(`PredictionFacts.kt`, opening null) · `PredictionFactsTest`(직접 test, 같은 패키지) — 셋 다 기존/이 slice의 예정된 자리, 새 외부 호출부 없음 |
| `ResolvedPolicies.provenancePolicy`(신설 필드) | `internal data class`(가시성 무변경) — 유일한 생성부는 `OpportunityAnalysis.resolvePolicies`와 test(`PredictionFactsTest.testPolicies`) | `grep -rn "ResolvedPolicies(" workflow` — production 1(`resolvePolicies`) + test 1(`testPolicies`), 새 생성부 없음. 필드가 `Resolution.Resolved<ProvenancePolicyData>` 타입이라 밖에서 미검증 값을 실을 수 없다(생성자가 `Resolution.Resolved`를 요구 — `resolvedOrNull`을 거쳐야만 얻는다) |
| 「`object` 주입」·「경계로 처리」 | 없음 | — |

## 알려진 제한

- `OPEN-4B8-CATEGORY-BACKFILL` — 정규화 전에 저장된 기존 `business_category_code` 행은
  도메인 객체(`NoticeReconstruction`)로는 여전히 복원되지만, 정규화된 값으로 조회하는
  `JdbcCompetitionSampleSource`(정확 일치 SQL)에서는 빠진다. 운영 데이터가 아직 없어(M4
  단계) 백필을 이 slice에서 수행하지 않는다 — M6 소관으로 신설.
- **설계 검토 가정 정정(전각 공백)** — `_workspace/m4-4b8/02_design-review.md` 구현 지시
  (1)은 "전각 공백(U+3000)은 Kotlin `trim()` 대상이 아니다 — Python `strip()`과의 차이"를
  가정했다. 실측(kotlinc 2.2.20 스크립트: `"　A01　".trim()` == `"A01"`)은 그 반대다 — Kotlin
  `trim()`도 U+3000을 제거하고, Python `str.strip()`도 U+3000을 제거한다(`'　'.isspace()`
  `== True`) — 이 축에서는 두 언어가 갈리지 않는다. `BusinessCategoryTest`의 유니코드 test는
  실측된 사실(정정된 방향)을 고정한다. `docs/discovery/data-dictionary.md` §6.3.1(팀장 기록,
  `dfaff1a`)이 "전각 공백"을 갈릴 수 있는 축의 예시로 든 것은 이 실측으로 갱신 대상이다(팀장
  보고 — 이 문서를 이 구현 레인이 고치지 않는다).
- 일반화된 유니코드 케이스 폴딩 차이(JVM `Character.toLowerCase` 대 Python `str.lower()`)는
  이 slice가 전수 대조하지 않는다 — KONEPS 업무구분 코드는 ASCII 영숫자라 실무에서 관측되지
  않는다(`normalizeCategoryKey` KDoc).
- `ArchitectureGateTest`(app) 실측 — `String.lowercase()`(무인자)는 `java.util.Locale.ROOT`를
  참조하도록 컴파일되어 domain 모듈 아키텍처 게이트(환경 축 누출 금지)에 걸린다
  (`member-effects.properties`가 `String#toLowerCase`를 이미 `forbidden`으로 분류해 뒀다).
  `normalizeCategoryKey`는 `Char.lowercaseChar()`(`Character.toLowerCase(Char)`, 문자 단위,
  Locale 미참조)로 구현했다 — 한 글자가 여러 글자로 바뀌는 유니코드 특수 매핑(예: 터키어
  `İ`)은 `String.toLowerCase(Locale.ROOT)`와 바이트 단위로 갈릴 수 있으나, 위와 같은 이유로
  KONEPS 코드에서는 관측되지 않는다.
- `SampleEligibilityTest`「라벨 — ...」두 test(Clean·SuspectRatio)만 표본 라벨을 재고,
  `DerivedYega`·`DerivedVat`은 `ProvenanceRulesTest`(1D 커널)가 이미 규칙표 전수를 잰다(4B-7
  알려진 제한 유지, 4B-8도 같은 이유로 반복하지 않는다) — 단 이 slice는 `DerivedYega`가
  `provenanceLabelFor`(대상·표본 공통 wiring)를 통해서는 production `ruleOrder`([SuspectRatio,
  CleanInteger, DerivedYega, DerivedVat])에서 **원화가 항상 정수라 CleanInteger가 먼저
  매치해 구조적으로 도달 불가**하다는 사실을 새로 확인했다(`PredictionFactsTest`
  「DerivedYega는 opening이 있어야만 매치한다」KDoc). 이는 4B-8이 새로 만든 제약이 아니라
  4B-7부터 있던 wiring의 성질이고, D-4B8-1의 「DerivedYega 구조적 불가」 주장을 opening 유무
  이상으로 더 강하게 만든다.

## Codex 범위

`procurement`·`workflow`·`adapters` 모두 되돌리기 어려운 경로(인증·인가·암호화·마이그레이션·
데이터 파기)가 아니다 — Codex 심판 대상 아님(CLAUDE.md 운영자 지시 2026-09-11). `verifier`가
다음 레인이다. 마이그레이션 없음(스키마·SQL 무변경).

## 사용자 승인

미승인 — 구현 레인 완료 보고 대기. verifier 검증 뒤 사용자 승인 절차(milestone-0 「verifier
ready-for-review + 사용자 승인」).
