# M6/6F-7 — checklist.md

계약: `reports/evidence/m6/6f7/scope.md`(D-6F7-1~6). 실측 HEAD: `009ec6bd`.

## 완료 조건 대조

- [x] `NotificationRequestPort`의 production 구현(`OutboxNotificationRequestPort`,
  `workflow` 모듈) — outbox 기반 재사용, 신설 어댑터 패키지 없음, 마이그레이션 없음
  (D-6F7-1).
- [x] payload가 판정·근거를 원시 값으로 나른다(D-6F7-2) — `NotificationRequestedPayload`
  ·`NotificationEvidencePayload`.
- [x] idempotencyKey는 noticeId 고정, 멱등 미주장(D-6F7-3).
- [x] aggregateId=noticeId 기반, aggregateVersion=0, 용법 실측 완료(D-6F7-4,
  `commands.md` 「aggregateVersion 실측」).
- [x] payload 타입은 `bidvector.workflow.event`(D-6F7-5).
- [x] 우회 2는 구조로 닫지 않고 전칭 문면을 쓰지 않는다(D-6F7-6) — KDoc·test 이름
  모두 "이중 요청을 막지 않는다"로 명시.
- [x] `config/quality/gate-tests.properties`에 신설 test class 등재.

## 우회 후보와 처분 — 실측 대응

scope.md 「우회 후보와 처분」 표의 일곱 항목 대응:

1. 판정 없이 알림 요청 — 타입 폐쇄(기존 `internal constructor`) + 변이 실측으로
   회귀 감시(`commands.md` 우회 1행, `EventInternalClosureCompileTest` 케이스 7 신설).
2. 이중 요청 — **닫지 않음**(D-6F7-6), 양성 test로 "막지 않는다" 사실을 고정.
3. 쓰기 실패 삼킴 — `Failed` 매핑 + 변이 실측(`commands.md` 우회 3행).
4. payload 민감값 — 아래 "toString·민감값 실측" 절.
5. 배달 주장 문면 — KDoc·commit 메시지 전부 "요청이 outbox 에 들었다"까지만 서술,
  "발송"·"전달" 단정 문구 없음(육안 확인, `OutboxNotificationRequestPort.kt`·
  `NotificationRequestedPayload.kt` KDoc 재확인).
6. payload 복원 불가 — 변이 실측(`commands.md` 우회 6행) + 정상 경로 왕복 test 8건
  (`OutboxNotificationRequestPortTest` payload 관련 3건 + `OutboxPayloadCodecTest`
  왕복 4건 + 이스케이프 1건).
7. codec 오분류 — 변이 실측(`commands.md` 우회 7행) + 기존 `StrategyUpdated` 왕복
  test가 회귀 없음을 재확인(`OutboxPayloadCodecTest`의 별도 회귀 test 1건).

## (2b) 값 획득 축 — 실측(수정 라운드 없음, 착수 표 그대로 확정)

| 표면 | 착수 판정 | 실측 |
| --- | --- | --- |
| 알림 sink 클래스(`OutboxNotificationRequestPort`) | 경계로 처리 — 새 권한 아님 | 생성자는 `public class`(주입 셋: `OutboxPort`·`EventIdFactory`·`Clock`, 전부 기존 workflow port). 이 클래스 없이도 `OutboxPort`를 직접 쥔 자는 이미 임의 payload를 등록할 수 있다(4C-2 선례와 동일 등급) — 이 클래스는 그 권한을 `NotificationRequest`라는 **더 좁은** 입력으로 제한할 뿐 넓히지 않는다. `request()`가 받는 `NotificationRequest`는 `internal` 생성자라 `workflow` 밖에서 애초에 못 만든다(위 우회 1). **판정 유지: 경계로 처리, 실측으로 확인.** |
| 새 payload 타입(`NotificationRequestedPayload`·`NotificationEvidencePayload`) | 닫는다 — 효과 경계는 register 호출 | `data class` 둘 다 `public` 생성자(`StrategyEvent.StrategyUpdated` 선례와 동급). 생성 자체는 아무 효과가 없다 — `OutboxPort.register(EventEnvelope<*>)`에 실제로 넘어가야 DB에 쓰인다. 이 payload를 밖에서 지어도 outbox에 넣을 방법이 없다(`newEnvelope`가 `workflow` internal). **판정 유지.** |
| `OutboxPayloadCodec`의 신설 분기 | 닫는다 — `internal object`라 안 샘 | `internal object OutboxPayloadCodec`(패키지 무편집) + 새 top-level 함수 전부 `private`(파일 스코프) — `adapters` 모듈 밖은 물론 같은 모듈의 다른 패키지에서도 안 보인다. **판정 유지.** |

## toString·민감값 실측(scope.md 우회 4, 6A-1 `data class` toString 교훈)

`NotificationRequestedPayload`·`NotificationEvidencePayload`는 둘 다 `data class`라
Kotlin이 `toString()`을 합성한다 — 6A-1에서 **아무도 명시적으로 부르지 않은
`toString()`이 자격증명 평문을 흘린 것**과 같은 위험 클래스다. 이 slice의 실측:

필드 전수(`NotificationRequestedPayload.kt` 정의 그대로) — `noticeId: String`(공고
식별자, 비밀 아님) · `bidNowReasons: List<String>`(판정 임계값·확률 수치의 구조
스냅샷) · `Diagnosed`의 열두 필드(전부 `Int`·`Boolean`·모델/데이터셋 식별자
`String`·enum 이름) · `NotPredicted.reason: String`(enum 이름). **자격증명·비밀번호·
토큰·개인정보에 해당하는 필드가 타입 정의에 없다** — 6A-1처럼 "필드는 있는데
toString이 새는" 형태가 아니라 애초에 새어나갈 필드가 없는 형태다. 이 구조적
부재는 `OutboxPayloadCodecTest`·`OutboxNotificationRequestPortTest`의 왕복 test들이
모든 필드값을 `shouldBe`로 고정하고 있어(코덱 왕복 test 넷, sink test 둘) 필드
집합이 바뀌면(추가·제거) 그 test들이 즉시 깨진다. **추가로 `payload 필드 집합은
고정돼 있다` test**(`OutboxNotificationRequestPortTest`, Java reflection으로
`declaredFields` 이름 집합을 단언)가 필드 집합 자체를 직접 잠근다 — 새 필드가
`shouldBe`를 안 건드리는 형태로 추가돼도(예: 생성자 밖에서 계산되는 파생 필드)
이 test는 여전히 걸린다. **향후 slice가 evidence 필드를 확장하면(예: 원문 텍스트·
연락처 등) 이 축을 재실측해야 한다** — 알려진 제한으로 아래에 등재.

## BidNowReason·MlUnavailableReason `toString()` 축어 잠금(팀장 지적, 2라운드)

**팀장 실측 — `bidNowReasons = verdict.reasons.map { it.toString() }`·
`NotificationEvidencePayload.NotPredicted(reason = reason.toString())`가
영속 데이터인데 그 문자열을 고정하는 test가 없었다.** `MlUnavailableReason`의
이름을 바꿔도 payload 형식이 조용히 바뀌고 아무것도 안 붉는 자리였다 — 우회
판단(경계 test가 `bidvector.decision`을 막고 `internal` 생성자라 재구성도
불가능) 자체는 옳았지만 "없어야 할 것을 못 막는" 자리가 남아 있었다.

**경계 test는 main 소스만 본다(실측)** — `EventBoundaryTest`의 `sourceRoot`는
`File("src/main/kotlin/bidvector/workflow/event")`다(소스 코드 확인). test
소스 디렉터리는 그 술어의 스캔 대상이 아니다 — `OutboxNotificationRequestPortTest`
가 `bidvector.decision.BidNowReason`·`MlUnavailableReason`·`VerdictLadder`를
이미 자유롭게 import하고 있고(우회 1 test부터), 그 test class 가 `check`를
통과한다는 사실 자체가 실측이다.

**처분 ① — 소리 나게 만들었다.** `OutboxNotificationRequestPortTest`에
`expectedBidNowReasonToString`·`expectedMlUnavailableReasonToString` 두
잠금 함수를 뒀다 — 둘 다 **`else` 없는 소진 `when`**(`BidNowReason`·
`MlUnavailableReason`의 각 하위 타입을 전부 나열)이라 새 case가 추가되면
이 test 파일부터 컴파일이 깨진다. `BidNowReason` 두 case(`VerdictLadder.judge`로
정직하게 얻은 진짜 값), `MlUnavailableReason` 열한 case(전부 `data object`) 를
literal 문자열로 축어 단언한다. sink test(payload 관련)도 production 호출을
그대로 베끼지 않고 이 잠금 함수와 대조하도록 고쳤다(`verdict.reasons.map(::expectedBidNowReasonToString)`
— production 코드의 `.map{it.toString()}`을 그대로 복사하면 assertion이
자기참조가 돼 형식이 바뀌어도 항상 통과한다).

**변이 실측(버릴 clone, `git clone --no-hardlinks`, numstat 확인)** — 이름을
바꾸는 것은 `decision` 모듈 전역에 ripple 이 커서(생성 지점·`EvidenceLines.kt`
등 여러 파일이 같이 깨짐, 격리된 실측이 안 됨) 대신 **`toString()`을 명시적으로
override**해 "이름은 그대로인데 직렬화 형식만 조용히 바뀌는" 더 현실적인
시나리오로 심었다:

| 대상 | 변이 | numstat | RED |
| --- | --- | --- | --- |
| `MlUnavailableReason.TransportFailed` | `override fun toString() = "TRANSPORT_FAILED_V2"` 추가 | `2 1` | `MlUnavailableReason 전 case` test 1건 FAILED(`expected:<TransportFailed> but was:<TRANSPORT_FAILED_V2>`) |
| `BidNowReason.ForceBidOverride` | 같은 형태로 `"FORCE_BID_OVERRIDE_V2"` override 추가 | `3 1` | 2건 FAILED — `payload 는 ForceBidOverride 사유도 나른다` · `BidNowReason 두 case 의 직렬화가 축어로 고정된다` |

둘 다 원복 확인 후 clone 삭제.

**처분 ② — 진짜 종점은 도메인, OPEN 신설 요청(문면 초안)**

```
OPEN-6F7-REASON-CODE-STABILITY

무엇: BidNowReason·MlUnavailableReason(bidvector.decision)이 payload 직렬화
가능한 안정적 code 속성(예: val code: String)을 갖지 않는다 — outbox에 실리는
값이 Kotlin 합성 toString()이다.

왜 지금 못 하는가: (a) workflow.event 의 EventBoundaryTest(D-6F7-5 의 같은
게이트)가 bidvector.decision 을 main 소스에서 이름으로 참조하는 것을 막는다
(b) BidNowReason 의 두 하위 타입은 internal 생성자라 workflow 도 재구성 못
한다 — sink 는 값을 toString()으로만 투영할 수 있다. 둘 다 도메인 타입 변경
없이는 못 푼다.

①이 무엇을 대신하는가: OutboxNotificationRequestPortTest 가 BidNowReason
두 case·MlUnavailableReason 열한 case 의 toString() 출력을 literal 로 축어
단언하고, 소진 when(else 없음)으로 새 case 추가 시 컴파일이 깨지게 한다.
**이것은 "형식이 안정적이다"를 만들지 않는다** — 누군가 decision 모듈에서
toString() 을 override 하면(변이 실측으로 재현) 그 커밋에서 이 test 가
RED 로 잡지만, 이미 영속된 옛 outbox 행의 형식을 고치거나 마이그레이션하지
않는다. 이 test 는 **회귀를 소리 나게 할 뿐 형식을 안정시키지 않는다.**

누가 닫는가: decision 모듈에 code: String 속성을 추가하는 slice(도메인
변경, M6/6F-7 밖) — 그 뒤 이 payload 도 code 기반으로 재작성해야 한다.
```

## 알려진 제한

- **`OPEN-STR-12`**(발송 채널·렌더링) — 무변경, 이 slice 밖.
- **`OPEN-6F-ASSEMBLY`**(app DI 조립) — 무변경, 이 slice는 포트 구현만 낸다.
- **4C-1의 outbox `idempotency_key` UNIQUE 부재** — 이 slice가 수령만 한다(D-6F7-3·6).
  같은 notice가 여러 evaluate run에서 반복 `BidNow`를 받으면 매번 새 outbox 행이
  생긴다. 구조로 닫지 않기로 결정됨(D-6F7-6).
- **contractGate 환경 결함(신규 등재, 이 slice 발견)** — 이 머신의 `~/.internal-bin/git`
  래퍼가 존재하지 않는 `/opt/homebrew/bin/git`을 가리켜 `buf breaking`의 내부
  `git clone`이 실패한다. `git stash`로 이 slice의 변경을 전부 치운 상태에서도
  동일 실패가 재현되어 **이 slice의 diff와 무관함을 확정**(`commands.md`). 이
  slice는 `.proto`/`contracts/`를 건드리지 않는다. 수정은 머신 환경(`~/.internal-bin/git`)
  소관이라 이 slice의 in_scope 밖 — 팀장에게 별도 보고.
- **payload evidence 필드 확장 시 toString 재실측 필요** — 위 절 참고.

## 리뷰 요청 조건 점검

- [x] 구현 diff 커밋, base/head 고정 — `git status --porcelain -- <in_scope 8경로>`
  결과 없음(clean). 양성 대조: `config/quality/gate-tests.properties`에 한 줄을
  일부러 지웠다 되돌려 porcelain이 `M`을 잡는 것을 확인 후 원복.
- [x] acceptance_commands 셋 — `commands.md`(`check`는 환경 결함 하나를 제외하고
  전건 통과, `qualityBaseline` 통과, `one-command-check.sh`는 같은 환경 결함으로
  Kotlin 첫 step에서 중단 — Python 단계 무관).
- [x] test/lint/type/architecture/contract 관련 명령 — 이 slice가 닿는 전 gate
  개별 실행 통과(`commands.md`). `contractGate`만 환경 결함으로 제외, 근거는 위.
- [x] 변경된 fixture 없음(N/A) · 정책 version 무변경(N/A, 마이그레이션 없음).
- [x] 알려진 제한·rollback — 위 절, `rollback.md`.
- [x] 비밀값 스캔 통과 — `commands.md`.

## 리뷰 레인(scope.md)

- `migration-reviewer`: 대상 아님(마이그레이션 없음).
- `contract-keeper`: 대상 아님(공개 HTTP 계약 없음).
- `privacy-gate`: scope.md가 요구 — **이 구현 레인은 실행하지 않는다**(저작과 검토는
  다른 패스). 다음 레인(verifier·code-reviewer 병렬) 소관.
- `code-reviewer`(`model: sonnet` 명시)·`verifier`: 다음 레인 소관.
- Codex: 대상 아님(되돌리기 어려운 경로 아님, scope.md 명시).
