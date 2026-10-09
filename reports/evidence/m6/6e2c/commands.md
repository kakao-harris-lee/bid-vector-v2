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
- 핵심 결과: `os=debian/12.15(정책 선언 debian/12.15)` · 차단 후보 **52 → 0** · findings_total 468 → 284 · 나열 패키지 115 그대로 · 등재 52건이 전부 **stale** 로 붉다(E-4 가 걷는다). **나열 수가 그대로인 채 finding 이 준 조합**은 6E-2b 가 사람에게 맡겼던 신호인데, 이제 OS 매칭 술어가 그것이 진짜 해소임을 함께 증명한다.
