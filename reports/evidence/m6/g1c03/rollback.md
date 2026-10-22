# M6/G1-C03 rollback — 되돌림 경로와 우회 실측

## 1. 되돌리는 명령

산출물만 되돌린다(evidence 는 남긴다 — 되돌린 사실의 기록이다). 경로 목록은 기계 산출이다.

```bash
git diff --name-status bee7c36f..HEAD        # 전체 목록
git restore --source=bee7c36f --staged --worktree -- \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  procurement/src/main/kotlin/bidvector/procurement/CollectionAttemptLedger.kt \
  procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt \
  procurement/src/main/kotlin/bidvector/procurement/NoticeIdentity.kt \
  procurement/src/test/kotlin/bidvector/procurement/CollectionAttemptLedgerTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/collection/CollectOpeningResultsUseCase.kt \
  workflow/src/main/kotlin/bidvector/workflow/collection/OpeningSampleFramer.kt \
  workflow/src/test/kotlin/bidvector/workflow/collection/OpeningCollectionTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/snapshot/FileAttemptLedgerTest.kt \
  app/src/main/kotlin/bidvector/app/collection/OpeningCollectionLines.kt \
  app/src/test/kotlin/bidvector/app/collection/OpeningCollectionE2ETest.kt \
  docs/discovery/capability-map.md \
  docs/runbook/m6-6g-real-collection.md \
  reports/evidence/m6/6e1/acceptance-trace.md
rm workflow/src/main/kotlin/bidvector/workflow/collection/OpeningAnchor.kt \
   workflow/src/main/kotlin/bidvector/workflow/collection/DetailSkipTally.kt \
   workflow/src/main/kotlin/bidvector/workflow/collection/InterruptedRoundCloser.kt \
   workflow/src/test/kotlin/bidvector/workflow/collection/OpeningDetailFetchGateTest.kt
```

`InterruptedRoundCloser.kt` 는 **옮긴 파일**이다 — 되돌리면 그 내용이 수집 use case 파일 안으로 함께 돌아온다
(그 파일을 `bee7c36f` 로 되돌리는 위 줄이 그것을 한다). 새 파일로 남겨 두면 같은 타입이 두 벌이 된다.

## 2. 되돌림 뒤 상태

- **마이그레이션 0** — 되돌릴 스키마가 없다.
- **원장 형식 불변** — 이 slice 가 쓴 줄과 앞 코드가 쓴 줄이 같은 형태다. 그래서 「새 코드가 쓴 원장을 옛
  코드가 읽는가」는 **물을 필요가 없는 물음**이고(형식이 하나다), 이어 돌기는 되돌림 뒤에도 같은 답을 낸다.
  그 사실 자체를 `FileAttemptLedgerTest` 의 「밖에서 쓰인 지금 형식의 원장을 그대로 읽는다」가 잠근다.
- **데이터 되돌림 없음** — 건너뜀이 더해지는 `collection_run.backoff_skipped` 는 기존 열이고, 되돌리면 그
  열에 게이트 건너뜀이 더 이상 실리지 않을 뿐 과거 행은 그대로 뜻을 지닌다.
- **정책 값도 함께 되돌아간다** — 재조회 창이 0 에서 48h 로 돌아간다. 그런데 되돌린 트리에는 이 창을 **읽는
  배선이 없으므로**(술어가 상수를 받던 판) 그 값은 아무 거동도 내지 않는다. 즉 되돌림은 48h 의 해악
  (§3-2)을 되살리지 않는다 — 값과 배선이 함께 돌아가기 때문이다.

## 3. 우회 ①~⑥ 실측

판정 표는 `checklist.md` §4-1 에 둔다 — 같은 사실을 두 자리에 적지 않는다. 여기서는 **무엇을 우회라고
불렀는지**만 남긴다(계약 설계 검토 (2)).

| ID | 우회 |
|---|---|
| ① | 호출부가 술어에 상수를 넘긴다(앞 판으로 복귀) |
| ② | 게이트 값을 무력화한다(창을 10만 시간으로) |
| ③ | 술어의 건너뜀 가지를 호출로 바꾼다 |
| ④ | 기준 시각 선언을 관측값으로 바꾼다(결측이 조용히 호출로 가는 길의 거울상) |
| ⑤ | 건너뜀을 세지 않는다 |
| ⑥ | 옛 원장을 읽지 못하게 한다 |
