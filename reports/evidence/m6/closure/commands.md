# M6 종결 판정 — 실행 기록 (2026-11-02)

출력 전문은 싣지 않는다(핵심 결과 한 줄). 판정은 `checklist.md` 가 정본이다.

## A. 조건 1 — one-command 전건 (버릴 clone)

- 자리: `docs/m6-close` 를 **clone 한 버릴 트리**. 공유 worktree 에서 돌리지 않았다.
- cmd: 빌드 자리 래퍼(`bv-build.sh`)를 통해 `./tools/one-command-check.sh`
- 호스트 점검(별도 호출로 먼저, 결과를 보고 시작): 다른 Gradle 빌드 0 · available 14.5GB · swap free 7.0GB ·
  RSS 상위에 다른 세션의 큰 프로세스 없음.
- 단계: 선언된 25 단계가 전부 **순서대로 돌았다**(Kotlin 둘 · Python 아홉 · web 열넷).

| 단계 | 결과 |
| --- | --- |
| Kotlin — `check` | **BUILD SUCCESSFUL in 29m 39s** |
| Kotlin — `qualityBaseline` | BUILD SUCCESSFUL |
| Kotlin test 계수(**결과 XML 집계**, 콘솔 grep 아님) | **tests 4,156 · 실패 0 · 오류 0 · 건너뜀 4** (XML 510개) |
| 건너뜀 4 의 자리 | `bidvector.adapters.contract.RealServerIntegrationTest` 하나 — 실 서버 통합이라 태그로 기본 실행에서 분리돼 있다(6C 계약의 「경계로 처리」) |
| Python — 아홉 단계(S-1~S-11) | 전부 통과 |
| web — W-1~W-12 | 전부 통과(Playwright 스모크 포함) |
| **web — W-13 여정 e2e** | **1회차 실패 — 1 failed · 10 passed.** 전건은 이 단계에서 멈췄고(완료 표지 없음) **비-0** 으로 끝났다 |

**1회차 실패의 정체 — 재실행으로 가렸다(아래 B).** production 거동이 아니라 **e2e 하네스의 응답 포착
경합**이다. 실패 단언은 지우기 뒤 체크리스트 `pending` 에 그 항목이 있는지인데, 받은 배열이 다른 항목
하나뿐이었다. **그 spec 자신의 주석이 같은 증상을 이미 실측으로 적고 있다** — 홈을 거쳐 들어오면 배너와
화면이 같은 질의를 각각 걸어 응답이 둘이고 늦게 닿는 쪽이 기다림에 잡힌다는 것. 앞 라운드가 그 자리에
화면 상태 단언을 넣어 창을 좁혔으나 **창이 닫히지는 않았다** — 응답 기다림을 **거는 순간**에도 앞선 재진입
응답이 아직 날아오고 있을 수 있다. 부하가 높은 1회차에서만 벌어졌다.

**종료 코드를 받을 자리를 두지 않았다(레인 실수)** — 전건을 `nohup` 으로 띄우며 래퍼의 종료 코드를 잡지
않아 **정확한 값이 없다**. 비-0 이라는 것은 `set -euo pipefail` · 마지막 단계에서 멈춤 · 완료 표지 부재로
안다. B 의 재실행은 종료 코드를 잡았다.

## B. W-13 표적 재실행 — 2회, 둘 다 통과

- cmd: 같은 clone 의 `web` 에서 여정 e2e 만
- 1회: **11 passed**(38.4s) · 2회: **exit 0 · 11 passed**(39.7s)
- 즉 **재현되지 않는다**. 같은 base 의 CI 도 초록이다(`main` `4d5d3d73` run 성공 — `check` job 이 같은
  스크립트를 S-20 으로 돌린다).

## C. 조건 2 — legacy 무의존 재실측

- 저장소에 legacy symlink 0(그 이름은 `.gitignore` 항목이라 작업 트리 밖 참조물이다).
- Kotlin·Gradle 소스의 그 문자열은 셋뿐이고 전부 **출처 주석·provenance 문자열**이다 — import 0.

## D. clean-tree (양성 대조 포함)

- cmd: `git status --porcelain -- reports/evidence/m6/closure milestone-6.md` → 커밋 뒤 **빈 출력**
- 양성 대조: in_scope 파일 하나에 줄을 심어 `M` 으로 **잡히는 것**을 확인하고 **비파괴 절삭**
  (`head -n <원래 줄수>`)으로 되돌렸다 — `checkout --` 는 쓰지 않는다(미커밋 편집을 함께 지운다).

## E. 비밀값 스캔

- cmd: 참조형(`-f config/quality/leak-patterns.txt`)으로 이 slice 의 evidence 와 `milestone-6.md` 를 훑었다.
- 핵심 결과: 이 slice 가 만든 evidence 둘 **0 건**. 작성 중 한 건이 잡혀(완료 조건 8 의 조건 이름을 축어로
  적은 자리) **문면을 바꿔 없앴다** — 조건 이름 자체가 스캔 어휘와 겹친다.
