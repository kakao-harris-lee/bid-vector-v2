# Slice 계약 — M4 / 4C-1 · 이벤트 봉투·outbox 어휘·dedup

> **지위**: **착수 계약 2026-09-09.** `milestone-4.md` 4C 를 둘로 나눈 앞쪽이다.
>
> **왜 나눴나 (운영자 결정 2026-09-09, 실측 근거).** `main` 의 M3 후속 레인이 같은 시각 3F(개찰완료 축)를 진행하며
> `adapters/src/main/resources/db/migration/V5__opening_complete_axis.sql` · `adapters/**/persistence/**` · `config/quality/gate-tests.properties` 를
> 만지고 있다(`git log --name-only` 실측). 이 브랜치의 base 에는 V4·V5 가 없어 4C 가 outbox 테이블을 만들면 **Flyway 버전 번호가 정면 충돌**한다.
> 그래서 **4C-1 은 `workflow` 모듈 한정**(충돌 0)으로 어휘·봉투·port·dedup 을 세우고, **4C-2**(Flyway·persistence 어댑터·DB↔outbox 원자성·
> crash-after-commit)는 M3 후속이 닫혀 `main` 을 병합한 뒤로 미룬다. 4B 가 기다리는 봉투 어휘는 4C-1 에서 나오므로 임계 경로는 산다.

```yaml
milestone: m4
slice: 4c1-event-envelope-and-outbox-vocabulary
base_sha: 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, 4A r1 B-3).
branch: m4/2026-09-08
in_scope:
  - workflow/src/main/kotlin/bidvector/workflow/event/**   # EventEnvelope·식별자 값 타입·OutboxEntryState 전이표·OutboxPort·InboxPort·EventIdFactory·OutboxEventSink
  - workflow/src/test/**                                    # 전이표 전수·dedup property(duplicate·out-of-order)·봉투 불변식·값 획득 축 경계 test
  - config/quality/gate-tests.properties                    # `gate.tests.workflow` 확장
  - docs/discovery/data-dictionary.md                       # §2.2.5 만 — 어휘 자리를 승인된 값으로 채우고 `OPEN-OPS-10` 종결(운영자 승인 2026-09-09)
  - docs/discovery/capability-map.md                        # §14.2 의 `OPEN-OPS-10` 상태 갱신만
  - workflow/src/main/kotlin/bidvector/workflow/strategy/Ports.kt        # **좁은 예외**(운영자 승인 2026-09-09) — `EventSink.publish(event, actor)` 한 줄
  - workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt   # 같은 예외 — 호출부 한 줄
  - workflow/src/test/**                                                # 위 둘의 회귀 test 포함(이미 in_scope)
  - milestone-4.md
  - app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt   # **갱신 2026-09-09** — `EventSink` 확장의 기계적 파급(하단 사유)
  - reports/evidence/m4/4a/scope.md                                     # 계약 갱신 절 append(이미 기록됨)
  - reports/evidence/m4/4c1/**
out_of_scope:
  - adapters/**                                             # persistence·db-scheduler·실 sender — 4C-2. **main 의 M3 후속 레인이 지금 만지는 경로다**
  - "**/db/migration/**"                                    # Flyway 버전 충돌 회피 — 4C-2 가 병합 뒤 잡는다
  - DB↔outbox 원자적 커밋·crash-after-commit·claim 경합       # 실 저장이 있어야 재는 것들 — 4C-2
  - lease port·어댑터(`OPEN-ADR-12`·ADR 0005 D-10)           # 4C-2/후속
  - 나머지 이벤트 넷의 payload 타입                            # `NoticeQualified`·`PredictionRequested`·`DecisionPrepared`·`NotificationRequested` 는 각 도메인 소유 → 4B. 봉투가 제네릭이라 그때 그대로 실린다
  - 4A 산출물(workflow/strategy/**) 편집 — **위 두 파일의 좁은 예외를 뺀 전부**   # 전이 커널·상태 기계·통로 타입·정책은 손대지 않는다.
                                                              # `EventSink` actor 확장은 설계 검토가 찾아 운영자가 승인한 예외이고 4A scope.md 하단에 갱신 사유가 있다
  - strategy/**·M2·M3 경로 · 그 밖의 승인 문서
acceptance_commands:
  - "S-0  d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch <repo> \"$d/repo\" && (cd \"$d/repo\" && ./gradlew --no-build-cache clean check)"
  - "S-1  ./gradlew --no-build-cache clean check"
  - "S-2  ./gradlew :workflow:test"
  - "S-3  ./gradlew :workflow:moduleDependencyGate :workflow:sizeGate :workflow:cpdCheck"
  - "S-3b ./gradlew :workflow:test --tests '*EventBoundaryTest*'"
  - "S-4  ./gradlew :app:test --tests '*Conformance*'"
  - "S-5  ./gradlew qualityBaseline"
  - "S-6  ./gradlew :app:gateExecutionGate"
rollback: |
    **정본은 `reports/evidence/m4/4c1/rollback.md`.** 경로 한정 `git restore --source=<base_sha> --staged --worktree -- <in_scope 경로 개별 인자>`,
    목록은 `git diff --name-status <base>..HEAD` 로 기계 산출, 임시 clone 에서 exit 0 · D/M 수 · in_scope diff 비어 있음 · **되돌린 트리의 compile · test** 까지 실측.
    **승인 문서 둘(`data-dictionary.md`·`capability-map.md`)은 in_scope 이므로 되돌림 대상이다** — 하네스 경로(`CLAUDE.md`·`.claude/**`)는 아니다.
```

작성: 2026-09-09, 세션 모델 단독. 근거: `milestone-4.md` 4C · `prep/m4-prep.md` D-M4-4·D-M4-5 · `ADR 0005` D-1·D-2·D-3·D-5·D-9·D-11 · `data-dictionary.md` §2.2.5 ·
`capability-map.md` NOTI-05·OPS-03·`OPEN-OPS-10`·`OPEN-DIC-07`·`OPEN-DIC-09` · 4A 계약과 그 종결 기록.

---

## 운영자 결정 — 2026-09-09 착수 승인

| ID | 결정 | 귀결 |
| --- | --- | --- |
| **분할** | **4C-1 / 4C-2** | 4C-1 은 `workflow` 한정(충돌 0). 4C-2 는 `main` 병합 뒤 |
| **D-M4-5** | **(a) `Pending → Claimed → Delivered \| Failed(final) \| Isolated`** + **`OPEN-OPS-10` 종결** | `Claimed` 에서 워커 사망은 `Isolated`(수동 검토·재실행 없음 — at-most-once, NOTI-05·OPS-03). `Failed(final)` 은 최대 시도 소진. 승인 문서 `data-dictionary.md` §2.2.5 를 이 값으로 채운다 |
| **D-M4-4** | **(a) 제네릭 봉투** + **`EventId` 생성은 port** | 봉투는 `EventEnvelope<P>`(아홉 필드), `StrategyUpdated` 봉투는 `actor` 필수. id 생성 기제(UUIDv7 등)는 4C-2 어댑터가 고른다 — **`workflow` 의 「외부 좌표 0」**(4A 의 구조적 사실)을 지킨다 |
| **이벤트 범위** | **`StrategyUpdated` 하나만 배선** | 나머지 넷은 payload 가 각 도메인 소유라 4B. 그중 `procurement` 는 지금 `main` 레인이 만지는 경로다 |

---

## 하네스 레인 변경 (상시 절)

`git log --oneline 4ec4e504db28b743d2cbb0ad4df5e4dbfbc98599..HEAD -- CLAUDE.md .claude/`

- 착수 시점: **없음**(base == HEAD). base 는 4A 종결 기록 뒤이므로 4A 범위의 하네스 커밋(`97d746d` Phase 2.5 (2b) 신설)은 이 range 밖이다.
- **리뷰 요청 시점(구현 완료, 2026-09-09) 재확인 — 1건**: `de99ddb harness(evidence-pack):
  양성 대조에서 git checkout -- 금지, 비파괴 절삭으로`(`.claude/skills/evidence-pack/SKILL.md`·
  `CLAUDE.md`). **정정(verifier B-3, 2026-09-09) — 시점 서술이 틀렸었다.** 이전 판은 이
  커밋이 산출물 커밋(`13cf0f6`) **이후**라고 적었으나 실측(`git merge-base --is-ancestor
  de99ddb 13cf0f63da18e13f0e2befd519710a8635742004 && echo ancestor` → `ancestor`)은
  **그 반대**를 보인다 — `de99ddb`는 `13cf0f6`의 **조상**(21f9a4e와 13cf0f6 사이, 즉 이
  slice의 구현 착수 **이전**)이다. 다른 세션(팀장)이 이 slice의 base(`4ec4e504`)와
  구현 커밋 사이에 붙인 하네스 개정이라는 사실 자체는 바뀌지 않는다 — slice 산출물이
  아니며 in_scope 밖, rollback 대상이 아니다. 같은 range 에 `4e07682`(4B-1 slice 계약
  신설, `reports/evidence/m4/4b1/scope.md` 만 추가)도 있으나 이건 하네스 경로가 아니라
  다음 slice 의 계약 파일이라 이 절의 대상이 아니다(별도 slice, 이 구현자가 손대지
  않았다).
- rollback 은 in_scope 경로 한정이라 하네스 경로를 되돌리지 않는다.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **이벤트 봉투** — `EventEnvelope<P>(eventId, aggregateId, aggregateVersion, occurredAt, correlationId, causationId?, idempotencyKey, actor?, payload: P)`. payload 는 도메인 sealed(1E `StrategyEvent` 등)이고 **봉투의 소유는 `workflow`**. `StrategyUpdated` 봉투는 **타입 수준으로 `actor` 를 요구**한다(전용 생성 경로) — 「누가 바꿨나」 기록(`OPEN-STR-04` 실물) | 4C 「event id, aggregate version, idempotency/correlation/causation id」 · D-M4-4 (a) · `OPEN-DIC-07`·`OPEN-DIC-09` |
| ② | **`OutboxEntryState` sealed + 명시 전이표** — `Pending → Claimed → Delivered \| Failed(final) \| Isolated`. **표에 없는 쌍은 거부이며 거부가 관측 가능**(4A ① 과 같은 형태). `Delivered`·`Failed`·`Isolated` 는 종단 | `OPEN-OPS-10` · `data-dictionary.md` §2.2.5 「sealed 어휘와 명시 전이표」 · NOTI-05 · OPS-03 |
| ③ | **outbox port** — `OutboxPort.register(envelope)` · `claim(limit)` · `markDelivered/markFailed/markIsolated`. **등록이 도메인 write 와 같은 트랜잭션이라는 계약은 문면으로 선언하되 강제는 4C-2**(실 구현이 있어야 잰다). 구현은 test fake 만 | ADR 0005 D-2 「도메인 write 와 부작용 등록은 같은 트랜잭션」 · D-9 「도메인은 전달 기제를 모른다」 |
| ④ | **consumer inbox / dedup** — `InboxPort` + **순수 판정 함수**: 같은 `idempotencyKey` 재수신은 **효과 0**. duplicate·out-of-order 수신에서 상태가 **수렴**함을 property 로 단언 | 4C 「consumer inbox/dedup」·「duplicate, out-of-order test」 · M4 완료 조건 |
| ⑤ | **재시도 계층은 하나** — 4C-1 은 **어느 계층에도 재시도를 넣지 않는다**. `Isolated` 는 재실행 없는 격리다. `onFailure`/`onDeadExecution` 명시 지정은 4C-2 의 db-scheduler 배선이 진다(`OPEN-ADR-13`) | ADR 0005 D-11 「한 부작용에 재시도 계층은 하나」·「기본값에 맡기지 않는다」 |
| ⑥ | **생성 기제는 port** — `EventIdFactory`(+ 4A `Clock` 재사용). `workflow` 는 시각도 난수도 직접 잡지 않는다. 실 기제는 4C-2 어댑터 | D-M4-4 (a) · `architecture-policy` 의 `java.util.Random` 금지 가족과 같은 갈래 · 4A 「workflow 외부 좌표 0」 |
| ⑦ | **4A 의 `EventSink` 를 실제로 구현** — `OutboxEventSink` 가 payload 를 봉투에 싣고 `OutboxPort.register` 로 넘긴다. **4A 인터페이스는 바꾸지 않는다.** 4A 가 남긴 「발행 비원자성」 제한의 앞쪽 절반(봉투·등록 경로)이 여기서 서고 **원자성은 4C-2** | 4A 알려진 제한 인계 · STR-07 |
| ⑧ | **승인 문서 갱신** — `data-dictionary.md` §2.2.5 의 「어휘 자리만 둔다」를 승인된 값 집합·전이표로 채우고 `capability-map.md` §14.2 의 `OPEN-OPS-10` 을 종결로 표시 | 운영자 승인 2026-09-09 |

**만들지 않는 것**: 마이그레이션 · persistence 어댑터 · db-scheduler 배선 · 실 원자성 · lease · 나머지 이벤트 넷 payload · 재시도 · 실 발송.

---

## 위협 모델 — 4C-1 고유 경계

**방어한다**: (a) 전이표 밖 outbox 전이의 조용한 성공(② 거부 관측) (b) 같은 `idempotencyKey` 의 이중 효과(④) (c) 봉투 필수 필드 누락(① 타입)
(d) `StrategyUpdated` 가 actor 없이 실리는 것(① 전용 생성 경로) (e) `workflow` 가 시각·난수·프레임워크를 직접 잡는 것(⑥ port + 외부 좌표 0)
(f) 재시도가 두 계층에 생기는 것(⑤ — 4C-1 에 재시도가 아예 없다).

**방어하지 않는다**: DB↔outbox 원자성과 crash-after-commit(4C-2 — **실 저장 없이는 잴 수 없다**) · claim 경합 · 실 배달 · lease · 나머지 이벤트 넷의 payload 옳음 ·
**빌드 스크립트를 임의로 쓰는 저자**(2026-09-03 경계).

**우회 후보 — 값 위조 축**: (1) `EventEnvelope` 직조·`copy` → `internal constructor` + `@ConsistentCopyVisibility`(4A 관례) (2) `OutboxEntryState` 를 전이 함수 밖에서 바꿈 → 전이는 함수 하나
(3) `idempotencyKey` 를 매번 새로 생성해 dedup 무력화 → 키는 봉투에서 오고 봉투는 등록 시 고정 (4) `actor` 없이 `StrategyUpdated` 봉투 → 전용 생성 경로 (5) 재시도를 sink 안에 넣음 → 호출 횟수 계수 test.

**우회 후보 — 값 획득 축 (하네스 (2b), 2026-09-09 신설 — 4A 가 이 축을 안 세워 두 라운드를 더 돌았다)**: 이 slice 가 **새로 public 으로 내놓는** 타입·최상위 함수·프로퍼티·반환값을 전수하고
각각이 밖에 허락하는 것을 설계 검토에서 표로 낸다. **4A 의 결론을 그대로 물려받는다** — 커널 함수는 `internal`, 통로가 필요한 값은 `internal constructor`,
port 인터페이스만 public. 특히 `OutboxPort` 의 `mark*` 계열은 「상태를 임의로 바꾸는 권한」이므로 인자를 전이 결과 타입으로 받을지 실측으로 판정한다.

---

## OPEN — 수령·신설

| OPEN | 4C-1 처리 |
| --- | --- |
| `OPEN-OPS-10` | **종결** — D-M4-5 (a) 어휘·전이표를 `data-dictionary.md` §2.2.5 에 등재 |
| `OPEN-DIC-07`·`OPEN-DIC-09` | ① 봉투가 수령 |
| `OPEN-ADR-13`(db-scheduler 실패 기본값) | **4C-2** — 4C-1 에는 스케줄러가 없다 |
| `OPEN-ADR-12`(lease 어댑터) | 4C-2/후속 |
| 신설 후보 `OPEN-4C1-TX-CONTRACT-UNVERIFIED` | ③ 의 「등록이 도메인 write 와 같은 트랜잭션」이 4C-1 에서는 **문면 선언일 뿐 강제되지 않는다**. 4C-2 가 실 저장으로 닫을 때까지 활성 |

---

## 계약 갱신 — 2026-09-09 (구현 중, 오케스트레이터 판단)

**무엇**: `app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt` 를 in_scope 에 추가(+5/−1줄).

**왜**: 운영자 승인 예외 `EventSink.publish(event, actor)` 를 반영하면 **그 인터페이스의 모든 구현체가 기계적으로 깨진다**. 4A 가 남긴 corpus 실행자의
`RecordingEventSink` 가 그 구현체라 `:app:gateExecutionGate` 가 컴파일 실패했다. **시그니처만 맞췄고 corpus 산출 로직·기대값은 무변경**이다
(실측: `:app:test --tests '*Conformance*'` 74건, 개수·값 불변).

**판단**: 실질적 scope 확장이 아니라 **이미 승인된 변경의 강제 파급**이라 slice 를 멈추지 않고 계약을 갱신한다. 구현 레인이 이 파일을 in_scope 밖으로
정직하게 신고했고(checklist §3), 그 신고가 옳았다 — 갱신은 오케스트레이터가 한다.

**범위 밖 range 잡음(선언)**: 이 slice 의 `base..HEAD` 에는 **4B-1 계약 커밋 둘**(`4e07682`·`e2bfb99`, `reports/evidence/m4/4b1/**`)과
**하네스 커밋 하나**(`de99ddb`)가 섞여 있다. 셋 다 in_scope 밖이고 이 slice 의 산출물이 아니며 **rollback 대상이 아니다**
(slice 의 커밋 집합은 range 가 아니라 in_scope 경로의 변경이다 — 2026-09-04).

