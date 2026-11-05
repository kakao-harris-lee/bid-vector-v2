# M9 / ledger — rollback

## 전제

- **이 slice 는 main 에 없다**(머지 전). 복원 원천은 **base `df4976cc`** — 이 브랜치가 분기한 main 커밋을
  고정 기재한다(움직이는 `origin/main` 을 쓰지 않는다). 이 브랜치는 main 을 병합하지 않았으므로 base 가 담은
  내용이 곧 그 시점 main 의 내용이고, 그래서 공유 파일을 거기서 복원해도 **남의 줄을 떨어뜨릴 수 없다**.
- 머지 **뒤** 되돌림은 이 절차가 아니라 **그 PR 의 머지 커밋 revert** 다(같은 복원은 머지 순간 아무것도 지우지
  않으면서 exit 0 이다).
- 이 range 에 하네스 경로(`CLAUDE.md`·`.claude/**`)의 커밋은 **없다**(계약의 「하네스 레인 변경: 없음」).
- 코드 0 이므로 되돌림은 문서 복원뿐이다 — 재빌드·재기동·마이그레이션 없음.

## 복원 목록 (기계 산출 — 라운드마다 재산출)

```bash
git log --no-merges --format= --name-only HEAD ^df4976cc | sort -u | command grep -v '^reports/evidence/m9/'
```

→ **일곱**. `git diff --name-status df4976cc..HEAD -- <그 일곱>` = **`M` 7**(새 파일·삭제 0).

`docs/discovery/capability-map.md` · `milestone-6.md` · `milestone-7.md` ·
`reports/evidence/m6/6e1/acceptance-trace.md` · `reports/evidence/m6/closure/open-inventory.md` ·
`reports/evidence/m7/close/checklist.md` · `reports/evidence/chore/cve-apt-109518/checklist.md`

**마지막 경로는 계약 갱신 2026-11-06(D-L-2)이 더한 것이다** — 라운드가 복원 목록을 **여섯에서 일곱으로**
늘렸고 그래서 실측을 **재산출**했다(앞 라운드 실측 `62cdde37` 을 옮기지 않았다).

**등식 확인**: 위 기계 산출 목록과 이 일곱의 집합이 같다(`comm -3` 빈 출력). 이 slice 의 evidence
디렉터리는 **목록 밖**이고 아래 명령이 지운다 — 목록 안에 두면 evidence 커밋마다 실측이 낡는다.

## 명령

```bash
git restore --source=df4976cc --staged --worktree -- \
  docs/discovery/capability-map.md milestone-6.md milestone-7.md \
  reports/evidence/m6/6e1/acceptance-trace.md \
  reports/evidence/m6/closure/open-inventory.md \
  reports/evidence/m7/close/checklist.md \
  reports/evidence/chore/cve-apt-109518/checklist.md
rm -rf reports/evidence/m9/ledger
```

운영 자리에는 아무것도 깔리지 않는다(코드·설정·마이그레이션 변경 0). 복원 뒤 운영자가 할 일은 없다.

## 실측 (버릴 clone, 2026-11-06)

- **실측 HEAD: `ecaaabb995cfac8def30e8323b3d03a0243a7105`**(이 slice 의 **마지막 산출물 커밋** — 복원 목록
  경로를 건드린 마지막 커밋, v L-1 수정 커밋). 뒤따르는 evidence 커밋은 이 디렉터리만 만지므로
  `git diff --name-only ecaaabb9..<판정 SHA> -- <복원 목록 일곱>` 이 **빈 출력**이면 이 실측이 유효하다.
  **L-1 라운드는 목록을 바꾸지 않았지만(일곱 그대로) 고친 한 줄이 목록 **안**의 파일이라 HEAD 를 옮겨
  적는 대신 그 커밋에서 ①~⑦ 을 다시 쟀다** — 앞 라운드 실측(`59d46915`)을 옮기지 않는다.
  (대조하는 것은 「실측 HEAD == 판정 SHA」가 아니라 **그 사이에 되돌림 대상이 움직였는가**다.)
- ① 명령 exit: `git restore …` **0** · `rm -rf …` **0**
- ② `D`/`M` 수: **`M` 7**(목록과 같다 · `D` 0)
- ③ 복원 뒤 base 와의 diff: `git diff df4976cc -- <목록 일곱> reports/evidence/m9` = **0 줄** ·
  `git status --porcelain` = 그 일곱 `M` + 이 디렉터리의 네 파일 `D`(목록 밖 경로 **0**)
- ④ **트리 동일성 — blob SHA 대조 일곱 전부 base 와 같다**(`git rev-parse df4976cc:<f>` == `git hash-object <f>`).
  이 slice 에 컴파일·test 대상 코드가 없으므로 ④ compile·⑤ test 축은 **해당하지 않고**, 갈음은
  「HEAD 초록」이 아니라 **이 동일성**이다.
- ⑤ 이 slice 의 evidence 디렉터리 **부재**
- ⑥ **내 표지 0 · 남의 표지 보존**(계수)
  - 내 표지: `M9/ledger` **0** · `§12.3 (` **0** · `등재 2026-11-06` **0** ·
    `M7 종결 checklist 4 가 함께 넘긴`(L-1 이 고친 그 줄) **0** · `M9/opening-harness` **0** ·
    `M9/cve-followups` **0**
  - **양성 대조(비파괴)** — 앞 넷을 복원 **전** 트리에서 세면 **8 · 6 · 3 · 1** 이다. 술어가 아무것도 세지
    못해 0 이 나오는 꼴과 가른다. 넷째는 **L-1 라운드가 고친 줄을 직접 겨눈 표지**다.
  - 남의 표지: open-inventory `§2 「M7 종결이 이미 넘긴 것」` **1**(L-1 이 귀속을 고친 상대 행) ·
    같은 문서 `§7 정정의 정정의 정정` **1** · M7 종결 `## 4. 넘기는 것` **1** · `## 5. evidence N/A` **1** ·
    추적표 `이 표가 공허해지는 길과 막는 것` **1** · capability map `## 13. 하류 인계` **1** ·
    `milestone-6.md` `OPEN-CODEX-RECORD-COORDINATES` **1** · `milestone-7.md` `## 7A 가 바꾸는 문서` **1** ·
    cve-apt `## 수용 체크리스트` **1** · capability map `### 14.4` **1** 과 **내가 끼운 두 행의 바로 윗 행**
    (`OPEN-7C4-AWAITING-CHECKLIST`) **1** — 표 안에 행을 끼운 라운드라 **이웃 행이 남았는지**를 따로 센다
- ⑥b 게이트: 복원 뒤 트리의 evidence 가 **base 와 바이트 동일**하므로 base 의 게이트 판정이 그대로 돌아온다.
  base 는 PR #123 의 머지 커밋이고 그 PR 의 최종 CI 가 네 job 전부 pass 다 — 되돌린 트리에서 게이트를
  다시 돌리지 않은 사유가 이것이다(트리 동일성으로 갈음).

예상 복구 시간: 1 분 미만(문서 복원뿐).
