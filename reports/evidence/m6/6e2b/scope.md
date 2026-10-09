# M6/6E-2b — 이미지 SBOM · CVE 스캔 · 차단 정책 (2026-10-08, 착수 계약, 팀장)

6E-2 의 둘째 갈래(첫째는 6E-2a 풀·접속 역할, `reports/evidence/m6/6e2a/scope.md`). 사용자 결정 2026-10-08 「추천대로 6E-2 진행해」.
완료 조건 8(「security/secret scan 통과」)의 **나머지 절반** — secret scan(`leakPatternGate`)과 이미지 위생(6C)은 있고 **취약점 스캔·SBOM 이 없다**.

- base: `2deb5f9d`. worktree `bid-vector-v2-m6-6e2b`, 브랜치 `m6-6e2b/2026-10-08`.
- 착수 조사 `_workspace/m6-6e2b/00_scout.md`. 결정 ID `D-6E2B-n`. evidence `reports/evidence/m6/6e2b/`.
- 받는 OPEN: **`OPEN-6C-IMAGE-VULN-SCAN`**(D-6C-5: 「6C 에서 흉내만 낸 스캔은 상시 초록 게이트가 된다」).
- Codex: 없음(되돌리기 어려운 경로 아님 — CI·도구·정책 파일).

## 착수 실측 (요지)

- 이미지 둘: `docker/app.Dockerfile`(temurin 21 jre noble, 다이제스트 고정) · `docker/ml-serving.Dockerfile`(python 3.12.8 slim bookworm, 다이제스트 고정). **빌드 stage 의 `COPY --from=ghcr.io/astral-sh/uv:0.9.22` 는 태그만**(다이제스트 없음, 위생 정책 밖).
- CI `container` job 이 두 이미지를 빌드하고 위생 게이트(S-22a/b)를 돈다 — docker·레지스트리 네트워크 전제가 이미 있다. 액션은 major 고정(SHA 아님), 새 도구 설치 선례는 `install buf`(릴리스 바이너리 직접, **버전은 정책 파일**, 워크플로에 숫자 금지).
- trivy·grype·syft 호스트·러너 모두 없음. Gradle 의존성 검증·락 없음(이 slice 범위 밖, 아래 OPEN).
- 임계·차단 정책·allowlist 규칙을 정한 문장은 저장소에 없다 — 이 slice 가 처음 정한다.

## 운영자 결정 — 추천안으로 착수(사용자 「추천대로」, 정정 시 계약 갱신)

- **B-1 도구** — (a) **Trivy 단일 도구**(이미지 하나에서 OS 패키지 + JVM jar + Python site-packages 를 함께 보고, 같은 실행에서 CycloneDX SBOM 생성 → SBOM 과 스캔 대상이 어긋날 자리 없음) · (b) CycloneDX Gradle + cyclonedx-py + syft + grype(베이스 OS 를 못 보고 SBOM 이 여러 벌). **추천 (a)**.
- **B-2 설치** — buf 선례: 릴리스 바이너리 직접 다운로드, **버전과 SHA-256 을 정책 파일**(`config/quality/vuln-policy.properties`)에 두고 설치 step 이 체크섬 불일치 시 비-0. `aquasecurity/trivy-action` 은 쓰지 않는다(액션 내부 trivy 버전이 워크플로에 안 보이는 둘째 버전 축 + 2026-03 trivy 배포 경로 공급망 사고 이력 — 버전은 그 사고와 무관함을 확인한 릴리스로 고르고 근거를 evidence 에).
- **B-3 차단 문턱** — (a) **HIGH·CRITICAL 중 수정본이 있는 것(`--ignore-unfixed`)만 차단**, 나머지는 보고서·SBOM 에 남김 · (b) 수정본 무관 HIGH·CRITICAL 전부 차단. **추천 (a)** — 수정할 길이 없는 것으로 붉어지면 상시 붉은 게이트가 된다.
- **B-4 allowlist** — 정책 파일 옆 `config/quality/vuln-allowlist.properties`(또는 동형): 키 = **취약점 ID + 패키지 이름 + 이미지 kind**, 값 = **만료일 + 사유**. 좌표(`file:line`) 금지. 래퍼가 ⓐ 만료 지난 등재 ⓑ **더 이상 아무 finding 과도 맞지 않는 등재**(stale) 둘 다 **비-0**(leak-baseline 「조용한 stale 금지」 동형). 만료 상한 90일.
- **B-5 취약점 DB** — (a) **실행마다 받고**, 받은 DB 의 버전·갱신 시각을 로그·evidence 에 남김 · (b) 다이제스트 핀. **추천 (a)** — 새 CVE 로 PR 이 붉어지는 것은 게이트의 목적이고 처방(올리기 또는 만료 있는 등재)이 정책 파일에 있다. 무관 PR 이 붉어지는 비용은 알려진 제한으로 등재.
- **B-6 SBOM 보관** — CycloneDX JSON 을 이미지마다 `upload-artifact`(보존 90일 — 운영자 보존 결정 2026-09-26 과 같은 값).
- **B-7 uv 빌드 stage 참조** — 다이제스트 고정으로 올린다(이미지 위생 축과 같은 규율).

## 산출물

| ID | 무엇 | 공허함을 막는 술어 |
|---|---|---|
| **V-1** | `config/quality/vuln-policy.properties` — 도구 버전·SHA-256·차단 severity·ignore-unfixed·스캔 대상 kind·SBOM 형식 | 래퍼가 키 누락·값 모양 위반을 비-0(기본값 없음, image-hygiene 동형) |
| **V-2** | `tools/vuln-scan-check.sh <image> <kind> <policy>` — SBOM 생성 + 스캔 + allowlist 적용 + 만료·stale 검사. 판정은 **트리비 JSON 결과를 파싱한 집합**(jq)에 걸고, 텍스트 표 출력 grep 에 걸지 않는다 | 음성 대조 넷(아래 acceptance) 전부 RED |
| **V-3** | `.github/workflows/ci.yml` `container` job — trivy 설치 step(정책 파일에서 버전·체크섬) + 두 이미지 스캔 step(S-22b 뒤) + SBOM upload | 로컬 재현 exit 0 · 워크플로에 버전 숫자 0 |
| **V-4** | `config/quality/vuln-allowlist.properties` — **오늘의 실제 finding 을 triage** 한 결과(올려서 없앨 수 있는 것은 베이스 다이제스트·의존 버전 상향이 아니라 이 slice 에서는 등재 + 사유 + 만료, 상향은 별도 결정으로 보고) | 등재마다 실재 finding 과 대응(stale 0) |
| **V-5** | `docker/ml-serving.Dockerfile` uv 참조 다이제스트 고정(B-7) | 이미지 재빌드·위생 S-22a 초록 |
| **V-6** | 문서 — runbook `docs/runbook/m6-6e-operations.md` 에 스캔 판독·allowlist 등재·만료 처리 절(새 절, 기존 절 번호 보존) | — |

## in_scope / out_scope

- in: `config/quality/vuln-policy.properties`(신설) · `config/quality/vuln-allowlist.properties`(신설) · `tools/vuln-scan-check.sh`(신설) · `.github/workflows/ci.yml`(`container` job 의 설치·스캔·업로드 step 추가만) · `docker/ml-serving.Dockerfile`(uv 참조 한 줄) · `docs/runbook/m6-6e-operations.md`(새 절) · `reports/evidence/m6/6e2b/**` · `milestone-6.md`(착수·종결 문단).
- out: 6E-2a 경로 전부(production 코드·Gradle 빌드·`gate-tests.properties`) · 베이스 이미지 다이제스트 상향(triage 결과로 필요하면 보고 → 결정) · Gradle 의존성 검증·락(`OPEN-6E2B-GRADLE-DEPENDENCY-VERIFICATION` 신설 등재만) · 멀티아키(`OPEN-6C-MULTIARCH`) · `OPEN-6C-POLICY-GATE-STRUCTURAL`(셸 게이트 하나가 늘어남을 모집단 증가로 등재만) · 레지스트리 push·서명.

## acceptance

CI `container` job 로컬 재현(S-21~S-25 + 새 step; 호스트 규율: pgrep·free·ps 별도 호출, available ≥ 6GB·swap free ≥ 2GB, 호스트 전체 무거운 빌드 1개 — 6E-2a 레인과 직렬) · `./gradlew --no-daemon check`(`leakPatternGate` 가 evidence 를 읽으므로; 새 evidence 에 스캔 어휘 축어 금지). 음성 대조(바꿔치우기): ① 차단 severity 를 `CRITICAL` 만 → allowlist 의 HIGH 등재가 stale 로 RED(또는 동치) ② allowlist 등재 하나 삭제 → 그 finding 으로 RED ③ 만료일을 어제로 → RED ④ 존재하지 않는 취약점 ID 등재 → stale RED ⑤ 정책 파일의 체크섬 한 글자 → 설치 RED. 양성 1회: 알려진 취약 이미지(오래된 공개 다이제스트)를 스캔해 finding 집합이 비지 않음.

## rollback

**정본은 `reports/evidence/m6/6e2b/rollback.md`** 이고 착수 때 적은 아래 문면은 그 뒤 두 번 바뀌었다.
현행: **단일 restore 다섯**(`ci.yml` · 정책 둘 · `docker/ml-serving.Dockerfile` · `tools/vuln-scan-check.sh`,
`git restore --source=2deb5f9d`) + **공유 둘**(`docs/runbook/m6-6e-operations.md` · `milestone-6.md`)은
**이 브랜치에 마지막으로 병합한 main 커밋 `b6d31b87` 판으로 복원**하고, 그 앞에 단언 둘(핀이 브랜치의
조상 · 핀이 이 slice 의 첫 산출물 커밋을 담지 않음)을 둔다. 임시 clone 에서 ①~⑥ 실측(⑥ 게이트 =
container job 재현) + **표지 계수**(이 slice 0 · 6E-2a 보존).

~~착수 문면: 신설 파일 제거 · Dockerfile 은 restore · 공유 파일은 커밋 해시 hunk 역적용~~ —
**hunk 역적용은 실측에서 완결되지 않았다**(conflict 둘, 내 줄 4건 잔존). 폐기 사유는 rollback.md.

## 하네스 레인 변경 (상시)

착수 시점에는 없었다. 그 뒤 둘로 늘었다 — **병합으로 들어온 것**과 **이 slice 가 낳은 것**이다.

**(1) `origin/main` 병합(PR #66)이 들여온 6E-2a 레인의 하네스 커밋 셋** — 이 slice 의 산출물이 아니고
in_scope 밖이며, 운영자 승인 아래 같은 range 에 있다:

| 커밋 | 경로 | 목적 |
|---|---|---|
| `99f2067c` | `CLAUDE.md` | 색인의 Codex 핀을 정본에 맞춤(0.160.1 · MCP 비활성) |
| `cbef2c06` | `.claude/skills/codex-review-gate/SKILL.md` | 호출마다 MCP·plugin·샌드박스 네트워크 비활성 |
| `075c03b1` | `.claude/skills/codex-review-gate/SKILL.md` | 바이너리·버전 핀 갱신(WSL 경로 · 0.160.1) |

**(2) 이 slice 가 낳은 하네스 PR 둘** — 레인이 겪은 사고가 하네스 문면으로 올라간 것이고, **이 브랜치의
커밋이 아니다**: **PR #67**(scratchpad 레인별 하위 디렉터리 · 슬롯 판정 두 표본과 `/proc` cwd 귀속) ·
**PR #69**(공유 파일 되돌림 — 고정 핀과 단언 둘).

되돌림은 이 둘을 **건드리지 않는다**(rollback.md 「되돌리지 않는 것」).

## 계약 갱신 r1 (2026-10-08, 팀장 — triage 결정)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-1** | 오늘의 수정 가능 HIGH/CRITICAL 67건(베이스 이미지 54 · jackson 10 · tomcat-embed-core CRITICAL 3)의 **상향은 후속 slice 6E-2c**(베이스 다이제스트 + tomcat·jackson 버전, `OPEN-6E2B-BASE-IMAGE-BUMP`·`OPEN-6E2B-DEPENDENCY-BUMP`). 이 slice 는 등재로 두되 **CRITICAL 등재 만료를 2026-10-31 로 단축**(HIGH 는 2026-12-31) — 6E-2c 가 늦어지면 게이트가 스스로 붉어진다 | 사용자 |

## 계약 갱신 r2 (2026-10-09, 팀장 — 구현 수령·동결 · 판정 착수)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-2** | 구현 레인 완료 보고 수령(`_workspace/m6-6e2b/02_implementer_report.md`, 레인 HEAD `95e738f7`) → **레인 동결**. 판정 대상 SHA = 이 계약 갱신 커밋. 레인 실측: container job 재현 · `check` · rollback ①~⑥ @`c4f22dd1` exit 0, `check` @`95e738f7` exit 0, 음성 대조 여섯 RED, 양성 1회. 판정 레인: verifier(opus) + code-reviewer(sonnet) 병렬 | 팀장 |

## 계약 갱신 r3 (2026-10-09, 팀장 — 판정 SHA 이동)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-3** | 동결(r2) 뒤 레인 커밋 둘 — `1d019fa3`(allowlist 사유 문면을 D-6E2B-1 뒤 상태로; 팀장 r1 지시의 늦은 반영) · `cb413d9c`(rollback 재실측, 실측 HEAD `1d019fa3`). **동결 위반으로 사실 기록**, 이력 되쓰지 않음. 판정 SHA 를 이 갱신 커밋으로 올린다; verifier 는 `ae2a8397` 판정 위에 델타(사유 문면·evidence 한정 · 게이트 계수 불변 · rollback 유효성)를 더한다 | 팀장 |

## 계약 갱신 r4 (2026-10-09, 팀장 — 판정 r1 수령 · 수정 라운드 1)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-4** | **verifier r1 `not-ready` @`6f583839`**(`_workspace/m6-6e2b/03_verifier_r1.md`) + code-review r1(high 1 · medium 5 · low 10, `03_code-review_r1.md`). 재작업 **1/5**. 두 리뷰의 뿌리는 둘: **(가) 암묵 입력** — trivy 가 cwd 의 `.trivyignore`·`trivy.yaml`·`TRIVY_*` 를 읽어 만료·stale 없는 둘째 면제 축이 된다(vr H-1 실측: CRITICAL 셋을 `.trivyignore` 로 옮겨 exit 0 · cr M-1) **(나) 스캔 축 양성 대조 부재** — 하한이 SBOM 입력에만 걸리고 「DB 와 실제로 맞춰 봤는가」는 allowlist stale 검사가 우연히 덮는다(6E-2c 가 allowlist 를 비우면 열림; vr M-1 · cr H-1 · cr M-2 DB `unknown` 통과 · severity 열거값 미검증) | 팀장 |
| **D-6E2B-5** | **수정 라운드 1 처방**: (가) trivy 를 **빈 임시 작업 디렉터리**에서 돌리고 config·ignorefile 을 명시적 빈 파일로 고정, 스크립트가 세우지 않은 `TRIVY_*` 환경변수가 있으면 **판정 거부(exit 2)** — 음성 대조 셋(저장소 루트 `.trivyignore` · `trivy.yaml` · `TRIVY_SEVERITY`) 추가 · (나) ① `block.severities` 를 trivy severity 열거값으로 검증 ② 스캔 산출 쪽 하한 — **finding 수가 아니라 스캔이 분석한 패키지 수**(전체 패키지 목록 출력에서 계수, kind 별 하한은 정책 파일) ③ DB 메타데이터 술어(버전·갱신 시각이 실재하고 정책 파일의 최대 경과 일수 안) — 각각 음성 대조 · cr M-3·M-4 S-22e/S-22f 조건(앞 스캔이 막아도 다음 스캔·업로드가 돌고, 앞 step 실패만으로 가짜 빨강이 생기지 않게 — 근거를 ci.yml 주석에) · cr M-5 하한 미달 종료 코드와 runbook §8.2 일치 · low 는 싼 것(`sha256sum --strict`·중복 키·`--quiet`·탭·`timeout-minutes`/`curl --retry`)만 고치고 나머지는 checklist 알려진 제한 · 장부 L-2(DB 판·시각 기록)·L-3(라운드 이력 서술 삭제). **L-1(CRITICAL 만료 단계가 데이터에만)은 알려진 제한으로 등재**(날짜 결정을 게이트 술어로 굳히지 않는다). 게이트 술어를 바꾸므로 severity 무관 **표적 재검증** | 팀장 |

## 계약 갱신 r5 (2026-10-09, 팀장 — 수정 라운드 1 수령·동결 · 표적 재검증)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-6** | 수정 라운드 1 수령(`_workspace/m6-6e2b/05_implementer_fix1.md`, 레인 HEAD `e7033ab6`, 마지막 산출물 커밋 = rollback 실측 HEAD `ebe8ebfc`) → **레인 동결**, 판정 SHA = 이 갱신 커밋. 신설 정책 키 넷(그중 `scan.db.max-age-days` 상향은 낡은 DB 로 판정하겠다는 결정). 게이트 술어 변경이므로 **verifier 표적 재검증**: (가) 세 겹 잠금 각각 독립 · (나) 열거값·분석 패키지 하한·DB 메타데이터 술어의 음성 대조 · N5(스캐너 빈 결과 + allowlist 비움 → exit 2) 재현 · 이번 수정이 연 새 표면(정책 키 넷) · S-22e/S-22f 조건 · 종료 코드-runbook 일치 · acceptance(container job · `check`) 판정 SHA 실측 · rollback 유효성 | 팀장 |

## 계약 갱신 r6 (2026-10-09, 팀장 — 표적 재검증 수령 · 승인 전 일괄)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-7** | **verifier 표적 재검증 `ready-for-review` @`1b89c70f`**(`_workspace/m6-6e2b/06_verifier_targeted.md`: container job·`check` exit 0 · r1 우회 닫힘 · 세 겹 잠금 각각 독립 · N5 exit 2 · 스크래치 루트 복원 출처 바이트 대조 부기). medium 셋 · low 둘 · 장부 하나. 재작업 1/5 유지 | 팀장 |
| **D-6E2B-8** | **승인 전 일괄** — 아래 둘은 **게이트 술어 변경이라 표적 재검증 한 번**: **M-2** trivy 캐시를 샌드박스 안의 빈 `--cache-dir` 로 고정(실행마다 새 DB — `XDG_CACHE_HOME`·`HOME` 경유 캐시 심기 차단) · **M-3** trivy 자신의 치명 오류를 판정 불가(exit 2) 경로로 · L-1 `scan.scanners` 에 `vuln` 필수 · L-2 미래 시각 DB 는 판정 불가. **M-1** 분석 패키지 하한은 「DB 와 실제로 맞춘 패키지」를 재지 않는다(OS 판 불일치 시 finding 이 줄어도 통과) → 정책 주석·runbook §8.2·보고의 주장을 실제 거동으로 **좁히고** 신설 `OPEN-6E2B-OS-MATCH-PREDICATE` + 알려진 제한. 장부: 실측 이미지 ID 를 commands.md 에(app 은 worktree 를 가른다는 실측과 함께) · L-3(동결 뒤 `_workspace` 편집) 사실 등재 | 팀장 |

## 계약 갱신 r7 (2026-10-09, 팀장 — 일괄 수령·동결 · 표적 재검증 2)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-9** | 승인 전 일괄 수령(`_workspace/m6-6e2b/07_implementer_batch.md`, 레인 HEAD `caf8ad64`, rollback 실측 HEAD `ecbb4028`). M-2 는 `--cache-dir` 를 샌드박스에 고정(열거 확장이 아니라 위치를 환경에서 빼앗음) · M-3·L-1·L-2 술어 · M-1 주장 좁힘 + `OPEN-6E2B-OS-MATCH-PREDICATE`(**6E-2c 전에 닫아야 할 항목** — 베이스 상향이 곧 배포판 변경·allowlist 비움의 위험 창). **레인 동결, 판정 SHA = 이 갱신 커밋 → 표적 재검증 2**(술어 변경). 장부 표적 하나: commands.md 의 「app ID 는 트리별로 재현된다」는 따뜻한 layer 캐시 아래 관측이라 재현성 주장이 미입증(팀장 지시 2026-10-09 는 「판정한 바이트」로만) — 등재 후 종결 전 정정 | 팀장 |

## 계약 갱신 r8 (2026-10-09, 팀장 — 표적 재검증 2 수령 · 종결 일괄)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-10** | **verifier 표적 재검증 2 `ready-for-review` @`a7dc7735`**(`_workspace/m6-6e2b/08_verifier_targeted2.md`: container job·`check` exit 0, 새 DB 로 52/15 applied · 0 unlisted; M-2·M-3·L-1·L-2 닫힘, M-1 좁힘 확인, 회귀 0). medium 하나 **M-A**(trivy 가 `$HOME/.trivy/modules` 의 WASM 모듈을 기본으로 읽는다 — 모듈은 post-scan 으로 결과를 고칠 수 있어 만료·stale 없는 면제 축; 로드 경로 실측, end-to-end 미측정) · 장부 L-A(이미지 ID 재현성 주장 과대) | 팀장 |
| **D-6E2B-11** | **종결 일괄**: M-A `--module-dir` 를 샌드박스의 빈 디렉터리로 고정 + 음성 대조(HOME 에 쓰레기 모듈 → 판정 불변; 대조군: 고정을 뺀 변이에서 로드됨) — 게이트 술어 변경이라 **M-A 한정 표적 재검증** · 레인이 보관한 장부 넷(이미지 ID 문면 → 「이 회차가 판정한 바이트」, 재현성 주장 철회 = L-A · rollback.md 에 `milestone-6.md` 공유 파일 hunk 절차(실행 시 산출) · 등식 문장 「복원 인자 ∪ hunk 처리 공유 파일」 · 「공유 파일에 다른 slice 줄 없음」 문장을 그 전환 조건대로 정정). milestone-6.md 착수·종결 문단은 팀장이 일괄 뒤 쓴다 | 팀장 |

## 계약 갱신 r9 (2026-10-09, 팀장 — 종결 일괄·main 병합 수령 · 표적 재검증 3)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-12** | 종결 일괄 수령(`_workspace/m6-6e2b/09_implementer_closure.md`): M-A `--module-dir` 샌드박스 고정(image 만 그 옵션을 받고 sbom 은 모듈을 읽지 않음 실측, 대조군 N14) · 장부 넷 · **origin/main(`b6d31b87`, PR #66) 병합 `67d3e787`**(runbook 충돌 둘, 두 slice 문면 보존). **rollback 절차 변경 수령**: 팀장이 지시한 공유 파일 hunk 역적용은 **실측에서 완료되지 않았다**(인접 삽입 지점 충돌 + 병합 커밋의 충돌 해소는 `--no-merges` 목록 밖 → 이 slice 의 줄 넷·§8 잔존, 그런데 ①~⑥ 은 전부 exit 0 — 「내 줄이 사라졌는가」 계수만이 잡았다). 대체: 공유 파일(runbook·milestone-6.md)을 **`origin/main` 에서 복원**(전제: main 에 6E-2b 가 없음; 머지 뒤 되돌림은 PR revert) — 재실측 HEAD `e6f15b74`, 이 slice 표지 0 · 6E-2a 표지 보존 · runbook 이 main 과 바이트 동일. 자기 경로 집합은 `^origin/main` non-merge 커밋으로 산출. 레인 동결, 판정 SHA = 이 갱신 커밋 → **표적 재검증 3**(M-A · 병합 결과 · 새 rollback 절차) | 팀장 |

## 계약 갱신 r10 (2026-10-09, 팀장 — 표적 재검증 3 수령 · 장부 일괄)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-13** | **verifier 표적 재검증 3 `ready-for-review` @`c3aff379`**(`_workspace/m6-6e2b/10_verifier_targeted3.md`: container job·`check`·되돌린 트리 `check` exit 0 · M-A 닫힘(sbom·version 은 모듈 미독, 게이트의 trivy 호출 셋) · 병합 깨끗(runbook 외 전부 한 부모와 바이트 동일) · rollback 절차 실행: 이 slice 표지 0, 6E-2a 표지 보존). 장부 medium **LR-1**: 공유 파일 복원이 **움직이는 ref `origin/main`** 을 믿는다 — fetch 안 된(낡은) ref 면 exit 0 으로 6E-2a 줄을 지운다(실측) · L-2: 그 사이 다른 slice 가 main 에 머지됐으면 그 slice 의 문서 줄만 코드 없이 들어온다(실측) · L-1: 「verifier 가 대조할 것」 블록이 옛 실측 HEAD·경로 여섯 | 팀장 |
| **D-6E2B-14** | **장부 일괄(레인)**: 복원 원천을 움직이는 ref 가 아니라 **이 브랜치에 마지막으로 병합한 main 커밋**(오늘 `b6d31b87`)으로 고정하고 그 SHA 를 rollback.md 에 적는다 — LR-1·L-2 를 함께 닫는다(낡은 ref 로 지우지도, 나중 slice 의 줄을 들이지도 않는다). 원천 SHA 가 브랜치의 조상이고 1bdd7b79 을 포함하지 않음을 실행 전 단언으로 · L-1 블록을 `e6f15b74`·대상 일곱으로 · rollback ①~⑥ 재실측(계수 포함). 하네스 PR #67 문면도 같은 방향으로 팀장이 갱신 | 팀장 |

## 계약 갱신 r11 (2026-10-09, 팀장 — 장부 일괄 수령 · 종결)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-15** | 장부 일괄 수령(레인 HEAD `c9cc2103`, rollback 실측 HEAD `c81f7d77`): 공유 파일 복원 원천을 `b6d31b87` 로 고정 + 실행 전 단언 둘(핀이 HEAD 의 조상 · 핀이 이 slice 의 **첫** 산출물 커밋 `e2545ea5` 를 포함하지 않음 — 계약 문면의 `1bdd7b79` 보다 강한 술어로 레인이 정정) · 이 slice 표지 0 · 6E-2a 표지 보존 · 핀과 바이트 동일 · L-1 블록 갱신. 산출물 무변경 · `check` exit 0. **종결 조건 충족**: verifier 표적 3 ready-for-review(+ 장부 일괄) — **사용자 승인(머지)만 남는다**. Codex 없음. 재작업 1/5. milestone-6.md 착수·종결 문단은 이 갱신과 같은 커밋 | 팀장 |

## 계약 갱신 r12 (2026-10-09, 팀장 — PR #68 `/code-review` 수령·처분 · 조치 라운드)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-16** | **PR #68 `/code-review`**(리뷰어 다섯 → 후보 20 → 채점 80+ 하나 게시: rollback 실측 HEAD `c81f7d77` 뒤 팀장 종결 커밋이 `milestone-6.md` 를 움직여 미검증). 문턱 아래(75 이하)지만 실재해 이 라운드에서 처분: **산출물** — (A) `printf … \| grep -q` 가 `pipefail` 아래 파이프 버퍼를 넘으면 일치를 불일치로 읽는다(중복 키 검사는 열리는 쪽; ci.yml 이 같은 결함을 이미 한 번 고쳤다) · (F) `--ignorefile` 이 `trivy image` 호출에 없다 · (H) S-22e 조건이 앞선 위생 게이트·거부 스모크 실패를 보지 않는다 · (M) `install trivy` 가 S-21 앞이라 설치 실패가 무관한 container 게이트 전부를 건너뛰게 한다 — **(A)(F) 는 게이트 술어 변경이라 표적 재검증** · **문서·장부** — rollback.md 의 「`base..HEAD` 기계 산출」 문면(병합 뒤 6E-2a 경로까지 냄) · scope.md rollback 절의 폐기된 hunk 절차 · 하네스 레인 절(병합으로 들어온 6E-2a 하네스 커밋 셋 + 이 slice 가 낳은 PR #67·#69) · runbook §8.2 exit 2 전수 · §8.4.1 겹 수 · §6 신설 OPEN 다섯 등재 + 서명·push 축 OPEN ID · checklist 머리 HEAD · app 계수 불일치 · exit 1 처방 라우팅(§8.2→§8.5) · CRLF 주석 · `TRIVY_*` 자기 변수 주석 · Dockerfile uv 「두 축」 주석(이미지는 다이제스트, CI 는 태그) · **알려진 제한** — V-5 다이제스트 핀을 잠그는 술어 없음 · DB 다운로드 재시도 없음과 그 처방이 게이트 잠금으로 막혀 있음. milestone CRITICAL 계수(10)는 팀장이 이 커밋에서 정정 | 팀장 |

## 계약 갱신 r13 (2026-10-09, 팀장 — 조치 라운드 수령·동결 · 표적 재검증 4)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-17** | PR #68 조치 라운드 수령(`_workspace/m6-6e2b/11_implementer_pr68.md`, 레인 HEAD `005c5fdb`, 마지막 산출물 커밋 = rollback 실측 HEAD `fc8f373c` — 팀장 milestone 정정 뒤). (A) 실재 확인: 고치기 전 부모 커밋은 큰 allowlist 의 중복 키를 0 으로, 고친 코드는 1 로 보고 · (F) 더 무거웠다: `image` 호출에 ignorefile 이 없으면 SBOM 자체가 CRITICAL 여섯을 잃고 판정이 그 SBOM 을 읽어 전파 — cwd 잠금 하나가 덮고 있었다 · (H)(M) ci.yml. 레인 자기 정정 셋(대조군이 측정 대상을 못 봄 ×2 · 표지 과광역 ×1) — 대조군은 **실제 부모 커밋**, 정확 축은 바이트 동일로. **레인 동결, 판정 SHA = 이 갱신 커밋 → 표적 재검증 4**(A·F 술어 변경 · H·M CI 조건 · rollback 재실측 유효성) | 팀장 |

## 계약 갱신 r14 (2026-10-09, 팀장 — 표적 재검증 4 수령 · 승인 전 일괄)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-18** | **verifier 표적 재검증 4 `ready-for-review` @`709dd11c`**(`_workspace/m6-6e2b/12_verifier_targeted4.md`: (A) 실제 부모 커밋은 239KB allowlist 의 중복을 놓쳐 exit 0, 판정 SHA 는 exit 2 · (F) 대조군 확인 · (H) 조건 평가기로 확인 · 문서 · rollback 유효). medium **M-1**(fail-closed, 주장 vs 거동): 「설치 실패에도 S-23~S-25 는 돈다」는 거짓 — 그 step 들은 `if` 가 없어 여전히 건너뛰고, 설치 실패 시 S-22e·S-22f 가 돌아 원인 하나에 빨강 셋 · L-1 runbook §8.2 「넷」 문면과 jq 실패(exit 5) 미기재 · L-A F 사유 문장 과대(유출은 보관 SBOM 에만, 판정은 50 그대로). **처분(레인, 승인 전 일괄)**: S-22e·S-22f 조건에 `install trivy` 성공을 더해 빨강을 하나로 · ci.yml 주석과 보고를 실제 거동으로 좁힘(설치 실패 시 S-23~S-25 는 건너뛴다 — 6E-2b 이전과 같은 결합이고, 얻은 것은 S-21~S-22c) · §8.2 「넷」 문면 정정 + jq 읽기를 판정 불가 경로로 감싸거나 「그 밖의 비-0 = 판정 불가」 명기 · F 사유 문장 정정. 조건 평가기로 레인이 실측, verifier 재검증 없이 수령(조건 변경은 빨강 수를 줄이는 쪽 · 판정 술어 무변경) | 팀장 |

## 계약 갱신 r15 (2026-10-09, 팀장 — 승인 전 일괄 수령 · 종결 재확정)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-19** | 승인 전 일괄 수령(`_workspace/m6-6e2b/13_implementer_batch.md`, 마지막 산출물 커밋 = rollback 실측 HEAD `38fe42bb`, 레인 HEAD `4ebb1947`): M-1 S-22e·S-22f 조건에 설치 성공 — 조건 평가 실측(설치 실패 → 빨강 하나 · S-22d 실패 → S-22e 그대로 · S-22b 실패 → 뒤 전부 skip), 주석을 「살리는 것은 S-21~S-22c」로 좁힘 · L-1 jq 판독 넷을 판정 불가로(부모 5 → 2), §8.2 「그 밖의 비-0 도 판정 불가」 · L-A F 사유 정정(유출은 보관 SBOM). container job · `check` · rollback ①~⑥ exit 0, 유효성 빈 출력. **종결 재확정** — verifier 표적 4 ready-for-review + 일괄; **사용자 승인(머지)만 남는다**. 재작업 1/5 | 팀장 |
