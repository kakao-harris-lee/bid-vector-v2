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
                                                     │  · TLS(certbot) · 80→301 · /api/session limit_req · XFF 덮어쓰기
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
   이 host 의 earlyoom 은 `java` 를 먼저 죽인다 — 운영 app 이 첫 희생자다(§9).
2. **Windows 포트**: 기존 공개 site 가 이미 공개 중이므로 80·443 의 Windows → WSL 경로가 있을 가능성이 높다.
   **확인 → 없으면 설정** 순서로 한다: Windows 의 포트 포워딩(또는 WSL 네트워킹 모드) · Windows 방화벽 inbound
   80·443 · 공유기/외부 방화벽 포워딩. WSL 재시작 뒤 포워딩이 사라지지 않는지도 확인한다.
3. **운영 env 파일**: `docker/prod.env.template` 을 저장소 **밖**으로 복사해 값을 채운다.
   ```bash
   sudo install -d -m 700 /etc/bidvector-v2
   sudo install -m 600 -o "$USER" docker/prod.env.template /etc/bidvector-v2/prod.env
   ```
   - 자격 값(PG 암호 · 운영자 자격)은 운영자가 생성한다(`openssl rand -hex 24` 등). 저장소·채팅·로그에 적지 않는다.
   - `BIDVECTOR_RELEASE_TAG` 는 비워 둔다(반입 스크립트가 준다).
   - `BIDVECTOR_PROXY_DOMAIN=bidvector.kr` · `BIDVECTOR_PROXY_HTTPS_PORT=28443`.
   - `BIDVECTOR_APP_JDBC_URL=jdbc:postgresql://postgres:5432/<BIDVECTOR_PG_DB 값>`.
   - 메모리 키는 §9 표.
   - 확인: `./tools/prod-env-keys-check.sh --env-file /etc/bidvector-v2/prod.env`
4. **상태 디렉터리**(release manifest · 백업 · 이력): `sudo install -d -m 700 -o "$USER" /var/lib/bidvector-v2`
5. **trivy 설치**(취약점 게이트 — release 가 요구한다. 없으면 release 는 판정 불가로 멈춘다).
6. **certbot 설치**: `sudo apt-get install certbot` — 패키지가 갱신 타이머(`certbot.timer`)를 함께 설치한다.

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

preflight 가 보는 것: env 파일(저장소 밖 · 0600 · 키 집합) · manifest 와 이미지 ID 등식 · compose ≥ 2.24 · 루프백
포트 · 디스크. apply 는 **반입 직전 DB 백업**(postgres 가 돌고 있을 때) → `up -d --wait` → `tools/prod-health.sh`.
이 단계까지는 public 노출이 없다 — 루프백뿐이다.

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
```

reload 는 같은 nginx 를 쓰는 기존 site 에도 걸린다(graceful — 연결을 끊지 않는다). hook 이 `nginx -t` 를 먼저
보므로 깨진 설정으로 reload 하지 않는다.

**인증서 감시 타이머**(갱신 실패 감지): 하루 한 번 `./tools/prod-cert-check.sh bidvector.kr` 를 돌린다(남은
21일 미만이면 exit 1). 외부 발송은 없다(R-4) — 결과는 journal 에서 본다.

```ini
# /etc/systemd/system/bidvector-cert-check.service
[Service]
Type=oneshot
WorkingDirectory=/home/deploy/project/bid-vector-v2
ExecStart=/home/deploy/project/bid-vector-v2/tools/prod-cert-check.sh bidvector.kr
# /etc/systemd/system/bidvector-cert-check.timer
[Timer]
OnCalendar=*-*-* 09:00 Asia/Seoul
Persistent=true
[Install]
WantedBy=timers.target
```

확인: `journalctl -u bidvector-cert-check --since -2d`.

## 5. DNS 전환 체크리스트 (운영자)

1. 전환 **하루 전**: 기존 레코드가 있으면 TTL 을 300 으로 낮춘다.
2. `bidvector.kr` **A** 레코드 → host 의 공인 IPv4.
3. **AAAA 는 두지 않는다** — 조각의 443 은 IPv4 만 듣는다(IPv6 443 을 열면 그 주소족에서 기존 site 이름까지 받는다).
4. 선택: CAA `0 issue "letsencrypt.org"`.
5. 확인: `dig +short bidvector.kr A` 가 공인 IP 하나 · `curl -sI http://bidvector.kr/` 가 301.
6. 그 뒤 §4.3 발급 → §4.4.

## 6. 인증서 장애 대응

| 증상 | 먼저 볼 것 | 조치 |
|---|---|---|
| `prod-cert-check` exit 1 「남은 일수」 | `systemctl status certbot.timer` · `journalctl -u certbot` | `sudo certbot renew --dry-run` 으로 원인 → 고친 뒤 `sudo certbot renew` |
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

직전 release(`previous`)로 `prod-deploy.sh` 를 다시 부른다 — 같은 preflight·백업·헬스.

### 7.2 DB 를 넘는 경우 — 멈춘다

현재 release 가 직전에 없는 마이그레이션을 실었으면 `prod-rollback.sh` 는 **exit 3** 으로 멈추고 반입 직전 백업
경로를 보인다. 이미지만 되돌리면 옛 app 이 새 스키마 위에서 뜬다. 선택은 둘이고 **운영자 결정**이다:

- **forward-fix**(정본, 6B-2 runbook §1): 고친 release 를 새로 만들어 반입한다.
- **백업 복원**: 반입 직전 백업을 6B-2 runbook §3 절차로 복원한다 — **그 사이의 쓰기를 잃는다.** 이 slice 는 이
  경로를 자동화하지 않는다.

### 7.3 host nginx 조각

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

## 9. 메모리

| 서비스 | `mem_limit` | 비고 |
|---|---|---|
| app | `1536m` | `BIDVECTOR_APP_JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=70` |
| postgres | `1g` | |
| proxy | `128m` | |
| web | `64m` | |
| ml-serving | `1g` | 기본 미기동(`--profile ml`) |

값은 반입 리허설의 RSS 실측으로 정했다(evidence `reports/evidence/m7/7f2/checklist.md`).

**위험**: 이 host 의 earlyoom 은 `--prefer '^(java)$'` 다. 개발 Gradle 빌드·다른 세션의 큰 프로세스가 메모리를
밀어 올리면 운영 app 이 먼저 죽는다 — `restart: unless-stopped` 가 되살리지만 그 사이 사이트는 502 다.
운영 반입 뒤에는 무거운 빌드 전 확인 셋에서 운영 app 의 몫(1.5GiB)을 여유로 함께 본다.

**운영자 판단 후보**(이 문서는 바꾸지 않는다): ⓐ earlyoom 의 avoid 에 `java` 를 넣으면 Gradle 까지 보호가 꺼진다 —
권하지 않는다 ⓑ 개발 빌드의 한도(`-Xmx`·workers)를 낮추는 것이 현실적이다 ⓒ 전역 지침의 「빌드 전 확인 셋」에 운영
app 여유를 더하는 문면.

## 10. 알려진 제한

1. **응답에 `Server: nginx` 이름이 보인다** — 버전은 감추지만 이름을 지우는 모듈이 없다.
2. **nginx → proxy 구간은 평문**이다 — host 루프백 안이고 publish 가 `127.0.0.1` 뿐이라는 전제(`tools/prod-proxy-sites-check.sh` 가 단언).
3. **host 위의 모든 프로세스는 proxy 에 직접 붙을 수 있다** — WSL2 의 localhost 전달로 Windows 쪽 프로세스도
   포함된다. 그 경로는 nginx 의 유량 제한을 지나지 않는다. app 이 오늘 XFF 를 읽지 않아 주소 위조의 영향은 0 이다.
4. **유량 제한은 클라이언트 주소 단위**다 — 같은 공인 IP 뒤의 여러 사용자는 한 몫을 나눠 쓴다. app 레벨 제한은 후속 후보.
5. **DB 를 넘는 되돌림은 자동화하지 않는다**(§7.2).
6. **반입 묶음은 tar 가 아니라 태그 + manifest** 다 — 운영 host 가 개발 host 와 같은 기계라서다. 다른 host 로
   옮기면 `docker save`/`load` 경로를 새로 만들어야 한다. `docker image prune -a` 가 돌지 않는 이전 release 의
   이미지를 지우면 되돌림이 manifest 대조에서 멈춘다.
7. **TLS 와 인증서의 자동 검증은 대역까지**다 — 하네스는 자체 서명 인증서로 nginx 조각을 재고, certbot·Let's Encrypt
   발급은 운영자 수작업(§4.3)에서 처음 실측된다.
