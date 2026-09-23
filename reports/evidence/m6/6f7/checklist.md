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
