# M6/6B-3 — commands

base `e2232a75` · 브랜치 `m6-6b3/2026-10-13` · worktree `bid-vector-v2-m6-6b3`.
산출물 커밋 18. **판정 SHA 후보 `bc18d033`**(수정 라운드 2 의 마지막 산출물 커밋).

## acceptance (CI `check` job 명령 그대로)

판정 SHA 후보 `bc18d033` 에서 **양끝 HEAD 를 단언하고** 돌렸다(측정 중 커밋 0 — 두 명령의 끝에서
`git rev-parse` 가 같은 SHA 를 냈다).

| # | 명령 | 결과 |
|---|---|---|
| 1 | `./gradlew --no-daemon check --rerun-tasks` | **exit 0** · 10m19s · **350 task 전부 실행**(캐시·up-to-date 0) · test 합계 **2,973** / fail 0 · 게이트 task 136 수행 |
| 2 | `./gradlew --no-daemon qualityBaseline --rerun-tasks` | **exit 0** · 22s · 24 task 전부 실행 |

> **task 수에 대하여**(vr R2-F8). 이 레인의 `--rerun-tasks` 실측은 **350 · 24** 이고 verifier r2 는 같은
> 코드에서 **359 · 33** 을 쟀다. 차이는 측정 자리다 — verifier 는 **갓 만든 clone**(build 디렉터리 없음)에서
> 돌리므로 `:build-logic` 계열 task 가 더 실행된다. 숫자를 맞추지 않고 **어디서 쟀는지와 함께** 적는다.

**둘 다 `--rerun-tasks` 로 쟀다** — 앞 라운드에서 초록이 캐시·실행 순서에 의존하던 것을 한 번
실측했으므로(그때 fixture 가 TRUNCATE 밖 공유 표를 오염시킨 것이 순서가 바뀌자 드러났다) 판정의
근거를 「아무것도 캐시하지 않은 run」으로 둔다.

skipped **4** 는 선언된 제외 하나(`RealServerIntegrationTest` — `@EnabledIfSystemProperty`,
`gate.tests.adapters.excluded` 등재)에서 온다(vr L-1 — 앞 판 evidence 는 「skipped 0」으로 적었고 그것은
실측과 달랐다). **6B-3 의 test class 16 은 skipped 0** 이다.

`ml-engine` job 은 생략한다 — Python 산출물 0(`git diff --name-only e2232a75..b252fa0c -- ml-engine`
빈 출력). `container` job 도 생략한다 — `docker/**`·`ci.yml` 무편집(같은 질의 빈 출력).

## test 수

모듈 합계 **2,973**(adapters 1,009 · app 587 · workflow 447 · build-logic 279 · procurement 263 ·
decision 184 · shared-kernel 89 · strategy 84 · qualification 31) · fail 0 · skipped 4.

이 라운드가 더한 test 는 **25**(라운드 1 끝 2,948 → 2,973): 신설 class 다섯
(`FieldConceptPiiAxisTest` 7 · `OpeningCompanyInfoMaskingTest` 4 · `MaskingKeepSetSourceTest` 5 ·
`InsertedAtOwnershipTest` 4 · `V18LockTimeoutTest` 1 = 21) + 기존 class 에 더한 5(가드의 `NOINHERIT` 1 ·
CHECK 장부의 `convalidated` 1 · 분류 등식의 CASCADE 깊이 1 · use case 의 진척 0 종료 1 · 러너의 건너뜀 1)
− **지운 1**(공허한 음성 대조, cr L-1). 보존 패키지와 그 장부 전체는 class 21 · test 164 다.

## SQL 축 선행 실측 (버릴 컨테이너 · Gradle 없음)

빌드 슬롯을 다른 레인이 쥔 동안, V18·V19 변경의 **SQL 축만** 따로 쟀다 — 버릴 `postgres:16.4`
컨테이너(이름 하나, **포트 publish 없음**, 끝에 제거)에 migration **사본**으로 V1~V19 를 psql 로 적용하고
probe 했다. worktree 는 손대지 않았다(사본은 scratchpad). 이 실측이 Gradle test 를 갈음하지는 않는다 —
하는 일은 **GREEN run 전에 SQL 문법·거동을 확인해 슬롯을 두 번 쓰지 않는 것**이다.

| probe | 결과 |
|---|---|
| V1~V19 적용(패치된 V18·V19 포함) | **17 파일 전부 ok** — `SET LOCAL lock_timeout` 첫 문장도, 새 INSERT 분기도, 복합 인덱스도 적용된다 |
| `SET LOCAL` 의 범위 | 트랜잭션 안에서 `5s` · **커밋 뒤 0 으로 돌아온다**(Flyway 가 이 파일을 트랜잭션에서 돌리므로 이 마이그레이션에만 걸린다) |
| 투영 열 형태 제약 | `convalidated=true` — V19 의 `VALIDATE` 가 실제로 검증까지 끝낸다(mig M-2 의 장부가 재는 값) |
| 앱 역할이 `inserted_at='2999-01-01'` 로 적재 | **그 값이 남지 않는다**(DB 시계가 덮는다 — ③) |
| 앱 역할이 표지를 찍은 적재 | **거부**(「마스킹 표지는 적재가 찍지 않는다」) |
| 소유자가 `inserted_at='2020-01-01'` 로 적재 | **그대로 남는다** — fixture·복원이 기대는 음성 대조 |
| 제3 역할 + UPDATE GRANT 로 마스킹 | **거부**(「마스킹은 표 소유자만 한다」) |
| `NOINHERIT` 소유자 멤버로 마스킹 | `pg_has_role` 이 **USAGE=false · MEMBER=true** 인 상태에서 **거부** — 술어를 `'USAGE'` 로 좁힌 효과를 그 자리에서 실측했다(mig L-3) |
| allow-list 투영 문장(② 의 새 SQL 그대로) | 담당자 키·업체 식별 키·미지 키가 **모두 빠지고** 남길 키만 남는다 |

## RED 먼저 (이 라운드)

앞 라운드의 이탈(「test 를 먼저 썼으나 RED 를 별도 run 으로 재지 않았다」)을 반복하지 않았다. 새 술어의
test 를 **구현 전에** 돌려 실패를 받았다.

표적(새 술어 + 바뀐 장부) 한 번에: **14 test FAILED**(adapters 12 · workflow 1 · app 1).
공허·통제군 쪽은 같은 run 에서 **초록**이었다 — 그래서 이 RED 는 「술어가 아무것도 못 본다」가 아니다.

| RED 한 자리 | 왜 붉었나 |
|---|---|
| `FieldConceptPiiAxisTest` 2/7 | 마스킹 집합이 선언 집합과 **다르다**(둘 대 셋) · 지울 집합이 선언의 rawName 과 다르다. 분할·서로소·공허 대조 다섯은 초록(선언 자체는 완전했다) |
| `OpeningCompanyInfoMaskingTest` 2/4 | 출하 수집 경로로 심은 개찰 행에서 합성 키가 **두 열에 그대로 남는다**. 공허 대조(그 키가 실제로 실린다)와 기한 안쪽 음성 대조는 초록 |
| `MaskingKeepSetSourceTest` 3/5 | 투영에 심은 담당자 키·미지 키가 **두 열에 남는다**(남김 집합이 행에서 왔다). 공허 대조와 등재 키 생존은 초록 |
| `InsertedAtOwnershipTest` 2/4 | 앱 역할의 2999 적재가 **그 시각을 그대로 갖고** 마스킹 후보에서 빠진다. 공허 대조와 소유자 음성 대조는 초록 |
| `RawObservationMaskingGuardTest` 1/16 | `NOINHERIT` 소유자 멤버의 마스킹 UPDATE 가 **지난다**(술어가 `MEMBER` 였다) |
| `CleanMigrationIndexTest` 1/2 | 자식 가드 인덱스의 **정의**가 장부와 다르다(열 하나 대 셋) |
| `V18LockTimeoutTest` 1/1 | 충돌 잠금 아래에서 V18 이 **기다렸다가 성공**했다 — 「예외가 던져지지 않았다」. 잠금을 35s 쥐고 놓는 설계라 41.9s 에 **끝났다**(무한 대기로 빌드를 멎게 하지 않는다) |
| `PurgeExpiredDataTest` 1/11 | 가득 찬 일괄이 진척 0 일 때 루프가 끝나지 않아 호출 상한에 걸렸다 |
| `RetentionRunnerTest` 1/7 | 건너뜀이 있는 run 이 종료 코드 **0** 을 냈다 |

> **틀린 사유의 RED 를 한 번 받았고 그것을 고쳤다.** 잠금 창 test 를 처음에는 공유 클러스터의 **두
> 번째 데이터베이스**에 V1~V17 을 적용해 만들려 했는데, V2 가 만드는 역할이 **클러스터 범위**라
> 「role "bidvector_app" already exists」로 깨졌다 — 붉기는 했지만 재려던 성질과 무관한 실패다.
> 격리 단위를 데이터베이스에서 **클러스터**로 바꿔(자기 컨테이너) 다시 쟀고, 그 뒤의 RED 가 위 표의
> 「기다렸다가 성공」이다.

구현 뒤 같은 표적이 전부 초록이고, 각 술어가 **실제로 그 성질을 잠그는지**는 아래 변이 표가 따로 잰다
(RED 한 번은 「그때 그 코드에서 실패했다」만 말한다 — 변이는 「지금 이 코드에서 그 성질을 지운다」를 잰다).

## D-6B3-5 ③ — 「수집 경로가 적재 시각을 쓰는가」 먼저 실측했다

조치(비소유자 적재에 DB 시계 강제)를 고르기 **전에** 쟀다 — 출하 경로가 그 열을 쓰면 강제가 정상
적재를 바꾼다.

| 질의 | 결과 |
|---|---|
| `grep -c inserted_at adapters/…/persistence/Sql.kt` | **0** — 그 파일의 어느 문장도 그 열을 쓰지 않는다 |
| `raw_observation` INSERT 의 열 목록 | **여섯**(관측 키·엔드포인트·원문·투영·관측 시각·배포 식별자) — `inserted_at` 이 없고 `DEFAULT now()` 가 찍는다 |
| `grep -rn inserted_at adapters/src/main/kotlin` | 보존 레지스트리(기준 시각 열 선언)와 **읽기 쪽 `ORDER BY`**(relay·스냅숏)뿐. `raw_observation` 에 그 열을 쓰는 production 문장 **0** |

그래서 강제의 비용이 0 이다. **거부가 아니라 덮어쓰기**를 고른 이유도 이 실측에 있다: 트랜잭션 안에서
`now()` 는 트랜잭션 시작 시각이라, 허용 오차로 거부하면 **긴 트랜잭션의 정상 적재**가 걸린다(수집은
트랜잭션 경계 안에서 적재한다). 소유자에게는 강제하지 않는다 — 묵은 행을 심는 fixture·복원 절차가 그
자유에 기대고, 그 자격은 이 slice 의 비방어 경계다(음성 대조 test 가 그 경계를 잠근다).

## D-6B3-5 ① — 업체 식별 **개념 셋** 기준으로 소비자 비용을 다시 쟀다

앞 라운드의 ⓒ·ⓓ 를 **셋째 운반자(`OPENING_COMPANY_INFO`)를 포함해** 다시 돌렸다. 결론은 같다 —
저장 행에서 업체 식별을 읽는 소비자가 0 이므로 「90일 안쪽만 읽는다」를 보일 필요가 없다.

| 축 | 질의 | 결과 |
|---|---|---|
| ⓒ 소비자가 읽는 개념 | `grep -rhoE 'FieldConcept\.[A-Z_]+' adapters/src/main/kotlin --exclude-dir=retention \| sed … \| sort -u` | **35 개념** · 업체 식별 **세 개념 모두 0 회**(각각 `grep -cx`) |
| ⓓ ml-engine(백테스트·학습) | `grep -rn 'raw_observation\|payload_fields\|<업체 식별 raw 키 셋>' ml-engine/{src,tools,policy,tests}` | **0 건** |
| ⓔ 합성 문자열 키의 production 참조 전수 | 같은 질의를 Kotlin 에 | **수집 경로와 계약 선언뿐**이다 — 수집 시점 마스킹(성분 폐기)·매퍼·소스·계수 주석, 그리고 계약의 rawName 선언. **저장 행을 읽는 자리 0** |

그래서 이 라운드의 조치(키 전체 삭제)는 6G 표본의 **읽히는 축**을 건드리지 않는다. 스냅숏 추출이
개찰결과 목록 행에서 읽는 것은 진행구분·참가자수뿐이고 투찰금액·투찰율은 개찰완료 축의 다른 개념에서
온다(ⓒ 의 35 가 그 사실을 센다).

## 비밀값·누출 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 를 **변경 파일 전수 + evidence + `_workspace`** 에
손으로 돌렸다 — 나온 것은 ⓐ 설정 속성 이름 대입 ⓑ `architecture-policy.properties` 의 비밀 축
**등재 키 이름** ⓒ 로그 무유출을 재는 test 의 자리값 라벨이고 **값은 하나도 없다**.
`qualityBaseline` 의 해당 게이트는 **`reports/evidence` 디렉터리만** 본다(privacy F-4 — 앞 판은 그
게이트가 산출물 경로까지 「전건으로 포함한다」고 적었고 그것은 성립하지 않는다). 산출물 경로의
결과는 위의 손 실행이 정본이다.

fixture 자리값은 **형태를 흉내내지 않는다** — 담당자 키·업체명이 사라지는지만 재므로 전화·전자우편
꼴이 필요 없다.

## clean-tree

`git status --porcelain -- <in_scope 경로 개별 인자>` → 빈 출력(위 변이 절의 각주 참조 — 변이
바퀴마다 같은 질의를 돌렸다). **양성 대조 1회**(비파괴 절삭): 산출물 파일 하나의 끝 한 줄을 지운
상태에서 같은 질의가 그 파일을 `M` 으로 냈고, 되돌린 뒤 다시 빈 출력이었다(`git checkout --` 를
쓰지 않는다 — 미커밋 산출물을 함께 지운다).

## DB 격리 단언

이 slice 의 모든 DB 실행은 **Testcontainers** 위에서다(`PersistenceTestSupport` 의 공유 컨테이너,
`postgres:16.4`). dev DB(`bid-vector-v2-dev`, 127.0.0.1:55432)·legacy DB 로의 접속은 **0** 이다.
정지 사건을 진단할 때 들여다본 것도 그 **일회용 컨테이너**이고(`pg_stat_activity`), 진단 뒤 컨테이너를
제거했다. 러너를 실제 DB 에 돌리지 않았다 — 운영 실행은 별도 승인 사항이다.
