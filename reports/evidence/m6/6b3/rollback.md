# M6/6B-3 — rollback

**실측 HEAD: `f92d4b55`**(r3b 문면 라운드의 마지막 산출물 커밋 — 앞 실측 @`35220afd` 은 옮기지 않고
전부 다시 냈다). 버릴 clone(`--no-local`)에서 ①~⑥ 을 그 커밋 위에서 돌렸다.

> **왜 다시 쟀나.** r3b 가 되돌림 대상 **둘**을 움직였다(V19 의 주석 한 문단 · 이 slice 의 runbook).
> `git diff --name-only 35220afd..f92d4b55 -- <목록 48>` 이 그 두 줄을 뱉으므로 앞 실측은 그 순간
> **미검증**이 되었다 — 주석 한 줄이어도 규칙은 같다. 목록 자체는 **48 그대로**다(경로 집합 불변).

> verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋은 언제나 뒤에 온다).
> **그 사이에 되돌림 대상이 움직였는가**를 본다:
> `git diff --name-only f92d4b55..<판정 SHA> -- <아래 1단계 목록 48>` 이 **빈 출력**이면 유효하다.

## 되돌리는 것과 되돌리지 않는 것

**되돌린다** — in_scope 산출물 경로 **48**. 목록은 손으로 쓰지 않고
`git diff --name-status e2232a75..f92d4b55` 에서 기계로 내고 evidence 경로만 뺐다(A 40 = 삭제 대상,
M 8 = base 로 복원). **디렉터리로 접지 않는다** — 접으면 목록이 slice 가 만진 파일보다 넓어진다.
**목록은 라운드마다 실측 HEAD 에서 다시 낸다**(34 → 40 → 48 → **48**(r3b 는 경로를 더하지 않았다 — 두 파일의 **내용만** 바뀌었다) · 앞 라운드에서는 파일 둘이 **이름만** 바뀌었다 — 잠금 창 test 와 투영 등식 test: 라운드 2 가 새 파일 일곱과
수정 하나를 더했다 — 선언 둘(`FieldConceptPiiAxis.kt`·`RetentionMaskedKeys.kt`) · test 다섯
(`FieldConceptPiiAxisTest`·`OpeningCompanyInfoMaskingTest`·`MaskingKeepSetSourceTest`·
`InsertedAtOwnershipTest`·`V18LockTimeoutTest`) · 공유 하네스 `PersistenceTestSupport.kt`).

**되돌리지 않는다**: `reports/evidence/m6/6b3/**` — 이 slice 의 evidence. 되돌리면 복구 상태가
「일이 있었다」는 기록을 잃는다. 그 디렉터리를 남긴 채 게이트가 초록인지는 ⑥ 이 잰다.

**하네스 경로 변경 0** — `git log --oneline e2232a75..35220afd -- CLAUDE.md .claude/` 빈 출력.

## 절차

### 1단계 — in_scope 산출물을 base 로 (경로는 **개별 인자**)

```
git restore --source=e2232a75 --staged --worktree -- \
  <위 기계 산출 48 경로>
```

`--source` 에 없는 경로(신설 40)는 삭제되므로 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로 끊긴다.

### 머지 뒤라면 — 공유 파일의 복원 출처가 바뀐다

수정 8 가운데 일곱은 **다른 레인도 만지는 공유 파일**이다(라운드 2 가 `PersistenceTestSupport.kt` 를 더했다 — 이미지 태그를 `internal` 로 연 한 줄)(`config/quality/gate-tests.properties` ·
`config/quality/architecture-policy.properties` · `docs/runbook/m6-6e-operations.md` ·
`app/.../AppHttpDependencyGateTest.kt` · `CleanMigrationColumnTest.kt` · `CleanMigrationTriggerTest.kt`
· `CleanMigrationCheckTest.kt`). **머지 전인 지금은** 이 브랜치에 그 파일들의 다른 레인 변경이 0 이라
(이 slice 가 유일한 저자) base 복원이 정확하다 — ③d 의 blob 동일성이 그것을 실측한다.
**머지 뒤에는** base 가 아니라 **병합된 main 커밋 SHA(고정값, 움직이는 `origin/main` 금지)** 에서
복원한다(PR #69 규칙). 그 전제는 「그 SHA 에 이 slice 가 없다」이고, 머지 순간 그 복원은 아무것도
지우지 않으며 exit 0 이다.

### DB 는 forward-fix 다 — 이 절차가 되돌리지 못하는 것

V18·V19 가 **적용된 DB** 는 위 명령으로 돌아오지 않는다. 되돌리려면 새 마이그레이션이 필요하고
그 순서와 대가는 runbook §1.2 가 정본이다 — 요지 넷: ⓐ 트리거를 V3 의 `reject_mutation` 으로
**먼저** 재지정(역순이면 함수 본문이 깨진다) ⓑ `reject_mutation_except_masking` DROP(빠뜨리면 고아
함수가 남고 트리거→함수 등재는 초록이다 — 라운드 2 가 그 함수에 더한 적재 시각 강제·INSERT 관문도
ⓐ 의 재지정이 함께 걷어내므로 별 항목이 아니다) ⓒ V19 의 인덱스 열과 CHECK 제약 DROP
ⓓ `pii_masked_at` DROP — **그 마지막이 「이 행은 마스킹했다」를 영구히 지운다**(마스킹된 행이 원문 행과 구별되지 않고,
재적용 때 fail-closed 표식까지 다시 판독되어 사라진다).

**이미 파기된 행과 마스킹된 값은 어느 쪽으로도 돌아오지 않는다** — 백업 복원만이 경로다
(`docs/runbook/m6-6b2-backup-restore.md`). 그래서 운영 실행이 별도 승인 사항이다.

## 되돌려도 운영 영향 0 (오늘의 배포에서)

러너는 **기본 꺼짐**이다(`bidvector.retention.mode` 미설정 — compose 는 `*.mode` 를 설정하지 않는다).
되돌리면 꺼져 있던 러너와 그 배선·저장소·정책 값이 사라지고, V18·V19 가 아직 적용되지 않은 DB 에서는
마이그레이션 파일 둘이 줄어들 뿐이다. **돌고 있는 어떤 DB 도 이 slice 때문에 바뀌지 않았다** —
모든 실행이 Testcontainers 위에서였다.

## 비활성화(되돌리지 않고 끄는 법)

| 끄고 싶은 것 | 최소 조치 |
|---|---|
| 보존 파기·마스킹 실행 | `bidvector.retention.mode` 를 **설정하지 않는다**(기본). 러너 빈이 생기지 않는다 — 배선 test 가 그 사실을 잰다 |
| 이미 적용된 V18 의 마스킹 구멍 | 새 V 로 `raw_observation_append_only` 를 V3 의 `reject_mutation` 으로 재지정한다(열·제약·인덱스는 남겨도 무해 — 아무도 쓰지 않는다). **그 재지정이 함께 없애는 것 둘**(mig L-3): 적재의 **표지 관문**과 **`inserted_at` 소유** — 「세 입력이 적재자 손을 벗어난다」 가운데 둘이 그 한 줄에 달려 있다 |
| cron | cron 줄 하나를 지운다(러너는 상주 루프가 아니다) |

## ①~⑥ 실측 (버릴 clone, 실측 HEAD `f92d4b55`)

| # | 확인 | 결과 |
|---|---|---|
| ⓪ | 목록이 기계 산출과 같다 | 기계 48 · 문서 48(같은 산출을 그대로 썼다 — 두 벌을 두지 않았다) |
| ① | 복원 명령이 선다 | `git restore` **exit 0** |
| ② | 복원 규모 | `git status --porcelain` → **D 40 · M 8**(기계 목록의 A 40 · M 8 과 같다) |
| ③ | 복원 경로가 base 와 같다 | `git diff e2232a75 -- <48>` **빈 출력**(exit 0) · `git diff --cached` 도 빈 출력 |
| ③c | **내 표지 0 · 남의 표지 보존** | 복원 뒤 내 표지 전부 0 — 게이트 등재의 `retention.` 행 0 · `MigrationLockTimeout` 등재 0 · 아키텍처 정책의 `RetentionWiring`·`app.retention.` 0 · 공유 runbook 의 `bidvector.retention` 0 · `adapters/.../retention/` 디렉터리 파일 0. 같은 파일들의 **남의 표지는 그대로** — 게이트 등재의 `adapters.relay.` 행 4(HEAD 에서는 5 — 이 slice 가 더한 한 줄만 사라졌다) · `EvaluationCommit` 12 · 공유 runbook 의 relay 줄 18 |
| ③d | **트리 동일성** | 경로마다 찍은 줄로 — 수정 8 **전부 `same`**(blob SHA 가 base 와 같음 · `DIFF` 0) · 신설 40 **전부 `LEFT` 없음**(파일로 남지 않음) · base 와 다른 추적 파일은 `reports/evidence/m6/6b3/` 넷뿐(이 slice 의 evidence — 의도) |
| ④ | 되돌린 트리가 컴파일된다 | `./gradlew --no-daemon compileTestKotlin` **exit 0**(16s) |
| ⑤ | test 가 초록 | `./gradlew --no-daemon check` **exit 0**(9m31s) |
| ⑥ | **게이트가 초록이고 실제로 돌았다** | 같은 run 에서 게이트 task **136 수행** · 그 가운데 **UP-TO-DATE 0**(버릴 clone 은 `build/` 가 없어 캐시로 넘어가는 task 가 없다 — 「돌았다」가 수행 수로 보인다) |

evidence 디렉터리를 남긴 채 `leakPatternGate` 가 초록이라 ⑥ 이 성립한다.

> ③d 의 **자동 집계는 세 라운드 연속 틀렸고**(구동 스크립트의 색인 정렬 결함) 그래서 **r3b 는 그
> 스크립트를 아예 쓰지 않았다** — 처음부터 경로마다 `git rev-parse <base>:<경로>` 와
> `git hash-object <경로>` 를 대조해 한 줄씩 찍었다(위 표의 「same 8 · DIFF 0」이 그 출력이다).
> 앞 라운드들의 교훈을 그대로 적용한 것이고, 고친 스크립트의 초록보다 경로마다 찍힌 줄이 증거로
> 낫다. 세 번 같은 자리에서 틀렸다는 것은 **그 스크립트의 출력을 증거로 쓰지 않는다**는 뜻이다.
> ③c 의 「보존 줄」 느슨한 grep(8 → 6)도 같은 부류였다 — 「보존」은 그 공유 runbook 에서 다른 뜻으로도
> 쓰이는 낱말이라 0 이 될 수 없다. 위 표의 ③c 는 **표지를 정확히 지목해** 다시 센 값이다.

## 하네스 레인 변경

없음. 이 slice 는 `CLAUDE.md`·`.claude/**` 를 건드리지 않았다.
