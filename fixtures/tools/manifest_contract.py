#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""`fixtures/manifest.yaml` 의 **계약 부분만** 읽고 평가하는 공용 모듈.

두 스윕 스크립트(`mutation_sweep_targeted.py` · `mutation_sweep_adversarial.py`)가
공유한다. 하는 일은 셋뿐이다.

1. `cases` 에서 **계약에 필요한 키만** 뽑는다 — `id` · `classification` ·
   `input_file` · `expected_file` · `verified_paths` · `verified_projections`.
2. `$.a.b[0]` 형태의 경로로 기대값 JSON 을 읽고(`get`) 쓴다(`set_path`).
3. 변이된 기대값 사본에 대해 **계약이 원본과 같은 판정을 내는가**를 본다(`holds`).

**왜 PyYAML 을 쓰지 않는가.** 이 저장소의 `python3` 에 `PyYAML` 이 없다 —
`fixtures-commands.md` **F-7** 이 그 사실 때문에 격리 venv 를 쓴다고 적고, 커밋된
다른 도구(`check_legacy_numbers.py`)는 표준 라이브러리만 쓴다. 스윕은 **clean HEAD
에서 추가 설치 없이 재실행되어야** 하므로(운영자 승인 2026-09-02, Codex B10 medium #3)
manifest 의 **계약 부분만 읽는 좁은 reader** 를 여기 둔다.

**reader 는 범용 YAML 파서가 아니다.** 이 manifest 가 실제로 쓰는 블록 형태만 안다.
아는 형태 밖을 만나면 **조용히 넘기지 않고 `ManifestFormatError` 를 낸다** — 오독이
분류를 조용히 바꾸는 것이 이 자리의 유일한 위험이라 그쪽을 막는다.
`PyYAML` 이 있는 감사자는 `--crosscheck-pyyaml` 로 두 결과의 일치를 직접 잰다
(두 스윕 스크립트 모두 그 옵션을 받는다).
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MANIFEST = os.path.join(ROOT, "fixtures", "manifest.yaml")

# reader 가 뽑는 키. 이 밖의 키는 읽지 않고 건너뛴다.
SCALAR_KEYS = ("id", "classification", "input_file", "expected_file")
LIST_KEYS = ("verified_paths",)
MAPLIST_KEYS = ("verified_projections",)
PROJECTION_KEYS = ("path", "projection", "operand", "normalize")

_BLOCK_INDICATORS = ("|", "|-", "|+", ">", ">-", ">+")


class ManifestFormatError(Exception):
    """reader 가 아는 형태 밖을 만났다. 조용한 오독 대신 여기서 멈춘다."""


class DELETE(object):
    """`set_path` 에 주면 그 키를 지운다(경로 부재 변이)."""


def _indent(line):
    return len(line) - len(line.lstrip(" "))


def _unquote(raw):
    s = raw.strip()
    if s in _BLOCK_INDICATORS:
        raise ManifestFormatError("계약 키가 블록 스칼라다: %r" % raw)
    if len(s) >= 2 and s[0] == s[-1] and s[0] in "\"'":
        body = s[1:-1]
        if s[0] == '"':
            body = body.replace('\\"', '"').replace("\\\\", "\\")
        return body
    return s


def _keys_at(lines, lo, hi, indent):
    """[lo, hi) 에서 들여쓰기 `indent` 인 매핑 키를 (key, inline, sub_lo, sub_hi) 로 낸다."""
    pattern = re.compile(r"^ {%d}([A-Za-z_][A-Za-z0-9_]*):( .*)?$" % indent)
    heads = []
    for i in range(lo, hi):
        line = lines[i]
        if not line.strip() or line.lstrip().startswith("#") or _indent(line) != indent:
            continue
        m = pattern.match(line)
        if not m:
            raise ManifestFormatError("들여쓰기 %d 에서 매핑 키가 아니다 (line %d): %r"
                                      % (indent, i + 1, line))
        heads.append((i, m.group(1), (m.group(2) or "").strip()))
    out = []
    for n, (i, key, inline) in enumerate(heads):
        end = heads[n + 1][0] if n + 1 < len(heads) else hi
        out.append((key, inline, i + 1, end))
    return out


def _string_list(lines, lo, hi):
    values = []
    for i in range(lo, hi):
        line = lines[i]
        if not line.strip():
            continue
        m = re.match(r"^ {6}- (.*)$", line)
        if not m:
            raise ManifestFormatError("문자열 목록 항목이 아니다 (line %d): %r" % (i + 1, line))
        values.append(_unquote(m.group(1)))
    return values


def _map_list(lines, lo, hi):
    """`- key: value` 로 시작하고 들여쓰기 8 의 키가 따라붙는 항목 목록."""
    starts = [i for i in range(lo, hi)
              if lines[i].strip() and re.match(r"^ {6}- [A-Za-z_]", lines[i])]
    items = []
    for n, s in enumerate(starts):
        end = starts[n + 1] if n + 1 < len(starts) else hi
        m = re.match(r"^ {6}- ([A-Za-z_][A-Za-z0-9_]*):( .*)?$", lines[s])
        if not m:
            raise ManifestFormatError("매핑 목록 항목이 아니다 (line %d): %r" % (s + 1, lines[s]))
        item = {m.group(1): _unquote(m.group(2) or "")}
        for key, inline, _, _ in _keys_at(lines, s + 1, end, 8):
            if key in PROJECTION_KEYS:
                item[key] = _unquote(inline)
        items.append(item)
    return items


def read_cases(path=MANIFEST):
    """manifest 의 `cases` 를 계약 키만 담은 dict 목록으로 낸다."""
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")
    try:
        top = next(i for i, l in enumerate(lines) if l.rstrip() == "cases:")
    except StopIteration:
        raise ManifestFormatError("`cases:` 를 찾지 못했다: %s" % path)
    end = len(lines)
    for i in range(top + 1, len(lines)):
        if lines[i][:1].strip() and not lines[i].startswith("#"):
            end = i
            break
    starts = [i for i in range(top + 1, end) if lines[i].startswith("  - ")]
    for i in range(top + 1, end):
        line = lines[i]
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if _indent(line) < 2 and i not in starts:
            raise ManifestFormatError("`cases` 안에 예상 밖의 줄 (line %d): %r" % (i + 1, line))

    cases = []
    for n, s in enumerate(starts):
        stop = starts[n + 1] if n + 1 < len(starts) else end
        m = re.match(r"^  - ([A-Za-z_][A-Za-z0-9_]*):( .*)?$", lines[s])
        if not m:
            raise ManifestFormatError("case 머리 줄이 아니다 (line %d): %r" % (s + 1, lines[s]))
        case = {m.group(1): _unquote(m.group(2) or "")}
        for key, inline, lo, hi in _keys_at(lines, s + 1, stop, 4):
            if key in SCALAR_KEYS:
                case[key] = _unquote(inline)
            elif key in LIST_KEYS:
                case[key] = _string_list(lines, lo, hi)
            elif key in MAPLIST_KEYS:
                case[key] = _map_list(lines, lo, hi)
        cases.append(case)
    return cases


def crosscheck_pyyaml(path=MANIFEST):
    """`PyYAML` 로 같은 투영을 뽑아 reader 결과와 대조한다. 어긋나면 예외."""
    import yaml  # 선택 의존. 이 함수 밖에서는 쓰지 않는다.

    def project(case):
        out = {k: case.get(k) for k in SCALAR_KEYS}
        out["verified_paths"] = case.get("verified_paths")
        raw = case.get("verified_projections")
        out["verified_projections"] = None if raw is None else [
            {k: p.get(k) for k in PROJECTION_KEYS if k in p} for p in raw]
        return out

    with open(path, encoding="utf-8") as fh:
        reference = [project(c) for c in yaml.safe_load(fh)["cases"]]
    mine = [project(c) for c in read_cases(path)]
    if mine != reference:
        diff = [(a, b) for a, b in zip(mine, reference) if a != b]
        raise ManifestFormatError("reader 가 PyYAML 과 어긋난다: %r" % (diff[:3],))
    return len(reference)


def resolve(path):
    """`$.a.b[0]` 를 키 목록으로 쪼갠다."""
    return path[2:].split(".")


def get(obj, path):
    """(`'OK'`|`'MISS'`, 값). 부재를 예외 대신 값으로 낸다 — 변이가 경로를 지운다."""
    cur = obj
    for key in resolve(path):
        if "[" in key:
            key, index = key[:-1].split("[")
            cur = cur[key][int(index)]
        else:
            if not isinstance(cur, dict) or key not in cur:
                return ("MISS", None)
            cur = cur[key]
    return ("OK", cur)


def set_path(obj, path, value):
    keys = resolve(path)
    cur = obj
    for key in keys[:-1]:
        if "[" in key:
            key, index = key[:-1].split("[")
            cur = cur[key][int(index)]
        else:
            cur = cur[key]
    if value is DELETE:
        cur.pop(keys[-1], None)
    else:
        cur[keys[-1]] = value


def expected_path(case):
    return os.path.join(ROOT, case["expected_file"])


def load_expected(case):
    with open(expected_path(case), encoding="utf-8") as fh:
        return json.load(fh)


def all_cases(path=MANIFEST):
    """id → case 전수. `differs-from-case` 가 거울 case 를 찾는 자리다."""
    return {c["id"]: c for c in read_cases(path)}


def _folded(left, right, normalize):
    if normalize == "case-fold":
        left = left.casefold() if isinstance(left, str) else left
        right = right.casefold() if isinstance(right, str) else right
    return left, right


def _mirror_case(case, operand, cases, manifest):
    """`differs-from-case` 의 거울 case. 자기 참조와 미지 id 를 거부한다."""
    if operand == case["id"]:
        raise ManifestFormatError("`differs-from-case` 가 자기 자신을 거울로 든다: %r" % operand)
    registry = all_cases(manifest) if cases is None else cases
    if operand not in registry:
        raise ManifestFormatError("`differs-from-case` 의 거울 case 를 찾지 못했다: %r" % operand)
    return registry[operand]


def holds(case, mutated, cases=None, manifest=MANIFEST):
    """계약(`verified_paths` 정확 비교 ∪ `verified_projections` 술어)이 원본과 같은 판정인가.

    **술어 어휘는 넷이다** — `not-equals`(2026-09-02 동결분) 에 **운영자 결정 2026-09-05
    decision 18** 이 `is-present` · `differs-from-path` · `differs-from-case` 를 더했다.
    정의의 정본은 `manifest.yaml` 의 `schema.extensions.verified_projections` 이고 이 함수는
    그 정의의 실행이다. **모르는 술어는 조용히 통과시키지 않고 예외를 낸다.**

    **`not-equals` 의 의미는 한 글자도 바뀌지 않았다** — null 치환이 이 술어를 빠져나가는
    것은 문서화된 한계이고(`uncovered_axes` 「계약 술어 부재」 축의 ②), 그 구멍을 여기서
    조용히 메우면 동결분의 의미를 몰래 바꾸는 것이 된다. 새 술어 셋에만 **존재·비-null
    전건**이 선다.
    """
    original = load_expected(case)
    for path in case.get("verified_paths") or []:
        if get(mutated, path) != get(original, path):
            return False
    for entry in case.get("verified_projections") or []:
        projection = entry["projection"]
        normalize = entry.get("normalize")
        status, value = get(mutated, entry["path"])

        if projection == "not-equals":
            left, right = _folded(value, entry["operand"], normalize)
            if not (status == "OK" and left != right):
                return False
            continue

        if projection not in ("is-present", "differs-from-path", "differs-from-case"):
            raise ManifestFormatError("어휘 밖의 술어: %r" % projection)

        # 새 술어 셋의 공통 전건 — (a) 삭제와 (b) null 치환이 여기서 걸린다.
        if status != "OK" or value is None:
            return False

        if projection == "is-present":
            if entry.get("operand") not in (None, ""):
                raise ManifestFormatError("`is-present` 는 피연산자를 받지 않는다: %r" % entry)
            continue

        if projection == "differs-from-path":
            other_status, other = get(mutated, entry["operand"])
        else:
            mirror = _mirror_case(case, entry["operand"], cases, manifest)
            other_status, other = get(load_expected(mirror), entry["path"])
        if other_status != "OK" or other is None:
            return False
        left, right = _folded(value, other, normalize)
        if left == right:
            return False
    return True


def authoritative_cases(path=MANIFEST):
    return {c["id"]: c for c in read_cases(path) if c["classification"] == "authoritative"}


# -----------------------------------------------------------------------------
# 술어 self-check — 어휘 넷의 실행이 정의와 맞는가
#
# **corpus 를 읽지 않는다.** 임시 디렉터리에 최소 기대값 두 개를 쓰고 그 위에서만 돈다 —
# 실제 fixture·manifest 는 열지 않으므로 case 가 바뀌어도 이 검사는 흔들리지 않는다.
# 새 도구를 만들지 않으려고 여기 둔다(1B-c scope: acceptance 명령 집합을 늘리지 않는다) —
# **C-5** 가 `mutation_sweep_adversarial.py` 를 통해 이것을 함께 돌린다.
# -----------------------------------------------------------------------------
_SUBJECT = {"fact": "Known", "rate": {"fraction": 0.875}, "left": "X", "right": "Y"}
_MIRROR = {"fact": "Absent", "rate": None}


def _case(tmp, cid, expected, **extra):
    target = os.path.join(tmp, cid + ".json")
    with open(target, "w", encoding="utf-8") as fh:
        json.dump(expected, fh)
    case = {"id": cid, "classification": "authoritative", "expected_file": target}
    case.update(extra)
    return case


def self_check():
    """술어 넷의 통과·실패·거부를 잰다. 어긋나면 `ManifestFormatError`."""
    import copy
    import tempfile

    checks, failures = 0, []

    def expect(label, want, thunk):
        nonlocal checks
        checks += 1
        try:
            got = thunk()
        except ManifestFormatError as exc:
            got = ("raises", type(exc).__name__)
        if got != want:
            failures.append("%s — want %r, got %r" % (label, want, got))

    tmp = tempfile.mkdtemp(prefix="predicate-self-check-")
    subject = _case(tmp, "subject", _SUBJECT)
    mirror = _case(tmp, "mirror", _MIRROR)
    registry = {"subject": subject, "mirror": mirror}

    def run(case, mutations):
        mutated = copy.deepcopy(load_expected(case))
        for path, value in mutations:
            set_path(mutated, path, value)
        return holds(case, mutated, cases=registry)

    def with_projection(entry, **extra):
        case = dict(subject)
        case["verified_projections"] = [entry]
        case.update(extra)
        return case

    present = with_projection({"path": "$.fact", "projection": "is-present"})
    expect("is-present 무변이", True, lambda: run(present, []))
    expect("is-present (a) 삭제", False, lambda: run(present, [("$.fact", DELETE)]))
    expect("is-present (b) null", False, lambda: run(present, [("$.fact", None)]))
    expect("is-present (a′) 값 변이는 못 잡는다(명세대로)", True,
           lambda: run(present, [("$.fact", "Absent")]))
    expect("is-present 는 피연산자를 거부한다", ("raises", "ManifestFormatError"),
           lambda: run(with_projection(
               {"path": "$.fact", "projection": "is-present", "operand": "Known"}), []))

    from_path = with_projection(
        {"path": "$.left", "projection": "differs-from-path", "operand": "$.right"})
    expect("differs-from-path 무변이", True, lambda: run(from_path, []))
    expect("differs-from-path (b′) 거울 경로 값 대입", False,
           lambda: run(from_path, [("$.left", "Y")]))
    expect("differs-from-path (a) 삭제", False, lambda: run(from_path, [("$.left", DELETE)]))
    expect("differs-from-path (b) null", False, lambda: run(from_path, [("$.left", None)]))
    expect("differs-from-path 거울 경로가 비면 실패", False,
           lambda: run(from_path, [("$.right", None)]))

    from_case = with_projection(
        {"path": "$.fact", "projection": "differs-from-case", "operand": "mirror"})
    expect("differs-from-case 무변이", True, lambda: run(from_case, []))
    expect("differs-from-case (c′) 거울 case 값 대입", False,
           lambda: run(from_case, [("$.fact", "Absent")]))
    expect("differs-from-case (a) 삭제", False, lambda: run(from_case, [("$.fact", DELETE)]))
    expect("differs-from-case (b) null", False, lambda: run(from_case, [("$.fact", None)]))
    expect("differs-from-case case-fold 가 표기 변형을 접는다", False,
           lambda: run(with_projection({"path": "$.fact", "projection": "differs-from-case",
                                        "operand": "mirror", "normalize": "case-fold"}),
                       [("$.fact", "aBsEnT")]))
    expect("differs-from-case 자기 참조 거부", ("raises", "ManifestFormatError"),
           lambda: run(with_projection(
               {"path": "$.fact", "projection": "differs-from-case", "operand": "subject"}), []))
    expect("differs-from-case 미지 거울 거부", ("raises", "ManifestFormatError"),
           lambda: run(with_projection(
               {"path": "$.fact", "projection": "differs-from-case", "operand": "없는-case"}), []))
    expect("differs-from-case 거울 경로가 비면 실패", False,
           lambda: run(with_projection(
               {"path": "$.rate", "projection": "differs-from-case", "operand": "mirror"},
               verified_paths=[]), [("$.rate", {"fraction": 0.5})]))

    equals = with_projection(
        {"path": "$.fact", "projection": "not-equals", "operand": "Absent"})
    expect("not-equals 무변이", True, lambda: run(equals, []))
    expect("not-equals (c) 피연산자 치환", False, lambda: run(equals, [("$.fact", "Absent")]))
    expect("not-equals 의 null 구멍은 그대로다(동결분 의미 불변)", True,
           lambda: run(equals, [("$.fact", None)]))

    expect("미지 술어 거부", ("raises", "ManifestFormatError"),
           lambda: run(with_projection({"path": "$.fact", "projection": "equals-path",
                                        "operand": "$.right"}), []))

    for path in (subject["expected_file"], mirror["expected_file"]):
        os.remove(path)
    os.rmdir(tmp)

    if failures:
        raise ManifestFormatError("술어 self-check 실패 %d 건: %s" % (len(failures), failures))
    return checks


if __name__ == "__main__":
    print("술어 self-check OK — 검사", self_check())
