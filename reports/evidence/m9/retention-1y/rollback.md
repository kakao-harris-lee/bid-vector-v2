# M9/retention-1y — rollback

**전제**: 이 slice 는 아직 main 에 **없다**(머지 전). 머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 revert 다.
복원원은 base **`79f3e5c4`** 고정 — 움직이는 `origin/main` 을 쓰지 않는다. 이 range 에 **병합 0**
(`git log --oneline --merges 79f3e5c4..HEAD` 빈 출력)이고, 첫 산출물 커밋 `6de5cc23` 은 base 의 조상이
**아니다**(`git merge-base --is-ancestor` 비-0 — base 에 이 slice 가 없다).

**실측 HEAD: `5f698117`** — 이 slice 의 **마지막 산출물 커밋**이다(계약 갱신 2026-11-05 이 들여온 세 경로를 담은 커밋). 그 뒤 커밋은 이 evidence 디렉터리뿐이고
evidence 는 아래 복원 목록 **밖**이다.

**공유 파일에 hunk 격리가 필요 없는 근거**: `milestone-6.md` · `docs/discovery/capability-map.md` ·
`reports/evidence/m6/closure/open-inventory.md` · `.github/workflows/ci.yml` · 두 runbook 은 다른 slice 도 만지는
파일이지만, 이 range 에서 그 여섯을 만진 커밋은 **각각 하나이고 전부 이 slice 의 것**이다
(`git log --oneline 79f3e5c4..5f698117 -- <파일>` 각 1행). 병합이 0 이므로 base 는 그 파일들의 선행 내용을 모두
담은 머지된 main 이고, 전체 복원이 남의 줄을 떨어뜨릴 수 없다.

**단 `ci.yml` 은 다음 라운드에 그 전제가 깨질 수 있다** — PR #122 가 **다른 브랜치에서 같은 파일의 다른
hunk** 를 만진다. 그 PR 이 먼저 머지되고 이 브랜치가 main 을 들이면, `ci.yml` 의 복원 원천은 base 가 아니라
**그때 병합한 main 커밋 SHA**(고정 기재)여야 한다 — base 로 복원하면 남의 hunk 를 지운다. 오늘은 병합 0 이라
base 가 맞고, 병합이 생기는 순간 이 절과 목록을 다시 산출한다.

**하네스 경로**(`CLAUDE.md` · `.claude/**`)는 되돌리지 않는다 — `git log --oneline 79f3e5c4..2aacb574 --
CLAUDE.md .claude/` **빈 출력**(이 range 에 하네스 레인 커밋 0).

## 복원 명령 (in_scope 경로 한정 · 경로는 개별 인자)

```bash
git restore --source=79f3e5c4 --staged --worktree -- \
  .github/workflows/ci.yml \
  adapters/src/main/kotlin/bidvector/adapters/retention/FieldConceptPiiAxis.kt \
  adapters/src/main/kotlin/bidvector/adapters/retention/JdbcRetentionStore.kt \
  adapters/src/main/kotlin/bidvector/adapters/retention/RetentionMaskedKeys.kt \
  adapters/src/main/kotlin/bidvector/adapters/retention/RetentionTableRegistry.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPersistenceTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/notificationinbox/NotificationInboxPrivilegeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/retention/MaskingKeepsContractKeysTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/retention/RetentionFixture.kt \
  adapters/src/test/kotlin/bidvector/adapters/retention/RetentionRegistrySchemaEqualityTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/retention/RetentionSweepTest.kt \
  app/src/main/kotlin/bidvector/app/http/NotificationInboxRequests.kt \
  app/src/main/kotlin/bidvector/app/wiring/OpeningPromotionWiring.kt \
  app/src/test/kotlin/bidvector/app/retention/RetentionRunnerTest.kt \
  docs/discovery/capability-map.md \
  docs/runbook/m6-6b3-retention.md \
  docs/runbook/m6-6e-operations.md \
  docs/runbook/m7-7c3b-notification-sweep.md \
  milestone-6.md \
  reports/evidence/m6/closure/open-inventory.md \
  workflow/src/main/kotlin/bidvector/workflow/notificationinbox/NotificationInboxPorts.kt \
  workflow/src/main/kotlin/bidvector/workflow/retention/PurgeExpiredData.kt \
  workflow/src/main/kotlin/bidvector/workflow/retention/RetentionPolicy.kt \
  workflow/src/test/kotlin/bidvector/workflow/retention/PurgeExpiredDataTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/retention/RetentionPolicyDataTest.kt
```

**신규 파일은 복원이 아니라 삭제다** — 이 slice 의 신규는 evidence 넷뿐이고 함께 지우려면
`git rm -r reports/evidence/m9/retention-1y`(장부층이라 위 목록 밖이고, 그래서 목록이 자기를 담은 커밋을
가리키지 않는다).

목록은 **기계 산출**이다(`git diff --name-status 79f3e5c4..5f698117` 에서 `reports/evidence/m9/retention-1y/`
제외 — `M` **26** · `A` 0). 계약 갱신 라운드가 셋을 더해 23 → 26 이 됐고 **그래서 이 절차를 그 커밋 뒤에
다시 돌렸다**(「라운드마다 파일이 늘면 재산출」).

## 되돌린 트리에서 무엇이 꺼지는가

보존 기간이 **90일로 돌아간다**: 정책 표의 개정 항목이 사라져 cutoff 가 `시계 − 90일` 이 되고, 같은 정책을
읽는 **개찰 승격의 원문 적재 시각 하한**도 90일로 좁아진다. test 앵커도 90 으로 돌아가 경계가 다시 90일에
선다. 문서 쪽은 runbook 의 개정 문면·§2.4 와 milestone·재고의 정정 문단이 사라지고, 확장 범위 셋
(두 운영 runbook 의 파기 시점 문면 · `ci.yml` SBOM 주석의 「별개 축」)이 **90일 서술로 돌아간다** —
`ci.yml` 의 artifact 보관 일수는 어느 쪽에서도 움직이지 않는다(값을 바꾼 적이 없다).

**운영 데이터 영향은 0 이다** — 운영 compose 가 `bidvector.retention.mode` 를 두지 않아 보존 run 이 돌 수
있는 길은 호스트 cron 하나이고, 이 레인은 운영 host·DB 에 닿지 않았다(그 확인 절차가 runbook §2.4 다).
다만 되돌림의 **방향**은 적어 둔다: 개정이 기간을 늘렸으므로 되돌리면 90일~1년 구간의 행이 **다시 파기
대상이 된다**. 되돌림 자체가 행을 지우지는 않지만(저장소 파일만 움직인다), 그 뒤 첫 run 은 90일 기준으로
돈다 — 그 run 을 돌릴지는 운영자 결정이고 운영 실행은 별도 승인이다.

예상 복구 시간: 명령 하나(초 단위).

## 실측 (버릴 clone `rb-clone`, @`2aacb574`)

| # | 확인 | 결과 |
|---|---|---|
| — | 목록 등식 | 기계 산출 **26**(evidence 제외) · 문서 목록 **26** · 전건 `M` |
| ① | 복원 명령 | exit **0** |
| ② | `git status --porcelain` | **`M` 26 · `D` 4**(`D` 는 함께 지운 evidence 네 파일) |
| ③ | `git diff 79f3e5c4 -- <26 경로>` | **0 줄** |
| ④⑤ | 컴파일·test 축 | **트리 동일성으로 갈음한다**(「HEAD 초록」이 아니다): 복원 + evidence 제거 뒤 작업 트리의 `git write-tree` 가 **base 의 트리 해시와 같다**(`80e6c3ba…`) — 되돌린 트리는 base 와 **같은 트리**이고 base 는 머지된 main 이다. 보조 측정: 복원 뒤 `git diff 79f3e5c4 --name-only -- '*.kt' '*.kts' '*.sql' '*.properties' '*.yaml' '*.ts' '*.py'` **0 파일** |
| ⑥ | 이 slice 가 닿는 게이트 | 아래 「⑥ 게이트」 |

## ⑥ 게이트

되돌린 트리에서 **base 와 다른 것은 되돌리지 않는 evidence 디렉터리 하나**다(④⑤ 의 트리 해시 측정이 그
사실의 반대 방향이다 — 그것까지 지우면 트리가 base 와 같아진다). 그 디렉터리를 입력으로 삼는 게이트는
`leakPatternGate` 하나이므로(scanRoot 가 `reports/evidence`), 그 트리에 **이 slice 의 최종 evidence 를 그대로
두고** 그 게이트를 돌려 쟀다. 다른 게이트(ktlint·detekt·sizeGate·architecture·등재 등식)의 입력은 복원으로
base 와 동일해졌다.

| 명령 | 결과 |
|---|---|
| `./gradlew --no-daemon -q leakPatternGate`(rb-clone — 복원된 트리 + 이 slice 의 evidence 네 파일) | **exit 0** · 계약 갱신 뒤 `5f698117` 에서 **재실측**(앞 라운드 `2aacb574` 의 측정을 옮기지 않았다) |
| 참조형 스캔 `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m9/retention-1y/` | **exit 1(매치 0)**. 이 명령의 **양성 대조는 따로 심지 않았다** — 같은 패턴 파일을 산출물 문서(base 에 이미 있던 줄)에 떴을 때 여러 줄이 잡혔으므로 술어가 비어 있지 않다는 것이 그 자리에서 실측됐다 |

**baseline 이 복원으로 함께 돌아간다는 점을 본다**: `config/quality/leak-pattern-baseline.txt` 는 이 slice 가
만지지 않았으므로 base 와 HEAD 가 같은 파일이고, 그래서 「되돌린 트리의 base baseline + 내 evidence」 조합이
위 exit 0 이다 — 되돌림이 baseline 만 걷어 게이트가 붉어지는 그 알려진 모양(M4/leak-baseline-coord)은 이
slice 에 없다.

**이 ⑥ 측정은 evidence 가 그 시점의 바이트일 때 돌았다.** 이 표의 결과를 적는 편집이 다시 그 바이트를
바꾸므로(자기참조), **마지막 HEAD 의 게이트 결과 정본은 verifier 와 완료 보고**다 — 하네스 규율이 그 한 칸의
기록 자리를 그렇게 정한다. 바뀐 것은 결과 줄이고 스캔 어휘를 더하지 않는다.

## 유효성 확인 (감사자용)

```bash
git diff --name-only 5f698117..<판정 SHA> -- \
  <위 복원 목록 26 경로>
```

빈 출력이면 실측 뒤 되돌림 대상이 움직이지 않았다는 뜻이고 이 실측이 유효하다. 한 줄이라도 나오면 미검증이다.
이 slice 는 실측 뒤 **evidence 만** 커밋했으므로 그 출력이 비어 있어야 한다.
