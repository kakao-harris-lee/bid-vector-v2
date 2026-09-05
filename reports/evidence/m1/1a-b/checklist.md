# Checklist — M1 / 1A-b

milestone-1.md 「완료 조건」·「Slice 1A-b」 대조. 각 항목은 `commands.md` 의 명령을 가리킨다 —
수치를 여기 다시 적지 않는다.

## M1 공통 완료 조건

- **`./gradlew check` 통과** — H-0·H-1 (isolated worktree + 직접 실행 둘 다 전건 GREEN).
- **금지 import·순환 의존 fixture 가 실제로 실패** — 이 slice 밖(1A/1B 산출물). H-3 이
  `ArchitectureGateCatchesViolationsTest`·`ArchitectureGateTest` 를 재실행해 회귀 없음을
  재확인한다(④(d) 의 `DO_NOT_INCLUDE_TEST_FIXTURES` 추가가 그 판정을 바꾸지 않았다).
- **승인된 authoritative corpus 전체 통과** — H-3 이 `*Conformance*` 를 재실행(⑤ 의 접두
  안전화가 판정을 바꾸지 않았다).
- **신규 파일/함수 예산 위반 없음 → 이 slice 가 축을 넷으로 넓힌다.** 기존 둘(파일 500·
  함수 50)에 ①(타입 멤버 30, PSI)·②(상속 깊이 2·인터페이스 1, 바이트코드 래칫)를 더했다.
  H-0/H-1 이 넷 다 GREEN 임을 함께 확인한다(`SizeGateTask`·`TypeShapeGateTask` 가 같은
  `check` 그래프에 물려 있다).

## Slice 1A-b 고유 완료 조건 — 「위 셋의 위반 fixture 가 실제로 걸림」

| 축 | 음성 증거 | 위치 |
| --- | --- | --- |
| ① 타입 멤버 30 | `TypeMembersTest`(31개 초과·30개 통과·data class 합성 멤버 불계수·중첩/companion 분리·enum entry 제외·접근자 포함, 7 test) | `build-logic/src/test/.../TypeMembersTest.kt` |
| ② 깊이·인터페이스 래칫 | `TypeShapeRatchetPolicyTest`(순수 함수, 5 test) + `TypeShapeFixtureTest`(실컴파일 바이트코드, 5 test — 상속 체인 3단계·인터페이스 2개·enum depth 2·`by` 위임이 인터페이스 수에 잡히고 상속 체인은 안 늘림) | `build-logic/src/test/.../TypeShape*Test.kt` |
| ③ CPD 관찰 모드 | 로컬 실측(commands.md) — 인위 중복 삽입 → 리포트에 잡히고 exit 0(관찰) → `mode=fail` 전환 → 같은 중복으로 실제 실패. 원복 완료(`git diff` 없음) | commands.md 「③ PMD CPD」절 |
| ④(a) testFixtures 명시 사유 실패 | `TestFixturesGateTest` — 이 저장소 첫 TestKit(`GradleRunner`) 사례. 가드 제거 시 RED 확인 후 복원 | `build-logic/src/test/.../TestFixturesGateTest.kt` |
| ④(b)(c)(d) 방어 심층 | 순수 함수·기존 패턴(정책 키 승격·`testGraphs` 분기·ArchUnit import option) — 전용 fixture 없음(팀장 지시대로 (a)의 constructive 차단이 1차이고 이 셋은 (a)가 이미 막는 경로를 손으로 우회했을 때만 발동해, 정상 경로에서 값을 받는 일이 없다) | `bidvector.kotlin-conventions.gradle.kts`·`ArchitectureGateTest.kt` |
| ⑤ fixture 번호 대조 접두 안전화 | 회귀 재현 — `11`→`1` 로 바꾸면 다시 빨개짐(1B-c verifier r2 low ① 원 지적 그대로) | `SharedKernelCorpusConformanceTest.kt` |
| ⑥ compile fixture 13 | `CompileFailureHarnessTest` 신규 test — negative(2인자, "no value passed for parameter")·positive(4인자)·mutant(오타, 같은 진단 불만족) | `shared-kernel/src/test/.../CompileFailureHarnessTest.kt` |

## 위협 모델 우회 재확인 (scope.md 「우회 경로와 막는 자리」)

7개 우회 중 이 slice 가 구성적으로 막는 것: 5(`java-test-fixtures` 없이 같은 이름 source
set 손 등록 — ④(b)(c)(d) 방어 심층). 나머지는 scope.md 가 이미 「등재」·「경계 밖」으로
분류했고 이 slice 가 재분류하지 않는다(1·3·4·6·7).

## 알려진 제한

- **PSI 상한 30 대 실측 최대 21(main, 이 slice 로 넓어진 build-logic 자신 포함) — 여전히
  느슨하다.** shared-kernel 최대는 6(D-1 실측)이었으나 build-logic 자신의 main 타입
  (`SourceSetLayoutGateTask` 류, task 설정 객체라 프로퍼티가 자연히 많다)이 21 로 재서
  간극이 좁혀졌다. 그래도 30 은 보수적이며, 이 값의 재조정은 이 slice 밖(운영자 재확인
  요청 등재만 — scope D-5 「수치를 자기 승인하지 않는다」).
- **CPD 관찰 한계** — 토큰 50 미만으로 잘린 조각, 의미적 중복(변수명만 다른 코드)은 CPD 가
  재지 못한다(scope.md 위협 모델 우회 6, 등재만).
- **확장 함수는 타입 멤버 축 밖** — 정의상 타입의 멤버가 아니다. 남용은 결합도 축
  (`qualityBaseline` fan-in/out)이 별도로 관찰한다(scope.md 우회 1).
- **TestKit 첫 도입의 대가** — `withPluginClasspath()` 가 build-logic 의 전체 runtime
  classpath(kotlin-compiler-embeddable·archunit·detekt·ktlint·kover·cpd 포함)를 매 실행
  격리 fixture project 에 다시 주입한다 — 이 test 하나가 `:build-logic:test` 실행 시간의
  체감 가능한 몫을 차지한다(다른 test 는 순수 함수라 밀리초 단위). fixture 를 플러그인
  하나만 적용하도록 최소화했지만 classpath 자체를 줄일 방법은 없다.
- **④(b)(c)(d) 전용 음성 fixture 부재** — (a)가 project 평가를 그 즉시 끊으므로,
  `testFixturesCompileClasspath`·source-set 집합 불일치·ArchUnit import option 이 실제로
  「걸리는」 상황은 (a)를 손으로 우회해야만 재현된다. 그 우회를 만드는 fixture 를 새로
  심는 것은 이 slice 범위를 넘는다고 판단해 만들지 않았다 — 세 게이트 각각의 판정 로직은
  기존 패턴(정책 파싱·`when` 분기·ArchUnit 옵션 체이닝)을 그대로 쓰므로 그 패턴의 기존
  test 커버리지가 이미 회귀를 잡는다.

## 문서 갱신 잔여 (이 slice 범위 밖 — 세션 모델 소관)

scope.md in_scope 에 `docs/adr/0007-test-pyramid-and-ratchet.md`(§5 해소 절 포인터)·
`milestone-1.md`(「Slice 1A-b」 항목 갱신)이 올라 있으나, 이 둘은 계획/명세 문서라
CLAUDE.md 운영자 지시(2026-09-04, 2026-09-05 세션 모델 단독 저작)에 따라 kotlin-implementer
가 아니라 세션 모델이 쓴다. 구현은 완료됐고 배선 완료 포인터·D-6 등재만 남았다 — 팀 리드에게
인계.

## 스코프 경로 표기 정정 (등재만)

scope.md in_scope 는 ⑤ 편집 파일을 `app/src/test/kotlin/bidvector/app/conformance/
CorpusExecutors.kt` 로 적었으나, 실제 `assertFixtureNumberMatchesContractBinding` 함수는
같은 디렉터리의 `SharedKernelCorpusConformanceTest.kt` 에 있다(둘 다 in_scope 디렉터리
안 — 범위 위반 아님). 팀 리드의 작업 지시문도 올바른 파일을 직접 지목했다.
