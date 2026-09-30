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
| **D-6G2d-8 ⓓ** 소수 금액은 행 단위 부재 | 이행 | `jsonAmount` 이 소수부 있는 값을 그 칸만 `null` 로. 끝자리 0 은 소수부가 아니다 |

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

HEAD `21d979e9`(마지막 산출물 커밋) 실측:

| 명령 | exit |
|---|---|
| `./gradlew --no-daemon clean` | 0 |
| `./gradlew --no-daemon check --no-build-cache` | 0 |
| test XML 합(`check` 직후) | 2,527 tests · skipped 4 · failures 0 · errors 0 |
| `./gradlew --no-daemon qualityBaseline` | 0 |
| `./tools/one-command-check.sh` | 0 (「Kotlin 전건 + Python 전건 통과」) |

test 수는 base 대비 **+22** 다(같은 명령을 되돌린 트리에서 돌려 2,505 를 실측했다 — rollback ⑤).
이 slice 가 더한 test 수와 방향이 맞는다.

`container` job 은 돌리지 않았다 — 이 slice 는 그 job 이 돌리는 이미지·compose·실서버 경로를
바꾸지 않는다(실행 상태 디렉터리 E2E 는 `check` job 의 `:app:test` 안에 있다).

**중간 실측 셋** — `check` 가 실제로 세 번 막았고 그 이력은 결함이 아니라 게이트가 작동했다는 증거다.
`:adapters:detekt` 3건(줄 길이 둘 · 한 함수의 return 수) → 술어를 이름 있는 함수로 분리.
`:adapters:ktlintTestSourceSetCheck`·`:procurement:ktlintTestSourceSetCheck` 4건(연속 빈 줄 · 본문
한 줄 · import 순서) → `ktlintFormat`. `:procurement:cpdCheck` 1건 — 이어 돌기의 두 물음이 (공고, 축)
묶기 네 줄을 복제했다 → `byNoticeAndAxis` 하나로. **cpd 의 지적이 옳았다**: 두 물음이 같은 묶음을
쓴다는 사실이 코드에 없었다.

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

`internal` 이라 모듈 밖에 열리지 않는 것: `RUN_STATE_FORMAT_VERSION` · `RunStateFormatFault` ·
`RunStateFormatRefusedException` · `STAGED_ATTEMPT_NAME` · `EXCLUDED_FROM_LEDGERED_SET` ·
`AXIS_WALK_REQUIRED` · `WalkRows` · `usableAxis` · `fromLedgeredWalk` · `failureOf`.

## 알려진 제한

- **(a) 모든 상세 축이 빈 응답으로 정착**하면 그 공고는 `sampled_without_detail` 로 계수된다 —
  `raw_observation` 에 그 공고의 상세 행이 **있는데도** 그렇다. 「상세를 못 받았다」는 사유와 다르다.
- **(b) 일부 축만 빈 응답**이면 행이 조립되고 Python 의 **행 단위 제외**로 떨어진다. verifier r5-t §5
  실측: 개찰완료가 비면 투찰 금액 부재, 예비가격 상세가 비면 개찰 기초금액 부재, 기초금액 상세가
  비면 기초금액 부재·지연 규칙에 걸린다. 둘 다 **잘린 값이 채점에 들어가지는 않는다**. 정확한 사유
  어휘는 `schema_version` 인상이라 이 slice 밖이다(운영자 결정 A-2) → `OPEN-6G2D-EMPTY-AXIS-REASON`.
- **(c) 소수부 금액**은 그 칸만 부재가 되고 **전용 사유 어휘가 없다** — Python 의 기존 값 결측 제외로
  떨어진다. 원천이 원 단위 정수를 낸다는 것은 조사 문서의 관측이므로 발현 가능성은 낮다.
- **(d) 키가 서지 않은 원문 행**의 수는 러너 로그 한 칸으로만 공시된다. manifest 어휘는 늘지 않는다
  (그 행은 어느 표본 공고에도 속하지 않아 네 항 항등식 밖이다).
- **(e) 재호출 상한 3 은 잠정값**이다 — `policy-values.md` 의 운영자 승인 표(P-1~P-6)에 이 축이 없다.
  실수집 뒤 재호출 분포를 보고 정한다 → `OPEN-6G2D-AXIS-RETRY-LIMIT`(신설).
- **(f) 실행 상태 형식 version 1 이전의 디렉터리는 기동되지 않는다.** 이 호스트에 실행 상태
  디렉터리는 test 임시 디렉터리뿐이고 실수집은 한 번도 돌지 않았다 — 옛 디렉터리를 관용할 이유가
  없는 유일하게 싼 때다(6G verifier r5-t L-1 의 노출 판단과 같은 근거).
- **(g)** 찢어진 끝 줄 표식이 오늘치 상한에서도 한 칸을 계속 뺀다(6G cr r5-t L-7)는 이 slice 밖이다 —
  보수적 방향이고 계약이 경계 밖으로 두었다.

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
8. `adapters/src/test/.../snapshot/` 의 test 파일 하나를 **둘로 갈랐다**(원장 줄 형태 test 를 새 파일
   로) — 파일 500줄 한도를 넘었기 때문이고 production 의 분할과 같은 경계다.

## 게이트 실측(evidence 편집 전)

- clean-tree: `git status --porcelain -- <in_scope 개별 인자>` 빈 출력. 양성 대조 한 번 — in_scope
  파일 하나의 끝에 줄을 붙여 잡히는지 확인하고 **비파괴 절삭**으로 되돌렸다(`checkout --` 금지).
- 누출 스캔: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/6g2d/`
  — evidence 경로 exit 1(매치 없음). 산출물 경로의 유일한 매치는 이 slice 가 **바꾸지 않은** 줄이고
  (값 이름 하나), 이 slice 가 더한 줄만 따로 스캔하면 0 이다. 정본 판정은 `check` 안의 해당 게이트다.
- 좌표: `reports/evidence/m6/6g2d/` 에 `<파일>.<확장자>:<숫자>` 형태 0건. 역방향(이 slice 가 편집한
  파일 stem 을 가리키는 좌표) 0건.
