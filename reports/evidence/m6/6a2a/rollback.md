# M6/6A-2a — 되돌림

실측 HEAD: `035311d4` (이 slice 의 **마지막 산출물 커밋**)

base: `4dc17214`(착수 실측, `git merge-base HEAD origin/main`). 아래 목록은 손으로 쓰지 않고
`git diff --name-status <base>..HEAD` 에서 기계적으로 냈다. **라운드마다 파일이 늘면 이 절차를
다시 돌린다.**

## 이 range 에서 누가 무엇을 만졌나 (hunk 격리가 필요한가)

판별은 파일별 `git log --format=%h <base>..HEAD -- <파일>` 로 냈다(셈을 산문에 옮기지 않는다 —
그 명령이 정본이다). 결과: **산출물 파일 전부가 이 slice 의 커밋만 갖는다.** 다른 slice 의 줄이
이 range 안에 섞여 있지 않으므로 **커밋 해시 hunk 격리가 필요하지 않다** — 공유 파일
(`docker/compose.yaml`·`ci.yml`·`config/quality/*`·`libs.versions.toml`·
`tools/image-hygiene-check.sh`·`app/build.gradle.kts`)도 이 range 에서는 이 slice 만 만졌다.
**수정 라운드가 만든 새 파일 둘**(적대 부팅 test · 자격 `toString` test)도 같은 확인을 받았고
`in_scope` 안이다(`checklist.md` 신규 파일 대조표).
팀장 레인 커밋은 `milestone-6.md`(착수 문단)와 `reports/evidence/m6/6a2a/scope.md`(착수 계약)
둘이고 **둘 다 되돌리지 않는다**(아래 절).

**하네스 레인 변경 없음** — `git log --oneline <base>..HEAD -- CLAUDE.md .claude/` 가 빈 출력이다.

## 되돌리지 않는 것

- `milestone-6.md` — 팀장 레인의 착수 문단이다. 이 slice 의 커밋이 이 파일에 하나도 없어
  되돌릴 내 줄이 없다.
- `reports/evidence/m6/6a2a/**` — evidence 는 남긴다(이 저장소의 관례이고, 누출 baseline 의
  allowlist 와 짝이라 evidence 만 지우면 게이트가 어긋난다).

## 절차

### ① 수정 파일 — base 로 복원 (경로를 개별 인자로 넘긴다)

```
git restore --source=4dc17214 --staged --worktree -- \
  .github/workflows/ci.yml \
  app/build.gradle.kts \
  app/src/main/kotlin/bidvector/app/BidVectorApplication.kt \
  app/src/main/kotlin/bidvector/app/wiring/PersistenceWiring.kt \
  app/src/test/kotlin/bidvector/app/http/EvaluationDryRunControllerTest.kt \
  app/src/test/kotlin/bidvector/app/http/HttpTestSupport.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiContractTest.kt \
  app/src/test/kotlin/bidvector/app/http/OpenApiDryRunContractTest.kt \
  app/src/test/kotlin/bidvector/app/http/OperatorAuthenticationTest.kt \
  app/src/test/kotlin/bidvector/app/http/ProductionAssemblyAuthAuditTest.kt \
  app/src/test/kotlin/bidvector/app/http/RequestAuditFilterTest.kt \
  config/quality/gate-tests.properties \
  config/quality/image-hygiene-policy.properties \
  docker/compose.yaml \
  gradle/libs.versions.toml \
  tools/image-hygiene-check.sh
```

### ② 신설 파일 — 삭제 (`restore` 는 base 에 없는 경로를 받지 않는다)

```
git rm -f \
  app/src/main/kotlin/bidvector/app/ManagementSurface.kt \
  app/src/test/kotlin/bidvector/app/ManagementSurfaceLockTest.kt \
  app/src/test/kotlin/bidvector/app/management/ManagementHealthSurfaceTest.kt \
  app/src/test/kotlin/bidvector/app/management/ManagementSurfaceBootRefusalTest.kt \
  app/src/test/kotlin/bidvector/app/wiring/PersistencePropertiesTest.kt \
  config/quality/image-hygiene-policy-app.properties \
  docker/app.Dockerfile \
  docker/app.Dockerfile.dockerignore
```

빈 디렉터리 하나가 남는다(`app/src/test/kotlin/bidvector/app/management/`) — git 은 디렉터리를
추적하지 않으므로 무해하고, `rmdir` 은 선택이다. `…/app/wiring/` 은 base 에도 파일이 있어 남지 않는다.

### ③ 확인 — 내 줄이 사라졌고 남의 줄이 남았다

```
git diff 4dc17214 -- <위 ①②의 경로 전부>        # 빈 출력이어야 한다
git diff 4dc17214 -- milestone-6.md              # 팀장 문단이 그대로 남아 있어야 한다(빈 출력이 아니다)
git status --porcelain -- CLAUDE.md .claude/     # 빈 출력(하네스 경로 무편집)
```

### ④⑤⑥ 되돌린 트리가 서고 게이트가 받는가

```
./gradlew --no-daemon :app:compileKotlin :app:compileTestKotlin   # ④
./gradlew --no-daemon :app:test                                   # ⑤
./gradlew --no-daemon check                                       # ⑥
```

⑥ 은 「코드가 서는가」가 아니라 **게이트가 받아들이는가**다. `gateExecutionGate` 는 되돌린
`config/quality/gate-tests.properties`(이 slice 의 두 test 이름이 없는 판)와 되돌린 test 집합이
짝이 맞아야 통과한다 — ①과 ②를 함께 해야 성립한다(이 slice 의 test 세 이름이 없는 판과 test 세 파일이
없는 트리가 짝이다). 이 slice 의 evidence 디렉터리는 되돌리지
않으므로 `leakPatternGate` 가 그것을 보는데, 이 문서와 형제 둘은 패턴 매치가 0 이다(`commands.md`
「비밀값 스캔」 행) — base 의 baseline 에 없는 새 매치가 생기지 않는다.

## 실측 (임시 worktree)

실측 HEAD `035311d4` 에서 만든 임시 worktree 에서 ①~⑥ 을 끝까지 실행했다(앞 라운드 실측을 옮기지
않았다 — 되돌림 대상이 두 파일 늘었고 한 파일이 수정 쪽으로 들어왔다).

| 단계 | 명령 | exit | 핵심 결과 |
|---|---|---|---|
| ① | 위 `git restore` | 0 | 수정 16개 복원 |
| ② | 위 `git rm -f` | 0 | 신설 8개 삭제. 인덱스 집계 **D 8 · M 16** |
| ③ | 위 `git diff`/`git status` 셋 | 0 | **내 줄 사라짐**(되돌린 경로의 base 대비 diff 0바이트 = 되돌린 트리가 base 트리와 같다) · **남의 줄 남음**(`milestone-6.md` 의 base 대비 diff 2018바이트 = 팀장 착수 문단 보존) · 하네스 경로 무편집(빈 출력) |
| ④ | `:app:compileKotlin :app:compileTestKotlin` | 0 | 되돌린 트리가 컴파일된다 |
| ⑤ | `:app:test` | 0 | 되돌린 트리의 test 초록 |
| ⑥ | `check` | 0 | **게이트 전건 초록**(433 task 줄) — `gateExecutionGate` 가 되돌린 `gate-tests.properties` 와 되돌린 test 집합의 짝을 받아들이고, 되돌리지 않은 evidence 디렉터리도 `leakPatternGate` 를 붉히지 않는다 |

**verifier 가 대조할 술어**: `git diff --name-only 035311d4..<판정 SHA> -- <위 ①②의 경로들>` 이
빈 출력이면 이 실측이 유효하다. 이 slice 의 뒤 커밋은 `reports/evidence/m6/6a2a/**` 만 만지고
그 경로는 되돌림 대상이 아니므로(위 「되돌리지 않는 것」) 빈 출력이어야 한다.

## 되돌림 없이 비활성화

- **compose**: `app` 서비스를 띄우지 않으면 기존 두 서비스(ml-serving·postgres)는 그대로다
  (`docker compose up -d ml-serving postgres`). 이 slice 는 기존 두 서비스 정의를 바꾸지 않았다.
- **CI**: `container` job 의 새 step 넷(앱 배포물·앱 이미지·앱 위생·스모크)만 지우면 6C 축은
  그대로 돈다. 단 위생 게이트 step 둘은 정책 파일 인자를 요구하므로 함께 남긴다.
- **앱**: `java -jar app.jar` 로 띄우는 기존 경로는 **관리 포트가 하나 더 열리는 것** 말고는
  바뀌지 않는다. 관리 포트를 열지 않으려면 `MANAGEMENT_SERVER_PORT=-1` 로 끈다 — 다만 그러면
  조립이 분리 판정에서 기동을 거부한다(설계상 fail-closed). 관리 표면만 좁히고 앱은 띄우려면
  포트를 방화벽·배치 구성에서 닫는다(그 통제는 경계 밖, `OPEN-6A2A-MGMT-PORT-EXPOSURE`).
  **접두사 거부(D-6A2a-10)는 끌 수 없다** — 끄는 스위치를 만들지 않았다. 그래서 기존 배치가
  `MANAGEMENT_*`(관리 포트 말고) 또는 `SPRING_JMX_*` 환경변수를 갖고 있으면 그 자리에서 기동이
  거부되고, 문면이 **어느 키인지** 말한다(값은 싣지 않는다). 그것이 이 slice 가 만든 유일한
  기동 비호환이고, 처방은 그 변수를 지우는 것이다(잠금 값과 같은 값을 주려는 경우도 거부된다 —
  같은 값을 두 자리에 두지 않는다).
- **DB 변경 없음** — 마이그레이션 0(`commands.md` 「경계 무편집 실측」).

## 예상 복구 시간

①~③ 은 초 단위(git 작업만). ④⑤⑥ 을 포함한 확인까지가 이 개발 장비에서 약 5~10분(전건 `check`
한 번). 되돌리는 flag/route/writer 는 없다 — 이 slice 는 런타임 스위치를 만들지 않았고
비활성화는 위의 「compose 서비스를 띄우지 않는다」가 전부다.
