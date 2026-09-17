# M6/6B-1 — rollback.md

## 명령

`base_sha`(`c4d09cc`)로 in_scope 경로만 되돌린다 — range revert 가 아니다(하네스
2026-09-04). 목록은 `git diff --name-status c4d09cc..HEAD -- <in_scope 경로>`로 기계
산출한 뒤, **evidence 문서 셋(`commands.md`·`checklist.md`·`rollback.md`)을 의도적으로
뺀다**(아래 근거) — 그 셋을 뺀 나머지는 산출된 그대로 쓰고 손으로 고치지 않는다.
**라운드마다 파일이 늘면 이 산출을 다시 돌린다**(verifier r1 LOW-3 시정 — "손으로 쓰지
않는다"였던 이전 문구가 이 의도적 제외까지 "기계 생성"으로 뭉뚱그려 부정확했다).

```bash
git restore --source=c4d09cc --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/persistence/Sql.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/EditSessionRow.kt \
  adapters/src/main/kotlin/bidvector/adapters/strategy/JdbcEditSessionRepository.kt \
  adapters/src/main/resources/db/migration/V8__edit_session.sql \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationCheckTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationColumnTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/CleanMigrationTriggerTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/EditSessionWorkflowTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcEditSessionRepositoryTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/JdbcEditSessionSaveGuardTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/persistence/PersistenceTestSupport.kt \
  adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt \
  app/src/test/kotlin/bidvector/app/conformance/StrategyEditExecutors.kt \
  config/quality/gate-tests.properties \
  milestone-6.md \
  reports/evidence/m6/6b1/scope.md \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionRestore.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditSessionSnapshot.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/EditStrategyWorkflow.kt \
  workflow/src/main/kotlin/bidvector/workflow/strategy/Ports.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditSessionSnapshotTest.kt \
  workflow/src/test/kotlin/bidvector/workflow/strategy/EditStrategyWorkflowTest.kt
```

**verifier r4 MEDIUM-6 시정** — 수정 라운드 2(sizeGate 분리)가 만든 신설 파일 둘
(`EditSessionWorkflowTestSupport.kt`·`JdbcEditSessionSaveGuardTest.kt`)이 위 목록에
빠져 있었다. verifier 가 실제로 그 명령을 실행해 재현했다 — 두 파일만 남고 저장소
구현(`JdbcEditSessionRepository`·`EditSessionRow` 등)이 삭제돼 `:adapters:compileTestKotlin`
이 `Unresolved reference`로 exit 1. 위 목록은 `git diff --name-status c4d09cc..HEAD`를
다시 돌려 재산출했다(손으로 두 줄 끼워 넣지 않았다) — 아래 임시 clone 실측이 재발
방지 확인이다.

`--source`에 없는 경로(위 목록의 신규 파일 11개)는 삭제되므로 별도 `git rm`이 필요 없다
(`--staged --worktree` 조합, 하네스 2026-09-04). `reports/evidence/m6/6b1/commands.md`·
`checklist.md`·`rollback.md`(이 파일 자신)는 위 목록에 없다 — evidence 문서는 slice 종결
판단의 일부이므로 되돌리지 않고 그대로 둔다(같은 slice 안의 자기 이력이지 다른 slice
와의 공유가 아니다).

## 하네스 레인 변경 — 되돌리지 않는다

`milestone-6.md`는 scope.md in_scope에 「6B 분할·6B-1 착수 문단(팀장 커밋)」으로 명시돼
있고 `.claude/`·최상위 `CLAUDE.md` 경로 변경은 이 slice 범위에 없다(scope.md 「하네스
레인 변경」절 — 착수 시점 없음, 이후로도 없음). 그래서 `milestone-6.md`는 **위 목록에
포함해 되돌린다** — 하네스 경로 예외 대상이 아니다.

## 마이그레이션 비대칭 — 실제 DB에는 코드 롤백이 미치지 않는다

`V8__edit_session.sql`을 삭제해도 **이미 마이그레이션을 실행한 DB에는 `edit_session`
표가 그대로 남는다.** 되돌린 코드는 그 표를 참조하지 않으므로 애플리케이션 동작은
`base_sha` 시점과 같다. 실제 DB에서 표 자체를 지우려면:

```sql
DROP TABLE edit_session;
```

이 문은 **사용자 승인 대상**이고 운영 DB에는 실행하지 않는다(scope.md 원문). Testcontainers
기반 test 컨테이너는 빈 컨테이너에서 새로 시작하므로 이 비대칭의 영향을 받지 않는다 —
아래 실측은 그 사실(빈 컨테이너 재적용)도 함께 확인한다.

## 임시 clone 실측(2026-09-17, 전 항목 실행 완료)

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① clone | `git clone <워크트리> /tmp/6b1-rollback-verify` | exit 0 |
| ② 경로 한정 restore | 위 `git restore` 명령 | exit 0, `git status --short`가 위 목록과 정확히 일치(A→D 9건·M 12건) |
| ③ 내 줄 사라짐 | `adapters/src/main/kotlin/bidvector/adapters/strategy/`·`adapters/src/test/kotlin/bidvector/adapters/strategy/` 디렉터리 존재 확인 | 둘 다 `No such file or directory`(정상 삭제) |
| ④ compile | `./gradlew --no-daemon :workflow:compileKotlin :workflow:compileTestKotlin :adapters:compileKotlin :adapters:compileTestKotlin :app:compileKotlin :app:compileTestKotlin` | exit 0 |
| ⑤ test | `./gradlew --no-daemon :workflow:test :adapters:test :app:test --rerun-tasks` | exit 0 |
| ⑥ 게이트 | `./gradlew --no-daemon check`(전건 — 이 slice가 `:workflow`·`:adapters`·`:app` 세 모듈에 걸쳐 있어 부분 게이트로 좁히지 않는다) | exit 0 |

되돌린 트리는 컴파일·테스트·전체 품질 게이트가 전부 초록이다 — `edit_session`이 코드에서
사라진 상태에서 `CleanMigration*Test` 계열이 그 부재를 정확히 반영해 통과함을(base_sha
시점 기대치로 자동 복귀) 함께 확인했다. 임시 clone은 실측 뒤 삭제했다.

## 재실측(2026-09-17, verifier r4 MEDIUM-6 시정 뒤)

목록 재산출(신규 파일 둘 추가, A→D 9건 → **11건**, M 12건 불변) 뒤 같은 ①~⑥을 새
임시 clone(`/tmp/6b1-rollback-verify-r4`)에서 다시 실행했다 — verifier 가 재현한
`:adapters:compileTestKotlin` exit 1(`Unresolved reference`)이 **지금은 exit 0**.

| 단계 | 명령 | 결과 |
| --- | --- | --- |
| ① clone | `git clone --no-hardlinks <워크트리> /tmp/6b1-rollback-verify-r4` | exit 0 |
| ② 경로 한정 restore(신규 목록) | 위 `git restore` 명령(신규 파일 둘 포함) | exit 0, `git status --short`: D 11건·M 12건 — 목록과 정확히 일치 |
| ④ compile | 같은 6-태스크 compile 명령 | **exit 0**(verifier r4 재현 시점엔 exit 1) |
| ⑤ test | `:workflow:test :adapters:test :app:test --rerun-tasks` | exit 0 |
| ⑥ 게이트 | `./gradlew --no-daemon check`(전건) | exit 0 |

임시 clone은 실측 뒤 삭제했다. Testcontainers 컨테이너는 ryuk 가 회수했다(잔존 0 확인).

## 공유 파일 — 다른 slice와 겹치는 줄 없음

이 slice가 편집한 공유 파일(`Sql.kt`·`gate-tests.properties`·`CleanMigration*Test` 넷·
`PersistenceTestSupport.kt`·`StrategyEditExecutors.kt`)은 전부 **이 slice(6B-1)의 커밋
만**이 만졌다 — `base_sha` 이후 다른 slice가 아직 같은 파일에 병합되지 않았다(병렬 레인
6F-1은 아직 별도 브랜치, 병합 전). 그래서 커밋 단위 hunk 격리 없이 `base_sha` 기준
단일 `git restore`로 안전하다. **6F-1이 먼저 병합되면** 이 절차를 재검토한다 — 특히
`adapters/src/main/kotlin/bidvector/adapters/strategy/`·`adapters/src/test/kotlin/bidvector/adapters/strategy/StrategyAdapterDependencyTest.kt`는
6F-1도 같은 패키지를 쓰기로 조율됐으므로(scope.md D-6B1-9 갱신 이력 참고) 그 시점에는
「내 줄만 걷고 6F-1 줄은 남기는」 hunk 격리가 필요해진다.
