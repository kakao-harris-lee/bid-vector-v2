# M6/6F-4-w — checklist.md

## (2b) 값 획득 축 — 신설 public 표면 전수

Kotlin source 표면(공개 API) 순증가 0, `noticeToWatchSubject` 는 `private` 으로 축소됐다
(code-reviewer LOW). 그 축소가 bytecode 레벨의 새 표면 1건(Kotlin 합성 `access$` 접근자)을
부수효과로 낸다 — 아래 표에 실측으로 적는다.

| 표면 | 종류 | 가시성 | 이 표면으로 감시 텍스트 값을 만들거나 바꿀 수 있는가(실측) |
|---|---|---|---|
| `NoticeWatchSubjectPort`(`adapters.evaluation`) | class, `WatchSubjectPort` 구현 | public | 아니오 — `subjectFor(notice)`는 `notice`의 title·businessCategory·agency 값만 읽어 `noticeToWatchSubject`에 위임한다. 자기 필드·상태 없음(순수 위임). |
| `NoticeWatchSubjectPort.subjectFor` | fun | public(override) | 아니오 — 반환은 항상 `noticeToWatchSubject(notice)`를 `Found`로 감싼 값. 이 함수 자체에 텍스트 조립 로직이 없다(상수 풀 허용 목록 `EvaluationAdapterDependencyTest`가 실측, D-6F4W-14 수정). |
| `noticeToWatchSubject`(`adapters.evaluation`) | fun | **private**(code-reviewer LOW) | 예, 그러나 경계 안 — `Notice`의 값만 읽어 `strategy.assembleKeywordScopeText`/`assembleFullScopeText`를 그대로 호출한다(상수 풀 허용 목록으로 실측). Kotlin 소스 레벨에서는 `NoticeWatchSubjectPort.kt` 밖에서 이름으로 참조할 수 없다. 허용된 getter 끼리의 **인자 치환**은 허용 목록이 구조상 못 잡고 `NoticeWatchSubjectPortTest` 거동 test 가 잡는다(verifier r2 m5 — 분담). |
| `access$noticeToWatchSubject`(`adapters.evaluation.NoticeWatchSubjectPortKt`, 합성) | fun(compiler-generated) | **public**(bytecode, `javap -p` 실측) | 예(서명 동일), 그러나 Kotlin source 로는 어떤 호출자도 이 이름을 쓸 수 없다 — Kotlin 이 같은 파일의 다른 class(`NoticeWatchSubjectPort`)가 `private` top-level 함수를 부르도록 자동으로 내는 접근자다. raw JVM 호출(리플렉션 등)로만 닿고, 닿아도 `noticeToWatchSubject`와 서명·로직이 완전히 같아 새 능력이 없다 — `Notice`를 안 거치는 임의 문자열 주입 경로가 아니다. `EvaluationAdapterDependencyTest`의 허용 목록에 등재(D-6F4W-16). |
| `KeywordScopeText.Companion.of`(`strategy`) | fun | **internal**(설계 선택 — Text.kt KDoc) | 예, 그러나 시그니처가 `assembleKeywordScopeText`와 동일(noticeTitle, businessCategoryLabel) — 원문 raw string 을 받는 별도 경로가 아니다. 모듈 밖에서 호출 불가 — 근거는 `internal` 가시성 자체다(`of` 를 부르는 fixture 는 없다, verifier L-2/LR2-1). |
| `FullScopeText.Companion.of`(`strategy`) | fun | **internal**(위와 같은 이유) | 예, `KeywordScopeText.of`와 같은 이유(근거도 같다 — `internal` 가시성). |
| `KeywordScopeText`/`FullScopeText` 주 생성자 | constructor | **private**(신설 폐쇄) | 「경계로 처리」 — 이 slice 이전에는 `public`이라 임의 문자열로 값을 만들 수 있었다(verifier r2 MEDIUM-3 실측 우회). 이제 클래스 본문(companion) 밖에서 호출 불가 — negative fixture 4·6 실측. |
| `KeywordScopeText`/`FullScopeText.copy()` | fun(auto-gen) | **private**(`@ConsistentCopyVisibility`) | 「경계로 처리」 — 생성자와 같은 가시성으로 닫혀 기존 값을 갈아끼울 수 없다 — negative fixture 5·7 실측. |
| `ConcatenationMachineryFixture`(`adapters.evaluation`, test-only) | class(메서드 2 — `concatenate`·`concatenateViaJoin`) | internal | 아니오 — production 표면이 아니다. `EvaluationAdapterDependencyTest`의 허용 목록 술어가 실제로 위반(문자열 템플릿 **및** `String.join`, D-6F4W-14)을 잡는지 증명하는 양성 대조 전용(주석에 명시). |

**「경계로 처리」 행의 실측 결론**: 넷 다 시그니처·값이 기존 합법 경로(`assemble*`)와
동일해 새 값 획득 능력이 없다 — direct 생성·`copy()`는 컴파일 자체가 안 되고(negative
fixture), `of`/`access$` 는 컴파일은 되지만 서명이 좁아 별도 raw-string 경로가 아니다.

**`object`/`companion` 커널 주입 자리**: `KeywordScopeText.Companion`·`FullScopeText.Companion`
둘 — 위 표에 이미 행으로 셌다. 그 외 신설 `object` 없음.

## D-6F4W-9 — 실측 결론(팀장 보고 완료, 2026-09-23)

`assembleKeywordScopeText`/`assembleFullScopeText`는 공백뿐인 비지 않은 문자열을 **낼 수
없다** — `joinNonBlankParts`가 join 전에 blank 조각을 전부 거르므로 결과는 `""`(조각 0개)
이거나 비공백 문자를 포함(조각 ≥1개)하는 두 경우뿐이다. 도메인 발견 아님 — D-6F4W-8 ⓑ로
처분.

## D-6F4W-8 ⓐ/ⓑ 건별 집계 (이관 39건, compile-fixtures 4건은 별도 — 아래 표 뒤)

| 파일 | 건수 | 처분 | 비고 |
|---|---|---|---|
| `strategy/.../WatchRulesTest.kt`(`subject()` 헬퍼) | 2 | ⓐ | 단일 조각 재구성, property test 의 blank 생성값은 `""`로 접힘(부작용 없음 — `NoGate` 등 결과가 내용과 무관) |
| `strategy/.../WatchTextAssemblyTest.kt` | 6 | `.value` 전환 | D-6F4W-11 조건 1 — 주제가 조립 함수의 출력 문자열 자체 |
| `workflow/.../EvaluationTestFixtures.kt` | 4 | ⓐ | `EMPTY_SUBJECT`·`MATCHING_SUBJECT`, 둘 다 빈 문자열 |
| `workflow/.../KeywordHitsCounterTest.kt` | 7 | ⓐ | 전부 단일 비공백 리터럴 |
| `workflow/.../TextSynthesisTest.kt` | 9 | ⓐ | 8건 단순 재구성 + verifier F-1(공백 N+`x`)은 `demandAgencyName` 단일 조각 경유로 재현 |
| `workflow/.../TextSynthesisTest.kt` | 1 | ⓑ | `FullScopeText("   ")`(공백 3자) — D-6F4W-9 실측으로 도달 불가, `""`로 대체(원 의도 「빈 subject → Empty」는 그대로 유지, 「절단 뒤 공백만 남는」 축은 옆 F-1 test 가 커버) |
| `adapters/.../extraction/ExtractionGateTest.kt` | 4 | ⓐ | 전부 빈 문자열(`subject()` 헬퍼 + 인라인 1건) |
| `adapters/.../ml/UnavailableMlAnalysisTest.kt` | 2 | ⓐ | `MATCHING_SUBJECT`, 빈 문자열 |
| `app/.../conformance/StrategyExecutors.kt` | 4 | ⓐ | corpus 경유(`fixtures/input/strategy-watch-00{1..8}.json` 8건 전수 확인 — 전부 `""` 또는 비공백) + `moneyBasis003Executor`(빈 문자열) |

**합계**: ⓐ 32 · ⓑ 1 · `.value` 전환 6 = 39(전체 raw 생성 43 − compile-fixtures 4).

### compile-fixtures (별도 — D-6F4W-11 조건 3, 부호 판단)

- `negative-2-keyword-scope-cross-text.kt.txt`/`positive-2-keyword-scope-own-text.kt.txt`/
  `mutant-2-keyword-scope-cross-text-typo.kt.txt`(3파일, raw 4건) — **부호 유지, 리터럴만
  이관**. 폐쇄 뒤에도 「`WatchSubject.keywordText`는 `FullScopeText`를 받지 않는다」(타입
  교차 대입 거부)라는 fixture 2 고유 명제를 그대로 잰다 — 생성 경계 폐쇄와 직교하는 축이라
  기계적으로 뒤집지 않았다.
- 신설 fixture 4·5·6·7(11파일) — 생성 경계 폐쇄 자체(직접 생성·`copy()`)를 새로 잠근다.

**D-6F4W-11 조건 3 개별 답(L-4 — 셋 각각으로 답한다, 묶음 답 아님).**
「own text」의 전제부터 정정한다: 「자기 모듈의 텍스트」가 아니라 **「자기 타입 자리의
텍스트」**다(`keywordText` 자리에 `KeywordScopeText`) — 이관 뒤 세 파일은 생성자가 아니라
public `assemble*`를 쓰므로 폐쇄로 명제가 거짓이 되지 않는다.

- **negative-2 — 고유하다.** `WatchSubject.keywordText` 자리에 `FullScopeText`를 넣으면
  type mismatch 가 난다는 **타입 자리 교차** 축이다. 「아무도 못 만든다」(생성 경계 폐쇄)의
  부분집합이 아니다 — 값 자체는 합법 경로(`assembleFullScopeText`)로 만들어지고, 실패
  사유는 오직 타입이 안 맞는다는 것뿐이다.
- **positive-2 — 고유하다.** negative-2 의 양성 쌍둥이로, 「negative-2 가 실패하는 이유가
  `WatchSubject` 생성 불가나 `assemble*` 비공개가 아니라 타입 교차 하나뿐」임을 보인다.
  positive-4·6 은 `WatchSubject` 에 올리지 않으므로 이 역할을 대신하지 못한다.
- **mutant-2 — 고유하다.** 진단(`unresolved reference 'WatchSubjectTypo'` + 타입 추론 불가)에
  `type mismatch` 가 없어, negative-2 가 재는 진단 조각이 아무 컴파일 오류에나 우연히
  맞는 조각이 아님을 보인다.

결론: 부호 유지가 옳다. 셋은 생성 경계 폐쇄와 직교하는 **타입 자리 교차 거부** 축을 재고,
fixture 4~7(직접 생성·`copy()`)과 겹치지 않는다.

## 알려진 제한

- `OPEN-6F4W-UNAVAILABLE-PRODUCER`(scope.md 신설) — `WatchSubjectOutcome.Unavailable`의
  production 생산자가 0(이 어댑터가 유일한 production 구현인데 그 타입을 낼 수 없다). 변이
  실측이 그 경로를 못 잡는다 — `workflow`의 `OpportunityAnalysis`/`EvaluateCandidatesUseCase`
  test 가 fake port 로 그 분기를 덮고 있다는 사실만 남긴다.
- `notice_title` 실 데이터 0건(6F-4 실측 인계) — 이 slice 가 배선을 끝내도 당분간 감시
  키워드/전문 텍스트는 빈 문자열이다. `OPEN-6F4-TITLE-INGEST`(수집 배선)의 몫.
- **폐쇄는 생성 경로 한정, 인자의 의미는 호출부 책임이다(D-6F4W-15).**
  `assembleKeywordScopeText`/`assembleFullScopeText`의 인자가 `String?`이라, 새 호출부가
  요건 텍스트 등을 `noticeTitle` 자리에 넣으면 컴파일되고 필수 키워드를 거짓 만족시킬 수
  있다(`assembleKeywordScopeText(<요건 텍스트>, null)`이 초록). 닫은 것은 직접 생성·
  `copy()`·출력 정규화(D-6F4W-12)뿐이고, 내용의 출처는 어댑터 책임이다(scope.md 위협 모델
  (d), fixture 2 KDoc). 오늘 production 호출자는 `NoticeWatchSubjectPort` 하나뿐이라는 것은
  **`grep` 실측이지 구조가 아니다** — 호출자 집합을 ArchUnit 집합 등식으로 구조화하는 일은
  `OPEN-6F4W-ASSEMBLE-CALLER`(scope.md 신설)로 넘긴다. `Text.kt` KDoc 이 이 문면을 담는다.
- `KeywordScopeText.of`/`FullScopeText.of`가 `internal`인 이유는 **Kotlin 의 한계가
  아니라 설계 선택이다.** 같은 저장소의 `procurement.NoticeTitle.of`가 top-level 래퍼 없이
  `private constructor` + `public companion factory`로 완전히 닫은 선례다.
  `assembleKeywordScopeText`가 top-level 함수로 남아 있는 한(현재 import 기준 7개 .kt 파일이 — `grep -rlE 'import bidvector\.strategy\.assemble(Keyword|Full)ScopeText' --include=*.kt | wc -l` —
  `import ...assembleKeywordScopeText`로 부른다) 그 함수가 `of`에 접근하려면 최소
  `internal`이어야 하지만, `NoticeTitle`처럼 `assemble*` 자체를 companion 멤버로 옮기고
  호출부를 갱신하면 이 `internal` 잔여 표면은 사라진다 — 호출부 7파일 갱신 비용 대신
  택한 설계 선택이다. `of`는 `assemble*`와 시그니처·본문이 같아 raw 임의 문자열 주입의
  새 경로는 아니다.
- **`noticeToWatchSubject`를 `private`으로 좁히면(D-6F4W-16) Kotlin 이 bytecode 레벨의
  합성 `access$noticeToWatchSubject`(`public static`, `javap -p` 실측)를 낸다.** Kotlin
  source 로는 어떤 호출자도 이 이름을 쓸 수 없고, 서명·로직이 원본과 같아 새 값 획득
  능력은 없다 — (2b) 표에 행으로 실측했다.
- **privacy-gate R-1** — `KeywordScopeText`/`FullScopeText`/`WatchSubject`는 여전히
  `data class`라 합성 `toString()`이 공고명·공종·기관명 원문을 그대로 낸다. 오늘 그 값을
  문자열에 보간하는 production 경로는 없다(어댑터가 Spring 빈으로 등록되지 않음, D-6F4W-6).
  `OPEN-6F-ASSEMBLY` 재확인 항목 ④(scope.md OPEN 표).
- `./tools/one-command-check.sh`가 이 장비에서 exit 0(Kotlin 전건 + Python S-1~S-11 전부
  통과, commands.md). S-1(Python `ml-engine` 설치)의 구 실패는 구현 레인 원래 장비의
  네트워크 제약(PyPI 접속 timeout)이었다(L-6).

## 수정 라운드가 만든 새 파일 ↔ in_scope 대조

`git diff --name-status ab83e27c..HEAD` 기계 재확인 — 새 파일 0, 전부 기존 파일 수정:

| 파일 | 상태 | in_scope 글롭 |
|---|---|---|
| `adapters/src/main/kotlin/bidvector/adapters/evaluation/NoticeWatchSubjectPort.kt` | M | `adapters/src/main/kotlin/bidvector/adapters/evaluation/**` |
| `adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt` | M | `adapters/src/test/kotlin/bidvector/adapters/evaluation/**` |
| `strategy/src/main/kotlin/bidvector/strategy/Text.kt` | M | `strategy/src/main/kotlin/bidvector/strategy/Text.kt`(명시) |
| `strategy/src/test/kotlin/bidvector/strategy/CompileFailureHarnessTest.kt` | M | `strategy/src/test/kotlin/bidvector/strategy/**` |
| `strategy/src/test/kotlin/bidvector/strategy/WatchRulesTest.kt` | M | `strategy/src/test/kotlin/bidvector/strategy/**` |
| `strategy/src/test/kotlin/bidvector/strategy/WatchTextAssemblyTest.kt` | M | `strategy/src/test/kotlin/bidvector/strategy/**` |
| `reports/evidence/m6/6f4w/checklist.md` | M | `reports/evidence/m6/6f4w/**` |
| `reports/evidence/m6/6f4w/commands.md` | M | `reports/evidence/m6/6f4w/**` |

8개 전부 in_scope 글롭 안이다 — 밖을 만진 파일 0, 신설 파일 0(rollback.md 는 이 표 이후
별도 커밋에서 재산출).

## 확인하지 않은 것

- **참고 R-1** — `strategy` 패키지에는 `gate-tests.properties` 등재를 강제하는 게이트(예:
  `strategy` 전용 `GateRegistrationTest`)가 없다(변이: `WatchTextAssemblyTest` 등재를
  지워도 `:strategy:check`가 초록). **기존 구조**다(`adapters`/`app`의
  `*GateRegistrationTest`와 달리 `strategy`에는 처음부터 그런 게이트가 없었다) — 이 slice
  가 새로 만든 공백이 아니라는 사실만 여기 남긴다.
- 리플렉션·Jackson 역직렬화로 private 생성자를 부르는 경로 — 위협 모델 밖이고, production
  에서 두 타입을 역직렬화하는 자리는 grep 0.
- 한국어 공고명에 비ASCII 공백(U+3000·U+00A0)이 실제로 들어오는지 — 실 데이터 0건
  (`OPEN-6F4-TITLE-INGEST`).

## 리뷰 요청 조건 점검

- [x] 구현 diff 가 커밋되어 base/head 고정 — 각 커밋 `git diff --cached --name-status`로
  in_scope 대조 후 커밋. `git status --porcelain`은 매 커밋 사이 빈 출력.
- [x] acceptance 세 명령 전부 실측(commands.md) — `check --rerun-tasks`·`qualityBaseline`·
  `one-command-check.sh` 전부 exit 0.
- [x] 캐시 우회 재확인 — `--rerun-tasks`로 전 모듈 test 재실행(모두 `EXECUTED`, 캐시 0).
- [x] 게이트 술어 변경 셋의 변이 RED — `internal` 퇴행·`String.join` 복제·`trim(' ')` 변이
  버릴 clone 에서 RED 확인(commands.md).
- [x] 비밀값 스캔 — 이 라운드가 변경한 6개 코드 파일 기준 참조형 exit 1(매치 0, commands.md).
- [x] fixture/정책 version 근거 — 이 라운드는 fixture·정책 데이터를 변경하지 않았다(변이는
  전부 버릴 clone 에서 원복). D-6F4W-11 조건 3 답을 세 fixture 개별로 보강(L-4).
- [x] 새 public 표면 재확인((2b) 표 갱신) — Kotlin source 표면 순증가 0, bytecode 레벨
  `access$` 합성 접근자 1건을 실측·등재.
- [x] 새 파일 ↔ in_scope 대조 — 새 파일 0, 8개 전부 in_scope 안(위 표).
- [x] 알려진 제한·rollback — 위 절 + `rollback.md`(임시 clone ①~⑥ 재실측, 실측 HEAD 갱신).
