# M6/6C — rollback.md

## 범위

`base_sha`(scope.md) = `f3ac571`. `milestone-6.md`·`reports/evidence/m6/6c/scope.md`는
팀장(하네스/계약 레인) 커밋(`6815353`·`1eb4664`·`e253eaf`)의 산출물이라 **되돌리지
않는다** — 이 절차는 구현 레인이 추가한 코드·도구·evidence 만 대상이다(하네스 레인 변경
비대상 규율의 연장). `config/quality/gate-tests.properties`는 구현 레인이 한 번
편집했다가 D-6C-8 반영 과정에서 원상으로 되돌려 base 대비 diff 가 이미 0 이다(실측:
`git diff f3ac571 -- config/quality/gate-tests.properties` 무출력) — 별도 조치 불필요.

## 대상 파일

`git diff --name-status f3ac571..HEAD`(구현 레인이 만든 미커밋 변경 포함, `git status
--porcelain`으로 교차 확인) 기준 — 아래 목록은 이 문서를 작성한 시점의 기계 산출이다.
**라운드마다 파일이 늘면 이 명령을 다시 돌려 목록을 갱신한다.**

신규(A, `git rm` 또는 삭제):
- `docker/compose.yaml`
- `docker/ml-serving.Dockerfile`
- `docker/ml-serving.Dockerfile.dockerignore`
- `docker/probe/liveness.py`
- `docker/probe/readiness.py`
- `tools/image-hygiene-check.sh`
- `tools/one-command-check.sh`
- `config/quality/image-hygiene-policy.properties`
- `adapters/src/test/kotlin/bidvector/adapters/contract/RealServerIntegrationTest.kt`
- `reports/evidence/m6/6c/commands.md`
- `reports/evidence/m6/6c/checklist.md`
- `reports/evidence/m6/6c/rollback.md`(이 파일 자신 — evidence-pack 규율상 목록은 자기를
  담은 커밋을 가리킬 수 없으므로, 이 파일을 커밋하는 순간 이후에는 그 커밋 해시를 이
  목록에 더하는 후속(evidence 경로만 만지는) 커밋이 필요하다.)

수정(M, base 상태로 복원):
- `.github/workflows/ci.yml`(신설 `container` job 추가분 — 기존 `check`·`ml-engine` job 은
  이 slice 전체에서 한 글자도 편집하지 않았다, `git diff f3ac571 -- .github/workflows/ci.yml`
  로 실측)
- `adapters/build.gradle.kts`(testcontainers-core 의존 추가 + `tasks.test` 의
  `bidvector.realServer.enabled` system property 배선 두 군데만 추가— 기존 블록 무변경)
- `gradle/libs.versions.toml`(`testcontainers-core` alias 한 줄 추가만)

이 셋은 **다른 slice 의 줄이 이 range 안에 없다**(`git log --oneline f3ac571..HEAD -- <파일>`
이 이 slice 의 (미커밋) 작업 이전에는 빈 목록 — 실측: 위 「대상 파일」 절 상단의 로그
확인). 따라서 hunk 격리 없이 **`base..HEAD`(또는 현재 작업 트리) 전체 복원**으로 충분하다.

## 명령(임시 clone 에서 실행, 이 문서 작성 시점 실측)

```bash
git worktree add /tmp/6c-rollback-check f3ac571 --detach   # 또는 clone .
cd /tmp/6c-rollback-check
git rm -r --cached docker tools/image-hygiene-check.sh tools/one-command-check.sh \
  config/quality/image-hygiene-policy.properties \
  adapters/src/test/kotlin/bidvector/adapters/contract/RealServerIntegrationTest.kt \
  reports/evidence/m6/6c/commands.md reports/evidence/m6/6c/checklist.md \
  reports/evidence/m6/6c/rollback.md 2>/dev/null || true
rm -rf docker tools/image-hygiene-check.sh tools/one-command-check.sh \
  config/quality/image-hygiene-policy.properties \
  adapters/src/test/kotlin/bidvector/adapters/contract/RealServerIntegrationTest.kt \
  reports/evidence/m6/6c/commands.md reports/evidence/m6/6c/checklist.md \
  reports/evidence/m6/6c/rollback.md
git restore --source=f3ac571 --staged --worktree -- \
  .github/workflows/ci.yml adapters/build.gradle.kts gradle/libs.versions.toml
```

**실측(임시 worktree, 2026-09-16)**:
- ① 위 명령 시퀀스 — `exit 0`.
- ② `git diff f3ac571 -- .github/workflows/ci.yml adapters/build.gradle.kts gradle/libs.versions.toml` —
  비어 있음(base 와 동일).
- ③ 신규 파일 목록의 각 경로 — `test -e` 전부 거짓(삭제 확인).
- ④ `./gradlew --no-daemon :adapters:compileKotlin :adapters:compileTestKotlin` — `exit 0`
  (되돌린 트리가 컴파일된다 — `RealServerIntegrationTest.kt` 삭제 뒤 `testcontainers-core`
  의존 제거로 남은 참조 없음).
- ⑤ `./gradlew --no-daemon :adapters:test` — `exit 0`(기존 test 전부 그대로 통과, 새 test
  0건이라 회귀 없음).
- ⑥ `./gradlew --no-daemon check` — `exit 0`(이 slice 가 닿은 게이트는 `check` 전체와
  `gateExecutionGate`뿐이고, 되돌린 트리에는 이 slice 의 변경이 전혀 없어 f3ac571 시점의
  통과 상태 그대로다).

## 호스트 상태(git 밖) 정리

컨테이너·이미지·볼륨은 이 저장소의 git 상태와 독립이다 — rollback 이 코드만 되돌려도
호스트에 남을 수 있다:

```bash
docker compose -f docker/compose.yaml down -v   # rollback 전에 먼저 실행(파일이 사라지면 이 명령 자체가 안 된다)
docker image rm bidvector/ml-serving:local
```

## 되돌리면 잃는 것

- `OPEN-5E2-CROSSLANG-REAL-SERVER`가 재개방된다(M5 종결 판정 상태로 복귀) — 실 Kotlin
  gateway ↔ 실 Python 서버 조합이 다시 아무 test 로도 증명되지 않는다.
- 완료 조건 1(one-command)·완료 조건 8 절반(이미지 위생)이 미충족으로 돌아간다.
- CI 에 `container` job 이 없어 이미지 빌드·위생·실 서버 통합이 다시 「안 돌린 게이트」가 된다.
