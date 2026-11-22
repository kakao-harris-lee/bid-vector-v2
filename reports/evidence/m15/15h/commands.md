# M15/15H — 명령과 실측

acceptance 는 CI Kotlin `check` job 의 명령 그대로이고 `--continue` 를 붙여 돈다
(알려진 빨강을 첫 하나에서 멈추지 않고 전수를 보기 위해서다).

**마지막 HEAD 의 게이트 결과 정본은 이 파일이 아니라 verifier 리포트와 PR 조치 코멘트다** —
evidence 는 자기 마지막 커밋의 post-state 를 담을 수 없다.

## R — 착수 전 실측(RED)

| # | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| R-1 | `bv-build.sh ./gradlew --no-daemon --no-build-cache :app:test --tests 'bidvector.app.evaluation.EvaluationCommitRotationTest'` (보폭 `n-1` · 신설 test 포함) | **1** | 신설 `평일만 도는 timer 에서도 전원이 돈다` 만 FAILED — `Some elements were missing: [8, 9, 10, 11, 12, 13]`(나머지 넷 통과). `OPEN` 행이 센 「계정 여섯」과 같은 수·같은 집합이다 |
| R-2 | 회전식 모사(순수 산술, 빌드 밖) | 0 | 보폭 `n-1`: 평일 400 run 뒤에도 시작 요일 일곱 전부에서 `{8..13}` 미도달 · 보폭 1: 시작 요일 일곱 전부에서 **최대 15 평일 run** 안에 전원. 매 달력일 cron 의 한 바퀴는 `K=22·n=4` 7→19 · `K=10·n=4` 3→7 · `K=200·n=50` 5→151 달력일 |

## G — 구현 뒤 실측

| # | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| G-1 | `bv-build.sh ./gradlew --no-daemon --no-build-cache :app:test --tests 'bidvector.app.evaluation.*'` | 0 | 평가 커밋 test 전건 통과(클래스 일곱) |
| G-2 | `bv-build.sh ./gradlew --no-daemon --no-build-cache check --continue` | 0 | acceptance — 전건 초록 |

## M — 변이 실측

변이는 **더하기가 아니라 바꿔치우기**다(기존 식을 지우고 다른 식을 넣는다). 되돌림은 변이 전
파일 사본에서 복사하고 `git checkout --` 을 쓰지 않는다(미커밋 산출물을 함께 지운다).

| # | 변이 | 판정 | 붉은 test |
|---|---|---|---|

## L — 비밀값 스캔

| # | 명령 | exit | 핵심 결과 |
|---|---|---|---|
