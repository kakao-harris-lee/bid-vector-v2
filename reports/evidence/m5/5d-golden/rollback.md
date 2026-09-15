# rollback — M5 / 5D-golden

**범위는 in_scope 경로 한정이다.** range revert 를 쓰지 않는다 — 이 브랜치에는 이 레인의 커밋만 있으나
`fixtures/manifest.yaml` 과 `fixtures/tools/mutation_sweep_adversarial.py` 는 **공유 파일**이라
다른 slice 의 줄이 함께 되돌아가는 형태를 애초에 만들지 않는다.

## 이 레인의 커밋

| 커밋 | 만진 것 | rollback 에서의 처리 |
| --- | --- | --- |
| `f138280` | `fixtures/{input,expected}/ml-kernel-*.json` 28 신설 · `fixtures/manifest.yaml` · `fixtures/tools/mutation_sweep_adversarial.py` | 신설 파일 삭제 + **공유 파일 둘의 hunk 만** 역적용 |
| (이 파일을 담은 커밋) | `reports/evidence/m5/5d-golden/**` 만 | 디렉터리 삭제 |

evidence 커밋이 **공유 파일을 만지지 않는다** — 그래서 위 표가 낡지 않는다(2026-09-10 규율).

## 절차

```sh
WT=/Users/harris/Development/private/bid-vector-v2-m5cur

# ① 신설 fixture 28 삭제 — base 에 없던 경로다
git -C "$WT" rm -q -- \
  fixtures/input/ml-kernel-001.json    fixtures/expected/ml-kernel-001.json \
  fixtures/input/ml-kernel-002.json    fixtures/expected/ml-kernel-002.json \
  fixtures/input/ml-kernel-003.json    fixtures/expected/ml-kernel-003.json \
  fixtures/input/ml-kernel-004.json    fixtures/expected/ml-kernel-004.json \
  fixtures/input/ml-kernel-005.json    fixtures/expected/ml-kernel-005.json \
  fixtures/input/ml-kernel-006.json    fixtures/expected/ml-kernel-006.json \
  fixtures/input/ml-kernel-007.json    fixtures/expected/ml-kernel-007.json \
  fixtures/input/ml-kernel-008.json    fixtures/expected/ml-kernel-008.json \
  fixtures/input/ml-kernel-009.json    fixtures/expected/ml-kernel-009.json \
  fixtures/input/ml-kernel-010.json    fixtures/expected/ml-kernel-010.json \
  fixtures/input/ml-kernel-011.json    fixtures/expected/ml-kernel-011.json \
  fixtures/input/ml-kernel-012.json    fixtures/expected/ml-kernel-012.json \
  fixtures/input/ml-kernel-013.json    fixtures/expected/ml-kernel-013.json \
  fixtures/input/ml-kernel-014.json    fixtures/expected/ml-kernel-014.json

# ② 공유 파일 둘 — f138280 의 hunk 만 역적용
git -C "$WT" diff f138280~1..f138280 -- fixtures/manifest.yaml fixtures/tools/mutation_sweep_adversarial.py \
  | git -C "$WT" apply -R --index

# ③ evidence 삭제
git -C "$WT" rm -rq -- reports/evidence/m5/5d-golden
```

**②가 conflict 를 내면**(다른 레인이 같은 삽입 지점에 줄을 넣은 경우) `--3way` 로도 자동 해소되지
않을 수 있다. 그때의 수동 절차는 이렇다 — `fixtures/manifest.yaml` 에서 `ml-kernel-001` 부터
`ml-kernel-014` 의 `review:` 블록 끝까지를 통째로 잘라내고(`  - id: ml-kernel-001` 부터
`  - id: ml-kernel-014` 의 마지막 `      approved_by_user: false` 까지), `uncovered_axes` 의
`axis: ML — LightGBM 런타임 오류 …` 항목을 `f138280~1` 의 `axis: ML — 피처 스키마 version …` 항목으로
되돌린다. `mutation_sweep_adversarial.py` 는 `ASSERTED` · `NULL_ASSERTED` · `ADVERSARIAL_VALUE` 에서
`ml-kernel` 로 시작하는 줄과 그 위 `# M5/5D` 주석 블록만 지운다. **남의 줄이 남아 있는지**를 함께 확인한다.

## 확인 — 되돌린 트리가 서는가

「파일이 제자리로 갔는가」만 재지 않는다(2026-09-08 규율). 네 단계 전부 통과해야 rollback 성립이다.

```sh
# (1) in_scope diff 0
git -C "$WT" diff f138280~1 --stat -- fixtures reports/evidence/m5/5d-golden   # 출력 없음
# (2) manifest reader 가 base 값을 낸다 — case 105 · authoritative 85
python3 -c "import sys;sys.path.insert(0,'fixtures/tools');import manifest_contract as mc;print(len(mc.read_cases()),len(mc.authoritative_cases()))"
# (3) 적대 스윕이 base 판정을 낸다 — 강등 verdict-004 하나(exit 1, 이월 부채)
python3 fixtures/tools/mutation_sweep_adversarial.py
# (4) 표적 스윕 exit 0 · prose 불일치 3
python3 fixtures/tools/mutation_sweep_targeted.py && python3 fixtures/tools/manifest_prose_consistency.py | tail -1
```

**실측**: 임시 clone 에서 위 절차를 끝까지 실행했고 결과는 `checklist.md` 2절 아래 「rollback 실측」이 적는다.
