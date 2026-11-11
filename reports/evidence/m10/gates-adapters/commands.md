# M10 / gates-adapters — 실측

출력 전문은 싣지 않는다(핵심 결과 한 줄). 라운드 이력 절은 두지 않는다.

## 1. 선행 확인 — 넷 중 둘이 base 에서 이미 닫혀 있다

| 무엇 | 명령 | 결과 |
| --- | --- | --- |
| ② 술어 종류 | `git grep -c '^import (bidvector' -- <그 파일>` · `javap` 호출 유무 | 소스 import 정규식 **0** · `javap -p -v` 상수 풀 술어 + 허용 뿌리 목록 **있음**. 양성·허용·세그먼트 경계 대조 **셋** |
| ③ 단언 형태 | `grep -n 'any {' <그 파일>` ↔ `grep -n 'shouldBe'` | `any { contains }` 가 **단언에 0**(일치 넷은 그 변경을 설명하는 KDoc 산문) · CHECK 단언은 전부 본문·본문 집합 `shouldBe` · 제약 조회의 0 행·중복 이름 **명시 실패** 있음 |
| 둘을 닫은 자리 | `git log --merges --ancestry-path <그 evidence 첫 커밋>..HEAD` | **PR #134 머지 `97ed49c4`** |
| 장부 공백 | `grep -c gates-tests docs/discovery/capability-map.md` | **0** — 그 종결이 어디에도 등재되지 않았다 |
| ④ 오늘 상태 | `git grep 'data class (Eligible\|Ineligible\|Uncertain)' -- qualification/src/main` | subtype 셋이 **`public data class`** |
| ④ 영향 모집단 | 같은 매치에서 **주석 줄(`*`·`//`·`/*`)을 뺀** 뒤 남은 줄이 있는 파일만 센다(`qualification/` 제외) | **17**(adapters 3 · app 10 · workflow 4) = M9/gates-tests 의 **16** + m9/ops-code 의 하나. **파일 단위 `git grep -ln` 은 18 을 내고 그것은 과다 계수다** — KDoc 언급까지 세기 때문이고, 그 차이 하나가 `QualificationAdapterDependencyTest`(match 둘 다 KDoc 줄, 컴파일 영향 0)다. 팀장 정정 · PR #148 cr r1 |

## 2. ① 의 착수 실측 — 두 층의 오늘

| 무엇 | 결과 |
| --- | --- |
| `source.scoped-packages` 축 수 | **8** |
| 축별 두 층 집합 비교(정책 unfold ↔ 각 축 `ALLOWED_ROOTS`) | **여덟 축 모두 일치**, 차 **0**(audit 1 · evaluation 10 · notice 5 · opening 7 · profile 7 · qualification 6 · relay 8 · strategy 7) |
| 등식 단언이 있는 축 | **1**(evaluation) — 나머지 일곱의 집합은 `private val` 이라 다른 파일에서 읽히지 않는다 |
| 중첩 축 쌍 | **0** |
| 어댑터 dependency test 전수 ↔ 이름 층 축 | dependency test **14** · `source.scoped-packages` **8** · 축이 **없는 여섯**: `event`·`extraction`·`koneps`·`ml`·`notification`·`persistence`. 그 여섯을 **술어 종류로 갈라 셌다** — 바이트코드 **둘**(`notification`·`persistence`) · 소스 텍스트 **넷**(`event`·`extraction`·`koneps`·`ml`). 허용 목록 심볼도 하나가 아니다(`ALLOWED_ROOTS` 11 · `OUT_OF_LAYER_DOMAIN_ROOTS` 2 · `ALLOWED_DOMAIN_ROOTS` 1) — **「`ALLOWED_ROOTS` 를 가진 14」는 틀린 묶음이었다**(자기 정정) |
| 최장 접두 선택 | `SourceReferencePolicy.scopeOf` 가 `maxByOrNull { scope.length }` — **최장 접두가 이긴다**(구현 확인) |
| 정책 키를 읽는 자리 **전수** | 셋 — `SourceReferencePolicy`(→ `domainSourceReferenceGate`, 모듈 **열**) · build-logic 의 그 정책 술어 test · evaluation 축 등식 test |

## 3. 음성 대조 — 구현 **전**, 그 구멍은 초록이다

이름 층 `notice` 축에만 바이트코드 층에 없는 뿌리 하나를 더한 뒤(두 층이 갈린 상태) **그 키를 읽는 자리
전수**를 돌렸다.

`./gradlew --no-daemon domainSourceReferenceGate :build-logic:test --tests '*SourceReferencePolicyTest' :adapters:test --tests '*EvaluationAllowedRootsParityTest'`
→ **BUILD SUCCESSFUL**(33 executed). 이어 돌린 `domainSourceReferenceGate` 가 모듈 **열** 전부
`UP-TO-DATE` — 그 변이한 정책이 선언 입력(`@InputFile policyFile`)인 채로 직전 실행이 성공했음을 뜻한다.
**아무것도 붉지 않았다.**

## 4. 변이 — 구현 **뒤**

실행은 전부 `:adapters:test --tests '*ScopedAllowedRootsParityTest' --tests '*AdapterDependencyTest' --rerun`
(통제군 cache 적중을 막기 위해 `--rerun`). 모집단은 **66 test**.

| 변이 | 꼴 | 결과 |
| --- | --- | --- |
| ⓐ→ⓕ 이름 층만 넓힘(§3 과 **같은 변이**) | 더함 | **RED 1/66** — 붉은 것은 **축별 등식 하나**. §3 의 초록과 쌍이다 |
| ⓑ 바이트코드 층만 넓힘(relay) | 더함 | **RED 2/66** — 축별 등식 + **그 축 dependency test 자신의 양성 대조**. 이 방향은 두 층이 함께 잡는다(그래서 조용한 방향은 이름 층 쪽이다) |
| ⓖ 바이트코드 층 뿌리 **교체**(profile) | 바꿔치우기 | **RED 2/66** — 축별 등식 + 그 축 dependency test 의 본 판정 |
| ⓒ 아홉째 축을 모집단에 더함 | 더함 | **RED 1/66** — 모집단 등식(registry 에 항목이 없다) |
| ⓓ 축 하나를 모집단에서 뺌 | 뺌 | **RED 1/66** — 모집단 등식 |
| ⓔ 중첩 축을 모집단에만 더함 | 더함 | **RED 1/66** — 붉은 것은 **모집단 등식**이고 접두 비포함은 아니다(가려진다) |
| ⓔ′ 중첩 축을 **등재까지 하고** 더함 | 더함 + registry 항목 | **RED 1/3** — 두 등식을 **만족시킨 뒤** 접두 비포함만 붉다. 실패 문면: `"bidvector.adapters.notice < bidvector.adapters.notice.jdbc"` |

**ⓔ′ 를 따로 잰 이유**: ⓔ 만으로는 접두 비포함 단언이 **한 번도 발화하지 않는다** — 모집단 등식이 먼저
붉어 그 단언이 죽은 분기로 보일 수 있다. 실제 우회 경로는 「중첩 축을 **제대로 등재하고** 더하는 것」이고,
ⓔ′ 가 그 경로에서 이 단언이 유일한 방어임을 보인다.

## 5. 선언 입력 음성 대조 — UP-TO-DATE 거짓 초록과 그 양성 쌍

`:adapters:test` 에서 정책 파일의 선언 입력 한 줄만 뺀 뒤 세 단계.

| 단계 | 상태 | 결과 |
| --- | --- | --- |
| 1 | 선언 입력 없음 · 정책 원판 | task **실행** · BUILD SUCCESSFUL (UP-TO-DATE 를 만든다) |
| 2 | 선언 입력 없음 · 정책 변이(ⓕ와 동일) | `:adapters:test` **UP-TO-DATE** · **BUILD SUCCESSFUL = 거짓 초록**. 변이가 보이지 않는다 |
| 3 | 선언 입력 **되살림** · 같은 변이 | task 재실행 · **1 failed** · BUILD FAILED |

2 와 3 의 차이는 **그 한 줄뿐**이다. 그래서 이 등식들은 「정책을 고쳐도 test 가 돌지 않는」 자리에
기대지 않는다 — 기대는 선언 입력이 진다.

## 6. 통제군과 acceptance

| 무엇 | 명령 | 결과 |
| --- | --- | --- |
| 통제군(변이 없음) | `:adapters:ktlintCheck :adapters:test --tests '*ScopedAllowedRootsParityTest' --tests '*AdapterDependencyTest' :build-logic:test --tests '*SourceReferencePolicyTest'` | **BUILD SUCCESSFUL** · 새 class 의 결과 XML 에 `tests=3 failures=0 skipped=0 errors=0` (단언 셋이 **실제로 돌았다** — exit 0 만으로 세지 않는다) |
| 낡은 `.class` | `find adapters/build -name '*EvaluationAllowedRootsParityTest*'` | 남은 것은 HTML 리포트 디렉터리 하나뿐 · **`.class` 0**(이동한 class 의 가짜 RED 자리가 없다) |
| 등재 | `grep -c` 두 이름 | 새 이름 **2**(목록 1 + 산문 1) · 옛 이름 **1**(걷었음을 적는 산문 한 줄) · 목록의 옛 항목 **0** |
| 정책 편집의 성질 | `git diff -U0` 에서 주석 아닌 `+`/`-` 줄 계수 | **0** — 주석만. `policy.version` 자리 diff **0**(D-10C-5) |
| acceptance (전건) | `./gradlew --no-daemon check` | 결과는 아래 「acceptance 결과」 |

### acceptance 결과

<!-- 최종 HEAD 에서의 실측. 이 파일을 또 고치면 그 HEAD 가 움직이므로, 정본은 verifier 보고와 PR 조치 코멘트다. -->

- **rollback 복원 트리**(임시 clone, 14 경로 복원 뒤, 실측 HEAD `ccbe98ff`): `./gradlew --no-daemon check`
  → **BUILD SUCCESSFUL**(35m 36s · 433 actionable 중 278 executed · 게이트 task 172 · 실패 0). 되돌린 트리가
  초록이다. **이 측정은 세 번째다** — 산출물을 고치는 커밋이 두 번 더 들어와 실측 자리가 그때마다 옮겨졌고,
  앞의 둘(과 앞 라운드가 쓴 트리 동일성 갈음)은 버렸다. 사유는 `rollback.md`.
- **최종 HEAD**: `./gradlew --no-daemon check` → 결과는 **verifier 보고와 PR 조치 코멘트**가 정본이다.
  이 파일을 고치는 것이 `leakPatternGate` 의 선언 입력(`reports/evidence/**`)을 바꿔 HEAD 를 또 움직이므로,
  마지막 HEAD 의 수치를 이 파일에 적지 않는다. 구현 레인이 잰 값은 인계 보고에 싣는다.
