# M6/6B-2 실행 기록

정본 계약은 같은 디렉터리의 `scope.md`, 수용 판정은 `checklist.md`, 되돌림 실측은 `rollback.md`.
명령은 전부 worktree `bid-vector-v2-m6-6b2`(브랜치 `m6-6b2/2026-10-05`)에서 돌았다.
**마지막 HEAD 의 게이트 결과 정본은 verifier 리포트와 PR 조치 코멘트다** — 이 표는 그 직전까지를 담는다.

## 2026-10-05T07:59+09:00 — ① compose 프로젝트 이름

- cmd: `docker compose -f docker/compose.yaml config --format json | jq -r .name` (이 worktree · main worktree · 각각 다른 cwd)
- exit: 0
- 핵심 결과: 바꾼 파일은 어느 디렉터리에서 불러도 `bidvector-v2`, base 파일은 어디서든 `docker`

- cmd: 위와 같되 `name:` 줄을 지운 변이 → 저장 바이트로 복원
- exit: 0
- 핵심 결과: 변이에서 이름이 `docker` 로 돌아갔고, 복원 뒤 `git diff --numstat` 와 `git status --porcelain` 둘 다 빈 출력

- cmd: `grep -rn 'docker compose' --include='*.yml' --include='*.sh' --include='*.md'`
- exit: 0
- 핵심 결과: 살아 있는 호출은 전부 `-f docker/compose.yaml` 형태라 프로젝트 이름에 의존하지 않는다(이름을 적은 것은 과거 slice 의 evidence 뿐)

## 2026-10-05T08:07+09:00 — ② 백업 스크립트

- cmd: `./tools/db-backup.sh <일회성 컨테이너> <출력 디렉터리>` (V1~V17 을 psql 로 올린 probe)
- exit: 0
- 핵심 결과: 표 18 · 시퀀스 4 · 역할 2 · 권한 행렬 1역할 · 함수 5 · 트리거 25 · 제약 152, `notice_audit` 행이 SELECT만·INSERT없음으로 6B-1 축9 기대 행렬과 일치

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <백업 디렉터리>`
- exit: 1
- 핵심 결과: 매치 0 — 역할 덤프의 자격 제외 플래그를 걸기 전에는 1 건이었다(플래그 이름의 정본은 `tools/db-backup.sh` 호출 줄)

## 2026-10-05T08:10+09:00 — ③ 복원 스크립트

- cmd: `./tools/db-restore.sh <백업> <새 컨테이너> <이미지> restore_1 <표식>`
- exit: 0
- 핵심 결과: 새 클러스터로 복원해 측정 10가지 전부 일치

- cmd: 가드 셋 — 라벨 없는 기존 컨테이너 지목 · manifest 의 덤프 해시 변조 · 이미 있는 데이터베이스 재복원
- exit: 3 / 1 / 3
- 핵심 결과: 각각 「이 실행이 만든 것이 아니다」 · 「sha256 이 manifest 와 다르다」 · 「빈 DB 로만 한다」로 끊는다

- cmd: 변이 넷 — 역할 복원 생략(m1) · +역할 존재 단언 생략(m2) · +`--exit-on-error` 제거(m3) · +복원 종료 코드 무시(m4)
- exit: 1 / 1 / 1 / 1
- 핵심 결과: m1 은 「없는 역할: bidvector_app」, m2·m3 은 pg_restore 가 GRANT 에서, m4 는 등식이 `privilegeMatrix / bidvector_app` 로 붉다 — 세 겹이 같은 축을 막는다

- cmd: 측정 축별 변조 → `./tools/db-restore.sh --compare`
- exit: 1 (손 안 댄 쌍은 0)
- 핵심 결과: `sequences / api_request_audit_id_seq` · `privilegeMatrix / bidvector_app` · `functions / guard_authoritative_slot/0` · `triggers / notice.guard_notice_allocated_budget` 로 측정과 이름을 각각 댄다

## 2026-10-05T08:19+09:00 — ④ 리허설

- cmd: `./tools/db-rehearsal.sh docker/compose.yaml postgres` (compose 환경 + 쓰기 왕복 뒤)
- exit: 0
- 핵심 결과: 25초 — 이력 15행 top 17 · 누출 스캔 0 · 복원 등식 10가지 · validate 통과 · forward-fix 뒤 top 19 이고 이력 밖 측정 일치 · 음성 대조 붉음 · 재복원 양성, 남은 리허설 자원 0

- cmd: 변이 셋 — V19 가 열을 지우지 않음(A) · TRUNCATE 생략(B) · 모르는 서비스 지목(C)
- exit: 1 / 1 / 3
- 핵심 결과: A 「forward-fix 뒤에도 열이 남아 있다」 · B 「데이터를 지웠는데 등식이 통과했다」 · C 「동작 중인 'not-a-service' 가 없다」

- cmd: 변이 뒤 `git diff --numstat -- tools/db-rehearsal.sh` · `git status --porcelain`
- exit: 0
- 핵심 결과: 둘 다 빈 출력 — 변이는 전부 커밋 뒤에 걸고 저장 바이트로 되돌렸다

## 2026-10-05T08:24+09:00 — ⑤ acceptance: `container` job 전건 로컬 재현

- cmd: ci.yml `container` job 의 `run` 블록 13개를 순서대로(러너 env 와 `$GITHUB_ENV` 만 흉내)
- exit: 0
- 핵심 결과: S-21~S-25 전부 exit 0, 새 S-23c 포함. 끝난 뒤 compose 프로젝트 `bidvector-v2` 의 컨테이너·볼륨 0

`one-command-check.sh`(S-20)는 돌리지 않았다 — `scope.md` acceptance 가 production 무변경이면 생략을 허용하고,
이 slice 는 `*/src/main/**` 과 `ml-engine/` 을 한 줄도 건드리지 않는다(변경은 셸 스크립트 셋·CI step·compose
한 줄·runbook·evidence 뿐). Kotlin `check` 는 evidence 를 훑는 게이트 때문에 아래에서 따로 돌린다.

## 2026-10-05T09:0x+09:00 — Kotlin `check` job

- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 350 task 중 32 수행. **`leakPatternGate` 가 이번 실행에서 실제로 돌았다**(UP-TO-DATE 표시 없음) —
  evidence 를 훑는 게이트라 이 slice 에서 붉어질 수 있는 유일한 자리였다

- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: 33 task 전부 up-to-date

## 게이트 실측

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/6b2/`
- exit: 1
- 핵심 결과: evidence 쪽 매치 0 — 스캔 어휘를 축어로 적지 않는다(플래그 이름의 정본은 산출물과 runbook).
  게이트의 scanRoot 는 `reports/evidence` 뿐이고, 산출물 쪽 매치는 전부 **환경변수·플래그의 이름**이지
  값이 아니다(`ci.yml` 의 매치 수는 base 와 같은 1 로 변하지 않았다)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0
- 핵심 결과: 빈 출력. 양성 대조 — 산출물 하나에 줄을 덧붙이자 `M` 로 잡혔고, 심은 줄만 비파괴로 절삭해 되돌렸다
