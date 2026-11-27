# M15 / 15T — rollback

**실측 HEAD: `0a5a46150`** — 마지막 **산출물** 커밋(게이트 둘의 술어 수정)이다. 이 파일을 담는
커밋은 그 뒤에 온다. 앞 실측 둘(`56a92a070` 착수 base 를 원천으로 · `847a755d2` 병합 커밋에서)은
**옮기지 않는다** — 그 뒤 라운드가 복원 목록 안의 파일(게이트 둘)을 또 움직였다. **원천은 그대로**
`78f2c77a9` 이고, 복원 결과 트리도 같은 객체다.

## 전제 — 복원 원천은 병합된 main 의 고정 SHA 다

`78f2c77a99e2993923ef0064b2656e6c2d144aab`(PR #208 머지 커밋, libpam 상향). 착수 base
`afc3b9db6…` 가 아니다 — 그 base 로 되돌리면 **남의 작업**(#208 의 libpam 하한 넷)을 함께 지운다.
움직이는 `origin/main` 이 아니라 이 40자 SHA 를 적는다.

- **단언 ① 원천이 HEAD 의 조상이다**: `git merge-base --is-ancestor` **예**.
- **단언 ② 원천에 이 slice 의 첫 산출물 커밋(`4338c69d5`)이 없다**: **없다**(실측).
- **단언 ③ 복원 대상 blob 이 착수 base 와 새 원천에서 같은가**(원천을 바꿀 때의 셋째 단언):
  `.github/workflows/ci.yml` · `milestone-15.md` · `reports/evidence/m15/15s/scope.md` ·
  `tools/workflow-action-pins-check.sh` 넷은 **같다**(blob 해시 동일). `docker/ml-serving.Dockerfile`
  은 **다르다** — #208 이 그 파일의 runtime apt RUN 과 주석을 고쳤다. 그래서 그 경로는 **새 원천
  기준으로 다시 셌다**(아래 계수).
- `git rev-list --merges <원천>..<실측 HEAD>` **1건** — 그 하나는 main 병합 `847a755d2`(부모
  `98bd626df` ∥ `78f2c77a9`)이고 실측 HEAD 의 **조상**이다. 즉 병합이 마지막 산출물 커밋보다
  **앞**에 오므로 실측 자리는 그 병합이 아니라 이 HEAD 다(「병합이 뒤에 오면 실측 자리는 병합
  커밋」의 반대 경우다).
- 하네스 레인(`CLAUDE.md`·`.claude/**`) 커밋 **0** 건 — 커밋 해시 hunk 격리가 필요 없다.
- 머지 뒤의 되돌림은 이 문서가 아니라 **그 PR 의 머지 커밋 revert** 다.

**저장소 밖에는 되돌릴 것이 없다** — 운영 host·systemd·운영 컨테이너·DB·상류 저장소에 쓰는 명령을
하나도 돌리지 않았다. 로컬 이미지와 대조용 임시 태그는 저장소 산출물이 아니다(대조 태그는 전부
지웠다). CI 설정과 Dockerfile 의 변경은 **다음 실행·다음 빌드부터** 효력이 있으므로 되돌리면 그 뒤가
곧바로 옛 거동으로 선다 — 운영 반입(다음 release)에 닿기 전이라 거둘 이미지가 없다.

## 목록과 명령

`git diff --name-status <원천>..<실측 HEAD>` — **M 5 · A 5**(기계 산출이고 라운드마다 다시 낸다.
이 라운드는 목록을 바꾸지 않았다 — 만든 새 파일이 0 이고 게이트 둘의 **내용만** 바뀌었다).

```
git restore --source=78f2c77a99e2993923ef0064b2656e6c2d144aab --staged --worktree -- \
  .github/workflows/ci.yml docker/ml-serving.Dockerfile milestone-15.md \
  reports/evidence/m15/15s/scope.md tools/workflow-action-pins-check.sh
git rm -rq reports/evidence/m15/15t \
  tools/image-pip-lock-check.sh tools/image-pip-lock-check.py
```

두 번째 명령이 게이트 스크립트 둘을 함께 지운다 — **ci.yml 을 되돌리면서 게이트만 남기면 `tools/` 에
호출자 없는 파일이 남고**, 그 스크립트가 요구하는 이미지 안 보고서는 되돌린 Dockerfile 이 더는 만들지
않으므로 누가 손으로 돌리면 영구히 exit 2 다. 순서는 상관없지만 **둘 다** 지워야 한다.
`reports/evidence/chore/cve-libpam-2025-6020/`(#208 의 evidence)는 목록 밖이다 — 남의 것이다.

## 임시 clone 실측 (실측 HEAD 에서)

저장소 밖 clone(`--no-hardlinks`)에서 돌렸다. 공유 작업 트리를 건드리지 않았다.

- ① 명령 exit: `restore` **0** · `rm` **0**
- ② 되돌린 것(판정 대상 대비): **D 5 · M 5** — 위 목록과 같은 수다
- ③ `git diff --cached <원천>` **빈 출력(0 줄)**
- 트리 동일성: 복원 뒤 `git write-tree` == `<원천>^{tree}` == **`8adaf2ec8aee136bb38b8ff2e12604e0bb7cb64d`**
- **완료는 종료 코드가 아니라 계수로 센다**(원천이 바뀐 경로가 있으므로):

  | 표지 | 복원 전 | 복원 후 | 기대 |
  |---|---|---|---|
  | 내 것 — Dockerfile 의 `pip-install-report.json` / `--require-hashes` / `build-constraints` | 2 / 5 / 3 | **0 / 0 / 0** | 0 |
  | 내 것 — ci.yml 의 `image-pip-lock-check` | 1 | **0** | 0 |
  | 내 것 — `tools/image-pip-lock-check.*` 파일 | 2개 | **0개** | 0 |
  | 남의 것 — Dockerfile 의 `libpam` / `CVE-2025-6020` / `1.5.2-6+deb12u3` | 5 / 3 / 3 | **5 / 3 / 3** | 보존 |
  | 남의 것 — apt RUN 의 하한 항목 | 6 | **6** | 보존 |
  | 남의 것 — `reports/evidence/chore/cve-libpam-2025-6020/` | 2개 | **2개** | 보존 |

- **양성 대조**(비파괴 절삭 — `checkout --` 을 쓰지 않았다): 복원 뒤 `docker/ml-serving.Dockerfile`
  (160줄)에 한 줄을 더하자 트리가 `347043143` 로 갈라졌고 `head -n 160` 절삭으로 `8adaf2ec8` 로
  돌아왔다. 빈 등식이 건전성의 증거가 아님을 그 한 줄로 가른다.
- ④ compile · ⑤ test · ⑥ 게이트는 **트리 동일성으로 갈음**한다 — 복원 뒤 트리가 원천의 트리와 **같은
  객체**이고 `78f2c77a9` 의 CI 는 **success** 다(실측: `gh run list --commit <원천>` → ci / completed /
  success). 「HEAD 가 초록이다」나 Gradle·Docker cache 적중을 근거로 쓰지 않았다.

## 유효성 술어

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  .github/workflows/ci.yml docker/ml-serving.Dockerfile milestone-15.md \
  reports/evidence/m15/15s/scope.md tools/workflow-action-pins-check.sh \
  reports/evidence/m15/15t/ tools/image-pip-lock-check.sh tools/image-pip-lock-check.py
git rev-list --merges <실측 HEAD>..<판정 SHA>
```

둘 다 빈 출력이면 유효하다. 첫 술어에서 **나온 경로가 전부 원천(`78f2c77a9`)에 없는 신설 파일이면
예외로 유효하다** — 이 파일이 그 경우다. 열거와 경로별 원천 부재 확인은 verifier 가 판정 SHA 에서
한다. 예외를 쓰면 ② 의 **D 수는 판정 SHA 에서 다시 센다**(evidence 파일이 늘면 복원이 지우는 수가
그만큼 는다 — 트리 결과는 같다).
