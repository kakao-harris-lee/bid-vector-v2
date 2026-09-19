# M6/6F-5-a — 자격 요건 영속 + 면허 게이트(저장된 것만 읽는다) (2026-09-19)

```yaml
milestone: M6
slice: 6f5a-requirement-persistence-license-gate
base_sha: ede5d5b   # main. PR #36·#37 병합 뒤. (PR #38/6F-6 은 열려 있고 이 slice 는 그 브랜치에 의존하지 않는다 — D-6F5-3)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - adapters/src/main/resources/db/migration/V13__notice_requirement.sql                  # 공고별 자격 요건 영속. V10 6A-1 · V11 6F-4 · V12 6F-6 선점(D-6F5-7)
  - adapters/src/main/kotlin/bidvector/adapters/qualification/JdbcRequirementStore.kt      # 요건 읽기·쓰기(쓰기는 6F-5-b 가 소비)
  - adapters/src/main/kotlin/bidvector/adapters/qualification/RequirementRowMapping.kt     # 행 ↔ `RequirementCollection` 세 갈래(`internal`)
  - adapters/src/main/kotlin/bidvector/adapters/qualification/StoredRequirementLicenseGate.kt  # `LicenseGatePort` 첫 production 구현
  - adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt                         # 요건 SQL 추가만(기존 문장 무편집)
  - adapters/src/test/kotlin/bidvector/adapters/qualification/**                           # 왕복(세 갈래) · 게이트 판정 · 의존 게이트 · 등재 완결성
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt          # 신설 표 등재(추가만)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt    # 같은 축
  - adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt     # 같은 축
  - adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt      # 정리 목록 추가(기계적)
  - config/quality/gate-tests.properties                                                   # 신설 게이트 test 등재(추가만) — 공유 파일
  - reports/evidence/m6/6f5a/**
  - milestone-6.md                                                                         # 6F-5 분할·6F-5-a 착수 문단(팀장 커밋)
out_of_scope:
  - **LLM 추출 호출·추출 결과 쓰기 경로**       # **6F-5-b.** 실제 LLM 호출은 사용자 승인 대상이고, 판정 경로에서 부르려면 port 를 열어야 한다(D-6F5-1)
  - `LicenseGatePort` 시그니처 변경              # **필요 없다**(D-6F5-2) — 저장된 것만 읽으면 `notice` 하나로 충분하다
  - `adapters/extraction/**`                     # M3 산출물 무편집. 이 slice 는 그 결과를 **담을 자리**만 만든다
  - `strategy/**`·`app/**`                       # 6F-4·6A-1 레인 소유
  - 감시 통과 판정·`WatchSubjectPort`            # 6F-4
  - app DI 조립                                  # `OPEN-6F-ASSEMBLY`
acceptance_commands:
  - "./gradlew --no-daemon check"                                                                    # S-10
  - "./gradlew --no-daemon qualityBaseline"                                                          # S-11
  - "./gradlew --no-daemon :adapters:test --tests '*JdbcRequirementStoreTest*' --rerun-tasks"        # S-50 — 세 갈래 왕복
  - "./gradlew --no-daemon :adapters:test --tests '*StoredRequirementLicenseGateTest*' --rerun-tasks" # S-51 — 게이트 판정
  - "./gradlew --no-daemon :adapters:test --tests '*QualificationAdapterDependencyTest*' --rerun-tasks" # S-52
  - "./gradlew --no-daemon :adapters:test --tests '*QualificationGateRegistrationTest*' --rerun-tasks" # S-53
  - "./tools/one-command-check.sh"                                                                   # S-20
rollback: |
  `rollback.md` 첫머리에 **`실측 HEAD: <sha>`** 한 줄(2026-09-18 규율). 앞 라운드 실측을 옮기지 않는다.
  목록은 **마지막 내용 커밋 뒤** `git diff --name-status ede5d5b..HEAD -- . ':!reports/evidence'` 로 재산출.
  신규는 삭제, 편집은 `git restore --source=ede5d5b --staged --worktree -- <재산출한 경로 개별 인자>`.
  공유 파일(`Sql.kt`·`gate-tests.properties`·`CleanMigration*Test`·`PersistenceTestSupport`·`milestone-6.md`)은
  커밋 해시 hunk 격리. **마이그레이션 비대칭 없음** — 적용 DB 인스턴스 0(6F-4·6F-6 이 각각 실측).
  임시 clone(`git clone --no-hardlinks`, **`cp -r` 금지**)에서 ①~⑥ 실측, 갈음은 **트리 동일성**으로만.
  **acceptance 재실측은 evidence 커밋 뒤 HEAD 에서**(6F-6 D-6F6-9 — 그 커밋이 게이트를 깨뜨린 실측이 있다).
```

작성: 2026-09-19, 세션 모델 단독. 근거: `milestone-6.md` 6F 군 표(6F-5 「요구사항 영속·운영자 면허 설계
(**LLM 재추출 대신 영속이 필요한지 포함**)」) · 배선 재고 `_workspace/m6-wiring/01_ports.md` 축 1 ·
착수 조사(아래 실측 셋).

## 착수 조사 — 구조적 제약 셋 (이 slice 의 분할 근거)

① **`WatchGatedExtractor` 는 `WatchVerdict.Passed` 를 요구하는데 `LicenseGatePort.verdictFor(notice)` 는 그
값을 주지 않는다.** 그 게이트는 「감시 탈락 공고로 속여 부를 수 없다」를 D-3C-6 의 방어로 세웠고
`WatchVerdict.Passed` 의 유일한 생성 경로가 `WatchRules.evaluate`(`internal constructor`)다. 판정 경로에서
추출을 부르려면 **port 를 열거나 어댑터가 감시를 재평가**해야 한다 — 전자는 계약 변경, 후자는 판정의 두 번째
구현이다. 둘 다 이 slice 가 할 일이 아니다.

② **실제 LLM 호출은 사용자 승인 대상**(agent-workflow 1절)이고, 매 run·매 후보 호출은 **비용·비결정성·지연을
판정 경로에** 넣는다. milestone 문면 자체가 「LLM 재추출 대신 영속이 필요한지」를 이 slice 의 물음으로 적었다.

③ production 에서 `WatchVerdict.Passed` 가 나오려면 **`WatchSubjectPort` 실 구현(6F-4)이 선행**한다 — 지금은
다른 세션이 진행 중이다.

## 그래서 둘로 가른다 (D-6F5-1)

- **6F-5-a(이 slice)** — 요건 **영속 자리**를 만들고 `LicenseGatePort` 가 **저장된 것만 읽어** 판정한다.
  LLM 호출 0, port 시그니처 무편집, 6F-4 무의존.
- **6F-5-b(뒤)** — 추출 결과를 그 표에 **채우는 경로**. 감시 통과 시점에 돌고, 실제 LLM 호출은 **운영자 승인**
  대상이며 6F-4 선행이다. `OPEN-6F5-EXTRACTION-FILL` 로 등재한다.

**이 분할이 만드는 상태를 숨기지 않는다**: 6F-5-a 만으로는 표가 비어 있어 게이트가 모든 공고에 대해
`Uncertain(RequirementDataAbsent)` 를 낸다. 그것은 **가짜가 아니라 참인 진술**이고, 도메인이 이미 가진 상태다
— 1C U-5 가 「`Uncertain` 은 미보유가 아니다」로 정해 use case 가 후보를 떨어뜨리지 않는다(실측:
`licenseGateDrop` 이 `Ineligible` 만 떨어뜨린다). 그래서 이 slice 는 **판정을 바꾸지 않고 자리를 만든다.**

## 계약 고정 결정

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F5-1** | 6F-5 를 **a/b 로 가른다**(위) | 착수 조사 ①②③. 한 slice 로 묶으면 **port 변경 · 외부 effect 승인 · 타 레인 선행** 셋을 한 번에 지어야 한다 — 6C 가 축을 섞어 재작업 3 라운드를 쓴 실측이 있다 |
| **D-6F5-2** | 게이트는 **읽기만** 한다. 저장된 요건이 없으면 `RequirementCollection.DataAbsent` 를 **그대로 커널에 넘긴다** — 지어내지 않는다. port 시그니처·가시성 **무편집** | 커널이 세 갈래를 이미 갖고 있고(`DataAbsent`/`CollectionFailed`/`Collected`) 각각을 다른 `UncertainReason` 으로 옮긴다. 어댑터가 「없음」을 「빈 목록」으로 접으면 **판정 사유가 바뀐다** — 6F-6 의 세 상태 교훈과 같은 축 |
| **D-6F5-3** | `OperatorLicenses` 는 **`OperatorProfilePort` 로부터** 온다(주입). Jdbc 구현에 직접 의존하지 않는다 | 그 port 는 **main 에 이미 있고** 실 구현은 6F-6(PR #38, 열림)이 낸다. port 로 받으면 이 slice 가 **미병합 브랜치에 의존하지 않는다**(「진행 중 slice 의 브랜치는 종결 뒤에만 병합한다」). 프로필 미설정이면 `NotDeclared` → 커널이 `Uncertain(OperatorLicensesNotDeclared)` |
| **D-6F5-4** | 요건 표는 **공고당 복수 행** + 「없음」·「수집 실패」·「수집됨」 **세 갈래를 표가 잃지 않게** 저장한다 | 커널의 sealed 세 갈래가 **다른 판정 사유**로 이어진다. 「행이 없다」와 「수집을 시도했으나 실패했다」는 다른 사실이다 — 후자를 전자로 접으면 실패가 조용히 사라진다(6F-6 이 `licenses_declared` 로 같은 문제를 푼 형태를 따른다) |
| **D-6F5-5** | 정책은 **조립이 주입**한다(`Resolution.Resolved<LicenseQualificationPolicyData>`) — 어댑터가 정책 파일을 직접 읽지 않는다 | 6F-1 D-6F1-6 과 같은 규율. 정책의 정본은 하나이고 어댑터가 두 번째 독자가 되면 값이 갈린다 |
| **D-6F5-6** | 패키지 `bidvector.adapters.qualification` 신설 + **바이트코드 상수 풀** 의존 게이트 + `gate-tests.properties` 등재 + **등재 완결성 게이트**를 한 벌로 낸다 | `adapters.persistence` 는 `bidvector.workflow` 참조 금지(M3/3D). 6B-1·6F-1·6F-2·6F-6 이 같은 벽을 만나 패키지를 갈랐다. **신설 게이트는 등재까지가 한 벌**(6F-2 verifier r1 HIGH-2) |
| **D-6F5-7** | 마이그레이션 번호는 **V13** | V10 6A-1(계약) · V11 6F-4(파일 실재) · V12 6F-6(PR #38) 선점 |
| **D-6F5-8** | **병합 순서는 V12(6F-6) 뒤**이고 그것은 V11(6F-4) 뒤다 | Flyway `outOfOrder=false`(migration-reviewer 가 6F-6 에서 실측). V10 은 파일이 없어 기다리지 않는다 |

## 위협 모델 — 6F-5-a 고유 경계

**방어한다**: ① 저장된 요건이 **커널의 세 갈래를 잃고** 들어오는 것(특히 「수집 실패」가 「없음」으로 접히는 것)
② 어댑터가 `RequirementCollection`·`LicenseVerdict` 를 **지어내는** 것 — 판정은 `LicenseEligibility.judge` 하나가
낸다 ③ 어댑터가 정책 파일의 두 번째 독자가 되는 것.

**방어하지 않는다**: ① **요건 내용의 진실성** — 이 slice 는 담을 자리만 만들고 채우는 것은 6F-5-b 다
② 누가 요건을 쓰는가(인가는 6A) ③ 감시 통과 판정(6F-4) ④ LLM 추출의 정확도·비용(6F-5-b) ⑤ **보존·파기**
(`OPEN-6F5A-RETENTION` — 6B-3 축. 6F-6 에서 같은 누락을 지적받았다).

이 경계는 승인 문면을 줄이지 않는다 — milestone 의 6F-5 행이 「**LLM 재추출 대신 영속이 필요한지 포함**」을
이 slice 의 물음으로 적었고, 이 계약이 **영속 쪽으로 답하고 그 절반을 짓는다**.

## 값 획득 축 — 새 public 표면 전수 (2b)

| 새 표면 | 밖에 허락하는 것 | 판정 |
| --- | --- | --- |
| `StoredRequirementLicenseGate`(클래스 + 생성자: store·`OperatorProfilePort`·정책) | 임의 store·프로필·정책으로 면허 판정을 낸다 | **경계로 처리** — `LicenseGatePort` 가 `fun interface`(public)라 아무 모듈이나 이미 임의 `LicenseVerdict` 를 내는 구현을 지을 수 있다. **실측 항목**: 컴파일로 확인 |
| `JdbcRequirementStore`(클래스 + 생성자 `DataSource`) + **쓰기 진입점** | 요건 행을 **쓴다** — 그 값이 면허 판정의 입력이 된다 | **경계로 처리**(DataSource 를 쥔 주체는 이미 표에 쓸 수 있다). **실측 항목**: 이 진입점이 없을 때와 있을 때 밖이 할 수 있는 일이 같은가. 쓰기를 **누가** 하는가의 인가는 6A |
| 행 매핑 타입 | — | **닫는다** — `internal` |

수정 라운드마다 이 표를 갱신한다.

## 우회 경로와 처분 (≥5)

| # | 우회 | 처분 |
| --- | --- | --- |
| 1 | 「수집 실패」를 「없음」으로 저장·복원해 실패를 지운다 | D-6F5-4 — 세 갈래를 열로 구분하고 **왕복 test 가 셋을 각각** 잰다(6F-6 선례) |
| 2 | 어댑터가 `LicenseVerdict` 를 직접 조립한다 | 판정 호출 자리가 `LicenseEligibility.judge` **하나**임을 test 로 잠그고, 의존 게이트가 그 외 경로를 본다 |
| 3 | 어댑터가 정책 파일을 직접 읽는다 | D-6F5-5 — 정책은 생성자 주입. 의존 게이트가 정책 로더 좌표 참조를 잡는다 |
| 4 | 신설 게이트를 등재하지 않아 지워도 초록 | D-6F5-6 — 등재 + **완결성 게이트**(그 게이트 자신도 등재) |
| 5 | `adapters.qualification` 에서 금지 루트를 **import 없이 전체 한정 좌표**로 참조 | 바이트코드 상수 풀 게이트 |
| 6 | **게이트 술어가 자기 입력을 재계산해 항진명제가 된다** | **상시 경계**(6F-2 HIGH-1 · 6F-6 HIGH-1 **두 번 연속**) — 단언 양변이 같은 생성 경로를 지나지 않게 하고, **닫힘 판정은 변이가 붉어지는 실측으로만** |
| 7 | 요건 행이 공고와 무관하게 남아 다른 공고 판정에 실린다 | 표의 FK·조회 키를 공고 식별자로 닫고 test 로 잰다 |

## 계약 갱신 (1) — 판정 레인 셋의 결과 (2026-09-19, 팀장)

verifier `not-ready`(산출물 HIGH 2 · LOW 3) · code-reviewer(HIGH 1 · LOW 2) · migration-reviewer 통과(권고 2).
판정 대상 `eac38c85`. **HIGH 둘은 이 계약의 우회 처분이 틀렸다고 지목한 자리다** — 계약이 먼저 움직인다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F5-9**(HIGH-1, **계약 오류 정정**) | 우회 3 의 처분 「의존 게이트가 정책 로더 좌표 참조를 잡는다」는 **거짓이다.** 처분을 **상수 풀 부재 단언**으로 바꾼다 — 어댑터의 컴파일된 클래스에 정책 로더 좌표가 **없어야 한다**를 단언한다 | `LICENSE_QUALIFICATION_POLICY` 는 `bidvector.qualification` 에 있고 그 루트는 의존 게이트의 **허용 루트**다(어댑터가 커널을 봐야 해서 필연이다). 실측: 어댑터에 그 좌표를 심어도 의존 test·`:adapters:check`·전건 `check` 가 **전부 초록**. 위협 모델이 「방어한다 ③」으로 적은 자리가 무방비였다. **허용 루트 안의 좌표는 「루트 단위 금지」로 닫히지 않는다** — 그 안에서 **무엇을 참조하면 안 되는지**를 따로 적어야 한다. 처방의 근거도 실측이다: 변이 상태의 상수 풀에 `LicensePolicyKt`·`getLICENSE_QUALIFICATION_POLICY` 가 남는다(새 하네스 불필요) |
| **D-6F5-10**(HIGH-2, **계약 오류 정정**) | 우회 2 의 처분 「판정 호출 자리가 하나임을 test 로 잠근다」는 **존재 단언이라 부족하다.** ① 상수 풀에 **`LicenseVerdict$` 부재 단언**을 더한다(어댑터는 verdict 를 **생성할 이유가 없다** — 원판 상수 풀에 0건) ② 게이트 test 에 **`Collected(빈 rows)`** 케이스를 더한다 | 의존 게이트는 `LicenseEligibility`·`judge` **어휘가 있는지**만 본다 — 호출을 **남긴 채 결과만 갈아치우면 통과**한다. 실측: `Collected(빈 rows)` 경로만 `Eligible(emptySet())` 로 뒤집었더니 전건 `check` **초록**, 커널 참값은 `Uncertain(RequirementDataAbsent)`. **면허 판정에서 가장 비싼 방향의 오판**(부적격을 적격으로)이다. 커버리지 공백이 나란히 있었다 — 게이트 test 6 케이스에 **`Collected(빈 rows)` 가 없었다**. D-6F5-4 가 표를 둘로 가른 바로 그 상태인데 **저장만 잠그고 판정은 안 잠갔다** |
| **D-6F5-11**(CPD 재서술) | `getNullableTextArray` 를 **형제 `getTextList` 위임**으로 바꾼다(`getArray(column) ?: return null; return getTextList(column)`). `setNullableTextArray` 도 같은 형태로 | **두 레인이 사실에는 합의하고 severity 만 갈렸다** — code-reviewer **HIGH**(중복 최소화 규율 위반, 더 나은 대안이 계약 변경 없이 존재), verifier **LOW**(거동 동일·게이트 무손상이라 라운드를 막지 않는다). 사실 판정은 같다: **중복 게이트를 문면으로 피한 재서술**이다. 실측 둘 — 같은 행에 입력 다섯(SQL NULL·빈 배열·정상·NULL 원소 1·전부 NULL)을 넣어 **두 구현이 전부 같은 결과**(예외 「형」만 ISE/IAE 차이) · 위임 대안이 `cpdCheck` 0 + 왕복·게이트 test 0. 회피가 **필요하지 않았다** — `getTextList` 는 `internal`(**모듈 범위**)이라 `adapters.qualification` 이 파일 편집·scope 확장 없이 이미 호출할 수 있다 |
| **D-6F5-12**(migration 권고 1) | 「`status='FAILED'` 면 자식 행이 없다」 불변식이 **DB CHECK 가 아니라 앱 읽기 경로**로만 방어된다는 사실을 알려진 제한에 등재하고, **6F-5-b 의 확인 항목**으로 넘긴다 | 헤더/행이 별표라 교차 표 제약은 트리거가 필요해 이 slice 가 두지 않았다. 읽기 경로가 `FAILED` 면 자식 인자를 무시하도록 짜여 있어 **판정에 지어낸 값이 섞이지는 않는다**(migration-reviewer 실측). **6F-5-b 가 두 번째 writer 가 될 때** 그 방어가 유지되는지가 그 slice 의 항목이다 |
| **D-6F5-13**(migration 권고 2) | 병합 순서 규율(D-6F5-8)에 **자동 게이트가 없다**는 사실을 `OPEN-MIGRATION-ORDER-GATE` 로 등재한다 — 받는 쪽은 하네스 레인 | CI 에 버전 연속성·순서 강제가 없다(grep 확인). 지금은 적용 인스턴스 0 이라 실질 위험이 0 이지만 **세 slice(6F-4·6F-6·6F-5-a)가 연쇄로 같은 수동 규율에 의존**한다. **산문으로만 사는 규율은 어긴 것이 보이지 않는다** — 이 저장소가 되돌리기 목록 낡음에서 세 번 겪은 형태다. 실 배선(`OPEN-6F-ASSEMBLY`)이 열리기 전에 닫아야 한다 |

**범위 밖 부채(등재만)**: **CPD 게이트가 이름 치환 하나로 열린다** — verifier 실측: 축어 복사판은 `cpdCheck` exit 1 인데 **변수명만** 바꿔도 0, **판정식만** 바꿔도 0. 이 slice 밖 기존 부채이고 `OPEN-CPD-GATE-RENAME-BYPASS` 로 등재한다(받는 쪽 하네스 레인).

**환경 경합 기록(finding 아님)**: verifier 의 S-52 첫 실행이 in_scope 밖 파일에서 exit 1 이었고 `Detected multiple Kotlin daemon sessions` 경고가 동반됐다 — **재실행 exit 0**, 직후 전건 `check` 도 0. 다른 세션 빌드와의 경합으로 판단했고 그 사실을 숨기지 않고 적는다.

## 계약 갱신 (2) — verifier r2 (2026-09-19, 팀장)

verifier r2 `not-ready`(신규 산출물 HIGH 1 · MEDIUM 1 · LOW 3). 판정 대상 `d550ee05`.
**r1 의 HIGH 둘은 닫혔다** — 변이 재식재로 RED 확인했고 과적합도 아니다(게이트 test 가 덮지
않는 경로까지 부재 단언 **단독**으로 잡았다). 새 HIGH 는 **그 둘을 닫은 단언의 범위**가 이
계약의 방어 선언과 갈린다는 지적이고, **원인은 r1 처방 문면이 클래스 파일 하나를 콕 집은
것**이다 — 계약이 먼저 정정된다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F5-14**(r2 HIGH, **계약 오류 정정 — r1 처방의 범위 오류**) | D-6F5-9·10 의 부재 단언 둘을 `StoredRequirementLicenseGate.class` **한 파일이 아니라 패키지 전체 class 파일 목록**에 대해 돌린다(허용 루트 축이 이미 쓰는 `walkTopDown()` 목록을 공유한다). **존재 단언**(`LicenseEligibility`·`judge` 참조)만 게이트 클래스에 남긴다 — 그것은 「그 클래스가 판정을 **호출한다**」는 진술이라 대상이 한 파일인 것이 맞다 | 계약이 선언한 방어 대상은 **패키지**다(위협 모델 ②③ 「**어댑터가** …」, D-6F5-9 「**어댑터의** 컴파일된 클래스」). 술어는 클래스 하나였다. verifier 실측 둘, **전건 `check` 모두 exit 0**: ① 정책 읽기를 **같은 패키지 형제 파일**로 옮기고 게이트가 호출 ② `LicenseVerdict.Eligible(emptySet())` 조립을 형제 파일로 옮기고 **게이트 test 가 덮지 않는 경로**에서 반환(게이트 클래스 상수 풀에는 반환 타입 시그니처만 남아 `LicenseVerdict$` 마커에 안 걸린다) — ②는 **다시 부적격 → 적격 방향**이다. 우회 비용이 「게이트를 바꾸기」가 아니라 **헬퍼 추출이라는 평범한 리팩터링**이면 술어가 서지 않는다. r1 HIGH 둘과 **같은 계열의 세 번째**다 — 술어의 범위가 계약의 선언보다 좁다 |
| **D-6F5-15**(r2 LOW-1, 문면 정정) | `LicenseVerdict$` 부재 단언의 문면을 **술어에 맞춰 넓힌다** — 「생성자 참조 부재」가 아니라 **「어댑터는 verdict 를 통과시키기만 한다 — 상수 풀에 어떤 subtype 좌표도 두지 않는다」**. 마커를 생성 형태로 좁히지 **않는다** | 실측: `if (judged is LicenseVerdict.Eligible)` 한 줄만 넣어도 RED(컴파일이 `instanceof …LicenseVerdict$Eligible` 를 남긴다) — 술어는 **읽기까지** 막는데 D-6F5-10·KDoc 은 「생성자」로 좁게 적었다. 맞추는 길이 둘인데 **좁히는 쪽은 이 slice 가 세 번 겪은 실패 방향**이다(생성 형태를 열거하면 `copy`·`data object` 싱글턴 참조 같은 다른 획득 경로가 열린다). 보수적 방향을 유지하고 **문면을 술어에 맞춘다** |

**장부층 셋**(라운드를 막지 않으나 같은 라운드에 함께 닫는다):
**MEDIUM-1** — `rollback.md` 의 `git restore` 거동 설명이 **사실과 반대**다. 신규 경로를 ② 목록에
섞으면 오류가 아니라 **exit 0 으로 조용히 삭제**되고, 문서가 인용한 exit 1 은 **경로가 어디에도
없을 때**만 난다(verifier 가 파이프 없이 종료 코드를 직접 받아 갈라 실측). 절차 자체는 판정 대상
SHA 에서 ①~⑥ 전부 재현되고 트리 동일성 6/6 이라 틀린 트리를 만들지는 않으나, 거기서 끌어낸
**안전 주장(「섞으면 오류로 잡힌다」)이 거짓**이라 다음 라운드가 그것에 기댄다.
**LOW-2** — `checklist.md` 알려진 제한의 축어 중복 bullet 하나.
**LOW-3** — `reports/evidence/m4/4c2/commands.md` 의 좌표를 이 slice 삽입(+8줄)이 더 민다. 이
slice 가 만든 낡음은 아니나(6B-1 둘·6F-1 둘이 먼저 밀었다) **알려진 제한 등재 대상**이다.

## 계약 갱신 (3) — verifier r3 (2026-09-19, 팀장)

verifier r3 **`ready-for-review`** — 산출물 blocker 0 · high 0 · **medium 3** · low 4. 판정 대상
`91eafb78`. **D-6F5-14 는 닫혔다**(r1·r2 의 우회 넷 전부 RED 재현). 승인 전 처분을 여기서 정한다.

**r3 가 답한 구조적 물음 — 이 slice 의 가장 값진 산출이다.** 팀장이 물은 「위치 술어를 한 칸씩
넓히는 것이 수렴인가 발산인가」에 verifier 가 실측으로 답했다: **수렴이되 0 이 아닌 바닥으로**
수렴한다. 우회 비용 추이는 「게이트에 한 줄 → 형제 파일 추출 → 이웃 패키지 + 관용을 벗어난
전체 한정 좌표」로 단조 상승했다. **발산하는 것은 술어가 아니라 「범위를 한 칸 넓힌다」는 처방**
이다 — 모듈 전체로 넓혀도 커널 모듈 헬퍼로 뚫리고(전건 초록 실측), **위치 술어 사다리에 종점이
없다.** 종점은 위치가 아니라 **타입**이다(D-6F5-20). 그래서 **네 번째 칸을 요구하지 않는다.**

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F5-16**(r3 MEDIUM-b, 산출물 — **이 slice 가 닫는다**) | V13 두 표의 CHECK 를 **개수 축만이 아니라 본문으로** 단언한다. 파일에 이미 선례가 있다 — 상태 열거는 `shouldBe` 로 정확히 고정(`outbox_state_check`·`edit_session_state_check` 관례), 결합식은 본문 대조(COL-06·H-3 관례). 닫힘은 **변이로만** 판정한다: CHECK 하나를 `CHECK (TRUE)` 로 약화 · 상태 열거에 세 번째 값 추가 → 둘 다 RED | 이 slice 가 더한 CHECK 는 일곱인데 `CleanMigrationCheckTest` 의 축8 은 **테이블별 개수**만 받았다. 실측: 셋을 `CHECK (TRUE)` 로 바꿔도, 세 번째 status 값을 더해도 **전건 초록**. **이 slice 가 세 라운드에 걸쳐 닫아 온 바로 그 결함 클래스**(있는지만 보는 단언)가 마이그레이션 축에 그대로 남아 있었다. 그 파일의 자기 KDoc 이 이미 이 한계를 적어 두었고 이웃 두 표는 본문 단언을 받았는데 **V13 두 표만 못 받았다.** 파일이 **in_scope 안**이라 계약 확장 없이 닫힌다 |
| **D-6F5-17**(r3 MEDIUM-a, 등재 + 이관) | `LicenseVerdict$` 마커가 도메인이 KDoc 으로 규율한 **소진 `when`** 관용구를 어댑터에서 금지한다는 사실을 알려진 제한에 등재하고 **`OPEN-6F5-EXTRACTION-FILL` 의 확인 항목**으로 6F-5-b 에 넘긴다. **마커를 넓히지 않는다** | 오늘 그 경로의 소비자는 **0**이라 무해하다. 넓히는 것은 **이 slice 가 세 라운드에 걸쳐 닫은 방향을 되돌리는 일**이다 — 소비 관용구를 허용하는 순간 「호출을 남긴 채 결과만 갈아치우기」가 다시 열린다. 부딪히는 것은 6F-5-b 의 저장 경로이고, **그 slice 가 실제 소비자를 만들 때** 마커와 관용구 중 무엇을 굽힐지 결정하는 것이 옳다(D-6F5-12 와 같은 형태의 이관) |
| **D-6F5-18**(r3 MEDIUM-2, **범위를 확장하지 않는다** → OPEN) | 남은 우회 하나(`adapters.persistence` 에 헬퍼를 두고 **전체 한정 좌표**로 부르기)는 `PersistenceAdapterDependencyTest` 가 **소스 텍스트 import 정규식**이라 통과한다. 처방은 범위가 아니라 **술어 종류**(바이트코드 상수 풀)이고, **이 slice 는 그것을 고치지 않는다** — `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 으로 하네스 레인에 넘긴다 | ① 그 파일은 **in_scope 밖**이다 ② 그 패키지는 **6F-4 가 지금 편집 중**(`Sql.kt`)이라 술어 종류를 바꾸면 **남의 레인을 붉힐 수 있다** ③ 평범한 import 형태로 옮기면 그 게이트가 **이미 RED** 를 낸다(verifier 실측) — 전건 초록이 되는 것은 **관용을 벗어난 전체 한정 좌표 하나**뿐이라 HIGH 가 아니라 MEDIUM 이다. **우회 비용이 「평범한 리팩터링」을 넘어섰다**는 것이 D-6F5-14 와의 차이다 |
| **D-6F5-19**(r3 LOW-4 **수용**) | 「`m3/3h` 심판 기록에 OPEN 을 신설하지 않는다」(계약 갱신 (2) 의 팀장 처분)는 **절반만 맞았다.** 기록 자체는 못 고쳐도 **다음 심판의 인용 관례**는 닫을 수 있는 결정이다 — `OPEN-CODEX-RECORD-COORDINATES` 를 신설해 하네스 레인에 넘긴다. 이 slice 가 그 좌표를 **+12** 민다는 수치도 등재한다 | 같은 라운드에 범위 밖 부채 **둘**(`OPEN-CPD-GATE-RENAME-BYPASS`·`OPEN-MIGRATION-ORDER-GATE`)은 OPEN 으로 보내 놓고 이것만 「고칠 수 없으니 OPEN 이 아니다」로 처분한 것은 **일관되지 않는다.** 「고칠 수 없다」는 **기록**에 대한 참이고, 「닫을 수 있다」는 **관례**에 대한 참이다 — 둘을 섞었다 |
| **D-6F5-20**(구조적 종점 등재 — 이 slice 밖) | 위치 술어의 종점은 **타입**이다: `LicenseVerdict` subtype 생성자를 `internal` + `@ConsistentCopyVisibility` 로 내리면 어댑터가 어디에 있든 verdict 를 지어낼 수 없다. `OPEN-VERDICT-CONSTRUCTION-VISIBILITY` 로 **도메인 레인**에 넘긴다 | verifier 타당성 실측: main 소스의 생성 지점은 **커널 둘뿐**이고 밖은 전부 읽기만 한다 — 깨지는 것은 **test 조립뿐**이다. 「불가능한 상태는 타입으로 닫는다」의 이 저장소 판례와 같은 형태이고, **`object` 커널·가시성 축**(M4/4B-2 계보)과도 같은 자리다. 도메인 커널 변경이라 **이 slice 의 in_scope 밖**이고, 여기서 하면 계약을 넓히는 일이 된다 |

**장부층 넷은 verifier 가 전부 사실 확인했다**(+8 독립 재측 · evidence 축어 좌표 0 · `m3/3h`
낡음이 이 slice 이전이라는 것 · MEDIUM-1 정정 문면이 r2 실측과 일치). 남은 것은 D-6F5-19 의
등재뿐이다.

## 계약 갱신 (4) — verifier r4 (2026-09-19, 팀장)

verifier r4 **`ready-for-review`** — 산출물 blocker 0 · high 0 · **medium 1** · low 2 · 장부층 medium 1.
판정 대상 `a86029bb`. D-6F5-16 의 변이 셋이 전부 RED 이고 개수 축은 초록 그대로라 **새 단언이 단독으로
잡는다**. 남은 MEDIUM 하나는 **이 slice 에서 같은 결함 클래스의 네 번째**라 닫는다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F5-21**(r4 MEDIUM, 산출물 — **이 slice 가 닫는다**) | `notice_requirement_row` 의 CHECK 본문을 `any { contains }` 존재 단언이 아니라 **집합 등식**(`shouldBe`)으로 고정한다. 개수·존재·본문·소속이 **한 단언**으로 닫힌다. 닫힘은 변이로만: 항등식 하나를 `… OR TRUE` 로 제자리 약화 → RED | **또 존재 단언이다**(D-6F5-10·D-6F5-14·D-6F5-16 에 이은 **네 번째**). verifier 실측: `CHECK (((kind='PARSED') = (source_field IS NOT NULL)) OR TRUE)` 로 바꾸면 제약이 **항진명제**가 되는데 부분 문자열도 개수도 불변이라 **전건 `check` exit 0**. 나머지 둘이 막히는 것은 술어의 힘이 아니라 **Postgres 의 중첩 `OR` 평탄화** 덕이다 — 즉 **우연이다**. 현재 상태는 「7/7 개수만」이 아니라 **「3/7 완전 고정 + 2/7 우연 보호 + 2/7 열림」**이고, D-6F5-16 KDoc 이 적은 근거(「항등식이라 부분 대조로 충분」)는 **측정으로 반증된다**. **이것은 「범위를 한 칸 넓히기」가 아니라 종점이다**(D-6F5-20 의 교훈 적용) — 집합 등식은 **어떤 제자리 편집도 집합을 바꾸므로** 열거를 늘리지 않고 구조로 닫는다 |
| **D-6F5-22**(범위 밖 부채 → OPEN) | 같은 파일의 **COL-06·H-3 단언 셋**이 동일한 `any { contains }` 형태라 **같은 약점을 공유한다**. 이 slice 는 그 셋을 고치지 않고 `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` 로 넘긴다. r4 LOW-2(`queryConstraintDef` 가 결과 없음을 안 보고 불명확한 예외로 죽는다 — 기존 헬퍼)도 **같은 파일·같은 축**이라 이 OPEN 이 함께 받는다 | 이 slice 는 **집안 관례를 따랐을 뿐**이고 그 관례의 약점이 이번에 처음 측정됐다 — 남의 표를 고치는 것은 범위 확장이다(D-6F5-18 과 같은 판단). **OPEN 으로 보내는 것이 일관된다** — D-6F5-19 에서 「범위 밖 부채 일부만 OPEN 으로 보내는 것은 일관되지 않는다」를 이미 수용했다 |
| **D-6F5-23**(장부층 MEDIUM 의 **구조적 원인**) | `rollback.md` 의 **공유 문서 절(§③)은 「마지막 팀장 커밋 뒤」에 재산출한다.** 이번 라운드에 §② 는 재산출됐는데 §③ 만 낡아, 완전 원복 범위가 `3a233938~1..ddefc3c8`(팀장 커밋 **둘**)로 남았다 — 실제로는 **셋**이고 `51614deb`(milestone 등재)가 빠진다 | **팀장 레인이 공유 문서를 커밋할 때마다 구현 레인의 §③ 이 낡는다.** 구현 레인은 자기 커밋 뒤에 재산출하므로 그 뒤에 오는 팀장 커밋을 구조적으로 못 본다 — 순서가 원인이지 부주의가 아니다. **이 저장소가 「되돌리기 목록 낡음」으로 세 번 겪은 그 형태**이고, 주 절차는 `milestone-6.md` 를 대상에서 빼므로 ①~⑥ 재현에는 영향이 없다(그래서 라운드를 막지 않는다) |

**LOW 처분** — r4 LOW-1(`checklist.md` 의 OPEN ID 둘이 **하이픈 줄바꿈**이라 `grep` 에 안 잡힌다): **고친다.**
등재가 기계 검색으로 보이지 않으면 등재 확인 자체가 서지 않는다(verifier 의 첫 스캔이 오판할 뻔했다).
r4 LOW-2 는 위 D-6F5-22 가 받는다.

## 계약 갱신 (5) — verifier r5 (2026-09-19, 팀장)

verifier r5 **`ready-for-review`** — 산출물 blocker 0 · high 0 · **medium 0 · low 0**. 판정 대상
`fc258399`. D-6F5-21 이 닫혔고(제자리 약화 둘·삭제·의미 동일 재서술 전부 RED, 무관 컬럼 추가는
GREEN), **집합 등식이 반대 방향으로도 안전하다**는 것이 실측됐다 — 넓이가 정확히 표 하나이고
(`conrelid = ?::regclass`), 열거 test 와의 이중 피복은 모순이 아니라 **이중 트립와이어**다(열거를
바꾸면 두 test 가 **모두** 붉어져 한쪽만 갱신한 채 조용히 공존할 수 없다). 남은 것은 장부층 low
하나이고 **그것은 팀장 레인의 몫**이다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F5-24**(r5 장부층 low, **팀장 레인 규율**) | **OPEN 을 신설하는 계약 갱신 커밋과 `milestone-6.md` 등재 커밋을 같은 라운드에 낸다.** 이번에 `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS` 가 `scope.md`·`checklist.md` 에는 있는데 `milestone-6.md` 에만 없었다 — 계약 갱신 (4) 에서 신설해 놓고 milestone 등재를 다음 라운드로 미룬 탓이다 | **D-6F5-23 과 같은 구조**의 두 번째 발현이다(r1 LOW-3 이 첫 번째). 원인은 부주의가 아니라 **위상**이다 — 구현 레인이 OPEN 을 등재하는 라운드와 팀장 레인이 milestone 에 옮기는 커밋이 어긋난다. 처방은 검사가 아니라 **순서**다: 계약 갱신에서 OPEN 을 신설하면 **그 자리에서** milestone 등재까지 낸다. 세 문서(`scope`·`checklist`·`milestone`)가 **항상 같은 라운드에** 움직인다 |
| **D-6F5-25**(r5 참고 수용 — 의도된 비용 명문화) | 집합 등식이 울리는 세 경우를 **「깨짐」이 아니라 「의도된 트립와이어」** 로 계약에 적는다 — (가) 그 표에 **CHECK 가 붙은 컬럼**을 더할 때 (나) 제약을 **의미 동일하게 다시 쓸** 때 (다) **Postgres 메이저 상향**으로 `pg_get_constraintdef()` 렌더링이 바뀔 때. 유지 비용은 **축어 2 → 11(고유 9)** 이고 (가)는 이제 `CleanMigrationCheckTest` 두 자리 + `CleanMigrationColumnTest` 를 함께 고쳐야 한다 | **스키마 스냅샷 래칫의 관례 그대로**이고 `outbox_state_check` 가 이미 지불하는 대가와 같은 종류다. 비용을 적어 두지 않으면 **다음 slice 가 「게이트가 깨졌다」로 읽고 단언을 느슨하게 만든다** — 이 저장소가 존재 단언으로 네 번 뚫린 뒤에 얻은 자리를 그렇게 잃는 것이 가장 흔한 경로다. (다)는 새 의존이 아니라 **기존 의존의 확대**다(`postgres:16.4` 단일 상수, 기존 두 단언이 이미 같은 렌더링에 의존) |

## 계약 갱신 (6) — V12 병합 통합 (2026-09-19, 팀장)

운영자가 **「병합하고, M6 잔여 진행해」**로 지시해 병합 순서 규율(D-6F5-8)이 뒤집혔다. PR #38(6F-6, V12)이
먼저 `main` 에 들어갔고(`edeaa9a3`), 그 결과 이 브랜치가 충돌해 **병합 커밋 `8ae5014f`** 로 흡수했다.
`main` 을 브랜치로 merge 했다(rebase 아님 — 6F-1 에서 rebase 가 상대 레인 줄을 지우는 것을 실측했다).

**충돌 7 hunk / 5 파일은 전부 「양쪽이 각자 더한 것」이었고 대체 자리는 없었다**(구현 레인 확인, 팀장 재확인).
남의 줄이 사라지지 않았음을 개수로 쟀다 — 게이트 등재 `adapters.profile` 3 · `adapters.qualification` 4,
일곱 test class 전부 exit 0, 마이그레이션 개수 축 표에 `operator_profile`·`notice_requirement`·
`notice_requirement_row` 전부, TRUNCATE 목록에 두 표 다.

| ID | 결정 | 근거 |
| --- | --- | --- |
| **D-6F5-26**(병합이 만든 새 파일 둘을 in_scope 에 등재) | `adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationPrivilegeTest.kt`(축9 유효 권한 행렬 분리) · `adapters/src/main/kotlin/bidvector/adapters/qualification/RequirementSql.kt`(이 slice 몫 SQL 상수 여섯 이전)을 in_scope 에 더한다. **둘 다 `sizeGate` 가 만든 것이지 기능 변경이 아니다** | **두 정당한 추가의 합이 한계를 넘겼다** — ① `CleanMigrationTest.kt` 484(이 브랜치) + main 466 → **501줄**(한도 500) ② `Sql.kt` 타입 멤버 이 브랜치 28 + main 25(base 22) → **31개**(한도 30, `OPEN-ADR-06 (a)`). 각자는 한도 안이었다. **한계 상향은 하지 않는다** — 게이트를 느슨하게 하는 방향이고 이 slice 가 다섯 라운드에 걸쳐 한 일과 정반대다. 둘 다 **이 저장소의 기존 전례**를 따랐다: 이 파일이 같은 이유로 축7(`CleanMigrationTriggerTest`)·축8(`CleanMigrationCheckTest`)을 이미 떼어냈고, `bidvector.adapters.event.EventSql` 이 같은 이유로 outbox·inbox SQL 을 `Sql` 밖에 두고 있다. **6F-6 의 `PROFILE_*` 상수와 그 호출부는 손대지 않았다**(팀장 실측: `Sql.kt` 의 main 대비 diff 는 KDoc 한 블록뿐) |
| **D-6F5-27**(`queryStrings` 는 위임이 아니라 **사본**이다) | 분리된 파일이 `queryStrings` 헬퍼를 **사본으로** 갖는다. D-6F5-11 이 CPD 회피를 위임으로 고친 것과 **반대 방향으로 보이지만 같은 원칙**이다 | D-6F5-11 의 요지는 「중복 게이트를 문면으로 피했고 **위임이 가능했다**」였다. 여기서는 **가시성이 위임을 막는다** — `PersistenceTestSupport.dataSource()` 가 `protected` 라 클래스 경계를 넘는 공유가 성립하지 않는다. 이 패키지의 기존 관례도 이미 사본이다(`CleanMigrationCheckTest` 의 `queryConstraintDef`·`queryCheckBodies`). **`cpdCheck` exit 0** 으로 중복 게이트가 이 형태를 중복으로 보지 않음을 실측했다. 「회피가 필요하지 않았는가」라는 D-6F5-11 의 물음에 여기서는 **필요했다**가 답이다 |
| **D-6F5-28**(**rollback 의 모양이 바뀌었다** — 비대칭 하나) | 병합 뒤 이 slice 만의 rollback 은 새 파일 둘을 **다르게** 처분한다. `RequirementSql.kt` 는 **이 slice 자신의 상수만** 담으므로 삭제 대상이다(+ `Sql.kt` KDoc 한 블록 되돌림). 그러나 `CleanMigrationPrivilegeTest.kt` 는 **이 slice 이전부터 있던 축9** 를 옮겨 담은 것이라 **삭제하면 안 되고 `CleanMigrationTest.kt` 로 되돌려 넣어야** 한다 | `rollback.md` 의 「신규 8 삭제」 목록은 **병합 전 브랜치 기준**이고 그 실측(`실측 HEAD d8e37fa3`, 트리 동일성 6/6)은 그 시점에 대해 여전히 참이다. 병합 뒤 형태는 그것과 다르다 — **범위가 이 slice 하나가 아니게 됐기 때문**이다(6F-6 이 끼어 있다). 이 사실을 `rollback.md` 에 절로 적고, **재실측은 하지 않는다**(되돌림 대상이 두 slice 에 걸치면 그것은 이 slice 의 rollback 이 아니라 두 병합의 되돌림이고, 그 절차는 `main` 의 병합 커밋 revert 다) |

**구현 레인의 자기신고를 사실로 등재한다**(이력을 되쓰지 않는다) — 병합 커밋 메시지에 MUT-S1~3 결과를
**먼저 적고 실측을 그 뒤에** 했다. 셋 다 적은 그대로 나왔고 구현 레인이 순서 역전을 스스로 밝혔다. **결과가
맞았어도 순서가 틀렸다** — 이 저장소는 6F-6 에서 「측정 전에 초록을 보고한 것」이 BLOCKER 였던 실측을 갖고
있다. 다음 라운드부터 실측이 먼저다.

## 하네스 레인 변경 (상시 절)

- (착수 시점) 없음.

## 병행 레인

| 레인 | 상태 | 겹침 |
| --- | --- | --- |
| `m6-6f4/2026-09-18`(다른 세션) | 진행 중 | V11 · `strategy/**`(이 slice 무편집) · `Sql.kt`·`gate-tests.properties`·`CleanMigration*Test`·`milestone-6.md`(**둘 다 추가만**) |
| `m6-6f6/2026-09-18`(PR #38, 열림) | 판정 완료·병합 대기 | V12 · **`OperatorProfilePort` 실 구현**(이 slice 는 port 로만 받아 **브랜치 무의존**) · 같은 공유 파일들 |
| `m6-6a/2026-09-17`(6A-1) | 정지 | V10 선점 · app 파일(무편집) |

## OPEN 항목

| ID | 처분 |
| --- | --- |
| `OPEN-6F5-EXTRACTION-FILL`(신설) | 요건을 **채우는 경로**(추출 호출·저장) — **6F-5-b**. 실제 LLM 호출은 **운영자 승인** 대상이고 6F-4 선행. 추출 시점(감시 통과 시점 vs 별도 job)과 무효화 정책이 그 slice 의 결정이다 |
| `OPEN-6F5A-RETENTION`(신설) | 요건 표의 수명 정책 없음 — 받는 쪽 **6B-3** |
| `OPEN-6F-ASSEMBLY` | 변경 없음 — 어댑터를 만들 뿐 꽂지 않는다 |
| `OPEN-GATE-REGISTRATION-STALE-INPUT` | 변경 없음(6F-6 이 등재) — 이 slice 의 신설 완결성 게이트도 **같은 한계를 물려받는다**. 그 사실을 알려진 제한에 적는다 |
| `OPEN-MIGRATION-ORDER-GATE`(신설, D-6F5-13) | 마이그레이션 **번호 순서 병합 규율에 자동 게이트가 없다** — 세 slice 가 연쇄로 수동 규율에 의존한다. 받는 쪽 **하네스 레인**, 실 배선 전 |
| `OPEN-CPD-GATE-RENAME-BYPASS`(신설, 범위 밖 부채) | **CPD 중복 게이트가 이름 치환 하나로 열린다**(변수명만 바꿔도 통과, 판정식만 바꿔도 통과 — verifier 실측). 이 slice 밖 기존 부채. 받는 쪽 **하네스 레인** |
| `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE`(신설, D-6F5-18) | `PersistenceAdapterDependencyTest` 가 **소스 텍스트 import 정규식**이라 전체 한정 좌표 참조를 못 본다 — 바이트코드 상수 풀 술어로 바꿔야 한다. 그 파일은 이 slice 의 in_scope 밖이고 **6F-4 가 그 패키지를 편집 중**이라 여기서 건드리지 않는다. 받는 쪽 **하네스 레인** |
| `OPEN-VERDICT-CONSTRUCTION-VISIBILITY`(신설, D-6F5-20) | **위치 술어의 종점은 타입이다** — `LicenseVerdict` subtype 생성자를 `internal` + `@ConsistentCopyVisibility` 로 내리면 어댑터가 어디에 있든 verdict 를 지어낼 수 없다. 실측상 깨지는 것은 test 조립뿐(main 생성 지점은 커널 둘). 도메인 커널 변경이라 받는 쪽 **도메인 레인** |
| `OPEN-CODEX-RECORD-COORDINATES`(신설, D-6F5-19) | Codex 심판 기록(append-only, 수정 금지)이 `file:line` 좌표를 인용해 **원리적으로 낡는다**. 기록은 못 고쳐도 **다음 심판의 인용 관례**(심볼 단위로 요구)는 닫을 수 있다. 받는 쪽 **하네스 레인** |
| `OPEN-CHECK-BODY-PRESENCE-ASSERTIONS`(신설, D-6F5-22) | `CleanMigrationCheckTest` 의 **COL-06·H-3 단언 셋**이 `any { contains }` 존재 단언이라 CHECK 본문을 제자리 약화해도 통과한다(이 slice 의 표에서 실측). `queryConstraintDef` 가 결과 없음을 안 보는 것도 같은 파일·같은 축. 이 slice 밖 기존 부채 — 받는 쪽 **하네스 레인** |
