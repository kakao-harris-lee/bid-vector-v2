# M2/2B rollback.md

정본은 **in_scope 경로 한정 복원**이다(range revert 금지 — 2026-09-04 관례, `55ecdcc..HEAD`
range에는 이 slice와 무관한 병행 레인 커밋도 섞여 있어 range revert는 그것까지 되돌린다).
하네스 경로(`CLAUDE.md`·`.claude/**`)와 승인 문서(`milestone-2.md`, 이 slice에서 편집하지
않았다)는 대상이 아니다.

## 되돌리는 것

- 신규 파일(`--source=<base>`에 없음 → 복원 시 삭제):
  - `contracts/proto/bidvector/ml/v1/prediction.proto`
  - `contracts/proto/bidvector/ml/v1/features.proto`
  - `contracts/testdata/prediction/**`(binpb 6 + json 6)
  - `adapters/src/test/kotlin/bidvector/adapters/contract/PredictionContractTest.kt`
  - `ml-engine/tests/test_prediction_contract.py`
- 편집 파일(`--source=<base>`가 있음 → 2A 상태로 복원):
  - `config/quality/gate-tests.properties`(`gate.tests.adapters`에서 `PredictionContractTest`
    항목 제거)

## 명령

```bash
BASE=55ecdcc
git restore --source="$BASE" --staged --worktree -- \
  contracts/proto/bidvector/ml/v1/prediction.proto \
  contracts/proto/bidvector/ml/v1/features.proto \
  contracts/testdata/prediction \
  adapters/src/test/kotlin/bidvector/adapters/contract/PredictionContractTest.kt \
  ml-engine/tests/test_prediction_contract.py \
  config/quality/gate-tests.properties
```

`--source`에 없는 경로(신규 파일 5종)는 `git restore`가 작업 트리에서 삭제한다 — 별도
`git rm`이 필요 없다(2026-09-04 관례, Codex 1A 16차 high가 `git checkout <base> --`의
pathspec 오류를 지적한 뒤의 정정된 형태).

## 확인 지점

- **`git status --porcelain -- <위 6개 경로>`는 빈 출력이 아니다**(verifier r1 F-5 —
  이전 판은 이 자리를 「빈 출력」으로 잘못 적었다). `git restore --staged --worktree`는
  작업 트리를 base 상태로 되돌리되 그 변경 자체는 staged 상태로 **남는다** — 실제로는
  신규 파일 17개가 `D`(삭제), `config/quality/gate-tests.properties` 1개가 `M`(수정)으로
  17행이 찍힌다(임시 clone 실측). 「복원됐다」의 확인은 이 명령이 빈 것이 아니라 **아래
  두 가지**로 한다.
- `git diff "$BASE" -- config/quality/gate-tests.properties`가 빈 출력(작업 트리 내용이
  2A 상태와 완전히 같다 — staged 표시와 무관하게 파일 바이트 자체는 base와 동일하다는
  뜻).
- `(cd contracts && buf lint && buf build)`가 exit 0(2A 상태 — `service` 없는 계약으로
  정상 복귀, `BidPredictionService`가 사라져도 `common.proto`·`error.proto`만으로 계약이
  자족한다).
- (선택) 완전히 커밋까지 마치려면 `git add -A`(이 좁은 6경로 범위에서만, 트리 전체에
  쓰지 않는다) 후 커밋 — 그러면 `git status --porcelain`이 빈 출력이 된다. 이 evidence의
  범위는 「작업 트리 복원」까지이고 커밋 여부는 운영자 결정.

## 예상 복구 시간

1분 미만(파일 삭제·`git restore` 한 번, 재빌드 불필요 — S-0/S-1 재실행은 검증 목적일 때만).

## 실측(임시 clone)

`commands.md` 「rollback 실측(임시 clone)」 참고 — 위 명령을 `/tmp` 임시 clone에서 그대로
실행해 exit 0과 대상 경로 삭제/복원을 확인했다.
