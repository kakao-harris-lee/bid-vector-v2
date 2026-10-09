# M6/6E-2b — rollback

실측 HEAD: `38fe42bb`(승인 전 일괄 — **되돌림 대상을 만진 마지막 커밋**이고, 팀장의
`milestone-6.md` 종결 문단 뒤다). 마지막 **산출물** 커밋은 `1bdd7b79` 이고, `cf608c9a` 는 evidence
전용 커밋이라 **되돌림 대상 여섯의 내용이 둘에서 같다** — 그래서 뒤쪽에서 재고 그 SHA 를 적는다(clone 이
evidence 까지 담아 ⑥ 의 `leakPatternGate` 가 이 slice 의 문서를 실제로 훑는다).
앞 라운드 실측은 **옮기지 않고 매번 다시 낸다** — 되돌림 대상이 한 줄이라도 움직이면 아래 verifier
술어가 그것을 미검증으로 판정한다(실제로 r0 에서 한 번 그렇게 됐다).

되돌림은 **range revert 가 아니라 in_scope 경로 한정**이다 — `git revert <base>..HEAD` 는 같은 range 에
있는 팀장 레인의 계약 갱신 커밋(`scope.md`)까지 되돌린다.

## 되돌리는 것 / 되돌리지 않는 것

목록은 손으로 쓰지 않고 기계적으로 낸다 — **다만 병합 뒤로 `git diff --name-status 2deb5f9d..HEAD` 는
쓸 수 없다**(PR #68 /code-review). 그 범위는 병합이 들여온 6E-2a 경로를 전부 담는다. 이 slice 의 경로는
**내 쪽 비-병합 커밋**에서 낸다 — `git log --no-merges --format=%h 2deb5f9d..HEAD ^b6d31b87` 로 커밋을
고르고 각 커밋의 `git diff-tree --no-commit-id --name-only -r` 를 모은다(아래 「등식」 절의 명령).
A/M 구분이 필요하면 그 집합을 base 와 대조한다
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

### 공유 파일 절차 — runbook · `milestone-6.md`

**복원 원천은 움직이는 ref 가 아니라 고정 SHA 다** — 이 브랜치에 **마지막으로 병합한 main 커밋**
`b6d31b87`(PR #66, 6E-2a 종결). 앞 판은 `origin/main` 을 썼고, 그것이 LR-1 이었다.

```
PIN=b6d31b87                      # 이 브랜치에 마지막으로 병합한 main 커밋
FIRST=e2545ea5                    # 이 slice 의 **첫** 산출물 커밋

git merge-base --is-ancestor "$PIN" HEAD   || { echo "핀이 이 브랜치의 조상이 아니다"; exit 1; }
git merge-base --is-ancestor "$FIRST" "$PIN" && { echo "핀이 이 slice 를 담는다 — 복원이 아무것도 지우지 않는다"; exit 1; }

git restore --source="$PIN" --staged --worktree -- \
  docs/runbook/m6-6e-operations.md milestone-6.md
```

**왜 고정 SHA 인가 — 움직이는 ref 는 두 방향으로 틀린다.**
- **낡은 쪽**: fetch 하지 않았거나 PR #66 이전에 받은 clone 의 `origin/main` 은 6E-2a 를 담지 않는다.
  그 ref 로 복원하면 **exit 0 인 채 6E-2a 의 줄(머리말·§2.6·§6 17~19·§7)을 지운다**(verifier 실측 A).
  앞 판 문면은 전제를 「main 에 6E-2b 가 없다」 **하나만** 적었다 — 짝인 「6E-2a 를 담는다」가 없었다.
- **앞선 쪽**: 그 사이 다른 slice 가 main 에 머지됐으면 그 slice 의 **문서 줄만 코드 없이 들여온다**
  (verifier 실측 B). 앞 판은 이것을 「보존된다(맞는 거동)」고 적었는데, 보존이 아니라 **유입**이다.

고정 SHA 는 **둘을 함께 닫는다**. 핀이 가리키는 트리는 변하지 않으므로 덜 담지도, 더 담지도 않는다.

**단언 둘을 복원 **앞**에 둔다**(명령이 성공하면서 뜻을 잃는 경우를 막는다) —
1. `PIN` 이 이 브랜치의 조상이다 → 그 트리가 실제로 이 이력의 일부다.
2. `PIN` 이 이 slice 의 **첫** 산출물 커밋을 담지 않는다 → 복원이 실제로 이 slice 를 걷어낸다.
   **첫** 커밋으로 재는 것이 요점이다. 늦은 커밋으로 재면 앞 커밋들을 담은 핀도 통과한다(실측 확인).

**한계는 그대로다**: 이 절차는 「핀이 이 slice 를 담지 않는다」에 기댄다. 내 PR 이 머지된 뒤의 되돌림은
이 절차가 아니라 **그 PR 의 revert** 다. 그때는 단언 2 가 **실패해서** 그 사실을 알려 준다 — 조용히
아무것도 안 지우는 대신.

확인은 **둘 다**: 내 줄이 사라졌는가(`6E-2b`·`OPEN-6E2B`·`^## 8\.` 가 0), **남의 줄이 남았는가**
(6E-2a 표지 — §2.6 풀 상수표 · §6 항목 17~19 · §7 「고치는 절에 맞는 블록」 문단).

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

## ①~⑥ 실측 (버릴 임시 clone, HEAD `38fe42bb`)

| # | 무엇 | 결과 |
|---|---|---|
| 단언 1 | 핀(`b6d31b87`)이 이 브랜치의 조상 | **통과** |
| 단언 2 | 핀이 이 slice 의 첫 산출물 커밋(`e2545ea5`)을 담지 않음 | **통과** |
| ① | 단일 restore 다섯 | exit 0 |
| ①b | 공유 둘을 핀 판으로 | exit 0 |
| ② | D/M 수 | **D 3 · M 4** |
| ③ | `git diff <base> -- <restore 다섯>` | **0 줄** |
| ③e | runbook 의 이 slice 표지 | **0건** |
| ③e2 | runbook 의 §8 | **0건** |
| ③f | runbook 의 6E-2a 표지 | **4건** — 보존 |
| ③g | runbook 이 핀 판과 바이트 동일 | **동일** |
| ③e3 | `milestone-6.md` 의 **내 문단** 표지(`**6E-2b 착수·종결`) | **0건** |
| ③f2 | `milestone-6.md` 의 6E-2a 문단 표지 | **1건** — 보존 |
| ③g2 | `milestone-6.md` 가 핀 판과 바이트 동일 | **동일** |
| ③b | 되돌리지 않기로 한 evidence | 네 문서 전부 남음 |
| ③c | 하네스 경로 porcelain | **0 줄** |
| ③d | 되돌린 `ci.yml` 의 게이트 흔적 | **0건** |
| ④ | compile | exit 0 |
| ⑤⑥ | `check` 전건 | exit 0 |

**등식은 이제 빈 출력이다** — 팀장이 `milestone-6.md` 에 문단을 쓰면서 그 경로가 내 쪽 커밋 집합에
들어왔고, 복원 목록(공유 둘)과 맞는다.

**표지를 좁혀야 했다(PR #68 조치 중 실측).** `6E-2b` 는 느슨하다 — **6E-2a 의 종결 문단이 6E-2 분할을
설명하며 내 slice 이름을 부른다**(핀에도 1건). 그 수를 0 으로 기대하면 **올바른 되돌림이 실패로 읽힌다**
(실제로 그렇게 읽혔다). 내 문단만 갖는 제목(`**6E-2b 착수·종결`)으로 바꾸고, 정확한 축은 **바이트
동일성**(③g·③g2)으로 뒀다. 표지는 「내 것」을 가리켜야지 「내 이름이 나오는 곳」을 가리키면 안 된다.

## verifier 가 대조할 것

「실측 HEAD == 판정 SHA」가 아니다(evidence 커밋은 언제나 뒤에 오므로 둘은 영원히 다르다).
**그 사이에 되돌림 대상이 움직였는가**를 본다 — 대상은 **일곱**이다(단일 restore 다섯 + 공유 둘):

```
git diff --name-only <실측 HEAD>..<판정 SHA> -- \
  .github/workflows/ci.yml config/quality/vuln-allowlist.properties \
  config/quality/vuln-policy.properties docker/ml-serving.Dockerfile \
  tools/vuln-scan-check.sh \
  docs/runbook/m6-6e-operations.md milestone-6.md
```

빈 출력이면 이 실측이 유효하다. 한 줄이라도 나오면 미검증이다.
