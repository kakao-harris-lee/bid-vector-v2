# M15 / 15V — rollback

**실측 HEAD: `cb5c7d73e`**(`origin/main` 을 들인 **병합 커밋**). 규율대로 main 병합이 마지막 산출물
커밋 뒤에 오므로 실측 자리는 그 병합 커밋이다. 이 파일과 `commands.md` 를 담는 커밋은 그 뒤에 오고,
둘 다 원천에 **없는 신설**이라 아래 유효성 술어의 예외에 든다. 목록 자체는 계약 갱신 ⑥ 에서
**M 7 · A 넷**이 된 뒤 바뀌지 않았다(신설 등재본이 그 A 의 하나다).

**병합 뒤에는 완료 기준이 바뀐다.** 복원은 in_scope 경로만 되돌리므로 다른 레인이 들여온 것
(libzstd chore — `docker/ml-serving.Dockerfile` + `reports/evidence/chore/cve-libzstd-2026-124851/`)
은 **그대로 남아야 한다**. 그래서 「복원 트리 == 착수 base 트리」는 이제 성립하지 않고(남은 192 줄이
그 chore 다), 기준은 **「내 표지 0 · 남의 표지 보존」 계수**다. 다만 이번에는 더 강한 등식이 섰다 —
**복원 트리 == 병합된 main 의 트리**(아래 ⑤).

**복원 원천은 착수 base `9a2ce352b` 그대로다** — `origin/main` 이 `cb90105b9` 로 움직였지만 원천을
바꾸지 않았고, 그 선택이 안전하다는 것을 **셋째 단언**으로 쟀다: **복원 대상 blob 이 착수 base 와
병합된 main 에서 같은가**. in_scope 복원 경로 **일곱 전부 같다**(`git rev-parse <sha>:<경로>` 비교 —
libzstd chore 가 만진 것은 `docker/ml-serving.Dockerfile` 과 그 chore evidence 뿐이다). 그러므로 이
경로들에서 「base 에서 복원」과 「`cb90105b9` 에서 복원」은 **같은 바이트**를 낸다. 신설 넷은
`cb90105b9` 에 **없음**도 확인했다(있으면 `git rm` 이 남의 것을 지운다).

**운영 host 에는 되돌릴 것이 없다.** 이 slice 는 `/etc`·systemd·운영 compose·운영 DB·조달청 API·공유
잠금 자리를 하나도 건드리지 않았고 `sudo` 를 쓰지 않았다. 운영자가 §14 를 이미 밟았다면 되돌림은
저장소가 아니라 그 host 에서 한다(§14.8 의 타이머 끄기 + unit·drop-in 제거). **실행 상태 디렉터리는
지우지 않는다** — 그 안의 원장이 그 달의 호출 이력이다.

## 목록과 명령

`git diff --name-status 9a2ce352b..<실측 HEAD>` — **M 여덟 · A 여섯**(기계 산출 · 라운드마다 재산출).
그 가운데 **내 것은 M 일곱 · A 넷**이고, 나머지(`docker/ml-serving.Dockerfile` ·
`reports/evidence/chore/cve-libzstd-2026-124851/` 둘)는 병합으로 들어온 **다른 레인의 것이라 복원
목록에 넣지 않는다** — 넣으면 남의 작업을 지운다.
`config/quality/runbook-prod-path-lines.txt` 는 수집 필터를 넓히면서 M 에 들어왔고, A 는 15V evidence
셋과 **계약 갱신 ⑥ 의 신설 등재본**(`config/quality/runbook-collection-step4-block.txt`)이다. 신설
파일은 원천에 없으므로 `restore` 가 아니라 `rm` 으로 지운다.

```
git restore --source=9a2ce352b3ea2d39b57d91f94f723cff36150b98 --staged --worktree -- \
  config/quality/runbook-prod-path-lines.txt docs/discovery/capability-map.md \
  docs/runbook/m7-7f-production.md milestone-15.md reports/evidence/m15/15u/commands.md \
  tools/prod-collection.sh tools/prod-guards-check.sh
git rm -rq reports/evidence/m15/15v config/quality/runbook-collection-step4-block.txt
```

`config/quality/runbook-prod-path-lines.txt` 가 **r1 에서 목록에 들어왔다** — 수집 필터에
`/var/tmp/bidvector` 를 더하면서 새 sink 네 줄이 집합에 등재됐다(cr L-1). `runbook-koneps-env-lines.txt` 는
그대로다(`koneps.env` 축의 줄이 바뀌지 않았다 — 기계 재산출로 확인).

## 임시 clone 실측(실측 HEAD 에서)

버릴 clone 에서 ①~⑥ 을 쟀다. 저장소 트리는 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 스테이징된 변경: **D 4 · M 7**(위 기계 목록의 수와 같다 — 신설 등재본이 넷째 D 다)
- ③ 복원 뒤 착수 base 대비 남은 것은 **chore 세 경로뿐**(`M docker/ml-serving.Dockerfile` ·
  `A` chore evidence 둘 · 192 줄)이고 그 diff 에 **내 표지는 0** 이다 — 과소·과다 복원이 아니다
- ⑤ **트리 동일성**: 복원 뒤 `git write-tree` == **`9d949748aa76bfd09ddcafe0c6bea195f5ca1556`** ==
  `cb90105b9^{tree}` — 즉 **복원하면 이 브랜치가 병합된 main 과 바이트로 같아진다**(병합 전 다섯
  라운드에서는 그 값이 착수 base 트리 `7989c86e5…` 였다)
- **양성 대조**(비파괴 절삭 — 버릴 clone 에서만, `checkout --` 를 쓰지 않았다): 복원 뒤
  `milestone-15.md` 에 한 줄을 더하자 등식이 깨졌다(절삭 트리
  **`30eccd42e5885c4afc7935c01140a83c3fdfe8ae`**). 그 줄을 다시 복원해 트리가 `9d949748a…` 로
  돌아오는 것까지 쟀다. 빈 등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ⑧ **「내 표지 0 · 남의 표지 보존」**(병합 뒤의 완료 기준): 내 표지
  (`STEP4_EXPECT`·`runbook-collection-step4-block`·`q_shimvar`·`SKIPHD`·`qbin`)를 담은 추적 파일
  **0 개** · libzstd 줄 **네 개 보존** · 그 chore 의 evidence 디렉터리 **있음**.
- ④ compile · ⑤ test 는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은 객체**이고
  (위 해시 등식) `9a2ce352b` 는 PR #209 머지 시점에 CI 가 초록이었다. 「HEAD 가 초록이다」로 갈음하지
  않았다.
- ⑥ **게이트는 직접 돌렸다**(신설 등재본이 지워진 뒤이므로 그 파일을 읽는 단언도 함께 사라진
  상태다): 복원된 트리에서 `./tools/prod-guards-check.sh` 가 **통과 474 · 어긋남 0**
  (15V 착수 전의 수 — 516 이 아니다) · `bash -n` 두 파일 0 · `reports/evidence/m15/15v` 는 복원 뒤
  **없다**(신설이므로 삭제가 맞다 — `ls` 로 확인했다). 신설 등재본도 **없다**(같은 사유).

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  config/quality/runbook-prod-path-lines.txt config/quality/runbook-collection-step4-block.txt \
  docs/discovery/capability-map.md docs/runbook/m7-7f-production.md milestone-15.md \
  reports/evidence/m15/15u/commands.md tools/prod-collection.sh tools/prod-guards-check.sh \
  reports/evidence/m15/15v/
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`9a2ce352b`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일과 `commands.md` 가 그 경우다(둘 다 `reports/evidence/m15/15v/` 아래의 신설
이고 `A` 로 들어온다). 열거와 원천 부재 확인은 verifier 가 한다. **r2 · 승인 전 묶음 · ⑥ 에서도 그
둘뿐이다** — 원천에 있던 파일은 실측 HEAD 가 마지막으로 움직였다. ⑥ 이 더한 신설 등재본은 실측 HEAD
**앞**에 커밋됐으므로 이 술어가 보는 구간 밖이다.

**머지는 실측 자리 그 자체다** — `origin/main`(`cb90105b9`)을 `--no-ff` 로 들인 병합 커밋
`cb5c7d73e`(부모 둘: `37372ed7a` · `cb90105b9`, 충돌 0 · `stash` 쓰지 않음)가 마지막 산출물 커밋
뒤에 오므로 규율대로 **실측 자리는 그 병합 커밋**이다. 그래서 `--merges <실측 HEAD>..<판정 SHA>` 는
빈 출력이고(머지가 그 구간 **앞**에 있다), `base..판정 SHA` 에는 그 한 개가 **있다** — 그 존재가
실측 자리를 병합 커밋으로 옮긴 사유이고, 셋째 단언이 복원 원천을 base 로 둘 수 있음을 받친다.
