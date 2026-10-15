# M7/7A rollback — in_scope 경로 한정 복원

**실측 HEAD: `cedfbb54`** (이 slice 의 마지막 산출물 커밋). ①~⑥ 을 그 커밋을 체크아웃한 임시
clone(저장소 밖)에서 **전부 실제로 실행**했다 — 앞 라운드의 갈음 논증은 걷었다(verifier r4 L-12:
in_scope 트리 동일성은 서지만 되돌린 **트리 전체**는 evidence 다섯에서 다르므로 「되돌린 트리의
파일 SHA 가 이미 실측한 트리와 같음」이 성립하지 않는다 — 갈음의 근거가 트리 동일성이 아니라
「evidence 를 읽는 Gradle 게이트는 하나」라는 분석이었다). 목록도 결과도 이 HEAD 에서 다시 냈다.

**라운드가 더 붙으면 이 절차를 다시 돌린다.** 특히 팀장 레인이 뒤에 `milestone-7.md`·`milestone-6.md`
의 착수·종결 문단을 더하면 그 두 경로가 복원 목록에 들어오므로 재산출이 필요하다(이 실측 시점에는
두 파일이 base 와 같아 목록에 없다).

## 전제 (머지 **전**을 가정한다)

- 운영 영향 0 — 이 slice 는 런타임 코드를 만들지 않는다. `web/` 는 Gradle `settings.gradle.kts` 에도
  Python 패키지에도 없고 어떤 출하 조립도 참조하지 않는다. 되돌림은 CI job 하나와 `check` job 의
  툴체인 step 둘, 그리고 정적 프런트 골격을 없애는 것이다.
- 복원 기준 SHA 는 **`d930d54c` 고정**(움직이는 `origin/main` 을 쓰지 않는다). 단언 넷:
  - `git merge-base --is-ancestor d930d54c cedfbb54` → exit 0 (base 가 실측 HEAD 의 조상).
  - `git merge-base HEAD origin/main` → `d930d54c` — 이 브랜치에 마지막으로 병합된 main 커밋이 곧
    base 다. `git log --merges d930d54c..cedfbb54` → 0 (병합 커밋 없음).
  - `git merge-base --is-ancestor 75a84f6e d930d54c` → exit 1 (base 가 이 slice 의 **첫** 산출물
    커밋을 포함하지 않는다).
  - `git branch -r --contains 75a84f6e` → 빈 출력 (main 에 이 slice 없음).
- **머지 뒤**의 되돌림은 이 복원이 아니라 **그 PR 의 머지 커밋 revert** 다 — 머지 순간 같은 복원
  명령은 아무것도 지우지 않으면서 exit 0 을 낸다.

## 복원 목록 — 기계 산출

```
git diff --name-status d930d54c..cedfbb54 -- web .github/workflows/ci.yml tools/one-command-check.sh \
  .gitignore docs/adr/0008-frontend-disposition.md docs/adr/0011-web-frontend-stack.md \
  docs/discovery/m7-screen-data-requirements.md
```
실측 HEAD 에서 **A 27 · M 4**. A(신규)는 복원 명령이 삭제하고 M(수정)은 base 내용으로 되돌린다 —
`--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다. (파일 이름을 여기
나열하지 않는다 — 목록은 위 명령이 정본이고, 손으로 옮긴 목록이 낡는 것이 이 결함의 원인이다.)

**전체 변경 − 복원 목록 = evidence 다섯**(`comm -23` 실측): `scope.md` · `checklist.md` ·
`commands.md` · `rollback.md` · `m6-condition3-adjustment-proposal.md`. 그 밖의 경로가 나오면
목록이 낡은 것이다.

## 되돌리지 않는 것

- `reports/evidence/m7/7a/**` — 이 slice 의 기록이다. 복원 집합에서 **빼는 이유 둘**: (a) evidence 는
  일이 되었음을 남기는 기록이고 되돌림 대상이 아니다, (b) 넣으면 뒤따르는 evidence 커밋이 「실측
  HEAD 이후 되돌림 대상이 움직였는가」 술어를 구조적으로 깨뜨린다(그 커밋은 언제나 마지막 산출물
  커밋 뒤에 온다). 아래 ⑥ 에서 **evidence 를 남긴 채** 게이트가 초록인 것을 실측했다 — 이 slice 의
  evidence 는 누출 스캔 어휘를 축어로 담지 않아 `leakPatternGate` 의 baseline 밖 매치를 만들지 않는다.
- `CLAUDE.md`·`.claude/**` — 하네스 레인. `git log --oneline d930d54c..cedfbb54 -- CLAUDE.md .claude/`
  는 **빈 출력**이다(이 range 에 하네스 커밋 0). scope.md 의 「하네스 레인 변경」 절도 「없음」이다.

## 복원 명령

```
git restore --source=d930d54c --staged --worktree -- \
  web \
  .github/workflows/ci.yml \
  tools/one-command-check.sh \
  .gitignore \
  docs/adr/0008-frontend-disposition.md \
  docs/adr/0011-web-frontend-stack.md \
  docs/discovery/m7-screen-data-requirements.md
```
경로는 **개별 인자**다(목록을 변수 하나에 담으면 pathspec 이 하나가 되어 아무것도 매치하지 않는다).
공유 파일(`ci.yml`·`one-command-check.sh`·`.gitignore`·ADR 0008)에 커밋 해시 hunk 격리를 쓰지
않는다 — base 가 곧 마지막 병합 main 커밋이라 거기서 만든 트리는 **다른 slice 의 줄을 떨어뜨릴 수
없다**. 되돌린 뒤 `web/` 는 디렉터리째 사라지고, 로컬에 남는 무시 대상(`web/node_modules`·`dist`·
Playwright 산출)은 `git clean -fdx -- web` 으로 지운다(게이트 대상 아님).

## ①~⑥ 실측 — 임시 clone, HEAD `cedfbb54`

| # | 잰 것 | 결과 |
|---|---|---|
| ① | 복원 명령 종료 코드 | **exit 0** |
| ② | 복원 뒤 `git status --porcelain -- <같은 경로들>` 의 D/M 수 | **D 27 · M 4** (기계 산출 목록 A 27 · M 4 와 일치) |
| ③ | `git diff d930d54c -- <같은 경로들>` | **0행** — 되돌린 트리의 in_scope 내용이 base 와 **같다**(트리 동일성) |
| ④⑤⑥ | 되돌린 트리에서 `./gradlew --no-daemon check` | **exit 0 · BUILD SUCCESSFUL in 9m 44s** · 359 actionable tasks(224 executed · 135 from cache) · `leakPatternGate` 가 UP-TO-DATE 가 아니라 **실제로 실행**됐다(되돌리지 않은 evidence 를 담은 트리에서 초록) |
| ⑥(web) | 되돌린 뒤 web 게이트가 돌 자리가 남았는가 | `web/` 없음 · ci.yml 의 job 목록에 `web` 없음 · `check` job 의 Node 툴체인 step 둘 없음 · `one-command-check.sh` 의 web 언급 0(카나리 step 포함) — 되돌린 뒤 돌 web 게이트가 **없다**(없는 게이트를 「초록」이라 적지 않는다) |

## verifier 가 대조할 술어

```
git diff --name-only cedfbb54..<판정 SHA> -- web .github/workflows/ci.yml \
  tools/one-command-check.sh .gitignore docs/adr/0008-frontend-disposition.md \
  docs/adr/0011-web-frontend-stack.md docs/discovery/m7-screen-data-requirements.md
```
**빈 출력이면 이 실측이 유효하다.** 한 줄이라도 나오면 그 뒤 커밋이 되돌림 대상을 바꾼 것이므로
실측은 낡았고 미검증이다(대조 경로는 위 복원 목록 자신이다 — evidence 와 팀장 레인 문서는 복원
집합이 아니므로 이 술어의 대상도 아니다).

## 다시 돌려야 하는 조건

이 실측은 **이 HEAD 의 되돌린 트리**에 대한 것이다. 복원 목록에 경로가 늘거나 base 가 움직이면
(rebase·흡수 병합) 목록을 재산출하고 ①~⑥ 을 그 HEAD 에서 다시 돌린다. 산출물 커밋이 더 붙으면
실측 HEAD 도 그 커밋으로 올린다(갈음하지 않는다).

## 예상 복구 시간

복원 명령 1초 미만 + 확인(①~③) 1분 미만. 되돌린 트리의 게이트 전건 재확인까지 포함하면 약 10분.
