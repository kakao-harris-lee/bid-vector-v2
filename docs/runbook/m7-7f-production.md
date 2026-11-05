# M7/7F 운영 runbook — 반입 · host nginx · 인증서 · DNS 전환 · 되돌림 (초안 2026-10-22)

> **지위**: 7F 본편(D-7F2-2, 설계 (A)). 이 문서의 명령 중 **운영 effect** 가 있는 것(host nginx 변경·reload,
> certbot 발급, DNS, Windows 포트, `--apply`)은 **운영자가 승인한 뒤 직접** 실행한다. 저장소 스크립트는
> 기본이 dry-run 이다.
>
> 운영 도메인 값은 이 문서와 host 의 운영 env 파일에만 있다: **`bidvector.kr`**. 코드·이미지·compose 에는
> 없다(테스트는 `bidvector.test`).

## 0. 구성 한눈

```
인터넷 ─► (공유기·외부 방화벽) ─► Windows 80/443 ─► WSL host nginx (0.0.0.0:80·443, 기존 공개 site 와 공존)
                                                     │  server_name bidvector.kr  (sites-enabled/zz-bidvector_kr)
                                                     │  · TLS(구매 인증서 §4.7 · certbot 은 §4.3) · 80→301 · /api/session limit_req · XFF 덮어쓰기
                                                     ▼
                                       127.0.0.1:<루프백 포트>  proxy(Caddy 운영 site, 평문)
                                          · 보안 헤더·HSTS · /actuator* 404 · 상류 둘
                                     edge 망 ▼                 ▼ default 망
                                           web(정적)           app ─► postgres
```

- compose 프로젝트 이름은 **`bidvector-v2-prod`** 다(`docker/compose.prod.yaml`). 개발 레인의 `bidvector-v2`
  스택과 컨테이너·볼륨·네트워크가 갈린다 — 개발 쪽 `down -v` 가 운영 DB 볼륨을 지우지 않는다.
- 루프백 포트는 개발 레인의 로컬 스모크 기본값(18443)을 **쓰지 않는다**. 권장 `28443`.
- ml-serving 은 기본으로 띄우지 않는다(`--profile ml` 일 때만). app 이 부르지 않는다.
- 운영 DB 는 **빈 DB** 로 시작한다. 개발 DB 의 데이터 이관은 「운영 DB 전환」 별도 승인 사항이다.
- 이 host 의 legacy `bid_vector_*` 컨테이너·볼륨은 읽지도 지우지도 않는다.

## 1. 운영자 선행 작업 (한 번)

1. **메모리 확인**: `free -m`(available ≥ 6GB · swap free ≥ 2GB) · `ps -eo pid,rss,args --sort=-rss | head`.
   이 host 의 earlyoom 은 `java` 를 먼저 죽인다 — 운영 app 이 첫 희생자다(§「메모리」).
2. **Windows 포트**: 기존 공개 site 가 이미 공개 중이므로 80·443 의 Windows → WSL 경로가 있을 가능성이 높다.
   **확인 → 없으면 설정** 순서로 한다: Windows 의 포트 포워딩(또는 WSL 네트워킹 모드) · Windows 방화벽 inbound
   80·443 · 공유기/외부 방화벽 포워딩. WSL 재시작 뒤 포워딩이 사라지지 않는지도 확인한다.
3. **운영 env 파일**: `docker/prod.env.template` 을 저장소 **밖**으로 복사해 값을 채운다.
   ```bash
   # 디렉터리 소유자도 **반입을 돌릴 사용자**여야 한다 — 0700 root:root 이면 그 아래 파일이 0600 "$USER" 여도
   # 지나갈 수 없어, 아래 확인 명령과 §3 의 deploy 가 「env 파일을 읽지 못했다」(exit 2)로 멈춘다(§1.4 와 같은 꼴).
   sudo install -d -m 700 -o "$USER" /etc/bidvector-v2
   # 이미 값을 채운 파일이 있으면 이 줄이 **말없이 덮는다.** 재설치·복구 때는 `-b`(사본을 남긴다)를 붙이거나
   # 먼저 `test ! -e /etc/bidvector-v2/prod.env` 로 확인한다.
   sudo install -m 600 -o "$USER" docker/prod.env.template /etc/bidvector-v2/prod.env
   ```
   - 자격 값(PG 암호 · 운영자 자격)은 운영자가 생성한다(`openssl rand -hex 24` 등). 저장소·채팅·로그에 적지 않는다.
   - `BIDVECTOR_RELEASE_TAG` 는 비워 둔다(반입 스크립트가 준다).
   - `BIDVECTOR_PROXY_DOMAIN=bidvector.kr` · `BIDVECTOR_PROXY_HTTPS_PORT=28443`.
   - `BIDVECTOR_APP_JDBC_URL=jdbc:postgresql://postgres:5432/<BIDVECTOR_PG_DB 값>` — **이 형태 그대로**다.
     호스트·포트를 바꾸거나 `?currentSchema=…` 같은 매개변수를 붙이면 반입이 `exit 2` 로 멈춘다: 마이그레이션
     가드는 이 compose 의 `postgres` 를 읽으므로, app 이 다른 자리에 붙으면 **가드가 다른 DB 를 보고 통과시킨다.**
   - `BIDVECTOR_APP_JAVA_TOOL_OPTIONS` 는 **`-XX:MaxRAMPercentage=<0..100>` 하나만** 받는다(§「메모리」 표의 그
     형태). 다른 옵션을 더하면 반입이 `exit 2` · 주기 러너가 `exit 3` 으로 멈춘다 — 이 문자열의 `-D` 시스템
     속성은 compose 가 준 env 를 **이기므로**, 여기에 `-Dbidvector.persistence.jdbcUrl=…` 를 넣으면 위 JDBC
     등식을 통과한 채 app 만 다른 DB 로 간다. 힙 말고 다른 JVM 조정이 필요하면 **이 키가 아니라** compose
     파일과 그 게이트 목록을 함께 고친다.
   - 메모리 키는 §「메모리」 표.
   - 확인: `./tools/prod-env-keys-check.sh --env-file /etc/bidvector-v2/prod.env`
4. **상태 디렉터리**(release manifest · 백업 · 이력): `sudo install -d -m 700 -o "$USER" /var/lib/bidvector-v2`
4c. **잠금 자리**(`/run/lock/bidvector-v2`) — 스크립트가 첫 실행에서 만들지만, **부팅마다 미리 만들어 둔다.**
   `/run/lock` 은 tmpfs 이고 sticky·world-writable(`1777`)이라, 재부팅과 첫 반입 사이에 같은 host 의 다른
   계정이 이 이름을 선점할 수 있다. 선점당하면 반입·되돌림은 `exit 2`, 주기 러너는 `exit 3` 으로 멈춘다
   (fail-closed지만 그때는 고칠 때까지 반입이 안 된다).
   ```bash
   # /etc/tmpfiles.d/bidvector-v2.conf  — systemd-tmpfiles 가 부팅마다 자리를 먼저 만든다
   d /run/lock/bidvector-v2 0755 deploy deploy -
   ```
   `sudo systemd-tmpfiles --create` 로 지금 한 번 적용한다. 소유자는 §1 에서 반입을 돌리는 사용자와 같아야
   한다 — 스크립트가 **소유자 == 자기 uid** 와 **group/other 쓰기 없음**을 단언하고, 어긋나면 거절한다
   (group/other 쓰기가 열린 자리는 잠금 파일을 지워 우회할 수 있다). 자리를 환경변수에서 떼어 둔 이유는
   §7.1 의 주를 본다.
   > 남은 자리: **그 사용자 자신**은 쥐어진 잠금 파일을 지워 우회할 수 있다(새 inode 가 잠긴다). 반입을
   > 돌리는 사용자는 이미 docker 소켓을 가지므로 이 경로로 늘어나는 권한은 없다 — 사고 조사에서 알아 두는 사실이다.
4b. **저장소 자리**를 한 번 정한다 — release 를 만들고(§2) 반입을 돌리고(§3) 주기 러너가 compose 파일을
   읽는(§「주기 실행」) 자리가 **모두 여기**다. 이 문서는 아래에서 `<저장소>` 로 쓴다.
   ```bash
   # 예: /srv/bidvector-v2 — 운영자가 정한다. 레인들이 브랜치를 바꾸는 작업 트리를 쓰지 않는다.
   echo /srv/bidvector-v2
   ```
5. **trivy 설치**(취약점 게이트 — release 가 요구한다. 없으면 release 는 판정 불가로 멈춘다).
6. **certbot 설치**: `sudo apt-get install certbot` — 패키지가 갱신 타이머(`certbot.timer`)를 함께 설치한다. 구매 인증서(§4.7)로 운영하면 쓰지 않는다.

## 2. release 만들기

```bash
git switch main && git pull --ff-only     # release 는 추적 파일 변경 0 인 커밋 하나에서 만든다
REL="rel-$(git rev-parse --short=12 HEAD)"
./tools/prod-release.sh --tag "$REL" --state-dir /var/lib/bidvector-v2 --env-file /etc/bidvector-v2/prod.env          # dry-run
./tools/prod-release.sh --tag "$REL" --state-dir /var/lib/bidvector-v2 --env-file /etc/bidvector-v2/prod.env --apply  # 빌드 + 게이트 + manifest
```

- 빌드는 **무거운 작업**이다 — 시작 전 `pgrep -af 'GradleWrapperMain|GradleWorkerMain'` · `free -m` · `ps` 를 **따로**
  보고, 다른 빌드가 돌면 기다린다. 00:30~03:00 KST(수집 창)에는 하지 않는다.
- release 태그는 다시 쓰지 않는다(이미 있으면 거부). `:local` 태그는 건드리지 않는다.
- allowlist 의 가장 이른 만료가 14일 안이면 release 가 멈춘다 — 6E runbook §8.5 의 만료 처리 먼저.

## 3. 반입

```bash
./tools/prod-deploy.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2 --release "$REL"          # dry-run
./tools/prod-deploy.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2 --release "$REL" --apply
```

preflight 가 보는 것: env 파일(저장소 밖 · 0600 · 키 집합) · manifest 와 이미지 ID 등식 · compose ≥ 2.24 ·
**프로젝트 이름** · 루프백 포트 · 디스크 · **지금 DB 스키마와 가려는 release 의 마이그레이션 비교**. apply 는
**반입 직전 DB 백업**(데이터 볼륨이나 postgres 컨테이너가 있으면 — 멈춰 있으면 올려서 뜬다) → `up -d --wait` →
`tools/prod-health.sh`. 이 단계까지는 public 노출이 없다 — 루프백뿐이다.

**dry-run 의 DB 줄을 읽는다.** 「데이터 볼륨이 없다(반입 기록은 …)」가 나오면 **멈추고 왜 볼륨이 없는지 먼저
확인한다** — 첫 반입이 아닌데 볼륨이 없다는 것은 누가 `down -v` 를 돌렸거나 프로젝트 이름이 어긋났다는 뜻이고,
그대로 진행하면 빈 DB 위에 뜬다.

**반입 뒤 — 운영 자리 사본 갱신.** 이 release 에서 `tools/prod-cert-check.sh` 나 알림 훑기 호출이 바뀌었으면
`/usr/local/lib/bidvector-v2/` 의 사본을 다시 깐다(§「host nginx 조각」의 인증서 감시 · §「주기 실행」). 사본은
자동으로 따라오지 않는다.

```bash
git diff --name-only "$(cat /var/lib/bidvector-v2/previous 2>/dev/null || echo HEAD)" HEAD -- tools/prod-cert-check.sh
```

## 4. host nginx 조각 (운영 effect)

**파일 이름은 `zz-bidvector_kr` 이어야 한다.** 이 host 의 443 에는 `default_server` 가 없어서 SNI 없는 요청은 이름
정렬상 첫 443 server 가 받는다. 기존 site 보다 앞에 정렬되는 이름(예: `bidvector_kr`)이면 기존 site 의 기본 대상을
가져간다. `tools/prod-nginx-render.sh` 는 `zz-` 가 아닌 이름을 거부한다.

### 4.1 배치 전 검증 (host 쓰기 0)

```bash
./tools/prod-nginx-check.sh --host-config /etc/nginx --proxy-image "bidvector/proxy:$REL"
```

host 설정을 임시 디렉터리에 복사해 같은 판의 nginx 컨테이너에서 「조각을 넣기 전후로 기존 응답이 같다」와 새
거동(301·ACME·헤더·`/actuator` 404·XFF·429·인증서 검사)을 잰다. 사본은 끝나면 지워진다. exit 0 이 아니면 배치하지
않는다.

### 4.2 첫 발급 전 — 80 만

```bash
./tools/prod-nginx-render.sh --http-only bidvector.kr 28443 /tmp/zz-bidvector_kr
sudo install -d -m 755 /var/www/bidvector-v2-acme
sudo install -m 644 /tmp/zz-bidvector_kr /etc/nginx/sites-available/zz-bidvector_kr
sudo ln -s /etc/nginx/sites-available/zz-bidvector_kr /etc/nginx/sites-enabled/zz-bidvector_kr
sudo nginx -t && sudo systemctl reload nginx
```

`nginx -t` 가 실패하면 reload 하지 않고 링크를 지운다. reload 뒤 **기존 site 를 먼저** 확인한다(브라우저와 `curl -sI`).

### 4.3 인증서 — staging 먼저

> 현행 운영은 구매 인증서다(§4.7). 이 절과 §4.5 의 certbot 경로는 Let's Encrypt 로 옮길 때 쓴다.

DNS 가 이 host 를 가리킨 뒤(§5)에만 된다.

```bash
sudo certbot certonly --webroot -w /var/www/bidvector-v2-acme -d bidvector.kr --staging \
  --email <연락 이메일> --agree-tos --no-eff-email
sudo certbot delete --cert-name bidvector.kr         # staging 인증서를 지운다
sudo certbot certonly --webroot -w /var/www/bidvector-v2-acme -d bidvector.kr \
  --email <연락 이메일> --agree-tos --no-eff-email
```

staging 이 실패하면 실 CA 로 넘어가지 않는다 — 실패를 반복하면 발급 한도에 닿는다.

### 4.4 443 포함 판으로 교체

```bash
./tools/prod-nginx-render.sh bidvector.kr 28443 /tmp/zz-bidvector_kr
sudo install -m 644 /tmp/zz-bidvector_kr /etc/nginx/sites-available/zz-bidvector_kr
sudo nginx -t && sudo systemctl reload nginx
./tools/prod-health.sh --env-file /etc/bidvector-v2/prod.env --public
```

### 4.5 갱신 — reload hook 과 타이머

```bash
sudo install -d /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\nnginx -t && systemctl reload nginx\n' | sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx >/dev/null
sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx
systemctl list-timers certbot.timer
sudo certbot renew --dry-run
# `renew --dry-run` 은 **deploy hook 을 돌리지 않는다** — 위에서 만든 reload hook 이 실제로 서는지는
# 따로 본다(둘 중 하나).
sudo /etc/letsencrypt/renewal-hooks/deploy/reload-nginx && echo "hook OK"
# 또는: sudo certbot renew --dry-run --run-deploy-hooks
```

reload 는 같은 nginx 를 쓰는 기존 site 에도 걸린다(graceful — 연결을 끊지 않는다). hook 이 `nginx -t` 를 먼저
보므로 깨진 설정으로 reload 하지 않는다.

**인증서 감시 타이머**(갱신 실패 감지 — 구매 인증서면 임계를 §4.7-4 의 30일로): 하루 한 번 `prod-cert-check.sh bidvector.kr` 를 돌린다(남은 21일
미만이면 exit 1). 외부 발송은 없다(R-4) — 결과는 journal 에서 본다.

**먼저 스크립트를 운영 자리로 복사한다.** 작업 트리(`/home/deploy/project/bid-vector-v2`)를 그대로 가리키면
안 된다 — 그 트리는 레인들이 브랜치를 바꾸는 공유 자리이고, 거기에 쓸 수 있는 누구든 이 unit 이 실행하는
내용을 바꿀 수 있다. 반입한 release 의 커밋에서 한 번 복사하고, 소유자는 root 로 둔다.

```bash
sudo install -d -m 755 /usr/local/lib/bidvector-v2
sudo install -m 755 -o root -g root tools/prod-cert-check.sh /usr/local/lib/bidvector-v2/prod-cert-check.sh
```

이 검사는 **root 가 필요 없다**(443 에 붙어 인증서를 읽을 뿐이다) — unit 도 일반 사용자로 돌린다.

> `User=` 에는 **§1 에서 반입을 돌린 사용자**를 적는다. 그 절들은 `$USER` 로 쓰고 여기는 리터럴이라 — 이 host 에서는
> 둘 다 `deploy` 다. 다르면 **두 자리를 함께** 바꾼다(아래 unit 과 §「주기 실행」의 unit).

```ini
# /etc/systemd/system/bidvector-cert-check.service
[Unit]
Description=bidvector 인증서 잔여일 검사

[Service]
Type=oneshot
User=deploy
ExecStart=/usr/local/lib/bidvector-v2/prod-cert-check.sh bidvector.kr
```

```ini
# /etc/systemd/system/bidvector-cert-check.timer
[Unit]
Description=bidvector 인증서 잔여일 검사 — 매일

[Timer]
OnCalendar=*-*-* 09:00 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
```

**켜는 단계**(이것이 없으면 타이머는 영영 돌지 않고 journal 도 비어 있다 — 「갱신 실패 감지」가 설치되지
않은 채 조용한 초록이 된다):

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now bidvector-cert-check.timer
systemctl list-timers bidvector-cert-check.timer   # NEXT 가 비어 있지 않아야 한다
sudo systemctl start bidvector-cert-check.service  # 한 번 즉시 돌려 결과를 본다
```

확인: `journalctl -u bidvector-cert-check --since -2d`.

### 4.6 HSTS 값을 올릴 때

지금 값은 `max-age=86400`(하루)이고 `includeSubDomains`·`preload` 는 없다 — 되돌리기 어려운 쪽을 피한
출발값이다. **반입 뒤 2주 동안 인증서 갱신과 https 접속이 안정되면** 올리는 것을 검토한다(운영자 결정).

올릴 때 **고칠 자리는 넷**이다 — 선언 하나와 그것을 단언하는 게이트 셋:

| 자리 | 무엇 |
|---|---|
| `docker/proxy.prod.Caddyfile` | 실제로 보내는 값 |
| `tools/prod-proxy-sites-check.sh` · `tools/prod-health.sh` · `tools/prod-nginx-check.sh` | `EXPECT_HSTS` — 그 값을 단언한다 |

한쪽만 고치면 게이트가 붉어진다(fail-closed — 틀린 채 반입되지는 않는다). 넷을 같은 커밋에서 바꾸고
`prod-nginx-check.sh` 를 한 번 돌려 확인한다.

### 4.7 구매 인증서 — 운영 현행 (2026-11-02 첫 반입)

첫 반입은 §4.3~4.5(certbot) 대신 **운영자가 구매한 DV 인증서**(GoGetSSL, 기존 site 와 같은 방식)로 열었다.
그래서 §4.3·§4.5 의 certbot 발급·갱신 hook 은 쓰지 않는다 — 갱신은 **만료 전 운영자 수작업**이다.

1. 확인(배치 전): 키 ↔ 인증서 공개키 일치 · `openssl verify -untrusted <all.crt> <crt>` OK · SAN 에 `DNS:bidvector.kr`.
2. 배치: `/etc/gogetssl/bidvector.kr_<주문번호>.{all.crt,crt}.pem`(0644) · `….key.pem`(**0600**). 원본 사본의 키도 0600 으로.
3. 조각: §4.4 와 같이 렌더한 뒤 **인증서 두 줄만** 바꾼다 — `ssl_certificate` → `….all.crt.pem` ·
   `ssl_certificate_key` → `….key.pem`. 나머지 줄(헤더·유량 제한·XFF)은 그대로다. §4.1 은 **렌더 원본**을 재고
   손으로 바꾼 두 줄은 재지 않는다 — 그 두 줄은 배치 때 `sudo nginx -t` 와 배치 뒤 `prod-cert-check.sh` 가 거른다.
4. 감시: §4.5 의 감시 unit `ExecStart` 끝에 **`--min-days 30`** 을 붙인다 — 구매 인증서는 자동 갱신이 없으므로 21일이 아니라
   한 달 앞에서 붉게 한다(재발급·검증에 시간이 든다).
5. 교체: 새 파일을 **새 이름**(주문번호가 다르다)으로 놓고 조각의 두 줄을 바꾼 뒤 `sudo nginx -t && sudo systemctl
   reload nginx` → `./tools/prod-cert-check.sh bidvector.kr` 로 서빙 인증서의 남은 일수를 확인한다. 옛 파일은 그 뒤 지운다.

나중에 certbot 으로 옮길 때는 §4.2 → §4.3(staging 먼저) → §4.4 → §4.5 순서이고, 감시 임계는 기본값(21)으로 돌린다.

## 5. DNS 전환 체크리스트 (운영자)

1. 전환 **하루 전**: 기존 레코드가 있으면 TTL 을 300 으로 낮춘다.
2. `bidvector.kr` **A** 레코드 → host 의 공인 IPv4.
3. **AAAA 는 두지 않는다** — 조각의 443 은 IPv4 만 듣는다(IPv6 443 을 열면 그 주소족에서 기존 site 이름까지 받는다).
4. 선택: CAA — **실제 발급 기관**을 적는다. 구매 인증서(§4.7)를 쓰는 동안 `letsencrypt.org` 만 적으면 재발급이
   막힌다(기관의 CAA 식별자는 그 기관 안내를 따른다). 확신이 없으면 두지 않는다.
5. 확인: `dig +short bidvector.kr A` 가 공인 IP 하나 · `curl -sI http://bidvector.kr/` 가 301.
6. 그 뒤 §4.3 발급 → §4.4.

## 6. 인증서 장애 대응

| 증상 | 먼저 볼 것 | 조치 |
|---|---|---|
| exit 1 「남은 일수」 — **구매 인증서(§4.7, 현행)** | 만료일 `openssl x509 -enddate` | 재발급·구매 → §4.7-5 교체 |
| `prod-cert-check` exit 1 「남은 일수」 — certbot | `systemctl status certbot.timer` · `journalctl -u certbot` | `sudo certbot renew --dry-run` 으로 원인 → 고친 뒤 `sudo certbot renew` |
| exit 1 「SAN 에 … 없다」 | 다른 site 인증서가 나온다 — `ls /etc/nginx/sites-enabled/` 이름 정렬 | 조각 이름이 `zz-` 인지 · server_name |
| 파일은 갱신됐는데 서빙 인증서가 옛것 | reload hook 실행 여부 | `sudo nginx -t && sudo systemctl reload nginx` |
| HTTP-01 실패 | 80 이 이 host 에 오는지(Windows 포워딩·DNS) · `/.well-known/acme-challenge/` 가 301 이 아닌지 | §1.2 · §5 |
| exit 2 | 443 에 TLS 가 서지 않았다 | `sudo nginx -t` · `systemctl status nginx` |

발급 한도: 같은 이름의 실패를 반복하지 말고 `--staging` 으로 원인을 먼저 찾는다.

## 7. 되돌림

### 7.1 이미지 (마이그레이션 차이 0)

```bash
./tools/prod-rollback.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2           # dry-run
./tools/prod-rollback.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2 --apply
```

직전 release(`previous`)로 `prod-deploy.sh` 를 다시 부른다 — 같은 preflight·백업·헬스. 그때 「되돌림이 보았던
떠 있는 release」를 `--expect-running` 으로 함께 넘기고, 반입이 잠금을 다시 쥔 자리에서 대조해 그 사이 다른
반입이 끝났으면 멈춘다. **되돌림이 자동으로 넘긴다 — 운영자가 손으로 줄 일은 없다.**

> 반입·되돌림·**주기 러너**(§9)는 **프로젝트와 상태 디렉터리에 잠금**을 건다. 그래서 반입이 도는 동안에는
> dry-run 도 `exit 2` 로 거절된다 — 그때 「지금 무엇이 떠 있나」는 `cat /var/lib/bidvector-v2/current`(그리고
> 끝나지 않은 반입이면 `.../pending`)로 본다. 셋 다 기다리지 않으므로 교착은 생기지 않는다.
>
> 프로젝트 잠금의 자리는 `/run/lock/bidvector-v2/prod.<프로젝트>.lock` 으로 **고정**이다 — 환경변수에서
> 오지 않는다. `TMPDIR` 아래였던 앞 판은 그 변수가 다른 두 셸이 서로 다른 파일을 잠가 동시 반입이
> 열렸다. systemd unit 에 `PrivateTmp=yes` 를 붙여도 이 자리는 unit 안팎이 같은 파일을 본다 — 그 설정이
> 가리는 것은 `/tmp` 와 `/var/tmp` 뿐이다. 반대로 `ProtectSystem=strict` 처럼 `/run` 을 읽기 전용으로
> 만드는 설정을 더한다면 **이 디렉터리를 `ReadWritePaths=` 에 넣는다** — 없으면 잠금을 열지 못해
> 반입·되돌림은 `exit 2`, 주기 러너는 `exit 3` 이다.

> **반입·되돌림이 `exit 2` 인데 반입·되돌림이 돌고 있지 않다면 주기 러너를 본다.** 러너도 같은 잠금을
> 쥐고(§9.1), 매달리면 그동안 이 두 경로가 막힌다.
> ```bash
> fuser -v /run/lock/bidvector-v2/prod.bidvector-v2-prod.lock   # 누가 쥐고 있나
> systemctl status bidvector-notification-sweep                  # 러너면 여기 보인다
> sudo systemctl stop bidvector-notification-sweep               # 멈추면 fd 가 닫히고 잠금이 풀린다
> ```
> unit 의 `TimeoutStartSec=`(§9.2)이 상한을 지므로 이 손질이 필요한 경우는 상한 안에서 급한 때뿐이다.
> 러너를 멈춘 뒤에는 일회 컨테이너가 남았는지 본다(`docker ps --filter name=<프로젝트>-app-run`).

> **두 번째 되돌림의 대상은 「직전」이 아니라 「방금 떠난 쪽」이다.** A→B 뒤 되돌리면 B→A 가 되고
> `previous` 는 B 가 된다 — 거기서 또 되돌리면 **B 로 간다**(마이그레이션 차이가 없으면 가드도 울지
> 않는다). dry-run 이 기본이고 머리줄에 `== 되돌림 <어디서> → <어디로>` 를 찍는다 — **그 줄을 읽고**
> apply 한다. 더 옛 release 로 가려면 `prod-deploy.sh --release <그것>` 을 쓴다.

### 7.2 반입이 실패했을 때 (가장 급한 경우)

`prod-deploy.sh` 가 `up` 또는 헬스에서 실패하면 **컨테이너는 새 release 이고 마지막으로 성공한 것은 옛
release** 다. 그 사이 상태는 `<state>/pending` 에 남는다 — 실패 문면이 그 자리와 다음 명령을 함께 보인다.

```bash
./tools/prod-rollback.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2           # dry-run
./tools/prod-rollback.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2 --apply
```

되돌림은 `pending` 을 「떠 있는 것」으로 보고 마지막으로 성공한 release 로 간다. **새 release 가
마이그레이션을 실었으면 여기서도 exit 3 으로 멈춘다**(아래 「DB 를 넘는 경우」) — 그 검사는 `prod-deploy.sh --release <옛것>`
으로 직접 부르는 길에도 걸려 있어, 가드를 우회하는 경로가 없다.

첫 반입이 실패했다면 되돌릴 이전이 없다 — 스택을 내리고(`down`) 원인을 고친 뒤 다시 반입한다.

### 7.3 DB 를 넘는 경우 — 멈춘다

현재 release 가 직전에 없는 마이그레이션을 실었으면 `prod-rollback.sh` 는 **exit 3** 으로 멈추고 반입 직전 백업
경로를 보인다. 이미지만 되돌리면 옛 app 이 새 스키마 위에서 뜬다. 선택은 둘이고 **운영자 결정**이다:

- **forward-fix**(정본, 6B-2 runbook §1): 고친 release 를 새로 만들어 반입한다.
- **백업 복원**: 반입 직전 백업을 6B-2 runbook §3 절차로 복원한다 — **그 사이의 쓰기를 잃는다.** 이 slice 는 이
  경로를 자동화하지 않는다.

### 7.4 host nginx 조각

```bash
sudo rm /etc/nginx/sites-enabled/zz-bidvector_kr
sudo nginx -t && sudo systemctl reload nginx
```

기존 site 는 조각 배치 전과 같은 상태로 돌아간다(조각은 기존 파일을 고치지 않았다).

## 8. 백업

- 반입마다 직전 백업이 `/var/lib/bidvector-v2/backups/` 에 남는다(`index` 에 release 와 경로).
- 정기 백업: `./tools/db-backup.sh "$(docker compose -p bidvector-v2-prod ps -q postgres)" /var/lib/bidvector-v2/backups/<KST 시각>`
  을 host 타이머로. 보관 기간·용량은 운영자 결정이다.
- 복원 리허설은 6B-2 runbook §4 의 절차다(자기 생성 컨테이너에만 쓴다 — 운영 컨테이너를 건드리지 않는다).

## 9. 주기 실행 — 알림 훑기 러너

시간이 지나서 생기는 알림(투찰 마감 D-1·D-0, 서류 기한, 담은 공고의 정정)은 **상주 스케줄러가 아니라 일회 러너**가
한 번 훑어 적는다. 주기는 이 host 의 타이머가 진다 — **등록하지 않으면 그 알림들은 하나도 발화하지 않는다.**
러너 자체의 정본은 `docs/runbook/m7-7c3b-notification-sweep.md`(호출 모양 · 종료 코드 · 로그 · 끄는 법)이고,
여기에는 **운영 반입된 스택에서 그것을 어떻게 거는가**만 적는다.

**운영자 승인 항목**: 이 타이머를 켜는 것은 「시간 기반 알림을 실제로 쓰기 시작한다」는 뜻이다 — 반입 승인과 별개로
한 번 확인한다. 외부 발송은 없다(R-4) · 결과는 알림함과 journal 에만 남는다.

### 9.1 거는 법

호출은 **저장소의 `tools/prod-notification-sweep.sh`** 가 진다 — 반입과 같은 compose 파일 셋·같은
프로젝트 이름·같은 env 파일을 쓰고, **release 태그를 `<state>/current` 에서 읽는다**(env 파일에 넣지
않는다 — §1.3 이 그 키를 비워 두라고 적는다. 그래서 반입 뒤 갱신을 잊을 수 없다). 끝나지 않은 반입이
있으면 돌리지 않고 멈춘다 — 그때 떠 있는 것은 `current` 가 아니다.

러너는 **반입과 같은 이미지 불변식**도 거친다: `<state>/releases/<current>.manifest` 의 이미지 ID 와 지금
태그가 가리키는 ID 가 같아야 하고, 일회 컨테이너는 `--pull never` 로 뜬다. 누가 태그를 옮겼거나 태그가
로컬에 없으면 **훑지 않고 `exit 3`** 이다(반입이 막는 것을 러너만 통과시키는 길이 없다). 잠금도 반입과
같은 둘을 잡는다(§7.1 의 주) — 반입이 도는 중이면 훑지 않고 다음 주기로 미룬다. 훑기는 멱등이라 그래도 된다.

**이 스크립트는 사본으로 떼어 둘 수 없다** — compose 파일 셋을 읽으므로 `<저장소>`(§1.4b) 안에서
돈다. 인증서 감시(§「host nginx 조각」)가 단독 스크립트라 사본으로 떼어 둔 것과 다른 점이다. 그래서
이 unit 이 실행하는 내용은 **그 저장소에 쓸 수 있는 사람**이 정한다 — 레인들이 브랜치를 바꾸는 작업
트리를 `<저장소>` 로 쓰지 않는 이유다(§1.4b).

한 번 손으로 확인:

```bash
cd <저장소>
./tools/prod-notification-sweep.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2
```

### 9.2 주기 — 하루 두세 번

마감 두 창은 각각 **그날 하루**라, 하루 한 번으로는 그날 몫을 잃을 수 있다(러너 runbook §3). 권장은 KST 이른
아침과 낮이다. 창이 달력일 단위라 한 창에서 여러 번 돌아도 항목은 하나다.

```ini
# /etc/systemd/system/bidvector-notification-sweep.service
[Unit]
Description=bidvector 알림 훑기 (일회 러너)

[Service]
Type=oneshot
User=deploy
WorkingDirectory=<저장소>
ExecStart=<저장소>/tools/prod-notification-sweep.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2
# **상한을 반드시 둔다.** 러너는 훑는 내내 프로젝트·상태 잠금을 쥔다(§7.1 의 주). `Type=oneshot` 은
# 시작 시간 제한이 기본으로 꺼져 있으므로, 러너가 매달리면 잠금이 풀리지 않고 **반입과 되돌림이
# `exit 2` 로 거절된다** — 사고 중에 가장 필요한 두 경로다. 상한이 지나면 systemd 가 러너를 죽이고
# fd 가 닫히며 잠금이 풀린다.
TimeoutStartSec=15m
TimeoutStopSec=1m
```

```ini
# /etc/systemd/system/bidvector-notification-sweep.timer
[Unit]
Description=bidvector 알림 훑기 — 하루 두 번

[Timer]
OnCalendar=*-*-* 07:30 Asia/Seoul
OnCalendar=*-*-* 13:30 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
```

**실행 사용자**: `User=` 는 §1 에서 반입을 돌린 사용자와 **같아야 한다**(docker 소켓 접근). §1.3·§1.4 가 쓰는
`$USER` 가 이 host 에서는 `deploy` 다 — 다르면 두 자리를 함께 바꾼다.

### 9.3 켜고 확인

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now bidvector-notification-sweep.timer
systemctl list-timers bidvector-notification-sweep.timer   # NEXT 가 비어 있지 않아야 한다
sudo systemctl start bidvector-notification-sweep.service  # 한 번 즉시 돌려 결과를 본다
journalctl -u bidvector-notification-sweep -n 50
```

### 9.4 실패 감지

종료 코드는 러너 runbook §4 가 정본이다 — `0` 완료 · `1` 예외 · `2` 적지 못한 알림(`failed>0`). 여기에
`tools/prod-notification-sweep.sh` 자신의 **`3` 전제 실패**가 더해진다: env 파일을 읽지 못했다 · compose 판이
하한 미만이다 · 잠금을 열지 못했거나 반입·되돌림·다른 훑기가 도는 중이다 · 반입 기록이 없거나 손상됐다 ·
끝나지 않은 반입이 있다 · release manifest 가 없다 · 태그 ID 가 manifest 와 다르다 · app 설정 통로가 기대와
다르다. **`3` 은 앱 장애가 아니라 스택·설정 상태의 문제**이고, 원인은 그 run 의 마지막 줄에 그대로 적힌다.
외부 발송이 없으므로 **journal 이 유일한 창구**다. 주기 확인:

```bash
# 최근 이틀 안에 COMPLETE(0) 로 끝난 run 이 있는가
journalctl -u bidvector-notification-sweep --since -2d | grep -c 'exit=COMPLETE(0)'
# 0 이면: 타이머가 꺼졌거나(§9.3 의 list-timers) 매 run 이 실패했다(같은 로그의 cause=)
systemctl list-timers bidvector-notification-sweep.timer
```

「안 도는 것처럼 보일 때」의 진단은 러너 runbook §6 이다.

## 10. 메모리

| 서비스 | `mem_limit` | 리허설 실측(2026-10-31 · 정본은 evidence checklist 1.5 — 리허설을 다시 돌리면 여기도 같이 고친다) | 비고 |
|---|---|---|---|
| app | `1536m` | 기동 직후 300MiB · 인증 GET 300건(8 병렬) 뒤 323MiB | `BIDVECTOR_APP_JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70` — 힙 상한 약 1.05GiB |
| postgres | `1g` | 42 → 45MiB | **빈 DB** 의 수다 — 데이터가 쌓이면 늘어난다 |
| proxy | `128m` | 11 → 16MiB | |
| web | `64m` | 11 → 11MiB | |
| ml-serving | `1g` | 재지 않았다(기본 미기동) | `--profile ml` 로 띄울 때 먼저 잰다 |

실측은 빈 DB · 짧은 부하라 **상한을 낮출 근거가 아니다**. 승인된 안을 그대로 두고, 운영 중 `docker stats`
로 다시 본다. 앱 힙이 상한에 닿으면 컨테이너가 OOM 으로 재시작한다(`restart: unless-stopped`).

**위험**: 이 host 의 earlyoom 은 `--prefer '^(java)$'` 다. 개발 Gradle 빌드·다른 세션의 큰 프로세스가 메모리를
밀어 올리면 운영 app 이 먼저 죽는다 — `restart: unless-stopped` 가 되살리지만 그 사이 사이트는 502 다.
운영 반입 뒤에는 무거운 빌드 전 확인 셋에서 운영 app 의 몫(1.5GiB)을 여유로 함께 본다.

**운영자 판단 후보**(이 문서는 바꾸지 않는다): ⓐ earlyoom 의 avoid 에 `java` 를 넣으면 Gradle 까지 보호가 꺼진다 —
권하지 않는다 ⓑ 개발 빌드의 한도(`-Xmx`·workers)를 낮추는 것이 현실적이다 ⓒ 전역 지침의 「빌드 전 확인 셋」에 운영
app 여유를 더하는 문면.

## 11. 알려진 제한

1. **응답에 `Server: nginx` 이름이 보인다** — 버전은 감추지만 이름을 지우는 모듈이 없다.
2. **nginx → proxy 구간은 평문**이다 — host 루프백 안이고 publish 가 `127.0.0.1` 뿐이라는 전제(`tools/prod-proxy-sites-check.sh` 가 단언).
3. **host 위의 모든 프로세스는 proxy 에 직접 붙을 수 있다** — WSL2 의 localhost 전달로 Windows 쪽 프로세스도
   포함된다. 그 경로는 nginx 의 유량 제한을 지나지 않는다. 주소 헤더는 둘이 다르게 다뤄진다(하네스 실측):
   Caddy 는 신뢰하지 않는 출처의 `X-Forwarded-For` 를 **스스로 버리고** 접속 주소로 바꾸지만, `X-Real-IP` 는
   **그대로 통과시킨다**. 그래서 nginx 경로에서는 nginx 의 덮어쓰기가 `X-Real-IP` 위조를 막는 유일한 겹이고,
   루프백 직접 접속자는 `X-Real-IP` 를 위조할 수 있다. app 이 오늘 두 헤더 모두 읽지 않아 영향은 0 이다 —
   app 이 클라이언트 주소를 읽기 시작하면 Caddy 의 `trusted_proxies` 와 `X-Real-IP` 처리를 함께 정한다.
   또 그 결과 app 이 보는 `X-Forwarded-For` 는 클라이언트가 아니라 compose 망의 게이트웨이 주소다.
4. **유량 제한은 클라이언트 주소 단위**다 — 같은 공인 IP 뒤의 여러 사용자는 한 몫을 나눠 쓴다. app 레벨 제한은 후속 후보.
5. **DB 를 넘는 되돌림은 자동화하지 않는다**(§7 「되돌림 — DB 를 넘는 경우」).
6. **반입 묶음은 tar 가 아니라 태그 + manifest** 다 — 운영 host 가 개발 host 와 같은 기계라서다. 다른 host 로
   옮기면 `docker save`/`load` 경로를 새로 만들어야 한다. `docker image prune -a` 가 돌지 않는 이전 release 의
   이미지를 지우면 되돌림이 manifest 대조에서 멈춘다. **`<state>/releases/*.manifest` 도 지우지 않는다** —
   반입과 되돌림 **양쪽**이 읽는다(마이그레이션 가드가 떠 있는 release 의 manifest 를 못 읽으면 판정 불가로 멈춘다).
7. **TLS 와 인증서의 자동 검증은 대역까지**다 — 하네스는 자체 서명 인증서로 nginx 조각을 재고, certbot·Let's Encrypt
   발급은 운영자 수작업(§4.3)에서 처음 실측된다.
8. **ml-serving 은 기본 미기동인데도 release 와 반입 preflight 가 그 이미지를 요구한다.** 묶음을 네 이미지로
   두는 선택이다 — 뒤에 `--profile ml` 로 띄울 때 그 바이트의 출처가 같은 release 이고, 네 이미지 모두 「태그가
   옮겨졌는가」를 보게 된다. 대가는 **실패 표면 하나**다: 누가 그 이미지만 지우면 서비스에 필요 없는데도 반입이
   멈춘다. 그때는 manifest 의 `image.ml-serving` ID 로 태그를 다시 달거나 같은 커밋에서 release 를 다시 만든다.
9. **시간이 지나서 생기는 알림은 §「주기 실행」의 타이머를 켜야 발화한다.** 그런 알림은 상주 스케줄러가 아니라
   일회 러너가 훑어 적는 구조라, 타이머를 켜지 않으면 **반입 직후 상태에서 하나도 발화하지 않는다**(요청으로
   생기는 알림은 영향 없음). 켜는 것 자체가 운영자 승인 항목이다. 그 절차는 §「주기 실행」에 있고, 러너의 호출
   모양·종료 코드·진단은 `docs/runbook/m7-7c3b-notification-sweep.md` 가 정본이다.
10. **운영 자리의 스크립트 사본은 자동으로 갱신되지 않는다.** §「host nginx 조각」의 인증서 감시는
   `/usr/local/lib/bidvector-v2/` 의 사본을 돌린다(작업 트리를 가리키지 않기 위한 선택). 그 스크립트가 바뀐 release 를
   반입하면 **사본을 다시 깔아야** 한다 — §「반입」의 마지막 단계가 그것을 묻는다. §「주기 실행」의 훑기 러너는 사본이
   아니라 `<저장소>` 에서 직접 돈다(compose 파일 셋을 읽으므로, §9.1).
11. **이 host 안에서 공인 주소로는 자기 site 에 닿지 않는다**(헤어핀 NAT — 2026-11-02 실측: 기존 site 도 같은
   시간 초과). host 의 검사 도구는 이 경로를 쓰지 않는다 — `prod-health.sh --public` 과 `prod-cert-check.sh` 는
   `127.0.0.1:443` + SNI 로 붙어 host nginx 까지를 잰다. **공인 경로(DNS → 공유기 → Windows 포워딩 → WSL)는 그
   도구들이 재지 못하므로** 바깥 망(휴대폰 LTE 등)의 브라우저나 `curl -sI https://bidvector.kr/` 로 확인한다.

## 12. 운영 계정 — 처음 한 번 · 비밀 문구 교체

가입 화면·HTTP endpoint 는 없다. 계정은 **운영자 명령**으로 만든다(M7/7D). 떠 있는 서비스를 내리지 않고 **일회
컨테이너**를 띄운다. 이 명령은 **DB 소유자 연결**로 쓰므로, 훑기 러너(§9.1)와 **같은 가드**를 같은 함수로 먼저
지난다 — compose 호출 단일 출처(`tools/prod-env.sh`: 템플릿 키·`COMPOSE_*` unset · 파일 셋 · 프로젝트 고정) ·
잠금 둘 · 반입 기록 · 끝나지 않은 반입 · manifest 이미지 ID · 설정 통로. 하나라도 어긋나면 `exit 3` 으로 멈추고 아무것도
쓰지 않는다(`exit 1` 은 앱이 낸 「계정이 이미 있다」·「계정이 없다」뿐이다). 비밀 문구는 잠금을 잡기 전에 받는다 —
프롬프트 앞에서 자리를 비워도 반입·되돌림·훑기를 막지 않는다.

블록 전체가 **서브셸**(`( … )`)이다 — 실패해도 운영자 셸이 닫히지 않고, 비밀 문구 변수는 서브셸과 함께 사라진다.
bash 에서 붙여 넣는다.

```bash
cd <저장소>
(
  set -euo pipefail
  read -rsp '비밀 문구: ' BIDVECTOR_ACCOUNT_INITIAL_VALUE; echo   # 잠금을 잡기 **전에** 받는다(입력을 기다리며 잠금을 쥐지 않게)
  . tools/prod-env.sh
  export BIDVECTOR_ACCOUNT_INITIAL_VALUE
  ENVF=/etc/bidvector-v2/prod.env S=/var/lib/bidvector-v2
  [ -r "$ENVF" ] || { echo "env 파일을 읽지 못했다: $ENVF"; exit 3; }
  _prod_compose_version_ok || { echo "$_PROD_COMPOSE_VER_ERR"; exit 3; }
  _prod_project_lock "$BIDVECTOR_PROD_PROJECT" || { echo "$_PROD_LOCK_ERR"; exit 3; }
  _prod_lock "$S/.lock" "이 상태 디렉터리" || { echo "$_PROD_LOCK_ERR"; exit 3; }
  rel="$(cat "$S/current" 2>/dev/null || true)"; [ -n "$rel" ] || { echo "반입 기록이 없다"; exit 3; }
  _prod_valid_tag "$rel" || { echo "반입 기록이 손상됐다"; exit 3; }
  p="$(cat "$S/pending" 2>/dev/null || true)"; [ -z "$p" ] || [ "$p" = "$rel" ] || { echo "끝나지 않은 반입: $p"; exit 3; }
  _prod_images_match "$S/releases/$rel.manifest" "$rel" || { echo "$_PROD_IMAGE_ERR"; exit 3; }
  export BIDVECTOR_RELEASE_TAG="$rel"
  C=("${BIDVECTOR_PROD_COMPOSE[@]}" --env-file "$ENVF")
  cfg="$("${C[@]}" config --format json 2>/dev/null)" || { echo "운영 compose 를 해석하지 못했다"; exit 3; }
  _prod_config_surface_ok "$cfg" || { echo "$_PROD_SURFACE_ERR"; exit 3; }
  "${C[@]}" run --rm --no-deps --pull never \
    -e SPRING_MAIN_WEB_APPLICATION_TYPE=none -e BIDVECTOR_ACCOUNT_INITIAL_VALUE \
    app --bidvector.account.mode=create --bidvector.account.login=<로그인 이름>
)
```

- 결과 줄: `account command=create result=created login=…`. 계정이 **하나라도** 있으면(이름과 무관)
  `result=refused cause=account-exists`(exit 1) — 둘째 계정은 이 명령으로 만들 수 없다.
- **비밀 문구 교체**는 `mode=rotate` — 그 계정의 세션을 전부 폐기하고(같은 트랜잭션) 연속 실패 잠금도 푼다
  (실패 횟수 0 · 잠금 시각 해제). 잠겨서 못 들어갈 때의 복구도 이것이다. 계정이 없으면 `cause=account-absent`.
- 값은 **환경변수로만** 넘긴다 — `-e NAME` 은 이름만 싣고 값은 셸 env 에서 읽는다(argv·프로세스 표·셸 이력에 값이
  없다). 일회 컨테이너가 떠 있는 수 초 동안은 `docker inspect` 로 그 env 가 보인다(`--rm` 으로 끝나면 지워진다) —
  docker 소켓을 가진 사람은 이미 DB 에 닿으므로 늘어나는 권한은 없다. 채팅·저장소·로그에 적지 않는다.
- `SPRING_MAIN_WEB_APPLICATION_TYPE=none` 은 선택이 아니다 — 끄지 않으면 일회 컨테이너 안에 무인증 `POST /api/session`
  을 포함한 API 표면이 수 초 뜬다(7D checklist 「운영자 명령 사용법」).
- `--no-deps` 는 postgres 가 이미 떠 있다는 전제다(반입 뒤).
- 로그인 실패·연속 실패 잠금은 응답에서 구분되지 않는다(401 하나).

---

## 13. 알림 실발송 (Telegram) 활성화 — 절차만, 실행은 운영자 확인 항목

M9/STR12 가 발송 채널을 들였다. **이 문서를 쓴 slice 는 아래를 실행하지 않았다** — 실 토큰 투입과 timer
설치는 「시간 기반 알림 타이머를 켠다」(§9)와 같은 등급의 운영자 확인 항목이다. 켜는 순간부터 **운영자의
휴대폰으로 메시지가 나간다.**

판정 구조(환경 → 모드 → claim → 종료 코드, 기동 거부의 뜻)의 정본은
`docs/runbook/m6-6e-operations.md` §5 다. 여기에는 **어떻게 켜는가**만 적는다.

### 13.1 봇 만들기 (Telegram 쪽, 한 번)

1. Telegram 앱에서 `@BotFather` 와 대화해 봇을 만든다 — 받는 값이 **봇 자격**(BotFather 는 이것을
   「token」이라 부른다)이다. **그 값을 채팅·저장소·로그·전달 메시지에 남기지 않는다.**
2. 알림을 받을 대화(1:1 또는 그룹)에서 그 봇에게 아무 메시지나 한 번 보낸다 — 봇은 **먼저 말을 걸 수
   없다**(Bot API 규칙). 이 한 번이 없으면 첫 발송이 `403` 으로 거부된다.
3. 그 대화의 **chat id** 를 얻는다. 가장 좁은 방법은 `getUpdates` 한 번이고, 그 호출은 자격을 URL 에
   싣는다 — 명령 문자열·history 에 남지 않는 형태로 돌린다(자격을 서브셸 변수로 읽어 그 프로세스
   환경에만 넘긴다, §5.4 의 서비스 키 관례와 같다).

### 13.2 env 두 값 + 링크 도메인

세 값이 **전부 있어야** `Live` 모드 환경의 러너가 뜬다(없으면 기동 실패, 메시지가 빈 칸 이름을 말한다).

```ini
# relay 러너 unit 의 env 파일 — root 소유 0600, **저장소 밖**
# 예: /etc/bidvector-v2/relay.env
BIDVECTOR_NOTIFICATION_TELEGRAM_BOT_CREDENTIAL=   # §13.1 ①
BIDVECTOR_NOTIFICATION_TELEGRAM_CHAT_ID=          # §13.1 ③
BIDVECTOR_NOTIFICATION_SITE_BASE_URL=             # 예: https://<운영 도메인> — 끝에 `/` 없이
```

**`docker/prod.env.template` 에는 넣지 않는다.** 그 파일의 키 집합은 `tools/prod-env-keys-check.sh` 가
compose 세 파일의 `${VAR` 참조 집합과 **등식**으로 재고, relay 는 compose 서비스가 아니라 일회
러너다 — 템플릿에 넣으면 compose 가 요구하지 않는 죽은 키가 되어 그 검사가 붉어진다.

바꿀 수 있는 나머지 넷은 기본이 있다(건드릴 이유가 거의 없다):
`…_TELEGRAM_BASE_URL`(기본 `https://api.telegram.org`) · `…_TELEGRAM_ROUTE_KEY`(기본
`telegram-primary`) · `…_TELEGRAM_CONNECT_TIMEOUT`(기본 `5s`) · `…_TELEGRAM_RESPONSE_TIMEOUT`(기본 `10s`).

### 13.3 일회 러너 — relay 와 평가 커밋

둘 다 `mode=once` 일회 러너다(상주 스케줄러가 없다 — `OPEN-6F10-SCHEDULER`). **순서가 있다**: 평가
커밋이 `outbox` 에 행을 남기고, relay 가 그 행을 집어 보낸다. 같은 주기에 둘을 거는 쪽이 자연스럽다.

```ini
# /etc/systemd/system/bidvector-notification-relay.service
[Unit]
Description=bidvector 알림 relay (일회 러너)

[Service]
Type=oneshot
User=deploy
WorkingDirectory=<저장소>
EnvironmentFile=/etc/bidvector-v2/relay.env
# 호출 모양은 반입된 스택의 일회 컨테이너다 — §9.1 의 훑기 러너와 같은 전제(같은 compose 파일 셋 ·
# 같은 프로젝트 이름 · release 태그는 <state>/current 에서 읽는다 · `--pull never`).
#   bidvector.relay.mode=once · bidvector.relay.environment=production
#   bidvector.relay.owner=<운영자 id> · bidvector.relay.channel=telegram · bidvector.relay.claim-limit=50
#   SPRING_MAIN_WEB_APPLICATION_TYPE=none
ExecStart=<저장소>/tools/prod-notification-relay.sh --env-file /etc/bidvector-v2/prod.env --state-dir /var/lib/bidvector-v2
# 상한을 반드시 둔다(§9.1 과 같은 이유) — 러너는 도는 내내 프로젝트·상태 잠금을 쥔다.
TimeoutStartSec=15m
TimeoutStopSec=1m
```

```ini
# /etc/systemd/system/bidvector-notification-relay.timer
[Unit]
Description=bidvector 알림 relay — 하루 두 번

[Timer]
OnCalendar=*-*-* 07:40 Asia/Seoul
OnCalendar=*-*-* 13:40 Asia/Seoul
Persistent=true

[Install]
WantedBy=timers.target
```

**`tools/prod-notification-relay.sh` 는 아직 없다** — §9.1 의 훑기 스크립트와 같은 모양으로 만드는 것이
활성화 slice 의 몫이다(이 slice 는 코드와 절차까지만 낸다). 그때까지 손으로 돌리려면 §9.1 의 스크립트를
읽고 같은 전제(잠금 · manifest 대조 · `--pull never`)를 지켜야 한다 — 그 전제를 건너뛰면 반입이 막는
것을 러너만 통과시키는 길이 생긴다.

### 13.4 첫 발송 확인

1. **먼저 억제 환경에서 한 번** — `bidvector.relay.environment=staging` 으로 돌린다. 정책표가 그 환경에
   `DryRun` 을 붙이므로 claim 0 이고 종료 코드는 `4` `ENV_SUPPRESSED` 다. 여기서 확인하는 것은
   「러너가 뜨고 DB·잠금에 닿는다」뿐이다(발송은 0).
2. **다음에 `production` 으로 한 번** — 종료 코드 `0` 과 마침 줄의 `delivered=` 계수를 본다. 메시지가
   실제로 도착했는지는 **Telegram 앱에서** 확인한다(장부는 발송을 선언하지만 읽음을 알지 못한다).
3. 어긋나면 §13.6.

### 13.5 끄는 법

```bash
sudo systemctl disable --now bidvector-notification-relay.timer
systemctl list-timers bidvector-notification-relay.timer   # 비어 있어야 한다
```

타이머만 끄면 **행은 `outbox` 에 `PENDING` 으로 쌓인다**(잃지 않는다) — 다시 켜면 그때 나간다. 90일
보존 러너가 `DELIVERED` 행만 파기하므로 미발송 행은 그 대상이 아니다. 더 센 차단이 필요하면 env 의 세
값을 비운다 — 그러면 `production` 러너가 **기동 실패**한다(claim 0, 행 보존).

### 13.6 어긋날 때

| 증상 | 뜻 | 처방 |
|---|---|---|
| 기동 실패, 메시지가 빈 칸 이름을 말한다 | env 세 값 중 결손 | §13.2 를 채운다 |
| 종료 코드 `4` | 그 환경이 발송 가능 모드가 아니다 | `environment=production` 인지 본다(§5 사상표) |
| 마침 줄 `failed>0` | Telegram 이 **확정 거부**했다 — chat id 가 틀렸거나(400) 봇이 차단됐거나(403) 자격이 무효(401) | §13.1 ②③ 를 다시 본다. 그 행은 되살아나지 않는다 — 설정을 고치면 **다음** 판정이 새 행을 낳는다 |
| 마침 줄 `isolated>0` | 받았는지 **모른다**(timeout · 5xx · 429 · 응답 해독 불가) — 재시도하지 않는다 | Telegram 앱에서 중복·누락을 눈으로 본다. 모호를 재발송하지 않는 것이 at-most-once 설계다 |
| 메시지에 「저장된 공고 정보가 없습니다」 | 그 공고 행이 파기(90일)됐거나 아직 수집되지 않았다 | 정상 거동이다. 알림 자체를 버리지 않고 **사실을 주장하지 않는** 본문을 보낸다 |

**로그에 자격·chat id 원문이 남지 않는다** — 발송 어댑터는 로거를 쥐지 않고, 결과 타입에 provider
문자열을 담을 칸이 없다. 그래서 「무엇이 거부됐는지」는 계수와 이 표로 읽는다(원문을 되짚는 경로는 없다).
