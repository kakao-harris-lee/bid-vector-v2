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
