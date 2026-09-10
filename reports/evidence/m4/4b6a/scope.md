# Slice 계약 — M4 / 4B-6a · 텍스트 합성 규약·키워드 매칭·프로필/workload port (workflow, 임베딩 무관 앞쪽)

> **지위**: **착수 계약 2026-09-11.** 운영자 결정 2026-09-11 「추천 (a)」 — 4B-6 을 둘로 갈라 **임베딩과 무관한 앞쪽**을 먼저 한다. 4B-6b(조합기 `OpportunityAnalysis :
> MlAnalysisPort`)는 4D-2 의 `EmbedTextPort` 병합 뒤. 운영자 결정 2026-09-10 D-4B6-1(합성 fact 집합)·계획 변경(임베딩 port 는 4D-2, 정본 `reports/evidence/m4/4d2/scope.md`
> D-4D2-1). 세션 모델 단독 작성.
>
> **자리.** `OPEN-2E-TEXT-SYNTHESIS` 가 묻는 「임베딩에 무슨 텍스트를 넣는가」를 **fact allow-list 시그니처**로 닫고(2E D-2E-5 — 합성 규약은 Kotlin 소유·version 화),
> legacy 복잡도 키워드 14 매칭(4B-5 D-4B5-5 인계)과 운영자 프로필·workload port 를 세운다. 전부 `workflow/evaluation` 안, 순수(port 는 인터페이스만). 임베딩 호출·
> 조합·use case 배선 없음.
>
> **레인 격리.** 다른 세션은 `m4/2026-09-08` 에서 4D-2(`workflow/embedding/**`·`adapters/ml/**`)를 구현 중. 이 slice 는 `workflow/evaluation/**` 신설 파일과 그 test 만 —
> 소스 경로 겹침 0. 공유 파일은 `config/quality/gate-tests.properties`(`gate.tests.workflow` 키 — 4D-2 도 같은 키를 만진다, 줄 단위 rollback)·`milestone-4.md`.
> 브랜치 `m4-4b6a/2026-09-11`(worktree `bid-vector-v2-m4e`, base = 4B-5 병합 커밋 = `main`).

```yaml
milestone: m4
slice: 4b6a-text-synthesis-and-profile-ports
base_sha: 786febe39469835a45e9a1c6d6e8081226777364   # 4B-5 병합 커밋 = main (m4/2026-09-08 은 4D-2 구현 중)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — 값을 박지 않는다.
branch: m4-4b6a/2026-09-11
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/TextSynthesis.kt          # synthesizeNoticeText(WatchSubject)·synthesizeProfileText(ProfileFacts)·SynthesizedText(internal constructor, version)·TextKind 미러(NOTICE/OPERATOR_PROFILE)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/KeywordHitsCounter.kt     # 정책 키워드 매칭 → decision KeywordHits
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/ProfilePorts.kt           # ProfileFacts 값 타입 · OperatorProfilePort · WorkloadPort(→ DerivationOutcome<UnitScore>)
  - workflow/src/main/kotlin/bidvector/workflow/evaluation/OpportunityPolicyData.kt  # synthesisVersion·keywords(14, legacy-behavior)·textMaxChars(2E 정책과 같은 값)·(4B-6b 가 쓸 예측/임베딩 CallBudget 슬롯은 만들지 않는다)
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/TextSynthesisTest.kt      # golden 텍스트·상한 절단·금액/마감/id 부재·version·allow-list 구조 test
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/KeywordHitsCounterTest.kt
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/ProfilePortsTest.kt       # fake 둘 + ProfileFacts 불변식
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/OpportunityPolicyDataTest.kt
  - config/quality/gate-tests.properties                                            # gate.tests.workflow 등재만(줄 단위 rollback — 4D-2 와 같은 키)
  - milestone-4.md                                                                  # 4B 절 4B-6a 문단
  - reports/evidence/m4/4b6a/**                                                     # + policy-values.md
out_of_scope:
  - 조합기 `OpportunityAnalysis`·`MlAnalysisPort` 구현·use case 배선(4B-6b) · 임베딩 port·값 타입·gateway(4D-2) · `UnavailableMlAnalysis` 처분(4D-2 병합 뒤)
  - workflow/evaluation/{Ports,EvaluateCandidatesUseCase,...}.kt 기존 파일 편집 · decision/** · adapters/** · strategy/**(`WatchSubject`·`Text` 소비만)
  - 프로필 저장·편집·조회 구현(M6 — port 만) · workload 집계 구현(port 만) · 유사 공고 · 시장 평균 · STR-05
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0
  - "./gradlew --no-build-cache clean check"                                                          # S-1
  - "./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.evaluation.*'"                  # S-2 — 기존 4B-2·4B-3 test 무변경 + 신설 넷
  - "./gradlew --no-daemon :workflow:test --tests '*CompositionBoundaryTest*'"                        # S-3 — evaluation 패키지 경계 스캔(신설 파일 자동 포함)
  - "./gradlew --no-daemon :workflow:gateExecutionGate"                                               # S-4
  - "grep -rniE -f config/quality/leak-patterns.txt workflow/src/main/kotlin/bidvector/workflow/evaluation; test $? -eq 1"   # S-5 — 합성 코드에 비밀값 단어 0
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/m4/4b6a/rollback.md`**. 신설 파일 여덟 삭제 + `gate-tests.properties`·`milestone-4.md` 줄 단위(최신→과거 hunk, 문서 커밋과 목록 갱신 커밋 분리 — 하네스 규율 ③).
```

근거: `milestone-4.md` 4B · 2E scope D-2E-5·`OPEN-2E-TEXT-SYNTHESIS`·`embedding.proto` `TextKind` · 4B-5 D-4B5-5(`KeywordHits`) · 1E `WatchSubject`(`categories`·`keywordText`·
`fullText`) · 1C `OperatorLicenses` · `v2-지침서.md` §8 안전 규칙 · legacy `opportunity_analysis/base.py:73-88`(키워드 14)·`classification/semantic.py`(합성 텍스트 실물 —
project/profile semantic text) · 4D-2 scope D-4D2-1 · `_workspace/m4-4b6a/02_design-review.md`.

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` — 착수 시 **없음**.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **`SynthesizedText(value: String, kind: TextKind, version: SynthesisVersion)`** — `internal constructor`. 유일한 생성 경로는 ②③ 의 합성 함수(문자열 직접 주입 차단). `TextKind = NOTICE \| OPERATOR_PROFILE`(2E proto enum 미러, `workflow` 는 proto 를 모른다 — 4D-2 gateway 가 매핑). `SynthesisVersion` 은 정책 값이고 4D-2 port 의 schema version 인자로 나간다 | 2E ⑥ 「`feature_schema_version` = 합성 규약 version」 |
| ② | **`synthesizeNoticeText(subject: WatchSubject, policy): SynthesizedText`** — 규약 v1: `categories`(코드, 정렬)·`keywordText`·`fullText` 를 고정 구분자로 이어 붙이고 `textMaxChars` 로 절단(뒤를 자른다, 앞부분이 제목·범주). **`baseAmount` 는 넣지 않는다**(금액은 의미 벡터에 기여하지 않고 legacy 도 넣지 않았다). `Notice` id·마감도 없다 — 인자에 아예 없다 | D-4B6-1 · legacy `_project_semantic_text` |
| ③ | **`synthesizeProfileText(profile: ProfileFacts, policy): SynthesizedText`** — 규약 v1: 업종 코드·면허 이름(`OperatorLicenses.Declared` 만; `NotDeclared` 는 항목 생략)·지역 term. **`ProfileFacts(businessTypes: Set<CategoryCode>, licenses: OperatorLicenses, regionTerms: List<String>)` 세 필드가 allow-list 자체** — 사업자번호·대표자·연락처는 필드가 없어 구조적으로 못 들어온다. test 가 `ProfileFacts` 의 생성자 인자 수를 3 으로 고정(늘면 test 가 떨어져 규약 재검토를 강제) | D-4B6-1 · §8 · legacy `_profile_semantic_text` |
| ④ | **`KeywordHitsCounter.count(fullText: FullScopeText, policy): KeywordHits`** — 정책 키워드 14 를 소문자 부분 문자열 매칭(legacy `keyword in project_text`)으로 센다. 같은 키워드 중복 출현은 1 회(legacy `sum(1 for keyword ... if keyword in text)` 와 같다) | 4B-5 D-4B5-5 · legacy `scoring.py:306-` |
| ⑤ | **port 둘** — `OperatorProfilePort.current(): ProfileFacts?`(null = 프로필 미설정) · `WorkloadPort.current(): DerivationOutcome<UnitScore>`(4B-5 `DerivationOutcome` 재사용; 항상-미가용 fake `Absent(WorkloadNotCollected)`). 구현은 M6/후속 | ADR 0005 D-9 |
| ⑥ | **정책 데이터** `OpportunityPolicyData(synthesisVersion, keywords: List<String>(14, 비공백·중복 없음), textMaxChars(2E `embedding.text.max-chars` 와 같은 값 4000 — 두 자리가 어긋나면 4D-2 gateway 가 거부하므로 test 가 `contract-policy.properties` 값과 대조))` + `OPPORTUNITY_POLICY` 슬롯. 승인 대상 `OPEN-4B6A-POLICY-VALUES` | ADR 0010 D-1 |

**만들지 않는 것**: 조합기 · 임베딩 호출 · use case 배선 · 프로필/workload 구현 · CallBudget 슬롯(4B-6b) · 유사 공고 · 시장 평균.

---

## 계약 고정 결정 (D-4B6A-1~4)

| ID | 판단 | 근거 | 상태 |
| --- | --- | --- | --- |
| **D-4B6A-1** | 합성 fact 집합 v1 = NOTICE(카테고리·키워드·전문, 금액·마감·id 제외) / PROFILE(업종·면허·지역) — 인자가 fact 타입, `String` 없음 | 운영자 승인 2026-09-10 D-4B6-1 | 확정 |
| **D-4B6A-2** | `TextKind` 는 workflow 자체 enum(proto 미러) — workflow 는 `contract.*` 를 참조하지 않는다(4A 경계 규칙) | ADR 0006 · `CompositionBoundaryTest` | 계약 고정 |
| **D-4B6A-3** | 절단은 뒤에서, 구분자는 정책이 아니라 규약 상수(version 이 바뀌면 함께 바뀜) — KDoc 에 v1 문면 고정 | 2E ⑥ | 계약 고정 |
| **D-4B6A-4** | `textMaxChars` 는 2E `contract-policy.properties` 값과 **같아야** 하고 test 가 대조(두 정본이 아니라 4B-6a 가 2E 값을 따른다) | 2E ④ | 계약 고정 |

---

## 위협 모델 — 4B-6a 고유 경계

**방어한다**: (a) 개인정보의 합성 텍스트 유입(fact 시그니처 + `ProfileFacts` 인자 수 고정 + S-5) (b) 문자열 직접 주입(`SynthesizedText` internal) (c) 상한 초과 텍스트(절단) (d) 합성 규약의 무버전 변경(`SynthesisVersion` 값 + golden test) (e) 키워드 대소문자·중복 계수 (f) 2E 상한과의 불일치(D-4B6A-4 test) (g) 채널·grpc·contract 타입 유입(S-3). **방어하지 않는다**: 프로필 내용의 옳음(M6) · 임베딩 품질 · 절단이 의미를 자르는 것(v1 규약의 한계, 알려진 제한).

**우회 후보(≥5)**: (1) `SynthesizedText("…")` 직접 → internal (2) `ProfileFacts` 에 필드 추가 → 인자 수 test (3) 금액 포함 → golden (4) 키워드 「보안」 vs 「보 안」 → 부분 문자열 정확 (5) 상한 4001자 → 절단 (6) `textMaxChars` 를 2E 와 다르게 → 대조 test (7) `contract.bidvector.ml.v1.TextKind` 참조 → S-3.

---

## OPEN — 수령·신설

| OPEN | 처리 |
| --- | --- |
| `OPEN-2E-TEXT-SYNTHESIS` | **v1 규약으로 종결 후보**(②③) — 종결 승인 시 capability-map 갱신은 4B-6b 병합 뒤 일괄 |
| **`OPEN-4B6A-POLICY-VALUES`**(신설) | ⑥ 값 |
| **`OPEN-4B6-PROFILE-SOURCE`**(신설) | `ProfileFacts` 의 저장·편집·조회(M6) |
