# M6/6F-6 — commands.md

## 2026-09-18T10:40:00Z (RED→GREEN, 왕복·세 상태 구분·갱신·싱글턴 제약)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcOperatorProfileRepositoryTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 미설정 null·Declared 왕복·NotDeclared↔Declared(빈 목록)
  구분·갱신(upsert)·싱글턴 표 제약(직접 SQL `id=2` 거부)·업종 코드 무정규화 왕복
  (`OPEN-6F6-CATEGORY-CODE-NORMALIZATION`) 7개 testcase.

## 2026-09-18T10:44:00Z (신설 패키지 의존 게이트)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*ProfileAdapterDependencyTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 바이트코드 상수 풀 스캔(허용 루트 밖 참조 없음) + 양성 대조 둘.

## 2026-09-18T10:48:00Z (등재 완결성 게이트)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*ProfileGateRegistrationTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — `adapters.profile` 패키지 `*Test` 셋 전부 `gate-tests.properties`
  등재 확인 + 양성 대조.

## 2026-09-18T10:52:00Z (schema-gate 회귀 확인 — CleanMigration 셋)
- cmd: `./gradlew --no-daemon :adapters:test --tests 'bidvector.adapters.persistence.CleanMigrationTest' --tests 'bidvector.adapters.persistence.CleanMigrationColumnTest' --tests 'bidvector.adapters.persistence.CleanMigrationCheckTest' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 축1(테이블)·축5b(PK)·축9(GRANT 행렬)에 `operator_profile` 등재,
  축2·3·4(컬럼)·축8(CHECK 개수 2) 신규 반영 뒤 전건 통과.

## 2026-09-18T10:56:00Z (ktlint/detekt 1차 실패 → 수정 → 재확인)
- cmd: `./gradlew --no-daemon check`
- exit: 1
- 핵심 결과: `:adapters:ktlintTestSourceSetCheck`·`:adapters:detekt` FAILED — 신설 test 파일의
  줄 길이(120자 초과)·인자 줄바꿈 위반 셋. 같은 파일 별도 커밋으로 수정.

## 2026-09-18T11:00:00Z (수정 뒤 좁은 재확인)
- cmd: `./gradlew --no-daemon :adapters:ktlintTestSourceSetCheck :adapters:detekt`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL.

## 2026-09-18T11:04:00Z (acceptance S-50·S-51·S-52 재확인, 수정 뒤)
- cmd: `./gradlew --no-daemon :adapters:test --tests '*JdbcOperatorProfileRepositoryTest*' --rerun-tasks`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — 서식 수정이 거동을 바꾸지 않음을 재확인.

## 2026-09-18T11:08:00Z (acceptance S-10 — check 전건)
- cmd: `./gradlew --no-daemon check`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL, 337 actionable tasks(67 executed, 270 up-to-date).
  `:adapters:cpdCheckObserved`가 중복을 관측 보고했으나(observed 전용, 게이트 실패 아님)
  `check` 자체는 통과했다.

## 2026-09-18T11:12:00Z (acceptance S-11 — qualityBaseline)
- cmd: `./gradlew --no-daemon qualityBaseline`
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL(UP-TO-DATE, 직전 `check`가 이미 실측).

## 2026-09-18T11:16:00Z (acceptance S-20 — one-command-check.sh, Kotlin + Python 전건)
- cmd: `./tools/one-command-check.sh`
- exit: 0
- 핵심 결과: `one-command-check: 완료 — Kotlin 전건 + Python 전건 통과`.

## 2026-09-18T11:20:00Z (값 획득 축 실측 ① — scope.md (2b) 표 1행, `OperatorProfilePort` 경계)
- cmd: `./gradlew --no-daemon :app:compileTestKotlin` (`app/src/test/.../ScratchProfilePortBoundaryProbe.kt`
  — `OperatorProfilePort { ProfileFacts(emptySet(), OperatorLicenses.NotDeclared, emptyList()) }`를
  `adapters.profile`과 무관한 `app` 모듈에서 람다 구현, 컴파일 확인 뒤 삭제·미커밋)
- exit: 0
- 핵심 결과: BUILD SUCCESSFUL — `OperatorProfilePort`가 `fun interface`(public)라 이 slice의
  구현과 무관하게 아무 모듈이나 이미 임의 `ProfileFacts`를 내는 구현을 지을 수 있다는
  「경계로 처리」 판정을 희망이 아니라 컴파일 성공으로 확인. `git status --porcelain -- app/`
  출력 없음(잔여 파일 없음 확인).

## 2026-09-18T11:24:00Z (값 획득 축 실측 ② — scope.md (2b) 표 2행, 저장 진입점 권한)
- 실측 방법: 컴파일 시점 구조 확인(런타임 실행 불필요) — `JdbcOperatorProfileRepository`의
  생성자는 `(dataSource: DataSource)` 하나뿐이고 `save(facts: ProfileFacts)`는 그 인스턴스의
  멤버 함수다. `DataSource` 참조 없이 이 클래스를 생성하거나 `save`를 호출하는 경로가
  Kotlin 타입 시스템 자체에 없다(새 인자 없는 팩토리·전역 접근자를 이 클래스가 노출하지
  않는다) — `JdbcStrategyRepository`(6F-1)와 같은 구조.
- 핵심 결과: 「저장 진입점이 밖에 새 권한을 주는가」 — 아니다. `DataSource`를 이미 쥔 주체만
  이 경로를 부를 수 있고, 그 주체는 이미 같은 표에 직접 SQL로 쓸 수 있었다(새 권한이 아니다).
  「누가 이 메서드를 실제로 부르는가」의 인가는 6A 소관(D-6F6-1) — 이 slice 는 그 경계를
  좁히지 않는다.

## 2026-09-18T11:28:00Z (clean-tree 게이트, 리뷰 요청 조건)
- cmd: `git status --porcelain -- adapters/src/main/kotlin/bidvector/adapters/profile adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt adapters/src/main/resources/db/migration/V12__operator_profile.sql adapters/src/test/kotlin/bidvector/adapters/profile adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt config/quality/gate-tests.properties`
- exit: 0
- 핵심 결과: 출력 없음(clean) — 마지막 내용 커밋 뒤 in_scope 경로 전부 깨끗함.

## 2026-09-18T11:32:00Z (clean-tree 양성 대조)
- cmd: `OperatorProfileRow.kt`에 주석 한 줄 추가(78→79줄) → 위와 같은 `git status --porcelain`
  → `sed -i '' '$ d'`로 마지막 줄만 비파괴 절삭(원래 78줄로 복귀, `checkout --` 미사용) → 재확인
- exit: 0(양성 대조 단계에서는 `M ...OperatorProfileRow.kt` 한 줄이 찍혔고, 복귀 뒤 재확인은
  출력 없음 — 파일이 정확히 78줄로 복귀)
- 핵심 결과: 게이트가 실제로 변경을 잡는다 — 항상 통과만 하는 회귀가 아님을 확인.

## 2026-09-18T11:36:00Z (비밀값 스캔, verifier r1 BLOCKER-1 시정 — 참조형으로 재작성)
- cmd: `grep -rniE -f config/quality/leak-patterns.txt adapters/src/main/kotlin/bidvector/adapters/profile adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt adapters/src/main/resources/db/migration/V12__operator_profile.sql adapters/src/test/kotlin/bidvector/adapters/profile adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt config/quality/gate-tests.properties`
- exit: 0
- 핵심 결과: `PersistenceTestSupport.kt`의 Testcontainers 고정 test 자격증명 줄 둘에서 매치 —
  둘 다 이 slice가 편집한 줄이 아니고(기존 코드, `operator_profile` 등재 한 줄에는 매치
  없음), 값 자체가 test 컨테이너 전용 상수라 비밀값이 아니다. **어휘를 이 문서에 축어로
  적지 않는다** — `LeakPatternGateTask`의 `scanRoot`가 `reports/evidence`라(제외는
  `scope.md` 하나뿐, `build-logic`의 `bidvector.quality-baseline.gradle.kts`
  `leakPatternGate` 등록 참고) **이 문서 자신도 그 게이트의 스캔 대상이다** — 앞 판(verifier
  r1 BLOCKER-1)이 「이 파일을 보지 않는다」고 적은 것은 사실과 반대였고, 그 문장이 적은
  어휘 자체가 `check`를 붉게 만들었다.

**마지막 HEAD(이 문단을 포함한 evidence 커밋 이후)의 `check` 재실측**은 아래 「acceptance
재실측(수정 라운드 1)」 절 — 이 절 자체가 이제 어휘를 적지 않으므로 그 재실측이 스스로를
다시 깨뜨리지 않는다.
