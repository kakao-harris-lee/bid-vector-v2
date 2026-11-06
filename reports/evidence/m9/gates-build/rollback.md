# M9 / gates-build — 되돌리기

**복원 원천은 고정된 main SHA `3ad4f3f6`** 다 — 이 브랜치가 **실제로 병합한** main 커밋이고, 그 SHA 에는
이 slice 가 없다(첫 산출물 커밋이 `3ad4f3f6` 의 조상이 아님을 `git merge-base --is-ancestor` 로 단언).
움직이는 `origin/main` 을 쓰지 않는다. 머지 뒤 되돌림은 PR revert 다.

착수 base `df4976cc` 는 **병합 뒤에는 경계로 쓸 수 없다**. 그 경계로 세면 병합이 들여온 남의 레인
커밋까지 세어 162 경로가 나오고 그 중 82 가 내 것이 아니다(web · usecase · 다른 slice 의 evidence).

**실측 HEAD: `58a0202c`.** 그 뒤에 움직인 되돌림 대상은 **이 파일 하나**(`rollback.md` 를 담은 커밋)다 —
유효성 확인은 `git diff --name-only 58a0202c..<판정 SHA> -- <아래 목록>` 이 그 한 경로만 내는 것으로
본다(evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」는 성립할 수 없다).

## 복원 목록 — 기계 산출, 내 커밋만

```
git log --no-merges --format= --name-only HEAD ^3ad4f3f6 | sort -u
```

**80 경로**(추가 16 · 수정 64). 등식 확인: 위 목록과 `git diff --name-only 3ad4f3f6..HEAD | sort -u` 의
`comm -23`·`comm -13` 이 **둘 다 빈 출력**이다(한쪽 방향만 보면 「내가 커밋하지 않아도 들어오는 경로」를
못 본다). 라운드마다 재산출한다 — 이 라운드가 더한 것은 `docs/discovery/capability-map.md` 하나다.

경로의 묶음은 다섯이다 — `build-logic/**`(게이트 구현·convention plugin·included build 스크립트) ·
`config/quality/**`(정책 데이터 넷) · **새 게이트가 찾은 기존 위반을 고친 40 파일**(adapters·app·
procurement·workflow 의 import 줄 삭제 — 목록은 `checklist.md`) · 문서 둘(`capability-map.md` 의 그 절 ·
`open-inventory.md` 의 그 절) · `reports/evidence/m9/gates-build/**`.

`capability-map.md` 와 `open-inventory.md` 는 **공유 파일**이다 — 다른 레인도 덧붙인다. 복원 원천이
고정 SHA 라 그 복원이 다른 레인의 뒤 편집을 지우지 않는다(머지 전 기준). 머지 뒤라면 PR revert 가
그 자리를 진다.

## 복원 명령 — range revert 가 아니라 경로 한정

```
git log --no-merges --format= --name-only HEAD ^3ad4f3f6 | sort -u \
  | xargs git restore --source=3ad4f3f6 --staged --worktree --
```

추가된 파일은 원천에 없으므로 이 명령이 **지운다**(별도 `rm` 이 필요 없다).

## 리허설 실측 — 버릴 clone, ①~⑥

저장소 밖 임시 clone 에서 `58a0202c` 를 체크아웃하고 위 명령을 돌렸다.

| # | 항목 | 결과 |
| --- | --- | --- |
| ① | 복원 목록 ↔ diff 등식 | `comm -23`·`comm -13` 둘 다 빈 출력 · 80 경로(A 16 · M 64) |
| ② | 복원 명령 exit | 0 |
| ③ | 복원 뒤 `git status --porcelain` 계수 | **D 16 · M 64**(= 추가 16 삭제 + 수정 64 되돌림), 합 80 |
| ④ | **트리 동일성** | 복원한 트리의 `git write-tree` 가 `3ad4f3f6^{tree}` 와 **같은 해시**다(경로 목록이 비는 것보다 강한 형태) · `git diff --name-only 3ad4f3f6 -- .` 도 빈 출력 |
| ⑤ | compile | ④ 로 갈음 — 아래 |
| ⑥ | test · 게이트 | ④ 로 갈음 — 아래 |

**⑤⑥ 의 갈음은 「HEAD 초록」이 아니라 트리 동일성이다.** 복원된 트리는 `3ad4f3f6` 의 트리와 해시가
같고, `3ad4f3f6` 의 트리는 PR #135 의 head `4427ce05` 와 **경로 차 0**이며 그 head 에서 CI 네 job
(`check`·`container`·`ml-engine`·`web`)이 전부 pass 다. 즉 되돌린 트리는 이미 전건으로 측정된 트리이고,
같은 트리에서 compile·test·게이트를 다시 도는 것은 그 측정의 재탕이다.

**「내 표지 0 · 남의 표지 보존」 계수** — 복원 뒤 이 slice 가 추가한 게이트 task 파일이 **0** 개 남고,
main 이 들여온 남의 표지는 보존된다(web 개찰 축 하네스 **2** · `opening-harness` evidence **5**).
완료를 종료 코드가 아니라 이 두 계수로 본다.

**되돌림이 실제로 무엇을 되돌리는지**: 복원된 트리에서 app 의 아카이브 판정은 `jar` 하나만 보는 앞 판
(`judged=279`)으로 돌아가고, 이 slice 의 HEAD 에서는 배포물까지 포함한 `archives=2 entries=657
judged=558` 이다. adapters 의 소스 참조 축도 복원 뒤 판정 없이 통과하던 앞 판으로 돌아간다.

## 축별 부분 비활성화 (전면 되돌림 없이)

정책 데이터 한 줄로 끈다 — ① `migration.module` 을 없는 모듈로 · ② `normalize.*=false`(오늘 거동은
어느 쪽이든 같다) · ③ `jar.class-roots`·`jar.external-class-roots` 를 비움(비우면 배포물 판정이 붉는다 —
그것이 의도다) · ④ 끌 수 없다(등식의 한 종류다) · ⑤ `source.import.syntax-names` 로 면제 확대 ·
⑥ `source.scoped-packages` 를 비움 · ⑧ `source.env-sniff.member-pairs` 를 비움.

## 예상 복구 시간

위 복원 명령 하나로 1 분 이내(트리 조작만). 복원된 트리는 `3ad4f3f6` 과 같은 트리라 재빌드 없이
그 SHA 의 CI 결과가 그대로 적용된다.
