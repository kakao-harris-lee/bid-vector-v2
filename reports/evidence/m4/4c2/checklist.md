# 4C-2 — 종결 체크리스트

## 사용자 승인 — 2026-09-10

**slice 4C-2 종결 승인.** 승인 둘:

1. **slice 종결** — 산출물(`V6__outbox_inbox.sql` · `TransactionBoundary`/`ConnectionSource` ·
   `bidvector.adapters.event`의 JDBC `OutboxPort`·`InboxPort`·`EventIdFactory` ·
   `JdbcRawObservationStore` 참여 개조 · 게이트 셋)을 최종 형태로 승인한다.
   `OPEN-4C1-TX-CONTRACT-UNVERIFIED`를 **종결**로 등재한다.
2. **`OPEN-3D-GRANT-PUBLIC-BLINDSPOT` 후속 slice** — 3D의 기존 권한 test 둘이 M-3과 같은
   PUBLIC 경유 사각을 갖는다. 4C-2가 만든 유효 권한 술어와 mutation 절차를 옮기는 작은
   후속 slice로 처리한다(운영자 결정 — OPEN 등재만으로 두지 않는다).

정본 서술은 `milestone-4.md` 4C 절의 종결 문단. 검증 근거는 `_workspace/m4-4c2/`의
verifier 보고서 셋(r1·r2·r3), 실행 기록은 `commands.md`, 되돌림은 `rollback.md`.

## 검증 상태

- verifier r1 `not-ready`(산출물 high 둘) → r2 `ready-for-review` → r3 `ready-for-review`.
- **재작업 카운터 1/5** — r1 한 번만 `not-ready`. r2·r3의 medium 시정은 운영자 결정에 따른
  자발적 강화라 카운터를 올리지 않는다(운영자 채택 2026-09-02 — 장부층·low·비차단은 라운드를
  막지 않는다).
- acceptance S-0~S-6 전건 exit 0(기준 head는 `commands.md`가 명시한다).

## 인계

| 항목 | 받는 곳 |
| --- | --- |
| `OPEN-4C2-MARK-UNEXERCISED` | 배달 오케스트레이션 slice |
| `OPEN-3D-GRANT-PUBLIC-BLINDSPOT` | 후속 slice(운영자 승인 2026-09-10) |
| `OPEN-ADR-13`(db-scheduler 실패 기본값) | 스케줄러 배선 slice — **열어 둔다**(운영자 결정) |
| `OPEN-ADR-12`(lease 어댑터) | 후속 |
| 3D repository 넷의 `ConnectionSource` 참여 · 전략 영속(D-4C2-1 갈래 a) | 후속 |
| `Claimed` 잔존 sweep | 후속 |
| `data-dictionary.md` §2.2.5의 낡은 OPEN 서술 | 후속(in_scope 밖) |
