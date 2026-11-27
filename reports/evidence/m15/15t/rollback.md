# M15 / 15T — rollback

**실측 HEAD: `56a92a070`**. 이 파일을 담는 커밋은 그 뒤에 온다. 첫 실측이다(앞 라운드 없음).

## 전제 — 복원 원천은 착수 base 다

`afc3b9db6f494b76285207e04a39649401dcfec8`(PR #206 머지 커밋). 이 브랜치는 그 뒤로 main 을 **병합하지
않았다** — `git rev-list --merges <base>..<실측 HEAD>` **빈 출력**(머지 0). 그래서 원천을 「병합한 main 의
고정 SHA」로 옮기는 규율도, 셋째 단언(두 원천에서 복원 대상 blob 이 같은가)도 이 라운드에 해당하지 않는다.

- **단언 ① 원천이 HEAD 의 조상이다**: `git merge-base --is-ancestor` **예**.
- **단언 ② 원천에 이 slice 의 첫 산출물 커밋(`4338c69d5`)이 없다**: **없다**(실측).
- `base..<실측 HEAD>` 의 커밋은 **다섯 전부 이 slice 의 것**이고 하네스 레인(`CLAUDE.md`·`.claude/**`)
  커밋은 **0** 건이다(실측) — 그래서 커밋 해시 hunk 격리가 필요 없다.
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

**저장소 밖에는 되돌릴 것이 없다** — 운영 host·systemd·운영 컨테이너·DB·상류 저장소에 쓰는 명령을
하나도 돌리지 않았다. 로컬에서 만든 이미지(`bidvector/ml-serving:local`)와 대조용 임시 태그는 저장소
산출물이 아니다(대조 태그는 전부 지웠다). **CI 설정과 Dockerfile 의 변경은 다음 실행·다음 빌드부터
효력이 있으므로**, 되돌리면 그 뒤가 곧바로 옛 거동으로 선다 — 이미 만들어진 이미지를 거두는 일은 운영
반입(다음 release)에 닿기 전이라 없다.

## 목록과 명령

`git diff --name-status <base>..<실측 HEAD>` — **M 5 · A 4**(기계 산출이고 라운드마다 다시 낸다).

```
git restore --source=afc3b9db6f494b76285207e04a39649401dcfec8 --staged --worktree -- \
  .github/workflows/ci.yml docker/ml-serving.Dockerfile milestone-15.md \
  reports/evidence/m15/15s/scope.md tools/workflow-action-pins-check.sh
git rm -rq reports/evidence/m15/15t \
  tools/image-pip-lock-check.sh tools/image-pip-lock-check.py
```

두 번째 명령이 게이트 스크립트 둘을 함께 지운다 — **ci.yml 을 되돌리면서 게이트만 남기면 `tools/` 에
호출자 없는 파일이 남고**, 그 스크립트가 요구하는 이미지 안 보고서는 되돌린 Dockerfile 이 더는 만들지
않으므로 누가 손으로 돌리면 영구히 exit 2 다. 순서는 상관없지만 **둘 다** 지워야 한다.

## 임시 clone 실측 (실측 HEAD 에서)

저장소 밖 clone(`--no-hardlinks`)에서 돌렸다. 공유 작업 트리를 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 되돌린 것(판정 대상 대비): **D 4 · M 5** — 위 목록과 같은 수다
- ③ `git diff --cached <base>` **빈 출력(0 줄)**
- 트리 동일성: 복원 뒤 `git write-tree` == `<base>^{tree}` == **`c58a189e702843727a72025dc03ab3e92da091fc`**
- **양성 대조**(비파괴 절삭 — `checkout --` 을 쓰지 않았다): 복원 뒤 `.github/workflows/ci.yml`(1278줄)에
  한 줄을 더하자 트리가 `4c0cd31fb` 로 갈라졌고 `head -n 1278` 절삭으로 `c58a189e7` 로 돌아왔다. 빈
  등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은
  객체**이고 `afc3b9db6` 의 CI 는 **success** 다(실측: `gh run list --commit <base>` → ci / completed /
  success). 「HEAD 가 초록이다」나 Gradle·Docker cache 적중을 근거로 쓰지 않았다.

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  .github/workflows/ci.yml docker/ml-serving.Dockerfile milestone-15.md \
  reports/evidence/m15/15s/scope.md tools/workflow-action-pins-check.sh \
  reports/evidence/m15/15t/ tools/image-pip-lock-check.sh tools/image-pip-lock-check.py
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`afc3b9db6`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일이 그 경우다. 열거와 경로별 원천 부재 확인은 verifier 가 판정 SHA 에서
한다. 예외를 쓰면 ② 의 **D 수는 판정 SHA 에서 다시 센다**(evidence 파일이 늘면 복원이 지우는 수가
그만큼 는다 — 트리 결과는 같다).
