# commands — M1 / 1D (Provenance first-match · Floor Shortfall)

명령과 종료 코드만 남긴다(evidence-pack 규격 — 출력 전문 금지, 핵심 결과 한 줄).

## acceptance_commands (scope.md 대응)

- **P-0** `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`
  — exit 0. 312 tasks 전건 executed(캐시 없는 격리 clone).
- **P-1** `./gradlew --no-build-cache clean check` — exit 0. 312 actionable tasks, 295 executed.
- **P-2** `./gradlew :decision:test` — exit 0. `ProvenanceRulesTest`·`FloorShortfallKernelTest` 전건 통과.
- **P-3** `./gradlew :shared-kernel:test` — exit 0. 1B 회귀 전건 + `RateArithmeticTest` 신규 통과.
- **P-4** `./gradlew :decision:domainApiTypeGate :decision:domainSourceReferenceGate :decision:typeShapeGate :decision:sizeGate :decision:cpdCheck` — exit 0.
- **P-5** `./gradlew :app:test --tests '*Conformance*'` — exit 0. `TEST-...SharedKernelCorpusConformanceTest.xml`: `tests="28" failures="0" errors="0"`(authoritative 7 case 신규 포함, dispatch 표 완전성 test 포함).
- **P-6** `./gradlew qualityBaseline` — exit 0.
- **P-7** 조건부(manifest 편집 시) — **생략**. 이 slice 는 `fixtures/manifest.yaml`을 편집하지 않았다(스크립트 자체는 다른 레인의 혼입분 — 아래 「알려진 제한」 참고).
- **P-8** `./gradlew :build-logic:test` — exit 0(`qualityBaseline` 묶음 실행에 포함).

## 모듈별 게이트(개별 확인)

- `./gradlew :app:check` — exit 0.
- `./gradlew :decision:check` — exit 0.
- `./gradlew :shared-kernel:check` — exit 0.
- `./gradlew :decision:gateExecutionGate :app:gateExecutionGate` — exit 0(`gate.tests.decision` 신설분 포함).

## secret 스캔

`git diff 95585b5^..bc15983 | grep -niE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))"`
— 매치 3건, 전부 `provenanceRuleIdFromToken`(함수명) 부분 문자열의 오탐. 실제 비밀값 없음(육안 확인).

## 변이 실측(5건, 위협 모델 방어 재현 — 전부 실제로 편집·실행·원복했다)

각 항목은 `decision` main 소스를 직접 편집해 `:decision:test`(항목 4는 `:app:test
--tests '*Conformance*'` 도 동반)를 돌려 RED 를 확인한 뒤 `git checkout --` 로
원복하고 다시 GREEN 을 확인했다.

1. **rule order 하드코딩** — `ProvenanceRules.firstMatchedRule` 이 `policy.value.ruleOrder`
   대신 고정 리스트 `listOf(CleanInteger, SuspectRatio, DerivedVat)` 를 쓰도록 바꾸자
   `ProvenanceRulesTest.② 같은 매치 집합이라도 정책 순서가 다르면 다른 라벨을 낸다` 가
   실패(`expected:<SuspectRatio> but was:<CleanInteger>`). 13 tests 중 1 failed. 원복 후 재통과.
2. **`Unmeasurable→Measured(0/1)` 접기** — `measureFloorShortfall` 의 `qualifiedDenominator
   < minSamples` 분기를 `Unmeasurable` 대신 `Measured(Frequency(0,1), ...)` 로 바꾸자
   `⑥ 149 표본은 Unmeasurable(SampleInsufficient) 이다`·`fs-005 전이` 둘 다 실패(14 tests 중
   2 failed). 원복 후 재통과.
3. **밴드 밖 표본 분모 잔류** — `ShortfallTally.qualifiedDenominator` 를 `rawCount -
   outsideBand` 대신 `rawCount` 로 바꾸자 위협 모델 (f) property·`fs-005 전이`·`⑥` 경계값
   test 셋이 실패(qualifiedDenominator 가 149 대신 152 로 나와 문턱을 안 넘음). 14 tests 중
   3 failed. 원복 후 재통과.
4. **경계 등가 뒤집기** — `isShortfall` 의 `StrictlyGreater` 분기를 `realized.rate >=
   critical.rate` 로 바꾸자 `④ 경계 등가는 미달이 아니다 — strictly-greater` 가 실패
   (`expected:<false> but was:<true>`). **corpus(`floor-threshold-001`·`003`)는 이 변이를
   못 잡는다** — 두 case 의 realized(1.0499·1.0501)가 critical(1.05)과 정확히 같지 않아
   경계 등가 자체를 겨누지 않는다(28 corpus tests 는 그대로 통과). 경계 등가 방어는
   `FloorShortfallKernelTest` 단위 test 단독 책임 — 알려진 제한에 등재. 원복 후 재통과.
5. **규칙 무매치를 Clean 으로 접기** — `classificationFor(null)` 을
   `BaseAmountProvenance.Unknown` 대신 `Clean` 으로 바꾸자 `매치 없음은 Unknown 이다 — D-2,
   신뢰로 접히지 않는다` 가 실패(`expected:<Unknown> but was:<Clean>`). 13 tests 중 1 failed.
   원복 후 재통과.

## 정책 version·fixture 근거

- 신규 fixture 없음 — 기존 authoritative 7 case(`base-amount-provenance-001~003`·
  `floor-threshold-001`·`003`·`floor-shortfall-001`·`005`) 그대로 재사용.
- 정책값(허용 오차·경계·최소 표본)은 main 에 없다 — `decision` 모듈의 정책 데이터 타입은
  형태·불변식만 갖고, 값은 `ProvenanceRulesTest`/`FloorShortfallKernelTest` 의 test 정책
  인스턴스와 `ProvenanceFloorExecutors.kt` 의 runner 가 fixture JSON 에서 직접 읽어
  구성한다(§12.1 `legacy-behavior` 좌표는 각 파일 KDoc 주석에 있다).
