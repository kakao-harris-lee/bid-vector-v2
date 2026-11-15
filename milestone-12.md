# 마일스톤 12 — M11 신설 OPEN 다섯과 여정 경합

> **지위: 계약(2026-11-14, 세션 모델 단독 저작).** 범위는 사용자 결정 2026-11-14 「M12: M11 잔여」가 승인했다.
> 받는 것은 M11 종결 절이 넘긴 신설 OPEN 다섯과 OPEN 없는 후속 하나다. 전부 외부 effect 없이 닫을 수 있다.
> M10·M11 과 같이 **착수 전에** 완료 조건을 고정한다. slice 범위는 착수 뒤 팀장 판정으로만 바뀌고, 그때 아래
> slice 표와 「착수 판정」 절을 함께 고친다.

## 범위 밖 — 이 마일스톤이 만들지 않는 것

- **G3 에서 채택한 기능 넷**(운영자 결정 2026-11-14 — 대화형 Telegram 전략 편집 · 실적 자격 · 페이퍼 투찰 정산 ·
  라이브 메일). 별도 기능 마일스톤이 받는다(capability map §12 G3 절 머리의 결정 문단).
- **M9 보류 셋**(수집 연장 · 6F-5-b LLM 추출 · `OPEN-ML-ANALYSIS-WIRING`) — 사용자 결정 2026-11-14 「계속 보류」.
- **M10·M11 계약의 범위 밖 전부**(사용자 결정 · 실데이터·외부 호출 선행 · 하네스 축 · `OPEN-VERDICT-CONSTRUCTION-VISIBILITY`).
- **새 KONEPS·외부 API 호출, 실 DB, 실 발송.** 0 이다.

## 받는 항목

| # | 항목 | 출처 |
| --- | --- | --- |
| 1 | `OPEN-M11-JAVAP-ROOT-REGEX-UNANCHORED` — 다섯(① 형제 축 비경계 정규식 ② 호출 꼴 비용 ③ 추출 층 대조 부재 ④ 「다른 이름의 판정 심볼」 구조 처방 ⑤ 양성 표본 유한 열거) | §12.3 (o) |
| 2 | `OPEN-M11-SCREEN-COVERAGE-CROSS-FILE-VOCAB` — ⓐ 기존 표시 모듈의 래퍼 ⓑ 새 표시 모듈 ⓒ 함수 값 표·클래스 static·`fn \| undefined` ⓓ 재수출 키 불일치 | §12.3 (l) |
| 3 | `OPEN-M11-SCREEN-COVERAGE-POPULATION-DIR` — 모집단이 `src/screens/**` 라 그 밖의 카드는 의무를 내지 않는다 | §12.3 (l) |
| 4 | `OPEN-M11-GATE-TEST-WARMUP-BUDGET` — 게이트 test 첫 칸이 기동 비용을 지는 꼴(후보 둘) | §12.3 (l) |
| 5 | `OPEN-M11-KOTLIN-READ-GATE-RESIDUALS-2` — ⓐ~ⓕ(그 행 머리는 「남는 다섯」이나 열거는 여섯 — ⓕ 는 v r1 L-1 이 더했다) | §12.3 (n) |
| 6 | 여정 e2e ③ 타임라인 항목 수 경합(OPEN 없음) | `milestone-11.md` 종결 절 · PR #157 첫 CI |

## slice

넷은 병렬로 진행하고 머지는 하나씩 한다. §12.3 절 글자는 **머지 순서**로 붙인다 — 각 slice 는 자기 PR 이 main 을
마지막으로 병합할 때 그 시점의 다음 글자((p)부터)를 쓰고 절을 끝에 덧붙인다. 공유 파일(capability map §12.3 ·
`config/quality/gate-tests.properties` · `web/config/gate-tests.txt` · `web/vite.config.ts` `test:` 블록 · **`web/scripts/kotlin-source-ownership.test.mjs`·`web/scripts/kotlin-source.mjs`·`web/scripts/kotlin-read-guard-setup.mjs`**(12C 가 고치고 12D 가 재는 자리))은 충돌 0 이어도
절·행·키 계수와 이름으로 합집합을 확인한다(M11 에서 자동 병합이 절을 앞에 끼우거나 글자를 겹친 일이 둘 있었다).

| slice | 항목 | 무엇이 닫는가 |
| --- | --- | --- |
| **12A** `adapters-javap-unify` | 1 | adapters dependency test 열네 축의 상수 풀 술어를 **공유 헬퍼 한 자리**로 모은다(`BytecodeConstantPool.kt` 를 넓힌다). 좌측 경계 고정 정규식 · 일괄 javap · 추출 층 양성 대조 · 양성 표본을 `settings.gradle.kts` 모듈 뿌리에서 **구성**으로 뽑기 · 판정 호출을 축 test 에서 빼 **표를 순회하는 단일 test** 가 판정하게 하기(④). 다섯이 같은 파일들을 만지므로 한 slice 가 받는다. 형제 열 축의 오늘 판정 집합이 바뀌지 않음을 per-file ↔ 일괄 대조로 잠근다. 비용이 줄었는지 잰다(M11 실측: persistence 한 축 per-file 56.9초 ↔ 일괄 1.0초) |
| **12B** `screen-coverage-3` | 2 · 3 | 화면 카드 모집단의 남은 두 축. ⓐⓑ 는 어휘 도달을 `src/display/` 안에서 **파일 사이 폐포**로 넓힌다 — 실측(M11/11E v F1)으로 의무가 둘 는다(`StrategyFieldRow` · `StrategyEditPanel`). **그 둘을 마운트 목록과 DOM 게이트에 더하는 것은 이 계약이 승인한다**(test 추가뿐, 출하 코드 0). ⓒ 함수 값 표 등은 「부를 수 있는 것」 질의를 속성까지 넓히는지 설계 검토가 정한다. ⓓ 는 해소된 선언의 자리를 키로 쓰는 처방. 3 은 모집단을 `src/components/**` 로 넓힐지 설계 검토가 판정한다 — 「마운트할 수 있는 카드」의 뜻을 먼저 고정한다 |
| **12C** `gates-web-read-3` | 5 | Kotlin 원문 읽기 게이트의 남은 여섯. **위협 모델 경계는 M11 계약의 「11D 위협 모델 경계」 절을 그대로 승계한다**(평범한 관용구의 표류는 막고 의도적 은닉은 등재). ⓐ 두 홉 추출 함수 · ⓑ vitest 밖 프로세스와 `fs` 표면 밖 다섯 꼴 · ⓒ~ⓕ. ⓑ 는 새 층(프로세스·번들러 경계)이 필요해 **설계 검토가 「닫음 / 정적 층 보강 / 등재 유지」를 축마다 먼저 판정**하고 팀장이 승인한다. 꼴 열거로 라운드를 돌리지 않는다(M10/10D 교훈) |
| **12D** `web-test-stability` | 4 · 6 | ① 여정 ③ 의 타임라인 단언이 먼저 받은 응답 길이가 아니라 **안정된 마지막 응답** 또는 화면이 반영한 상태를 기준으로 삼게 고친다. 경합을 재현하는 대조를 먼저 세운다 ② 게이트 test 후보 둘(`money-vocab-cross-lang.test.mjs` · `kotlin-source-ownership.test.mjs`)의 첫 칸 소요를 부하 조건과 함께 재고, 예산 대비 여유가 얇으면 11E 처방(모듈 적재 단계 예열)을 적용한다. 측정 없이 고치지 않는다. **12C 와 겹치는 파일의 측정은 12C 머지 뒤 그 main 에서 다시 잰다**(12C 가 그 모듈 그래프를 바꾼다) |

**게이트 slice(12A·12B·12C)는 구현 전에 설계 검토(v2-slice-pipeline Phase 2.5)를 거친다.** 술어는 구조로 닫는다.
변이는 바꿔치우기로, 커밋 뒤 `--rerun` 으로, 적용 단언과 함께. 「`beforeAll` 예열」은 예산을 hook 으로 옮길 뿐이다(M11/11E 실측).

## 착수 판정 (팀장 기록 — 범위 밖 판정의 정본)

| slice | 판정 |
| --- | --- |
| 12D | 승인: 여정 ③ 의 「쓰기 응답 뒤에 출발한 첫 200 읽기」 장부를 `web/e2e/journey/shared.ts` 한 자리로 올리고 `onboarding.spec.ts` 가 그것을 쓰게 한다(in_scope). ② 는 부하 아래 반복(전건 ≥ 20회, 부하 조건 기록)으로 여유를 재고 두꺼우면 항목 4 를 「이미 닫힘 — 후보 둘의 첫 칸은 기동 비용을 지지 않는다」로 처분(얇으면 처방). `kotlin-source-ownership` 무거운 칸은 여유 3배 미만일 때만 신설 OPEN. 범위 밖 유지: 날 `waitForResponse` 를 금지하는 구조 게이트 → 신설 OPEN(오늘 경합 자리 0) |
| 12B | 승인: 「마운트할 수 있는 카드」를 **타입**(JSX 반환 내보낸 선언 · `render` 클래스)으로 고정하고 모집단을 `src/**` 로 넓혀 POPULATION-DIR 을 닫는다(오늘 의무 증가 0) · 등식 모집단을 `src/display/**` 로 넓히고 새 실례(`editSessionDisplay` 의 자체 금액 문면 함수)를 씨앗 등재(분류 표 +27 행 — 출하 코드·마운트 의무 아님) · ⓒ 는 경계 있는 술어(자기·사상 키 한 홉, 원시·배열·튜플 제외)로 닫는다. 마운트 의무가 7 을 넘으면 멈춘다 |
| 12C | 승인(2026-11-14): ⓑ⑥ 은 진입점 열거가 아니라 **선언에서 뽑은 의무**로 닫고 마지막 별 커밋으로 한다 · ⓒ 는 경계로 둘로 가른다(추출 전 정규화는 경계 안 · 걷기 재구현·항등 함수는 경계 밖) · ⓓⓔ 는 위협 모델 경계 밖으로 등재 유지(승인 2026-11-13·2026-11-14). 리뷰 중(2026-11-15): 무거운 칸의 첫 패스 웃돈은 이 slice 에서 닫지 않고 `OPEN-M12-GATE-AST-RESCAN-COST` 로 등재 |
| 12D 리뷰 중 | 판정(2026-11-15): 부하 아래 재현된 칸 타임아웃은 예산 축이라 `OPEN-M12-GATE-TEST-BUDGET-UNDER-LOAD` 로 등재하고 (q) 행은 고치지 않는다 · 머지 차례의 초안 거둠은 주입 교착을 내므로 철회(처방 (a)) |
| 12A | 승인(2026-11-14): M11 알려진 제한 10 의 `MutableSet` 제자리 변경을 축 값 불변 집합 + 거동 단언으로 닫는다 · 축이 없는 adapters 1급 패키지 여섯에 새 축을 세우는 것은 범위 밖 → `OPEN-M12-ADAPTERS-PACKAGES-WITHOUT-AXIS`. 머지 판정(2026-11-16): 반환값 계약의 부분 절단(cr LOW-1 · v L-c)은 후속(아래 종결 절) |

## 완료 조건

1. slice 넷이 각자 PR 로 머지됐다. 각 PR 은 verifier `ready-for-review` · code-reviewer(`model: sonnet` 으로 띄운 다른 패스) approve(또는 차단 0) · head CI 5/5 를 거쳤다. 판정은 PR 코멘트에 남는다.
2. 받는 항목 여섯이 각각 **닫힘** · **이미 닫힘**(근거 PR·SHA + 재측정) · **범위 밖 유지**(판정자는 팀장, 착수 보고나 구현 전 설계 검토 보고에 대한 답으로만) 가운데 하나로 처분됐다. 범위 밖 판정은 **종결 절을 쓰는 시점의** 「착수 판정」 절이 전부다.
3. OPEN ID 가 있는 항목과 새 OPEN 은 capability map §12.3 의 새 하위 절에 머지 순서 글자로 날짜와 함께 등재됐다. 항목 6 은 12D evidence checklist 에 처분을 적는다.
4. 외부 effect 0 이다.
5. 이 문서의 종결 절을 세션 모델이 쓴다.

## 승인 경계

결정 지점은 추천안으로 진행한다(전역 규칙). 다음은 멈추고 보고한다.
- **새 게이트가 오늘의 production 코드를 붉힐 때.** 위반을 고치는 것은 새 결정이다. 12B 의 마운트 목록 확장(의무 +2)은 이 계약이 미리 승인했다 — 그 밖의 의무 증가는 멈춘다.
- **출하 코드 변경이 필요해질 때**(이 마일스톤은 게이트·test·장부 축이다).
- 외부 effect 나 Flyway 마이그레이션이 필요해지는 경우.
- 재작업 상한 5(not-ready + request_changes 합산)에 닿을 때 — 진단을 붙여 보고한다.

## 종결 (2026-11-16)

**M12 종결.** slice 넷이 각자 PR 로 머지됐다. 외부 effect 는 0 이다. 각 PR 은 verifier `ready-for-review` · code-reviewer(`model: sonnet` 으로 띄운 다른 패스) approve(또는 차단 0) · head CI 5/5 를 거쳤고, 판정과 조치와 팀장 머지 판정은 PR 코멘트에 있다.

| slice | PR | 머지 커밋 | 재작업 | 비고 |
| --- | --- | --- | --- | --- |
| 계약 | #161 | `2de8bc4e` | — | 이 계약 |
| 12B `screen-coverage-3` | #162 | `e07adaa8` | 2/5 | 첫 CI 의 `check` 는 여정 ③ 타임라인 항목 수 경합(항목 6 — 12D 가 닫았다) — 문서만 바뀐 라운드라 실패 job 재실행 |
| 12C `gates-web-read-3` | #164 | `3fd8f62a` | 2/5 | r2 blocker: `.npmrc` 의 상대 지정자가 의존 install script 에서 `npm ci` 를 죽였다 — 레인 측정이 설치된 `node_modules` 를 재사용해 놓쳤고, 그 측정을 빈 트리 설치부터 재는 꼴로 바꿨다 |
| 12D `web-test-stability` | #163 | `15a0cb8f` | 1/5 | 머지 차례에 더한 초안 거둠이 주입 아래 교착을 냈다 — 철회하고 알려진 제한으로 등재 |
| 12A `adapters-javap-unify` | #165 | `90d2a0c4` | 1/5 | 머지 차례 cr 차단 high: 새로 건 덮개 등식이 망원합 항등식(입력과 무관하게 참) — 반환값 계약으로 바꾸고 변이 두 자리 RED 를 세 레인이 각자 실측. PR CI 는 충돌로 머지 차례까지 한 번도 발화하지 않았다 |

- **완료 조건 2 — 받는 항목 여섯.** 닫힘: 1(①~⑤ 전부 · M11 알려진 제한 10 의 `MutableSet` 제자리 변경도 거동 단언으로 닫음 — (s)) · 3((p)) · 6(12D `checklist.md` — 쓰기가 자기 재조회를 낳고 그 늦은 응답이 다음 포착에 걸리는 자리 다섯) · 2(ⓐ~ⓓ 닫힘 — 닫는 과정에서 드러난 인접 경계 넷은 신설로 등재, (p)). **이미 닫힘**: 4(후보 둘은 `typescript` 를 모듈 꼭대기에서 들여 기동 비용이 칸·hook 예산 밖이다 — 처방의 대상이 없다, (r)). **갈림**: 5(ⓐ · ⓑ③⑤⑥ · ⓕ-1·ⓕ-2 닫힘, ⓑ①②④ · ⓒ · ⓓ · ⓔ · ⓕ-3 은 `OPEN-M12-KOTLIN-READ-GATE-RESIDUALS-3` 로 승계, (q)). 범위 밖 판정은 위 「착수 판정」 절이 전부다(이 절을 쓰는 시점 기준).
- **완료 조건 3** — ID 있는 처분은 capability map §12.3 (p) 12B · (q) 12C · (r) 12D · (s) 12A 에 머지 순서대로 날짜와 함께 붙었다. ID 없는 항목 6 의 처분은 12D `checklist.md` 에 있다. 머지마다 절 글자 집합·중복 0·남의 절 본문 바이트·`gate-tests` 등재 합집합을 셌고, 12A 는 §12.3 에서 실제 충돌 하나를 만나 `(s)` 로 풀었다.
- **완료 조건 4·5** — 외부 effect 0(새 KONEPS 호출·실 DB·실 발송 없음 — 각 slice scope.md 의 out_scope 와 acceptance 가 로컬·CI 뿐). 이 절이 조건 5 다. 위 「착수 판정」 12C·12A 행과 리뷰 중 판정 행은 이 절과 같은 커밋에서 더했다.
- **신설 OPEN 아홉**(받는 자리는 각 OPEN 행이 정본, 마일스톤 수준 배정은 다음 계획이 한다) — 화면 카드 축 넷 `OPEN-M12-SCREEN-REACH-NON-DISPLAY-WRAPPER` · `OPEN-M12-SCREEN-FALLBACK-VISIBLE-CODE` · `OPEN-M12-SCREEN-STRING-RETURNING-CARD` · `OPEN-M12-SCREEN-MOUNT-PROOF-WHOLE-TEXT`(p) · Kotlin 원문 읽기 게이트 `OPEN-M12-KOTLIN-READ-GATE-RESIDUALS-3` 과 게이트 test 비용 `OPEN-M12-GATE-AST-RESCAN-COST`(q — (r) 이 수치를 보탰다) · e2e `OPEN-M12-E2E-RESPONSE-CAPTURE-UNGUARDED` 와 게이트 test 예산 `OPEN-M12-GATE-TEST-BUDGET-UNDER-LOAD`(r) · `OPEN-M12-ADAPTERS-PACKAGES-WITHOUT-AXIS`(s). 받은 다섯을 닫고 아홉이 생겼다 — 술어를 구조로 바꿀 때마다 그 구조의 경계가 이름을 얻었다. 이 가운데 `OPEN-M12-GATE-TEST-BUDGET-UNDER-LOAD` 는 부하 아래 칸 타임아웃이 **재현된 실패**라 다음 계획이 먼저 본다.
- **OPEN 없는 후속 둘** — ① 12A 반환값 계약이 상수 풀 항목 「≥1」만 재서 첫 항목 뒤를 자르는 부분 절단은 판정 test 가 초록이다(PR #165 cr LOW-1 · v L-c, 비차단). 실 노출은 javap 비정상 종료 가드가 덮는다. 고치면 복원 대상 파일이 다시 움직여 rollback 재실측이 따라오므로 머지 판정에서 넘겼다 — `BytecodeConstantPool.kt` 를 다음에 만지는 slice 가 받는다. ② PR #163 cr 의 선택 LOW 들은 다음 web slice 가 받는다(PR #163 팀장 머지 판정).
- **운영 교훈 셋** — ① 레인이 긴 배경 작업의 끝을 놓쳐 idle 로 멈춘 일이 이번에도 반복됐다(12A 는 한 번 12시간). 팀장이 빌드 프로세스와 로그 끝줄을 직접 감시해 깨웠다. ② 「하네스 점유」 경보가 `pgrep -f` 가 자기 셸을 센 오탐이었다 — 점유는 `ps` 로 셸을 빼고 센다. ③ 의존 설치에 닿는 설정 변경은 설치된 `node_modules` 위의 측정으로 초록이 될 수 있다 — 빈 트리 `npm ci` 부터 잰다(12C r2).
