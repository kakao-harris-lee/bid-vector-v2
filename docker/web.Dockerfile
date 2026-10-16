# M7/7F-local — web 정적 이미지(scope.md 산출물 1). 번들을 **이미지 안에서** 만든다
# (`docker/ml-serving.Dockerfile` 과 같은 형태이고 `docker/app.Dockerfile` 과는 다르다 —
# 그쪽이 밖에서 만든 `bootJar` 를 받는 이유는 Gradle 이 무겁고 CI 가 이미 `setup-gradle` 을
# 갖췄기 때문이다. npm 빌드는 그 축에 들지 않으므로 `docker build` 한 번으로 이미지가
# 나오게 둔다 — 로컬 스모크가 web 빌드를 따로 요구하지 않는다).
#
#     docker compose -f docker/compose.yaml -f docker/compose.local-tls.yaml build web
#
# 베이스 둘 다 다이제스트로 고정한다(2026-10-16 실측 `docker buildx imagetools inspect
# node:24.12.0-slim` · `caddy:2.11.7` — 멀티플랫폼 index 의 다이제스트). `:latest`·미고정
# 태그는 쓰지 않는다(6C 위생 기준 (2)).
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

# ---- runtime ----
FROM caddy:2.11.7@sha256:685ae3d7c34d9b5f8caeddc5d14051db7bbd88ff1f5a2fa0c16bd8ca328fb7af AS runtime

# 이 라벨은 **보조 정보**다(D-6C-9) — 손으로 적는 자유 텍스트라 위 `FROM` 과 아무 것도 묶지
# 않는다. 구속력 있는 베이스 고정은 위 다이제스트 자신이다.
LABEL org.bidvector.baseimage="caddy:2.11.7@sha256:685ae3d7c34d9b5f8caeddc5d14051db7bbd88ff1f5a2fa0c16bd8ca328fb7af"

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

# 베이스의 `CMD` 가 같은 명령을 들고 있지만 **명시한다** — 베이스가 바뀌어도 이 이미지가
# 무엇을 실행하는지가 파일에 남는다. `CMD` 를 비우는 것이 함께 필요하다(ENTRYPOINT 뒤에
# 베이스의 CMD 가 인자로 붙으면 명령이 두 번 적힌 꼴이 된다).
ENTRYPOINT ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
CMD []
