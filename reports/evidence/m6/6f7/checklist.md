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

**처분 ② — D-6F7-9 로 전면 재작성됨(정본: `scope.md` OPEN 절·D-6F7-9,
`milestone-6.md` 6F-7 문단, sink KDoc `OutboxNotificationRequestPort.kt`)**

`OPEN-6F7-REASON-CODE-STABILITY` — 남는 위험은 하나다: outbox 에 영속되는 값이
**Kotlin 합성 `toString()`** 이라 형식이 조용히 바뀔 수 있다. 이 slice 의 축어
잠금(처분 ①) + 소진 `when` 이 그것을 **「소리 나게」** 했다(형식이 바뀌면 그
커밋에서 RED) — 다만 **이미 영속된 옛 outbox 행을 고치거나 마이그레이션하지
않는다.**

**「안정적 `code` 속성으로 바꾼다」는 해법이 아니다** — `toString()` 이 함께
나르는 **임계·확률 수치를 잃기 때문**이고, 그 정보는 D-6F7-2(판정 복원
가능성)가 요구한 것이다.

**착수 판이 적은 차단 사유 둘은 거짓이었다(verifier 변이 실측, D-6F7-9)**:
경계 게이트(`EventBoundaryTest.sourceRoot`)는 `workflow/event` 만 보고
D-6F7-7 로 옮겨진 이 sink(`workflow.evaluation`)를 덮지 않는다. `BidNowReason`
하위 타입의 `internal` 생성자는 **생성**을 막지 **읽기**를 막지 않는다 —
sink 에 `when { is BidNowReason.X -> "CODE" }` 를 심어도 `:workflow:compileKotlin`
· `EventBoundaryTest` · `ArchitectureGateTest` 전부 exit 0 이었다(도메인 변경
불필요, verifier 실측). 받는 레인은 이 둘을 차단 사유로 깔지 않는다.

누가 닫는가: 이미 영속된 옛 outbox 행의 형식 마이그레이션이 필요해지는
시점의 **도메인 레인**(M6/6F-7 밖).

## 알려진 제한

- **`OPEN-STR-12`**(발송 채널·렌더링) — 무변경, 이 slice 밖.
- **`OPEN-6F-ASSEMBLY`**(app DI 조립) — 무변경, 이 slice는 포트 구현만 낸다.
- **4C-1의 outbox `idempotency_key` UNIQUE 부재** — 이 slice가 수령만 한다(D-6F7-3·6).
  같은 notice가 여러 evaluate run에서 반복 `BidNow`를 받으면 매번 새 outbox 행이
  생긴다. 구조로 닫지 않기로 결정됨(D-6F7-6).
- **`OPEN-6F7-REASON-CODE-STABILITY`** — `scope.md`에 등재됨(팀장). 위 절 참고.
- **contractGate 환경 결함(이 slice 발견) — 해소됨.** 팀장이 `~/.internal-bin/git`
  래퍼의 `exec` 경로를 고쳤다(Intel Homebrew 머신에 Apple Silicon 경로가 박혀
  있었다). `git stash`로 이 slice의 변경을 전부 치운 상태에서도 동일 실패가
  재현돼 **이 slice의 diff와 무관함을 확정**한 뒤(`commands.md`), 해소 뒤
  acceptance를 전건 재실측했다(아래).
- **아키텍처 패키지 순환(이 slice 발견, D-6F7-7로 해소) — `commands.md` 「아키텍처
  순환 발견과 수정」 절.** `contractGate`가 먼저 죽어 `ArchitectureGateTest`
  (`:app:test`)가 이 slice의 구현 착수부터 지금까지 **한 번도 실행된 적이
  없었다** — 안 돌린 게이트가 다른 게이트의 실패를 가렸다. 해소 뒤 처음
  도달하자 순환 다섯 건이 드러났고, sink를 `workflow.evaluation`으로 옮겨
  닫았다(팀장 요청대로 순환 재발 변이로 RED 재확인 완료).
- **`one-command-check.sh`의 Python(ml-engine) 단계 — 외부 네트워크(PyPI) 문제,
  이 slice와 무관.** Kotlin 두 step(`check`·`qualityBaseline`)은 exit 0로 끝까지
  돈다. Python 첫 step(`uv sync`)이 `pypi.org` 접속 시간초과로 실패한다 —
  프록시(`HTTPS_PROXY`)를 거쳐도 그 목적지에 도달하지 못하는 이 세션의
  네트워크 제약이고, 이 slice는 `ml-engine`을 전혀 건드리지 않는다.
- **payload evidence 필드 확장 시 toString 재실측 필요** — 위 절 참고.
- **`NotificationRequested` outbox 행도 6B-3(승인된 보존 기간 없음) 대상이다**
  (`privacy-gate` 권고, 계약 갱신 (3)). 이 slice는 보존 정책을 새로 만들지도
  바꾸지도 않는다 — 기존 6B-3 한계가 이 새 payload_type에도 그대로 적용됨을
  등재만 한다.
- **`excludedSamples` 코덱 왕복에서 `emptyList()`와 `listOf("")`(빈 문자열
  하나짜리 목록)를 구분하지 못한다**(`code-reviewer` LOW). `encodeReasons`가
  빈 목록과 `[""]`을 둘 다 빈 문자열로 인코딩하고 `decodeNotificationRequested`가
  `fields[1].isEmpty()`를 `emptyList()`로 되돌리기 때문 — 코덱 계층의 일반
  성질이고 오늘 두 값을 실제로 만드는 호출부가 없어 **도달 불가**다. 구조로
  닫지 않고 등재만 한다(`OutboxPayloadCodec.kt` `decodeNotificationRequested`).
- **D-6F7-11 — `SQLException` → `Failed` 매핑이 선례 `OutboxEventSink`(전파 →
  트랜잭션 롤백)와 갈린다.** 지금은 결함이 아니다(이 port가 아직 배선되지
  않았다, `OPEN-6F-ASSEMBLY`). **배선 뒤** 호출부가 `inTransaction` 안에서
  `request()`를 부르고 반환값 `Failed`를 무시하면 도메인 write만 커밋되고
  outbox 행이 없는 상태가 남는다(dual-write) — `OPEN-6F-ASSEMBLY`가 그 처리를
  받는다. 소스 KDoc(`OutboxNotificationRequestPort.kt` 클래스 문서)에도
  같은 문장을 적었다.
- **`privacy-gate` 확인 불가 — 하위 소비자의 `Failed` 로깅.** 이 port가
  반환하는 `NotificationRequestOutcome.Failed`를 호출부가 어떻게 로깅·
  처리하는지는 아직 배선 전이라 확인할 수 없다. **조립 slice
  (`OPEN-6F-ASSEMBLY`) 재확인 항목**으로 등재한다 — 그 slice가 로깅 경로에
  payload 원문(민감값 없음이 이 slice의 실측이지만)이나 예외 스택이 새지
  않는지 다시 봐야 한다.

## 리뷰 요청 조건 점검

- [x] 구현 diff 커밋, base/head 고정 — `git status --porcelain -- <in_scope 10 glob>`
  결과 없음(clean). 양성 대조: `config/quality/gate-tests.properties`에 한 줄을
  일부러 지웠다 되돌려 porcelain이 `M`을 잡는 것을 확인 후 원복.
- [x] acceptance_commands 셋 — `commands.md`. `check`·`qualityBaseline`
  **전건 exit 0**(아키텍처 게이트 포함). `one-command-check.sh`는 Kotlin
  단계 exit 0, Python 단계는 PyPI 네트워크 문제로 exit 1(이 slice 무관, 위
  「알려진 제한」).
- [x] test/lint/type/architecture/contract 관련 명령 — 이 slice가 닿는 전 gate
  전건 통과(`commands.md`), `contractGate`·`ArchitectureGateTest` 포함.
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
