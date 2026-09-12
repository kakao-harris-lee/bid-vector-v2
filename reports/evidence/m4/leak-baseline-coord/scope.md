```yaml
milestone: M4
slice: leak-baseline-coord
base_sha: a6ab6a8dc4c5304ce91f5ae5b591eb0e4c0a0b95
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - build-logic/src/main/kotlin/bidvector/buildlogic/LeakPatternGateChecks.kt
  - build-logic/src/test/kotlin/bidvector/buildlogic/LeakPatternGateChecksTest.kt
  - config/quality/leak-pattern-baseline.txt
  - reports/evidence/m4/leak-baseline-coord/
out_of_scope:
  - config/quality/leak-patterns.txt          # 패턴 어휘 불변
  - build-logic/.../LeakPatternGateTask.kt    # 스캔 대상·제외 목록 불변 (키 산출 위임만 허용)
  - reports/evidence/ 아래 다른 slice 문서     # 매치 대상 문서를 고쳐서 통과시키지 않는다
acceptance_commands:
  - ./gradlew :build-logic:test --tests '*LeakPatternGateChecksTest*'
  - ./gradlew leakPatternGate --no-daemon
  - ./gradlew --no-daemon check
rollback: config/quality/leak-pattern-baseline.txt 와 LeakPatternGateChecks.kt 를 base 로
  되돌리면 게이트가 좌표 키로 복귀한다(둘은 같은 커밋에서 함께 움직여야 한다 — 한쪽만
  되돌리면 형식 불일치로 전 항목이 new 가 된다). 상세는 rollback.md.
```

## 계약 — 무엇을 바꾸고 무엇을 바꾸지 않는가

**바꾸는 것 하나**: baseline 이 승인된 매치를 식별하는 **키**. `경로:줄번호` → `경로` + `정규화한
줄 내용의 해시`.

**바꾸지 않는 것**: 어떤 줄이 매치인가(패턴·스캔 대상·제외 목록), 무엇이 게이트를 실패시키는가
(baseline 밖의 새 매치만), stale 항목의 처분(보고만 하고 실패시키지 않음).

### D-LBC-1 — 키는 `경로` + `정규화 줄 내용 해시`

경로를 키에서 빼지 않는다. 내용만으로 키를 잡으면 한 파일에서 승인한 문자열이 **모든 파일에서**
승인되어, 새 파일에 붙여 넣은 실유출이 통과한다. 정규화는 **trim + 내부 공백 접기**까지이고
소문자화·구두점 제거는 하지 않는다(서로 다른 줄을 같은 키로 접는다).

**남는 한계(알려진 제한)**: 경로도 좌표다 — 파일 rename 시 그 파일의 항목이 전부 stale 이 되고
매치가 new 가 된다. 줄 번호와 급이 다르다는 것이 채택 사유다(줄 번호는 위쪽 아무 편집에나 밀리고,
경로는 rename 에만 밀린다).

### D-LBC-2 — 옛 형식은 조용히 죽지 않고 게이트를 실패시킨다

baseline 에 `경로:숫자` 형태가 남아 있으면 **명시적 실패**로 사유를 말한다. 관용적으로 두 형식을
다 받으면 결함을 안은 채 복잡도만 는다. 다른 레인이 옛 형식 항목을 추가한 채 병합되는 경로가
실재하므로(현재 `m4/2026-09-08`·`m4-4b6b/2026-09-12` 동시 진행), 혼합 파일이 stale 로 조용히
통과하지 않게 한다.

### D-LBC-3 — 좌표는 보고서에만 산다

위반 메시지와 보고서는 **저장 가능한 키와 현재 좌표를 함께** 낸다. 좌표는 사람이 보러 갈 때
필요하고, 저장되면 낡는다. 보고서는 매 실행 재생성되므로 낡을 수 없다. 보고서에 키 수(`keys`)를
줄 수(`matches`)와 **따로** 싣는다 — 접힘이 보이지 않으면 한 항목이 몇 줄을 덮는지 알 수 없다.

### D-LBC-4 — 마이그레이션은 기계 산출, 지금이 적기

290 항목을 손으로 변환하지 않는다. **착수 시점 실측: baseline 290 = 실제 매치 290, stale 0 ·
new 0** — 정확히 동기라 변환이 무손실이다. 변환 결과는 **268 항목**(9 항목이 2줄 이상을 덮고,
덮는 줄 총 31)이고, 변환 뒤 게이트는 **new 0 · stale 0** 이어야 한다. 하나라도 나오면 변환이
틀린 것이다. baseline 머리말의 형식 설명도 새 형식으로 갱신한다.

## 수용 기준

| # | 기준 | 어떻게 잰다 |
| --- | --- | --- |
| S-1 | **매치 집합 불변** — 매치한 줄 290 이 변경 전후 동일 | base 와 head 두 트리에서 매치 줄 목록을 내어 대조 |
| S-2 | 변환 뒤 `leakPatternGate` 가 new 0 · stale 0 | 보고서 실측 |
| S-3 | 위쪽 줄 삽입이 승인을 무효화하지 않는다 | baseline 에 있는 매치 줄 **위에** 줄을 넣고 게이트 재실행 → 여전히 통과 (PR #7 재현 시나리오) |
| S-4 | 다른 파일에 같은 글자를 넣으면 막힌다 | 승인된 줄을 다른 evidence 파일에 복사 → new 로 잡힘 |
| S-5 | 옛 형식 항목이 남으면 실패한다 | baseline 에 `경로:숫자` 한 줄을 넣고 실행 → 사유를 담은 실패 |
| S-6 | 그 줄의 글자가 바뀌면 다시 본다 | 승인된 줄의 어휘를 유지한 채 다른 글자로 수정 → new 로 잡힘 |
| S-7 | 기존 test 14 가 새 규격으로 갱신되고 전부 통과 | `:build-logic:test` |
| S-8 | 전건 통과 | `./gradlew --no-daemon check` (2026-09-12 규칙 ① — job 단위로만 줄인다) |

## 하네스 레인 변경

리뷰 요청 시점에 `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 로 등재한다.
현재: 없음.
