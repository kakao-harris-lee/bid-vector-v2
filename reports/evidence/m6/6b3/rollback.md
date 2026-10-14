# M6/6B-3 — rollback

**실측 HEAD: `3f4512e9`**(두 번째 머지 커밋 — PR #71(6B-4)이 main 에 들어가 브랜치가 다시 BEHIND 가
되어 고정 SHA **`4f2b45b3`** 를 `--no-ff` 로 들였다. 부모는 `bcd7428f` + `4f2b45b3`, 충돌 0).
앞 실측 @`0cf252c6` 은 옮기지 않고 전부 다시 냈다. 버릴 clone(`--no-local`)에서 ①~⑥ 을 그 커밋
위에서 돌렸다.

> **복원 출처를 `ae9b2f52` → `4f2b45b3` 으로 옮겼다.** 규칙은 「**가장 최근에 병합된 main 커밋 SHA**를
> 고정값으로」 하나이고, 두 벌(「옛 출처 + 그것이 아직 유효하다는 증명」)을 들고 다니지 않는다 —
> 다음 머지가 또 붙을 때 그 증명을 매번 갱신해야 하고 한 번 잊으면 복원이 남의 변경을 지운다.
>
> 옮겨도 **결과가 같다는 것을 기계로 확인했다**: `git diff --name-only ae9b2f52 4f2b45b3 -- <49 경로>`
> 가 **빈 출력**이다(6B-4 가 만진 40 경로와 이 목록의 교차가 0 이므로). 실측에서도 두 출처 모두
> 복원 뒤 diff 0 줄이다(③ 두 줄). 즉 이 교체는 **거동 변화가 아니라 규칙의 단일화**다.

> **왜 base 가 아닌가.** 머지 둘이 들어오면서 **공유 파일 둘이 양쪽 변경을 함께 담는다**
> (`config/quality/gate-tests.properties` · `docs/runbook/m6-6e-operations.md`). base 에서 복원하면
> 그 두 파일에서 **남의 변경까지 지운다** — 그래서 출처는 병합된 main 커밋 SHA 를 **고정값으로**
> 쓴다(움직이는 `origin/main` 을 쓰지 않는다 — 다음 main 커밋이 붙는 순간 복원 결과가 달라진다).
>
> 전제 둘을 **머지마다** 단언했다(지금 SHA 기준): ① `git merge-base --is-ancestor 4f2b45b3 HEAD`
> **참**(머지가 실제로 들어왔다) ② `git merge-base --is-ancestor <이 slice 의 첫 커밋> 4f2b45b3`
> **거짓**(그 SHA 에 이 slice 가 없다 — 없으면 복원이 이 slice 의 일부를 「원상」으로 되살린다).
> 첫 머지(`ae9b2f52`)에서도 같은 둘을 단언했다.

> verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋은 언제나 뒤에 온다).
> **그 사이에 되돌림 대상이 움직였는가**를 본다:
> `git diff --name-only 3f4512e9..<판정 SHA> -- <아래 1단계 목록 49>` 이 **빈 출력**이면 유효하다.

## 되돌리는 것과 되돌리지 않는 것

**되돌린다** — in_scope 산출물 경로 **49**. 목록은 손으로 쓰지 않고
**`git diff --name-status 4f2b45b3..3f4512e9`** 에서 기계로 내고 evidence 경로만 뺐다(A 41 = 삭제 대상,
M 8 = `4f2b45b3` 로 복원). **머지 뒤에는 base 기준으로 내지 않는다** — base..머지 는 main 의 변경까지
담으므로 목록이 이 slice 보다 넓어지고, 그 넓은 목록으로 복원하면 **남의 변경을 지운다**.
`<고정 main SHA>..<실측 HEAD>` 는 정확히 **이 slice 가 머지된 main 위에 더한 것**이다(base 가 그 SHA 의
조상이라 main 의 변경은 이미 그 안에 있다). **디렉터리로 접지 않는다** — 접으면 목록이 slice 가 만진 파일보다
넓어진다.

**남의 경로는 목록 밖이다**(실측 둘):
- 6E-2c 머지까지(`e2232a75..ae9b2f52`, 20 경로): 교차는 **공유 파일 둘**뿐(위 두 파일)이고 나머지 18
  (`docker/**` · `app/build.gradle.kts` · `BootJarSecurityFloorTest.kt` · `gradle/libs.versions.toml` ·
  `tools/vuln-scan-check.sh` · `milestone-6.md` · 하네스 파일 · 다른 slice 의 evidence)은 들어오지 않는다.
- **6B-4 머지(`ae9b2f52..4f2b45b3`, 40 경로): 교차 0**(`comm -12` 빈 출력) — `ml-engine/**` ·
  `contracts/proto/**` · `docker/compose.yaml` · `docker/ml-serving.Dockerfile` · 그 slice 의 evidence 는
  이 목록에 **한 경로도** 들어오지 않는다. 그래서 6B-4 의 산출물은 이 되돌림이 건드리지 않는다.

그 경로들이 복원 뒤에도 `4f2b45b3` 와 동일한지 ③b 가 센다.
**목록은 라운드마다 실측 HEAD 에서 다시 낸다** — 34 → 40 → 48 → 48 → **49**. 마지막 증가 하나는
PR #73 P7 의 `ForeignKeyEdgeIdentityTest.kt` 다(r3b 는 경로를 더하지 않고 두 파일의 내용만 바꿨고,
그 앞 라운드는 파일 둘의 **이름만** 바꿨다 — 그 둘의 지금 이름은 `MigrationLockTimeoutTest` ·
`MaskingKeepsContractKeysTest` 다). **목록 자체를 여기 적지 않는다** — 적으면 다음 라운드에 낡는다.

**되돌리지 않는다**: `reports/evidence/m6/6b3/**` — 이 slice 의 evidence. 되돌리면 복구 상태가
「일이 있었다」는 기록을 잃는다. 그 디렉터리를 남긴 채 게이트가 초록인지는 ⑥ 이 잰다.

**하네스 경로 변경 0(이 slice 의 몫으로는)** — `git log --oneline 4f2b45b3..3f4512e9 -- CLAUDE.md .claude/`
빈 출력. `CLAUDE.md`·`.claude/**` 는 **main 이** 바꿨고(PR #72 codex 핀) 그 변경은 머지로 들어왔다 —
목록 밖이므로 이 복원이 건드리지 않는다.

## 절차

### 0단계 — **어느 갈래인가**: V18·V19 가 적용된 DB 가 있는가 (PR #73 P2)

되돌림은 **두 갈래**고 명령이 다르다. 먼저 이것을 확인한다(아래 ⓐ·ⓑ 중 하나를 고른다).

```sql
-- 되돌리려는 배포가 닿는 모든 DB 에서 — 한 곳이라도 행이 있으면 ⓑ 다.
SELECT version, success, installed_on FROM flyway_schema_history WHERE version IN ('18', '19');
```

**ⓐ 아직 적용된 DB 가 없다**(개발 중·머지 전 되돌림 — 지금 이 slice 의 상태다): 1단계를 그대로
돌린다. 마이그레이션 파일 둘이 트리에서 사라지고 어느 DB 도 그 둘을 모른다.

**ⓑ 한 곳이라도 적용됐다**: 1단계의 복원 목록에서 **`V18__…sql` · `V19__…sql` 두 경로를 뺀다.**
그 파일을 지운 채 앱을 띄우면 Flyway 가 **기동에서 `validate` 로 실패한다** — 이력에 있는
적용된 버전이 로컬에서 해소되지 않는다(`Detected applied migration not resolved locally`). 즉
파일을 지우는 행위가 「되돌림」이 아니라 **기동 불능**을 만든다. 그래서 ⓑ 는 **코드 되돌림과
forward-fix 마이그레이션을 한 배포로 함께** 한다(아래 「DB 는 forward-fix 다」의 ⓐ~ⓓ 순서가 그
마이그레이션의 내용이고 runbook §1.2 가 정본이다).

> **`flyway repair` 는 답이 아니다.** 그 명령이 고치는 것은 **이력 표**다(실패 항목 삭제·체크섬
> 재정렬). 적용된 DDL 을 되돌리지 않으므로, 파일을 지우고 repair 로 이력 줄을 없애면 **열·트리거·
> 인덱스는 그대로 남은 채 「적용된 적 없다」고 적힌 DB** 가 된다 — 다음 배포의 새 V18 이 이미 있는
> 객체와 충돌하고, 그 충돌은 기동 실패로만 보인다. 스키마와 장부가 조용히 어긋나는 쪽이 더 비싸다.

### 1단계 — in_scope 산출물을 **고정된 `4f2b45b3`** 로 (경로는 **개별 인자**)

```
git restore --source=4f2b45b3 --staged --worktree -- \
  <위 기계 산출 49 경로>        # ⓑ 라면 V18·V19 두 경로를 뺀 47
```

`--source` 에 없는 경로(신설 41)는 삭제되므로 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로 끊긴다.

**아래 ①~⑥ 실측은 ⓐ 갈래다**(오늘의 상태 — 적용된 DB 가 없다). ⓑ 는 복원 목록이 47 이고 그
트리는 마이그레이션 파일 둘을 **그대로 가진** 채 코드만 base 인 상태라, test 가 그 파일들을 쓰는
자리(`CleanMigration*` 장부)가 base 의 기대와 어긋나 붉는다 — 그것이 ⓑ 가 **되돌림만으로 끝나지
않는다**는 사실의 표현이다(forward-fix 마이그레이션이 같은 배포에 들어가야 한다).

### 공유 파일의 복원 출처 — **지금은 `4f2b45b3` 다**

수정 8 은 전부 **다른 레인도 만지는 공유 파일**이다(`config/quality/gate-tests.properties` ·
`config/quality/architecture-policy.properties` · `docs/runbook/m6-6e-operations.md` ·
`app/.../AppHttpDependencyGateTest.kt` · `CleanMigration{Check,Column,Trigger}Test.kt` ·
`PersistenceTestSupport.kt`). 앞 라운드까지는 이 브랜치에 그 파일들의 **다른 레인 변경이 0** 이어서
base 복원이 정확했다. **main 을 들인 지금은 아니다** — 둘(`gate-tests.properties` ·
`m6-6e-operations.md`)이 main 의 변경을 함께 담고, base 복원은 그것을 **지운다**(PR #69 규칙).

그래서 출처는 **가장 최근에 병합된 main 커밋 SHA 고정값 `4f2b45b3`** 다(머지가 둘이 되었으므로
규칙을 그 한 줄로 둔다 — 머리말에 교체 사유와 등가 확인이 있다). 「그 SHA 에 이 slice 가 없다」는
전제를 단언했고, 그 복원이 **남의 줄을 하나도 지우지 않는다**는 것을 ③c 가 계수로 센다 — 6E-2c 의
표지 둘과 **6B-4 의 표지 넷**이 복원 뒤에도 그대로 있고, 공유 파일 둘의 blob 이 `4f2b45b3` 와
**동일**하다. 완료 판정을 종료 코드가 아니라 **「내 표지 0 · 남의 표지 보존」 계수**로 두는 이유가
이것이다.

**PR 이 main 에 머지된 뒤**의 되돌림은 이 절차가 아니라 **PR revert** 다.

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

## ①~⑥ 실측 (버릴 clone, 실측 HEAD `3f4512e9` · 출처 `4f2b45b3`)

| # | 확인 | 결과 |
|---|---|---|
| ⓪ | 목록이 기계 산출과 같다 | 기계 **49** · 문서는 수를 적고 목록은 적지 않는다(두 벌을 두지 않는다) |
| ① | 복원 명령이 선다 | `git restore --source=4f2b45b3` **exit 0** |
| ② | 복원 규모 | `git status --porcelain` → **D 41 · M 8**(기계 목록의 A 41 · M 8 과 같다) |
| ③ | 복원 경로가 **머지된 main** 과 같다 | `git diff 4f2b45b3 -- <49>` 와 `git diff --cached 4f2b45b3 -- <49>` **둘 다 0줄** · 앞 출처(`ae9b2f52`)로 같은 질의도 **0줄**(두 출처의 등가를 복원 뒤에도 확인) |
| ③b | **남의 경로를 건드리지 않았다** | 두 머지가 바꾼 경로 가운데 목록 밖 전부의 `git diff 4f2b45b3` **0줄**(6B-4 의 40 경로 포함 — 복원이 남의 변경 영역에 닿지 않는다) |
| ③c | **내 표지 0 · 남의 표지 보존(6E-2c · 6B-4 둘 다)** | 내 표지 전부 0 — 게이트 등재의 `retention.` 0 · `MigrationLockTimeout` 0 · `ForeignKeyEdgeIdentity` 0 · 정책의 `RetentionWiring`·`app.retention.` 0 · 공유 runbook 의 `bidvector.retention` 0 과 「90일에 파기」 0 · `adapters/.../retention/` 파일 0. **남의 표지는 그대로** — 6E-2c: `BootJarSecurityFloorTest` 등재 **2행** · 공유 runbook 의 6E-2c 문면 **7행**. **6B-4: `TrainingContractTest` 등재 2행 · `ml_engine/training/jobs/` 파일 8 · `reports/evidence/m6/6b4/` 파일 6 · `docker/ml-serving.Dockerfile` 존재**. 공유 파일 둘의 blob 이 `4f2b45b3` 와 **동일** |
| ③d | **트리 동일성** | 경로마다 찍은 줄로 — 수정 8 **전부 `same`**(blob SHA 가 `4f2b45b3` 와 같음 · `DIFF` 0) · 신설 **41** 전부 `LEFT` 없음(파일로 남지 않음) · base 와 다른 추적 파일은 `reports/evidence/m6/6b3/` **여섯**뿐(이 slice 의 evidence — 의도) |
| ④ | 되돌린 트리가 컴파일된다 | `./gradlew --no-daemon compileTestKotlin` **exit 0**(14s) |
| ⑤ | test 가 초록 | `./gradlew --no-daemon check` **exit 0**(9m34s) |
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

**없음.** 범위는 **base..HEAD 전체**로 다시 쟀다(PR #73 low — 앞 판은 그때의 마지막 산출물 커밋까지만
봤다): `git log --oneline e2232a75..HEAD -- CLAUDE.md .claude/` 빈 출력 ·
`git diff --name-only e2232a75..HEAD` 에 `CLAUDE.md`·`.claude/**`·`AGENTS.md` 경로 0.
