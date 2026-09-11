# S-7 양성 대조 표본(verifier r1 F-3 — 역방향) — evidence 행은 있는데 docstring 에 Reuse
# 포인터가 없는 모듈.

`ml-engine/tools/reuse_provenance_check.py --evidence
tests/gates/fixtures/reuse-claims-fake-pointer.md`가 이 파일 하나만 evidence 로 쓰면
`design_ratchet.py` 행은 실제 docstring 과 일치해 위반이 없지만, `generate_contracts.py`
행은 그 모듈이 (정당하게) `Reuse:` 줄이 없는 신규 작성 코드라 위반이어야 한다
(D-6.1 #4 (ㄱ) 「있음」 위반 — evidence 에만 있고 docstring 에 없다).

| module | original_path | commit | 수행한 수정·튜닝 |
| --- | --- | --- | --- |
| ml-engine/tools/design_ratchet.py | bid-vector/scripts/_design_ratchet_scan.py | ed4b06c | (정상 행 — 실제 docstring 포인터와 일치) |
| ml-engine/tools/generate_contracts.py | bid-vector/scripts/some_fake_source.py | ed4b06c | 양성 대조 — 이 모듈은 신규 작성이라 Reuse 포인터가 없다. 이 행이 역방향 대조를 걸어야 한다 |
