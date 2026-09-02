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


def holds(case, mutated):
    """계약(`verified_paths` 정확 비교 ∪ `verified_projections` 술어)이 원본과 같은 판정인가.

    술어 어휘는 `not-equals` 하나로 **동결**돼 있다(운영자 결정 2026-09-02,
    `manifest.yaml` 의 `schema.extensions.verified_projections`). 모르는 술어는
    조용히 통과시키지 않고 예외를 낸다.
    """
    original = load_expected(case)
    for path in case.get("verified_paths") or []:
        if get(mutated, path) != get(original, path):
            return False
    for entry in case.get("verified_projections") or []:
        if entry["projection"] != "not-equals":
            raise ManifestFormatError("동결된 어휘 밖의 술어: %r" % entry["projection"])
        status, value = get(mutated, entry["path"])
        left, right = value, entry["operand"]
        if entry.get("normalize") == "case-fold":
            left = left.casefold() if isinstance(left, str) else left
            right = right.casefold() if isinstance(right, str) else right
        if not (status == "OK" and left != right):
            return False
    return True


def authoritative_cases(path=MANIFEST):
    return {c["id"]: c for c in read_cases(path) if c["classification"] == "authoritative"}
