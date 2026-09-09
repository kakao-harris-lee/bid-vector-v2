# checklist.md — M4/4E 리뷰 준비도

## 사용자 승인 — 2026-09-09

**slice 4E 종결 승인.** 승인 범위 다섯:

1. **slice 종결** — 이 slice의 산출물(배달 경로 판정 커널 `resolveDeliveryPlan`·
   `DispatchNotification` use case·port 셋(`RouteDirectory`·`ContentRenderer`·
   `NotificationSender`)·값 타입 다수·`gate.tests.workflow` 등재)을 최종 형태로
   승인한다.
2. **D-M4-8 = (a) 확정** — 메일 라이브 송신(`OPEN-NOTI-04`)·채널 fallback
   (`OPEN-NOTI-07`)은 4E 밖(후속) · 읽음 상태 되돌림(`OPEN-NOTI-05`)은 하지 않는다 ·
   통지 이후 판정 확정의 재통지(`OPEN-NOTI-08`)는 기존 메시지 갱신 없이 새 항목.
   scope.md 착수 가정(D-4E-3·D-4E-4가 참조하던 근거)이 그대로 확정 결정이 됐다.
3. **정책 값 승인** — `Production→Live`·`Staging→DryRun`·`Development→DryRun`·
   `Test→Blocked`·`maskedSuffixLength=4`. 착수 placeholder였던 값 자체가 이제 운영
   정책값이다(`OPEN-4E-POLICY-VALUES` 종결). 정본은 `reports/evidence/m4/4e/
   policy-values.md`(이 승인과 함께 갱신).
4. **corpus 미신설 재확인** — D-4E-6 판단(② 전수 표는 `DeliveryPlanTableTest`가
   갖고 corpus 승격은 이 slice가 하지 않는다)을 사용자가 재확인했다. `OPEN-4E-CORPUS`
   는 닫히지 않고 `m4` 병합 뒤 curator 작업으로 유지된다.
5. **`m4/2026-09-08` 병합은 지금 하지 않는다** — 4B-1·4C-1 종결 뒤 한 번에 병합한다
   (scope.md 「레인 격리」 절이 예고한 절차 그대로).

**승인의 근거 — verifier r1 `ready-for-review`.** 산출물층 finding은 medium
둘(M-1 `DeliveryOutcome` 생성자 공개·M-2 출하 정책 값 무커버리지)뿐이었고, 장부층
finding 넷(L-1~L-4)과 함께 이 승인 반영 직전 커밋 한 번으로 일괄 반영했다(SHA는
`git log -1 --oneline`로 확인). 도메인·계약층에 미해결 finding은 없다(verifier r1
`_workspace/m4-4e/04_verifier_report.md` §5).

**재작업 카운터: 0/5**(상한 5, 여유 5) — verifier r1이 high 없이 곧바로
`ready-for-review`를 냈고, medium 둘·장부층 넷의 일괄 반영은 운영자 채택
2026-09-02 low/장부층 문턱과 같은 처리로 라운드에 세지 않는다(4A 관례 계승).

**다음**: 브랜치 `m4-4e/2026-09-09`는 4B-1·4C-1 종결 뒤 `m4/2026-09-08`에 병합한다
(위 승인 ⑤). 이 slice 자체의 추가 구현 작업은 없다.

## 리뷰 요청 조건 (evidence-pack 스킬 기준)

- [x] 구현 diff가 커밋되어 base/head 고정 — `git status --porcelain -- <in_scope 경로>`
      결과 없음(commands.md 「clean-tree 게이트」 절 — 양성 대조 포함 실측 완료).
- [x] scope.md의 acceptance_commands(S-0~S-6) 전부 exit 0으로 commands.md에 기록됨.
- [x] test/lint/type/architecture/contract 관련 명령 통과 — `clean check` 전건(ktlint·
      detekt·cpd·sizeGate·moduleDependencyGate·gateExecutionGate·qualityBaseline·
      conventionCoverageGate·contractGate 포함).
- [x] 변경된 fixture와 정책 version의 근거가 기록됨 — 이 slice는 fixture 신설이 없다
      (golden-manifest.json N/A). 정책 데이터(`NotificationDeliveryPolicyData`)는 신설이나
      값은 착수 placeholder 지위로 `policy-values.md`에 등재(`OPEN-4E-POLICY-VALUES`).
- [x] 알려진 제한과 rollback 방법이 기록됨(아래 「알려진 제한」·`rollback.md`).
- [x] 인증값 스캔 통과 — S-3·S-3c 실 매치 0(commands.md 「S-3/S-3c」 절 — 최초 실측에서
      자기매치 2건을 발견해 한국어 서술·표본 문자열 교체로 해소한 이력 포함). **육안 확인
      (scope.md 육안 확인 항목)**: S-3c가 패턴 스캔 대상에서 영구 제외하는 `scope.md`를
      포함해 evidence 5파일(`scope.md`·`commands.md`·`checklist.md`·`policy-values.md`·
      `rollback.md`) 전체를 육안으로 대조 — 실 Telegram id·메일 주소·사업자번호·인증값
      0건. 7자 이상 숫자열 매치는 전부 git 커밋 SHA 또는 `base_sha`다(패턴 스캔이 못
      잡는 축, evidence-pack 「Telegram id·사업자 정보는 패턴 스캔으로 못 잡으므로 육안
      확인을 병기한다」 요구 대응).

## 이 slice 고유 확인

### 1. 우회 후보 (1)~(9) ↔ 코드 대응 ↔실측

| # | 우회 | 코드 대응 | 실측 근거 |
| --- | --- | --- | --- |
| 1 | `MaskedTarget(raw)` 직접 생성 | `MaskedTarget`이 `@ConsistentCopyVisibility` + `internal constructor`(`MaskedTarget.kt`), 유일한 생성이 `mask()` factory | 임시 clone에서 `app` 모듈(다른 모듈) test 소스에 `MaskedTarget("raw-value")` 직접 호출을 심고 `:app:compileTestKotlin` → `Cannot access '<init>': it is internal`(commands.md 「우회 (1)(8) 컴파일 거부 실측」) |
| 2 | 실패 뒤 `Delivered` | `NotificationSender` KDoc이 계약을 명시(같은 idempotencyKey 재호출 효과 0) + `SenderContractTest`가 `FakeNotificationSender`로 실패·Unknown 주입 뒤에도 재호출이 그 결과를 그대로 돌려줌을 단언 | `SenderContractTest.실패 주입 뒤에도 같은 키 재호출은 그 Rejected 결과를 그대로 돌려준다`·`Unknown 주입도...` |
| 3 | `Unknown`을 dispatch 안에서 재시도 | `DispatchNotification.dispatch`에 재호출 코드가 없다(함수 하나, `sender.send` 호출 지점이 1곳) | `DispatchNotificationTest.Unknown 결과는 재시도 없이 호출 1회로 그대로 위로 올라간다` — `RecordingSender.callCount shouldBe 1` |
| 4 | 사유를 문자열로 | `SuppressionReason`·`RejectionReason` 모두 sealed, `String` 필드 0(payload는 `RuntimeEnvironment` enum뿐) | 코드 리뷰(`DeliveryPlan.kt`·`DeliveryResult.kt`) — 두 sealed 타입 전 하위 타입이 `data object` 또는 enum 값 하나만 나른다 |
| 5 | dry-run이 sender를 감쌈 | `DispatchNotification.dispatch`는 `plan.outcome`이 `Suppressed`면 `attemptSend`를 아예 호출하지 않는다(`when` 분기가 sender 참조에 닿지 않음) | `DispatchNotificationTest`의 dry-run·차단·비활성·route 없음 네 test 전부 `ThrowingSender`(호출되면 `error()`)를 주입 — 넷 다 통과했다는 것 자체가 무호출의 증거 |
| 6 | 환경 매핑에서 값 하나를 빠뜨려 「모르는 환경은 Live」 | `NotificationDeliveryPolicyData.init`이 `environmentModes.keys == RuntimeEnvironment.entries.toSet()`을 강제 | `NotificationPolicyDataTest.환경 하나라도 빠지면 생성이 거부된다` |
| 7 | raw 채팅 id를 `RouteKey`로 | `RouteKey.init`이 `^[a-z][a-z0-9_-]{0,80}$` shape를 강제 | `RouteKeyTest`의 숫자 시작·콜론·대문자·골뱅이·공백·82자 거부 test 6개 + property test 2개(임의 슬러그는 항상 성공, 임의 숫자열은 항상 거부) |
| 8 | `DeliveryPlan`의 결과를 필드와 어긋나게 손으로 조립 | `DeliveryPlan`이 `@ConsistentCopyVisibility` + `internal constructor`이고 `outcome`은 생성자 인자가 아니라 `policy`·`environment`에서 계산되는 `val`(손으로 지정할 자리 자체가 없음) | 임시 clone에서 `app` 모듈 test 소스에 `DeliveryPlan(PolicyVerdict.Allowed, EnvironmentVerdict.Allowed)` 직접 호출을 심고 `:app:compileTestKotlin` → `Cannot access '<init>': it is internal`(commands.md) |
| 9 | Telegram/mail 라이브러리 타입을 FQN으로 참조 | `notification` 패키지는 `kotlin`·`kotlinx`·`java`·`javax`·`bidvector.sharedkernel`·`bidvector.workflow` 밖을 참조하지 않는다(S-5) | `NotificationBoundaryTest`(4A `disallowedQualifiedReferences` 술어 재사용) — allow-list 스캔 + Telegram 패키지 심은 표본 양성 대조 |

### 1b. 값 획득·위조 축 표(설계 검토 (2)) — 코드에서 어떻게 섰는가

| 타입 | 판정 | 근거 |
| --- | --- | --- |
| `RouteKey` | 연다(shape로 닫힘) | 어댑터가 설정에서 만든다. shape 밖은 생성 실패(우회 (7)) |
| `MaskedTarget` | 닫는다 | `@ConsistentCopyVisibility` + `internal constructor`, 유일한 생성이 `mask()`(우회 (1)) |
| `DeliveryRequest` | 닫는다 | `@ConsistentCopyVisibility` + `internal constructor`, 유일한 생성이 `planDelivery`(internal) |
| `DeliveryPlan` | 닫는다(결과는 계산) | `@ConsistentCopyVisibility` + `internal constructor`, `resolveDeliveryPlan`(public 순수 함수)만 만든다(우회 (8)) |
| `DeliveryResult.*` | 연다 | sender 구현(어댑터)이 만들어야 한다. 위조해도 dispatch가 `Suppressed`를 덮어쓰지 않으므로(T-5) 「미전달 → 완료」 경로는 여전히 없다 |
| `DeliveryOutcome.*` | **닫는다**(verifier r1 M-1 시정) | ~~public이었다~~ `@ConsistentCopyVisibility` + `internal constructor`로 정정 — dispatch만 만들고 4C(같은 `workflow` 모듈)만 소비하므로 닫는 데 비용이 없었다. 임시 clone에서 `app`(다른 모듈) test 소스에 `DeliveryOutcome.Attempted(...)` 직접 생성을 심고 `:app:compileTestKotlin` → `Cannot access '<init>': it is internal`(commands.md) |
| `RenderedContent` | 연다 | 렌더러(port 구현)가 만든다. 내용의 옳음은 비방어 |
| `NotificationIntent` | 연다 | 4B가 만든다 |
| `NotificationDeliveryPolicyData` | 연다(불변식으로 닫힘) | 값은 정책 슬롯. 전사상·suffix≥1 생성 불변식(우회 (6)) |

### 2. 위협 모델 방어 (a)~(i) ↔ 증거(요약)

scope.md 「위협 모델」 절이 정본이다. gate-tests.properties의 `gate.tests.workflow`
M4/4E 문단이 test 일곱 각각을 (a)~(i)에 대응시킨 표를 갖는다(중복 등재 회피).

### 3. `resolveDeliveryPlan` 9행 전수 표

`DeliveryPlanTableTest`가 정책 3({Enabled+route, Disabled, Enabled+route 없음}) ×
환경 3({Live, DryRun, Blocked}) = 9행을 전수로 단언한다(scope.md D-4E-6 — corpus가
아니라 이 test가 전수 표를 갖는다). 첫 위반 순서(정책 우선)도 같은 test의 두 번째
케이스(`DISABLED`+`DRY_RUN` → `ChannelDisabled`)로 실측된다.

### 4. legacy와의 의도적 재설계 둘 — differential.json N/A 사유

scope.md 「조사 결과」(c-1)가 정본이다: canonical/synthetic operator 구분은 단일
회사 V2에 없어(§1.2) 옮기지 않았고, legacy-settings fallback(채널 행 없는 canonical
운영자에게 레거시 chat으로 배달)은 「설정 없음 = 보내지 않는다」(`TargetMissing`)로
재설계했다. 둘 다 `intentional-redesign`이고 case 자체가 없어 `differential.json`은
N/A다.

## 알려진 제한 (이 slice 종료 시점)

1. **실 어댑터(Telegram/Email sender)의 계약 준수는 이 slice가 증명하지 않는다** —
   `NotificationSender` KDoc이 요구하는 idempotency 계약은 `SenderContractTest`와
   같은 골격의 test로 실 어댑터 slice가 계승해야 한다(설계 검토 (0)). 그 검증은
   그 slice의 verifier 몫이다.
2. **`RuntimeEnvironment`를 실행 환경(`ENVIRONMENT`)에서 읽어 채우는 배선은 이
   slice 밖이다** — app/M6이 만든다. 커널은 그 값을 사실로 받는다(설계 검토 (0)) —
   배선이 틀리면 커널은 그 틀린 값을 믿는다.
3. **② 전수 표의 corpus 승격은 이 slice가 하지 않는다** — `m4` 병합 뒤 별도 curator
   작업(`OPEN-4E-CORPUS`, D-4E-6). 지금은 `DeliveryPlanTableTest`가 유일한 전수
   증거다.
4. **정책 데이터 값(환경→모드 매핑, `maskedSuffixLength`)은 착수 placeholder다** —
   승인 대기(`OPEN-4E-POLICY-VALUES`, `policy-values.md`).
5. **`RouteDirectory`·`ContentRenderer` 구현은 이 slice에 없다** — port만 있다.
   owner isolation의 실제 보장(구현이 다른 owner의 route를 안 섞는가)은 어댑터
   결함 표면이라 이 slice 밖(위협 모델 「방어하지 않는다」).

## rollback

정본은 `rollback.md`.
