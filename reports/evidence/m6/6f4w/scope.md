# M6/6F-4-w — 관심대상 포트 배선 (2026-09-23)

`WatchSubjectPort` 의 **첫 production 구현**을 낸다. 6F-4 가 감시 텍스트의 **데이터**(V14 `notice_title`,
`strategy` 의 조립 함수 둘)를 냈지만 **그것을 부르는 자가 없다** — 이 slice 가 그 호출부다.

- base: 이 브랜치가 분기해 나온 **현재** `main` — `git merge-base HEAD origin/main` 으로 산출한다.
  **고정 SHA 로 적지 않는다**(D-6F5-30 의 종점 — 옆 slice 가 먼저 병합되면 고정 SHA 기준 절차가 남의
  산출물을 지운다). 착수 시점 실측값은 `02164e44`(PR #42 머지 커밋)이고, 라운드마다 재산출한다.
- 선행: M6/6F-7 병합 완료(`02164e44`). 이 레인은 그 뒤의 `main` 에서 분기했다.
- worktree `bid-vector-v2-m6f4w`, 브랜치 `m6-6f4w/2026-09-23`.

## 착수 조사가 확정한 것 — 테이블 신설은 없다

조사(읽기 전용) 결론:

- `WatchSubjectPort` 의 production 구현은 **0**이다. 주입 자리 둘, test fake 둘뿐.
- 조립에 필요한 열은 **`notice` 에 이미 다 있다** — `notice_title`(V14) · `business_category_label`(V1) ·
  `demand_agency_name`·`notice_agency_name`(V7) · `base_amount_*`(V1).
- 그 열을 읽는 어댑터도 **이미 있다** — `CandidateSourcePort` 가 내주는 `Notice` 에 `title`·
  `businessCategory`·`agency` 둘·`baseAmount` 가 전부 실려 온다.
- **→ 마이그레이션 신설 없음. DB 재조회 없음.** 이 slice 는 `Notice` → `WatchSubject` **순수 매퍼**다.

## 계약 고정 결정

**D-6F4W-1 — 범위를 「포트 절반」으로 가른다. `OPEN-6F4-TITLE-WIRING` 은 둘을 한 이름으로 묶고 있었다.**
그 OPEN 이 안고 있던 것은 축이 다른 둘이다 — ⓐ `WatchSubjectPort` 구현(도메인 조립) ⓑ KONEPS 원시 키
(`bidNtceNm`/`ntceNm`) → canonical `notice_title` 배선(수집). **이 slice 는 ⓐ 만 닫는다.**
ⓑ 는 **`OPEN-6F4-TITLE-INGEST`** 로 신설해 수집 레인에 넘기고, **D-6F4-6 의 제약**(공고명 키를 어댑터에
하드코딩하지 않는다 — 정책 데이터가 키를 나르고 매퍼가 그것을 읽는다)을 **그 OPEN 이 그대로 안고 간다.**
사유: 둘은 게이트도 위험도 다르고, 다음 두 단계(6A-3+6F-3, 조립)를 막는 것은 ⓐ 뿐이다. ⓑ 는 **배선이 아니라
값**에만 영향을 준다.

**D-6F4W-2 — 「값이 없다」는 `Found` 다. `Unavailable` 은 「조립에 실패했다」다.**
port KDoc 이 `Unavailable` 을 「원문 텍스트 **조립 실패**」로 정의했고, `Text.kt` 는 D-6F4-4b 로 「값이
없으면 없는 것이다」(빈 문자열)를 이미 정했다. 둘을 합치면 **부재 → `Found`(빈 텍스트), 실패 → `Unavailable`**.
반대로 정하면(부재 → `Unavailable`) `WatchSubjectOutcome.Unavailable` → `halt(ScoreNotProvided)` →
`EvaluationDropReason.WatchSubjectUnavailable` 경로를 타서 **공고가 평가에서 탈락**한다. 지금
`notice_title` 의 실 데이터가 **0건**이므로 그 선택은 **전건 탈락**을 뜻한다 — 도메인 규칙이 아니라 사고다.

**D-6F4W-3 — 그래서 `Unavailable` 을 이 어댑터에서 「표현 불가능」으로 닫는다.**
순수 매퍼는 I/O 가 없어 **실패할 수 없다.** **어댑터의 매퍼 함수**(`strategy` 의 `assemble*` 가 아니다 —
그쪽은 ADR 0006 D-4 로 `procurement` 타입을 못 받아 원문 `String?` 을 받는다. 그 변환을 하는 것이
어댑터다)의 반환형을 `WatchSubjectOutcome` 이 아니라 **`WatchSubject` 로 두어 전(total) 함수**로 만들고,
포트 구현이 그것을 `Found` 로 감싼다. 그러면 「이 어댑터가 `Unavailable` 을 낸다」가 **test 로 막을 것이
아니라 타입으로 없는 것**이 된다(수정이 예외를 옮기기만 하면 뿌리는 표현 가능한 불가능 상태다).
**알려진 제한으로 등재**: 이로써 `Unavailable` variant 의 production 생산자가 **하나도 없다**. 변이 실측이
그 경로를 못 잡는다 — 도메인 쪽 `OpportunityAnalysis` test 가 fake 로 덮고 있다는 사실을 evidence 에 적는다.

**D-6F4W-4 — 부재한 기초금액의 사유 코드는 `EMPTY_INPUT` 이다.**
`ResolvedBaseAmount?` 가 `null` 이면 `Fact.Absent(EMPTY_INPUT)`. 후보를 전수 검토했다:
`POLICY_NOT_APPLICABLE`(정책이 적용되지 않는다 — 공고가 값을 안 실은 것과 다르다. **test fixture 가 이것을
쓰고 있으나 fixture 는 승인 문서가 아니다**) · `UNIT_NOT_DECLARED`(단위 미선언 — 값은 있는데 단위가 없는
경우) · `AMOUNT_OVERFLOW`·`VAT_TREATMENT_MISMATCH`·`ROUNDING_NOT_REPRESENTABLE`·`NEGATIVE_AMOUNT`·
`UNDECLARED_PROVENANCE`·`ROUNDED_BELOW_FLOOR`(전부 **값이 있을 때의 실패**) 는 다른 사유다.
`EMPTY_INPUT`(입력이 비었다)만이 「공고가 기초금액을 싣지 않았다」와 맞는다.
**새 enum 값을 만들지 않는다** — `ReasonCode` 는 shared-kernel 이라 값 추가가 모든 소진 `when` 에 파급한다.
나중에 「출처가 안 줬다」와 「받았는데 비었다」를 갈라야 하면 그때 신설 OPEN 으로 올린다.

**D-6F4W-5 — 어댑터는 `bidvector.adapters.evaluation` 에 넣는다.**
6F-2 의 선례(D-6F2-8: port 가 사는 루트와 같은 이름의 패키지). 그 패키지가 이미 있어 **의존 게이트
(`EvaluationAdapterDependencyTest`, 바이트코드 상수 풀)와 등재 게이트(`EvaluationGateRegistrationTest`)가
이미 서 있다** — 신설 게이트가 아니라 **기존 게이트의 덮개 안**으로 들어간다.

**D-6F4W-6 — Spring 빈 등록은 하지 않는다.**
`JdbcCandidateSource`·`StoredRequirementLicenseGate`·`JdbcOperatorProfileRepository` 중 빈으로 꽂힌
것은 하나도 없다. 조립은 `OPEN-6F-ASSEMBLY`(4번 단계)의 몫이고 1~3 이 전제다. **선례를 따른다.**

**D-6F4W-7 — 감시 텍스트 두 타입의 생성 경계를 닫는다. 단, 측정 뒤 갈린다.**
6F-4 checklist 제한 5 가 이 slice 에 명시로 넘긴 숙제다 — `KeywordScopeText`·`FullScopeText` 가 공개
생성자 `data class` 라 **조립 함수를 우회할 수 있고, verifier r2 가 실측했다**(요건 텍스트로 직접 만들어
필수 키워드를 만족시키는 test 가 초록이었다). 경계는 **`NoticeTitle` 과 같은 기전**:
`@ConsistentCopyVisibility` + `private constructor` + `companion object` 팩토리. `copy()` 까지 덮는다.

- **`internal` 로 때우지 않는다** — `internal` 은 모듈 범위라 `strategy/src/test` 에서 그대로 열린다
  (M3 교훈). 폐쇄 확인은 grep 이 아니라 **negative 컴파일 probe**(기존 `compile-fixtures` 형태)로 건다.
- **분기점**: 생성 지점이 `strategy/src/main` 밖 **54곳**으로 측정됐다. 구현 레인은 **먼저 그 54를
  main/test × 모듈로 갈라 보고**한다. 전부 test 면 이 slice 에서 닫는다. **`strategy` 밖 main 코드에
  생성 지점이 있으면 그 사실을 보고하고 멈춘다** — 이 slice 를 부풀리지 않고 별도 slice 로 가른다.
- 순서: **포트 매퍼를 먼저 끝내고**(6A-3·조립을 푸는 산출물) 그 다음 경계 폐쇄에 손댄다.

## 계약 갱신 (1) — 생성 경계 폐쇄의 부수 물음 (2026-09-23, 팀장)

**0단계 측정 결과: 생성 지점은 전부 test 다. main 0건.** 멈춤 조건에 해당하지 않으므로 이 slice 에서
닫는다. 구현 레인 실측(occurrence 단위): `strategy` test 24 · `workflow` test 21 · `adapters` test 6 ·
`app` test 4 = **55**. 착수 계약이 적은 「54」는 조사 레인의 라인 단위 셈이었다 — **구현 레인의 표가 정본**
이고, 결론(전부 test, main 0)은 두 셈이 같다.

**D-6F4W-8 — 팩토리로 만들 수 없는 test 입력은 「도달 가능한가」로 처분한다.**
경계를 닫으면 기존 test 중 **팩토리를 거쳐서는 만들 수 없는 입력**을 쓰는 것이 나온다. 개별 판단에
맡기지 않는다. 둘 중 하나가 참이다:

- **ⓐ production 에서 도달 가능하다** → 팩토리로 **그 값이 나오는 입력**을 찾아 그걸로 만든다. **우선**한다 —
  도달 경로를 실제로 보이는 것이 test 의 가치를 올린다.
- **ⓑ production 에서 도달 불가능하다** → **그 케이스를 지우고 사유를 evidence 에 남긴다.** 도달 불가능한
  입력에 대해 거동을 잠그는 test 는 **지킬 것이 없는 계약**이다.

**하지 않는 것 셋** — ① **test 전용 뒷문**(`@VisibleForTesting` 생성자·`internal` 탈출구·test 전용 팩토리)을
만들지 않는다. **우리가 닫으려는 그 우회를 다시 여는 것**이다 ② **팩토리를 느슨하게 만들어** test 를
통과시키지 않는다. 넓혀야 할 진짜 이유가 있으면 그건 ⓐ이고 **도메인 근거**로 적어야 한다 — 「test 가
필요로 해서」는 근거가 아니다 ③ **단언을 약화**시키지 않는다. 어려우면 지우거나 묻는다.

**`compile-fixtures` 는 다르다** — 일부러 생성을 하는 fixture 는 test 데이터가 아니라 **게이트의 입력**이다.
폐쇄 뒤 「컴파일 실패」를 기대하도록 **부호를 뒤집는 것**이 맞을 수 있다. 각 fixture 가 무엇을 증명하려는
것인지 먼저 읽고 판단한다.

**보고 항목**: 이관 건별로 ⓐ/ⓑ 중 어느 쪽으로 처분했는지 **집계**(파일별 건수 + ⓑ로 지운 것의 사유).

**D-6F4W-9 — 「빈 텍스트」와 「공백 텍스트」가 다른 상태로 드러나면 그것은 도메인 발견이다. 보고 대상.**
구현 레인이 `FullScopeText("   ")`(공백 보존)를 재는 기존 test 를 발견했다. **조립 함수가 공백만 있는
문자열을 낼 수 있는가**가 물음이다 — 필드를 구분자로 이을 때 일부 필드가 비면 **구분자만 남을 수 있다.**
낼 수 있다면 그 값은 production 에서 실제로 생기고, D-6F4W-2(부재 → `Found`(빈 텍스트))가 **덮지 않은
축**이다. 감시 규칙이 둘을 같게 볼지 다르게 볼지는 **팀장이 정한다** — 구현 레인이 test 안에서 정하지
않는다. 낼 수 없다면 D-6F4W-8 ⓑ다.

## 계약 갱신 (2) — `in_scope` 확장과 착수 계약의 결함 둘 (2026-09-23, 팀장)

**2단계 착수에서 드러났다**: `private constructor` 로 닫으면 직접 생성 **43곳이 즉시 컴파일 불가**가 되고,
`check` 는 **하나의 컴파일 단위**라 부분 폐쇄로는 빌드가 아예 안 돈다. 그중 **10곳이 `in_scope` 밖**이었다.

### 착수 계약의 결함 둘 — 내가 쓴 것이다

1. **글롭이 틀렸다.** `adapters/src/test/resources/compile-fixtures/**` 로 적었으나 이 slice 의 대상은
   **`strategy/src/test/resources/compile-fixtures/**`** 다(`CompileFailureHarnessTest` 가 그쪽을 돈다.
   adapters 쪽은 event envelope 용이라 무관). **정정한다.**
2. **분기점을 잘못 걸었다.** D-6F4W-7 의 멈춤 조건을 「main 코드에 생성 지점이 있으면」으로 적었는데
   **진짜 제약은 `in_scope` 덮개**였다. main 0건이어도 **test 가 계약 밖이면 같은 문제**다. 구현 레인이
   그 자리에서 멈춘 것이 옳다 — 조건의 문면이 아니라 **취지**(계약 밖을 만지게 되면 멈춘다)를 읽었다.

### D-6F4W-10 — `in_scope` 를 넷 넓히고 경계 폐쇄를 이 slice 에서 끝낸다

넓히는 경로: `adapters/.../extraction/**` · `adapters/.../ml/**` · `app/.../conformance/**` ·
`strategy/src/test/resources/compile-fixtures/**`(글롭 정정).

**가르지 않는 이유**: 가르면 **표면이 줄지 않는다** — 새 slice 도 같은 10곳을 만져야 하고 `in_scope` 문제도
따라간다. 줄어드는 것 없이 계약·evidence·리뷰 한 바퀴가 **더해질** 뿐이다. 그리고 닫는 대상이 **실측된
구멍**이다(6F-4 verifier r2 MEDIUM-3 — 요건 텍스트로 직접 만들어 **필수 키워드를 만족시키는 test 가
초록이었다**). 이관은 기계적이고 거동 동일이 확인됐다. **미루면 구멍이 그만큼 더 열려 있는다.**

**레인 충돌 실측**: 세 파일의 마지막 변경은 전부 **병합된 옛 slice** 다(M3/3C · M4/4B-3 · M3/3A).
활성 worktree 는 이 레인 하나, 로컬 브랜치는 `main` 과 이 브랜치뿐, 원격은 전부 병합·정지분.

### D-6F4W-11 — 이관에 거는 조건 셋

1. **`.value` 치환을 기본으로 삼지 않는다.** `assemble*` 경유가 **우선**이다(D-6F4W-8 ⓐ — 도달 경로를
   증명한다). `.value` 비교는 **그 test 의 주제가 문자열 내용 자체일 때만** 쓰고 **건수를 따로 센다** —
   타입 동등성을 문자열 동등성으로 낮추는 것이므로 무의식적으로 번지면 안 된다.
2. **D-6F4W-8 ⓐ/ⓑ 건별 집계**를 남긴다(파일별 건수 + ⓑ 사유 + `.value` 건수).
3. **`compile-fixtures` 는 부호를 먼저 읽는다.** `positive-2-keyword-scope-own-text` 는 「자기 모듈
   텍스트로는 만들 수 있다」를 증명하는 것으로 보이는데 **폐쇄하면 그 명제가 거짓이 된다.**
   `negative-2-keyword-scope-cross-text` 의 「남의 모듈 텍스트로는 못 만든다」는 이제
   **「아무도 못 만든다」의 부분집합**이라 **이름이 가리키는 것을 더 이상 재지 않을 수 있다.**
   셋 각각에 대해 **폐쇄 뒤에도 고유한 무언가를 재는가**를 답한다. 기계적으로 뒤집지 않는다.

**rollback 재산출** — `in_scope` 가 넓어졌으므로 목록을 다시 기계 산출한다.

## 계약 갱신 (3) — D-6F4W-9 의 답과 그것이 드러낸 것 (2026-09-23, 팀장)

**D-6F4W-9 닫힘 — 도메인 발견 아님.** 구현 레인 실측: 조립 함수가 조각을 잇기 전에 **blank 조각을 전부
거른다**(`filter(String::isNotBlank)`). 그래서 출력은 **`""` 이거나 비공백 문자를 반드시 포함**한다 —
「빈 텍스트」와 「공백 텍스트」가 갈리는 축이 **assemble 출력에는 없다.** 「공백 3자」 case 는 D-6F4W-8 ⓑ
(팩토리로 도달 불가 → 지우고 사유 기록)로 처분한다. 다만 **「긴 공백 + 비공백 1자」는 ⓐ 다** — 단일
비공백 조각은 구분자 없이 그대로 나오므로 팩토리로 정확히 재현된다.

**D-6F4W-12 — 그 불변식을 test 로 잠근다. 지금은 구현의 성질일 뿐 계약이 아니다.**
「출력은 `""` 이거나 비공백 문자를 포함한다」는 술어 한 글자(`isNotBlank` → `isNotEmpty`)로 **조용히
깨진다.** 그리고 **D-6F4W-2 가 그것에 기대고 있다** — 「부재 → `Found`(빈 텍스트)」가 뜻을 가지려면
**「빈」이 정확히 `""` 하나**여야 한다. 깨지면 부재가 `""` 와 공백 문자열 **두 모양으로 갈리고**, 하류에서
`isEmpty()` 로 보는 자리가 **후자를 놓친다.**

- **속성 test** 로 잠근다 — 전건 열거가 아니라 **경계 조합**(전부 blank · 일부 blank · 전부 비blank ·
  빈 문자열 섞임 · 공백 낀 비blank).
- **변이로 확인한다**: 술어를 `isNotEmpty` 로 바꾸면 **RED 여야 한다.** 변이가 실제 적용됐는지
  `git diff --numstat` 으로 먼저 보고 잰다. RED 가 안 되면 그 test 는 잠그는 것이 아니다.
- 신설 게이트이므로 **`gate-tests.properties` 등재**한다.

## 계약 갱신 (4) — 검토 라운드 1 판정과 팀장 결정 (2026-09-23, 팀장)

판정 SHA `1f34858d` 에서 검토 레인 셋이 판정했다 — verifier **not-ready**(HIGH 1 · MEDIUM 2 · LOW 1 · 장부 5),
code-reviewer(sonnet) **머지 가능**(MEDIUM 1 · LOW 1), privacy-gate **위반 0**(권고 2 · 확인 불가 1). 재작업
**1/5**. 리포트는 레인 worktree `_workspace/m6-6f4w/1{0,1,2}_*.md`(gitignore) — 판정은 PR 코멘트로 옮긴다.

**D-6F4W-13 — 컴파일 probe 의 기대 조각은 가시성을 구별해야 한다(HIGH-1).** `CompileFailureHarnessTest` 의
negative 4~7 이 단언하는 조각 `"cannot access"` 는 `private` 과 `internal` 을 구별하지 못한다 — verifier 가
`Text.kt` 의 두 생성자를 `internal` 로 퇴행시켜도 `check` 전체가 초록임을 실측했다. D-6F4W-7 이 「`internal`
로 때우지 않는다」를 명시했는데 그 probe 가 바로 그 퇴행을 통과시킨다. **기대 조각을 `"it is private in"` 으로
좁힌다**(negative·mutant 4~7 모두). negative-3(`Score.of`, 계약이 `internal`)은 그대로 둔다. 게이트 술어
변경이므로 severity 와 무관하게 **표적 재검증** 대상 — `internal` 퇴행 변이가 RED 인 것을 evidence 에 명령으로 남긴다.

**D-6F4W-14 — 우회 3 의 부재 단언은 열거가 아니라 허용 목록이다(MEDIUM-2).** `EvaluationAdapterDependencyTest`
의 「이어붙이기 기계 부재」가 이름 셋(`StringBuilder`·`makeConcatWithConstants`·`joinToString`)의 금지 목록이라
`java.lang.String.join` 으로 조립을 복제한 변이가 부재 단언·커널 참조 단언·거동 test 8건을 전부 통과했다(오늘은
값이 같아서 거동 test 도 못 잡는다). **어댑터 두 class(`NoticeWatchSubjectPort`·`NoticeWatchSubjectPortKt`)의
상수 풀 `Methodref`/`InterfaceMethodref` 집합이 허용 목록의 부분집합**이도록 바꾼다 — 허용은 `assemble*` 둘 ·
`Notice`/값 객체 getter · `WatchSubject.<init>` · `Fact.*` · `CategoryCode.<init>` · `setOf`/`orEmpty`류 stdlib ·
`Intrinsics` 형태로, **손 목록이되 새 이름은 무엇이든 RED** 가 된다. 양성 대조는 기존 `ConcatenationMachineryFixture`
에 `String.join` 변형을 더해 허용 목록이 그것을 잡음을 보인다. 게이트 술어 변경 — 표적 재검증 대상(m4b 변이 RED).

**D-6F4W-15 — MEDIUM-1 처분: 호출자 한정 게이트는 이 slice 가 세우지 않는다. 문면을 고치고 OPEN 으로 넘긴다.**
verifier 실측: `assemble*` 의 인자가 `String?` 이라 `assembleKeywordScopeText(<요건 텍스트>, null)` 이 그대로
컴파일되고 필수 키워드를 거짓 만족시킨다 — 6F-4 r2 시나리오가 **철자만 바꿔 재현**된다. 그래서 `Text.kt` KDoc 의
「요건·기관명이 이 값에 실릴 길이 타입으로 없다」·「그 배제는 이 함수 하나로 닫힌다」와 checklist 의 「요건·기관명을
keywordText 에 실을 길은 여전히 없다」는 **사실과 다르다**(privacy-gate R-2 도 같은 지적). 폐쇄가 실제로 닫은 것은
**직접 생성 · `copy()` · 출력 정규화(D-6F4W-12)** 이고, 닫지 못한 것은 **내용의 출처** — 그것은 위협 모델이 처음부터
경계 밖에 둔 축(「원천 값이 맞는가」)이며 어댑터의 책임이다. 문면을 그렇게 고친다.
- **게이트를 지금 세우지 않는 이유**: 오늘 production 호출자는 `NoticeWatchSubjectPortKt` 하나이고(`grep` 실측 —
  **구조가 아니라 실측이다**, 그 사실을 알려진 제한에 그대로 적는다), 새 호출자는 `OPEN-6F-ASSEMBLY` 배선에서만
  생긴다. 게이트의 자리는 app 의 ArchUnit 층(`ArchitectureRules`, 전 모듈 production 클래스를 본다)인데 그러면
  in_scope 를 **네 번째**로 넓히고 `app/**` 를 열어야 한다 — 이미 게이트 술어 둘을 바꾸는 라운드에 얹지 않는다.
- **`OPEN-6F4W-ASSEMBLE-CALLER` 신설** — 「`assembleKeywordScopeText`·`assembleFullScopeText` 를 부르는 production
  클래스 집합 == {`bidvector.adapters.evaluation.NoticeWatchSubjectPortKt`}」를 ArchUnit 규칙(집합 등식)으로 잠근다.
  **`OPEN-6F-ASSEMBLY` 의 전제 조건**이다 — 배선이 호출자를 더하기 전에 닫혀야 「어댑터만 부른다」가 희망이 아니라
  구조가 된다.
- code-reviewer MEDIUM(「구조적으로 못 닫는다」 과장)도 같은 자리의 문면이다: `of` 를 `private` 으로 두면 **현재
  형태(top-level `assemble*`)에서는 컴파일되지 않는다**(verifier 실측)가 참이고, `procurement.NoticeTitle` 처럼
  `assemble*` 를 companion 멤버로 옮기면 `internal of` 자체가 사라진다 — 호출부 7파일(import 기준) 갱신 비용 대신 택한 **설계
  선택**이지 Kotlin 의 한계가 아니다. 그렇게 적는다. `of` 는 `assemble*` 와 시그니처·본문이 같아 새 구멍이 아니다.

**D-6F4W-16 — 장부층·low 일괄(한 커밋), 승인 전.**
- verifier LOW-1: D-6F4W-12 의 Arb 가 ASCII 공백뿐이라 `trim(' ')` 변이가 산다 — `"\t"`·`"\u3000"`(전각, 한국어
  공고명에서 현실적)·`"\u00A0"` 을 더한다.
- code-reviewer LOW: `noticeToWatchSubject` 는 유일한 호출자가 같은 파일이므로 `private` 으로 좁힌다(top-level
  `private` 은 파일 범위). (2b) 표의 가시성 행을 갱신한다.
- verifier L-2: (2b) 표의 `of` 행 — 「모듈 밖 호출 불가」의 근거는 fixture 4 가 아니라 `internal` 가시성 자체다
  (`of` 를 부르는 fixture 는 없다). 귀속을 고친다.
- L-3: rollback.md 의 A/M 개수 표제(현재 A 16 · M 16)를 재산출값으로. 실측 HEAD 는 이 라운드의 마지막 산출물
  커밋으로 옮기고 임시 clone ①~⑥ 을 다시 돈다.
- L-4: D-6F4W-11 조건 3 의 답을 셋 각각으로 적는다(verifier ③ 의 답을 그대로 옮겨도 된다 — `positive-2` 의
  「own text」는 「자기 모듈」이 아니라 「자기 타입 자리」다). `WatchRulesTest` 표 행과 헬퍼 KDoc 의 ⓐ/ⓑ 를 맞춘다.
- L-5: `out_of_scope` 의 `app/**` 는 `app/src/main/**` 다(이 갱신에서 팀장이 고쳤다).
- L-6: `one-command-check.sh` 는 이 장비에서 **exit 0**(verifier 실측, `uv` 있음). 구현 레인이 같은 장비에서
  재실행해 commands.md 를 갱신하고 checklist 의 「S-1 알려진 제한」을 「구현 레인 원래 장비의 네트워크 제약이었다」로
  고친다.
- privacy-gate R-1: 두 타입과 `WatchSubject` 의 합성 `toString()` 이 공고명·공종·기관명 원문을 낸다(오늘 그 값을
  문자열에 싣는 main 경로 없음) — 알려진 제한 한 줄 + `OPEN-6F-ASSEMBLY` 재확인 항목.
- privacy-gate U-1: 배선 전이라 확인 불가한 셋(평가 use case 의 예외·에러 응답에 subject 적재 · 임베딩 gRPC 실패
  시 요청 텍스트 로깅 · ml-engine 의 요청 텍스트 보관)을 `OPEN-6F-ASSEMBLY` 재확인 항목으로 아래 표에 등재한다.
- verifier 참고 R-1(`strategy` 에 gate-tests 등재 게이트가 없다)은 기존 구조라 이 slice 의 finding 이 아니다 —
  checklist 「확인하지 않은 것」에 사실로만 적는다.

**이 라운드의 보고 항목** — (2b) 표 갱신(새 public 표면이 생겼는가 — 예상 0, `private` 축소만) · 수정 라운드가
만든 새 파일 ↔ in_scope 대조 · 게이트 술어 변경 둘의 변이 RED 명령.

## 위협 모델 — 6F-4-w 고유 경계

이 slice 가 지키려는 것: **감시 판정의 입력 텍스트는 승인된 조립 함수만 만든다.** 조립을 우회해 만든
텍스트가 감시 판정에 들어가면, 필수 키워드·제외 키워드 같은 **운영자의 감시 규칙이 무력화**된다
(verifier r2 가 이미 실측한 경로다).

경계 밖: 그 텍스트의 **원천 값이 맞는가**(수집 정확성 — `OPEN-6F4-TITLE-INGEST`) · **감시 규칙 자체**
(`operator_strategy`, 6F-1 소관) · **포트를 app 에 꽂는 일**(`OPEN-6F-ASSEMBLY`).

### 우회 — 일곱

1. **조립 함수를 안 부르고 두 타입을 직접 생성한다.** ← D-6F4W-7 (private 생성자 + 컴파일 probe)
2. **`copy()` 로 조립 결과를 갈아끼운다.** ← `@ConsistentCopyVisibility`
3. **어댑터가 커널을 안 부르고 값을 직접 조립한다**(문자열 이어붙이기를 어댑터에 복제).
   ← `StoredRequirementLicenseGate` 의 본(「커널 호출만 하고 값을 직접 조립하지 않는다」)을 따르고,
   **어댑터 안에 구분자·이어붙이기 상수가 없음**을 단언한다(**상수 풀 부재 단언** — 존재 단언은 못 막는다).
4. **`Unavailable` 을 부재에 쓴다**(전건 탈락). ← D-6F4W-3 (타입으로 표현 불가능)
5. **`Fact.Absent` 의 사유를 아무 값으로 준다.** ← D-6F4W-4 + 거동 test 로 잠근다
6. **어댑터가 DB 를 다시 본다**(`Notice` 가 이미 나르는 값을 재조회 — N+1 과 불일치의 씨앗).
   ← 의존 게이트 allow-list 에 persistence/JDBC 루트를 넣지 않는다
7. **신설 게이트를 `gate-tests.properties` 에 안 올린다**(등재 안 된 게이트는 안 도는 게이트).
   ← 기존 `EvaluationGateRegistrationTest` 가 덮는다 — **덮는지 변이로 확인한다**

### (2b) 값 획득 축 — 새 public 표면 전수

구현 레인이 **신설 public 표면을 하나도 빠짐없이 표로** 내고, 각 행에 「이 표면으로 감시 텍스트 값을
만들거나 바꿀 수 있는가」를 **실측**으로 적는다. 「경계로 처리」 행도 실측한다(문장으로 때우지 않는다).
`object`/`companion` 커널 주입 자리도 행으로 센다. **수정 라운드마다 갱신한다.**

## in_scope

```yaml
in_scope:
  - adapters/src/main/kotlin/bidvector/adapters/evaluation/**   # 어댑터 신설 (D-6F4W-5)
  - adapters/src/test/kotlin/bidvector/adapters/evaluation/**   # 조립 test · 의존/등재 게이트 갱신
  - strategy/src/test/resources/compile-fixtures/**             # 생성 경계 probe — 계약 갱신 (2) 로 정정
  - adapters/src/test/kotlin/bidvector/adapters/extraction/**   # 계약 갱신 (2) — 생성 지점 이관
  - adapters/src/test/kotlin/bidvector/adapters/ml/**           # 계약 갱신 (2) — 생성 지점 이관
  - app/src/test/kotlin/bidvector/app/conformance/**            # 계약 갱신 (2) — 생성 지점 이관
  - strategy/src/main/kotlin/bidvector/strategy/Text.kt         # 두 타입 생성 경계 폐쇄 (D-6F4W-7)
  - strategy/src/test/kotlin/bidvector/strategy/**              # 폐쇄로 깨지는 생성 지점 이관
  - workflow/src/test/kotlin/bidvector/workflow/evaluation/**   # test fake 의 생성 지점 이관
  - config/quality/gate-tests.properties                        # 신설 게이트 등재(추가만) — 공유 파일
  - reports/evidence/m6/6f4w/**
  - milestone-6.md                                              # 착수·종결 문단(팀장 커밋) — 공유 파일
out_of_scope:
  - adapters/src/main/resources/db/migration/**                 # 마이그레이션 신설 없음 (착수 조사)
  - app/src/main/**                                             # Spring 등록 안 함 (D-6F4W-6) — 계약 갱신 (4) L-5 정정
  - 수집→canonical 매퍼                                          # OPEN-6F4-TITLE-INGEST (D-6F4W-1)
```

**주의 — `strategy/src/main` 밖 생성 지점 54곳.** D-6F4W-7 의 측정 결과에 따라 `in_scope` 가 **넓어질 수
있다**. 넓어지면 **계약 갱신 절로 명시하고 `rollback.md` 를 재산출**한다 — 6A-1(D-6A1-26)·6F-7(HIGH-1)에서
**두 번 연속** 「파일이 움직였는데 `in_scope` 를 안 고쳐 clean-tree 게이트가 눈이 멀었다」가 났다.
**세 번째를 만들지 않는다.**

## acceptance

CI 워크플로 job 의 명령 그대로(`.github/workflows/ci.yml` — 구현 레인이 착수 시 원문을 대조해
`commands.md` 에 옮긴다). Kotlin `check` job 전체. **부분 게이트는 안 돌린 것과 같다.**

- **버릴 clone 에서 돌린다.**
- **캐시 우회로 한 번 잰다** — 6F-7 r2 실측: `check` 가 exit 0 이어도 **해당 모듈의 test task 가
  `FROM-CACHE`** 일 수 있다(그때는 `:workflow:test`, 그 안에 산출물 test 가 있었다). `--rerun-tasks` 로
  실제 실행 수를 확인한다. 「`:app:test` 가 `UP-TO-DATE`」로 좁혀 기억하지 않는다 — **어느 모듈이든
  캐시로 대체될 수 있다**는 것이 교훈이다.

## rollback

`in_scope` 경로 한정 `git restore --source=<base> --staged --worktree --`.
`<base>` 는 **고정 SHA 가 아니라** `git merge-base HEAD origin/main` 로 산출한다(D-6F5-30).
목록은 `git diff --name-status <base>..HEAD` **기계 산출**, 라운드마다 재산출.
공유 파일 둘(`config/quality/gate-tests.properties`·`milestone-6.md`)은 **커밋 해시 hunk 격리**
(`git diff <sha>~1..<sha> -- <파일> | git apply -R`) — `--3way` 도 자동 해소에 실패하므로 수동 절차를
미리 적는다. 확인은 **「내 줄 사라짐」과 「남의 줄 남음」 둘 다.**
실측은 **그 slice 의 마지막 산출물 커밋**에서 돌고 `실측 HEAD: <sha>` 를 적는다. 유효성 술어는
`git diff --name-only <실측 HEAD>..<판정 SHA> -- <되돌리는 경로들>` 이 **빈 출력**인가이다(2026-09-19 정정 —
「실측 HEAD == 판정 SHA」가 아니다).

## 하네스 레인 변경 (상시 절)

착수 시점 없음. 생기면 여기에 적는다.

## OPEN — 수령·신설

| ID | 방향 | 내용 |
|---|---|---|
| `OPEN-6F4-TITLE-WIRING` | **수령·절반 닫음** | ⓐ 포트 구현 = 이 slice. ⓑ 수집 배선 = 신설로 가름 |
| `OPEN-6F4-TITLE-INGEST` | **신설** | KONEPS 원시 키 → canonical `notice_title`. **D-6F4-6 제약을 안고 간다** |
| 6F-4 제한 5 (D-6F4-3c) | **수령** | 두 타입 생성 경계 폐쇄 — D-6F4W-7 |
| `OPEN-6F-ASSEMBLY` | **넘김** | 포트를 app 에 꽂기(4번 단계). 이 slice 는 받지 않는다. **재확인 항목(계약 갱신 (4))**: ① 평가 use case 호출부가 예외·에러 응답에 `WatchSubject`/`Notice` 를 싣는가 ② 임베딩 gRPC 실패 시 요청 텍스트가 로그에 남는가 ③ ml-engine 이 운영에서 요청 텍스트를 보관하는가 ④ 두 타입·`WatchSubject` 합성 `toString()` 이 진단 출력에 실리는 경로가 생기는가 |
| `OPEN-6F4W-ASSEMBLE-CALLER` | **신설** | `assemble*` production 호출자 집합 == {`NoticeWatchSubjectPortKt`} 를 app ArchUnit 층에서 집합 등식으로 잠근다(D-6F4W-15). **`OPEN-6F-ASSEMBLY` 의 전제** |
| `OPEN-6F4W-UNAVAILABLE-PRODUCER` | **신설** | D-6F4W-3 의 대가 — `Unavailable` 의 production 생산자가 없다 |

## 확인하지 않은 것 (착수 시점)

- `notice_title` 의 **실 데이터는 0건**이다(6F-4 실측: 적용 DB 인스턴스 0 · 수집→canonical 배선 0 ·
  fixture 표본 0). 즉 이 slice 가 배선을 끝내도 **감시 텍스트는 당분간 빈 문자열**이다. 그것은 결함이
  아니라 `OPEN-6F4-TITLE-INGEST` 의 몫이며, **evidence 에 사실로 적는다.**
- 「빈 텍스트로 감시 규칙을 돌리면 필수 키워드가 전건 불일치」 — 그 거동이 옳은지는 **감시 규칙 쪽 결정**
  이고 이 slice 가 정하지 않는다. 다만 **그 거동이 일어난다는 사실**을 알려진 제한에 적는다.

## 리뷰 레인

`verifier`(opus) + `code-reviewer`(**호출 시 `model: sonnet` 명시**, 저자와 다른 패스) 병렬.
**Codex 는 이 slice 의 대상이 아니다** — 되돌리기 어려운 경로(결제·인증/인가·암호화·마이그레이션·데이터
파기)도 계약 파일도 아니다. 마이그레이션 신설이 없으므로 `migration-reviewer` 도 해당 없음.
`privacy-gate` 는 공고명·기관명이 텍스트로 조립돼 로그·outbox 에 닿을 수 있으므로 **붙인다.**
종결은 **`verifier ready-for-review` + 사용자 승인**으로 읽는다.
