# M9 / gates-build — 되돌리기

**복원 원천은 고정된 main SHA `0c2fd7ae`** 다 — 이 브랜치가 **실제로 병합한** 여덟째 main 커밋이고,
그 SHA 에는 이 slice 가 없다(첫 산출물 커밋 `692af252` 가 `0c2fd7ae` 의 조상이 **아님**을
`git merge-base --is-ancestor` 로 단언 — 실측 rc=1). 움직이는 `origin/main` 을 쓰지 않는다. 머지 뒤
되돌림은 PR revert 다.

착수 base `df4976cc` 는 **병합 뒤에는 경계로 쓸 수 없다**. 그 경계로 세면 병합이 들여온 남의 레인
커밋까지 센다. 이 브랜치는 main 을 **여덟 번** 병합했고(`e12c0485` → `3ad4f3f6` → `97ed49c4` →
`56bd33ea` → `3101035f` → `70406318` → `07140729` → `0c2fd7ae`) 원천은 그때마다 옮긴다.

## 실측 자리와 유효성 술어 — 파일 이름을 열거하지 않는다

**실측 HEAD: `2f8b24b0`**(여덟째 병합 뒤의 마지막 산출물 커밋 — 이름 층 허용 뿌리를 바이트코드 층과
맞춘 커밋이다. 원천이 옮겨지고 산출물 커밋이 더 생기면 앞 실측은 낡으므로 라운드마다 **다시 잰다**:
이 slice 에서 `28f1128e` → `c759ded9` → `29f695a3` → `2f8b24b0` 네 번째다). 실측 뒤에 오는 것은 evidence 커밋
뿐이고, evidence 커밋은 **언제나** 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」는 성립할 수
없다. 보아야 하는 것은 **그 사이에 되돌림 대상이 움직였는가**이고, 움직였어도 다음 전칭이 서면 실측은
그대로 유효하다.

```
D := git diff --name-only <실측 HEAD>..<판정 SHA> -- <복원 목록>
∀ p ∈ D : git cat-file -e 0c2fd7ae:p  →  실패        (p 가 원천에 없다)
```

원천에 없는 경로는 복원이 **내용과 무관하게 지운다**. 그러니 그 경로의 뒤 편집은 복원 결과를 바꾸지
못한다. D 가 비면 당연히 유효하고, **비지 않아도 위 전칭이 서면 유효하다**. 어느 파일인지를 적지
않는 것이 요점이다 — 이름을 적으면 다음 라운드에 그 이름이 낡는다(앞 판 문면이 그랬다, 리뷰 LOW-2).

## 복원 목록 — 기계 산출, 내 커밋만

```
git log --no-merges --format= --name-only HEAD ^0c2fd7ae | sort -u
```

**92 경로**(추가 25 · 수정 67). 등식 확인: 위 목록과 `git diff --name-only 0c2fd7ae..HEAD | sort -u`
의 `comm -23`·`comm -13` 이 **둘 다 빈 출력**이다(한쪽 방향만 보면 「내가 커밋하지 않아도 들어오는
경로」를 못 본다). 라운드마다 재산출한다.

**수정 라운드 6 과 여덟째 병합은 새 파일을 하나도 만들지 않았다** — 경로 수가 **90 → 92** 인 것은
병합이 들여온 위반을 고친 파일 둘(`unusedImportGate` 가 낸 쓰이지 않는 import)이고, 둘 다 「게이트가
고쳐야 하는 기존 위반」이라 **in_scope 는 바뀌지 않는다**. 앞 라운드들: 1 이 다섯을 더했고 2·3 은 만들지 않았으며 4 가
하나, 5 가 넷(`BootArchivePolicy.kt` · `NestedArchiveReader.kt` · `BootArchivePolicyTest.kt` ·
`ArchiveFixtures.kt`, 전부 `build-logic/**` 안이라 in_scope 불변)을 더했다. **새 파일 ↔ in_scope
대조는 라운드마다 다시 한다** — 그 대조를 빼먹는 것이 반복되는 사각이다.

경로의 묶음은 다섯이다 — `build-logic/**` **38**(게이트 구현·판독기·convention plugin·나눈 배선·
included build 스크립트·test 클래스) · `config/quality/**` **4**(정책 데이터) · **새 게이트가 찾은
기존 위반을 고친 42 파일**(adapters·app·procurement·workflow 의 import 줄 삭제 — 목록은
`checklist.md`. 여덟째 병합이 둘을 더했다) · `reports/evidence/m9/gates-build/**` **5** · 그 밖 **3**
(`capability-map.md` 의 그 절 · `open-inventory.md` 의 그 절 · `app/build.gradle.kts` 의 주석 한 문단).

`capability-map.md` 와 `open-inventory.md` 는 **공유 파일**이다 — 다른 레인도 덧붙인다. 복원 원천이
고정 SHA 라 그 복원이 다른 레인의 뒤 편집을 지우지 않는다(머지 전 기준). 머지 뒤라면 PR revert 가
그 자리를 진다.

## 복원 명령 — range revert 가 아니라 경로 한정

```
git log --no-merges --format= --name-only HEAD ^0c2fd7ae | sort -u \
  | xargs git restore --source=0c2fd7ae --staged --worktree --
```

추가된 파일은 원천에 없으므로 이 명령이 **지운다**(별도 `rm` 이 필요 없다).

## 리허설 실측 — 버릴 clone, ①~⑥

저장소 밖 임시 clone 에서 `2f8b24b0`(실측 HEAD)를 체크아웃하고 위 명령을 돌렸다.

| # | 항목 | 결과 |
| --- | --- | --- |
| ① | 복원 목록 ↔ diff 등식 | `comm -23`·`comm -13` 둘 다 빈 출력 · 92 경로(A 25 · M 67) |
| ② | 복원 명령 exit | 0 |
| ③ | 복원 뒤 `git status --porcelain` 계수 | **D 25 · M 67**(= 추가 25 삭제 + 수정 67 되돌림), 합 92 |
| ④ | **트리 동일성** | 복원한 트리의 `git write-tree` 가 `0c2fd7ae^{tree}`(`e91b280a…`)와 **같은 해시**다 · `git diff --cached 0c2fd7ae` 경로 0 |
| ⑤ | compile | ④ 로 갈음 — 아래 |
| ⑥ | test · 게이트 | ④ 로 갈음 — 아래 |

**⑤⑥ 의 갈음은 「HEAD 초록」이 아니라 트리 동일성이다.** 복원된 트리는 `0c2fd7ae` 의 트리와 해시가
같고(`e91b280a…`), **그 SHA 자리에서 main 의 CI 가 다섯 job 전부 초록**이다(`check`·`web`·`container`·
`ml-engine`·`mutation`). 즉 되돌린 트리는 이미 전건으로 측정된 트리이고, 같은 트리에서
compile·test·게이트를 다시 도는 것은 그 측정의 재탕이다.

**갈음의 근거는 「머지 커밋이니까 초록」이 아니라 트리 해시의 동일성과 그 해시가 측정된 자리**다 —
앞 원천(`07140729`)에서 그 둘이 갈렸다: 그 머지 커밋의 CI `check` 는 러너의 `install buf` 단계가
실패해 **아예 skipped** 였고, 초록은 같은 tree 해시를 가진 PR head 자리에만 있었다. 그래서 라운드마다
**원천 SHA 의 CI 결론을 직접 확인한다**(이번 원천은 그 자리가 초록이라 한 자리로 끝난다).

**「내 표지 0 · 남의 표지 보존」 계수** — 복원 뒤 이 slice 가 만든 파일(`BootArchivePolicy` ·
`NestedArchiveReader` · `EnvironmentAccessGateTask` · `MigrationOrderGateTask` · `PolicyGateWiring` ·
`BootArchivePolicyTest` · `ArchiveFixtures`)이 **0** 개 남고, 정책에서 이 slice 의 키 넷
(`jar.boot-start-class` · `jar.boot-archive-module` · `jar.nested-lib-roots` ·
`bytecode.env-access.allowed-callers`)도 **0** 회 나온다. 이름 축의 허용 뿌리 키
(`source.scoped-allowed-roots.*`)도 **8 → 0** 이다 — 그 축 자체가 이 slice 의 것이다.

**표지는 어휘 grep 이 아니라 항목 집합으로 센다.** 이 라운드가 그 함정을 실제로 밟았다: 여덟째 병합이
더한 허용 뿌리 하나(`bidvector.workflow.notificationinbox`)를 파일 전체 grep 으로 세면 복원 트리에서도
**23 회** 나온다 — 그 이름을 main 의 다른 정책 키들이 이미 쓰기 때문이다. 어휘가 아니라 **그 키의 항목
목록**을 보면 복원 트리에는 그 키가 아예 없다. 상류가 같은 이름을 채택하면 어휘 표지는 조용히 공허해진다.

남의 표지는 보존된다 — 일곱째 원천과 여덟째 원천 사이에 main 이 더한 파일 **31 개가 31 개 모두** 복원
트리에 남고(`git diff --name-status 07140729..0c2fd7ae` 의 A 행 전수 대조), 그 안에 이번 병합이 들여온
마이그레이션 둘(V26·V27)이 **둘 다** 있다. 완료를 종료 코드가 아니라 이 계수들로 본다.

**되돌림이 실제로 무엇을 되돌리는지**: 복원된 트리에서 app 의 아카이브 판정은 `jar` 하나만 보는 앞 판
으로 돌아가고, 이 slice 의 HEAD 에서는 배포물까지 포함한 `archives=2 entries=701 judged=602
nestedJars=102 nestedOutside=0 bootArchives=1` 이다(여덟째 병합이 class 넷을 들여와 앞 판의
`entries=693 judged=594` 에서 늘었다 — 판정되지 않는 엔트리의 수는 그대로다). 배포 경로 축(manifest
속성 여섯 · classpath index 등식)과 환경 축은 복원 뒤 그 게이트 자체가 사라지고, adapters 의 소스
참조 축은 판정 없이 통과하던 앞 판으로 돌아간다 — 그 축은 이번 병합이 들여온 포트 참조를 **실제로
받아 냈다**(`commands.md` 여덟째 병합 절).

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

위 복원 명령 하나로 1 분 이내(트리 조작만). 복원된 트리는 `0c2fd7ae` 와 같은 트리라 재빌드 없이
**그 자리에서 초록으로 측정된** CI 결과가 그대로 적용된다.
