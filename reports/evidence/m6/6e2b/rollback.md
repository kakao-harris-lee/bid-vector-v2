# M6/6E-2b — rollback

실측 HEAD: `cf608c9a`(종결 일괄). 마지막 **산출물** 커밋은 `1bdd7b79` 이고, `cf608c9a` 는 evidence
전용 커밋이라 **되돌림 대상 여섯의 내용이 둘에서 같다** — 그래서 뒤쪽에서 재고 그 SHA 를 적는다(clone 이
evidence 까지 담아 ⑥ 의 `leakPatternGate` 가 이 slice 의 문서를 실제로 훑는다).
앞 라운드 실측은 **옮기지 않고 매번 다시 낸다** — 되돌림 대상이 한 줄이라도 움직이면 아래 verifier
술어가 그것을 미검증으로 판정한다(실제로 r0 에서 한 번 그렇게 됐다).

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

**공유 파일은 둘로 갈린다 — 그 판정을 라운드마다 다시 내린다.**
`git log --oneline 2deb5f9d..HEAD -- <파일>` 로 그 파일을 만진 커밋이 누구 것인지 본다.

- **단일 restore 다섯**(`ci.yml`·두 정책 파일·`docker/ml-serving.Dockerfile`·`tools/vuln-scan-check.sh`)
  — 만진 커밋이 **전부 이 slice 의 것**이다(`origin/main` 병합 뒤 재확인: 6E-2a 는 이 다섯을 **하나도**
  건드리지 않았다 — `git log --no-merges 2deb5f9d..origin/main -- <파일>` 전부 0 커밋).
- **`docs/runbook/m6-6e-operations.md`** — **병합으로 성질이 바뀌었다.** 6E-2a 가 이 파일을 세 커밋 만졌고
  (머리말·§2.6·§6·§7), 그 줄들이 이제 내 브랜치에 있다. 전체 복원은 **남의 줄을 함께 지운다** →
  복원 목록에서 빼고 아래 hunk 절차로 옮긴다.
- **`milestone-6.md`** — 같은 이유(다른 slice 들의 문단이 산다). 아직 내 쪽 커밋은 없고, 팀장이 종결
  문단을 **이 일괄 뒤에** 쓴다 — 그래서 해시를 박지 않고 실행 시점에 산출한다.

앞 라운드까지 이 절은 「공유 파일에 다른 slice 의 줄이 없으니 hunk 격리가 필요 없다」고 적었다. 그 문장은
**참인 채로 낡는 종류**였고, 자기 전환 조건(「다른 slice 가 같은 파일을 만지는 순간 hunk 로 바뀐다」)을
함께 적어 둔 덕에 여기서 그 조건대로 고친다. `milestone-6.md` 가 그 전환을 발동시켰다.

### 공유 파일 hunk 절차 — runbook · `milestone-6.md`

이 slice 의 줄만 **커밋 해시 역적용**으로 지운다. **해시를 문서에 박지 않는다** — 목록은 실행 시점에
산출한다(종결 문단이 이 evidence 뒤에 붙고, 그 뒤로도 더 붙을 수 있다).

**`^origin/main` 이 핵심이다.** 병합으로 6E-2a 의 커밋이 `2deb5f9d..HEAD` 안에 들어왔다 —
`--no-merges` 만으로는 **남의 커밋까지 역적용**한다(실측: `--no-merges` 만 72 커밋 · `^origin/main` 을
더하면 **37**, 내 것만).

```
for f in docs/runbook/m6-6e-operations.md milestone-6.md; do
  for sha in $(git log --no-merges --format=%h 2deb5f9d..HEAD ^origin/main -- "$f"); do   # 최신부터
    git diff "$sha~1..$sha" -- "$f" | git apply -R
  done
done
```

**그리고 병합 커밋의 충돌 해소분이 남는다.** 위 목록은 `--no-merges` 라 병합 커밋을 건너뛰는데, runbook
머리말의 「둘이 빠졌고 …」 문단은 **내가 병합에서 쓴 것**이다. 그 문단은 손으로 지우고 6E-2a 의 문면
(「커넥션 풀은 이 목록에서 빠졌다 …」)으로 되돌린다 — 그 원문은 `git show origin/main:<파일>` 에 있다.

conflict 가 나면 수동으로 **이 slice 의 줄만** 지운다 — 내 문단은 자기 표지를 갖는다(§8 전체,
「M6/6E-2b」, 「6E-2b」). 확인은 **둘 다**: 내 줄이 사라졌는가, **남의 줄이 남았는가**
(6E-2a 표지: §2.6 의 풀 상수표 · §6 항목 17~19 · §7 의 「고치는 절에 맞는 블록」 문단).

## 명령

```
git restore --source=2deb5f9d --staged --worktree -- \
  .github/workflows/ci.yml \
  config/quality/vuln-allowlist.properties \
  config/quality/vuln-policy.properties \
  docker/ml-serving.Dockerfile \
  tools/vuln-scan-check.sh
```

`--source` 에 없는 경로는 삭제되므로 신규 파일에 별도 `git rm` 이 필요 없다.
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 경로마다 pathspec 오류로 끊긴다.

## 되돌린 뒤 남는 상태

CI `container` job 에서 `install trivy`·S-22d·S-22e·S-22f 가 사라지고, 이미지 위생(S-22a/b)과 나머지
축은 그대로다. 완료 조건 8 은 **다시 절반**(저장소 텍스트를 보는 기존 축만 — `leakPatternGate`)이
되고 `OPEN-6C-IMAGE-VULN-SCAN` 이 다시 열린다. 비활성화만 원한다면 되돌리지 않고 `ci.yml` 의 S-22d·S-22e 두 step 만 빼도 된다 —
그 경우 SBOM 보관(S-22f)이 올릴 파일이 없어 `if-no-files-found: error` 로 붉어지므로 셋을 함께 뺀다.

## ①~⑥ 실측 (버릴 임시 clone, HEAD `cf608c9a`)

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

**목록 등식도 기계로 확인했다 — 다만 병합 뒤로 「범위 diff」를 쓸 수 없다.** `2deb5f9d..HEAD` 의 diff 는
이제 **6E-2a 가 병합으로 들여온 경로 전부**를 담는다(내 것이 아니다). 그래서 이 slice 의 경로 집합을
**내 쪽 비-병합 커밋에서** 뽑는다:

```
git log --no-merges --format=%h 2deb5f9d..HEAD ^origin/main \
  | while read -r c; do git diff-tree --no-commit-id --name-only -r "$c"; done \
  | sort -u | grep -v '^reports/evidence'
```

그 집합을 **「단일 restore 다섯 ∪ hunk 처리 공유 파일」**과 `comm -23`·`comm -13` 로 양방향 대조한다. 「문서에 빠진 경로」와
「문서에만 있는 경로」를 **둘 다** 본다. 대조 집합이 복원 인자만이 아닌 것이 중요하다 — `milestone-6.md`
는 되돌림 대상이면서 복원 목록에 없으므로, 복원 인자만 대조하면 **처리돼 있는데도 stale 로 읽힌다.**

**빈 출력은 목록이 건전하다는 증거가 아니다.** 깨짐이 측정 **뒤에** 오는 경로가 있으면 목록은 이미
낡았고 아직 읽히지 않았을 뿐이다. 그래서 등식을 낼 때 함께 묻는다 — **「내가 아무것도 커밋하지 않아도
이 집합에 경로가 들어올 수 있는가?」** 이 slice 의 답은 **예**였다(`milestone-6.md` 는 in_scope 인데
팀장이 쓴다). 그래서 그 경로의 처리를 위에 **미리** 적어 두었다.

⑥ 은 **evidence 네 문서가 전부 들어 있는 트리**에서 돌았다. 마지막 산출물 커밋이 evidence 커밋보다
뒤에 왔기 때문인데(사유 문면 정정이 `config/` 와 evidence 를 함께 만졌다), 덕분에 되돌린 트리에서
`leakPatternGate` 가 이 slice 의 evidence 를 실제로 훑고 통과했다 — 「되돌리지 않기로 한 evidence 가
base 의 baseline 과 어긋나 게이트를 붉히는가」(M4 에서 세 라운드 동안 거짓으로 적혔던 축)를 **이번에는
갈음이 아니라 직접** 쟀다는 뜻이다.

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 오므로 둘은 영원히 다르다).
**그 사이에 되돌림 대상이 움직였는가**를 본다:

```
git diff --name-only cf608c9a..<판정 SHA> -- \
  .github/workflows/ci.yml config/quality/vuln-allowlist.properties \
  config/quality/vuln-policy.properties docker/ml-serving.Dockerfile \
  docs/runbook/m6-6e-operations.md tools/vuln-scan-check.sh
```

빈 출력이면 이 실측이 유효하다. 한 줄이라도 나오면 미검증이다.
