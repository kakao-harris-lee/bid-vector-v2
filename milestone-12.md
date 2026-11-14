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
| (착수 보고를 받으면 여기에 적는다) | |

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
