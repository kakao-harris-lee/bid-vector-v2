# M5/5D-3 — reuse.md

새 `Reuse:` 태그 파일 없음(신규 이식 모듈 없음) — 이 slice는 기존 이식 모듈(`distribution.py`·
`observations.py`)의 `Reuse:` 헤더 줄을 바꾸지 않았다. `facts.py`는 5B 신규 작성 모듈이라
애초에 `Reuse:` 태그 대상이 아니다(재확인 — `tools/reuse_provenance_check.py`의 대상 집합은
docstring 첫 줄이 `Reuse:` 패턴인 파일만).

## 양방향 확인 (`tools/reuse_provenance_check.py`)

| module | original_path@commit(코드 docstring) | reuse.md 소재 |
| --- | --- | --- |
| `ml-engine/src/ml_engine/inference/distribution.py` | `bid-vector/app/ai/predictors/distribution.py@ed4b06c`(변경 없음) | `reports/evidence/m5/5d2/reuse.md`(row 12, 5D-2 소유 — 편집하지 않음) |
| `ml-engine/src/ml_engine/inference/observations.py` | `bid-vector/app/ai/predictors/distribution_extraction.py@ed4b06c`(변경 없음) | `reports/evidence/m5/5d2/reuse.md`(row 10, 5D-2 소유 — 편집하지 않음) |

`uv run python tools/reuse_provenance_check.py`(commands.md S-7)가 위 두 파일의 경로·commit
이 5D-2 `reuse.md`의 기존 행과 여전히 일치함을 실측한다(경로 조인 키 불변 — S-7 exit 0).

## 알려진 제한 — 5D-2 `reuse.md` 서술의 부분 낡음(문서는 편집하지 않는다)

5D-2 `reuse.md` row 12(`distribution.py`)의 프로즈는 「global 레벨만 채운다」·
「`SegmentMissing()`을 낸다」로 이 slice가 바꾼 동작을 더는 정확히 서술하지 않는다
(경로·commit 조인 키는 불변이라 `reuse_provenance_check.py`는 계속 통과한다 — 이 도구는
프로즈를 대조하지 않는다). 정정된 서술은 이 문서와 `checklist.md`(D-5D3-1~7)·
`distribution.py` 모듈 docstring이 정본이다. 5D-2 evidence는 그 slice 종결 시점의 기록으로
남겨 둔다(다른 slice의 evidence를 편집하지 않는다, 하네스 규율).
