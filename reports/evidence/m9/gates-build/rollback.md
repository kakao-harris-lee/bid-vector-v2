# M9 / gates-build — 되돌리기

**복원 원천은 고정된 main SHA `3101035f`** 다 — 이 브랜치가 **실제로 병합한** 다섯째 main 커밋이고,
그 SHA 에는 이 slice 가 없다(첫 산출물 커밋 `692af252` 가 `3101035f` 의 조상이 **아님**을
`git merge-base --is-ancestor` 로 단언 — 실측). 움직이는 `origin/main` 을 쓰지 않는다. 머지 뒤
되돌림은 PR revert 다.

착수 base `df4976cc` 는 **병합 뒤에는 경계로 쓸 수 없다**. 그 경계로 세면 병합이 들여온 남의 레인
커밋까지 세어 162 경로가 나오고 그 중 82 가 내 것이 아니다(web · usecase · 다른 slice 의 evidence).
이 브랜치는 main 을 **다섯 번** 병합했고(`e12c0485` → `3ad4f3f6` → `97ed49c4` → `56bd33ea` →
`3101035f`) 원천은 그때마다 옮긴다.

**실측 HEAD: `18c21275`**(수정 라운드 4 의 마지막 산출물 커밋 — evidence 포함). 그 뒤에 움직인 되돌림
대상은 **이 파일 하나**(`rollback.md` 를 담은 커밋)다 — 유효성 확인은
`git diff --name-only 18c21275..<판정 SHA> -- <아래 목록>` 이 그 한 경로만 내는 것으로 본다
(evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」는 성립할 수 없다).

## 복원 목록 — 기계 산출, 내 커밋만

```
git log --no-merges --format= --name-only HEAD ^3101035f | sort -u
```

**86 경로**(추가 21 · 수정 65). 등식 확인: 위 목록과 `git diff --name-only 3101035f..HEAD | sort -u` 의
`comm -23`·`comm -13` 이 **둘 다 빈 출력**이다(한쪽 방향만 보면 「내가 커밋하지 않아도 들어오는 경로」를
못 본다). 라운드마다 재산출한다.

**수정 라운드 4 는 새 파일을 **하나** 만들었다** — `build-logic/src/test/kotlin/bidvector/buildlogic/
NestedArchivePolicyTest.kt`(전건 `check` 의 `buildLogicSizeGate` 가 붉어 중첩 축 test 를 자기 클래스로
나눈 것). 그래서 경로 수가 **85 → 86** 이다. 그 경로는 `build-logic/**` 안이라 **in_scope 는 바뀌지
않는다**(scope.md 에 대조를 적었다). 앞 라운드들: 1 이 다섯을 더했고 2·3 은 새 파일을 만들지 않았다.
**새 파일 ↔ in_scope 대조는 라운드마다 다시 한다** — 그 대조를 빼먹는 것이 반복되는 사각이다.

경계도 라운드마다 옮겼다(지금은 `56bd33ea` → `3101035f`). main 이 바꾼 `gate-tests.properties` 가 내
목록에도 있어 복원이 그 파일을 main 의 새 판으로 되돌린다(내 등재 행 다섯이 빠지고 main 의 `Mutation*`
네 행이 남는다 — 의도된 방향).

경로의 묶음은 다섯이다 — `build-logic/**` **34**(게이트 구현·convention plugin·나눈 배선·included
build 스크립트·나눈 test 클래스) · `config/quality/**` **4**(정책 데이터) · **새 게이트가 찾은 기존
위반을 고친 40 파일**(adapters·app·procurement·workflow 의 import 줄 삭제 — 목록은 `checklist.md`) ·
`reports/evidence/m9/gates-build/**` **5** · 그 밖 **3**(`capability-map.md` 의 그 절 ·
`open-inventory.md` 의 그 절 · `app/build.gradle.kts` 의 주석 한 문단).

`capability-map.md` 와 `open-inventory.md` 는 **공유 파일**이다 — 다른 레인도 덧붙인다. 복원 원천이
고정 SHA 라 그 복원이 다른 레인의 뒤 편집을 지우지 않는다(머지 전 기준). 머지 뒤라면 PR revert 가
그 자리를 진다.

## 복원 명령 — range revert 가 아니라 경로 한정

```
git log --no-merges --format= --name-only HEAD ^3101035f | sort -u \
  | xargs git restore --source=3101035f --staged --worktree --
```

추가된 파일은 원천에 없으므로 이 명령이 **지운다**(별도 `rm` 이 필요 없다).

## 리허설 실측 — 버릴 clone, ①~⑥

저장소 밖 임시 clone 에서 `18c21275`(실측 HEAD)를 체크아웃하고 위 명령을 돌렸다.

| # | 항목 | 결과 |
| --- | --- | --- |
| ① | 복원 목록 ↔ diff 등식 | `comm -23`·`comm -13` 둘 다 빈 출력 · 86 경로(A 21 · M 65) |
| ② | 복원 명령 exit | 0 |
| ③ | 복원 뒤 `git status --porcelain` 계수 | **D 21 · M 65**(= 추가 21 삭제 + 수정 65 되돌림), 합 86 |
| ④ | **트리 동일성** | 복원한 트리의 `git write-tree` 가 `3101035f^{tree}`(`c0913842…`) 와 **같은 해시**다(경로 목록이 비는 것보다 강한 형태) · `git diff --name-only 3101035f -- .` 도 빈 출력 |
| ⑤ | compile | ④ 로 갈음 — 아래 |
| ⑥ | test · 게이트 | ④ 로 갈음 — 아래 |

**⑤⑥ 의 갈음은 「HEAD 초록」이 아니라 트리 동일성이다.** 복원된 트리는 `3101035f` 의 트리와 해시가
같다(`c0913842…`). `3101035f` 는 **main 의 머지 커밋**이고 main 의 CI 가 그 자리에서 돈다. 즉 되돌린
트리는 이미 전건으로 측정된 트리이고, 같은 트리에서 compile·test·게이트를 다시 도는 것은 그 측정의
재탕이다.

**「내 표지 0 · 남의 표지 보존」 계수** — 복원 뒤 이 slice 가 만든 파일(`EnvironmentAccessGateTask` ·
`MigrationOrderGateTask` · `PolicyGateWiring` · `NestedArchivePolicyTest`)이 **0** 개 남고, 정책에서
이 slice 의 키 셋(`jar.nested-lib-roots` · `jar.boot-launcher-main-class` ·
`bytecode.env-access.allowed-callers`)도 **0** 회 나온다. main 이 들여온 남의 표지는 보존된다 — 다섯째
병합이 들여온 여섯을 각각 확인했다(`MutationReportGateTask.kt` · `mutation-policy.properties` ·
`TelegramNotificationSender.kt` · `NotificationRelayRun.kt` · `m9/adr07-pitest/scope.md` ·
`m9/str12/scope.md`). 완료를 종료 코드가 아니라 이 두 계수로 본다.

**되돌림이 실제로 무엇을 되돌리는지**: 복원된 트리에서 app 의 아카이브 판정은 `jar` 하나만 보는 앞 판
(`judged=279`)으로 돌아가고, 이 slice 의 HEAD 에서는 배포물까지 포함한 `archives=2 entries=677
judged=578 nestedJars=102 nestedOutside=0` 이다. 환경 축도 복원 뒤 그 게이트 자체가 사라지고, adapters
의 소스 참조 축은 판정 없이 통과하던 앞 판으로 돌아간다.

## 축별 부분 비활성화 (전면 되돌림 없이)

정책 데이터 한 줄로 끈다 — **① 과 ③ 중첩 축은 예외다**(수정 라운드 4 정정, 리뷰 M-3: 앞 판 문면 둘이
**실패하는 방향**을 적고 있었다).

- ① **한 키로는 꺼지지 않는다.** `migration.module` 만 없는 모듈로 바꾸면 소유 단언이 「그 모듈에
  마이그레이션 22개가 있다」로 깨지고(수정 라운드 3, verifier L-1), `migration.classpath-location` 만
  빈 위치로 돌리면 **소유 모듈의 비-공허 단언**이 깨진다(앞 판 문면이 이쪽이었다 — 같은 이유로 실패하는
  절차를 적고 있었다). 끄려면 **둘을 함께** 돌린다: `migration.module` 을 없는 모듈로 **그리고**
  `migration.classpath-location` 을 빈 위치로.
- ② `normalize.*=false`(오늘 거동은 어느 쪽이든 같다).
- ③ 아카이브 판정은 `jar.class-roots`·`jar.external-class-roots` 를 비움(비우면 배포물 판정이 붉는다 —
  그것이 의도다). **중첩 축과 배포 경로 축은 정책만으로 꺼지지 않는다**: `jar.external-nested-jars` 를
  넓히면 ⓐ 카탈로그 등식 test 가 「목록은 정확히 한 항목」으로 붉고 ⓑ 더한 이름은 대상이 없어 **죽은
  면제**로 붉는다. `jar.nested-lib-roots` 를 비우는 길도 닫혔다(빈 값 거부). 수정 라운드 5 의 새 키
  다섯은 `BootArchivePolicyTest` 가 정책 파일에 대해 값으로 고정하고, 적재 자리 둘은 **다른 키에서
  오므로**(`requireSingle`) 그 자리만 따로 넓힐 수 없다. 끄려면 `NestedArchivePolicyTest` 와
  `BootArchivePolicyTest` 의 단언과 정책을 **함께** 고친다.
- ④ 끌 수 없다(등식의 한 종류다).
- ⑤ **`source.import.syntax-fqns`** 로 면제 확대 — 앞 판 문면의 `source.import.syntax-names` 는 **이제
  없는 키**라 그 절차가 `requireValue` 에서 죽는다(수정 라운드 3, 리뷰 M-5).
- ⑥ `source.scoped-packages` 를 비움.
- ⑧ **두 축을 따로** — 소스 1차 그물은 `source.env-sniff.member-pairs`, **임계 0 의 정본은
  `bytecode.env-access.denied-members`**. 정본을 비우면 그 축은 공허해지고 거부 집합 크기만 0 이 된다
  (끄는 쪽 선택이다). 다만 그 축소는 이제 `EnvironmentAccessTest` 가 **붉게 받는다**(리뷰 M-3) — 끄려면
  그 test 의 좌표 목록도 함께 줄여야 한다. 「조용히 작아지지 않는다」가 그 test 의 목적이다.
  **허용 목록(`bytecode.env-access.allowed-callers`)도 같다**(수정 라운드 4): 넓히면 그 목록을 값으로
  고정한 test 가 붉고, 대상 없는 항목을 더하면 게이트가 붉는다. 허용을 늘리는 것은 정책 한 줄이 아니라
  **정책 + test + 대상의 존재** 셋이 맞아야 하는 일이다.

## 예상 복구 시간

위 복원 명령 하나로 1 분 이내(트리 조작만). 복원된 트리는 `3101035f` 과 같은 트리라 재빌드 없이
그 SHA 의 CI 결과가 그대로 적용된다.
