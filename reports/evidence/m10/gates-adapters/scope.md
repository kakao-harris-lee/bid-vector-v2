# M10 / gates-adapters — adapters·domain test 술어 넷

- base: main `8062dcd784cf81905c50a2c8706d8bbd3ea5263e` · 계약 정본 `milestone-10.md`(10C 행)
- 공통 규율: M10 레인 공통 규율(팀장 2026-11-11) · `CLAUDE.md` · `v2-slice-pipeline` · `evidence-pack`
- 결정 지점은 추천안으로 진행(전역 규칙). 거동을 바꾸는 선택·외부 effect·Flyway 는 멈추고 보고한다.

## 대상 넷의 원 정의 확인 결과

계약이 지목한 넷을 원 정의 자리에서 읽고 **base main 의 오늘 상태와 대조했다**. 넷 가운데 **둘은 이미 닫혀
있다** — 계약이 저작된 시점에 그 사실이 반영되지 않았다.

| OPEN | 원 정의 자리 | base main 의 상태 | 이 slice 의 처분 |
| --- | --- | --- | --- |
| ① `OPEN-SCOPED-ROOTS-PARITY-REST` | `reports/evidence/m9/gates-build-followups/scope.md` 「OPEN」절 · `EvaluationAllowedRootsParityTest` KDoc · `architecture-policy.properties` 패키지 범위 축 주석 | **열려 있다** — 두 층 등식 단언이 있는 축은 `evaluation` 하나. 남은 일곱(`audit`·`notice`·`opening`·`profile`·`qualification`·`relay`·`strategy`)에 없다(실측) | **닫는다**(아래 처방) |
| ② `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` | `milestone-6.md` 6F-5 「신설해 인계하는 OPEN 셋 더」 · `PersistenceAdapterDependencyTest` KDoc · `gate-tests.properties` 주석 | **이미 닫혔다** — 술어가 상수 풀 허용 목록이고 소스 import 정규식은 0(실측은 `commands.md` §1) | **등재만** — capability map §12.3 |
| ③ `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` | `CleanMigrationCheckTest` KDoc 넷 · `milestone-6.md` 6F-5 같은 절 · `reports/evidence/m6/6f5a/scope.md` D-6F5-22 | **이미 닫혔다** — CHECK 단언이 전부 본문·집합 `shouldBe` 이고 존재 단언은 0(실측은 `commands.md` §1) | **등재만** — capability map §12.3 |
| ④ `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` | `milestone-6.md` 6F-5 같은 절 · `reports/evidence/m6/6f5a/scope.md` D-6F5-20 · 그 slice `checklist.md` | **열려 있다** — `LicenseVerdict` 의 subtype 셋이 `public data class` | **범위 밖 — 사유와 함께 유지**(아래) |

②·③ 을 닫은 것은 **M9/gates-tests, PR #134 머지 `97ed49c4`**(evidence `reports/evidence/m9/gates-tests/`).
그 slice 의 `checklist.md` 「OPEN 처분」이 둘을 「닫힘 후보」로 적었고, **capability map 에는 그 종결이
등재되지 않았다** — §12.3 (d) 는 그보다 앞선 M9/gates-build 의 시점에서 둘을 「이 slice 가 손대지 않은 여섯」에
세고 있다. 그래서 이 slice 가 하는 일은 **처분의 등재**이고 구현은 ① 하나다.

### ④ 를 범위 밖으로 판정하는 사유

처방(D-6F5-20)은 「`LicenseVerdict` subtype 생성자를 `internal` + `@ConsistentCopyVisibility`, test 조립은
testFixtures factory 로」다. 셋 다 이 slice 의 계약 밖이다.

1. **production domain 커널의 가시성 변경**이다 — 10C 의 축은 「adapters·domain **test 술어**」이고
   `qualification/src/main/**` 은 거동에 닿는 편집이다.
2. **처방의 둘째 절반이 저장소 전역 차단에 막혀 있다.** `java-test-fixtures` 는 전 모듈에서 거부되고
   (`bidvector.kotlin-conventions.gradle.kts` · `module.expected-source-sets=main,test` ·
   `TestFixturesGateTest`) 그 자리가 `OPEN-1BC-TESTFIXTURES-GATE`(「면제·순수성 규칙을 ADR 로 먼저 정한
   뒤에 연다」, ADR 0007)를 가리킨다. ADR 없이 여는 것은 **그 OPEN 의 우회**다.
3. **영향 범위가 모듈 셋에 걸친다.** 오늘 `qualification` 모듈 밖에서 subtype 생성자를 부르는 test 파일은
   **17** — `adapters` **셋** · `app` **열** · `workflow` **넷**. M9/gates-tests 가 센 **16** 에 m9/ops-code 가
   더한 하나(`EvaluationCommitInboxProductionTest`, 실제 코드 호출)다.
   **모집단 술어는 파일 단위 grep 이 아니다**(팀장 정정, PR #148 cr r1): `git grep -ln` 은 **KDoc 언급까지
   세므로** 과다 계수한다 — `QualificationAdapterDependencyTest` 의 match 둘은 모두 KDoc 줄이라 생성자에
   대해 **컴파일되지 않는다**(그 slice 도 같은 이유로 세지 않았다). 쓰는 술어는 **주석 줄을 뺀 계수**다:
   `LicenseVerdict.(Eligible|Ineligible|Uncertain)(` 매치 줄에서 `*`·`//`·`/*` 로 시작하는 줄을 빼고
   남은 줄이 하나 이상인 파일. 이 축을 받는 slice 가 이 목록을 입력으로 쓴다.

**팀장은 2026-11-06 에 같은 판정을 이미 내렸다**(M9/gates-tests 계약 갱신 ①: 「이 slice 에서 뺀다」, 받는 자리는
**testFixtures 개방 ADR slice**). 이 slice 는 그 판정을 뒤집을 입력을 갖고 있지 않다. 처분: **열린 채 유지**,
받는 자리 그대로.

## in_scope

- `adapters/src/test/kotlin/bidvector/adapters/architecture/ScopedAllowedRootsParityTest.kt`(신설 — 아래
  D-10C-4 의 일반판. `EvaluationAllowedRootsParityTest.kt` 를 `git mv` 로 옮겨 일반화한다)
- `adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAllowedRootsParityTest.kt`(삭제 —
  옛 경로도 pathspec 에 든다)
- 남은 일곱 축의 `*AdapterDependencyTest.kt` 각 **한 단어**(`private val ALLOWED_ROOTS` → `internal val`):
  `audit` · `notice` · `opening` · `profile` · `qualification` · `relay` · `strategy`
- `adapters/build.gradle.kts` · `adapters/src/test/.../evaluation/EvaluationAdapterDependencyTest.kt`
  (**주석·KDoc 한 줄 치환 — in_scope 추가, 아래 계약 갱신 (5)**): 둘이 옛 class 이름을 부르고 있어 `git mv`
  로 **거짓이 됐다**. 같은 줄 치환이라 더한 줄 0 이고 499 줄 파일은 **499 줄 그대로**다
- `config/quality/gate-tests.properties`(등재 교체 — 정렬된 자리)
- `config/quality/architecture-policy.properties`(**주석만** — 「남은 일곱 축은 아직 그 등식이 없다」가 거짓이 된다)
- `docs/discovery/capability-map.md` §12.3 에 **이 slice 의 하위 절 하나**를 덧붙인다(기존 행 수정 0, §12 머리 집계 무변경)
- `reports/evidence/m10/gates-adapters/**`

## out_scope

- **production 소스 0 줄.** `qualification/**`·`build-logic/**`·`openapi/**`·`web/**`·migration 무접촉.
- `EvaluationAdapterDependencyTest.kt` — **499/500 줄**이라 한 줄도 더하지 않는다(그 축의 `ALLOWED_ROOTS` 는
  이미 `internal` 이어서 더할 것이 없다). 그 파일 분리는 M9/gates-tests 가 남긴 선행 작업이고 다른 결정이다.
- **이름 층 축이 없는 여섯 패키지** — `event`·`extraction`·`koneps`·`ml`·`notification`·`persistence` 는
  dependency test 를 갖지만 `source.scoped-packages` 에 없다. **그 여섯의 결여는 한 가지가 아니다**(실측):
  `notification`·`persistence` 는 여덟과 같은 **바이트코드** 술어라 이름 층 짝이 없고,
  `event`·`extraction`·`koneps`·`ml` 은 **소스 텍스트** import 정규식이라 바이트코드 층이 없다. 어느 쪽이든
  더하는 것은 **게이트의 판정 모집단 확장 또는 술어 종류 변경**이고 이 OPEN 의 문면 밖이다. 처분은
  `OPEN-M10-SCOPED-PACKAGES-COVERAGE`.
- ④ 의 처방 전부(위 사유 셋) · testFixtures 개방 ADR.
- `milestone-6.md`·`milestone-10.md`·다른 slice 의 evidence — 팀장 레인이 받는다.

## acceptance

CI `check` job 의 명령 그대로 — `./gradlew --no-daemon check`. 표적 실행(`--tests`)은 변이 측정에만 쓰고
판정 근거로 쓰지 않는다(새 test 클래스의 등재 게이트는 전건에서만 붉는다).

## 설계 검토 (Phase 2.5 — ① 하나가 대상이다)

### (0) 위협 모델 경계 문장

**`source.scoped-packages` 가 판정하는 축 전부에서, 이름 층 정책의 허용 뿌리 집합과 그 축 dependency test 의
바이트코드 층 `ALLOWED_ROOTS` 집합이 같다. 그리고 어느 축도 다른 축의 패키지 접두가 아니다** — 그래서
`SourceReferencePolicy.scopeOf` 의 최장 접두 선택이 패키지마다 단일 후보를 가지며, 축별 등식이 이름 층의
판정 모집단 **전체**를 덮는다.

**재지 않는 것**: 두 목록이 **옳은가**(둘이 함께 넓어지면 조용하다 — ADR·리뷰 자리, M9/gates-build-followups
알려진 제한 2 가 등재) · 두 층 **술어의 동치**(각 층의 술어 test 가 든다) · 이름 층 축이 **없는** 여섯 패키지
(모집단 밖) · 값이 흐르는 경로 일반.

### (1) 열거인가 구성인가

앞 판은 **열거**였다 — 축 하나를 손으로 적었고 아홉째 축이 생기면 조용했다. 새 판은 **구성**이다.

- 축 → 바이트코드 층 목록 **registry 하나**를 두고, `registry.keys` ↔ `source.scoped-packages` 를 **집합 등식**으로 잰다.
- registry 의 값은 **컴파일되는 심볼 참조**다(문자열이 아니다) — 어느 축의 `ALLOWED_ROOTS` 를 지우거나 이름을
  바꾸면 컴파일이 깨진다. CLAUDE.md 「게이트 술어는 문자열이 아니라 구조로 닫는다」.
- 비공허 둘을 함께 단언한다 — `scoped-packages` 가 비지 않음 · 축마다 양쪽 목록이 비지 않음(빈 집합끼리의
  등식은 아무 범위도 잠그지 않는다).

### (2) 우회 — 막히는가

| 우회 | 막히는가 |
| --- | --- |
| 이름 층 정책만 넓힌다(아무 게이트도 붉지 않던 조용한 방향) | 막힘 — 축별 집합 등식 |
| 바이트코드 층 `ALLOWED_ROOTS` 만 넓힌다 | 막힘 — 같은 등식 + 그 축 dependency test 의 음성 대조 |
| 축을 `scoped-packages` 에서 뺀다(이름 층이 그 패키지를 아예 판정하지 않게) | 막힘 — registry keys 등식 |
| 아홉째 축을 `scoped-packages` 에 더한다 | 막힘 — registry 에 항목이 없어 keys 등식이 깨진다 |
| **중첩 축**을 더해 최장 접두로 좁은 축의 등식을 가린다(계약이 지목한 「최장 접두 사각」) | 막힘 — 축 접두 비포함 단언 |
| 정책 파일을 `:adapters:test` 의 **선언 입력**에서 뺀다 | **안 막힘 — 등재**(cr MEDIUM 정정). 그 한 줄이 **있으면** 정책 변이가 보이고 **없으면** `UP-TO-DATE` 거짓 초록이 된다는 것을 음성 대조로 실측했는데, **그 줄을 지키는 게이트는 없다** — build-logic 의 게이트 task 가운데 「정책 파일이 그 test task 의 선언 입력인가」를 재는 축이 없고 `declarePolicyInputs` 는 build-logic 자기 test task 전용이다. 비용: `adapters/build.gradle.kts` 의 그 한 줄을 지우는 편집이라 diff·리뷰가 보는 자리. 실측은 `commands.md` 「선언 입력 음성 대조」 |
| test class 를 등재에서 뺀다 · 파일을 지운다 | 막힘 — `gateRegistrationGate` 양방향 등식 + registry 심볼의 컴파일 실패 |
| 축의 `ALLOWED_ROOTS` 를 `internal` 에서 `private` 로 되돌린다 | 막힘 — 컴파일 실패 |
| 양쪽 목록을 **함께** 넓힌다 | **안 막힘 — 구조적으로 열려 있다.** 게이트/정책을 바꾸는 편집이라 리뷰가 받는다 |
| dependency test 가 쓰는 집합을 그 파일 안에서 **다른 심볼로 바꾸고** `ALLOWED_ROOTS` 는 장식으로 남긴다 | **안 막힘** — registry 는 옛 심볼을 보고 그 test 는 새 집합으로 판정한다. 등재한다(비용은 「게이트 파일을 고치는」 편집이고 한 파일 안에 쓰이지 않는 집합이 남아 리뷰·ktlint 가 보는 자리다) |
| 이름 층 축이 **없는** 여섯 패키지로 코드를 옮겨 거기서 층 밖에 닿는다 | 이 축의 모집단 밖 — out_scope 의 그 항목 |
| **registry 별칭 바꿔치기** — 한 축의 자리에 다른 축의 `ALLOWED_ROOTS` 를 별칭으로 끼우고(`import …notice.ALLOWED_ROOTS as AUDIT_ROOTS`) 그 축 정책을 그 집합으로 넓힌다 | **막힘 — 이 라운드가 닫았다**(verifier F-2 M7). key ↔ **값 심볼의 선언 패키지** 등식. 바꿔치운 축의 facade 참조가 상수 풀에서 사라져 뽑힌 집합이 작아진다 |
| **유령 축 개명** — 정책의 축 이름(`scoped-packages`·roots 키)과 registry key 를 **함께** `…audits` 로 바꿔 이름 층이 실재하는 패키지를 더는 판정하지 않게 한다 | **막힘 — 이 라운드가 닫았다**(verifier F-2 M8). 같은 등식 — key 가 가리키는 패키지가 상수 풀에서 뽑힌 집합에 없다 |
| 위 「별칭 바꿔치기」를 한 뒤 **표에 쓰지 않는 참조**를 같은 파일에 더해 패키지 집합을 메꾼다 | **안 막힘 — 등재.** 술어가 「이 class 가 참조하는 패키지」라 표 밖의 참조도 센다. 다만 **import 를 놀려둘 수는 없다** — `unusedImportGate` 의 등식(「도입 이름 ∖ 본문 식별자 == ∅」, ktlint 쪽은 `OPEN-KTLINT-UNUSED-IMPORTS-SILENT` 로 조용하다)이 그 자리를 잡으므로 그 참조는 **실제로 쓰여야** 하고, 결과는 게이트 파일에 뜻 없는 사용 한 줄이 남는 것이다(위 「다른 심볼로 바꾸고 장식으로 남긴다」 행과 같은 비용) |
| **유령 축 + 그 패키지의 test 파일 신설** — 실재하지 않는 production 패키지 이름으로 test 파일을 만들어 그 안에 `ALLOWED_ROOTS` 를 두고 정책 둘·registry·등재를 함께 더한다 | **안 막힘 — 등재.** 닫는 처방은 「key 마다 adapters **main** 출력에 그 패키지 class ≥ 1」(verifier 후속 후보)이고 **이 라운드에 하지 않는다** — 위 둘이 한 줄 편집인 것과 달리 이것은 새 파일 + 등재 + 정책 둘 + registry 넷 자리의 편집이라 리뷰가 평면으로 보는 꼴이다. 받는 자리는 `OPEN-M10-SCOPED-PACKAGES-COVERAGE` 와 같은 정책 키 slice |

**안 막히는 것은 다섯이다** — 「양쪽을 함께 넓힌다」·「판정 집합을 다른 심볼로 옮긴다」·「선언 입력에서
뺀다」·「쓰지 않는 참조로 메꾼다」·「유령 축 + test 파일 신설」. 다섯 모두 이 표에 등재돼 있고, 「모집단 밖」
한 행은 열려 있는 것이 아니라 **다른 OPEN 이 받는 축**이다.

### (2b) 값 획득 축 — 새 표면 전수

| 새/바뀐 표면 | 무엇이 얻어지는가 | 경계 |
| --- | --- | --- |
| `ALLOWED_ROOTS` ×7: `private` → `internal` | adapters **test 컴파일 안에서만** 그 집합을 읽을 수 있다 | `internal` 은 모듈 밖에서 보이지 않는다. production 은 그 집합을 볼 수 없다(test source set) · 읽는 자리는 registry **하나** |
| 신설 test class 하나 | 없음 — 판정만 한다 | `@Test` 뿐, production 심볼 0 |
| `object` 커널 주입 자리 | **없다** — production 소스 0 줄, 새 배선 클래스 0 | architecture-policy tier·`EXPECTED_EXEMPT_COUNT` 무변경(새 배선 없음) |
| 「경계로 처리」 행의 실측 | 이름 층 축 없는 여섯을 **열거로 확인했다**(위 out_scope) | 산문이 아니라 기계 산출 |
| 신설 단언이 읽는 것: **자기 class 파일**(`javap -p -v`) | 없음 — 읽기만 한다. 새 public 표면 0, production 심볼 0 | `javap` 는 OS `PATH` 가 아니라 실행 중 JVM 의 `java.home` 에서 해석한다(형제 관례). 읽는 class 출력은 `:adapters:test` 자신의 **선언 입력**이라 registry 를 고치면 task 가 다시 돈다 — 이 축에는 UP-TO-DATE 거짓 초록이 없다 |

### (3) 과잉·미달

- **과잉 후보 — 하지 않는다**: 비포함 단언을 `SourceReferencePolicy.load` 에 넣어 **로드 시 중첩 축을 거부**하는
  것. 우회 비용은 test 단언과 같고(둘 다 게이트 자체를 고쳐야 한다) 장래의 정당한 중첩 축을 막는 쪽이 넓다.
  build-logic 편집도 된다. 등재하고 test 단언으로 둔다.
- **미달 — 유지**: 「두 목록이 **옳은가**」는 이 등식의 대상이 아니다(ADR·리뷰 자리).
- **미달 — 보고**: 이름 층 축 없는 여섯. 특히 `persistence` 는 **PR #134 가 그 javap 술어를 준 축**인데 이름 층에
  들어가지 않았다 — 여덟이라는 수가 그 머지로 낡았다.

## 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-10C-1** | ②·③ 은 **PR #134(머지 `97ed49c4`)가 이미 닫았다**. 구현하지 않고 capability map §12.3 에 등재한다 | base main 실측(`commands.md` §1) + 「이 절은 다시 판정하지 않는다」(§12.3 머리) — 적는 것은 어디서 닫혔는가다 |
| **D-10C-2** | ④ 는 **범위 밖 — 열린 채 유지**. 받는 자리는 testFixtures 개방 ADR slice | 위 「④ 를 범위 밖으로 판정하는 사유」 셋 + 팀장 2026-11-06 판정 |
| **D-10C-3** | ① 의 처방은 **registry + keys 등식 + 축별 집합 등식 + 접두 비포함** | 비용은 OPEN 문면 그대로 축마다 한 단어. 대안인 단일 출처화는 두 층의 독립성을 버리는 거래라 ADR 자리(OPEN 문면이 같이 적는다) |
| **D-10C-4** | 앞 판 `EvaluationAllowedRootsParityTest` 를 **일반판으로 대체**한다(`git mv` + 일반화). 축 하나짜리 class 를 남기고 여덟짜리를 더하지 않는다 | 중복 금지(CLAUDE.md §5). 일반판이 그 class 의 주장을 **전부 포함**한다(`evaluation` 축 등식 · 모집단 소속 · 비공허 둘). 패키지는 `bidvector.adapters.architecture` — 여덟 축에 걸친 판정이라 `evaluation` 패키지가 거짓이 되고, 그 자리는 `packageOwnershipGate` 의 **모집단 밖**이다(cr LOW 정정 — 그 게이트의 `classDirectories` 배선이 「test 출력은 뺀다 — fixture 는 일부러 `bidvector.archfixture` 에 산다」로 test source set 을 제외한다. 결론은 같다: 새 test 패키지가 막히지 않는다) |
| **D-10C-6** | verifier F-2 의 우회 둘(별칭 바꿔치기·유령 축 개명)을 **구조로 닫는다** — key 를 산문이 아니라 **자기 class 파일 상수 풀에서 뽑은 「값 심볼의 선언 패키지」**와 집합 등식으로 묶는다 | 둘의 공통 뿌리가 「key(문자열)와 값 심볼의 실제 패키지가 묶이지 않음」이고, 컴파일은 심볼의 **존재**만 잠근다. 대안 후보(「key 마다 adapters main 출력에 그 패키지 class ≥ 1」)는 유령 축만 닫고 **별칭 바꿔치기를 못 닫아**, 둘을 한 단언으로 닫는 쪽을 골랐다. `internal` top-level `val` 은 다른 파일에서 facade static getter 호출로 읽히므로 그 호출의 소유 class 가 선언 패키지를 짊어진다 — 별칭은 그 자리를 바꾸지 못한다. 남는 꼴 둘은 위 우회표에 등재 |
| **D-10C-5** | `architecture-policy.properties` 편집은 **주석만**이고 `policy.version` 을 **올리지 않는다** | 그 판 이력은 「더한 키 · 바꾼 값」을 적는 장부다(v9~v14 전부 그 둘 가운데 하나를 가졌다). 이 편집은 키 0·값 0 이다. **누락이 아니라 판단임을 여기에 적는다** — v11 이 등재한 결함은 「값을 바꾸고도 조용히 빠진 bump」이고 이 편집은 그 모양이 아니다 |

## rollback

in_scope 경로 한정 `git restore --source=<원천> --staged --worktree -- <경로들>`. 원천은 **이 브랜치가 병합한
main 커밋 `67ff54b1`**(고정 — 움직이는 `origin/main` 이 아니다). 착수 base `8062dcd7` 에서 원천을 바꿨으므로
**셋째 단언**(복원 대상 blob 이 두 원천에서 같은가)을 함께 잰다. 목록·전제·셋째 단언·①~⑥ 실측은
`rollback.md` — 마지막 산출물 커밋에서 임시 clone 으로 돌리고 `실측 HEAD:` 를 적는다.

## 하네스 레인 변경

**없다.** `CLAUDE.md`·`.claude/**` 커밋 **0**. 이 slice 가 만지는 정책 파일 둘(`gate-tests.properties`·
`architecture-policy.properties`)은 **게이트 입력**이고 하네스 문서가 아니다.

## 계약 갱신 2026-11-11 (팀장 — 착수 보고 답)

착수 보고의 판단 넷이 모두 승인됐다. 아래는 그 답이 고정한 것이고 **사후 판정이 아니다** — 완료 조건 2 가
요구하는 「착수 보고에 있는 사유」의 자리는 위 「④ 를 범위 밖으로 판정하는 사유」와 이 절이다.

1. **②·③ 은 「바꾼다」가 아니라 「등재한다」**다 — 「PR #134 / `97ed49c4` 로 이미 닫힘(장부 공백)」으로
   capability map **§12.3 (i)** 에 종결 등재만 한다. 완료 조건 2 의 근거 PR·SHA 는 그 좌표다.
2. **④ 는 범위 밖 유지 확정** — 사유는 위 「④ 를 범위 밖으로 판정하는 사유」가 정본이고 그 넷을 **지금**
   고정한다. 받는 자리는 그대로 **testFixtures 개방 ADR slice**다.
3. **이름 층 축 없는 여섯은 이 slice 에서 더하지 않는다.** 새 OPEN **`OPEN-M10-SCOPED-PACKAGES-COVERAGE`**
   를 발급해 §12.3 (i) 에 등재한다 — 여섯 축, `persistence` 는 #134 가 술어를 준 축이라 「여덟」이 낡았다는
   사실, 받는 자리는 **정책 키 신설 계약 slice**.
4. **`git mv` 일반화 승인** — 옛 경로도 pathspec 에 들고 등재 교체는 같은 커밋에서. `policy.version`
   무변경 판단(D-10C-5)도 승인. 설계 검토의 우회표와 「안 막히는 둘」 등재는 그대로 둔다.

### (5) in_scope 추가 — 옛 class 이름을 부르는 live 좌표 둘(구현 레인 판단, 보고 항목)

`git mv` 가 끝난 뒤 `git grep` 으로 **옛 class 이름의 남은 참조 전수**를 셌다. evidence(이력)와 이 slice 가
새로 쓴 산문을 빼면 **둘**이 남았고, 둘 다 **그 이름을 부르는 live 좌표**라 rename 이 그 문면을 거짓으로
만들었다 — 하나는 `adapters/build.gradle.kts` 의 선언 입력 주석(「그 class 가 이 정책을 읽는다」), 하나는
`EvaluationAdapterDependencyTest.kt` 의 KDoc 링크 `[옛 이름]`(이동 뒤 해석되지 않는다).

**둘을 고쳤다.** 사유: 삭제된 class 를 가리키는 좌표를 남기는 것이 하네스의 「낡는 좌표 금지」에 정면으로
걸리고, 그 거짓은 **내 rename 이 만든 것**이다. 비용: 둘 다 **같은 줄 치환**이라 더한 줄 **0**이고,
`EvaluationAdapterDependencyTest.kt` 는 `sizeGate` 한도에 한 줄 남은 **499 줄 그대로**다(실측). KDoc 링크는
새 class 가 **다른 패키지**라 전체 한정 이름을 써야 길이 한도에 닿으므로 백틱 산문으로 바꿨다 — 링크 하나를
잃는 대신 그 줄이 참이 된다. `build.gradle.kts` 의 거동 변경 **0**(주석만, `inputs.file` 한 줄은 그대로).

이 둘은 착수 보고의 in_scope 에 없었다 — **보고 항목으로 올린다**. 범위를 넓히지 말라는 판단이면 되돌리고
「알려진 제한」으로 등재한다.

## 계약 갱신 2026-11-12 (팀장 — 병합 신호와 F-2 처분)

1. **main 을 병합한다**(병합 커밋 하나, 충돌은 capability map §12.3 하나). 병합 뒤 rollback 원천을 그 병합
   커밋 SHA 로 바꾸고 셋째 단언을 더해 다시 잰다.
2. **verifier F-2(M7·M8)를 「안 막힘」 등재로 받지 않고 구조로 닫는다** — CLAUDE.md 「게이트 술어는 문자열이
   아니라 구조로 닫는다」. 처분은 D-10C-6. **in_scope 는 넓어지지 않았다**: 신설 단언이 이미 in_scope 인
   `ScopedAllowedRootsParityTest.kt` 안에 들고, 이 라운드가 만든 **새 파일은 0** 이다(Phase 3 대조).
3. **cr MEDIUM 둘·LOW 넷과 F-3~F-5 는 장부층 일괄**이다 — 우회표 「안 막힘」 계수, 복원 목록 수, 크기 비,
   `packageOwnershipGate` 근거 문면, F-5 체크 근거. cr LOW 「`scoped-packages` 비공허를 build-logic 에서
   구조로」는 `build-logic/**` 무접촉 계약 밖이라 **등재만** 한다(`checklist.md` 알려진 제한).
4. **게이트 술어를 바꾼 라운드**이므로 종결 뒤 verifier 표적 재검증이 붙는다(`v2-slice-pipeline` Phase 4 예외).

## 산출물 커밋

| SHA | 무엇 |
| --- | --- |
| `c038c124` | 이 계약(scope.md) |
| `7b02b3cc` | 구현 — 일반판 등식 test(`git mv` + 일반화) · 일곱 축 `internal` · 등재 교체 · 정책 주석 |
| `6787ba70` | capability map §12.3 (i) — 넷의 처분 + 신설 OPEN 하나 |
| `ddba9277` | 옛 class 이름을 부르는 live 좌표 둘 — 같은 줄 치환(계약 갱신 (5)) |
| `a7314559` | **main 병합**(원천 `67ff54b1`) — 충돌 하나, 합집합 계수는 그 커밋 메시지가 싣는다 |
| `854a2e12` | **마지막 산출물 커밋** — key ↔ 값 심볼의 선언 패키지 등식(D-10C-6, verifier F-2) |
