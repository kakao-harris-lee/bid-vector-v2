# commands — chore/caddy-upgrade-20261029

trivy 0.75.0(정책 핀 체크섬 `sha256sum -c` OK). 이미지는 다른 레인의 `:local` 덮어쓰기를 피해 빌드 직후
`:caddy-before`·`:caddy-after` 로 다시 태그해 판정했다(같은 이미지 id).

## 2026-10-29 — 경로를 가른 실측

- cmd: 상류 릴리스 목록(caddyserver/caddy) · 레지스트리 태그(library/caddy, golang, alpine) · proxy.golang.org 판 목록
- exit: 0
- 핵심 결과: caddy 최신 **v2.11.7** · `caddy:2.11.7` index `5e5286cb…`(2026-10-27) · golang `1.26.10-alpine3.23`
  `34414731…` · alpine `3.23.7` `ff7f0c72…` · go-jose/v4 v4.1.6 · x/net v0.60.0 있음

- cmd: 최신 index·고정 판의 `/usr/bin/caddy` sha256 · `apk list -I` · `go version -m`
- exit: 0
- 핵심 결과: 두 바이너리 같음(`678ade3b…`, go1.26.8, dep 147) · curl 8.22.0-r0 · brotli-libs 1.2.0-r0

- cmd: `alpine:3.23.7@ff7f0c72…` 안 `apk update && apk policy curl libcurl brotli-libs`
- exit: 0
- 핵심 결과: curl·libcurl **8.23.0-r0** · brotli-libs **1.2.1-r0**

## base `78d97c59` — 이미지 빌드와 게이트

- cmd: `~/.local/bin/bv-build.sh ./tools/local-tls-build.sh`
- exit: 0
- cmd: `./tools/vuln-scan-check.sh bidvector/{web,proxy}:caddy-before {web,proxy} config/quality/vuln-policy.properties`
- exit: **1** · **1**
- 핵심 결과: `findings_total=79 차단후보=9 allowlist_전체=20 이_kind_적용=9 미등재=0` · 실패 사유 stale 한 줄
  `allow|<kind>|CVE-2026-56857|stdlib` — **base 가 이미 붉다**

## 직접 빌드 — 결과 동일성

- cmd: golang 1.26.10 컨테이너에서 `go mod init caddy` · `go get caddy/v2@v2.11.7` · `go get go-jose/v4@v4.1.6 x/net@v0.60.0` · `go mod tidy`
- exit: 0
- 핵심 결과: `docker/caddy/go.mod`(`go 1.26.10`) · `go.sum` 587줄

- cmd: 이미지의 `/usr/bin/caddy` 를 꺼내 `go version -m` → 공식 바이너리와 dep 목록 diff · `caddy list-modules` diff
- exit: 0
- 핵심 결과: go1.26.10 · dep 147 = 147, 차이 **둘**(go-jose/v4 v4.1.5→v4.1.6 · x/net v0.59.0→v0.60.0) · `vcs.*` 넷(`vcs`·`vcs.revision`·`vcs.time`·`vcs.modified`)을 뺀 build 줄 동일 — 공식은 git 체크아웃에서 빌드해 VCS 정보를 싣는다, 거동 차이 0
  · list-modules 137줄 diff 0 · `caddy version` 공식과 같은 문자열 · `getcap` = `cap_net_bind_service=ep`

## 새 HEAD `b95fdf1e` — 빌드와 게이트

- cmd: `~/.local/bin/bv-build.sh ./tools/local-tls-build.sh`
- exit: 0
- 핵심 결과: `caddy 바이너리 sha256(web=proxy) ddcb81ca…` · 크기 web 66.3MB · proxy 65.9MB(base 66.3MB)

- cmd: `./tools/image-hygiene-check.sh bidvector/{web,proxy}:caddy-after config/quality/image-hygiene-policy-{web,proxy}.properties`
- exit: **0** · **0**
- 핵심 결과: `base-layer-접두-일치=true`(alpine 다이제스트) · 크기 상한 안
- 중간 실측: 첫 판(`COPY` 뒤 별도 `RUN setcap`, `-s -w` 없음)은 160MB 로 크기 상한 실패 → 한 layer 설치 + `-s -w`

- cmd: `./tools/vuln-scan-check.sh bidvector/{web,proxy}:caddy-after {web,proxy} config/quality/vuln-policy.properties`
- exit: allowlist 편집 전 **1**(등재 10줄 전부 stale) → 편집 뒤 **0** · **0**
- 핵심 결과: `os=alpine/3.23.7` · `findings_total=79 → 7` · `차단후보 9 → 0` · `allowlist_전체 20 → 0` · SBOM 구성요소 182

- cmd: 컨테이너 안 `apk list -I` · `id`
- 핵심 결과: curl·libcurl 8.23.0-r0 · brotli-libs 1.2.1-r0 · uid 10001

## 음성 대조

- cmd: 옛 이미지(`:caddy-before`) + 새 allowlist(0줄), OS 선언만 3.23.6 으로 둔 정책 사본
- exit: **1** · **1** — `미등재=9`
- cmd: 옛 이미지 + 새 정책 그대로
- exit: **2** — OS 선언 불일치(판정 불가)
- cmd: `docker/proxy.Dockerfile` 만 `-ldflags='-s -w'` 를 뺀 변이(커밋 뒤) → `local-tls-build.sh`
- exit: **1** — 「web·proxy 이미지의 caddy 바이너리가 다르다」. 변이 판 sha256 은 앞서 같은 플래그로 잰 판과
  **같았다**(재현 빌드). 뒷정리 `git checkout -- docker/proxy.Dockerfile` → porcelain 빈 출력 → 재빌드 exit 0

## 로컬 TLS 스모크

- cmd: `~/.local/bin/bv-build.sh ./tools/local-tls-smoke.sh`
- exit: **0**
- 핵심 결과: 지문 등식 성립(게이트가 판정한 바이트) · app·postgres·web·proxy healthy · 「로컬 패키징 스모크 통과」
  · 잔여 0

## r1(2026-10-29) — app libbrotli1 · 검토 지적

- cmd: `eclipse-temurin:21-jre-noble` 태그 조회 · 고정 판(`000fd431…`)·최신 판(`cd35bb33…`) 안 `dpkg-query -W libbrotli1` · `java -version` · 패키지 수
- exit: 0
- 핵심 결과: 1.1.0-2build2 → **1.1.0-2ubuntu0.1** · JDK 21.0.12.1 양쪽 · ubuntu 24.04 · 136 = 136

- cmd: base 의 app 이미지(`:caddy-before`, CI 명령 `docker build -f docker/app.Dockerfile`)에 취약점 게이트
- exit: **1** — 미등재 한 줄 `CVE-2026-111497|libbrotli1|HIGH|1.1.0-2build2|1.1.0-2ubuntu0.1` · `findings_total=53` — CI 와 같다
- cmd: 상향 이미지(`:caddy-after`)에 취약점 · 위생 게이트
- exit: **0** · **0** — `findings_total 53 → 31` · 차단후보 0 · `base-layer-접두-일치=true`
- 음성 대조: 옛 app 이미지 + 새 위생 정책 → exit **1**(앞 5 layer 가 정책이 파생한 베이스 체인과 다르다)

- cmd(M-1): `alpine:3.23.7` 안 `apk add` 를 새 줄 그대로 · `curl>=99.0.0` 을 앞·가운데에 둔 줄 · `brotli-libs>=99`
- exit: 0(설치 32 — 고치기 전과 같다) · **1** · **1** · **3** — 순서와 무관하게 fail-closed

- cmd(M-2): `go` 하나를 심은 변이 이미지(`FROM bidvector/web:caddy-after` + golang 빌더의 `go`) · 옛 정책 / 새 정책
- exit: **0 → 1**(「금지 실행 파일 'go'」) · 실제 web 이미지 + 새 정책 exit 0. proxy 로 같은 변이는 옛 정책에서도
  크기 상한(80MB)으로 exit 1 이라 대조에 쓰지 않았다(보상 통제가 먼저 잡는다)
- cmd(verifier L-2 재현): `FROM <web 이미지>` + `COPY --from=<golang 빌더> /usr/local/go/bin/go /usr/local/bin/go`(81.7MB) · `7d10f600` 정책 / `f6c2b0dd` 정책
- exit: **0 → 1**(「금지 실행 파일 'go'」)

- cmd: 첫 CI 실행(PR #101, head `7d10f600`) 로그
- 핵심 결과: caddy 컴파일 **한 번**(web 쪽 caddy-build `CACHED`) · CI 의 caddy sha256 `ddcb81ca…` = 로컬(기계 간 재현) ·
  `container` 3분 34초(main 같은 날 3분 38초) · 붉은 step 은 app 취약점 게이트 하나

## r1 — acceptance: CI `container` job 의 run step 전부

- cmd: `.github/workflows/ci.yml` `jobs.container` 의 `run:` 블록을 순서대로 실행(job `env`·`$GITHUB_ENV`·`$GITHUB_PATH`
  포함, `uses:` step 은 로컬 해당 없음) · `~/.local/bin/bv-build.sh` 한 자리
- 실행 HEAD: `1654f602`
- exit: run step **23 전부 0**
- 핵심 결과: 위생 넷 `base-layer-접두-일치=true` · 거부 스모크 통과 · 취약점 넷 차단후보 0(findings ml-serving 276 ·
  app 31 · web 7 · proxy 7, allowlist 0) · S-23 app·ml-serving·postgres healthy · S-23b·S-23c·S-24 통과 · S-26 로컬
  패키징 스모크 통과 · 끝난 뒤 bidvector 컨테이너 0

## Kotlin `check`

- cmd: evidence 와 비밀값 스캔 어휘 대조(참조형 — `grep -rniE -f config/quality/leak-patterns.txt`)
- `check`: `leakPatternGate` 의 입력이 `reports/evidence` 라 이 evidence 를 담은 HEAD 에서
  `~/.local/bin/bv-build.sh ./gradlew --no-daemon check` 를 돈다. 결과 정본은 verifier 와 PR 조치 코멘트다.
