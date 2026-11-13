# 마일스톤 11 — M10 신설 OPEN 다섯과 리뷰 비차단 후속

> **지위: 계약(2026-11-13, 세션 모델 단독 저작).** 범위는 사용자 지시 2026-11-13 「M10 후속 처리해」가 승인했다.
> 「M10 후속」은 (1) M10 이 신설한 OPEN 다섯(`milestone-10.md` 종결 절)과 (2) M10 리뷰가 낸 **비차단 후속**
> (registry 에 ID 가 없고 PR 코멘트·팀장 장부에만 있는 것)이다. 둘 다 외부 effect 없이 닫을 수 있다.
> M10 과 같이 **착수 전에** 완료 조건을 고정한다.

## 범위 밖 — 이 마일스톤이 만들지 않는 것

- **M10 이 범위 밖으로 유지한 것.** `OPEN-VERDICT-CONSTRUCTION-VISIBILITY`(받는 자리는 testFixtures 개방 ADR slice,
  `OPEN-1BC-TESTFIXTURES-GATE` 선행) · 금액 어휘 「하나의 출처」(openapi `enum`).
- **M10 계약의 범위 밖 전부**(사용자 결정 · 실데이터·외부 호출 선행 · 하네스 축 · `OPEN-M9-REHEARSAL-FLYWAY-CONFIG-PARITY`).
  사유는 `milestone-10.md` 「범위 밖」 절 그대로다.
- **새 KONEPS·외부 API 호출, 실 DB, 실 발송.** 0 이다.

## 받는 항목

| # | 항목 | 출처 |
| --- | --- | --- |
| 1 | `OPEN-M10-OPENING-LIST-RESUME-TOTAL` | capability map §12.3 (g) · PR #152 cr r1 HIGH |
| 2 | `OPEN-M10-TIMELINE-STATE-OPENAPI-ENUM` | §12.3 (h) · 10B 착수 보고 |
| 3 | `OPEN-M10-SCOPED-PACKAGES-COVERAGE` | §12.3 (i) |
| 4 | 10C 의 등재 우회 ⓐⓑⓒ(값 식 메꾸기 · 같은 패키지 다른 보유자 · 접두가 같은 별도 파일) | §12.3 (i) 첫 행 · PR #150 verifier r2 N-1~N-3 |
| 5 | `OPEN-M10-KOTLIN-READ-GATE-RESIDUALS`(⑤⑥⑧⑨⑩) | §12.3 (j) · PR #151 |
| 6 | `OPEN-M10-SCREEN-COVERAGE-INDIRECT-RENDER` | §12.3 (j) |
| 7 | `satisfies` 표를 `as Record<…>` 선언으로 바꾸면 전수 등식이 선언 타입만 본다 | PR #149 verifier r2 M-2(비차단·기존 구멍) |
| 8 | `parseOpenApiSpec` KDoc 의 「세 계약 test」 하드코딩(실측 소비자 19) | PR #149 code-reviewer r1 N-1 |
| 9 | web `npm run test` 불안정 두 서명 — A: `display-gate-selectors.test.mjs` 첫 칸 5초 timeout(부하 민감) · B: 전부 통과인데 exit 1(`AppRoot.test.tsx` 의 tanstack 타이머가 jsdom teardown 뒤 발화, 52회 중 6회) | M10 팀장 장부(10B·10D 레인 · v-10d-r2 실측) |

## slice

다섯 slice 는 서로 다른 경로를 만지고 병렬로 진행한다. main 이 strict up-to-date 이므로 머지는 하나씩 하고,
뒤 PR 은 앞 머지를 병합한 뒤 다시 잰다(rollback 은 그 마지막 병합 커밋에서 잰다).

| slice | 항목 | 무엇이 닫는가 |
| --- | --- | --- |
| **11A** `opening-resume-total` | 1 | 개찰결과 목록 갈래(`OpeningSampleFramer`)의 슬롯 걷기가 10A 가 세운 공유 자리(`widerTotalOf`)로 배치별 총수의 최댓값을 쥐고 회계에 싣는다. **공유 누산기(`SourceAccountingTally`)는 바꾸지 않는다** — 10A D-1 과 같다. 재개 첫 쪽이 「없음」(`0`·`null`)인 경우를 RED 로 먼저 세우고, 공유 자리를 바꿔치우는 변이가 **세 갈래 test 를 함께** 붉히는지 잰다. 착수 실측에서 그 누산기 + `MaxPages` 재개를 쓰는 자리를 **다시 전수**한다(10A 가 둘로 센 오류의 재발 방지) — 넷째가 나오면 착수 보고에서 멈춘다 |
| **11B** `timeline-state-enum` | 2 · 8 | `openapi/bidvector-operator-api.yaml` 의 타임라인 상태 두 칸(출발·도착)을 Kotlin 상태 어휘와 같은 집합의 `enum` 으로 올리고, web 은 생성 타입에서 유니온을 끌어온다. 10B 의 명령 칸과 같은 꼴이다. **10B 의 전방 호환 판단 세 조건**(§12.3 (h) — 저장소 안 소비자 하나 · 같은 PR 재생성 · Kotlin 어휘 ↔ enum 등식 test)이 이 칸에서도 서는지 착수에서 확인하고, 하나라도 깨지면 멈춘다. wire 값은 바뀌지 않아야 한다(스키마 강화뿐). 8 은 같은 test 지원 파일의 KDoc 한 줄 — 수를 하드코딩하지 않는 문면으로 고친다 |
| **11C** `gates-adapters-2` | 3 · 4 | adapters dependency test 술어 축이다. ① 이름 층 짝이 없는 `notification`·`persistence` 에 정책 키를 신설해 두 층 등식에 넣는다 ② 소스 import 정규식만 있는 `event`·`extraction`·`koneps`·`ml` 을 `javap` 상수 풀 술어로 옮긴다(`OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 의 이식 선례) ③ 표의 값이 소유 getter 호출 결과 **그 자체**인지 리플렉션 `===` 로 단언한다(ⓐ) ④ ⓑⓒ 는 설계 검토가 처분한다 — 술어를 심볼에 묶어 닫거나, 위협 모델 경계 밖으로 등재한다. **새 축이 production 코드에서 붉어지면**(오늘 위반이 드러나면) 고치지 말고 착수 보고에서 멈춘다 — 그것은 이 slice 의 범위가 아니라 새 결정이다 |
| **11D** `gates-web-read` | 5 · 7 | web 의 Kotlin 원문 읽기 게이트 잔여와 타입 단언 우회. **위협 모델 경계를 먼저 고정한다**(아래 절). 그 경계 안에서 ⑤(여는 행위를 런타임에 재는 층 — vitest setup 에서 `fs` 읽기·glob 을 감싸 소유 모듈 밖 호출자가 `.kt` 를 열면 실패)를 **구조 층**으로 세워 ⑥⑧⑨ 를 함께 닫는 것이 1안이다. ⑩ 은 측정된 처방(데이터 파일 본문에서 `#` 시작 줄을 빼고 allowlist 값을 차감한 뒤 단어 토큰 적용)을 쓴다. 7 은 eslint `@typescript-eslint/consistent-type-assertions` `{assertionStyle:'as', objectLiteralTypeAssertions:'never'}` 를 `src/**` 에 건다(오늘 위반 0 실측). flat config 덮어쓰기 함정에 주의한다 — 블록을 더하면 앞 블록의 같은 규칙 옵션이 사라진다 |
| **11E** `web-screen-flakes` | 6 · 9 | ① 화면 카드 모집단이 내보낸 겉에서 파일 안 호출 그래프를 **한 겹** 따라가 비공개 속의 어휘 호출을 의무로 낸다. 잔여 다섯(`memo`·`forwardRef` · 개명 재수출 · 클래스 컴포넌트 · 함수 참조 전달 · 새 어휘 함수) 각각을 닫거나 경계로 등재한다. 의무 집합이 마운트 가능성과 어긋나는 자리는 설계 검토가 판정한다 ② 서명 A·B 를 **따로** 재현하고(로그는 `tee` + `PIPESTATUS`), 원인별로 고친다. B 는 tanstack 타이머의 teardown 처리(쿼리 클라이언트 정리·fake timer)로, A 는 timeout 을 늘리는 것이 아니라 콜드 기동 비용을 test 밖(`beforeAll`)으로 옮기는 것을 1안으로 둔다. 각 서명의 재현율을 수정 전후로 같은 횟수(≥ 50회)로 잰다 |

**게이트 slice(11C·11D·11E ①)는 구현 전에 설계 검토(v2-slice-pipeline Phase 2.5)를 거친다.** 술어는 문자열이
아니라 구조로 닫는다. 변이는 바꿔치우기로 하고, 통제군은 `--rerun` 으로 돌린다. 변이가 적용됐는지 먼저 단언한다.

## 11D 위협 모델 경계 (착수 전 고정 — M10/10D 의 교훈)

**방어하는 것:** web test·스크립트가 **평범한 관용구로** Kotlin 원문을 읽게 되는 표류 — 새 test 가 경로 문자열·glob·
데이터 파일·심볼릭 링크·대소문자 차이로 `.kt` 를 여는 것. 「저자가 게이트를 의식하지 않고 쓴 코드」가 기준이다.

**방어하지 않는 것:** 게이트를 알고 **의도적으로 숨기는** 저자 — 경로를 런타임에 조립해 난독화하거나, 게이트 파일·
vitest setup·eslint 설정 자체를 고치는 것. 이것은 등재로 답하고 수정 라운드를 열지 않는다.

이 경계 밖 finding 은 수정이 아니라 이 절의 참조로 답한다. 경계를 바꾸려면 팀장 결정으로 이 문서를 개정한다.
10D 가 r0~r7 동안 꼴을 하나씩 열거한 것은 이 경계가 착수 때 없었기 때문이다.

## 완료 조건

1. slice 다섯이 각자 PR 로 머지됐다. 각 PR 은 verifier `ready-for-review` · code-reviewer(sonnet) approve · head CI 5/5 를 거쳤다. 판정은 PR 코멘트에 남는다.
2. 받는 항목 아홉이 각각 셋 가운데 하나로 처분됐다 — **닫힘** · **이미 닫힘**(근거 PR·SHA + 재측정) · **범위 밖 유지**(사유 · 판정자는 팀장이고 착수 보고에 대한 답으로만 낸다).
3. OPEN ID 가 있는 항목(1·2·3·5·6)과 새 OPEN 은 `docs/discovery/capability-map.md` §12.3 의 새 하위 절 (k)~(o)(11A~11E 순)에 날짜와 함께 등재됐다. ID 없는 항목(4·7·8·9)은 그 slice 의 evidence checklist 에 처분을 적는다.
4. 외부 effect 0 이다.
5. 이 문서의 종결 절을 세션 모델이 쓴다.

## 승인 경계

이 마일스톤 안의 결정 지점은 추천안으로 진행한다(전역 규칙). 다음은 멈추고 보고한다.
- **거동 변경.** 11A 의 공유 누산기 변경, 11B 의 API 응답 값 변경이 여기에 든다. 11A 가 `collection_run.source_total` 값을 바꾸는 것은 10A 와 같은 **장부 품질** 변경이라 범위 안이다.
- **새 게이트가 오늘의 production 코드를 붉힐 때.** 11C·11D·11E 에서 그 위반을 고치는 것은 새 결정이다.
- 외부 effect 가 필요해지는 경우와 Flyway 마이그레이션이 필요해지는 경우.
- 재작업 상한 5(not-ready + request_changes 합산)에 닿을 때 — 진단을 붙여 보고한다.
