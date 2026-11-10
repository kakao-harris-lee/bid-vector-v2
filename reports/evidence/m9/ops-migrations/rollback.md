# M9 / ops-migrations rollback

착수 base **`df4976cc`** · **복원 원천 `07140729`** · **실측 HEAD `eac93b15`**(라운드 10 의 마지막
산출물 커밋).

**원천은 언제나 「이 브랜치가 마지막으로 합친 main 의 고정 SHA」다**(라운드 10 — 앞 판은
`3101035f`). 이 브랜치가 만든 main 병합은 **넷**이다 — `56bd33ea`(paper-bid V25) ·
`3101035f`(CVE chore #142 · STR12 telegram #127 · pitest #136) · `70406318`(award-ingest #128) ·
**`07140729`(ml-lifecycle #139 — 마지막, 그래서 원천)**. 움직이는 `origin/main` 을 쓰지 않는다.
전제 둘을 함께 쟀다 — 원천에 `V26__outbox_terminal_reason.sql`·`V27__execution_row_release.sql` 이
**없고**(`git cat-file -e` 둘 다 실패), 이 slice 의 첫 산출물 커밋은 `07140729` 의 조상이
**아니다**(그 SHA 에 이 slice 가 없다).

**병합이 「남의 변경 보존」 축을 실재하게 만들었다** — 처음 두 라운드까지는 병합 0 이라 그 축이
없었다. 이제 복원 뒤에 main 이 들여온 것(V25 `bid_kind` · portal-02 의 `02` 경로와 그 되돌림 인자
test · STR12 발송 축 · **award-ingest 의 수집 축**)이 **남아 있어야** 하고, 아래 표지 계수가 그것을
센다. 마지막 병합이 들여온 것은 ml-engine 쪽이라 되돌림 목록과 거의 겹치지 않는다 — 그래서 이
라운드가 늘린 「남의 표지」는 award-ingest 의 넷이다.

**되돌릴 마이그레이션은 둘이다** — 계약 갱신(2026-11-06)으로 DEC-08 축이 빠지고 번호가 당겨졌다:
`V26__outbox_terminal_reason.sql`(OPS-05) · `V27__execution_row_release.sql`(OPS-10).

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고, 그 확인 명령은 §4 다.

## 1. 되돌리는 법 — 미적용 DB(머지 전·CI)

range revert 가 아니라 **in_scope 경로 한정 복원**이고 목록은 기계가 낸다.

```
MAIN=07140729                                         # 이 브랜치가 **마지막으로 합친** main 의 고정 SHA
EXCL=':(exclude)reports/evidence/m9/ops-migrations'   # 자기 자신은 되돌릴 수 없다

# 목록은 **두 트리의 차이**다 — 이력이 아니다(CLAUDE.md 「기계 산출」).
changes() { git diff --name-status "$MAIN..HEAD" -- . "$EXCL"; }
mapfile -t RESTORE < <(changes | awk '$1=="M"||$1=="D"{print $2}')  # 원천에 있다 → 복원(내가 지운 것도 되살아난다)
mapfile -t DELETE  < <(changes | awk '$1=="A"{print $2}')           # 원천에 없다 → 지운다

# M/D/A 밖의 상태(R·C 등)가 한 줄이라도 나오면 멈춘다 — 이 절차가 다루는 모양이 아니다.
changes | awk '$1!="M"&&$1!="D"&&$1!="A"' | command grep -q . && exit 3

git restore --source=$MAIN --staged --worktree -- "${RESTORE[@]}"
git rm -q -f -- "${DELETE[@]}"
```

`git rm -f` 인 이유는 색인까지 지워야 복원 뒤 색인이 원천과 같아지기 때문이다.

`DELETE` **아홉** 가운데 **둘이 V 파일**(V26·V27)이다. 미적용 DB 에서는 그 파일들이 사라지는 것이
되돌림의 전부다 — Flyway 이력에 없으므로 흔적이 0 이고 체크섬 충돌도 없다.

### 실측이 고친 자리 — 목록을 이력에서 내면 깨진다

앞 판들은 목록을 **`git log --name-only`(이력)** 로 냈다. 이 slice 는 범위 **안에서** 파일을 넣었다
뺐으므로(DEC-08 제외 · 번호 당김) 그 집합에 **양쪽 트리 어디에도 없는 경로 여섯**이 섞였다 — DEC-08
축 넷과 번호 당김이 남긴 옛 이름 둘(`V27__outbox_terminal_reason.sql`·`V28__execution_row_release.sql`).
`git rm` 은 그 pathspec 에서 **exit 128**(`did not match any files`)로 멈춰 복원이 반쪽에서 끝났고,
그것을 메우려고 「원천·HEAD 양쪽에 `cat-file -e` 로 묻는 세 갈래」를 들였다.

**목록을 두 트리의 차이로 내면 그 갈래가 구성상 생기지 않는다** — 어디에도 없는 경로는 diff 에
나타날 수 없다. 라운드 10 실측: M/D/A **밖의 상태 0 줄**, 「할 일 0」 갈래 **0**. 세 갈래 장치를
걷어내고 위의 두 갈래로 되돌렸다.

## 2. 되돌리는 법 — 적용 DB(배포 뒤)

Flyway community 에 undo 가 없으므로 **forward-fix** 다. 새 V 하나가 두 축을 거꾸로 밟는다.

```sql
-- ① OPS-10(V27) — 관문 트리거 → 함수 → 제약 → 열 순서. 트리거가 남은 채 열을 떨구면 함수가 깨진다.
DROP TRIGGER outbox_release_sha_required ON outbox;
DROP TRIGGER collection_run_release_sha_required ON collection_run;
DROP FUNCTION require_release_sha();
ALTER TABLE outbox DROP CONSTRAINT outbox_release_sha_check, DROP COLUMN release_sha;
ALTER TABLE collection_run DROP CONSTRAINT collection_run_release_sha_check, DROP COLUMN release_sha;

-- ② OPS-05(V26) — 같은 순서. 사유 칸이 사라지면 「왜 종단인가」는 다시 로그만 안다(되돌림의 대가).
DROP TRIGGER outbox_terminal_reason_required ON outbox;
DROP FUNCTION require_outbox_terminal_reason();
ALTER TABLE outbox DROP CONSTRAINT outbox_terminal_reason_state_check, DROP COLUMN terminal_reason;
```

**행 처분이 필요 없다** — 두 축이 더한 것은 전부 **열·제약·트리거**이고 열을 떨구면 그 값도 함께
간다. V24 선례(새 어휘 행을 먼저 지워야 `ADD CONSTRAINT` 가 서는 모양)와 다른 축이다.

**이 갈래는 §1 의 파일 삭제와 배타다.** 적용 뒤 V 파일을 지우면 Flyway `validateOnMigrate` 가
「applied migration not resolved locally」로 기동을 막는다 — 적용 뒤에는 새 V 뿐이다.

**순서는 앱 되돌림이 먼저다**(V24 와 반대). 옛 앱은 이 열들을 모르므로 열이 사라져도 멀쩡하지만,
**새 앱은 열이 없으면 `INSERT` 가 깨진다**(outbox 등록·회계 기록이 `release_sha` 를 싣고 종단
전이가 `terminal_reason` 을 싣는다). 설정 쪽도 같은 축이다 — `bidvector.release.sha` 를 지우는 것은
옛 jar 로 돌아간 **뒤**라야 한다(새 jar 는 그 값 없이 못 뜬다).

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`, HEAD `eac93b15`)

| 단계 | 실측 |
| --- | --- |
| ① 목록 산출 | 원천 `07140729` 대비 **102**(evidence 제외) = 복원 **93**(M/D) · 삭제 **9**(A) · **그 밖의 상태 0**. `DELETE` 아홉 가운데 V 파일 둘이 V26·V27 이다 |
| ①′ **목록 완전성** | 원천과 다른 경로 전부가 목록 안에 있다 — `comm -23` **0 줄** |
| ①″ **빈 등식이 아니다** | 두 갈래 모두 비어 있지 않다(93·9). 「내가 커밋하지 않아도 경로가 들어오는가」를 먼저 묻는 자리이고, 빈 목록이 내는 0 과 가른다 |
| ①‴ **재측정한 이유** | 원천이 `3101035f` → **`07140729`** 로 옮겼다(라운드 10 의 병합 둘). 복원 갈래가 98 → **93** 으로 **준** 것은 목록을 이력이 아니라 두 트리의 차이로 냈기 때문이다(위 「실측이 고친 자리」). **마지막 산출물 커밋 `eac93b15` 에서** 다시 쟀고 앞 실측 `8c1cddb4` 를 옮기지 않았다 |
| ② 명령 종료 코드 | `restore` **0** · `git rm -f` **0** — 둘 다 실행했다 |
| ③ 결과 트리 == `07140729` 트리 | 복원·삭제만으로 `git diff --name-only 07140729 -- . :(exclude)<evidence>` **0 파일** · 미추적 **0** · 남은 V25+ 마이그레이션은 **V25 하나**(paper-bid 것이고 내 둘은 사라졌다) |
| ③′ 남의 변경 보존 | **병합 넷**이 들여온 것이 남아야 한다 — 아래 표지 계수가 STR12 발송 축과 award-ingest 수집 축까지 센다 |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | `read-tree` 갈래에서는 **0**(evidence 까지 함께 되돌아간다). §1 의 실제 절차는 evidence 를 `EXCL` 로 빼므로 그 갈래에서는 이 디렉터리의 파일만 남고, §4 의 술어가 그 디렉터리를 대상에서 빼므로 유효성은 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 `07140729` 의 트리와 **파일 단위로 같다**(③ 의 0 파일). 그 커밋은 머지된 main 이고
CI 가 초록으로 통과한 자리다. 같은 트리에서 `check` 를 다시 돌리는 것은 같은 입력을 다시 재는 일이다.
`read-tree` 갈래는 evidence 까지 되돌리므로 「같지 않은 입력」이 **없다**.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 센 **파일 수**다(§1 절차가 복원하지 않는 evidence 디렉터리는 센 대상에서 뺀다 — 그
디렉터리에는 이 slice 의 어휘가 당연히 남는다). 종료 코드가 아니라 이 계수가 완료의 증거다.

**복원 전을 같은 모집단에서 먼저 쟀다**(도구 양성 대조) — 아래 「내 표지」 열다섯이 복원 전에는
**전부 0 이 아니다**(13·12·39·28·2·37·1·3·1·1·2·5·3·3·1, V26·V27 파일 2). 복원 뒤의 0 이 「없앴다」
인지 「못 세고 있다」인지를 가르는 것은 이 한 쌍뿐이다.

| 내 표지(0 이어야 한다) | 복원 전 | 복원 뒤 |
| --- | --- | --- |
| `OutboxTerminalReason` · `terminal_reason` · `ReleaseIdentifier` · `bidvector.release.sha` · `release_sha_required` | 13 · 12 · 39 · 28 · 2 | **전부 0** |
| `ReleaseWiring`(조립과 그 게이트 등재 — 라운드 4 가 더한 축) | 37 | **0** |
| `journey-e2e-release`(여정 하네스에 더한 줄) | 1 | **0** |
| `OutboxGateValues`(라운드 4 가 더한 test 지원 타입) | 3 | **0** |
| `VERSIONS_TOUCHING_EXISTING_TABLES` · `ContextInitialized`(라운드 7 이 더한 census·중단 표지) | 1 · 1 | **0** |
| `MIGRATION_LOCK_TIMEOUT` · `ownerMigration` · `MigrationLockBoundTest`(라운드 8 의 연결 상한과 그 test) | 2 · 5 · 3 | **0** |
| `CheckConstraintRenderingFixpointTest` · `check_rendering_fixpoint_probe`(라운드 10 의 왕복 고정점 test 와 그 탐침) | 3 · 1 | **0** |
| V26·V27 마이그레이션 파일 | 2 | **0** |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `release_sha`(V1 `raw_observation` — OPS-10 조인의 원천. HEAD 는 **29**, 내 여덟이 빠진다) | 21 |
| `OutboxEntryState`(M4/4C-1) | 17 |
| `BIDVECTOR_RELEASE_TAG`(M7/7F) | 12 |
| `forceOutboxState`(relay DB 지원 — 내가 **사유를 더한** 함수가 원판으로 돌아왔다) | 5 |
| `OUTBOX_STATE_VOCABULARY`(보존 fixture — 같은 축) | 3 |
| `COMMON_PROPERTIES`(여정 하네스 — 파일은 남고 내 한 줄만 사라진다) | 1 |
| `ProfileWiring`(면제 목록의 남의 이름 — 목록 자체가 원판으로 돌아왔다) | 17 |
| `PRODUCTION_DISPATCH_PROPERTIES`(출하 기동점 — 내 test 만 사라지고 기동점은 남는다) | 18 |
| **`bid_kind`**(paper-bid V25 — 첫 병합이 들여온 축) | 35 |
| **`NotificationBidKind`**(그 축의 workflow 타입) | 31 |
| **`BidPublicInfoService02`**(portal-02 전환) | 6 |
| **`KonepsRollbackArgumentsTest`**(main 의 새 test) | 7 |
| **`NotificationChannelSettings`**·**`TelegramNotificationSender`**·**`HttpClientLogProperty`**(STR12 발송 축 — **둘째 병합**이 들여왔고 내 복원이 지우지 않는다) | 7 · 9 · 6 |
| **`AwardCollectionWiring`**·**`KonepsAwardListSource`**·**`CollectAwardListUseCase`**·**`policy_approvals`**(award-ingest 수집 축 — **셋째 병합**이 들여왔다. 내 복원이 그 배선의 릴리스 인자만 되돌리고 축 자체는 남긴다) | 9 · 6 · 11 · 11 |
| **`SET LOCAL lock_timeout`**(V18 이 세운 관례 — 내 둘이 빠지고 남의 열둘이 남는다) | 12 |
| **`MigrationLockTimeoutTest`**(그 test 는 main 것이다 — 내 lint·V26 거동 둘만 빠져 **@Test 가 4 → 2**) | 9 |
| 래칫 넷 | `EXPECTED_EXEMPT_COUNT` **94**(main 의 값. HEAD 는 **95** — 내 `ReleaseWiring` 하나가 빠졌다) · `collection_run` **13** · `outbox` **11**(HEAD 는 14 · 13 — 내 증분이 사라졌다)이고 `notification_inbox` **7** 은 **남는다**(V25 의 증분, HEAD 와 같다) |

**계수 전에 도구를 먼저 쟀다**(라운드 9 가 실패로 배운 자리). 첫 시도는 `grep` 플래그를 경로
**뒤**에 둬 `--exclude-dir` 가 파일 이름으로 읽혔고, evidence 가 모집단에 남아 내 표지가 전부 0 이
아니었다. 고친 뒤 **양성 대조**(`OutboxEntryState` 17 · `bid_kind` 34 — 0 이 아님)를 먼저 확인하고
위를 셌다. 모집단은 §1 절차와 같다 — **이 slice 의 evidence 디렉터리만** 뺀다(`reports/` 전체를
빼면 같은 트리에서 `OutboxEntryState` 가 10 으로 떨어진다. 그 수는 다른 질문의 답이다).

앞 판의 `raw_observation.release_sha` 행은 **그 축어가 저장소에 없다**(`ColumnSpec("raw_observation",
"release_sha", …)` 로 떨어져 있어 2 는 evidence 쪽을 세고 있었다) — 실재하는 토큰 `release_sha` 로
바꿔 쟀고, 「남는다」는 복원 뒤 21 과 HEAD 29 의 차이가 센다.

내 표지를 고를 때 `release_sha` 단독은 쓸 수 없다 — V1 의 `raw_observation.release_sha` 가 그
이름을 이미 들고 있어 **남의 줄까지 센다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다.

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

```
git diff --name-only eac93b15..<판정 SHA> -- \
  $(git diff --name-only 07140729..eac93b15 \
      -- . ':(exclude)reports/evidence/m9/ops-migrations' | sort -u)
```

되돌림 목록과 **같은 산출법**(두 트리의 차이)을 쓴다 — §1 이 이력에서 두 트리의 차이로 옮겼으므로
이 술어도 함께 옮겼다. 두 목록이 다른 방식으로 나면 「되돌리는 경로」와 「움직였는지 보는 경로」가
어긋난다.

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 5. 적용 DB 리허설 — **실측**(팀장 허용 1회, 버릴 clone · 버릴 클러스터)

§2 의 forward-fix 를 임시 `V28__rollback_rehearsal.sql` 로 두고 **Flyway 로** 적용했다(장부 기입까지).
임시 V 와 임시 test 는 **커밋하지 않았다**.

**이 리허설은 병합 전 스키마에서 돌았다**(V25 없이, 라운드 3). 다시 돌리지 않은 근거: §2 의
forward-fix 가 손대는 객체는 `outbox`·`collection_run` 의 열·제약·트리거뿐이고 V25 가 바꾼 것은
`notification_inbox`(열 하나 · CHECK 둘 · PK 한 칸)라 **겹치는 객체가 0** 이다. 적용 순서
V25 → V26 → V27 자체는 병합 뒤 전건 `:adapters:test`(clean-migrate 전수)가 초록으로 실측했다.
**뒤의 병합 셋(`3101035f`·`70406318`·`07140729`)은 마이그레이션을 0 개 들여온다** — 복원 트리의
V25+ 파일이 V25 하나뿐인 것이 그 계수이고, 마지막 병합은 `git diff --name-status 3101035f..07140729
-- <migration dir>` 가 **빈 출력**이다. 그래서 이 근거는 그대로 선다.

| 단계 | 실측 |
| --- | --- |
| 되돌림 전 — 열 셋 존재 | `outbox.terminal_reason` · `outbox.release_sha` · `collection_run.release_sha` **있음** |
| 되돌림 전 — 관문 트리거 셋 | 이름 집합이 기대와 같다(소유자 연결 — `CleanMigrationTriggerTest` 와 같은 모집단). app 역할에서도 셋이 다 보인다는 것은 verifier probe 가 따로 실측했다 |
| 되돌림 전 — 거부 셋 | 릴리스 없는 outbox INSERT · 사유 없는 종단 UPDATE · 릴리스 없는 회계 INSERT **전부 거부** |
| forward-fix 적용 | `migrationsExecuted` **1** · 적용 version **28** · `flyway_schema_history` 마지막 **28** |
| 되돌림 후 — 열·트리거·함수 | 열 셋 **없음** · 관문 트리거 **0** · 함수 둘 **0** |
| 되돌림 후 — **음성 대조** | 앞서 거부됐던 두 INSERT 가 **통과한다**(관문이 조용해진 것이 아니라 없어졌다). outbox 행 2 · 회계 행 2 |
| 적용 전 행 | 종단 행의 상태가 그대로(`FAILED`) — **행 처분이 필요 없다**는 §2 의 문면이 실측됐다 |

test 실행 계수: `tests="1" skipped="0" failures="0" errors="0"`.

**이 리허설이 잡은 것 둘**(둘 다 리허설 자신의 결함이고 산출물 결함이 아니다): ⓐ 첫 라운드에서
**app 역할 연결**의 `information_schema.triggers` 가 트리거 셋 가운데 둘만 냈다 — 메타데이터
단언을 admin 연결로 옮겼다. ⓑ 거부된 INSERT 를 행 수에 세는 기대 오류 하나.

ⓐ 의 **괄호 설명을 정정한다**(v L-1): 「`CleanMigrationTriggerTest` 가 app 풀로 세기 때문에 이
축이 CI 판정을 가른다」는 **거짓**이다 — 그 test 는 `dataSource()`(= 소유자 연결)로 센다. 「app
풀에서도 셋이 보인다」는 단언은 커밋되지 않은 리허설 test 에만 있었고, verifier 의 probe 는 `SET
ROLE bidvector_app` 에서 **새 트리거 셋이 모두 보인다**고 실측했다(앱 권한이 두 표에 I/U 를
가져 `information_schema.triggers` 의 가시성 조건을 만족한다). 「둘만 보였다」는 재현되지 않았고
원인 기록도 없다. 커밋된 단언(트리거 이름 셋, 소유자 연결)은 이름 래칫으로 유효하고, 별도의 app
가시성 단언은 필요 없다.

## 6. 코드를 되돌리지 않고 끄는 법

**없다 — 두 축 다 스위치를 더하지 않았다.** 사유·릴리스는 쓰기 경로가 늘 싣고, 설정 키는 기본값이
없어 「끄기」가 곧 기동 실패다. 끄는 것에 가장 가까운 것은 §2 의 forward-fix 이고, 그때 「왜 종단
인가」는 다시 로그만 알게 된다.
