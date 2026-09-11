# M4/4B-6a checklist

base_sha: `786febe39469835a45e9a1c6d6e8081226777364`
head_sha: `8c0a9df8f29c79664278cf54aab37824ef3237f4`(verifier r1 F-1/F-3 반영 커밋 — 이
evidence 커밋은 자기 자신을 가리키지 않는다)

## 우회 ↔ 코드 ↔ 실측 대응표(scope.md 「우회 후보(≥5)」+ 설계 검토 (4) 추가 (8))

| # | 우회 | 차단 코드 | 실측 |
| --- | --- | --- | --- |
| 1 | `SynthesizedText("…")` 직접 생성 | `SynthesizedText`의 `internal constructor` + `@ConsistentCopyVisibility`(`TextSynthesis.kt`) — 유일한 생성 경로는 `synthesize`(private) 경유 `synthesizeNoticeText`·`synthesizeProfileText` | 컴파일러 강제(같은 모듈 test는 여전히 생성 가능 — `WatchVerdict.Passed` 관례와 같은 한계, 모듈 밖 4D-2에서만 실효). `TextSynthesisTest` golden 셋이 정상 경로로만 값이 나옴을 보인다 |
| 2 | `ProfileFacts`에 필드 추가(사업자번호·대표자·연락처 등) | `ProfileFacts(businessTypes, licenses, regionTerms)` 세 필드만(`ProfilePorts.kt`) | `ProfilePortsTest`("ProfileFacts 는 업종 면허 지역 세 필드로만 생성된다") + `TextSynthesisTest` PROFILE golden이 그 세 조각만 담김을 문자열로 고정 |
| 3 | 금액을 NOTICE 텍스트에 포함 | `synthesizeNoticeText`는 `subject.baseAmount`를 읽지 않는다(파라미터 목록에서 아예 제외) | `TextSynthesisTest`("NOTICE 합성은 baseAmount 값과 무관하다") — `Fact.Absent` 사유를 바꿔도 출력 텍스트 동일 |
| 4 | 키워드 「보안」 vs 「보 안」(부분 문자열 우회) | `KeywordHitsCounter.count`는 `haystack.contains(keyword)`(정확한 부분 문자열, 토큰화 없음) | `KeywordHitsCounterTest`("verifier F-3 — 글자 사이에 공백이 끼면 매치되지 않는다") + ("verifier F-3 — 단어 내부에 나타나도 매치된다") — verifier r1 F-3(low) 이 「암묵 전제」였던 이 규약을 test로 고정하라고 지적해 두 test를 추가했다 |
| 5 | 상한 4001자(상한+1) | `truncateToPolicy`가 `policy.textMaxChars` 초과분을 뒤에서 자름(코드포인트 단위) | `TextSynthesisTest`("상한 초과 텍스트는 뒤를 자른다") — `textMaxChars=10`로 재현, 길이·접두사 둘 다 단언 |
| 6 | `textMaxChars`를 2E와 다르게 설정 | `OpportunityPolicyData` 자체는 값을 강제하지 않지만(정책 표면), `OPPORTUNITY_POLICY` 출하 값이 2E와 같은 4000 | `OpportunityPolicyDataTest`("출하 값 — textMaxChars 가 2E contract-policy properties 의 embedding text max-chars 와 같다") — `../config/quality/contract-policy.properties` 를 직접 읽어 대조(값이 아니라 실측) |
| 7 | `contract.bidvector.ml.v1.TextKind` 등 proto 타입 참조 | `TextKind`는 workflow 자체 enum(`TextSynthesis.kt`), proto import 없음 | `CompositionBoundaryTest`(`walkTopDown` 소스 스캔, allow-list 밖 완전정규화 참조 0건) — 구현 중 이 test가 KDoc의 FQN 문자열까지 실제로 잡아냄(commands.md 「GREEN」 1차 실패 참고) |
| 8 | 빈 `WatchSubject`(카테고리 0·텍스트 빈)·빈 `ProfileFacts`, 그리고 **절단 뒤에만 blank가 되는 입력**(verifier r1 F-1) | `synthesize`(private)가 결합 문자열과 **절단 결과 둘 다** blank 검사를 거쳐 `SynthesisOutcome.Empty`를 낸다(예외 없음, 단일 `return`으로 재구성 — detekt `ReturnCount`) | `TextSynthesisTest`("NOTICE 빈 subject 는 SynthesisOutcome Empty", "PROFILE 빈 fact 는 SynthesisOutcome Empty", "verifier F-1 — NOTICE/PROFILE 절단 뒤 공백만 남으면 … Empty" — 출하 정책 4000으로 `" ".repeat(4000) + "x"` 재현) |

## verifier r1 반영 — F-1(medium)·F-3(low)

- **F-1**: `synthesize`가 절단 **전**에만 blank 검사를 해, 첫 비공백 조각의 앞 `textMaxChars`
  개가 전부 공백이면(전각 공백·탭 등) 절단 뒤 공백뿐인 `Synthesized`가 나가던 결함을
  수정 — 절단 뒤에도 재검사한다. 수정은 `if (combined.isBlank()) return … ; if
  (truncated.isBlank()) return … ; return …` 세 `return`(detekt `ReturnCount` 한도 2
  초과)을 `truncated: String?` 중간값 + `truncated.isNullOrBlank()` 단일 `return`으로
  재구성해 통과시켰다 — 로직은 동일(두 경로 모두 `Empty`), 표현만 바꿨다. **새 public
  표면 없음**((2b) 값 획득 축: `synthesize`는 `private`, 함수 시그니처·타입 무변경).
- **F-3**: 키워드 매칭이 단어 경계를 보지 않는다는 규약(legacy `keyword in text`와 동일 —
  단어 내부 매치 허용, 공백 삽입은 불일치)이 「암묵 전제」였던 것을 test 둘로 고정하고
  `KeywordHitsCounter.kt` KDoc에 한 줄 추가.
- **F-2(low)**: 코드 변경 없음 — 아래 「알려진 제한」에 등재.

## 값 획득 축

`KeywordHitsCounter.count`는 `FullScopeText`(1E 값 타입)만 읽고 `OpportunityPolicyData`가
이미 검증한 키워드 목록(비공백·중복없음·소문자)을 그대로 쓴다 — 매칭 자체에 `Money`·
`Rate` 등 산술이 없어 4B-5 값 획득 축 판단(D-4B5-4)이 걸리는 축이 아니다.

## 알려진 제한

- **절단이 의미를 자른다(v1 한계)** — 뒤를 자르는 규약(D-4B6A-3)이 문장 중간에서 끊기는
  경우를 배제하지 않는다. 실측하지 않았다(합성 텍스트가 임베딩 입력이라 정확한 문장
  경계가 결과에 얼마나 영향을 주는지는 4D-2/4B-6b 병합 뒤 corpus로 확인 대상).
- **`OperatorProfilePort`·`WorkloadPort` 실 구현 없음** — port만 정의(`fun interface`).
  프로필 저장·편집·조회는 M6(`OPEN-4B6-PROFILE-SOURCE`), workload 집계는 후속.
- **조합기 `OpportunityAnalysis : MlAnalysisPort`는 4B-6b**(이 slice 밖) — 4D-2
  `EmbedTextPort` 병합 뒤. 이 slice의 함수·타입은 아직 어디서도 호출되지 않는다(합성
  경로 자체가 아직 배선되지 않음, `EvaluateCandidatesUseCase` 미편집).
- **`OPEN-4B6A-POLICY-VALUES` 승인 대기** — 키워드 14(legacy `EXECUTION_COMPLEXITY_KEYWORDS`
  그대로)·`textMaxChars=4000`(2E 값과 동일). 정본 `policy-values.md`.
- **`CompositionBoundaryTest`의 알려진 사각(4A 상속)** — 대문자로 시작하는 단일 세그먼트
  루트(`Telegram.Bot`류)는 잡지 못한다(정규식 KDoc에 이미 등재됨, 이 slice가 새로
  만든 사각 아님).
- **절단 단위가 UTF-16 코드 유닛이 아니라 코드포인트다(verifier r1 F-2, low)** — 4000
  코드포인트 이모지 텍스트는 UTF-16 기준 8000자다(실측 `chars=8000 codepoints=4000`).
  저장소에 `embedding.text.max-chars` 소비자가 아직 없어(4D-2 소유) 「상한 초과」 판정
  단위가 양끝(이 slice의 코드포인트 vs 4D-2/2E의 실제 판정 단위)에서 같은지 이 slice
  범위에서는 검증할 대상이 없다 — Python `len()` 기준이면 일치. **4D-2 병합 시 확인
  항목으로 인계.**
- **`gate.tests.workflow` 등재 완전성 test 부재(verifier r1 참고, finding 아님·범위 밖
  기존 부채)** — `TextSynthesisTest` 한 줄을 `gate-tests.properties`에서 지워도
  `gateExecutionGate`·`:workflow:test`가 모두 exit 0으로 통과한다(등재 누락을 잡는
  test가 없다). 이 slice는 신설 test 넷을 전부 등재했고(S-4로 실행 확인) 게이트 술어
  자체를 바꾸지 않았다 — `adapters/ml`·`adapters/event`에서 이미 닫힌 같은 사각의
  `workflow/evaluation` 판을 후속 slice에 권한다.

## Codex 리뷰

운영자 지시 2026-09-04(CLAUDE.md)에 따라 코드 slice는 Codex 심판 대상에서 제외된다.
이 slice의 완료 조건은 verifier ready-for-review + 사용자 승인이다(milestone 계약의
「Codex approve」를 그렇게 읽는다).

## 사용자 승인

(대기 — verifier 검증 뒤 팀장이 기록)
