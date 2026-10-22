# M7/7F-local — 리버스 프록시 이미지(scope.md 산출물 2). 설정은 `docker/proxy.Caddyfile` 이고
# 이미지에 굽는다 — bind mount 를 쓰지 않는다(뜬 컨테이너의 규칙과 저장소의 파일이 갈라질
# 자리를 만들지 않는다).
#
# 베이스는 web 이미지의 runtime 과 **같은 다이제스트**다(2026-10-16 실측 `docker buildx
# imagetools inspect caddy:2.11.7`) — 고정해야 하는 베이스가 하나로 줄고, 두 이미지가 서로
# 다른 Caddy 판으로 갈라지지 않는다.
FROM caddy:2.11.7@sha256:685ae3d7c34d9b5f8caeddc5d14051db7bbd88ff1f5a2fa0c16bd8ca328fb7af

LABEL org.bidvector.baseimage="caddy:2.11.7@sha256:685ae3d7c34d9b5f8caeddc5d14051db7bbd88ff1f5a2fa0c16bd8ca328fb7af"

# non-root — app·ml-serving·web 이미지와 같은 고정 UID/GID.
#
# web 이미지와 달리 **쓰기 가능한 디렉터리가 필요하다**: `tls internal` 이 실행 시 로컬 CA 와
# 인증서를 `$XDG_DATA_HOME/caddy`(= `/data/caddy`) 에 만든다. 베이스의 `/data`·`/config` 는
# root 소유라 그대로는 비-root 가 쓸 수 없다 — 소유권을 옮긴다. **볼륨을 붙이지 않으므로**
# 그 자리는 컨테이너 수명과 함께 사라진다(스모크의 「볼륨 잔여 0」과 「인증서는 실행 시
# 생성」이 같은 선택의 두 면이다).
RUN addgroup -S -g 10001 bidvector \
    && adduser -S -D -H -u 10001 -G bidvector -h /nonexistent -s /sbin/nologin bidvector \
    && mkdir -p /data/caddy /config/caddy \
    && chown -R 10001:10001 /data /config

# 설정 파일 셋 — 로컬 site(기본 ENTRYPOINT 가 읽는다) · 운영 site(`compose.prod.yaml` 의
# `command:` 가 고른다) · 두 site 가 `import` 하는 공유 본문. 두 site 를 한 이미지에 구워
# **게이트가 판정한 바이트가 두 프로필에서 같다**(운영용 이미지를 따로 만들지 않는다).
COPY docker/proxy.Caddyfile /etc/caddy/Caddyfile
COPY docker/proxy.prod.Caddyfile /etc/caddy/Caddyfile.prod
COPY docker/proxy.site.caddy /etc/caddy/proxy.site.caddy

USER bidvector

ENTRYPOINT ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
CMD []
