# 마일스톤 10 — 외부 effect 없이 닫을 수 있는 M9 잔여

> **지위: 계약(2026-11-11, 세션 모델 단독 저작).** 범위는 사용자 지시 2026-11-11 「1번 진행해, 끝나면 머지까지」가
> 승인했다. 「1번」은 M9 종결 뒤 남은 OPEN 가운데 **승인 없이 바로 할 수 있는 것**(코드·문서, 외부 호출 0)
> 묶음이다. 이 문서는 그 묶음을 slice·완료 조건으로 옮긴다. M9 와 달리 **착수 전에** 완료 조건을 고정한다.
>
> **선행:** PR #146(M9 종결)이 먼저 머지된다. 아래 OPEN 가운데 셋(`OPEN-M9-COMMAND-OPENAPI-ENUM` ·
> `OPEN-SCOPED-ROOTS-PARITY-REST` · `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP`)의 registry 행은 그 PR 의 §12.3 (f) 가
> 세운다. README 의 M9 행도 그 PR 이 더한다. 이 문서의 README 행은 그 뒤에 병합해 이어 붙인다.

## 범위 밖 — 이 마일스톤이 만들지 않는 것

- **사용자 결정이 필요한 것.** G3 범위 결정, G4 legacy 의도 판정, `OPEN-ADR07-*` 비용 결정, `OPEN-M9-DDAY-CALENDAR`(거동 변경), 6G 리뷰 잔여 11 의 처분, 운영 잔여 분류 변경, `OPEN-ADR-15` 가 여기에 든다.
- **실데이터·외부 호출이 선행인 것.** G2 값 재유도, G5 재측정, G1 외부 정의, 조달청 02 응답 필드 확인 호출, 분리 오퍼레이션 수집, 낙찰 실 수집, Telegram 활성화, 운영 반입이 여기에 든다.
- **새 KONEPS·외부 API 호출, 실 DB, 실 발송.** 0 이다.
- **하네스·evidence 규율 축.** `OPEN-CODEX-RECORD-COORDINATES` · `OPEN-HARNESS-ROLLBACK-VALIDITY-BY-KIND` 가 여기에 든다. 받는 자리는 하네스 레인이다(PR #147 이 같은 축의 첫 정리를 한다).
- **`OPEN-M9-REHEARSAL-FLYWAY-CONFIG-PARITY`.** 그 스크립트의 인자 변경은 CI `container` job 에서만 실측된다. 거동 차이는 0 이라 다음에 그 스크립트를 만지는 slice 로 둔다.

## slice

> 이 표는 registry(§12.3 (b))에서 유도한 착수 전 판이다. 처분의 정본은 아래 「착수 실측」 표다.

네 slice 는 서로 다른 경로를 만지고 병렬로 진행한다. main 이 strict up-to-date 이므로 머지는 하나씩 하고,
뒤 PR 은 앞 머지를 병합한 뒤 다시 잰다.

| slice | OPEN | 무엇이 닫는가 |
| --- | --- | --- |
| **10A** `notice-resume-total` | `OPEN-M9-NOTICE-LIST-RESUME-TOTAL` | 공고 목록 갈래(`CollectNoticesUseCase`)가 쪽 상한 재개 뒤에도 `collection_run.source_total` 에 관측한 최댓값을 적는다. 낙찰 갈래(#128)와 같은 꼴로 그 use case 한 자리에서 고친다. **공유 누산기는 바꾸지 않는다** — 바꾸면 두 갈래 거동이 함께 바뀐다. 재개 첫 쪽이 「없음」인 경우를 RED 로 먼저 세운다 |
| **10B** `timeline-command-enum` | `OPEN-M9-COMMAND-OPENAPI-ENUM` · `OPEN-M9-TIMELINE-COMMAND-WORDING` | `openapi/bidvector-operator-api.yaml` 의 타임라인 `command` 칸을 Kotlin 명령 어휘와 같은 집합의 `enum` 으로 올린다. web 은 생성 타입에서 유니온을 끌어온다. 손 유니온과 Kotlin 원문 텍스트 파싱 게이트는 걷는다. 명령 토큰에는 한국어 표시 표(`satisfies Record<…>` 전수)를 둔다. Kotlin 어휘 ↔ openapi enum 등식은 기존 openapi 계약 test 층에 건다 |
| **10C** `gates-adapters` | `OPEN-SCOPED-ROOTS-PARITY-REST` · `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` · `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` · `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` | 넷 다 adapters·domain test 술어 축이다. ① 남은 범위 축 일곱에 두 층 허용 뿌리 등식을 단다. 최장 접두 사각은 「축 접두가 서로 포함하지 않는다」 단언으로 닫는다 ② `PersistenceAdapterDependencyTest` 를 소스 import 정규식에서 바이트코드 상수 풀 술어로 바꾼다 ③ `CleanMigrationCheckTest` 의 있는지만 보는 단언을 본문 집합 단언으로 바꾼다 ④ verdict 생성 가시성의 구조적 종점을 닫는다(정의는 `milestone-6.md` 6F-5 절과 `reports/evidence/m6/6f5a/scope.md` D-6F5-20). 원 정의를 읽은 뒤 처방이 계약 밖 편집을 요구하면 착수 보고에서 멈춘다 |
| **10D** `gates-web` | `OPEN-M9-CLOCK-LINT-SCOPE` · `OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH` · `OPEN-M9-MONEY-VOCAB-CROSS-LANG` · `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP` | web 게이트 축이다. ① 벽시계 lint 가 `Intl`·`Temporal.Now`·`performance.timeOrigin` 과 제외 디렉터리 경유 값까지 덮는다 ② 화면 카드 모집단을 식별자 이름이 아니라 TS 심볼 import 그래프로 잰다(재수출·별칭 포함) ③ 금액 어휘 네 벌 복제를 하나의 출처 + 등식으로 줄인다. openapi 가 `string` 인 칸이 문제라 10B 와 같은 방향(enum)이 처방 후보다. 10B 와 경로가 겹치면 10B 뒤로 미룬다 ④ Kotlin 주석 걷기가 문자열 안 `//` 를 보존한다. 줄 단위 구현을 공유 한 자리로 모으고 URL 리터럴 표본으로 RED 를 선행한다 |

**게이트 slice(10C·10D)는 구현 전에 설계 검토(v2-slice-pipeline Phase 2.5)를 거친다.** 술어는 문자열이 아니라
구조로 닫는다. 변이는 바꿔치우기로 하고, 통제군은 `--rerun` 으로 돌린다.

## 착수 실측 (2026-11-11, 레인 Phase 1 보고 + PR #148 code-reviewer)

위 표는 registry(§12.3 (b))에서 유도했다. 그 장부는 PR #132·#134 의 종결을 등재하지 않은 판이었다. 레인 넷이 원
정의를 코드로 다시 쟀다. 바꿔치우기 변이로 쟀고, 팀장이 착수 보고에서 판정을 승인했다. 결과는 다음과 같다.

| slice | 구현 | 이미 닫힘(장부 공백 — 근거 PR) | 범위 밖 유지(사유) | 새 OPEN |
| --- | --- | --- | --- | --- |
| 10A | `OPEN-M9-NOTICE-LIST-RESUME-TOTAL` | — | — | — |
| 10B | `OPEN-M9-COMMAND-OPENAPI-ENUM` | `OPEN-M9-TIMELINE-COMMAND-WORDING`(#132). 10B 가 그 방어를 계약 유도로 교체한다 | — | `OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM`(상태 두 칸) |
| 10C | `OPEN-SCOPED-ROOTS-PARITY-REST` | `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` · `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`(#134 `97ed49c4`) | `OPEN-VERDICT-CONSTRUCTION-VISIBILITY`. production 가시성 변경이 필요하고, testFixtures 전역 차단(`OPEN-1BC-TESTFIXTURES-GATE`, ADR 선행)을 우회하게 되며, 모듈 밖 test 17 파일이 깨진다. 2026-11-06 판정 때는 16 이었고, M9/ops-code 가 하나를 더했다. 판정은 팀장 판정 2026-11-06(M9/gates-tests 계약 갱신 ①)과 같다 | `OPEN-M10-SCOPED-PACKAGES-COVERAGE`(이름 층 축 없는 여섯) |
| 10D | `OPEN-M9-KOTLIN-COMMENT-STRIP-DUP` | `OPEN-M9-CLOCK-LINT-SCOPE` · `OPEN-M9-SCREEN-COVERAGE-SYMBOL-GRAPH` · `OPEN-M9-MONEY-VOCAB-CROSS-LANG`(PR #132 머지 `e12c0485`). 10D 가 재측정한다 | 금액 어휘 「하나의 출처」(enum 은 네 벌 중 하나만 지우고 해악은 등식이 이미 닫았다) | `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER`(간접 렌더 — 조용한 초록 재현) |

그래서 실제 구현은 넷이고, 장부 정정은 여섯, 범위 밖 유지는 하나, 새 등재는 셋이다. 10D 의 「하나의 출처」는 OPEN 이 아니라 그 OPEN 안의 처방 하나를 남긴 판정이라 id 를 세우지 않는다.

## 완료 조건

1. slice 넷이 각자 PR 로 머지됐다. 각 PR 은 verifier `ready-for-review` · code-reviewer(sonnet) approve · head CI 5/5 를 거쳤다. 판정은 PR 코멘트에 남는다.
2. 위 표의 OPEN 열하나가 각각 셋 가운데 하나로 처분됐다.
   - **닫힘**: 이 마일스톤의 PR·SHA 가 닫은 것.
   - **이미 닫힘**: 앞 PR 이 닫았는데 장부만 비어 있던 것. 근거 PR·SHA 와 이 마일스톤의 재측정을 함께 적는다.
   - **범위 밖 유지**: 사유와 함께 남긴 것. 판정자는 레인이 아니라 **팀장**이고, 착수 보고에 대한 답으로만 낸다. 그 판정은 위 「착수 실측」 표에 적힌 것이 전부이고, 사후 추가는 인정하지 않는다.
3. 처분 전부(닫힘·이미 닫힘·범위 밖 유지·새 OPEN)가 `docs/discovery/capability-map.md` §12.3 의 slice 하위 절 (g)~(j) 에 날짜와 함께 등재됐다. 각 slice 가 자기 머지에서 한다.
4. 외부 effect 0 이다. 새 KONEPS 호출·실 DB·실 발송이 없다.
5. 이 문서의 종결 절을 세션 모델이 쓴다.

## 승인 경계

이 마일스톤 안의 결정 지점은 추천안으로 진행한다(전역 규칙). 거동을 바꾸는 선택은 멈추고 보고한다. 10A 의
공유 누산기 변경, 10B 의 API 응답 값 변경이 여기에 든다. 외부 effect 가 필요해지는 경우와 Flyway 마이그레이션이
필요해지는 경우도 마찬가지다.

## 종결 (2026-11-13)

**M10 종결.** slice 넷이 각자 PR 로 머지됐다. 외부 effect 는 0 이다(새 KONEPS 호출·실 DB·실 발송 없음).

| slice | PR | 머지 커밋 | 판정 |
| --- | --- | --- | --- |
| 10B `timeline-command-enum` | #149 | `67ff54b1` | verifier ready-for-review · code-reviewer approve · CI 5/5 |
| 10C `gates-adapters` | #150 | `6c4fbe94` | verifier ready-for-review · code-reviewer approve · CI 5/5 |
| 10D `gates-web` | #151 | `ff867b5c` | **재작업 상한 5/5 도달 뒤 운영자 예외 두 번**(2026-11-12: r5 토큰 술어 한 라운드 · r6 마이크로 수정과 「또 high 면 범위 축소로 머지」). r6 의 남은 high 는 계약 문면이 실측보다 넓다는 것이었고, 문면을 실측 범위로 좁혀 닫았다. 막지 못하는 다섯은 `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS` 로 등재했다 · CI 5/5 |
| 10A `notice-resume-total` | #152 | `94ee445b` | verifier ready-for-review(표적 재검증 r2) · code-reviewer approve(r2) · CI 5/5 |

- **완료 조건 2·3** — OPEN 열하나의 처분(닫힘 넷 · 이미 닫힘 여섯 · 범위 밖 유지 하나)은 `docs/discovery/capability-map.md`
  §12.3 (g)~(j) 에 각 slice 가 자기 머지에서 등재했다. 위 「착수 실측」 표 밖의 사후 범위 밖 판정은 없다.
- **신설 OPEN 다섯** — `OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM`(h) · `OPEN-M10-SCOPED-PACKAGES-COVERAGE`(i) ·
  `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS`(j) · `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER`(j) ·
  `OPEN-M10-OPENING-LIST-RESUME-TOTAL`(g — 착수 표에 없던 것. 10A cr r1 이 착수 계수 오류로 찾았다).
  다섯 모두 외부 effect 없이 닫을 수 있고, **M11(`milestone-11.md`)이 받는다.**
- **10D 에서 얻은 교훈** — 「사본 없음」류 게이트는 위협 모델 경계를 착수 때 고정하지 않으면 라운드마다 꼴 열거가
  되풀이된다(r0~r7). M11 의 web 게이트 slice 는 계약에서 경계를 먼저 고정한다.
