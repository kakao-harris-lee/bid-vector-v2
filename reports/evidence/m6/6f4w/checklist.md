# M6/6F-4-w — checklist.md

## (2b) 값 획득 축 — 신설 public 표면 전수

| 표면 | 종류 | 가시성 | 이 표면으로 감시 텍스트 값을 만들거나 바꿀 수 있는가(실측) |
|---|---|---|---|
| `NoticeWatchSubjectPort`(`adapters.evaluation`) | class, `WatchSubjectPort` 구현 | public | 아니오 — `subjectFor(notice)`는 `notice`의 title·businessCategory·agency 값만 읽어 `noticeToWatchSubject`에 위임한다. 자기 필드·상태 없음(순수 위임). |
| `NoticeWatchSubjectPort.subjectFor` | fun | public(override) | 아니오 — 반환은 항상 `noticeToWatchSubject(notice)`를 `Found`로 감싼 값. 이 함수 자체에 텍스트 조립 로직이 없다(상수 풀 부재 단언 `EvaluationAdapterDependencyTest`가 실측). |
| `noticeToWatchSubject`(`adapters.evaluation`) | fun | **internal** | 예, 그러나 경계 안 — `Notice`의 값만 읽어 `strategy.assembleKeywordScopeText`/`assembleFullScopeText`를 그대로 호출한다(구분자·이어붙이기 상수 없음, 상수 풀 부재 단언으로 실측). 이 함수가 값을 "만든다"는 것은 조립 커널을 부른다는 뜻이지 직접 이어붙인다는 뜻이 아니다. |
| `KeywordScopeText.Companion.of`(`strategy`) | fun | **internal** | 예, 그러나 시그니처가 `assembleKeywordScopeText`와 동일(noticeTitle, businessCategoryLabel) — 원문 raw string 을 받는 별도 경로가 아니다. 모듈 밖에서 호출 불가(컴파일 fixture 4 가 실측). |
| `FullScopeText.Companion.of`(`strategy`) | fun | **internal** | 예, `KeywordScopeText.of`와 같은 이유(fixture 6 실측). |
| `KeywordScopeText`/`FullScopeText` 주 생성자 | constructor | **private**(신설 폐쇄) | 「경계로 처리」 — 이 slice 이전에는 `public`이라 임의 문자열로 값을 만들 수 있었다(verifier r2 MEDIUM-3 실측 우회). 이제 클래스 본문(companion) 밖에서 호출 불가 — negative fixture 4·6 실측. |
| `KeywordScopeText`/`FullScopeText.copy()` | fun(auto-gen) | **private**(`@ConsistentCopyVisibility`) | 「경계로 처리」 — 생성자와 같은 가시성으로 닫혀 기존 값을 갈아끼울 수 없다 — negative fixture 5·7 실측. |
| `ConcatenationMachineryFixture`(`adapters.evaluation`, test-only) | class | internal | 아니오 — production 표면이 아니다. `EvaluationAdapterDependencyTest`의 부재 단언이 실제로 위반을 잡는지 증명하는 양성 대조 전용(주석에 명시). |

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

## 알려진 제한

- `OPEN-6F4W-UNAVAILABLE-PRODUCER`(scope.md 신설) — `WatchSubjectOutcome.Unavailable`의
  production 생산자가 0(이 어댑터가 유일한 production 구현인데 그 타입을 낼 수 없다). 변이
  실측이 그 경로를 못 잡는다 — `workflow`의 `OpportunityAnalysis`/`EvaluateCandidatesUseCase`
  test 가 fake port 로 그 분기를 덮고 있다는 사실만 남긴다.
- `notice_title` 실 데이터 0건(6F-4 실측 인계) — 이 slice 가 배선을 끝내도 당분간 감시
  키워드/전문 텍스트는 빈 문자열이다. `OPEN-6F4-TITLE-INGEST`(수집 배선)의 몫.
- `./tools/one-command-check.sh`의 S-1(Python `ml-engine` 설치)이 로컬 환경에서 PyPI
  network timeout 으로 실패(commands.md 참고) — 이 slice 가 만지는 Kotlin 파일과 무관한
  로컬 네트워크 제약. Kotlin `check`+`qualityBaseline`(S-1 선행 전 단계)은 전부 GREEN.
- `KeywordScopeText.of`/`FullScopeText.of`가 `internal`이라 `strategy` 모듈 **안**에서는
  여전히 호출 가능하다 — 다만 시그니처가 `assemble*`와 동일(제목+라벨[+기관명 둘])이라
  raw 임의 문자열 주입 경로가 아니다(요건·기관명을 keywordText 에 실을 길은 여전히 없다).
  완전한 「단일 호출자」 강제(예: `assemble*`만 접근 가능한 `private` companion)는 Kotlin
  이 top-level 함수에 그런 세밀 가시성을 주지 못해 구조적으로 못 닫는다 — 이 한계는
  **닫히지 않는 것을 닫혔다고 적지 않는다**(CLAUDE.md) 원칙에 따라 여기 명시한다.

## 리뷰 요청 조건 점검

- [x] 구현 diff 가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 개별 경로>`
  결과 없음(commands.md 커밋 전 확인, 위 절차). 양성 대조 1회(scope.md 에 줄 추가 →
  porcelain 이 잡음 → 비파괴 절삭으로 원복, 원복 후 재확인 결과 없음).
- [x] acceptance 세 명령 전부 실측(commands.md) — `check`·`qualityBaseline` exit 0,
  `one-command-check.sh` exit 1(S-1 무관 환경 제약, 알려진 제한에 등재).
- [x] 캐시 우회 재확인 — `--rerun-tasks`로 4개 모듈 test 50/50 실제 실행, 실패 0.
- [x] 비밀값 스캔 — exit 0(매치 5건)이나 전수 diff 밖(사전 존재 식별자) 확인, commands.md.
- [x] fixture/정책 version 근거 — `fixtures/input/strategy-watch-00{1..8}.json` 대조 기록(위
  ⓐ/ⓑ 표), 정책 version 신설 없음(이 slice 는 정책 데이터를 추가하지 않는다).
- [x] 알려진 제한·rollback — 위 절 + `rollback.md`.
