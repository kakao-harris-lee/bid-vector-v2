# M7/7F-local — 실행 명령과 종료 코드

**acceptance 정의**(계약 「acceptance」절)

- `.github/workflows/ci.yml` 의 **`container` job 명령 그대로** + 새 스모크. 이 slice 가 그 job 에
  스모크 step(S-26)을 더했으므로 **둘은 한 집합**이다 — job 전건을 돌린 것이 acceptance 다.
- `check` job 은 돌지 않는다 — `tools/one-command-check.sh` 를 **바꾸지 않았다**(그 스크립트가 축어로
  따르는 것은 `check`·`ml-engine`·`web` 세 job 이고 컨테이너 축인 이 job 은 그 안에 없다).
  **한 축만 예외다**: evidence 커밋은 `check` 안 `leakPatternGate` 의 입력(`reports/evidence/`)을
  건드린다. 그 축은 아래 비밀값 스캔 둘째 명령과 그 게이트 실행으로 따로 쟀고, 마지막 HEAD 의
  `check` 전건은 verifier 가 그 HEAD 에서 직접 잰다.
- `web` job 은 생략한다 — `web/` 입력 변경 0(계약 acceptance 절).
- job step 은 **파일에서 뽑아** 순서대로 돌렸다(손으로 옮겨 적으면 「명령 그대로」가 깨진다).
  `uses:` step(체크아웃·setup-java·setup-gradle·artifact 업로드)만 CI 전용으로 건너뛰었고,
  러너가 하는 `$GITHUB_ENV`·`$GITHUB_PATH` 흡수는 같은 형태로 재현했다.

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의 post-state 를
담을 수 없다. 그 한 칸은 verifier 와 PR 조치 코멘트가 갖는다.

---

## 2026-10-16 — 새 스모크 단독 (산출물 4, 착수 라운드)

- cmd: `./tools/local-tls-smoke.sh`
- exit: 0
- 핵심 결과: 검사 블록 전부 통과 — 셸·번들 자산·SPA fallback · `/api` 무인증 401·인증 200 ·
  우회 ①~⑤ · 자격 값 로그 0줄 · 정리 뒤 컨테이너·볼륨·네트워크 잔여 0(블록 목록은 스크립트의
  `_step` 문면이 정본이다)

## 2026-10-19 — `container` job 전건 (acceptance, 수정 라운드 r1 뒤)

- cmd: job 의 run step 을 **파일에서 뽑아** 순서대로(러너가 하는 job 수준 env · `$GITHUB_ENV`·
  `$GITHUB_PATH` 흡수 · `$RUNNER_TEMP` 를 재현. `uses:` step 넷은 CI 전용이라 생략)
- exit: 0
- 핵심 결과: **run step 22개 전부 exit 0**(종말 줄 `ALL STEPS exit=0`). 이 장비 소요 **211초**(캐시가
  더운 상태 — CI 의 차가운 러너 수를 대신하지 못한다. `timeout-minutes: 30` 여유는 첫 CI 실행의 실측으로).
- **step 수: 앞 라운드 23 → 22.** 줄어든 이유는 하나다 — cr M-5 로 이미지 빌드 둘(S-21c·S-21d)이
  **빌드 진입점 하나**(S-21c = `./tools/local-tls-build.sh`, compose 가 두 이미지를 만든다)로 합쳐졌다.
  base(`77850903`)의 16 과 비교하면 이 slice 가 더한 것은 **여섯**이다.

| # | step | exit | 소요 |
|---|---|---|---|
| 1 | DB 접속 값 생성 | 0 | 0s |
| 2 | 운영자 자격 값 생성 | 0 | 0s |
| 3 | 이미지 빌드 (S-21) | 0 | 2s |
| 4 | 앱 배포물 빌드 (S-21a) | 0 | 7s |
| 5 | 앱 이미지 빌드 (S-21b) | 0 | 2s |
| 6 | **web·프록시 이미지 빌드 (S-21c)** | 0 | 2s |
| 7 | 이미지 위생 — ml-serving (S-22a) | 0 | 6s |
| 8 | 이미지 위생 — 앱 (S-22b) | 0 | 13s |
| 9 | **이미지 위생 — web (S-22b2)** | 0 | 12s |
| 10 | **이미지 위생 — 프록시 (S-22b3)** | 0 | 12s |
| 11 | 앱 이미지 거부 스모크 (S-22c) | 0 | 2s |
| 12 | install trivy | 0 | 2s |
| 13 | 이미지 취약점 — ml-serving (S-22d) | 0 | 14s |
| 14 | 이미지 취약점 — 앱 (S-22e) | 0 | 45s |
| 15 | **이미지 취약점 — web (S-22e2)** | 0 | 10s |
| 16 | **이미지 취약점 — 프록시 (S-22e3)** | 0 | 10s |
| 17 | 로컬 환경 기동 (S-23) | 0 | 13s |
| 18 | 컨테이너 스모크 (S-23b) | 0 | 4s |
| 19 | 백업·복원·되돌림 리허설 (S-23c) | 0 | 26s |
| 20 | 실 Kotlin gateway ↔ 실 Python 서버 (S-24) | 0 | 7s |
| 21 | 로컬 환경 정리 (S-25) | 0 | 1s |
| 22 | **로컬 패키징 스모크 (S-26)** | 0 | 26s |

## 2026-10-19 — 수정 라운드 r1: 지적별 초록과 「대신」 변이

지적마다 **구현 → 스모크 초록 → 커밋 → 변이**로 갔다. 변이는 전부 버릴 clone 에서 하고 사본에서
복원했으며(`git checkout --` 미사용) 복원 뒤 `git diff --numstat` 빈 출력을 확인했다.

| 지적 | 초록 | 「대신」 변이 | 변이 결과 |
|---|---|---|---|
| cr M-1 `(healthy)` | 스모크 0 | 프록시 healthcheck 가 엉뚱한 포트를 묻게 → unhealthy 확정 | **1** 「proxy 가 … 수렴하지 않았다」(ps 줄 `proxy: Up 2 minutes (unhealthy)`). 네 상태 × 옛/새 술어 표에서 `(unhealthy)` 가 옛 MATCH → 새 no-match |
| vr F-1 망 분리 | 스모크 0 | verifier 의 변이 그대로 — web 에 `/healthz` → `reverse_proxy app:<관리포트>`(numstat 9/2) | **1** 「web 의 상류 집합이 비어 있지 않다: [app:80]」. **그 변이 이미지로 verifier 의 탐침을 직접 재현**: `https://…/healthz` → **502 · 관리 표지 부재**(앞 판은 200 `UP`) — 망 축이 스모크 단언과 **독립으로** 막는다 |
| vr F-2 키 재료 | 위생 web·proxy 0 | `/data/caddy/pki/authorities/local/` 에 구운 키 / `/etc/caddy` 에 구운 키 | **1** · **1**(앞 판은 `/data` 쪽이 **0** 이었다). 제외 접두를 `/` 로 넓혀 축을 끄려는 변이 → **1**「대상 0 … 판정 불가」, 범위를 상대 경로로 → **2**(정책 오류) |
| cr M-2·M-3 · vr F-3 헤더 | 스모크 0 | 값 약화 셋(`unsafe-url`·`ALLOWALL`·CSP 에 `unsafe-inline`+`unsafe-eval`, numstat 3/3) / `-Server` 제거 | **1** 「/ 의 Referrer-Policy 값이 선언과 다르다」 · **1** 「/ 응답에 Server 헤더가 1개 남아 있다」(둘 다 앞 판은 0) |
| cr M-4 코드↔본문 짝 | 스모크 0 | 합성 조합(무인증=셸 아닌 200 · 인증=셸 200) | 옛 술어 **PASS** · 새 술어 **FAIL** |
| cr M-5 빌드 단일화 | 스모크 0 · 빌드 진입점 단독 0 | 게이트가 판정한 태그가 재빌드 결과와 다른 바이트를 가리키게(다른 이미지를 같은 태그로) | **1** 「재빌드로 이미지 ID 가 바뀌었다 …」 |
| vr F-4 평문 | 스모크 0(평문 `/`·`/api/strategy` 둘 다 400) | 같은 포트에 평문 리다이렉트 래퍼 추가 | **1** 「평문 / 가 308 로 리다이렉트됐다 …」(앞 판은 0) |
| cr L-2 관리 포트 | 스모크 0 | 해석된 설정에 `BIDVECTOR_APP_MANAGEMENT_PORT=8080` | 옛 술어 **셋**(거짓 실패) · 새 술어 **하나**. 기본값 18081 에서는 둘 다 하나 |
| cr L-3 `no-new-privileges` | 스모크 0 | — (해석된 설정 단언, 지우는 편집이 바로 붉어진다) | — |
| cr L-8 문면 | 스모크 0 | — (문면만) | — |
| vr F-7 `root:root` | 스모크 0 | 형태 일곱 × 옛/새 술어 | `root:root` 가 **PASS→FAIL**, 비-root 형태는 둘 다 PASS |
| cr L-1 = vr F-6 정책 자리 | 취약점 web·proxy·app 전부 0 | — (거동 무관: 키·값 정렬 비교가 앞뒤 **바이트 동일**, 순서만 바뀜) | — |

**위생 게이트 재실측**(r1 의 새 축 포함) — web exit 0: 금지-실행파일 8 · 필수 1 · `/srv` 3(하한 2) ·
**키재료 대상 117(하한 81) · 패턴 5건 · 적중 0** · 실프로세스 uid [10001] · 크기 상한 이내 /
proxy exit 0: 같은 축 · `/etc/caddy` 1(하한 1) · **키재료 대상 114(하한 79) · 적중 0**.

**`jvm-app` 거동 보존 재실측**(계약 D-7F-3 — 이 라운드가 위생 스크립트를 다시 고쳤다): base
(`77850903`) 스크립트와 HEAD 스크립트를 같은 이미지·정책에 돌려 **출력 바이트 동일 · 종료 코드 동일** —
app clean 0/0 · ml-serving clean 0/0 · app + 변이 정책 1/1(위반 3줄).

**취약점 게이트**: web 0 · proxy 0 · app 0(정책 자리 정리 뒤 재실측).

**부수로 닫힌 Open Question 둘** — OQ-1: `/api/strategy` 401 응답에 네 헤더가 **각 1개·선언된 값**으로
실렸다(`defer` 는 `/api/**` 에도 걸린다). OQ-2: 빌드 전후 이미지 ID 등식이 **매 실행 도는 단언**이 됐고
이번 실행은 등식 성립이다.

**정직하게 남기는 관찰 하나** — 「이름마다 정확히 1개」 축은 오늘 아무것도 운동시키지 않는다. `defer` 를
떼는 변이를 넣어도 스모크가 초록이었고, 그 까닭을 직접 쟀다: **app 은 네 헤더 이름을 하나도 보내지
않는다**(프록시를 거치지 않고 내부망에서 `/api/strategy` 401 의 헤더를 떠 확인). 그러므로 이 축의 값은
오늘의 탐지가 아니라 **app 이 뒷날 보안 헤더를 켜는 변경에 대한 회귀 가드**다.


## 2026-10-16 — clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>` + 양성 대조(in_scope 파일 하나에 줄을
  심어 잡히는지 보고 **비파괴 절삭**으로 복원 — `checkout --` 을 쓰지 않는다)
- exit: — (아래 사유)
- 핵심 결과: **이 게이트는 evidence 커밋 뒤에 성립한다.** in_scope 에 evidence 경로가 들어 있어
  그 커밋 전에는 당연히 양성이고, 커밋 뒤의 결과는 evidence 가 자기 커밋의 post-state 를 담을 수
  없으므로 **verifier 와 PR 조치 코멘트가 정본**이다(같은 규율이 마지막 HEAD 의 게이트 결과에도
  적용된다). 저작 레인은 그 둘을 커밋 직후 실행해 보고에 싣는다.

## 2026-10-19 — 비밀값 스캔 (r1 재측)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m7/7f/`
- exit: 0
- 핵심 결과: 매치 **2줄** — 둘 다 환경변수 **이름**이고 값이 아니다(ci.yml 의 한 줄은 base 에 이미
  있던 것, 하나는 값 조각의 기본값 줄). 앞 라운드의 3줄에서 하나 줄었다 — runbook §9.2 가
  환경변수를 손으로 나열하던 블록을 값 조각 `source` 로 바꿨기 때문이다. 저장소에 들어간 **값**은
  포트·이미지 다이제스트·CVE 번호·`localhost` 뿐이다.
- 핵심 결과(자체 점검 하나): r1 이 F-2 축을 처음 `secret…` 접두로 이름 붙였을 때 이 수가 **36줄**로
  뛰었다 — 전부 값이 아니라 **새로 지은 식별자**였고, 위생 스크립트는 base 에서 0줄이던 파일이다.
  축 이름을 `keymaterial…` 로 바꿔 세 파일 모두 0줄로 되돌렸다(더 정확한 이름이기도 하다 — 이 축이
  찾는 것은 「비밀값」 일반이 아니라 이미지에 구워진 키·인증서 재료다).
- cmd: 같은 명령을 **게이트의 스캔 축만**(`reports/evidence/m7/7f/`, 게이트는 `scope.md` 를 제외한다)
- exit: 1
- 핵심 결과: 매치 없음 — `leakPatternGate` 의 입력에 새 어휘가 없다. 패턴 어휘는 이 문서에 축어로
  적지 않는다(참조형 명령만 둔다).

## 2026-10-19 — `leakPatternGate`(evidence 를 읽는 유일한 게이트, r1 재측)

- cmd: `./gradlew --no-daemon leakPatternGate` — **되돌린 트리**(rollback.md 의 임시 clone)에서
- exit: 0
- 핵심 결과: `> Task :leakPatternGate` · `BUILD SUCCESSFUL in 13s` · 게이트 리포트 판정 **새 매치 0 ·
  쓸모없어진 baseline 0** — 되돌리지 않는 evidence 가 게이트를 붉히지 않는다(rollback ⑥ ⓐ).
- **이 커밋 자신의 post-state 에서의 결과는 여기 적을 수 없다**(적으려면 커밋이 하나 더 필요하고 그
  커밋이 다시 같은 줄을 요구한다) — 그 한 칸은 verifier 와 PR 조치 코멘트가 갖는다.

## 2026-10-16 — rollback 실측

- `rollback.md` 의 명령을 임시 clone 에서 **실제로** 돌렸다 — 종료 코드·D/M 수·diff 공백·④~⑥ 은 그
  문서에 있다(`실측 HEAD` 한 줄 포함).
