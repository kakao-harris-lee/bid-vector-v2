# M6/6E-2a — rollback 절차와 실측

실측 HEAD: `1dce56ab`(PR #66 조치 라운드의 **마지막 산출물 커밋**) · base: `2deb5f9d`.

실측 HEAD 와 판정 SHA 사이에 **되돌림 대상이 움직이지 않았는가**가 유효성의 기준이고, 그 확인은 아래
「verifier 가 보는 등식」이 명령으로 낸다 — 「그 뒤에 움직인 경로가 무엇인가」를 **문면에 세어 적지
않는다**(그렇게 적은 앞 판은 Codex 판정 JSON 둘이 뒤에 생기면서 곧바로 거짓이 됐다, PR #66 #1).

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 목록은 손으로 쓰지 않고 ⓪ 가 산출한다 —
**라운드마다 파일이 늘면 이 절차를 다시 돌린다.**

**대상에서 빠지는 셋을 선언한다.**
- `reports/evidence/m6/6e2a/scope.md` — 팀장 소유 **계약 문서**다. 되돌림을 지시하는 자리라 함께 지우면
  되돌림의 근거가 사라진다. 어느 게이트도 이 파일을 읽지 않는다(누출 스캔이 이름으로 제외한다).
- `reports/evidence/m6/6e2a/rollback.md`(이 문서) — 자기를 지우는 목록을 들면 대상이 라운드마다 움직여
  「실측 뒤 대상이 움직였는가」 확인이 영원히 깨진다.
- **`reports/evidence/m6/6e2a/codex-review-*`**(판정 JSON 과 그 형제 preflight) — **append-only 리뷰
  기록**이다. 심판 레인이 쓰고 구현 레인이 고치지 않으며, 「그 SHA 에 그 판정이 있었다」는 사실은 산출물을
  되돌려도 참으로 남아야 한다. scope.md 와 같은 취급이다. **이 셋이 ⓪ pathspec 에서 빠지는 것이 PR #66
  #1 의 처방이다** — 앞 판은 이 제외가 없어, 실측 뒤 심판 레인이 JSON 둘을 더하자 유효성 등식이 빈 출력을
  내지 못했다(목록이 낡은 것이 아니라 **pathspec 이 리뷰 기록까지 집어삼킨 것**이 원인이다).

`CLAUDE.md`·`.claude/**`·`docs/harness/**` 는 **되돌리지 않는다**(하네스 레인). 그 커밋의 **개수와 목록을
이 문서에 적지 않는다** — 라운드마다 늘어 반드시 낡는다. 산출은 명령이 한다:

```sh
git log --oneline $BASE..HEAD -- CLAUDE.md .claude/ docs/harness/
```

그 커밋들이 만지는 파일은 아래 ③' 의 잔여 목록에 **그대로 남는 것이 맞다** — slice 산출물이 아니고
in_scope 밖이다. ③' 를 읽을 때는 위 명령을 먼저 돌려 잔여가 그 집합으로 설명되는지 본다.

## ⓪ 복원 목록 — 기계 산출

```sh
BASE=2deb5f9d
git diff --name-status $BASE..HEAD -- adapters app config/quality docs/runbook gradle/libs.versions.toml \
  reports/evidence/m6/6e2a \
  ':!reports/evidence/m6/6e2a/scope.md' ':!reports/evidence/m6/6e2a/rollback.md' \
  ':!reports/evidence/m6/6e2a/codex-review-*'
```

산출 결과: **`A` 다섯 · `M` 열아홉**.

- `A`(제거 대상): `adapters/src/test/kotlin/bidvector/adapters/event/PooledLeaseFloorTest.kt` ·
  `app/src/test/kotlin/bidvector/app/AdminDataSource.kt` ·
  `app/src/test/kotlin/bidvector/app/wiring/ProductionPoolRoleTest.kt` ·
  `reports/evidence/m6/6e2a/commands.md` · `reports/evidence/m6/6e2a/checklist.md`
- `M`(base 로 restore): `adapters/build.gradle.kts` · `adapters/src/test/.../event/PoolerLeaseProbeTest.kt` ·
  `.../event/PostgresAdvisoryLockLeaseTest.kt` · `.../persistence/PersistenceTestSupport.kt` ·
  `.../relay/RelayLeaseLossDatabaseTest.kt` · `app/build.gradle.kts` ·
  `app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt` ·
  `app/src/test/.../compatibility/BootCompatibilitySmokeTest.kt` ·
  `app/src/test/.../evaluation/EvaluationCommitRunE2ETest.kt` ·
  `app/src/test/.../http/EvaluationDryRunBidNowE2ETest.kt` · `.../http/EvaluationDryRunE2ETest.kt` ·
  `.../http/HttpTestSupport.kt` · `.../http/ProductionAssemblyAuthAuditTest.kt` ·
  `.../http/StrategyEditProductionE2ETest.kt` · `app/src/test/.../packaging/BootJarRuntimeClasspathTest.kt` ·
  `config/quality/architecture-policy.properties` · `config/quality/gate-tests.properties` ·
  `docs/runbook/m6-6e-operations.md` · `gradle/libs.versions.toml`

**공유 파일 확인** — 이 range 에서 각 파일을 만진 커밋이 이 slice 의 것뿐인지 본다.

```sh
for f in config/quality/gate-tests.properties config/quality/architecture-policy.properties \
         docs/runbook/m6-6e-operations.md gradle/libs.versions.toml app/build.gradle.kts \
         adapters/build.gradle.kts milestone-6.md; do
  printf '%s: ' "$f"; git log --no-merges --format=%h $BASE..HEAD -- "$f" | tr '\n' ' '; echo
done
```

실행 결과: 여섯 파일 전부 **이 레인의 커밋만**이고 `milestone-6.md` 는 **빈 출력**(이 레인이 건드리지
않았다). 다른 slice 의 줄이 섞이지 않았으므로 **hunk 격리가 필요 없고** 전부 `base..HEAD` 단일 restore
로 끝난다. 팀장 레인이 뒤에 `milestone-6.md`(착수·종결 문단)를 쓰면 그 파일만 **커밋 해시로 hunk 를
격리해 최신부터 역적용**한다(`git diff <sha>~1..<sha> -- milestone-6.md | git apply -R`) — 위 루프가 그
해시를 **실행 시 산출**하므로 이 문서에 해시를 박지 않는다.

## ① 되돌림

```sh
BASE=2deb5f9d
git restore --source=$BASE --staged --worktree -- \
  adapters/build.gradle.kts \
  adapters/src/test/kotlin/bidvector/adapters/event/PoolerLeaseProbeTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/event/PostgresAdvisoryLockLeaseTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/relay/RelayLeaseLossDatabaseTest.kt \
  app/build.gradle.kts \
  app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt \
  app/src/test/kotlin/bidvector/app/compatibility/BootCompatibilitySmokeTest.kt \
  app/src/test/kotlin/bidvector/app/evaluation/EvaluationCommitRunE2ETest.kt \
  app/src/test/kotlin/bidvector/app/http/EvaluationDryRunBidNowE2ETest.kt \
  app/src/test/kotlin/bidvector/app/http/EvaluationDryRunE2ETest.kt \
  app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt \
  app/src/test/kotlin/bidvector/app/http/ProductionAssemblyAuthAuditTest.kt \
  app/src/test/kotlin/bidvector/app/http/StrategyEditProductionE2ETest.kt \
  app/src/test/kotlin/bidvector/app/packaging/BootJarRuntimeClasspathTest.kt \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  docs/runbook/m6-6e-operations.md \
  gradle/libs.versions.toml
git rm -f -- \
  adapters/src/test/kotlin/bidvector/adapters/event/PooledLeaseFloorTest.kt \
  app/src/test/kotlin/bidvector/app/AdminDataSource.kt \
  app/src/test/kotlin/bidvector/app/wiring/ProductionPoolRoleTest.kt \
  reports/evidence/m6/6e2a/commands.md \
  reports/evidence/m6/6e2a/checklist.md
```

`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 신설 경로마다 pathspec 오류로 끊긴다.

## 확인 ①~⑥ — 임시 clone 실측

임시 clone(HEAD `1dce56ab`)에서 위 명령을 **그대로** 돌렸다. PR #66 조치가 복원 집합의 파일 다섯과
evidence 둘을 만졌고 ⓪ 의 pathspec 자체도 바뀌었으므로 **①~⑥ 을 다시 쟀다** — 앞 라운드의 실측을
옮기지 않는다.

| # | 확인 | 실측 |
|---|---|---|
| ⓪ | 목록 기계 산출 · 문서 목록과의 양방향 차집합 | `A` **5** · `M` **19** · 그 밖 0 · 차집합 **M 0 · A 0**(이 라운드도 파일을 늘리지 않아 집합은 그대로다) |
| ① | `git restore` · `git rm` 종료 코드 | 둘 다 **exit 0** |
| ② | 되돌림 뒤 staged 상태의 D/M 수 | **D 5 · M 19** |
| ③ | `git diff <base> -- <대상 전부>` | **0 줄**(빈 출력) |
| ③' | base 대비 트리에 남은 것 | **여덟** — 전부 **slice 산출물이 아닌 것**이고 세 묶음으로 설명된다: 대상 밖 선언 넷(`scope.md` · 이 문서 · `codex-review-*` 둘) · **하네스 레인** 셋(`CLAUDE.md` · `.claude/skills/codex-review-gate/SKILL.md` · `docs/harness/change-history.md`) · **팀장 승인 문서** 하나(`milestone-6.md`). 수를 세어 외우지 말고, 위 하네스 산출 명령과 이 세 묶음으로 **설명되는지**를 본다 |
| ④⑤⑥ | 되돌린 트리에서 `./gradlew --no-daemon check` | **exit 0**(9m 20s) — 컴파일·test·게이트 전건 |

**`milestone-6.md` 는 ⓪ pathspec에 없다** — 이 레인이 건드리지 않았고(착수·종결 문단은 팀장 레인) 그
파일을 되돌려야 하면 위 「공유 파일 확인」의 루프가 내는 **커밋 해시로 hunk 를 격리해 최신부터
역적용**한다. 그 경로를 ⓪ 에 넣으면 팀장의 다른 slice 줄까지 걷는다.

④⑤⑥ 을 따로 쪼개지 않은 이유: `check` 가 그 셋을 모두 포함하고, 이 slice 가 닿은 게이트
(`gateRegistrationGate`·`compatibilitySmoke`·`moduleDependencyGate`·전송 표면 게이트)가 전부 그 안에 있다.
부분 게이트는 **안 돌린 것과 같게** 취급한다.

**갈음의 기준은 「HEAD 초록」이 아니라 트리 동일성이다.** ③ 이 빈 출력이면 되돌린 트리는 base 트리에
③' 의 여덟(계약·이 문서·리뷰 기록 둘·하네스 레인 셋·팀장 승인 문서 — 전부 slice 산출물이 아니다)만
더한 것이고, ④⑤⑥ 은 그 사실 위에서 **실제로 돌려** 확인했다.

## verifier 가 보는 등식

```sh
git diff --name-only <실측 HEAD>..<판정 SHA> -- <⓪ 가 산출한 경로들>
```

**빈 출력이면 이 실측이 유효하다.** 한 줄이라도 나오면 그 뒤 커밋이 되돌림 대상을 바꾼 것이므로
미검증이다(「실측 HEAD == 판정 SHA」가 아니다 — evidence 커밋은 언제나 뒤에 온다. 이 문서 자신은 대상이
아니므로 이 문서를 고치는 커밋은 그 등식을 깨지 않는다).

## 비활성화 — 되돌리지 않고 끄는 법

`PersistenceWiring.dataSource` 의 본문을 base 의 `PGSimpleDataSource` 한 덩어리로 되돌리는 것이 최소
비활성화다(한 함수). 그러면 풀도 역할 전환도 사라지고 migration 이 같은 연결로 돌아간다 — **그 상태의 앱
세션은 다시 DB 소유자 권한을 쥔다**(6E-1 이 기록한 상태). 다만 그 되돌림만으로는 **test 가 붉다**:
`ProductionPoolRoleTest` 와 `PooledLeaseFloorTest` 가 풀을 요구하므로 함께 지워야 한다(위 `A` 목록의 둘).
**설정 키로 끄는 길은 없다**(운영자 결정 A-2 (a) — 설정 키를 만들지 않는다). 재배포가 필요하다.
