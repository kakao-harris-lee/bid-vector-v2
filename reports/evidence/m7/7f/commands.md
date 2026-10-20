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

## 2026-10-20 — `container` job 전건 (acceptance, 마지막 배치 r5 뒤)

- cmd: job 의 run step 을 파일에서 뽑아 순서대로 + **`if:` 평가**(`uses:` 넷 생략).
- exit: 0
- 핵심 결과: **ran 23 · skipped 0 · failed 0**(227초). step 수 23, base 대비 +7.

## 2026-10-20 — 마지막 배치 r5: 지적별 초록과 「대신」 변이

| 지적 | 초록 | 「대신」 변이 | 결과 |
|---|---|---|---|
| **vr R5-M1** tmpfs 가 `/tmp` 를 가림 | 위생 web·proxy 0 | `/tmp/client.p12`(0600 · 비-PEM) | **1**. **고치기 전 스크립트는 exit 0** 이고 그때 대상 수가 정상과 같은 **118** 이라 부재가 수로도 보이지 않았다 |
| **vr R5-L2** 링크 이름 | 〃 | `/opt/client.p12` → 바이너리 blob 링크 | **1**(고치기 전 **0**) |
| (R5-L2 가 드러낸 거짓 차단) | 〃 | 링크를 넣자마자 공개 CA 번들 링크 둘이 잡힘 | 링크의 **가리키는 곳**도 제외에 대어 닫음 — 둘 다 더 이상 잡히지 않는다 |
| vr R5-L1 · R5-L3 | — | — (문면 등재만) | — |

**계측기 간섭 재확인**(계약이 수로 확인하라고 한 것): 정상 이미지의 이름 축 대상이 **118 · 121**
로 문서값 그대로다. fd 로 가르므로 수신 파일도 마운트도 없고, 자기 계수가 생길 자리가 없다.
링크를 포함한 수(**463 · 466**)는 요약에 따로 찍는다 — **하한은 일반 파일 모집단에만** 건다(링크
수는 베이스의 busybox applet 수에 따라 흔들려 「스캔했는가」를 재지 못한다).

**`jvm-app` 거동 보존**: base↔HEAD 세 조합 **출력 바이트 동일 · 종료 코드 동일**(0/0 · 0/0 · 1/1).


## 2026-10-16 — clean-tree 게이트

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>` + 양성 대조(in_scope 파일 하나에 줄을
  심어 잡히는지 보고 **비파괴 절삭**으로 복원 — `checkout --` 을 쓰지 않는다)
- exit: — (아래 사유)
- 핵심 결과: **이 게이트는 evidence 커밋 뒤에 성립한다.** in_scope 에 evidence 경로가 들어 있어
  그 커밋 전에는 당연히 양성이고, 커밋 뒤의 결과는 evidence 가 자기 커밋의 post-state 를 담을 수
  없으므로 **verifier 와 PR 조치 코멘트가 정본**이다(같은 규율이 마지막 HEAD 의 게이트 결과에도
  적용된다). 저작 레인은 그 둘을 커밋 직후 실행해 보고에 싣는다.

## 2026-10-20 — 비밀값 스캔 (r5 재측)

- cmd: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로 개별 인자> reports/evidence/m7/7f/`
- exit: 0
- 핵심 결과: 매치 **2줄** — 둘 다 환경변수 **이름**이고 값이 아니다(ci.yml 의 한 줄은 base 에 이미
  있던 것, 하나는 값 조각의 기본값 줄). r3·r4 가 더한 것(참조 검사의 새 술어 셋 · 위생의 PEM 머리 줄
  탐지와 매처 표본)도 **0줄**이다 — 식별자와 패턴을 짓기 전에 목록을 먼저 걸어 보고 지었다.
  저장소에 들어간 **값**은 포트·이미지 다이제스트·CVE 번호·`localhost` 뿐이다.
- 핵심 결과(자체 점검 하나): r1 이 F-2 축의 이름을 **패턴 목록에 걸리는 접두**로 지었을 때 이 수가
  **36줄**로 뛰었다 — 전부 값이 아니라 **새로 지은 식별자**였고, 위생 스크립트는 base 에서 0줄이던
  파일이다. 축 이름을 `keymaterial…` 로 바꿔 세 파일 모두 0줄로 되돌렸다(더 정확한 이름이기도 하다 —
  이 축이 찾는 것은 그 어휘가 가리키는 것 일반이 아니라 **이미지에 구워진 키·인증서 재료**다).
- cmd: 같은 명령을 **게이트의 스캔 축만**(`reports/evidence/m7/7f/`, 게이트는 `scope.md` 를 제외한다)
- exit: 1
- 핵심 결과: 매치 없음 — `leakPatternGate` 의 입력에 새 어휘가 없다. 패턴 어휘는 이 문서에 축어로
  적지 않는다(참조형 명령만 둔다).

## 2026-10-20 — `leakPatternGate`(evidence 를 읽는 유일한 게이트, r5 재측)

- cmd: `./gradlew --no-daemon leakPatternGate` — **되돌린 트리**(rollback.md 의 임시 clone)에서
- exit: 0
- 핵심 결과: `> Task :leakPatternGate` · `BUILD SUCCESSFUL in 14s` · 게이트 리포트 판정 **새 매치 0 ·
  쓸모없어진 baseline 0** — 되돌리지 않는 evidence 가 게이트를 붉히지 않는다(rollback ⑥ ⓐ).
- **이 커밋 자신의 post-state 에서의 결과는 여기 적을 수 없다**(적으려면 커밋이 하나 더 필요하고 그
  커밋이 다시 같은 줄을 요구한다) — 그 한 칸은 verifier 와 PR 조치 코멘트가 갖는다.

## 2026-10-16 — rollback 실측

- `rollback.md` 의 명령을 임시 clone 에서 **실제로** 돌렸다 — 종료 코드·D/M 수·diff 공백·④~⑥ 은 그
  문서에 있다(`실측 HEAD` 한 줄 포함).
