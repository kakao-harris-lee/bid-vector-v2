# M0 / 0C — 검증 명령과 출력

이 파일은 **명령과 그 stdout**을 담고, 각 블록에 **그 출력을 읽는 데 필요한 최소한의
범위·한계 각주**를 붙인다. **acceptance 대조는 `checklist.md`, 라운드 이력은 `scope.md`**에
있다 — 이 파일은 그 둘을 담지 않는다. **셈을 산문으로 옮겨 적지 않는다.**

**각 블록은 자기 실행 시점의 SHA를 선언한다.** 커밋은 자기 SHA를 담을 수 없으므로
**C-1 ~ C-9의 블록은 `ba23629`**(수정 라운드 2의 문면 수정 커밋)의 트리를 선언한다 —
그 SHA를 체크아웃한 worktree에서 재현된다.

**C-10만 예외다** — 그것은 다른 블록들이 실제로 그 트리에서 축어 재현되는지를 재는
검사이므로 **대상 파일과 실행 트리 두 SHA를 모두 명시**한다.

**출력은 명령의 stdout만 싣는다.** 로그인 프로파일 잡음이 stdout에 섞이면 재현이
깨지므로 `bash -c`로 돌린다.

**`ed4b06c`를 여는 블록(C-7·C-9)은 `bid-vector` symlink가 있어야 재현되고,
`_workspace`를 여는 블록(C-1)은 그 디렉터리가 있어야 재현된다.** 둘 다 git이 추적하지
않으므로 **SHA에서 복원할 수 없고, 그 두 경로가 없는 worktree에서는 재현되지 않는다.**
**C-10도 그 제약을 벗어나지 못한다** — C-10은 그 두 경로를 **실행 CWD에서** 가져다
연결하므로, CWD에 없으면 C-10 자신이 잴 수 없는 블록이 생긴다. **C-10이 두 환경에서
각각 무엇을 내는지는 그 절이 실행으로 낸다.**

---

## C-1 · 운영자 결정 사본이 원본과 같은가

선언 SHA `ba23629`. 원본은 `_workspace/`(gitignore 대상)라 이 블록은 그 디렉터리가
있는 작업 트리에서만 재현된다.

```
diff <(sed -n '281,564p' _workspace/m0-open-decisions/decisions-log.md) \
     <(sed -n '17,$p' reports/evidence/m0/0c/decisions-2026-08-28.md) \
  && echo "차이 0줄 — 사본이 원본과 일치"
echo "exit=$?"
```

```
차이 0줄 — 사본이 원본과 일치
exit=0
```

---

## C-2 · `in_scope` 밖 경로 변경과 공백 오류

**0D가 병행했으므로 `base_sha..HEAD`에는 0D의 커밋이 있다.** 그래서 이 검사는
**이 slice의 커밋을 먼저 고르고, 그 커밋이 건드린 경로 전부**를 본다 — 혼합 커밋이
있으면 여기서 드러난다.

### C-2.1 이 slice의 커밋과 그 커밋이 건드린 경로

선언 SHA `ba23629`.

```
for c in $(git log --format='%H' 2b05684..ba23629 \
             -- docs/discovery/data-dictionary.md reports/evidence/m0/0c/); do
  echo "-- $(git log --format='%h %s' -1 $c)"
  git show --name-only --format='' $c | sed '/^$/d' | sed 's/^/   /'
done
echo "### in_scope 밖 경로 (아래 줄이 '(없음)'이면 통과)"
for c in $(git log --format='%H' 2b05684..ba23629 \
             -- docs/discovery/data-dictionary.md reports/evidence/m0/0c/); do
  git show --name-only --format='' $c
done | sed '/^$/d' | sort -u \
     | grep -v -E '^(docs/discovery/data-dictionary\.md|reports/evidence/m0/0c/)' \
     || echo "(없음)"
```

```
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
다시 돌리려면 위 두 자리의 `ba23629`를 HEAD로 바꾼다.

### C-2.2 공백 오류

선언 SHA `ba23629`. **이 블록은 그 SHA까지의 range를 잰다** — 리뷰 시점의 HEAD로 다시
돌리려면 두 자리의 `ba23629`를 HEAD로 바꾼다.

```
# 지적 줄을 그대로 실으면 이 파일이 다시 후행 공백을 갖는다 — 표지로 바꿔 싣는다.
git diff --check 2b05684..ba23629 | sed 's/[[:space:]]\{1,\}$/<후행공백>/'
echo "git diff --check 지적: $(git diff --check 2b05684..ba23629 | wc -l | tr -d ' ')"
```

```
reports/evidence/m0/0c/commands.md:701: new blank line at EOF.
git diff --check 지적: 1
```

> **지적된 자리는 이 파일 자신이다** — `ba23629`가 C-10 절을 들어내면서 파일 끝에 빈 줄이
> 남았고, **C-10을 다시 넣는 다음 커밋에서 사라진다.** 그 시점의 초록 출력이 **C-2.3**이다.
> **판정은 `checklist.md` A8**에 있다.
>
> **이 블록이 선언 SHA에서 잡은 것은 이 파일의 상태이지 산출물의 결함이 아니다** —
> 같은 range를 `5a9a5f5`로 끊으면 **0**이고 그것이 C-2.3이다.

### C-2.3 공백 오류 — 초록 출력

**C-2.2가 선언한 SHA에는 지적이 있다.** 그 지적이 사라진 시점의 출력이 이것이다.
선언 SHA `5a9a5f5`.

```
git diff --check 2b05684..5a9a5f5 | sed 's/[[:space:]]\{1,\}$/<후행공백>/'
echo "git diff --check 지적: $(git diff --check 2b05684..5a9a5f5 | wc -l | tr -d ' ')"
echo "review_base 기준: $(git diff --check aff62ab..5a9a5f5 | wc -l | tr -d ' ')"
```

```
git diff --check 지적: 0
review_base 기준: 0
```

> **이 블록은 커밋 이력만 읽는다** — 작업 트리의 상태에 의존하지 않으므로 `bid-vector`나
> `_workspace`가 없어도 재현된다. 리뷰 시점의 HEAD로 다시 재려면 세 자리의 `5a9a5f5`를
> HEAD로 바꾼다.

---

## C-3 · 6축 커버 (A1)

선언 SHA `ba23629`. 스크립트 본문은 인라인이다.

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
### 절 수(### 기준): 45
### 6축 절 전부 존재: True
exit=0
```

---

## C-4 · 도메인 숫자 전수 — §12 밖에 정의 없는 숫자가 없는가 (A2)

사전 §12 「숫자 인덱스」의 **값 칸**을 정본으로 삼고, §12 밖 본문에서 그 목록에 없는
숫자 토큰을 찾는다. **셈·좌표·식별자는 마스크로 뺀다** — 무엇을 뺐는지는 `MASKS`가
한 줄씩 밝힌다. 선언 SHA `ba23629`.

```
python3 - docs/discovery/data-dictionary.md <<'PY'
import re, sys, pathlib
DOC = sys.argv[1]
text = pathlib.Path(DOC).read_text()
lines = text.split("\n")
# §12 「숫자 인덱스」 구간 = 정본. 그 구간의 첫 칸(값 칸)에서 허용 숫자를 모은다.
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
print(f"§12 값 칸이 허용하는 숫자 토큰: {len(allowed)}")
print(f"§12 밖 본문에서 §12에 없는 숫자 토큰: {len(found)}")
for tok in sorted(found):
    print(f"  {tok!r} @ 본문 행 {found[tok][:6]}")
print("PASS" if not found else "FAIL")
PY
echo "exit=$?"
```

```
문서: docs/discovery/data-dictionary.md
§12 값 칸이 허용하는 숫자 토큰: 52
§12 밖 본문에서 §12에 없는 숫자 토큰: 0
PASS
exit=0
```

> **이 검사의 한계**: 마스크는 **한 자리 수(`0`~`9`)를 빼지 않는다** — `0`·`1`은 §12에
> 등재해 통과시킨다. 마스크가 잘못 넓으면 진짜 도메인 숫자를 놓칠 수 있으므로
> **MASKS 각 줄에 무엇을 빼는지 적는다.**

---

## C-5 · `capability-map.md`에서 정책 version을 말하는 자리 전수 (A3 · §4.2)

선언 SHA `ba23629`.

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

선언 SHA `ba23629`.

```
for c in $(git log --format='%H' 2b05684..ba23629 \
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

선언 SHA `ba23629`.

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
§9 표의 OPEN-DIC 행 수: 4
OPEN-DIC-01 OPEN-DIC-02 OPEN-DIC-03 OPEN-DIC-04
§9 표 안의 중복 id: 0
```

### C-6.3 사전이 언급한 모든 `OPEN` id의 상태 분류

`capability-map.md` §12의 **표 첫 칸**과 `regression-ledger.md` §9의 첫 칸을 정본으로
삼는다. **한 id가 활성과 결정 완료 양쪽에 나오면 활성이 이긴다** — §12.2가 "라운드 7에
활성으로 복원", "임계 자체는 미결"이라 적는 행들이 그렇다. 선언 SHA `ba23629`.

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

신설: 4 · 활성: 15 · 결정 완료: 17 · 미분류: 0
exit=0
```

> **이 분류의 읽는 법과 항목별 처리는 `checklist.md` **A5**가 적는다** — 이 파일은
> 명령과 출력만 담는다. `OPEN-REG-05`·`OPEN-QUAL-10`·`OPEN-QUAL-11`이 「활성」으로 찍히는
> 것은 두 상류 파일의 행을 **고치지 않았기 때문**이며(out_of_scope), 사전 §11이 그것들을
> 결정 근거와 함께 등재해 닫는다. 상류 registry 갱신은 별도 slice의 몫이다.

---

## C-7 · legacy 인용 (A6)

`bid-vector` symlink가 있어야 재현된다. 선언 SHA `ba23629`.

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
legacy 인용(고유 (경로,범위)): 81
고유 경로: 35
경로 존재 + 행 범위 유효: 81
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

선언 SHA `ba23629`.

```
grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" \
  reports/evidence/m0/0c/ docs/discovery/data-dictionary.md
echo "exit=$?"
echo "--- 사업자/채널 식별자 패턴 ---"
grep -rnE "([0-9]{3}-[0-9]{2}-[0-9]{5}|chat_id|telegram[_-]?id|@[A-Za-z0-9_]{5,}bot)" \
  reports/evidence/m0/0c/ docs/discovery/data-dictionary.md \
  || echo "(매치 0)"
```

```
reports/evidence/m0/0c/scope.md:108:| **A8** | 불변: 인용 형식 위반 0 · 중복 id 0 · secret 스캔 통과 · `git diff --check` 0 | agent-workflow §6, evidence-pack 스킬 | `commands.md` **C-2** · **C-7** · **C-8** |
reports/evidence/m0/0c/commands.md:637:## C-8 · secret 스캔
reports/evidence/m0/0c/commands.md:642:grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" \
reports/evidence/m0/0c/commands.md:652:reports/evidence/m0/0c/scope.md:108:| **A8** | 불변: 인용 형식 위반 0 · 중복 id 0 · secret 스캔 통과 · `git diff --check` 0 | agent-workflow §6, evidence-pack 스킬 | `commands.md` **C-2** · **C-7** · **C-8** |
reports/evidence/m0/0c/commands.md:653:reports/evidence/m0/0c/commands.md:598:## C-8 · secret 스캔
reports/evidence/m0/0c/commands.md:654:reports/evidence/m0/0c/commands.md:603:grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" \
reports/evidence/m0/0c/commands.md:655:reports/evidence/m0/0c/commands.md:613:reports/evidence/m0/0c/scope.md:108:| **A8** | 불변: 인용 형식 위반 0 · 중복 id 0 · secret 스캔 통과 · `git diff --check` 0 | agent-workflow §6, evidence-pack 스킬 | `commands.md` **C-2** · **C-7** · **C-8** |
reports/evidence/m0/0c/commands.md:656:reports/evidence/m0/0c/commands.md:614:reports/evidence/m0/0c/commands.md:584:## C-8 · secret 스캔
reports/evidence/m0/0c/commands.md:657:reports/evidence/m0/0c/commands.md:615:reports/evidence/m0/0c/commands.md:589:grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" \
reports/evidence/m0/0c/commands.md:658:reports/evidence/m0/0c/commands.md:616:reports/evidence/m0/0c/commands.md:599:reports/evidence/m0/0c/scope.md:108:| **A8** | 불변: 인용 형식 위반 0 · 중복 id 0 · secret 스캔 통과 · `git diff --check` 0 | agent-workflow §6, evidence-pack 스킬 | `commands.md` **C-2** · **C-7** · **C-8** |
reports/evidence/m0/0c/commands.md:659:reports/evidence/m0/0c/commands.md:617:reports/evidence/m0/0c/commands.md:600:reports/evidence/m0/0c/commands.md:524:## C-8 · secret 스캔
reports/evidence/m0/0c/commands.md:660:reports/evidence/m0/0c/commands.md:618:reports/evidence/m0/0c/commands.md:601:reports/evidence/m0/0c/commands.md:529:grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" \
reports/evidence/m0/0c/commands.md:661:reports/evidence/m0/0c/commands.md:619:reports/evidence/m0/0c/commands.md:602:reports/evidence/m0/0c/commands.md:539:reports/evidence/m0/0c/scope.md:98:| **A8** | 불변: 인용 형식 위반 0 · 중복 id 0 · secret 스캔 통과 · `git diff --check` 0 | agent-workflow §6, evidence-pack 스킬 | `commands.md` **C-2** · **C-7** · **C-8** |
reports/evidence/m0/0c/commands.md:662:reports/evidence/m0/0c/commands.md:620:reports/evidence/m0/0c/commands.md:603:reports/evidence/m0/0c/commands.md:546:> 있는 `secret 스캔 통과`가 자기 패턴에 걸린 것이다. **이 파일이 커밋되면 위 정규식
reports/evidence/m0/0c/commands.md:663:reports/evidence/m0/0c/commands.md:621:reports/evidence/m0/0c/commands.md:604:reports/evidence/m0/0c/checklist.md:182:| secret 스캔 | `commands.md` **C-8** — 매치와 그 판정이 그 절에 있다 |
reports/evidence/m0/0c/commands.md:664:reports/evidence/m0/0c/commands.md:622:reports/evidence/m0/0c/commands.md:611:> `scope.md`·`checklist.md`의 acceptance 표에 있는 `secret 스캔 통과`, 이 절의 제목과
reports/evidence/m0/0c/commands.md:665:reports/evidence/m0/0c/commands.md:623:reports/evidence/m0/0c/checklist.md:182:| secret 스캔 | `commands.md` **C-8** — 매치와 그 판정이 그 절에 있다 |
reports/evidence/m0/0c/commands.md:666:reports/evidence/m0/0c/commands.md:631:> `scope.md`·`checklist.md`의 acceptance 표에 있는 `secret 스캔 통과`, 이 절의 제목과
reports/evidence/m0/0c/commands.md:667:reports/evidence/m0/0c/commands.md:723:  축어 일치  grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RS
reports/evidence/m0/0c/commands.md:668:reports/evidence/m0/0c/checklist.md:182:| secret 스캔 | `commands.md` **C-8** — 매치와 그 판정이 그 절에 있다 |
reports/evidence/m0/0c/commands.md:677:> `scope.md`·`checklist.md`의 acceptance 표에 있는 `secret 스캔 통과`, 이 절의 제목과
reports/evidence/m0/0c/checklist.md:182:| secret 스캔 | `commands.md` **C-8** — 매치와 그 판정이 그 절에 있다 |
exit=0
--- 사업자/채널 식별자 패턴 ---
reports/evidence/m0/0c/commands.md:646:grep -rnE "([0-9]{3}-[0-9]{2}-[0-9]{5}|chat_id|telegram[_-]?id|@[A-Za-z0-9_]{5,}bot)" \
reports/evidence/m0/0c/commands.md:671:reports/evidence/m0/0c/commands.md:607:grep -rnE "([0-9]{3}-[0-9]{2}-[0-9]{5}|chat_id|telegram[_-]?id|@[A-Za-z0-9_]{5,}bot)" \
reports/evidence/m0/0c/commands.md:672:reports/evidence/m0/0c/commands.md:626:reports/evidence/m0/0c/commands.md:593:grep -rnE "([0-9]{3}-[0-9]{2}-[0-9]{5}|chat_id|telegram[_-]?id|@[A-Za-z0-9_]{5,}bot)" \
reports/evidence/m0/0c/commands.md:673:reports/evidence/m0/0c/commands.md:627:reports/evidence/m0/0c/commands.md:607:reports/evidence/m0/0c/commands.md:533:grep -rnE "([0-9]{3}-[0-9]{2}-[0-9]{5}|chat_id|telegram[_-]?id|@[A-Za-z0-9_]{5,}bot)" \
```

> **매치는 전부 그 단어를 말하는 산문이거나 위 정규식 자체이고 값이 아니다** —
> `scope.md`·`checklist.md`의 acceptance 표에 있는 `secret 스캔 통과`, 이 절의 제목과
> 판정 문단, 그리고 두 grep의 패턴 문자열이다. 둘째 스캔이 내는 한 줄도 자기 정규식이다.
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

