# M6/6E-2c — 실행 명령과 종료 코드

base `e2232a75` · 브랜치 `m6-6e2c/2026-10-09`. 출력 전문은 싣지 않는다(핵심 결과 한 줄) — 감사자는
명령을 다시 돌린다. **마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니라 verifier 리포트와 PR 조치
코멘트**다(evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다).

측정 체인은 **시작과 끝에서 HEAD 를 찍어 같음을 단언**한다. `trivy` 는 CI `install trivy` step 과 같은
판(정책 핀 `tool.trivy.version`)을 PATH 에 얹어 부른다 — 저장소에 도구 바이너리를 두지 않는다.

## E-1 — OS 매칭 술어 (D-6E2C-1)

### 2026-10-09 · 상향 **전** 현 값에서 초록 (HEAD `b5d8bdfc` 양끝 단언)
- cmd: `tools/vuln-scan-check.sh bidvector/{ml-serving,app}:local {ml-serving,app} config/quality/vuln-policy.properties`
- exit: 0 · 0
- 핵심 결과: `os=debian/12.9(정책 선언 debian/12.9) os-pkgs-results=1` · `os=ubuntu/24.04(정책 선언 ubuntu/24.04) os-pkgs-results=1`, 미등재 0 둘 다.

### 2026-10-09 · 음성 대조 넷 — 전부 **바꿔치우기**, 대조군은 실제 부모 커밋 `b5d8bdfc`
- cmd: ① 정책 name `12.9`→`12.8` ② 정책 family `debian`→`ubuntu` (둘 다 `git diff --numstat` 이 `1 1`) ③ shim 이 결과 JSON 의 `os-pkgs` Result 의 `.Class` 를 `lang-pkgs` 로 치환 ④ shim 이 `.Metadata.OS` 를 삭제
- exit: 2 · 2 · 2 · 2
- 핵심 결과: ①② 는 축 ⓐ(배포판 정확 일치)가, ③ 은 축 ⓑ(`Class=os-pkgs, Type=debian` Result 실재)가, ④ 는 ⓐ 의 메타데이터 부재 갈래가 각각 **서로 다른 문면**으로 끊었다.
- 설계 주석: ③ 은 Result 를 **지우지 않고 `.Class` 만 치환**한다 — 통째로 지우면 나열 패키지 수가 하한 아래로 떨어져 **앞선 양성 대조가 먼저** 끊어 ⓑ 가 섰는지 알 수 없다. 치환형이라 나열 수는 보존되고 ⓑ 가 홀로 선다.
- 알려진 제한: ③④ 의 첫 회차는 shim 이 하위 명령을 `$1` 로만 찾아(게이트가 `--config` 를 앞에 붙인다) 필터가 돌지 않은 채 exit 0 이었다. 그 회차는 **술어의 음성 대조가 아니라 shim 의 무효 대조**이고, 고친 뒤 위 결과를 얻었다.

## E-2 — 베이스 두 개 상향 (D-6E2C-2)

### 2026-10-09 · 다이제스트 독립 확인
- cmd: `docker buildx imagetools inspect python:3.12.15-slim-bookworm` · `… eclipse-temurin:21-jre-noble`
- exit: 0 · 0
- 핵심 결과: 두 index 다이제스트가 착수 조사가 적은 값과 일치(`34386ef0…a07258` · `000fd431…f9408c`, 둘 다 OCI image index).

### 2026-10-09 · **술어가 살아 있다는 증거** — 이미지만 올리고 정책은 그대로 (HEAD `a8242701` 양끝 단언)
- cmd: 새 베이스로 ml-serving 재빌드 → `tools/vuln-scan-check.sh bidvector/ml-serving:local ml-serving …`(정책은 아직 `12.9`)
- exit: 2
- 핵심 결과: 「스캔이 읽은 배포판(debian/12.15)이 정책 선언(debian/12.9…)과 다르다」 — 문면이 고쳐야 할 키 둘을 지목한다. 베이스 상향이 정책 편집을 **강제**한다.

### 2026-10-09 · 상향 뒤 위생 게이트 둘 (HEAD `a8242701` 양끝 단언)
- cmd: `tools/image-hygiene-check.sh bidvector/{ml-serving,app}:local config/quality/image-hygiene-policy{,-app}.properties`
- exit: 0 · 0
- 핵심 결과: `base-layer-접두-일치=true` 둘 다 · 금지 실행 파일 9 · 필수 실행 파일 1(`curl` 새 index 에도 존재) · 의존 layer 98(하한 50) · 크기 343,090,786 / 346,573,243(상한 400M / 450M).

### 2026-10-09 · 상향 직후 ml-serving 취약점 게이트
- cmd: `tools/vuln-scan-check.sh bidvector/ml-serving:local ml-serving …`(정책 `12.15`)
- exit: 1
- 핵심 결과: `os=debian/12.15(정책 선언 debian/12.15)` · 차단 후보 **52 → 0** · findings_total 468 → 284 · 나열 패키지 115 그대로 · 등재 52건이 전부 **stale** 로 붉다(E-4 가 걷는다).
- **정정(verifier r1 L-a)**: 앞 판은 여기에 「OS 매칭 술어가 그것이 **진짜 해소임을 증명**한다」고 적었는데 **과잉 주장**이다. 술어가 보는 것은 「선언과 실제가 같은가」뿐이고 「DB 가 그 배포판을 덮는가」는 보지 않는다(`OPEN-6E2C-OS-DB-COVERAGE`). 이 회차가 진짜 해소인 근거는 술어가 아니라 **수정판이 실제로 들어왔다는 사실**(등재 52건이 전부 stale 로 붉어진 것)이고, 「나열 수 불변 + findings 급감」 조합은 여전히 **사람이 보는 신호**다(runbook §8.2).

## E-3 — JVM 보안 하한 (D-6E2C-3)

### 2026-10-09 · 좌표 실재 확인
- cmd: Maven Central 의 `jackson-bom` 2.21.7 · `tools/jackson/jackson-bom` 3.1.7 · `tomcat-embed-core` 11.0.26 POM 조회
- exit: 0 (HTTP 200 셋)
- 핵심 결과: 세 좌표가 전부 실재 — 카탈로그에 적기 전에 확인했다.

### 2026-10-09 · 하한이 해석에 닿는다 (HEAD `9a71e668` 양끝 단언)
- cmd: `./gradlew --no-daemon :app:test --tests 'bidvector.app.packaging.*'`
- exit: 0
- 핵심 결과: 배포물 `BOOT-INF/lib` 가 tomcat-embed 셋 **11.0.24 → 11.0.26** · jackson2 **2.21.5 → 2.21.7**(dataformat-yaml 포함) · jackson3 **3.1.5 → 3.1.7**.

### 2026-10-09 · 변이 둘 — 전부 **바꿔치우기**, 대조군은 실제 부모 커밋 `398398eb`
- cmd: ⓐ `constraints { … }` 안의 tomcat 세 줄 제거(`git diff --numstat` `0 3`) ⓑ `platform(libs.jackson2.bom)` 한 줄 제거(`0 1`) — 각각 재빌드 후 같은 test
- exit: 1 · 1
- 핵심 결과: ⓐ `expected:<["11.0.26"]> but was:<["11.0.24"]>` ⓑ `expected:<["2.21.7", "3.1.7"]> but was:<["3.1.7", "2.21.5"]>`. 둘 다 **그 축의 test 만** 붉었고 복원 뒤 `git diff --numstat` 이 빈 출력, HEAD 불변.
- 알려진 제한: 기대값이 카탈로그에서 오므로 **카탈로그 값을 내리면 이 test 는 초록인 채로 하한이 내려간다**. 그 축을 지는 것은 취약점 게이트다(그 CVE 가 되살아나 exit 1). 이 test 가 지는 것은 「선언이 효과를 냈는가」 하나다.

## E-4 — allowlist 비움 + 잔여 triage (D-6E2C-4)

### 2026-10-09 · 상향 뒤 두 이미지 재스캔, 등재 0 (HEAD `c90f6c77` 양끝 단언)
- cmd: 두 이미지 재빌드(`:app:bootJar` → `docker build` 둘) 뒤 `tools/vuln-scan-check.sh` 를 kind 별로
- exit: 0 · 0
- 핵심 결과: `allowlist_전체=0` 로 **둘 다 통과** — ml-serving 차단 후보 52 → 0(findings_total 468 → 284) · 앱 15 → 0(50 → 19). 양성 대조 넷(116/115 · 226/225)은 하한(80/80 · 150/150) 위 그대로라 `scan.min-*` 은 손대지 않는다.
- 주의(측정 규율): 변이 라운드가 남긴 배포물은 **변이 상태로 빌드돼 있다**(`jackson-databind-2.21.5`). 이미지 빌드 전에 복원된 트리에서 `bootJar` 를 다시 떠 그 사실을 확인했다 — 소스 복원만으로는 산출물이 복원되지 않는다.

### 2026-10-09 · 잔여 finding — **등재가 필요한 것은 0**
- cmd: 두 스캔 결과를 severity × 수정판 유무로 집계
- exit: 0
- 핵심 결과: ml-serving CRITICAL 2 · HIGH 55 가 남지만 **수정판이 있는 것은 0**(`block.only-fixed=true` — 고칠 길 없는 것으로 붉히면 상시 붉은 게이트가 된다). 그 밖에 ml MEDIUM 5 · LOW 1, 앱 MEDIUM 2 가 수정판을 갖지만 차단 문턱(HIGH,CRITICAL) 아래다. 앱에는 HIGH/CRITICAL 이 **한 건도** 없다.

## acceptance — 판정 SHA `a4d978ec` (양끝 HEAD 단언)

### 2026-10-10 · Kotlin `check` · `qualityBaseline`
- cmd: `./gradlew --no-daemon check` · `./gradlew --no-daemon qualityBaseline`
- exit: 0 · 0 (14s · 6s)
- 핵심 결과: 350 actionable 중 31 executed · 1 from cache · 318 up-to-date — **전건 재실행이 아니다**. 입력이 바뀐 task 만 돌았고(앞 회차에서 `:leakPatternGate` 가 그래서 다시 돌아 통과), test task 는 입력이 바이트 동일이라 up-to-date 다. **독립 전건 실행은 verifier 몫**이다.

### 2026-10-10 · CI `container` job 로컬 재현 (HEAD `04123b8c`, CJ-HEAD 양끝 단언)
- cmd: 워크플로의 `jobs.container.steps[*].run` 을 **ci.yml 에서 직접 읽어** 순서대로 실행(손으로 옮겨 적지 않는다). `uses:` step 넷은 러너 전용이라 제외.
- exit: **16 step 전부 0** (총 ~2분 30초)
- 핵심 결과: 이미지 둘 재빌드 — ml-serving `sha256:911a9026…1e9c01` · app `sha256:4a3ad368…5cfbb4`. S-23 이 12s 에 healthy 수렴(베이스 상향이 깰 수 있던 자리 — compose healthcheck 이 이미지 안 `curl` 을 쓴다) · S-24 가 새 ml-serving 이미지로 통과(python 3.12 마이너 유지라 lock 재생성이 필요 없다는 판단의 실측).
- **`a4d978ec` 에서 재실행하지 않은 근거**: `04123b8c..a4d978ec` 의 차이는 `config/quality/vuln-policy.properties` **한 파일**이고 그 내용은 `policy.version` 한 줄과 주석 여섯 줄뿐이다. 그 키를 읽는 게이트는 **없다** — `grep -rn 'policy\.version' tools/ .github/workflows/` 0건, build-logic 의 참조 넷은 전부 다른 정책 파일 자리다(`SizeGateTask`→size-policy · `MemberEffectClassification`→member-effects · `QualityPolicy` KDoc). 상향 뒤 취약점 게이트를 한 번 더 돌려 exit 0 을 확인했다. **최종 판정은 verifier 가 한다.**

### 2026-10-10 · 비밀값 스캔 (참조형)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m6/6e2c/`
- exit: 0 (매치 있음)
- 핵심 결과: 매치는 `tools/vuln-scan-check.sh` 의 **trivy 스캐너 열거값 한 줄**뿐이고 6E-2b 가 쓴 도구 어휘다. 이 slice 가 더했던 매치 다섯 줄은 정책 값 종류 이름을 `ident` 로 바꿔 **0** 이 됐다(그 개명이 그 목적이었다). `leakPatternGate` 의 scanRoot 는 `reports/evidence/` 뿐이라 CI 판정 대상은 evidence 쪽이고, 그쪽 매치는 0 이다.
