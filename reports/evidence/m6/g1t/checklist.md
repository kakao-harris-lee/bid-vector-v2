# M6/G1-T checklist — 변이 실측과 알려진 제한

## 1. 산출물 — 신설 test 넷과 각자 닫은 bullet

| ID | capability | 잰 bullet(개정 뒤 문면) | test FQN |
|---|---|---|---|
| T-1 | COL-02 | 실 기초금액이 없는 재관측이 **저장된 기초금액을 덮지 않는다** | `bidvector.adapters.persistence.BaseAmountMergeGuardTest` |
| T-2 | COL-03 | 429 소진 → 그 공고는 예가 없이 남고 **그 실행은 멈추며** 사유가 회계에 실리고 **다음 실행이 이어받는다** | `bidvector.workflow.collection.OpeningQuotaHaltResumptionTest` |
| T-3 | DEC-06 | 같은 공고를 **어느 경로로 평가해도** 액션과 근거가 같다 | `bidvector.app.evaluation.DryRunCommitParityTest` |
| T-4 | OPS-01 | 홀더를 **강제 종료**하면 lease 가 즉시 해제된다 | `bidvector.adapters.event.LeaseHolderTerminationTest` |

네 클래스를 `gateRegistrationGate` 의 등재 장부에 함께 올렸다(모듈 키 아래 한 줄씩, 값·정책 version
무변경). 등재하지 않으면 그 등식(`모집단 ∖ 제외 == 등재`)이 붉고, 추적표의 「등재 식별자」 어휘도
성립하지 않는다 — 계약 갱신 D-G1T-1 의 in_scope 추가가 그 사유다.

## 2. 변이 실측 — 바꿔치기 · `numstat` · 통제군

변이는 production 속성을 **바꿔치운다**(더하기만 하는 변이는 초록이다). 적용 즉시 `git diff --numstat`
으로 바뀐 줄을 확인하고, 측정 뒤 **사본에서 복원**한다(`git checkout --` 금지 — 같은 파일의 미커밋
편집까지 지운다). 복원은 `cmp` 바이트 동일 + `git status --porcelain` 빈 출력으로 확인했다. 변이 실행은
`:<모듈>:test` 의 표적 실행이라 게이트를 돌리지 않는다(게이트 test 는 변이 대상이 아니다).

| ID | 무엇을 **바꿔치웠나** | numstat | 그 변이에서 RED | 같은 변이에서 함께 붉은 **등재** test | 통제군(복원 뒤) |
|---|---|---|---|---|---|
| M-1 | 공고 행 금액 병합의 「유입이 없으면 기존을 지킨다」 술어를 「유입이 없으면 덮는다」로 | `1 1` | T-1 의 결측 재관측 단언(기대 `Unchanged`, 실제는 비권위 덮어쓰기 거부) | **없음** — persistence 7 클래스 **38 test 중 1 실패(T-1 뿐)** | T-1 2/2 |
| M-1 앞 판 | 같은 파일에서 **기초금액 축 가드를 통째로** 제거 | `1 1` | T-1 | `PrecedenceMutationTest` 의 「persist 경로는 애초에 UPDATE 를 시도하지 않는다」도 RED(34 중 2) — 그래서 **결측 유입 레그만** 뒤집는 M-1 로 좁혀 다시 쟀다 | — |
| M-2 | 수집 갈래 멈춤 판정에서 **쿼터 가지 제거**(소진이어도 멈추지 않는다) | `1 1` | T-2(「쿼터 소진은 그 실행을 멈춰야 한다」) | 같은 클래스의 K6 둘(29 중 3) — **멈춤** 고리는 이미 측정된다. T-2 가 더하는 것은 네 고리를 **한 사슬로** 잇는 것과 **다음 실행의 이어받기**다 | T-2 1/1 |
| M-3 | 커밋 조립 메서드가 use case 에 넘기는 **분석 예산**(전략의 후보 상한 → 0) | `1 1` | T-3(기대 `Reached`+BidNow, 실제 `NotReached`+예산 소진) | **없음** — 평가 5 클래스 **19 test 중 1 실패**. 커밋 E2E 는 `EvaluationCommitRun` 을 **손으로** 조립해 그 값을 리터럴로 넘기므로 조립 메서드의 값 선택을 보지 못한다 | T-3 1/1 |
| M-4 | 임대 판정 자리를 **DB 세션 advisory lock → 프로세스 범위 집합**으로(해제도 같은 집합) | `9 7` | T-4(기대 `Held`, 실제 `Busy`) | **없음** — 임대·relay 5 클래스 **14 test 중 1 실패**. 추적표가 OPS-01 근거로 드는 `PostgresAdvisoryLockLeaseTest` 는 **전건 초록** — 프로세스 범위 배제도 그 네 단언을 만족한다 | T-4 2/2 |

**M-4 의 변이가 왜 그 모양인가**: 「세션이 끊기면 서버가 잠금을 놓는다」는 PostgreSQL 의 성질이라
advisory lock 을 쓰는 동안에는 깰 수 없다. 깨는 유일한 길은 **자물쇠의 자리를 바꾸는 것**이고, 그것이
바로 이 test 가 막는 회귀다(그 구현의 KDoc 이 행 플래그·TTL 키·프로세스 범위 파일 잠금 셋을 이름으로
기각했다). 그래서 변이는 그 기각된 대안 하나를 실제로 구현한 판이다.

## 3. 알려진 제한

- **T-1 의 경계** — V2 는 개찰 축을 공고 행의 기초금액 열에 쓰지 않는다(개찰 fact 는 자기 표로 간다).
  그래서 T-1 이 지키는 자리는 **공고 행의 write 가드**이고, 개찰 pass 가 그 열에 닿는 경로가 생기면
  COL-02 행을 다시 봐야 한다(추적표 본문에 같은 문장을 남겼다).
- **T-2 의 대역** — 공유 대역을 쓰지 않고 port 대역을 새로 지었다. 사유는 하나다: 회계의 **429 관측 수**
  칸을 실어야 하는데 공유 대역은 그 칸을 늘 0 으로 둔다(그 칸은 어댑터가 센다). 관문 줄(의도·호출)도
  적지 않는다 — T-2 가 재는 것은 축의 **결말 줄**이 다음 실행의 입력이 되는가다.
- **T-3 의 DataSource 는 컨테이너 소유자다** — 이 test 의 축은 두 경로의 등식이고, 최소 권한 역할 축은
  출하 조립을 띄우는 다른 test 들이 진다.
- **T-3 의 payload 대조는 부분 문자열**이다 — 사유 문자열을 쉼표로 쪼갠 조각 전부가 저장된 payload 에
  있는지 본다(쉼표는 wire 구분자라 탈출되고, 그 형식의 정본은 어댑터의 어휘 골든이며 디코더는 그 모듈
  `internal` 이라 `app` 에서 부를 수 없다). 개정 번호는 자릿수가 긴 값으로 심어 다른 칸의 숫자와 우연히
  겹치지 않게 했다.
- **COL-03 의 앞 두 bullet** — 6G production 호출부는 조회 가치 술어에 `alreadyHeld=false`·
  `lastCheckedAt=null` 상수를 넘긴다(이어 돌기를 시도 원장이 진다). 그래서 age-gate·recheck-gate 는
  오늘 출하 경로에서 **발화하지 않는다**. 이 사실을 추적표 COL-03 의 production 칸에 적었고, 그것이 ⓐ
  술어 ②(출하 배선에서 성립하는가)에 걸리는지는 **그 행의 재판정** 문제다 — G1-T 는 사실만 적고 6E-1 의
  판정을 바꾸지 않았다(계약은 T-2 성립 시 ⓐ 로 올리라고 정한다). **보고 항목**이다.

## 4. 수정 라운드가 만든 새 파일 ↔ in_scope 대조

이 라운드의 신규 파일은 test 넷과 이 evidence 디렉터리뿐이다(`git diff --name-status <base>..HEAD` 가
정본, `rollback.md` 가 그 목록을 든다). in_scope 밖 새 파일은 없다. 수정 파일 둘(등재 장부·추적표)은
계약이 든 범위이고, 등재 장부는 D-G1T-1 이 더한 자리다.

## 5. 리뷰 요청 조건

| 항목 | 상태 |
|---|---|
| 구현 diff 커밋 · base/head 고정 | `77850903` → 산출물 HEAD(= `commands.md` acceptance 절의 SHA) · clean-tree 빈 출력 + 양성 대조 1회(`commands.md`) |
| acceptance 전부 exit 0 | `commands.md` acceptance 절 |
| test/lint/type/architecture/contract | `check` 한 번이 전부를 든다(부분 게이트로 줄이지 않았다) |
| 변경된 fixture·정책 version 근거 | **없음** — fixture·정책 값 무변경(이 slice 는 test 와 장부만 더한다) |
| 알려진 제한·rollback | 위 §3 · `rollback.md` |
| 비밀값 스캔 | `commands.md` 해당 절(참조형 명령 · 판정 한 줄) |
