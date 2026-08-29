# M0 / 0C — 검증 명령과 출력

이 파일은 **명령과 그 stdout**을 담고, 각 블록에 **그 출력을 읽는 데 필요한 최소한의
범위·한계 각주**를 붙인다. **acceptance 대조는 `checklist.md`, 라운드 이력은 `scope.md`**에
있다 — 이 파일은 그 둘을 담지 않는다. **명령이 내는 셈을 산문에 옮겨 적지 않는다.**

**각 블록은 자기 실행 시점의 SHA를 선언한다.** 커밋은 자기 SHA를 담을 수 없으므로
**C-1 ~ C-9의 블록은 `09d5840`**(독립 검증 finding V-1·V-2·V-3을 닫은 마지막 커밋)의 트리를 선언한다 —
그 SHA를 체크아웃한 worktree에서 재현된다.

**C-10만 예외다** — 그것은 다른 블록들이 실제로 그 트리에서 축어 재현되는지를 재는
검사이고, **대상 파일이 SHA가 아니라 「이 파일 자신」**이다. 그 절 머리가 **이 검사의
형태를 선언하는 유일한 자리**이며, 다른 파일은 그것을 **가리키기만 한다.**

**출력은 명령의 stdout만 싣는다.** 로그인 프로파일 잡음이 stdout에 섞이면 재현이
깨지므로 `bash -c`로 돌린다.

**`ed4b06c`를 여는 블록(C-7·C-9)은 `bid-vector` symlink가 있어야 재현되고,
`_workspace`를 여는 블록(C-1)은 그 디렉터리가 있어야 재현된다.** 둘 다 git이 추적하지
않으므로 **SHA에서 복원할 수 없고, 그 두 경로가 없는 worktree에서는 재현되지 않는다.**
**C-10도 그 제약을 벗어나지 못한다** — C-10은 그 두 경로를 **실행 CWD에서** 가져다
연결하므로, CWD에 없으면 C-10 자신이 잴 수 없는 블록이 생긴다. **C-10이 두 환경에서
각각 무엇을 내는지는 그 절이 실행으로 낸다.**

---

## C-1 · 운영자 결정 사본과 원본의 차이

선언 SHA `09d5840`. 원본은 `_workspace/`(gitignore 대상)라 이 블록은 그 디렉터리가
있는 작업 트리에서만 재현된다.

**사본은 이제 원본과 한 자리에서 다르다** — Codex 리뷰 finding **E**가 U-3의 소유 `OPEN`
오기를 지적했고, **결정 기록이므로 원 표기를 지우지 않고 취소선과 ⚠ 정정으로** 남겼다.
**그 차이가 무엇인지는 아래 diff가 그대로 낸다** — 산문으로 옮겨 적지 않는다.

```
# diff 가 낸 줄을 그대로 실으면 이 파일이 후행 공백을 갖는다 — 표지로 바꿔 싣는다.
diff <(sed -n '281,564p' _workspace/m0-open-decisions/decisions-log.md) \
     <(sed -n '17,$p' reports/evidence/m0/0c/decisions-2026-08-28.md) \
  | sed 's/[[:space:]]\{1,\}$/<후행공백>/'
echo "exit=${PIPESTATUS[0]}"
```

```
100c100
< | **U-3** | **`TenderOutcome` aggregate — current는 event stream의 fold 결과** | `OPEN-SET-05` |
---
> | **U-3** | **`TenderOutcome` aggregate — current는 event stream의 fold 결과** | ~~`OPEN-SET-05`~~ → **`OPEN-SET-04`** ⚠ 아래 정정 |
113a114,120
><후행공백>
> > **⚠ 정정 (2026-08-29, Codex 리뷰 finding E)** — 위 표의 U-3 행이 소유 `OPEN`을
> > `OPEN-SET-05`로 적었으나 **`capability-map.md` §12에서 그 id는 「재공고(차수 다수) 대사
> > 대상 선택 규칙」**이고 U-3와 무관하다. U-3가 걸리는 것은 **`OPEN-SET-04`(이벤트 재관측
> > 횟수를 운영자에게 노출할지)**이며 **아래 U-3 본문도 `OPEN-SET-04`만 관련 쟁점으로
> > 설명한다.** 원 표기는 지우지 않고 취소선으로 남긴다 — **이 파일은 결정 기록이다.**
> > **`OPEN-SET-05`는 활성으로 유지된다.**
exit=1
```

---

## C-2 · `in_scope` 밖 경로 변경과 공백 오류

**0D가 병행했으므로 `base_sha..HEAD`에는 0D의 커밋이 있다.** 그래서 이 검사는
**이 slice의 커밋을 먼저 고르고, 그 커밋이 건드린 경로 전부**를 본다 — 혼합 커밋이
있으면 여기서 드러난다.

### C-2.1 이 slice의 커밋과 그 커밋이 건드린 경로

선언 SHA `09d5840`.

```
for c in $(git log --format='%H' 2b05684..09d5840 \
             -- docs/discovery/data-dictionary.md reports/evidence/m0/0c/); do
  echo "-- $(git log --format='%h %s' -1 $c)"
  git show --name-only --format='' $c | sed '/^$/d' | sed 's/^/   /'
done
echo "### in_scope 밖 경로 (아래 줄이 '(없음)'이면 통과)"
for c in $(git log --format='%H' 2b05684..09d5840 \
             -- docs/discovery/data-dictionary.md reports/evidence/m0/0c/); do
  git show --name-only --format='' $c
done | sed '/^$/d' | sort -u \
     | grep -v -E '^(docs/discovery/data-dictionary\.md|reports/evidence/m0/0c/)' \
     || echo "(없음)"
```

```
-- 09d5840 fix(m0-0c): C-8 각주의 갈래 열거를 다시 연다 (V-3 low)
   reports/evidence/m0/0c/commands.md
-- 9c32711 fix(m0-0c): 같은 사실에 두 수를 두던 것을 정본 하나로 모은다 (V-2 low)
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 414c413 fix(m0-0c): 가림이 낱말만 덮어 값이 인쇄되던 것을 값 자리까지 덮게 한다 (V-1 high)
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 1e0a837 docs(m0-0c): scope.md 에 수정 라운드 8 을 등재한다
   reports/evidence/m0/0c/scope.md
-- b199fe8 docs(m0-0c): 출력 블록 전부를 선언 SHA c36dd99 트리에서 다시 뜨고 head_sha 를 함께 옮긴다
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- c36dd99 docs(m0-0c): differential.json·golden-manifest.json 을 N/A + 사유로 적는다 (OG-4)
   reports/evidence/m0/0c/scope.md
-- 560767a fix(m0-0c): C-8 의 자기 인용 재귀를 끊고 심어 둔 가짜 값으로 능력을 잰다 (OG-2)
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 4b3606a fix(m0-0c): 표본이 「산 표본」이라 인쇄하면서 재지 않는 경로를 닫는다 (OG-1)
   reports/evidence/m0/0c/commands.md
-- 94dcf79 fix(m0-0c): 10eb819 행의 C-8 변경 사유를 한 자리가 온전히 적게 한다 (H-2)
   reports/evidence/m0/0c/scope.md
-- d6dbd92 fix(m0-0c): bd454c5 행이 low 하나를 「게이트 밖」으로 옮겨 적은 것을 되돌린다 (H-1)
   reports/evidence/m0/0c/scope.md
-- aa67a88 docs(m0-0c): 라운드 7 이력 커밋 4137860 의 행을 세운다 (규약)
   reports/evidence/m0/0c/scope.md
-- 4137860 docs(m0-0c): scope.md 에 수정 라운드 7 을 등재한다
   reports/evidence/m0/0c/scope.md
-- 10eb819 docs(m0-0c): 출력 블록 전부를 선언 SHA 450cc8f 트리에서 다시 뜨고 head_sha 를 함께 옮긴다
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 450cc8f fix(m0-0c): C-10 의 trap 에서 git worktree prune 을 걷는다 — 공유 상태 side effect
   reports/evidence/m0/0c/commands.md
-- cdcc73a fix(m0-0c): 코드 주석의 앵커 관계 서술을 재는 자리로 돌린다 (G-1 전수 훑기)
   reports/evidence/m0/0c/commands.md
-- 2a20aad docs(m0-0c): 이력 표에 빠진 이력 커밋 두 행을 세운다 (G-4)
   reports/evidence/m0/0c/scope.md
-- 2a352e3 fix(m0-0c): head_sha 를 상대 술어에서 SHA 지목으로 바꾼다 — 선언 SHA 와 같은 값 (G-2·G-3)
   reports/evidence/m0/0c/scope.md
-- bd454c5 fix(m0-0c): 목록 증가·② 규칙·한정 이름 세 구멍을 같은 실행이 닫게 한다
   reports/evidence/m0/0c/commands.md
-- eb3ec86 fix(m0-0c): `어디에도 없음` 이 왜 FAIL 이 아닌지를 산출물에 적는다 (G-6)
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
-- e467341 fix(m0-0c): observed·digits 를 옳은 갈래 주석 아래로 옮긴다 (G-5)
   reports/evidence/m0/0c/commands.md
-- ecae979 fix(m0-0c): 두 앵커의 관계를 고정 표본으로 재고 정본을 한 자리로 모은다 (G-1)
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 938d9d0 docs(m0-0c): scope.md 에 수정 라운드 6 을 등재하고 head_sha → 159f69c
   reports/evidence/m0/0c/scope.md
-- 159f69c docs(m0-0c): 출력 블록 전부를 선언 SHA d7851ad 트리에서 다시 뜬다
   reports/evidence/m0/0c/commands.md
-- d7851ad fix(m0-0c): F-4 이분법의 다섯 번째 사본을 checklist.md A2 에서 걷는다 (F-4 전수 훑기)
   reports/evidence/m0/0c/checklist.md
-- be4d1a0 fix(m0-0c): §12.2 estimateMargin 행이 분기 단서를 행 안에 싣는다 (F-6)
   docs/discovery/data-dictionary.md
-- 98997a3 fix(m0-0c): ③의 이름 규칙이 못 보는 갈래를 규칙을 넓혀 재고 블록이 낸다 (F-5)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/commands.md
-- 20a72d1 fix(m0-0c): 대체 명제를 사전에 복제하지 않고 명령이 갈래를 세게 한다 (F-4)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/commands.md
-- 5f0870e fix(m0-0c): 라벨이 세는 것과 열거하는 것을 일치시킨다 (F-2)
   reports/evidence/m0/0c/commands.md
-- 2e70f86 fix(m0-0c): `이름 규칙 밖 인자` 가 stray 유무와 무관하게 이름째 낸다 (F-3)
   reports/evidence/m0/0c/commands.md
-- b469512 fix(m0-0c): 사각지대 앵커를 선언 앵커와 분리해 넓힌다 (F-1)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
-- 10a6bce docs(m0-0c): scope.md 에 수정 라운드 5 를 등재하고 head_sha → b0d8b42
   reports/evidence/m0/0c/scope.md
-- b0d8b42 docs(m0-0c): 출력 블록 전부를 선언 SHA 446a39f 트리에서 다시 뜬다
   reports/evidence/m0/0c/commands.md
-- 446a39f fix(m0-0c): 라운드 이력의 「이 커밋」 행을 커밋된 SHA 두 행으로 가른다 (V-3)
   reports/evidence/m0/0c/scope.md
-- 384c780 fix(m0-0c): §12 서술의 사본을 사전 §0.4 의 현재 문장에 맞춘다 (V-6)
   reports/evidence/m0/0c/checklist.md
-- 3e9feb2 fix(m0-0c): C-4.1 자기서술을 구현에 맞춘다 (V-5)
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
-- d75f406 fix(m0-0c): §12.2 의 「모든 필드」 전칭을 걷고 빠진 둘을 등재한다 (V-1)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/scope.md
-- 685fe08 fix(m0-0c): C-4.2 가 자기 사각지대를 실행으로 재게 한다 (V-2)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
-- 494b5b7 fix(m0-0c): margin 산식의 sample_size 분기를 적는다 (V-7)
   docs/discovery/data-dictionary.md
-- 33f05f5 fix(m0-0c): estimateMargin 정정의 legacy 좌표를 산식이 있는 파일로 옮긴다 (V-4)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/checklist.md
-- 03072ce docs(m0-0c): scope.md 에 Codex 수정 라운드 1 을 등재하고 head_sha 를 갱신한다
   reports/evidence/m0/0c/scope.md
-- 295a6cf docs(m0-0c): 출력 블록 전부를 선언 SHA aa05167 트리에서 다시 뜬다
   reports/evidence/m0/0c/commands.md
-- aa05167 fix(m0-0c): C-1 이 후행 공백을 되싣지 않게 명령을 고친다 (A8)
   reports/evidence/m0/0c/commands.md
-- 87ad2d9 fix(m0-0c): 인용 블록 빈 줄의 후행 공백을 뗀다 (A8 git diff --check)
   reports/evidence/m0/0c/commands.md
-- c6db97e docs(m0-0c): 출력 블록 전부를 선언 SHA c0fe44f 트리에서 다시 뜬다
   reports/evidence/m0/0c/commands.md
-- c0fe44f fix(m0-0c): Codex finding A~E 를 고친다 — 필드 인덱스 신설, 승인 라벨 복원
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/codex-review-20260829T061613Z.json
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/decisions-2026-08-28.md
   reports/evidence/m0/0c/scope.md
-- 227ba0c docs(m0-0c): scope.md 이력에 9112236·d879b29 등재, head_sha → d879b29
   reports/evidence/m0/0c/scope.md
-- d879b29 fix(m0-0c): F-21 자리의 근거를 선언 SHA 교체에 낡지 않는 형태로 되돌린다
   reports/evidence/m0/0c/commands.md
-- 9112236 docs(m0-0c): scope.md 이력에 수정 라운드 4 를 등재하고 head_sha → 57dc5ae
   reports/evidence/m0/0c/scope.md
-- 57dc5ae docs(m0-0c): 출력 블록 전부를 선언 SHA 37c9905 트리에서 다시 뜬다
   reports/evidence/m0/0c/commands.md
-- 37c9905 fix(m0-0c): C-10 을 설명하는 자리를 전수로 찾아 현재 형태에 맞춘다 (F-19~F-22)
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 64aecc7 docs(m0-0c): scope.md 이력에 66b986d·993bd75 등재, head_sha → 993bd75
   reports/evidence/m0/0c/scope.md
-- 993bd75 fix(m0-0c): C-10.2 를 실행 가능한 명령으로 바꾸고 C-10 의 실행 위치 전제를 적는다
   reports/evidence/m0/0c/commands.md
-- 66b986d docs(m0-0c): scope.md 이력에 수정 라운드 3 커밋을 등재하고 head_sha → 642e2ab
   reports/evidence/m0/0c/scope.md
-- 642e2ab fix(m0-0c): 머리의 자기 선언을 실제 규칙으로 좁히고 산문의 셈을 걷는다 (F-15 전수 훑기)
   reports/evidence/m0/0c/commands.md
-- 9578308 docs(m0-0c): 출력 블록을 19c7e57 트리에서 다시 뜨고 C-10 을 자기 참조 없이 다시 짠다
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 19c7e57 fix(m0-0c): 시점·전칭·집합·셈을 실측에 맞춘다 (F-14~F-18)
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
-- b701d1b fix(m0-0c): C-10 의 능력을 두 환경에서 실행으로 재고 그 결과를 싣는다 (F-9)
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 9d8d206 fix(m0-0c): 출력 블록을 ba23629 트리에서 다시 뜨고 C-2.2 각주를 실제 지적에 맞춘다
   reports/evidence/m0/0c/commands.md
-- ba23629 fix(m0-0c): 자기 선언과 귀속을 실측에 맞추고 C-2.3(초록 출력)을 신설 (F-10~F-13)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 5a9a5f5 docs(m0-0c): C-10 을 새 두 SHA(989e2c1 · 5e2c67e)로 다시 재고 라운드 이력을 기입
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- 989e2c1 fix(m0-0c): C-2.2 가 후행 공백을 되싣지 않게 하고 출력 블록을 5e2c67e 트리에서 다시 뜬다
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
-- 5e2c67e fix(m0-0c): C-5 가 후행 공백을 내지 않게 한다 (A8 git diff --check)
   reports/evidence/m0/0c/commands.md
-- 6eba815 docs(m0-0c): C-10 신설 — 출력 블록의 축어 재현을 재고, 라운드 이력·head_sha 기입
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- c81c8a1 fix(m0-0c): 출력 블록 전부를 선언 SHA 1e9e670 트리에서 다시 뜬다 (F-1·F-3)
   reports/evidence/m0/0c/commands.md
-- 1e9e670 fix(m0-0c): C-5 를 문자 단위 절단으로 바꾸고 전수 주장을 명령의 실제 범위에 맞춘다
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/commands.md
-- cd1fbce fix(m0-0c): 정본 목록의 범위·귀속과 셈·인용을 실측에 맞춘다 (F-2·F-4~F-8)
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
   reports/evidence/m0/0c/scope.md
-- b833fee docs(m0-0c): scope.md 이력에 f852716·c3020e8 등재, head_sha → c3020e8
   reports/evidence/m0/0c/scope.md
-- c3020e8 fix(m0-0c): C-6.2 가 후행 공백을 내지 않게 한다 (A8 git diff --check)
   reports/evidence/m0/0c/commands.md
-- f852716 docs(m0-0c): scope.md 에 head_sha(6af4179)와 커밋 이력 기입
   reports/evidence/m0/0c/scope.md
-- 6af4179 docs(m0-0c): 검증 명령·출력(C-1~C-9)과 acceptance 대조(A1~A8)
   reports/evidence/m0/0c/checklist.md
   reports/evidence/m0/0c/commands.md
-- 8b938d6 docs(m0-0c): 도메인 명세·데이터 사전 6축 작성 + slice 계약
   docs/discovery/data-dictionary.md
   reports/evidence/m0/0c/decisions-2026-08-28.md
   reports/evidence/m0/0c/scope.md
### in_scope 밖 경로 (아래 줄이 '(없음)'이면 통과)
(없음)
```

**이 판정은 뒤 커밋에 낡지 않는다** — 뒤 커밋(`commands.md`·`checklist.md`·`scope.md`
갱신)의 경로가 전부 `reports/evidence/m0/0c/` 안이기 때문이다. 리뷰 시점의 HEAD로
다시 돌리려면 위 두 자리의 `09d5840`를 HEAD로 바꾼다.

### C-2.2 공백 오류

선언 SHA `09d5840`.

```
# 지적 줄을 그대로 실으면 이 파일이 다시 후행 공백을 갖는다 — 표지로 바꿔 싣는다.
git diff --check 2b05684..09d5840 | sed 's/[[:space:]]\{1,\}$/<후행공백>/'
echo "base_sha 기준 지적: $(git diff --check 2b05684..09d5840 | wc -l | tr -d ' ')"
echo "review_base 기준 지적: $(git diff --check aff62ab..09d5840 | wc -l | tr -d ' ')"
```

```
base_sha 기준 지적: 0
review_base 기준 지적: 0
```

> **이 블록은 커밋 이력만 읽는다** — 작업 트리의 상태에 의존하지 않으므로 `bid-vector`나
> `_workspace`가 없어도 재현된다. 리뷰 시점의 HEAD로 다시 재려면 세 자리의 선언 SHA를
> HEAD로 바꾼다.
>
> **이 검사가 지적을 낸 적이 있다.** 어느 커밋에서 무엇이 지적됐고 **어떻게 닫혔는지**는
> `scope.md`의 커밋 표에 있다 — 닫힌 방식은 한 가지가 아니었다. **이 블록은 선언 SHA
> 하나만 잰다.**

---

## C-3 · 6축 커버 (A1)

선언 SHA `09d5840`. 스크립트 본문은 인라인이다.

```
python3 - <<'PY'
import re, pathlib
axes = {
 "1":"용어","2":"aggregate","3":"rule의","4":"정책","5":"canonical","6":"ML"}
t = pathlib.Path("docs/discovery/data-dictionary.md").read_text()
heads = dict(re.findall(r"^## (\d+)\. (.+)$", t, re.M))
for n,key in axes.items():
    h = heads.get(n,"(없음)")
    print(f"축 {n}: {'덮음' if key in h else '미확인'} — {h}")
print("### 절 수(### 기준):", len(re.findall(r"^### \d+\.\d+", t, re.M)))
print("### 6축 절 전부 존재:", all(n in heads for n in axes))
PY
echo "exit=$?"
```

```
축 1: 덮음 — 축 1 — 용어 · 단위 · basis · nullable 의미
축 2: 덮음 — 축 2 — aggregate와 상태 전이
축 3: 덮음 — 축 3 — rule의 입력 / 출력 / reason code
축 4: 덮음 — 축 4 — 정책 version과 effective date
축 5: 덮음 — 축 5 — canonical KONEPS fact와 derived fact
축 6: 덮음 — 축 6 — ML feature와 업무 판단의 경계
### 절 수(### 기준): 47
### 6축 절 전부 존재: True
exit=0
```

---

## C-4 · 도메인 숫자 전수 (A2)

**숫자는 두 자리에 있다 — 리터럴과 필드.** `C-4.1`이 리터럴을, **`C-4.2`가 필드**를 본다.
**`C-4.1`만으로는 필드가 나르는 수를 볼 수 없다** — Codex 리뷰 finding **A**가 그것으로 났다.

### C-4.1 숫자 리터럴 — §12.1 밖에 정의 없는 리터럴이 없는가

사전 **§12 전 구간**(`## 12.`부터 `## 13.` 앞까지)의 **표 첫 칸**을 정본으로 삼고,
§12 밖 본문에서 그 목록에 없는 숫자 토큰을 찾는다 — **§12.1의 값 칸이 그 대부분이고
§12.2의 첫 칸도 들어온다.** **셈·좌표·식별자는 마스크로 뺀다** — 무엇을 뺐는지는
`MASKS`가 한 줄씩 밝힌다. 선언 SHA `09d5840`.

```
python3 - docs/discovery/data-dictionary.md <<'PY'
import re, sys, pathlib
DOC = sys.argv[1]
text = pathlib.Path(DOC).read_text()
lines = text.split("\n")
# §12 「숫자 인덱스」 구간 = 정본. 그 구간의 **표 첫 칸** 전부에서 허용 숫자를 모은다.
s = next(i for i,l in enumerate(lines) if l.startswith("## 12."))
e = next(i for i,l in enumerate(lines) if l.startswith("## 13."))
allowed = set()
for l in lines[s:e]:
    if not l.startswith("|"): continue
    cell = l.split("|")[1]
    for m in re.finditer(r"\d[\d,]*(?:\.\d+)?(?:e-?\d+)?", cell):
        allowed.add(m.group(0))
body = "\n".join(lines[:s] + lines[e:])
MASKS = [
    r"\d{4}-\d{2}-\d{2}",                                   # ISO 날짜
    r"(?:app|tests|scripts|docs)/[\w./-]+\.(?:py|md):\d+(?:-\d+)?",  # legacy 인용 행 범위
    r"reports/evidence/m0/0c/[\w.-]+",                      # evidence 경로
    r"§\d+(?:\.\d+)*",                                      # 절 참조
    r"^#{1,6}\s+\d+(?:\.\d+)*",                             # 머리 번호
    r"\b[A-Z][A-Za-z]*(?:-[A-Z]+)*-\d+(?:\.\d+)?[a-z]?(?!\d)",  # OPEN-DIC-01 · DEC-11 · X-2 · U-1b · ML-11.4 (뒤에 한글 조사가 붙어도 잡힌다)
    r"\b(?:S[1-4]|B[1-7]|C[1-3]|P[1-3]|A[1-8]|M[0-6]|F[0-9])(?![0-9A-Za-z])",  # 짧은 항목 id
    r"제\d+",                                               # 한국어 서수(제3 상태)
    r"(?:축|라운드) \d+",                                   # 축 3 · 라운드 5
    r"\b0[a-dA-D][0-9]?(?![0-9A-Za-z])",                    # slice id (0a2 · 0A3 · 0c · 0d)
    r"`[0-9a-f]{7,40}`",                                    # commit 해시
    r"#\d+",                                                # legacy 이슈 번호
    r"\bC-\d+(?:\.\d+)?(?!\d)",                             # commands.md 절
    r"^[ \t]*\d+\.[ \t]",                                   # 순서 있는 목록 표지([ \t]만 써서 줄바꿈을 먹지 않게 한다)
    r"\b\d{2}:\d{2}\b",                                     # 시각(주 경계)
]
masked = body
for p in MASKS:
    masked = re.sub(p, lambda m: " "*len(m.group(0)), masked, flags=re.M)
found = {}
for i, l in enumerate(masked.split("\n")):
    for m in re.finditer(r"\d[\d,]*(?:\.\d+)?(?:e-?\d+)?", l):
        tok = m.group(0)
        if tok in allowed: continue
        found.setdefault(tok, []).append(i+1)
print(f"문서: {DOC}")
print(f"§12 표 첫 칸이 허용하는 숫자 토큰: {len(allowed)}")
print(f"§12 밖 본문에서 §12에 없는 숫자 토큰: {len(found)}")
for tok in sorted(found):
    print(f"  {tok!r} @ 본문 행 {found[tok][:6]}")
print("PASS" if not found else "FAIL")
PY
echo "exit=$?"
```

```
문서: docs/discovery/data-dictionary.md
§12 표 첫 칸이 허용하는 숫자 토큰: 55
§12 밖 본문에서 §12에 없는 숫자 토큰: 0
PASS
exit=0
```

> **이 검사의 한계**: 마스크는 **한 자리 수(`0`~`9`)를 빼지 않는다** — `0`·`1`은 §12에
> 등재해 통과시킨다. 마스크가 잘못 넓으면 진짜 도메인 숫자를 놓칠 수 있으므로
> **MASKS 각 줄에 무엇을 빼는지 적는다.**


### C-4.2 타입이 나르는 필드 — §12.2가 뽑힌 이름을 전부 덮는가

**이름을 뽑는 자리는 셋이다.** ① **타입 선언의 인자 목록** — 인자 목록을 **균형 괄호**로
잘라 **중첩 괄호·제네릭**을 견디고, 백틱 span의 **블록인용 줄바꿈을 편다.** ② **머리 칸이
「필드」·「성분」인 표.** ③ **본문의 단독 백틱 이름.** **덮개는 「수인지 아닌지」를 묻지
않는다** — §12.2가 각 필드를 **수** 또는 **수가 아님**으로 분류하므로, 덮이지 않은 이름이
있으면 그것이 곧 미분류다.

**두 앵커의 관계에 대한 정본은 이 자리 하나다** — `checklist.md`·`scope.md`는 여기를
가리키기만 한다. 사각지대를 재는 앵커가 선언을 읽는 앵커와 같으면 **못 읽는 자리와 못
읽었다고 세는 자리가 같아 구조적으로 자기를 볼 수 없다** — 이 검사가 그렇게 지어져
`_FloorSchedule(effective_from, brackets)` 한 자리를 네 사각지대 줄 어디에도 내지 못했다.
그래서 사각지대 앵커를 **대문자를 전제하지 않는 형태**로 따로 세웠다.

**그 관계를 산문이 주장하지 않고 블록이 잰다. 재는 것은 둘이고 둘 다 깨지면 `FAIL`이다.**

- **㉠ 문서에 대해 — 포함.** 모든 백틱 span에서 `선언 앵커가 읽은 자리 ⊆ 사각지대 앵커가
  본 자리`.
- **㉡ 고정 표본에 대해 — 「세는 앵커만 보는 자리가 있다」.** 문서와 무관한 상수 셋을 두고,
  각 표본에서 **넓은 쪽만 보고 좁은 쪽은 못 보는지**를 같은 실행이 확인한다.
  **두 쪽의 크기를 함께 내고, 표본마다 두 쪽이 다 무엇인가를 보게 짠다** — 「살았다」는
  표시만 내거나 좁은 쪽이 늘 비어 있으면 **좁은 쪽을 통째로 없애도 출력이 축어 동일**이라,
  표본이 계속 `[산 표본]`이라 인쇄하면서 한쪽을 재지 않는 상태가 드러나지 않는다.
  같은 이유로 **판정에 드는 술어의 목록도 그 목록 자신이 인쇄하고 판정도 그 목록에서 낸다** —
  판정에서 술어를 빼면 인쇄가 함께 바뀌므로 둘이 갈라질 수 없고, 그 변조를 `C-10`이 받는다.

**㉠만으로는 부족하다** — 두 앵커가 **같은 폭**이 되어도 포함은 깨지지 않으므로, `F-1`이
고친 결함이 그대로 되돌아와도 ㉠은 침묵한다. 그때 **㉡의 표본이 죽는다.**
**이것이 능력을 문서의 현재 내용으로 재지 않는 이유다** — 내용이 우연히 깨끗하면 내용에
기대는 술어는 침묵하지만, 표본은 상수이므로 내용과 무관하게 죽는다.
**㉠·㉡이 재지 않는 것**: 두 앵커의 관계를 **모든 입력에 대해** 재지는 않는다. 재는 것은
**오늘 이 문서**(㉠)와 **적어 둔 표본 셋**(㉡)뿐이다.

**사각지대가 무엇인지도 이 블록이 낸다.**

**빼는 것은 되도록 규칙이 아니라 이름 목록으로 한다** — 규칙은 조용히 넓어지지만 목록은
밖의 새 이름을 `FAIL`로 낸다. **목록 자신이 조용히 넓어지는 것도 같은 실행이 막는다** —
이번 실행이 실제로 쓰지 않은 항목이 있으면 `FAIL`이다. **어떤 갈래로 몇 개를 뺐는지,
규칙으로 뺀 것이 무엇인지, 목록의 크기와 실사용이 얼마인지는 블록이 낸다** — 갈래를
산문이 세면 새 갈래가 늘 때 이 자리가 낡는다.

**덮개는 §12.2의 「표 칸」만 센다** — 그 절의 산문에 나오는 이름은 분류가 아니므로 덮개가
아니다.

```
python3 - docs/discovery/data-dictionary.md <<'PY'
import re, sys, pathlib
DOC = sys.argv[1]
lines = pathlib.Path(DOC).read_text().split("\n")
s = next(i for i,l in enumerate(lines) if l.startswith("### 12.2"))
e = next(i for i,l in enumerate(lines) if l.startswith("## 13."))
# 덮개 = §12.2 의 **표 칸**에 적힌 백틱 이름. 그 절의 산문은 덮개로 세지 않는다.
covered = {m.group(1) for l in lines[s:e] if l.startswith("|")
           for m in re.finditer(r"`([a-z][A-Za-z0-9_]*)`", l)}
body = "\n".join(lines[:s] + lines[e:])
# 앵커·규칙에 이름을 붙여 두고 **출력이 자기 패턴을 그대로 낸다** — 산문이 옮겨 적지 않는다.
DECL  = re.compile(r"\b(_*[A-Z][A-Za-z0-9_]*)")                    # ① 선언 앵커. 밑줄 선두를 포함한다
SITE  = re.compile(r"\b[A-Za-z_][A-Za-z0-9_]*\s*(?:<[^<>]*>)?\(")  # 사각지대 앵커 — 대문자를 전제하지 않는다
NAME  = re.compile(r"[a-z][A-Za-z0-9]*")                           # 인자 이름 규칙
THEAD = re.compile(r"^\|\s*(필드|성분)\s*\|")                      # ② 표 머리 칸 규칙
TROW  = re.compile(r"^\| `([a-z][A-Za-z0-9]*)`(?: \(|\s*\|)")      # ② 표 행 규칙
LOOSE = re.compile(r"`([a-z][A-Za-z0-9]*)`")                       # ③ 단독 백틱 이름 규칙
WIDER = re.compile(r"`([a-z][A-Za-z0-9_]*)`")                      # ③ 규칙 밖을 재는 더 넓은 규칙
QUAL  = re.compile(r"`([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)`")  # ④ WIDER 도 못 보는 한정 이름
HASH  = re.compile(r"[0-9a-f]{7,40}\Z")                            # commit 해시는 규칙으로 뺀다
# 고정 표본 — **검사의 능력을 문서의 현재 내용이 아니라 이 상수로 잰다.** 내용이 우연히
# 깨끗하면 내용에 기대는 술어는 침묵한다. 각 표본은 「넓은 쪽만 보고 좁은 쪽은 못 보는」
# 자리이고, 두 쪽이 같은 폭이 되는 순간 표본이 죽으며 그것이 FAIL 이다.
# **표본마다 두 쪽이 다 무엇인가를 보게 짠다** — 좁은 쪽이 언제나 빈 집합이면 좁은 쪽을
# 통째로 없애도 셈이 바뀌지 않아, 표본이 「산 표본」으로 인쇄되면서 한쪽을 재지 않게 된다.
PROBES = [
  ("세는 앵커가 읽는 앵커 밖을 본다 — 소문자 선두", "Decl(a) to_bid_rate_fraction(numeric)",
   lambda p: {m.start() for m in SITE.finditer(p)}, lambda p: {d[1] for d in decls(p)}),
  ("세는 앵커가 읽는 앵커 밖을 본다 — 밑줄+소문자 선두", "_Decl(a) _floorSchedule(effective_from)",
   lambda p: {m.start() for m in SITE.finditer(p)}, lambda p: {d[1] for d in decls(p)}),
  ("④ 가 ③ 규칙 밖 규칙의 밖을 본다 — 점이 든 이름", "`plainName` `Legacy.some_field`",
   lambda p: {m.group(1) for m in QUAL.finditer(p)}, lambda p: {m.group(1) for m in WIDER.finditer(p)}),
]
# legacy 파이썬 선언의 축어 인용(§4.1 · §5.3). V2 타입이 아니므로 그 인자는 §12.2 등재 대상이 아니다.
LEGACY_DECLS = {"FieldContract", "_FloorSchedule"}
NOT_A_DECL = {   # 「이름(」 이지만 타입 선언이 아닌 자리. 목록 밖의 새 이름은 못 읽은 선언 자리로 센다
  # 문서가 산식·술어를 적을 때 쓰는 호출 표기
  "bool","float","round","year","fold","resolve","isBiddable","to_bid_rate_fraction",
  # legacy 파이썬 메서드 호출의 축어 인용(§2.2)
  "settled_any_signal","settled_with_amount",
}
NOT_A_FIELD = {
  # legacy KONEPS 응답 키 — V2 대응 필드는 §12.2 가 따로 등재한다
  "asignBdgtAmt","bdgtAmt","bidNtceOrd","bssAmt","cnstrtnAbltyEvlAmt",
  "cnstrtnAbltyEvlAmtList","lcnsLmtNm","lmtGrpNo","lmtSno","permsnIndstrytyList",
  "presmptPrce","resultCode",
  # legacy 저장 값·열거 문자열·컬럼 이름
  "action","applicable","clean","derived","notice","status","submit","uncertain",
  # 이 문서 자신의 출처 분류 어휘(사전 §0.2 표) — 어떤 타입도 필드로 나르지 않는다
  "observed",
  # legacy 함수가 필수 키워드로 받는 인자(사전 §1.3, `app/domain/aggregates.py:20-26`)
  "digits",
  # legacy 지역 변수와 이 문서가 폐기한 이름
  "margin","marginToFloor",
  # 필드가 아님 — 리터럴·타입·보통 명사
  "false","int","legacy",
  # 필드가 아님 — 정책·모델 식별 「축」의 이름(§4.1). 어떤 타입도 필드로 나르지 않는다
  "corpusScope","ruleVersion",
}
def flat(sp):                       # 블록인용 줄바꿈을 편다 — '> ' 가 인자 이름에 붙지 않게
    return re.sub(r"\s*\n\s*>?\s*", " ", sp)
def decls(span):                    # `Name(...)` 를 균형 괄호로 자른다 — 중첩 괄호·제네릭을 견딘다
    out = []
    for m in DECL.finditer(span):
        j = m.end()
        if j < len(span) and span[j] == "<":
            d = 0
            while j < len(span):
                d += (span[j] == "<") - (span[j] == ">"); j += 1
                if d == 0: break
        if j >= len(span) or span[j] != "(": continue
        d, k = 0, j
        while k < len(span):
            d += (span[k] == "(") - (span[k] == ")")
            if d == 0: break
            k += 1
        if k >= len(span): continue
        out.append((m.group(1), m.start(), span[j+1:k]))
    return out
def top_split(a):                   # 최상위 콤마로만 자른다
    parts, d, cur = [], 0, ""
    for ch in a:
        if ch in "(<[": d += 1
        elif ch in ")>]": d = max(0, d - 1)
        if ch == "," and d == 0: parts.append(cur); cur = ""
        else: cur += ch
    parts.append(cur)
    return [p.strip() for p in parts if p.strip()]
def wrap(head, names, width=96):    # 셈과 열거가 같은 것을 세도록 전수를 접어 싣는다
    if not names: return head + "(없음)"
    out, cur = [], head
    for i, n in enumerate(names):
        tok = n + ("" if i == len(names) - 1 else " ·")
        if cur.strip() and len(cur) + len(tok) > width:
            out.append(cur.rstrip()); cur = "    "
        cur += tok + " "
    out.append(cur.rstrip())
    return "\n".join(out)
found, type_only, off_name, legacy_args, unread = {}, [], [], [], []
used = {"NOT_A_DECL": set(), "LEGACY_DECLS": set()}   # 목록이 조용히 커지지 않게 실사용을 센다
n_site = n_decl = leaked = ruled_out = 0
for span in (flat(sp) for sp in re.findall(r"`([^`]+)`", body)):
    ds = decls(span); taken = {d[1] for d in ds}
    # 사각지대는 **선언을 읽는 앵커와 다른 앵커**로 잰다 — 못 읽는 자리와 못 읽었다고 세는
    # 자리가 같으면 구조적으로 자기를 못 본다. 두 앵커의 관계는 ㉠(아래 leaked)과
    # ㉡(고정 표본)이 잰다.
    sites = {m.start() for m in SITE.finditer(span)}
    n_site += len(sites); n_decl += len(taken); leaked += len(taken - sites)
    for p in sorted(sites - taken):
        nm = re.match(r"[A-Za-z_][A-Za-z0-9_]*", span[p:]).group(0)
        if nm in NOT_A_DECL: ruled_out += 1; used["NOT_A_DECL"].add(nm)
        else: unread.append(span[p:p+50])
    for tname, _, args in ds:
        for a in top_split(args):
            n = a.split(":")[0].strip()
            if tname in LEGACY_DECLS: legacy_args.append(f"{tname}({n})"); used["LEGACY_DECLS"].add(tname)
            elif NAME.fullmatch(n): found.setdefault(n, set()).add(tname)
            elif re.fullmatch(r"[A-Z][A-Za-z0-9]*", n): type_only.append(f"{tname}({n})")
            else: off_name.append(f"{tname}({n})")
    m2 = re.match(r"^([a-z][A-Za-z0-9]*)\s*:\s*[A-Z]", span)
    if m2: found.setdefault(m2.group(1), set()).add("(단독 선언)")
hdr = False
for l in body.split("\n"):
    if l.startswith("|") and THEAD.match(l): hdr = True; continue
    if not l.startswith("|"): hdr = False; continue
    if not hdr: continue
    m = TROW.match(l)
    if m: found.setdefault(m.group(1), set()).add("(표 선언)")
loose, wider, qual = {}, {}, {}
for i, l in enumerate(body.split("\n")):
    for m in LOOSE.finditer(l): loose.setdefault(m.group(1), []).append(i + 1)
    for m in WIDER.finditer(l): wider.setdefault(m.group(1), []).append(i + 1)
    for m in QUAL.finditer(l): qual.setdefault(m.group(1), []).append(i + 1)
missing = sorted(n for n in found if n not in covered)
# ③이 낸 이름을 빼는 갈래를 **명령이 세어 낸다** — 산문이 「둘 다 아니면 FAIL」이라
# 적어 세 번째 갈래(commit 해시 규칙)를 빠뜨렸던 자리다.
route = {"덮개": [], "①② 추출": [], "NOT_A_FIELD 목록": [], "commit 해시 규칙": [], "남은 것": []}
for n in sorted(loose):
    route[("덮개" if n in covered else "①② 추출" if n in found
           else "NOT_A_FIELD 목록" if n in NOT_A_FIELD
           else "commit 해시 규칙" if HASH.match(n) else "남은 것")].append(n)
unclassified = route["남은 것"]
used["NOT_A_FIELD"] = set(route["NOT_A_FIELD 목록"])
# 목록의 크기와 내용은 출력에 드러나지 않으면 조용히 커진다 — 이번 실행이 실제로 쓰지
# 않은 항목을 FAIL 로 낸다. 그래야 미리 넣어 둔 이름이 나중에 조용히 흡수되지 않는다.
LISTS = [("NOT_A_DECL", NOT_A_DECL), ("NOT_A_FIELD", NOT_A_FIELD), ("LEGACY_DECLS", LEGACY_DECLS)]
unused = [f"{k}:{n}" for k, v in LISTS for n in sorted(v - used[k])]
# ③의 이름 규칙 자체가 못 보는 갈래도 **더 넓은 규칙으로 재어** 낸다.
off_rule = sorted(n for n in wider if n not in loose)
blind = [n for n in off_rule if n not in covered and n not in found]
# ③ 규칙 밖을 재는 규칙 자신이 못 보는 갈래(점이 든 한정 이름)도 재어 이름째 낸다.
qual_off = sorted(n for n in qual if n not in loose and n not in wider)
# 고정 표본이 살아 있는지 — 문서와 무관한 상수로 잰다(㉡). **두 쪽의 크기를 함께 낸다** —
# 살았다는 표시만 내면 좁은 쪽을 빈 집합으로 만들어도 출력이 축어 동일이라, 표본이 계속
# 「산 표본」이라 인쇄하면서 아무것도 재지 않는 상태를 라운드 간 래칫이 못 잡는다.
probe = [(("산 표본" if wide(p) - narrow(p) else "죽은 표본"), why, p, len(wide(p)), len(narrow(p)))
         for why, p, wide, narrow in PROBES]
probe_dead = [t for t in probe if t[0] == "죽은 표본"]
# 판정에 드는 술어를 한 자리에 세우고 **그 목록 자신을 인쇄한다** — 인쇄와 판정이 같은
# 목록을 읽으므로 판정에서 술어를 빼면 출력이 함께 바뀐다. 산문이 술어를 옮겨 적지 않는다.
size = lambda v: v if isinstance(v, int) else len(v)
VERDICT = [("미덮개", missing), ("③ 덮개·①②·목록·해시 밖", unclassified),
           ("통째로 못 읽은 선언 자리", unread), ("이름 규칙 밖 인자", off_name),
           ("샌 자리", leaked), ("죽은 표본", probe_dead), ("쓰이지 않은 항목", unused)]
print(f"문서: {DOC}")
print(f"§12.2 표 칸이 덮는 이름: {len(covered)}")
print(f"① 타입 선언 인자 + ② 필드·성분 표에서 뽑은 이름: {len(found)}  미덮개: {len(missing)}")
for n in missing: print(f"    {n!r} ← {' · '.join(sorted(found[n]))}")
print(f"③ 본문의 단독 백틱 이름: {len(loose)}  덮개·①②·목록·해시 밖: {len(unclassified)}")
for n in unclassified: print(f"    {n!r} @ 본문 행 {loose[n][:6]}")
print("--- 이 블록이 자기 범위와 예외를 스스로 낸다 ---")
print(f"① 선언 앵커 {DECL.pattern}  ·  인자 이름 규칙 {NAME.pattern}")
print(f"② 표 머리 칸 {THEAD.pattern}  ·  표 행 {TROW.pattern}")
print(f"③ 단독 이름 규칙 {LOOSE.pattern}")
print("③이 낸 이름을 빼는 갈래 — " + " · ".join(f"{k} {len(v)}" for k, v in route.items()))
print(f"    규칙으로 빼는 갈래는 commit 해시({HASH.pattern}) 하나뿐이고 그것이 뺀 이름: {route['commit 해시 규칙']}")
print("목록의 크기와 이번 실행이 실제로 쓴 항목 — "
      + " · ".join(f"{k} {len(v)}/{len(used[k])}" for k, v in LISTS)
      + f"  쓰이지 않은 항목: {len(unused)}")
print(wrap("    ", unused))
print(f"③ 규칙 밖 — {WIDER.pattern} 는 맞고 {LOOSE.pattern} 는 아닌 백틱 이름: {len(off_rule)}"
      f" (덮개·①② 안 {len(off_rule) - len(blind)} · 이 검사 어디에도 없음 {len(blind)})")
print(wrap("    어디에도 없는 것: ", blind))
print(f"④ ③ 규칙 밖 규칙도 못 보는 한정 이름 — {QUAL.pattern}: {len(qual_off)}"
      " (판정에 넣지 않고 이름째 낸다 — 각주)")
print(wrap("    ", qual_off))
print("--- 두 앵커의 관계를 잰다 — ㉠ 문서에 대해 포함 · ㉡ 고정 표본에 대해 「세는 앵커만 보는 자리가 있다」 ---")
print(f"㉡ 고정 표본 {len(probe)} · 죽은 표본 {len(probe_dead)}")
for st, why, p, nw, nn in probe: print(f"    [{st}] {why} :: {p!r} (넓 {nw} · 좁 {nn})")
print(f"㉠ 사각지대 앵커 {SITE.pattern}")
print(f"선언 앵커가 읽은 자리 {n_decl} ⊆ 사각지대 앵커가 본 자리 {n_site} — 그 밖으로 샌 자리: {leaked}")
print(f"통째로 못 읽은 선언 자리: {len(unread)} (NOT_A_DECL 로 가른 함수·술어 호출 {ruled_out})"
      + ("" if not unread else " -> " + repr(unread)))
print(f"legacy 선언 축어 인용의 인자(V2 필드가 아니다): {len(legacy_args)}자리")
print(wrap("    ", sorted(legacy_args)))
print(f"이름 규칙 밖 인자(legacy 선언 밖): {len(off_name)}자리")
print(wrap("    ", sorted(off_name)))
print(f"이름 없이 타입만 적힌 인자: {len(type_only)}자리")
print(wrap("    ", sorted(type_only)))
print(f"덮개에만 있는 이름(①②③이 못 내는 자리 — 손 등재): {sorted(covered - set(found) - set(loose))}")
print("판정에 드는 술어와 그 셈 — " + " · ".join(f"{k} {size(v)}" for k, v in VERDICT))
print("PASS" if not any(size(v) for _, v in VERDICT) else "FAIL")
PY
echo "exit=$?"
```

```
문서: docs/discovery/data-dictionary.md
§12.2 표 칸이 덮는 이름: 65
① 타입 선언 인자 + ② 필드·성분 표에서 뽑은 이름: 60  미덮개: 0
③ 본문의 단독 백틱 이름: 56  덮개·①②·목록·해시 밖: 0
--- 이 블록이 자기 범위와 예외를 스스로 낸다 ---
① 선언 앵커 \b(_*[A-Z][A-Za-z0-9_]*)  ·  인자 이름 규칙 [a-z][A-Za-z0-9]*
② 표 머리 칸 ^\|\s*(필드|성분)\s*\|  ·  표 행 ^\| `([a-z][A-Za-z0-9]*)`(?: \(|\s*\|)
③ 단독 이름 규칙 `([a-z][A-Za-z0-9]*)`
③이 낸 이름을 빼는 갈래 — 덮개 24 · ①② 추출 0 · NOT_A_FIELD 목록 29 · commit 해시 규칙 3 · 남은 것 0
    규칙으로 빼는 갈래는 commit 해시([0-9a-f]{7,40}\Z) 하나뿐이고 그것이 뺀 이름: ['a05deb3', 'ed4b06c', 'fc291c7']
목록의 크기와 이번 실행이 실제로 쓴 항목 — NOT_A_DECL 10/10 · NOT_A_FIELD 29/29 · LEGACY_DECLS 2/2  쓰이지 않은 항목: 0
    (없음)
③ 규칙 밖 — `([a-z][A-Za-z0-9_]*)` 는 맞고 `([a-z][A-Za-z0-9]*)` 는 아닌 백틱 이름: 26 (덮개·①② 안 1 · 이 검사 어디에도 없음 25)
    어디에도 없는 것: announced_at · artifact_version · base_amount · basis_checked_at · bid_now ·
    bid_rate · clamp_bid_rate · created_at · denominator_source ·
    estimate_historical_confidence · event_key · is_observable_assessment_rate · model_version ·
    not_applicable · opened_at · opening_checked_at · planned_price · policy_version ·
    sample_scope · sample_size · separate_regime · std_bid_rate · std_rate · total_count ·
    winning_rate
④ ③ 규칙 밖 규칙도 못 보는 한정 이름 — `([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+)`: 11 (판정에 넣지 않고 이름째 낸다 — 각주)
    CompanyProfile.construction_capacity_amount · HistoricalData.base_amount ·
    MaturityWindow.maturity · ResolvedBaseAmount.Direct · TenderResult.winning_amount ·
    TenderResultEvent.observed_at · bid_target.py · commands.md · date.min · decisions.md ·
    field_contract_spec.py
--- 두 앵커의 관계를 잰다 — ㉠ 문서에 대해 포함 · ㉡ 고정 표본에 대해 「세는 앵커만 보는 자리가 있다」 ---
㉡ 고정 표본 3 · 죽은 표본 0
    [산 표본] 세는 앵커가 읽는 앵커 밖을 본다 — 소문자 선두 :: 'Decl(a) to_bid_rate_fraction(numeric)' (넓 2 · 좁 1)
    [산 표본] 세는 앵커가 읽는 앵커 밖을 본다 — 밑줄+소문자 선두 :: '_Decl(a) _floorSchedule(effective_from)' (넓 2 · 좁 1)
    [산 표본] ④ 가 ③ 규칙 밖 규칙의 밖을 본다 — 점이 든 이름 :: '`plainName` `Legacy.some_field`' (넓 1 · 좁 1)
㉠ 사각지대 앵커 \b[A-Za-z_][A-Za-z0-9_]*\s*(?:<[^<>]*>)?\(
선언 앵커가 읽은 자리 51 ⊆ 사각지대 앵커가 본 자리 61 — 그 밖으로 샌 자리: 0
통째로 못 읽은 선언 자리: 0 (NOT_A_DECL 로 가른 함수·술어 호출 10)
legacy 선언 축어 인용의 인자(V2 필드가 아니다): 11자리
    FieldContract(basis) · FieldContract(concept) · FieldContract(expected_max) ·
    FieldContract(expected_min) · FieldContract(present_in) · FieldContract(provenance) ·
    FieldContract(raw_name) · FieldContract(scale) · FieldContract(zero_padded) ·
    _FloorSchedule(brackets) · _FloorSchedule(effective_from)
이름 규칙 밖 인자(legacy 선언 밖): 0자리
    (없음)
이름 없이 타입만 적힌 인자: 13자리
    Actual(Instant) · Column(Float) · Known(Money) · OperatorAction(Review) ·
    OperatorAction(Skip) · OperatorAction(Submit) · Resolved(PolicyVersion) · Resolved(T) ·
    Scheduled(Instant) · Substituted(Instant) · Uncertain(RequirementDataAbsent) ·
    Uncertain(RequirementDataAbsent) · Uncertain(RequirementDataAbsent)
덮개에만 있는 이름(①②③이 못 내는 자리 — 손 등재): ['review_required']
판정에 드는 술어와 그 셈 — 미덮개 0 · ③ 덮개·①②·목록·해시 밖 0 · 통째로 못 읽은 선언 자리 0 · 이름 규칙 밖 인자 0 · 샌 자리 0 · 죽은 표본 0 · 쓰이지 않은 항목 0
PASS
exit=0
```

> **이 검사가 무엇을 보고 무엇을 빼고 무엇을 못 보는지는 위 출력이 낸다** — 셈만 내고
> 이름을 삼키는 줄을 두지 않으므로, **새 이름이 셈에만 얹혀 조용히 빠지지 않는다.**
> 여기 옮겨 적지 않는다.
>
> **③의 이름 규칙이 무엇을 못 보는지도 규칙을 넓혀 재고 위 출력이 낸다** — 산문이
> 「필드가 늘면 이 검사가 낸다」고 적으면 그 규칙 밖의 이름에 거짓이 된다.
>
> **`③ 규칙 밖 … 어디에도 없음`은 정보 줄이고 판정에 들어가지 않는다 — 왜인가.**
> 그 이름들은 `lower_snake_case`라 **V2 필드 이름 규칙(③) 밖**이고, 그런 자리가
> **legacy 식별자의 인용인지 미등재 V2 필드인지는 기계가 가르지 못한다** — 문자열
> 형태가 같다. 그래서 `FAIL`로 세지 않고 **이름째 낸다.** 새 이름이 들어오면 그 열거가
> 바뀌므로 diff로 드러나고, 기록된 출력과 달라지면 `C-10`이 불일치로 낸다.
> **오늘 그 줄이 낸 이름이 전부 legacy 참조라는 확인은 사람이 전수로 연 것이고 매
> 실행이 되풀이하지 않는다 — 그것이 이 검사의 한계다.**
>
> **④는 `③ 규칙 밖`을 재는 규칙(`WIDER`) 자신이 못 보던 갈래다** — 점이 든 한정 이름
> (`Type.field`·파일명)은 그 규칙도 보지 못했다. **계측기의 계측기가 좁았던 자리**이고 이
> 줄이 그것을 이름째 낸다. **④도 판정에 넣지 않는다** — 위와 같은 이유이고, **파일명이
> 같은 형태로 섞이므로** 무엇인지는 사람이 열어야 한다. ④ 자신이 다시 좁아지는 것은
> ㉡의 셋째 고정 표본이 막는다.
>
> `checklist.md` A2의 *"어느 갈래에도 들지 않은 이름이 남으면 `FAIL`"*에서 **「갈래」는
> ③의 이름 규칙 안에서 나눈 갈래**를 뜻한다 — 위 `③이 낸 이름을 빼는 갈래` 줄이 그
> 갈래를 세어 낸다. **그 규칙 밖의 이름은 그 판정에 들어가지 않는다.**

---

## C-5 · `capability-map.md`에서 정책 version을 말하는 자리 전수 (A3 · §4.2)

선언 SHA `09d5840`.

```
python3 - <<'PY'
import pathlib, re
cap = ""
pat = re.compile(r"정책 version|policy version|policyVersion|versioned policy")
for line in pathlib.Path("docs/discovery/capability-map.md").read_text().split("\n"):
    if line.startswith("### "): cap = line
    if pat.search(line):
        # 문자 단위로 자른다 — 바이트 절단은 한글을 깨뜨린다.
        # 자른 끝의 공백은 떤다 — 후행 공백은 git diff --check 가 지적한다(A8).
        print(cap); print(("    ↳ " + line.strip()[:90]).rstrip())
PY
echo "exit=$?"
```

```
### QUAL-01 · 이 공고에 우리가 참가할 수 있는가, 없다면 무엇이 없어서인가
    ↳ 과거 판정의 의미가 조용히 달라진다 — 정책 version 식별자로 참조한다.
### QUAL-03 · 그룹 AND/OR 자격 경로 판정
    ↳ - 판정 결과에 어떤 policy version으로 판정했는지가 함께 남는다.
### QUAL-03 · 그룹 AND/OR 자격 경로 판정
    ↳ - **판정에 쓰인 요건 소스 집합과 결합 규칙의 policy version이 결과에 함께 남는다** —
### ML-07 · 모델 승격 게이트 (사전 선언 판정식 + 검정력 공시)
    ↳ - 게이트 리포트가 자신이 사용한 정책 version을 싣고, 정책 version이 바뀌면 그 이전
### ML-11 · ML 재활용 대상 요약 (v2-지침서 §3.2 이식 판단 입력)
    ↳ | 전역 `settings` 싱글턴 | 14파일. predictor 동작에 영향 주는 설정 33개 | 생성자 주입으로. 33개 중 다수는 환경 값이 아니라 **도
### DEC-03 · 법정 낙찰하한율 해석 (우선순위 + 적용 범위)
    ↳ - **재사용 후보(설계 규율)**: 시행일 표를 versioned policy + effective date로 두고 기준일을
### DEC-03 · 법정 낙찰하한율 해석 (우선순위 + 적용 범위)
    ↳ 유형이 어느 모델에 해당하는지는 versioned policy가 정하며, 이 시나리오는 그 매핑과
### DEC-04 · 하한 미달 빈도 표시와 판정 불가 사유 (정직 명세)
    ↳ - 빈도 결과가 값 단독이 아니라 (임계 사정률, 빈도, 분자, 분모, 범위, 정책 version)
### DEC-05 · 투찰가 메뉴 3안 (recommended / aggressive / safe)
    ↳ `_MAX_ADJUSTMENT`)가 코드 상수인 형태는 `폐기` — versioned policy 데이터로.
### DEC-08 · 기초금액 provenance 분류
    ↳ - 분류에 사용된 정책 version이 결과와 함께 저장된다.
### SET-06 · 정산 성숙도(관측 가능성) → 평가 창 embargo
    ↳ (개찰 시각 / 승인된 대체 출처)와 적용된 정책 version이 함께 남는다. 시각 종류를
### SET-06 · 정산 성숙도(관측 가능성) → 평가 창 embargo
    ↳ (`OPEN-SET-10`), 대체 출처와 정책 version을 결과에 보존하며, 대체 불가 행을 사유 있는
### G2. 정책 값·임계의 근거 — 재유도 또는 승인 필요
    ↳ | OPEN-SET-10 | 성숙도 시간축에서 개찰 시각 결측 시 마감 시각 대체를 **승인된 정책**으로 채택할 것인가 | (a) 채택 + 대체 출처·polic
### G2. 정책 값·임계의 근거 — 재유도 또는 승인 필요
    ↳ | OPEN-ML-05 | predictor 정책 값 33개 중 어디까지가 versioned policy이고 어디까지가 환경 설정인가 | 특히 발주기관별 값들은
### 12.1 통합·해소된 항목 (ID 결번 사유)
    ↳ | `OPEN-QUAL-11` (신설) | `OPEN-QUAL-02`가 `permsnIndstrytyList`의 **판정 소스 포함**을 확정했으나 **결합 규칙
### 12.2 결정 완료 항목 (운영자 결정 2026-08-26 · 2026-08-27)
    ↳ | `OPEN-QUAL-02` | **(b)** `permsnIndstrytyList`를 자격 경로로 포함한다 | 문서 정의가 "공고의 제한되는 면허에서 **허용
### 12.2 결정 완료 항목 (운영자 결정 2026-08-26 · 2026-08-27)
    ↳ | `OPEN-COL-02` | 문서의 **17개 `resultCode`를 versioned policy data로 등재**한다 | 조달청 OpenAPI 참고자료
exit=0
```

---

## C-6 · 활성 `OPEN` 비침범과 신설 id (A5 · A7)

### C-6.1 상류 산출물과 0D 산출물이 이 slice의 커밋에서 변하지 않았다

선언 SHA `09d5840`.

```
for c in $(git log --format='%H' 2b05684..09d5840 \
             -- docs/discovery/data-dictionary.md reports/evidence/m0/0c/); do
  git show --name-only --format='' $c
done | sed '/^$/d' | sort -u \
     | grep -E '^(docs/discovery/capability-map\.md|docs/discovery/regression-ledger\.md|docs/adr/|reports/evidence/m0/0[abd])' \
     || echo "(상류·0D 산출물 변경 0건)"
```

```
(상류·0D 산출물 변경 0건)
```

### C-6.2 신설 `OPEN-DIC` id와 중복

선언 SHA `09d5840`.

```
grep -c '^| \*\*`OPEN-DIC-' docs/discovery/data-dictionary.md \
  | xargs printf '§9 표의 OPEN-DIC 행 수: %s\n'
grep -o '^| \*\*`OPEN-DIC-[0-9][0-9]' docs/discovery/data-dictionary.md \
  | grep -o 'OPEN-DIC-[0-9][0-9]' | sort | xargs echo
grep -o '^| \*\*`OPEN-DIC-[0-9][0-9]' docs/discovery/data-dictionary.md \
  | grep -o 'OPEN-DIC-[0-9][0-9]' | sort | uniq -d | wc -l \
  | xargs printf '§9 표 안의 중복 id: %s\n'
```

```
§9 표의 OPEN-DIC 행 수: 5
OPEN-DIC-01 OPEN-DIC-02 OPEN-DIC-03 OPEN-DIC-04 OPEN-DIC-05
§9 표 안의 중복 id: 0
```

### C-6.3 사전이 언급한 모든 `OPEN` id의 상태 분류

`capability-map.md` §12의 **표 첫 칸**과 `regression-ledger.md` §9의 첫 칸을 정본으로
삼는다. **한 id가 활성과 결정 완료 양쪽에 나오면 활성이 이긴다** — §12.2가 "라운드 7에
활성으로 복원", "임계 자체는 미결"이라 적는 행들이 그렇다. 선언 SHA `09d5840`.

```
python3 - <<'PY'
import re, pathlib
cm  = pathlib.Path("docs/discovery/capability-map.md").read_text().split("\n")
led = pathlib.Path("docs/discovery/regression-ledger.md").read_text().split("\n")
dd  = pathlib.Path("docs/discovery/data-dictionary.md").read_text()
def at(seq, pref): return next(i for i,l in enumerate(seq) if l.startswith(pref))
i12, i121 = at(cm,"## 12."), at(cm,"### 12.1")
i122, i13 = at(cm,"### 12.2"), at(cm,"## 13.")
i9, i10   = at(led,"## 9."), at(led,"## 10.")
def head_ids(seq):
    out=set()
    for l in seq:
        if l.startswith("|"):
            out |= set(re.findall(r"OPEN-[A-Z]+-\d\d", l.split("|")[1]))
    return out
active  = head_ids(cm[i12:i121]) | head_ids(led[i9:i10])
decided = head_ids(cm[i122:i13])
mentioned = sorted(set(re.findall(r"OPEN-[A-Z]+-\d\d", dd)))
w = max(map(len, mentioned))
for o in mentioned:
    if o.startswith("OPEN-DIC"): cls = "이 문서가 신설"
    elif o in active:            cls = "활성 — 사전은 소유자만 밝힌다"
    elif o in decided:           cls = "결정 완료 — 사전이 등재한다"
    else:                        cls = "??? 미분류"
    print(f"  {o:<{w}}  {cls}")
n = lambda p: sum(1 for o in mentioned if p(o))
print()
print("신설:", n(lambda o:o.startswith("OPEN-DIC")),
      "· 활성:", n(lambda o:not o.startswith("OPEN-DIC") and o in active),
      "· 결정 완료:", n(lambda o:not o.startswith("OPEN-DIC") and o not in active and o in decided),
      "· 미분류:", n(lambda o:not o.startswith("OPEN-DIC") and o not in active and o not in decided))
PY
echo "exit=$?"
```

```
  OPEN-COL-02   결정 완료 — 사전이 등재한다
  OPEN-DEC-01   결정 완료 — 사전이 등재한다
  OPEN-DEC-02   결정 완료 — 사전이 등재한다
  OPEN-DEC-04   결정 완료 — 사전이 등재한다
  OPEN-DEC-05   결정 완료 — 사전이 등재한다
  OPEN-DEC-06   결정 완료 — 사전이 등재한다
  OPEN-DEC-07   활성 — 사전은 소유자만 밝힌다
  OPEN-DEC-08   결정 완료 — 사전이 등재한다
  OPEN-DEC-09   결정 완료 — 사전이 등재한다
  OPEN-DEC-10   활성 — 사전은 소유자만 밝힌다
  OPEN-DIC-01   이 문서가 신설
  OPEN-DIC-02   이 문서가 신설
  OPEN-DIC-03   이 문서가 신설
  OPEN-DIC-04   이 문서가 신설
  OPEN-DIC-05   이 문서가 신설
  OPEN-ML-01    결정 완료 — 사전이 등재한다
  OPEN-ML-04    결정 완료 — 사전이 등재한다
  OPEN-ML-05    활성 — 사전은 소유자만 밝힌다
  OPEN-NUM-01   활성 — 사전은 소유자만 밝힌다
  OPEN-OPS-05   결정 완료 — 사전이 등재한다
  OPEN-OPS-10   활성 — 사전은 소유자만 밝힌다
  OPEN-QUAL-02  결정 완료 — 사전이 등재한다
  OPEN-QUAL-05  활성 — 사전은 소유자만 밝힌다
  OPEN-QUAL-06  결정 완료 — 사전이 등재한다
  OPEN-QUAL-08  결정 완료 — 사전이 등재한다
  OPEN-QUAL-09  활성 — 사전은 소유자만 밝힌다
  OPEN-QUAL-10  활성 — 사전은 소유자만 밝힌다
  OPEN-QUAL-11  활성 — 사전은 소유자만 밝힌다
  OPEN-REG-04   활성 — 사전은 소유자만 밝힌다
  OPEN-REG-05   활성 — 사전은 소유자만 밝힌다
  OPEN-SET-02   결정 완료 — 사전이 등재한다
  OPEN-SET-04   활성 — 사전은 소유자만 밝힌다
  OPEN-SET-05   활성 — 사전은 소유자만 밝힌다
  OPEN-SET-06   활성 — 사전은 소유자만 밝힌다
  OPEN-SET-10   활성 — 사전은 소유자만 밝힌다
  OPEN-STR-03   결정 완료 — 사전이 등재한다
  OPEN-STR-07   결정 완료 — 사전이 등재한다

신설: 5 · 활성: 15 · 결정 완료: 17 · 미분류: 0
exit=0
```

> **이 분류의 읽는 법과 항목별 처리는 `checklist.md` **A5**가 적는다** — 이 파일은
> 이 파일은 그 판정을 싣지 않는다. `OPEN-REG-05`·`OPEN-QUAL-10`·`OPEN-QUAL-11`이 「활성」으로 찍히는
> 것은 두 상류 파일의 행을 **고치지 않았기 때문**이며(out_of_scope), 사전 §11이 그것들을
> 결정 근거와 함께 등재해 닫는다. 상류 registry 갱신은 별도 slice의 몫이다.

---

## C-7 · legacy 인용 (A6)

`bid-vector` symlink가 있어야 재현된다. 선언 SHA `09d5840`.

### C-7.1 경로 존재 · 행 범위 유효 · 파일명만 쓴 인용

```
python3 - docs/discovery/data-dictionary.md <<'PY'
import re, subprocess, sys, pathlib
DOC = sys.argv[1]
LEGACY = "bid-vector"
SHA = "ed4b06c"
text = pathlib.Path(DOC).read_text()
# 인용 형태: <path>:<start>-<end> 또는 <path>:<line> (path 는 저장소 루트부터)
pat = re.compile(r"(?P<path>(?:app|tests|scripts|docs)/[A-Za-z0-9_./-]+\.(?:py|md)):(?P<a>\d+)(?:-(?P<b>\d+))?")
# 파일명만 쓴 인용(디렉터리 없음) 탐지
bare = re.compile(r"`(?!app/|tests/|scripts/|docs/)(?P<f>[A-Za-z0-9_-]+\.py):(?P<a>\d+)")
lens = {}
def flen(p):
    if p not in lens:
        r = subprocess.run(["git","-C",LEGACY,"show",f"{SHA}:{p}"],capture_output=True)
        lens[p] = None if r.returncode else r.stdout.decode("utf-8","replace").count("\n")
    return lens[p]
cites = sorted({(m.group("path"), int(m.group("a")), int(m.group("b") or m.group("a"))) for m in pat.finditer(text)})
missing, over, ok = [], [], 0
for p,a,b in cites:
    n = flen(p)
    if n is None: missing.append((p,a,b)); continue
    if b > n or a < 1 or a > b: over.append((p,a,b,n)); continue
    ok += 1
bares = sorted({(m.group("f"), m.group("a")) for m in bare.finditer(text)})
print(f"문서: {DOC}")
print(f"legacy 인용(고유 (경로,범위)): {len(cites)}")
print(f"고유 경로: {len(lens)}")
print(f"경로 존재 + 행 범위 유효: {ok}")
print(f"경로 부재: {len(missing)}" + ("" if not missing else " -> " + repr(missing)))
print(f"행 범위 초과/역전: {len(over)}" + ("" if not over else " -> " + repr(over)))
print(f"파일명만 쓴 인용(디렉터리 없음): {len(bares)}" + ("" if not bares else " -> " + repr(bares)))
print("PASS" if not missing and not over and not bares else "FAIL")
PY
echo "exit=$?"
```

```
문서: docs/discovery/data-dictionary.md
legacy 인용(고유 (경로,범위)): 84
고유 경로: 36
경로 존재 + 행 범위 유효: 84
경로 부재: 0
행 범위 초과/역전: 0
파일명만 쓴 인용(디렉터리 없음): 0
PASS
exit=0
```

> **이 축이 확인하는 것은 경로와 행 범위뿐이다.** 그 범위의 **내용이 서술과 맞는가**는
> 정본을 계산할 수 없어 **사람이 읽는다** — `checklist.md` A6이 그 확인을 기록한다.
> 0B가 세운 두 축 규약(`regression-ledger.md` §0.5)을 그대로 쓴다.

### C-7.2 시공능력 요건 필드가 legacy에 수집되는가 (§1.2.4 · §5.5)

```
git -C bid-vector grep -l 'cnstrtnAbltyEvlAmt' ed4b06c -- app/ tests/ \
  || echo "(매치 0 — ed4b06c 의 app/·tests/ 에 이 키가 없다)"
```

```
(매치 0 — ed4b06c 의 app/·tests/ 에 이 키가 없다)
```

### C-7.3 `0`을 부재로 쓰는 legacy 컬럼 (§1.3)

이 명령이 내는 것은 **두 모델 파일에서 `0` 기본값을 선언한 컬럼 전부**다. 부재 금지 규칙이
걸리는 것은 그중 **도메인 값을 나르는 컬럼**이며, 불리언 플래그처럼 `0`이 진짜 값인 자리는
그 규칙의 대상이 아니다.

```
git -C bid-vector grep -n 'default=0\.0\|server_default="0"' ed4b06c \
  -- app/models/models.py app/models/pipeline.py | sed 's/^ed4b06c://'
echo "exit=$?"
```

```
app/models/models.py:150:    predicted_bid_rate = Column(Float, default=0.0)
app/models/models.py:170:    annual_revenue = Column(Float, default=0.0)
app/models/models.py:171:    capacity_score = Column(Float, default=0.0)
app/models/models.py:176:        Float, default=0.0, nullable=False, server_default="0"
app/models/models.py:181:        Float, default=0.0, nullable=False, server_default="0"
app/models/models.py:222:    min_budget_estimate = Column(Float, default=0.0)
app/models/models.py:223:    max_budget_estimate = Column(Float, default=0.0)
app/models/models.py:255:    is_active = Column(Boolean, default=False, nullable=False, server_default="0")
app/models/models.py:273:    base_amount = Column(Float, default=0.0)
app/models/models.py:274:    predicted_price = Column(Float, default=0.0)
app/models/models.py:275:    bid_rate = Column(Float, default=0.0)
app/models/models.py:330:    probability_score = Column(Float, default=0.0)
app/models/models.py:331:    matched_score = Column(Float, default=0.0)
app/models/models.py:332:    priority_score = Column(Float, default=0.0)
app/models/models.py:333:    urgency_score = Column(Float, default=0.0)
app/models/models.py:334:    competitiveness_score = Column(Float, default=0.0)
app/models/models.py:335:    budget_capture_score = Column(Float, default=0.0)
app/models/models.py:336:    expected_margin_score = Column(Float, default=0.0)
app/models/models.py:337:    execution_complexity_score = Column(Float, default=0.0)
app/models/models.py:341:    current_workload_score = Column(Float, default=0.0)
app/models/models.py:408:    recommended_amount = Column(Float, default=0.0)
app/models/models.py:409:    probability_score = Column(Float, default=0.0)
app/models/models.py:461:    paper_bid_amount = Column(Float, default=0.0)
app/models/models.py:462:    paper_bid_rate = Column(Float, default=0.0)
app/models/models.py:464:    priority_score = Column(Float, default=0.0)
app/models/models.py:465:    probability_score = Column(Float, default=0.0)
app/models/models.py:466:    matched_score = Column(Float, default=0.0)
app/models/models.py:467:    predicted_price = Column(Float, default=0.0)
app/models/models.py:468:    predicted_bid_rate = Column(Float, default=0.0)
app/models/models.py:469:    price_range_min = Column(Float, default=0.0)
app/models/models.py:470:    price_range_max = Column(Float, default=0.0)
app/models/models.py:471:    confidence_score = Column(Float, default=0.0)
app/models/models.py:494:    winning_amount = Column(Float, default=0.0)
app/models/models.py:495:    winning_rate = Column(Float, default=0.0)
app/models/models.py:496:    amount_delta = Column(Float, default=0.0)
app/models/models.py:497:    absolute_error_rate = Column(Float, default=0.0)
app/models/models.py:498:    bid_rate_delta = Column(Float, default=0.0)
app/models/models.py:499:    absolute_bid_rate_error = Column(Float, default=0.0)
app/models/pipeline.py:156:    winning_amount = Column(Float, default=0.0)
app/models/pipeline.py:157:    winning_rate = Column(Float, default=0.0)
exit=0
```

### C-7.4 상태 컬럼 — 전이 선언이 없다 (§2.2)

```
git -C bid-vector grep -n 'status = Column' ed4b06c \
  -- app/models/models.py app/models/pipeline.py | sed 's/^ed4b06c://'
echo "---"
git -C bid-vector grep -rn 'TRANSITION\|transition_table\|state_machine' ed4b06c -- app/ \
  || echo "(전이표 선언 매치 0)"
```

```
app/models/models.py:86:    status = Column(String(50), default="open")  # open, re_notice, closed, awarded, failed, cancelled
app/models/models.py:117:    status = Column(String(50), default="submitted")  # submitted, reviewed, accepted, rejected
app/models/models.py:198:    business_verification_status = Column(
app/models/models.py:321:    decision_status = Column(String(50), default="planned")
app/models/models.py:323:    initial_decision_status = Column(String(50), default="planned")
app/models/models.py:375:    status = Column(String(50), default="planned", index=True)
app/models/models.py:411:    status = Column(String(50), default="proposed")
app/models/models.py:427:    status = Column(String(50), default="running", index=True)
app/models/models.py:459:    decision_status = Column(String(50), default="skipped", index=True)
app/models/models.py:492:    result_status = Column(String(50), default="pending")
app/models/models.py:535:    telegram_status = Column(String(50), nullable=True)
app/models/models.py:597:    status = Column(String(50), default="queued", index=True)  # queued/running/completed/failed
app/models/models.py:699:    status = Column(String(20), nullable=False)
app/models/models.py:731:    status = Column(String(50), default="idle", nullable=False, index=True)  # idle / running / failed
app/models/pipeline.py:31:    status = Column(String(50), default="queued", index=True)
app/models/pipeline.py:74:    status = Column(String(30), nullable=False, default="processing", index=True)
app/models/pipeline.py:118:    status = Column(String(20), nullable=False, default="pending", index=True)
app/models/pipeline.py:158:    result_status = Column(String(50), default="pending")
app/models/pipeline.py:192:    status = Column(String(50), default="queued")
---
(전이표 선언 매치 0)
```

### C-7.5 필드 계약 등재율 (§5.3 · §10.1 **X-7**)

```
git -C bid-vector show ed4b06c:app/services/koneps/field_contract_spec.py | python3 -c '
import sys, ast
tree = ast.parse(sys.stdin.read())
for node in tree.body:
    name = getattr(getattr(node,"target",None),"id","")
    if name == "KNOWN_FIELDS":
        keys = [e.value for e in ast.walk(node.value)
                if isinstance(e,ast.Constant) and isinstance(e.value,str)]
        print("KNOWN_FIELDS 고유 키:", len(set(keys)))
    if name == "FIELD_CONTRACTS":
        print("FIELD_CONTRACTS 등재:", sum(
            1 for e in ast.walk(node.value)
            if isinstance(e,ast.Call) and getattr(e.func,"id","") == "FieldContract"))
'
echo "--- capability-map COL-07 의 서술 ---"
grep -n 'KNOWN_FIELDS' docs/discovery/capability-map.md
```

```
FIELD_CONTRACTS 등재: 3
KNOWN_FIELDS 고유 키: 60
--- capability-map COL-07 의 서술 ---
342:  (`field_contract_spec.py:68-238`). `KNOWN_FIELDS`(약 55개 raw 키) 밖의 키는 "미지 필드"로
```

---

## C-8 · secret 스캔

선언 SHA `09d5840`.

**이 검사는 자기 기록을 스캔 대상에 담고 있다** — `commands.md`가 앞 라운드의 기록된 출력을
싣고, 그 출력이 매치한 줄을 통째로 나르므로 **자기 인용이 라운드마다 한 겹씩 쌓였다**
— **`C-8` 출력 펜스 블록의 줄 수**가 라운드마다 늘어 **라운드 7 기록에서 146줄**이 됐다.
그러면 진짜 값 한 줄이 잡음 수백 줄에 묻혀 **판정이 사람의 육안에만 의존**한다.
**어휘는 그대로 두고 세 자리를 바꿨다.**

- **낱말만이 아니라 그 옆의 값 자리까지 가려서 낸다.** 어휘 갈래가 매치하는 것은 **낱말**이라
  매치한 span만 가리면 낱말이 가려지고 **값은 그대로 인쇄된다.** 그래서 가리는 폭을
  `낱말 [:=] 값`과 `낱말 바로 뒤에 붙는 값`까지 늘렸다 — 구분자는 남기고 두 자리를 따로
  가리므로 꼴은 읽을 수 있다. ① 그 꼴로 걸린 값은 **이 파일에 실리지 않고** ② **이 출력
  자신이 다음 라운드의 매치가 되지 않는다** — 재귀가 여기서 끊긴다.
  **판정 어휘(`PATS`)는 그대로다 — 고친 것은 인쇄지 판정이 아니다.**
- **능력을 이 디렉터리의 현재 내용이 아니라 심어 둔 가짜 값 여섯으로 잰다.** 내용이 깨끗하면
  스캔은 잡을 것이 없어도 침묵하므로, 침묵이 능력을 뜻하지 않는다. 표본 여섯은 **어휘가
  값을 데리고 오는 네 꼴**과 **값 형태 둘**을 각각 하나씩 든다.
- **값이 실제로 가려졌는지도 블록이 잰다.** 표본에서 값 조각을 뽑아 **그것이 출력에 남으면
  `FAIL`**이다. 남았다는 사실은 표본 이름으로만 내고 조각 자체는 인쇄하지 않는다 — 그
  인쇄가 곧 유출이다.

**끊겼다는 것과 잡는다는 것과 가려졌다는 것을 산문이 주장하지 않고 블록이 잰다** — 자기 출력을
자기 패턴으로 되재어 내고(`0`이라야 쌓이지 않는다), 심은 표본을 놓치거나 그 값 조각이 출력에
남으면 `FAIL`이다.

```
python3 - <<'PY'
import re, pathlib
# 스캔 어휘 — 앞 라운드까지 두 grep 이 쓰던 것과 같은 둘이다. 어휘를 넓히지 않았다.
PATS = [("비밀 어휘", re.compile(r"(?i)(api[_-]?key|secret|token|password|bearer |begin (rsa|ec|openssh))")),
        ("식별자",   re.compile(r"([0-9]{3}-[0-9]{2}-[0-9]{5}|chat_id|telegram[_-]?id|@[A-Za-z0-9_]{5,}bot)"))]
VALUE = re.compile(r"(?i)([0-9]{3}-[0-9]{2}-[0-9]{5}|begin (rsa|ec|openssh))")   # 어휘가 아니라 값 형태인 갈래
# 가리는 폭 — 낱말만이 아니라 그 옆의 값 자리다. 어휘 갈래가 매치하는 것은 낱말이므로
# 매치한 span 만 가리면 낱말이 가려지고 값은 그대로 인쇄된다. 판정 어휘(PATS)는 그대로 두고
# 인쇄에서만 값 자리를 함께 덮는다 — 판정이 아니라 인쇄를 고치는 것이다.
SEP  = re.compile(r"\s*[:=]\s*('[^']*'|\"[^\"]*\"|[^\s'\",]+)")   # 낱말 뒤 구분자와 그 값
NEXT = re.compile(r"[A-Za-z0-9][^\s'\",]{7,}")                    # 구분자 없이 낱말 바로 뒤에 붙는 값
# 심어 둔 가짜 값(planted probe) — **진짜 비밀값이 아니다.** 여섯 다 이 자리에서 지어낸
# 형태이고 어떤 계정·채널·사업자에도 대응하지 않는다. 검사의 능력을 이 디렉터리의 현재
# 내용이 아니라 이 상수로 잰다 — 내용이 깨끗하면 스캔은 잡을 것이 없어도 침묵한다.
PROBES = [("어휘 + 따옴표 값", 'api_key = "Zz00-not-a-real-key-0000"'),
          ("어휘 뒤 바로 값",  'Authorization: Bearer Zz00.not-a-real-head.0000'),
          ("어휘 + 등호 값",   'db_password=Zz00-not-a-real-pw-0000'),
          ("어휘 + 콜론 값",   'slack_token: xoxb-0000-not-a-real-0000'),
          ("값 형태 — 번호",   '999-88-77777'),
          ("값 형태 — 개인키", '-----BEGIN RSA PRIVATE KEY-----')]
# 표본의 「값 조각」은 표본에서 뽑아 얻는다 — 상수로 한 번 더 적으면 그 리터럴이 낱말 옆에
# 있지 않아 가려지지 않고, 검사가 자기가 적은 값에 걸린다.
def val(p):
    m = re.search(r"(?i)bearer\s+([^\s'\",]+)", p) or re.search(r"[:=]\s*('[^']*'|\"[^\"]*\"|[^\s'\",]+)", p)
    return m.group(1).strip("\"'") if m else p
TARGETS = sorted(p for p in pathlib.Path("reports/evidence/m0/0c").rglob("*") if p.is_file())
TARGETS.append(pathlib.Path("docs/discovery/data-dictionary.md"))
def hits(s): return sorted({(m.start(), m.end()) for _, r in PATS for m in r.finditer(s)})
def valspan(s, b):   # 낱말이 b 에서 끝났을 때 그 옆의 값 자리
    m = SEP.match(s, b)
    if m: return (m.start(1), m.end())            # 구분자는 남기고 값만 가린다
    m = NEXT.match(s, b) if s[b-1:b] == " " else None
    return m.span() if m else None
def spans(s):   # 낱말 자리와 값 자리를 따로 세고 겹치는 것만 합친다
    raw = []
    for a, b in hits(s):
        raw.append([a, b]); v = valspan(s, b)
        if v: raw.append(list(v))
    out = []
    for a, b in sorted(raw):
        if out and a < out[-1][1]: out[-1][1] = max(out[-1][1], b)
        else: out.append([a, b])
    return out
def mask(s):
    out, prev = [], 0
    for a, b in spans(s):
        out += [s[prev:a], f"<가림 {b-a}자>"]; prev = b
    return "".join(out + [s[prev:]])
L = ["--- 심어 둔 가짜 값을 이 패턴이 잡는가 (planted probe — 진짜 비밀값이 아니다) ---",
     f"심은 표본 {len(PROBES)} · 놓친 표본 {sum(1 for _, p in PROBES if not hits(p))}"]
L += [f"    [{'잡힘' if hits(p) else '놓침'}] {k} :: {mask(p)}" for k, p in PROBES]
L.append("--- 스캔 매치 — 낱말과 그 옆의 값 자리를 가려서 낸다 ---")
rows, planted, stray = [], 0, []
for t in TARGETS:
    n = 0
    for i, l in enumerate(t.read_text(errors="replace").split("\n"), 1):
        if not hits(l): continue
        # 자른 끝의 공백은 뗀다 — 후행 공백은 A8 이 지적한다
        n += 1; rows.append(f"    {t}:{i}: {mask(l)[:110].rstrip()}")
        if VALUE.search(l):
            if any(pr in l for _, pr in PROBES): planted += 1
            else: stray.append(f"{t}:{i}")
    L.append(f"{t} — 매치 {n}줄")
L += rows
L.append(f"값 형태 갈래({VALUE.pattern})에서 난 매치: {planted + len(stray)}"
         f" — 심어 둔 표본의 정의 줄 {planted} · 그 밖 {len(stray)}")
L += [f"    {s}" for s in stray]
# 남았는지는 표본 이름으로만 낸다 — 남은 조각 자체를 여기 인쇄하면 그 인쇄가 곧 유출이다.
leak = [k for k, p in PROBES if any(val(p) in l for l in L)]
L.append(f"심은 표본의 값 조각이 위 줄들에 가려지지 않고 남은 것: {len(leak)}"
         " (0 이라야 값이 이 파일에 실리지 않는다)")
L += [f"    [{k}] 의 값 조각이 가려지지 않았다" for k in leak]
ok = lambda n: not stray and not leak and n == 0 and all(hits(p) for _, p in PROBES)
tail = lambda n: [f"이 블록의 출력 중 자기 패턴에 걸리는 줄: {n} (0 이라야 자기 인용이 쌓이지 않는다)",
                  "PASS" if ok(n) else "FAIL"]
n = sum(1 for l in L if hits(l))
n = sum(1 for l in L + tail(n) if hits(l))   # 꼬리 두 줄까지 넣어 다시 센다
print("\n".join(L + tail(n)))
PY
echo "exit=$?"
```

```
--- 심어 둔 가짜 값을 이 패턴이 잡는가 (planted probe — 진짜 비밀값이 아니다) ---
심은 표본 6 · 놓친 표본 0
    [잡힘] 어휘 + 따옴표 값 :: <가림 7자> = <가림 26자>
    [잡힘] 어휘 뒤 바로 값 :: Authorization: <가림 7자><가림 25자>
    [잡힘] 어휘 + 등호 값 :: db_<가림 8자>=<가림 23자>
    [잡힘] 어휘 + 콜론 값 :: slack_<가림 5자>: <가림 25자>
    [잡힘] 값 형태 — 번호 :: <가림 12자>
    [잡힘] 값 형태 — 개인키 :: -----<가림 9자> PRIVATE KEY-----
--- 스캔 매치 — 낱말과 그 옆의 값 자리를 가려서 낸다 ---
reports/evidence/m0/0c/checklist.md — 매치 1줄
reports/evidence/m0/0c/codex-review-20260829T061613Z.json — 매치 1줄
reports/evidence/m0/0c/commands.md — 매치 9줄
reports/evidence/m0/0c/decisions-2026-08-28.md — 매치 0줄
reports/evidence/m0/0c/scope.md — 매치 1줄
docs/discovery/data-dictionary.md — 매치 0줄
    reports/evidence/m0/0c/checklist.md:188: | <가림 6자> 스캔 | `commands.md` **C-8** — 매치와 그 판정이 그 절에 있다 |
    reports/evidence/m0/0c/codex-review-20260829T061613Z.json:64:     "rg -n --glob '!commands.md' '(api[_-]?key|<가림 6자>|<가림 5자>|<가림 8자>|<가림 7자>|BEGIN (RSA|EC|OPENSSH)|[0-9]{3}
    reports/evidence/m0/0c/commands.md:1115: ## C-8 · <가림 6자> 스캔
    reports/evidence/m0/0c/commands.md:1146: PATS = [("비밀 어휘", re.compile(r"(?i)(api[_-]?key|<가림 6자>|<가림 5자>|<가림 8자>|<가림 7자>|begin (rsa|ec|openssh))")),
    reports/evidence/m0/0c/commands.md:1147:         ("식별자",   re.compile(r"([0-9]{3}-[0-9]{2}-[0-9]{5}|<가림 7자>|telegram[_-]?id|@[A-Za-z0-9_]{5,}bot)"))]
    reports/evidence/m0/0c/commands.md:1157: PROBES = [("어휘 + 따옴표 값", '<가림 7자> = <가림 26자>'),
    reports/evidence/m0/0c/commands.md:1158:           ("어휘 뒤 바로 값",  'Authorization: <가림 7자><가림 25자>'),
    reports/evidence/m0/0c/commands.md:1159:           ("어휘 + 등호 값",   'db_<가림 8자>=<가림 23자>'),
    reports/evidence/m0/0c/commands.md:1160:           ("어휘 + 콜론 값",   'slack_<가림 5자>: <가림 25자>'),
    reports/evidence/m0/0c/commands.md:1161:           ("값 형태 — 번호",   '<가림 12자>'),
    reports/evidence/m0/0c/commands.md:1162:           ("값 형태 — 개인키", '-----<가림 9자> PRIVATE KEY-----')]
    reports/evidence/m0/0c/scope.md:131: | **A8** | 불변: 인용 형식 위반 0 · 중복 id 0 · <가림 6자> 스캔 통과 · `git diff --check` 0 | agent-workflow §6, evidence-pack
값 형태 갈래((?i)([0-9]{3}-[0-9]{2}-[0-9]{5}|begin (rsa|ec|openssh)))에서 난 매치: 2 — 심어 둔 표본의 정의 줄 2 · 그 밖 0
심은 표본의 값 조각이 위 줄들에 가려지지 않고 남은 것: 0 (0 이라야 값이 이 파일에 실리지 않는다)
이 블록의 출력 중 자기 패턴에 걸리는 줄: 0 (0 이라야 자기 인용이 쌓이지 않는다)
PASS
exit=0
```

> **어디에 몇 줄이 걸렸는지는 블록이 낸다** — 산문이 열거하지 않는다. 열거를 산문에 두면
> 출처가 하나 늘 때마다 그 자리가 낡는다.
> **그 자리들이 진짜 값이 아니라는 확인은 사람이 전수로 연 것이고 매 실행이 되풀이하지
> 않는다.** 무엇이 어떤 까닭으로 걸렸는지도 **산문이 열거하지 않는다** — 블록이 낸 줄을
> 읽는다. 기계가 매 실행 되풀이하는 것은 **값 형태 갈래**(사업자번호 형태 · 개인키 머리)
> 뿐이며, 그 갈래에서 **심어 둔 표본 밖의 매치가 나면 `FAIL`**이다.
> **가림이 덮는 것은 `낱말 [:=] 값`과 낱말 바로 뒤에 붙는 값이다.** 그 꼴을 벗어난 자리
> (예로 값이 낱말보다 앞에 오는 줄)까지 덮는다고 주장하지 않는다. **덮는다고 적은 꼴에
> 대해서는 산문이 아니라 블록이 잰다** — 표본 여섯의 값 조각이 출력에 남으면 `FAIL`이다.
> **심어 둔 표본 여섯은 진짜 비밀값이 아니다** — 이 자리에서 지어낸 형태이고 어떤 계정·
> 채널·사업자에도 대응하지 않는다.
> **좌표는 선언 SHA 트리의 것이고 이 커밋의 편집으로 이동한다** — 자기 커밋의 내용을
> 좌표로 주장하지 않으므로 갱신하지 않는다.

---

## C-9 · `bid_target` 경로 모호성 (§10 **X-6**)

```
git -C bid-vector ls-tree -r --name-only ed4b06c | grep bid_target
echo "exit=$?"
```

```
app/ai/bid_target.py
app/services/bid_target_signals.py
tests/test_bid_target.py
tests/test_bid_target_integration.py
tests/test_bid_target_schema.py
tests/test_bid_target_signals.py
tests/test_bid_target_workflow.py
exit=0
```

---

## C-10 · 출력 블록의 **축어 재현** — 두 환경에서 잰다

**대상 파일** — **이 파일 자신**(읽는 체크아웃의 내용). **실행 트리** — `09d5840`
(C-1 ~ C-9가 선언한 SHA).

**그래서 이 검사는 「리뷰 대상 `commands.md`가 있는 체크아웃」에서 돌려야 한다.**
이 검사는 **대상 파일을 CWD에서 읽고 실행 트리만 SHA로 고정**하므로, 그 둘이 나르는
기록이 어긋나면 **다른 답이 나온다** — 실행 트리를 체크아웃한 곳에서 돌리는 것이 그런
경우다. `C-10.2`가 clean worktree에 **이 파일을 넣어** 돌리는 이유가 그것이다.

**실행 트리를 SHA로 고정해도 그것만으로 결과가 정해지지 않는다.** `bid-vector`와
`_workspace`는 **git이 추적하지 않아 SHA에서 복원할 수 없고**, 이 검사는 그 둘을
**실행 CWD에서** 가져다 연결한다. 그래서 **CWD에 그 둘이 있는지가 결과를 가른다.**

그 사실을 주장으로 적지 않고 **실행으로 잰다** — 아래 두 블록이 같은 명령을 두 환경에서
돌린 것이다.

**이 검사는 공유 git 상태를 건드리지 않는다** — `trap`은 자기 worktree만
`git worktree remove --force`로 걷고 **`git worktree prune`을 부르지 않는다.** prune은
**다른 레인이 같은 저장소에서 쓰는 worktree 등록까지 훑으므로** 검사가 부를 side effect가
아니다. 실제로 독립 검증자가 그 한 줄 때문에 이 두 블록을 **축어 그대로 돌리지 못하고
같은 알고리즘의 자기 하네스로 대신 쟀다.** 그 줄이 없으면 두 블록은 다른 레인이 worktree를
쓰는 중에도 그대로 돌릴 수 있다.

- **명령문에 `bid-vector`·`_workspace`가 나오는 블록**을 그 경로가 필요한 블록으로 본다.
  없으면 **`환경 부족`으로 표시하고 재지 않는다** — 잴 수 없는 것을 `불일치`로 세면
  결과가 오독된다.
- **이 절 자신은 표지(`SELF-EXCLUDE`-`C10`)로 걸러 뺀다.** 자기 출력을 자기가 재면
  결과가 자기 참조가 된다.
- 판정 어휘: **`PASS`**(전부 재고 불일치 0) · **`INCOMPLETE`**(재지 못한 블록이 있고
  잰 것은 전부 일치) · **`FAIL`**(불일치가 있다).

### C-10.1 두 경로가 **있는** 작업 트리에서

```
# SELF-EXCLUDE-C10 — 이 표지가 있는 블록은 검사에서 뺀다(자기 자신).
# 대상 파일 = 이 파일 자신(읽는 체크아웃의 내용) · 실행 트리 = 09d5840 (블록들이 선언한 SHA)
# bid-vector · _workspace 는 git 이 추적하지 않아 SHA 에서 복원할 수 없다.
# 실행 CWD 에 있으면 연결하고, 없으면 그 사실을 출력에 낸다.
WT="$(mktemp -d)/wt"
# 실패해도 worktree 를 남기지 않는다 — 검사가 영속 git metadata 를 남기면 안 된다.
trap 'git worktree remove --force "$WT" >/dev/null 2>&1' EXIT
git worktree add --detach "$WT" 09d5840 >/dev/null 2>&1
[ -d bid-vector ] && ln -sfn "$(cd bid-vector && pwd -P)" "$WT/bid-vector"
[ -d _workspace ] && ln -sfn "$(cd _workspace && pwd -P)" "$WT/_workspace"
cp reports/evidence/m0/0c/commands.md "$WT/.blocks.md"
python3 - "$WT" <<'PY'
import os, subprocess, sys, pathlib
WT = sys.argv[1]
FENCE = chr(96) * 3   # 리터럴 백틱 세 개를 쓰면 이 블록의 펜스가 끊긴다
SELF = "SELF-EXCLUDE" + "-C10"
NEEDS = ("bid-vector", "_workspace")   # git 미추적 — 명령문에 이 문자열이 있으면 그 경로가 필요하다
have = {n: os.path.isdir(os.path.join(WT, n)) for n in NEEDS}
print("환경 전제 — " + " · ".join(f"{n}: {'있음' if have[n] else '없음'}" for n in NEEDS))
lines = pathlib.Path(WT + "/.blocks.md").read_text().split("\n")
fences, open_at = [], None
for i, l in enumerate(lines):
    if l.strip() == FENCE:
        if open_at is None: open_at = i
        else: fences.append((open_at, i)); open_at = None
assert open_at is None and len(fences) % 2 == 0
env = {"PATH": "/usr/bin:/bin:/usr/sbin:/sbin:/opt/homebrew/bin",
       "LC_ALL": "en_US.UTF-8", "HOME": os.environ.get("HOME", "/tmp")}
same = diff = skip = self_n = 0
for k in range(0, len(fences), 2):
    (cs, ce), (os_, oe) = fences[k], fences[k+1]
    cmd = "\n".join(lines[cs+1:ce])
    head = cmd.splitlines()[0][:58].rstrip()   # 자른 끝의 공백은 뗀다 — 후행 공백은 A8 이 지적한다
    if SELF in cmd:
        self_n += 1
        print(f"  자기 제외              {head}")
        continue
    missing = [n for n in NEEDS if n in cmd and not have[n]]
    if missing:
        skip += 1
        print(f"  환경 부족({','.join(missing)})  {head}")
        continue
    r = subprocess.run(["bash", "-c", cmd], cwd=WT, capture_output=True,
                       text=True, errors="replace", env=env)
    ok = r.stdout.rstrip("\n") == "\n".join(lines[os_+1:oe]).rstrip("\n")
    same, diff = same + ok, diff + (not ok)
    print(("  축어 일치            " if ok else "  불일치              ") + head)
print(f"명령/출력 쌍: {len(fences)//2} · 자기 제외: {self_n} · 잰 것: {same+diff}"
      f" (축어 일치 {same} · 불일치 {diff}) · 환경 부족으로 못 잰 것: {skip}")
print("PASS" if diff == 0 and skip == 0 else ("INCOMPLETE" if diff == 0 else "FAIL"))
PY
git worktree remove --force "$WT" >/dev/null 2>&1
echo "exit=$?"
```

```
환경 전제 — bid-vector: 있음 · _workspace: 있음
  축어 일치            # diff 가 낸 줄을 그대로 실으면 이 파일이 후행 공백을 갖는다 — 표지로 바꿔 싣는다.
  축어 일치            for c in $(git log --format='%H' 2b05684..09d5840 \
  축어 일치            # 지적 줄을 그대로 실으면 이 파일이 다시 후행 공백을 갖는다 — 표지로 바꿔 싣는다.
  축어 일치            python3 - <<'PY'
  축어 일치            python3 - docs/discovery/data-dictionary.md <<'PY'
  축어 일치            python3 - docs/discovery/data-dictionary.md <<'PY'
  축어 일치            python3 - <<'PY'
  축어 일치            for c in $(git log --format='%H' 2b05684..09d5840 \
  축어 일치            grep -c '^| \*\*`OPEN-DIC-' docs/discovery/data-dictionary
  축어 일치            python3 - <<'PY'
  축어 일치            python3 - docs/discovery/data-dictionary.md <<'PY'
  축어 일치            git -C bid-vector grep -l 'cnstrtnAbltyEvlAmt' ed4b06c --
  축어 일치            git -C bid-vector grep -n 'default=0\.0\|server_default="0
  축어 일치            git -C bid-vector grep -n 'status = Column' ed4b06c \
  축어 일치            git -C bid-vector show ed4b06c:app/services/koneps/field_c
  축어 일치            python3 - <<'PY'
  축어 일치            git -C bid-vector ls-tree -r --name-only ed4b06c | grep bi
  자기 제외              # SELF-EXCLUDE-C10 — 이 표지가 있는 블록은 검사에서 뺀다(자기 자신).
  자기 제외              # SELF-EXCLUDE-C10 — 이 절의 실행 절차이므로 검사 대상에서 뺀다.
명령/출력 쌍: 19 · 자기 제외: 2 · 잰 것: 17 (축어 일치 17 · 불일치 0) · 환경 부족으로 못 잰 것: 0
PASS
exit=0
```

### C-10.2 두 경로가 **없는** clean worktree에서 — Codex 리뷰어 환경과 같은 형태

**위 블록의 명령 본문을 이 파일에서 그대로 뽑아** 두 경로가 없는 worktree 안에서 돌린다.
검사가 이 파일 자신을 읽으므로 그 worktree에 이 파일도 넣는다.

```
# SELF-EXCLUDE-C10 — 이 절의 실행 절차이므로 검사 대상에서 뺀다.
# C-10.1 의 명령 본문을 이 파일에서 그대로 뽑아 clean worktree 안에서 돌린다.
ROOT="$(pwd -P)"
CT="$(mktemp -d)/cleanwt"
# 실패해도 worktree 를 남기지 않는다 — 검사가 영속 git metadata 를 남기면 안 된다.
# 지우기 전에 CWD 를 돌려놓는다 — 안에 선 채로 지우면 이후 git 이 CWD 를 못 읽는다.
trap 'cd "$ROOT"; git worktree remove --force "$CT" >/dev/null 2>&1' EXIT
git worktree add --detach "$CT" 09d5840 >/dev/null 2>&1
cp reports/evidence/m0/0c/commands.md "$CT/reports/evidence/m0/0c/commands.md"
python3 - "$CT" <<'PY'
import pathlib, sys
F = chr(96) * 3
t = pathlib.Path("reports/evidence/m0/0c/commands.md").read_text().split("\n")
i = next(k for k, l in enumerate(t) if l.startswith("### C-10.1"))
j = next(k for k, l in enumerate(t) if l.startswith("### C-10.2"))
seg = t[i:j]
fz = [k for k, l in enumerate(seg) if l.strip() == F]
pathlib.Path(sys.argv[1] + "/.c10.sh").write_text("\n".join(seg[fz[0]+1:fz[1]]) + "\n")
PY
cd "$CT" && bash .c10.sh
```

```
환경 전제 — bid-vector: 없음 · _workspace: 없음
  환경 부족(_workspace)  # diff 가 낸 줄을 그대로 실으면 이 파일이 후행 공백을 갖는다 — 표지로 바꿔 싣는다.
  축어 일치            for c in $(git log --format='%H' 2b05684..09d5840 \
  축어 일치            # 지적 줄을 그대로 실으면 이 파일이 다시 후행 공백을 갖는다 — 표지로 바꿔 싣는다.
  축어 일치            python3 - <<'PY'
  축어 일치            python3 - docs/discovery/data-dictionary.md <<'PY'
  축어 일치            python3 - docs/discovery/data-dictionary.md <<'PY'
  축어 일치            python3 - <<'PY'
  축어 일치            for c in $(git log --format='%H' 2b05684..09d5840 \
  축어 일치            grep -c '^| \*\*`OPEN-DIC-' docs/discovery/data-dictionary
  축어 일치            python3 - <<'PY'
  환경 부족(bid-vector)  python3 - docs/discovery/data-dictionary.md <<'PY'
  환경 부족(bid-vector)  git -C bid-vector grep -l 'cnstrtnAbltyEvlAmt' ed4b06c --
  환경 부족(bid-vector)  git -C bid-vector grep -n 'default=0\.0\|server_default="0
  환경 부족(bid-vector)  git -C bid-vector grep -n 'status = Column' ed4b06c \
  환경 부족(bid-vector)  git -C bid-vector show ed4b06c:app/services/koneps/field_c
  축어 일치            python3 - <<'PY'
  환경 부족(bid-vector)  git -C bid-vector ls-tree -r --name-only ed4b06c | grep bi
  자기 제외              # SELF-EXCLUDE-C10 — 이 표지가 있는 블록은 검사에서 뺀다(자기 자신).
  자기 제외              # SELF-EXCLUDE-C10 — 이 절의 실행 절차이므로 검사 대상에서 뺀다.
명령/출력 쌍: 19 · 자기 제외: 2 · 잰 것: 10 (축어 일치 10 · 불일치 0) · 환경 부족으로 못 잰 것: 7
INCOMPLETE
exit=0
```

> **이 절이 재는 범위는 이 파일과 위 실행 트리, 그리고 위 두 환경뿐이다.** 다른 트리·
> 다른 환경에서 재현된다고 주장하지 않는다.
>
> **C-10.2가 재지 못한 블록은 한 갈래가 아니다** — 합쳐 적지 않고 갈래별로 적는다.
>
> - **`C-7.1`~`C-7.5`** — legacy 인용 축. `checklist.md` **A6**이 이 축을 두고
>   「이 slice가 유일한 확인 지점」이라 적는다.
> - **`C-1`** — 운영자 결정 사본 대조. `checklist.md` **A3**이 가리키는 자리이며 A6과 다른 축이다.
> - **`C-9`** — `bid_target` 경로 모호성의 재현. **`checklist.md`는 이 블록을 참조하지 않는다** —
>   가리키는 곳은 `data-dictionary.md` §10의 `X-6` 행이다.
