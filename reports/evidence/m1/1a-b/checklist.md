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
  느슨하다.** (verifier r1 B-1 정정) D-1 preflight 의 「6」은 shared-kernel **모듈 최대**가
  아니라 `BaseAmount` **한 타입**의 값이었다 — 게이트 배선 뒤 실측한 모듈 전체 최대(main,
  gated)는 shared-kernel 8(`UnroundedBidAmount`)·저장소 전체 21(build-logic 자신의
  `SourceSetLayoutGateTask`, task 설정 객체라 프로퍼티가 자연히 많다)이다. 그래도 30 은
  보수적이며, 이 값의 재조정은 이 slice 밖(운영자 재확인 요청 등재만 — scope D-5 「수치를
  자기 승인하지 않는다」).
- **CPD 관찰 한계** — 토큰 50 미만으로 잘린 조각, 의미적 중복(변수명만 다른 코드)은 CPD 가
  재지 못한다(scope.md 위협 모델 우회 6, 등재만).
- **확장 함수는 타입 멤버 축 밖** — 정의상 타입의 멤버가 아니다. 남용은 결합도 축
  (`qualityBaseline` fan-in/out)이 별도로 관찰한다(scope.md 우회 1).
- **TestKit 첫 도입의 대가** — `withPluginClasspath()` 가 build-logic 의 전체 runtime
  classpath(kotlin-compiler-embeddable·archunit·detekt·ktlint·kover·cpd 포함)를 매 실행
  격리 fixture project 에 다시 주입한다 — 이 test 하나가 `:build-logic:test` 실행 시간의
  체감 가능한 몫을 차지한다(다른 test 는 순수 함수라 밀리초 단위). fixture 를 플러그인
  하나만 적용하도록 최소화했지만 classpath 자체를 줄일 방법은 없다.
- **④(b)(c)(d) 전용 음성 fixture 부재 — (b) 는 verifier r1 이 실측으로 닫았다.** (b)
  (`packageOwnershipGate`의 source-set 집합 비교)는 verifier 가 가드 (a)를 로컬로 제거하고
  `java-test-fixtures`를 적용해 `예상 밖 source set 'testFixtures'` 로 실제로 잡히는 것을
  확인했다(N16, commands.md 「verifier r1 재현」절). (c)(`testFixturesCompileClasspath`
  분기)·(d)(ArchUnit import option)는 여전히 전용 fixture 없이 미실증이다 — (a)가 project
  평가를 그 자리에서 끊어 재현하려면 (a)를 손으로 우회해야 하고, 그 우회 fixture 를 새로
  심는 것은 이 slice 범위를 넘는다고 판단해 만들지 않았다. 판정 로직 자체는 기존 패턴
  (정책 파싱·`when` 분기·ArchUnit 옵션 체이닝)을 그대로 쓰므로 그 패턴의 기존 test
  커버리지가 회귀를 잡는다.

## Verifier r1 반영 (M-1~M-3·L-1~L-3, head 이전 `cfcb023` → 이번 커밋)

| id | 조치 | 재현(commands.md) |
| --- | --- | --- |
| M-1 | 모듈 `sizeGate`의 `typeSources`에 `build.gradle.kts` 추가(`scriptSizeGate`와 대칭) + size-policy 주석의 「타입 없는 스크립트」 정정 | 31개 멤버 클래스를 `shared-kernel/build.gradle.kts`에 심음 → exit 1 → 원복 |
| M-2 | `bidvector.quality-baseline.gradle.kts`에 `buildLogicTypeShapeGate` 신설(build-logic 자신의 `build/classes/kotlin/main`을 배선) | **새 finding** — 아래 절 |
| M-3 | `buildLogicGateExecutionGate` 신설(루트, 순환 회피) + `gate-tests.properties`에 `gate.tests.build-logic`(**19개**(신규 6 + 기존 13) test 전부 — 이 모듈은 게이트 정의 자체가 사는 곳이라 test 스위트 전체가 "게이트가 잡는다는 증거", verifier r3 장부층 ④ 정정 — 이전 판은 「18개」) | `TestFixturesGateTest.kt`를 컴파일 대상에서 빼고 재실행 → `buildLogicGateExecutionGate` 실패 → 원복 |
| L-1 | `TypeShapeGateTask`의 실패 메시지에 `docs/adr/0007-test-pyramid-and-ratchet.md §5 OPEN-ADR-06 해소 절` 상향 경로 명시 | — |
| L-2 | size-policy.properties 주석에 「형태 래칫도 main 한정」 한 줄 추가 | — |
| L-3 | `CpdReportPresenceGateTask.cpdXmlReport`를 `@InputFile`→`@Internal`로(순서는 명시 `dependsOn`이 이미 짐) — 「존재하지 않는다」 사유가 실제로 로그에 나온다 | 리포트 삭제 + `-x cpdCheck` → D-4 사유 메시지 확인(이전엔 Gradle 일반 메시지) |

### M-2 가 드러낸 finding → D-7 로 해소(측정 정의 정제, 팀장 결정)

`buildLogicTypeShapeGate`를 배선하고 실측하니 build-logic 자신의 상속 깊이 최대가 **3**이라
래칫 상한(2)을 넘었다 — 위반 17건 전부 depth 3, interfaces 는 1로 안 넘었다. 원인은
`bidvector.buildlogic.*GateTask`류(`abstract class X : DefaultTask()`)와 정밀 스크립트
컴파일 클래스 둘 — **Gradle 의 `DefaultTask` 자체가 이미 2단 상속이라 어떤 Task 구현이든
그것을 확장하는 순간 depth 3 이 됐다**(mixin 팽창이 아니라 프레임워크 강제 깊이). D-5
원칙대로 임계·코드를 손대지 않고 보고했고, 팀장이 **D-7**로 결정했다: 상속 깊이의 정의를
「프로젝트가 소유한 타입 안에서의 상위 체인 길이」로 정제 — 상위 클래스를 따라 올라가다
import 집합(같은 `ClassFileImporter` 스캔) 밖의 클래스를 만나면 멈춘다.

구현(`03349ad`): 공유 함수 `JavaClass.inheritanceDepth(ownedTypeNames)` 하나만 고쳤다
(`qualityBaseline`·`typeShapeGate`·`buildLogicTypeShapeGate` 전부 같은 함수). 9모듈 +
build-logic 전체 재실측 결과:

| 모듈 | max depth(이전 정의) | max depth(D-7) | max interfaces |
| --- | --: | --: | --: |
| shared-kernel | 2 | **0** | 1 |
| adapters·app·decision·procurement·qualification·settlement·strategy·workflow | 1 | **0** | 0 |
| build-logic | 3(위반) | **0** | 1 |

**팀장의 예상("도메인 sealed 1")과 다르게 실측값은 9모듈+build-logic 전부 0이다** —
shared-kernel 을 포함한 모든 모듈이 sealed **인터페이스**(클래스 아님)를 구현하는 형태라
`rawSuperclass` 체인에 소유 클래스가 하나도 안 걸린다(인터페이스는 이 축에 안 잡힌다,
`interfaceCount`가 별도로 잰다). `ratchet.type.inheritance-depth.max` 를 실측대로 **0**
으로 낮췄다. `buildLogicTypeShapeGate`는 이제 통과하고(D-3 대로 위반 0건), 저장소 전체
`clean check` 가 291/291 task 전건 GREEN 이다(isolated worktree 콜드 빌드 포함).

test: `TypeShapeFixtureTest`에 Gradle `DefaultTask` 확장(depth 0, build-logic 시나리오 그대로)
· 소유 2단 체인(depth 1) 두 case 추가, enum test 를 새 정의(0, 이전 2)로 갱신. 기존 3단
소유 체인 fixture 의 상대 비교(middle>root, leaf>middle)는 절대값이 바뀌어도 여전히 성립해
그대로 뒀다.

## verifier r2 처리(2026-09-06, 커밋 `8549144`) — H-1(high)·M-1(medium)

**H-1 — D-7 의 「소유 타입」 집합이 스캔 단위(보통 한 모듈)라 모듈 경계를 넘는 소유 클래스
상속이 미탐이었다.** shared-kernel 의 `open class`를 workflow 가 상속하는 실제 구조가 이
사각에 걸린다. 소유 판정을 `ownedTypeNames`(이번 스캔 집합) **이거나** 루트 패키지
(`architecture-policy.properties` 의 `package.root`, ADR 0006 D-3 — 하드코딩하지 않고
같은 정책 소스에서 읽는다) 아래로 넓혔다. `qualityBaseline`·`typeShapeGate`·
`buildLogicTypeShapeGate` 가 전부 같은 공유 함수를 쓰므로 세 곳이 함께 바뀐다.

재현 — verifier 의 VProbe 셋(shared-kernel 에 `open class VProbeBase`, workflow 에
`VProbeDerived : VProbeBase()`·`VProbeDeeper : VProbeDerived()`)을 그대로 임시 배치하고
`:workflow:typeShapeGate` 실행: 수정 전엔 `VProbeDeeper`(같은 모듈 상속, depth 1)만
잡고 `VProbeDerived`(교차 모듈, 이전 정의로는 depth 0)를 놓쳤다. 수정 후엔 **둘 다** 잡힌다
(`VProbeDerived` depth 1 · `VProbeDeeper` depth 2). 원복 확인.

**M-1 — `TypeShapeGateTask` 는 입력 경로가 어긋나면 조용히 통과했다.** 스캔한 타입이 0개면
`cpdReportPresenceGate`(D-4)와 대칭으로 실패하는 비공허 단언을 추가했다. 재현 —
`buildLogicTypeShapeGate` 의 하드코딩 경로를 `.../kotlin/mainRENAMED` 로 바꾸면 실패
(「스캔한 타입이 0개다」), 원복 후 `types=282` 확인. 자리표시자 모듈(타입 1개)은 이 단언에
걸리지 않는다.

test: `TypeShapeFixtureTest`에 `CrossModuleDerived`(다른 모듈의 소유 타입을 흉내 — 일부러
`importClasses`에 그 상위 `CrossModuleBase`를 넣지 않는다) case 추가 — depth 1로 잡혀야
스캔 집합만으로는 놓치던 것을 루트 패키지 접두가 잡는다는 것을 고정한다.

실측: `clean check` 전건(291 task, isolated worktree 콜드 빌드 포함) BUILD SUCCESSFUL,
`:build-logic:test` 176 test(19 class) 0 failed.

## 리뷰 요청 조건 — verifier 판정 이력

| 라운드 | 판정 | 요지 | head |
| --- | --- | --- | --- |
| r1 | ready-for-review | high 0 · medium 3(M-1·M-2·M-3) · low 3(L-1·L-2·L-3) | `cfcb023` |
| r2 | **not-ready** | high 1(H-1, 소유 판정이 모듈 경계를 넘는 상속을 놓침) · medium 1(M-1, 비공허 단언 부재) | `c045c77` |
| r3 | ready-for-review | high 0 · medium 0 · low 1(ADR §5 소유 판정 서술이 논리합 중 한쪽만 적음) · 장부층 6(B-1~B-6) | `4ceb008` |

수정 라운드 누계 **1/5**(r2 not-ready 에 대한 대응 1회 — H-1·M-1). r3 low 1 과 장부층
①②③⑤(ADR §5·capability-map.md·milestone-1.md 등 정본 문서 수치)는 팀 리드가 처리한다
(포인터만, 이 evidence 의 산출물 대상 아님). 장부층 ④(등재 수)·⑥(secret 스캔 판독)은
이 커밋이 정정했다. **남은 것은 사용자 승인이다.**

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
