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

신설 파일 제거 · `docker/ml-serving.Dockerfile` 은 `git restore --source=2deb5f9d` · 공유 파일(`ci.yml`·runbook·`milestone-6.md`)은 커밋 해시 hunk 역적용(실행 시 산출, 최신부터). 임시 clone 에서 ①~⑥ 실측(⑥ 게이트 = container job 재현).

## 하네스 레인 변경 (상시)

- (착수 시점 없음)

## 계약 갱신 r1 (2026-10-08, 팀장 — triage 결정)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-1** | 오늘의 수정 가능 HIGH/CRITICAL 67건(베이스 이미지 54 · jackson 10 · tomcat-embed-core CRITICAL 3)의 **상향은 후속 slice 6E-2c**(베이스 다이제스트 + tomcat·jackson 버전, `OPEN-6E2B-BASE-IMAGE-BUMP`·`OPEN-6E2B-DEPENDENCY-BUMP`). 이 slice 는 등재로 두되 **CRITICAL 등재 만료를 2026-10-31 로 단축**(HIGH 는 2026-12-31) — 6E-2c 가 늦어지면 게이트가 스스로 붉어진다 | 사용자 |

## 계약 갱신 r2 (2026-10-09, 팀장 — 구현 수령·동결 · 판정 착수)

| ID | 결정 | 출처 |
|---|---|---|
| **D-6E2B-2** | 구현 레인 완료 보고 수령(`_workspace/m6-6e2b/02_implementer_report.md`, 레인 HEAD `95e738f7`) → **레인 동결**. 판정 대상 SHA = 이 계약 갱신 커밋. 레인 실측: container job 재현 · `check` · rollback ①~⑥ @`c4f22dd1` exit 0, `check` @`95e738f7` exit 0, 음성 대조 여섯 RED, 양성 1회. 판정 레인: verifier(opus) + code-reviewer(sonnet) 병렬 | 팀장 |
