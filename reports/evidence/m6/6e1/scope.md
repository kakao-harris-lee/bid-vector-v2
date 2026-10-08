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

- (착수 시점 없음)
- 2026-10-08 RT-L-4: rollback 유효성 확인(`git diff --name-only <실측 HEAD>..<판정 SHA> -- <되돌림 경로>` 빈 출력)을 **되돌림 동작 종류별**로 읽었다 — `M`(hunk 역적용, 내용 의존)은 문면 그대로, `A`(제거 대상)는 내용 변경이 무효화하지 않는다(되돌린 트리가 두 SHA 에서 동일함을 verifier 가 실측). CLAUDE.md 문면과 다르므로 하네스 후보 `OPEN-HARNESS-ROLLBACK-VALIDITY-BY-KIND`(evidence-pack 규격 문장 추가는 다음 하네스 편집). `.claude/`·CLAUDE.md 편집 0.

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

## 계약 갱신 r4 (2026-10-08, 팀장 — 판정 r1 수령 · 수정 라운드 1)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-8** | **verifier r1 `not-ready` @`6b72c2f3`**(`03_verifier_r1.md`): high 3 — **R1-H-1** runbook §4.2·§4.3 model rollback 절차(「레지스트리 승격 해제 → `LatestPromoted` 가 앞 release」)의 진입점이 저장소에 없다: 실 serving 의 release 는 추론 정책 version 에서 파생된 **런타임 상수**(`build_derived_release`, `distribution/<policy.version>`), 레지스트리에 승격 저장소·demote 0, 실 serving 은 비현재 `EXACT` 를 `RELEASE_MISMATCH` 로 거부, 인용 e2e 는 `MlFakeServer` 위의 사실 · **R1-H-2** G-4 ③ outbox 행 수 등식이 공허(컨테이너 DB 공고 0 → 후보 0 → 커밋 포트로 바꿔치워도 초록; 문면 셋이 「outbox 를 건드리지 않음을 잰다」고 주장; 구조 잠금은 `DryRunCommitSeparationGateTest`) · **R1-H-3** C-1 ⓐ 표본 7 중 3 위반(COL-03 429 종료 실패 미측정 · OPS-01 강제 종료 해제 미측정 · ML-07 effective date·정책 version bullet 미처리) → ⓐ 29 전수 bullet 단위 재대조. medium 3(R1-M-1 §3.4 INCOMPLETE 처방이 KDoc 과 정반대 · R1-M-2 QUAL-02 「Uncertain 통과 분기 test 없음」은 거짓 — `PipelineOneLineE2ETest` PASSING_NOTICE 가 그 분기 · R1-M-3 C-7 R-ASYNC-03 연결 경로가 회귀 표면(수집 파일 잠금)과 다름) · low 4(R1-L-1 C-6·C-7·milestone 되돌림 절차 없음 · L-2 끊긴 참조 · L-3 §1.5 거부 자리 · L-4 STR-01 넷째 bullet 측정자). acceptance: `check` 깨끗한 clone `--no-build-cache` exit 0(359 task 전부 실행) · `qualityBaseline`·container 미실측(swap 1.2GB < 예외 문턱 1.5GB) · 등식 15/15 · G-4 키 변이 둘 exit 1 · rollback 등식·유효성 빈 출력. **code-reviewer r1**(`04_code_review_r1.md`): high 0 · medium 2(G-1 ≡ R1-M-1 · G-2 §1.5 `candidate-cap` 거부 자리는 `EvaluationWiring` 바인딩이고 아래 두 행은 `EvaluationCommitWiring` 이 맞음) · low 9(G-3 실패 문면이 dry-run 본문 전문을 CI 로그에 · G-4 재조회 둘 200 미단언 · G-5 OPS-07 「저장 상태 불변」 과대 · G-6 FAILED 처방 · G-7 §3.6 payload 생략 미선언 · G-8 「부분 측정(강함)」 술어 없음 · G-9 C-6 §0 굵은 서식은 넷 · G-10 R-RATE-02 경로 칸 · G-11 G-4 주석) · informational 1. **재작업 1/5** | 두 보고서 |
| **D-6E1-9** | **수정 라운드 1 — 레인 커밋: 산출물 ≤3(runbook · C-1 · ci.yml 공유) + evidence 1.** ① **R1-H-1**: runbook §4 를 사실로 다시 쓴다 — 오늘 release 는 정책 version 파생 상수, 승격·demote 없음, 비현재 `EXACT` 거부; 실재하는 유일한 지렛대는 **이전 추론 정책 파일/serving 이미지로 재배포**(그 절차의 타당성은 레인이 `ml_engine/serving/runtime.py`·배포 스크립트로 확인해 적되 **없는 명령을 쓰지 않는다**); 6D-1 C-3 정의(「EXACT 로 직전 release」)는 **fake 서버 위에서만 성립**함을 알려진 제한 + 신설 `OPEN-6E1-MODEL-ROLLBACK-MECHANISM`(승격 상태·demote 가 생기는 slice, ML 레인·운영자 결정) ② **R1-H-2**: 처방 ②(주장 낮춤) 채택 — ci.yml 주석·runbook §5.3·checklist G-4 제한 3 을 「200 + 본문 키 아홉 + **빈 후보에서의** 비쓰기」로; 「외부 effect 없음」의 정본은 `DryRunCommitSeparationGateTest`(구조) 임을 적고, 비공허 측정(후보 ≥1·`wouldNotifyNoticeIds` 비어 있지 않음 전제 단언 + seed)은 ML 배선 뒤 `OPEN-6E1-G4-NONVACUOUS` 로. **step 이름 불변·다른 줄 불변** ③ **R1-H-3**: C-1 ⓐ 29 **전수**를 bullet 단위로 재대조해 위반 행을 ⓒ 로(COL-03·OPS-01·ML-07 포함), 비어 있는 bullet 을 행에 명시; 머리 집계·축별 표·「성립 범위」·checklist·milestone 인용 수치를 함께 갱신(팀장이 milestone·scope 수치를 받는다) ④ R1-M-1/G-1: §3.4 근거를 「재평가 멱등성 + relay 의 idempotency 중복 접기」로, outbox UNIQUE 없음(성공 공고 행이 재실행마다 새로 생김) 명시 ⑤ R1-M-2: QUAL-02 행·checklist 제한 4 정정, QUAL-01 식별자에 `PipelineOneLineE2ETest` 추가, 자진 보고 철회를 보고에 ⑥ R1-M-3: C-7 R-ASYNC-03 을 수집 파일 잠금(`RunStateLock`, exit 3)으로 재연결하거나 미연결로 — 집계 갱신 ⑦ G-2/R1-L-3: §1.5 세 행에 자리를 각각 ⑧ low 전부 고친다(G-3 실패 문면은 본문 전문 대신 키 집합·건수만 · G-4 재조회 200 단언 · G-5·6·7·8·9·10·11 · R1-L-2 참조 · R1-L-4) ⑨ R1-L-1: rollback.md 에 C-6·C-7 신설 파일 제거 + milestone hunk 절차 등재(팀장 커밋도 레인 문서가 든다) ⑩ evidence: commands(등식 15 재실행 · 변이 재측정) · checklist(OPEN 신설 둘 · 제한 갱신) · rollback 실측 HEAD 를 새 산출물 커밋으로 ①~③ 재실측, ④~⑥ 은 호스트 D-6E1-4(swap ≥ 1.5GB) 안에서만 — 미달이면 「미실측」 표기하고 보고. **호스트 미달 시 빌드를 기다리지 말고 문서 수정·커밋·보고까지 끝낸다**(acceptance 는 팀장이 호스트 회복 뒤 verifier 표적으로 돌린다) | R1-H-1~3 · M-1~3 · L-1~4 · G-1~G-11 |

## 계약 갱신 r5 (2026-10-08, 팀장 — 수정 라운드 1 수령·동결)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-10** | **수정 라운드 1 수령·동결.** 레인 커밋 여섯 `57c571a7`(runbook §4 여섯 절 재작성 — release 는 정책 version 파생 상수·승격/demote 0·비현재 EXACT 거부·6D-1 정의는 fake 위에서만·유일 지렛대는 이전 정책 값 재배포이나 **배포 절차가 저장소에 없음**까지 적음 · G-4 주장 낮춤 · INCOMPLETE 근거 교체 · 거부 자리 셋) · `427532a7`(C-1 ⓐ 전수 재대조 — **ⓐ20/ⓑ2/ⓒ39, 성립 범위 22/61**; ⓒ 를 「구현에 없다」(OPEN 신설 필요 14)와 「구현은 성립하고 측정만 없다」(test 신설 5)로 가름) · `1dacc5d7`(ci.yml G-4 — 주석 낮춤·실패 문면 본문 전문 제거·재조회 200 단언; step 이름 13 불변) · `b0df65c6`(C-7 R-ASYNC-03 수집 파일 자물쇠로 재연결, 집계 61/43/18/0 불변 · R-RATE-02 경로 · **C-6 G-9 한 줄 — 팀장 산출물이나 D-6E1-9 ⑧ 할당대로 레인 수정 수용**) · `24bdc8bd`(evidence — 등식 24/24(§4 양면 대조 포함) · 정적 변이 6/6 RED) · `dff87ad4`(rollback — C-6·C-7·milestone 대상 등재, **실측 HEAD `24bdc8bd`**, ⓪~③ 재실측 A 6·M 2·base 바이트 동일). 팀장 대조: 미커밋 0 · production·migration·docker·build·`config/quality` diff 0 · ci.yml step 이름 0 · 유효성 `git diff --name-only 24bdc8bd..HEAD -- <대상 여덟>` 빈 출력 · 6E-1 파일 참조형 누출 0(스캔 범위 안 hit 3 은 6B-2 runbook 의 `--no-role-passwords` 옵션명, 이 slice 밖·게이트 입력 밖). R1-M-2 자진 보고 철회 수용. **신설 OPEN 둘 등재**: `OPEN-6E1-MODEL-ROLLBACK-MECHANISM`(승격 상태·demote·재배포 절차 — ML 레인·운영자 결정; 6D-1 C-3 정의 재검토 포함) · `OPEN-6E1-G4-NONVACUOUS`(후보 ≥1 seed + `wouldNotifyNoticeIds` 비어 있지 않음 전제 — ML 배선 뒤). **미실측(호스트)**: `check`·`qualityBaseline`·container job·rollback ④~⑥ — swap free 1.42GB < 예외 문턱 1.5GB, 회복 없음(상주 서비스). **판정 SHA = 이 r5 커밋** | 레인 보고 · 팀장 대조 |
| **D-6E1-11** | **verifier 표적 재검증(opus)** — 호스트 결정 뒤: ① acceptance `check`·`qualityBaseline` 판정 SHA 재실측 + container job 재현(G-4 r1 블록 실제 실행 줄) ② R1-H-1 — runbook §4 의 각 진술을 코드로 재대조(`build_derived_release`·`_validate_selector`·레지스트리 grep·배포 절차 부재) · 「없는 명령 0」 ③ R1-H-2 — 문면 셋이 낮춰졌는지 + G-4 키 변이·200→201 동적 변이 ④ R1-H-3 — 새 ⓐ 20 에서 **표본 ≥5 재대조**(앞 표본과 다른 행) + 강등 9 행의 빈 bullet 명시 확인 + ⓒ 두 갈래(14/5) 표본 ⑤ M-1~3·L-1~4·G-1~11 닫힘 대조 ⑥ 등식 24 재실행 ⑦ rollback ⓪ 등식 판정 SHA 재산출 + 유효성 + ④~⑥ ⑧ 장부 ⑨ 호스트 규율 | 하네스 |

## 계약 갱신 r6 (2026-10-08, 팀장 — 호스트 예외 갱신)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-12** | **사용자 결정: 이 slice 의 남은 빌드에 한해 swap 문턱 1.0GB**(available ≥ 6GB 유지, swap free 1GB 아래면 즉시 중단·보고; 6F-10 의 1회 예외와 같은 형태). D-6E1-4 의 1.5GB 를 대체하되 규칙(2GB)은 유지, 다음 slice 이월 없음. 순서: **레인이 acceptance 셋 + rollback ④~⑥ 을 먼저 실측해 commands·rollback 을 채운다(evidence 커밋, 산출물 불변) → 동결 → verifier 표적 재검증(D-6E1-11)**. 호스트 전체 무거운 빌드 1개 규칙 그대로(레인 → verifier 직렬) | 사용자 2026-10-08 |

## 계약 갱신 r7 (2026-10-08, 팀장 — acceptance 수령·동결 · 표적 재검증 착수)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-13** | **acceptance 수령·동결.** evidence 커밋 `d327390b`(산출물 불변, `05_implementer_fix1.md` 「acceptance 실측」 절): worktree `check` exit 0 + **깨끗한 clone `check --no-build-cache` exit 0**(359 task 전부 실행 · 2817 test 실패 0 · e2e 7 · 두 게이트 실행) · `qualityBaseline` exit 0 · container job S-21a~S-25 exit 0(G-4 r1 블록 실행 줄, 잔여 컨테이너·볼륨 0) · M4 동적 변이 200→201 step exit 1(문면이 키 집합·code 만 — G-3 조치 함께 실측) → 복원 초록 · rollback ①~⑥ exit 0(`ci.yml`·`milestone-6.md` base 바이트 동일). 레인이 멈췄던 사유: 백그라운드 빌드 완료 통지 대기 — 결과 파일을 읽어 재개(교훈: waiter 는 죽는다, 결과 파일 폴링). **고정점 해석 수용**: 실측 HEAD `24bdc8bd` 유지 — `M` 둘(hunk 역적용, 내용 의존)은 그 뒤 불변(팀장 실측 빈 출력), `A` 여섯은 제거 대상이라 내용 변경이 무효화하지 않는다; rollback.md 가 종류별로 갈라 적음. 팀장 대조: 미커밋 0 · production·build·docker·`config/quality` diff 0 · daemon 0. 호스트: 문턱 1.0GB 결정 뒤 swap 4.2~5.0GB 로 회복(1GB 문턱에 닿지 않음). **판정 SHA = 이 r7 커밋** → vr-6e1 표적 재검증(D-6E1-11) | 레인 보고 · 팀장 대조 |

## 계약 갱신 r8 (2026-10-08, 팀장 — 표적 재검증 수령 · 수정 라운드 2)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-14** | **표적 재검증 `not-ready` @`39d64a5c`**(`06_verifier_targeted.md`): R1-H-1·H-2·M-1~3·L-1~4 **닫힘**(runbook §4 코드 재대조 통과 · G-4 문면 낮춤 확인 · QUAL-02 동적 변이 RED 2/3). **RT-H-1(R1-H-3 미닫힘)**: 새 ⓐ 20 중 앞 표본과 다른 6 행에서 **3 위반** — OPS-10(수집·run 행마다 release id 요구 — `raw_observation.release_sha` 만 있고 수집 release-sha 기본값 `unversioned`) · DEC-08(분류 정책 version 저장 요구 — production 은 `classification` 만 보존, version 열 없음) · COL-07(척도·기준 불일치 거부가 운영 `rangeBands` 표에 의존하는데 출하 표가 비어 있음(`OPEN-DEC-10`) — 인용 test 는 test 정책 아래서만 통과). 공통 결함: **「test 가 있다」와 「production 배선·출하 정책 값에서 그 거동이 성립한다」를 같은 것으로 읽었다.** RT-M-1 OPS-09 는 「구현 성립·test 만 없음」이 아니라 「구현에 없다」(403 이 응답 봉투로만 분류돼 재시도 없음을 보장하지 않음) → 14/5 → 15/4. RT-L-1 checklist 낡은 수치(ⓒ30·ⓐ29) · RT-L-2 commands 「24 단언」 vs 출력 29 · RT-L-3 scope.md D-6E1-10 에 누출 어휘 축어(옵션 이름 — 게이트 밖이나 하네스 규율 위반, 이 r8 에서 참조형으로 정정하지 않고 **사실 선언**: 그 줄은 6B-2 옵션 이름 인용이며 비밀값이 아니다) · RT-L-4 종류별 rollback 유효성 해석은 타당하나 CLAUDE.md 문면과 다름 → **하네스 레인 변경 절에 등재**(evidence-pack 규격에 「`A` 제거 대상은 내용 변경이 유효성을 무효화하지 않는다」 문장 추가 후보, `OPEN-HARNESS-ROLLBACK-VALIDITY-BY-KIND`) · RT-L-5 verifier 가 worktree 인덱스를 잠시 덮었다가 복원(잔여 0 — 사실 선언). acceptance 셋 전부 재실측 exit 0(깨끗한 clone 전건 · container 재현 · 등식 29 · rollback 등식·유효성 빈 출력). **재작업 2/5** | 표적 재검증 |
| **D-6E1-15** | **수정 라운드 2 — 레인 커밋: C-1 1 + evidence 1.** ① **ⓐ 술어 명문화**(표 머리에): 「bullet 의 거동이 **production 배선과 출하 정책 값**에서 성립하고, **등재 식별자**가 그 거동을 **그 배선·그 값으로** 잰다」 — test 정책·test 대역·fake 아래서만 성립하면 ⓐ 아님. ② 이 술어로 **ⓐ 20 전수 재대조** — 행마다 bullet 별로 (a) 식별자 (b) production 배선 사실(파일·타입·출하 값) 두 칸을 적는다; 하나라도 비면 ⓒ. OPS-10·DEC-08·COL-07 은 ⓒ 로. **의심되면 ⓒ** — 과소가 과대보다 낫다(완료 조건 3 은 어차피 오늘 성립하지 않는다). ③ RT-M-1 OPS-09 → 「구현에 없다」, 두 갈래 집계 갱신 ④ RT-L-1·L-2 수치 정정 ⑤ RT-L-4 를 scope.md 「하네스 레인 변경」 절과 checklist 에 등재(팀장이 scope 절을 쓴다) ⑥ evidence: 등식 수 정정, 변이(가짜 식별자 → RED) 재실측, rollback 실측 HEAD 를 새 C-1 커밋으로 옮기고 ⓪~③ 재실측(④~⑥ 은 C-1 이 문서라 트리 동일성 갈음 불가 — `check` 1회 재실측, container 는 ci.yml 불변이라 생략 사유 등재) ⑦ 보고에 **새 집계와 강등 행 목록**. 그 뒤 **표적 재검증 2(C-1 ⓐ 전수 표본 ≥8 + 장부)** | RT-H-1 · M-1 · L-1~5 |

## 계약 갱신 r9 (2026-10-08, 팀장 — 수정 라운드 2 수령·동결 · 표적 재검증 2)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-16** | **수정 라운드 2 수령·동결.** C-1 `29bfa5f7`(산출물 유일 변경; = rollback 실측 HEAD) · evidence `9ea777f1`·`abe0efb5`(`07_implementer_fix2.md`). **ⓐ15/ⓑ2/ⓒ44, 성립 범위 17/61**, 두 갈래 OPEN 신설 필요 **17** / test 만 없음 **4**. ⓐ 술어 셋 명문화 + ⓐ 행마다 production 배선·출하 값 칸(7 열). 강등 5: COL-07(출하 `rangeBands` 빈 표, `OPEN-DEC-10`) · STR-02(요건 원문 축 부재, `OPEN-6F4-STR02-REQUIREMENTS-AXIS`) · ML-04(출하 조립이 `UnavailableMlAnalysis` 고정, `OPEN-ML-ANALYSIS-WIRING`) · DEC-08(정책 version 저장 칸 0) · OPS-10(실행 행 릴리스 식별자 칸 0). OPS-09 → 「구현에 없다」. 등식 31 OK(E-23 ⓐ production 칸 전부 채움 · E-24 「강함」 표식 == 열거 18 신설; 세는 법 `grep -c '^OK'` 로 고정 — RT-L-2 정정) · 변이 M8 신설(production 칸 비움 → E-23 RED) · rollback ①~⑥ 재실측(`check` 1회 exit 0, container 는 ci.yml 불변이라 생략 사유). 팀장 대조: 미커밋 0 · runbook·ci.yml·C-6·C-7 불변 · production diff 0 · `M` 둘 유효성 빈 출력 · 누출 0 · daemon 0. **OPEN 등재 결정**: 신설 OPEN 은 이 slice 가 **열지 않는다** — C-1 ⓒ 의 「OPEN 신설 필요 17」은 **종결 보고의 OPEN 에스컬레이션 목록**(사용자가 열지·어느 slice 가 받을지 결정)이고, 계약에 등재하는 신설은 r1 의 둘(`OPEN-6E1-MODEL-ROLLBACK-MECHANISM`·`OPEN-6E1-G4-NONVACUOUS`)뿐이다. **판정 SHA = 이 r9 커밋** | 레인 보고 · 팀장 대조 |
| **D-6E1-17** | **verifier 표적 재검증 2(C-1 + 장부)**: ① 새 ⓐ 15 에서 **표본 ≥8**(앞 두 라운드 표본과 다른 행 우선)을 bullet 단위로 — 술어 셋(식별자 · production 배선·출하 값 · 그 배선으로 잼) 각각 ② 강등 5 와 OPS-09 갈래 이동의 근거 대조 ③ 등식 31 재실행 + M8·M1 변이 ④ rollback ⓪ 등식 판정 SHA 재산출 + `M` 둘 유효성 + `A` 종류별 해석 ⑤ 장부(수치 일관 — C-1·checklist·commands 의 15/2/44·17/4, 좌표·축어·크기) ⑥ acceptance `check`·`qualityBaseline` 판정 SHA 1회(container 생략 사유 타당성 판정) ⑦ 호스트 D-6E1-12. 새 high 없으면 종결 | 하네스 |

## 계약 갱신 r10 (2026-10-08, 팀장 — 표적 재검증 2 수령 · 승인 전 일괄)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-18** | **표적 재검증 2 `ready-for-review` @`c04aae88`, 새 high 0, RT-H-1 닫힘**(`08_verifier_targeted2.md`): 표본 9(앞 라운드와 다른 8 + ML-01) 술어 셋 전부 통과, 강등 5·OPS-09 이동 근거 일치. acceptance 깨끗한 clone 전건 exit 0 · `qualityBaseline` exit 0 · container 생략 타당(evidence 만 변경, 39d64a5c 에서 verifier 자신이 재현) · 등식 31 · M8(다른 행)·M1 RED · rollback 등식·유효성 빈 출력, 종류별 해석 타당(`git archive` 로 인덱스 무접촉). non-blocking: **RT2-M-1** 술어 ② 비일관 — ML-01·ML-02 는 ML-04 와 같은 상태(출하 ml-serving 이미지에서 성립·커널 골든 011 로 잠김이나 Kotlin 출하 조립이 `UnavailableMlAnalysis` 고정) → 「의심되면 ⓒ」 대로 **ⓐ13, 성립 범위 15/61** · **RT2-M-2** COL-01 둘째 bullet(기준일 없으면 오늘) 은 production 경로 없음(`from`·`to` 필수) — 「부재가 요구를 충족」 근거로 행에 기재 · RT2-L-1 STR-03 에 `StrategyEditEndpointTest` 인용 · RT2-L-2 checklist 항목 3 「ⓒ 30」 · RT2-L-3 E-23 의 `-` 통과는 술어 한계(공시됨). 누출 1 = scope.md 의 6B-2 옵션 이름 인용(D-6E1-14 사실 선언, 게이트 밖). 재작업 **2/5**(not-ready 추가 없음) | 표적 재검증 2 |
| **D-6E1-19** | **승인 전 일괄 — 레인 커밋 둘(C-1+checklist 1 · evidence 1), 그 뒤 표적 재검증 3(경량: C-1 diff·등식·장부)**: ① ML-01·ML-02 → ⓒ(「구현에 없다」 갈래 — 갈래 집계 갱신), 집계 ⓐ13/ⓑ2/ⓒ46 ② COL-01 둘째 bullet 근거 기재 ③ STR-03 식별자 추가 ④ checklist 수치 전부 최신으로(ⓒ 30 잔존 제거) ⑤ evidence: 등식 재실행(E-23·E-24 갱신), 변이 M1·M8 재측정, rollback 실측 HEAD 를 새 C-1 커밋으로 ⓪~③ 재실측 + `check` 1회(container 생략 사유). **집계가 또 바뀌므로 milestone 종결 문단은 이 일괄 뒤 팀장이 쓴다** | RT2-M-1·2 · L-1~3 |

## 계약 갱신 r11 (2026-10-08, 팀장 — 일괄 수령·동결 · 표적 재검증 3)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-20** | **일괄 수령·동결.** C-1+checklist `ba6c25f1`(= rollback 실측 HEAD) · evidence `c8419916`(`09_implementer_batch.md`). **최종 집계 ⓐ13/ⓑ2/ⓒ46, 성립 범위 15/61**, 갈래 OPEN 신설 필요 17 / test 만 없음 4(ML-01·02 는 기존 홀더 `OPEN-ML-ANALYSIS-WIRING`). RT2-M-1·M-2·L-1·L-2 조치, **RT2-L-3 보강**(E-23 이 자리채움 여섯 류 + 20자 미만을 거부, M9 변이 RED — **술어 변경**). 등식 31 OK · 변이 M1·M8 자리 이동 재측정 RED · rollback ⓪~③ + `check` exit 0(container 생략: ci.yml 불변). 팀장 대조: 미커밋 0 · production diff 0 · `M` 둘 유효성 빈 출력 · daemon 0. **판정 SHA = 이 r11 커밋** | 레인 보고 · 팀장 대조 |
| **D-6E1-21** | **표적 재검증 3(경량, 술어 변경 규칙)**: ① E-23 새 술어 — 자리채움 변이(`-`·`—`·`N/A`·`TBD`·`없음`·공백·19자) 전부 RED 인지 + 정상 ⓐ 13 행 초록 ② ML-01·02 강등과 COL-01 근거 문면 대조 ③ 등식 31 재실행 ④ 수치 일관(C-1·checklist·commands 13/2/46·17/4) ⑤ rollback ⓪ 등식 + `M` 둘 유효성 ⑥ `qualityBaseline` 1회(evidence 누출 게이트). 통과 → 종결 | 하네스 |

## 계약 갱신 r12 (2026-10-08, 팀장 — 종결 판정)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-22** | **표적 재검증 3 `ready-for-review` @`4d8ae582`, 새 high·medium 0**(`10_verifier_targeted3.md`): E-23 자리채움 변이 일곱 + 백틱·굵게 전부 RED(numstat 1 1 확인·복원), 기준·음성 대조 31 OK · ML-01/02 강등·COL-01 근거·STR-03 인용 문면 일치 · 수치 13/2/46·4·17·15/61 전부 일관 · rollback 등식·유효성 빈 출력 · production diff 0 · `qualityBaseline` exit 0 + `leakPatternGate --rerun` exit 0. RT3-L-1(20자 이상 자리채움은 통과 — 문자열 술어 한계, 공시됨) 등재. **6E-1 종결** — 재작업 **2/5**(not-ready 2: r1·표적 1). 다음: milestone 종결 문단 → push → PR → `/code-review` → 조치 → **머지는 사용자 결정** | 표적 재검증 3 |

## 계약 갱신 r13 (2026-10-08, 팀장 — PR #65 `/code-review` 수령·처분)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-23** | **PR #65 `/code-review`(head `9c3f9d57`) finding 7 — 처분. 조치 라운드: 산출물 커밋 ≤2(runbook · ci.yml 공유) + evidence 1 → 표적 재검증 4 → 조치 코멘트 새로.** **F1+F7(rollback, 같은 뿌리)**: rollback.md 가 `milestone-6.md` hunk 출처를 `89a0d8fc` 하나로 **하드코딩**해 팀장 종결 문단 `9c3f9d57` 을 빠뜨림 — 리뷰어가 그 하나만 역적용해 실패. 처방: **목록을 문서에 박지 않고** `git log --no-merges --format=%h <base>..HEAD -- <파일>` 로 **실행 시 산출, 최신부터** 역적용(6D-2 rollback.md 선례; 팀장은 `9c3f9d57` 에서 순차 역적용 base 동일을 이미 실측); 「실측 뒤 움직인 것은 commands.md 하나」 문면 정정 — `M` 유효성은 **산출물 레인 커밋 범위**로 한정하고 팀장 종결 문단은 「팀장 hunk 재실측 @종결 커밋」이 든다고 적는다; 실측 HEAD 를 조치 산출물 커밋으로 옮겨 ⓪~③ 재실측 **F2(runbook §3.6 사실 오류, high 급)**: 「앱 역할은 DELETE 권한 없음」은 GRANT 가 `bidvector_app` 역할에 있을 뿐이고 그 역할은 `NOLOGIN` 이며 `src/main` 에 `SET ROLE` 0 — 출하 배포 모양 전부(compose·CI container·6G runbook)에서 앱은 `bidvector.persistence.username`(= superuser)로 접속해 **권한 보호가 작동하지 않는다**. 처방: §3.6 과 checklist 의 「권한으로 닫혀 있다」를 **사실로** 다시 쓴다(「GRANT 는 있으나 접속 역할이 그 역할이 아니다 — 보호는 운영 반입 시 접속 역할을 `bidvector_app` 로 바꿀 때만 선다」), 신설 **`OPEN-6E1-APP-ROLE-NOT-ASSUMED`**(운영 반입 전제, 6E-2/M7; 6B-2 축9 권한 행렬이 잰 것은 역할의 권한이지 접속 역할이 아님) · E-14 가 GRANT 줄만 보는 한계를 등식 설명에 **F3** ci.yml 편집 세션 왕복 사본 둘 → `_edit_field <FIELD> <COUNT>` helper 로 합치고 첫 왕복의 `.state == APPLIED` 단언을 둘 다에(**step 이름 불변**) **F4** `keys=$(… jq …)` 에 `|| fail` guard **F5** commands.md `+55`→실제 diff 수·C-1 커밋 참조 `ba6c25f1` 로 **F6** S-23c 주석의 S-23b 종료 상태를 `revision+2`·`max_active_bids=5` 로. ci.yml 이 바뀌므로 **container job 로컬 재현 필수**. 레인 자유 판단 없음 — 전부 고친다 | `/code-review` 65 |

## 계약 갱신 r14 (2026-10-08, 팀장 — 조치 라운드 수령·동결 · 표적 재검증 4)

| ID | 결정 | 근거 |
|---|---|---|
| **D-6E1-24** | **PR #65 조치 라운드 수령·동결.** runbook `9b179d57` · ci.yml `0aa96b56`(= rollback 실측 HEAD, 공유) · evidence `bffaaf6b`(`11_implementer_pr65.md`). F2 — 코드 실측 넷(GRANT 대상 `bidvector_app` · `NOLOGIN` · `SET ROLE` 0 · 출하 접속 사용자 = DB 소유 superuser 변수)으로 §3.6 사실화(「앱 세션은 outbox 행을 지울 수 있다; 지금 서는 보호는 전이표와 운영 규율뿐」) + **신설 `OPEN-6E1-APP-ROLE-NOT-ASSUMED`** + E-25(세 사실 코드 잠금) · F3/F4/F6 ci.yml `_edit_field` helper·guard·주석(step 13 불변, +43/−46) · **F1 이 실제 값을 냄**: 실행 시 산출 목록이 ci.yml 셋·milestone 둘 — 하드코딩 목록이면 둘 누락 · F7 `M` 유효성 범위 산출물 레인 커밋으로 · container job 재현 exit 0 + 동적 변이 M4·M10·M11 RED(M11 = helper 미사용 경로가 둘째 왕복에서 끊김 — F3 목적 실측) · rollback ①~⑥ exit 0 · 등식 32 OK · C-1 집계 불변(ⓐ13/ⓑ2/ⓒ46). 팀장 대조: 미커밋 0 · production diff 0 · step 이름 변경 0 · 누출 0 · daemon 0. **신설 OPEN 셋 등재 확정**: `OPEN-6E1-MODEL-ROLLBACK-MECHANISM` · `OPEN-6E1-G4-NONVACUOUS` · `OPEN-6E1-APP-ROLE-NOT-ASSUMED`. **판정 SHA = 이 r14 커밋** | 레인 보고 · 팀장 대조 |
| **D-6E1-25** | **표적 재검증 4(조치 커밋만)**: ① F2 — §3.6 의 사실 넷을 코드로 재대조(마이그레이션 `CREATE ROLE … NOLOGIN`·GRANT · `src/main` `SET ROLE` grep 0 · compose/CI 접속 사용자 변수) + E-25 변이(사실 하나 문면 반전 → RED) ② ci.yml — helper 변이 M10·M11 정적 재현(셸 논리) · `|| fail` guard · 주석 사실 · step 이름 13 ③ rollback — 산출 루프를 판정 SHA 에서 실행해 두 공유 파일 base 바이트 동일(milestone 은 팀장 커밋 포함) · ⓪ 등식 · `M` 유효성(산출물 레인 범위 `0aa96b56..<판정 SHA>` 빈 출력) ④ 등식 32 · 수치 일관 ⑤ acceptance `check`·`qualityBaseline` 1회(container 는 레인 재현 로그 정적 대조, 여력 있으면 재현) ⑥ 장부 ⑦ 호스트 D-6E1-12. 통과 → 조치 코멘트 새로 → CI → **머지 결정 요청** | 하네스 |
