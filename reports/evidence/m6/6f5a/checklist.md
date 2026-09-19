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
- **보존·파기 정책 없음**(`OPEN-6F5A-RETENTION`, `milestone-6.md`에 계약 갱신 (1)로 등재됨,
  verifier r1 LOW-3 정정 — 이전 라운드에는 `scope.md`·`checklist.md`에만 있었다) —
  `notice_requirement` 행은 삭제·만료 트리거 없이 계속 남는다. 받는 쪽 6B-3.
- **`status='FAILED'`면 자식 행이 없다는 교차 표 불변식이 DB `CHECK`가 아니라 앱 읽기
  경로로만 방어된다**(D-6F5-12, migration-reviewer 권고 1) — 헤더/행이 별표라 트리거
  없이는 DB가 직접 강제하지 못한다. 지금은 읽기 경로(`toRequirementCollection`)가
  `FAILED`면 행 인자를 무시해 판정에 지어낸 값이 섞이지 않는다. **6F-5-b가 두 번째
  writer가 될 때 이 방어가 유지되는지가 그 slice의 확인 항목**이다.
- **마이그레이션 병합 순서 자동 게이트 없음**(`OPEN-MIGRATION-ORDER-GATE`, D-6F5-13) —
  받는 쪽 하네스 레인, 실 배선(`OPEN-6F-ASSEMBLY`) 전에 닫아야 한다. 이 slice가 새로
  만드는 위험이 아니라 6F-4·6F-6과 함께 쌓인 것을 여기서 처음 등재만 한다.
- **`OPEN-GATE-REGISTRATION-STALE-INPUT`을 이 slice의 신설 완결성 게이트
  (`QualificationGateRegistrationTest`)도 물려받는다**(scope.md 그대로 — verifier r1
  HIGH-2 계열 한계, 등재된 test class가 실제로 도는지의 반대 방향인 "등재 안 된 class가
  있는지"는 이 게이트가 잡지만, "properties 파일 자체가 손상됐을 때"는 별도 게이트
  (`gateExecutionGate`) 소관이다).
- **`OperatorProfilePort`의 실 구현(6F-6, PR #38)에 이 slice는 의존하지 않는다** —
  test는 fake(`OperatorProfilePort { ... }`)만 쓴다. 프로필 미설정(`null`) 분기는
  실측했으나 실제 JDBC 구현과의 통합은 이 slice 범위 밖이다(D-6F5-3, port 계약만 공유).
- **`LicenseVerdict$` 부재 단언이 소진 `when` 소비 관용구를 어댑터 패키지 안에서
  금지한다**(D-6F5-17, verifier r3 MEDIUM-a) — 도메인이 KDoc으로 규율한
  `when (verdict) { is Eligible -> …; is Ineligible -> … }` 형태를 이 패키지 안 코드가
  쓰면 컴파일이 각 subtype의 `instanceof` 참조를 상수 풀에 남겨 부재 단언에 걸린다.
  오늘 이 경로의 소비자는 **0**이다(실측 —
  `bidvector.adapters.qualification` 안에서 `LicenseVerdict`를 `when`으로 소비하는
  코드가 없다, `StoredRequirementLicenseGate`는 판정 결과를 그대로 반환할 뿐 분기하지
  않는다) — 지금은 무해하다. **마커를 넓히지 않는다**(넓히면 이 slice가 세 라운드에
  걸쳐 닫은 방향이 되돌아간다 — 소진 `when` 소비를 허용하는 순간 「호출을 남긴 채
  결과만 갈아치우기」가 다시 열린다). **`OPEN-6F5-EXTRACTION-FILL`의 확인 항목**으로
  6F-5-b에 넘긴다(D-6F5-12와 같은 형태의 이관) — 그 slice가 실제 소비자를 만들 때
  마커와 관용구 중 무엇을 굽힐지 결정한다.
- **범위 밖 부채(등재만, 이 slice가 만든 것 아님)**: CPD 중복 게이트가 이름 치환 하나로
  열린다(`OPEN-CPD-GATE-RENAME-BYPASS`, verifier r1 실측 — 변수명만 바꿔도, 판정식만
  바꿔도 통과). 받는 쪽 하네스 레인.
- **남은 우회 하나는 범위를 넓혀도 닫히지 않는다**(D-6F5-18,
  `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE`) — `adapters.persistence`에 헬퍼를 두고 그
  헬퍼가 정책 로더를 **전체 한정 좌표**로 부르면 `PersistenceAdapterDependencyTest`가
  **소스 텍스트 import 정규식**이라 못 본다. 처방은 위치가 아니라 **술어 종류**
  (바이트코드 상수 풀)라 이 slice의 범위(`qualification` 패키지)를 넓히는 것으로는
  안 닫힌다. 이 slice는 고치지 않는다 — 그 파일은 in_scope 밖이고, 그 패키지는
  6F-4가 지금 편집 중이라 술어 종류를 바꾸면 남의 레인을 붉힐 수 있다. 받는 쪽
  하네스 레인.
- **위치 술어 사다리의 구조적 종점은 타입이다**(D-6F5-20,
  `OPEN-VERDICT-CONSTRUCTION-VISIBILITY`, 이 slice 밖) — 이 slice가 세 라운드에
  걸쳐 위치 술어(클래스→패키지)를
  넓혀 온 것은 우회 비용을 단조 상승시켰을 뿐 종점을 만들지 못했다(모듈 전체로
  넓혀도 커널 모듈 헬퍼로 뚫린다, verifier r3 타당성 실측). 종점은 `LicenseVerdict`
  subtype 생성자를 `internal` + `@ConsistentCopyVisibility`로 내리는 **타입** 변경이다
  — 어댑터가 어디에 있든 verdict를 지어낼 수 없어진다. main 소스의 생성 지점은
  커널 둘뿐이라 깨지는 것은 test 조립뿐(verifier r3 실측). 도메인 커널 변경이라
  이 slice의 in_scope 밖 — 받는 쪽 도메인 레인.
- **같은 파일의 COL-06·H-3 단언 셋은 고치지 않는다**(D-6F5-22,
  `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`) — `CleanMigrationCheckTest`의 COL-06 항등식·
  H-3 결합식·`notice_round` 형식 단언 셋이 D-6F5-16이 쓰던 것과 같은
  `any { contains }` 존재 단언 형태라 같은 약점을 공유한다(제자리에 `… OR TRUE`를
  붙여도 통과한다 — D-6F5-21이 실측한 것과 같은 결함, 이 slice의 표에서 실측). 이
  slice는 **집안 관례를 따랐을 뿐**이고 그 관례의 약점이 이번에 처음 측정됐다 —
  남의 표(COL-06·H-3은 `notice_requirement_row`가 아니라 `collection_run`·
  `notice` 등 다른 표 소관)를 고치는 것은 범위 확장이다(D-6F5-18과 같은 판단).
  r4 LOW-2(`queryConstraintDef` 헬퍼가 결과 없음을 안 보고 제약 이름이 바뀌면
  불명확한 예외로 죽는다)도 **같은 파일·같은 축**이라 이 OPEN이 함께 받는다 —
  그 헬퍼도 고치지 않는다. 받는 쪽 하네스 레인.
- **집합 등식(D-6F5-21)이 울리는 세 경우는 「깨짐」이 아니라 「의도된
  트립와이어」다**(D-6F5-25, 계약 갱신 (5) 수용) — (가) `notice_requirement_row`에
  **CHECK가 붙은 컬럼**을 더할 때 (나) 그 표의 제약을 **의미 동일하게 다시 쓸**
  때(예: 괄호·공백만 다른 재서술) (다) **Postgres 메이저 상향**으로
  `pg_get_constraintdef()`의 렌더링 자체가 바뀔 때 — 셋 다 집합 test가 의도대로
  RED를 낸다. 유지 비용은 **축어 2 → 11(고유 9)**이고, (가)가 실제로 벌어지면
  `CleanMigrationCheckTest`의 개수 축·집합 등식 test 두 자리와
  `CleanMigrationColumnTest`(신규 컬럼 등재 축)를 **함께** 고쳐야 한다. 이것은
  스키마 스냅샷 래칫 관례 그대로이고 `outbox_state_check`가 이미 치르는 대가와
  같은 종류다 — 비용을 적어 두지 않으면 다음 slice가 이 트립와이어를 「게이트가
  깨졌다」로 읽고 단언을 느슨하게 만들 위험이 있다(이 slice가 존재 단언으로 네
  번 뚫린 뒤에야 얻은 자리를 그렇게 잃는 것이 가장 흔한 경로). (다)는 새 의존이
  아니라 **기존 의존의 확대**다(`postgres:16.4` 단일 상수, 기존 두 단언이 이미
  같은 렌더링에 의존한다).
- **범위 밖 파일의 좌표 낡음(등재만, verifier r2 LOW-3, 팀장 실측으로 수치 정정)** —
  `m4/4c2`의 `commands.md`가 `CleanMigrationCheckTest`의 한 줄 좌표를 인용하는데, 이
  slice가 그 파일에 **+8줄**(전부 그 좌표 앞)을 더해 좌표가 밀린다. `f19d2eb`(M4)
  이후 그 파일을 만진 커밋이 6B-1 둘·6F-1 둘·이 slice 하나라 좌표는 이 slice
  이전에 이미 낡아 있었고, 이 slice는 더 밀 뿐이다 — evidence-pack의 「낡는 좌표
  금지」는 **이 slice가 편집하는 파일**에 적용되고, `m4/4c2`는 범위 밖이라 직접
  고치지 않는다.
  같은 계열로 `m3/3h`의 codex-review JSON(`codex-review-20260916T162509Z.json`)도
  `CleanMigrationTest`의 좌표를 인용하는데, `f19d2eb` 이후 그 파일을 만진 커밋이
  m4-4c2·m3-3g·6B-1·6F-1 넷에 이 slice 하나라 좌표가 이미 낡아 있었다 — **이 slice가
  만든 낡음이 아니다**(이 slice의 몫만 **+12** — `git show 056d3a37 --stat`의 세
  hunk가 그 인용 줄 앞에서 각각 3·3·6줄을 더한다, 뒤 hunk 하나는 인용 줄 뒤라 무관).
  이쪽은 **고칠 수 없는 종류**다: Codex 심판 기록은 append-only이고 CLAUDE.md가 그
  파일의 사후 수정을 규율로 금지한다 — evidence-pack의 「낡는 좌표 금지」를 심판
  기록 자체에는 적용할 수 없다. 다만 **「기록은 못 고쳐도 다음 심판의 인용 관례는
  닫을 수 있는 결정」**이다(계약 갱신 (3) D-6F5-19, 앞 라운드의 「OPEN을 신설하지
  않는다」 처분을 절반만 맞은 것으로 정정) — **`OPEN-CODEX-RECORD-COORDINATES`로
  넘긴다**(받는 쪽 하네스 레인, 다음 Codex 심판부터 `file:line`이 아니라 심볼 단위로
  인용하도록).

## 재활용(reuse) — N/A

legacy Python bid-vector에 「요건 영속 표」에 대응하는 코드가 없다(`app/services/
license_eligibility.py`는 판정 로직만 갖고 저장은 매 호출 재추출이었다 — 영속 자체가
V2 신설 capability). **D-6F5-11(verifier r1 LOW-1 뒤 정정)** — 착수 판은
`PersistenceJdbcSupport`의 `setTextArray`/`getTextList`를 재사용하지 않고 CPD 중복
게이트를 문면으로 피한 별도 재서술을 썼다(code-reviewer HIGH). 지금은 그 관용구에
**형제 위임**한다(`internal`=모듈 범위라 파일 편집·scope 확장 없이 가능했다) — NULL과
「빈 배열」의 구분(D-6F5-2 계약)만 `JdbcRequirementStore.kt`가 더한다.

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
