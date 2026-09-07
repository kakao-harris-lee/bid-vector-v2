# Slice 계약 — 하네스 / `test-discovery-guard` · JUnit 미발견 test 형태 게이트 + breaking-mutations 알려진 제한 11~13 — **착수 2026-09-07**

> **지위**: 브리프 3(`_workspace/briefs/2026-09-07-m3-parallel-briefs.md`, 하네스 레인) 의 코드 slice. M3/3A 구현 레인과 같은 working tree 에서 병행한다.
> Phase 2.5 는 세션 모델이 직접 했다(`_workspace/harness-test-shape/02_design-review.md`). 완료 조건은 verifier `ready-for-review` + 운영자 승인(코드 slice, 2026-09-04).

```yaml
milestone: harness
slice: test-discovery-guard
base_sha: 7581106ecf7c52bbf8bb032adc233e90a8f1eb9b
# verifier r1 F-4 — 값을 SHA 로 박으면 뒤 커밋마다 낡는다(장부층). 리뷰 시점 HEAD 는
# 「이 slice 의 마지막 in_scope 커밋」이고, 그 커밋은 항상 아래 명령으로 낸다(정본은
# 명령이지 값이 아니다):
#   git log --oneline 7581106..HEAD -- <in_scope 경로 12개>  |  head -1
head_sha: 리뷰 시점 HEAD = 이 절의 마지막 커밋(위 명령으로 확인)
in_scope:
  - build-logic/src/main/kotlin/bidvector/buildlogic/TestShapes.kt          # 순수 함수 — PSI 위 test 메서드 형태 판정
  - build-logic/src/main/kotlin/bidvector/buildlogic/TestShapeGateTask.kt   # Gradle task — 배선·report 만
  - build-logic/src/main/kotlin/bidvector.kotlin-conventions.gradle.kts     # `testShapeGate` 등록 + `check` 의존 (그 밖의 편집 금지)
  - build-logic/src/main/kotlin/bidvector.quality-baseline.gradle.kts       # 루트 `buildLogicTestShapeGate` 등록 (그 밖의 편집 금지)
  - build-logic/src/test/kotlin/bidvector/buildlogic/TestShapesTest.kt      # 문자열 fixture 단위 test
  - config/quality/test-shape-policy.properties                             # 신규 정책 파일 — `gate-tests.properties` 는 3A in_scope 라 편집 금지
  - contracts/tools/breaking-mutations.sh                                   # F-21·F-22·F-23 + source 가능 진입 가드
  - contracts/tools/breaking-mutations-selftest.sh                          # 신규 — 회귀 실측 셋(buf 미호출)
  - contracts/testdata/breaking/**                                          # `expected.tsv` 는 내용 불변이어야 한다(재생성 diff 0)
  - reports/evidence/harness/test-discovery-guard/**
  - reports/evidence/m2/2d/checklist.md                                     # 알려진 제한 11~13 상태 한 줄씩만
  - docs/discovery/capability-map.md                                        # §14.3 `OPEN-2B-TEST-DISCOVERY-GUARD` 행의 상태 문구만(행 문면 유지) — `git diff -U0` 로 자기 hunk 만 스테이징
out_of_scope:
  - config/quality/gate-tests.properties                                    # 3A in_scope — 새 단위 test 의 `gate.tests.build-logic` 등재는 3A 종결 뒤 한 줄(알려진 제한)
  - procurement/** · shared-kernel/** · app/** · decision/** · milestone-3.md · reports/evidence/m3/**   # 3A·curator·문서 레인
  - fixtures/**                                                             # curator 레인
  - 다른 게이트 task·정책 파일의 변경 · detekt/ArchUnit 규칙 추가
  - 기존 test 소스의 수정(위반 0건이라 고칠 것이 없다 — preflight §4)
    # 정정(리뷰 요청 시점, 운영자 확인) — 위 "0건"은 preflight 시점(착수 전) 기준이다.
    # 착수 뒤 3A 커밋 b8d4c4e(procurement/AccountingTest.kt)에서 1건 발생 — 이 slice
    # out_of_scope 이며 3A 소유. checklist.md 「게이트가 잡은 실제 위반(범위 밖)」 참고.
acceptance_commands:
  - "git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)"   # S-0 — **정본**(HEAD 만, 다른 레인의 미커밋 파일 배제). `testShapeGate`·`buildLogicTestShapeGate` 가 `check` 안에
  - "./gradlew --no-build-cache clean check"                                                          # S-1 — 공유 트리(3A 미커밋 파일 포함이라 3A 사유로 붉을 수 있음 — 그 경우 S-0 이 판정)
  - "./gradlew -p build-logic test --tests '*TestShapes*'"                                            # S-2 — 문자열 fixture 단위 test(위반 4종·정당 3종·factory·suspend·private)
  - "./gradlew check --dry-run | grep -c 'testShapeGate'"                                             # S-3 — 9 모듈 + 루트 = 10 (배선 증거)
  - "(cd contracts && ./tools/breaking-mutations.sh)"                                                 # S-4 — 11/11 exit 0, `expected.tsv` 불변
  - "(cd contracts && ./tools/breaking-mutations-selftest.sh)"                                        # S-5 — F-21 → 2 · F-22 → 1 / `--accept-drift` → 0 · F-23 참, exit 0
  - "./gradlew qualityBaseline"                                                                        # S-6
rollback: |
    **정본은 `reports/evidence/harness/test-discovery-guard/rollback.md`**. in_scope 경로를 base 로 되돌린다 —
    `git restore --source=7581106ecf7c52bbf8bb032adc233e90a8f1eb9b --staged --worktree -- <in_scope 경로 개별 인자>`(신규 파일은 삭제됨). 임시 clone 실측 의무.
```

## 하네스 레인 변경 (상시 절)

`git log --oneline 7581106..HEAD -- CLAUDE.md .claude/` — 착수 시 없음. (이 slice 자체가 하네스 레인이다 — 3A 의 scope 「하네스 레인 변경」 절에 이 slice 의 커밋이 등재된다.)

**리뷰 요청 시점(head `6752791`) 재확인 — 없음.** `git log --oneline 7581106..6752791 -- CLAUDE.md .claude/` 0건.

**verifier r1 일괄 수정 커밋(head_sha F-4 반영) 재확인 — 없음.** `git log --oneline 7581106..HEAD -- CLAUDE.md .claude/` 는 매 리뷰 요청 시점마다 이 명령으로 다시 낸다(값을 박지 않는다).

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | `testShapeGate` — 모듈의 main 외 Kotlin 소스에서 test-메서드 어노테이션(정책 목록)이 붙은 함수가 **블록 본문 ∧ (반환 타입 없음 ∨ `Unit`) ∧ 비-`suspend` ∧ 비-`private`** 이 아니면 실패. `@TestFactory` 는 비-`Unit` 이 정당(반대로 `Unit` 이면 위반). 디렉터리 부재 = 통과(설계 A-5) | `OPEN-2B-TEST-DISCOVERY-GUARD` 「`@Test` 메서드 반환 타입 `Unit` 강제」 |
| ② | 순수 함수 `TestShapes.extract` + 문자열 fixture 단위 test, task 는 배선·report 만(`PublicApiTypes` 관례) | 1A 게이트 가족 관례 |
| ③ | 정책 데이터 `test-shape-policy.properties`(version 1): `test.shape.method-annotations`·`test.shape.factory-annotations`·`test.shape.forbid-suspend`·`test.shape.forbid-private` — 리터럴을 task 에 두지 않는다 | `v2-지침서.md` §5 |
| ④ | breaking-mutations.sh: F-21 표 완전성 양성 단언(exit 2) · F-22 `--update` diff 출력 + exit 1, `--accept-drift` 만 exit 0 · F-23 파이프 제거(변수 + 패턴 매칭) · source 가능 진입 가드 + selftest | 2D checklist 알려진 제한 11~13 |
| ⑤ | evidence 셋 + 2D checklist 11~13 상태 한 줄 + `OPEN-2B` 행 상태 | evidence-pack |

## 알려진 제한 (착수 시)

1. `gate.tests.build-logic` 에 `TestShapesTest` 미등재 — `gate-tests.properties` 가 3A in_scope. 3A 종결 뒤 한 줄 병합. 그때까지 S-2 가 실행을 보인다.
2. typealias 로 숨긴 test 어노테이션은 PSI 짧은 이름 매칭이 못 본다(설계 우회 (4)) — 저자 의도 우회로 경계 밖. 완화는 정책 목록 편집.
3. S-1 은 공유 트리의 3A 미커밋 파일 때문에 3A 사유로 붉을 수 있다 — S-0 이 정본.
