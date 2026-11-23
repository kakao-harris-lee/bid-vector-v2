```yaml
milestone: M15
slice: 15h
base_sha: ff8e0cad42c5c114ee30b42ca4318252bbf35c5f
head_sha: 7fc13c2eb642982ac11538cab4997e8e172b3077   # main(PR #196) 병합 커밋 = 마지막 **산출물** 커밋. evidence 커밋이 뒤에 붙으므로 판정 SHA 는 PR head 다
in_scope:
  - app/src/main/kotlin/bidvector/app/evaluation/EvaluationCommitRunner.kt
  - app/src/main/kotlin/bidvector/app/wiring/EvaluationWiring.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerTest.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRotationTest.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitStopLineTest.kt
  - app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunnerFixtures.kt
  - config/quality/gate-tests.properties
  - docs/discovery/capability-map.md
  - docs/runbook/m6-6e-operations.md
  - reports/evidence/m15/15h/
out_of_scope:
  - OPEN-M15-EVAL-COST-PER-ACCOUNT ⓑ(`deferred` 경보) · ⓒ(기본 50 의 실측)
  - OPEN-M15-TEST-RESIDUE ⓑ·ⓒ·ⓓ(설정 여정 harness) · ⓔ(비밀값 스캔 어휘)
  - web/ 전부 · config/quality/leak-patterns.txt · 가입 경로
  - DB 마이그레이션 · 영속 커서 · 설정 키의 신설·기본값 변경
acceptance_commands:
  - ~/.local/bin/bv-build.sh ./gradlew --no-daemon --no-build-cache check --continue
rollback: reports/evidence/m15/15h/rollback.md — in_scope 경로 한정 `git restore --source=<base>`
```

## 무엇을 닫는가

`docs/discovery/capability-map.md` §14.3 의 **두 행의 ⓐ** 하나씩이다. 둘은 같은 파일
(`EvaluationCommitRunner.kt` · 그 test)에 살아 한 slice 로 묶는다.

### (1) `OPEN-M15-TEST-RESIDUE` ⓐ — 멈춤 줄 `consecutive=` 의 의미가 잠겨 있지 않다

그 행이 든 사실: 우선순위 test 의 고정물에서 `failed` 와 연속 수가 **둘 다 3** 이라, 멈춤 줄에
연속 수 대신 `failed` 를 싣는 변이가 **산다**. 착수 시점에 멈춤 줄을 내는 test 는 다섯이고
**다섯 전부에서 두 수가 같다**(1·1 · 2·2 · 3·3 · 1·1 · 3·3) — 그래서 그 변이는 test 전건에서
초록이다.

닫는 방법은 **흩어진 실패를 앞에 두어 두 수를 가르는 것**이다. 사유 둘 다에서 가른다 —
연속 상한 갈래(`failed=4` · `consecutive=3`)와 인터럽트 갈래(`failed=2` · `consecutive=1`).
단언은 `shouldContain` 문자열이 아니라 **그 칸의 수**로 읽는다(`stopLineCountsOf`) — 이 slice 가
잠그는 것이 「그 칸에 무엇이 실리는가」이므로 문자열 포함으로는 축이 서지 않는다.

같은 행이 지목한 **크기 게이트**: `EvaluationCommitRunnerTest` 가 500 줄 중 499 줄이라 test 를
더할 자리가 없다. **관심사로 셋으로 나눈다**(D-15H-2).

### (2) `OPEN-M15-EVAL-COST-PER-ACCOUNT` ⓐ — 주기적 결행이 계정을 영구히 굶긴다

그 행이 든 사실: 회전 창의 시작 위치가 `floorMod(날짜 × 보폭, span)` 이고 **보폭 = `n-1` > 1**
이라, 시작 위치가 `날짜 mod P`(`P = span / gcd(보폭, span)`)에만 달려 있다. 실행일이 **주기적**
부분집합이면(평일만 도는 timer) 그 잔여류가 `Z_P` 를 덮지 못해 **어느 창은 영원히 오지 않는다**.

행이 든 예(`n=4` · `K=22`)를 그대로 재서 착수 전 코드에서 **계정 여섯이 평일 400 run 뒤에도
한 번도 평가되지 않음**을 확인했다(commands.md R-1). 요일 위상 일곱 전부에서 같은 여섯이다.

## 결정

**D-15H-1 — 보폭을 1 로 바꾼다.** `start = floorMod(달력일, span)`. 행이 든 두 길
(「보폭 1」·「축을 실행 순번으로」) 가운데 **영속 커서를 요구하지 않는 쪽**이다 — 축을 실행
순번으로 옮기면 「몇 번째 run 인가」를 어딘가에 적어야 하고, 그것은 DB 마이그레이션이거나
되돌림·재실행이 서로 다른 판을 만드는 상태다(회전이 날짜의 **순수 함수**라는 성질을 잃는다).

보폭 1 이 왜 **구조적으로** 더 강한가: 실행일이 공차 `c` 의 주기적 집합일 때 시작 위치가 도는
집합의 거칢은 `gcd(보폭 × c, span)` 이다. 보폭이 1 이면 그 값이 `gcd(c, span)` 으로 **cron 자신의
주기에만** 달리고, 보폭이 `n-1` 이면 거기에 창 크기가 곱해져 커진다. 즉 보폭 1 은 「어떤 주기적
cron 에 대해서도 가장 거칢이 작은」 선택이고, 특정 요일 패턴을 열거해 막는 것이 아니다.

**그 대가는 한 바퀴가 길어지는 것이다**(OPEN 행이 든 교환 그대로). 매 달력일 도는 cron 에서
전원이 한 번 도는 데 드는 달력일이 `ceil((K-1)/(n-1))` 에서 **`K-n+1`** 로 바뀐다 — 실측
(commands.md R-2): `K=22·n=4` 7 → 19 일, `K=10·n=4` 3 → 7 일, `K=200·n=50` 5 → 151 일.
**오늘 거동은 바뀌지 않는다**: 회전은 `K > n` 일 때만 서고 출하 기본값이 `n=50` 이라 계정이
50 을 넘기 전에는 창 자체가 없다. 그 수를 넘는 날은 ⓒ(기본 50 의 실측)가 함께 서는 날이고,
한 바퀴를 되찾는 축(실행 순번·커서)은 그 행이 ML 배선 slice 에 맡긴 그대로 둔다.

**D-15H-2 — test 파일을 관심사 셋으로 나눈다.**

| 파일 | 관심사 |
|---|---|
| `EvaluationCommitRunnerTest` | 계정 순회의 **실패 경로** — 던지는 협력자 · 청중 부재 · 청중 불가 · 줄의 누출 부재 |
| `EvaluationCommitRotationTest` | **창 선택** 축 — 회전 표 · 보유자 · 주기 · KST 경계 · run 당 상한과 `deferred` |
| `EvaluationCommitStopLineTest` | **멈춤** 축 — 연속 상한 · 인터럽트 · 사유 우선순위 · 멈춤 줄의 계수 |

크기로 자르지 않고 축으로 자른다(기계적 분할 금지). 창 선택 축은 이미 선 클래스
(`EvaluationCommitRotationTest`)가 있어 **새 클래스는 하나**다.

**D-15H-3 — `config/quality/gate-tests.properties` 를 한 줄 만진다.** 레인 지시의 out_scope 는
`config/quality/` 를 다른 레인이 쓴다고 적고, in_scope 는 새 test 클래스를 **같은 커밋에서**
등재하라고 적는다. 둘이 겹치는 자리에서 **등재를 택했다** — `gateRegistrationGate` 가
「모집단 ∖ 제외 == 등재」를 양방향으로 재므로 미등재 신설 클래스는 acceptance 를 **반드시**
떨어뜨린다. 만지는 것은 app 모듈 목록의 **한 줄 추가**이고, 다른 레인이 쓰는 파일
(`leak-patterns.txt`)과는 다른 파일이다.

## 하네스 레인 변경

`git log --oneline ff8e0cad42c5c114ee30b42ca4318252bbf35c5f..HEAD -- CLAUDE.md .claude/` —
**없음**. 이 slice 의 range 에 하네스 레인 커밋이 없고, 그래서 rollback 목록도 그 경로를 담지
않는다. (리뷰 요청 시점에 재산출한다 — 목록이 비면 「없음」으로 적는다.)

## 계약 갱신 — 2026-11-23, main 병합

PR #196(M15/15I)이 머지돼 `docs/discovery/capability-map.md` 가 **공유 파일이 됐다**(두 레인이 같은
`OPEN-M15-TEST-RESIDUE` 행을 다시 썼다). 이 브랜치에 main 을 **병합**했고(rebase 아님), 해소는
합집합이다 — ⓐ 는 PR #195, ⓑⓒⓓⓔ 는 PR #196 이 닫은 것으로 적고 **ⓕ 를 새로 세웠다**
(PR #196 verifier L-r1-1 — 설정 여정의 속성 채움 하한이 이름의 가짓수가 아니라 `length` 를 세어,
두 목록을 같은 속성 네 벌로 바꿔도 초록이다. 닫는 자리는 `new Set(...).size`).

그 결과 **rollback 의 복원 원천이 착수 base 에서 병합한 main SHA 로 옮겨간다** — 착수 base 에서
복원하면 15I 의 줄까지 지운다. 셋째 단언을 재서 `capability-map.md` 하나가 두 원천에서 다름을
확인했고, 그래서 ①~⑥ 을 **새 원천 기준으로 전부 다시 냈다**(rollback.md).

in_scope 는 바뀌지 않는다 — 병합으로 들어온 15I 의 산출물은 이 slice 의 것이 아니고 복원 목록
밖이다.

## 인계 — 이 slice 가 만지지 않은 자리

**`milestone-15.md` 의 요약 두 줄이 두 OPEN 의 ⓐ 를 아직 열린 것으로 적는다**(「회전의 「매 달력일
cron」 전제」·「멈춤 줄 `consecutive=` 의미 잠금」). 그 문서는 **팀장 레인 소유**이고
(`CLAUDE.md` 운영자 지시 — 목표·마일스톤·로드맵은 세션 모델이 단독으로 쓴다) 이 slice 의 in_scope
가 아니라 고치지 않았다. OPEN 행의 정본은 `docs/discovery/capability-map.md` §14.3 이고 그쪽은
갱신했다. 요약을 맞추는 일은 팀장 레인에 넘긴다.

## 알려진 제한

① **보폭 1 도 전칭 보증이 아니다.** 보증은 「run 사이의 달력일 간격이 `n-1` 이하이면 `K-n+1`
달력일 안에 전원이 돈다」이고, 평일 timer 의 최대 간격이 3 이므로 **`n ≥ 4` 에서** 성립한다.
`n` 이 2·3 이고 나머지 수가 7 의 배수인 조합에서는 평일 timer 가 여전히 일부를 덮지 못한다
(출하 기본값 `n=50` 은 그 구간 밖이다). 날짜의 순수 함수로는 **어떤** 선택도 전칭이 될 수
없다 — 실행일 집합이 그 함수의 주기와 같은 주기를 가지면 창 하나가 영원히 오지 않는다.

② **한 바퀴가 길어진다**(D-15H-1 의 교환). 위 표가 그 수다. 처방·경보 쪽에서 닫을 일은
ⓑ(`deferred` 경보)가 들고 있다.

③ **회전은 여전히 「가장 오래 평가되지 않은 순」이 아니다.** 축이 날짜의 순수 함수라 계정
집합이 바뀌면 위상이 통째로 움직인다(15B 알려진 제한 ⑧ 그대로, 이 slice 가 바꾸지 않는다).

④ **`consecutive=` 의 의미 test 는 러너 경로만 잠근다.** 줄 조립 함수
(`evaluationCommitStoppedLine`)의 인자 순서를 바꾸는 변이는 호출부가 이름 인자를 쓰지 않으므로
타입이 막지 못하고 이 test 가 막는다 — 그 축이 test 층에 남는다는 사실 자체는 바뀌지 않는다.

## rollback

`reports/evidence/m15/15h/rollback.md`. in_scope 경로 한정 복원이고 range revert 가 아니다.
