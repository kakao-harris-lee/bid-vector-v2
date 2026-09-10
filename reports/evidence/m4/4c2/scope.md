# Slice 계약 — M4 / 4C-2 · outbox·inbox 영속과 원자적 커밋

> **지위**: **착수 계약 2026-09-10.** 4C 의 뒤쪽(4C-1 이 어휘·port·dedup 판정, 4C-2 가 그것을 **실제로 잰다**).
> 4C-1 은 실 저장이 없어 「등록이 도메인 write 와 같은 트랜잭션」을 **문면으로만 선언**했고 `OPEN-4C1-TX-CONTRACT-UNVERIFIED` 로 남겼다.
> **이 slice 가 그 선언을 강제로 바꾼다.**
>
> **전제가 방금 갖춰졌다** — `main` 병합(2026-09-10, `edd57fb`)으로 M3 의 V1~V5 마이그레이션과 3D 의 Testcontainers PostgreSQL 기반이 이 브랜치에 들어왔다.
> 4C-1 착수 때 4C 를 둘로 나눈 이유가 정확히 그 충돌이었고, 이제 **V6** 를 잡을 수 있다.

```yaml
milestone: m4
slice: 4c2-outbox-persistence-and-atomicity
base_sha: ff210c187bb7885da7639de0434d815e59e7f32a   # 착수 시점 재고정 — `m4/2026-09-08` = `m4-4d/2026-09-10` 의 HEAD(4D-1·4E 가 들어온 뒤).
                                                     # 계약 작성 시점의 `edd57fb` 는 4D-1 종결 병합으로 낡았다(값을 그대로 두면 rollback 목록이 남의 slice 를 되돌린다)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD` — **값을 박지 않는다**(evidence 커밋 자신이 head 가 되어 즉시 낡는다, 4A r1 B-3).
branch: m4/2026-09-08
in_scope:
  - adapters/src/main/resources/db/migration/V6__outbox_inbox.sql   # outbox·inbox 테이블
  - adapters/src/main/kotlin/bidvector/adapters/persistence/**       # 트랜잭션 경계 + `JdbcRawObservationStore` 참여 개조(D-4C2-1 (b)) — 3D 의 다른 넷은 만지지 않는다
  - adapters/src/main/kotlin/bidvector/adapters/event/**             # **신규 패키지**(설계 검토 (4)-①) — JDBC `OutboxPort`·`InboxPort`·`EventIdFactory` 구현. `persistence` 에 둘 수 없다:
                                                                     # `PersistenceAdapterDependencyTest`(게이트 등재)가 그 패키지의 `bidvector.workflow`·`bidvector.strategy` import 를 막는다. 3D allow-list 를 넓히지 않는다
  - adapters/src/test/kotlin/bidvector/adapters/event/**             # 위의 통합 test + 자기 dependency test + 등재 완전성 test(ml 관례)
  - adapters/src/test/kotlin/bidvector/adapters/persistence/**       # 원자성 실측 + 3D 스냅샷 래칫 기대표 갱신(D-4C2-2, 추가만) + `PersistenceTestSupport` truncate 목록
  - workflow/src/main/kotlin/bidvector/workflow/event/**             # **조건부·최소** — 트랜잭션 경계 port 가 필요하면 그것만. 어휘·전이표·봉투는 손대지 않는다
  - workflow/src/test/**                                             # 위 변경의 회귀
  - config/quality/gate-tests.properties                             # `gate.tests.adapters` 확장
  - docs/discovery/capability-map.md                                 # `OPEN-4C1-TX-CONTRACT-UNVERIFIED` 처리 표시만
  - milestone-4.md
  - reports/evidence/m4/4c2/**
out_of_scope:
  - db-scheduler 배선·워커 루프·`onFailure`/`onDeadExecution`      # `OPEN-ADR-13` 은 **열어 둔다**(운영자 결정 2026-09-10) — 스케줄러를 실제 배선할 때 닫는다
  - lease port·어댑터(`OPEN-ADR-12`)                                 # 후속
  - 편집 세션 테이블(4A 인계)·run 단위 식별자(4B-2 인계)             # 후속 — 이 slice 는 **원자성 물음**에 집중한다
  - 실 배달·sender·렌더링(4E) · 실 ML 호출(4D)
  - 4C-1 의 어휘·전이표·봉투·dedup **판정 로직**                      # 종결 산출물. 필요한 것이 없으면 멈추고 보고
  - decision/** · strategy/** · qualification/** · procurement/** · shared-kernel/**
  - V1~V5 마이그레이션 편집                                           # append-only. 되돌림은 V7 로
acceptance_commands:
  - "S-0  d=$(mktemp -d) && git clone --quiet --no-hardlinks --branch m4/2026-09-08 --single-branch <repo> \"$d/repo\" && (cd \"$d/repo\" && ./gradlew --no-build-cache clean check)"
  - "S-1  ./gradlew --no-build-cache clean check"
  - "S-2  ./gradlew :adapters:test"      # Testcontainers — Docker 부재는 **실패**다(ADR 0004 D-1, 3D 관례)
  - "S-3  ./gradlew :adapters:moduleDependencyGate :adapters:sizeGate :adapters:cpdCheck"
  - "S-4  ./gradlew :app:test"           # 필터 없이 전건(4C-1 L-7)
  - "S-5  ./gradlew qualityBaseline"
  - "S-6  ./gradlew :app:gateExecutionGate"   # S-4 와 **별도 호출**
rollback: |
    **정본은 `reports/evidence/m4/4c2/rollback.md`.** 경로 한정 `git restore --source=<base_sha> --staged --worktree -- <in_scope 경로 개별 인자>`.
    **공유 파일**(`gate-tests.properties`·`capability-map.md`·`milestone-4.md`)은 **커밋 해시 hunk 격리**로 자기 몫만 걷는다(evidence-pack 2026-09-09) —
    「내 줄 사라짐」과 **「남의 줄 남음」을 둘 다** 실측한다. **마이그레이션은 되돌리지 않고 V7 로 무력화**한다(append-only, 3D 관례) — 그 명령도 임시 clone 에서 실측한다.
```

작성: 2026-09-10, 세션 모델 단독. 근거: `milestone-4.md` 4C·완료 조건 · `ADR 0005` D-1·D-2·D-3·D-5·D-11 · `ADR 0004`(persistence) · 4C-1 계약과 종결 기록 ·
`capability-map.md` NOTI-05·OPS-03 · 조사 `_workspace/m4-4b2/01_scout_composition.md`(legacy outbox 둘의 성숙도 차이·「실패한 전송을 completed 로 닫는다」).

---

## 운영자 결정 — 2026-09-10 착수 승인

| ID | 결정 | 귀결 |
| --- | --- | --- |
| **범위** | **outbox·inbox 영속과 원자성까지** | 편집 세션·run 식별자·스케줄러 워커는 후속. 이 slice 는 **원자성 물음 하나**에 집중한다 |
| **claim 경합** | **`SELECT … FOR UPDATE SKIP LOCKED`** | PostgreSQL 단일 채택(ADR 0004)이라 엔진 중립을 사려고 표현력을 잃지 않는다. **워커 사망 시 트랜잭션 종료로 잠금이 즉시 해제**되어 `Claimed` 잔존이 구조적으로 줄어든다 — OPS-01 의 advisory lock 논거와 같은 갈래 |
| **`OPEN-ADR-13`** | **열어 둔다** | ADR 0005 D-11 의 「기본값에 맡기지 않는다」는 **스케줄러를 실제 배선할 때** 할 일이다. 영속만 하는 이 slice 에는 닫을 근거가 없다 |

---

## 하네스 레인 변경 (상시 절)

`git log --oneline <base_sha>..HEAD -- CLAUDE.md .claude/` — 착수 시점 **없음**(base == HEAD). 리뷰 요청 시점에 갱신. rollback 대상 아님.

---

## 이 slice 가 하는 일

| # | 일 | 승인 문면 |
| --- | --- | --- |
| ① | **V6 스키마** — outbox·inbox 테이블. 4C-1 의 `OutboxEntryState`(`Pending → Claimed → Delivered \| Failed(final) \| Isolated`)와 봉투 아홉 필드를 **그대로** 싣는다. 어휘를 다시 정의하지 않는다 | 4C 「DB state 와 outbox 의 atomic commit」 · `data-dictionary.md` §2.2.5(4C-1 이 채운 값) |
| ② | **원자적 커밋 — 이 slice 의 존재 이유** — 도메인 write 와 outbox 등록이 **같은 트랜잭션**에서 커밋된다. **둘 중 하나만 남는 상태가 관측되지 않음**을 실 DB 로 증명한다(중간 실패 주입) | ADR 0005 D-2 · `OPEN-4C1-TX-CONTRACT-UNVERIFIED` 종결 목표 |
| ③ | **crash-after-commit** — 커밋 뒤 프로세스가 죽어도 **등록된 것이 사라지지 않고**, 재기동이 그것을 다시 집는다. 3D 의 Testcontainers 관례로 실측 | 4C 「crash-after-commit test」 · M4 완료 조건 |
| ④ | **claim 경합** — `SELECT … FOR UPDATE SKIP LOCKED`. 워커 둘이 동시에 집어도 **같은 행이 두 번 배달되지 않는다**. 워커 사망 시 잠금이 즉시 풀려 다른 워커가 집는다 — 그 거동을 test 가 관측한다 | 운영자 결정 · NOTI-05 · OPS-03 |
| ⑤ | **consumer inbox / dedup 의 영속** — 4C-1 의 순수 판정을 실 테이블 위에 올린다. **같은 `idempotencyKey` 를 두 번 적재해도 행 1개**(4C-1 이 L-2 로 알려진 제한에 남긴 등록 측 dedup) | 4C 「consumer inbox/dedup」 · NOTI-05 |
| ⑥ | **duplicate·out-of-order 수렴** — 실 DB 위에서 재현. 4C-1 이 순수 함수로 증명한 것을 **저장 층에서도** 잰다 | M4 완료 조건 |
| ⑦ | **재시도는 여전히 없다** — 이 slice 는 어느 계층에도 재시도를 넣지 않는다. `Isolated` 는 재실행 없는 격리다. 스케줄러 배선(`OPEN-ADR-13`)이 올 때 **부작용 유형별 명시 지정**으로 닫는다 | ADR 0005 D-11 「한 부작용에 재시도 계층은 하나」 |

**만들지 않는 것**: 스케줄러 워커 루프 · lease · 편집 세션 테이블 · run 식별자 · 실 배달·sender · 재시도 · 4C-1 어휘 재정의.

---

## 위협 모델 — 4C-2 고유 경계

**방어한다**: (a) 도메인 write 와 outbox 등록이 **갈라지는 것**(② — 유실 창) (b) 커밋된 것이 재기동에서 사라지는 것(③) (c) 같은 행의 **이중 배달**(④) (d) 같은 키의 이중 적재(⑤)
(e) 순서·중복 수신에서 상태가 갈리는 것(⑥) (f) 재시도가 몰래 들어오는 것(⑦) (g) **legacy 의 「실패한 전송을 `completed` 로 닫는다」**(조사 C-4)가 이 층에서 재현되는 것 — 배달 결과를 4C-2 가 **주장하지 않는다**(sender 는 4E).

**방어하지 않는다**: 실 배달의 성공 여부(4E) · 스케줄러의 실패 기본값(`OPEN-ADR-13`, 열림) · lease(`OPEN-ADR-12`) · 편집 세션·run 영속(후속) ·
**DB 관리자 권한으로 행을 직접 쓰는 주체**(3D 의 append-only·권한 게이트가 그 층을 이미 진다) · 빌드 스크립트를 임의로 쓰는 저자(2026-09-03 경계).

**우회 후보 — 값 위조 축**: (1) 어댑터가 봉투를 지어 등록 → **4C-1 이 이미 닫았다**(`restore` internal, `claim` 은 원시 행 반환). 이 slice 가 그 폐쇄를 **깨지 않는지**가 역으로 검사 대상이다
(2) 상태를 전이표 밖으로 UPDATE → DB CHECK + 어댑터가 `OutboxTransition` 만 받음 (3) 원자성을 두 트랜잭션으로 쪼갬 → 실패 주입 test (4) dedup 을 우회해 두 번 적재 → 유니크 제약 (5) `Isolated` 에서 재실행 → 그 전이가 표에 없다.

**우회 후보 — 값 획득 축 (하네스 (2b))**: 새로 public 으로 내놓는 것(JDBC 어댑터 클래스·DataSource 배선·트랜잭션 경계 port)을 전수하고 각각이 밖에 허락하는 것을 설계 검토가 표로 낸다.
**「연다」·「경계로 처리」 행도 실측 목록에 넣는다.** **`object` 커널 계수 항목**(2026-09-10 고정 항목)도 본다 — **이 slice 에서 「배달 횟수를 어떻게 세는가」가 바로 그 축이다**(4B-2 가 세 라운드를 쓴 자리).

---

## OPEN — 수령·신설

| OPEN | 4C-2 처리 |
| --- | --- |
| `OPEN-4C1-TX-CONTRACT-UNVERIFIED` | **종결 목표** — ② 가 실 DB 로 강제한다. 못 닫으면 근거와 함께 활성 유지 |
| `OPEN-ADR-13`(db-scheduler 실패 기본값) | **열어 둔다**(운영자 결정) — 스케줄러 배선 slice 가 닫는다 |
| `OPEN-ADR-12`(lease 어댑터) | 후속 |
| `OPEN-4B2-2`·`-4`·`-6`(run item 영속·중복 계수·`running` 잔존) | **후속** — 이 slice 는 run 영속을 하지 않는다. 다만 ④ 가 `running` 잔존의 **기제**를 바꾸므로 그 관계를 evidence 에 적는다 |
| `OPEN-4B2-5`(배달 outbox drain 운영 설정) | 후속(운영 관측) |
| 4C-1 알려진 제한 「공개 sink 자기-조립」 | 이 slice 가 바꾸지 않는다 — 등재 유지 |

---

## 계약 갱신 — 2026-09-10 (Phase 2.5 설계 검토 귀결 · 운영자 결정)

설계 검토(`_workspace/m4-4c2/02_design-review.md`)가 착수 전에 계약을 강제로 바꾸는 사실 셋을
냈다. 검토가 승인 문면을 축소한 것이 아니라, **계약이 전제한 자리가 저장소에 없음**을 실측한
결과다.

| ID | 결정 | 귀결 |
| --- | --- | --- |
| **D-4C2-1** | **② 의 「도메인 write」 = `raw_observation` append** (갈래 b) | 계약 ② 는 실 production 쌍(`strategies.save` + `events.publish`)을 겨눴지만 **그 저장 쪽이 없다** — 4A 가 `StrategyRepository` 를 port 로만 두고 인계했고(4A KDoc 「원자적 저장+발행+세션전진은 4C 트랜잭션 outbox 소관」), 3D repository 다섯은 각자 `dataSource.connection` 을 잡아 **두 port 를 한 트랜잭션에 묶을 자리 자체가 없다**(실측). 트랜잭션 경계를 세우고 `JdbcRawObservationStore` **하나**를 additive 하게 참여시켜(주 생성자 `ConnectionSource`, 기존 `DataSource` 생성자는 보조로 위임 — 호출부·경계 밖 거동 무변경) 실 도메인 write 와의 원자성을 실측한다. **전략 영속(갈래 a)은 만들지 않는다** — 정책 version drift 판정이라는 새 도메인 물음을 이 slice 에 끌어들이지 않는다(후속 slice) |
| **D-4C2-2** | **3D 스키마 스냅샷 래칫 「추가만」 예외 승인** | `CleanMigrationCheckTest.expectedCheckCountByTable` 은 전체 public 테이블의 **정확 일치 map** 이고 `CleanMigrationTest` 도 같은 성격이라, V6 가 테이블을 더하면 반드시 기대표가 는다. 3E(2026-09-08)·3F(D-3F-6)와 같은 처분 — **기존 테이블의 값은 한 자리도 바꾸지 않고 신규 행만 더한다**는 것을 evidence 가 실측한다. 게이트 술어 자체는 바꾸지 않는다 |
| **D-4C2-3** | **어댑터 패키지 분리** — `bidvector.adapters.event` 신설 | `PersistenceAdapterDependencyTest`(게이트 등재)가 `persistence` 패키지의 `bidvector.workflow`·`bidvector.strategy` import 를 막는다. outbox 어댑터는 둘 다 봐야 하므로 koneps·ml·extraction 관례대로 **자기 dependency test 를 가진 새 패키지**에 둔다. **3D allow-list 를 넓히지 않는다**(게이트를 여는 수정) |

**base_sha 재고정** — 계약 작성 시점 `edd57fb` 는 4D-1 종결 병합(`ff210c1`)으로 낡았다. 위 YAML
의 값이 정본이다.

### 신설 OPEN

| OPEN | 내용 |
| --- | --- |
| `OPEN-4C2-MARK-UNEXERCISED` | `markDelivered`/`markFailed`/`markIsolated` 는 이 slice 에서 **port 수준으로 실행되지 않는다** — 배달자가 없고(경계), `adapters` test 도 인자를 만들 수 없다(`OutboxTransition` 하위 타입이 `workflow` 의 `internal constructor`). 전이 UPDATE 의 효과와 `WHERE state` 거부는 DB 층에서 실측하고, **통로를 열어 해결하지 않는다** — 그것이 legacy C-4(「실패한 전송을 `completed` 로 닫는다」)를 이 층에 재현하는 문이다. 배달 오케스트레이션 slice 로 인계 |
