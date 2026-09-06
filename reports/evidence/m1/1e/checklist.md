# M1/1E — 리뷰 요청 조건 점검

CLAUDE.md 운영자 지시(2026-09-04)에 따라 코드 slice의 완료 조건은 **Codex approve가
아니라 verifier ready-for-review + 사용자 승인**이다. 아래는 evidence-pack 스킬의
「리뷰 요청 조건 점검」을 이 slice에 적용한 자기 점검이다 — verifier가 독립적으로 재확인한다.

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- strategy/
      config/quality/gate-tests.properties reports/evidence/m1/1e/` 빈 결과, 양성 대조
      (`Score.kt` 한 줄 추가 → 잡힘 → 복원 → 다시 빈 결과) 완료(`commands.md`).
- [x] scope.md의 acceptance_commands가 전부 exit 0으로 commands.md에 기록됨 — S-0·S-1·
      S-2·S-3·S-5·S-7 전부 exit 0. S-4·S-6은 조건부(D-2·D-3 authoritative case 신설
      시)라 이번 라운드 대상 아님을 명시(`commands.md`).
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `:strategy:check`(detekt·
      ktlint·아키텍처 게이트·CPD·gateExecutionGate·koverVerify 포함) 및 저장소 전체
      `check` 통과.
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — 이번 slice는 fixture를 신설하지
      않았다(`fixtures/manifest.yaml`·`fixtures/input|expected/**` 무접촉, in_scope 조건
      미충족). `STRATEGY_POLICY`(`StrategyPolicyData.kt`)의 근거 문자열이 「점수 범위
      내용 미확정, 형태만」을 명시하고 `budgetBoundInclusivity`만 D-15 결정값(`Inclusive`)임을
      KDoc에 적었다.
- [x] 알려진 제한과 rollback 방법이 기록됨 — `rollback.md` 「알려진 제한」 5건, 되돌리는
      명령은 임시 clone에서 실측(exit 0).
- [x] secret 스캔 통과 — `commands.md` 「secret 스캔」, grep 매치 0. 이 slice는 개인식별
      정보를 다루지 않는다(순수 도메인 값 타입).

## 판단이 갈린 지점 (구현자 판단, verifier 재검토 대상)

1. **텍스트 매칭 대소문자 접기 채택 여부** — scope.md D-5의 "legacy-behavior로 test
   정책에만"이라는 문면과 "1C의 lowercase() 게이트 회피 선례를 따른다"는 문면이 서로
   당기는 방향이 달랐다. lowercase() 회피 선례를 언급하는 것 자체가 실제로 fold 함수를
   구현함을 전제한다고 읽어 **구현했다**(legacy `strip().lower()` 상당, ASCII만, 1C
   `LicensePolicy.kt`의 `CharArray` 직접 조립 관례). CPD 실측(cpdCheck 통과)으로
   shared-kernel 승격이 불필요함을 확인했다.
2. **`Score.of`의 `Fact.Absent` 사유 코드** — D-1이 `Fact<Score>`를 명시하는데 D-16은
   `ReasonCode`를 늘리지 않는다. `POLICY_NOT_APPLICABLE`을 재사용하고 그 값이 `validate`
   내부에서만 소비돼 `StrategyViolation.ScoreOutOfRange`로 번역됨을 근거로 들었다
   (`Score.kt` KDoc).
3. **`StrategyRevision`을 `StrategyDraft`에 넣지 않음** — D-10이 "전부 optional"이라
   적지만 revision은 시스템이 매기는 시퀀스값이라 `validate`의 별도 인자로 뺐다
   (`StrategyValidation.kt` KDoc).
4. **`BudgetBound`에 `BudgetBoundInclusivity` 필드 신설** — `data-dictionary.md` §1.4.3
   "경계 포함성을 값과 함께 선언한다" 원칙과 D-15 "초기값 Inclusive, 정책 데이터 슬롯"을
   결합해 새 타입을 만들었다(`WatchTypes.kt`).
5. **D-9 결합에서 동시 매치 exclude 규칙의 보고 범위** — `Rejected.failed`가 우선순위상
   먼저인 규칙 하나만 싣도록 구현했다(`combineAxes`의 `firstOrNull` 단락 평가). D-7의
   "부분집합" 요건은 충족하나 "전체 집합"은 아니다(`rollback.md` 알려진 제한, `WatchRules.kt`
   KDoc).

## 위협 모델 우회 ↔ 방어 대응표 (Phase 2.5 설계 검토 대응)

| 우회 후보(scope.md) | 막는 것 | 근거 |
| --- | --- | --- |
| (1) `Rejected(failed=∅)` 조립 | `WatchVerdict.Rejected.init`의 `require` | `WatchTypes.kt`, `WatchRulesTest`의 "Rejected 는 failed 가 비어 있으면 구성 시점에 거부된다" |
| (2) `OperatorStrategy`를 validation 없이 조립 | `internal constructor`(모듈 밖 차단) | `StrategyTypes.kt`. 같은 모듈 test는 가능 — 알려진 제한 등재(`rollback.md`) |
| (3) `EstimatedAmount.export()`를 `BaseAmount(...)`로 재포장 | 타입은 못 막음(어댑터 정직성 층) — 「방어하지 않는다」로 명시 | scope.md 위협 모델, `rollback.md` |
| (4) `FullScopeText`의 문자열로 `KeywordScopeText` 생성 | 서명은 강제하되 값의 정직성은 방어하지 않음 | 컴파일 fixture 2(음성: 필드 자리 자체를 잘못 넣는 경우만 차단) |
| (5) `validate`에 `[0,0]` 정책을 줘 임계치 전부 거부 | 정책 내용, 방어하지 않음(명시) | `StrategyPolicyData.kt` |
| (6) `Undeterminable`을 소비자가 `Rejected`로 매핑 | 소진 `when`만(1C·1D와 같은 한계) | `WatchRulesTest`의 "소진 when 만으로 WatchVerdict 을 소비한다" |
| (7) 예산 규칙 없이 `Fact.Absent` 공고를 `Passed`로 | 의도된 동작(그 축을 안 봄) | `WatchRulesTest`의 "예산 규칙이 없으면 ... 우회 후보 (7), 의도된 동작" |

**구조 게이트(모듈 의존)**: `strategy/build.gradle.kts`가 `shared-kernel` 하나만 참조 —
ML·adapters·procurement에 대한 의존 선언이 없어 "watch rule 판정은 ML port 를 0회
호출한다"가 `moduleDependencyGate`로 구조적으로 보장된다(`WatchRulesTest`의 STR-01 4번
test가 값으로도 보조 확인).

## corpus runner 확장(⑪) — 이번 라운드 미실행

`app/src/test/kotlin/bidvector/app/conformance/**`·`fixtures/manifest.yaml`·
`fixtures/input|expected/**`를 손대지 않았다. D-2·D-3의 승인은 scope.md에 이미
기록됐으나(decision 29·30) 실제 `strategy-watch-*`·`strategy-validation-*`·
`money-basis-003` case 신설은 fixture-curator 소관이고 이번 커밋 다섯 개는 구현
레인의 범위(커널·validation·컴파일 하네스·게이트 등재)만 담았다. curator가 case를
만든 뒤 runner dispatch 확장이 후속 작업이다.
