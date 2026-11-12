# M10 / notice-resume-total — 실행 명령과 종료 코드

base `8062dcd7` · 브랜치 `feature/m10-notice-resume-total` · 전부 worktree
`bid-vector-v2-m10-notice-resume-total` 에서 돌았고 Gradle 은 호스트 빌드 자리 래퍼(`bv-build.sh`)를 지난다.
**마지막 HEAD 의 게이트 결과 정본은 verifier 와 PR 조치 코멘트**다 — 이 표는 그 직전까지 담는다.

## 표적 실행

## 2026-11-12T02:09Z — C1(최댓값 함수·test helper 의 순수 이동) 거동 0
- cmd: `./gradlew --no-daemon :workflow:test --tests 'bidvector.workflow.collection.*'`
- exit: 0
- 핵심 결과: 수집 갈래 12 클래스 116 통과·실패 0(이동 전후 같은 수)

## 2026-11-12T02:13Z — RED (구현 전)
- cmd: `… :workflow:test --tests '…CollectNoticesUseCaseTest' --rerun`
- exit: 1
- 핵심 결과: 18 중 1 실패 `expected:<7> but was:<0>` — 재개 둘째 배치의 0 이 앞 총수를 덮었다

## 2026-11-12T02:16Z — GREEN (구현 뒤)
- cmd: `… :workflow:test --tests 'bidvector.workflow.collection.*' --rerun`
- exit: 0
- 핵심 결과: 117 통과·실패 0

## 2026-11-12T02:21Z — 전건 check 1차
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `:workflow:sizeGate`(그 test 파일 508 줄 > 500) · `:workflow:ktlintTestSourceSetCheck`(연쇄 4 단계) 둘이 붉었다

## 2026-11-12T02:24Z — 재개 표본을 한 자리로 묶은 뒤 그 둘
- cmd: `… :workflow:sizeGate :workflow:ktlintMainSourceSetCheck :workflow:ktlintTestSourceSetCheck :workflow:test --tests 'bidvector.workflow.collection.*' --rerun`
- exit: 0
- 핵심 결과: 495 줄 · 117 통과·실패 0

## 변이 — 2026-11-12T02:26Z, 최종 코드

필터는 수집 갈래 test **전수**이고 모든 판이 task 옵션 `--rerun` 을 지난다(통제군 cache 적중 0 — task 상태
줄에 접미 없음). 적용은 앵커 계수 단언을 지나 어긋나면 비영 종료하고, 판마다 적용 전 porcelain 빈 출력을
확인하며 복원은 한 자리다.

| 판 | **바꿔치운** 것 | exit | RED |
| --- | --- | --- | --- |
| 기준 | — | 0 | 0 / 117 |
| M1 | 공유 최댓값 함수를 「마지막 배치 값 쥐기」로 | 1 | **2** — 공고 목록 · 낙찰 각 1 |
| M2 | 같은 함수를 「첫 배치 값 고정」으로 | 1 | **1** — 공고 목록만 |
| C | 선언과 **모든 사용처**를 함께 개명(거동 0) | 0 | 0 / 117 |

- M1 이 두 갈래를 함께 붉히는 것이 「두 갈래가 같은 한 자리를 부른다」(D-2)의 실측이다.
- M2 가 공고 목록만 붉히는 것은 **낙찰 test 가 그 축(뒤 배치가 더 큰 총수를 말한다)을 재지 않기** 때문이다 —
  새 test 의 둘째 단언이 최댓값과 「첫 값 고정」을 가르는 유일한 자리다.
- 음성 대조가 초록이라 suite 가 무관한 이유로 붉지 않는다. 끝 트리 porcelain **0 줄**.

## 2026-11-12T13:1xZ — 전건 check 2차
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `:workflow:cpdReportPresenceGate` 하나 — **산출물 결함이 아니라 변이가 남긴 로컬 상태**(아래)

## 2026-11-12T13:12Z — CPD 리포트 재산출
- cmd: `./gradlew --no-daemon :workflow:cpdCheck --rerun :workflow:cpdCheckObserved --rerun`
- exit: 0
- 핵심 결과: 2 task 실행 — 리포트 둘이 현재 소스 mtime 뒤로 다시 났다

**판정**: 변이의 적용·복원이 `workflow/src/main` 세 파일의 **mtime** 을 올렸는데 **내용**은 제자리라,
Gradle 의 내용 해시 판정이 `cpdCheck` 를 `UP-TO-DATE` 로 건너뛰고 1차 전건이 남긴 리포트가 그대로 남았다.
presence 게이트의 술어는 **mtime** 이라 「리포트가 잴 소스보다 낡았다」로 붉었다. 근거 셋을 쟀다 —
리포트 mtime < 세 소스 mtime · `git diff --stat <1차 전건 HEAD>..HEAD -- workflow/src/main` **0 줄**(내용
불변이라 `UP-TO-DATE` 가 정당했다) · `build/` 는 이 worktree 안의 실디렉터리이고 다른 레인과 공유하지 않는다.
CI 는 새 checkout 에서 같은 빌드가 리포트를 내므로 이 상태가 생기지 않는다.

## acceptance

## 2026-11-12T13:12Z — CI Kotlin job 명령 그대로
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: 42m48s · 433 task 중 79 실행 · 353 up-to-date · 실패 0

전건 명령에 `--rerun` 을 섞지 않았다 — 리포트 재산출을 **앞선 별도 실행**으로 두어 acceptance 는 CI job
명령 그대로 남는다.

## 그 밖

- 비밀값 스캔: `grep -rniE -f config/quality/leak-patterns.txt <in_scope 경로들> reports/evidence/m10/notice-resume-total/`
  exit **1**(매치 없음 = 통과). 패턴으로 못 잡는 축은 육안 — 이 slice 가 만지는 값은 정수 회계뿐이다.
- clean-tree: `git status --porcelain -- <in_scope 개별 인자>` **빈 출력** · **양성 대조**(in_scope 파일 하나에
  줄을 더하니 ` M` 한 줄로 잡혔고, 사본에서 되돌리니 다시 빈 출력 — `checkout --` 을 쓰지 않았다).
