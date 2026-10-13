# M6/6B-3 — commands

base `e2232a75` · 브랜치 `m6-6b3/2026-10-13` · worktree `bid-vector-v2-m6-6b3`.
산출물 커밋 아홉(`c7ea5b7b` → `5c622296`). **판정 SHA 후보 `5c622296`**(마지막 산출물 커밋).

> **판정 SHA 가 한 번 움직였다.** `575674d2` 에서 동결을 보고한 뒤 팀장 지시(설계 수용 조건 —
> test 셋)가 늦게 닿아 `1dfd0094`·`5c622296` 가 쌓였다. `575674d2`..`5c622296` 의 **production
> 경로 diff 는 0**(`git diff --name-only 575674d2..5c622296 -- '*/src/main/*'` 빈 출력) — 늘어난
> 것은 test 둘·수렴 test 하나·fixture 수정 하나·등재 둘이다.

## acceptance (CI `check` job 명령 그대로)

판정 SHA 후보 `5c622296` 에서 **양끝 HEAD 를 단언하고** 돌렸다(측정 중 커밋 0).

| # | 명령 | 결과 |
|---|---|---|
| 1 | `./gradlew --no-daemon check --rerun-tasks` | **exit 0** · 11m44s · **350 task 전부 실행**(캐시·up-to-date 0) · test 합계 **2,919** |
| 2 | `./gradlew --no-daemon qualityBaseline --rerun-tasks` | **exit 0** · 43s · 24 task 전부 실행 |

**둘 다 `--rerun-tasks` 로 쟀다.** 이 slice 는 초록이 **캐시와 실행 순서에 의존하던 것**을 한 번
실측했으므로(아래 「게이트가 실제로 막은 것」 넷째 줄), 판정의 근거를 「아무것도 캐시하지 않은
run」으로 둔다. `qualityBaseline` 의 경우 그것 없이 부른 첫 호출이 **「33 actionable tasks: 33
up-to-date」로 exit 0** 이었다 — 돌지 않은 게이트를 측정으로 세지 않았다.

skipped **4** 는 선언된 제외 하나(`RealServerIntegrationTest` — `@EnabledIfSystemProperty`,
`gate.tests.adapters.excluded` 등재)이고 **6B-3 의 신설 13 class 는 skipped 0** 이다.

`--rerun-tasks` 를 붙인 이유: 그것 없이 부른 첫 호출이 **「33 actionable tasks: 33 up-to-date」로 exit 0**
이었다 — 돌지 않은 게이트는 아무것도 막지 못하므로 그 초록을 측정으로 세지 않는다.

`ml-engine` job 은 생략한다 — Python 산출물 0(`git diff --name-only e2232a75..HEAD -- ml-engine` 빈 출력).
`container` job 도 생략한다 — `docker/**`·`ci.yml` 무편집(같은 질의 빈 출력), compose 스택에 닿는 변경이 없다.

## test 수

신설 test class 13 + 기존 class 에 더한 test 1 = **test 메서드 90**(그 13 class 의 skipped 0).
`workflow.retention` 19 · `adapters.retention` 57 · `app.retention`·`app.wiring` 13 ·
`CleanMigrationTriggerTest` 1. 되돌린 트리 2,829 + 90 = **2,919** — 등식이 닫힌다.

## 변이 실측 (바꿔치우기 — 적용 직후 `git diff --numstat` 로 교체를 확인, 산출물은 먼저 커밋)

20 변이 · **18 KILLED · 2 SURVIVED**(아래 M-14a·M-14b, 사유를 밝힌다).
각 변이는 「적용 → 표적 test → `git checkout --` → `git status --porcelain` 빈 것 확인」 한 바퀴다.

| # | 무엇을 바꿔치웠나 | 결과 (FAIL 한 test 수) |
|---|---|---|
| M-1 | 경계 술어 `<` → `<=` | KILLED (1) — 통제군 test |
| M-2 | 정책 보존 기간 90 → 89 | KILLED (4) — 경계·outbox·FK |
| M-2w | 같은 변이, workflow 표적 | KILLED (1) — 「승인된 값은 90일」 |
| M-3 | 정책 보존 기간 90 → 91 | KILLED (3) |
| M-4 | 분류 ↔ 스키마 집합 등식 무력화 | KILLED (2) — 양방향 거부 둘 |
| M-5 | 부모 파기의 자식 가드 제거 | KILLED (1) — 기한 안쪽 자식 |
| M-6 | allow-list 반전 제거(전 키 보존) | KILLED (5) |
| M-7 | fail-closed 제거(판독 실패를 빈 객체로) | KILLED (4) |
| M-8 | 트리거 관문 ② 앱 역할 거부 무력화 | KILLED (1) — GRANT 를 준 뒤의 거부 |
| M-9 | 트리거 관문 ③ 표지 일방향 무력화 | KILLED (3) — 둘째 마스킹·되돌림·원문 UPDATE |
| M-10 | 트리거 관문 ④ 행 전체 비교 무력화 | KILLED (1) — 다른 열 끼워 넣기 |
| M-11 | 트리거 이벤트에서 DELETE 제거 | KILLED (2) — 6B-3 하나 + **기존** `RawAppendOnlyTest` 하나 |
| M-12 | 위상 정렬 결과 역순 | KILLED (4) — 수렴 test 포함 |
| M-13 | 임대 획득 실패를 무시 | KILLED (1) — 둘째 run |
| M-14a | 마스킹 **조회**의 표지 필터 제거 | **SURVIVED** |
| M-14b | 마스킹 **UPDATE** 의 표지 가드 제거 | **SURVIVED** |
| M-14c | 위 둘을 **함께** 제거 | KILLED (1) — 「두 번째 run 은 0 행」 |
| M-15 | `payload` 를 읽는 질의를 **있게** 만든다(도입형 — 「없음」을 잠그는 게이트는 그 형태로만 잰다) | KILLED (1) — 읽기 자리 0 |
| M-16 | 보존 임대 키를 relay 키와 같게 | KILLED (2) — 임대 양방향 |

**생존 둘의 뜻**(숨기지 않는다): 「이미 마스킹된 행 건너뜀」은 두 겹으로 막혀 있어 **하나만 지우면
다른 하나가 덮는다.** 그래서 test 가 잠그는 것은 **건너뜀이라는 거동**이고 어느 구현이 그것을 하는지는
잠그지 않는다 — 쌍으로 지우면 RED 다(M-14c). 되돌릴 수 없음의 **DB 층 보장**은 이 둘과 무관하게
트리거가 지고 그것은 M-9 가 독립으로 죽인다.

## 게이트가 실제로 막은 것 (구현 중 받은 RED)

| 게이트 | 무엇을 막았나 |
|---|---|
| `AppHttpDependencyGateTest` 면제 수 래칫 | 새 app 표면 다섯을 면제에 등재하면서 그 수를 함께 고치지 않은 상태 — expected 46, was 51 |
| detekt `ReturnCount`·`MatchingDeclarationName` | 판독 실패 경로가 섞인 함수 · 파일 이름과 단일 최상위 선언 불일치 |
| ktlint (`when-entry-bracing` 등) | 형식 — 눌러 끄지 않고 구조를 고쳤다(`@Suppress` 추가 0) |
| 전건 `check`(test 순서) | **fixture 가 TRUNCATE 목록 밖 공유 표를 오염시킨 것** — `provenance_authority` 에 심은 행이 다른 test 로 새어 `ProvenanceAuthoritySeedTest` 가 미지 provenance 로 터졌다. 앞선 전건 초록들은 그 test 가 **먼저** 돌아서였고, test class 둘이 늘자 순서가 바뀌어 드러났다 — **초록이 순서에 의존하고 있었다**. 심지 않는 쪽으로 고쳤다(`5c622296`) |

## D-6B3-1 ④ — 「마스킹이 읽는 키를 지우지 않는다」 실측

**① 기계 수집 — `raw_observation.payload` 를 읽는 production 질의는 0 이다.**
`raw_observation` 을 이름으로 부르는 production Kotlin 파일 전수를 모은 뒤(모듈 넷) 그 가운데
투영 아닌 `payload` 를 SELECT 하는 자리를 골랐다 — 남은 한 건은 **다른 표의 다른 열**
(`edit_session` 의 상태 열)이었다. 소비자는 전부 `payload_fields` 를 읽는다(스냅숏 추출·수집 축 조회).

**② 포함의 다른 절반은 트리거가 진다.** 마스킹 UPDATE 는 `payload_fields` 를 바꿀 수 없다(마스킹
열을 뺀 행 전체 비교, M-10 이 죽이는 관문) — 그 투영을 읽는 소비자는 마스킹에 **영향을 받을 수 없다**.

**③ 등식 test**(`MaskingKeepsProjectedKeysTest`): production 쓰기 경로로 쓴 행에서
「마스킹 뒤 남은 키 집합 == 그 행의 투영 키 집합」. 손 fixture 가 아니라 실제 필드 계약 레지스트리가
쓴 투영이라 등식의 양쪽이 모두 계약에서 나온다.

## 비밀값·누출 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 를 산출물·evidence 경로에 대해 돌린 결과는
`qualityBaseline` 의 해당 게이트가 전건으로 포함한다(위 acceptance 2, exit 0). fixture 의 자리값은
**형태를 흉내내지 않는다** — 담당자 키가 사라지는지만 재므로 전화·전자우편 꼴이 필요 없다.

## clean-tree

`git status --porcelain -- <in_scope 경로 개별 인자>` → 빈 출력.
**양성 대조 1회**(비파괴 절삭): 산출물 파일 하나의 끝 한 줄을 지운 상태에서 같은 질의가 그 파일을
`M` 으로 냈고, 그 줄을 되돌린 뒤 다시 빈 출력이었다(`git checkout --` 를 쓰지 않는다 — 미커밋
산출물을 함께 지운다).

## DB 격리 단언

이 slice 의 모든 DB 실행은 **Testcontainers** 위에서다(`PersistenceTestSupport` 의 공유 컨테이너,
`postgres:16.4`). dev DB(`bid-vector-v2-dev`, 127.0.0.1:55432)·legacy DB 로의 접속은 **0** 이다 —
test 어디에도 그 포트·이름·볼륨이 없고, 러너를 실제 DB 에 돌리지 않았다(운영 실행은 별도 승인).
