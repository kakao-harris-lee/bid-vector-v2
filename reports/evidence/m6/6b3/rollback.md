# M6/6B-3 — rollback

**실측 HEAD: `5c622296`**(마지막 산출물 커밋 — 앞 라운드 실측 @`575674d2` 는 옮기지 않고 전부 다시 냈다). 버릴 clone(`--no-local`)에서 ①~⑥ 을 그 커밋 위에서
돌렸다.

> verifier 가 대조할 것은 「실측 HEAD == 판정 SHA」가 **아니다**(evidence 커밋은 언제나 뒤에 온다).
> **그 사이에 되돌림 대상이 움직였는가**를 본다:
> `git diff --name-only 5c622296..<판정 SHA> -- <아래 1단계 목록 34>` 가 **빈 출력**이면 유효하다.

## 되돌리는 것과 되돌리지 않는 것

**되돌린다** — in_scope 산출물 경로 **34**. 목록은 손으로 쓰지 않고
`git diff --name-status e2232a75..5c622296` 에서 기계로 내고 evidence 경로만 뺐다(A 28 = 삭제 대상,
M 6 = base 로 복원). **디렉터리로 접지 않는다** — 접으면 목록이 slice 가 만진 파일보다 넓어진다.
**목록은 라운드마다 실측 HEAD 에서 다시 낸다.**

**되돌리지 않는다**: `reports/evidence/m6/6b3/**` — 이 slice 의 evidence. 되돌리면 복구 상태가
「일이 있었다」는 기록을 잃는다. 그 디렉터리를 남긴 채 게이트가 초록인지는 ⑥ 이 잰다.

**하네스 경로 변경 0** — `git log --oneline e2232a75..5c622296 -- CLAUDE.md .claude/` 빈 출력.

## 절차

### 1단계 — in_scope 산출물을 base 로 (경로는 **개별 인자**)

```
git restore --source=e2232a75 --staged --worktree -- \
  <위 기계 산출 32 경로>
```

`--source` 에 없는 경로(신설 28)는 삭제되므로 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로 끊긴다.

### 머지 뒤라면 — 공유 파일의 복원 출처가 바뀐다

수정 6 가운데 넷은 **다른 레인도 만지는 공유 파일**이다(`config/quality/gate-tests.properties` ·
`config/quality/architecture-policy.properties` · `docs/runbook/m6-6e-operations.md` ·
`app/.../AppHttpDependencyGateTest.kt`). **머지 전인 지금은** 이 브랜치에 그 파일들의 다른 레인
변경이 0 이라(이 slice 가 유일한 저자) base 복원이 정확하다 — ③d 의 blob 동일성이 그것을 실측한다.
**머지 뒤에는** base 가 아니라 **병합된 main 커밋 SHA(고정값, 움직이는 `origin/main` 금지)** 에서
복원한다(PR #69 규칙). 그 전제는 「그 SHA 에 이 slice 가 없다」이고, 머지 순간 그 복원은 아무것도
지우지 않으며 exit 0 이다.

### DB 는 forward-fix 다 — 이 절차가 되돌리지 못하는 것

V18 이 **적용된 DB** 는 위 명령으로 돌아오지 않는다. 트리거와 열을 되돌리려면 새 마이그레이션이
필요하다(트리거를 V3 의 `reject_mutation` 으로 재지정 · `pii_masked_at` DROP). 그리고 **이미 파기된
행과 마스킹된 값은 어느 쪽으로도 돌아오지 않는다** — 백업 복원만이 경로다
(`docs/runbook/m6-6b2-backup-restore.md`). 그래서 운영 실행이 별도 승인 사항이다.

## 되돌려도 운영 영향 0 (오늘의 배포에서)

러너는 **기본 꺼짐**이다(`bidvector.retention.mode` 미설정 — compose 는 `*.mode` 를 설정하지 않는다).
되돌리면 꺼져 있던 러너와 그 배선·저장소·정책 값이 사라지고, V18 이 아직 적용되지 않은 DB 에서는
마이그레이션 파일 하나가 줄어들 뿐이다. **돌고 있는 어떤 DB 도 이 slice 때문에 바뀌지 않았다** —
모든 실행이 Testcontainers 위에서였다.

## 비활성화(되돌리지 않고 끄는 법)

| 끄고 싶은 것 | 최소 조치 |
|---|---|
| 보존 파기·마스킹 실행 | `bidvector.retention.mode` 를 **설정하지 않는다**(기본). 러너 빈이 생기지 않는다 — 배선 test 가 그 사실을 잰다 |
| 이미 적용된 V18 의 마스킹 구멍 | 새 V 로 `raw_observation_append_only` 를 V3 의 `reject_mutation` 으로 재지정한다(열은 남겨도 무해 — 아무도 쓰지 않는다) |
| cron | cron 줄 하나를 지운다(러너는 상주 루프가 아니다) |

## ①~⑥ 실측 (버릴 clone, 실측 HEAD `5c622296`)

| # | 확인 | 결과 |
|---|---|---|
| ⓪ | 목록이 기계 산출과 같다 | 기계 34 · 문서 34(같은 산출을 그대로 썼다 — 두 벌을 두지 않았다, `comm` 양방향 0) |
| ① | 복원 명령이 선다 | `git restore` **exit 0** |
| ② | 복원 규모 | `git status --porcelain` → **D 28 · M 6** |
| ③ | 복원 경로가 base 와 같다 | `git diff e2232a75 -- <34>` 빈 출력 · `git diff --cached` 도 빈 출력 |
| ③c | **내 줄 사라짐 · 남의 줄 남음** | 공유 runbook 의 보존 언급 **0**(제목이 「`mode=once` 다섯」으로 돌아갔다) · 같은 파일의 relay·수집 러너 행 **3 그대로** |
| ③d | **트리 동일성** | 수정 6 전부 blob SHA 가 base 와 **같음** · 신설 28 전부 지워짐 · base 와 다른 추적 파일은 `reports/evidence/m6/6b3/` 넷뿐(이 slice 의 evidence — 의도) |
| ④ | 되돌린 트리가 컴파일된다 | `./gradlew --no-daemon compileTestKotlin` **exit 0** |
| ⑤ | test 가 초록 | `./gradlew --no-daemon check` **exit 0** · 9m26s · test 합계 **2,829**(HEAD 2,919 − 신설 90 — 등식이 닫힌다) |
| ⑥ | **게이트가 초록이고 실제로 돌았다** | 같은 run 에서 `gateExecutionGate`·`gateRegistrationGate`·`sizeGate`(모듈별) · `contractGate` · `memberEffectGate` · `leakPatternGate` 전부 **수행**(UP-TO-DATE 아님 — 게이트 task 30 수행) · 209 task 실행 |

evidence 디렉터리를 남긴 채 `leakPatternGate` 가 초록이라 ⑥ 이 성립한다.

## 하네스 레인 변경

없음. 이 slice 는 `CLAUDE.md`·`.claude/**` 를 건드리지 않았다.
