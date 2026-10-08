# M6/6E-2a — rollback 절차와 실측

실측 HEAD: `(TBD — evidence 커밋 뒤 채운다)` · base: `2deb5f9d`.

되돌림 대상은 range 가 아니라 **in_scope 경로의 변경**이다. 목록은 손으로 쓰지 않고 ⓪ 가 산출한다 —
**라운드마다 파일이 늘면 이 절차를 다시 돌린다.**

**대상에서 빠지는 둘을 선언한다.**
- `reports/evidence/m6/6e2a/scope.md` — 팀장 소유 **계약 문서**다. 되돌림을 지시하는 자리라 함께 지우면
  되돌림의 근거가 사라진다. 어느 게이트도 이 파일을 읽지 않는다(누출 스캔이 이름으로 제외한다).
- `reports/evidence/m6/6e2a/rollback.md`(이 문서) — 자기를 지우는 목록을 들면 대상이 라운드마다 움직여
  「실측 뒤 대상이 움직였는가」 확인이 영원히 깨진다.

`CLAUDE.md`·`.claude/**` 는 **되돌리지 않는다**(하네스 레인). 이 range 의 하네스 레인 커밋은 **0** 이다
(`git log --oneline 2deb5f9d..HEAD -- CLAUDE.md .claude/` 빈 출력).

## ⓪ 복원 목록 — 기계 산출

```sh
BASE=2deb5f9d
git diff --name-status $BASE..HEAD -- adapters app config/quality docs/runbook gradle/libs.versions.toml \
  reports/evidence/m6/6e2a \
  ':!reports/evidence/m6/6e2a/scope.md' ':!reports/evidence/m6/6e2a/rollback.md'
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

(MEASURE)

**갈음의 기준은 「HEAD 초록」이 아니라 트리 동일성이다.** ③ 이 빈 출력이면 되돌린 트리는 base 트리에
`scope.md`·`rollback.md` 둘(어느 게이트도 읽지 않는 문서)만 더한 것이고, ④⑤⑥ 은 그 사실 위에서
**실제로 돌려** 확인했다.

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
