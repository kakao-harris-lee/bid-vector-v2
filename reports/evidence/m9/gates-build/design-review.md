# M9 / gates-build — 설계 검토 (구현 전)

여덟 축을 한 slice 에 담는다. 축마다 ⓐ 경계 한 문장 · ⓑ 새 public 표면 · ⓒ 우회 ≥5 와 그것을 막는 층을
적는다. 「막는 층」이 **문자열 비교**면 그 축은 다시 설계한다(공통 규율 「게이트 술어는 구조로」).

## 공통 — 위협 모델 경계

이 slice 가 막는 것은 **게이트를 끄지 않고 게이트 밑을 지나가는 변경**이다. 게이트를 명시적으로
지우거나 `check` 에서 빼는 경로는 이미 `conventionCoverageGate`·`gateExecutionGate`·
`gateRegistrationGate` 셋이 든다 — 이 slice 는 그 셋이 초록인 상태에서 통과하는 틈만 다룬다.
레인 권한 밖: CI 워크플로 편집 · adapters test 술어 · mutation 도구 채택.

## ① 마이그레이션 번호 순서 (`OPEN-MIGRATION-ORDER-GATE`)

- ⓐ **경계**: `db/migration` 파일 집합의 version 다중집합이 「1..max 에서 은퇴 번호를 뺀 집합」과
  **같아야** 한다. Flyway `outOfOrder=false` 아래에서 결번은 「나중에 그 번호로 들어온 마이그레이션이
  조용히 적용되지 않는다」와 같은 말이다.
- ⓑ **새 public 표면**: `MigrationOrderGateTask`(task 하나) · 정책 키 셋(`migration.module` ·
  `migration.resource-path` · `migration.retired-versions`). 판정은 `internal` 순수 함수.
- ⓒ **우회**
  1. 번호를 겹쳐 붙인다(`V24__a.sql` 둘) → 다중집합 등식이 중복을 센다(집합 크기 ≠ 파일 수).
  2. 번호를 건너뛴다(`V26` 다음에 `V30`) → `1..max ∖ retired` 등식.
  3. 결번을 은퇴 목록에 끼워 조용히 넓힌다 → 목록은 정책 데이터라 리뷰 diff 에 보이고, **대상이 없는
     은퇴 항목**(max 밖·실재하는 파일과 겹침)도 위반이다(OPS-13 원리 4 「죽은 allowlist 검출」).
  4. 파일 이름을 규약 밖으로 짓는다(`migrate-25.sql`) → 판독 불가 자체가 위반(열거가 아니라 전수).
  5. 디렉터리를 옮긴다 → 경로가 정책 값이고, 그 모듈의 디렉터리가 **비면 실패**한다(비-공허 단언).
  6. 게이트를 다른 모듈로 옮긴다 → `migration.module` 한 모듈만 단언 대상이고, 그 모듈에서 비면 실패.
  7. 입력 선언을 비껴가 UP-TO-DATE 로 건너뛴다 → 디렉터리를 선언 입력으로 걸고 경로 민감도 RELATIVE
     (이름 변경이 입력 변경이다).
- **main 의 기존 상태**: version 10·11 이 결번이다(6F-4 가 쓰지 않은 번호 — `6f5a/scope.md` D-6F5-8
  「V10 은 파일이 없어 기다리지 않는다」). 파일 이름을 다시 매기는 것은 adapters 의 production 거동
  변경이므로 **하지 않는다** — 그 둘을 `migration.retired-versions` 로 **고정**한다. 효과는 「그 번호로
  새 파일이 들어오면 실패」(은퇴 번호 재사용 금지)이고, 그것이 Flyway 사각을 닫는 방향이다.

## ② CPD 이름 치환 우회 (`OPEN-CPD-GATE-RENAME-BYPASS`)

- ⓐ **경계**: 중복 판정이 **식별자·리터럴 글자에 의존하지 않는다**(PMD Type-2). 판정식 구조를 바꾼
  복제(Type-3)는 닫지 않는다 — 알려진 한계로 선언한다.
- ⓑ **새 public 표면**: 없다. 정책 키 둘(`normalize.identifiers`·`normalize.literals`)과
  `CpdReportPresenceGateTask` 의 입력 하나.
- ⓒ **우회**
  1. 변수명만 바꾼 복사판 → `ignoreIdentifiers` 가 식별자를 토큰 한 종류로 정규화한다.
  2. 리터럴만 바꾼 복사판 → `ignoreLiterals`.
  3. 정책을 `mode=observe` 로 되돌린다 → 되돌림 경로로 **의도된** 자리(기존 rollback 근거). 거동이
     바뀐 사실이 정책 diff 에 남는다.
  4. `fail.source-sets` 를 비운다 → `DuplicatePolicy.failSourceSets` 가 이미 빈 값을 거부한다.
  5. `check -x cpdCheck` 로 빼고 **낡은 리포트**가 가려 준다 → 프레즌스 게이트가 리포트 mtime 을
     「가장 새 입력 소스의 mtime」과 견준다. 소스를 고친 뒤 cpd 를 빼면 리포트가 그보다 낡아 붉는다.
  6. 정책 키 값에 아무 글자나 쓴다(`normalize.identifiers=yes`) → 참/거짓 외 값은 정책 오류로 거부
     (닫히는 쪽으로 튼다).
- **알려진 한계**: 리포트를 `touch` 하면 mtime 축은 통과한다(내용 무변). didWork 를 직접 보는 축은
  configuration-cache 아래에서 task 참조를 실행 시점에 들고 있어야 해 쓰지 않았다.

## ③ 배포물 아카이브 사각 (`OPEN-JAR-CONTENT-GATE-BOOTJAR-BLINDSPOT`)

- ⓐ **경계**: 「게이트를 통과한 바이트인가」를 **그 모듈이 만드는 Jar 타입 task 전수**에 건다. `jar`
  하나를 보던 하드코딩을 provider 로 바꾼다.
- ⓑ **새 public 표면**: 없다. 정책 키 둘(`jar.class-roots`·`jar.external-class-roots`).
- ⓒ **우회**
  1. 배포물을 `bootJar` 로 내고 `jar` 만 재게 둔다 → `tasks.withType<Jar>()` 전수(provider).
  2. `BOOT-INF/classes/` 접두 때문에 소유 판정이 전부 붉는 것을 「게이트가 시끄럽다」로 끈다 →
     접두를 정책 값으로 벗기고 그 **밑의** 경로로 판정한다(판정을 느슨하게 하지 않는다).
  3. 새 Jar task 를 만들어(`shadowJar` 같은) 거기로 배포한다 → 타입 전수라 새 task 도 대상이다.
  4. `jar.enabled = false` 로 아카이브를 통째로 없애 **빈 입력**으로 초록 → 아카이브 0 개 또는 class
     엔트리 0 개는 실패(비-공허 단언). 지금 `app/build.gradle.kts` 가 `jar` 를 켜 두는 유일한 이유가
     이 공허였고, 이 축이 서면 그 주석의 전제가 바뀐다(주석은 그 파일 소관이라 건드리지 않는다).
  5. launcher 클래스(`org/springframework/boot/loader/**`)를 핑계로 비-소유 엔트리를 전면 허용한다 →
     허용은 **접두 집합 정책 데이터**이고 그 밖의 비-소유 엔트리는 그대로 위반.
  6. launcher 접두 밑에 자기 클래스를 심는다 → 그 접두는 「검증 집합 대조 면제」이지 「아무 이름 허용」이
     아니다. 면제 엔트리 수를 리포트에 세어 수가 늘면 보인다(래칫은 아니고 관측).
- **알려진 한계**: 면제 접두 밑의 바이트는 대조 집합이 없어 내용 판정이 없다(그 바이트의 출처는 Boot
  Gradle 플러그인이고 이 저장소의 소스가 아니다).

## ④ 등재 장부의 블록 안 순서 (`OPEN-HARNESS-GATE-TESTS-ORDER`)

- ⓐ **경계**: `gate.tests.<module>`·`…excluded` 의 **등재 행 순서가 정렬판과 같다**. 순서는 사람이
  세 라운드 연속으로 잡은 축이다(6F-10 D-6F10-37).
- ⓑ **새 public 표면**: 없다(기존 task 의 위반 목록에 한 종류 추가).
- ⓒ **우회**
  1. 새 이름을 블록 끝에 붙인다 → 정렬 등식.
  2. 중복을 끼운다 → 정렬판 비교가 중복 인접을 통과시키므로 **집합 크기 == 행 수**를 함께 본다.
  3. 한 줄에 쉼표로 여러 개를 몰아 쓴다 → 판정이 줄이 아니라 **쉼표로 가른 값 열**이다(줄바꿈은
     `\` 이어달리기라 값에서 사라진다).
  4. 제외 블록으로 옮겨 정렬을 피한다 → 제외 블록도 같은 단언 대상.
  5. 정책 파일을 안 고친 척 UP-TO-DATE → 정책 파일은 이미 선언 입력이다.
- **main 의 기존 상태**: `app`·`decision`·`procurement` 셋이 어긋나 있다(그 밖 열은 정렬 상태) —
  같은 커밋에서 정렬한다.

## ⑤ 쓰이지 않는 import (`OPEN-KTLINT-UNUSED-IMPORTS-SILENT`)

- ⓐ **경계**: **import 가 들여온 이름이 그 파일 본문에서 이름으로 쓰인다.** 등식은
  `도입 이름 ∖ (본문 참조 이름 ∪ 문법이 쓰는 이름) == ∅`.
- ⓑ **새 public 표면**: `UnusedImportGateTask` · 정책 키 둘(`source.import.syntax-names` ·
  `source.import.gate-source-sets`).
- ⓒ **우회**
  1. 쓰지 않는 import 를 남긴다 → 등식(이 축의 본문).
  2. 별칭으로 들여와 안 쓴다(`import a.b.C as D`) → 도입 이름은 **별칭**이다.
  3. KDoc 에만 쓴다(`[Foo]`) → KDoc 이름 참조도 본문 참조로 센다(오탐 방향 회피).
  4. 문자열 템플릿 안에서만 쓴다 → 템플릿 안 식도 PSI 노드다.
  5. 연산자 규약으로만 쓴다(`import …assign` 뒤 `=`) → 그 이름들은 **정책 데이터**로 면제하고,
     면제는 이름 집합이라 리뷰 diff 에 보인다.
  6. `.kts` 로 옮긴다 → 대상은 `kt`·`kts` 둘 다이고 모듈 밖 스크립트·build-logic 트리도 같은 task 가
     잰다(`sizeGate`/`buildLogicSizeGate`/`scriptSizeGate` 세 쌍과 같은 배선).
  7. wildcard 로 바꾼다(`import a.b.*`) → 이 축의 대상이 아니다(쓰임을 셀 수 없다). 그 경로는
     `domainSourceReferenceGate` 의 wildcard 규칙이 이미 좁힌다 — **알려진 한계**로 선언한다.
- **알려진 한계**: 같은 짧은 이름을 그 파일이 스스로 선언하면 import 는 「쓰인 것」으로 보인다(미탐).

## ⑥ 컴파일 시간 상수 사각 (`OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`)

- ⓐ **경계**: adapters 의 네 축(`evaluation`·`qualification`·`profile`·`audit`) 소스가 **이름으로**
  부르는 `bidvector.*` 좌표가 그 축의 허용 뿌리 안이다. 바이트코드 층은 결합을, 이 층은 이름을 본다 —
  `const val` 은 인라인돼 결합을 남기지 않지만 **이름은 소스에 남는다**.
- ⓑ **새 public 표면**: 없다(`DomainSourceReferenceGateTask` 의 축 추가). 정책 키
  `source.scoped-packages` + 축마다 `source.scoped-allowed-roots.<패키지>`.
- ⓒ **우회**
  1. `const val` 로 금지 좌표를 끌어온다 → import·완전수식 이름이 소스에 남아 걸린다.
  2. top-level const·다른 모듈 companion const → 같은 이름 축(D-6A1-34 가 넓힌 문면 전부).
  3. 완전수식으로 import 없이 부른다 → `SourceReferences` 가 수식 참조도 본다(S-2 단언).
  4. 별칭 import → `importedFqName` 이 원 FQN 을 낸다.
  5. wildcard import 로 뭉갠다 → T-C 패키지의 wildcard 는 그 자체가 위반(기존 규칙 재사용).
  6. 패키지를 네 축 밖으로 옮겨 축을 비껴간다 → 그러면 바이트코드 층 네 test 의 대상도 아니게 되지만
     `moduleDependencyGate`·`packageOwnershipGate` 가 모듈 경계를 그대로 든다.
- **알려진 한계**: 허용 뿌리 목록이 **두 자리**에 산다(정책 데이터 + 네 test 의 `ALLOWED_ROOTS`).
  어긋나면 두 층의 범위가 갈린다. 정본화(test 가 정책을 읽게)는 adapters test 술어라 out_scope.

## ⑦ legacy 좌표의 상시 소스 축 (M6 종결 checklist §3 ④)

- ⓐ **경계**: 완료 조건 2(「기존 `bid-vector` import·symlink·DB schema 에 runtime dependency 없음」)를
  **이미지 컨텍스트 밖에서도** 잰다 — 모든 모듈 main 소스가 금지 좌표를 이름으로도, 문자열
  리터럴로도 담지 않는다.
- ⓑ **새 public 표면**: 없다(같은 task 의 축 추가). 정책 키 둘(`source.forbidden.coordinates` ·
  `source.forbidden.literals`).
- ⓒ **우회**
  1. legacy 패키지를 import 한다 → 좌표 거부 집합.
  2. 경로 문자열로 symlink 를 읽는다(`"bid-vector/..."`) → **PSI 문자열 리터럴**을 걸러 본다(파일
     텍스트 grep 이 아니라 리터럴 노드다 — 주석·KDoc 은 대상이 아니다).
  3. 리터럴을 쪼개 이어 붙인다(`"bid-" + "vector/"`) → **닫지 못한다.** 알려진 한계.
  4. 템플릿으로 가린다(`"${'$'}base/bid-vector"`) → 템플릿의 리터럴 조각도 노드라 걸린다.
  5. test 로 옮긴다 → 이 축은 main 한정이다(production runtime dependency 가 조건 문면이다).
     test 의 legacy 참조는 조건 2 가 금지하는 것이 아니다 — 범위를 넓히지 않는다.
  6. 정책 목록을 지워 조용히 끈다 → 키 부재는 정책 오류(`requireList`).

## ⑧ 실행 환경 스니핑 0 (OPS-13)

- ⓐ **경계**: production(main) 소스가 **자기 실행 환경을 물어** 거동을 가르지 않는다. 억제는 환경
  스니핑이 아니라 명시적 정책 값이어야 한다(OPS-07 (a) 형태).
- ⓑ **새 public 표면**: 없다(같은 task 의 축). 정책 키 `source.env-sniff.member-pairs`.
- ⓒ **우회**
  1. `System.getenv("CI")` → 수신자 뿌리 이름 + selector 이름 **쌍**을 PSI 에서 본다.
  2. `System.getProperty("…")` → 같은 쌍 집합.
  3. 완전수식(`java.lang.System.getenv`) → 수신자 뿌리의 **마지막 세그먼트**로 견준다.
  4. 별칭 import(`import java.lang.System as S`) → 별칭도 수신자 뿌리 이름이라 쌍 집합에 별칭을
     넣어야 피하는데, 그러면 **import 가 원 FQN 을 낸다**(⑥ 축과 겹쳐 걸린다).
  5. test 코드로 옮겨 그 값을 main 에 주입한다 → 주입은 명시적 정책 값이고 금지 대상이 아니다
     (경계 문장 그대로).
  6. 리플렉션으로 부른다 → **닫지 못한다.** 알려진 한계(리플렉션 축은 `collection.reflection.*` 가
     따로 든다).
- **main 의 기존 상태**: 모듈 main 소스에서 0 건(측정). build-logic 은 production 모듈이 아니라
  대상 밖이다 — 그 트리의 JDK 판 단언은 빌드 도구의 정당한 질의다.
