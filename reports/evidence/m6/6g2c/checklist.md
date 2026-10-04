# M6/6G-2c — 리뷰 요청 조건 점검

레인마다 자기 절만 쓴다.

## P — Python

판정 대상 SHA **`fa7483cb`**.

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋돼 base/head 고정 | 예 — base `ecdc9d9f` |
| 관련 test/lint/type/architecture/contract 명령 통과 | 예 — `ml-engine` job 열 step 전부 exit 0 |
| 구현 항목마다 변이 하나(D-6G2c-14) | 예 — commands.md 「항목과 잠금」 표, 변이 전부 기대대로 |
| 게이트 술어를 바꾼 커밋 표시 | 예 — `49505a74`(D-6G2c-23) · **`a179e784`**(F-1 스윕 범위) |
| 변경된 fixture·정책 version 의 근거 기록 | 해당 없음 — fixture 바이트·정책 파일 무변경. 공유 파일 하나(스키마 문서 §2 어휘 문장, 팀장 커밋 `ae1077e4`)가 함께 움직였고 rollback 공유 파일 목록 대상이다 |
| 알려진 제한과 되돌리는 방법 기록 | 예 — commands.md 「이탈과 알려진 제한」 · rollback 은 in_scope 경로 한정 복원 |
| 자기 승인 금지 | 예 — 이 레인은 판정하지 않는다 |

### 항목 전수 대조 (계약 「이 PR 의 항목 요약」 P 줄)

| ID | 상태 |
|---|---|
| D-6G2c-12 manifest 키 세 자리 등식 | 구현·변이 RED |
| D-6G2c-17 업무 대표 표지 셋 | 구현·변이 셋 RED |
| D-6G2c-21 ③ seed 키 열거 한 자리 | 구현·변이 RED |
| D-6G2c-21 ④ 기존 판정 덮어쓰기 거부 | 구현·변이 RED |
| D-6G2c-22 판독기 URI 디코드 | 구현·변이 RED(CLI 끝까지 + 판독기 단위) · **음성 대조 넷**으로 거부가 그대로임을 확인 |
| D-6G2c-23 app 층 HTTP import 계약 | 구현·변이 RED(두 층 모두) |
| D-6G2c-24 파생 지역 변수 한 단계 | 구현·변이 둘 RED |

### 수정 라운드가 만든 새 파일 ↔ in_scope 대조

새 파일 **넷**, 전부 `ml-engine/tests/**`(in_scope) 안이다 — D-6G2c-23 의 양성 대조용 독립 미니
프로젝트 셋(`tests/app/fixtures/bad_app_http/` 의 `pyproject.toml` 과 `ml_engine` 스캐폴드 둘)과
D-6G2c-22 의 판독기 단위 test 하나(`tests/adapters/test_snapshot_files.py`). 계약 갱신이 필요한 새
경로는 없다.

### OPEN 처분

| OPEN | 상태 |
|---|---|
| `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE` | **닫힘**(D-6G2c-22) |
| `OPEN-6G2E-APP-HTTP-IMPORT-CONTRACT` | **닫힘**(D-6G2c-23) |
| `OPEN-6G2A-CENSUS-DERIVED-LOCALS` | **닫힘**(D-6G2c-24, 한 단계까지 — 두 단계·컨테이너는 알려진 제한) |

## K — Kotlin

판정 대상 SHA **`ec07df09`**(마지막 K 커밋).

| 조건 | 상태 |
|---|---|
| 구현 diff 가 커밋돼 base/head 고정 | 예 — in_scope 경로 clean-tree 빈 출력 + 양성 대조 1회(비파괴 절삭) |
| acceptance 전건 통과 | 예 — Kotlin `check` exit 0 · `qualityBaseline` exit 0. `one-command-check.sh` 와 container job 은 두 레인 동결 뒤 팀장 몫 |
| 구현 항목마다 변이 하나(D-6G2c-14) | 예 — 구현 항목 열둘, 변이 열여섯이 전부 기대대로(음성 대조 하나 포함) |
| 게이트 술어를 바꾼 커밋 표시 | 예 — `92b35a82`(누출 자물쇠) · `d65e8a80`(문서를 test 입력으로) |
| 변경된 fixture·정책 version 의 근거 | 해당 없음 — fixture 바이트·정책 version 무변경. 게이트 **데이터** 둘(등재 추가 · 전송 표면 쌍)은 commands.md 에 사유 기록 |
| 알려진 제한과 되돌리는 방법 | 예 — commands.md 「이탈과 알려진 제한」 넷. 되돌림은 in_scope 경로 한정 복원 |
| 자기 승인 금지 | 예 — 이 레인은 판정하지 않는다 |

### 항목 전수 대조 (계약 「이 PR 의 항목 요약」 K 줄)

| 계약 항목 | 상태 |
|---|---|
| D-1·2·3 두 프로세스 잠금 · `IOException` 사유 분리 | 이행 — 세 갈래 모두 별 프로세스 보유자 |
| D-4 표본 원장 held 가드 · `release` 가시성 | 이행 — 놓는 길이 `close()` 하나 |
| D-5 KDoc 등재 | 이행(코드 0) |
| D-6 두 모드 동시 기동 실패 test + KDoc | 이행 — 걸리는 것이 공유 타입 셋뿐임까지 단언 |
| D-19 (a) harness `try/finally` | 이행(게이트 술어) |
| D-19 (b) 구분자 등식 문서 읽기 | 이행 — 입력 선언까지(r3 승인). 선언 없는 판도 실측해 남겼다 |
| D-19 (c) 고정 시계 harness | 이행 — production `walkNameOf` 무편집 |
| D-19 저위 변이 둘 | 이행 — 둘 다 **구조**로 잠갔다(거동 없는 변이라 거동 test 로는 못 잡는다) |
| D-20 `unusableRawRows` 세 계수 | 이행 — 러너 로그에만, manifest 무변경 |
| D-21 ① 접두 대조 순서 | 이행 |
| D-21 ② 절단·변조 진단 분리 | 이행 — 거부는 그대로, 실행 상태 바이트 무변경 |
| D-21 ⑦ Busy 경로 누적 해시 | **절반 이행** — 러너까지는 이행, 전 조립 기동은 상한 seed 가 먼저 읽는다(제한 등재) |
| D-21 ⑧ 게이트 등재 | 이행 — 계약이 지목한 둘 + 같은 이유로 빠져 있던 넷 |

### 수정 라운드가 만든 새 파일 ↔ in_scope 대조

새 파일 다섯 — `RunStateLock.kt`(in_scope) · `RunStateLockTest.kt`(in_scope) ·
`RunStateLockHolderProcess.kt`(in_scope) · `FixedClockHarnessTest.kt`(in_scope) ·
`CollectionLedgerSurfaceTest.kt`(in_scope). in_scope 밖 **신설 0**. 편집한 in_scope 밖 파일은 둘이고
위 commands.md 에 사유와 승인 근거를 적었다.
