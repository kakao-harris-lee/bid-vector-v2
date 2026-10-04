# M6/6B-2 — 백업·복원·마이그레이션 되돌림 리허설 (계약 초안, 2026-10-05, 팀장)

- base: `e922dc7b`(PR #60 머지 = `main`) · 브랜치 `m6-6b2/2026-10-05` · worktree `bid-vector-v2-m6-6b2`
- 정본: 이 파일. 결정 ID `D-6B2-N`. 운영자 결정은 「운영자 승인」 절. 착수 조사 `_workspace/m6-6b2/00_scout.md`(읽기 전용, 미커밋).
- 성격: **운영 절차 slice**(완료 조건 7 「rollback/restore rehearsal 증거 존재」). production 코드 0 · **새 마이그레이션 SQL 0** · 스크립트(`tools/`) + CI step + runbook + evidence. Codex 심판: 마이그레이션 SQL 을 더하지 않고 파괴하는 것은 **자기가 만든 일회성 DB 뿐**이라 되돌리기 어려운 경로가 아니다 — 운영자가 요청하면 탄다(A-5).
- 레인: 구현 하나(`kotlin-implementer`, sonnet — 셸·ci.yml·runbook; Kotlin test 를 만들면 등재 등식 task 가 등재를 강제). 판정 `verifier`(opus) + `code-reviewer`(sonnet) 병렬.

## 왜 이 slice 인가

완료 조건 7 이 요구하는 「rollback/restore rehearsal 증거」가 저장소에 없다 — runbook 은 실수집 하나뿐, 백업·복원·되돌림을 다룬 ADR 0 건, 역 SQL 0 건. 6B-1 이 clean DB 재현 등식(`CleanMigration*Test` 다섯)을 세웠지만 **살아 있는 DB 를 복원해 같은 상태로 돌아오는가**는 아무도 재지 않았다. 실수집이 `m6-6g` 실행 상태 디렉터리에 쌓이는 지금, 운영 반입(M7) 전에 그 절차를 실측해 두는 자리다.

## 착수 실측(조사 레인)

| 항목 | 값 |
|---|---|
| Flyway 파일 | 15(V1~V17, **V10·V11 영구 공백** — 복원 단언은 「15 행 · max 17」) · community 판(undo 없음) · 설정은 `PersistenceWiring.migrate()` 한 곳, 전부 기본값(validate on · `cleanDisabled` · baselineOnMigrate=false) |
| 마이그레이션 성격 | **17 개 전부 추가 전용**(열 삭제·타입 변경·데이터 이동 0) → 되돌림 정본은 **forward-fix**(V16·V17 주석이 이미 그렇게 적음: 미적용 DB 는 파일 삭제, 적용된 DB 는 새 V 의 `DROP COLUMN`); 백업 복원은 **데이터 손실 축** 하나뿐 |
| 복원이 부딪칠 다섯 | ① `CREATE ROLE bidvector_app` 은 cluster 객체라 `pg_dump <db>` 가 담지 않는다 — GRANT 만 담겨 **새 클러스터 복원이 전부 실패**(1순위 실측 축; 6B-1 축9 권한 행렬이 기성 단언) ② `SECURITY DEFINER` 함수의 소유자 전환 ③ IDENTITY 시퀀스 넷의 현재값 ④ 비어 있지 않은 스키마 + 이력 없음이면 Flyway 가 거부 ⑤ `clean` 은 구성상 불가 |
| DB 자산 | compose postgres `postgres:16.4@sha256:e62fbf9d…`(다이제스트 핀), 볼륨 `bidvector-pg-data`, **host 포트 publish 없음**, env 전부 `${VAR:?}`; CI `container` job 자격 생성 = `openssl rand` → `::add-mask::` → `$GITHUB_ENV`. 이미지 위생 정책 둘은 `psql`·`pg_dump` 를 금지하지 않으나 **postgres 이미지가 도구를 이미 담아** 두 정책·두 Dockerfile 무편집 가능 |
| 이음매 | CI `container` job 의 **S-23b(쓰기 왕복 — `candidateLimit=13`·`revision+1` 을 남김)와 S-25(`down -v`) 사이**가 백업→파괴→복원→대조 자리 |
| **위험(새 발견)** | `docker/compose.yaml` 에 top-level `name:` 이 없다 → 프로젝트 이름이 디렉터리 basename **`docker`** 로 고정되어 **main·모든 worktree 가 같은 compose 프로젝트·같은 볼륨(`docker_bidvector-pg-data`)을 공유**한다. S-25 의 `down -v` 가 다른 worktree 의 실행을 지울 수 있다(CI 일회성 VM 에선 무해, 로컬 리허설에선 실재). 금지 자산: `bid-vector-v2-dev`(55432, 6G 실수집 보유) · legacy `bid_vector_db`(5432) · easy-doc · kis_paper |
| 넘어온 OPEN | 0(`OPEN-…-RETENTION` 넷은 전부 6B-3) |

## 항목(r1 확정)

| ID | 항목 | 수용 기준(실측) |
|---|---|---|
| D-1 | **백업 스크립트** `tools/db-backup.sh` — `pg_dump` custom 형식 + **`pg_dumpall --roles-only`(또는 역할 정의 파일)** + manifest(Flyway 이력 행 수·max version·표별 행 수·sha256) — 비밀값은 argv 가 아니라 환경/`PGPASSFILE` 로 | 백업 산출물에 자격 값 0(참조형 스캔) · manifest 가 Flyway 이력 15 행·max 17 을 적음 |
| D-2 | **복원 스크립트** `tools/db-restore.sh` — **새 일회성 postgres 컨테이너**(자기 생성, `-p` 명시 프로젝트/이름, host 포트 publish 없음)에 역할 → 스키마·데이터 순으로 복원, 소유자·`SECURITY DEFINER`·IDENTITY 현재값 포함 | 복원 뒤 **등식 셋**: Flyway `validate()` 통과 + 이력 행 동일 · 표별 행 수·내용 해시 동일 · **6B-1 축9 권한 행렬 동일**(역할·GRANT·소유자) |
| D-3 | **되돌림 리허설** — forward-fix 가 정본임을 **실측으로 보인다**: (i) 백업 → 가짜 `V18__rehearsal.sql`(열 추가, 테스트 전용 임시 파일, 커밋 안 함) 적용 → forward-fix `V19` 로 되돌림 → `validate()`·등식 셋 (ii) 데이터 손실 축: 쓰기 왕복 뒤 백업 → 파괴(표 TRUNCATE) → 복원 → `candidateLimit=13`·`revision` 동일 | (i)(ii) 둘 다 exit 0, 되돌린 뒤 이력에 V18·V19 가 남는 것(forward-fix 의 성질)을 runbook 에 적는다 |
| D-4 | **CI 배선** — `container` job 의 S-23b 와 S-25 사이에 리허설 step(S-23c) 추가(ci.yml `run` 블록 그대로가 `tools/one-command-check.sh` 와 같은 규율) | CI 초록 · 로컬 재현(ci.yml `run` 추출 실행) exit 0 · step 이 자기 생성 자원만 파괴 |
| D-5 | **runbook** `docs/runbook/m6-6b2-backup-restore.md` — 백업 주기·보관·복원 절차·되돌림 정책(forward-fix 정본, 백업 복원은 데이터 손실 축)·멈춤 조건·금지(운영 DB 에 `clean`/`down -v` 금지) | 절차 전부가 D-1~D-3 의 실측 명령과 1:1 |
| D-6 | **compose 프로젝트 이름 고정**(A-4) — `docker/compose.yaml` 에 `name: bidvector-v2` 한 줄(6C 산출물 편집 — in_scope 확장, 운영자 결정) | 프로젝트 이름이 **디렉터리와 무관하게 파일에서 결정**된다(어느 cwd 에서 불러도 `bidvector-v2`, base 파일은 어디서든 `docker`) · `name:` 제거 변이에서 `docker` 로 되돌아감 · 기존 호출(ci.yml·runbook) 무영향 실측. ~~두 worktree 가 같은 프로젝트를 들지 않음~~ — r2 D-6B2-5 로 정정(이 저장소의 worktree 끼리는 **같은** 이름을 든다; 갈림은 `COMPOSE_PROJECT_NAME`/`-p` 의 몫) |

## 위협 모델 경계 (Phase 2.5 (0), 초안)

**방어하는 것**: 백업으로 돌아올 수 없는 상태(역할 미포함·권한 상실·시퀀스 되감김·Flyway 거부)가 **운영 반입 전에** 실측으로 드러난다 · 리허설이 자기 밖 자원(다른 compose 프로젝트·dev 컨테이너·legacy DB)에 닿지 않는다 · 자격 값이 백업 산출물·argv·로그에 실리지 않는다. **방어하지 않는 것**: 운영 DB 의 실제 백업 저장소·암호화·보존(6B-3 + M7) · PITR/WAL 아카이브(이 slice 는 논리 백업만) · Flyway undo(community 에 없음 — forward-fix 정책으로 대신).

## in_scope (r1 확정)

- `tools/db-backup.sh` · `tools/db-restore.sh` · `tools/db-rehearsal.sh`(또는 하나로) · `.github/workflows/ci.yml`(**`container` job 의 step 추가만**) · `docs/runbook/m6-6b2-backup-restore.md` · `docker/compose.yaml`(**`name:` 한 줄만** — A-4 (a) 일 때) · `reports/evidence/m6/6b2/**` · `milestone-6.md`(착수·종결 문단만) · Kotlin test 를 만들면 그 파일 + `config/quality/gate-tests.properties`(등재 추가만).
- **out_scope**: production 코드 전부 · `adapters/src/main/resources/db/migration/**`(새 V 파일 금지 — 리허설의 V18/V19 는 커밋하지 않는 임시 파일) · 이미지 위생 정책·Dockerfile · 보존 기간(6B-3) · 운영 DB.

## acceptance

CI `container` job 명령 그대로(S-21~S-25 + 새 S-23c) 로컬 재현 exit 0 · Kotlin `check`(test 를 더하면) · `one-command-check.sh` 는 production 무변경이면 생략 가능(사유 등재). 항목마다 변이 ≥1 RED(예: 역할 복원 step 제거 → 권한 행렬 등식 RED · manifest 해시 변조 → RED · `name:` 제거 → 프로젝트 이름 공유 재현).

## rollback

in_scope 경로 한정 `git restore --source=<base>`; 공유 파일(`ci.yml`·`compose.yaml`·`gate-tests.properties`·`milestone-6.md`)은 커밋 해시 hunk — 목록은 실측 HEAD 에서 `git log` 로. 되돌려도 운영 영향 0(절차 자산뿐).

## 운영자 승인 (2026-10-05 결정 완료 — 「추천대로 진행해」: A-1 (a) · A-2 (a) · A-3 (c) · A-4 (a) · A-5 (a); 정본은 D-6B2-1)

- **A-1 백업 저장 위치** — (a) 리허설 안에서만 생성·폐기(저장소 밖 고정 자리는 M7 운영 반입에서) · (b) 저장소 밖 고정 자리 지금 지정(보존 기간이 6B-3 입력으로 신설) · (c) 미정. **추천 (a)**.
- **A-2 CI 주기** — (a) `container` job 상시 step(매 PR — 리허설은 같은 postgres 컨테이너 안이라 분 단위) · (b) 별도 schedule 워크플로(전제 복제 — D-6C-10 ④ 와 상충) · (c) `workflow_dispatch`. **추천 (a)**.
- **A-3 리허설 범위** — (a) 스키마·데이터만 · (b) + 시퀀스 · (c) **+ 역할·권한·소유자까지**(조사의 1순위 실측 축). **추천 (c)**.
- **A-4 compose 프로젝트 이름** — (a) `compose.yaml` 에 `name: bidvector-v2`(6C 산출물 한 줄, in_scope 확장) · (b) 리허설 호출에만 `-p` · (c) 무변경. **추천 (a)** — 이 호스트에서 main·worktree 가 볼륨을 공유하는 실재 위험이고, 어제 팀장이 돌린 container job 도 그 프로젝트 이름으로 `down -v` 했다.
- **A-5 Codex 심판** — (a) 안 탄다(마이그레이션 SQL 0, 파괴 대상은 자기 생성 DB) · (b) 탄다(비용 승인). **추천 (a)**.

## 계약 갱신 r1 (2026-10-05, 팀장 — 운영자 결정)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-1** | **운영자 결정 A-1~A-5 = 추천대로**: A-1 (a) 백업은 리허설 안에서만 생성·폐기(운영 저장소·암호화·보존은 M7·6B-3) · A-2 (a) `container` job 상시 step S-23c(매 PR) · A-3 (c) 리허설 범위 = 스키마·데이터 + 시퀀스 + **역할·권한·소유자**(`pg_dumpall --roles-only` 또는 역할 정의 선복원) · A-4 (a) `docker/compose.yaml` 에 `name: bidvector-v2` 한 줄(in_scope 확장, 6C 산출물 — D-6C-2 문면에 사실 추가) · A-5 (a) Codex 안 탐 | 운영자 2026-10-05 |
| **D-6B2-2** | **설계 검토(팀장, `_workspace/m6-6b2/01_design-review.md`) 요지**: (0) 경계 위 절 · (1) 등식은 **구성**(복원 뒤 Flyway `validate()` + 이력 행 + 표별 행 수·내용 해시 + 6B-1 축9 권한 행렬 — 손 목록 아님) · (2) 우회 다섯: 역할 복원 생략(권한 행렬 RED) · manifest 변조(해시 RED) · 시퀀스 되감김(IDENTITY 현재값 등식 RED) · 다른 프로젝트 자원 파괴(자기 생성 자원만 — 프로젝트 이름·컨테이너 이름을 리허설이 생성해 지목) · 비밀 argv(스캔 RED) · (2b) 새 public 표면 없음(스크립트·CI·문서) · (3) 과잉 후보 PITR/WAL 은 경계, 미달 후보 「되돌림 = 백업 복원」 오해는 runbook 이 forward-fix 정본을 못 박는다 | 설계 검토 |
| **D-6B2-3** | **리허설 자원 격리(필수)**: 리허설은 **자기가 만든** 일회성 postgres 컨테이너/프로젝트만 쓰고 파괴한다 — compose 프로젝트 `bidvector-v2`(A-4) 안의 서비스를 지목하되, 파괴(`down -v`·TRUNCATE·DROP)는 **S-23c 가 만든 복원 대상 컨테이너**에만; host 포트 publish 없음(`-p` 금지); `bid-vector-v2-dev`·legacy·easy-doc·kis_paper 는 이름·포트·볼륨 어느 축으로도 닿지 않음을 스크립트가 사전 단언(존재하는 컨테이너 목록과 교차 0) | 조사 7 |

## 계약 갱신 r2 (2026-10-05, 팀장 — 구현 완료 수령 · 동결 · 판정 SHA)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-4** | **구현 완료 수령·레인 동결.** 산출물 마지막 커밋 `36a3bff9`(= rollback 실측 HEAD) · evidence 커밋 `05042cd0` = **판정 SHA**. 팀장 대조: 변경 11 파일 +1,226/−0 전부 in_scope · production `src/main`·`db/migration`·`ml-engine` diff **0** · in_scope porcelain 빈 출력 · 스크립트 셋 mode 755 · 참조형 누출 스캔 evidence **0**(산출물 쪽 매치는 전부 플래그·환경변수의 **이름** — `ci.yml` 은 base 와 같은 1) · 호스트에 남은 `bidvector-v2` 컨테이너·볼륨 0 · rollback 술어 `git diff --name-only 36a3bff9..05042cd0 -- <복원 여섯 + milestone-6.md>` 빈 출력. 「미처리 지시 없음」 — 레인이 자기 보고에서 동결을 선언했다 | 레인 보고 2026-10-05 |
| **D-6B2-5** | **레인이 지시 문면 대신 택한 셋의 처분.** ① **역할 덤프에 자격 제외 플래그 — 채택.** 지시 「역할 제외 없이 그대로」는 *역할 필터링* 금지의 뜻이었고 레인도 역할을 거르지 않았다; 맨 형태는 로그인 역할의 SCRAM 검증자를 평문 파일에 적어 위협 모델 「자격 값이 백업 산출물에 없다」와 정면 충돌(실측 1건 → 0건). 대가(운영 복원 뒤 로그인 암호 별도 설정)는 runbook §2·§6 에 등재됨. ② **D-6 수용 문면 정정(팀장 오기).** 「두 worktree 가 같은 프로젝트를 들지 않음」은 틀렸다 — 파일에 이름을 박으면 이 저장소의 모든 checkout 이 **같은** `bidvector-v2` 를 든다. 결정 A-4 (a) 가 닫는 것은 (가) 디렉터리 basename `docker` 로 **다른 저장소**와 충돌하는 축 · (나) 리허설이 「내 프로젝트 안의 서비스」를 이름으로 단언할 수 있는 축이고, **이 저장소 worktree 끼리의 공유는 남는다**(알려진 제한 — 로컬 container job 재현은 호스트 전체 1개, 빌드 직렬화 규율과 같이). 갈라야 하면 파일 편집 없이 `COMPOSE_PROJECT_NAME`/`-p` 가 이긴다 — 팀장 실측: 기본 `bidvector-v2` · env 지정 시 그 이름 · `-p` 지정 시 그 이름. 위 D-6 표에 정정 반영. ③ **리터럴 13 대신 원본 대조 · 권한 행렬을 해시 대신 객체 — 채택.** 전자는 매직 넘버 제거 + S-23b→S-23c 순서를 단언으로 잠금(S-23c 를 앞으로 옮기면 「쓰기 왕복이 선행하지 않았다」 RED); 후자는 판정 문면이 「어느 등식의 어느 이름」을 대야 한다는 요구의 귀결 | 팀장 판단 |
| **D-6B2-6** | **판정 레인 표적**(verifier opus + code-reviewer sonnet 병렬, 판정 SHA `05042cd0`, 산출물 `36a3bff9`): (a) 격리 — 리허설이 원본에 쓰기·파괴 명령을 갖지 않는가(스크립트 전수), 복원 대상이 라벨 등식 없이 손대지는 자리가 없는가, `--network none`·host publish 0, 다른 compose 프로젝트·`bid-vector-v2-dev`·legacy 에 닿는 경로 0(변이: 라벨 불일치 컨테이너 지목 → exit 3) (b) 등식 열 가지 각각이 **살아 있는가**(측정별 변조 → 그 이름을 대며 RED; 무시 목록 jq 결함의 회귀 — `flywayHistory` 무시 호출에서 다른 측정이 여전히 재지는가) (c) 역할 축 — 역할 복원 생략·`--exit-on-error` 제거·종료 코드 무시 세 겹을 전부 뚫어도 `privilegeMatrix` 등식이 붉은가 (d) forward-fix — V18/V19 가 임시 디렉터리에만 살고 `adapters/src/main/resources/db/migration/` 에 남지 않음(실행 뒤 porcelain) · 이력에 18,19 남음 단언이 **순서**까지 보는가 (e) S-23c 의 ci.yml `run` 블록 재현(참조 스크립트 `scratchpad/rb6g2c/container-job.sh` 와 같은 추출 방식; 자격 생성은 `openssl rand`, `::add-mask::` 줄 redaction, 값 출력 0) (f) 자격 값·공고 식별자가 산출물·evidence·로그에 0(참조형 스캔만) (g) rollback — 복원 여섯·hunk 하나가 `36a3bff9..05042cd0` 에서 움직이지 않았는가, 목록 등식 `comm` 양방향 (h) `one-command-check.sh` 생략 사유의 성립(production diff 0) (i) 크기 게이트(evidence 341 ≤ 산출물) · `file:line` 좌표 0 · 축어 스캔 어휘 0. **빌드 규율**: 전건 `check` 는 verifier 만, 사전 확인 셋 별도 호출, flock, `--no-daemon`, 끝나면 자기 daemon PID 만 정리; 00:30~01:00 KST 는 Gradle·docker 빌드 시작 금지 | 설계 검토 (2) · D-6B2-3 |

## 하네스 레인 변경

`git log --oneline e922dc7b..HEAD -- CLAUDE.md .claude/` → **없음**(r2 시점). 팀장 레인 커밋은 `reports/evidence/m6/6b2/scope.md`(`git log -- <파일>` 산출: 초안 `0e0b1b5e` · r1 `8ca6cf1b` · r2 이 커밋)와 `milestone-6.md`(`93b04488` 착수 문단) 뿐.

## 입력·이관

- `OPEN-…-RETENTION` 넷 → 6B-3(이 slice 는 보존 기간을 정하지 않는다). PITR/WAL → M7 운영 반입. compose `name:` 을 (a) 로 고치면 6C D-6C-2 문면에 사실 추가.
