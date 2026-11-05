# M9 / retention-1y — 보존 기간 90일 → 1년 (6B-3 정책 날짜 개정)

- base: main `79f3e5c4` · 브랜치 `feature/m9-retention-1y` · worktree `bid-vector-v2-m9-retention-1y`
- 근거: 6B-3 은 PR #73 로 구현·머지(정본 `reports/evidence/m6/6b3/scope.md`, runbook `docs/runbook/m6-6b3-retention.md`), 정책값 90일은 `milestone-6.md` 「운영자 결정 ② 6B-3 보존 기간 90일」(2026-09-26).
- **사용자 결정 2026-11-05**: 보존 기간 **1년**(기존 90일을 변경 — 6B-3 이 이미 구현돼 있다는 사실을 알린 뒤 재확인).

## 결정

| ID | 결정 |
| --- | --- |
| D-1 | `RETENTION_POLICY`(`workflow/.../retention/RetentionPolicy.kt`)에 **날짜 개정 항목**을 더한다 — `EffectiveFrom.On(2026-11-05)` 부터 `retentionDays = 365`. `Initial` 의 90 은 그대로 둔다(90일 시대의 감사 보존). `source` 는 두 결정을 함께 인용. batchSize 불변. |
| D-2 | 같은 정책을 읽는 소비자(파기·마스킹 sweep · 승격 하한 `OpeningPromotionWiring`/`PromoteOpeningResultsUseCase`)는 정책 해소값을 그대로 따른다 — 값을 다른 자리에 복제하지 않는다. 복제가 이미 있으면(상수·주석·SQL 주석 문면) 정책 경유로 바꾸거나 문면을 「정책값(현재 365일)」 꼴로. **현재 정책을 서술하는 「90일」 문면 0**(역사 서술은 날짜와 함께 남겨도 된다). |
| D-3 | test 앵커 `RetentionFixture` 의 `RETENTION_ANCHOR` 를 365 로, 경계 변이 근거를 364/366 으로 다시 쓴다. 「앵커를 정책에서 읽지 않는다」는 기존 의도(정책을 바꾸면 test 가 깨져야 한다)는 유지. |
| D-4 | 마이그레이션 0. `V19`·`V23` 의 「90일」 SQL **주석**은 적용된 마이그레이션이라 고치지 않는다(checksum) — 그 사실을 evidence 에 적는다. |
| D-5 | 낡은 문면 정정: `milestone-6.md` 의 「승인된 보존 기간이 없다」류 문면과 `reports/evidence/m6/closure/open-inventory.md` §2 「운영자 결정 선행 slice 셋 … 열리지 않았다」에 날짜 붙은 **정정 문단**(6B-3 은 PR #73, 세 OPEN 은 6B-3 이 닫음; 2026-11-05 1년 개정). 남은 서브 OPEN(`OPEN-6B3-OUTBOX-UNSENT-RETENTION`·`-NOTICE-AUDIT-SNAPSHOT-RETENTION`·`-PURGE-LOCK-TIMEOUT`·`-FREE-TEXT-PII-AXIS`)은 처분하지 않고 등재 유지를 적는다(`FREE-TEXT-PII-AXIS` 의 「운영 첫 실행 전 실표본 판독」 선행 조건을 runbook 에 그대로). |
| D-6 | runbook `m6-6b3-retention.md` 갱신 — 값·개정일·「운영에서 아직 한 번도 돌지 않았을 수 있다(운영 compose 가 `bidvector.retention.mode` 를 두지 않음)」 확인 절차. 운영 실행은 이 slice 밖. |

## in_scope

- `workflow/src/main/kotlin/bidvector/workflow/retention/**` 와 그 test
- `adapters/src/main/kotlin/bidvector/adapters/retention/**` 와 `adapters/src/test/kotlin/bidvector/adapters/retention/**`
- 「90일」 현재-정책 문면이 있는 main/test 파일(`app/.../http/NotificationInboxRequests.kt` · `app/.../wiring/OpeningPromotionWiring.kt` · `adapters/src/test/.../persistence/CleanMigrationTest.kt` · `NotificationInboxPersistenceTest.kt` · `NotificationInboxPrivilegeTest.kt` 등 — 실제로 고친 경로를 evidence 에 열거)
- `docs/runbook/m6-6b3-retention.md` · `milestone-6.md` · `reports/evidence/m6/closure/open-inventory.md` · `docs/discovery/capability-map.md`(보존 행)
- `reports/evidence/m9/retention-1y/**`

## out_scope

마이그레이션 · 표 분류(PURGE/MASK/EXEMPT) 변경 · 서브 OPEN 처분 · 운영 실행.

## acceptance

- CI Kotlin `check` job 명령 그대로 exit 0.
- test: 개정일 전 기준일 → 90, 개정일 이후 → 365(날짜 개정 보존) · cutoff −1s 파기·마스킹 / +1s 보존(앵커 365) · 러너 시작 줄의 cutoff 와 저장소 cutoff 가 같은 한 값.
- 변이: 개정 항목 제거 · 364/366 경계 — 각각 RED.

## rollback

in_scope 경로 한정 `git restore --source=79f3e5c4 …`; 목록 기계 산출(`rollback.md`). 운영 데이터 영향 0(운영에서 sweep 미가동, 기간을 늘리는 방향).

## 하네스 레인 변경

없음.

## 계약 갱신 2026-11-05 (팀장)

**in_scope 에 세 경로를 더한다** — 구현 레인이 「현재 정책을 90일로 서술하는 문면이 in_scope 밖에 다섯 자리
남는다」고 보고한 것에 대한 확장 승인.

| 경로 | 범위 |
| --- | --- |
| `docs/runbook/m6-6e-operations.md` | `DELIVERED` outbox 파기 시점을 90일로 적은 두 자리 |
| `docs/runbook/m7-7c3b-notification-sweep.md` | 공고 파기·알림함 지움 경로를 90일로 적은 두 자리 |
| `.github/workflows/ci.yml` | **SBOM 보관 주석의 「같은 값」 문장 한 줄만**. artifact 보관 일수(90)는 **그대로 두고**, 주석을 「artifact 보관 90일은 데이터 보존 정책과 별개 축이다」 꼴로 바꾼다. PR #122 가 같은 파일의 **다른 hunk** 를 만지므로 그 줄 외에는 손대지 않는다 |

**승격 하한이 1년으로 함께 넓어지는 것은 의도된 귀결이다**(팀장 판단). 낙찰자 상호 수집에 유리하고, 애초에
D-2 가 값 복제를 금한 이유가 그것이다 — 소비자가 정책 해소값을 따르므로 개정이 그 창도 함께 움직인다.
evidence 에 **거동 파급**으로 적고 그대로 진행한다(되돌림 항목이 아니다).

**acceptance 재실측의 자리**: 호스트 빌드 큐가 포화라(대기 12) 전건 `check`·`qualityBaseline` 은 **PR CI 가
정본**이다(같은 명령). 이 갱신 뒤 HEAD 가 움직이므로 전건은 draft PR 의 CI 로 재실측하고, 로컬에서는
**표적 test 와 변이만** 돌린다.
