# M9 / gates-build — 되돌리기

**복원 원천은 고정된 main SHA `07140729`** 다 — 이 브랜치가 **실제로 병합한** 일곱째 main 커밋이고,
그 SHA 에는 이 slice 가 없다(첫 산출물 커밋 `692af252` 가 `07140729` 의 조상이 **아님**을
`git merge-base --is-ancestor` 로 단언 — 실측 rc=1). 움직이는 `origin/main` 을 쓰지 않는다. 머지 뒤
되돌림은 PR revert 다.

착수 base `df4976cc` 는 **병합 뒤에는 경계로 쓸 수 없다**. 그 경계로 세면 병합이 들여온 남의 레인
커밋까지 센다. 이 브랜치는 main 을 **일곱 번** 병합했고(`e12c0485` → `3ad4f3f6` → `97ed49c4` →
`56bd33ea` → `3101035f` → `70406318` → `07140729`) 원천은 그때마다 옮긴다.

## 실측 자리와 유효성 술어 — 파일 이름을 열거하지 않는다

**실측 HEAD: `29f695a3`**(수정 라운드 6 의 마지막 산출물 커밋 — 전건 `check` 가 낸 ktlint 를 고친
커밋이다. 앞선 실측은 `c759ded9` 에서 했고, 그 뒤 산출물 커밋이 하나 더 생겨 **다시 쟀다** — 실측
뒤에 복원 목록 안의 산출물을 또 커밋하면 그 실측은 낡는다). 실측 뒤에 오는 것은 evidence 커밋
뿐이고, evidence 커밋은 **언제나** 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」는 성립할 수
없다. 보아야 하는 것은 **그 사이에 되돌림 대상이 움직였는가**이고, 움직였어도 다음 전칭이 서면 실측은
그대로 유효하다.

```
D := git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 목록>
∀ p ∈ D : git cat-file -e 07140729:p  →  실패        (p 가 원천에 없다)
```

원천에 없는 경로는 복원이 **내용과 무관하게 지운다**. 그러니 그 경로의 뒤 편집은 복원 결과를 바꾸지
못한다. D 가 비면 당연히 유효하고, **비지 않아도 위 전칭이 서면 유효하다**. 어느 파일인지를 적지
않는 것이 요점이다 — 이름을 적으면 다음 라운드에 그 이름이 낡는다(앞 판 문면이 그랬다, 리뷰 LOW-2).

## 복원 목록 — 기계 산출, 내 커밋만

```
git log --no-merges --format= --name-only HEAD ^07140729 | sort -u
```

**90 경로**(추가 25 · 수정 65). 등식 확인: 위 목록과 `git diff --name-only 07140729..HEAD | sort -u`
의 `comm -23`·`comm -13` 이 **둘 다 빈 출력**이다(한쪽 방향만 보면 「내가 커밋하지 않아도 들어오는
경로」를 못 본다). 라운드마다 재산출한다.

**수정 라운드 6 은 새 파일을 하나도 만들지 않았다** — 경로 수는 **90 그대로**다(A 25 · M 65, 원천을
일곱째 병합으로 옮기고 다시 센 수). 앞 라운드들: 1 이 다섯을 더했고 2·3 은 만들지 않았으며 4 가
하나, 5 가 넷(`BootArchivePolicy.kt` · `NestedArchiveReader.kt` · `BootArchivePolicyTest.kt` ·
`ArchiveFixtures.kt`, 전부 `build-logic/**` 안이라 in_scope 불변)을 더했다. **새 파일 ↔ in_scope
대조는 라운드마다 다시 한다** — 그 대조를 빼먹는 것이 반복되는 사각이다.

경로의 묶음은 다섯이다 — `build-logic/**` **38**(게이트 구현·판독기·convention plugin·나눈 배선·
included build 스크립트·test 클래스) · `config/quality/**` **4**(정책 데이터) · **새 게이트가 찾은
기존 위반을 고친 40 파일**(adapters·app·procurement·workflow 의 import 줄 삭제 — 목록은
`checklist.md`) · `reports/evidence/m9/gates-build/**` **5** · 그 밖 **3**(`capability-map.md` 의 그
절 · `open-inventory.md` 의 그 절 · `app/build.gradle.kts` 의 주석 한 문단).

`capability-map.md` 와 `open-inventory.md` 는 **공유 파일**이다 — 다른 레인도 덧붙인다. 복원 원천이
고정 SHA 라 그 복원이 다른 레인의 뒤 편집을 지우지 않는다(머지 전 기준). 머지 뒤라면 PR revert 가
그 자리를 진다.

## 복원 명령 — range revert 가 아니라 경로 한정

```
git log --no-merges --format= --name-only HEAD ^07140729 | sort -u \
  | xargs git restore --source=07140729 --staged --worktree --
```

추가된 파일은 원천에 없으므로 이 명령이 **지운다**(별도 `rm` 이 필요 없다).

## 리허설 실측 — 버릴 clone, ①~⑥

저장소 밖 임시 clone 에서 `29f695a3`(실측 HEAD)를 체크아웃하고 위 명령을 돌렸다. 아래 수치는 그
자리의 것이고, 앞선 `c759ded9` 자리에서 잰 수치와 **모두 같다**(두 자리의 목록이 같은 90 이다).

| # | 항목 | 결과 |
| --- | --- | --- |
| ① | 복원 목록 ↔ diff 등식 | `comm -23`·`comm -13` 둘 다 빈 출력 · 90 경로(A 25 · M 65) |
| ② | 복원 명령 exit | 0 |
| ③ | 복원 뒤 `git status --porcelain` 계수 | **D 25 · M 65**(= 추가 25 삭제 + 수정 65 되돌림), 합 90 |
| ④ | **트리 동일성** | 복원한 트리의 `git write-tree` 가 `07140729^{tree}`(`f0eef6df…`)와 **같은 해시**다 · `git diff --cached 07140729` 경로 0 |
| ⑤ | compile | ④ 로 갈음 — 아래 |
| ⑥ | test · 게이트 | ④ 로 갈음 — 아래 |

**⑤⑥ 의 갈음은 「HEAD 초록」이 아니라 트리 동일성이다.** 복원된 트리는 `07140729` 의 트리와 해시가
같다. 다만 **그 머지 커밋 자리의 CI 는 초록이 아니다** — 러너의 `install buf` 단계가 실패해
`./gradlew --no-daemon check` 가 **아예 skipped** 였다(이 라운드의 실측. 「상시 붉은 게이트도, 안
돌린 게이트도 아무것도 막지 못한다」가 그대로 보이는 자리다). 갈음이 가리키는 초록은 **같은 트리의
다른 자리**다: 이 머지가 들여온 PR 의 head `555bf6c5` 의 tree 가 `f0eef6df…` 로 **글자 그대로 같은
해시**이고, 그 자리에서 CI `check` job 이 `./gradlew --no-daemon check` 를 **success** 로 끝냈다. 즉
되돌린 트리는 이미 전건으로 측정된 트리이고, 같은 트리에서 compile·test·게이트를 다시 도는 것은 그
측정의 재탕이다. **갈음의 근거는 머지 커밋의 초록이 아니라 트리 해시의 동일성과 그 해시가 측정된
자리**다 — 자리를 적어 두지 않으면 다음 라운드에 이 문장이 조용히 거짓이 된다.

**「내 표지 0 · 남의 표지 보존」 계수** — 복원 뒤 이 slice 가 만든 파일(`BootArchivePolicy` ·
`NestedArchiveReader` · `EnvironmentAccessGateTask` · `MigrationOrderGateTask` · `PolicyGateWiring` ·
`BootArchivePolicyTest` · `ArchiveFixtures`)이 **0** 개 남고, 정책에서 이 slice 의 키 넷
(`jar.boot-start-class` · `jar.boot-archive-module` · `jar.nested-lib-roots` ·
`bytecode.env-access.allowed-callers`)도 **0** 회 나온다. 남의 표지는 **항목 집합으로** 센다(어휘
grep 이 아니다 — 상류가 같은 이름을 채택하면 어휘는 조용히 공허해진다): 일곱째 병합이 들여온 추가
파일 **16 개가 16 개 모두** 복원 트리에 남는다(`git diff --name-status 70406318..07140729` 의 A 행
전수 대조). 완료를 종료 코드가 아니라 이 두 계수로 본다.

**되돌림이 실제로 무엇을 되돌리는지**: 복원된 트리에서 app 의 아카이브 판정은 `jar` 하나만 보는 앞 판
으로 돌아가고, 이 slice 의 HEAD 에서는 배포물까지 포함한 `archives=2 entries=693 judged=594
nestedJars=102 nestedOutside=0 bootArchives=1` 이다. 배포 경로 축(manifest 속성 여섯 · classpath
index 등식)과 환경 축은 복원 뒤 그 게이트 자체가 사라지고, adapters 의 소스 참조 축은 판정 없이
통과하던 앞 판으로 돌아간다.

## 축별 부분 비활성화 (전면 되돌림 없이)

정책 데이터 한 줄로 끈다 — **① 과 ③ 은 예외다**(수정 라운드 4 정정, 리뷰 M-3: 앞 판 문면 둘이
**실패하는 방향**을 적고 있었다).

- ① **한 키로는 꺼지지 않는다.** `migration.module` 만 없는 모듈로 바꾸면 소유 단언이 「그 모듈에
  마이그레이션 22개가 있다」로 깨지고, `migration.classpath-location` 만 빈 위치로 돌리면 **소유
  모듈의 비-공허 단언**이 깨진다(두 키가 서로의 실패를 막지 못한다). 끄려면 `migration.module` 을
  없는 모듈로 **그리고** `migration.classpath-location` 을 빈 위치로.
- ② `normalize.*=false`(오늘 거동은 어느 쪽이든 같다).
- ③ 아카이브 판정은 `jar.class-roots`·`jar.external-class-roots` 를 비움(비우면 배포물 판정이 붉는다 —
  그것이 의도다). **중첩 축과 배포 경로 축은 정책만으로 꺼지지 않는다**: `jar.external-nested-jars` 를
  넓히면 ⓐ 카탈로그 등식 test 가 「목록은 정확히 한 항목」으로 붉고 ⓑ 더한 이름은 대상이 없어 **죽은
  면제**로 붉는다. `jar.nested-lib-roots` 를 비우는 길도 닫혔다(빈 값 거부). 수정 라운드 5 의 새 키
  다섯은 `BootArchivePolicyTest` 가 정책 파일에 대해 값으로 고정하고, 적재 자리 둘은 **다른 키에서
  오므로**(`requireSingle` 이 목록 둘·빈 목록을 거부한다) 그 자리만 따로 넓힐 수 없다. 그리고 값을
  바꾸면 **실제 배포물의 manifest 와 어긋나 게이트가 붉는다**(실측 — 수정 라운드 5 변이 넷째). 끄려면
  `NestedArchivePolicyTest`·`BootArchivePolicyTest` 의 단언과 정책을 **함께** 고친다.
- ④ 끌 수 없다(등식의 한 종류다).
- ⑤ **`source.import.syntax-fqns`** 로 면제 확대 — 앞 판 문면의 `source.import.syntax-names` 는 **이제
  없는 키**라 그 절차가 `requireValue` 에서 죽는다(수정 라운드 3, 리뷰 M-5).
- ⑥ `source.scoped-packages` 를 비움.
- ⑧ **두 축을 따로** — 소스 1차 그물은 `source.env-sniff.member-pairs`, **임계 0 의 정본은
  `bytecode.env-access.denied-members`**. 정본을 비우면 그 축은 공허해지고 거부 집합 크기만 0 이 된다
  (끄는 쪽 선택이다). 다만 그 축소는 `EnvironmentAccessTest` 가 **붉게 받는다**(리뷰 M-3) — 끄려면
  그 test 의 좌표 목록도 함께 줄여야 한다. **허용 목록(`bytecode.env-access.allowed-callers`)도
  같다**: 넓히면 그 목록을 값으로 고정한 test 가 붉고, 대상 없는 항목을 더하면 게이트가 붉으며,
  **같은 클래스에서 한 번 더 부르는 것도 붉는다**(수정 라운드 5 — 허용 하나는 호출 하나까지다).
  허용을 늘리는 것은 정책 한 줄이 아니라 **정책 + test + 대상의 존재와 수** 넷이 맞아야 하는 일이다.

## 예상 복구 시간

위 복원 명령 하나로 1 분 이내(트리 조작만). 복원된 트리는 `07140729` 와 같은 트리라 재빌드 없이
**그 트리가 초록으로 측정된 자리**(`555bf6c5`, 같은 tree 해시)의 CI 결과가 그대로 적용된다.
