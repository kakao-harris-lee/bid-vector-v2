# 마일스톤 10 — 외부 effect 없이 닫을 수 있는 M9 잔여

> **지위: 계약(2026-11-11, 세션 모델 단독 저작).** 범위는 사용자 지시 2026-11-11 「1번 진행해, 끝나면 머지까지」가
> 승인했다. 「1번」은 M9 종결 뒤 남은 OPEN 가운데 **승인 없이 바로 할 수 있는 것**(코드·문서, 외부 호출 0)
> 묶음이다. 이 문서는 그 묶음을 slice·완료 조건으로 옮긴다. M9 와 달리 **착수 전에** 완료 조건을 고정한다.

## 범위 밖 — 이 마일스톤이 만들지 않는 것

- **사용자 결정이 필요한 것.** G3 범위 결정, G4 legacy 의도 판정, `OPEN-ADR07-*` 비용 결정, `OPEN-M9-DDAY-CALENDAR`(거동 변경), 6G 리뷰 잔여 11 의 처분, 운영 잔여 분류 변경, `OPEN-ADR-15` 가 여기에 든다.
- **실데이터·외부 호출이 선행인 것.** G2 값 재유도, G5 재측정, G1 외부 정의, 조달청 02 응답 필드 확인 호출, 분리 오퍼레이션 수집, 낙찰 실 수집, Telegram 활성화, 운영 반입이 여기에 든다.
- **새 KONEPS·외부 API 호출, 실 DB, 실 발송.** 0 이다.

## slice

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

## 완료 조건

1. slice 넷이 각자 PR 로 머지됐다. 각 PR 은 verifier `ready-for-review` · code-reviewer(sonnet) approve · head CI 5/5 를 거쳤다. 판정은 PR 코멘트에 남는다.
2. 위 표의 OPEN 열하나가 각각 **닫힘**(근거 PR·SHA)이거나, 착수 보고에서 범위 밖으로 판정돼 **사유와 함께 유지**됐다. 유지가 있으면 그 사유는 착수 보고에 있어야 하고, 사후 판정은 인정하지 않는다.
3. 닫힌 OPEN 의 처분이 `docs/discovery/capability-map.md` §12.3 에 등재됐다. 각 slice 가 자기 머지에서 한다.
4. 외부 effect 0 이다. 새 KONEPS 호출·실 DB·실 발송이 없다.
5. 이 문서의 종결 절을 세션 모델이 쓴다.

## 승인 경계

이 마일스톤 안의 결정 지점은 추천안으로 진행한다(전역 규칙). 거동을 바꾸는 선택은 멈추고 보고한다. 10A 의
공유 누산기 변경, 10B 의 API 응답 값 변경이 여기에 든다. 외부 effect 가 필요해지는 경우와 Flyway 마이그레이션이
필요해지는 경우도 마찬가지다.
