# M6/G1-SET01 checklist — 상호 매칭 커널 (SET-01)

## 0. 설계 메모 (착수, 팀장 회신 전 — TDD 는 이 메모대로 진행한다)

계약 `scope.md` 산출물 1~5 에 대한 구체 형태다. 계약 전제가 틀렸다고 판단한 자리는 없다.

### 0.1 정규화 토큰 목록의 legacy 출처

> **식별자를 축어로 적지 않는 이유**: legacy 상수와 V2 정책 필드의 이름에 비밀값 스캔 어휘와
> 겹치는 조각(`…_TOK…`)이 들어 있어, evidence 에 그대로 적으면 `leakPatternGate` 가 붉다(레인
> 규율 — false positive 설명으로라도 축어로 적지 않고 **부류로만** 적는다). 좌표(`파일:줄`)와
> 「`AwardMatchingPolicyData` 의 유일한 필드」라는 지시가 그 자리를 모호함 없이 가리킨다.

정본은 legacy `bid-vector/app/services/award_verification.py:77-89` 의 **법인 표기 상수 하나**
(그 파일의 마지막 커밋 `3447952d`, 2026-08-07) 열하나다 — 법인 접두·접미 표기
(주식회사 계열 다섯 · 괄호/전각 괄호/합자 기호 여섯). **값과 순서를 그대로** 옮기고 출처
문자열을 정책의 `source` 에 싣는다. 목록의 충분성은 판정하지 않는다(`OPEN-SET-03`, 실데이터
확인 대기 — M3 3B/3D 수집 뒤로 등재돼 있다).

**정규화 단계의 순서는 legacy 가 정한다**: ① 공백 제거(`re.sub(r"\s+", "", …)`, `:102`)
→ ② 토큰 제거(`:105`) → ③ `casefold()`(`:107`). capability map 문면(「접두·접미 토큰 제거 +
공백 제거 + casefold」)은 **열거**이고 순서를 주장하지 않는데, 순서가 거동을 가른다 —
「주식 회사 가상해양기술」이 토큰 제거에 닿으려면 공백이 **먼저** 사라져야 한다. 그래서 문면이 아니라
legacy 순서를 구현하고, 그 사실을 test 사례로 고정한다(공백이 낀 법인 표기).

두 옮김 함정:

- **`\s` 를 공백 넷 리터럴로 옮기지 않는다.** Python `str` 정규식의 `\s` 는 유니코드 공백
  전체(전각 공백·NBSP 등)를 덮는다. Kotlin `Char.isWhitespace()` 가 정확히 그 집합이라는
  실측은 이 저장소에 이미 있다(`qualification/…/LicensePolicy.kt` 의 `_KEY_NOISE_RE` 주석) —
  그 선례를 인용해 재실측 비용을 쓰지 않는다. `java.util.Locale` 은 domain 의 금지 표면
  (`architecture-policy.properties` `effect.surface.classes`)이라 참조하지 않는다.
- **`casefold()` 와 `lowercase()` 는 같지 않다**(`ß` → `ss` vs `ß`). 한글·ASCII 상호에서는
  일치하고 Kotlin 에 casefold 대응이 없다 — **알려진 제한**으로 적고 `OPEN-SET-03` 옆에 둔다.

### 0.2 결과 sealed 계층 (4-state)

```
AwardOutcome                                  (sealed interface)
├─ SettledAwardOutcome                        (sealed interface — 확정 = 기록 대상)
│  ├─ Won                                     낙찰(같은 회사)
│  └─ Lost                                    패찰(다른 상호)
├─ NotRecorded(reason: WinnerNameAbsentReason)    결과 쪽 상호 없음 — `false` 가 아니다
└─ NotJudged(reason: OperatorNameAbsentReason)    운영자 상호 미설정 — 판정 자체를 하지 않는다
```

- 사유도 sealed 다(`LicenseVerdict`/`UncertainReason` 관례): `NotPublished`·`NormalizesToEmpty`
  (결과 쪽) / `NotDeclared`·`NormalizesToEmpty`(운영자 쪽). 「상호가 `(주)` 뿐이라 정규화 결과가
  빈 문자열」은 legacy 가 조용히 `None` 으로 접던 자리라 **사유를 가진 상태**로 세운다.
- **접는 API 를 두지 않는다** — `isWon(): Boolean`·`orElse` 류 없음. 소비자는 소진 `when` 만
  쓴다(`LicenseVerdict` 의 명시 규율).
- 「확정」을 **주석이 아니라 타입**으로 둔다(`SettledAwardOutcome`). 2단 sealed 가 형태 게이트에
  안전함은 실측으로 확인했다 — `TypeShape.interfaceCount()` 는 `rawInterfaces.size`(**직접**
  인터페이스)라 각 타입 1 이고, `inheritanceDepth` 는 superclass 체인만 세므로 인터페이스
  계층은 0 이다(`ratchet.type.interfaces.max=1` · `inheritance-depth.max=0` 둘 다 충족).

### 0.3 「외부 조회 술어」의 형태 — 티켓 타입

술어를 `Boolean` 으로 두면 호출부가 무시할 수 있다. **술어의 산출을 판정 함수의 입력 타입으로**
만들어 우회를 컴파일 단계에서 없앤다.

```kotlin
sealed interface AwardLookupDecision {
    data class Lookup(val operator: NormalizedCompanyName) : AwardLookupDecision
    data class Skip(val reason: OperatorNameAbsentReason) : AwardLookupDecision
}

fun decideAwardLookup(operator: OperatorTradeName, policy: AwardMatchingPolicyData): AwardLookupDecision

fun judgeAwardOutcome(
    lookup: AwardLookupDecision,
    winner: AwardWinnerTradeName,
    policy: AwardMatchingPolicyData,
): AwardOutcome
```

두 가지가 동시에 닫힌다 — (a) 판정에 닿으려면 술어를 **지나야** 하고(첫 인자 타입이 술어의
산출), (b) 조회에 필요한 정규화 상호를 **`Lookup` 만이 나른다**(미설정이면 그 값이 존재하지
않으므로 조회 호출을 조립할 수 없다). 실제 조회 경로는 이 slice 에 없다(계약 산출물 4).
`Skip` 이 들어오면 판정은 `NotJudged(그 사유)` 다 — 운영자 미설정 검사가 **결과 쪽보다 먼저**
온다(legacy 순서, `:116-121`).

### 0.4 불변 표현 — 「한 번만 기록되고 덮이지 않는다」

```kotlin
sealed interface RecordedAwardOutcome {
    data class Settled(val outcome: SettledAwardOutcome) : RecordedAwardOutcome
    data object Absent : RecordedAwardOutcome
}

sealed interface AwardOutcomeRecording {
    data class Recorded(val outcome: SettledAwardOutcome) : AwardOutcomeRecording
    data class AlreadySettled(val kept: SettledAwardOutcome) : AwardOutcomeRecording
    data class NotSettled(val outcome: AwardOutcome) : AwardOutcomeRecording
}

fun recordAwardOutcome(existing: RecordedAwardOutcome, judged: AwardOutcome): AwardOutcomeRecording
```

- `existing` 이 `Settled` 면 `judged` 가 무엇이든 `AlreadySettled(existing.outcome)` — **기존
  값을 그대로 돌려준다**(새 값을 나르는 자리가 없으므로 호출부가 덮어쓸 값을 얻지 못한다).
- 기록 대상 타입이 `SettledAwardOutcome` 이라 **「미기록」·「판정 안 함」이 기록으로 승격되는
  경로가 타입으로 없다**. legacy 의 `outcome is not None and record.award_outcome is None`
  (`award_notifications.py:186-191`) 두 조건이 각각 셋째·둘째 갈래다.
- 영속화·시각 축은 두지 않는다(계약 산출물 4 — 소비 slice 몫). `Instant` 를 커널에 끌어오지
  않는다.

### 0.5 PII (P-10 (a) · `COMPANY_IDENTITY_CONCEPTS`)

- 원문 상호를 나르는 세 타입(`OperatorTradeName.Declared` · `AwardWinnerTradeName.Published` ·
  `NormalizedCompanyName`)은 **`toString()` 을 가린다**. data class 의 생성 `toString` 은
  로그·예외 메시지·단언 실패 출력으로 실값을 내보내는 기본 경로다. `equals`/`hashCode` 는
  그대로 둔다 — 판정이 그 위에 선다.
- `NormalizedCompanyName` 은 private 생성자 + 모듈 내 factory(`@ConsistentCopyVisibility`,
  `KeywordScopeText` 관례) — 「정규화를 거치지 않은 문자열을 정규화된 것처럼」 만드는 경로가
  모듈 **밖에서 컴파일되지 않는다**.
- 사업자등록번호·대표자명 슬롯을 **만들지 않는다**(P-10 (a) — 계약 미등재가 차단의 실물).
- test·evidence 의 상호는 전부 가상이다(실 상호·사업자번호 0).

### 0.6 산출물 파일과 test

| main | 내용 |
|---|---|
| `settlement/…/AwardMatchingPolicyData.kt` | 정책 data class + 출하 `AWARD_MATCHING_POLICY`(`EffectiveDatedPolicy`) |
| `settlement/…/CompanyName.kt` | `NormalizedCompanyName` · `normalizeCompanyName` · 입력 상태 둘 |
| `settlement/…/AwardOutcome.kt` | 결과·사유·조회 결정·기록 결과 sealed |
| `settlement/…/AwardMatchingKernel.kt` | `decideAwardLookup` · `judgeAwardOutcome` · `recordAwardOutcome` |

`ModuleBoundaryAnchor.kt` 를 지운다(ADR 0006 D-2 — 실제 타입이 들어오면 지운다는 그 주석의 조건).
`settlement/build.gradle.kts` 에 `implementation(project(":shared-kernel"))`(정책 version 축)과
`testImplementation(libs.kotlin.compilerEmbeddable)`(컴파일 실패 하네스)를 더한다.

test 다섯(전부 `config/quality/gate-tests.properties` 의 새 `gate.tests.settlement` 에 등재):
`CompanyNameNormalizationTest` · `AwardMatchingPolicyDataTest` · `AwardMatchingKernelTest` ·
`AwardOutcomeRecordingTest` · `CompileFailureHarnessTest`.

### 0.7 변이 (계약 (2) 우회 ≥5)

| # | 바꿔치기 | 기대 RED |
|---|---|---|
| M-1 | 정규화에서 공백 제거 단계 제거 | 공백이 낀 법인 표기 흡수 |
| M-2 | 정규화에서 토큰 제거 단계 제거 | 토큰별 사례 전부 |
| M-3 | 정규화에서 casefold 단계 제거 | 대소문자 차이 흡수 |
| M-4 | `NotRecorded` 를 `Lost` 로 접기 | 「상호 없음 = 미기록」 |
| M-5 | 미설정을 `Lost` 로 | 「미설정 = 판정 안 함」 |
| M-6 | `decideAwardLookup` 이 미설정에도 `Lookup` 을 돌려준다 | 조회 술어 거짓 단언 |
| M-7 | `recordAwardOutcome` 이 기존 확정을 덮는다 | 덮어쓰기 불가 |
| M-8 | 토큰 목록을 정책 인자 대신 코드 상수로 | 주입 정책 test |
| M-9 | 판정 시그니처에 금액 인자 추가 | 컴파일 실패 하네스(양성 쌍둥이 + 오타 변이 통제군) |

### 0.8 ⓐ/ⓒ 예상 — **ⓒ, 집계 증감 0**

추적표 술어 ②(출하 배선·출하 값에서 성립)를 **지나지 못한다**. 계약 산출물 4 가 지목한 두 빈
자리가 그대로다 —

1. **운영자 상호 필드가 없다**(프로필은 업종·면허·지역 셋). 입력 칸은 7C ④ 이고 이 slice 는
   마이그레이션을 만들지 않는다(마이그레이션 레인은 7B 하나).
2. **낙찰자 상호가 canonical 로 적재되지 않는다**. `bidwinnrNm` 은 낙찰 목록 끝점에만 있고
   6G 는 그 끝점을 걷지 않는다. 결정 블록은 새 외부 호출을 허용하지 않는다.

그래서 acceptance 네 bullet 의 거동은 **커널 수준에서 전부 측정되지만** 운영자가 그것을 보는
배선이 없다 — 빈 bullet 이 넷이므로 「부분 측정(**강함**)」 표식의 술어(빈 bullet ≤ 2)도
충족하지 않는다. 행은 ⓒ 로 두고 본문에 (a) 신설 test FQN 을 **부분 측정 식별자**로, (b) 두 빈
자리와 홀더(7C ④ · 낙찰 목록 수집 축)를 적는다. **집계 머리 숫자는 증감 0** 이며 병렬 레인
g1dec05 와 겹치므로 머지 직전 재계산 대상이다.

의심되면 ⓒ — 이 행은 의심이 아니라 두 빈 자리가 이름으로 확인된다.

## 1. 산출물 — `settlement` 의 첫 실제 타입과 그것이 잰 acceptance

`ModuleBoundaryAnchor.kt` 를 지웠다(그 파일의 주석이 적은 조건 — *"실제 타입이 들어오면 지운다"*).

| 파일 | 무엇 |
|---|---|
| `settlement/src/main/kotlin/bidvector/settlement/AwardMatchingPolicyData.kt` | 법인 표기 목록 정책 + 출하 `AWARD_MATCHING_POLICY`(`EffectiveDatedPolicy`, 출처 문자열이 `PolicyVersion` 에 실린다) |
| `settlement/src/main/kotlin/bidvector/settlement/CompanyName.kt` | `NormalizedCompanyName`(private 생성자 + `@ConsistentCopyVisibility`) · `normalizeCompanyName` · 입력 상태 둘 |
| `settlement/src/main/kotlin/bidvector/settlement/AwardOutcome.kt` | 결과 4-state · 사유 둘 · 조회 관문 · 기록 결과 sealed |
| `settlement/src/main/kotlin/bidvector/settlement/AwardMatchingKernel.kt` | `decideAwardLookup` · `judgeAwardOutcome` · `recordAwardOutcome` |

acceptance 네 줄과 재는 자리:

| acceptance bullet | 재는 자리 |
|---|---|
| 법인 표기 차이를 흡수해 같은 회사로 판정한다 | `CompanyNameNormalizationTest`(목록의 열하나 **각각** + 공백이 낀 표기 + 전각 공백·NBSP + 대소문자) · `AwardMatchingKernelTest` |
| 동일 금액·다른 상호는 패찰이다(금액은 판정에 쓰지 않는다) | `AwardMatchingKernelTest`(상호 불일치 = 패찰) + `CompileFailureHarnessTest` fixture 1(금액 인자를 더한 호출이 **컴파일되지 않는다**) |
| 낙찰가만 있고 낙찰자 상호가 없으면 `false` 가 아니라 미기록 | `AwardMatchingKernelTest`(`NotRecorded` 둘 — 미공개 · 정규화 결과 빈 문자열) |
| 운영자 상호가 미설정이면 판정하지 않고 외부 조회도 하지 않는다 | `AwardMatchingKernelTest`(조회 술어 `Skip` 둘 + 판정 `NotJudged` 둘, 운영자 축이 결과 축보다 먼저) |
| (분류 근거) 판정은 한 번만 기록되고 덮이지 않는다 | `AwardOutcomeRecordingTest`(반대 판정 · 같은 판정 · 확정 뒤 미기록 관측 셋 다 기존 값 유지) |
| (계약) 목록은 코드 상수가 아니라 versioned 정책 데이터 | `AwardMatchingPolicyDataTest` + `CompanyNameNormalizationTest` 의 주입 정책 사례 |

클래스 다섯을 `config/quality/gate-tests.properties` 의 **신설 키** `gate.tests.settlement` 에 등재했다
(`gateRegistrationGate` 의 양방향 등식 — 모집단 ∖ 제외 == 등재). 헬퍼 파일
(`AwardMatchingTestPolicies.kt`)은 test 클래스가 아니라 모집단에 들지 않는다.

**정규화 순서**: legacy 는 ① 공백 제거 → ② 법인 표기 제거 → ③ `casefold()` 다. capability map 문면은
단계의 **열거**이고 순서를 주장하지 않는데 순서가 거동을 가른다(공백이 먼저 사라져야 「주식 회사 …」가
표기 제거에 닿는다). 그래서 문면이 아니라 legacy 순서를 구현하고 그 자체를 test 로 고정했다(M-1 이 그
줄만으로 붉다).

## 2. 변이 실측 — 바꿔치기 · `numstat` · 통제군

변이는 production 속성을 **바꿔치운다**. 적용 직후 `git diff --numstat` 으로 실제 적용을 확인하고,
측정 뒤 **사본에서 복원**한다(`git checkout --` 금지 — 같은 파일의 미커밋 편집까지 지운다). 복원은
매 회차 `git status --porcelain -- settlement` 빈 출력으로 확인했다(표의 마지막 열).

**이 변이들의 소비자 집합은 `:settlement:test` 가 전부다** — `settlement` 밖의 **main 소스 가운데
`bidvector.settlement` 를 참조하는 파일이 0** 이다(`grep -rln 'bidvector\.settlement' --include=*.kt
*/src/main | grep -v '^settlement/'` 빈 출력). `workflow/build.gradle.kts` 가 project 의존을 선언하지만
호출부가 아직 없고, adapters 의 의존 test 들이 드는 `bidvector.settlement` 는 **패키지 이름 문자열**
(허용 목록)이지 타입 참조가 아니다. 그래서 「같은 변이에서 함께 붉은 등재 test」의 모수는 아래 34 다.

**통제군**: 변이 없는 트리에서 `:settlement:test` **34 test · 실패 0**. 각 변이의 복원 뒤에도 같다
(아래 ④⑤⑥ 의 전건 `check` 가 그 복원된 트리를 다시 통과한다).

**이 표는 판정 트리 `4a52c2fd` 에서 다시 쟀다.** 첫 측정은 아키텍처 게이트 수정(`70926fcb`) **전** 트리에서
했고, 그 수정이 `CompanyName.kt` 의 정규화 구현을 바꿨으므로 앞 수치는 판정 트리를 설명하지 못한다 —
**열 전부를 재측정**했고 RED 수는 열 자리 모두 첫 측정과 같았다(구현이 바뀌어도 각 단계가 무엇을 재는지는
같다는 뜻이고, 그 일치를 근거로 쓰지 않고 **재측정 값**을 싣는다).

**rebase 뒤(2026-10-23) 이 표를 다시 재지 않은 근거**: 표는 `4a52c2fd`(rebase 뒤 `5fb6a61e`) 트리에서 쟀다.
그 뒤 판정 트리까지 `settlement/src/main` 의 변경은 **KDoc 한 줄**(`fb6c5872`, §5 P-16)뿐이고, rebase 가
가져온 main 쪽 변경은 `config/quality/vuln-allowlist.properties` 하나다. 바이트코드가 같다는 것은 추측이 아니라
실측이다 — 판정 회차 `check` 에서 `:settlement:compileKotlin` 은 실행됐는데 `:settlement:jar` 가
**UP-TO-DATE**(출력이 앞 실행과 같다)였고 그래서 `:settlement:test` 도 UP-TO-DATE 였다(commands.md §4 회차 3).
변이가 바꿔치우는 줄은 하나도 움직이지 않았다.

| ID | 무엇을 **바꿔치웠나** | numstat | 그 변이에서 RED(34 중) |
|---|---|---|---|
| M-1 | 정규화의 **공백 제거** 단계를 「원문 그대로」로 | `1 1` | **6** — 공백 낀 표기 흡수 · 전각 공백/NBSP · 대소문자(공백 포함 사례) · 정규화 결과 빈 문자열 · 법인 표기 흡수 낙찰 · 조회 관문의 정규화 값 |
| M-2 | 법인 표기 **제거를 자기 자신으로 치환**(단계 무력화) | `1 1` | **11** — 목록 열하나 각각 · 주입 정책 사례 · 미기록/`Skip` 의 「정규화 결과 빈 문자열」 사유 둘 포함 |
| M-3 | **소문자화** 단계를 「원문 그대로」로 | `1 1` | **1** — 대소문자 흡수만(한글 사례는 영향 없음 — 그 좁음이 정확하다) |
| M-4 | 결과 쪽 상호 **부재(미기록)를 패찰로** 접는다 | `1 1` | **1** — 「공개되지 않으면 패찰이 아니라 미기록」 |
| M-5 | 운영자 **미설정(판정 안 함)을 패찰로** 접는다 | `1 1` | **2** — 미설정 + 둘 다 없음(운영자 축 우선) |
| M-6 | **조회 술어가 미설정에도 `Lookup`** 을 돌려준다 | `1 1` | **3** — 조회 술어 `Skip` 단언 + 판정 둘 |
| M-7 | 기록 시 **기존 확정 대신 새 판정 값**을 든다 | `1 1` | **1** — 반대 판정 덮어쓰기만(같은 판정·미기록 관측은 값이 같아 초록 — 그래서 M-7b 를 함께 쟀다) |
| M-7b | **불변 가드를 통째로** 바꿔치운다(확정이 있어도 없는 것처럼 기록) | `1 1` | **3** — 기록 불변 test 전건 |
| M-8 | 목록을 **주입 정책 대신 코드 상수**로 | `1 1` | **1** — 「목록은 주입된 정책에서 온다」 |
| M-9 | 판정 시그니처에 **금액 인자**를 더한다(기본값 있는 넷째 인자) | `1 0` | **1** — 컴파일 실패 하네스 fixture 1(음성 fixture 가 **컴파일에 성공**해 버린다) |

**M-9 만 「더하기」인 이유**: *인자의 부재*는 바꿔치울 대상이 없다 — 계약이 「그 자리가 없다」이므로
위반은 더하기로만 표현된다. 그 변이가 초록이 아니라 **붉다**는 것이 이 하네스가 공허하지 않다는 증거다
(기본값을 준 것은 모듈 안 호출부를 살려 **하네스 fixture 만** 판정에 걸리게 하려는 것이다).

**M-7 의 좁음을 숨기지 않는다**: 값만 바꾸는 변이는 불변 test 셋 중 하나만 붉힌다. 가드 자체를 들어낸
M-7b 가 셋 전부를 붉히므로, 「덮어쓰기 불가」의 실제 잠금은 **가드의 존재**이고 세 test 가 그것을 각자
다른 입력으로 잰다.

### 2-1. 컴파일 실패 하네스의 음성 대조

세 fixture 각각에 **양성 쌍둥이**와 **오타 변이**를 두었다. 오타 변이는 컴파일에 실패하되 **기대
진단 조각을 담지 않아야** 한다 — 「컴파일 안 됨」만 보면 어떤 오타든 단언을 만족시키기 때문이다.

| fixture | 음성(기대 진단) | 양성 | 오타 변이 |
|---|---|---|---|
| 1 금액 인자 | `too many arguments for 'fun judgeAwardOutcome(` | 3-인자 호출 | 없는 이름 호출 → `unresolved reference`(기대 조각 없음) |
| 2 정규화 우회 | `it is private in`(**가시성을 구별하는** 조각 — `cannot access` 는 `private`/`internal` 을 못 가른다) | `normalizeCompanyName` 경유 | 없는 이름 생성 |
| 3 `copy()` 우회 | `it is private in` | 같은 양성 | 없는 멤버 호출 |

fixture 의 상호는 전부 가상이고 **ASCII 로만** 적었다 — 컴파일러 소스 인코딩 가정을 두지 않는다.

## 3. 알려진 제한

1. **소문자화가 ASCII `A`~`Z` 만 접는다** — legacy 는 Python `casefold()`(유니코드 전체)다. Kotlin 에
   대응이 없고, 그 자리에 쓰려던 `lowercase()` 는 **domain 허용 목록 밖 표면에 닿아** 쓸 수 없다
   (§4 참조 — 전건 `check` 가 잡았다). 그래서 `qualification` 의 면허명 키와 같은 선택을 했다: 상호는
   한글(대소문자 없음) + 라틴 코드 축이므로 ASCII 만 접는다. **접지 않는 것**: 비-ASCII 라틴 대문자
   (`À`·`Ç` 등) · `ß` → `ss` 류 특수 접힘 · 터키어 `İ` 류 locale 의존 접힘. 실데이터에서 이 차이가
   나타나는지는 `OPEN-SET-03` 과 같은 축(실수집 상호 확인)이다.
2. **목록의 충분성은 판정하지 않는다**(`OPEN-SET-03`). 열하나는 legacy 값 그대로이고, 「일부 법인
   형태가 목록에 없다」는 관찰은 그대로 열려 있다. 이 slice 가 하는 것은 그 목록을 **version 을 가진
   데이터로** 만든 것뿐이다.
3. **재공고(차수 다수) 대사 대상 선택은 범위 밖**(`OPEN-SET-05`, 운영자 결정 대상). 커널은 「어느 결과를
   고를까」를 묻지 않고 **주어진 한 쌍**을 판정한다.
4. **소비자가 없다.** 영속화·수집·endpoint·운영자 상호 입력은 전부 이 slice 밖이고, 그래서 추적표
   판정이 ⓒ 다. `workflow` 가 project 의존을 선언하지만 호출부는 0 이다.
5. **PII 가림의 대가**: 상호 운반 타입의 `toString()` 이 가려져 있어 단언 실패 메시지에 값이 보이지
   않는다. test 는 가상 상호만 쓰므로 받아들인 trade-off 이고, 값 비교가 필요한 자리는 `.value` 로
   읽는다.
6. **`judgeAwardOutcome` 이 정책을 두 번째로 받는 모양**: 관문(`Lookup`)이 운영자 쪽 정규화 결과를
   나르므로 판정 함수는 결과 쪽만 정규화하는데, 그 때문에 정책 인자가 두 함수에 모두 있다. 한 쌍을
   묶는 타입을 두면 인자는 줄지만 관문의 뜻(「조회해도 되는가」)이 흐려져 지금 모양을 택했다.
7. **관문이 닫는 범위**: `Skip` 은 정규화 상호를 나르지 않으므로 그 값으로 조회를 조립할 수 없다.
   운영자 **미설정**(`NotDeclared`)에서는 원문 상호 자체가 없어 우회할 재료가 없다 — acceptance 가
   말하는 자리는 닫힌다. 다만 **설정은 됐으나 정규화 결과가 빈 문자열**인 경우, 호출부가 관문을
   무시하고 `OperatorTradeName.Declared.rawName` 을 직접 들고 조회할 **여지는 남는다**. 오늘은
   호출부가 0 이라 실현된 경로가 아니고, 소비 slice 가 생길 때 그 자리는 **호출부 쪽 test** 가 져야
   한다(커널이 구조로 닫을 수 있는 범위를 넘는다). 이 한계를 적어 두는 이유는 「구조적으로 닫았다」가
   실제보다 넓게 읽히지 않게 하기 위함이다.

## 4. 전건 `check` 가 잡은 것 — 모듈 게이트 전건이 초록인데 붉었다

첫 회차 전건 `check` 는 **실패했다**(exit 1, 10분 10초). `:app:test` 의
`ArchitectureGateTest > 도메인 모듈이 허용 목록 밖을 보지 않는다` 가 659 중 1 실패였고, 위반 둘 다
`normalizeCompanyName` 이었다.

```
calls java.lang.Appendable.append(char)   ← filterNot { it.isWhitespace() }
gets  java.util.Locale.ROOT               ← text.lowercase()
```

**틀린 추론**: 「`java.util.Locale` 이 금지 표면이니 **인자 없는** `lowercase()`(locale 비의존)를 쓰면
된다」고 KDoc 에 적었는데, 인자 없는 형태도 바이트코드에서 `Locale.ROOT` 를 **읽는다**. 소스 수준의
추론이었고 게이트는 바이트코드를 본다.

**왜 모듈 게이트가 못 봤나** — `:settlement:` 의 게이트 열(`typeShape`·`domainApiType`·
`domainSourceReference`·`moduleDependency`·`packageOwnership`·`size`·`testShape`·`gateRegistration`·
`sourceLanguage`·`sourceSetLayout`)은 전부 초록이었다. 도메인 허용 목록을 **ArchUnit 으로** 재는
자리는 `:app:test` 하나뿐이다. 그리고 `domainSourceReferenceGate` 는 **소스가 이름으로 부르는 좌표**를
보는데 여기서 새는 것은 **stdlib 이 뒤에서 부르는 표면**이라 두 게이트의 사각이 서로 반대다 — 앞쪽만
초록인 상태는 아무것도 보장하지 않는다. 「부분 게이트는 안 돌린 것과 같다」의 또 한 번 실측이다.

**처방은 저장소에 이미 있었다.** `qualification/…/LicensePolicy.kt` 의 `stripAndLowercase` 가 같은
위반을 같은 사유로 겪고 주석으로 남겼다(「`filterNot`·`map`·`lowercase()`·`joinToString` 은 전부
`Appendable` 을 거치고 JVM `lowercase()` 는 그 위에 `Locale.ROOT` 까지」). `CharArray` 직접 조립 +
`String(CharArray, Int, Int)` 생성자로 바꿨다. **세 단계를 한 loop 로 합치지 않았다** — 합치면 단계
하나를 따로 변이할 수 없어 M-1·M-3 이 각자 재는 것이 사라진다(`qualification` 은 두 단계를 한 loop 로
합쳤는데, 그 모듈에는 단계별 변이 요구가 없었다).

**교훈(레인에 남길 것)**: domain 모듈에 **문자열을 조립하는 코드**를 새로 쓰면 `:settlement:` 류 모듈
게이트로는 모자라고 `:app:test` 의 ArchUnit 축까지 돌려야 한다. 비용이 싼 표적은
`./gradlew :app:test --tests '*ArchitectureGateTest*'` 다(이 호스트 실측 42초, 커밋 전에 돌릴 만하다).

## 5. 약속 ↔ 출하 대조 — §0 설계 메모(Phase 1)와 계약 `scope.md` 를 출하 코드에 대고

판정 칸 넷: **약속대로** · **선언된 이탈**(evidence 가 이미 이유와 함께 적었다) · **무선언 이탈**(이
대조에서 처음 드러났다) · **약속 자체가 틀림**(약속을 그대로 지키면 다른 규칙을 어긴다). 대조는
출하 소스를 직접 읽어 했다(2026-10-23, 재기동 뒤 레인 — 앞 세션의 기억이 아니라 파일에서).

| # | 약속(출처) | 출하 | 판정 |
|---|---|---|---|
| P-1 | 결과 sealed 4-state, 확정은 타입 `SettledAwardOutcome`(§0.2) | `AwardOutcome.kt` 그대로 — `Won`·`Lost` 가 `SettledAwardOutcome`, `NotRecorded(reason)`·`NotJudged(reason)` | 약속대로 |
| P-2 | 사유도 sealed, 결과 쪽 `NotPublished`·`NormalizesToEmpty` / 운영자 쪽 `NotDeclared`·`NormalizesToEmpty`(§0.2) | 같은 넷 | 약속대로 |
| P-3 | 접는 API 없음 — `isWon(): Boolean`·`orElse` 류 0(§0.2) | `settlement/src/main` 에 `Boolean` 반환 공개 함수·`orElse` 0(grep 빈 출력) | 약속대로 |
| P-4 | 조회 술어를 `Boolean` 이 아니라 티켓 타입으로, 판정 함수의 첫 인자가 그 산출(§0.3) | `decideAwardLookup(operator, policy): AwardLookupDecision` · `judgeAwardOutcome(lookup, winner, policy)` — 시그니처 설계 그대로, `Skip` 이면 결과 쪽을 보기 전에 `NotJudged` | 약속대로 |
| P-5 | 판정 시그니처에 금액 없음, 컴파일 실패 하네스로 강제(계약 산출물 1·3) | 세 함수 어디에도 금액 인자 없음 · `CompileFailureHarnessTest` fixture 1 | 약속대로 |
| P-6 | 기록 불변 — `existing` 이 확정이면 기존 값을 돌려준다, 승격 경로가 타입으로 없다, 시각 축 없음(§0.4) | `recordAwardOutcome` 그대로 · `Recorded` 가 `SettledAwardOutcome` 만 받는다 · `Instant` 참조 0 | 약속대로 |
| P-7 | 정규화 순서는 legacy ① 공백 → ② 토큰 → ③ casefold, 공백 집합은 `Char.isWhitespace()`(§0.1) | 순서·공백 집합 그대로 | 약속대로 |
| P-8 | ③ 을 `lowercase()` 로, `ß` 류만 알려진 제한(§0.1) | **ASCII `A`~`Z` 만** 접는 `lowercaseAscii` | **선언된 이탈** — `lowercase()` 가 바이트코드에서 `Locale.ROOT` 를 읽어 `ArchitectureGateTest` 가 붉었다(§4). 제한의 범위가 `ß` 에서 비-ASCII 라틴 전체로 넓어졌고 §3-1 이 그것을 적는다 |
| P-9 | 계약 산출물 1 「casefold」 | 위 P-8 | **약속 자체가 틀림(부분)** — domain 모듈에서 casefold 를 그대로 지킬 길이 없다(Kotlin 대응 없음 + 대체 stdlib 경로가 허용 목록 밖). 계약 문면을 고치지 않고 이 행과 §3-1 로 남긴다 |
| P-10 | 토큰 목록은 versioned 정책 데이터, legacy 값·순서 그대로, 출처를 `source` 에(계약 산출물 1, §0.1) | `AwardMatchingPolicyData` + `AWARD_MATCHING_POLICY`(`EffectiveDatedPolicy`, 초기 version 하나) · 열하나 값·순서 legacy 와 같음 · 출처 상수가 `source` | 약속대로 |
| P-11 | 정책 불변식(설계 메모에 없음) | 비어 있음·공백 토큰·중복 금지를 구성 시점에 `require` | 약속대로(더한 것) — KDoc 이 이유를 적고 `AwardMatchingPolicyDataTest` 가 잰다 |
| P-12 | `CompanyName.kt` 의 공개 표면 = `NormalizedCompanyName` · `normalizeCompanyName` · 입력 상태 둘(§0.6) | 그 넷 **+ 공개 sealed `CompanyNameNormalization`(`Normalized`·`Empty`)** | **무선언 이탈(더한 것, 무해)** — 정규화의 「빈 결과」를 `null` 대신 결말로 나르는 반환 타입이다. §0.2 의 `NormalizesToEmpty` 사유가 그 결말에서 나오므로 설계 의도와 어긋나지 않지만 §0.6·§1 의 파일 표에 없었다. 이 행으로 선언한다 |
| P-13 | 원문 상호 운반 타입 셋의 `toString()` 가림, `equals` 유지(§0.5) | `OperatorTradeName.Declared` · `AwardWinnerTradeName.Published` · `NormalizedCompanyName` 셋 다 `REDACTED_TRADE_NAME` | 약속대로 |
| P-14 | `NormalizedCompanyName` private 생성자 + `@ConsistentCopyVisibility` + 모듈 내 factory(§0.5) | 그대로(`internal companion object.of`) · fixture 2·3 | 약속대로 |
| P-15 | 사업자등록번호·대표자명 슬롯 0(§0.5, P-10 (a)) | 0 | 약속대로 |
| P-16 | 상호는 전부 가상, 실 상호 0(§0.5 — 문면은 「test·evidence」, 위협 모델은 「로그·evidence 에 실값」) | test·fixture·evidence 는 가상만. **main KDoc 한 줄**이 legacy 주석의 예시 상호를 그대로 옮겨 **실투찰에 등장한 실 상호 하나**를 담고 있었다 | **무선언 이탈 → 고침** — `fb6c5872` 가 그 줄을 가상 상호로 바꿨다(주석만, 바이트코드 무변). 실명은 이 evidence 에도 적지 않는다. 교훈: legacy 에서 **주석까지** 옮길 때 예시 값이 실데이터인지 본다 |
| P-17 | 변이 M-1~M-9(§0.7) | M-1~M-9 + **M-7b**(가드 통째 제거) | 선언된 이탈(더한 것) — §2 가 M-7 의 좁음과 함께 적는다 |
| P-18 | 계약 산출물 3 의 변이 「3-state 를 boolean 으로」 | M-4(미기록 → 패찰) · M-5(판정 안 함 → 패찰)로 잰다 | 선언된 이탈(해석) — §0.7 이 그 대응을 적었다. 타입이 sealed 라 문자 그대로의 `Boolean` 치환은 컴파일되지 않고, 「boolean 으로 접힘」의 실제 거동은 비확정 상태가 `Lost` 가 되는 것이다 |
| P-19 | 컴파일 실패 하네스는 fixture 마다 양성 쌍둥이(§2-1) | 양성 파일 **둘** — fixture 3(`copy()` 우회)은 fixture 2 의 양성을 공유한다 | 선언된 이탈 — §2-1 표가 「같은 양성」으로 적는다 |
| P-20 | 마이그레이션·영속화·endpoint·운영자 상호 입력·낙찰 목록 수집 0(계약 (3)) | `a61ddff1..HEAD` 의 파일 목록이 `settlement/**` · 등재 장부 · capability map · 추적표 · 이 evidence 뿐 | 약속대로 |
| P-21 | 판정 ⓒ, 집계 증감 0(§0.8, 계약 산출물 4) | 추적표 SET-01 행 ⓒ · 증감 0 | 약속대로 |
| P-22 | rollback ①~⑥ 은 **마지막 산출물 커밋**에서 실측(계약 rollback) | 앞 세션의 `rollback.md` 는 `8fc84e46` 에서 쟀는데 그 뒤 `70926fcb`(현 `2c16603d`)가 `CompanyName.kt` 를 바꿔 **그 문서가 스스로 건 대조 술어(복원 경로 diff 빈 출력)가 거짓**이었다 | **무선언 이탈 → 다시 잼** — rebase 뒤 마지막 산출물 커밋에서 다시 쟀다(`rollback.md` 가 새 실측으로 바뀌었다) |
| P-23 | 새 OPEN 이 필요하면 레인 제안(계약 산출물 4) | `OPEN-SET01-AWARD-NAME-INGEST` 신설(팀장 지시). **운영자 결정 2026-10-22: (a) 승인, 착수는 6G 라이브 수집 종료(~10-29) 뒤** — `fb05a786` 가 capability map 의 두 행에 인용했다. 이 slice 는 착수하지 않는다 | 약속대로 — 적재가 출하되기 전까지 SET-01 행의 빈 자리는 그대로라 행 판정 ⓒ·OPEN 행 존치·집계 증감 0 은 바뀌지 않는다 |

**집계**: 약속대로 15 · 선언된 이탈 4(P-8·P-17·P-18·P-19) · 무선언 이탈 3(P-12 선언으로 닫음 ·
P-16 고침 · P-22 다시 잼) · 약속 자체가 틀림 1(P-9).
