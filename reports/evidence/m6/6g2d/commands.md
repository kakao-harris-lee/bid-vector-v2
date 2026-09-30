# M6/6G-2d — 실행 명령과 실측

base `c357e437` · 레인 `kotlin-implementer` 하나 · 브랜치 `m6-6g2d/2026-09-30`.

> **마지막 HEAD 의 게이트 결과는 이 문서가 정본이 아니다**(evidence-pack 규격) — evidence 를 고치는
> 커밋은 언제나 마지막 산출물 커밋 뒤에 오므로 자기 post-state 를 담을 수 없다. 마지막 HEAD 의
> acceptance 는 verifier 와 PR 조치 코멘트가 정본이고, 아래 표는 그 직전까지를 담는다.

## 계약 조항 ↔ 구현 심볼

| 결정 | 이행 | 심볼 |
|---|---|---|
| **D-6G2d-1** 누적 해시는 복구 뒤에 | 이행 | `RunStateDirectory` 의 누적 해시 초기화식이 복구를 **자기 안에서** 먼저 돌린다(`healedLedgerDigest`·`heldOrRelease`) — 프로퍼티 선언 순서에 기대지 않는다 |
| **D-6G2d-2** 복구 쓰기는 staged + `ATOMIC_MOVE` | 이행 | `healTornTail` 이 `recordState` 와 같은 형태. 장부 제외 이름을 집합 하나로 모아 원장의 중간 이름을 등재(`EXCLUDED_FROM_LEDGERED_SET`·`STAGED_ATTEMPT_NAME`) |
| **D-6G2d-3** 결말 줄 없는 축은 가장 늦은 걷기 | 이행 | `WalkRows` 가 (공고, 축)마다 쓰기로 정한 걷기를 값으로 든다. `collectWalkRow`·`fromLedgeredWalk` |
| **D-6G2d-4 ⓐ** 형식 version fail-closed | 이행 | `RUN_STATE_FORMAT_VERSION`·`requireCurrentFormat`·`RunStateFormatRefusedException`(`internal`, 사유 `MISSING`/`MISMATCHED`) |
| **D-6G2d-4 ⓑ** 걷기 없는 AXIS 줄은 형태 위반 | 이행 | `AxisConclusion.walk` 가 non-null. `FileAttemptLedger.parseLine` 이 `CollectionAttempt.init` 의 양방향 검사에 걸린다 |
| **D-6G2d-4 ⓒ** `CollectionAttempt.walk` 기본값 제거 | 이행 | `init` 이 `(kind == AXIS) == (walk != null)` 를 요구 |
| **D-6G2d-4 ⓓ** `SourceBatch.observedAt` 기본값 판정 | 이행 — **기본값·nullable 둘 다 제거** | 착수 실측: production 생성 자리 둘 중 빈 배치(무효 cursor)도 걷기 이름을 실을 수 있다 → 「채우지 않는 자리 0」 |
| **D-6G2d-5** 「정착했으나 0 행」은 등재로 | 이행(문서) | 아래 「알려진 제한」 (a)(b) · `OPEN-6G2D-EMPTY-AXIS-REASON` |
| **D-6G2d-6** 게이트 술어 확장 0 · 새 public 표면 0 | **부분 — 이탈 2·5** | 술어 확장 0 은 지켰다(계약 파일 변경은 등재 **해제** 한 줄). 새 public 표면은 여덟 |
| **D-6G2d-7** 6G evidence 정정 선언 | 이행 | 아래 「6G evidence 정정」 |
| **D-6G2d-8 ⓐ** 빈 번호 원문 행 | 이행 | `NoticeNumber.ofOrNull` — 추출의 `keyAndEndpointOf`·이어 돌기의 `JdbcCollectedAxisStore.matched` 두 자리. 계수 `SnapshotExtraction.unusableRawRows`, 러너 로그 한 칸 |
| **D-6G2d-8 ⓑ** raw 있고 결말 없는 축은 재호출 | 이행 | `AttemptHistory.axisResumptions` 가 「줄 있음 · 결말 없음」을 `false` 로 낸다. 줄이 아예 없으면 항목 부재(D-6G-58 그대로) |
| **D-6G2d-8 ⓒ** 결정적 실패는 확정 · 일시는 상한까지 | 이행 — **분류 하나 이탈 3** | `AttemptOutcome.FinalFailure`(원장 라벨 `FINAL:<코드>`) · `failureOf` 의 소진 `when` · 상한은 `DetailFetchGates.axisRetryLimit`(잠정 3) |
| **D-6G2d-8 ⓓ** 소수 금액은 행 단위 부재 | **D-6G2d-15 로 교정** | 칸 단위 `null` 이 집계 둘에서 스키마 형태 위반이었다 — 아래 D-15 |
| **D-6G2d-15** 소수 금액은 집계 전체 null · 계수 | 이행 | `AssemblyTally.wonAmount`(스칼라 다섯)·`wonAggregate`(집계 둘)·`isWonInteger` · `SnapshotExtraction.fractionalAmounts` · 러너 로그 한 칸. `jsonAmount` 의 같은 검사는 발화하지 않는 마지막 방어로 남고 KDoc 이 그 사실을 적는다 |
| **D-6G2d-16** 관문 거부는 상한 밖 | 이행 | `AttemptOutcome.Refused`(라벨 `REFUSED:<코드>`) · `failureOf` 의 세 갈래 · `doneWith` 는 `Failed` 만 센다. 형식 version 2 |
| **D-6G2d-17** 확정 실패는 정확히 셋 | 이행 | `failureOf` 의 확정 갈래 = INPUT_ERROR · NOT_RETRYABLE · MAX_PAGES. `ALL_TRUNCATION_CAUSES` 전수 등식 test 둘 |
| **D-6G2d-18** 일괄 | **부분 — 이탈 9** | 형식 version 정수만(`requireCurrentFormat`·`STRICT_FORMAT_VERSION`) · 낡은 KDoc 넷 · 중복 하한을 쓰는 자리 하나로 · test 대역의 확정 코드 교정 · 계수 이름 유지. **형식 거부의 전용 종료 코드는 안 했다**(이탈 9) |
| **D-6G2d-19** 셈 창 등재 + **마지막 정착 뒤부터** | 이행 | `doneWith` 의 `takeLastWhile { !isSettled }` — 정착 앞의 실패는 그 정착으로 무효가 된 증거다. 등식 test 하나. 아래 「알려진 제한」 (e) |

## 변이표

변이마다 적용 직후 `git diff` 로 **실제로 적용됐는지** 확인한 뒤 돌렸다(초록으로 남는 변이는 test 가
아무것도 잠그지 않는다는 뜻이다 — 한 번 그렇게 나와 test 를 고쳤다).

| 변이 | 대상 test | 결과 |
|---|---|---|
| **M-heal-order** 누적 해시를 복구보다 먼저 짓는다 | `RunStateDirectoryTest` | RED 1 |
| **M-heal-inplace** 복구 쓰기를 제자리 truncate+rewrite 로 | 같은 클래스 | RED 1 |
| **M-staged-not-excluded** 원장의 중간 이름을 장부 제외에서 뺀다 | 같은 클래스 | RED 1 |
| **M-format-warn-only** 형식 version 이 달라도 기동한다 | 같은 클래스 | RED 2 |
| **M-walk-merge-all** 결말 없는 축의 선별을 없앤다 | `JdbcSnapshotSourceSampleTest` | RED 2 |
| **M-walk-by-inserted** 걷기를 적재 순서로 고른다 | 같은 클래스 | RED 1 |
| **M-blank-number-unguarded** 두 자리의 번호 guard 를 되돌린다 | 같은 클래스 · `OpeningCollectionLedgerTest` | RED 2 |
| **M-amount-strict-scale** 금액의 자릿수 검사를 없앤다 | `SnapshotWriterTest` | RED 1 |
| **M-walk-one-way** AXIS 줄의 걷기 요구를 단방향으로 | `FileAttemptLedgerTest` | RED 2 |
| **M-final-not-settled** 확정 실패를 미정착으로 | `CollectOpeningResultsUseCaseTest` | RED 1 |
| **M-no-retry-cap** 재호출 상한을 없앤다 | 같은 클래스 | RED 1 |
| **M-b-conclusionless-absent** 결말 없는 축을 항목 부재로 | 같은 클래스 · `CollectionAttemptLedgerTest` | RED 2 |
| **M-structure-as-final** 구조 붕괴를 확정 실패로 | `CollectionAttemptLedgerTest` | RED 1 |
| **M-element-null** 예비가격 배열의 원소만 null 로 | `SnapshotAmountContractTest` | RED 1 |
| **M-parse-truncate** `parseAmount` 가 소수를 버린다(A 과소 합산) | 같은 클래스 | RED 3 |
| **M-refusal-counted** 예산 거부를 일시 실패로 되돌린다 | `CollectOpeningResultsUseCaseTest` | RED 1(넷째 기동이 부르지 않음) |
| **M-unclassified-final** 미지 코드를 확정으로 | `CollectionAttemptLedgerTest` | RED 3 |
| **M-count-whole-life** 상한을 디렉터리 생애 전체로 센다 | `CollectionAttemptLedgerTest` | RED 1 |

## 착수 실측 표 갱신

| 항목 | 착수 문면 | 실측 |
|---|---|---|
| probe C2 기동 셋(찢어진 끝 줄 → A·B·C) | A ACCEPT · B·C REJECT | base 에서 **RED 재현**(기동 B 가 「앞부분이 장부와 다르다」) → 수정 뒤 셋 다 수락 |
| probe W6b(옛 공고 목록 행 뒤 6G 행) | `noticedOn=null · method=null` | base 에서 **RED 재현** → 수정 뒤 뒤 관측의 값 |
| probe W7(walk 없는 SUCCEEDED 줄) | 행 0 · 축 완료 | base 에서 그 줄이 **읽혔다** → 수정 뒤 읽기 거부 |
| `SourceBatch(` 생성 자리(production) | 2(빈 배치 하나 · 쪽 배치 하나) | 둘 다 걷기 이름을 실을 수 있다 → 기본값·nullable 제거(D-4 ⓓ) |
| `CollectionAttempt(` 생성 자리(production) | 3 | 그대로 3 — 양방향 `init` 이 셋 다 명시를 요구한다 |

## acceptance

CI `check` job 의 명령 그대로에 `clean` 과 `--no-build-cache` 를 더한 집합이다(CI 는 `check` 를
캐시와 함께 돌린다 — 더 좁은 쪽이라 이 집합이 그것을 덮는다).

HEAD `44a70d5f`(재작업 1 의 마지막 산출물 커밋) 실측:

| 명령 | exit |
|---|---|
| `./gradlew --no-daemon clean` | 0 |
| `./gradlew --no-daemon check --no-build-cache` | 0 |
| `./gradlew --no-daemon qualityBaseline` | 0 |
| `./tools/one-command-check.sh` | 0 (「Kotlin 전건 + Python 전건 통과」) |

test XML 합은 **task 별로** 적는다(vr r1 L-3 — 앞 판은 `test` task 만 세면서 그 기준을 적지 않았다):
`test` 2,540 · skipped 4 · failures 0 · errors 0, `compatibilitySmokeTest` 8 · failures 0.
전체 합 2,548. base 대비 `test` 는 **+35** 다(되돌린 트리에서 같은 명령으로 2,505 를 실측했다 —
rollback ⑤, 같은 기준). 이 slice 가 더한 test 수와 방향이 맞는다.

`container` job 은 돌리지 않았다 — 이 slice 는 그 job 이 돌리는 이미지·compose·실서버 경로를
바꾸지 않는다(실행 상태 디렉터리 E2E 는 `check` job 의 `:app:test` 안에 있다).

**중간 실측 여섯** — `check` 가 실제로 여섯 번 막았고 그 이력은 결함이 아니라 게이트가 작동했다는 증거다.
`:adapters:detekt` 3건(줄 길이 둘 · 한 함수의 return 수) → 술어를 이름 있는 함수로 분리.
`:adapters:ktlintTestSourceSetCheck`·`:procurement:ktlintTestSourceSetCheck` 4건(연속 빈 줄 · 본문
한 줄 · import 순서) → `ktlintFormat`. `:procurement:cpdCheck` 1건 — 이어 돌기의 두 물음이 (공고, 축)
묶기 네 줄을 복제했다 → `byNoticeAndAxis` 하나로. **cpd 의 지적이 옳았다**: 두 물음이 같은 묶음을
쓴다는 사실이 코드에 없었다.

재작업 1 에서 셋 더: `:adapters:detekt` 파일당 함수 수(금액 정수 술어를 **판정하는 자리 옆**으로 옮겼다 —
상한을 비껴가는 이동이 아니다) · `:adapters:sizeGate` 파일 500줄 **둘**(production·test 각각 501 —
형식 판별 축을 제 파일로 갈랐다, `FileAttemptLedger.kt` 를 가른 경계와 같은 이유) · 그 분리가 만든
이름 충돌(`internal` 은 패키지 전체에 보인다 — 하네스의 파일 수준 이름에 접두를 줬다).

## 새 public 표면 (D-6G2d-6 기대값 0 — 실제 여덟, 이탈 2)

| 표면 | 밖에서 허용하는 것 |
|---|---|
| `AttemptOutcome.FinalFailure(code)` | 결말 줄을 짓는 누구나 「다시 부르지 않는 실패」를 말할 수 있다. 코드 문자열만 나르고 새 주입 자리는 없다 |
| `AxisConclusion(outcome, walk)` — 형태 변경(`settled` 파생, `usesRows` 신설) | 판독이 결말 **어휘**를 본다. 걷기가 non-null 이라 「모름」을 만들 수 없다 |
| `AttemptHistory.axisResumptions(axisRetryLimit)` — `settledAxes()` 제거 | 이어 돌기의 답을 상한과 함께 묻는다. 1 미만 상한은 `require` 가 막는다 |
| `DetailFetchGates.axisRetryLimit`(기본값 없음) | 배선이 재호출 상한을 정한다. 1 미만은 `init` 이 막고, 운영 값은 정책 데이터 한 자리다 |
| `SourceBatch.observedAt: Instant`(기본값·nullable 제거) | 포트 구현이 걷기 이름을 **반드시** 싣는다 — 빠뜨림이 컴파일 오류다. 앞 판의 위험(아무 걷기나 실을 수 있다)은 그대로이고 출하 어댑터는 한 자리에서 짓는다 |
| `CollectionAttempt.walk`(기본값 제거 · 양방향 `init`) | AXIS 줄은 걷기를 반드시, 그 밖의 줄은 절대 갖지 못한다 |
| `NoticeNumber.ofOrNull(raw)` | 형태를 어긴 원문 번호를 예외 없이 거른다. 정규화 규칙은 여전히 한 자리(`of` 와 같은 함수를 지난다) |
| `SnapshotExtraction.unusableRawRows` | 판독이 「키가 서지 않아 버린 원문 행 수」를 읽는다 — 네 항 항등식 **밖**이다 |

### 재작업 1 이 더한 표면 둘

| 표면 | 밖에서 허용하는 것 |
|---|---|
| `AttemptOutcome.Refused(code)` | 결말 줄을 짓는 누구나 「호출이 나가지 않았다」를 말할 수 있다. `isSettled` 는 `false` 라 그 축은 다시 불리고, 재호출 상한은 이 갈래를 **세지 않는다** — 코드 문자열로 되읽어 분류하는 길을 열지 않는다 |
| `SnapshotExtraction.fractionalAmounts` | 판독이 「소수부로 없는 값이 된 금액 칸 수」를 읽는다(집계는 통째로 하나). `unusableRawRows` 와 같은 자리 — 네 항 항등식 **밖**이다 |

좁아진 표면 하나: `DetailFetchGates` 의 `init` 에서 상한 하한 검사가 사라졌다(쓰는 자리 하나로,
D-6G2d-18). 생성자 형태는 그대로다.

**모듈 밖에 열리지 않는 것**(cr r1 L-3 정정 — 앞 판은 전부 `internal` 이라고 적었으나 다섯은
`private` 이다): `internal` 은 `RUN_STATE_FORMAT_VERSION` · `RunStateFormatFault` ·
`RunStateFormatRefusedException` · `requireCurrentFormat` · `STAGED_ATTEMPT_NAME` · `AXIS_WALK_REQUIRED` ·
`isWonInteger` · `AssemblyTally`. **파일 범위 `private`** 은 `EXCLUDED_FROM_LEDGERED_SET` · `WalkRows` ·
`usableAxis` · `fromLedgeredWalk` · `failureOf` · `conclusionOf` · `STRICT_FORMAT_VERSION`. 결론(밖에
열리지 않는다)은 `private` 쪽이 더 강하게 참이다.

## 알려진 제한

- **(a) 모든 상세 축이 빈 응답으로 정착**하면 그 공고는 `sampled_without_detail` 로 계수된다 —
  `raw_observation` 에 그 공고의 상세 행이 **있는데도** 그렇다. 「상세를 못 받았다」는 사유와 다르다.
- **(b) 일부 축만 빈 응답**이면 행이 조립되고 Python 의 **행 단위 제외**로 떨어진다. verifier r5-t §5
  실측: 개찰완료가 비면 투찰 금액 부재, 예비가격 상세가 비면 개찰 기초금액 부재, 기초금액 상세가
  비면 기초금액 부재·지연 규칙에 걸린다. 둘 다 **잘린 값이 채점에 들어가지는 않는다**. 정확한 사유
  어휘는 `schema_version` 인상이라 이 slice 밖이다(운영자 결정 A-2) → `OPEN-6G2D-EMPTY-AXIS-REASON`.
- **(c) 소수부 금액**의 처분은 칸에 따라 다르다(D-6G2d-15 로 교정). 스칼라 다섯(기초금액·순공사원가·
  예정가격·개찰 기초금액·투찰금액)은 스키마가 `int | null` 이라 그 칸만 비고 Python 의 **기존 값 결측
  제외**로 떨어진다. 집계 둘(`a_value`·`reserve_prices`)은 원소 자리의 `null` 이 형태 위반이므로
  **집계 전체**가 비고 A 결측·예비가격 결측 제외로 떨어진다. 어느 쪽도 전용 사유 어휘는 없다 —
  발생 수는 러너 로그가 공시한다. 원천이 원 단위 정수를 낸다는 것은 조사 문서의 관측이다.
- **(d) 키가 서지 않은 원문 행**의 수는 러너 로그 한 칸으로만 공시된다. manifest 어휘는 늘지 않는다
  (그 행은 어느 표본 공고에도 속하지 않아 네 항 항등식 밖이다).
- **(e) 재호출 상한 3 은 잠정값**이고 **셈 창은 실행 상태 디렉터리의 생애 전체**다(D-6G2d-19).
  `doneWith` 는 그 (공고, 축)의 일시 실패를 **누적으로** 센다 — 하루치도 실행치도 아니다. 승인 수집이
  3~4일에 걸치므로 서로 다른 날의 일시 실패 셋이 같은 축을 확정으로 접는다(네 번째 실행에서 받을 수
  있었을 축이다). 다만 셈은 그 축의 **마지막 정착(성공·빈 응답) 뒤**부터다(vr r1 M-2) — 한 번 끝난 축의
  앞 실패는 세지 않는다. 관문 거부도 이 셈 밖이므로(D-6G2d-16) 상한이 소진된 실행이 창을 태우지 않는다.
  순서는 원장의 **덧붙인 순서**로 읽는다: 계약 문면은 `at` 순서를 들었으나 시계가 뒤로 간 실행이 있으면
  `at` 정렬이 앞 실행의 결말을 「마지막」으로 만들어 추출 쪽의 「마지막 줄이 이긴다」(D-6G-58)와 어긋난다
  (6G verifier probe W2 가 그 시계를 실측했다). 이탈 12.
  `policy-values.md` 의 운영자 승인 표(P-1~P-6)에 이 축이 없다 → `OPEN-6G2D-AXIS-RETRY-LIMIT`(신설)은
  **값과 창**(디렉터리 생애 / 일 단위)을 함께 묻는다. 이 라운드에서 창은 바꾸지 않았다.
- **(f) 실행 상태 형식 version 2 이전의 디렉터리는 기동되지 않는다.** 이 호스트에 실행 상태
  디렉터리는 test 임시 디렉터리뿐이고 실수집은 한 번도 돌지 않았다 — 옛 디렉터리를 관용할 이유가
  없는 유일하게 싼 때다(6G verifier r5-t L-1 의 노출 판단과 같은 근거).
- **(g)** 찢어진 끝 줄 표식이 오늘치 상한에서도 한 칸을 계속 뺀다(6G cr r5-t L-7)는 이 slice 밖이다 —
  보수적 방향이고 계약이 경계 밖으로 두었다.
- **(h)** `a_value.open_at` 은 스키마 §2.2 가 `datetime`(널 허용 아님)으로 두는데, A 구성 항목은 있고
  공개일시가 없는 원문이 오면 그 묶음이 `{total:<수>, open_at:null, …}` 로 렌더된다 — D-6G2d-15 와
  **같은 형태 위반 계열**이고 금액 칸이 아니라 이 라운드의 표적 밖이다(구현 레인 관측, 미수정).
  고치는 방향은 「공개일시 없으면 `a_value` 통째로 null」이지만 그것은 A 를 가진 공고의 제외 사유를
  바꾸는 데이터 정확성 결정이라 계약 항목이다.
- **(j)** version 2 아래에서 쓰인 옛 `PENDING` 줄이 그대로 수락되어 상한 +1 소비·재호출로 흐른다 —
  보수 방향(덜 세지 않는다)이라 등재만 한다(D-6G2d-19 문면).
- **(i)** `aValueTotalOf` 는 **결측** 구성 항목을 빼고 나머지를 더한다(6G 부터의 기존 부채, vr r1 참고).
  이 라운드가 바꾼 것은 **소수부** 처분뿐이다 — 결측 항목의 과소 합산은 그대로다.

## 6G evidence 정정 (D-6G2d-7)

6G `commands.md` 를 되쓰지 않고 여기서 선언한다. 그 문서의 D-6G-70 행(「기동 수락 · 상한 불변」)은
**기동 A 한 번만 여는 test 위의 서술**이었고, 기동 B 는 거부됐다 — 그 자리가 이 slice 의 D-6G2d-1 이다.
D-6G-68 행의 「원장 이전 원문은 D-6G-58 그대로」는 **결말 줄이 있는 축에 대해서만** 참이었다: 결말 줄이
없는 축(목록 축 둘)은 선별이 사라져 전 관측이 합쳐졌고, 그 자리가 D-6G2d-3 이다. 두 행은 6G 종결 시
「미이행 — OPEN」으로 바뀌어 있고, 이 slice 가 그 OPEN 셋을 닫는다.

## 이탈

1. **out_scope 파일 넷을 편집했다.** `adapters/.../koneps/KonepsPageUriBuilder.kt`(빈 배치가 걷기
   이름을 싣는 한 자리)·`KonepsCallGate.kt`(호출 줄이 걷기 없음을 **명시**하는 한 자리) —
   scope.md out_scope 가 이름으로 든 경로이고, 기본값 제거의 기계적 귀결이다(전송 거동 무변경).
   `adapters/.../persistence/JdbcCollectedAxisStore.kt` — D-6G2d-8 ⓐ 결정문이 이름으로 든 자리인데
   in_scope **경로 목록**에는 없다. `app/src/main/.../collection/SnapshotExtractionRunner.kt` — 계수를
   공시하는 로그 한 칸(어댑터는 로거를 쓸 수 없다, 구조 게이트).
2. **D-6G2d-6 「새 public 표면 0」을 어긴다** — 여덟(위 표). 여섯은 **좁히는** 방향이고(기본값 제거·
   nullable 제거·메서드 제거·양방향 `require`), 둘은 새 값을 나른다(`FinalFailure`·`unusableRawRows`).
   ⓒ 의 상한을 「정책 값으로」 두라는 요구와 ⓐ 의 「계수 공시」 요구는 표면 없이 이행할 수 없다.
3. **D-6G2d-8 ⓒ 가 든 결정적 실패 넷 중 구조 붕괴는 일시로 두었다.** 이 저장소에서 HTTP 5xx 는 봉투가
   없어 그 사유로 오고(개찰 예산 E2E 의 5xx 절단이 그 사유다 — 확정으로 두었을 때 그 E2E 가 RED 였다),
   확정으로 두면 일시적 5xx 한 번이 그 축을 영구히 버려 **느린 시간대에 몰린 공고만 빠지는 비랜덤
   결측**이 된다. 어댑터의 같은 판단(재개 가능 축에 구조 붕괴를 둔다)과도 일치한다. 백스톱 둘은
   확정이다 — 이어 돌기는 cursor 없이 1쪽부터 다시 걷는다.
4. **ⓒ 의 재호출 상한이 `KonepsCollectionPolicyData` 가 아니라 `DetailFetchGates` 에 있다.** 수집
   use case 는 전자의 **멤버를 읽을 수 없다**(구조 게이트 D-6F8-1 우회 1 — 실측으로 RED). 그 금지는
   옳으므로 게이트를 넓히지 않고 값을 옮겼다.
5. `config/quality/architecture-policy.properties` 변경은 **등재 해제** 한 줄이다(use case 가 더는
   이름 붙이지 않는 타입 하나). 「허용 == 관측」 등식이 요구하는 좁히는 방향이고 술어는 그대로다.
6. **D-6G2d-8 ⓑⓒ 는 한 커밋이다** — 두 항목이 같은 술어(`axisResumptions`)를 바꾼다.
7. **D-6G2d-8 ⓐⓑⓒ 의 RED 는 base 가 아니라 변이로 실측했다.** ⓒ 는 새 결말 타입 없이는 test 가
   컴파일되지 않아 base-RED 가 구조적으로 불가능했고, ⓐⓑ 는 구현이 test 보다 앞섰다. 잠그는 술어는
   같지만 계약의 RED-우선 순서와 다르다.
8. `adapters/src/test/.../snapshot/` 의 test 파일을 **갈랐다** — 파일 500줄 한도를 넘었기 때문이고
   경계는 production 의 분할과 같다(원장 줄 형태 · 형식 판별 · 공통 하네스). 재작업 1 에서
   production 도 같은 이유로 갈랐다(형식 판별 축).
9. **D-6G2d-18 의 「형식 거부가 러너 종료 코드·로그에 닫힌 어휘로 나오게」는 하지 않았다.** 계약이
   허용한 「불가하면 이탈로 사유 기록」이다. 사유: 실행 상태 디렉터리는 **네 빈의 의존**이고(상한 seed ·
   수집 소스 · use case · 러너) 「여는 자리가 곧 잠금 자리」가 D-6G-57 의 설계다 — 러너의 `catch` 안으로
   옮기려면 그 배선과 잠금·seed 순서 보장을 함께 다시 짜야 하고, 그 재설계는 이 라운드의 표적(데이터
   정확성 둘)보다 크고 위험하다. 최소안(배선에서 잡아 로그 한 줄)도 `app/src/main/.../wiring/**` 세
   파일을 건드리는데 그 경로는 in_scope 가 아니고, 로거 사용자 게이트 등재까지 따라온다. **이 라운드가
   한 것**: 거부 사유가 기동 실패 출력에서 그대로 grep 되는 닫힌 토큰을 갖는다(`RUN_STATE_FORMAT_MISSING`
   ·`RUN_STATE_FORMAT_MISMATCHED`). 전용 종료 코드는 남았다.
10. **계약 목록 밖 low 둘을 문면만 고쳤다** — 빈 번호 guard 의 도달 가능성 근거(cr r1 L-1: 오늘의 수집
   경로는 적재 **전에** 떨어뜨린다, 이것은 방어 심화다)와 걷기 부재 `requireNotNull` 의 도달 가능성
   서술(cr r1 L-4: 발화하지 않는 이중 잠금). 코드 거동 무변경. 알면서 거짓인 KDoc 을 남기는 것이 이
   slice 가 반복해서 고치는 결함 계열이라 함께 담았다.
12. **D-6G2d-19 의 「`doneWith` 가 `at` 순서를 읽는다」를 따르지 않았다** — 원장의 덧붙인 순서로 읽는다.
   사유는 위 알려진 제한 (e): `at` 정렬은 시계가 뒤로 간 실행에서 앞 실행의 결말을 「마지막」으로 만들어
   추출 쪽 규율과 어긋난다. 정착 창을 고르는 성질(계약이 요구한 것)은 그대로 성립한다.
11. **vr r1 L-5 는 이 레인이 고치지 않는다** — scope.md 의 「OPEN 수령·신설」 표에
   `OPEN-6G2D-AXIS-RETRY-LIMIT` 행이 없다. slice 계약은 세션 모델 단독 소관이라 보고만 한다.

## 게이트 실측(evidence 편집 전)

- clean-tree: `git status --porcelain -- <in_scope 개별 인자>` 빈 출력. 양성 대조 한 번 — in_scope
  파일 하나의 끝에 줄을 붙여 잡히는지 확인하고 **비파괴 절삭**으로 되돌렸다(`checkout --` 금지).
- 누출 스캔: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/6g2d/`
  — evidence 경로 exit 1(매치 없음). 산출물 경로의 유일한 매치는 이 slice 가 **바꾸지 않은** 줄이고
  (값 이름 하나), 이 slice 가 더한 줄만 따로 스캔하면 0 이다. 정본 판정은 `check` 안의 해당 게이트다.
- 좌표: `reports/evidence/m6/6g2d/` 에 `<파일>.<확장자>:<숫자>` 형태 0건. 역방향(이 slice 가 편집한
  파일 stem 을 가리키는 좌표) 0건.
