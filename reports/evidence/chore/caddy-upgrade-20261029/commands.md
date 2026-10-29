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
- 핵심 결과: go1.26.10 · dep 147 = 147, 차이 **둘**(go-jose/v4 v4.1.5→v4.1.6 · x/net v0.59.0→v0.60.0) · build 줄 동일
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

## Kotlin `check`

- cmd: evidence 와 비밀값 스캔 어휘 대조(참조형 — `grep -rniE -f config/quality/leak-patterns.txt`)
- `check`: `leakPatternGate` 의 입력이 `reports/evidence` 라 이 evidence 를 담은 HEAD 에서
  `~/.local/bin/bv-build.sh ./gradlew --no-daemon check` 를 돈다. 결과 정본은 verifier 와 PR 조치 코멘트다.
