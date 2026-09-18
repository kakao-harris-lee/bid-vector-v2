# M6/6F-6 — checklist.md

## 위협 모델 방어 — 실행 증거

| # | 방어 | 증거 |
| --- | --- | --- |
| ① | 「미설정」과 「빈 프로필」이 섞이지 않는다(`NotDeclared`가 「면허 0개 보유」로 납작해지지 않는다) | `JdbcOperatorProfileRepositoryTest`의 미설정(null)·NotDeclared·Declared(빈 목록) 세 case가 서로 다른 값으로 왕복 — `licenses_declared` 열이 sealed 두 상태를 그대로 나른다 |
| ② | 저장된 값이 도메인 생성 경로를 우회해 `ProfileFacts`가 되지 않는다 | `OperatorProfileRow.toProfileFacts`가 `ProfileFacts`·`OperatorLicenses.Declared`·`NotDeclared`·`CategoryCode`·`LicenseName`의 public 생성자만 호출한다(D-6F6-2) — `ProfileAdapterDependencyTest`가 그 경로 밖 domain import(`decision`·`procurement`·`workflow.strategy`·`workflow.event`)를 바이트코드 상수 풀로 거부 |
| ③ | 개인정보 열이 이 표에 생기지 않는다 | `ProfileFacts`에 사업자번호·대표자·연락처 필드가 없어 어댑터가 그 값을 실을 곳이 컴파일 층에서 없다. V12는 `business_types`·`licenses_declared`·`license_names`·`region_terms`·`updated_at` 다섯 열뿐 — `privacy-gate`(D-6F6-8)가 별도 판정 대상 |

## 우회 경로 처분 — 실측 대응

| # | 우회 | 처분 | 실측 |
| --- | --- | --- | --- |
| 1 | 「미선언」을 빈 목록으로 저장·복원해 판정을 바꾼다 | 닫음 | `JdbcOperatorProfileRepositoryTest`의 세 상태 구분 test(위 방어 ①과 동일 증거) |
| 2 | 표에 개인정보 열을 더한다 | 닫음(구조) + `privacy-gate` 대상 | `ProfileFacts` 필드 부재(컴파일) — `privacy-gate` 판정은 이 evidence 밖(별도 게이트 실행) |
| 3 | 어댑터가 두 번째 복원 구현을 만든다 | 닫음 | D-6F6-2 — `OperatorProfileRow.kt`가 유일한 행↔도메인 변환 지점(`internal`, 모듈 범위). `ProfileAdapterDependencyTest`가 domain import allow-list를 지킨다 |
| 4 | 신설 게이트를 등재하지 않아 지워도 초록 | 닫음 | `gate-tests.properties`의 `gate.tests.adapters`에 `JdbcOperatorProfileRepositoryTest`·`ProfileAdapterDependencyTest`·`ProfileGateRegistrationTest` 셋 등재 + `ProfileGateRegistrationTest`가 그 등재 완결성 자체를 잰다(자기 자신 포함) |
| 5 | `adapters.profile`에서 금지 루트를 import 없이 전체 한정 좌표로 참조한다 | 닫음(구조) | `ProfileAdapterDependencyTest`가 소스 텍스트가 아니라 `javap -p -v` 상수 풀을 스캔(`StrategyAdapterDependencyTest`·`EvaluationAdapterDependencyTest`와 같은 형태) |
| 6 | 정규화 없는 업종 코드로 공고 공종과 조용히 어긋난다 | **닫지 않음 — 등재만** | `OPEN-6F6-CATEGORY-CODE-NORMALIZATION` — `JdbcOperatorProfileRepositoryTest`의 무정규화 왕복 test가 현 거동을 회귀 방지로 고정(`strategy/Text.kt`는 6F-4 소관이라 열지 않는다) |
| 7 | 싱글턴 가정을 깨고 두 행을 넣는다 | 닫음 | V12 `CHECK (id = 1)` — `JdbcOperatorProfileRepositoryTest`의 싱글턴 제약 test가 직접 SQL(`bidvector_app` 역할)로 `id=2` 삽입 시도가 `SQLException`으로 거부됨을 확인. `CleanMigrationCheckTest`의 축8 CHECK 개수(2)도 같은 제약을 다른 층에서 잰다 |

## 값 획득 축 — 새 public 표면 실측 (scope.md (2b))

| 새 표면 | 밖에 허락하는 것 | 판정 | 실측 |
| --- | --- | --- | --- |
| `JdbcOperatorProfileRepository`(클래스 + 생성자 `DataSource`) | 프로필을 읽는다 | 경계로 처리 | `commands.md`의 값 획득 축 실측 ① — `app` 모듈에서 `OperatorProfilePort` 람다 구현이 컴파일됨을 확인(스크래치, 미커밋). `fun interface`(public)라 이 구현과 무관하게 이미 열려 있던 경계다 |
| 같은 클래스의 **저장** 진입점(`save(facts: ProfileFacts)`) | 프로필을 덮어쓴다 | 경계로 처리 | `commands.md`의 값 획득 축 실측 ② — 생성자가 `DataSource` 하나만 받아, 그 참조 없이 `save`를 부르는 경로가 타입 시스템에 없다(구조 확인). DataSource를 쥔 주체는 이미 직접 SQL로 이 표에 쓸 수 있었다 — 새 권한이 아니다. 「누가 실제로 부르는가」의 인가는 6A 소관(D-6F6-1) |
| `OperatorProfileRow` | — | 닫음 | `internal`(모듈 범위) — public 선언이 아니므로 `ProfileAdapterDependencyTest`의 대상이 아니라 컴파일러 가시성 자체가 닫는다 |

## 알려진 제한

1. **업종 코드 정규화 공백**(`OPEN-6F6-CATEGORY-CODE-NORMALIZATION`, D-6F6-4) — `bidvector.strategy.CategoryCode`는 `bidvector.procurement.CategoryCode.of()`와 달리 strip·lower 정규화가 없다. 공백·대소문자 표기가 다른 값이 서로 다른 값으로 저장·복원돼 공고 공종과 조용히 어긋날 수 있다. 이 slice는 그 타입(`strategy/Text.kt`, 6F-4 소관)을 열지 않고 현 거동만 test로 고정했다.
2. **면허 요구사항 게이트 미배선**(D-6F6-5·6F-5 소관) — 이 slice는 `OperatorLicenses`를 싣기만 하고 자격 판정에 꽂지 않는다.
3. **`WorkloadPort` 미구현**(D-6F6-5, 운영자 결정 ②) — 이 slice의 명시적 out_of_scope다.
4. **HTTP 편집 endpoint 없음**(D-6F6-1) — 저장 진입점은 있으나 인가·HTTP 배선은 6A 소관. 초기 투입 전까지 `current()`는 `null`(「미설정」)을 낸다.
5. **`privacy-gate`·`migration-reviewer` 판정은 이 evidence 밖** — D-6F6-8이 지정한 두 게이트는 별도 레인이 실행·기록한다(이 slice는 구조적 방어만 여기 남긴다). 수정 라운드 1에서 실제로 돈 판정 결과(privacy-gate 통과·수정 필요 2·확인 불가 1, migration-reviewer 통과·권고 1)는 `scope.md` 「계약 갱신 (1)」이 정본이다.
6. **보존·파기 미정**(`OPEN-6F6-PROFILE-RETENTION`, D-6F6-11) — 프로필 표에 수명 정책이 없다. 상세는 `scope.md` 위협 모델 「방어하지 않는다」⑤ 참고(중복 서술하지 않는다).
7. **프로필 어휘 재식별 가능성**(D-6F6-12) — 범주 어휘라 식별성이 낮으나 희소 조합을 코드로 배제할 근거가 없다. 상세는 `scope.md` 위협 모델 「방어하지 않는다」⑥ 참고(중복 서술하지 않는다).
