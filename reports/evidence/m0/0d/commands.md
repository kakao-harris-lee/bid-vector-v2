# 검증 명령과 출력 — M0 / 0D

**이 파일은 명령과 그 출력만 담는다.** 라운드 이력·자기평가 산문은
`reports/evidence/m0/0d/scope.md`에 있다. 판정은 각 블록 아래 한두 줄이며,
**셈과 좌표를 산문으로 옮기지 않는다** — 수는 그것을 내는 명령이 낸다.

## 선언 SHA와 실행 계약

**이 파일의 출력 블록은 전부 `b925b91` 트리에서 뜬 것이다** — 일부가 아니라 전부다.
`b925b91`은 이 커밋의 직전 커밋이며, 커밋 안의 블록은 자기 커밋 트리에서 뜰 수 없다.

| 블록 | 읽는 것 | 선언 SHA |
| --- | --- | --- |
| C-1 · C-2 · C-3 · C-3n · C-4 · C-7 · C-7n · C-8 · C-9 · C-10 | 이 저장소 | **`b925b91`** |
| C-5.1 ~ C-5.5 | legacy `bid-vector`만 | **`ed4b06c`** (이 저장소의 어느 HEAD에서도 같다) |
| C-6 · C-6n | **양쪽** — `citescan.py`가 `docs/adr`(저장소)와 legacy를 함께 읽는다 | **`b925b91` + `ed4b06c`** |
| C-11 | 이 파일 자신 ↔ 선언 SHA 트리 | **HEAD** (그 블록이 사유를 적는다) |

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

**C-11이 이 선언을 실행으로 확인한다** — 26쌍을 선언 SHA의 트리에 대고 축어 대조한다.

---

## C-1 · base / head / 작업 트리

```
git rev-parse HEAD
git status --porcelain | grep -vc '^??' | sed 's/^/tracked_dirty=/'
```

```
b925b918f54b6b40c5f777a92fe445ac76f672e6
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
slice_commits=11
slice_paths=12
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

## C-7 · `OPEN-OPS-07` 종료 조건 — OPS-21 후보 전부가 「대안」 절에 판정과 함께 있는가

후보는 `docs/discovery/capability-map.md` OPS-21 표의 것이다.

```
T=$(mktemp -d)
cat > "$T/ops07scan.py" <<'PY'
#!/usr/bin/env python3
"""OPS-21 후보가 지정 ADR 의 「대안」 절에서 판정 토큰과 같은 줄에 나오는지.
argv[1]=ADR 디렉터리. argv[2:] 는 `이름@ADR번호` 형식의 추가 후보(음성 대조용)."""
import pathlib, sys
PAIRS = [("Spring Modulith", "0005"), ("JobRunr", "0005"), ("db-scheduler", "0005"),
         ("Spring Integration JDBC lock registry", "0005"), ("ShedLock", "0005"),
         ("Resilience4j", "0005"), ("Micrometer", "0005"),
         ("ArchUnit", "0007"), ("Konsist", "0007"), ("Detekt", "0007")]
TOKENS = ["불채택", "채택", "판정 보류", "대상 아님", "조건부"]
adr_dir = pathlib.Path(sys.argv[1])
for extra in sys.argv[2:]:
    name, _, num = extra.partition("@")
    PAIRS.append((name, num or "0005"))

def alt_section(num):
    hits = list(adr_dir.glob(f"{num}-*.md"))
    if not hits:
        return None
    out, on = [], False
    for ln in hits[0].read_text(encoding="utf-8").splitlines():
        if ln.startswith("## "):
            on = "대안" in ln
            continue
        if on:
            out.append(ln)
    return (hits[0].name, out)

found = missing = 0
for cand, num in PAIRS:
    sec = alt_section(num)
    if sec is None:
        missing += 1
        print(f"MISSING\t{cand}\t(no ADR {num})\t-")
        continue
    fname, body = sec
    rows = [ln for ln in body if cand in ln]
    judged = sorted({t for ln in rows for t in TOKENS if t in ln})
    if rows and judged:
        found += 1
        print(f"FOUND\t{cand}\t{fname}\t{'|'.join(judged)}")
    elif rows:
        missing += 1
        print(f"NO-VERDICT\t{cand}\t{fname}\t(대안 절에 있으나 판정 토큰 없음)")
    else:
        missing += 1
        print(f"MISSING\t{cand}\t{fname}\t(대안 절에 없음)")
print(f"candidates={len(PAIRS)} found={found} missing={missing}")
PY
python3 "$T/ops07scan.py" docs/adr
```

```
FOUND	Spring Modulith	0005-domain-events-and-outbox.md	불채택|채택
FOUND	JobRunr	0005-domain-events-and-outbox.md	불채택|채택
FOUND	db-scheduler	0005-domain-events-and-outbox.md	불채택|채택
FOUND	Spring Integration JDBC lock registry	0005-domain-events-and-outbox.md	불채택|채택|판정 보류
FOUND	ShedLock	0005-domain-events-and-outbox.md	불채택|채택
FOUND	Resilience4j	0005-domain-events-and-outbox.md	채택
FOUND	Micrometer	0005-domain-events-and-outbox.md	불채택|채택
FOUND	ArchUnit	0007-test-pyramid-and-ratchet.md	불채택|채택
FOUND	Konsist	0007-test-pyramid-and-ratchet.md	불채택|채택
FOUND	Detekt	0007-test-pyramid-and-ratchet.md	조건부|채택
candidates=10 found=10 missing=0
```

**판정**: 후보 전부가 지정 ADR의 「대안」 절에 판정 토큰과 함께 있다 —
`OPEN-OPS-07`의 종료 조건(**ADR 대안 절 기입**)이 충족된다.

**이 스캐너가 잡지 못하는 것**: **어느 판정인지.** 표의 한 행이 다른 후보를 언급하면
토큰이 여러 개 뜬다(위 출력이 그렇다). **기계가 세우는 것은 「기입됐다」이고, 어느 쪽으로
판정됐는지는 사람이 ADR을 읽는다** — `regression-ledger.md` §0.5의 두 축 구분과 같다.

### C-7n · 음성 대조

```
python3 "$T/ops07scan.py" docs/adr 'Kafka@0005' 'RabbitMQ@0007' | tail -3
```

```
MISSING	Kafka	0005-domain-events-and-outbox.md	(대안 절에 없음)
MISSING	RabbitMQ	0007-test-pyramid-and-ratchet.md	(대안 절에 없음)
candidates=12 found=10 missing=2
```

**판정**: 「대안 절에 없음」을 실제로 낸다.

---

## C-8 · secret 스캔

```
xargs grep -nEi 'api[_-]?key|secret|token|password|passwd|BEGIN [A-Z ]*PRIVATE KEY|xox[baprs]-|ghp_|AKIA[0-9A-Z]{16}' < "$T0/slice_paths.txt" 2>/dev/null | wc -l | sed 's/^ *//;s/^/matches=/'
xargs grep -nEi '[a-z+]+://[A-Za-z0-9_.-]+:[^@/[:space:]]+@|AKIA[0-9A-Z]{16}|ghp_[A-Za-z0-9]{20,}|xox[baprs]-|BEGIN [A-Z ]*PRIVATE KEY|sk-[A-Za-z0-9]{20,}' < "$T0/slice_paths.txt" 2>/dev/null | wc -l | sed 's/^ *//;s/^/credential_literals=/'
```

```
matches=17
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
OPEN-OPS-07	count=12
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
OPEN-NOTI-02	count=1
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
OPEN-ADR-01 OPEN-ADR-02 OPEN-ADR-03 OPEN-ADR-04 OPEN-ADR-05 OPEN-ADR-06 OPEN-ADR-07 OPEN-ADR-08 OPEN-ADR-09 OPEN-ADR-10 OPEN-ADR-11
0001-target-architecture.md	OPEN-ADR-01,OPEN-ADR-02,OPEN-ADR-03,OPEN-ADR-04,OPEN-ADR-05,OPEN-ADR-06,OPEN-ADR-10
0003-contract-transport.md	OPEN-ADR-01,OPEN-ADR-11
0004-persistence-and-events.md	OPEN-ADR-01
0005-domain-events-and-outbox.md	OPEN-ADR-01
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
docs/adr/0001-target-architecture.md:175:| **A-3** | **업무별 배포 서비스로 분해(MSA)** | **불채택** | `v2-지침서.md` §3이 *"과도한 MSA가 아닌 두
docs/adr/0001-target-architecture.md:177:| **A-5** | **커널 일부만 Kotlin으로** (혼합 경계) | **불채택** | `OPEN-ML-01`이 (a)로 확정됐다 — 8
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
docs/adr/0005-domain-events-and-outbox.md:107:  **`OPS-13`은 이 행이 아니라 「아키텍처 규칙 강제」 행의 것**이며 그 항목의 정의는
docs/adr/0005-domain-events-and-outbox.md:188:### 3.5 아키텍처 규칙 강제 (OPS-13)
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

## C-11 · 출력 블록 전수 재현 — 선언 SHA 트리에서 축어 대조

**이 블록만 선언 SHA가 HEAD다.** 이 검사는 *"HEAD의 `commands.md`를 선언 SHA의 트리에
대고 맞춰 본다"*이므로 **HEAD의 파일 내용이 입력**이다. 선언 SHA 트리에는 이 블록이
없으니 거기서는 돌 수 없다. **자기 자신은 대조 대상에서 뺀다** — 아래 스크립트가
`==0D-BLOCK-HARNESS==` 마커가 든 쌍을 건너뛰고 그 수를 `skipped_harness`로 낸다.

블록 쌍은 fenced 블록의 **교대**(짝수=명령, 홀수=출력)로 집는다. 각 쌍 앞에서 루트로
돌아가고 셸은 하나라 `$T`·`$T0`가 이어진다 — 「실행 계약」 그대로다.

```
# ==0D-BLOCK-HARNESS== — 이 쌍은 대조 대상에서 빠진다
DECL=b925b91
WT=$(mktemp -d)/wt
git worktree add --detach "$WT" "$DECL" >/dev/null 2>&1
ln -sfn "$PWD/bid-vector" "$WT/bid-vector"
python3 - reports/evidence/m0/0d/commands.md "$WT" <<'PY'
import pathlib, subprocess, sys
md, wt = pathlib.Path(sys.argv[1]), sys.argv[2]
lines = md.read_text(encoding="utf-8").splitlines()
fence = [i for i, l in enumerate(lines) if l.startswith("`" * 3)]
assert len(fence) % 2 == 0
blocks = [(fence[k] + 1, fence[k + 1]) for k in range(0, len(fence), 2)]
assert len(blocks) % 2 == 0
pairs = [(blocks[k], blocks[k + 1]) for k in range(0, len(blocks), 2)]
MARK = "==0D-BLOCK-HARNESS=="
sel = [(c, o) for c, o in pairs if MARK not in chr(10).join(lines[c[0]:c[1]])]
SEP = "@@@0D-BLOCK-%d@@@"
script = ["set +e", 'ROOT="%s"' % wt, 'cd "$ROOT"']
for n, (c, _) in enumerate(sel):
    script.append('cd "$ROOT"')
    script.append('echo "%s"' % (SEP % n))
    script.extend(lines[c[0]:c[1]])
script.append('echo "%s"' % (SEP % len(sel)))
out = subprocess.run(["bash", "-c", chr(10).join(script)],
                     capture_output=True, text=True, cwd=wt).stdout
diff = 0
for n, (c, o) in enumerate(sel):
    a = out.index(SEP % n) + len(SEP % n)
    b = out.index(SEP % (n + 1))
    got = out[a:b].strip(chr(10))
    want = chr(10).join(lines[o[0]:o[1]]).strip(chr(10))
    ok = got == want
    diff += 0 if ok else 1
    print("pair %2d L%-5d %s  %s" % (n, o[0] + 1, "OK  " if ok else "DIFF", lines[c[0]][:56].rstrip()))
print("pairs=%d skipped_harness=%d diff=%d" % (len(sel), len(pairs) - len(sel), diff))
PY
git worktree remove --force "$WT"
```

```
pair  0 L44    OK    git rev-parse HEAD
pair  1 L65    OK    T0=$(mktemp -d)
pair  2 L121   OK    T=$(mktemp -d)
pair  3 L148   OK    # adrscan.py 는 C-3 블록이 만든 "$T" 의 것을 그대로 쓴다 — T 를 덮어쓰지 않는
pair  4 L171   OK    git log --format='%H %s' 998dc21..HEAD | grep 'm0-0d' |
pair  5 L205   OK    cd bid-vector
pair  6 L274   OK    cd bid-vector
pair  7 L291   OK    awk -F'\t' '$4>50 {printf "%s:%d-%d\t%d\t%s\n", $1,$2,$3
pair  8 L345   OK    cd bid-vector
pair  9 L416   OK    cd bid-vector
pair 10 L487   OK    cd bid-vector
pair 11 L537   OK    cd bid-vector
pair 12 L570   OK    cd bid-vector
pair 13 L609   OK    cd bid-vector
pair 14 L718   OK    T=$(mktemp -d)
pair 15 L773   OK    # citescan.py 는 C-6 블록이 만든 "$T" 의 것을 그대로 쓴다 — T 를 덮어쓰지 않
pair 16 L844   OK    T=$(mktemp -d)
pair 17 L871   OK    python3 "$T/ops07scan.py" docs/adr 'Kafka@0005' 'RabbitM
pair 18 L888   OK    xargs grep -nEi 'api[_-]?key|secret|token|password|passw
pair 19 L912   OK    grep -ci 'react' docs/discovery/capability-map.md | sed
pair 20 L924   OK    grep -h '^### Slice' milestone-*.md | wc -l | sed 's/^ *
pair 21 L939   OK    grep -ho 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs/adr/*.md |
pair 22 L976   OK    grep -ho 'OPEN-ADR-[0-9]\{2\}' docs/adr/*.md | sort -u |
pair 23 L1001  OK    grep -n 'OPEN-[A-Z]\{2,4\}-[0-9]\{2\}' docs/adr/*.md | g
pair 24 L1036  OK    T=$(mktemp -d)
pair 25 L1068  OK    echo "-- ADR 안의 OPS-13 전수"
pairs=26 skipped_harness=1 diff=0
```

**판정**: `diff=0`이면 **그 26쌍이 선언 SHA `b925b91`의 트리에서 축어로 다시 뜬다.**

**이 검사가 재는 범위**: `commands.md`의 명령/출력 쌍 26개다. **재지 않는 것** —
`checklist.md`·`scope.md`의 인라인 블록, 각 블록 아래 **판정 산문의 참·거짓**,
그리고 **이 블록 자신**. `diff=0`은 *"기록이 그 트리에서 다시 뜬다"*이지
*"기록이 옳다"*가 아니다.

> 이 검사는 `git worktree`를 하나 만들고 지운다. legacy 인용 블록을 위해 그 worktree
> 안에 `bid-vector` symlink를 건다 — `.gitignore` 대상이라 `tracked_dirty`에 잡히지 않는다.
