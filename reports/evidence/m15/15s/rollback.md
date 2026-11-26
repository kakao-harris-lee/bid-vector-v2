# M15 / 15S — rollback

**실측 HEAD: `78367735b`**. 이 파일을 담는 커밋은 그 뒤에 온다. **둘째 실측이다** — 수정 라운드 1 이
복원 목록 안의 파일 넷을 움직이고 **신설 파일 하나**(`tools/workflow-action-pins-parity.py`)를
더해서 다시 쟀다. 앞 실측(`c59b04f45` · M 2 · A 4)을 옮기지 않았다.

## 전제 — 복원 원천은 착수 base 다

`8e7a91ead0e01e6a4f4c5f4d827d2668176a80b6`(PR #205 머지 커밋). 이 브랜치는 그 뒤로 main 을 **병합하지
않았다**(`base..HEAD` 의 머지 커밋 **0**) — 원천을 병합한 main 의 고정 SHA 로 옮기는 규율도, 셋째
단언(두 원천에서 복원 대상 blob 이 같은가)도 이 라운드에 해당하지 않는다.

- **단언 ① 원천이 HEAD 의 조상이다**: `git merge-base --is-ancestor` **예**.
- **단언 ② 원천에 이 slice 의 첫 산출물 커밋(`91caaea12` — 계약)이 없다**: **없다**(실측).
- `base..<실측 HEAD>` 의 커밋은 **열둘 전부 이 slice 의 것**이고 하네스 레인(`CLAUDE.md`·`.claude/**`)
  커밋은 **0** 건이다 — 그래서 hunk 격리가 필요 없다. (앞 판은 「다섯」이라고 적었다 — 그 시점에도
  수가 틀렸고 결론만 같았다. verifier L-2 정정.)
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

**저장소 밖에는 되돌릴 것이 없다** — 운영 host·systemd·운영 컨테이너·DB 에 닿는 명령을 하나도 돌리지
않았고, 상류 저장소에 쓰기를 한 적도 없다(조회만). CI 설정의 변경은 **다음 워크플로 실행부터** 효력이
있으므로, 되돌리면 그 뒤 실행이 곧바로 옛 거동으로 선다.

## 목록과 명령

`git diff --name-status 8e7a91ead..<실측 HEAD>` — **M 2 · A 6**(기계 산출이고 라운드마다 다시 낸다.
수정 라운드 1 이 `tools/workflow-action-pins-parity.py` 를 더해 A 가 넷에서 여섯이 됐다 —
evidence 셋 + 산출물 셋).

```
git restore --source=8e7a91ead0e01e6a4f4c5f4d827d2668176a80b6 --staged --worktree -- \
  .github/workflows/ci.yml milestone-15.md
git rm -rq reports/evidence/m15/15s \
  config/quality/workflow-action-pins.txt \
  tools/workflow-action-pins-check.sh tools/workflow-action-pins-parity.py
```

두 번째 명령이 게이트 스크립트·parity 스크립트·기대 파일을 함께 지운다 — **ci.yml 을 되돌리면서
게이트만 남기면 그 게이트가 영구히 붉는다**(되돌린 워크플로는 태그를 쓴다). parity 도 같다: 되돌린
ci.yml 에는 S-12 가 없지만, 스크립트를 남기면 `tools/` 에 호출자 없는 파일이 남고 그 안의
`GATE` 경로가 사라진 스크립트를 가리킨다. 순서는 상관없지만 **셋 다** 지워야 한다.

## 임시 clone 실측 (실측 HEAD 에서)

저장소 밖 clone(`--no-hardlinks`)에서 돌렸다. 공유 작업 트리를 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 되돌린 것(판정 대상 대비): **D 6 · M 2** — 위 목록과 같은 수다
- ③ `git diff --cached 8e7a91ead` **빈 출력(0 줄)**
- 트리 동일성: 복원 뒤 `git write-tree` == `8e7a91ead^{tree}` == **`5a7a6238fe300694fcd64fe6ed8e470537f52e33`**
- **양성 대조**(비파괴 절삭 — `checkout --` 을 쓰지 않았다): 복원 뒤 `.github/workflows/ci.yml` 에 한 줄을
  더하자 등식이 깨졌고(트리 `41d4887d4`) `head -n <원래 줄수>` 절삭으로 `5a7a6238f` 로 돌아왔다. 빈
  등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은
  객체**이고 `8e7a91ead` 의 CI 는 **success** 다(실측). 「HEAD 가 초록이다」나 Gradle cache 적중을 근거로
  쓰지 않았다.
- `git rev-list --merges 8e7a91ead..<실측 HEAD>` **빈 출력** — 원천을 옮길 병합이 없다(위 전제).

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  .github/workflows/ci.yml milestone-15.md reports/evidence/m15/15s/ \
  config/quality/workflow-action-pins.txt \
  tools/workflow-action-pins-check.sh tools/workflow-action-pins-parity.py
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`8e7a91ead`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일이 그 경우다. 열거와 경로별 원천 부재 확인은 verifier 가 판정 SHA 에서
한다. 예외를 쓰면 ② 의 **D 수는 판정 SHA 에서 다시 센다**(evidence 파일이 늘면 복원이 지우는 수가
그만큼 는다 — 트리 결과는 같다).
