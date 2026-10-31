```yaml
milestone: chore
slice: caddy-upgrade-20261029
base_sha: 78d97c599c55f0a0cb44e4778a865eb4b8745d6f
in_scope:
  - docker/caddy/main.go · docker/caddy/go.mod · docker/caddy/go.sum(새 파일 셋 — caddy 빌드 입력)
  - docker/web.Dockerfile · docker/proxy.Dockerfile(caddy-build stage · alpine 런타임)
  - docker/web.Dockerfile.dockerignore · docker/proxy.Dockerfile.dockerignore(빌드 입력 셋 재포함)
  - tools/local-tls-build.sh(두 이미지 caddy sha256 등식)
  - config/quality/image-hygiene-policy-web.properties · -proxy.properties(base.image.repo·digest)
  - config/quality/vuln-policy.properties(scan.os.name.web·proxy)
  - config/quality/vuln-allowlist.properties(web·proxy 등재 20줄 삭제)
  - reports/evidence/chore/caddy-upgrade-20261029/
  # r1 확장(2026-10-29, 팀장 승인 — 아래 「계약 갱신 이력」)
  - docker/app.Dockerfile · config/quality/image-hygiene-policy-app.properties(app 베이스 다이제스트)
  - docker/compose.local-tls.yaml · .github/workflows/ci.yml(주석만) · docs/runbook/m6-6e-operations.md(§8.3·§8.3.1)
out_of_scope:
  - docker/proxy.Caddyfile · docker/web.Caddyfile · compose 의 거동(healthcheck·ENTRYPOINT·UID 무변경)
  - 취약점·위생 게이트 술어, 차단 문턱(block.*), 패키지 하한(scan.min-*)
  - ml-serving 이미지
acceptance_commands:  # r1 부터 CI `container` job 의 run step 전부(commands.md 「r1」)
  - ./tools/local-tls-build.sh
  - ./tools/image-hygiene-check.sh bidvector/web:local config/quality/image-hygiene-policy-web.properties
  - ./tools/image-hygiene-check.sh bidvector/proxy:local config/quality/image-hygiene-policy-proxy.properties
  - ./tools/vuln-scan-check.sh bidvector/web:local web config/quality/vuln-policy.properties
  - ./tools/vuln-scan-check.sh bidvector/proxy:local proxy config/quality/vuln-policy.properties
  - ./tools/local-tls-smoke.sh
  - ./gradlew --no-daemon check(evidence 가 leakPatternGate 입력)
rollback: git restore --source=78d97c599c55f0a0cb44e4778a865eb4b8745d6f --staged --worktree -- <rollback.md 의 목록>
```

## 왜 — 운영자 지시(2026-10-29): 면제를 늘리지 말고 Caddy 를 올려 해소

web·proxy 는 caddy 바이너리·caddy 베이스 alpine 패키지에 기한부 등재 20줄(kind 둘 × 10)을 안고 있었고 대부분
**2026-11-05** 만료다. 그날 모든 PR 의 `container` job 이 붉어진다. 게다가 base `78d97c59` 는 **이미 붉다** —
DB 가 `CVE-2026-56857` 를 더 보고하지 않아 그 두 줄이 stale 로 끊긴다(commands.md 「base」).

## ① 경로를 가른 실측 — 상류 상향은 닫는 것이 0, 직접 빌드는 9 전부

1. 상류 최신 릴리스는 여전히 **v2.11.7**(2026-10-03). `caddy:2.11.7` 최신 index(`sha256:5e5286cb…`, 2026-10-27)
   는 alpine 3.23.7 이지만 `/usr/bin/caddy` 가 고정 판과 **같은 파일**(sha256 `678ade3b…`, go1.26.8)이고
   `curl`·`libcurl` 8.22.0-r0 · `brotli-libs` 1.2.0-r0 그대로다.
2. 수정판은 모두 있다: Go **1.26.10**(golang 이미지 `1.26.10-alpine3.23`), `go-jose/v4` **v4.1.6**,
   `golang.org/x/net` **v0.60.0**(proxy.golang.org), alpine v3.23 index 의 `curl`·`libcurl` **8.23.0-r0** ·
   `brotli-libs` **1.2.1-r0**.

그래서 직접 빌드한다. 판은 **v2.11.7 그대로**이고 바꾸는 것은 toolchain 과 모듈 둘이다.

## 무엇을 바꿨나

- **빌드 방식은 caddy v2.11.7 `.goreleaser.yml` 과 같다** — `go mod init caddy` 한 모듈에 `cmd/caddy/main.go`
  (표준 모듈만), `-trimpath -mod=readonly -ldflags='-s -w'`, CGO 끔, `-tags=nobadger,nomysql,nopgx`. xcaddy 는
  쓰지 않는다 — xcaddy 가 하는 일이 이 세 줄이고, 도구 하나를 공급망에서 뺀다. `GOTOOLCHAIN=local`.
- **결과 동일성**: 공식 바이너리와 `go version -m` 의 dep **147 = 147**, 다른 줄은 의도한 둘(go-jose/v4 ·
  x/net)뿐 · `vcs.*` 넷(`vcs`·`vcs.revision`·`vcs.time`·`vcs.modified`)을 뺀 build 줄 동일 — 공식은 git 체크아웃에서 빌드해 VCS 정보를 싣는다, 거동 차이 0 · `caddy list-modules` 137줄 **동일** · `caddy version` 같은 문자열 · 크기 51.6MB(공식 52.0MB).
- **런타임**: `alpine:3.23.7`(공식 이미지와 같은 판)에 공식 런타임 단계를 옮김 — 패키지 넷 · `setcap
  cap_net_bind_service` · `XDG_*` · `WORKDIR` · `EXPOSE`. 그 위 UID/GID 10001 · `/data`·`/config` 소유 ·
  Caddyfile · ENTRYPOINT 는 무변경. 공식의 환영 페이지·기본 Caddyfile(어차피 덮어씀)은 싣지 않는다.
- **하한 셋**: `curl`·`libcurl` ≥ 8.23.0-r0, `brotli-libs` ≥ 1.2.1-r0(apk 제약 — 낮은 판이면 빌드 실패).
- **두 이미지의 같은 바이너리**: caddy-build stage 는 두 Dockerfile 에 같은 글자로 있다. 갈라짐은 글자가 아니라
  산출물로 잠근다 — `local-tls-build.sh` 가 두 `/usr/bin/caddy` sha256 이 다르면 exit 1(변이 실측 commands.md).

## 공급망 trade-off

- **잃는 것**: 상류가 서명·체크섬으로 내는 릴리스 바이너리 대신 우리가 굽는다. Go 보안 판이 나올 때마다
  golang 다이제스트·`go.mod` 를 **우리가** 올려야 한다(상류 재배포를 기다리지 않는 대가).
- **지키는 것**: 모듈 무결성은 `go.sum`(저장소에 커밋) + `-mod=readonly`, 첫 해석은 sum.golang.org 검증.
  toolchain 은 golang 이미지 다이제스트 고정. 같은 입력은 같은 바이트다(재빌드 sha256 일치 실측).
- **OS 패키지**: 공식 이미지처럼 빌드 시점의 alpine index 에서 받는다 — **정확 판 고정이 아니라 하한**이다.
  정확 고정은 alpine 이 옛 판을 index 에서 빼는 날 모든 빌드를 붉힌다. 이미지 내용이 빌드 시점에 따라 위로
  움직일 수 있고, 그 변화는 취약점·위생 게이트가 매 빌드 판정한다.

## 하지 않은 것

- 차단 문턱·패키지 하한·게이트 술어 무편집. 새 등재 0, 만료일 이동 0.
- `golang.org/x/net` 을 v0.62.0 까지 올리지 않았다 — 남은 x/net finding 둘은 severity UNKNOWN(비차단)이고
  v0.62.0 은 x/crypto·sys·term·text 넷을 상류 검증 그래프 밖으로 옮긴다(알려진 제한 2).

## 알려진 제한

1. 판정은 linux/amd64 한 플랫폼이다(게이트와 같다).
2. 남은 비차단 finding 7(kind 마다): x/net v0.60.0 UNKNOWN 둘(수정 0.62.0) · `go-chi/chi/v5` MEDIUM(수정 5.4.0) ·
   `go-jose/v3` v3.0.5 HIGH 1·MEDIUM 2(**수정판 없음** — `block.only-fixed=true` 라 비차단) · x/crypto UNKNOWN 1.
   DB 가 severity 를 매기면 붉어질 수 있다 — 그때는 `go.mod` 한 줄 상향이 처방이다.
3. alpine 하한은 「그 판 이상」만 보장한다. 판이 올라간 빌드는 이 evidence 의 패키지 수와 다를 수 있다.
4. allowlist 가 네 kind 모두 0 — web·proxy 에서 stale 검사가 그물 노릇을 하던 자리는 양성 대조 둘과 OS
   매칭 술어가 진다(allowlist 머리 주석 갱신).
5. x/net v0.59.0 → v0.60.0 은 caddy 의 HTTP/2 구현을 건드린다. 거동 축은 스모크의 https 요청(h2)과 로컬 CA
   발급까지이고 h2c·HTTP/3 는 이름 있는 축으로 재지 않았다.
6. `container` job 의 네트워크 의존이 넷이 됐다(proxy.golang.org·sum.golang.org). 첫 CI 실행 소요는 main 과
   같은 대역(ci.yml 주석).

## 수용 체크리스트

- [x] 상류 상향 경로 실측(닫는 것 0) · 직접 빌드 경로 실측(9 → 0)
- [x] web·proxy 위생 게이트 exit 0 · 취약점 게이트 exit 0(차단 후보 0, allowlist 0)
- [x] 음성 대조: 옛 이미지 + 새 allowlist → exit 1(미등재 9) · OS 선언 불일치 → exit 2
- [x] 바이너리: go1.26.10 · go-jose v4.1.6 · x/net v0.60.0 · dep·모듈 목록 공식과 대조
- [x] 로컬 TLS 스모크 통과(healthy 4 · 잔여 0)
- [x] rollback 트리 동일성 실측(rollback.md)
- [x] r1: app 게이트 1 → 0 · CR M-1·M-2 재실측 · CI `container` job run step 전부 로컬 실행(commands.md 「r1」)
- [ ] Kotlin `check` — 결과 정본은 verifier 와 PR 조치 코멘트

## 계약 갱신 이력

- **r1(2026-10-29, 팀장 승인)** — 범위 확장: app 이미지 `CVE-2026-111497|libbrotli1`(같은 날 DB 갱신, main·모든 PR
  공통)을 이 PR 에서 닫는다. 따로 내면 그 PR 은 main 의 web·proxy stale(56857)에, 이 PR 은 app 에 서로 막힌다.
  같은 회차에 검토(code-reviewer, approve · M 3 · L 4) 지적을 반영 — 처리는 아래 「검토 지적 처리」.

## r1 — app 베이스 상향

`eclipse-temurin:21-jre-noble` 같은 태그의 새 index `cd35bb33…`(2026-10-27) — `libbrotli1` 1.1.0-2build2 →
**1.1.0-2ubuntu0.1**(수정판), JDK 21.0.12.1 · ubuntu 24.04 · dpkg 패키지 136 **그대로**. 상향으로 닫히므로 등재하지
않는다. 두 `FROM`·LABEL·위생 정책 `base.image.digest` 를 한 커밋에서 옮겼다(runbook §8.3.1, 배포판 문자열 무변경).

## 검토 지적 처리

- **M-1** 무제약 `curl` 토큰 삭제 — 하한 셋은 패키지당 제약형 한 번. 불가능한 하한은 어느 위치에서도 exit ≠ 0 재실측.
- **M-2** web·proxy `forbidden.executables` 에 `go`·`gofmt`(golang 빌더 stage 경계) · 계수 문면 정정. 양성 대조
  commands.md 「r1」(web 변이: 옛 정책 exit 0 → 새 정책 exit 1).
- **M-3** runbook §8.3 셋째 갈래 · §8.3.1 의 2 에 `org.bidvector.caddy` 라벨.
- **L-1** web ENTRYPOINT 주석(alpine `CMD` = `/bin/sh`) · **L-2** 위생 정책 크기·curl 출처 주석 · **L-3** compose
  healthcheck 주석 · **L-4** ci.yml 네트워크 의존 넷째(Go 모듈)와 첫 CI 소요.
- Open Question 둘 — ⓐ x/net 상향의 거동 축은 아래 알려진 제한 5. ⓑ CI 에서 caddy 컴파일은 **한 번**이다(첫 CI
  로그: web 의 caddy-build 가 `CACHED`) — stage 공유 재설계 불필요.

## 하네스 레인 변경

없음(`git log --oneline 78d97c59..HEAD -- CLAUDE.md .claude/` 빈 출력).
