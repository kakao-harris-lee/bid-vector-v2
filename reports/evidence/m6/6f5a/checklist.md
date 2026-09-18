# M6/6F-5-a — checklist.md

## 새 파일 ↔ in_scope 대조 (실측 HEAD 694fad4a)

`git diff --name-status ede5d5b..694fad4a -- . ':!reports/evidence'` 산출(신규 8·수정 6,
`milestone-6.md` 제외 — 팀장 착수 계약 고정 커밋 하나뿐, 이 레인은 편집하지 않았다):

```
M  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt
A  adapters/src/main/kotlin/bidvector/adapters/qualification/JdbcRequirementStore.kt
A  adapters/src/main/kotlin/bidvector/adapters/qualification/RequirementRowMapping.kt
A  adapters/src/main/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGate.kt
A  adapters/src/main/resources/db/migration/V13__notice_requirement.sql
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt
M  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt
A  adapters/src/test/kotlin/bidvector/adapters/qualification/JdbcRequirementStoreTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationAdapterDependencyTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/qualification/QualificationGateRegistrationTest.kt
A  adapters/src/test/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGateTest.kt
M  config/quality/gate-tests.properties
```

전부 scope.md in_scope 열넷 그대로다 — 계약 갱신 없이 원판대로 구현했다. scope 밖
필연적 companion(6F-1이 겪은 형태)은 없다 — `CleanMigration*Test`·`PersistenceTestSupport`
·`Sql.kt`·`gate-tests.properties` 넷은 scope.md 자체가 in_scope로 이미 등재해 뒀다.

## (2b) 값 획득 축 — 실측 대응표

scope.md 「경계로 처리」 둘을 실측한다(계약이 요구한 실측 항목, 표본은 커밋하지 않는다).

| 새 표면 | scope.md 판정 | 실측 |
| --- | --- | --- |
| `StoredRequirementLicenseGate`(public class + 생성자 3인자) | 경계로 처리 | **컴파일로 확인** — `LicenseGatePort`가 `fun interface`(public)라, 이 slice 밖 어떤 모듈도 `LicenseGatePort { notice -> 임의LicenseVerdict }` 형태로 이미 임의 판정을 지을 수 있었다. `workflow` 모듈의 test 소스에 그런 구현 하나를 얹어 `:workflow:compileTestKotlin`을 돌려 확인했다(BUILD SUCCESSFUL) — 표본은 검증 후 삭제하고 커밋하지 않았다(`git status --porcelain -- workflow/` 빈 출력으로 잔재 없음 확인). 이 slice가 새 표면을 여는 것이 아니라 기존 port 계약이 이미 열어 둔 자리를 처음 채울 뿐이다. |
| `JdbcRequirementStore`(public class + 생성자 `DataSource`) + `save()` 쓰기 진입점 | 경계로 처리 | **코드 확인** — `find`·`save` 둘 다 인스턴스 메서드이고 `companion object`·top-level 진입점이 없다(`grep -n "class JdbcRequirementStore\|fun save\|fun find\|companion object"` — 셋 다 인스턴스 멤버). `save()`를 부르려면 `DataSource`를 생성자에 주입해야 하므로, 그 값을 쥔 주체는 이미 원한다면 직접 SQL로도 표에 쓸 수 있다 — 이 진입점이 새로 허락하는 것은 **없음**(DataSource 없이 부를 경로 자체가 존재하지 않는다). 쓰기를 실제로 **누가** 호출하는가의 인가는 6A 소관(scope.md 위협 모델 「방어하지 않는다」②) — 이 slice는 app 조립에 꽂지 않는다(`OPEN-6F-ASSEMBLY`). |
| 행 매핑 타입(`RequirementRowRecord`·`RequirementCollectionStatus`·`RequirementRowKind`) | 닫는다 | `internal`(모듈 범위) — `QualificationAdapterDependencyTest`의 상수 풀 판정이 `adapters.qualification` 패키지의 컴파일된 클래스가 허용 루트 밖 `bidvector` 좌표를 참조하지 않는지 잰다(양성 대조 포함). |

## 알려진 제한

- **요건 내용의 진실성은 이 slice의 경계 밖이다**(scope.md 위협 모델 「방어하지 않는다」①)
  — 채우는 경로(추출 결과 저장)는 6F-5-b(`OPEN-6F5-EXTRACTION-FILL`). 이 slice만으로는
  표가 항상 비어 있어 `StoredRequirementLicenseGate`가 모든 공고에 `Uncertain
  (RequirementDataAbsent)`를 낸다 — 계약이 이미 「가짜가 아니라 참인 진술」로 적어 둔
  대로다.
- **동시 쓰기 충돌 무방어** — `save()`는 delete-then-insert 단일 트랜잭션일 뿐 낙관적
  잠금이 없다. 6F-5-a는 쓰기 호출자를 하나도 배선하지 않아(`OPEN-6F-ASSEMBLY`) 지금은
  동시 호출 경로 자체가 없다 — 6F-5-b가 실 호출자를 배선할 때 재평가 대상.
- **보존·파기 정책 없음**(`OPEN-6F5A-RETENTION`, scope.md 그대로) — `notice_requirement`
  행은 삭제·만료 트리거 없이 계속 남는다. 6B-3 소관.
- **`OPEN-GATE-REGISTRATION-STALE-INPUT`을 이 slice의 신설 완결성 게이트
  (`QualificationGateRegistrationTest`)도 물려받는다**(scope.md 그대로 — verifier r1
  HIGH-2 계열 한계, 등재된 test class가 실제로 도는지의 반대 방향인 "등재 안 된 class가
  있는지"는 이 게이트가 잡지만, "properties 파일 자체가 손상됐을 때"는 별도 게이트
  (`gateExecutionGate`) 소관이다).
- **`OperatorProfilePort`의 실 구현(6F-6, PR #38)에 이 slice는 의존하지 않는다** —
  test는 fake(`OperatorProfilePort { ... }`)만 쓴다. 프로필 미설정(`null`) 분기는
  실측했으나 실제 JDBC 구현과의 통합은 이 slice 범위 밖이다(D-6F5-3, port 계약만 공유).

## 재활용(reuse) — N/A

legacy Python bid-vector에 「요건 영속 표」에 대응하는 코드가 없다(`app/services/
license_eligibility.py`는 판정 로직만 갖고 저장은 매 호출 재추출이었다 — 영속 자체가
V2 신설 capability). `PersistenceJdbcSupport`의 `setTextArray`/`getTextList` 관용구는
재사용하지 않았다(license_names의 NULL 유무가 계약이 달라 새 nullable 판을 썼다 —
JdbcRequirementStore.kt KDoc에 근거 기록).

## golden-manifest — N/A

fixture corpus를 소비하지 않는다. 왕복·판정 test 전부 합성 도메인 값(`RequirementRow`·
`LicenseName` 리터럴)만 쓴다. `fixtures/manifest.yaml` 갱신 없음.

## differential — N/A

Python/V2 차이 판정 대상 기능이 없다(위 재활용 절과 같은 이유 — legacy에 영속 대응
capability가 없어 비교할 두 구현이 없다).

## 정책 값 근거

`LicenseQualificationPolicyData`는 test가 빈 alias table(`LicenseAliasTable(emptyList())`)
로 직접 조립해 주입한다 — `LICENSE_QUALIFICATION_POLICY`(`OPEN-QUAL-07`, 내용 미확정)를
그대로 참조하지 않고 값을 지어내지도 않는다(D-6F5-5, 어댑터가 정책 파일의 두 번째
독자가 되지 않는다).
