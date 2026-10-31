# M7/7F-local — 리버스 프록시 이미지(scope.md 산출물 2). 설정은 **site 둘과 공유 본문 하나**
# (`docker/proxy.Caddyfile` · `docker/proxy.prod.Caddyfile` · `docker/proxy.site.caddy`, 7F 본편
# D-7F2-2)이고 셋 다 이미지에 굽는다 — bind mount 를 쓰지 않는다(뜬 컨테이너의 규칙과 저장소의
# 파일이 갈라질 자리를 만들지 않는다).
#
# 베이스·caddy 바이너리는 web 이미지의 runtime 과 **같다**(같은 alpine 다이제스트, 같은 caddy-build
# stage) — 고정해야 하는 베이스가 하나로 줄고, 두 이미지가 서로 다른 Caddy 판으로 갈라지지 않는다.

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
FROM alpine:3.23.7@sha256:ff7f0c727dd796aa36787bfcd9a64bf35a6ce72a73451b3359fde849b6703d63

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

# non-root — app·ml-serving·web 이미지와 같은 고정 UID/GID.
#
# web 이미지와 달리 **쓰기 가능한 디렉터리가 필요하다**: `tls internal` 이 실행 시 로컬 CA 와
# 인증서를 `$XDG_DATA_HOME/caddy`(= `/data/caddy`) 에 만든다. 위 단계가 만든 `/data`·`/config` 는
# root 소유라 그대로는 비-root 가 쓸 수 없다 — 소유권을 옮긴다. **볼륨을 붙이지 않으므로**
# 그 자리는 컨테이너 수명과 함께 사라진다(스모크의 「볼륨 잔여 0」과 「인증서는 실행 시
# 생성」이 같은 선택의 두 면이다).
RUN addgroup -S -g 10001 bidvector \
    && adduser -S -D -H -u 10001 -G bidvector -h /nonexistent -s /sbin/nologin bidvector \
    && mkdir -p /data/caddy /config/caddy \
    && chown -R 10001:10001 /data /config

# 설정 파일 셋 — 로컬 site(기본 ENTRYPOINT 가 읽는다) · 운영 site(`compose.prod.yaml` 의
# **`entrypoint:`** 가 고른다 — `command:` 로는 안 된다. 아래 ENTRYPOINT 가 이미 로컬 site 를 인자로
# 들고 있어서 `command:` 는 그 뒤에 덧붙을 뿐이고 로컬 site 가 뜬다) · 두 site 가 `import` 하는 공유 본문.
# 두 site 를 한 이미지에 구워 **게이트가 판정한 바이트가 두 프로필에서 같다**(운영용 이미지를 따로 만들지 않는다).
COPY docker/proxy.Caddyfile /etc/caddy/Caddyfile
COPY docker/proxy.prod.Caddyfile /etc/caddy/Caddyfile.prod
COPY docker/proxy.site.caddy /etc/caddy/proxy.site.caddy

USER bidvector

ENTRYPOINT ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
CMD []
