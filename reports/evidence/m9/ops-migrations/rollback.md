# M9 / ops-migrations rollback

착수 base **`df4976cc`** · **복원 원천 `56bd33ea`** · **실측 HEAD `b5e7eef0`**(라운드 7 의 마지막
산출물 커밋).

**원천을 병합한 main 으로 다시 고정했다**(라운드 6) — 이 브랜치는 paper-bid(V25)가 들어간
main `56bd33ea` 를 합쳤다 — **내가 만든 병합은 하나**다(`git log --merges 56bd33ea..HEAD` 한 줄.
착수 base 부터 세면 21 이고 그 가운데 20 은 main 자신의 PR 병합이다 — 세는 구간을 원천으로
잡아야 「내 병합」이 나온다). 원천은 **그때 합친 main 의
고정 SHA** 이고 움직이는 `origin/main` 을 쓰지 않는다. 전제도 함께 쟀다 — 이 slice 의 첫 산출물
커밋은 `56bd33ea` 의 조상이 **아니다**(그 SHA 에 이 slice 가 없다).

**병합이 「남의 변경 보존」 축을 실재하게 만들었다** — 앞 라운드까지는 병합 0 이라 그 축이
없었다. 이제 복원 뒤에 main 이 들여온 것(V25 의 `bid_kind` · portal-02 의 `02` 경로 · 그 되돌림
인자 test)이 **남아 있어야** 하고, 아래 표지 계수가 그것을 센다.

**되돌릴 마이그레이션은 둘이다** — 계약 갱신(2026-11-06)으로 DEC-08 축이 빠지고 번호가 당겨졌다:
`V26__outbox_terminal_reason.sql`(OPS-05) · `V27__execution_row_release.sql`(OPS-10).

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」가 아니다 — 대조해야 하는 것은
**그 사이에 되돌림 대상이 움직였는가**이고, 그 확인 명령은 §4 다.

## 1. 되돌리는 법 — 미적용 DB(머지 전·CI)

range revert 가 아니라 **in_scope 경로 한정 복원**이고 목록은 기계가 낸다.

```
MAIN=56bd33ea                                         # 이 브랜치가 **실제로 합친** main 의 고정 SHA
EXCL=':(exclude)reports/evidence/m9/ops-migrations'   # 자기 자신은 되돌릴 수 없다

mapfile -t MINE < <(git log --no-merges --format= --name-only $MAIN..HEAD -- . "$EXCL" | sort -u)
# **세 갈래다**(아래 「실측이 고친 자리」) — 원천과 HEAD **양쪽**에 물어야 한다
MODIFIED=(); ADDED=(); HISTORY_ONLY=()
for f in "${MINE[@]}"; do
  m=0; h=0
  git cat-file -e "$MAIN:$f" 2>/dev/null && m=1
  git cat-file -e "HEAD:$f"  2>/dev/null && h=1
  if   [ $m -eq 1 ]; then MODIFIED+=("$f")   # 원천에 있다 → 복원(내가 지운 것도 되살아난다)
  elif [ $h -eq 1 ]; then ADDED+=("$f")      # 원천에 없고 HEAD 에 있다 → 지운다
  else HISTORY_ONLY+=("$f"); fi              # 양쪽에 없다 → **할 일 0**
done

git restore --source=$MAIN --staged --worktree -- "${MODIFIED[@]}"
git rm -q -f -- "${ADDED[@]}"
```

「있음/없음」을 `--diff-filter` 가 아니라 **`git cat-file -e`** 로 가른다(병합이 끼면 필터가 남의
변경을 M 으로 센다). `git rm -f` 인 이유는 색인까지 지워야 복원 뒤 색인이 원천과 같아지기 때문이다.

`ADDED` 일곱 가운데 **둘이 V 파일**(V26·V27)이다. 미적용 DB 에서는 그 파일들이 사라지는 것이
되돌림의 전부다 — Flyway 이력에 없으므로 흔적이 0 이고 체크섬 충돌도 없다.

### 실측이 고친 자리 — 「원천에 없음」만으로 가르면 깨진다

앞 판의 목록은 `ADDED` 를 **「원천에 없음」** 하나로 갈랐다. 이 slice 는 범위 **안에서** 파일을
넣었다 뺐으므로(DEC-08 제외 · 번호 당김) 그 집합에 **HEAD 에 없는 경로 여섯**이 섞였고, `git rm` 은
그 pathspec 에서 **exit 128**(`did not match any files`)로 멈춰 복원이 반쪽에서 끝났다 — 실측으로
드러난 결함이다. 그래서 위처럼 세 갈래로 가른다. 이 slice 의 `HISTORY_ONLY` **여섯**은 ⓐ DEC-08 축 넷(판정 표 마이그레이션 · Jdbc store · port ·
그 test)과 ⓑ **번호 당김이 남긴 옛 이름 둘**(`V27__outbox_terminal_reason.sql` ·
`V28__execution_row_release.sql`)이다. ⓑ 는 이 라운드가 잡은 결함(HEAD 트리에 버전 27 이 둘)의
**바로 그 경로들**이라 이름을 빼놓을 수 없다. 둘 다 원천에도 없으므로 되돌림이 **이미** 끝나 있다.

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

## 3. 실측 (버릴 clone, `git clone --no-hardlinks`, HEAD `b5e7eef0`)

| 단계 | 실측 |
| --- | --- |
| ① 목록 산출 | 내 커밋 경로 **108**(원천 `56bd33ea`, evidence 제외) = 복원 **95**(M) · 삭제 **7**(A) · **할 일 0 여섯**(HISTORY_ONLY). 세 갈래 합이 108 로 닫힌다. **목록 완전성**: 병합본이 main 과 다른 경로 91 전부가 이 목록 안에 있고(차집합 빈 출력), 병합 커밋이 목록 밖 경로를 만들지 않았다 |
| ①′ **재측정한 이유** | 라운드 7 이 복원 목록에 경로 **하나**를 더했다(`MigrationLockTimeoutTest` — cr H-1 의 census·거동). 그 뒤 형식 수정이 같은 목록 안의 파일 둘을 또 고쳤으므로 **마지막 산출물 커밋 `b5e7eef0` 에서** ①~③ 을 다시 쟀다(앞 실측 `a14f07f1` 을 옮기지 않았다) |
| ② 명령 종료 코드 | `restore` **0** · `git rm -f` **0** — 이 라운드는 **둘 다 실행했다**(앞 라운드는 이 환경의 안전 검사가 스크립트 안의 `git rm` 을 막아 끝 상태로만 쟀다. 경로를 명령행 인자로 넘기면 돈다) |
| ③ 결과 트리 == `56bd33ea` 트리 | 복원·삭제만으로 `git diff --name-only 56bd33ea -- . :(exclude)<evidence>` **0 파일** · 미추적 **0** · 남은 V25+ 마이그레이션은 **V25 하나**(paper-bid 것이고 내 둘은 사라졌다) |
| ③′ 남의 변경 보존 | **이제 실재하는 축이다**(병합 1) — 아래 표지 계수가 main 이 들여온 것을 센다 |
| ④⑤⑥ 복원 트리의 컴파일·test·게이트 | **트리 동일성으로 갈음** — 아래 |
| ⑦ 원천과 남는 차이 | `read-tree` 갈래에서는 **0**(evidence 까지 함께 되돌아간다). §1 의 실제 절차는 evidence 를 `EXCL` 로 빼므로 그 갈래에서는 이 디렉터리의 파일만 남고, §4 의 술어가 그 디렉터리를 대상에서 빼므로 유효성은 흔들리지 않는다 |

### ④⑤⑥ 의 갈음 — 「HEAD 초록」이 아니라 트리 동일성

복원한 트리는 `56bd33ea` 의 트리와 **파일 단위로 같다**(③ 의 0 파일). 그 커밋은 머지된 main 이고
CI 가 초록으로 통과한 자리다. 같은 트리에서 `check` 를 다시 돌리는 것은 같은 입력을 다시 재는 일이다.
`read-tree` 갈래는 evidence 까지 되돌리므로 「같지 않은 입력」이 **없다**.

### 표지 계수 — 「내 줄 사라짐」과 「남의 줄 남음」 둘 다

복원 뒤 센 **파일 수**다(§1 절차가 복원하지 않는 evidence 디렉터리는 센 대상에서 뺀다 — 그
디렉터리에는 이 slice 의 어휘가 당연히 남는다). 종료 코드가 아니라 이 계수가 완료의 증거다.

| 내 표지(0 이어야 한다) | 복원 뒤 |
| --- | --- |
| `OutboxTerminalReason` · `terminal_reason` · `ReleaseIdentifier` · `bidvector.release.sha` · `release_sha_required` | **전부 0** |
| `ReleaseWiring`(조립과 그 게이트 등재 — 라운드 4 가 더한 축) | **0** |
| `journey-e2e-release`(여정 하네스에 더한 줄) | **0** |
| `OutboxGateValues`(라운드 4 가 더한 test 지원 타입) | **0** |
| `VERSIONS_TOUCHING_EXISTING_TABLES` · `ContextInitialized`(라운드 7 이 더한 census·중단 표지) | **0** |
| V26·V27 마이그레이션 파일 | **0** |

| 남의 표지(남아 있어야 한다) | 복원 뒤 |
| --- | --- |
| `raw_observation.release_sha`(V1 — OPS-10 조인의 원천) | 2 |
| `OutboxEntryState`(M4/4C-1) | 17 |
| `BIDVECTOR_RELEASE_TAG`(M7/7F) | 12 |
| `forceOutboxState`(relay DB 지원 — 내가 **사유를 더한** 함수가 원판으로 돌아왔다) | 5 |
| `OUTBOX_STATE_VOCABULARY`(보존 fixture — 같은 축) | 3 |
| `COMMON_PROPERTIES`(여정 하네스 — 파일은 남고 내 한 줄만 사라진다) | 1 |
| `ProfileWiring`(면제 목록의 남의 이름 — 목록 자체가 원판으로 돌아왔다) | 17 |
| `PRODUCTION_DISPATCH_PROPERTIES`(출하 기동점 — 내 test 만 사라지고 기동점은 남는다) | 17 |
| **`bid_kind`**(paper-bid V25 — 병합이 들여온 축) | 34 |
| **`NotificationBidKind`**(그 축의 workflow 타입) | 31 |
| **`BidPublicInfoService02`**(portal-02 전환) | 6 |
| **`KonepsRollbackArgumentsTest`**·**`koneps-portal-02-rollback-args`**(main 의 새 test 와 그 정본 리소스) | 7 · 4 |
| **`SET LOCAL lock_timeout`**(V18 이 세운 관례 — 내 둘이 빠지고 남의 열둘이 남는다) | 12 |
| **`MigrationLockTimeoutTest`**(그 test 는 main 것이다 — 내 census·V26 거동 둘만 빠져 **@Test 가 4 → 2**) | 9 |
| 래칫 셋 | `EXPECTED_EXEMPT_COUNT = 81` · `collection_run` **13** · `outbox` **11**(내 증분이 사라졌다)이고 `notification_inbox` **7** 은 **남는다**(V25 의 증분) |

`OutboxEntryState` 가 앞 라운드 표의 **18** 과 다른 이유는 술어다 — 위 문단대로 evidence
디렉터리를 뺐고, 그 디렉터리의 파일 둘이 그 이름을 든다(포함해 세면 19). 산출물 쪽 변화가 아니다.

내 표지를 고를 때 `release_sha` 단독은 쓸 수 없다 — V1 의 `raw_observation.release_sha` 가 그
이름을 이미 들고 있어 **남의 줄까지 센다**. 그래서 이 slice 가 처음 들여온 이름들로 골랐다.

## 4. 실측 HEAD 와 판정 SHA 사이가 움직였는가

```
git diff --name-only b5e7eef0..<판정 SHA> -- \
  $(git log --no-merges --format= --name-only 56bd33ea..b5e7eef0 \
      -- . ':(exclude)reports/evidence/m9/ops-migrations' | sort -u)
```

빈 출력이면 이 실측이 유효하고, 한 줄이라도 나오면 **미검증**이다(그때 ①~③ 을 다시 잰다).
동결 보고가 그 확인 결과를 적는다.

## 5. 적용 DB 리허설 — **실측**(팀장 허용 1회, 버릴 clone · 버릴 클러스터)

§2 의 forward-fix 를 임시 `V28__rollback_rehearsal.sql` 로 두고 **Flyway 로** 적용했다(장부 기입까지).
임시 V 와 임시 test 는 **커밋하지 않았다**.

**이 리허설은 병합 전 스키마에서 돌았다**(V25 없이, 라운드 3). 다시 돌리지 않은 근거: §2 의
forward-fix 가 손대는 객체는 `outbox`·`collection_run` 의 열·제약·트리거뿐이고 V25 가 바꾼 것은
`notification_inbox`(열 하나 · CHECK 둘 · PK 한 칸)라 **겹치는 객체가 0** 이다. 적용 순서
V25 → V26 → V27 자체는 병합 뒤 전건 `:adapters:test`(clean-migrate 전수)가 초록으로 실측했다.

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
