# S-7 양성 대조 표본 — 의도적으로 어긋난 evidence

`ml-engine/tools/reuse_provenance_check.py --evidence tests/gates/fixtures/reuse-mismatch.md`가
이 파일 하나만 evidence 로 쓰면 `ml-engine/tools/design_ratchet.py`의 실제 docstring 포인터
(`bid-vector/scripts/_design_ratchet_scan.py@ed4b06c`)와 아래 행의 commit 이 달라 exit 1 이어야
한다(D-6.1 #4 (ㄴ) 「같음」 위반).

| module | original_path | commit | 수행한 수정·튜닝 |
| --- | --- | --- | --- |
| ml-engine/tools/design_ratchet.py | bid-vector/scripts/_design_ratchet_scan.py | 0000000 | 의도적으로 틀린 commit — 양성 대조용 |
