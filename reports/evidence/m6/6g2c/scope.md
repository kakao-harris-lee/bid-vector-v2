# M6/6G-2c — 6G 리뷰 후속 일괄(형식 무관분): 잠금 실측 · 실행 상태 표면 · 공시 어휘 (계약 초안 2026-09-30 · 착수 2026-10-04)

> **지위: 착수(계약 갱신 r0-b, 2026-10-04).** 초안은 `docs/m6-6g2-contracts` 커밋 `576d92f7`(2026-09-30). 착수 실측과 운영자 결정 A-1~A-3 은 아래
> 「계약 갱신 r0-b」 절이 정본이고, 초안 표의 처분이 그 절과 다르면 **r0-b 가 이긴다**(표에는 → D-6G2c-nn 포인터만 달았다).
> **운영자 결정 A-3 으로 이 slice 는 셋으로 갈린다** — ① **이 PR(6G-2c)**: 실행 상태 **형식·스키마에 닿지 않는** 항목만 ② **6G-2g**(신설, 게이트 OPEN 여섯)
> ③ **6G-2c-형식**: 스냅숏 추출 뒤에만 머지할 수 있는 항목(아래 D-6G2c-28). 수집은 지금 진행 중(디렉터리 `m6-6g`, 3일째)이라 형식에 닿는 변경은 머지할 수 없다.
> 수령하는 OPEN: **`OPEN-6G-REVIEW-FOLLOWUPS`**(6G 계약 D-6G-75 — code-review r5 M-3·LOW 잔여, verifier r5 L-7·L-8·L-9, privacy r2 INFO).
> **항목 목록은 6G 표적 재검증과 PR 리뷰 결과로 갱신한다** — 거기서 나온 low 도 이 slice 가 받는다.

- base: **`ecdc9d9f`**(main, PR #58 머지 커밋). worktree `bid-vector-v2-m6-6g2c` · 브랜치 `m6-6g2c/2026-10-04`.
- 레인: `kotlin-implementer`(항목 K) · `ml-implementer`(항목 P). 두 레인의 파일이 겹치지 않는다 — 공유 파일(`milestone-6.md` · 이 scope.md · `config/quality/gate-tests.properties`)은 팀장과 K 만 만진다(P 는 `ml-engine/**` 안에서만).

## 왜 이 slice 인가

6G 는 재작업 상한에 닿아 **실수집에 닿는 것만** 고치고 닫았다(D-6G-65·75). 남은 것은 셋으로 갈린다: 게이트 둘(6G-2a·6G-2b 가 받는다)과
이 slice 가 받는 **작은 항목들**이다. 하나하나는 low 또는 info 지만 같은 자리(실행 상태 디렉터리 · 잠금 · 판정문 공시)에 모여 있어 한 번에 본다.

이 slice 의 항목은 **오늘 출하 거동을 틀리게 만들지 않는다** — 전부 fail-closed 이거나 회귀 자물쇠의 부재이거나 문면이다.

## 실수집과의 관계 — 머지 시점 제약

6G 실수집은 **며칠에 걸쳐 같은 실행 상태 디렉터리**로 이어 돈다. 그 사이에 실행 상태의 형식이나 해시가 바뀌면 진행 중인 디렉터리가 기동을 거부한다.

- 항목마다 **「실행 상태 형식에 닿는가」** 를 표에 적는다.
- **닿는 항목은 실수집이 끝나고 스냅숏을 추출한 뒤에만 머지한다.** 닿지 않는 항목은 아무 때나.
- 그래서 이 slice 는 PR 을 **둘로 나눌 수 있다**(형식 무관 항목 먼저). 나누는지는 착수 시 실수집 진행 상태를 보고 정한다.

## 착수 실측 (착수 시 채운다)

| 항목 | 값 |
|---|---|
| base SHA | `ecdc9d9f` |
| 6G 표적 수정(D-6G-74)이 이미 닫은 항목 | vr r5 M-2 · L-2 · L-4 · L-5 · L-6, cr r5 LOW 한 줄짜리(항등식 test 항 넷 · 미사용 import · PENDING `outcome` 표기) — 초안 표에 없던 것들이라 뺄 행 없음. vr r5-t **L-2(`budget-since` 잔존 설정)** 는 저장소에서 그 설정이 사라져(`grep -rn 'budget-since\|budgetSince' app/src adapters/src workflow/src` 0건) **닫힘**. vr r5-t **L-3** 은 verifier 가 「low(확인) — 걷기 식별자는 한 자리에서 지어진다」로 적은 확인 항목이라 할 일 없음 |
| 6G 뒤 slice 들이 이 slice 로 넘긴 항목 전수 | D-6G-79(cr r5-t M-4 · L-3~L-7 · vr r5-t 저위 변이 둘 · vr r5-t M-4) · 6G-2d(D-6G2d-22 ① · cr r1 M-2) · 6G-2e(D-6G2e-25 ①②③④⑤⑥⑦⑧ · `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE` · `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT`) · 6G-2a(`OPEN-6G2A-CENSUS-DERIVED-LOCALS`) · 6G-2f(`OPEN-6G2F-NOTICE-LIST-ROWS` · `OPEN-6G2D-FRAME-REWALK` · `OPEN-6G2F-MAX-PAGES-PROVENANCE`) · 6G-2b 게이트 OPEN 여섯 · 6G-2d `OPEN-6G2D-EMPTY-AXIS-REASON` — 처분은 D-6G2c-19~28 |
| 실수집 진행 상태 | **진행 중** — 디렉터리 `~/.local/bid-vector-run-state/m6-6g/`, 2026-10-02 시작, 표본 24,000 중 HTTP 7,489(2일) + 3일째 진행. 쪽 999(6G-2f) 뒤 하루 ~1,000 개찰완료 호출 → **추출까지 약 25일**. 형식에 닿는 항목은 이 PR 에 없다 |
| 코드 실측(D-7·9·11 의 전제) | `NoticeKeyHash` salt 없음(그대로) · 명칭 셋은 스키마 §(`successful_bid_method_name` string\|null · `progress_division` string\|null)에 **원문 자유 텍스트**로 실린다 → D-9 는 스키마 변경(**6G-2c-형식**) · `DivisionCoverage` 는 `COVERED if count else UNDERPOWERED` 두 값(`report.py`) · `RunStateLock.release()` 는 인터페이스 public, `RunStateDirectory.close() = lock.release()` · `sampleList: FileSampleListLedger` 는 held 가드 없는 public val · `_APPROVED_SEED_KEYS` 두 벌(`evaluation/policy.py` · `evaluation/backtest/policy.py`) · `unusableRawRows` 는 러너 로그 한 줄에만(manifest 밖) |

## 항목과 처분

처분은 셋 중 하나다: **구현**(test 와 함께) · **등재**(고치지 않고 사유와 함께 알려진 제한으로) · **운영자 결정**.

### K — Kotlin

| ID | 출처 | 항목 | 처분 | 형식에 닿는가 |
|---|---|---|---|---|
| **D-6G2c-1** | cr r5 M-3 | 잠금 E2E 둘이 **같은 JVM** 에서 두 번째 `tryLock()` 을 부른다. 잡히는 것은 `OverlappingFileLockException` 이고 두 프로세스의 경우는 재지 않는다. `tryAcquire` 를 JVM 안 맵 가드로 바꾸는 변이가 초록 | **구현** — 별 프로세스(자식 JVM 또는 셸)가 잠금 파일을 든 상태에서 두 갈래를 각각 기동 → 0 요청 · `ALREADY_RUNNING`. 변이(JVM 안 가드) → RED. verifier r5 가 손으로 잰 것을 저장소에 둔다 | 아니오 |
| **D-6G2c-2** | cr r5 M-3 부수 | `runCatching` 이 `IOException` 도 삼켜 「잠글 수 없는 파일시스템」과 「다른 실행 중」이 같은 코드로 보고된다 | **구현** — 사유 어휘를 둘로 가른다(닫힌 어휘에 하나 추가). 둘 다 0 호출로 멈추는 것은 그대로 | 아니오(로그·종료 사유만) |
| **D-6G2c-3** | vr r5 L-7 | 「잠금을 밖에서 들고 **각 갈래**를 기동」 중 공고 목록 갈래 E2E 가 없다 | **구현** — D-6G2c-1 의 test 가 두 갈래를 다 덮는다 | 아니오 |
| **D-6G2c-4** | cr r5 L-1 · vr r5 L-9 | `RunStateDirectory.sampleList` 는 잠금을 못 든 인스턴스에서도 쓰기 가능하다. `RunStateLock.release()` 가 public 이라 살아 있는 실행의 잠금만 풀 수 있다(원장은 쓰기 가능한 채) | **구현** — 표본 원장도 잠금 없는 인스턴스에서 쓰기를 거부(시도 원장과 같은 감싸기). `release` 는 가시성을 내리거나 `close` 하나로 합쳐 「잠금만 풀린 원장」 상태를 없앤다. **값 획득 축**: 가시성 변경이 새 public 표면을 만들지 않는지 표에 적는다 | 아니오(파일 형식 무변경) |
| **D-6G2c-5** | vr r5 L-9 | 한 `RunStateDirectory` 인스턴스를 두 스레드가 쓰면 장부가 깨진다(probe P2b). 운영 경로는 순차다 | **등재** — 「한 인스턴스는 한 스레드」를 KDoc 과 알려진 제한에 적는다. 동기화를 넣지 않는다(쓰는 자리가 없다) | — |
| **D-6G2c-6** | cr r5 L-8 | 두 수집 배선이 각자 `RunStateDirectory`·`CallBudgetLedger` 빈을 등록한다. 두 모드를 **함께** 켜면 컨텍스트가 뜨지 않는다(fail-closed). `CollectionProperties.runStateDir` KDoc 은 한 프로세스에서 둘을 켜는 판을 상상하게 한다 | **구현(문면 + test 하나)** — KDoc 을 「두 갈래는 따로 기동한다」로 좁히고, 두 모드를 함께 켠 기동이 실패함을 test 로 고정한다. `@Qualifier` 로 공존시키지 않는다 — 한 프로세스에 두 갈래를 두는 요구가 없다 | 아니오 |
| **D-6G2c-7** | privacy r2 INFO-1 | `NoticeKeyHash` 는 `sha256("<공고번호>/<차수>")` 이고 salt 가 없다. 공고번호는 공개 정보라 해시를 되돌릴 수 있다. 이 해시가 스냅숏 · 표본 목록 · 시도 원장에 실린다 | **운영자 결정 A-1 → (가) 그대로(D-6G2c-16)** — 등재로 닫는다 | **예** — 그래서 (나)는 처음부터 선택지 밖이었다 |
| **D-6G2c-8** | privacy r2 INFO-3 | `state.json` 의 `directory_id` 는 절대 경로의 sha256 이다. 경로에 로컬 계정명이 들어 있다. 파일은 저장소 밖에 있다 | **등재** — 이 값은 「디렉터리를 다른 자리로 복사했는가」를 재는 것이라 경로에서 와야 한다. 임의 UUID 로 바꾸면 복사본이 같은 값을 갖는다. 통제는 저장소 밖 강제(`requireOutsideRepository`)다 | — |
| **D-6G2c-9** | privacy r2 INFO-5 | 명칭 셋(낙찰방법 · 예정가격 결정방법 · 진행 상태)이 원문 그대로 스냅숏에 간다. Python 은 판정 조건으로만 쓰고 판정문에 싣지 않는다 | 착수 실측: **원문 자유 텍스트**다 → 닫힌 어휘화는 스키마 변경 → **6G-2c-형식으로 이관(D-6G2c-28)**. 이 PR 에서는 없음 | **예** |
| **D-6G2c-10** | privacy r1·r2 INFO-A | 상호가 raw 관측에 표본 공고 규모로 적재된다 | **등재 + 연결** — 6B-3 보존 기간(90일, 운영자 결정 2026-09-26)이 raw 관측에 적용되는지를 6B-3 계약의 확인 항목으로 넘긴다. 이 slice 는 파기 코드를 만들지 않는다(데이터 파기는 되돌리기 어려운 경로 — Codex 심판 대상) | — |

### P — Python

| ID | 출처 | 항목 | 처분 | 형식에 닿는가 |
|---|---|---|---|---|
| **D-6G2c-11** | vr r5 L-8 | `division_coverage` 는 행이 1 이상이면 `COVERED` 다. 1 행뿐인 업무도 COVERED 로 읽힌다 | **운영자 결정 A-2 → (가) 표지 셋(D-6G2c-17) — 구현** | 판정 JSON 어휘(실행 상태 아님 — 이 PR) |
| **D-6G2c-12** | Python r5 등재 제한 | manifest 키를 늘리면 고칠 자리가 판독기 하나가 아니다 — test 쪽 생성기 둘(`_backtest_support.manifest_bytes` · `_backtest_fixture.build_files`). 빠뜨리면 판독이 거부해 잡히지만, 어느 자리를 고칠지 등식이 말해 주지 않는다 | **구현** — 두 생성기가 내는 키 집합 == 스키마 문서 §2 의 키 집합 == 판독기 허용 키(**세 자리 등식**). 생성기를 하나로 합치지 않는다(깨뜨린 manifest 를 짓는 test 가 헐거워진다 — 저자 판단 승계) | 아니오 |

## 결정 (항목 밖)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2c-13** | **게이트 술어를 넓히지 않는다.** 이 slice 가 만지는 게이트는 없다. 게이트에 닿는 low(cr r5 L-7 열거 · L-9 판)는 6G-2b·6G-2a 가 받았고, 6G-2b 가 남긴 게이트 OPEN 여섯은 **6G-2g**(D-6G2c-27)가 받는다 | 세 slice 의 경계 |
| **D-6G2c-14** | **구현 항목마다 변이 하나.** 「고쳤다」는 그 수정을 되돌리는 변이가 RED 일 때만 적는다 | 6G 에서 「대조표에 이행, 실제로 다름」이 반복됐다 |
| **D-6G2c-15** | **새 항목의 수령 문턱.** 6G 표적 재검증 · PR 리뷰에서 나온 항목 가운데 low·info 만 받는다. medium 이상은 이 slice 에 넣지 않고 팀장이 분류한다(데이터 정확성이면 실수집 차단) | 일괄 slice 가 큰 결함의 은신처가 되지 않게 |

## 계약 갱신 r0-b (2026-10-04, 팀장 — 착수 실측 · 운영자 결정 A-1~A-3 · 수령 항목 처분)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6G2c-16** | **A-1 (가) — 공고 키 해시는 지금대로**(salt 없음). D-7 은 **등재로 닫는다**: 이 해시의 역할은 익명화가 아니라 결합 키이고, 통제는 저장소 밖 강제(`requireOutsideRepository`)다. `OPEN-6G2C-NOTICE-HASH-KEYING` 은 신설하지 않는다 | 운영자 결정 2026-10-04 · 위협 모델 ⑤ 는 공고번호를 말하지 않는다 |
| **D-6G2c-17** | **A-2 (가) — 업무 대표 표지를 셋으로**: `COVERED`(업무별 필요 표본 이상) · `UNDERPOWERED`(행은 있으나 필요 표본 미만) · `ABSENT`(행 0). 지금의 행 0 `UNDERPOWERED` 는 `ABSENT` 가 된다. **업무별 필요 표본 수의 출처는 백테스트 정책의 기존 값**(창별 필요 표본 수 공시와 같은 값 — 새 정책 키를 만들지 않는다; 없으면 멈추고 계약 갱신). **창 단위 `NotEvaluableReason.UNDERPOWERED`(D-6G-31) 와 이름이 같지만 다른 축**이다 — 업무 표지가 `UNDERPOWERED` 여도 창 판정은 바뀌지 않음을 test 로 잠근다. 변이: 행 1 업무가 `COVERED` 로 접히면 RED · 행 0 이 `UNDERPOWERED` 로 남으면 RED | 운영자 결정 2026-10-04 · 백테스트 판정 보고 전(추출까지 ~25일) |
| **D-6G2c-18** | **A-3 — 셋으로 가른다**: ① 이 PR = 형식·스키마 무관 항목 ② **6G-2g 신설**(게이트 OPEN 여섯 — D-6G2c-27) ③ **6G-2c-형식**(D-6G2c-28, 스냅숏 추출 뒤). 「형식에 닿는가」의 판정 기준: 실행 상태 디렉터리(`state.json` · 시도 원장 · 표본 목록 · `sample-scope.json`)의 **바이트·판독 술어**, 또는 스냅숏 스키마(`snapshot-schema.md`)의 **칸·어휘** 가 바뀌면 닿는 것이다. **판독기를 엄격하게 하는 변경도 닿는 것**으로 센다(진행 중 디렉터리를 거부할 수 있다). 판정 JSON(백테스트 verdict)·러너 로그·종료 사유 어휘는 닿지 않는다 | 운영자 결정 2026-10-04 · 「실수집과의 관계」 절 |
| **D-6G2c-19** | **수령 — D-6G-79(6G code-review r5-t · verifier r5-t), K 레인**: **cr r5-t M-4 = vr r5-t M-4**(같은 결함) — E2E harness `bootAndRun` 이 `System.setOut/setErr` 뒤 `.run()` 을 `try` 밖에서 불러 실패 경로 test 가 **그 실행의** stdio 를 읽지 않는다(심은 누출 둘이 실패 경로에서 초록) → **구현**: `try/finally` 로 되돌리기·`lastStdio` 대입을 보장하고 실패 경로 누출 test 가 이번 실행의 출력을 읽음을 **심은 누출 변이 RED** 로 보인다. **게이트 술어(누출 자물쇠)를 바꾸는 커밋**이라 verifier 표적 재검증 대상 · **cr L-3** 구분자 등식이 문서 ↔ 코드가 아니라 리터럴 ↔ 코드 → **구현**: `snapshot-schema.md` §2.2 의 구분자 문면을 test 가 파일에서 읽어 비교(문서만 바뀌면 RED) · **cr L-4** 고정 시계 harness 의 걷기 이름 충돌 → **구현(test harness)**: 고정 시계 test 가 서로 다른 걷기를 만들 때 `walkNameOf` 가 같은 이름을 내지 않게 harness 쪽에서 시계를 전진 · **cr L-5** `OBSERVATION_SQL` 전건 순차 훑기 → **등재**(표본 24,000 규모에서 추출은 1회성 배치; 측정 없이 색인·조건을 더하지 않는다) · **cr L-6** `torn` 표식 위치 무제한 → **6G-2c-형식**(판독 엄격화) · **cr L-7** 표식 줄이 매일 오늘치 1 을 뺀다(보수 방향) → **등재**(하루 1 호출 손해, 상한 1,000 에서 무시 가능; 고치면 예산 셈이 바뀌어 진행 중 디렉터리의 오늘치 셈과 어긋남) · **vr r5-t 저위 변이 둘**(UTC 하루 경계 변형 GREEN · `alreadySpent` 기본값 되살림 GREEN) → **구현**: 각각을 RED 로 만드는 test 하나씩 | D-6G-79 · 변이 적용 확인 규율 |
| **D-6G2c-20** | **수령 — 6G-2d(D-6G2d-22 ① · cr r1 M-2), K 레인**: 형식 거부의 러너 종료 코드 → **등재 유지**(D-6G2d-22 ① 의 사유 그대로 — 디렉터리 열기가 빈 의존이고 「여는 자리 = 잠금 자리」; 거부 사유의 닫힌 토큰이 기동 실패 출력에 있음은 6G-2d 가 test 로 잠갔다) · `unusableRawRows` 세 원인(빈 번호 · 형태 어긴 차수 · 열거 밖 엔드포인트) 분리 계수 → **구현**: 러너 로그 한 줄에 세 계수를 따로 싣고(`unusableRawRows` 합계 이름 유지), 세 원인 각각을 심은 raw 행 셋이 각자 칸에서만 1 임을 test 로. **manifest 에는 싣지 않는다**(실으면 스키마). 새 public 표면이 생기면(계수 셋을 나르는 타입) (2b) 표에 올린다 | 6G-2d OPEN 표 |
| **D-6G2c-21** | **수령 — 6G-2e D-6G2e-25, K 레인(①②⑦⑧) · P 레인(③④⑤⑥)**: ① `verifyThenHeal` 의 순서를 **접두 대조 → 되돌림**으로(한 줄) → **구현** + 「확정 중단 + 접두 변조」 디렉터리의 표본 파일이 거부 뒤에도 남아 있음을 test 로 ② 진짜 절단(장부가 센 줄의 꼬리 손실)과 변조의 진단 메시지 분리 → **구현(메시지만 — 둘 다 거부 그대로)**; 거부 사유 어휘에 값 하나 추가는 「닫힌 어휘에 추가」로 형식에 닿지 않는다(실행 상태 바이트 무변경) ⑦ Busy 경로에서 `LedgerDigest` 를 짓지 않는다(Held 에서만) → **구현** + 원장이 판독 불가인 디렉터리를 밖에서 잠근 채 기동하면 예외가 아니라 `ALREADY_RUNNING` 임을 test 로 ⑧ `SnapshotAValueContractTest`·`SnapshotAmountContractTest` 가 snapshot 패키지 양방향 등재에 없다 → **구현(등재 추가만)**: `config/quality/gate-tests.properties` 에 등재하고 **등재 ↔ 패키지의 test 클래스 집합 등식**이 이미 있으면 그 등식이 왜 이 둘을 놓쳤는지 적는다(없으면 신설하지 않는다 — 게이트 술어 확장은 6G-2g) ③ `_APPROVED_SEED_KEYS` 두 로더 중복 → **구현**: 공용 자리 하나로, 두 로더가 같은 객체를 쓰고 길이 비교가 아니라 **키 집합 등식**으로 ④ CLI 가 기존 `verdict.json` 을 조용히 덮어씀 → **구현**: 존재하면 거부(종료 코드 ≠ 0, 닫힌 사유), 덮어쓰기 플래그는 **만들지 않는다**(runbook 은 스냅숏별 판정 디렉터리) ⑤⑥ 기동 시 `state.json` 두 번·원장 세 번 읽기 → **등재**(정확성 무관, 측정 없이 캐시를 넣지 않는다) | D-6G2e-25 |
| **D-6G2c-22** | **수령 — `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`, P 레인 → 구현**: 공백·한글이 든 스냅숏 경로를 판독기가 끝까지 읽는다(`file:` URI 는 `urllib.parse.unquote` — HTTP 모듈 금지는 D-6G2e-23 ① 그대로). test: 공백·한글 디렉터리의 스냅숏을 지어 백테스트 CLI 가 exit 0 · 변이(디코딩 제거) RED | 6G-2e D-16·20 |
| **D-6G2c-23** | **수령 — `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT`, P 레인 → 구현**: `ml-engine/pyproject.toml` import-linter 「app 은 DB·HTTP·업무 모듈을 모른다」 계약의 `forbidden_modules` 가 서드파티 다섯만 열거해 `urllib.request`·`http.client` 같은 **표준 HTTP 모듈**이 지나간다 → 금지를 **패키지 뿌리 + 표준 HTTP 모듈**(`urllib.request` · `http` · `socket` 등 — 목록은 계약 파일 한 자리)까지 넓히고, 6G-2e 가 임시로 둔 test 쪽 AST sweep 의 금지 집합과 **등식**(test 가 pyproject 를 읽어 비교). 변이: app 층 모듈에 `import urllib.request` 한 줄 → `lint-imports` RED **그리고** AST sweep RED. 이것은 게이트 술어 변경이라 verifier 표적 재검증 대상 | 6G-2b D-6G2b-41 이관 |
| **D-6G2c-24** | **수령 — `OPEN-6G2A-CENSUS-DERIVED-LOCALS`, P 레인 → 구현(한 단계)**: 쓰임 명단(AST) 이 **제공자 호출 결과를 품은 식에서 대입된 지역 변수**(`timedelta(days=p.x)` · `rate_from_basis_points(p.y)` · 합·곱)의 소비자를 **한 단계** 따라간다. 두 단계 이상·컨테이너 경유는 알려진 제한으로 남긴다(사유: 데이터 흐름 해석기를 짓지 않는다). 공시 칸당 키 **둘 이상** 흔들기(PR #53 리뷰 ④)도 함께. 변이: 파생 지역 변수의 소비자를 하나 지우면 구조 축 RED. 넘치면(한 단계가 명단을 거짓 양성으로 채우면) 이탈 사유와 함께 등재로 되돌린다 | 6G-2a D-6G2a-24 |
| **D-6G2c-25** | **수령 — `OPEN-6G2F-NOTICE-LIST-ROWS` → 등재 유지(이 PR 코드 0)**: 공고 목록 레인 쪽 100 은 일일 증분(하루 ~470 공고 → 5 쪽)에서 1,000 안이고 백필은 끝났다. 백필 재실행이 필요해지는 때(운영 전환 M7 뒤)에 값 slice 로 연다 — 그때 3B 기존 test 무편집 제약을 다시 본다 | 6G-2f D-6G2f-5 |
| **D-6G2c-26** | **수령 — D-6G2c-10(raw 관측 보존 기간) → 등재 + `OPEN-6B3-RAW-OBSERVATION-RETENTION` 신설**(6B-3 으로). 이 slice 는 파기 코드를 만들지 않는다 | 초안 그대로 |
| **D-6G2c-27** | **6G-2g 신설 — 게이트 OPEN 여섯**: `OPEN-6G2B-COLLECTION-DEPTH` · `OPEN-6G2B-FOLDING-UNIFICATION` · `OPEN-6G2B-REFLECTION-ROOT-DOMAIN` · `OPEN-6G2B-ALLOWED-PACKAGE-EGRESS` · `OPEN-6G2B-HOLDER-INTERNAL-SURFACE` · `OPEN-6G-GATE-REGISTRY-KONEPS`. 전부 **게이트 술어 확장**이라 D-13(이 slice 는 게이트를 넓히지 않는다)에 걸린다. 계약 초안은 이 PR 종결 뒤 팀장이 쓴다(`reports/evidence/m6/6g2g/scope.md`). 순서: **6G-2c → 6G-2g → (추출 뒤) 6G-2c-형식** | 운영자 결정 A-3 |
| **D-6G2c-28** | **6G-2c-형식 — 스냅숏 추출 뒤에만 머지할 수 있는 항목**: D-6G2c-9(명칭 셋 닫힌 어휘 — 스키마) · `OPEN-6G2D-EMPTY-AXIS-REASON`(스키마 v6 후보) · `OPEN-6G2D-FRAME-REWALK`(표본틀을 실행 상태에 보존 — 형식) · `OPEN-6G2F-MAX-PAGES-PROVENANCE`(행 수 문턱을 `sample-scope.json`·장부에 싣기 — 형식) · cr r5-t L-6(`torn` 표식 위치 — 판독 엄격화). **그 PR 의 전제**: 디렉터리 `m6-6g` 의 스냅숏이 추출돼 있고 다음 수집은 새 디렉터리에서 시작한다(상한 N=3 창도 새로). 계약은 추출 뒤 쓴다 | 운영자 결정 A-3 · D-18 기준 |
| **D-6G2c-29** | **in_scope 정정**(초안 대비): Python 쪽을 `ml-engine/src/ml_engine/evaluation/**` · `ml-engine/src/ml_engine/app/backtest_cli.py` · `ml-engine/pyproject.toml`(import-linter 계약 절만) · `ml-engine/tests/**` 로 넓힌다(D-21 ③④ · D-22 · D-23 · D-24 의 자리). Kotlin 쪽 `adapters/.../koneps/**` 는 **넣지 않는다**(D-25 등재 유지; cr L-4 의 `walkNameOf` 는 harness 쪽에서 푼다 — production 무편집). `snapshot-schema.md` 는 **이 PR 에서 편집하지 않는다**(형식 무관 PR 의 정의) — 단 cr L-3 의 test 가 그 파일을 **읽기만** 한다 | D-18 |
| **D-6G2c-30** | **acceptance 확정**: Kotlin `check` job · `ml-engine` job · container job(production 코드 변경: `RunStateDirectory` · `JdbcSnapshotSource` · `SnapshotExtractionRunner` · 수집 배선 KDoc). 구현 항목마다 변이 하나(D-14), 게이트 술어를 바꾸는 둘(D-19 누출 자물쇠 · D-23 import 계약)은 verifier 표적. **(2b) 표**는 K·P 보고에 「새 public 표면」 항목으로 | D-14 · CLAUDE.md 「게이트 술어 변경은 severity 무관 표적 재검증」 |
| **D-6G2c-31** | **(P 레인 보고, 계약 갱신 r1) `snapshot-schema.md` §2 의 「판정문의 업무 대표 어휘」 문장 하나는 팀장이 고친다.** 그 문장은 **판정 JSON 어휘의 정본**이고 test(`test_division_coverage_vocabulary_matches_the_schema_document`)가 그것과 enum 의 집합 등식을 단언하므로, D-17 을 하려면 문장이 먼저 바뀌어야 한다. D-18 기준으로 판정 JSON 어휘는 형식에 닿지 않는다 — 문장이 스냅숏 스키마 문서 안에 산다는 사실이 그 어휘를 스키마로 만들지 않는다(스냅숏 manifest·rows 의 칸·판독기 술어 무변경). D-29 의 「이 PR 에서 편집하지 않는다」는 **그 문장 하나만 예외**(팀장 커밋, 다른 절 무편집). **필요 표본 수의 출처 확정**: `verdict.min_window_rows`(창당 표본 하한, 정책 주석 「창당 n ≥ 483」) — 업무 행 수 ≥ 그 값 `COVERED` · 1 이상 미만 `UNDERPOWERED` · 0 `ABSENT`. rollback 공유 파일 목록에 `snapshot-schema.md` 를 되돌린다(hunk 격리) | P 레인 보고 2026-10-04 · D-17·18·29 |
| **D-6G2c-32** | **(P 레인 보고, 계약 갱신 r2) in_scope 누락 정정**: D-22 의 「판독기」는 `ml-engine/src/ml_engine/adapters/snapshot_files.py`(`read_snapshot_files` — `file:` URI → `Path` 변환이 그 한 자리)다. D-29 가 그 자리를 넓힌다고 적고 `adapters/**` 를 빠뜨렸다 → 그 파일 하나를 in_scope 에 더한다(`adapters/**` 전체는 아님). 변경은 `unquote` 한 줄(`urllib.parse` 만 — D-6G2e-23 ① 준수), 판독 술어는 느슨해지지도 엄격해지지도 않는다(없는 경로는 여전히 `NOT_FOUND`). 판독 책임을 `backtest_cli` 로 올리는 대안은 계층을 깨므로 택하지 않는다 | P 레인 보고 2026-10-04 · D-22·29 |
| **D-6G2c-33** | **(K 레인 보고, 계약 갱신 r3) in_scope 에 `workflow/build.gradle.kts` 의 test task 입력 선언 한 블록을 더한다.** D-19 (b) 「구분자 등식을 문서 ↔ 코드로」는 test 가 `snapshot-schema.md` 를 런타임에 읽는 것만으로는 서지 않는다 — 실측: 문서의 용도 토큰을 바꾸고 `:workflow:test` 를 돌리면 Gradle 이 그 문서를 입력으로 모르므로 **UP-TO-DATE 로 건너뛰어 6초 초록**. 루트 `gradle.properties` 의 `org.gradle.caching=true` + CI 의 `setup-gradle` 캐시 복원이라 CI 에서도 문서만 고친 PR 이 캐시된 초록을 받는다(「안 돌린 게이트는 아무것도 막지 못한다」). 조치는 같은 파일의 기존 관례(`WorkflowGateRegistrationTest` 가 `gate-tests.properties` 를 입력으로 선언한 블록) 옆에 스냅숏 스키마 문서를 같은 형태로 한 줄 — 산출물 코드 무변경. **일반 규율로 올린다**: 문서를 읽어 등식을 재는 Kotlin test 는 그 문서를 test task 입력으로 선언해야 하고, 변이는 **문서만 바꾼 뒤** 재실행이 일어나는지(UP-TO-DATE 가 아닌지)까지 재야 한다. Python 쪽(D-12 · D-17 · D-23 의 문서 읽기 test)은 pytest 가 캐시를 쓰지 않아 이 문제가 없다 | K 레인 보고 2026-10-04 · CLAUDE.md CI 절 |

### 이 PR 의 항목 요약 (레인별)

**K(kotlin-implementer, production 셋 + test)**: D-1·2·3(두 프로세스 잠금 E2E · `IOException` 사유 분리) · D-4(표본 원장 held 가드 · `release` 가시성) · D-5(KDoc 등재) · D-6(두 모드 동시 기동 실패 test + KDoc) · D-19(harness `try/finally` 누출 자물쇠 · 구분자 등식 문서 읽기 · 고정 시계 harness · 저위 변이 test 둘) · D-20(`unusableRawRows` 세 계수) · D-21 ①②⑦⑧.
**P(ml-implementer)**: D-12(manifest 키 세 자리 등식) · D-17(표지 셋) · D-21 ③④ · D-22(URI 디코드) · D-23(import 계약) · D-24(파생 지역 변수 한 단계).
**등재만(코드 0)**: D-5 · D-8 · D-16 · D-19 의 L-5·L-7 · D-20 의 종료 코드 · D-21 ⑤⑥ · D-25 · D-26.

## 위협 모델 — 6G-2c 고유 경계 (Phase 2.5 (0))

이 slice 는 새 경계를 세우지 않는다. 6G 의 경계(판정의 정직성 · 승인 상한 · 개인정보) 안에서 **자물쇠의 빈 자리**를 채운다.

**방어하는 것**: 두 프로세스가 같은 실행 상태로 겹쳐 도는 것(이미 막혀 있다 — 그것을 재는 test 가 없다) · 잠금 없이 표본 원장을 쓰는 것 ·
잠금만 풀린 채 원장이 살아 있는 상태.

**방어하지 않는 것(경계 밖)**: 실행 상태 파일을 손으로 고쳐 해시까지 맞추는 운영자 · 한 인스턴스를 여러 스레드가 쓰는 코드(쓰는 자리가 없다) ·
공개 공고번호에서 해시를 되돌리는 것(A-1 에서 정한다).

### (2b) 값 획득 축

| 표면 | 변화 | 밖에 허락하는 것 |
|---|---|---|
| `RunStateLock.release` | 가시성을 내리거나 `close` 로 합친다 | 줄어든다 — 밖에서 잠금만 풀 수 없다 |
| `RunStateDirectory.sampleList` | 잠금 없는 인스턴스에서 쓰기 거부 | 줄어든다 |
| 잠금 실패 사유 어휘 | 값 하나 추가 | 읽기만 |

새로 public 이 되는 선언은 없어야 한다. 생기면 수정 라운드 보고 항목으로 올린다.

## 운영자 승인 (착수 전 — 2026-10-04 결정 완료: A-1 (가) · A-2 (가) · A-3 셋으로 분할, 정본은 D-6G2c-16~18)

- **A-1 공고 키 해시의 salt**(D-6G2c-7). 선택지:
  - **(가) 지금대로 둔다(추천).** 공고번호는 공개 정보이고 이 해시의 역할은 익명화가 아니라 **결합 키**다. 통제는 스냅숏과 실행 상태가 저장소 밖에만
    있다는 것이고 그것은 코드가 강제한다. 위협 모델 ⑤ 는 상호 · 사업자번호 · 담당자를 말하고 공고번호를 말하지 않는다.
  - (나) 로컬 비밀로 키를 건 해시(HMAC)로 바꾼다. 스냅숏이 새도 공고를 특정하기 어렵다. 대가: 해시가 바뀌어 원장 · 표본 목록 · 스냅숏 · golden 을
    전부 다시 짓고, 비밀을 잃으면 기존 스냅숏을 raw 와 맞댈 수 없다. **실수집 · 추출이 끝난 뒤에만** 할 수 있다.
- **A-2 업무 대표 표지**(D-6G2c-11). 선택지:
  - **(가) 표지를 셋으로(추천).** `COVERED`(업무별 필요 표본 이상) · `UNDERPOWERED`(행은 있으나 필요 표본 미만) · `ABSENT`(행 0). 지금의
    `UNDERPOWERED`(행 0)는 `ABSENT` 가 된다. 판정 JSON 어휘가 바뀌므로 **백테스트 판정 보고 전에** 머지한다.
  - (나) 지금대로 둔다. 1 행 업무가 COVERED 로 읽히는 것은 판정문의 창별 필요 표본 수 공시로 보완된다.
- **A-3 PR 분할**: 형식 무관 항목을 먼저 머지할지(「실수집과의 관계」 절).

## in_scope

- `adapters/src/main/kotlin/bidvector/adapters/snapshot/**` · `adapters/src/test/kotlin/bidvector/adapters/snapshot/**`
- `app/src/main/kotlin/bidvector/app/collection/**` · `app/src/main/kotlin/bidvector/app/wiring/**`(KDoc · 사유 어휘)
- `app/src/test/kotlin/bidvector/app/collection/**` · `app/src/test/kotlin/bidvector/app/wiring/**`
- `workflow/src/main/kotlin/bidvector/workflow/collection/**`(사유 어휘가 여기 있을 때) · 대응 test · `workflow/build.gradle.kts`(**test task 입력 선언 블록만** — D-6G2c-33)
- `ml-engine/src/ml_engine/evaluation/**` · `ml-engine/src/ml_engine/app/backtest_cli.py` · `ml-engine/src/ml_engine/adapters/snapshot_files.py`(D-22 의 판독기 — D-6G2c-32) · `ml-engine/pyproject.toml`(import-linter 계약 절만) · `ml-engine/tests/**`(D-6G2c-29)
- `config/quality/gate-tests.properties`(등재 추가만 — D-21 ⑧) · `app/src/test/kotlin/bidvector/app/architecture/**` 는 **out_scope**(등재 파일만 열림)
- `reports/evidence/m6/6g2c/**` · `milestone-6.md`(착수·종결 문단만)

**out_scope**: 게이트 술어(`app/src/test/.../architecture/**`) · `reports/evidence/m6/6g/snapshot-schema.md`(읽기만) · `adapters/src/main/kotlin/bidvector/adapters/koneps/**` · `ml-engine/policy/**` · golden fixture · `db/migration/**` · `contracts/**` · `docker/**` · 데이터 파기 코드 · 실행 상태 디렉터리의 바이트·판독 술어(D-18).

## acceptance

CI job 명령 그대로 — Kotlin `check` job 과 `ml-engine` job. production 코드가 바뀌므로 container job 도 돌린다. `commands.md` 에 항목별 처분과
변이 결과를 한 줄씩 적는다.

## rollback

in_scope 경로 한정 `git restore --source=<base> --staged --worktree --`. 공유 파일(`snapshot-schema.md` · `milestone-6.md` ·
`architecture-policy.properties`)은 커밋 해시 hunk 격리와 수동 절차. 버릴 clone 에서 ①~⑥ 실측. **실행 상태 형식에 닿는 항목이 머지된 뒤의 rollback 은
진행 중인 실행 상태 디렉터리를 거부하게 만들 수 있다** — 그 경우의 복구 절차(디렉터리를 새로 시작하면 상한이 0 부터 다시 센다)를 rollback.md 에 적는다.

## 리뷰 레인

`verifier`(두 프로세스 잠금 재실측 · 구현 항목마다 변이 · 가시성 변경이 연 표면) + `code-reviewer`(sonnet) + `privacy-gate`(A-1·D-6G2c-9·10 의
처분이 문면과 맞는지). Codex 없음 — 데이터 파기와 마이그레이션이 없다(생기면 멈추고 계약 갱신).

## 하네스 레인 변경

(착수 뒤 리뷰 요청 시점마다 등재)

## OPEN 수령·신설 (r0-b)

| OPEN | 처분 |
|---|---|
| `OPEN-6G-REVIEW-FOLLOWUPS` | **이 PR 이 닫는다** — 남는 항목은 전부 다른 OPEN 으로 이름을 얻는다(아래 둘 + 6G-2g 여섯 + 6G-2c-형식 다섯) |
| `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE` · `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` · `OPEN-6G2A-CENSUS-DERIVED-LOCALS` | **이 PR 이 닫는다**(D-22 · D-23 · D-24; D-24 는 한 단계까지 — 남는 한계는 알려진 제한) |
| `OPEN-6G2F-NOTICE-LIST-ROWS` | **등재 유지**(D-25) — M7 뒤 운영 전환 때 값 slice |
| (신설) `OPEN-6B3-RAW-OBSERVATION-RETENTION` | D-26 — 6B-3 으로 |
| `OPEN-6G2D-EMPTY-AXIS-REASON` · `OPEN-6G2D-FRAME-REWALK` · `OPEN-6G2F-MAX-PAGES-PROVENANCE` · D-9 · cr r5-t L-6 | **6G-2c-형식**(D-28, 추출 뒤) |
| 6G-2b 게이트 OPEN 여섯 | **6G-2g**(D-27) |
| `OPEN-6G2C-NOTICE-HASH-KEYING` | **신설하지 않음**(A-1 (가)) |
