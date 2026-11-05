# M6 종결 재고 — OPEN 처분과 6G 현재 상태 (2026-11-02)

판정 정본은 `checklist.md` 다. 이 문서는 **M6 종결이 넘기는 것**을 적는다.

## §1 추출과 판정 규칙 — 건별 처분은 이 판정이 재지 않는다

추출은 **판정 base 트리에 고정**한다 — 작업 트리를 훑으면 **이 문서 자신이 모수에 들어가** 쓸수록 숫자가
움직인다(evidence 규격의 「자기 검사 하네스 금지」와 같은 뿌리).

```
git grep -hoE 'OPEN-[A-Z0-9]+[A-Z0-9-]*' 4d5d3d73 -- milestone-6.md reports/evidence/m6 \
  | grep -v -- '-$' | sort -u | wc -l        # → 204
```

- 필터 없이 세면 **207** 이다. 차이 셋은 식별자가 아니라 **줄바꿈 조각**이다 — 원문에서 줄 끝에 걸쳐 잘린
  이름이 하이픈으로 끝나 따로 잡힌다. 그래서 `-$` 로 떨어뜨린다.
- 트리를 고정하지 않고 지금 작업 트리를 훑으면 **213** 이 나온다(차이는 이 slice 자신의 evidence).
- 아래 73 / 131 분할과 보정 대조는 전부 **이 204 집합** 위에서 센 값이다.

M5 종결(47건)의 네 배가 넘는다.

**이 문서는 204 건을 건별로 다시 재지 않는다.** 그 결정의 근거는 추측이 아니라 **보정 대조 실측**이다.
건별 상태를 기계로 가르려고 「식별자 주변 창(앞 100·뒤 200자)에 종결 어휘(닫힘·닫혔·종결·해소·닫는다)가
있는가」를 술어로 써 봤다 — 204 를 73 / 131 로 갈랐다. 그 뒤 **오늘 다른 문서가 홀더로 이름을 부르는 열하나**
(ML 배선 · 조립 · 발송 채널 · 잔여 묶음 둘 · 요건 채우기 · 요건 원문 축 · 점수 축 · 6G 리뷰 후속 · 낙찰자 상호
적재 · 하한율 표 값)로 **보정 대조**했더니 **열하나 중 아홉이 「종결 어휘 있음」 쪽에 잘못 들어갔다**.
「`X` 가 닫히려면 …」·「… 때 닫는다」 같은 문장이 전부 그 창에 걸리기 때문이다.

**보정 대조 자체가 내 후보 목록의 오류도 하나 잡았다** — 처음에는 커넥션 풀(`OPEN-6A1-CONNECTION-POOL`)도
열린 것으로 넣었는데, 6E-2a 가 런타임 `DataSource` 를 풀로 바꾸면서 **닫았다**(`6e2a/checklist.md` 의 그 행).
그래서 모수를 열둘에서 열하나로 줄였다. 홀더 이름을 눈으로 세는 것도 틀린다는 실측이다.

그러므로 **술어는 양쪽으로 틀리고 판정으로 쓸 수 없다**. 건별 처분의 정본은 각 소유 slice 의 evidence 이고,
**204 건 전수 재고는 별 작업**으로 넘긴다(§4 후속 ①). 이 문서가 거는 것은 **받는 자리**다 — 종결이 실제로
넘겨야 하는 것은 건별 상태가 아니라 **누가 받는가**이고, 그 집합은 작고 이름이 붙어 있다.

## §2 받는 자리 — M6 종결이 넘기는 묶음

| 받는 자리 | 무엇을 받나 | 근거 |
| --- | --- | --- |
| **`OPEN-ML-ANALYSIS-WIRING`**(ML 배선 slice — 6G 백테스트 판정 뒤) | `ML-01` 판정 · `ML-02`·`ML-04` 재판정(그 slice 의 acceptance) · `DEC-05`·`DEC-02` 의 **값 출처 축과 그 값에서만 생기는 축** · `DEC-10` 의 가격 경로 계수 · 완료 조건 6 의 모델 version 축 · **`OPEN-6E1-G4-NONVACUOUS`**(후보 ≥1 seed 가 있어야 dry-run 의 outbox 등식이 공허하지 않다 — 완료 조건 4 의 한정) | 운영자 결정 2026-10-24(「완료 조건」 아래 결정 블록). 배선 자체는 「결정 ④ 개정 2026-09-27」이 가격 경로 백테스트 뒤로 보류했고 그 보류는 그대로다. `OPEN-6E1-G4-NONVACUOUS` 의 받는 자리가 ML 배선인 것은 6E-1 종결 문단이 적는다 |
| **`OPEN-M6-RESIDUAL-OPS`**(M7 7F 운영 반입 또는 별 마일스톤) | M6 잔여 운영·전략 축 **15** — `STR-04`·`STR-09`·`STR-10`·`NOTI-01`·`OPS-02`~`OPS-05`·`OPS-07`~`OPS-13` | 운영자 결정 2026-10-15 이 G3 24 를 조건 3 에서 빼고 묶음 둘로 등재하라고 정했다. 등재는 M6/G1-DEC05 가 했고 정본은 capability map §14.3 의 그 행이다 |
| **`OPEN-M6-RESIDUAL-ML-LIFECYCLE`**(같은 자리) | M6 잔여 ML 수명주기 축 | 같은 결정·같은 등재 |
| **하네스 레인** | 게이트 술어의 약한 층 **열둘** — `OPEN-MIGRATION-ORDER-GATE` · `OPEN-CPD-GATE-RENAME-BYPASS` · `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` · `OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT` · `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` · `OPEN-CODEX-RECORD-COORDINATES` · `OPEN-GATE-REGISTRATION-STALE-INPUT` · `OPEN-HARNESS-GATE-TESTS-ORDER` · `OPEN-HARNESS-ROLLBACK-VALIDITY-BY-KIND` · `OPEN-KTLINT-UNUSED-IMPORTS-SILENT` · `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT` · `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` | 열둘 전부 **어느 문서에도 종결 어휘가 붙은 적이 없다**(§1 술어로 전수 확인 — 이 묶음은 술어가 틀리지 않은 쪽이다). 각 식별자의 신설 자리와 받는 쪽은 `milestone-6.md` 6F-5-a·6A-1 인계 문단이 적는다. 여기에 완료 조건 2 의 **소스 축 상시 게이트 부재**(`checklist.md` §3 ④)가 더해진다 |
| **`OPEN-SET01-AWARD-NAME-INGEST`**(낙찰 목록 수집 축) | 낙찰자 상호의 canonical 적재 — 조건 3 `SET-01` 의 남은 빈자리 | 운영자 결정 2026-10-22 (a) 승인 · 착수는 6G 라이브 수집 종료 뒤. **그 결정의 정본은 `milestone-6.md` 가 아니라** `reports/evidence/m6/g1set01/checklist.md` 처분표 P-23 행과 `docs/discovery/capability-map.md` 의 그 OPEN 행이다. M7 종결도 「M7 밖, 등재 유지」로 넘겼다. **사용자 결정 2026-11-03 이 `SET-01` 을 조건 3 모수에서 분리해 이 OPEN 으로 넘겼다**(`ML-01` 과 같은 꼴 — `checklist.md` §5 ①). 적재 slice 의 **새 외부 호출과 쿼터는 별도 승인 대상**이고 그 승인에 들어 있지 않다 |
| **`OPEN-6F-ASSEMBLY`** 계열 | 포트 조립의 잔여(`OPEN-6F4W-ASSEMBLE-CALLER` 등) | 「M6 잔여 해소와 배선」 절 착수 순서 4번. 3·4번은 PR #44 로 닫혔고 그 문단이 잔여를 적는다 |
| **`OPEN-STR-12`**(발송 채널) | 알림의 **실 발송** 경로 — outbox 까지는 6F-7 이 냈고 채널은 그 뒤다 | 「M6 잔여 해소와 배선」 절 착수 순서 1번. M4 종결 판정도 같은 식별자를 「조건이 공허하게 참」의 사유로 들었다 |
| **`OPEN-6G-REVIEW-FOLLOWUPS`** 와 6G-2 계열 | 6G 리뷰 후속의 잔여 열하나(코드 0 의 정리 항목) + 6G-2a·2b·2d·2e·2f 가 넘긴 OPEN | 6G 절 「6G 종결 2026-09-30」 문단(D-6G-75 분리 종결)과 6G-2c 의 D-6G2c-42 ③ |
| **운영자 결정 선행 slice 셋** | **6B-3**(데이터 수명·마스킹 — 승인된 보존 기간이 없다; `OPEN-6F5A-RETENTION`·`OPEN-6F6-PROFILE-RETENTION`·`OPEN-6F8-RAW-PII-RETENTION`·`OPEN-6B3-*` 가 여기로 모인다) · **6B-4**(ML job 영속·큐 상한 — M5 가 넘긴 `OPEN-5E-JOB-PERSISTENCE`·`OPEN-5E-JOB-QUEUE-BOUND` 포함) · **6F-5-b**(요건을 채우는 LLM 추출 — **실 LLM 호출은 운영자 승인 대상**, `OPEN-6F5-EXTRACTION-FILL`·`OPEN-6F4-STR02-REQUIREMENTS-AXIS`) | 「배선 밖 잔여」 절의 「운영자 결정 선행」 목록 그대로. 셋 다 M6 안에서 열리지 않았다 |
| **`OPEN-ADR-07`**(M1 소유, slice 미지목) | mutation 도구 — 완료 조건 5 의 전수 측정 축 | `docs/adr/0007` 「`OPEN-ADR-07` · mutation 도구」 · capability map §12 의 그 행. **사용자 결정 2026-11-03 이 「표적 변이로 갈음」을 조건 5 의 명시적 예외로 승인하면서 이 OPEN 은 후속으로 명시해 남겼다** — 승인된 것은 갈음이고 전수 측정은 아직 없다(`checklist.md` §5 ②) |
| **M7 종결이 이미 넘긴 것** | `OPEN-7E2B-BUDGET-CLEAR` · `OPEN-7E3B-TIME-DISPLAY` · `OPEN-7E3B-MONEY-WORDING` · `OPEN-7E3A-OPENING-AXIS-HARNESS` · `OPEN-7C3B-*` 셋 · 7F 장부 둘 | `reports/evidence/m7/close/checklist.md` 4. **M6 종결이 다시 세지 않는다** — 두 번 세는 것을 피한다 |

**정정 2026-11-05 — 「운영자 결정 선행 slice 셋」 행의 6B-3 은 틀렸다.** 「셋 다 M6 안에서 열리지 않았다」는
**6F-5-b 에만 참이다**. **6B-3 은 이 판정의 base 보다 먼저 열려 닫혔다** — 운영자 결정 2026-09-26 ②(보존
90일)를 받아 PR #73 으로 구현·머지됐다(2026-10-14 · 정본 `reports/evidence/m6/6b3/scope.md` · 절차
`docs/runbook/m6-6b3-retention.md`). 그 행이 「여기로 모인다」고 적은 셋(`OPEN-6F5A-RETENTION` ·
`OPEN-6F6-PROFILE-RETENTION` · `OPEN-6F8-RAW-PII-RETENTION`)은 **그 slice 가 닫았다**. 받는 자리로 남는 것은
6B-3 이 신설한 서브 OPEN **여섯**(`OPEN-6B3-OUTBOX-UNSENT-RETENTION` · `OPEN-6B3-NOTICE-AUDIT-SNAPSHOT-RETENTION` ·
`OPEN-6B3-PURGE-LOCK-TIMEOUT` · `OPEN-6B3-FREE-TEXT-PII-AXIS` · `OPEN-6B3-BLOAT-AFTER-FIRST-PURGE` ·
`OPEN-6B3-INDEX-WINDOW`)이고 **처분하지 않고 등재를 유지한다**. `-FREE-TEXT-PII-AXIS` 의 「운영 첫 실행 전
실표본 판독」 선행 조건은 그대로다. **`-BLOAT-AFTER-FIRST-PURGE` 는 이 개정이 전제를 건드린 건**이다 — 그
등재는 「첫 실행이 90일 백로그를 지운 뒤의 부풀음」을 말하는데, 기간을 늘리면 첫 파기 집합이 **줄어** 그
우려가 미뤄진다(처분이 아니라 전제 변경이다). `-INDEX-WINDOW` 는 이 slice 와 무관하게 등재 유지다. **사용자 결정 2026-11-05 이 보존 기간을 1년으로
개정**했다(정본 `reports/evidence/m9/retention-1y/scope.md` — 90일 항목은 정책의 날짜 개정 표에 그 시대의
값으로 남는다, 소급 없음).

**이 문단의 첫 판이 같은 줄의 6B-4 를 틀렸다**(cr H-1, 2026-11-05) — 「6B-4·6F-5-b 에만 참」이라고 적었는데
**6B-4 도 열려서 닫혔다**(PR #71 「M6/6B-4: ML 학습 job 저널 · 대기 큐 상한」 머지 2026-10-14, 6B-3 의 PR #73
보다 먼저 · `reports/evidence/m6/6b4/` 존재). 그 처분은 이 slice 범위 밖이지만 **단언은 범위가 없다.** 낡은 한
줄을 고치는 문단이 같은 줄의 다른 항목에 새 거짓을 만들었고, 그 축은 §1 이 스스로 「내 술어가 보지 못하는
축」이라고 적은 바로 그 축이다(그 OPEN 을 받을 slice 가 이미 열렸는가).

§1 은 이 「받는 자리」 집합을 「작고 이름이 붙어 있다」며 건별 재고와 달리 신뢰했다. 이 정정은 그 집합도 낡는다는
**반례**다 — 다만 축이 다르다: 틀린 것은 OPEN 의 상태가 아니라 **그 OPEN 을 받을 slice 가 이미 열렸는가**이고,
§1 의 종결 어휘 근접 술어는 그 축을 아예 보지 않는다(이 행에는 종결 어휘가 없고 「열리지 않았다」가 있다).

## §3 6G 현재 상태 — 판정하지 않고 적는다 (D-M6C-1)

운영자 결정 2026-10-03(「다음 마일스톤」 절)이 6G 의 실수집과 백테스트 판정을 **M6 완료 조건과 분리**했다.
이 절은 그 상태를 적을 뿐이고 **6G 종결을 판정하지 않는다**. 6G 종결은 `milestone-6.md` 6G 절의 종결 문단으로
적는다(같은 결정).

- **수집 cron** — 매일 00:41 KST 에 걸린다. 기록은 호스트 로그(`~/.local/bid-vector-logs/cron-6g.log` 와
  `m6-6g/` 일별 로그, **읽기 전용으로만 보았다**). 로그 파일 자신이 day20 에서 시작하므로 그 앞의 날은 이
  로그로 세지 않는다. 러너는 **main 이 아닌 옛 배포물**로 돈다(러너 줄의 jar 경로가 레인 밖 작업 트리를
  가리킨다).
- **라이브 수집이 끝난 날은 day28(2026-10-29)이다** — 그날부터 마침 줄이 `budgetLimit=TOTAL · halted=true ·
  exit 2` 이고 표본·상세 호출이 **전부 0** 이다(day28~day32 전부). 그 앞은 사유가 달랐다: `QuotaExhausted`
  (일 쿼터 소진)이고 하루 표본 24,000 · 상세 3,353~3,407 건을 걷었다.
- **다만 「day20~day27 은 매일 걸었다」는 사실이 아니다.** 그 여덟 날 가운데 **수집이 돈 날은 여섯**
  (day20·21·22·25·26·27)이고, **day23·day24 는 시작 1초 안에 끝났다** — 마침 줄이 아예 없고
  (`log=none finished-line-missing`) 수집 0 이다. 두 날 모두 시작 줄의 swap 여유가 약 3.4GB 로, 빌드 자리
  래퍼와 같은 호스트 가드에 걸린 모양이다(이 판정은 원인을 단정하지 않는다).
- **수집 기록 브랜치** — `origin/m6-6g-collection/2026-10-02` 에 **main 에 없는 커밋 7 개**가 있고 마지막은
  **2026-10-16(day15)** 이다. 그 뒤 **day16~day27 의 열두 날 창에서 수집이 실제로 돈 날은 아홉**
  (day16·18·19·20·21·22·25·26·27 — day17 은 일별 로그가 없고 day23·24 는 위 사유)이고, **그 아홉 날의 결과
  행이 git 에 없다**. 「열두 날의 공백」은 **창의 길이**이지 열두 날분의 결과가 밖에 있다는 뜻이 아니다.
- **백테스트 판정** — 없다. 「6G 종결」의 정의가 「실수집 표본 완료 뒤의 백테스트 판정」(2026-10-03 결정)인데
  그 판정 기록이 저장소에 없다. 6G-rc(PR #98)는 **규칙 수정과 백테스트 정책 v2** 를 냈을 뿐 표본 판정이 아니다.
- **「6G 종결」을 넘긴다.** 받는 자리는 6G 레인이고, 그 종결이 `ML-01` 과 `OPEN-ML-ANALYSIS-WIRING` 의 전제다.

## §4 후속 제안

| # | 무엇 | 제안 |
| --- | --- | --- |
| ① | M6 범위 `OPEN-` **204 건 전수 재고** — 이 판정이 재지 않았다(§1) | 읽기 전용 조사 레인 한 건(M5 종결의 재고 레인과 같은 형태). 건별 상태는 **문면으로만** 잡고 종결 어휘 근접 술어를 판정으로 쓰지 않는다 — §1 의 보정 대조가 그 이유다 |
| ② | 6G 기록 공백 — day16~day27 창에서 **수집이 돈 아홉 날**의 결과가 git 에 없다(§3) | 6G 레인이 기록 브랜치를 보완하거나, 보완하지 않는다면 **그 공백을 사실로 선언**하고 백테스트 모수를 day15 까지로 적는다. 어느 쪽이든 판정 전에 정해야 한다 |
| ③ | 수집 cron 이 **옛 배포물**로 돈다(§3) | 라이브 수집이 끝났으므로 오늘 거동 영향은 0 이다. 재개한다면 배포물을 먼저 맞춘다 |
| ④ | **여정 e2e W-13 의 응답 포착 경합** — 필수 `check` job 을 간헐적으로 막는다(`checklist.md` §3 ⑤, CI 7 중 2) | **web 레인, 우선순위 높음.** 이 묶음 가운데 **유일하게 오늘 게이트를 붉히는 것**이라 받는 자리를 여기 함께 적는다. 거동 변경 0, test 전용 |

## §5 정정 — M9/budget-clear (2026-11-05)

이 문서는 2026-11-02 판정의 기록이라 **덮어쓰지 않는다**. 아래 둘은 §2 표의 두 행이 그 뒤의 사실과 어긋난
자리이고, 고치는 대신 여기에 적는다(근거는 각 slice 의 evidence 다).

- **「운영자 결정 선행 slice 셋 … 셋 다 M6 안에서 열리지 않았다」는 틀렸다 — 6B-4 는 열렸고 머지됐다**
  (PR #71). 그래서 그 행이 6B-4 로 모아 둔 둘의 상태는 「받는 자리에서 대기」가 아니다:
  `OPEN-5E-JOB-PERSISTENCE` 는 **닫는 후보**(파일 저널 + compose 볼륨으로 프로세스 재시작·컨테이너 재생성
  둘 다 기록이 남고 고아는 정해진 처분을 받는다 — 남는 것은 그 slice 의 알려진 제한 1·3·5)이고,
  `OPEN-5E-JOB-QUEUE-BOUND` 는 **부분 종결 후보**다(닫힌 것은 상태 상한과 그 코드의 생산 경로이고, 닫히지
  않은 것은 실행기 작업 큐의 **항목 수** — 폐기된 job 의 항목이 워커가 집을 때까지 남는다). 근거는
  `reports/evidence/m6/6b4/checklist.md` 의 OPEN 처분 절이고, 종결 판정은 그 구분을 보고 해야 한다.
  **같은 행의 나머지 둘은 그대로다** — 6B-3 과 6F-5-b 는 여전히 열리지 않았고, 6F-5-b 는
  **사용자 결정 2026-11-05 로 보류**다(그래서 `OPEN-6F4-STR02-REQUIREMENTS-AXIS` 의 축소도 유지된다 —
  정본은 `docs/discovery/capability-map.md` 의 STR-02 절과 그 OPEN 행).

- **`OPEN-6F-ASSEMBLY` 계열 행의 「잔여」를 실측으로 좁힌다.** 그 행이 대표로 적은 둘은 **이미 닫혔다** —
  `OPEN-6F-ASSEMBLY`(포트 아홉의 Spring 배선)와 `OPEN-6F4W-ASSEMBLE-CALLER`(조립 호출자의 ArchUnit 집합
  등식)를 6A-3f3 이 수령·닫았다(`reports/evidence/m6/6a3f3/scope.md` 의 OPEN 표). 그 계열에서 **아직 열린
  것은 넷**이다: 재확인 ④(합성 `toString()` 진단 경로 — 받는 자리는 로거를 처음 들이는 slice 또는
  `OPEN-ML-ANALYSIS-WIRING`, 같은 scope 가 적는다) · `OPEN-6F4W-UNAVAILABLE-PRODUCER` ·
  `OPEN-6F4-TITLE-INGEST` · `OPEN-6F10-*`. 이 slice 는 **장부 문면만** 고치고 그 넷은 다른 M9 slice 가
  받는다(코드 변경 0).

## §6 정정의 정정 — M9/budget-clear (2026-11-05, PR #124 수정 라운드)

§5 도 덮지 않는다. 위 §5 의 둘째 문단에 틀린 것이 하나, 덜 센 것이 하나 있었다 — 검증 레인이 잡았고
(v M-2) 그 오류는 **slice 계약 D-4 문면에서 옮겨 온 것**이다(계약 쪽 정정은 `reports/evidence/m9/budget-clear/scope.md`
「계약 갱신 2026-11-05 r2」).

- **`OPEN-6F4-TITLE-INGEST` 는 열린 것이 아니라 닫혔다.** 6F-8 이 수령·닫았다 — 공고명을 필드 계약
  데이터(`FieldConcept.NOTICE_TITLE`)가 나르게 한 결정이고(`reports/evidence/m6/6f8/scope.md` 의 D-6F8-2 와
  같은 문서 OPEN 표의 「수령·닫음」 행), milestone-6 의 「6F-8 종결 2026-09-24」 문단이 PR #45 로 적는다.
  §5 가 그 식별자를 「아직 열린 넷」에 넣은 것은 **실측이 아니라 계약 문면의 복제**였다.
- **`OPEN-6F10-*` 와일드카드는 실제 식별자로 바꾼다.** 그 계열에서 열린 채인 것은 **여덟**이고 근거 자리는
  6F-10 의 「이 slice 가 신설한 OPEN」 표(일곱)와 `milestone-6.md` 6F-10 종결 문단의 신설 목록이다 —
  `OPEN-6F10-CLAIM-OBSERVABILITY` · `OPEN-6F10-CLAIM-INDEX` · `OPEN-6F10-CAPACITY-SOURCE` ·
  `OPEN-6F10-EVALUATION-DOMAIN-WRITE` · `OPEN-6F10-SCHEDULER` · `OPEN-6F10-CAUSE-CODE-DEDUP` ·
  `OPEN-6F10-RELAY-BOOT-WIRING-LOCK` · `OPEN-6F10-STRATEGY-EVENT-CONSUMER`(D-6F10-13 이 신설, 그 표에는 없고
  종결 문단이 든다 — **두 자리가 어긋나 있다**는 사실도 함께 적는다).
- **그 여덟에 처분을 적은 뒤 slice 는 없다.** 종결 어휘 근접 술어는 여덟 모두에서 「있음」을 냈지만 그
  일치는 전부 **6F-10 종결 문단 한 줄**이다(한 줄에 식별자 여덟과 종결 어휘가 함께 있다) — §1 이 적은
  바로 그 오탐 꼴이다. 그래서 이 문단은 「열려 있다」를 6F-10 의 신설 표와 종결 문단으로만 주장하고,
  건별 재판정은 §4 후속 ① 의 전수 재고에 그대로 넘긴다.

그러므로 §5 둘째 문단의 「아직 열린 것은 넷」은 **재확인 ④(합성 `toString()` 진단 경로) ·
`OPEN-6F4W-UNAVAILABLE-PRODUCER`(6F-4W 신설 뒤 처분 기록 없음, 실측) · `OPEN-6F10-*` 여덟**으로 읽는다.

## §7 정정의 정정의 정정 — M9/budget-clear (2026-11-05, PR #124 라운드 3)

§6 도 덮지 않는다. §6 의 셋째 문단이 「종결 어휘 근접 술어는 **여덟 모두에서** 「있음」을 냈고 그 일치는
전부 6F-10 종결 문단 **한 줄**이다(한 줄에 식별자 여덟과 종결 어휘가 함께 있다)」고 적었는데, **그 한 줄에
있는 식별자는 일곱**이다. 검증 레인이 잡았다(v r2 L-a). 실측으로 바로잡는다.

- **6F-10 종결 문단(`milestone-6.md`)의 6F10 식별자는 일곱**이다 — `CAPACITY-SOURCE` · `CLAIM-INDEX` ·
  `CLAIM-OBSERVABILITY` · `EVALUATION-DOMAIN-WRITE` · `RELAY-BOOT-WIRING-LOCK` · `SCHEDULER` ·
  `STRATEGY-EVENT-CONSUMER`.
- **6F-10 「이 slice 가 신설한 OPEN」 표의 6F10 식별자도 일곱**인데 **같은 일곱이 아니다** — 그 표에는
  `STRATEGY-EVENT-CONSUMER` 가 없고 대신 `CAUSE-CODE-DEDUP` 이 있다.
- `CAUSE-CODE-DEDUP` 은 **`milestone-6.md` 전체에서 0 회**다(그 문단만이 아니라 그 파일 어디에도 없다).
  그 식별자의 유일한 자리는 6F-10 checklist 의 신설 표이고, 그래서 마일스톤 문서만 읽는 쪽에는 **그 OPEN 이
  존재하지 않는 것으로 보인다** — 어긋남이 「문단에 빠졌다」보다 한 단 더 깊다.
- 재현(둘 다 이 문단의 근거다):
  `sed -n '/6F-10 종결/p' milestone-6.md | command grep -oE 'OPEN-6F10-[A-Z-]+' | sort -u` → **일곱** ·
  `command grep -c 'OPEN-6F10-CAUSE-CODE-DEDUP' milestone-6.md` → **0**.
- 그래서 **어긋남은 양방향**이다(§6 은 한 방향만 적었다): 문단에만 있는 것 하나(`STRATEGY-EVENT-CONSUMER`,
  D-6F10-13 신설) · 표에만 있는 것 하나(`CAUSE-CODE-DEDUP`). 합집합 여덟 · 교집합 여섯.
- **닫음어휘 일치는 일곱이고 `CAUSE-CODE-DEDUP` 은 0 건**이다. 즉 §6 의 「여덟 모두」는 두 번 틀렸다 —
  모수가 일곱이고, 여덟째는 애초에 일치가 없었다. 일치한 일곱의 출처가 **그 종결 문단 한 줄**이라는 §6 의
  판단 자체는 그대로 유효하고(§1 의 오탐 꼴), 「여덟 다 열려 있다」는 결론도 바뀌지 않는다 — 바뀌는 것은
  **그 결론을 받치는 계수**다.

세 번 고친 자리이므로 교훈을 함께 적는다: **정정은 「무엇이 틀렸나」만 적고 「몇 개인가」를 다시 세지 않으면
또 틀린다.** §5 는 계약 문면을 복제해 틀렸고, §6 은 식별자 집합을 고치면서 그 집합의 **계수를 눈으로** 셌다.
§4 후속 ① 의 전수 재고가 이 계열을 받을 때는 모수를 **기계로** 산출하는 것이 전제다.

## §8 M9 머지분의 처분 — M9/ledger (2026-11-06)

§1~§7 을 덮지 않는다. **§2 「받는 자리」 표의 두 행이 M9 머지분으로 줄었다** — 줄어든 사실만 적고 건별
재판정은 하지 않는다(§4 후속 ① 의 전수 재고 그대로). 장부 쪽 정본은 `docs/discovery/capability-map.md`
§12.3 이고 「닫힘」 주장마다 PR·머지 커밋을 그 절이 든다.

**「M7 종결이 이미 넘긴 것」 행 — 아홉 가운데 일곱이 닫혔다.**

- `OPEN-7C3B-FAILED-BID-DETAIL` · `OPEN-7C3B-REVISION-REUNREAD` → PR **#123** 머지 `df4976cc`.
- `OPEN-7E3B-TIME-DISPLAY` · `OPEN-7E3B-MONEY-WORDING` → PR **#125** 머지 `4953d743`.
- `OPEN-7E2B-BUDGET-CLEAR` → PR **#124** 머지 `319fe212`.
- 7F 장부 v r2 **L-1**·**L-2** → PR **#122** 머지 `6c38fb32`.
- **남는 둘**: `OPEN-7E3A-OPENING-AXIS-HARNESS`(M9 가 만지지 않았다) · `OPEN-7C3B-PAPER-BID-NOTIFICATION`
  (#123 의 out_scope — 그 계약이 「이 slice 머지 뒤 별도」로 적었다). 같은 행이 든
  `OPEN-6F4-STR02-REQUIREMENTS-AXIS` 는 #124 가 **열린 채 상태만 갱신**했다(6F-5-b LLM 추출이 사용자 결정
  2026-11-05 로 보류라 축소 유지).

**「하네스 레인」 행 — 열둘 가운데 하나가 닫혔다(열하나로 읽는다).**
`OPEN-GATE-REGISTRATION-STALE-INPUT` 은 **6G-2g 가 구조적으로 닫았다** — 등재 등식을 손으로 건 test
여덟에서 build-logic task 하나로 올리고 그 task 가 정책 파일을 선언된 입력으로 받는다(PR **#60**,
D-6G2g-12). **§2 가 이 묶음을 「열둘 전부 어느 문서에도 종결 어휘가 붙은 적이 없다」로 센 것은 그 시점에
참이었고 지금은 아니다** — 틀린 축은 또 「그 OPEN 을 받을 레인이 이미 일했는가」이고(§2 의 6B-4 정정과
같은 축), 6G-2g 는 2026-10-05 에 머지됐으므로 **2026-11-02 판정보다 먼저다**. 즉 이 한 건은 낡은 것이
아니라 **그때도 이미 닫혀 있었다** — §1 의 종결 어휘 근접 술어가 보지 못한 것은 6G-2g 의 「닫은 OPEN
여덟」 목록이 **수령한 일곱만 열거하고 이 식별자를 빼놓았기** 때문이다(한 방향 누락, §7 의 6F-10 양방향
어긋남과 같은 꼴).

**6B-3 보존 기간은 1년으로 개정됐다**(사용자 결정 2026-11-05 · PR **#129** 머지 `31551579`). §2 의 2026-11-05
정정 문단이 적은 그대로이고, 서브 OPEN 여섯은 처분 없이 등재 유지다.

**M9 가 신설한 OPEN 일곱**(화면 표시 다섯 · CVE apt 둘)은 이 문서가 아니라 capability map §12.3 (b) 가
받는다 — 이 문서는 M6 종결의 기록이고 M9 잔여의 registry 가 아니다.
