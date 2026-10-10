# M6/6E-2c — OS 매칭 술어 · 베이스 이미지 · JVM 의존 상향 (2026-10-09, 착수 계약, 팀장)

6E-2b 가 등재한 수정 가능 HIGH/CRITICAL **67** 을 실제로 걷는 slice. CRITICAL 10 의 등재가 **2026-10-31** 에 만료된다(D-6E2B-1) —
그 전에 머지되지 않으면 `container` job 이 스스로 붉어진다. 사용자 「다음 작업 진행」(2026-10-09).

- base: `e2232a75`(PR #68·#69 머지 뒤 `main`). worktree `bid-vector-v2-m6-6e2c`, 브랜치 `m6-6e2c/2026-10-09`.
- 착수 조사 `_workspace/m6-6e2c/00_scout.md`. 결정 ID `D-6E2C-n`. evidence `reports/evidence/m6/6e2c/`.
- 받는 OPEN: **`OPEN-6E2B-OS-MATCH-PREDICATE`**(상향 **전에** 닫는다) · `OPEN-6E2B-BASE-IMAGE-BUMP` · `OPEN-6E2B-DEPENDENCY-BUMP`.
- Codex: 없음(CI·의존 버전 — 되돌리기 어려운 경로 아님).

## 착수 실측 (요지)

| 축 | 오늘 | 필요 |
|---|---|---|
| ml-serving 52 | `python:3.12.8-slim-bookworm`(debian **12.9**) | `python:3.12.15-slim-bookworm` index `sha256:34386ef0…a07258`(debian **12.15**) — 등재 사유의 수정판 전부 포함 |
| app OS 2 | `eclipse-temurin:21-jre-noble@sha256:7edbe853…` | 같은 태그 새 index `sha256:000fd431…f9408c`(ubuntu 24.04 그대로, openssl 3.0.13-0ubuntu3.16) |
| app JVM 13 | Boot 4.1.1 BOM: tomcat 11.0.24 · jackson 2.21.5 / 3.1.5 | **Boot 4.1.x 는 4.1.1 이 최신** → BOM 으로 해결 불가. `platform()` 적용이라 `extra["tomcat.version"]` 은 무효(dependency-management 미적용) |
| 위생 정책 | 키는 `base.image.digest` 하나(layer 는 스크립트가 pull 해 파생) | Dockerfile `FROM`×4 · `LABEL`×2 · 정책 digest×2 · 실측 주석 |
| allowlist | 67 | 상향하면 전부 stale → **같은 PR 에서 비운다** |
| OS 매칭 | 스캔 `.Metadata.OS` = app `ubuntu/24.04` · ml `debian/12.9`; Results 에 `Class=os-pkgs, Type=<family>` | 술어 없음 |

## 운영자 결정 — 추천안으로 착수(정정 시 계약 갱신)

- **C-1 OS 매칭 술어** — (a) **둘 다**: ⓐ kind 별 정책 키 `scan.os.family.<kind>`·`scan.os.name.<kind>` 를 `.Metadata.OS.Family`/`.Name` 과 **정확 일치**(불일치·부재 = 판정 불가 exit 2) ⓑ `Class=="os-pkgs" && Type==family` 인 Result 가 **하나 이상**(없으면 exit 2). 정확 일치라 debian point release 상향은 **정책 키를 같은 커밋에서 올려야** 한다(의도한 fail-closed — 「배포판이 바뀌었는데 아무도 모름」을 막는다) · (b) family 만. **추천 (a)**.
- **C-2 베이스** — 위 두 index 다이제스트(같은 태그 계열, 최신 패치). python 은 3.12 마이너 유지(`requires-python ==3.12.*`, cp312 동일 → lock 재생성 불필요).
- **C-3 JVM** — 카탈로그에 버전 셋을 두고 `app` 에 `platform(jackson2-bom 2.21.7)` · `platform(jackson3-bom 3.1.7)` · tomcat `constraints { require("11.0.26") }`(`tomcat-embed-core`·`-el`·`-websocket` 함께; Boot 4.2.0-M2 가 11.0.26 을 쓴다). Boot 판 올림은 하지 않는다(4.1.1 이 최신). 해석 결과가 실제로 그 판인지 **해석 그래프·bootJar 내용으로** 잠근다(`compatibilitySmoke.expectedModules`·`BootJarRuntimeClasspathTest` 동반).
- **C-4 allowlist** — 67 전부 제거. 재스캔 뒤 남는 finding 은 **수정본이 있으면 더 올리고**, 없거나 이 slice 밖이면 새 사유·만료로 등재(만료 ≤ 30일).

## 산출물

| ID | 무엇 | 공허함을 막는 술어 |
|---|---|---|
| **E-1** | OS 매칭 술어(C-1) — 상향 **전** 커밋에서 현 값(`debian/12.9`·`ubuntu/24.04`)으로 초록 | 음성 대조: 정책 name 을 틀리게 → exit 2 · family 틀리게 → exit 2 · shim 으로 os-pkgs Result 제거 → exit 2 · `.Metadata.OS` 제거 → exit 2. **상향 커밋에서 정책을 안 올리면 exit 2 가 나는 것을 한 번 실측**(술어가 산 증거) |
| **E-2** | 베이스 두 개 상향(C-2) + 정책 digest·실측 주석 | 위생 S-22a/b 초록 · `required.executables`·`forbidden.executables` 재실측 |
| **E-3** | JVM 상향(C-3) | 해석된 tomcat-embed-core == 11.0.26, jackson 2.21.7·3.1.7 을 test 가 잰다 · 변이: constraint 제거 → RED |
| **E-4** | allowlist 비움 + 잔여 triage(C-4) | 스캔 exit 0 · stale 0 · 남은 등재마다 사유·만료 |
| **E-5** | runbook §8 · checklist — OPEN 셋 해소, 2026-10-31 기한 소진 기록 | — |

## in_scope / out_scope

- in: `tools/vuln-scan-check.sh` · `config/quality/vuln-policy.properties` · `config/quality/vuln-allowlist.properties` · `config/quality/image-hygiene-policy.properties` · `config/quality/image-hygiene-policy-app.properties` · `docker/app.Dockerfile` · `docker/ml-serving.Dockerfile` · `gradle/libs.versions.toml` · `app/build.gradle.kts` · `app/src/test/**`(해석 판 test) · `config/quality/gate-tests.properties`(신규 test 시) · `docs/runbook/m6-6e-operations.md` · `reports/evidence/m6/6e2c/**` · `milestone-6.md`(팀장 문단).
- out: Boot 판 올림 · python 마이너 변경 · `ml-engine/**` · production `src/main` · 마이그레이션 · `.github/workflows/ci.yml`(필요해지면 멈추고 계약 갱신).

## acceptance

`./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline` · **`container` job 로컬 재현**(이미지 둘 재빌드, 이미지 ID 기록) · S-20 은 ml-engine 무변경이라 생략. 호스트 규율: 빌드 감지 `pgrep -af 'java .*GradleWorkerMain' | grep -v -e pgrep -e 'bash -c'` + `/proc/<pid>/cwd`, 30초 간격 두 표본, available ≥ 6GB · swap free ≥ 2GB. 변이는 바꿔치우기·`git diff --numstat` 확인·대조군은 실제 부모 커밋.

## rollback

단일 restore 경로는 in_scope 의 산출물 파일. 공유 파일(runbook·`milestone-6.md`)은 **이 브랜치에 마지막으로 병합한 main 커밋 SHA**(오늘은 base `e2232a75`)에서 복원 + 실행 전 단언 둘(PR #69 규칙). 완료는 계수(이 slice 표지 0 · 남의 표지 보존). 임시 clone 에서 ①~⑥.

## 승인 전 일괄 (r2, 2026-10-10, 팀장) — D-6E2C-5

verifier r1 **ready-for-review** @`a4d978ec`(evidence `cb9c715d`; `check --rerun-tasks` 전건 exit 0 · container 생략 근거 수용 · rollback 유효) · code-reviewer r1 새 high 없음. 재작업 계수 0/5(일괄은 계수 밖). 처분:

| finding | 처분 | 층 |
|---|---|---|
| vr F-1 · cr M-1 (OS 술어는 배포판 **선언**을 강제할 뿐 DB 가 그 배포판을 **덮는지**는 재지 않는다 — 가짜 `99.0` 을 정책에 옮겨 적으면 exit 0, findings 284→6) | 문면을 술어만큼 좁힌다: runbook §8.2 의 사람 신호(「findings 급감 + 나열 수 불변」) 문단 복원 · §8.3.1 상향 절차의 정책 옮겨 적기 앞에 사전 확인(findings 총수 대조 · trivy 경고) · `OPEN-6E2B-OS-MATCH-PREDICATE` 를 **부분 해소**로 · 잔여를 `OPEN-6E2C-OS-DB-COVERAGE` 로 신설. **게이트 술어는 바꾸지 않는다**(EOSL 축은 이 재현을 잡지 못한다) | 산출물(문서) + evidence |
| vr F-2 (ⓑ 는 `os-pkgs` Result 의 **실재**만 잰다 — 패키지를 비워도 통과) | 술어에 `(.Packages // []) | length > 0` — **게이트 술어 변경 → 표적 재검증** | 산출물(게이트) |
| cr M-2 (§8.3.1 「1~3 어느 쪽도 조용히 지나가지 않는다」 — LABEL 은 어떤 게이트도 읽지 않는다) | 「1·3 은 게이트가, 2(LABEL)는 리뷰가 진다」로 정정 | 산출물(문서) |
| vr F-3 (카탈로그 주석 「여기를 내리면 그 test 가 RED」 거짓) | 주석을 알려진 제한 5 와 같은 말로 | 산출물(주석) |
| cr L-2 (`versionsOf` 가 정규식 밖 jar 이름 — classifier·`-SNAPSHOT` — 을 조용히 버림) · cr L-3 (jackson 단언이 Jackson 2 의 **존재**까지 고정) | 대상 좌표 접두의 jar 가 파싱되지 않으면 실패 · major 별 술어로 | 산출물(test) — 표적 재검증 |
| cr L-1 (위생 정책 크기 주석의 원인 귀속 근거 없음 · 상한 여유 86%) | 원인 미상으로 적고 여유를 알려진 제한에 | 산출물(주석) + evidence |
| vr F-4 (알려진 제한 4 「상류 재발행으로 무변경 PR 이 붉어짐」 — digest 핀이라 일어나지 않음) | 제한 4 를 실제 성질(상향 시 정책 동반 갱신 강제)로 재서술 | evidence |
| cr L-4 · vr L-a · L-b | L-4 등재만 · E-2 과잉 주장 정정 · 정책 판 선례 엇갈림을 양쪽 다 인용 | evidence |

acceptance(일괄 뒤 마지막 산출물 커밋에서): `check` · `qualityBaseline` · **container job 재현**(술어가 바뀌므로 생략 불가) · rollback ①~③ 재측정(④~⑥ 은 트리 동일성 갈음 가능). verifier 표적: F-2 술어 커밋 · test 커밋만.

## 하네스 레인 변경 (상시)

- (착수 시점 없음)
