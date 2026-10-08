# M6/6E-2b — rollback

실측 HEAD: `1d019fa3`(이 slice 의 **마지막 산출물 커밋**). 이 뒤의 커밋은 evidence 전용이다.
앞 라운드 실측(`c4f22dd1`)은 **옮기지 않고 버렸다** — 그 뒤 `vuln-allowlist.properties`(되돌림 대상)가
움직였으므로 아래 verifier 술어가 그 실측을 미검증으로 판정했을 것이다.

되돌림은 **range revert 가 아니라 in_scope 경로 한정**이다 — `git revert <base>..HEAD` 는 같은 range 에
있는 팀장 레인의 계약 갱신 커밋(`scope.md`)까지 되돌린다.

## 되돌리는 것 / 되돌리지 않는 것

목록은 손으로 쓰지 않고 `git diff --name-status 2deb5f9d..HEAD` 에서 기계적으로 낸다
(A = 삭제 대상, M = base 로 복원). **라운드마다 파일이 늘면 이 산출을 다시 돌린다.**

| 상태 | 경로 |
|---|---|
| A | `config/quality/vuln-allowlist.properties` · `config/quality/vuln-policy.properties` · `tools/vuln-scan-check.sh` |
| M | `.github/workflows/ci.yml` · `docker/ml-serving.Dockerfile` · `docs/runbook/m6-6e-operations.md` |

**되돌리지 않는 것** — `reports/evidence/m6/6e2b/**`(이 slice 의 증적) · 하네스 경로(`CLAUDE.md`·
`.claude/**`) · 팀장 레인이 쓴 `scope.md`.

**공유 파일에 다른 slice 의 줄이 없다.** `git log --oneline 2deb5f9d..HEAD -- <파일>` 로 확인했고
`ci.yml` 은 `c9decffe` 하나, runbook 은 `1eafab7e`·`c4f22dd1` 둘 다 이 slice 의 커밋이다. 그래서
**커밋 해시 hunk 격리가 필요 없고** 단일 `git restore --source=<base>` 로 끝난다. 이 판정은 라운드마다
다시 내린다 — 다른 slice 가 같은 파일을 만지는 순간 절차가 hunk 격리로 바뀐다.

## 명령

```
git restore --source=2deb5f9d --staged --worktree -- \
  .github/workflows/ci.yml \
  config/quality/vuln-allowlist.properties \
  config/quality/vuln-policy.properties \
  docker/ml-serving.Dockerfile \
  docs/runbook/m6-6e-operations.md \
  tools/vuln-scan-check.sh
```

`--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로 끊긴다.

## 되돌린 뒤 남는 상태

CI `container` job 에서 `install trivy`·S-22d·S-22e·S-22f 가 사라지고, 이미지 위생(S-22a/b)과 나머지
축은 그대로다. 완료 조건 8 은 **다시 절반**(저장소 텍스트를 보는 기존 축만 — `leakPatternGate`)이
되고 `OPEN-6C-IMAGE-VULN-SCAN` 이 다시 열린다. 비활성화만 원한다면 되돌리지 않고 `ci.yml` 의 S-22d·S-22e 두 step 만 빼도 된다 —
그 경우 SBOM 보관(S-22f)이 올릴 파일이 없어 `if-no-files-found: error` 로 붉어지므로 셋을 함께 뺀다.

## ①~⑥ 실측 (버릴 임시 clone, HEAD `1d019fa3`)

| # | 무엇 | 결과 |
|---|---|---|
| ① | 복원 명령 | exit 0 |
| ② | D/M 수 | **D 3 · M 3** (기계 산출 목록과 같다) |
| ③ | `git diff 2deb5f9d -- <경로들>` | **0 줄** |
| ③b | 되돌리지 않기로 한 evidence | 네 문서 전부 남아 있다 |
| ③c | 하네스 경로 `git status --porcelain` | **0 줄**(HEAD 그대로) |
| ③d | 되돌린 `ci.yml` 의 게이트 흔적 | **0 건** |
| ④ | `:app:compileKotlin :adapters:compileKotlin` | exit 0 |
| ⑤⑥ | `./gradlew --no-daemon check` 전건 | exit 0 — `leakPatternGate` 포함 |

**목록 등식도 기계로 확인했다** — `git diff --name-only <base>..HEAD -- . ':!reports/evidence'` 와 위
복원 인자 집합을 `comm -23`·`comm -13` 로 양방향 대조해 둘 다 빈 출력이었다. 「문서에 빠진 경로」와
「문서에만 있는 경로」를 **둘 다** 본다.

⑥ 은 **evidence 네 문서가 전부 들어 있는 트리**에서 돌았다. 마지막 산출물 커밋이 evidence 커밋보다
뒤에 왔기 때문인데(사유 문면 정정이 `config/` 와 evidence 를 함께 만졌다), 덕분에 되돌린 트리에서
`leakPatternGate` 가 이 slice 의 evidence 를 실제로 훑고 통과했다 — 「되돌리지 않기로 한 evidence 가
base 의 baseline 과 어긋나 게이트를 붉히는가」(M4 에서 세 라운드 동안 거짓으로 적혔던 축)를 **이번에는
갈음이 아니라 직접** 쟀다는 뜻이다.

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 오므로 둘은 영원히 다르다).
**그 사이에 되돌림 대상이 움직였는가**를 본다:

```
git diff --name-only 1d019fa3..<판정 SHA> -- \
  .github/workflows/ci.yml config/quality/vuln-allowlist.properties \
  config/quality/vuln-policy.properties docker/ml-serving.Dockerfile \
  docs/runbook/m6-6e-operations.md tools/vuln-scan-check.sh
```

빈 출력이면 이 실측이 유효하다. 한 줄이라도 나오면 미검증이다.
