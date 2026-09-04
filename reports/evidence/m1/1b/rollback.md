# rollback — M1 / 1B · Money/Rate/Basis

**정본 절차**(`evidence-pack` SKILL, 2026-09-04 개정): 되돌림은 range revert 가 아니라
in_scope 경로 한정이다.

**verifier r2 H-4 정정 — 경로를 더 이상 손으로 나열하지 않는다.** verifier r1 H-2 가
지적한 5/10 누락을 고쳤으나(10경로 전건 나열), 그 뒤 커밋(`617b9f3`, decision 15)이
`scope.md` `in_scope` 에 `milestone-1.md` 를 더해 열하나로 늘렸는데 이 파일은 갱신되지
않았다 — **손으로 복사한 목록은 원본이 자라면 반드시 다시 낡는다.** 이 판은 복사를
아예 없앤다: 아래 명령은 `scope.md` 의 `in_scope:` 블록을 **실행 시점에 파싱해서** 경로
배열을 만들고, 그 배열로 `git restore` 를 돈다 — `scope.md` 가 넓어지면 이 문서를 고칠
필요 없이 다음 실행이 자동으로 새 경로를 포함한다.

**주의 — 파생에 쓰는 값은 restore 전에 전부 캡처한다.** `scope.md` 자신이
`reports/evidence/m1/1b/**`(in_scope 원소)에 속해 base 에 없으므로, restore 를 실행하면
`scope.md` **가 작업 트리에서 사라진다**(실측으로 발견 — 처음 판은 restore 뒤에 `scope.md`
를 다시 읽어 확인 지점 2 를 돌리려다 `awk: can't open file`로 죽었다). 그래서 경로 배열과
그 개수의 독립 대조값을 **restore 이전**, 같은 스크립트 안에서 함께 잡는다.

```bash
# in_scope 배열을 scope.md 에서 파생한다(경로를 손으로 적지 않는다) — 변수 확장 함정
# (evidence-pack 2026-09-02 행 — 문자열 하나에 담으면 pathspec 하나로 접힌다)을 피해
# 배열로 받는다. `mapfile`/`readarray`(bash 4+)는 쓰지 않는다 — 이 하네스가 도는
# macOS 기본 `/bin/bash` 가 3.2(라이선스상 4 미탑재)라 그 내장 명령이 없다(실측:
# `bash: mapfile: command not found`) — while-read 루프는 bash 3.2 에서도 된다.
#
# 한 줄에 여러 경로가 「·」로 묶일 수 있다(out_of_scope 가 이미 쓰는 1A 식
# "a · b · c" 관례) — in_scope 에는 지금 그런 줄이 없지만, 나중에 생기면 awk 가
# 그 줄 전체를 문자열 하나로 뽑아 깨진 pathspec 하나를 조용히 만든다(verifier r3
# L-11 — git diff --stat 은 매치 0인 pathspec 을 에러 없이 그냥 건너뛴다, 실측
# 확인). 그래서 awk 가 뽑은 줄마다 「·」로 다시 나눠 항목마다 배열 원소로 넣는다.
IN_SCOPE_PATHS=()
while IFS= read -r line; do
  IFS='·' read -ra PARTS <<< "$line"
  for part in "${PARTS[@]}"; do
    trimmed="$(echo "$part" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
    [ -n "$trimmed" ] && IN_SCOPE_PATHS+=("$trimmed")
  done
done < <(
  awk '/^in_scope:/{f=1;next} /^out_of_scope:/{f=0} f && /^  - /{sub(/^  - /,""); sub(/[ \t]*#.*/,""); print}' \
    reports/evidence/m1/1b/scope.md
)

# 확인 지점 2 가 쓸 독립 대조값 — awk 파싱과 다른 방법(단순 grep 카운트, 「·」 분리 전
# 원본 줄 수)으로 같은 블록을 세어 파생 스크립트 자체의 버그(연속 주석 줄을 잘못 집는
# 등)를 교차 확인한다. restore 뒤에는 scope.md 가 사라지므로 반드시 여기서, restore
# 전에 잡는다. **주의 — 이 값은 "원본 in_scope 줄 수"이지 "파생 경로 수"가 아니다**
# (한 줄이 「·」로 여럿을 낼 수 있어 다를 수 있다, r3 L-11) — 확인 지점 2 는 그래서
# 줄 수만 비교하고, 파생 경로 각각의 유효성은 별도 확인 지점 5(아래)가 잰다.
INDEPENDENT_COUNT=$(
  awk '/^in_scope:/{f=1} /^out_of_scope:/{f=0;exit} f' reports/evidence/m1/1b/scope.md \
    | grep -c '^  - '
)

# 확인 지점 5 — 파생된 pathspec 각각이 실재 경로에 매치되는지(r3 L-11). 확인 지점 2
# 는 「개수」만 보므로 "a · b · c" 처럼 내용이 깨진 채 개수만 맞는 경우를 못 잡는다 —
# git ls-files 로 각 pathspec 이 최소 한 파일에 매치하는지 restore **전에** 확인한다.
for path in "${IN_SCOPE_PATHS[@]}"; do
  if [ -z "$(git ls-files -- "$path")" ]; then
    echo "불일치 — pathspec '$path' 가 실재 경로에 매치되지 않는다(파생이 깨졌다)" >&2
    exit 1
  fi
done

git restore --source=66c1ab79af4c5a68145811a9e87008dfdb10da3c --staged --worktree -- "${IN_SCOPE_PATHS[@]}"
```

`--source` 에 없는 경로(base 에 없던 신규 파일)는 이 명령으로 삭제된다 — 별도 `git rm` 이
필요 없다(`scope.md` 자신이 그 예다). `git checkout <base> -- <경로>` 는 쓰지 않는다(base
에 없는 경로마다 pathspec 오류로 exit 1, `evidence-pack` SKILL 인용: Codex 1A 16차 high).

**`docs/discovery/data-dictionary.md`·`docs/adr/0002-money-rate-basis.md`·
`docs/adr/0007-test-pyramid-and-ratchet.md`·`docs/discovery/capability-map.md`·
`milestone-1.md` 는 승인 문서다.** 이 명령이 되돌리는 것은 **1B가 그 문서에 넣은 편집분**
(운영자 결정 열여섯 건 등재 + 후속 정정)이고, 그 편집 자체가 `scope.md` in_scope 에
「해당 절만」로 한정돼 있다 — 문서 전체가 base 이전 상태로 없어지는 것이 아니라 **base
시점의 그 문서 상태**로 돌아간다(그 문서가 1B 이전에 이미 있었으므로 삭제가 아니라
복원이다).

**`fixtures/manifest.yaml` 은 fixture-curator 레인 소유다.** 이 명령이 그 경로를
포함하는 이유는 `scope.md` in_scope 가 「경로만 조건부 등재」로 그 파일을 이미 들고
있어서다 — 레인 소유와 in_scope 여부는 다른 축이다. `fixtures.md` §6(그 레인이 쓴
rollback)이 **같은 경로를 독립적으로도** 되돌리므로 두 문서의 절차가 서로 다른 결론을
내면 안 된다 — 「확인 지점」 4가 그 일치를 확인한다.

하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서의 하네스 레인 편집은 **되돌리지 않는다**
— `scope.md` `in_scope:` 블록에 그 경로들이 없으므로 위 파생 배열도 자동으로 그것들을
빼놓는다(파생이 곧 그 배제를 구조적으로 보장한다).

## 확인 지점

되돌린 뒤 **다섯 다** 확인한다 — 1만으로는 부족하다(r1 H-2 가 지적한 공허 통과가 바로 이
지점이고, r2 H-4 는 그 공허 통과를 막으려던 확인 지점 2 자체가 낡은 손 목록에 기대고
있었다는 재발이었다 — 그래서 그 판은 확인 지점 2 도 파생식으로 바꾸고, 그 파생값을
restore **전에** 캡처해 위 스크립트 안에 넣었다. r3 L-11 은 확인 지점 2 가 「개수」만
보고 「내용」은 안 봐 `·` 로 묶인 줄 하나가 깨진 pathspec 하나로 뭉쳐도 개수가 맞아
통과할 수 있음을 지적했다 — 그래서 확인 지점 5 를 새로 더했다).

1. **파생 배열의 경로 전건에서 diff가 빈다**(위 `IN_SCOPE_PATHS` 를 같은 세션에서 그대로 씀):
   ```bash
   git diff 66c1ab79af4c5a68145811a9e87008dfdb10da3c --stat -- "${IN_SCOPE_PATHS[@]}"
   ```
2. **파생 목록 수 = in_scope 항목 수 — 명령으로 비교한다(하드코딩 금지, r2 H-4 지적).**
   위에서 restore 전에 잡은 `INDEPENDENT_COUNT`(원본 in_scope **줄** 수)와 `IN_SCOPE_PATHS`
   배열 길이를 비교한다. **주의(r3 L-11)** — 이 둘은 「·」로 묶인 줄이 있으면 원래 다를 수
   있어야 정상이다(줄 하나가 경로 여럿을 낸다) — 지금 in_scope 에는 그런 줄이 0건이라
   같은 값이 나온다. 형식이 바뀌면 이 비교값도 같이 바뀐다는 뜻이지 항상 같아야 한다는
   뜻이 아니다:
   ```bash
   [ "${#IN_SCOPE_PATHS[@]}" -eq "$INDEPENDENT_COUNT" ] && echo "일치" || echo "불일치(줄에 「·」 묶음이 있으면 정상 — 5번이 내용을 잰다)"
   ```
3. **하네스 경로는 그대로다**: `git diff HEAD -- CLAUDE.md .claude/` 가 비어 있다.
4. **`fixtures.md` §6과 결론이 같다**: 그 문서의 `git restore --source=<이 slice의 최신
   SHA> --staged --worktree -- fixtures/manifest.yaml`을 이 문서의 명령과 **같은 clone에서
   순서 무관하게** 실행해도 `fixtures/manifest.yaml`의 최종 상태(= base)가 같다 — 두 절차가
   같은 파일을 다른 목표로 되돌리지 않는다.
5. **파생된 pathspec 각각이 실재 경로에 매치된다(r3 L-11 신설).** 위 스크립트가 이미
   restore 전에 이 확인을 강제로 돌린다(매치 0인 pathspec 이 있으면 그 자리에서 exit 1) —
   독립 재확인은 같은 루프를 다시 돌려도 되고, `git status --porcelain` 이 낸 변경 파일
   수가 「명백히 너무 적음」(pathspec 일부가 조용히 빠졌다는 신호)이 아닌지 눈으로도 본다.

## 실측 (verifier r2 H-4 뒤 재측정, 임시 clone, HEAD `6f0a507`)

`git clone --no-hardlinks` 으로 만든 임시 clone에서 위 스크립트 전문을 **실제로** 실행했다
— 결과는 `commands.md`가 아니라 이 자리에 남긴다. **첫 시도가 실패해 스크립트를 고쳤다**
(과정도 정직하게 남긴다) — restore 뒤 `scope.md` 를 다시 읽어 `INDEPENDENT_COUNT` 를 구하려
했더니 `awk: can't open file reports/evidence/m1/1b/scope.md` 로 죽었다(그 파일이
`reports/evidence/m1/1b/**` 에 속해 restore 로 지워졌기 때문). `INDEPENDENT_COUNT` 캡처를
restore **앞**으로 옮긴 뒤 재실행해 아래 결과를 얻었다.

- while-read 루프로 `IN_SCOPE_PATHS` 를 채움 → **11개 원소**
  (`shared-kernel/**`·`config/quality/architecture-policy.properties`·
  `config/quality/member-effects.properties`·`fixtures/manifest.yaml`·
  `docs/discovery/data-dictionary.md`·`docs/adr/0002-money-rate-basis.md`·
  `docs/adr/0007-test-pyramid-and-ratchet.md`·`docs/discovery/capability-map.md`·
  `config/quality/gate-tests.properties`·`milestone-1.md`·`reports/evidence/m1/1b/**`)
- `INDEPENDENT_COUNT` = **11**(restore 전에 캡처)
- `git restore --source=66c1ab7 --staged --worktree -- "${IN_SCOPE_PATHS[@]}"` → **exit 0**
- 확인 지점 1: `git diff 66c1ab7 --stat -- "${IN_SCOPE_PATHS[@]}"` → **비어 있음**(0줄 —
  `milestone-1.md` 의 +26줄이 이번엔 파생 목록에 자동으로 포함돼 남지 않는다)
- 확인 지점 2: `IN_SCOPE_PATHS` 길이 **11** = `INDEPENDENT_COUNT` **11** → **일치**
- 확인 지점 3: `git diff HEAD -- CLAUDE.md .claude/` → **비어 있음**
- 확인 지점 4: 같은 clone에서 이 문서의 명령을 먼저 실행한 뒤 `fixtures.md` §6의 명령
  (`git restore --source=9a36aa8 --staged --worktree -- fixtures/manifest.yaml`)을 이어
  실행 — `fixtures/manifest.yaml`의 SHA-256이 **두 순서 모두 동일**했다(H-2 라운드 실측과
  같은 결론 — `93be34a` 이후 구현 레인 커밋 어느 것도 그 경로를 편집하지 않았다)
- `git status --porcelain`(HEAD 대비)이 낸 변경 종류(실행 시점 기록, 슬라이스가 자라면
  낡는다 — 재확인은 위 확인 지점 1·2 명령으로 한다): `A` 1(`ModuleBoundaryAnchor.kt` 복원) ·
  `D` 46(1B·curator 가 새로 만든 파일이 base 에 없어 삭제 — `scope.md` 자신도 여기 포함) ·
  `M` 8(승인 문서 다섯 + `config/quality` 셋이 base 버전으로 되돌아감)

## 실측 (verifier r3 L-11 뒤 재측정, 임시 clone, HEAD `4ef0a4f`)

위 스크립트 전문(`·` 분리 + 확인 지점 5 포함)을 임시 clone에서 다시 실행했다.

- `IN_SCOPE_PATHS` 배열 길이 **11**, `INDEPENDENT_COUNT` **11** → 일치(지금 in_scope 에
  `·` 로 묶인 줄이 없어 r2 실측과 같은 값)
- 확인 지점 5(신설): 11개 pathspec 전부 `git ls-files` 매치 ≥ 1 → **전부 통과**, `exit 1`
  없이 restore 로 진행
- `git restore --source=66c1ab7 --staged --worktree -- "${IN_SCOPE_PATHS[@]}"` → **exit 0**
- 확인 지점 1: 빈 출력. 확인 지점 2: 일치. 확인 지점 3: 빈 출력 — r2 실측과 동일

**`·` 분리가 실제로 동작하는지 별도 clone에서 재현했다**(verifier r3 가 H-4 를 검증하며 쓴
것과 같은 방법 — in_scope 를 실제로 늘려서 파생이 따라오는지 본다). `scope.md` 의
`reports/evidence/m1/1b/**` 항목 뒤에 `  - README.md · CLAUDE.md   # 재현용` 한 줄을
**임시 clone 안에서만** 추가하고 파생 스크립트를 다시 돌렸다 — 배열이 **13개** 원소로
늘고, 새 줄은 하나의 문자열(`"README.md · CLAUDE.md"`)이 아니라 `README.md`·`CLAUDE.md`
**두 개의 개별 원소**로 갈라져 들어갔다(둘 다 `git ls-files` 매치 확인). `·` 분리 전
awk 만 썼다면 이 줄이 통짜 문자열 하나(`"README.md · CLAUDE.md"`)로 들어갔을 것이다 —
그런 문자열은 어떤 실재 파일과도 매치되지 않으므로(실측: `git restore -- "totally-bogus-…"`
류의 임의 미매치 pathspec은 `error: pathspec … did not match any file(s)`로 전체
명령을 죽인다), 분리 없이 그런 줄이 in_scope 에 들어오면 restore 전체가 실패했을 것이다.
분리가 그 실패를 원천적으로 없앤다.

## 되돌리는 flag/route/writer

없음 — 1B 는 신규 wiring(feature flag, route, writer)을 만들지 않는다. `shared-kernel` 은
순수 도메인 값 타입이고 그 자체가 어디서도 자동 호출되지 않는다(다른 모듈이 아직 참조하지
않는다). 되돌림은 파일 되돌리기로 완결되며 별도 비활성화 스위치가 필요 없다.

## 예상 복구 시간

수 초 — 위 while-read 루프 + `git restore` 명령 둘이 in_scope 전체를 되돌린다. DB write·외부
API 호출이 없으므로 후속 정리 단계가 없다.
