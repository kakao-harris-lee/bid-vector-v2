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
| 마이그레이션 성격 | **15 개(V17 까지) 전부 추가 전용**(열 삭제·타입 변경·데이터 이동 0) → 되돌림 정본은 **forward-fix**(V16·V17 주석이 이미 그렇게 적음: 미적용 DB 는 파일 삭제, 적용된 DB 는 새 V 의 `DROP COLUMN`); 백업 복원은 **데이터 손실 축** 하나뿐 |
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

- `tools/db-backup.sh` · `tools/db-restore.sh` · `tools/db-rehearsal.sh`(또는 하나로) · `.github/workflows/ci.yml`(**`container` job 의 step 하나와 그 사유 주석만**) · `docs/runbook/m6-6b2-backup-restore.md` · `docker/compose.yaml`(**`name:` 키와 그 사유 주석만** — A-4 (a) 일 때; r8 문면 정정) · `reports/evidence/m6/6b2/**` · `milestone-6.md`(착수·종결 문단만) · Kotlin test 를 만들면 그 파일 + `config/quality/gate-tests.properties`(등재 추가만).
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

## 계약 갱신 r3 (2026-10-05, 팀장 — 판정 r1 수령 · 수정 라운드 1/5)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-7** | **판정 r1 수령 @`05042cd0`**: verifier r1 **not-ready**(`_workspace/m6-6b2/02_verifier_r1.md` — H-1 · M-1 · M-2 · L-1~L-3; 표적 (a)~(i)·container job 13 step·`check` 통과) · code-reviewer r1 **새 high 있음**(`03_code_review_r1.md` — G-1 high · G-2 medium · G-3~G-9 low). **G-1 ≡ M-1**(같은 결함, 발동 조건 서술만 다름) · **G-7 ≡ L-1**. 재작업 **1/5** | 판정 레인 |
| **D-6B2-8** | **수정 라운드 처분 — 산출물(각각 별도 커밋)**: ① **H-1** 빈 실행 표식 거부(빈 문자열·공백만은 exit 3) + `docker inspect --type container` + 라벨 등식은 「라벨이 **존재**하고 값이 같다」로(없는 라벨의 빈 문자열과 구분) · 회귀: 라벨 없는 자기 컨테이너에 빈 표식 → exit 3 ② **G-1/M-1** `_compare` 의 jq 실패를 명시 분기로 받아 `return 1`(억제 문맥 무관) + 양쪽 `.measurements` 가 object 이고 `schema` 가 `bidvector-db-backup/1` 임을 먼저 단언 · 회귀: 잘린/`measurements` 없는/`[]` manifest 다섯 경우 전부 exit 1(양방향) ③ **M-2** 리허설이 원본을 **구조로** 단언 — 원본 컨테이너의 compose 라벨 `com.docker.compose.project.config_files` 가 인자로 준 compose 파일의 realpath 와 같을 것(이 파일에서 생성된 서비스만 원본이 된다; 프로젝트 이름 문자열 대조가 아니라 **생성 출처** 대조라 `COMPOSE_PROJECT_NAME` 으로 갈라 둔 worktree 도 자기 파일로 뜬 것이면 통과하고, 다른 저장소·다른 compose 파일의 컨테이너는 이름이 무엇이든 거부) · 회귀: 다른 config_files 라벨을 가진 컨테이너를 서비스로 지목 → exit 3 ④ **G-2** manifest 에서 온 `$role`·`$SUPERUSER` 에 `_assert_plain_identifier` 와 같은 모양 검사, `encoding`·`collate`·`ctype` 리터럴은 `psql -v` 변수 인용으로 ⑤ **low 코드 일괄(한 커밋)**: G-3 classpath 를 `/jvm/migrations` 앞으로(이 checkout 의 마이그레이션이 정본 — 낡은 이미지는 체크섬에서 붉어짐) + 주석 사실화 · G-7/L-1 `getenv` 제거(`null` 전달, `trust` 의존 주석) · G-8 `[ -n … ]` 선단언 · G-9 함수 키 `oid::regprocedure::text` · L-2 권한 행렬에 시퀀스(`has_sequence_privilege` USAGE/SELECT/UPDATE)·함수(`has_function_privilege` EXECUTE) 축 추가 — 전부 카탈로그 발견. **①②③ 은 게이트 술어 변경이라 severity 무관 표적 재검증** | verifier · code-reviewer |
| **D-6B2-9** | **수정 라운드 처분 — 문서·evidence(한 커밋, 산출물 커밋 뒤)**: runbook §0 「17 개」→「15 개(V17 까지)」(G-6; scope.md 착수 실측의 같은 오기는 이 r3 가 고침) · §3 「대상은 자기 자원뿐」 문면을 ① 뒤 사실로(L-3) · §7 에 G-4(임시 암호가 `docker inspect` Env 에 컨테이너 수명 동안 남음 — 미사용 난수, 포트 0) · §5 에 G-5(남은 리허설 컨테이너 치우는 줄; 단독 복원은 만들기만 함, 치명 신호는 EXIT 트랩을 안 돎) · §4 에 M-2 의 출처 단언 · checklist 「우회 4」 행을 ①③ 뒤 사실로, 「알려진 제한 2」의 「계약 문면 정정은 팀장 소관」 줄을 r2 D-6B2-5 ② 참조로, 알려진 제한에 함수 ACL 범위(L-2 잔여가 있으면)·G-3 선택 추가 · commands.md 에 이번 라운드 회귀 실측 행(한 줄씩) · rollback.md 실측 HEAD 를 **마지막 산출물 커밋**으로 재실측(복원 여섯 그대로인지 `comm` 양방향, hunk 목록 `git log` 재산출) | evidence-pack |
| **D-6B2-10** | **보고 항목**: 이번 라운드가 만든 새 public 표면(새 CLI 인자·env 이름·라벨 키가 생겼는가) · 새 파일 ↔ in_scope 대조 · 산출물 마지막 SHA·evidence SHA · 「미처리 지시 없음」 뒤 동결. 판정 r2 = verifier 표적 재검증(①②③ 변이 + 회귀 다섯) + code-reviewer r2 | 규율 |

## 계약 갱신 r4 (2026-10-05, 팀장 — 수정 라운드 1 수령 · 동결 · 판정 r2 표적)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-11** | **수정 라운드 1 수령·동결.** 산출물 커밋 `30b5c34e`(H-1) · `a900dfe2`(G-1/M-1) · `64e9b309`(M-2) · `4517be43`(G-2) · `a9101bc5`(low 일괄) · `8ce3bb52`(runbook = **rollback 실측 HEAD**) · evidence `c995b576`·`d7a93d99` = **판정 SHA `d7a93d99`**. 팀장 대조: 이번 라운드 변경 8 파일 전부 in_scope · 새 파일 0 · production·migration diff 0 · evidence 누출 스캔 0 · 크기 422 ≤ 996 · `git diff --name-only 8ce3bb52..d7a93d99 -- <복원 여섯 + milestone-6.md>` 빈 출력 · 호스트 잔여 컨테이너·daemon 0 | 레인 보고 |
| **D-6B2-12** | **레인 판단 둘의 처분.** ① **문서 커밋 분할 채택** — D-9 가 「한 커밋」이라 적었으나 runbook 은 rollback 복원 대상이라 목록 갱신과 같은 커밋에 두면 실측 HEAD 가 자기를 담은 커밋을 가리키게 된다(evidence-pack 자기참조 금지); 레인의 분할이 맞고 D-9 문면이 틀렸다. ② **manifest `schema` 버전 유지(알려진 제한 9 채택)** — 측정 모양이 바뀌었으나(`privilegeMatrix` 종류 키·함수 전 서명 키) 버전 문자열은 `/1` 그대로. 지금 어떤 manifest 도 리허설 밖에서 살지 않으므로(A-1 (a)) 구·신 혼재가 없고, 혼재가 생겨도 어긋남 방향은 **붉음**(거짓 일치가 아니다). 버전 올림은 **백업이 리허설 밖에 보존되기 시작하는 slice**(6B-3 보존·M7 저장소)의 첫 항목으로 이관 — 그때 `schema` 등식이 「다른 판으로 비교하지 않는다」를 구조로 막는다 | 팀장 판단 |
| **D-6B2-13** | **판정 r2 표적**(verifier 표적 재검증 — 술어 변경 ①②③ 은 severity 무관; code-reviewer r2): (a) H-1 — 빈·공백·틀린 표식 각 exit 3, 라벨 없는 컨테이너 무변경(역할 속성 전후 동일), 구판 술어 복귀 변이 exit 0 재현 (b) G-1/M-1 — 다섯 깨진 입력 × 양방향 exit 1, 복원 경로가 생성 **전에** manifest 를 단언하는가(컨테이너를 만들기 전에 끊기는가) (c) M-2 — 다른 compose 파일로 뜬 같은 서비스 이름 컨테이너 exit 3 · 자기 파일로 뜬 것은 `COMPOSE_PROJECT_NAME` 을 바꿔도 통과 · realpath 대조가 symlink·상대경로에 흔들리지 않는가 (d) G-2 — 따옴표 든 역할 이름 manifest 가 SQL 조립 전에 끊기는가 (e) low 일괄 — classpath 순서 뒤 리허설 validate 가 **이 checkout** 의 파일을 읽는가(checkout V 파일 한 글자 변이 → 체크섬 RED, 저장 바이트 복원) · 시퀀스 USAGE·함수 EXECUTE 회수 → 그 이름을 대며 RED · 함수 키 전 서명 (f) 등식 축이 늘었으니 열 가지 + 신설 축이 **전부 여전히 살아 있는가**(측정별 변조) (g) container job 전건 재현 exit 0 · Kotlin `check` @판정 SHA (h) rollback 술어·`comm` 양방향·되돌린 트리 (i) 문서 — runbook §0/§3/§4/§5/§7 과 checklist 가 코드와 1:1 인가, 「계약 문면 정정은 팀장 소관」 잔존 0, 알려진 제한 9 의 서술이 D-6B2-12 ② 와 같은 방향인가 (j) 새 public 표면 0 · 제거된 env 이름이 문서에서도 사라졌는가 | 규율 |

## 계약 갱신 r5 (2026-10-05, 팀장 — 판정 r2 수령 · 승인 전 일괄 라운드)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-14** | **판정 r2 수령 @`d7a93d99`**: verifier r2 **ready-for-review**(`04_verifier_r2.md` — H-1·M-1·M-2 실측 닫힘, 구판 술어 복귀 변이 셋 전부 재개방 재현; 새 low 넷 R2-L-1~4) · code-reviewer r2 **새 high 없음**(`05_code_review_r2.md` — G-1~G-9 아홉 닫힘, 새 R-1 medium · R-2~R-6 low). 겹침: **R-2 ∪ R2-L-1**(G-3 기제 — Flyway 는 classpath 순서와 무관하게 디렉터리와 jar 의 `db/migration` 을 둘 다 읽고 같은 버전·다른 바이트면 「more than one migration」으로 거부; 「체크섬에서 붉어진다」는 서술이 틀렸고 순서 변경 자체는 무해) · **R-4 ≡ R2-L-2**(권한 행렬 판정 줄이 객체 이름을 못 댐). 재작업 **1/5 유지** — 산출물 blocker/high 0 이므로 이 라운드는 차단 라운드가 아니라 **승인 전 일괄**이다 | 판정 레인 |
| **D-6B2-15** | **일괄 라운드 처분(산출물 한 커밋 → runbook 한 커밋 → evidence 한 커밋)**: ① **R-1** `$role` 의 `_assert_plain_identifier` 호출 삭제(값 자리는 `psql -v` 인용이 닫는다; `$SUPERUSER` 가드는 식별자 자리라 유지) ② **R-3** 도구 검사에 `realpath` ③ **R-4/R2-L-2** `_compare` 가 값이 object 이면 한 단계 더 파서 `측정 / 역할 / 객체` 로 보고 ④ **R-5** `IMAGE` 를 compose 파일이 아니라 원본 컨테이너 `docker inspect --type container -f '{{.Config.Image}}'` 에서(`APP_IMAGE` 는 파일 유지 — 원본에 앱 컨테이너가 없을 수 있다) ⑤ **R-6** 빈 manifest 의 사유 어휘(`empty`) ⑥ **R2-L-3** 복원 경로의 깨진 manifest 종료 코드를 규약(3 대상 오류)으로 — 형식 단언을 해시 대조 루프 **앞**으로 ⑦ **R-2/R2-L-1** `db-rehearsal.sh` 머리 주석의 기본값 괄호를 제자리로 + classpath 문단을 「둘 다 읽히며 바이트가 다르면 Flyway 가 거부한다 — 순서는 어느 쪽이 먼저 열리는가일 뿐」으로; runbook §7·checklist 제한 11 같은 문면 ⑧ **R2-L-4** checklist 제한 9 에 D-6B2-12 ② 인용(버전 유지·혼재 시 붉음·6B-3/M7 이관) ⑨ runbook §5 에 R-1 의 결과(역할 이름 모양 제약 없음) 반영이 필요하면 한 줄. **rollback 실측 HEAD 를 마지막 산출물 커밋(runbook 커밋)으로 재실측**(복원 여섯 그대로 — `comm` 양방향, hunk 목록 `git log` 재산출, 되돌린 트리 ④~⑥ 는 버릴 clone, 전건 `check` 호스트 규율) | 규율 「장부층·low 는 승인 전 일괄」 |
| **D-6B2-16** | **일괄 뒤 표적 재검증(가볍게)** — ①(술어 완화)·④(값 출처 변경)·⑥(종료 코드)만 verifier 가 본다: `-`·`.`·대문자 역할 이름 manifest 수용 + 따옴표 역할은 여전히 fail-closed · `IMAGE` == 원본 컨테이너 이미지 · 깨진 manifest 셋 exit 3 컨테이너 생성 전. 그 밖은 PR `/code-review` 가 본다. 통과하면 **종결**(verifier ready-for-review 유지 + 운영자 승인은 PR 머지 결정으로) | 「게이트 술어를 바꾸는 커밋은 severity 무관 표적 재검증」 |

## 계약 갱신 r6 (2026-10-05, 팀장 — 일괄 라운드 수령 · 동결 · 표적 재검증 착수)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-17** | **일괄 라운드 수령·동결.** 산출물 `a2e949af`(①~⑥ 코드) · runbook `47d5663b`(= **rollback 실측 HEAD**) · evidence `91f7303a` = **판정 SHA**. 팀장 대조: 변경 7 파일 전부 in_scope · 새 파일 0 · production diff 0 · evidence 누출 0 · 크기 483 ≤ 1,055 · `git diff --name-only 47d5663b..91f7303a -- <복원 여섯 + milestone-6.md>` 빈 출력 · 호스트 잔여 0. 레인이 commands.md 의 옛 「exit 1」 수치를 이번 라운드의 3 으로 고쳐 쓴 것은 낡은 수치 방지로 채택. 읽히는 Docker 소유 라벨 하나(`com.docker.compose.project.config_files`)가 늘었을 뿐 새 public 표면 0. 표적 재검증(D-6B2-16 셋)을 verifier 에 건다 — 통과하면 종결 | 레인 보고 |

## 계약 갱신 r7 (2026-10-05, 팀장 — 종결)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-18** | **종결 판정.** verifier 표적 재검증 **ready-for-review** @`91f7303a`(`06_verifier_targeted.md` — D-6B2-16 셋 성립: 실제 역할 이름 `-`·`.`·대문자·따옴표 복원 끝까지 + manifest 위조 이름 exit 1 구문 오류 0 · 복원 대상이 원본 컨테이너 이미지로(옛 줄 복귀 변이 검출) · 깨진 manifest 넷 exit 3 생성 0 사유 parse/shape/shape/empty · R-4 객체 이름 보고 · rollback 술어·`comm` 0; `check`·container job 은 지시로 생략). 판정 이력: verifier r1 not-ready → r2 ready-for-review → 표적 ready-for-review · code-reviewer r1 high(≡M-1) → r2 새 high 없음. 재작업 **1/5**. 종결 문단 `milestone-6.md` `61874bfc`; **팀장 rollback 재실측 @`61874bfc`**(rollback.md — hunk 둘 역적용 conflict 0, 트리 base 동일). 다음: push → PR → `/code-review` → 판정·조치 코멘트 → 머지(운영자 상시 지시 「리뷰 이상 없으면 PR·머지」) | 판정 레인 |
| **D-6B2-19** | **6C D-6C-2 「사실 추가」의 자리** — r1 D-6B2-1 이 「D-6C-2 문면에 사실 추가」라 적었으나 `reports/evidence/m6/6c/**` 는 닫힌 slice 의 evidence 이고 이 slice 의 in_scope 밖이다. 고치지 않는다. compose `name:` 의 사실은 `docker/compose.yaml` 머리 주석 + 이 계약 D-6B2-5 ② + milestone 종결 문단이 정본 | in_scope 경계 |

## 계약 갱신 r8 (2026-10-05, 팀장 — PR #61 리뷰 수령 · 조치 라운드)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-20** | **PR #61 리뷰 수령 @`85f54627`.** `/code-review`(리뷰어 다섯 → 후보 22 → 80 이상 **2**): **A** 리허설이 V18/V19 를 고정 이름으로 써서 다음 실 V18 이 들어오면 S-23c 가 「more than one migration」으로 붉어짐(100) · **B** 데이터 손실 음성 대조가 비-0 종료를 전부 「붉음」으로 읽음 — exit 2·3 도 통과(100). CI 세 job(`check`·`container`·`ml-engine`) 초록 @`85f54627`. 75 이하 20 중 처분: 75 — C 누출 스캔이 패턴 파일 부재(grep exit 2)·바이너리 매치(stderr)에서 0건으로 읽힘 · D compose `name:` 제거를 아무 술어도 못 잡음(`config` 는 basename 을 돌려줌) · E rollback.md 머리 실측 HEAD·술어가 `47d5663b` 인 채 종결 문단 커밋 뒤 「미검증」 · H 착수/종결 문단 빈 줄 없음 · I `grep -q` 파이프 pipefail(ci.yml 이 금지한 모양) · K rollback 나머지 수 「A 1·M 1」 낡음 · O `javac --release 21` 없음 · R runbook 전제·`--compare` 음성 대조 의무·종료 코드 표 없음; 72 — F rollback 「치울 외부 상태 없음」이 compose 이름 되돌림에 거짓(6C 선례: 되돌리기 전 `down -v`) + 옛 이름 스택 1회 정리; 50 — G 착수 문단 원인 문면 · J `check` task 수 350/359 불일치 · L compose 주석이 이름 위 단언을 과장 · M S-23c 주석 「읽지도 않는다」 과장 · N 종료 코드 규약 이탈 다섯 자리 · Q `chmod -R` 가 V18/V19 쓰기 전 · S in_scope 「한 줄만/step 추가만」 문면·하네스 「뿐」 · T `_assert_manifest` 가 빈 measurements 허용; 0 — P(`docker exec` 는 root 라 rm 성립) · U(CI 초록) · V(`wc -l` 자리는 printf 래퍼 없음) | 리뷰 |
| **D-6B2-21** | **조치 처분.** 팀장(이 r8 과 함께): G·H → `milestone-6.md` `6897e50e`(착수 원인 문면 D-6B2-5 ② 정정 + 빈 줄) · S → 위 in_scope 문면 정정 + 하네스 절. **레인(산출물 한 커밋 → runbook 한 커밋 → evidence 한 커밋)**: ① A 프로브 버전 = 복사한 파일의 max(version)+1 과 +2 (파일명·DROP 대상 열 이름은 그대로) — 회귀: 임시 디렉터리에 가짜 `V18__x.sql` 을 더해도 리허설이 19/20 으로 돌아 통과 ② B 음성 대조 `rc` 포착 → **정확히 1** 만 통과, 1 외는 그 코드와 사유를 대며 `_die 1`; `2>&1` 제거(어긋남 줄은 보이게) ③ C 패턴 파일 `-r` 선단언(exit 2) · grep 상태 포착(0/1 만 정상, ≥2 는 `_die 2`) · `-a` 로 바이너리도 텍스트로 ④ D 리허설이 compose **파일 자체**에 top-level `name:` 이 있음을 단언(파일 grep — 해석 결과가 아니라 선언) — 회귀: `name:` 줄 제거 → exit 3 ⑤ I `_wait_ready` 로그를 변수로 받아 `case` 매치 ⑥ O `javac --release 21` ⑦ Q V18/V19 쓴 뒤 `chmod a+r` ⑧ T `_assert_manifest` 에 `measurements | length > 0` ⑨ N 종료 코드 — sha256 불일치·식별자 모양 → 3, `compose config`·`printenv` 실패 → `\|\| _die … 3`, 맨 psql 호출에 `\|\| _die … 1` ⑩ L·M 주석 문면(compose: 이름은 결정적 접두·다른 저장소 충돌 차단, 단언의 자리는 출처·실행 표식 라벨; S-23c: 「값을 로그·argv 로 내보내지 않는다 — `compose config` 출력은 jq 로만 흐른다」). runbook: R(전제 = 도구 여섯 + compose env 가 그 셸에 있을 것 · `--compare` 무시 목록 쓰면 음성 대조 의무 · §5 머리에 종료 코드 1/2/3 표) · F(§7 「이 변경 전에 띄운 스택은 프로젝트 `docker` 로 남는다 — `docker compose -p docker -f docker/compose.yaml down -v` 로 한 번 치운다」). evidence: E rollback.md 머리 실측 HEAD = 마지막 산출물 커밋(runbook 커밋)으로 재실측(hunk 목록은 `git log` 재산출 — milestone 커밋 셋) · F 「되돌림 시점에 치울 외부 상태 없다」 → 「compose 이름을 되돌리기 **전에** `docker compose -f docker/compose.yaml down -v`」(6C 선례) + 비활성화 행 같은 문면 · K 나머지 수 재산출 · J `check` 수를 새 HEAD 실측 하나로 통일(둘 다) · commands.md 회귀 행(①②③④ 각 한 줄). **①②③④ 는 술어 변경 — verifier 표적 재검증**(A 가짜 V18 공존 · B exit 3 주입 시 붉음 · C 패턴 파일 제거 시 exit 2 · D `name:` 제거 시 exit 3). 재작업 1/5 유지(차단 라운드 아님) | 「게이트 술어 변경은 표적 재검증」 |

## 계약 갱신 r9 (2026-10-05, 팀장 — 조치 라운드 수령 · 동결 · 표적 재검증)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6B2-22** | **PR #61 조치 라운드 수령·동결.** 산출물 `4a29b175`(①~⑩) · runbook `4aaf5966`(= **rollback 실측 HEAD**) · evidence `efb8c763` = **판정 SHA**. 팀장 대조: 변경 10 파일 전부 in_scope · 새 파일 0 · production diff 0 · evidence 누출 0 · 크기 579 ≤ 1,111 · `git diff --name-only 4aaf5966..efb8c763 -- <복원 여섯 + milestone-6.md>` 빈 출력 · 호스트 잔여 0. **J 처분 변경 채택** — `check` task 수는 clone(처음부터)과 worktree(증분)가 구조적으로 다른 수를 내므로 하나로 통일하지 않고 **둘 다 제거**(exit·게이트 수행·test 2,679 만 남김). 표적 재검증 → verifier: ① 가짜 V18 적용 공존에서 19/20 으로 통과 ② 음성 대조 exit 3 주입 시 `_die 1` ③ 패턴 파일 제거 시 exit 2 ④ `name:` 줄 제거 시 exit 3 — 전부 구판 복귀 변이 대조 포함. 통과하면 push → PR 조치 코멘트 → 머지 | 레인 보고 |

## 하네스 레인 변경

`git log --oneline e922dc7b..HEAD -- CLAUDE.md .claude/` → **없음**(r2 시점). 팀장 레인 커밋은 `reports/evidence/m6/6b2/scope.md`(`git log -- <파일>` 산출: 초안 `0e0b1b5e` · r1 `8ca6cf1b` · r2 `0d96bc58` · r3 `417a8727` · r4 `d9c0d9a7` · r5 `caa78fe3` · r6 `de88833e` · r7 `85f54627`(+ `rollback.md` 팀장 재실측 절) · r8 `181ea21f` · r9 이 커밋)와 `milestone-6.md`(`93b04488` 착수 · `61874bfc` 종결 · `6897e50e` 정정) — `git log -- <파일>` 산출.

## 입력·이관

- `OPEN-…-RETENTION` 넷 → 6B-3(이 slice 는 보존 기간을 정하지 않는다). PITR/WAL → M7 운영 반입. compose `name:` 을 (a) 로 고치면 6C D-6C-2 문면에 사실 추가.
