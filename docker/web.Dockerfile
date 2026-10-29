# M7/7F-local — web 정적 이미지(scope.md 산출물 1). 번들을 **이미지 안에서** 만든다
# (`docker/ml-serving.Dockerfile` 과 같은 형태이고 `docker/app.Dockerfile` 과는 다르다 —
# 그쪽이 밖에서 만든 `bootJar` 를 받는 이유는 Gradle 이 무겁고 CI 가 이미 `setup-gradle` 을
# 갖췄기 때문이다. npm 빌드는 그 축에 들지 않으므로 `docker build` 한 번으로 이미지가
# 나오게 둔다 — 로컬 스모크가 web 빌드를 따로 요구하지 않는다).
#
#     docker compose -f docker/compose.yaml -f docker/compose.local-tls.yaml build web
#
# 베이스 셋 다 다이제스트로 고정한다(멀티플랫폼 index 의 다이제스트 — node 는 2026-10-16 실측,
# golang·alpine 은 2026-10-29 실측). `:latest`·미고정 태그는 쓰지 않는다(6C 위생 기준 (2)).
FROM node:24.12.0-slim@sha256:7326fb2dbdce998edd72140946851be64ef4a643e8715e138ca467e8e9d92c99 AS builder

# `@playwright/test` 의 설치 훅이 브라우저(수백 MB)를 내려받는다 — 이 stage 는 번들만 만들고
# e2e 는 `web` job 이 자기 자리에서 돌리므로 받지 않는다.
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /src/web

# 잠금 파일 먼저 — 소스가 바뀌어도 설치 layer 는 캐시된다.
COPY web/package.json web/package-lock.json web/.npmrc web/.nvmrc ./

# **Node 버전의 정본은 `web/.nvmrc` 하나다**(ADR 0011 D-1). 위 `FROM` 은 그 정본을 따라야 하는
# 둘째 자리이므로, 어긋나면 **이 자리에서 빌드를 멈춘다** — 주석으로 「맞춰 두라」고 적는
# 대신 구조로 잠근다. `.npmrc` 의 `engine-strict` 도 같은 방향이지만 그것이 보는 것은
# `package.json` 의 범위(>=24 <25)이고, 이 검사가 보는 것은 **정본 파일과의 등식**이다.
RUN set -eu; \
    want="$(head -n1 .nvmrc | tr -cd '0-9.' | cut -d. -f1)"; \
    have="$(node -v | tr -cd '0-9.' | cut -d. -f1)"; \
    [ -n "$want" ] || { echo "web/.nvmrc 에서 major 를 읽지 못했다" >&2; exit 1; }; \
    [ "$want" = "$have" ] || { \
        echo "베이스 Node major($have)가 web/.nvmrc($want)와 다르다 — 이 Dockerfile 의 FROM 을 정본에 맞춘다" >&2; \
        exit 1; \
    }

# 잠금 파일 밖 해석을 하지 않는다 — 잠금 파일과 `package.json` 이 어긋나면 설치가 실패한다
# (ci.yml `web` job 의 W-1 과 같은 명령).
RUN npm ci

# `npm run build`(= `vite build`)가 읽는 것만 넣는다. `eslint.config.js`·`playwright.config.ts`·
# `e2e/`·`config/gate-tests.txt` 는 게이트 레인의 입력이고 번들에 들어가지 않는다.
COPY web/index.html web/vite.config.ts web/tsconfig.json ./
COPY web/scripts ./scripts
COPY web/src ./src

RUN npm run build

# ---- caddy 바이너리 ----
# chore(2026-10-29, 운영자 지시 「면제를 늘리지 말고 Caddy 를 올려 해소」) — caddy 바이너리를 **우리가 빌드한다.**
# 2026-10-29 실측: 상류 최신 릴리스는 여전히 v2.11.7 이고 `caddy:2.11.7` 의 최신 재배포판도 같은 바이너리
# (go1.26.8 · go-jose v4.1.5 · x/net v0.59.0)를 싣는다 — 베이스 상향으로는 닫히지 않는다.
#
# 무엇을 빌드하는가는 `docker/caddy/` 세 파일이 정한다: `main.go` 는 caddy v2.11.7 의 `cmd/caddy/main.go`
# 그대로(표준 모듈만, 플러그인 0), `go.mod` 는 caddy v2.11.7 의 의존 그래프에서 **go-jose/v4 → v4.1.6 ·
# golang.org/x/net → v0.60.0 두 줄만** 올린 것, `go.sum` 이 모듈 체크섬을 고정한다(`-mod=readonly` 라
# 어긋나면 빌드가 멈춘다). 빌드 방식·플래그는 caddy v2.11.7 의 `.goreleaser.yml` 과 같다(`go mod init caddy` 한
# 모듈에 `cmd/caddy/main.go`, `-trimpath`·`-ldflags='-s -w'`·CGO 끔·`-tags=nobadger,nomysql,nopgx`). `GOTOOLCHAIN=local` — go.mod 의 `go 1.26.10` 보다 낮은 toolchain 이면
# 다른 toolchain 을 내려받지 않고 실패한다.
#
# **이 stage 는 `docker/web.Dockerfile` 과 `docker/proxy.Dockerfile` 에 같은 글자로 있다.** 두 이미지의
# 바이너리가 같은지는 글자가 아니라 산출물로 잠근다 — `tools/local-tls-build.sh` 가 두 이미지의
# `/usr/bin/caddy` sha256 이 다르면 실패한다.
FROM golang:1.26.10-alpine3.23@sha256:34414731924cf2263160f0021f5cf6e1807382d85248f7cb419d4187b96d5e43 AS caddy-build
ENV CGO_ENABLED=0 GOTOOLCHAIN=local GOFLAGS=-mod=readonly
WORKDIR /src/caddy
COPY docker/caddy/go.mod docker/caddy/go.sum docker/caddy/main.go ./
RUN go build -trimpath -ldflags='-s -w' -tags=nobadger,nomysql,nopgx -o /out/caddy . && /out/caddy version

# ---- runtime ----
# 런타임 베이스는 공식 caddy 이미지와 같은 alpine 판이고(공식 2.11.7 = alpine 3.23.7), 공식 이미지의 런타임
# 단계를 그대로 옮긴다 — 같은 패키지 넷(`ca-certificates curl libcap mailcap`), 같은 `XDG_*`·`WORKDIR`·
# `EXPOSE`, 같은 `setcap`. 다른 것은 바이너리의 출처(위 stage)와 아래 **하한 셋**이다.
#
# 하한 셋(`curl`·`libcurl` ≥ 8.23.0-r0, `brotli-libs` ≥ 1.2.1-r0) — compose healthcheck 가 쓰는 `curl` 과
# 그 의존이 취약점 게이트의 차단 후보였다(CVE-2026-92392 · CVE-2026-111497). 패키지는 공식 이미지처럼
# 빌드 시점의 alpine v3.23 index 에서 온다(정확 판 고정이 아니다 — 정확 고정은 alpine 이 옛 판을 index
# 에서 빼는 날 모든 빌드를 붉힌다). 하한은 그 index 가 고친 판보다 낮은 것을 주면 빌드를 멈춘다.
# 하한을 건 패키지는 **제약형 한 번만** 적는다 — 무제약 `curl` 을 함께 적으면 인자 순서에 따라 apk 가
# 하한을 조용히 무시한다(검토 실측: `'curl>=99' curl` 순서면 exit 0).
#
# 라벨은 **보조 정보**다(D-6C-9) — 손으로 적는 자유 텍스트라 `FROM` 과 아무 것도 묶지 않는다. 구속력
# 있는 베이스 고정은 다이제스트 자신이다.
FROM alpine:3.23.7@sha256:ff7f0c727dd796aa36787bfcd9a64bf35a6ce72a73451b3359fde849b6703d63 AS runtime

LABEL org.bidvector.baseimage="alpine:3.23.7@sha256:ff7f0c727dd796aa36787bfcd9a64bf35a6ce72a73451b3359fde849b6703d63"
LABEL org.bidvector.caddy="v2.11.7 self-built (docker/caddy/go.mod)"

RUN apk add --no-cache ca-certificates libcap mailcap \
        'curl>=8.23.0-r0' 'libcurl>=8.23.0-r0' 'brotli-libs>=1.2.1-r0'

# 바이너리 복사와 `setcap` 을 **한 layer** 에서 한다 — `COPY` 뒤 별도 `RUN setcap` 은 xattr 하나 때문에
# 바이너리 전체를 새 layer 에 한 번 더 싣는다(실측 이미지 크기 2배).
RUN --mount=type=bind,from=caddy-build,source=/out/caddy,target=/tmp/caddy-build \
    set -eux; \
    install -m 0755 /tmp/caddy-build /usr/bin/caddy; \
    mkdir -p /config/caddy /data/caddy /etc/caddy; \
    chmod 1777 /config/caddy /data/caddy; \
    setcap cap_net_bind_service=+ep /usr/bin/caddy; \
    caddy version

ENV CADDY_VERSION=v2.11.7 XDG_CONFIG_HOME=/config XDG_DATA_HOME=/data
EXPOSE 80 443 443/udp 2019
WORKDIR /srv

# non-root — 고정 UID/GID, 시스템 계정 범위(app·ml-serving 이미지와 같은 10001). 베이스가
# alpine 이라 busybox `addgroup`/`adduser` 다.
RUN addgroup -S -g 10001 bidvector \
    && adduser -S -D -H -u 10001 -G bidvector -h /nonexistent -s /sbin/nologin bidvector

COPY docker/web.Caddyfile /etc/caddy/Caddyfile
# `--chown` 을 쓰지 않는다(app.Dockerfile 과 같은 사유) — 런타임 사용자에게 소유권을 주면
# 침해된 프로세스가 **재기동 뒤에도 남는 형태로** 번들을 고칠 수 있다. 이 서비스는 `/srv` 를
# 읽기만 한다(설정 자동 저장도 꺼 두었다 — `web.Caddyfile` 의 `persist_config off`).
COPY --from=builder /src/web/dist /srv

USER bidvector

# 베이스(alpine)의 `CMD` 는 `/bin/sh` 다 — 이 이미지가 무엇을 실행하는지는 ENTRYPOINT 로 **명시하고**,
# `CMD` 를 비워 베이스의 `/bin/sh` 가 ENTRYPOINT 뒤에 인자로 붙지 않게 한다(`caddy run … /bin/sh` 가 된다).
ENTRYPOINT ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
CMD []
