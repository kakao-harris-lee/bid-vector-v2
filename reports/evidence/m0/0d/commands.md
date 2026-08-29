# 검증 명령과 출력 — M0 / 0D

**이 파일은 명령과 그 출력만 담는다.** 라운드 이력·자기평가 산문은
`reports/evidence/m0/0d/scope.md`에 있다. 판정은 각 블록 아래 한두 줄이며,
**셈과 좌표를 산문으로 옮기지 않는다** — 수는 그것을 내는 명령이 낸다.

## 선언 SHA와 실행 계약

**출력 블록은 `5012bde` 트리에서 뜬 것이다** — **예외는 아래 표의 「재현 래칫」 열이
`제외`인 블록들이고, 그 열의 값을 사람이 정하지 않는다.** C-11의 스크립트가 명령 블록의
문면에서 정하며, **무엇이 어느 사유로 빠졌는지는 C-11 출력이 쌍마다 낸다.**
커밋 안의 블록은 자기 커밋 트리에서 뜰 수 없다 — 그래서 선언 SHA를 **`5012bde`로 그대로
지목한다.** 다른 커밋과의 관계로 부르지 않는다: 관계는 뒤 편집이 들어올 때마다 낡는다.

**제외의 축은 「트리를 고정하면 답도 고정되는가」다.** 선언 SHA 트리 밖의 상태를 읽는
블록은 그 트리의 함수가 아니므로 **트리 재현 래칫이 잴 수 있는 대상이 아니다** — 같은
트리에서 오늘 뜬 값이 내일 다르게 뜬다. 그런 블록의 기록을 지우지는 않는다.
`codex-review-gate`가 preflight 결과와 reviewer 메타를 **그 slice의 `commands.md`에**
남기라고 요구하기 때문이다. **재현되는 기록으로 적지 않고 시점 기록으로 적을 뿐이며**,
그 블록들은 자기 출력에 뜬 시각(`taken_at`)을 함께 낸다.

| 블록 | 읽는 것 | 선언 SHA | 재현 래칫 |
| --- | --- | --- | --- |
| C-1 · C-2 · C-3 · C-3n · C-4 · C-7 · C-7n · C-8 · C-9 · C-10 | 이 저장소 | **`5012bde`** | 대조 |
| C-5.1 ~ C-5.5 | legacy `bid-vector`만 | **`ed4b06c`** (이 저장소의 어느 HEAD에서도 같다) | 대조 |
| C-6 · C-6n | **양쪽** — `citescan.py`가 `docs/adr`(저장소)와 legacy를 함께 읽는다 | **`5012bde` + `ed4b06c`** | 대조 |
| C-12.1 · C-12.2 | **양쪽** — 리뷰 레인 산출물(`_workspace/`)·`~/.codex` 실물과 **등재된 verdict JSON**(이 저장소) | **`5012bde`** + 저장소 밖 실물 | **제외 `EXT`** — 트리의 함수가 아니다 |
| C-11 | 이 파일 자신 ↔ 선언 SHA 트리 | **HEAD** (그 블록이 사유를 적는다) | **제외 `SELF`** |

### 실행 계약

1. **저장소 루트에서 실행한다.** 각 블록은 루트에서 시작한다 — 블록 안의 `cd bid-vector`는
   그 블록에서만 유효하다고 보고, 다음 블록 전에 루트로 돌아온다.
2. **한 셸에서 순서대로 실행한다.** `$T`(스캐너 임시 디렉터리)와 `$T0`(C-2가 만드는 것)는
   **앞 블록이 만들고 뒤 블록이 쓴다** — C-3n은 C-3의 `$T`를, C-6n은 C-6의 `$T`를,
   C-7n은 C-7의 `$T`를, C-4·C-8은 C-2의 `$T0`를 쓴다.
3. **legacy는 `bid-vector` symlink 아래이며 읽기 전용으로만 접근했다**
   (`git show` · `git ls-tree` · `git grep` · `git cat-file -e`).
4. **셸 주의**: 이 저장소의 기본 셸은 zsh이고 **zsh는 따옴표 없는 변수 확장을 단어 분할하지
   않는다.** 아래 명령은 디렉터리 세 개를 변수에 담지 않고 **매번 명시적으로 나열**한다.
   블록 실행에 쓴 셸은 `bash`다(C-11).

**C-11이 이 선언을 실행으로 확인한다** — 명령/출력 쌍을 선언 SHA의 트리에 대고 축어
대조하고, **위 표의 「재현 래칫」 열을 자기가 다시 계산한다.** 전체 쌍 수·대조한 수·사유별로
빠진 수는 그 출력의 마지막 줄이 내고, 어느 쌍이 어느 토큰 때문에 빠졌는지는 그 위의 `SKIP`
행이 낸다. **표와 출력이 갈라지면 출력이 이긴다.**

**이 절이 「출력 블록이 선언 SHA 트리에서 다시 뜬다 · 무엇이 예외인가」 규약의 정본이다**
— 다른 파일은 이 규약을 스스로 정하지 않는다. 요약해 적는 자리는 **여기를 지목하고**,
문면이 갈라지면 이 절과 C-11 출력이 이긴다.

---

## C-1 · base / head / 작업 트리

```
git rev-parse HEAD
git status --porcelain | grep -vc '^??' | sed 's/^/tracked_dirty=/'
```

```
5012bdee07d43a9a38787edda2279f1d7abf335c
tracked_dirty=0
```

> `tracked_dirty`는 **추적 파일의 미커밋 변경 수**다 — untracked(`??`)는 세지 않는다.
> 이 저장소의 브랜치는 다른 slice와 공유되므로 untracked에는 그 slice의 산출물이 들어온다.
> **base는 `998dc21`로 불변**이고, 이 slice의 커밋이 `in_scope` 안에 있다는 것은 C-2가 낸다.

---

## C-2 · in_scope 밖 경로 · 공백 오류

```
T0=$(mktemp -d)
git log --format='%H %s' 998dc21..HEAD | grep -c 'm0-0d' | sed 's/^/slice_commits=/'
git log --format='%H %s' 998dc21..HEAD | grep 'm0-0d' | cut -d' ' -f1 | git show --stdin --name-only --format= | sort -u | grep -c . | sed 's/^/slice_paths=/'
git log --format='%H %s' 998dc21..HEAD | grep 'm0-0d' | cut -d' ' -f1 | git show --stdin --name-only --format= | sort -u | grep -cvE '^(docs/adr/|reports/evidence/m0/0d/)' | sed 's/^/out_of_scope_paths=/'
git diff --check 998dc21...HEAD -- docs/adr reports/evidence/m0/0d | wc -l | sed 's/^ *//;s/^/whitespace_problems=/'
```

```
slice_commits=51
slice_paths=14
out_of_scope_paths=0
whitespace_problems=0
```

**판정**: 이 slice의 커밋이 건드린 경로에 `in_scope` 밖이 없고, 그 경로들에 공백 오류가
없다. **목록도 셈도 여기 옮겨 적지 않는다** — 경로 목록이 궁금하면 둘째 명령에서
`grep -c .`를 지우면 그 명령이 목록을 낸다.

> **이 저장소의 브랜치는 다른 slice와 공유된다.** 그래서 이 블록은 `base...HEAD` **범위**가
> 아니라 **이 slice의 커밋이 건드린 경로**를 잰다 — 범위로 재면 다른 slice의 경로가
> 섞여 `out_of_scope`가 그 slice 탓으로 오른다. 커밋 선별자는 **제목의 `m0-0d` 태그**이며,
> `slice_commits`가 그 선별의 크기를 함께 낸다.
> 공백 오류만은 **누적 diff를 `in_scope` 경로로 한정**해 잰다 — 중간 커밋이 넣었다가
> 뒤 커밋이 지운 것은 최종 상태에 없기 때문이다.

---

## C-3 · ADR 구조 — 9건, 필수 절, 번호 유일·연속

스크립트는 이 블록 안에 전부 있다. 외부 파일에 의존하지 않는다.

```
T=$(mktemp -d)
cat > "$T/adrscan.py" <<'PY'
#!/usr/bin/env python3
"""ADR 구조 스캐너 — 각 파일이 필수 절을 갖는지, 번호가 유일·연속인지.
필수: `- **상태**:` 줄 + 제목에 맥락/결정/대안/결과 를 포함하는 `## ` 절 각 1개 이상."""
import re, sys, pathlib
root = pathlib.Path(sys.argv[1])
need = [("STATUS", None), ("CONTEXT", "맥락"), ("DECISION", "결정"),
        ("ALT", "대안"), ("CONSEQ", "결과")]
nums = []
for p in sorted(root.glob("*.md")):
    text = p.read_text(encoding="utf-8")
    heads = [ln for ln in text.splitlines() if ln.startswith("## ")]
    cells = []
    for name, word in need:
        if word is None:
            ok = any(re.match(r"^- \*\*상태\*\*:", ln) for ln in text.splitlines())
        else:
            ok = any(word in h for h in heads)
        cells.append(f"{name}={'OK' if ok else 'MISSING'}")
    m = re.match(r"^(\d{4})-", p.name)
    n = int(m.group(1)) if m else -1
    nums.append(n)
    print("\t".join([f"{n:04d}" if n >= 0 else "????", p.name] + cells))
print(f"files={len(nums)}")
print(f"numbers_unique={'yes' if len(set(nums)) == len(nums) else 'no'}")
print(f"numbers_contiguous_from_1={'yes' if sorted(nums) == list(range(1, len(nums)+1)) else 'no'}")
PY
python3 "$T/adrscan.py" docs/adr
```

```
0001	0001-target-architecture.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0002	0002-money-rate-basis.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0003	0003-contract-transport.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0004	0004-persistence-and-events.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0005	0005-domain-events-and-outbox.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0006	0006-gradle-modules.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0007	0007-test-pyramid-and-ratchet.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0008	0008-frontend-disposition.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
0009	0009-ml-reuse-provenance.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
files=9
numbers_unique=yes
numbers_contiguous_from_1=yes
```

### C-3n · 이 스캐너가 무엇을 잡는지 — 실행으로 잰다

검사를 만들었으면 그 능력을 **실행으로 재고 나서** 적는다.

```
# adrscan.py 는 C-3 블록이 만든 "$T" 의 것을 그대로 쓴다 — T 를 덮어쓰지 않는다
N=$(mktemp -d)
printf '# ADR 0001 — neg\n\n## 1. 맥락\n\n## 2. 결정\n' > "$N/0001-a.md"
printf '# ADR 0003 — neg\n\n- **상태**: x\n\n## 1. 맥락\n\n## 2. 결정\n\n## 3. 대안\n\n## 4. 결과\n' > "$N/0003-b.md"
python3 "$T/adrscan.py" "$N"
```

```
0001	0001-a.md	STATUS=MISSING	CONTEXT=OK	DECISION=OK	ALT=MISSING	CONSEQ=MISSING
0003	0003-b.md	STATUS=OK	CONTEXT=OK	DECISION=OK	ALT=OK	CONSEQ=OK
files=2
numbers_unique=yes
numbers_contiguous_from_1=no
```

**판정**: 스캐너가 결측 절과 번호 불연속을 실제로 잡는다.
**잡지 못하는 것**: 절의 **내용**. 제목에 그 단어가 있으면 통과하며, 그 절이 실제로
결정을 담는지는 사람이 읽는다.

---

## C-4 · 확정 산출물 무변경

```
git log --format='%H %s' 998dc21..HEAD | grep 'm0-0d' | cut -d' ' -f1 | git show --stdin --name-only --format= | sort -u > "$T0/slice_paths.txt"
grep -cE '^docs/discovery/' "$T0/slice_paths.txt" | sed 's/^/discovery_touched=/'
grep -cE '^reports/evidence/m0/0(a|b|c)' "$T0/slice_paths.txt" | sed 's/^/other_slice_evidence_touched=/'
grep -cE '^(\.claude/|fixtures/)' "$T0/slice_paths.txt" | sed 's/^/harness_or_fixtures_touched=/'
```

```
discovery_touched=0
other_slice_evidence_touched=0
harness_or_fixtures_touched=0
```

**판정**: 이 slice의 커밋이 `docs/discovery/`·다른 slice의 evidence·하네스·fixtures를
하나도 건드리지 않았다. 따라서 **활성 OPEN의 등록 상태를 이 slice가 바꾸지 않았다.**
ADR 본문이 활성 OPEN을 확정 서술로 선점했는지는 기계가 아니라 사람이 판정한다 —
그 자리를 C-9.5와 C-9.6이 낸다.

> `$T0`는 C-2 블록이 만든 임시 디렉터리다(아래 「실행 계약」).

---

## C-5 · ML 재활용 래칫 사전 조사 (legacy `ed4b06c`)

기준은 `v2-지침서.md` §5의 **함수 50줄 · 파일 500줄**이다.

### C-5.1 · 파일 축 — 세 앵커 디렉터리

```
cd bid-vector
git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release | wc -l | sed 's/^ *//;s/^/files_total=/'
for p in $(git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release); do
  n=$(git show "ed4b06c:$p" | wc -l | tr -d ' '); [ "$n" -gt 500 ] && echo "OVER $p $n"
done | wc -l | sed 's/^ *//;s/^/files_over_500=/'
for p in $(git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release); do git show "ed4b06c:$p" | wc -l; done \
  | awk '{s+=$1; if($1>m) m=$1} END {print "lines_total="s; print "max_file_lines="m}'
for p in $(git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release); do
  [ "$(git show "ed4b06c:$p" | tail -c 1 | xxd -p)" = "0a" ] || echo "NO-TRAILING-NEWLINE $p"
done | wc -l | sed 's/^ *//;s/^/files_without_trailing_newline=/'
```

```
files_total=56
files_over_500=0
lines_total=13388
max_file_lines=491
files_without_trailing_newline=0
```

**계측 정의(파일 축)**: `wc -l`은 개행 수를 센다. 마지막 줄에 개행이 없으면 1을 덜
세므로 마지막 명령이 그 조건을 확인한다.

### C-5.2 · 함수 축 — 세 앵커 디렉터리

스크립트는 이 블록 안에 전부 있다. **계측 정의는 그 docstring에 있다.**

```
cd bid-vector
T=$(mktemp -d)
cat > "$T/funclines.py" <<'PY'
#!/usr/bin/env python3
"""stdin 의 Python 소스에서 함수 크기를 잰다.

계측 정의 — 이 수를 재현하려면 아래가 같아야 한다.
  lines = node.end_lineno - node.lineno + 1   (ast.FunctionDef / ast.AsyncFunctionDef)
  * 데코레이터 제외 — CPython ast 는 lineno 를 `def` 줄로 잡는다. decorator_list 를 더하지 않는다.
  * docstring 포함 — 본문 statement 다.
  * 함수 안의 주석·빈 줄 포함 — span 은 순수 위치 기반이다.
  * `def` 줄 자체 포함 — 그것이 +1 의 정체다.
  * 중첩 함수·메서드는 각각 별도 항목으로 보고하며 바깥 함수 span 안에도 남는다(중복 제거 없음).
  * lambda · 클래스 본문 · 모듈 레벨 코드는 보고하지 않는다.
사용: git show <sha>:<path> | python3 funclines.py <path> [<min_lines>]
출력 TSV: path <TAB> start <TAB> end <TAB> lines <TAB> qualname
"""
import ast
import sys

path = sys.argv[1]
low = int(sys.argv[2]) if len(sys.argv) > 2 else 1
tree = ast.parse(sys.stdin.read(), filename=path)
rows = []


def walk(node, prefix):
    for child in ast.iter_child_nodes(node):
        if isinstance(child, (ast.FunctionDef, ast.AsyncFunctionDef)):
            qual = prefix + child.name
            rows.append((child.lineno, child.end_lineno,
                         child.end_lineno - child.lineno + 1, qual))
            walk(child, qual + ".")
        elif isinstance(child, ast.ClassDef):
            walk(child, prefix + child.name + ".")


walk(tree, "")
for start, end, n, qual in sorted(rows, key=lambda r: -r[2]):
    if n >= low:
        print("%s\t%d\t%d\t%d\t%s" % (path, start, end, n, qual))
PY
for p in $(git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release); do
  git show "ed4b06c:$p" | python3 "$T/funclines.py" "$p" 1
done > "$T/all_funcs.tsv"
wc -l < "$T/all_funcs.tsv" | sed 's/^ *//;s/^/functions_total=/'
awk -F'\t' '$4>50' "$T/all_funcs.tsv" | wc -l | sed 's/^ *//;s/^/functions_over_50=/'
awk -F'\t' '$4>100' "$T/all_funcs.tsv" | wc -l | sed 's/^ *//;s/^/functions_over_100=/'
awk -F'\t' '$4>50 {c[$1]++} END {print "files_with_over_50="length(c)}' "$T/all_funcs.tsv"
awk -F'\t' 'NR==1||$4>m{m=$4} END {print "max_function_lines="m}' "$T/all_funcs.tsv"
awk -F'\t' '$4>50 {split($1,a,"/"); print a[1]"/"a[2]"/"a[3]}' "$T/all_funcs.tsv" | sort | uniq -c | awk '{print $2"\tover_50="$1}'
```

```
functions_total=360
functions_over_50=29
functions_over_100=4
files_with_over_50=14
max_function_lines=134
app/ai/predictors	over_50=7
app/services/ml_release	over_50=16
app/services/ml_training	over_50=6
```

**50줄 초과 전수** — 위 블록에 이어서:

```
awk -F'\t' '$4>50 {printf "%s:%d-%d\t%d\t%s\n", $1,$2,$3,$4,$5}' "$T/all_funcs.tsv" | sort -t$'\t' -k2 -nr
```

```
app/ai/predictors/ensemble.py:188-321	134	build_ensemble_prediction_payload
app/ai/predictors/historical/__init__.py:284-405	122	build_historical_prediction
app/ai/predictors/historical/base_rate.py:127-246	120	apply_high_rate_distribution_adjustment
app/services/ml_training/dataset_quality.py:31-149	119	DatasetQualityMixin._build_dataset_quality_report
app/ai/predictors/historical/summary.py:27-126	100	summarize_historical_records
app/services/ml_training/comparison.py:47-145	99	ComparisonMixin._build_artifact_comparison_report
app/services/ml_release/manifest.py:26-122	97	_ManifestLifecycleMixin.create_release_manifest
app/services/ml_release/storage/preflight.py:342-437	96	_ObjectStoragePreflightMixin._preflight_s3_write_probe
app/services/ml_release/gate.py:74-169	96	_PromotionGateMixin._build_predictor_promotion_gate
app/services/ml_release/gate.py:171-261	91	_PromotionGateMixin._extract_predictor_gate_metrics
app/services/ml_training/calibration.py:97-182	86	CalibrationMixin._fit_group_probability_curve
app/ai/predictors/historical/base_rate.py:40-124	85	select_competitive_base_rate
app/services/ml_training/comparison.py:204-284	81	ComparisonMixin._evaluate_predictor
app/services/ml_release/rollout.py:106-186	81	_RolloutMixin.publish_release_manifest
app/services/ml_release/rollout.py:26-104	79	_RolloutMixin.apply_release_manifest
app/services/ml_release/storage/preflight.py:78-155	78	_ObjectStoragePreflightMixin._preflight_file_storage
app/services/ml_release/storage/preflight.py:266-340	75	_ObjectStoragePreflightMixin._preflight_s3_bucket_access
app/services/ml_release/preflight.py:380-452	73	_PreflightMixin._manifest_artifact_preflight_checks
app/services/ml_training/orchestration.py:27-96	70	OrchestrationMixin.train_price_predictor
app/services/ml_training/calibration.py:16-78	63	CalibrationMixin._build_probability_calibration
app/ai/predictors/historical/reserve.py:10-72	63	resolve_reserve_prior_rate
app/services/ml_release/preflight.py:145-206	62	_PreflightMixin._load_rollout_manifest
app/services/ml_release/rollout.py:316-374	59	_RolloutMixin.trigger_remote_embedding_rebuild
app/ai/predictors/historical/__init__.py:223-281	59	build_heuristic_prediction
app/services/ml_release/storage/preflight.py:21-76	56	_ObjectStoragePreflightMixin.preflight
app/services/ml_release/storage/preflight.py:157-212	56	_ObjectStoragePreflightMixin._preflight_s3_storage
app/services/ml_release/storage/preflight.py:214-264	51	_ObjectStoragePreflightMixin._build_s3_client
app/services/ml_release/manifest.py:214-264	51	_ManifestLifecycleMixin.write_manifest_env_file
app/services/ml_release/gate.py:302-352	51	_PromotionGateMixin._build_predictor_gate_thresholds
```

**결합 축** — `v2-지침서.md` §3.2가 이식 경계에서 잘라내라고 한 것. **각 축의 판정 근거는
그 축 옆의 정규식이며, 다른 정규식은 다른 답을 낼 수 있다**:

```
cd bid-vector
for spec in \
  'ORM/session|sqlalchemy|orm\.Session|from sqlalchemy' \
  'DB query|\.query\(' \
  '설정 객체|from app\.core\.config import|settings\.' \
  'Celery|celery|shared_task|apply_async|\.delay\(' \
  '로깅|^import logging|getLogger' \
  '인프라(subprocess/urllib/boto3/botocore)|^import subprocess|^import urllib|boto3|botocore' ; do
  name="${spec%%|*}"; pat="${spec#*|}"
  echo "-- $name   [$pat]"
  for p in $(git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release); do
    git show "ed4b06c:$p" | grep -qE "$pat" && echo "   $p"
  done
  echo "   ---"
done
true
```

```
-- ORM/session   [sqlalchemy|orm\.Session|from sqlalchemy]
   app/services/ml_release/rollout.py
   app/services/ml_training/award_landing_dataset.py
   app/services/ml_training/orchestration.py
   ---
-- DB query   [\.query\(]
   app/services/ml_training/award_landing_dataset.py
   ---
-- 설정 객체   [from app\.core\.config import|settings\.]
   app/ai/predictors/award_rate_gbm.py
   app/ai/predictors/distribution.py
   app/ai/predictors/ensemble.py
   app/ai/predictors/historical/base_rate.py
   app/ai/predictors/historical/calibration.py
   app/ai/predictors/historical/reserve.py
   app/services/ml_release/base.py
   app/services/ml_release/gate.py
   app/services/ml_release/manifest.py
   app/services/ml_release/preflight.py
   app/services/ml_release/rollout.py
   app/services/ml_release/signing.py
   app/services/ml_training/comparison.py
   app/services/ml_training/dataset_quality.py
   ---
-- Celery   [celery|shared_task|apply_async|\.delay\(]
   ---
-- 로깅   [^import logging|getLogger]
   app/ai/predictors/artifact_contracts.py
   app/ai/predictors/historical/calibration.py
   ---
-- 인프라(subprocess/urllib/boto3/botocore)   [^import subprocess|^import urllib|boto3|botocore]
   app/services/ml_release/__init__.py
   app/services/ml_release/rollout.py
   app/services/ml_release/storage/preflight.py
   app/services/ml_release/storage/transfer.py
   ---
```

**판정**:

- **`Celery` 축이 비어 있는 것은 결합이 없다는 뜻이 아니다.** 결합의 방향이 바깥→안이라
  이 grep으로는 잡히지 않는다 — legacy의 Celery task가 `ml_training` 서비스를 부른다.
  **import 스캔의 한계이며 이 축은 호출자 쪽에서 봐야 한다.**
- **「업무 판정」 축은 이 명령들이 잡지 못한다.** import 문자열이 아니라 의미의 문제다.
  그 축의 후보는 ADR 0001 §5의 `OPEN-ADR-03`·`OPEN-ADR-04`·`OPEN-ADR-05`가 들고 있다.
- 설정 객체 축의 파일 목록은 위 명령이 낸다 — **여기 옮겨 적지 않는다.**

### C-5.2c · ADR 0001 §5의 `OPEN` 후보가 딛는 실측

```
cd bid-vector
echo "-- (a) 규칙표 4파일의 외부 import (app.* 만)"
for f in legal_floor_spec procurement_band_rules rate_band_spec blend_tables; do
  printf 'app/ai/predictors/%s.py\tapp_imports=%s\n' "$f" \
    "$(git show "ed4b06c:app/ai/predictors/$f.py" | grep -cE '^(from|import) app\.')"
done
for f in legal_floor_spec procurement_band_rules rate_band_spec blend_tables; do
  git show "ed4b06c:app/ai/predictors/$f.py" | grep -nE '^(from|import) app\.' | sed "s|^|app/ai/predictors/$f.py:|"
done
echo "-- (b) award_landing_ladder 가 부르는 업무 술어 import"
git show ed4b06c:app/services/ml_training/award_landing_ladder.py | grep -nE 'floor_applicability|price_regime|floor_shortfall|published_floor_rate|reliable_base|legal_floor_spec' | head -30
echo "-- (c) 8개 커널을 import 하는 세 디렉터리 파일"
for p in $(git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release); do
  git show "ed4b06c:$p" | grep -qE 'from app\.domain\.(award_margin_distribution|award_landing_curve|award_rate_features|award_landing_distribution|assessment_shrinkage|reserve_draw_distribution|settlement_maturity|award_landing_curve_builders)' && echo "   $p"
done
for p in $(git ls-tree -r --name-only ed4b06c -- app/ai/predictors app/services/ml_training app/services/ml_release); do
  git show "ed4b06c:$p" | grep -qE 'from app\.domain\.(award_margin_distribution|award_landing_curve|award_rate_features|award_landing_distribution|assessment_shrinkage|reserve_draw_distribution|settlement_maturity|award_landing_curve_builders)' && echo x
done | wc -l | sed 's/^ *//;s/^/kernel_consumer_files=/'
```

```
-- (a) 규칙표 4파일의 외부 import (app.* 만)
app/ai/predictors/legal_floor_spec.py	app_imports=1
app/ai/predictors/procurement_band_rules.py	app_imports=0
app/ai/predictors/rate_band_spec.py	app_imports=0
app/ai/predictors/blend_tables.py	app_imports=0
app/ai/predictors/legal_floor_spec.py:41:from app.utils.numeric import optional_float
-- (b) award_landing_ladder 가 부르는 업무 술어 import
16:게시 하한 개연           :func:`app.domain.published_floor_rate.plausible_published_floor_rate`
17:적용범위(기관 축)        :func:`app.ai.floor_applicability.is_floor_judgeable` (#274/#296)
20:basis clean              :func:`app.domain.reliable_base.get_reliable_base` (#199/#358)
22:사정률 개연              :func:`app.domain.floor_shortfall.is_plausible_assessment_rate`
39:로 세 축을 만든다. ``a`` 를 이 비로 얻는 것은 :func:`app.services.floor_shortfall.
59:from app.ai.floor_applicability import is_floor_judgeable, resolve_floor_applicability
66:from app.ai.price_prediction.price_regime import (
68:    _build_price_regime_features,
70:from app.ai.predictors.legal_floor_spec import CONSTRUCTION_FLOOR_REVISION_DATE
73:from app.domain.floor_shortfall import is_plausible_assessment_rate
74:from app.domain.published_floor_rate import plausible_published_floor_rate
76:from app.domain.reliable_base import ReliableBaseSource, get_reliable_base
275:    ``_build_price_regime_features`` 를 직접 부르는 것이 요점이다. 규칙표만 빌려 와
281:    features = _build_price_regime_features(
290:    return str(features["price_regime_label"])
325:    floor_rate = plausible_published_floor_rate(floor_rate_fraction)
328:    if not is_floor_judgeable(resolve_floor_applicability(agency_name)):
344:    reliable = get_reliable_base(
351:        # floor_shortfall 의 표본 규칙과 같은 스탠스로 여기서도 clean 만 받는다.
359:    reliable_base = float(reliable.value)
360:    omega_direct = award_amount / reliable_base
366:            base_amount=reliable_base,
-- (c) 8개 커널을 import 하는 세 디렉터리 파일
   app/ai/predictors/award_rate_gbm.py
   app/ai/predictors/distribution.py
   app/ai/predictors/distribution_extraction.py
   app/services/ml_training/award_landing_curves.py
   app/services/ml_training/award_landing_ladder.py
   app/services/ml_training/award_landing_report.py
   app/services/ml_training/award_rate_backtest_report.py
   app/services/ml_training/award_rate_gbm.py
   app/services/ml_training/award_rate_holdout.py
   app/services/ml_training/award_rate_windows.py
kernel_consumer_files=10
```

**판정**:

- **(a)** 규칙표 넷 중 `app.*` import를 가진 것은 `legal_floor_spec.py` 하나이고 그것도
  `app.utils.numeric` 한 줄이다 — `OPEN-ADR-04`(수학 커널인가 정책 데이터인가)가 딛는 실측.
- **(b)** `award_landing_ladder.py`가 업무 술어 여섯을 import하고 그 호출 지점이 본문에
  있다 — `OPEN-ADR-05`가 딛는 실측. **`head -30`으로 잘랐으므로 이 출력이 전수가 아니다.**
- **(c)** 8개 커널을 import하는 세 디렉터리 파일이 열이다 — ADR 0009 §1.2가 인용한다.

---

### C-5.2d · mixin 합성 클래스의 실제 크기 (ADR 0007 §1.3)

파일 축이 통과해도 **합성된 클래스**는 한도를 넘는다. legacy가 그 분해를 스스로
문서화한 자리는 ADR 0001 §4.3이 인용한다(`app/services/ml_release/__init__.py:1-10`).

```
cd bid-vector
for f in base signing gate preflight manifest rollout; do
  printf 'app/services/ml_release/%s.py\t%s\n' "$f" "$(git show "ed4b06c:app/services/ml_release/$f.py" | wc -l | tr -d ' ')"
done
for f in base signing gate preflight manifest rollout; do git show "ed4b06c:app/services/ml_release/$f.py" | wc -l; done \
  | awk '{s+=$1} END {print "MLReleasePromotionService_composed_lines="s}'
for f in base preflight transfer; do git show "ed4b06c:app/services/ml_release/storage/$f.py" | wc -l; done \
  | awk '{s+=$1} END {print "RemoteObjectStorageClient_composed_lines="s}'
git show ed4b06c:app/services/ml_training/service.py | sed -n '1,32p' | grep -c 'Mixin' | sed 's/^/PricePredictionTrainingService_mixin_mentions=/'
```

```
app/services/ml_release/base.py	392
app/services/ml_release/signing.py	57
app/services/ml_release/gate.py	421
app/services/ml_release/preflight.py	452
app/services/ml_release/manifest.py	341
app/services/ml_release/rollout.py	437
MLReleasePromotionService_composed_lines=2100
RemoteObjectStorageClient_composed_lines=632
PricePredictionTrainingService_mixin_mentions=12
```

**판정**: 여섯 파일 전부 500줄 한도 안인데 **합성된 클래스는 그 합**이다.
파일 축만으로는 이 크기가 관측되지 않는다 — `OPEN-ADR-06`(ADR 0007 §5)의 근거다.
**`PricePredictionTrainingService`의 합은 여기서 재지 않았다** — 위 마지막 명령은
`service.py` 머리에서 mixin 이름이 언급된 횟수만 세며, 그 클래스의 구성 파일 목록을
이 slice가 확정하지 않았다.

---

### C-5.3 · 8개 커널 — 같은 계측으로

`OPEN-ML-01`이 지목한 8개다(ADR 0001 §4.1). `funclines.py`는 C-5.2 블록의 것을 쓴다.

```
cd bid-vector
cat > "$T/kernels.txt" <<'EOF'
app/domain/award_margin_distribution.py
app/domain/award_landing_curve.py
app/domain/award_rate_features.py
app/domain/award_landing_distribution.py
app/domain/assessment_shrinkage.py
app/domain/reserve_draw_distribution.py
app/domain/settlement_maturity.py
app/domain/award_landing_curve_builders.py
EOF
while IFS= read -r p; do
  git cat-file -e "ed4b06c:$p" 2>/dev/null || { echo "MISSING $p"; continue; }
  n=$(git show "ed4b06c:$p" | wc -l | tr -d ' ')
  mx=$(git show "ed4b06c:$p" | python3 "$T/funclines.py" "$p" 1 | head -1 | cut -f4)
  printf '%s\t%s\tmax_func=%s\n' "$p" "$n" "$mx"
done < "$T/kernels.txt"
while IFS= read -r p; do git show "ed4b06c:$p" | wc -l; done < "$T/kernels.txt" \
  | awk '{s+=$1; if($1>m)m=$1} END {print "kernel_lines_total="s; print "kernel_max_file_lines="m}'
while IFS= read -r p; do git show "ed4b06c:$p" | python3 "$T/funclines.py" "$p" 51; done < "$T/kernels.txt" \
  | wc -l | sed 's/^ *//;s/^/kernel_functions_over_50=/'
while IFS= read -r p; do n=$(git show "ed4b06c:$p" | wc -l | tr -d ' '); [ "$n" -gt 500 ] && echo x; done < "$T/kernels.txt" \
  | wc -l | sed 's/^ *//;s/^/kernel_files_over_500=/'
```

```
app/domain/award_margin_distribution.py	460	max_func=41
app/domain/award_landing_curve.py	440	max_func=47
app/domain/award_rate_features.py	376	max_func=34
app/domain/award_landing_distribution.py	278	max_func=43
app/domain/assessment_shrinkage.py	213	max_func=49
app/domain/reserve_draw_distribution.py	148	max_func=25
app/domain/settlement_maturity.py	133	max_func=28
app/domain/award_landing_curve_builders.py	118	max_func=21
kernel_lines_total=2166
kernel_max_file_lines=460
kernel_functions_over_50=0
kernel_files_over_500=0
```

**판정**: 8개 전부 `app/domain/` 아래이며 **세 앵커 디렉터리 밖**이다 —
그것이 `OPEN-ADR-02`(§2.1 앵커에 `app/domain/`을 넣는가)의 근거다.
8개는 두 축 모두 위반이 0이다.

### C-5.4 · ADR이 인용한 그 밖의 legacy 사실

```
cd bid-vector
git ls-tree -r --name-only ed4b06c | grep -c '\.proto$' | sed 's/^/proto_files=/'
git grep -l 'from app.ai.predictors' ed4b06c -- app | wc -l | sed 's/^ *//;s/^/files_importing_predictors=/'
git ls-tree -r --name-only ed4b06c | grep -cE '\.tsx?$|\.jsx?$' | sed 's/^/frontend_ts_files=/'
git show ed4b06c:app/core/config.py | grep -n 'DATABASE_URL' | sed 's/=.*/= <redacted — 자격증명 형태>/'
git cat-file -e ed4b06c:alembic.ini && echo "alembic.ini=present"
git ls-tree -r --name-only ed4b06c -- alembic | wc -l | sed 's/^ *//;s/^/alembic_files=/'
git show ed4b06c:requirements/runtime.txt | grep -nE 'sqlalchemy|psycopg|pgvector|alembic|celery|sqlmodel'
git show ed4b06c:frontend/package.json | grep -n 'sync-types\|openapi-typescript'
```

```
proto_files=0
files_importing_predictors=31
frontend_ts_files=292
10:DEFAULT_DATABASE_URL = <redacted — 자격증명 형태>
40:    DATABASE_URL: str = <redacted — 자격증명 형태>
817:        """Allow split DATABASE_* env vars to compose DATABASE_URL deterministically."""
828:            self.DATABASE_URL = <redacted — 자격증명 형태>
832:        elif self.DATABASE_URL and self.DATABASE_URL != <redacted — 자격증명 형태>
840:                self.DATABASE_URL
alembic.ini=present
alembic_files=30
3:sqlalchemy==2.0.23
4:psycopg[binary]==3.2.1
5:pgvector==0.3.6
21:sqlmodel==0.0.14
22:alembic==1.13.0
23:celery==5.3.4
14:    "sync-types": "python ../scripts/sync_openapi_types.py",
15:    "check:sync-types": "python ../scripts/sync_openapi_types.py --check",
48:    "openapi-typescript": "^7.13.0",
```

> **`sed`가 `=` 뒤를 지운다** — 자격증명 형태의 문자열이라 `agent-workflow.md` §6이
> 생성물에 남기지 못하게 한다. **행 번호와 존재는 그대로 뜬다.**
> `817` · `840`은 `=`가 없어 원문이 그대로 남는다 — 그 두 줄에는 값이 없다.
> `828` · `832`는 **env 조합 로직**이며 자격증명 리터럴이 아니다.
> 41행이 이 출력에 없는 것은 그 줄에 `DATABASE_URL` 문자열이 없기 때문이며,
> ADR 0004가 인용한 `:40-42`는 `(`…`)`로 닫히는 대입 **한 덩어리**다 — 그 세 줄은
> `git show ed4b06c:app/core/config.py | sed -n '40,42p'`가 낸다.

### C-5.5 · training-serving skew 방지 구조 (`P-ML-01`)

```
cd bid-vector
git show ed4b06c:app/ai/predictors/award_rate_gbm.py | sed -n '60,66p'
git show ed4b06c:app/services/ml_training/award_rate_gbm.py | sed -n '51,59p'
```

```
from app.domain.award_rate_features import (
    AWARD_RATE_FEATURE_NAMES,
    SERVING_DENOMINATOR_SOURCE,
    AgencyTargetEncoding,
    AwardRateFeatureSpace,
    normalize_feature_key,
)
from app.domain.award_rate_features import (
    AWARD_RATE_FEATURE_NAMES,
    CATEGORICAL_AWARD_RATE_FEATURES,
    AgencyTargetEncoding,
    AwardRateFeatureSpace,
    AwardRateObservation,
    build_agency_target_encoding,
    normalize_feature_key,
)
```

**판정**: 두 인용 모두 `import` 블록 경계(`(`…`)`)까지이며, 학습·서빙이 같은 모듈을
import한다. `decisions.md` `OPEN-ML-01` 절이 학습 측을 `:52-59`로 인용했으나
**블록 경계는 `:51-59`**다 — `from` 줄이 51이다. 이 ADR은 후자를 쓴다.

---

## C-6 · ADR의 legacy 인용이 `ed4b06c`에 실재하는가

```
T=$(mktemp -d)
cat > "$T/citescan.py" <<'PY'
#!/usr/bin/env python3
"""ADR 안의 legacy 인용(`path:N` / `path:A-B` / 맨 경로 / commit SHA)이 기준 commit 에
실재하는지 검사. argv: <ADR 디렉터리> <legacy 저장소> <commit>
legacy 접두: app/ tests/ scripts/ frontend/ requirements/ alembic/ + 정확 이름 몇 개.
접두가 아닌 것은 SKIP 으로 **전수 출력**한다 — 조용히 건너뛰지 않는다."""
import pathlib, re, subprocess, sys
adr_dir, repo, commit = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
LEGACY = ("app/", "tests/", "scripts/", "frontend/", "requirements/", "alembic/")
EXACT = {"alembic.ini", "conftest.py", "pyproject.toml", "run.py"}
CITE = re.compile(r"`([A-Za-z0-9_./\-]+\.[A-Za-z0-9_]+):(\d+)(?:-(\d+))?`")
BARE = re.compile(r"`([A-Za-z0-9_./\-]+/[A-Za-z0-9_.\-]+\.[A-Za-z0-9_]+)`")
SHA = re.compile(r"`([0-9a-f]{7,40})`")
lengths, counts, seen = {}, {"OK": 0, "MISSING": 0, "RANGE": 0, "SKIP": 0}, set()

def length_of(path):
    if path not in lengths:
        r = subprocess.run(["git", "-C", repo, "show", f"{commit}:{path}"], capture_output=True)
        lengths[path] = None if r.returncode else r.stdout.decode("utf-8", "replace").count("\n")
    return lengths[path]

for p in sorted(adr_dir.glob("*.md")):
    for m in CITE.finditer(p.read_text(encoding="utf-8")):
        path, a, b = m.group(1), int(m.group(2)), int(m.group(3) or m.group(2))
        key = (p.name, path, a, b)
        if key in seen:
            continue
        seen.add(key)
        cite = f"{path}:{a}" + (f"-{b}" if b != a else "")
        if not (path.startswith(LEGACY) or path in EXACT):
            counts["SKIP"] += 1
            print(f"SKIP\t{p.name}\t{cite}\tnot-a-legacy-path")
            continue
        n = length_of(path)
        if n is None:
            counts["MISSING"] += 1
            print(f"MISSING\t{p.name}\t{cite}\tno such path at {commit}")
        elif b > n or a < 1 or a > b:
            counts["RANGE"] += 1
            print(f"RANGE\t{p.name}\t{cite}\tfile has {n} lines")
        else:
            counts["OK"] += 1
            print(f"OK\t{p.name}\t{cite}\tfile has {n} lines")
for p2 in sorted(adr_dir.glob("*.md")):
    text2 = p2.read_text(encoding="utf-8")
    for m in BARE.finditer(text2):
        path = m.group(1)
        if not (path.startswith(LEGACY) or path in EXACT):
            continue
        key = ("bare", p2.name, path)
        if key in seen:
            continue
        seen.add(key)
        n = length_of(path)
        if n is None:
            counts["MISSING"] += 1
            print(f"MISSING\t{p2.name}\t{path}\tno such path at {commit}")
        else:
            counts["OK"] += 1
            print(f"OK\t{p2.name}\t{path}\texists, {n} lines")
    for m in SHA.finditer(text2):
        sha = m.group(1)
        key = ("sha", p2.name, sha)
        if key in seen:
            continue
        seen.add(key)
        r = subprocess.run(["git", "-C", repo, "cat-file", "-e", sha + "^{commit}"], capture_output=True)
        if r.returncode:
            counts["MISSING"] += 1
            print(f"MISSING\t{p2.name}\tcommit {sha}\tnot a commit in legacy repo")
        else:
            counts["OK"] += 1
            print(f"OK\t{p2.name}\tcommit {sha}\tresolves in legacy repo")
print("legacy=%d ok=%d missing=%d range=%d skipped=%d" % (
    counts["OK"] + counts["MISSING"] + counts["RANGE"],
    counts["OK"], counts["MISSING"], counts["RANGE"], counts["SKIP"]))
PY
python3 "$T/citescan.py" docs/adr "$PWD/bid-vector" ed4b06c
```

```
OK	0001-target-architecture.md	requirements/ml-training.txt:3-7	file has 8 lines
OK	0001-target-architecture.md	app/ai/predictors/award_rate_gbm.py:60-66	file has 404 lines
OK	0001-target-architecture.md	app/services/ml_training/award_rate_gbm.py:51-59	file has 383 lines
OK	0001-target-architecture.md	app/services/ml_release/__init__.py:1-10	file has 47 lines
OK	0002-money-rate-basis.md	app/domain/money.py:30	file has 37 lines
OK	0002-money-rate-basis.md	app/schemas/bid_summary.py:69-77	file has 224 lines
OK	0004-persistence-and-events.md	app/core/config.py:10	file has 848 lines
OK	0004-persistence-and-events.md	app/core/config.py:40-42	file has 848 lines
SKIP	0007-test-pyramid-and-ratchet.md	build.gradle:48-49	not-a-legacy-path
OK	0008-frontend-disposition.md	frontend/package.json:14-15	file has 55 lines
OK	0008-frontend-disposition.md	frontend/package.json:48	file has 55 lines
OK	0001-target-architecture.md	app/domain/award_margin_distribution.py	exists, 460 lines
OK	0001-target-architecture.md	app/domain/award_landing_curve.py	exists, 440 lines
OK	0001-target-architecture.md	app/domain/award_rate_features.py	exists, 376 lines
OK	0001-target-architecture.md	app/domain/award_landing_distribution.py	exists, 278 lines
OK	0001-target-architecture.md	app/domain/assessment_shrinkage.py	exists, 213 lines
OK	0001-target-architecture.md	app/domain/reserve_draw_distribution.py	exists, 148 lines
OK	0001-target-architecture.md	app/domain/settlement_maturity.py	exists, 133 lines
OK	0001-target-architecture.md	app/domain/award_landing_curve_builders.py	exists, 118 lines
OK	0001-target-architecture.md	app/services/ml_release/rollout.py	exists, 437 lines
OK	0001-target-architecture.md	app/services/ml_release/gate.py	exists, 421 lines
OK	0001-target-architecture.md	app/ai/predictors/legal_floor_spec.py	exists, 150 lines
OK	0001-target-architecture.md	app/services/ml_training/award_landing_ladder.py	exists, 462 lines
OK	0001-target-architecture.md	tests/design_ratchet_baseline.json	exists, 904 lines
OK	0001-target-architecture.md	commit ed4b06c	resolves in legacy repo
OK	0002-money-rate-basis.md	commit ed4b06c	resolves in legacy repo
OK	0002-money-rate-basis.md	commit c4ec93b	resolves in legacy repo
OK	0003-contract-transport.md	commit ed4b06c	resolves in legacy repo
OK	0004-persistence-and-events.md	requirements/runtime.txt	exists, 24 lines
OK	0004-persistence-and-events.md	commit ed4b06c	resolves in legacy repo
OK	0005-domain-events-and-outbox.md	commit ed4b06c	resolves in legacy repo
OK	0007-test-pyramid-and-ratchet.md	scripts/design_ratchet.py	exists, 322 lines
OK	0007-test-pyramid-and-ratchet.md	tests/design_ratchet_baseline.json	exists, 904 lines
OK	0007-test-pyramid-and-ratchet.md	commit ed4b06c	resolves in legacy repo
OK	0008-frontend-disposition.md	frontend/package.json	exists, 55 lines
OK	0008-frontend-disposition.md	scripts/sync_openapi_types.py	exists, 195 lines
OK	0008-frontend-disposition.md	commit ed4b06c	resolves in legacy repo
OK	0009-ml-reuse-provenance.md	commit ed4b06c	resolves in legacy repo
legacy=37 ok=37 missing=0 range=0 skipped=1
```

**`SKIP` 한 건**은 ArchUnit 저장소의 `build.gradle:48-49`이며 legacy가 아니다.
**조사 노트가 취득한 외부 저장소 파일이고 이 slice가 재확인하지 않았다** —
ADR 0007 D-6이 그 사실을 그 자리에 적는다.

**이 스캐너는 ADR 안의 backtick 7~40 hex 토큰을 legacy commit 인용으로 읽는다** — 아래
C-6n의 `deadbeef1` 행이 그 규칙을 실행으로 낸다. 그래서 **ADR 산문은 이 저장소의 커밋을
backtick SHA로 지목하지 않는다**: 라운드를 가리켜야 하는 자리는 **라운드 이름**(Codex
1차·2차 수정 라운드, ADR 신설 판본)을 쓴다. `checklist.md`·`scope.md`는 이 스캐너의
대상이 아니므로(위 명령의 인자는 `docs/adr` 하나다) 그 자리에는 SHA를 쓴다.

### C-6n · 이 스캐너가 무엇을 잡는지 — 실행으로 잰다

```
# citescan.py 는 C-6 블록이 만든 "$T" 의 것을 그대로 쓴다 — T 를 덮어쓰지 않는다
NC=$(mktemp -d)
printf 'x `app/no_such_file.py:1-5` y `app/domain/money.py:9999` z `deadbeef1`\n' > "$NC/0001-neg.md"
python3 "$T/citescan.py" "$NC" "$PWD/bid-vector" ed4b06c
```

```
MISSING	0001-neg.md	app/no_such_file.py:1-5	no such path at ed4b06c
RANGE	0001-neg.md	app/domain/money.py:9999	file has 37 lines
MISSING	0001-neg.md	commit deadbeef1	not a commit in legacy repo
legacy=3 ok=0 missing=2 range=1 skipped=0
```

**판정**: 없는 경로·범위 초과·없는 commit을 실제로 잡는다.
**잡지 못하는 것**: *"그 행이 이 주장을 뒷받침하는가"* — **내용 정합은 사람이 읽는다.**
그래서 인용문을 C-5.5와 각 ADR 본문에 **전문으로 실었다.**

---

## C-7 · `OPEN-OPS-07` — OPS-21 후보의 판정

후보는 `docs/discovery/capability-map.md` OPS-21 표의 것이다. **이 검사는 후보마다
「대안」 절 표의 판정 열을 읽는다** — 행 어디에나 있는 낱말을 판정으로 세지 않는다.

```
T=$(mktemp -d)
cat > "$T/ops07scan.py" <<'PY'
#!/usr/bin/env python3
"""OPS-21 후보가 지정 ADR 의 「대안」 절 표에서 어떤 판정을 받았는지.

argv[1]=ADR 디렉터리. argv[2:] 는 `이름@ADR번호` 형식의 추가 후보(음성 대조용).
표 행을 `|` 로 갈라 **1열(후보)에서만 이름을 찾고 2열(판정)을 읽는다** — 행 어디에나
있는 부분문자열을 판정으로 세지 않는다.
판정 분류: ADOPTED(채택) · REJECTED(불채택) · CONDITIONAL(조건부) · DEFERRED(보류·미조사) ·
NOT-APPLICABLE(대상 아님) · NO-VERDICT(2열에 판정 어휘 없음) · MISSING(1열에 후보 없음)
"""
import pathlib, re, sys

PAIRS = [("Spring Modulith", "0005"), ("JobRunr", "0005"), ("db-scheduler", "0005"),
         ("Spring Integration JDBC lock registry", "0005"), ("ShedLock", "0005"),
         ("Resilience4j", "0005"), ("Micrometer", "0005"),
         ("ArchUnit", "0007"), ("Konsist", "0007"), ("Detekt", "0007")]
adr_dir = pathlib.Path(sys.argv[1])
for extra in sys.argv[2:]:
    name, _, num = extra.partition("@")
    PAIRS.append((name, num or "0005"))


def classify(cell):
    c = re.sub(r"[*`]", "", cell)
    if "보류" in c or "미조사" in c or "조사되지 않음" in c:
        return "DEFERRED"
    if "대상 아님" in c:
        return "NOT-APPLICABLE"
    if "조건부" in c:
        return "CONDITIONAL"
    if "불채택" in c:
        return "REJECTED"
    if "채택" in c:
        return "ADOPTED"
    return "NO-VERDICT"


def alt_rows(num):
    hits = list(adr_dir.glob(f"{num}-*.md"))
    if not hits:
        return None
    rows, on = [], False
    for ln in hits[0].read_text(encoding="utf-8").splitlines():
        if ln.startswith("## "):
            on = "대안" in ln
            continue
        if on and ln.startswith("|"):
            cells = [c.strip() for c in ln.strip().strip("|").split("|")]
            if len(cells) >= 2:
                rows.append((cells[0], cells[1]))
    return (hits[0].name, rows)


counts = {}
for cand, num in PAIRS:
    sec = alt_rows(num)
    if sec is None:
        print(f"MISSING\t{cand}\t(no ADR {num})\t-")
        counts["MISSING"] = counts.get("MISSING", 0) + 1
        continue
    fname, rows = sec
    hit = [v for name, v in rows if cand in re.sub(r"[*`]", "", name)]
    if not hit:
        print(f"MISSING\t{cand}\t{fname}\t(대안 절 표의 후보 열에 없음)")
        counts["MISSING"] = counts.get("MISSING", 0) + 1
        continue
    verdict = classify(hit[0])
    counts[verdict] = counts.get(verdict, 0) + 1
    print(f"{verdict}\t{cand}\t{fname}\t{re.sub(r'[*`]', '', hit[0])[:52]}")
print("candidates=%d " % len(PAIRS) + " ".join(
    "%s=%d" % (k, counts[k]) for k in sorted(counts)))
PY
python3 "$T/ops07scan.py" docs/adr
```

```
REJECTED	Spring Modulith	0005-domain-events-and-outbox.md	불채택 (현 시점) · 재검토 예약
REJECTED	JobRunr	0005-domain-events-and-outbox.md	불채택
ADOPTED	db-scheduler	0005-domain-events-and-outbox.md	채택
DEFERRED	Spring Integration JDBC lock registry	0005-domain-events-and-outbox.md	판정 보류 — 조사되지 않음
REJECTED	ShedLock	0005-domain-events-and-outbox.md	불채택 (OPS-01의 lease 어댑터로) — 요구 ② 불충족
ADOPTED	Resilience4j	0005-domain-events-and-outbox.md	채택
ADOPTED	Micrometer	0005-domain-events-and-outbox.md	채택 (선택의 여지가 사실상 없다)
ADOPTED	ArchUnit	0007-test-pyramid-and-ratchet.md	채택
REJECTED	Konsist	0007-test-pyramid-and-ratchet.md	불채택
CONDITIONAL	Detekt	0007-test-pyramid-and-ratchet.md	조건부 — 버전 경로가 OPEN-ADR-08
candidates=10 ADOPTED=4 CONDITIONAL=1 DEFERRED=1 REJECTED=4
```

**판정**: 후보 전부가 지정 ADR의 「대안」 절 표에 있고 **각자 하나의 판정**을 갖는다.
남은 `DEFERRED` 행은 **advisory lock 축**이며 ADR 0005 §3.2·§5가 그 사유와
`OPEN-ADR-12`를 적는다. **「후보 전건 채택/불채택 완료」는 이 출력이 지지하지 않는다** —
`DEFERRED`가 있는 한 그 주장은 성립하지 않는다.

**이 스캐너가 재는 것**: 표의 **판정 열 한 칸**을 분류한다.
**재지 않는 것**: 그 판정이 **옳은지**, 사유가 판정을 **떠받치는지**. 그것은 사람이 읽는다.

### C-7n · 이 스캐너가 무엇을 잡는지 — 실행으로 잰다

**앞선 형태는 행 어디에서든 판정 낱말 하나를 찾으면 통과시켰고**, 그래서
`판정 보류 — 조사되지 않음`인 행을 `불채택|채택|판정 보류`로 표시하면서 통과로 셌다.
아래 fixture는 **판정 열과 사유 열에 서로 반대되는 낱말**을 넣어 어느 쪽을 읽는지 가른다.

```
# ops07scan.py 는 C-7 블록이 만든 "$T" 의 것을 그대로 쓴다
NV=$(mktemp -d)
cat > "$NV/0005-neg.md" <<'MD'
# neg
## 3. 대안
| 후보 | 판정 | 사유 |
| --- | --- | --- |
| **ShedLock** | **판정 보류 — 조사되지 않음** | 앞 라운드는 **불채택**이라 적었으나 그것은 판정이 아니다 |
| **db-scheduler** | **불채택** | **채택**이라는 낱말이 사유에 들어 있어도 판정은 2열이다 |
MD
python3 "$T/ops07scan.py" "$NV" | grep -vE '^MISSING'
```

```
REJECTED	db-scheduler	0005-neg.md	불채택
DEFERRED	ShedLock	0005-neg.md	판정 보류 — 조사되지 않음
candidates=10 DEFERRED=1 MISSING=8 REJECTED=1
```

**판정**: **사유 열의 낱말에 끌려가지 않는다** — `ShedLock` 행은 사유에 `불채택`이
있어도 `DEFERRED`, `db-scheduler` 행은 사유에 `채택`이 있어도 `REJECTED`다.
그리고 **표에 없는 후보는 `MISSING`**으로 나온다(그 여덟이 위 출력에서 걸러진 것들이다).

---

## C-8 · secret 스캔

```
xargs grep -nEi 'api[_-]?key|secret|token|password|passwd|BEGIN [A-Z ]*PRIVATE KEY|xox[baprs]-|ghp_|AKIA[0-9A-Z]{16}' < "$T0/slice_paths.txt" 2>/dev/null | wc -l | sed 's/^ *//;s/^/matches=/'
xargs grep -nEi '[a-z+]+://[A-Za-z0-9_.-]+:[^@/[:space:]]+@|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}|xox[baprs]-|BEGIN [A-Z ]*PRIVATE KEY|sk-[A-Za-z0-9]{20,}' < "$T0/slice_paths.txt" 2>/dev/null | wc -l | sed 's/^ *//;s/^/credential_literals=/'
```

```
matches=7
credential_literals=0
```

**판정**: 넓은 패턴(`matches`)은 **그 단어를 말하는 산문과 위 정규식 자체**를 함께 세므로
0이 되지 않는다 — 그래서 **값 형태만 잡는 좁은 패턴**(`credential_literals`)을 함께 낸다.
**자격증명 리터럴이 0**이라는 것이 이 블록의 판정이다.

> **좌표를 세지 않고 개수만 낸다.** 좌표는 편집마다 이동하고 이 파일 자신도 대상이라,
> 좌표를 적으면 다음 편집에 반드시 낡는다. 어느 줄인지 보려면 위 명령에서
> `| wc -l | sed …`를 지우면 그 명령이 목록을 낸다.

---

## C-9 · UI 축 부재와 활성 `OPEN` 참조

### C-9.1 · `capability-map.md`에 UI 축이 없다

```
grep -ci 'react' docs/discovery/capability-map.md | sed 's/^/react_matches_in_capability_map=/'
grep -cE '^## [0-9]+\. 축 [0-9]' docs/discovery/capability-map.md | sed 's/^/capability_axes=/'
```

```
react_matches_in_capability_map=0
capability_axes=8
```

### C-9.2 · 마일스톤에 UI slice가 없다

```
grep -h '^### Slice' milestone-*.md | wc -l | sed 's/^ *//;s/^/milestone_slices_total=/'
grep -hi '^### Slice' milestone-*.md | grep -ciE 'ui|front|react|화면' | sed 's/^/milestone_slices_ui=/'
```

```
milestone_slices_total=32
milestone_slices_ui=0
```

**판정**: ADR 0008 §1.3·§1.4의 전제가 이 두 블록에서 나온다.
**`milestone_slices_ui=0`은 slice 제목 기준이다** — 본문에 UI 작업이 숨어 있는지는
이 명령이 답하지 않는다.

### C-9.3 · ADR이 참조한 활성/기결 `OPEN` — ID별 등장 횟수

```
grep -ho 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs/adr/*.md | grep -v '^OPEN-ADR-' | sort | uniq -c | sort -rn | awk '{printf "%s\tcount=%s\n", $2, $1}'
```

```
OPEN-OPS-07	count=13
OPEN-ML-01	count=9
OPEN-OPS-05	count=8
OPEN-OPS-10	count=5
OPEN-QUAL-06	count=3
OPEN-OPS-04	count=3
OPEN-OPS-03	count=3
OPEN-ML-06	count=3
OPEN-SET-02	count=2
OPEN-REG-05	count=2
OPEN-REG-01	count=2
OPEN-NOTI-02	count=2
OPEN-ML-04	count=2
OPEN-DEC-08	count=2
OPEN-STR-01	count=1
OPEN-SET-06	count=1
OPEN-REG-04	count=1
OPEN-QUAL-10	count=1
OPEN-QUAL-08	count=1
OPEN-OPS-08	count=1
OPEN-OPS-02	count=1
OPEN-OPS-01	count=1
OPEN-NUM-01	count=1
OPEN-NOTI-09	count=1
OPEN-ML-05	count=1
OPEN-DEC-07	count=1
OPEN-DEC-03	count=1
OPEN-DEC-01	count=1
```

### C-9.4 · 신설 `OPEN-ADR-NN`의 등록 자리

```
grep -ho 'OPEN-ADR-[0-9]\{2\}' docs/adr/*.md | sort -u | paste -sd' ' -
for f in docs/adr/*.md; do ids=$(grep -ho 'OPEN-ADR-[0-9]\{2\}' "$f" | sort -u | paste -sd',' -); [ -n "$ids" ] && printf '%s\t%s\n' "$(basename $f)" "$ids"; done
```

```
OPEN-ADR-01 OPEN-ADR-02 OPEN-ADR-03 OPEN-ADR-04 OPEN-ADR-05 OPEN-ADR-06 OPEN-ADR-07 OPEN-ADR-08 OPEN-ADR-09 OPEN-ADR-10 OPEN-ADR-11 OPEN-ADR-12 OPEN-ADR-13
0001-target-architecture.md	OPEN-ADR-01,OPEN-ADR-02,OPEN-ADR-03,OPEN-ADR-04,OPEN-ADR-05,OPEN-ADR-06
0003-contract-transport.md	OPEN-ADR-01,OPEN-ADR-11
0004-persistence-and-events.md	OPEN-ADR-01
0005-domain-events-and-outbox.md	OPEN-ADR-01,OPEN-ADR-12,OPEN-ADR-13
0007-test-pyramid-and-ratchet.md	OPEN-ADR-01,OPEN-ADR-06,OPEN-ADR-07,OPEN-ADR-08
0008-frontend-disposition.md	OPEN-ADR-09
0009-ml-reuse-provenance.md	OPEN-ADR-10
```

**판정**: `scope.md`의 신설 `OPEN` 표가 지목한 ADR과 위 출력이 대응한다.
**`OPEN-ADR-06`은 0001과 0007 양쪽에 나오고 소유는 0007**이다(0001 §4.3이 넘긴다).

### C-9.5 · 「해소」·「확정」이 활성 `OPEN` ID와 같은 줄에 있는 자리 — 전수

**계열 A(미결을 확정으로 쓰기)의 적출은 기계가 하지 못한다.** 명령이 **읽을 자리를
전수로 내고**, 그 줄이 「이미 확정된 결정의 인용」인지 「이 slice의 임의 해소」인지는
사람이 읽는다.

```
grep -n 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs/adr/*.md | grep -v 'OPEN-ADR-' | grep -E '해소|확정' | wc -l | sed 's/^ *//;s/^/lines_to_read=/'
grep -n 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs/adr/*.md | grep -v 'OPEN-ADR-' | grep -E '해소|확정' | cut -c1-120 | sort
```

```
lines_to_read=6
docs/adr/0001-target-architecture.md:176:| **A-3** | **업무별 배포 서비스로 분해(MSA)** | **불채택** | `v2-지침서.md` §3이 *"과도한 MSA가 아닌 두
docs/adr/0001-target-architecture.md:178:| **A-5** | **커널 일부만 Kotlin으로** (혼합 경계) | **불채택** | `OPEN-ML-01`이 (a)로 확정됐다 — 8
docs/adr/0002-money-rate-basis.md:139:| **A-7** | **"모름"을 `null`로, 하한 미달 빈도를 `0`으로** | **불채택 (D-5)** | `R-PROV-08` · `R-
docs/adr/0002-money-rate-basis.md:187:| **`OPEN-DEC-07`** | 기준 금액 신뢰 비율 1.15의 **마진 0.05 값**. 결정은 "V2 코퍼스에서 재유도"로 확정됐고 **
docs/adr/0002-money-rate-basis.md:189:| **`OPEN-DEC-01`** (해소됨, 참고) | 하한 미달 빈도의 최소 표본 수 150은 **유지로 확정**됐고 **통계적 편의임을 명시*
docs/adr/0006-gradle-modules.md:59:확정됐다**(운영자 결정 `OPEN-OPS-05`, 2026-08-26). 위 표에서 그 항목을 뺐고, 그 자리를
```

**판정(사람이 읽은 것)**: 여섯 자리 전부가 **이미 확정된 운영자 결정의 인용**이거나
**해소하지 않았다는 선언**이다. 이 slice가 활성 `OPEN`을 닫은 자리가 없다.
**`OPEN-OPS-07`은 예외이며 종료 조건이 「ADR 대안 절 기입」이므로 C-7이 그것을 낸다.**

> **순서는 `sort`가 준다** — 이 환경의 `grep`은 인자 glob 순서로 내지 않아 정렬 없이는
> 순서가 재현되지 않는다. **줄 번호를 판정의 근거로 쓰지 않는다** — 근거는 위 명령이고
> 이 열은 선언 SHA 시점의 좌표다. 값이 어긋나면 명령을 다시 돌린다.

### C-9.6 · ADR이 언급하지 않은 활성 `OPEN` — 전수

**계열 A는 「ADR이 언급한 활성 `OPEN`」이 아니라 「언급하지 않은 채 그 쟁점을 확정 서술로
쓴 것」에서 난다.** 그래서 이 블록은 **언급되지 않은 활성 `OPEN`을 전수로
낸다** — 인접 여부는 사람이 그 목록을 읽고 판정한다. 기계는 목록만 낸다.

```
T=$(mktemp -d)
awk '/^### G1\./{f=1} /^## 12\.1/{f=0} f && /^\| OPEN-/ {print $2}' docs/discovery/capability-map.md | sort -u > "$T/active.txt"
wc -l < "$T/active.txt" | sed 's/^ *//;s/^/active_open_ids=/'
grep -ho 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs/adr/*.md | grep -v '^OPEN-ADR-' | sort -u > "$T/mentioned.txt"
comm -12 "$T/active.txt" "$T/mentioned.txt" | wc -l | sed 's/^ *//;s/^/mentioned=/'
comm -12 "$T/active.txt" "$T/mentioned.txt" | paste -sd' ' -
comm -23 "$T/active.txt" "$T/mentioned.txt" | wc -l | sed 's/^ *//;s/^/unmentioned=/'
comm -23 "$T/active.txt" "$T/mentioned.txt" | paste -sd' ' -
```

```
active_open_ids=45
mentioned=14
OPEN-DEC-03 OPEN-DEC-07 OPEN-ML-05 OPEN-ML-06 OPEN-NUM-01 OPEN-OPS-01 OPEN-OPS-02 OPEN-OPS-03 OPEN-OPS-04 OPEN-OPS-07 OPEN-OPS-08 OPEN-OPS-10 OPEN-QUAL-10 OPEN-SET-06
unmentioned=31
OPEN-COL-01 OPEN-COL-03 OPEN-COL-04 OPEN-COL-05 OPEN-DEC-10 OPEN-ML-02 OPEN-ML-03 OPEN-NOTI-01 OPEN-NOTI-04 OPEN-NOTI-05 OPEN-NOTI-06 OPEN-NOTI-07 OPEN-NOTI-08 OPEN-NUM-02 OPEN-NUM-03 OPEN-OPS-09 OPEN-QUAL-04 OPEN-QUAL-05 OPEN-QUAL-07 OPEN-QUAL-09 OPEN-QUAL-11 OPEN-SET-01 OPEN-SET-03 OPEN-SET-04 OPEN-SET-05 OPEN-SET-08 OPEN-SET-09 OPEN-SET-10 OPEN-STR-02 OPEN-STR-04 OPEN-STR-12
```

**판정(사람이 읽은 것)**: **`unmentioned` 목록 전수를 읽었고 ADR의 확정 서술과 인접한
것은 없다.** 인접한 것으로 판정된 다섯은 `mentioned` 쪽에 있고 **관계 서술만** 갖는다 —
`OPEN-ML-06`(ADR 0001 §4.1·§5) · `OPEN-SET-06`(0001 §5) · `OPEN-OPS-02` ·
`OPEN-OPS-08`(0005 §5) · `OPEN-ML-05`(0006 D-7).
**다섯 중 어느 것도 해소하지 않았다 — 관계만 적었다.**

**이 명령이 잡지 못하는 것**: **쟁점의 인접성.** ID 문자열의 등장 여부만 센다.
`mentioned`에 있다고 선점하지 않은 것도, `unmentioned`에 있다고 선점한 것도 아니다.

---

## C-10 · capability ID 용법 — `OPS-13`

`OPS-13`이 ADR에서 어느 뜻으로 쓰이는지와 정본의 정의를 함께 낸다.

```
echo "-- ADR 안의 OPS-13 전수"
grep -n 'OPS-13' docs/adr/*.md
echo "-- capability-map 의 OPS-13 정의"
grep -n '^### OPS-13' docs/discovery/capability-map.md
echo "-- OPS-21 표에서 그 두 행"
grep -n '^| 재시도·backoff\|^| 아키텍처 규칙 강제' docs/discovery/capability-map.md
```

```
-- ADR 안의 OPS-13 전수
docs/adr/0005-domain-events-and-outbox.md:110:  **`OPS-13`은 이 행이 아니라 「아키텍처 규칙 강제」 행의 것**이며 그 항목의 정의는
docs/adr/0005-domain-events-and-outbox.md:286:### 3.5 아키텍처 규칙 강제 (OPS-13)
-- capability-map 의 OPS-13 정의
2596:### OPS-13 · 설계 래칫 (비대화 방지)
-- OPS-21 표에서 그 두 행
2724:| 재시도·backoff·circuit breaker·rate limiter | Resilience4j | OPS-08 (a)(c), COL-03 |
2725:| 아키텍처 규칙 강제 | ArchUnit(의존 방향), Konsist, Detekt(크기 임계) | OPS-13 |
```

**판정**: ADR 안의 `OPS-13`은 **두 자리**이고 둘 다 「아키텍처 규칙 강제」 맥락이다 —
하나는 그 정정을 적는 자리, 하나는 §3.5 제목이다. `capability-map.md`가 `OPS-13`을
**「설계 래칫(비대화 방지)」**로 정의하고 OPS-21 표가 그것을 **아키텍처 규칙 강제 행**에
둔다. 재시도 행의 대응 항목은 **`OPS-08 (a)(c)` · `COL-03`**뿐이다.

---

## C-12 · Codex 2차 리뷰의 실행 메타와 preflight

`codex-review-gate`가 리뷰 레인에 지운 기록 의무다 — **reviewer 메타**(`cli_version` ·
`model`+effort)와 **memory 오염 preflight**. **2차 리뷰 레인은 이 파일이 자기 리뷰
대상이라 쓰지 않았고, `f207412`가 이 절을 신설해 썼다.**

**이 절의 두 블록은 재현 래칫에서 빠진다.** 둘 다 선언 SHA 트리 밖의 상태를 읽으므로
(`_workspace/`의 리뷰 레인 산출물 · `~/.codex` 실물) **트리를 고정해도 값이 고정되지
않는다.** 빼는 일은 C-11의 스크립트가 명령 문면에서 그 경로 토큰을 찾아 하고, 뺀 사실과
근거 토큰을 그 출력이 낸다 — **사람이 기억해서 빼는 것이 아니다.** 기록은 그대로 남는다:
스킬이 preflight 결과와 reviewer 메타를 이 파일에 남기기를 요구한다. 그래서 두 블록은
**재현되는 기록이 아니라 시점 기록**이고, 각 블록이 마지막 줄에 `taken_at`을 낸다 —
그 값이 있는 한 이 블록들은 어느 트리에서도 축어로 다시 뜨지 않으며, **빠져야 한다는
사실이 출력 자신에 남는다.**

**C-12.2 블록이 내는 값은 리뷰 시점이 아니라 그 뒤에 뜬 것이다** — 지금 기록된 값은
`8af7a30`이 넣었고, 뜬 시각은 블록 자신의 `taken_at`이 낸다. 리뷰 시점 값은
레인이 남기지 않았고 **되살릴 수 없다** — 그래서 여기 적는 것은 *"리뷰 당시 오염이
없었다"*가 아니라 *"지금 이 저장소 흔적이 얼마이고 stage1에 리뷰 세션이 올라와 있지
않다"*이다. **C-12.1은 다르다** — 리뷰 실행이 남긴 파일의 머리글을 읽으므로 **리뷰
시점의 실측**이다.

### C-12.1 · reviewer 메타

```
echo "-- 실행 메타 · 출처: _workspace/m0-0d/codex.raw-output.txt 머리글"
sed -n '1,10p' _workspace/m0-0d/codex.raw-output.txt |
  grep -E '^OpenAI Codex|^model:|^reasoning effort:'
echo "-- 등재된 verdict JSON"
python3 - <<'PY'
import json
d = json.load(open("reports/evidence/m0/0d/codex-review-20260829T063806Z.json"))
print("cli_version=%s" % d["reviewer"]["cli_version"])
print("model=%s" % d["reviewer"]["model"])
print("verdict=%s" % d["verdict"])
print("reviewed_base=%s" % d["reviewed_base"][:7])
print("reviewed_head=%s" % d["reviewed_head"][:7])
print("findings=%d" % len(d["findings"]))
print("severities=%s" % ",".join(sorted(f["severity"] for f in d["findings"])))
PY
date -u +%Y-%m-%dT%H:%M:%SZ | sed 's/^/taken_at=/'
```

```
-- 실행 메타 · 출처: _workspace/m0-0d/codex.raw-output.txt 머리글
OpenAI Codex v0.149.0
model: gpt-5.6-sol
reasoning effort: high
-- 등재된 verdict JSON
cli_version=codex-cli 0.149.0
model=gpt-5.6-sol (reasoning effort: high)
verdict=request_changes
reviewed_base=998dc21
reviewed_head=584305b
findings=3
severities=medium,medium,medium
taken_at=2026-08-29T11:32:33Z
```

**판정**: raw output 머리글과 등재된 verdict JSON의 `reviewer`가 같은 값을 말하고,
`model_reasoning_effort`가 **`high`로 고정된 채 돌았다** — 하네스가 재현성 때문에
못박은 값이다. **그 JSON이 evidence 패키지에 등재됐다는 사실은 C-2 출력이 낸다**
(이 slice의 커밋이 건드린 `in_scope` 경로 목록에 그 파일이 있다).

### C-12.2 · memory 오염 preflight

```
echo "-- preflight ① consolidate 이후 이 저장소 흔적"
grep -rniE 'bid-vector-v2|regression-ledger|capability-map|OPEN-REG|0a2|0b-regression' \
  ~/.codex/memories/memory_summary.md ~/.codex/memories/MEMORY.md \
  ~/.codex/memories/rollout_summaries/ ~/.codex/memories/skills/ \
  ~/.codex/rules/default.rules 2>/dev/null | wc -l | tr -d ' ' | sed 's/^/traces=/'
echo "-- preflight ② stage1 에 리뷰 세션이 올라왔는가 (0 이 아니면 리뷰 중단)"
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" \
  "select count(*) from stage1_outputs;" | sed 's/^/stage1_rows_total=/'
sqlite3 "file:$HOME/.codex/memories_1.sqlite?immutable=1" \
  "select count(*) from stage1_outputs
    where raw_memory like '%bid-vector-v2-review%'
       or rollout_summary like '%bid-vector-v2-review%';" | sed 's/^/stage1_review_rows=/'
echo "-- 스킬 문면의 mode=ro 를 그대로 실행 (시점 값 — 아래 산문)"
sqlite3 "file:$HOME/.codex/memories_1.sqlite?mode=ro" "select 1;" 2>&1 | sed 's/^/mode_ro: /'
date -u +%Y-%m-%dT%H:%M:%SZ | sed 's/^/taken_at=/'
```

```
-- preflight ① consolidate 이후 이 저장소 흔적
traces=17
-- preflight ② stage1 에 리뷰 세션이 올라왔는가 (0 이 아니면 리뷰 중단)
stage1_rows_total=548
stage1_review_rows=0
-- 스킬 문면의 mode=ro 를 그대로 실행 (시점 값 — 아래 산문)
mode_ro: 1
taken_at=2026-08-29T11:32:32Z
```

**판정**: `stage1_review_rows=0` — 리뷰 세션 transcript가 stage1에 올라와 있지 않다.
**`stage1_rows_total`이 0이 아니므로 「테이블이 비어서 0」이 아니다.**
`traces`는 **consolidate 이후 이 저장소 흔적의 양**이고 0이 아니다 — 그래서 호출이
`--disable memories --ignore-rules`를 붙인다. **이 값은 그 플래그의 효과를 재지 않는다**
(흔적의 양일 뿐이다).

**스킬 문면과 갈라진 자리**: 스킬은 `?mode=ro`를 적고 이 블록은 그 명령을 그대로 돌린다.
**그 결과가 시점에 따라 갈렸다.** 관측을 라운드 지시어로 부르지 않고 **그것이 남은
자리로** 적는다 — 지시어는 라운드가 겹치면 두 라운드를 가리킨다:

| 관측이 남은 자리 | `mode_ro` |
| --- | --- |
| `f207412`의 이 블록 — 그 라운드에는 `taken_at`이 없어 뜬 시각은 그 커밋 시각이 상한이다 | `Error: in prepare, unable to open database file (14)` |
| `_workspace/m0-0d/18_verifier_report_codex2fix.md` — 독립 검증 레인, 연속 2회 | 둘 다 `1` |
| 위 출력 블록 — `8af7a30`이 넣었고 뜬 시각은 그 블록의 `taken_at`이 낸다 | `1` |
| `_workspace/m0-0d/20_verifier_report_h1fix.md` — 독립 검증 레인, 연속 2회 | 둘 다 `(14)` |

그 사이 `memories_1.sqlite`의 `-wal`·`-shm`이 갱신됐고 **그때의 상태는 되돌릴 수 없어
원인을 확정하지 못했다.**
그래서 이 slice는 `?mode=ro`를 **「열린다」로도 「열리지 않는다」로도 적지 않는다** —
실측된 것은 **같은 환경에서 같은 명령이 시점에 따라 갈린다**는 것뿐이고, 위 `mode_ro:`
줄은 그 자체가 **`taken_at` 시점의 값**이다. **이것이 이 두 블록을 재현 래칫에서 빼는
이유의 실물**이다 — 같은 블록의 `stage1_rows_total`도 `f207412`에 기록됐던 값과 다르게
떴다. 두 기록을 나란히 놓으면 그 줄들이 트리의 함수가 아님이 보인다. preflight는 `?immutable=1`로 읽었고 그 대가는 **파일이 동시에 쓰이면
찢긴 페이지를 읽을 수 있다**는 것이다. **이 갈라짐을 스킬에 반영할지는 하네스 소유자의
판정이며 이 slice는 `.claude/`를 고치지 않는다.**

**원인 후보 — 이 slice가 확정한 것이 아니다.** 독립 검증 레인이
`_workspace/m0-0d/20_verifier_report_h1fix.md` `O-4`에서 기제 하나를 좁혔다:
**WAL sidecar의 유무**. WAL 모드 DB를 `?mode=ro`로 열면 **읽기 전용 연결은 `-shm`을
만들 수 없어** `-wal`·`-shm`이 없는 시점(checkpoint 직후)에는 `SQLITE_CANTOPEN(14)`가
나고, 있는 시점에는 열린다는 것이다. 그 레인은 자기 관측 시점에 **`-wal`·`-shm`이
없었음**을 실물로 적었고(`ls -la ~/.codex/`), 위 표의 관측 넷이 전부 이 규칙에 맞는다고
보고했다. **이 slice는 그것을 실험으로 확정하지 않았다** — sidecar를 만들고 없애며
`mode_ro`를 가르는 실험을 돌리지 않았고, `f207412` 시점의 sidecar 상태는 되돌릴 수
없다. 그래서 위 *"원인을 확정하지 못했다"*는 그대로 서고, 이 문단은 **후속이 그 실마리를
잃지 않도록 후보와 출처만 남긴다.**

---

## C-11 · 출력 블록 전수 재현 — 선언 SHA 트리에서 축어 대조

**이 블록만 선언 SHA가 HEAD다.** 이 검사는 *"HEAD의 `commands.md`를 선언 SHA의 트리에
대고 맞춰 본다"*이므로 **HEAD의 파일 내용이 입력**이다. 선언 SHA 트리에는 이 블록이
없으니 거기서는 돌 수 없다.

**무엇을 대조 대상에서 뺄지는 아래 스크립트가 정한다 — 사람이 표시해 두는 것이 아니다.**
두 사유가 있다.

| 사유 코드 | 판정 방법 | 왜 빼는가 |
| --- | --- | --- |
| `SELF` | 명령 블록에 `==0D-BLOCK-HARNESS==`가 있다 | 이 블록 자신. 선언 SHA 트리에 없어 거기서 돌 수 없다 |
| `EXT` | 명령 블록의 문면에 `$HOME` · `~/` · `_workspace/` 중 하나가 있다 | 선언 SHA 트리 **밖**의 상태를 읽는다 — 그 출력은 트리의 함수가 아니라 **시점 값**이라 트리 재현 래칫이 잴 수 있는 대상이 아니다 |

**`EXT`는 명령 문면에서 나오므로, 새 블록이 저장소 밖을 읽기 시작하면 그것만으로 빠지고
읽지 않게 되면 그것만으로 대조로 돌아온다.** 빠진 쌍은 `SKIP <코드> <근거 토큰>` 행으로
남고 사유별 수는 마지막 줄이 낸다 — **출력 자신이 무엇을 왜 뺐는지 말한다.**

블록 쌍은 fenced 블록의 **교대**(짝수=명령, 홀수=출력)로 집는다. 각 쌍 앞에서 루트로
돌아가고 셸은 하나라 `$T`·`$T0`가 이어진다 — 「실행 계약」 그대로다.

```
# ==0D-BLOCK-HARNESS== — 이 쌍은 대조 대상에서 빠진다 (SELF)
DECL=5012bde
WT=$(mktemp -d)/wt
git worktree add --detach "$WT" "$DECL" >/dev/null 2>&1
ln -sfn "$PWD/bid-vector" "$WT/bid-vector"
python3 - reports/evidence/m0/0d/commands.md "$WT" <<'PY'
import pathlib, re, subprocess, sys
md, wt = pathlib.Path(sys.argv[1]), sys.argv[2]
lines = md.read_text(encoding="utf-8").splitlines()
fence = [i for i, l in enumerate(lines) if l.startswith("`" * 3)]
assert len(fence) % 2 == 0
blocks = [(fence[k] + 1, fence[k + 1]) for k in range(0, len(fence), 2)]
assert len(blocks) % 2 == 0
pairs = [(blocks[k], blocks[k + 1]) for k in range(0, len(blocks), 2)]
SELF = "==0D-BLOCK-HARNESS=="                  # 이 하네스 자신
EXT = re.compile(r"[$]HOME|~/|_workspace/")    # 선언 SHA 트리 밖의 상태
skip = {}
for n, (c, _) in enumerate(pairs):
    body = chr(10).join(lines[c[0]:c[1]])
    m = EXT.search(body)
    if SELF in body:
        skip[n] = ("SELF", SELF)
    elif m:
        skip[n] = ("EXT", m.group(0))
sel = [n for n in range(len(pairs)) if n not in skip]
SEP = "@@@0D-BLOCK-%d@@@"
script = ["set +e", 'ROOT="%s"' % wt, 'cd "$ROOT"']
for i, n in enumerate(sel):
    script.append('cd "$ROOT"')
    script.append('echo "%s"' % (SEP % i))
    script.extend(lines[pairs[n][0][0]:pairs[n][0][1]])
script.append('echo "%s"' % (SEP % len(sel)))
out = subprocess.run(["bash", "-c", chr(10).join(script)],
                     capture_output=True, text=True, cwd=wt).stdout
res, diff = {}, 0
for i, n in enumerate(sel):
    o = pairs[n][1]
    a = out.index(SEP % i) + len(SEP % i)
    b = out.index(SEP % (i + 1))
    got = out[a:b].strip(chr(10))
    want = chr(10).join(lines[o[0]:o[1]]).strip(chr(10))
    diff += 0 if got == want else 1
    res[n] = "OK  " if got == want else "DIFF"
for n, (c, o) in enumerate(pairs):
    tag = res[n] if n in res else "SKIP %s <%s>" % skip[n]
    print("pair %2d L%-5d %-34s %s" % (n, o[0] + 1, tag, lines[c[0]][:44].rstrip()))
print("pairs_total=%d replayed=%d skipped_SELF=%d skipped_EXT=%d diff=%d" % (
    len(pairs), len(sel),
    sum(1 for v in skip.values() if v[0] == "SELF"),
    sum(1 for v in skip.values() if v[0] == "EXT"), diff))
PY
git worktree remove --force "$WT"
```

```
pair  0 L62    OK                                 git rev-parse HEAD
pair  1 L83    OK                                 T0=$(mktemp -d)
pair  2 L139   OK                                 T=$(mktemp -d)
pair  3 L166   OK                                 # adrscan.py 는 C-3 블록이 만든 "$T" 의 것을 그대로 쓴다 —
pair  4 L189   OK                                 git log --format='%H %s' 998dc21..HEAD | gre
pair  5 L223   OK                                 cd bid-vector
pair  6 L292   OK                                 cd bid-vector
pair  7 L309   OK                                 awk -F'\t' '$4>50 {printf "%s:%d-%d\t%d\t%s\
pair  8 L363   OK                                 cd bid-vector
pair  9 L434   OK                                 cd bid-vector
pair 10 L505   OK                                 cd bid-vector
pair 11 L555   OK                                 cd bid-vector
pair 12 L588   OK                                 cd bid-vector
pair 13 L627   OK                                 cd bid-vector
pair 14 L736   OK                                 T=$(mktemp -d)
pair 15 L797   OK                                 # citescan.py 는 C-6 블록이 만든 "$T" 의 것을 그대로 쓴다
pair 16 L892   OK                                 T=$(mktemp -d)
pair 17 L934   OK                                 # ops07scan.py 는 C-7 블록이 만든 "$T" 의 것을 그대로 쓴다
pair 18 L953   OK                                 xargs grep -nEi 'api[_-]?key|secret|token|pa
pair 19 L977   OK                                 grep -ci 'react' docs/discovery/capability-m
pair 20 L989   OK                                 grep -h '^### Slice' milestone-*.md | wc -l
pair 21 L1004  OK                                 grep -ho 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs
pair 22 L1042  OK                                 grep -ho 'OPEN-ADR-[0-9]\{2\}' docs/adr/*.md
pair 23 L1067  OK                                 grep -n 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs/
pair 24 L1102  OK                                 T=$(mktemp -d)
pair 25 L1134  OK                                 echo "-- ADR 안의 OPS-13 전수"
pair 26 L1195  SKIP EXT <_workspace/>             echo "-- 실행 메타 · 출처: _workspace/m0-0d/codex.
pair 27 L1236  SKIP EXT <~/>                      echo "-- preflight ① consolidate 이후 이 저장소 흔적
pair 28 L1363  SKIP SELF <==0D-BLOCK-HARNESS==>   # ==0D-BLOCK-HARNESS== — 이 쌍은 대조 대상에서 빠진다 (S
pairs_total=29 replayed=26 skipped_SELF=1 skipped_EXT=2 diff=0
```

**판정**: `diff=0`이면 **위 출력이 `replayed=`로 센 쌍이 선언 SHA `5012bde`의 트리에서
축어로 다시 뜬다.** 빠진 쌍은 `SKIP` 행이 사유 코드와 **그 사유의 근거 토큰**까지 함께
내고, 사유별 수는 마지막 줄의 `skipped_SELF=`·`skipped_EXT=`가 낸다.

**이 검사가 재는 범위**: `commands.md`의 명령/출력 쌍 중 **래칫 대상인 것**이다 —
전체 수와 대조한 수는 `pairs_total=`·`replayed=`가 낸다. **재지 않는 것** —
`checklist.md`·`scope.md`의 인라인 블록, 각 블록 아래 **판정 산문의 참·거짓**,
**이 블록 자신**(`SELF`), 그리고 **선언 SHA 트리 밖의 상태를 읽는 블록**(`EXT`).
`diff=0`은 *"래칫 대상 기록이 그 트리에서 다시 뜬다"*이지 *"기록이 옳다"*가 아니며,
`EXT`로 빠진 기록에 대해서는 **아무 말도 하지 않는다** — 그 기록은 시점 기록이고
그 사실은 C-12 절이 적는다.

> 이 검사는 `git worktree`를 하나 만들고 지운다. legacy 인용 블록을 위해 그 worktree
> 안에 `bid-vector` symlink를 건다 — `.gitignore` 대상이라 `tracked_dirty`에 잡히지
> 않는다. **`_workspace` symlink는 걸지 않는다.** 그 경로를 읽는 블록은 `EXT`로 빠져
> 재생되지 않으므로 필요가 없고, **걸지 않는 것이 그 규칙을 실행으로 강제한다** —
> 대조 대상 블록이 `_workspace/`를 읽기 시작하면 그 자리에서 깨진다.
