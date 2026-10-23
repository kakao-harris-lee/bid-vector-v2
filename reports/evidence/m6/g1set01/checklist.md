# M6/G1-SET01 checklist — 상호 매칭 커널 (SET-01)

## 0. 설계 메모 (착수, 팀장 회신 전 — TDD 는 이 메모대로 진행한다)

계약 `scope.md` 산출물 1~5 에 대한 구체 형태다. 계약 전제가 틀렸다고 판단한 자리는 없다.
**이 절은 착수 메모 원문이다** — 검토 r1(D-G1SET01-1)이 바꾼 자리(조회 관문의 생성 경로·정책 동반,
토큰 제거 위치, 공백 집합 문장)는 §6 처분표가 정본이다.

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
  전체(전각 공백·NBSP 등)를 덮는다. Kotlin `Char.isWhitespace()` 를 쓴다 — 그것은 `\s` 에서
  **U+0085 하나를 뺀 부분집합**이다(r1 실측 29 대 28, 착수 때 「정확히 그 집합」으로 적은 것은
  틀렸다 — §6 R-9; r2 에서 그 글자를 더해 legacy 와 같은 집합이 됐다 — §7 S-4). `java.util.Locale` 은 domain 의 금지 표면
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
**r1 이후 모양**: `Lookup(operator, policy)` 의 생성자·`copy` 가 `internal`, 판정은
`judgeAwardOutcome(lookup, winner)` — 정책 인자가 사라졌다(§6 R-1·R-2).
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
| `settlement/src/main/kotlin/bidvector/settlement/CompanyName.kt` | `NormalizedCompanyName`(private 생성자 + `@ConsistentCopyVisibility`) · `normalizeCompanyName`(토큰은 접두·접미만) · 입력 상태 둘(빈·공백만 상호 거부) |
| `settlement/src/main/kotlin/bidvector/settlement/AwardOutcome.kt` | 결과 4-state(`Won`·`Lost` 생성자 `internal`) · 사유 둘 · 조회 관문(`Lookup` 생성자·`copy` `internal`, 정책 동반) · 기록 결과 sealed |
| `settlement/src/main/kotlin/bidvector/settlement/AwardMatchingKernel.kt` | `decideAwardLookup` · `judgeAwardOutcome(lookup, winner)` · `recordAwardOutcome` |

acceptance 네 줄과 재는 자리:

| acceptance bullet | 재는 자리 |
|---|---|
| 법인 표기 차이를 흡수해 같은 회사로 판정한다 | `CompanyNameNormalizationTest`(목록의 열하나 **각각** + 공백이 낀 표기 + 전각 공백·NBSP + 대소문자 + 양 끝 겹친 토큰 · **중간 토큰은 남는다**) · `AwardMatchingKernelTest`(접두·접미·부분 문자열 쌍 넷 양방향 = 패찰) |
| 동일 금액·다른 상호는 패찰이다(금액은 판정에 쓰지 않는다) | `AwardMatchingKernelTest`(상호 불일치 = 패찰 · 판정에 닿는 입력 타입 여섯의 **필드 집합 등식**) + `CompileFailureHarnessTest` fixture 1(금액 인자) · fixture 6·7(모듈 밖 `Won`·`Lost` 생성) — 전부 **컴파일되지 않는다** · 낙찰/패찰 구별 test |
| 낙찰가만 있고 낙찰자 상호가 없으면 `false` 가 아니라 미기록 | `AwardMatchingKernelTest`(`NotRecorded` 둘 — 미공개 · 정규화 결과 빈 문자열) |
| 운영자 상호가 미설정이면 판정하지 않고 외부 조회도 하지 않는다 | `AwardMatchingKernelTest`(조회 술어 `Skip` 둘 + 판정 `NotJudged` 둘, 운영자 축이 결과 축보다 먼저 · 관문을 만든 정책 하나로 양쪽 정규화 — 토큰이 운영자 쪽에만·낙찰자 쪽에만 있는 두 사례) + `CompileFailureHarnessTest` fixture 4·5(모듈 밖 `Lookup` 직접 생성·`copy` 가 **컴파일되지 않는다**) |
| (분류 근거) 판정은 한 번만 기록되고 덮이지 않는다 | `AwardOutcomeRecordingTest`(반대 판정 · 같은 판정 · 확정 뒤 미기록 관측 셋 다 기존 값 유지) |
| (계약) 목록은 코드 상수가 아니라 versioned 정책 데이터 | `AwardMatchingPolicyDataTest` + `CompanyNameNormalizationTest` 의 주입 정책 사례 |

클래스 다섯을 `config/quality/gate-tests.properties` 의 **신설 키** `gate.tests.settlement` 에 등재했다
(`gateRegistrationGate` 의 양방향 등식 — 모집단 ∖ 제외 == 등재). 헬퍼 파일
(`AwardMatchingTestPolicies.kt`)은 test 클래스가 아니라 모집단에 들지 않는다.

**정규화 순서**: legacy 는 ① 공백 제거 → ② 법인 표기 제거 → ③ `casefold()` 다. capability map 문면은
단계의 **열거**이고 순서를 주장하지 않는데 순서가 거동을 가른다(공백이 먼저 사라져야 「주식 회사 …」가
표기 제거에 닿는다). 그래서 문면이 아니라 legacy 순서를 구현하고 그 자체를 test 로 고정했다(M-1 이 그
줄만으로 붉다).

## 2. 변이 실측 — 바꿔치기 · `numstat` · 통제군 (r2, 마지막 산출물 커밋 `dee96d41`)

버릴 clone 에서 쟀다(공유 worktree 무접촉). 회차마다: 치환(대상 문자열이 정확히 한 번 나오는지 단언) →
`git diff --numstat` → `:settlement:test --rerun`(캐시·up-to-date 없이 **실행**) → JUnit XML 에서 실패 수집 →
`git restore --source=HEAD -- settlement` → `git status --porcelain -- settlement` **빈 출력**(전 회차 0).
재현의 정본은 아래 「무엇을 바꿔치웠나」 칸이다(스크립트는 레인 scratchpad 에 있고 저장소에 두지 않는다).

**소비자 집합**은 `:settlement:test` 가 전부다 — `settlement` 밖 main 소스의 `bidvector.settlement` 참조 0.
**통제군**: 변이 없는 트리 `--rerun` **51 test · 실패 0** — 측정 열의 **처음과 끝** 두 번.

| ID | 무엇을 **바꿔치웠나** | numstat | RED(51 중) |
|---|---|---|---|
| M-1 | 공백 제거 단계를 「원문 그대로」로 | `1 1` | **9** |
| M-2 | 토큰 제거 단계를 「입력 그대로」로(단계 무력화) | `1 1` | **15** |
| M-3 | 소문자화 단계를 「원문 그대로」로 | `1 1` | **1** — 대소문자 흡수만(한글 사례는 무관 — 그 좁음이 정확하다) |
| M-4 | 결과 쪽 미공개(미기록) → 패찰 | `1 1` | **1** |
| M-5 | 운영자 미설정(판정 안 함) → 패찰 | `1 1` | **3** |
| M-6 | 조회 술어가 미설정에도 `Lookup` | `1 1` | **3** |
| M-7 | 기록 시 기존 확정 대신 새 판정 값 | `1 1` | **1** — 반대 판정만(값이 같은 두 test 는 초록 — 그래서 M-7b) |
| M-7b | 불변 가드를 통째로(확정이 있어도 없는 것처럼 기록) | `1 1` | **3** — 기록 불변 test 전건 |
| M-8 | 토큰 목록을 주입 정책 대신 코드 상수로 | `1 1` | **3** — 주입 정책 사례 · 관문 정책 동반 test 둘 |
| M-9 | 판정 시그니처에 기본값 있는 금액 인자를 **더한다** | `1 0` | **1** — fixture 1(음성이 컴파일돼 버린다) |
| X1 | 정확매치 `==` → 양방향 접두 포함 | `1 1` | **1** — 접두·접미·부분 쌍 test(r1 전에는 **생존**) |
| X2 | 정확매치 `==` → 양방향 부분 포함 | `1 1` | **2** — 같은 test + 관문 정책 test(r1 전에는 **생존**) |
| X12 | 결과 쪽 입력에 기본값 있는 금액 필드 + 판정에 `amount > 0` | `1 1`·`1 0` | **1** — 필드 집합 등식(r1 전에는 **생존**) |
| N1 | 토큰 제거를 legacy 전역 `replace` 로 | `1 1` | **1** — 중간 토큰 test |
| N2 | 양 끝 제거의 반복(`while`)을 한 번(`if`)으로 | `1 1` | **1** — 겹친 토큰 test |
| N3 | `Declared` 의 빈 상호 거부를 `require(true)` 로 | `1 1` | **1** |
| N4 | `Published` 의 빈 상호 거부를 `require(true)` 로 | `1 1` | **1** |
| N5 | 판정이 `lookup.policy` 대신 다른 고정 정책으로 결과 쪽을 접는다 | `1 1` | **2** — 법인 표기 흡수 · 표기뿐 미기록(관문 정책 test 자체는 그 고정 정책에서 우연히 초록 — 잠금은 이 둘이 진다) |
| N6b | `Lookup` 을 public 으로(`internal` 과 `@ConsistentCopyVisibility` 둘 다 제거) | `1 2` | **2** — fixture 4·5 |
| N7 | `Won` 생성자를 public 으로 | `1 1` | **1** — fixture 6 |
| N7b | `Lost` 생성자를 public 으로(r2) | `1 1` | **1** — fixture 7(r1 에서는 **생존**, 검증자 Y7) |
| N9 | 판정이 결과 쪽을 `lookup.policy` 대신 **출하 정책 해석값**으로 접는다(r2) | `1 1` | **1** — 낙찰자 쪽 토큰 사례(r1 에서는 **생존**, 검증자 Y8) |
| N10 | 공백 제거에서 U+0085 절을 뺀다(r2) | `1 1` | **1** — U+0085 test |
| E1 | `Won`·`Lost` 의 `equals` 본문을 둘 다 `other is SettledAwardOutcome` 으로(r2) | `2 2` | **1** — 낙찰/패찰 구별 test **하나뿐** |
| N6 · N8 | `internal` 만 제거 / 주석만 제거 | `1 1` · `0 1` | **main 컴파일 실패** — `allWarningsAsErrors` 가 「주석이 중복」·「non-public 생성자가 `copy()` 로 노출」 경고를 오류로 올린다. 컴파일러가 지는 잠금이고 test 를 재지 않으므로 N6b 를 따로 쟀다 |

**E1 이 하나만 붉는 것이 요점이다**(CR N-1): 오늘 코드에서 X1·X2·N5 가 붉는 것은 「실제 `Won` ≠ 기대 `Lost`」
이기 때문이다 — 그 셋의 잠금은 이 등식 위에 서고, E1 아래에서는 모든 단언이 같은 종류끼리 비교돼 비교 방향과
무관하게 초록이 된다. 그래서 등식 자체를 재는 test 가 그 셋의 탐지기를 지킨다.

**M-9 만 「더하기」인 이유**: 인자의 **부재**는 바꿔치울 대상이 없다 — 위반은 더하기로만 표현된다. X12 도
같은 사유다(필드의 부재).

### 2-1. 컴파일 실패 하네스의 음성 대조

일곱 fixture 각각에 **양성 쌍둥이**와 **오타 변이**를 두었다. `internal` 조각(`it is internal in`)은 r1 첫 실행에서 실측됐다. 오타 변이는 컴파일에 실패하되 **기대
진단 조각을 담지 않아야** 한다 — 「컴파일 안 됨」만 보면 어떤 오타든 단언을 만족시키기 때문이다.

| fixture | 음성(기대 진단) | 양성 | 오타 변이 |
|---|---|---|---|
| 1 금액 인자 | `too many arguments for 'fun judgeAwardOutcome(` | 2-인자 호출(r1 에서 정책 인자가 빠졌다) | 없는 이름 호출 → `unresolved reference`(기대 조각 없음) |
| 2 정규화 우회 | `it is private in`(**가시성을 구별하는** 조각 — `cannot access` 는 `private`/`internal` 을 못 가른다) | `normalizeCompanyName` 경유 | 없는 이름 생성 |
| 3 `copy()` 우회 | `it is private in` | 같은 양성 | 없는 멤버 호출 |
| 4 관문 `Lookup` 직접 생성(r1) | `it is internal in` — `normalizeCompanyName` 으로 얻은 값으로 `Lookup(…)` 을 만들어 판정 | fixture 1 의 양성(관문 경유 판정) | 없는 이름 생성 |
| 5 관문 `Lookup.copy()`(r1) | `it is internal in` — 관문을 지난 값의 운영자 상호를 바꿔치움 | 같은 양성 | 없는 멤버 호출 |
| 6 `Won` 직접 생성 → 기록(r1) | `it is internal in` | 판정 경유 기록(`positive-6`) | 없는 이름 생성 |
| 7 `Lost` 직접 생성 → 기록(r2) | `it is internal in` | 같은 양성 | 없는 이름 생성 |

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
6. **미달 매치** — 공백 집합은 r2 에서 legacy `\s` 와 같아졌다(`Char.isWhitespace()` + U+0085). 남는 것:
   zero-width 글자(U+200B·U+FEFF 등), 전각 라틴, NFD 한글, 괄호 혼용(`(주）`)은 정규화하지 않는다. **실상호
   쌍에서는** 거짓 「패찰」 쪽이고 `OPEN-SET-03`(실수집 상호 확인) 옆의 축이다. (그 글자만으로 된 퇴화 상호가
   양쪽에 같이 오면 같은 값이라 낙찰이 된다 — legacy 도 그 글자를 지우지 않으므로 같은 거동이다.)
7. **관문이 닫는 범위** — `Lookup` 의 유일한 생성 경로는 `decideAwardLookup` 이다(생성자·`copy`
   `internal`, fixture 4·5). 남는 여지는 하나다: **설정은 됐으나 정규화 결과가 빈 문자열**인 경우
   호출부가 관문을 무시하고 `Declared.rawName` 을 직접 들고 외부 조회를 할 수 있다. 판정에는 닿지
   못한다(`Lookup` 을 만들 수 없다). 조회 경로가 생기는 소비 slice 의 호출부 test 몫이다.
8. **토큰 제거는 접두·접미만 — legacy 와 의도된 어긋남.** legacy `str.replace` 는 상호 중간의 토큰도
   지워 「가상주식회사건설」 = 「가상건설」(거짓 낙찰 방향)이다. 이 커널은 계약 문면대로 양 끝에서만
   (겹치면 남김없이) 지운다. 그 결과 중간에 법인 표기가 박힌 실제 같은 회사 표기는 거짓 「패찰」이 된다.
   differential 분류는 **intentional** — 근거는 계약 결정 **D-G1SET01-1 ②**(「계약 문면대로 접두·접미만 제거한다 ·
   legacy 와의 차이는 의도된 어긋남으로 적는다」)다. 오차 방향을 거짓 낙찰에서 거짓 패찰로 옮긴다.
9. **저장된 확정 판정의 복원 경로가 없다** — `Won`·`Lost` 생성자가 `internal` 이라 모듈 밖에서
   `RecordedAwardOutcome.Settled(…)` 를 만들 길이 없다. 영속화 소비 slice 가 저장값에서 기록을
   되살리려면 **출처를 가진 복원 함수**를 이 모듈에 더해야 한다(그 함수가 모듈 밖 `Won` 생성 경로를 다시
   열지 않게 하는 설계가 그 slice 의 몫이다).
10. **기록과 새 판정의 어긋남을 알리지 않는다**(CR Q-1) — `AlreadySettled` 는 기존 값만 나른다. 새
    판정은 호출부가 넘긴 값이라 이미 쥐고 있어 `kept` 와 비교하면 어긋남이 드러나므로 거동을 바꾸지
    않았다. 어긋남을 세고 경보를 낼지는 영속화 소비 slice 몫이다(KDoc 이 같은 문장을 갖는다).
11. **`build-logic` 의 한 KDoc 이 거짓이 됐다**(CR M-2) — `DomainApiTypeFixtureTest` 클래스 KDoc 이
    「domain main 의 유일한 선언 `ModuleBoundaryAnchor` 가 `internal` 이라 공개 선언이 0」을 근거로 든다.
    이 slice 가 그 타입을 지우고 공개 표면을 세웠으므로 그 문장은 틀렸다. 게이트는 fixture 로 돌아 초록이고
    `build-logic/**` 은 이 slice 의 in_scope 밖이라 고치지 않고 후속으로 넘긴다. 교훈: 타입을 지울 때
    `grep -rn '<지운 심볼>'` 로 **심볼 이름의** 역방향 파급을 본다(좌표 규율은 심볼 이름을 잡지 못한다).
12. **모듈 밖 오버로드**(verifier V92) — 모듈 밖에서 금액을 넷째 인자로 받는 같은 이름 함수를 선언하는
    것은 언어가 막지 않는다. 그 함수는 커널을 거치지 않으므로 확정 판정을 만들 수 없다(fixture 6·7).
13. **`internal` 은 Kotlin 의 경계다**(verifier L-1r2) — 바이트코드에서는 public 이라 모듈 밖 **Java** 소스는
    `new SettledAwardOutcome.Won()`·`new AwardLookupDecision.Lookup(…)` 를 컴파일·실행할 수 있다(검증자 실측). 저장소
    main 의 `.java` 는 0 이고 그것을 막는 게이트는 없다 — Java 소스가 생기면 이 잠금을 다시 본다. (Kotlin 의
    `@Suppress("INVISIBLE_*")` 우회는 `allWarningsAsErrors` 가 막는다.)
14. **정책의 출처는 열려 있다**(CR Q-1r2) — 커널은 넘어온 정책을 양쪽에 같게 쓸 뿐, 그것이 출하
    `AWARD_MATCHING_POLICY` 의 해석값인지는 묻지 않는다(`*PolicyData` 의 public 생성자는 저장소 관례). 호출부
    test 의 몫이고 `OPEN-SET-03` 과 같은 축이다 — capability map SET-01 절이 같은 문장을 갖는다.

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
| P-4 | 조회 술어를 `Boolean` 이 아니라 티켓 타입으로, 판정 함수의 첫 인자가 그 산출(§0.3) | `decideAwardLookup(operator, policy): AwardLookupDecision` · `judgeAwardOutcome(lookup, winner)` — `Skip` 이면 결과 쪽을 보기 전에 `NotJudged` | 약속대로 + r1 강화 — `Lookup` 생성자 `internal`·정책 동반(§6 R-1·R-2). 착수 모양은 관문 생성자가 public 이라 「지나야 한다」가 구조상 거짓이었다 |
| P-5 | 판정 시그니처에 금액 없음, 컴파일 실패 하네스로 강제(계약 산출물 1·3) | 세 함수 어디에도 금액 인자 없음 · `CompileFailureHarnessTest` fixture 1 | 약속대로 |
| P-6 | 기록 불변 — `existing` 이 확정이면 기존 값을 돌려준다, 승격 경로가 타입으로 없다, 시각 축 없음(§0.4) | `recordAwardOutcome` 그대로 · `Recorded` 가 `SettledAwardOutcome` 만 받는다 · `Instant` 참조 0 | 약속대로 |
| P-7 | 정규화 순서는 legacy ① 공백 → ② 토큰 → ③ casefold, 공백 집합은 `Char.isWhitespace()`(§0.1) | 순서·공백 집합 그대로 · ② 는 r1 에서 접두·접미 한정 | 약속대로(순서) · **선언된 이탈**(토큰 위치 — 계약 문면 「접두·접미」로, §3-8 · §6 R-5) · 공백 집합을 「정확히 같다」고 쓴 문장은 틀렸다(§6 R-9) |
| P-8 | ③ 을 `lowercase()` 로, `ß` 류만 알려진 제한(§0.1) | **ASCII `A`~`Z` 만** 접는 `lowercaseAscii` | **선언된 이탈** — `lowercase()` 가 바이트코드에서 `Locale.ROOT` 를 읽어 `ArchitectureGateTest` 가 붉었다(§4). 제한의 범위가 `ß` 에서 비-ASCII 라틴 전체로 넓어졌고 §3-1 이 그것을 적는다 |
| P-9 | 계약 산출물 1 「casefold」 | 위 P-8 | **약속 자체가 틀림(부분)** — domain 모듈에서 casefold 를 그대로 지킬 길이 없다(Kotlin 대응 없음 + 대체 stdlib 경로가 허용 목록 밖). 계약 문면을 고치지 않고 이 행과 §3-1 로 남긴다 |
| P-10 | 토큰 목록은 versioned 정책 데이터, legacy 값·순서 그대로, 출처를 `source` 에(계약 산출물 1, §0.1) | `AwardMatchingPolicyData` + `AWARD_MATCHING_POLICY`(`EffectiveDatedPolicy`, 초기 version 하나) · 열하나 값·순서 legacy 와 같음 · 출처 상수가 `source` | 약속대로 |
| P-11 | 정책 불변식(설계 메모에 없음) | 비어 있음·공백 토큰·중복 금지를 구성 시점에 `require` | 약속대로(더한 것) — KDoc 이 이유를 적고 `AwardMatchingPolicyDataTest` 가 잰다 |
| P-12 | `CompanyName.kt` 의 공개 표면 = `NormalizedCompanyName` · `normalizeCompanyName` · 입력 상태 둘(§0.6) | 그 넷 **+ 공개 sealed `CompanyNameNormalization`(`Normalized`·`Empty`)** | **무선언 이탈(더한 것, 무해)** — 정규화의 「빈 결과」를 `null` 대신 결말로 나르는 반환 타입이다. §0.2 의 `NormalizesToEmpty` 사유가 그 결말에서 나오므로 설계 의도와 어긋나지 않지만 §0.6·§1 의 파일 표에 없었다. 이 행으로 선언한다 |
| P-13 | 원문 상호 운반 타입 셋의 `toString()` 가림, `equals` 유지(§0.5) | `OperatorTradeName.Declared` · `AwardWinnerTradeName.Published` · `NormalizedCompanyName` 셋 다 `REDACTED_TRADE_NAME` | 약속대로 |
| P-14 | `NormalizedCompanyName` private 생성자 + `@ConsistentCopyVisibility` + 모듈 내 factory(§0.5) | 그대로(`internal companion object.of`) · fixture 2·3 | 약속대로 |
| P-15 | 사업자등록번호·대표자명 슬롯 0(§0.5, P-10 (a)) | 0 | 약속대로 |
| P-16 | 상호는 전부 가상, 실 상호 0(§0.5 — 문면은 「test·evidence」, 위협 모델은 「로그·evidence 에 실값」) | test·fixture·evidence 는 가상만. 착수 커밋의 **main KDoc 한 줄**이 legacy 주석의 예시 상호(실투찰의 실 상호 하나)를 옮겨 왔고, 뒤 커밋의 수정만으로는 **이력에 남았다**(CR H-2) | **무선언 이탈 → 고침(이력까지)** — r1 에서 브랜치 이력을 재작성해 그 상호가 어느 커밋에도 없다(§6 R-0). 실명은 이 evidence 에도 적지 않는다 |
| P-17 | 변이 M-1~M-9(§0.7) | M-1~M-9 + **M-7b**(가드 통째 제거) | 선언된 이탈(더한 것) — §2 가 M-7 의 좁음과 함께 적는다 |
| P-18 | 계약 산출물 3 의 변이 「3-state 를 boolean 으로」 | M-4(미기록 → 패찰) · M-5(판정 안 함 → 패찰)로 잰다 | 선언된 이탈(해석) — §0.7 이 그 대응을 적었다. 타입이 sealed 라 문자 그대로의 `Boolean` 치환은 컴파일되지 않고, 「boolean 으로 접힘」의 실제 거동은 비확정 상태가 `Lost` 가 되는 것이다 |
| P-19 | 컴파일 실패 하네스는 fixture 마다 양성 쌍둥이(§2-1) | 양성 파일 **셋** — fixture 3 은 fixture 2 의, fixture 4·5 는 fixture 1 의 양성(관문 경유 판정)을 공유하고 fixture 6 은 자기 양성(판정 경유 기록)을 갖는다 | 선언된 이탈 — §2-1 표가 「같은 양성」으로 적는다 |
| P-20 | 마이그레이션·영속화·endpoint·운영자 상호 입력·낙찰 목록 수집 0(계약 (3)) | `54e2c5e2..HEAD`(r1 rebase 뒤 base) 의 파일 목록이 `settlement/**` · 등재 장부 · capability map · 추적표 · 이 evidence 뿐 | 약속대로 |
| P-21 | 판정 ⓒ, 집계 증감 0(§0.8, 계약 산출물 4) | 추적표 SET-01 행 ⓒ · 증감 0 | 약속대로 |
| P-22 | rollback ①~⑥ 은 **마지막 산출물 커밋**에서 실측(계약 rollback) | 착수 세션의 rollback 실측은 그 뒤 산출물 커밋이 `CompanyName.kt` 를 바꿔 문서가 스스로 건 대조 술어가 거짓이었다 | **무선언 이탈 → 다시 잼** — r1 에서도 rebase·재작성 뒤 마지막 산출물 커밋에서 새로 쟀다(`rollback.md`) |
| P-23 | 새 OPEN 이 필요하면 레인 제안(계약 산출물 4) | `OPEN-SET01-AWARD-NAME-INGEST` 신설(팀장 지시). **운영자 결정 2026-10-22: (a) 승인, 착수는 6G 라이브 수집 종료(~10-29) 뒤** — capability map 의 두 행이 인용한다. 이 slice 는 착수하지 않는다 | 약속대로 — 적재가 출하되기 전까지 SET-01 행의 빈 자리는 그대로라 행 판정 ⓒ·OPEN 행 존치·집계 증감 0 은 바뀌지 않는다 |

**집계**: 약속대로 15(P-7 의 순서 포함) · 선언된 이탈 5(P-7 토큰 위치 · P-8 · P-17 · P-18 · P-19) · 무선언 이탈 3(P-12
선언으로 닫음 · P-16 이력까지 고침 · P-22 다시 잼) · 약속 자체가 틀림 1(P-9).

## 6. 검토 r1 처분표 (D-G1SET01-1 — verifier not-ready · CR REQUEST_CHANGES, 재작업 1/5)

처분 셋: **고침**(산출물 변경 + test/변이) · **등재**(거동 유지, §3 제한) · **ledger**(문서만).
변이 ID 는 §2 표의 행이다.

| R | 지적 | 처분 | 잠그는 것 |
|---|---|---|---|
| R-0 | CR H-2 — 실 상호가 커밋 넷의 이력에 남음 | **고침** — 브랜치 이력 재작성(squash 없이). 술어: 저장소 밖 목록 파일로 `git log -p <base>..HEAD` 적중 **0**. 원 HEAD 는 로컬 백업 ref 만(push 0). 수정 커밋 하나는 재작성 뒤 빈 커밋이 되어 빠졌다(산출물 커밋 여섯 → 다섯 + r1 둘) | 술어 실측(commands §0) |
| R-1 | CR H-1 · verifier L-5 — 판정이 관문과 다른 정책을 받음 | **고침** — `Lookup` 이 정책을 나르고 `judgeAwardOutcome(lookup, winner)` | test 「판정은 관문을 만든 정책 하나로」 · 변이 N5 |
| R-2 | verifier H-2 — `Lookup` public 생성자로 관문 우회 | **고침** — 생성자 `internal` + `@ConsistentCopyVisibility` | fixture 4·5(음성·오타·양성) · 변이 N6·N8 |
| R-3 | verifier H-1 — 정확매치 미잠금(X1·X2 생존) | **고침** — 접두·접미·부분 쌍 넷 양방향 `Lost` | 변이 X1·X2 |
| R-4 | verifier M-1 — 금액이 입력 타입·`Won` 직접 생성으로 들어옴 | **고침** — 입력 타입 여섯의 필드 집합 등식 · `Won`·`Lost` 생성자 `internal` | 변이 X12 · fixture 6 · 변이 N7 |
| R-5 | verifier M-2 · CR M-1 — 토큰을 상호 중간에서도 지움 | **고침** — 접두·접미만, 겹치면 남김없이. legacy 와 의도된 어긋남(§3-8) | test 둘 · 변이 N1·N2 |
| R-6 | CR L-2 — 빈·공백 상호가 입력 값이 됨 | **고침** — 두 입력 타입 `require(isNotBlank)` | test · 변이 N3·N4 |
| R-7 | CR Q-1 — 어긋난 재판정을 조용히 버림 | **등재**(§3-10) — 호출부가 새 판정을 이미 쥐고 있어 거동 유지 | KDoc 문장 |
| R-8 | CR M-2 — `build-logic` KDoc 거짓 | **등재**(§3-11) — in_scope 밖 | — |
| R-9 | CR L-1 · verifier L-1 — `isWhitespace` = `\s` 문장 거짓 | **ledger** — KDoc·§0.1 을 「U+0085 를 뺀 부분집합」으로 | — |
| R-10 | CR L-3 — legacy 좌표가 다른 문장을 가리킴 | **고침**(KDoc) — 금액 무관 사유를 `:121-123` 으로 | — |
| R-11 | verifier M-3 — container 생략 근거가 틀림 | **ledger + 실측** — app.jar 가 settlement.jar 를 싣는다. container job 을 재생기로 전부 쟀다(commands §4) | 재생 결과 |
| R-12 | verifier L-2 — 계약의 base | 계약(D-G1SET01-1)이 base 를 `54e2c5e2` 로 고정 | — |
| R-13 | verifier L-3 — evidence 크기 경계 | **ledger** — 수치는 commands §5 | — |
| R-14 | verifier L-4 — 미달 매치 제한 누락 | **등재**(§3-6) | — |
| R-15 | CR Q-2 — 판정 SHA 의 `check` 가 대부분 up-to-date | **실측** — `:settlement:test` 실행분은 변이 통제군 `--rerun`(commands §3) | — |

## 7. 검토 r2 처분표 (D-G1SET01-2 — verifier not-ready H-1r2 · CR REQUEST_CHANGES N-1, 재작업 2/5)

| S | 지적 | 처분 | 잠그는 것 |
|---|---|---|---|
| S-1 | CR N-1 — 낙찰/패찰 구별(손으로 쓴 `equals`·`hashCode`·`toString`)을 재는 test 가 없다 | **고침** — 양방향 `shouldNotBe` · 해시·문자열 구별 · 결말 래퍼(`AlreadySettled`·`Recorded`) 구별 | 변이 E1 |
| S-2 | verifier H-1r2 — 「정책 동반」 test 가 결과 쪽을 잠그지 않음(Y8 생존) | **고침** — 토큰이 **낙찰자 상호에만** 있는 사례 → `Won` | 변이 N9(검증자 Y8 과 같은 모양) |
| S-3 | verifier M-1r2 · CR N-3 — `Lost` 생성자 `internal` 미잠금(Y7 생존) | **고침** — fixture 7(음성 · 오타 변이, 양성은 fixture 6 공유) | 변이 N7b |
| S-4 | CR N-2 — U+0085 퇴화 상호가 미기록 대신 낙찰 | **고침** — `stripWhitespace` 에 U+0085, 공백 집합이 legacy 와 같아짐 · §3-6 문면 정정 | test · 변이 N10 |
| S-5 | verifier L-1r2 — Java 호출자는 `internal` 을 우회 | **등재**(§3-13) | — |
| S-6 | verifier L-2r2 — container 문장 둘(run 수 · 새 이미지 ID) | **ledger** — r2 재생의 실측으로 다시 적었다(commands §4) | 재생 결과 |
| S-7 | verifier L-3r2 — `intentional` 분류의 근거 | **ledger** — D-G1SET01-1 ② 인용(§3-8) | — |
| S-8 | CR Q-1r2 — 정책의 출처는 열려 있다 | **등재**(§3-14 + capability map SET-01 절) | — |
