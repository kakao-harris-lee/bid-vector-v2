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

## 2026-10-19 — `container` job 전건 (acceptance, 수정 라운드 r2 뒤)

- cmd: job 의 run step 을 **파일에서 뽑아** 순서대로. 러너가 하는 job 수준 env · `$GITHUB_ENV`·
  `$GITHUB_PATH` 흡수 · `$RUNNER_TEMP` 를 재현하고, **`if:` 를 평가한다**(`uses:` step 넷은 생략).
- exit: 0
- 핵심 결과: **ran 23 · skipped 0 · failed 0**. 이 장비 소요 226초(더운 캐시 — CI 의 차가운 러너
  수를 대신하지 못한다).
- **앞 라운드 evidence 의 정정**: r1 은 이 자리에 「22 step 전부 exit 0」을 적었고 그중
  `S-22e2`·`S-22e3` 를 **exit 0 으로 셌다**. 사실이 아니다 — 그때의 재생 하네스가 `if:` 를
  평가하지 않았고, 두 step 은 없는 step id 를 가리키는 조건 때문에 **CI 에서라면 skipped** 였다
  (vr R2-H1). 하네스를 고쳐 `if:` 를 평가하게 했고, 같은 하네스로 **고치기 전 트리**를 재생하면
  `ran 21 · skipped 1` 이 나온다(조건 하나만 되돌린 변이 기준). 이 라운드의 수는 그 하네스로 낸
  것이다.
- step 수: r1 의 22 에서 **23** 으로 하나 늘었다 — 워크플로 참조 검사(`S-20z`)가 맨 앞에 들어왔다.
  base(`77850903`)의 16 과 비교하면 이 slice 가 더한 것은 **일곱**이다.

## 2026-10-19 — 수정 라운드 r2: 지적별 초록과 「대신」 변이

| 지적 | 초록 | 「대신」 변이 | 변이 결과 |
|---|---|---|---|
| **vr R2-H1(high)** 매달린 step id | 재생 ran 23 · skipped 0 | ⓐ 매달린 id 를 심음 ⓑ **개명**(`build_web_proxy`→다른 이름, 검증자 지정 표적) ⓒ 훑지 못하는 입력 | **1** · **1** · **2**(판정 불가). 고치기 전 트리를 `if:` 평가 재생에 걸면 `skipped 1` 로 드러난다 |
| cr R2-1 제외 범위 | 위생 web·proxy 0 | `/etc/ssl/private/` 에 구운 키 | **1** — 경로를 문면에 찍는다(앞 판은 제외에 먹혀 **0** 이었다) |
| cr R2-2 이름 열거 | 〃 | 확장자 없는 PEM 키 | **1** — 내용 축이 잡는다(앞 판 **0**) |
| cr R2-5 = vr R2-L2 표적 제외 | 〃 | 제외에 `/data/` 하나 추가 | **1** 「제외 접두가 허용 집합과 다르다」(앞 판 **0**) |
| (자체) 내용 축 양성 대조 | 〃 | 내용 축 탐침을 무력화(크기 상한 1) | **1** 「훑은 파일 수 2 < 하한 82」 판정 불가 |
| cr R2-6 판정↔실행 바이트 | 스모크 0 | 기록 뒤 태그를 다른 이미지로 바꿔치움 | **1** 「그 사이에 태그가 바뀌었다 …」 |
| cr R2-7 인자 | 빌드 0 | 인자 하나를 줌 | **2** + 사용법 문면(앞 판은 조용히 둘 다 빌드) |
| cr R2-8 preflight | 빌드 0 | `openssl` 을 PATH 에서 가림 | **2** 「openssl 가 필요하다」. 앞 판 실측은 **즉사가 아니라 빈 값으로 계속**이었다(len=0, exit 0) |
| cr R2-10 `edge` internal | 스모크 0 | `internal: true` 제거(numstat 3/0) | **1** 「edge 망이 internal 이 아니다: false」 |
| cr R2-3·R2-4·R2-9 · vr R2-L1 | 위생 0 · 스모크 0 | — (거짓 차단·fail-open·문면·문서 축) | — |

**위생 게이트 재실측**(r2 의 축 보강 뒤) — web exit 0: 키재료 대상 **121**(하한 84) · 이름패턴 10 ·
제외 2(등식 고정) · 내용축 훑음 237 / proxy exit 0: 대상 **118**(하한 82) · 같은 축. 제외를
`certs/` 로 좁혀 대상 수가 r1 보다 늘었다(117→121 · 114→118).

**`jvm-app` 거동 보존**(이 라운드가 위생 스크립트를 또 고쳤다): base↔HEAD 세 조합 **출력 바이트
동일 · 종료 코드 동일** — app 0/0 · ml-serving 0/0 · app+변이 정책 1/1.

**워크플로 참조 검사**: `ci.yml` 에서 job 4 · id 13 · `steps.<id>` 참조 17 · 매달린 참조 0.


## 2026-10-16 — clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>` + 양성 대조(in_scope 파일 하나에 줄을
  심어 잡히는지 보고 **비파괴 절삭**으로 복원 — `checkout --` 을 쓰지 않는다)
- exit: — (아래 사유)
- 핵심 결과: **이 게이트는 evidence 커밋 뒤에 성립한다.** in_scope 에 evidence 경로가 들어 있어
  그 커밋 전에는 당연히 양성이고, 커밋 뒤의 결과는 evidence 가 자기 커밋의 post-state 를 담을 수
  없으므로 **verifier 와 PR 조치 코멘트가 정본**이다(같은 규율이 마지막 HEAD 의 게이트 결과에도
  적용된다). 저작 레인은 그 둘을 커밋 직후 실행해 보고에 싣는다.

## 2026-10-19 — 비밀값 스캔 (r2 재측)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m7/7f/`
- exit: 0
- 핵심 결과: 매치 **2줄** — 둘 다 환경변수 **이름**이고 값이 아니다(ci.yml 의 한 줄은 base 에 이미
  있던 것, 하나는 값 조각의 기본값 줄). r2 가 더한 파일 둘(`workflow-step-refs-check.sh` ·
  위생 스크립트의 내용 축)은 **0줄**이다 — 식별자를 지을 때 패턴 목록을 먼저 걸어 보고 지었다.
  저장소에 들어간 **값**은 포트·이미지 다이제스트·CVE 번호·`localhost` 뿐이다.
- 핵심 결과(자체 점검 하나): r1 이 F-2 축의 이름을 **패턴 목록에 걸리는 접두**로 지었을 때 이 수가
  **36줄**로 뛰었다 — 전부 값이 아니라 **새로 지은 식별자**였고, 위생 스크립트는 base 에서 0줄이던
  파일이다. 축 이름을 `keymaterial…` 로 바꿔 세 파일 모두 0줄로 되돌렸다(더 정확한 이름이기도 하다 —
  이 축이 찾는 것은 그 어휘가 가리키는 것 일반이 아니라 **이미지에 구워진 키·인증서 재료**다).
- cmd: 같은 명령을 **게이트의 스캔 축만**(`reports/evidence/m7/7f/`, 게이트는 `scope.md` 를 제외한다)
- exit: 1
- 핵심 결과: 매치 없음 — `leakPatternGate` 의 입력에 새 어휘가 없다. 패턴 어휘는 이 문서에 축어로
  적지 않는다(참조형 명령만 둔다).

## 2026-10-19 — `leakPatternGate`(evidence 를 읽는 유일한 게이트, r2 재측)

- cmd: `./gradlew --no-daemon leakPatternGate` — **되돌린 트리**(rollback.md 의 임시 clone)에서
- exit: 0
- 핵심 결과: `> Task :leakPatternGate` · `BUILD SUCCESSFUL in 15s` · 게이트 리포트 판정 **새 매치 0 ·
  쓸모없어진 baseline 0** — 되돌리지 않는 evidence 가 게이트를 붉히지 않는다(rollback ⑥ ⓐ).
- **이 커밋 자신의 post-state 에서의 결과는 여기 적을 수 없다**(적으려면 커밋이 하나 더 필요하고 그
  커밋이 다시 같은 줄을 요구한다) — 그 한 칸은 verifier 와 PR 조치 코멘트가 갖는다.

## 2026-10-16 — rollback 실측

- `rollback.md` 의 명령을 임시 clone 에서 **실제로** 돌렸다 — 종료 코드·D/M 수·diff 공백·④~⑥ 은 그
  문서에 있다(`실측 HEAD` 한 줄 포함).
