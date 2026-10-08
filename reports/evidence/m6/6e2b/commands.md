# M6/6E-2b — 실행 명령과 종료 코드

base `2deb5f9d` · 브랜치 `m6-6e2b/2026-10-08`. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는
명령을 다시 돌린다. **마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니라 verifier 리포트와 PR 조치
코멘트**다(evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

도구 실행 경로: `trivy` 는 CI `install trivy` step 과 같은 절차로 `$RUNNER_TEMP/bin` 에 깔고 PATH 에
얹는다(저장소에 도구 바이너리를 두지 않는다).

## 도구 채택 — 어느 판을 왜 골랐나 (B-2)

### 2026-10-08T14:10Z
- cmd: `curl -fsSL https://api.github.com/repos/aquasecurity/trivy/releases?per_page=12 | jq -r '.[]|"\(.tag_name)\t\(.published_at)"'`
- exit: 0
- 핵심 결과: v0.69.3(2026-03-03) 다음이 v0.70.0(2026-04-17) — 사고판 세 태그는 저장소에서 사라졌고, 최신은 v0.75.0(2026-10-01).

### 2026-10-08T14:10Z
- cmd: `curl -fsSL .../releases/download/v0.75.0/{trivy_0.75.0_Linux-64bit.tar.gz,trivy_0.75.0_checksums.txt}` 뒤 `sha256sum -c`
- exit: 0
- 핵심 결과: 자산 해시가 공식 목록과 일치 — 이 값을 `tool.trivy.sha256` 에 핀했다(이후 대조 기준은 릴리스 목록이 아니라 이 핀이다).

### 2026-10-08T14:10Z
- cmd: `jq -r '.verificationMaterial.certificate.rawBytes' trivy_0.75.0_checksums.txt.sigstore.json | base64 -d | openssl x509 -inform der -noout -text`
- exit: 0
- 핵심 결과: 발급자 GitHub Actions OIDC · 소스 `aquasecurity/trivy` · ref `refs/tags/v0.75.0` · 빌드 커밋이 태그 커밋(독립 조회)과 일치 · 인증서 유효창 2026-10-01T13:32~13:42Z — 권고가 적은 노출 창(2026-03-19~23) 어디에도 들지 않는다.
- 알려진 제한: cosign 부재로 **서명 자체의 암호학적 검증은 돌리지 않았다**(인증서 문면 확인까지). 구속력 있는 잠금은 체크섬 핀이다.

## 설계 실측 — SBOM 과 스캔이 같은 입력임을 재는 근거

### 2026-10-08T14:20Z
- cmd: `trivy image --format cyclonedx` 로 만든 SBOM 을 `trivy sbom --format json` 으로 스캔한 집합 대 `trivy image --format json` 집합을 `diff`
- exit: 0
- 핵심 결과: 두 이미지 모두 `(ID, 패키지, severity, 수정판)` 집합이 **동일** — SBOM 경유 스캔이 직접 스캔을 잃지 않는다(그래서 게이트는 SBOM 을 입력으로 쓴다).

## acceptance ① — CI `container` job 로컬 재현 (S-21~S-25 + 새 step)

워크플로의 `run` step 을 **문면 그대로** 순서대로 실행했다(`actions/*` step 은 러너 전용이라 제외).
아래 수치는 **HEAD `c4f22dd1` 에 트리를 고정하고 돌린 회차**의 것이다. 앞 회차는 실행 **도중에**
산출물 커밋이 셋 붙어(계약 갱신 r1 포함) 「어느 트리를 쟀는가」가 사라졌으므로 폐기했다 — 측정은
시작 시점의 HEAD 를 읽는 것만으로는 모자라고 **그 사이 트리가 움직이지 않았음**까지 성립해야 한다.

### 2026-10-09 (HEAD `c4f22dd1`)
- cmd: `install trivy` step(정책에서 버전·자산·체크섬 판독 → 핀 대조 → `$RUNNER_TEMP/bin` 설치 → 절대 경로 `--version`)
- exit: 0
- 핵심 결과: 핀 대조 `OK`, 설치본이 0.75.0.

### 2026-10-09 (HEAD `c4f22dd1`)
- cmd: `docker build -f docker/ml-serving.Dockerfile -t bidvector/ml-serving:local .` (S-21)
- exit: 0
- 핵심 결과: 빌드 로그가 `ghcr.io/astral-sh/uv:0.9.22@sha256:2320e6c2…` 로 메타데이터를 읽는다 — 다이제스트 참조가 실제로 해석됐다.

### 2026-10-09 (HEAD `c4f22dd1`)
- cmd: `./gradlew --no-daemon :app:bootJar` (S-21a) · `docker build -f docker/app.Dockerfile -t bidvector/app:local .` (S-21b)
- exit: 0 · 0
- 핵심 결과: 앱 배포물·이미지 생성.

### 2026-10-09 (HEAD `c4f22dd1`)
- cmd: `./tools/image-hygiene-check.sh` 두 벌 (S-22a·S-22b) · 앱 거부 스모크 (S-22c)
- exit: 0 · 0 · 0
- 핵심 결과: uv 참조 변경이 위생 축(태그·실 프로세스 uid·base layer 체인·크기·금지/필수)에 영향 없음.

### 2026-10-09 (HEAD `c4f22dd1`)
- cmd: `./tools/vuln-scan-check.sh bidvector/ml-serving:local ml-serving config/quality/vuln-policy.properties` (S-22d)
- exit: 0
- 핵심 결과: SBOM 구성요소 116(하한 80) · 전체 finding 454 · 차단 후보 52 · 이 kind 적용 52 · 미등재 0.

### 2026-10-09 (HEAD `c4f22dd1`)
- cmd: `./tools/vuln-scan-check.sh bidvector/app:local app config/quality/vuln-policy.properties` (S-22e)
- exit: 0
- 핵심 결과: SBOM 구성요소 225(하한 150) · 전체 finding 50 · 차단 후보 15 · 이 kind 적용 15 · 미등재 0.

### S-22f(SBOM 보관)
- 로컬 재현 불가(`actions/upload-artifact` 는 러너 전용). 대신 두 가지를 쟀다 — ⓐ 워크플로가 YAML 로
  파싱되고 `container` job 의 step 수가 기대값과 같다 ⓑ 그 step 의 `path` glob 이 실제로 두 파일에
  맞는다(S-22d·S-22e 가 만든 SBOM 둘). `if-no-files-found: error` 라 빈 artifact 는 초록이 되지 않는다.

## acceptance ② — Kotlin `check` job

### 2026-10-09 (HEAD `c4f22dd1`)
- cmd: `./gradlew --no-daemon check`
- exit: **1** — `:app:test` 가 `java.io.EOFException`(테스트 워커 통신 끊김).
- 원인: **이 레인의 실행 실수**다. 호스트 슬롯을 기다리는 grabber 를 둘 겹쳐 띄워 둔 탓에 같은
  워크트리에서 `check` 두 벌이 동시에 돌았고, 중복을 정리하며 보낸 종료 신호가 상대 빌드의 워커를
  끊었다. 산출물 결함이 아니라는 근거 둘 — ⓐ 실패 시점에 test 리포트가 생성조차 안 됐다(테스트가
  떨어진 게 아니라 워커가 사라졌다) ⓑ 같은 커밋을 되돌린 clone 에서 돈 전건 `check` 는 같은 시간대에
  exit 0 이다(rollback.md ⑤⑥).

### 2026-10-09 (같은 HEAD `c4f22dd1`, 단일 빌드로 재실행)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 전건 통과(`leakPatternGate` 포함 — 이 slice 의 evidence 가 스캔 대상에 들어간다).
- 주의: **마지막 evidence 커밋 뒤의 결과는 이 파일에 담길 수 없다**(그 줄을 적으려면 커밋이 하나 더
  필요하고 그 커밋이 다시 같은 줄을 요구한다). 마지막 HEAD 의 정본은 verifier 와 PR 조치 코멘트다.

## 음성 대조 — 바꿔치우기 변이 (전부 커밋 뒤에 걸고, 복원은 사본 덮어쓰기)

변이마다 ⓐ 걸기 전 `git status --porcelain -- <in_scope 개별 인자>` 가 빈 출력임을 확인하고 ⓑ
`git diff --numstat` 으로 **실제로 바뀌었음**을 수치로 확인한 뒤 돌렸다. 더하기만 하는 변이는 쓰지
않는다(그런 변이는 초록이 나와도 아무것도 재지 못한다). 복원 뒤 `numstat` 빈 출력을 매번 확인했다.

| # | 무엇을 바꿔치웠나 | numstat | exit | 게이트가 낸 사유 |
|---|---|---|---|---|
| ① | `block.severities` 를 `CRITICAL` 만으로 | `1 1` | 1 | HIGH 등재 12건이 **stale** — 문턱을 좁히면 그 바깥을 덮던 등재가 즉시 붉어진다 |
| ② | 등재 한 줄 삭제(app·openssl) | `0 1` | 1 | 그 finding 이 **미등재 차단 대상** 1건으로 목록에 뜬다 |
| ③ | 그 등재의 만료일을 어제로 | `1 1` | 1 | `만료됐다(2026-10-07 < 2026-10-08)` |
| ③b | 그 등재의 만료일을 상한 밖(2027-06-30)으로 | `1 1` | 1 | `만료일이 상한(90일)보다 멀다` |
| ④ | 그 등재의 취약점 ID 를 실재하지 않는 것으로 | `1 1` | 1 | **stale** 로 그 줄을 지목하고, 덮이지 않게 된 실제 finding 도 미등재로 함께 뜬다 |
| ⑤ | 정책의 `tool.trivy.sha256` 첫 글자 | `1 1` | 1 | 설치 step 이 `FAILED`/`computed checksum did NOT match` — `$RUNNER_TEMP/bin` 이 빈 채로 끝난다 |
| ⑥ | 정책의 `tool.trivy.version` 을 도는 판과 다르게 | `1 1` | 2 | `도는 trivy(0.75.0)가 정책이 핀한 판(0.74.0)과 다르다` — 판정 자신이 핀을 확인한다 |

### 실행 중 잡은 결함 하나 (변이가 아니라 구현의 것)
초판의 줄 수 계산이 `printf '%s'`(개행 없음) + `wc -l` 이라 **마지막 줄이 빠졌다**. 차단 대상이 정확히
1건일 때 `0` 으로 읽혀 게이트가 조용히 통과하는 방향의 off-by-one 이다. 요약의 「차단 후보 52 대 적용
52」가 「51 대 52」로 어긋나 드러났다 — 수를 **두 자리에서 따로 세어 맞춰 보는** 요약 줄이 잡았다.
`_count_lines`(개행을 붙여 세고 빈 문자열에 0)로 바꾼 뒤 두 수가 맞는다.

## 양성 대조 — 알려진 취약 공개 이미지(오래된 다이제스트) 스캔

### 2026-10-08
- cmd: `trivy image --scanners vuln --pkg-types os,library --format json` on `debian@sha256:bb3dc79f…`(=`debian:10-slim` index 다이제스트, EOL)
- exit: 0
- 핵심 결과: finding **67건**(비지 않음) · 게이트와 같은 술어(HIGH·CRITICAL + 수정본 있음)로 좁혀도 **2건**.

### 2026-10-08
- cmd: `./tools/vuln-scan-check.sh debian@sha256:bb3dc79f… ml-serving config/quality/vuln-policy.properties`
- exit: 1
- 핵심 결과: 게이트가 끝까지 돌아 **미등재 2건**(`CVE-2024-33599|libc-bin`·`libc6`)을 목록으로 내고,
  이 이미지에 맞지 않는 ml-serving 등재 **52건 전부를 stale 로** 지목한다(총 53건). SBOM 구성요소 85 로
  하한 80 은 넘겼다 — **하한을 kind 별로 두는 이유가 여기서 보인다**(다른 이미지를 같은 kind 로 재면
  구성요소 수가 하한 가까이로 내려간다).

## 계약 갱신 r1 반영 — CRITICAL 등재 만료 단축 (D-6E2B-1)

### 2026-10-08
- cmd: 오늘 triage 산출물에서 `severity == CRITICAL` 인 (ID, 패키지)를 뽑아 그 등재의 만료일만 치환
- 핵심 결과: **바뀐 줄 10 = CRITICAL finding 10**(손으로 고르지 않았음을 수치로 확인). HIGH 57 은 무변경.

### 2026-10-08
- cmd: `./tools/vuln-scan-check.sh` 두 이미지 재실행
- exit: 0 · 0
- 핵심 결과: 적용 52/15 · 미등재 0 · stale 0 — 만료 단축이 판정 집합을 바꾸지 않는다(만료는 날짜 축이고
  stale 은 finding 축이라 서로 독립임이 여기서 보인다).

### 2026-10-09 — 등재 사유 문면을 결정 뒤 상태로 (S-22d·S-22e 재실행)
- cmd: 등재 **67건 전부**의 사유에서 「운영자 결정 대기」를 「후속 slice 6E-2c 가 올린다 · 운영자 결정
  2026-10-08」로 치환한 뒤 두 게이트 재실행
- exit: 0 · 0
- 핵심 결과: 적용 52/15 · 미등재 0 · stale 0 무변경. **문면만 바뀌고 판정은 그대로**임을 같은 수치로 확인
  (사유는 게이트의 술어가 아니라 사람에게 남기는 처방이다 — 그래서 결정이 난 뒤 거짓이 되면 고쳐야 한다).

## 비밀값 스캔

### 2026-10-08
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/6e2b/`
- exit: 0 — **매치 둘이 나왔고 둘 다 이 slice 가 쓴 줄이 아니다.** ⓐ `ci.yml` 의 기존 「DB 접속 값
  생성」 step(이 slice 가 건드리지 않은 줄이고, 게이트의 스캔 뿌리는 `reports/evidence` 뿐이라 대상
  밖이다) ⓑ 팀장이 쓴 `scope.md`(게이트가 파일 이름으로 제외한다 — 4E 관례, 계약 문서는 육안 대상).
  **이 slice 가 새로 쓴 파일 전부에서 매치 0** 이라 baseline 에 더할 항목이 없다.
- 육안 확인: Telegram id·사업자 정보·실 자격 값 없음. 스캔 어휘는 이 파일에 축어로 적지 않는다.
