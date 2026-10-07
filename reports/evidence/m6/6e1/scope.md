# M6/6E-1 — 제품 acceptance 추적표 · 운영 runbook · 차이 목록 (2026-10-07, 착수 계약 초안, 팀장)

6E(「제품 acceptance 와 운영 runbook」, milestone 6E bullet 다섯)를 **둘로 가른다** — 6C 가 네 축을 한 slice 에 담아 재작업 3 라운드를 쓴 선례(milestone 6B 절)
를 피한다. **6E-1(이 slice)** 은 production diff 0 의 문서 slice + CI 스모크 한 step(dry-run 왕복)이고, **6E-2** 는 운영자 결정이 선행하는 코드·게이트 축
(커넥션 풀 `OPEN-6A1-CONNECTION-POOL` · SBOM/CVE `OPEN-6C-IMAGE-VULN-SCAN` · metric 계측 `OPEN-OPS-03` 임계 · 투찰 기록 표 `OPEN-6F3-BID-RECORD`)이다.

- base: `git merge-base HEAD origin/main`(착수 실측 `80dc33b3`, PR #64 6D-2 머지). worktree `bid-vector-v2-m6-6e1`, 브랜치 `m6-6e1/2026-10-07`.
- 착수 조사 `_workspace/m6-6e1/00_scout.md`(읽기 전용 scout, gitignored — 축별 있음/없음 표 · 6E 로 라우팅된 OPEN 8 · 완료 조건 아홉의 현재 상태). 이 계약의
  「착수 실측」은 그 보고를 팀장이 저장소에서 재대조한 것이다.
- 결정 ID `D-6E1-n`, 운영자 결정 `E-n`. evidence `reports/evidence/m6/6e1/`. Codex: 없음(문서 + CI yml 한 step).

## 착수 실측 (요지 — 전문은 00_scout.md)

| 축 | 있음 | 없음 → 6E-1 산출물 |
|---|---|---|
| ① capability acceptance | `docs/discovery/capability-map.md` 의 `V2 필수` 61 전건에 acceptance scenario 문면 | **capability → test·명령 대응 관계 0**(E2E 25 파일에 capability ID 인용 0). 완료 조건 3 은 판정 대상이 정의되지 않은 조건 → **C-1 추적표** |
| ② metric/SLO/log/trace | actuator health(별도 포트, `MANAGEMENT_SURFACE_LOCK`), slf4j 평문 `key=value`(계수·열거값만), correlation id 는 이벤트·audit 층 | production 계측 0 · 구조화 로그 0 · trace 0 · 대시보드 0 · 임계는 `OPEN-OPS-03`·`OPEN-OPS-04` 미결 → **6E-2**(결정 선행). 6E-1 은 「오늘 관측할 수 있는 것」(종료 코드·로그 줄·health·DB 질의)을 runbook 에 적는다 |
| ③ backup/restore·rollback·incident | 6B-2 runbook + 스크립트 셋 + CI S-23c(완료 조건 7 충족) · model rollback 정의(EXACT 선택자)·타입·gateway test | **incident 절차 0** · model rollback **절차** 0(파이프라인 선택자는 정책에 `LatestPromoted` 고정 — 운영자가 되돌리는 production 경로 없음) → **C-3·C-4** |
| ④ live read probe·dry-run | 6G 실수집 runbook(백테스트 전용) · `POST /api/evaluation-dry-runs`(outbox 미기록) · relay 환경 억제(`environmentModes`, Live 아니면 claim 0) · `Live` 기동 거부(`OPEN-STR-12`) | CI 가 dry-run 을 호출하지 않음 · 일반 운영 probe 절차 0 → **C-5 + G-4** |
| ⑤ legacy ↔ V2 차이 | capability-map 분류(폐기 6·후속 17·근거 부족 11) · `v2-지침서` §1·§9 · regression-ledger | 한 자리의 차이 목록 0 · ledger ↔ 예방 제약 연결 전수표 0 → **C-6(팀장)·C-7** |
| 완료 조건 | 1·2·7 충족 · 3·4·6 test-scope 자리 충족 · 5 는 `OPEN-ADR-07`(M1 소유) · 8 절반(이미지 위생) · 9 미성립(M4 종결 판정 전제 — `reports/evidence/m4/closure/` 에 계약·checklist·open-inventory 는 있으나 종결 판정 문단은 이 조사에서 미확인) | 6E-1 은 **3 의 정의**(C-1)와 **4 의 절차+CI 실측**(C-5·G-4)을 낸다. 8 나머지·9 는 6E-2 뒤 |
| 리뷰 레인 | `privacy-gate`·`contract-keeper`·`migration-reviewer` 정의 파일 없음(`.claude/agents/` 7개) | 비밀값 축은 verifier 에 실측 지시(참조형 `grep -rniE -f config/quality/leak-patterns.txt`) |

## 운영자 결정 — E-1 ~ E-5 (추천안으로 착수, 정정 시 계약 갱신) · 6E-2 결정 대기 넷은 별도 절

- **E-1 분할** — (a) **6E-1 = 문서(C-1~C-7·C-9) + G-4 한 step**, 6E-2 = 코드·게이트(G-1·G-2·G-3, G-7 은 결정 뒤) · (b) 6E 한 덩어리. **추천 (a)**.
- **E-2 C-7 ledger ↔ 예방 제약 연결 전수표** — (a) **포함**: ledger 항목 전건에 대해 예방 제약(타입·계약·test 의 **경로**) 또는 「미연결」을 적고 미연결 **건수를 사실로** 보고(숨기지 않음) · (b) 6E-2 로 미룸. **추천 (a)** — `v2-지침서` §9 완료 정의 항목이고 읽기만으로 가능.
- **E-3 `OPEN-5E-POLICY-VALUES` 재승인(값 일곱)** — (a) **「6E 실측 불가(serving 부하 측정 환경 없음) → 잠정 유지, 재승인 조건을 M7 운영 반입 실측으로 이전」 선언**(C-9) · (b) 이 slice 에서 부하 실측. **추천 (a)**.
- **E-4 G-4 CI dry-run 왕복** — (a) **포함**: `container` job S-23b 에 `POST /api/evaluation-dry-runs` 왕복(인증·200·응답 본문 형태·**outbox 전후 행 수 등식**(dry-run 은 쓰지 않는다)) 한 step. 로컬에서 container job 을 돌려 실측(호스트 규율) · (b) 제외. **추천 (a)** — 완료 조건 4 를 컨테이너 축에서 **재게** 하는 유일한 저비용 항목.
- **E-5 Codex** — (a) 없음 · (b) 탐. **추천 (a)**.

**6E-2 로 넘기는 운영자 결정 넷(이 slice 는 문면만 등재, 착수하지 않음)**: G-2 커넥션 풀 도입(HikariCP, 「운영 반입 가능」의 전제 — 기동 실패 즉시화·호환 표면 증가) · G-1 SBOM/CVE 도구 채택(완료 조건 8 나머지 절반) · G-3 metric 계측(`OPEN-OPS-03` 임계, `MANAGEMENT_SURFACE_LOCK` 술어 변경 = 설계 검토 필수) · G-7 투찰 기록 표(`OPEN-6F3-BID-RECORD`, 마이그레이션 = 되돌리기 어려운 경로). G-5(자격증명 원문 경계)·G-6(멀티아키)는 범위·배포 대상 결정 뒤.

## 산출물

| ID | 무엇 | 자리 | 공허함을 막는 술어(설계 검토 D-6E1-2) |
|---|---|---|---|
| **C-1** | capability → acceptance 추적표 — `V2 필수` 61 각각: ⓐ 덮는 test 클래스·CI step ⓑ 「경계로 처리」(사유) ⓒ 「미구현」(후속/OPEN ID) 셋 중 하나 | `reports/evidence/m6/6e1/acceptance-trace.md` | 행 수 == 61(집계 등식) · ⓐ 의 식별자는 **전부 저장소에 존재**(기계 대조: test 클래스는 `gate-tests.properties` 등재 집합, step 은 ci.yml `name:` 집합) · ⓒ 의 OPEN ID 는 capability-map §12/§14 또는 milestone OPEN 목록에 존재 · ⓐ/ⓑ/ⓒ 건수를 머리에 |
| **C-2** | 운영 runbook — 기동·설정·거부: 환경변수 표(`bidvector.*` prefix 전수) · 관리 표면 잠금과 거부 규칙(`MANAGEMENT_*`, k8s service link) · 관리 포트 노출 통제(`OPEN-6A2A-MGMT-PORT-EXPOSURE` 받음) · audit fail-closed 의 가용성 대가(D-6A1-17 받음) · 커넥션 풀 부재 경고(`OPEN-6A1-CONNECTION-POOL`, 6E-2) | `docs/runbook/m6-6e-operations.md` §1~§2 | 환경변수·키 목록은 코드에서 **기계 수집**(`@ConfigurationProperties` prefix · `MANAGEMENT_SURFACE_*` 상수)해 문서 표와 집합 등식 |
| **C-3** | incident 절차 — 러너 종료 코드별 처방(relay 0~4 · 수집 0/2/3/4 · 평가 커밋) · cause code 읽는 법(메시지 미기재 규율) · `ISOLATED` 는 알림을 영구히 잃는다 + 그때 할 일(수동 되돌림 없음 — V6 DELETE 권한 없음, 단방향) · audit 장애 · 임대 Busy | 같은 파일 §3 | 종료 코드 표 == `RelayExitCode` enum 값 집합(기계 대조) · 수집 종료 코드 == 6G runbook §2 |
| **C-4** | model rollback 절차 — 정의(EXACT 선택자로 직전 release) · **오늘 production 에 운영자 경로가 없다는 사실**(선택자가 정책에 고정) · 할 수 있는 것(gateway 수준 test 로 검증된 것)과 없는 것 | 같은 파일 §4 | 「없는 경로를 약속하지 않는다」 — 절차의 각 명령은 저장소에 실재하는 진입점만 |
| **C-5** | notification dry-run / live read probe 절차 — 환경값 → `DeliveryMode` → claim 여부 → 종료 코드 사상표(출하 정책 4 환경) · `Live` 기동 거부의 의미 · 평가 dry-run 호출법(curl --config, 자격 값 argv 금지) · 일반 운영 live read probe(실 KONEPS 1건 호출 절차 — 6G runbook §1 키 취급 재인용, 실행은 사용자 승인) | 같은 파일 §5 | 사상표 == `NOTIFICATION_DELIVERY_POLICY` 출하 값(기계 대조) |
| **C-6** | legacy ↔ V2 차이 목록(팀장 직접) — 폐기 6 · 후속 17 · 근거 부족 11 · pull 모델 변경 6 을 한 자리에, 「V2 에 없다」 선언 | `docs/discovery/legacy-v2-differences.md` | 건수 == capability-map §10 집계 |
| **C-7** | regression ledger ↔ 예방 제약 연결 전수표 | `reports/evidence/m6/6e1/ledger-constraint-trace.md` | 행 수 == ledger 항목 수(기계 집계) · 연결은 **경로**(타입·계약·test) · 미연결 건수 머리에 |
| **C-9** | `OPEN-5E-POLICY-VALUES` 판정 문면(E-3) | `reports/evidence/m6/6e1/checklist.md` 절 | — |
| **G-4** | ci.yml S-23b 에 dry-run 왕복 step | `.github/workflows/ci.yml` | 로컬 container job 실측 exit 0 · 음성 대조(응답 코드 기대값을 바꾸면 RED) |

## in_scope / out_scope

- in: `reports/evidence/m6/6e1/**` · `docs/runbook/m6-6e-operations.md`(신설) · `docs/discovery/legacy-v2-differences.md`(신설, 팀장) · `.github/workflows/ci.yml`(S-23b 안 한 step 추가만) · `milestone-6.md`(착수·종결 문단).
- out: production 코드 전부 · 마이그레이션 · `docker/**` · build 파일 · `config/quality/**` · `docs/discovery/capability-map.md`·`regression-ledger.md`(읽기만 — 분류를 고치지 않는다) · 6E-2 항목 전부.

## acceptance

`./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline`(evidence 가 누출 게이트 입력) · **`container` job 로컬 재현**(G-4 가 ci.yml 을 바꾸므로 — S-21a~S-25 전부, 호스트 규율: pgrep·free·ps 별도 호출, available ≥ 6GB·swap ≥ 2GB, 전체 1개) · S-20 생략(Python 무변경). 문서 등식 셋(C-1 61 · C-3 종료 코드 · C-5 사상표)은 commands.md 에 **명령과 결과**로. 변이: G-4 기대값 변경 → RED · C-1 표에서 존재하지 않는 test 이름 한 줄 → 기계 대조 RED.

## rollback

in_scope 경로 한정 `git restore --source=<base>`; 신설 파일은 제거; 공유 파일(ci.yml·milestone-6.md)은 커밋 해시 hunk `--no-merges`.

## 하네스 레인 변경 (상시)

- (없음 — 착수 시점)

## 계약 갱신 r1 (2026-10-07, 팀장 — 착수 결정 · 설계 검토)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-1** | **E-1~E-5 추천안으로 착수**(사용자 「남은 것 계속 진행」 2026-10-07; 정정 시 계약 갱신). 6E-2 결정 넷(G-1·G-2·G-3·G-7)은 이 slice 가 **문면만 등재**하고 착수하지 않는다 | 팀장 |
| **D-6E1-2** | **설계 검토 요지**(세션 모델 직접, `_workspace/m6-6e1/01_design-review.md`): (0) 경계 — 방어하는 것: 완료 조건 3 의 **판정 대상 정의**(C-1)와 4 의 **절차 + CI 실측**(C-5·G-4), 운영자가 오늘 저장소로 할 수 있는 것과 없는 것의 **정직한 목록**(C-2~C-5); 방어하지 않는 것: SLO 임계·계측·풀·SBOM·투찰 기록(6E-2) · 실 발송 · M4 종결 판정 · (1) 문서 slice 의 결함 클래스는 **「문서가 코드와 어긋나도 초록」** — 그래서 표마다 **코드에서 기계 수집한 집합과의 등식**을 acceptance 에 둔다(C-1 61·식별자 존재 · C-2 설정 키 · C-3 종료 코드 enum · C-5 정책 사상표 · C-6 집계 · C-7 ledger 행 수) · (2) 우회 — ① 61 행을 전부 「경계로 처리」로 채움 → ⓐ/ⓑ/ⓒ 건수를 머리에 적고 verifier 가 ⓑ 사유를 표본 대조 ② ⓐ 에 존재하지 않는 test 이름 → 기계 대조 ③ G-4 가 호출만 하고 단언 없음 → 200 + 본문 형태 + **outbox 전후 행 수 등식** ④ 절차가 없는 경로를 약속(model rollback) → 각 명령의 진입점 실재 ⑤ 종료 코드 표가 enum 과 어긋남 → 등식 ⑥ C-7 미연결을 「연결」로 분식 → 연결은 경로여야 하고 verifier 표본 대조 ⑦ runbook 에 비밀값·호스트 경로 원문 → 참조형 누출 스캔 ⑧ 「경계로 처리」 행 — C-4 의 「운영자 경로 없음」 진술 자체를 코드(정책 `releaseSelector` 고정)로 실측 · (2b) 새 production 표면 0; G-4 는 CI 가 **읽기만**(dry-run) · (3) 과잉: SLO 숫자 발명 · 계측 코드 · 풀; 미달: 추적표 없이 「E2E 통과」만 적기 · incident 를 「로그를 본다」로 끝내기 | 설계 검토 |
| **D-6E1-3** | **규율**: production·migration·docker·build·`config/quality` diff 0 · `git add` 개별 인자 · 게이트 결과 종료 코드 · 변이 전 커밋 · 호스트 3단 점검 별도 호출(available ≥ 6GB · **swap ≥ 2GB**, 6D-2 의 1GB 는 1회 예외였다) · 컨테이너 job 은 호스트 전체 1개 · 실 KONEPS·LLM·발송 호출 0(C-5 의 live probe 는 **절차만**, 실행 안 함) | 6D-1 D-6D-3 · 호스트 규칙 |

## 계약 갱신 r2 (2026-10-07, 팀장 — 호스트 예외 · 산출물 수령)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-4** | **사용자 결정: swap 2GB 규칙 1회 예외(이 slice 한정)** — 레인 실측: swap free 1.57GB 를 쥔 것은 상주 서비스(python 수집기·redis·intellij-server 등)의 페이지라 기다려도 다른 프로젝트 daemon 을 멈춰도 2GB 에 닿지 않음(available 10.3GB). 예외 문면: **available ≥ 6GB 그리고 swap free ≥ 1.5GB** 면 Gradle·container job 을 **하나씩** 허용, swap free **1GB 아래면 즉시 중단·보고**. 규칙(2GB)은 유지되고 다음 slice 에 이월되지 않는다(6D-2 D-6D2-4 와 같은 형태) | 사용자 2026-10-07 |
| **D-6E1-5** | **산출물 수령(빌드 전)**: C-6 `1e396ff6`(팀장, 집계 등식 통과) · C-7 `b79fdb76`(ls-6e1: 61/연결 43/**미연결 18**/해당 없음 0, 등식 셋 성립) · C-2~C-5 runbook `14bcfe4c` · C-1 추적표 `4b4c6bac`+`e1f5995b`+`bf5850b2`(**ⓐ29/ⓑ2/ⓒ30 — 완료 조건 3 은 오늘 성립하지 않음, 성립 범위 31/61**; OPEN 홀더 없는 미구현 10: STR-06·07·10·16 · QUAL-08 · ML-08 · DEC-05 · DEC-10 · OPS-08 · OPS-13 → 종결 보고의 OPEN 에스컬레이션) · G-4 `2df1da3b` · checklist `b3fa51ff`. 문서 등식 15/15 · 정적 변이 3/3 RED. 남은 것: acceptance 셋 + rollback ④~⑥ + commands/rollback 완성 → 동결 | 레인 보고 · 팀장 대조(G-4 diff 선례 형태 확인) |

## 계약 갱신 r3 (2026-10-08, 팀장 — 구현 수령·동결 · 판정 표적)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-6** | **구현 수령·동결.** 산출물 마지막: runbook `14bcfe4c` · C-1 `bf5850b2` · G-4 `2df1da3b` · C-6 `1e396ff6` · C-7 `b79fdb76`; evidence 마지막 `65cee748`(`02_implementer_report.md`). acceptance: `check` exit 0(350 클래스 · 2817 test · 실패 0, e2e 7 클래스 기본 check 안에서 실행) · `qualityBaseline` exit 0 · container job S-21a~S-25 exit 0(G-4 실행 줄 「outbox 행 수 2 == 2 (쓰기 0)」, S-23c 가 G-4 뒤에도 통과) · 변이 4/4 RED(정적 셋 + 동적 200→201). **rollback 실측 HEAD `4a8600a5`**(대상 다섯이 멈춘 뒤 고정점; `rollback.md` 자신은 대상 아님). 팀장 대조: 미커밋 0 · production·migration·docker·build·`config/quality` diff 0 · ci.yml step `name:` 변경 0(기존 step 안 블록 추가만) · in_scope 밖 0 · 유효성 `git diff --name-only 4a8600a5..HEAD -- <대상 다섯>` 빈 출력 · 참조형 누출 0 · daemon 0. 레인 정정 수용: ci.yml 커밋은 amend 로 `71c394f8` → `2df1da3b`(push 전, 이력 되쓰기 아님). 호스트 D-6E1-4 안에서 swap 1.6~1.7GB 유지. **판정 SHA = 이 r3 커밋** | 레인 보고 · 팀장 대조 |
| **D-6E1-7** | **verifier 표적**(opus, 판정 SHA): ① acceptance 재실측 — `check`·`qualityBaseline`(호스트 D-6E1-4 예외 안에서 하나씩); container job 은 레인 실측 로그 줄과 G-4 블록 **정적 대조** + 호스트 여력이 있으면 재현(없으면 미실측 표기) ② **C-1 ⓐ 29 의 표본 대조 ≥5** — 각 행의 식별자가 그 capability 의 acceptance 무조건 항목을 **실제로** 재는지(이름 유사로 채웠으면 RED); 레인 자진 보고(COL-04 를 ⓒ 로 내린 기준을 ⓐ 에 재적용하면 더 내려갈 수 있음 · QUAL-02 production 분기 `Uncertain` 통과에 test 없음)를 표적으로 ③ **C-7 표본 대조 ≥5** — 연결 43 중 「경로가 그 회귀를 실제로 막는가」(R-RATE-05·R-COL-05 의 다른 형태 연결 포함) + 미연결 18 중 둘이 정말 미연결인지 ④ 문서 등식 15 재실행(commands.md 명령 그대로) ⑤ G-4 변이 하나 재현(정적 가능: 기대 키 집합 변경이 fail 로 가는 경로) ⑥ runbook C-4 「운영자 rollback 경로 없음」 진술을 코드(`OpportunityPolicyData.releaseSelector`)로 실측 · C-3 종료 코드 처방이 `RelayExitCode` KDoc 과 어긋나지 않는지 ⑦ rollback ⓪ 등식 판정 SHA 재산출 + 유효성 ⑧ 장부(좌표·축어·크기 — 문서 slice 라 evidence 디렉터리 안의 C-1·C-7 은 산출물로 센다). **code-reviewer(sonnet) 병렬** — G-4 셸 블록(fail 경로·인용·`set -e` 거동) · runbook 의 사실 오류·약속하지 않아야 할 문면 · 추적표 분류 규칙의 자기 일관성 | 하네스 |
