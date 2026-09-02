# rollback — M1 / 1A

## 되돌리는 것

**1A 는 flag·route·writer 를 만들지 않았다.** 실행되는 것은 빌드와 테스트뿐이고 DB·스키마·
외부 호출·배포 대상이 없다. 그러므로 되돌릴 런타임 상태가 없고 되돌림은 **파일 제거** 하나다.

| 무엇 | 어떻게 |
| --- | --- |
| 이 slice 의 커밋 전부 | `git revert --no-commit 6b03c75..HEAD && git commit` — 범위는 `C-0` 이 낸다 |
| 남는 빌드 산출물 | `build/`·`.gradle`·`build-logic/build/` — 전부 `.gitignore` 대상이라 revert 로는 사라지지 않는다. `git clean -fdx build .gradle build-logic/build` |
| Gradle 이 내려받은 것 | `~/.gradle/wrapper/dists/gradle-9.6.1-bin` 과 `~/.gradle/caches` 의 신규 아티팩트. **지우지 않아도 된다** — 저장소 밖 캐시이고 다른 프로젝트가 공유한다 |
| 데이터·스키마 | **없다.** Flyway migration 을 쓰지 않았고 DB 에 붙지 않았다 |

## 예상 복구 시간

**10 분 이내.** revert 자체는 1 분 미만이고, 나머지는 되돌린 뒤 저장소가 M0 상태로 빌드
없이 성립하는지 확인하는 시간이다(1A 이전 저장소에는 Gradle 빌드가 없었으므로 확인은
「Kotlin 관련 파일이 남지 않았다」로 끝난다).

## 검증 방법

```
git revert --no-commit 6b03c75..HEAD && git commit
git clean -fdx build .gradle build-logic/build
git diff --stat 6b03c75 HEAD          # 비어 있어야 한다
ls settings.gradle.kts build.gradle.kts gradlew    # 셋 다 없어야 한다
```

`docs/adr/0007` 과 `docs/discovery/capability-map.md` 의 편집도 같은 revert 로 함께 돌아간다 —
`OPEN-ADR-08` 이 다시 활성이 되고 `OPEN-ADR-14` 가 사라지며 §12 의 계수가 43 으로 돌아간다.
**되돌린 뒤 그 둘의 상태가 M0 종료 시점과 같은지가 이 slice 되돌림의 진짜 확인 지점**이고,
위 `git diff --stat` 이 비어 있으면 그것이 확인된다.

## 부분 되돌림

게이트 하나만 끄고 싶을 때 — **전체 revert 없이 가능하다.**

| 대상 | 방법 |
| --- | --- |
| detekt | `bidvector.kotlin-conventions.gradle.kts` 와 `build-logic/build.gradle.kts` 양쪽에서 `dev.detekt` 제거. **승인된 파일 500 줄 임계는 `sizeGate` 가 계속 든다**(ADR 0007 D-7 이 노린 성질). 함수 50줄 임계는 detekt 과 함께 사라진다 |
| ktlint | 같은 두 자리에서 `org.jlleitschuh.gradle.ktlint` 제거 |
| architecture test | `app/src/test/kotlin/bidvector/app/architecture/` 와 `app/src/test/kotlin/bidvector/archfixture/` 제거. **1 차 강제인 `moduleDependencyGate` 는 남는다** |
| build-logic 게이트만 | 루트 `bidvector.quality-baseline.gradle.kts` 의 `check` task 제거. 아홉 모듈의 게이트는 그대로 |
| Boot 4.x 스모크 | `app/build.gradle.kts` 의 Boot 플러그인·스모크 의존·`compatibilitySmoke` 제거. 다른 모듈은 Spring 을 모른다 |

**게이트를 끄는 것은 되돌림이 아니라 회귀다.** 위 표는 급한 우회로이며 쓰는 경우 사유와
해소 계획을 남긴다(`ADR 0007` D-4 의 allowlist 규격).
