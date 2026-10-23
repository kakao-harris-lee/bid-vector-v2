# M6/G1-C03 rollback — 되돌림 경로와 우회 실측

## 1. 되돌리는 명령

산출물만 되돌린다(evidence 는 남긴다 — 되돌린 사실의 기록이다). 경로 목록은 기계 산출이다.

**base 는 `a61ddff1`** 이다 — 계약의 `bee7c36f` 위에서 시작했고 2026-10-23 main(PR #81) 위로 rebase 했다. 두 base 사이의
차이는 `config/quality/vuln-allowlist.properties` 한 파일이라(이 slice 의 경로가 아니다) 아래 경로들의 되돌림 내용은 어느
base 로 해도 같다(`git diff --name-only bee7c36f a61ddff1` = 그 한 줄). 다만 목록 산출은 `bee7c36f..HEAD` 로 하면 main 의 그
파일이 섞이므로 새 base 로 한다.

```bash
git diff --name-status a61ddff1..HEAD        # 전체 목록
git restore --source=a61ddff1 --staged --worktree -- \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  procurement/src/main/kotlin/bidvector/procurement/CollectionAttemptLedger.kt \
  procurement/src/main/kotlin/bidvector/procurement/CollectionPolicy.kt \
  procurement/src/test/kotlin/bidvector/procurement/CanonicalizeTest.kt \
  procurement/src/test/kotlin/bidvector/procurement/CollectionPolicyTest.kt \
  workflow/src/main/kotlin/bidvector/workflow/collection/CollectOpeningResultsUseCase.kt \
  workflow/src/main/kotlin/bidvector/workflow/collection/OpeningSampleFramer.kt \
  workflow/src/test/kotlin/bidvector/workflow/collection/OpeningCollectionTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/snapshot/FileAttemptLedgerTest.kt \
  app/src/main/kotlin/bidvector/app/collection/OpeningCollectionLines.kt \
  app/src/test/kotlin/bidvector/app/collection/OpeningCollectionE2ETest.kt \
  app/src/test/kotlin/bidvector/app/architecture/ArchitecturePolicy.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateCatchesViolationsTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureGateTest.kt \
  app/src/test/kotlin/bidvector/app/architecture/CollectionArchitectureRules.kt \
  docs/discovery/capability-map.md \
  docs/runbook/m6-6g-real-collection.md \
  reports/evidence/m6/6e1/acceptance-trace.md
git rm -q procurement/src/test/kotlin/bidvector/procurement/AxisLastObservedAtTest.kt \
   app/src/test/kotlin/bidvector/archfixture/violating/app/RogueCandidateBuilder.kt \
   app/src/test/kotlin/bidvector/archfixture/violating/app/RogueDetailFetchCaller.kt \
   app/src/test/kotlin/bidvector/archfixture/violating/app/RogueSelfFactories.kt \
   workflow/src/main/kotlin/bidvector/workflow/collection/OpeningAnchor.kt \
   workflow/src/main/kotlin/bidvector/workflow/collection/DetailSkipTally.kt \
   workflow/src/main/kotlin/bidvector/workflow/collection/InterruptedRoundCloser.kt \
   workflow/src/test/kotlin/bidvector/workflow/collection/OpeningDetailFetchGateTest.kt
```

`InterruptedRoundCloser.kt` 는 **옮긴 파일**이다 — 되돌리면 그 내용이 수집 use case 파일 안으로 함께 돌아온다.
새 파일은 `git rm` 으로 지운다 — 작업 트리에서만 지우면 인덱스에 남아 그 트리가 컴파일되지 않는다(r1 F-3).

## 2. 되돌림 뒤 상태

- **마이그레이션 0** · **데이터 되돌림 없음**(`collection_run.backoff_skipped` 는 기존 열이고 과거 행은 뜻을 지닌다).
- **원장 형식 불변** — 이 slice 가 쓴 줄과 앞 코드가 쓴 줄이 같은 형태라 「새 코드가 쓴 원장을 옛 코드가 읽는가」는
  형식 하나의 물음이다(`FileAttemptLedgerTest` 「밖에서 쓰인 지금 형식의 원장을 그대로 읽는다」가 잠근다).
- **정책 값도 함께 돌아간다**(창 0 → 48) — 되돌린 트리에는 그 값을 읽는 배선이 없어 거동이 없다.

우회 ①~⑥ 과 r1 변이의 판정은 `checklist.md` §4-1 에만 둔다.

## 4. 실측 (2026-10-23)

실측 HEAD: `788af8ca`(마지막 산출물 커밋). 버릴 clone 에서 §1 의 명령을 문서 그대로 돌렸다.

| # | 확인 | 결과 |
|---|---|---|
| ① | 명령 exit | **0** |
| ② | 바뀐 경로 수 | M 19 · D 8 — §1 목록과 `git diff --name-status a61ddff1..788af8ca`(evidence 제외)가 같다 |
| ③ | 되돌린 트리 ↔ base | 작업 트리 ↔ 인덱스 차이 0 · 추적 안 된 파일 0 · `git diff --cached --name-status a61ddff1` 은 이 slice 의 evidence 넷(A)뿐 |
| ④⑤⑥ | compile · test · 게이트 | 되돌린 clone 에서 `./gradlew --no-daemon check` **exit 0**(16:14~16:24, 359 task — `adapters`·`app` test 실행, 나머지 모듈 test 는 같은 입력의 build cache 적중). evidence 넷이 텍스트 게이트 입력이라 트리 동일성으로 갈음하지 않았다 |
