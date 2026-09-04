# rollback — M1 / 1A

## 되돌리는 것

**1A 는 flag·route·writer 를 만들지 않았다.** 실행되는 것은 빌드와 테스트뿐이고 DB·스키마·
외부 호출·배포 대상이 없다. 그러므로 되돌릴 런타임 상태가 없고 되돌림은 **파일 제거** 하나다.

| 무엇 | 어떻게 |
| --- | --- |
| 이 slice 의 산출물(in_scope 경로) | **~~`git revert --no-commit 6b03c75..HEAD && git commit` — 범위는 `C-0` 이 낸다~~**(2026-09-04 정정, 아래 「되돌리는 절차」) — range revert 는 하네스 레인 커밋(개수는 scope.md 「하네스 레인 변경」 표와 `git log --oneline 6b03c75..HEAD -- CLAUDE.md .claude/` 가 정본)까지 되돌린다 |
| 남는 빌드 산출물 | `build/`·`.gradle`·`build-logic/build/` — 전부 `.gitignore` 대상이라 되돌림으로는 사라지지 않는다. `git clean -fdx build .gradle build-logic/build` |
| Gradle 이 내려받은 것 | `~/.gradle/wrapper/dists/gradle-9.6.1-bin` 과 `~/.gradle/caches` 의 신규 아티팩트. **지우지 않아도 된다** — 저장소 밖 캐시이고 다른 프로젝트가 공유한다 |
| 데이터·스키마 | **없다.** Flyway migration 을 쓰지 않았고 DB 에 붙지 않았다 |

## 되돌리는 절차 (2026-09-04 신설, 2026-09-04 `git restore` 로 정정 — Codex 16차 high)

**되돌림은 range revert 가 아니라 in_scope 경로 한정이다** — `evidence-pack` SKILL.md
2026-09-04 규격, Codex 15차 high. `git revert <base>..HEAD` 는 같은 range 의 하네스 레인
커밋(개수는 scope.md 「하네스 레인 변경」 표와 `git log --oneline 6b03c75..HEAD -- CLAUDE.md
.claude/` 가 정본)까지 되돌린다. 대신 `scope.md` 의 `in_scope` 경로만 base 상태로 되돌리고,
**하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서의 하네스 레인 편집은 되돌리지 않는다.**

**~~`git checkout <base> -- <경로>` 는 쓰지 않는다~~**(Codex 16차 high) — base 에 없는
신규 경로마다 `pathspec ... did not match any file(s) known to git` 로 **exit 1** 이고
아무것도 적용되지 않는다. 이 slice 는 in_scope 135개 변경 중 129개가 신규라 첫 명령부터
실패한다(임시 clone 실측, 아래). 대신 `git restore`를 쓴다 — `--source` 에 없는 경로는
**삭제**되므로 신규 파일에 별도 `git rm` 이 필요 없다:

```
git restore --source=6b03c75 --staged --worktree -- \
  settings.gradle.kts build.gradle.kts gradle.properties \
  gradle/libs.versions.toml gradle/wrapper gradlew gradlew.bat \
  build-logic \
  shared-kernel procurement qualification strategy decision settlement workflow adapters app \
  config/quality .editorconfig config/detekt .github/workflows/ci.yml .gitignore \
  v2-지침서.md \
  docs/adr/0007-test-pyramid-and-ratchet.md docs/adr/0006-gradle-modules.md \
  milestone-1.md docs/discovery/capability-map.md \
  reports/evidence/m1/1a/scope.md reports/evidence/m1/1a/commands.md \
  reports/evidence/m1/1a/checklist.md reports/evidence/m1/1a/rollback.md
git commit -m "revert(m1-1a): in_scope 경로를 base 6b03c75 로 되돌린다"
```

`--staged --worktree` 둘 다 줘야 인덱스와 워킹 트리가 함께 base 상태로 간다 — 신규 파일은
인덱스에서 지워지고(스테이지된 삭제) 워킹 트리에서도 사라지므로, 뒤이은 `git commit` 하나로
삭제와 복원이 함께 커밋된다.

**임시 clone 실측(2026-09-04, Codex 16차 high — dry-run 성립 확인은 실행 가능성을
증명하지 않는다).** `commands.md` 의 `E-116` 이 정본:

```
git clone --no-hardlinks -q . <scratchpad>/rollback-probe
cd <scratchpad>/rollback-probe
git restore --source=6b03c75 --staged --worktree -- <위와 같은 in_scope 경로 개별 인자>
echo exit=$?
git status --porcelain | awk '{print $1}' | sort | uniq -c
git diff --cached --stat 6b03c75 -- <위와 같은 in_scope 경로 개별 인자> | tail -1
```

probe clone 은 실측 뒤 지운다(`rm -rf`) — 저장소에 남기지 않는다.

## 예상 복구 시간

**10 분 이내.** 되돌림 자체는 명령 하나(수 초)이고, 나머지는 되돌린 뒤 저장소가 M0 상태로
빌드 없이 성립하는지 확인하는 시간이다(1A 이전 저장소에는 Gradle 빌드가 없었으므로 확인은
「Kotlin 관련 파일이 남지 않았다」로 끝난다).

## 검증 방법

```
git restore --source=6b03c75 --staged --worktree -- <in_scope 경로 개별 인자, 위와 동일>
git commit
git clean -fdx build .gradle build-logic/build
git diff 6b03c75 -- <in_scope 경로 개별 인자>          # 비어 있어야 한다
git status --short -- CLAUDE.md .claude/               # 출력 없어야 한다(하네스 경로는 HEAD 그대로 — 되돌리지 않는다)
ls settings.gradle.kts build.gradle.kts gradlew    # 셋 다 없어야 한다
```

확인 지점은 **in_scope 경로의 `git diff 6b03c75 -- <경로>` 가 비어 있고 하네스 경로는 HEAD
그대로**다 — range revert 의 「전체 `git diff --stat 6b03c75 HEAD` 가 비어 있어야 한다」를
대체한다(전체 diff 는 하네스 레인 편집이 살아 있는 한 더 이상 비지 않는다).

`docs/adr/0007` 과 `docs/discovery/capability-map.md` 의 편집도 같은 절차로 함께 돌아간다 —
`OPEN-ADR-08` 이 다시 활성이 되고 `OPEN-ADR-14` 가 사라지며 §12 의 계수가 43 으로 돌아간다.
**되돌린 뒤 그 둘의 상태가 M0 종료 시점과 같은지가 이 slice 되돌림의 진짜 확인 지점**이고,
위 in_scope 경로 한정 `git diff` 가 비어 있으면 그것이 확인된다.

## 부분 되돌림

게이트 하나만 끄고 싶을 때 — **전체 revert 없이 가능하다.**

| 대상 | 방법 |
| --- | --- |
| detekt | `bidvector.kotlin-conventions.gradle.kts` 와 `build-logic/build.gradle.kts` 양쪽에서 `dev.detekt` 제거. **승인된 두 임계(함수 50줄·파일 500줄)는 `sizeGate` 가 계속 든다**(ADR 0007 D-7 이 노린 성질). detekt 과 함께 사라지는 것은 **승인 문서가 수치를 정하지 않은 축**(복잡도·중첩·파라미터 수)뿐이다 |
| ktlint | 같은 두 자리에서 `org.jlleitschuh.gradle.ktlint` 제거 |
| 효과 멤버 금지 | `member-effects.properties` 의 그 줄을 `forbidden` 에서 `reviewed:<사유>` 로 바꾸면 **그 좌표만** 열린다 — 배선은 그대로 서고 사유가 리뷰 대상으로 남는다. 도출 자체를 끄려면 루트 `check` 에서 `memberEffectGate` 를 뗀다 |
| architecture test | `app/src/test/kotlin/bidvector/app/architecture/` 와 `app/src/test/kotlin/bidvector/archfixture/` 제거. **1 차 강제인 `moduleDependencyGate` 는 남는다** |
| build-logic 게이트만 | 루트 `bidvector.quality-baseline.gradle.kts` 의 `check` task 제거. 아홉 모듈의 게이트는 그대로 |
| Boot 4.x 스모크 | `app/build.gradle.kts` 의 Boot 플러그인·스모크 의존·`compatibilitySmoke` 제거. 다른 모듈은 Spring 을 모른다 |

**게이트를 끄는 것은 되돌림이 아니라 회귀다.** 위 표는 급한 우회로이며 쓰는 경우 사유와
해소 계획을 남긴다(`ADR 0007` D-4 의 allowlist 규격).
