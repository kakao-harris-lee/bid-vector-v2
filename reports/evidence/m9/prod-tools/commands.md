# M9 / prod-tools — 실행 명령과 종료 코드

계열 주의: 여기의 L-1·L-2 는 **7F 장부 v r2** 계열이다(`m7/close/checklist.md` §4). `7f/scope.md`·
`7f2/checklist.md` 의 L-1/L-2 는 다른 계열이고 이미 닫혔다.

## 2026-11-05 — 구문·acceptance

- cmd: `bash -n tools/prod-env.sh tools/prod-deploy.sh tools/prod-rollback.sh tools/prod-notification-sweep.sh tools/prod-guards-check.sh`(파일별)
- exit: 0
- 핵심 결과: 다섯 파일 모두 구문 통과. shellcheck 는 이 host 에 없다(`command -v shellcheck` 공집합) — 돌리지 않았다.

- cmd: `./tools/prod-guards-check.sh`
- exit: 0
- 핵심 결과: `통과 39 · 어긋남 0` — 술어 둘의 양성·음성 대조와 세 스크립트의 거절·통과·trap 삭제 대상.

- cmd: `./tools/prod-env-keys-check.sh`  (CI container job 의 S-27 명령 그대로)
- exit: 0
- 핵심 결과: `등식 — 키 18개 (템플릿 == compose 세 파일)` — 이 slice 가 `prod-env.sh` 를 편집하므로 같은 job 에서 다시 측정.

- cmd: `python3 -c "yaml.safe_load(...)"` 로 `.github/workflows/ci.yml` 파싱 + container job step 이름 열거
- exit: 0
- 핵심 결과: S-27 · S-27b · S-27c · **S-27d**(신규) 네 step 이 그 순서로 등재.

### 돌리지 않은 acceptance 줄과 그 사유

- CI container job 의 **S-27b·S-27c** 는 `--proxy-image bidvector/proxy:local` 로 **실제 docker** 를 쓴다.
  이 레인은 운영 host 를 공유하는 기계에서 돌아 **실제 docker 명령을 쓰지 않는다**(팀장 지시 — 읽기만 하는
  `docker ps` 도 포함). 그래서 두 줄은 이 레인에서 돌리지 않았고, 이 slice 는 그 둘이 보는 파일
  (proxy site·host nginx 조각)을 편집하지 않는다. S-27d 는 가짜 docker 로 돌므로 그 제약과 무관하다.
- Kotlin `check` · `ml-engine` job 은 이 slice 의 in_scope 에 코드가 없어 해당 없다(셸·문서·워크플로뿐).

## 2026-11-05 — 변이 (각각 커밋 뒤 적용, 변이 파일만 `git checkout --` 로 원복)

- 변이 ①: `_prod_git_sha_match` 머리에 `return 0`(술어를 항상 참으로) → `./tools/prod-guards-check.sh` exit 1,
  `통과 25 · 어긋남 14` — 세 호출부(반입 exit 0, 되돌림이 반입까지 흘러가 exit 1, 러너 exit 0)가 모두 붉다.
- 변이 ②: `_prod_oneoff_residue` 머리에 `return 0` → exit 1, `통과 32 · 어긋남 7` — 라벨 축·이름 축·조회 실패
  셋과 러너의 `exit 3`·「`run` 에 닿지 않음」이 붉다.
- 변이 ③: 러너의 `trap '_sweep_on_signal …'` 두 줄 삭제 → exit 1, `통과 36 · 어긋남 3` — 삭제 호출 0 개.
  **주의**: 종료 코드 143 은 그대로였다(bash 의 기본 TERM 동작) — 그래서 「무엇을 지웠는가」를 따로 센다.
- 변이 ④: `compose run` 에서 `--name "$ONEOFF_NAME"` 삭제 → exit 1, `통과 38 · 어긋남 1` — 이름 고정이 깨지면
  trap 이 지울 대상을 접두로 고르게 되는 축.
- 원복 뒤 재실행: `./tools/prod-guards-check.sh` exit 0 (`통과 39 · 어긋남 0`), `git status --porcelain` 빈 출력.

## 2026-11-05 — rollback 실측

`rollback.md` 의 ①~⑥ 을 임시 clone 에서 돌렸다 — 명령과 결과는 그 파일에 한 줄씩. `실측 HEAD` 도 거기 있다.
수정 라운드 뒤 **같은 절차로 재실측**했고 그 파일의 실측 HEAD 를 갱신했다(앞 라운드 실측을 옮기지 않았다).

## 2026-11-05 — 수정 라운드 (PR #122 · v M-1 · cr M-1·M-2 · LOW)

- cmd: `./tools/prod-guards-check.sh` (단언을 먼저 세운 **RED 선행** 상태 — 커밋 `3d609700`)
- exit: 2(중간 판정 불가 — 러너가 트리 축에서 멈춰 가짜 일회 컨테이너가 시작되지 않았다)
- 핵심 결과: `BAD 14` — 가짜 git 이 세션의 `GIT_EDITOR` 를 보고 exit 99 를 내 트리 묶기 전건이 붉다
  (= 「술어가 `GIT_*` 를 비우지 않는다」의 실측). 이 RED 가 cr M-1 의 재현이다.
- cmd: `./tools/prod-guards-check.sh` (구현 뒤 — 커밋 `8ee87339`)
- exit: 0
- 핵심 결과: `통과 57 · 어긋남 0`(앞 라운드 39 + 새 단언 18).
- cmd: `./tools/prod-env-keys-check.sh`(CI S-27) · `bash -n` 다섯 파일 · `ci.yml` YAML 파싱
- exit: 0 / 0 / 0
- 핵심 결과: 키 18개 등식 · 구문 통과 · S-27d step 등재.

### 변이 재확인 (전부 커밋 뒤, 변이 파일만 원복)

| 변이 | exit | 어긋남 | 비고 |
| --- | --- | --- | --- |
| ① `_prod_git_sha_match` 항상 참 | 1 | 14 | 네 호출부 |
| ② `_prod_oneoff_residue` 항상 참 | 1 | 9 | 라벨·이름·멈춤·조회 실패 |
| ③ trap 세 줄 삭제 | 1 | 6 | TERM·HUP 각 삭제 계수 0 |
| ④ `--name` 삭제 | 1 | 1 | 이름 고정 |
| ⑤ `docker ps -a` → `docker ps` | 1 | 5 | **앞 라운드엔 초록이었다**(v L-1) |
| ⑥ `BIDVECTOR_PROD_REPO_ROOT` → `.` | 2 | 14 | **앞 라운드엔 초록이었다**(v L-2) |
| ⑦ `_prod_git` → 직접 `git -C` | 1 | 1 | source 뒤에 들어온 `GIT_*` 축 |
| ⑧ unset 루프에서 `GIT_*` 제거(⑦ 유지) | 0 | 0 | **초록 — 겹 둘이라 한쪽만 빠지면 잡히지 않는다**(아래) |
| ⑨ 프로젝트 이름 등식 삭제 | 1 | 2 | 새 단언이 공허하지 않다 |
| ⑩ 전역 이름 충돌 검사 삭제 | 1 | 2 | 같음 |

**변이 ⑧ 이 초록인 이유를 숨기지 않는다.** `GIT_*` 를 막는 자리가 둘이고(source 시점의 unset 루프 ·
술어의 `_prod_git`) 오늘 git 을 부르는 곳은 술어뿐이라, 뒤쪽 하나만 있어도 모든 측정 축이 닫힌다.
그래도 앞쪽을 남긴다 — `COMPOSE_*` 가 `-p` 와 겹을 둘로 두는 것과 같은 선택이고, 이 스크립트들에 git
호출이 하나 더 들어오는 날 그 호출은 아무 보호도 없이 시작된다. 측정되지 않는 겹이라는 사실을 등재한다.

- 원복 뒤 재실행: exit 0 (`통과 57 · 어긋남 0`) · `git status --porcelain` 빈 출력.
- 운영 자리: 실행 뒤 `/run/lock/bidvector-v2` 에 남은 파일은 운영 것 하나뿐(검사 파일 잔존 0) ·
  디렉터리는 **만들지 않았고** 소유자·권한 그대로.

## 2026-11-05 — 수정 라운드 2 (PR #122 v r2 F-1·F-2·F-3 — 장부층 low 셋)

- cmd: `./tools/prod-guards-check.sh`(단언을 세운 뒤) → exit 0, `통과 60 · 어긋남 0`(57 + 3).
  **산출물 변경 0** — 셋 다 산출물의 결함이 아니라 「검사가 재지 못한 축」이었다.
- cmd: `bash -n` 다섯 파일 · `./tools/prod-env-keys-check.sh`(S-27) → exit 0 / 0.

| 변이(앞 라운드엔 전부 초록이었다) | exit | 어긋남 |
| --- | --- | --- |
| F-1a runbook 에서 `\|\| [ "$$EXIT_STATUS" = 3 ]` 조항 삭제 | 1 | 1 |
| F-1b `$$SERVICE_RESULT` → `${SERVICE_RESULT}`(systemd 가 미리 빈 값으로 펼친다) | 1 | 1 |
| F-2 잠금 자리 점검 **호출 줄** 삭제 | 1 | 3 |
| F-3 충돌 조회 `docker ps -aq` → `-q` | 1 | 2 |

- 원복 뒤 재실행: exit 0 (`통과 60 · 어긋남 0`) · `git status --porcelain` 빈 출력.
- rollback: 복원 목록 같은 일곱 경로(재산출) · 임시 clone 재실측 ①~⑥ 통과 — 실측 HEAD 는 `rollback.md`.
