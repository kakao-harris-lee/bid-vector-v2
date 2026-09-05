# rollback — M1 / 1B-c

정본. `scope.md` 의 `rollback` 절이 가리키는 자리(evidence-pack 규격, 1B 관례 승계).
경로 한정 복원 + 신규 경로 삭제. **range revert 금지** — in_scope 경로에만 적용한다.

base_sha: `a5ea955c44a935c3245288e6a72c830eeeee64b8`

## 변경 파일 전건 (`git diff --name-status a5ea955c..HEAD`, in_scope 경로만)

`M` = base 에도 있던 파일(내용만 복원), `A` = 이 slice 가 신설(파일째 삭제).

```
M  app/build.gradle.kts
A  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt
M  config/quality/gate-tests.properties
M  docs/discovery/capability-map.md
M  fixtures/expected/money-basis-001.json
M  fixtures/expected/money-basis-002.json
M  fixtures/expected/money-basis-004.json
M  fixtures/expected/money-basis-005.json
M  fixtures/expected/rate-unit-001.json
M  fixtures/expected/rate-unit-002.json
M  fixtures/expected/rate-unit-003.json
M  fixtures/expected/rate-unit-004.json
M  fixtures/expected/rate-unit-005.json
M  fixtures/input/money-basis-001.json
M  fixtures/input/money-basis-004.json
M  fixtures/input/money-basis-005.json
M  fixtures/manifest.yaml
M  fixtures/tools/manifest_contract.py
M  fixtures/tools/mutation_sweep_adversarial.py
M  fixtures/tools/mutation_sweep_targeted.py
M  gradle/libs.versions.toml
M  milestone-1.md
A  reports/evidence/m1/1b-c/commands.md
A  reports/evidence/m1/1b-c/fixtures.md
A  reports/evidence/m1/1b-c/golden-manifest.json
A  reports/evidence/m1/1b-c/rollback.md
A  reports/evidence/m1/1b-c/scope.md
A  reports/evidence/m1/1b-c/checklist.md
M  shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt
A  shared-kernel/src/test/resources/compile-fixtures/mutant-12-rate-undeclared-unit-typo.kt.txt
A  shared-kernel/src/test/resources/compile-fixtures/negative-12-rate-undeclared-unit.kt.txt
A  shared-kernel/src/test/resources/compile-fixtures/positive-12-rate-declared-unit.kt.txt
```

`shared-kernel/build.gradle.kts` 와 `shared-kernel/src/testFixtures/**` 는 D5(d) 전환으로
**이미 무접촉** — `git diff HEAD -- shared-kernel/build.gradle.kts` 가 빈 것을 커밋 전
확인했다(`commands.md` 참고). 롤백 목록에 없다.

## 복원 명령 (경로 개별 인자 — 변수 확장 금지, 1A 교훈)

```bash
BASE=a5ea955c44a935c3245288e6a72c830eeeee64b8

# M(base 에도 있던 파일) — 내용 복원
git restore --source="$BASE" --staged --worktree -- \
  "app/build.gradle.kts" \
  "config/quality/gate-tests.properties" \
  "docs/discovery/capability-map.md" \
  "fixtures/expected/money-basis-001.json" \
  "fixtures/expected/money-basis-002.json" \
  "fixtures/expected/money-basis-004.json" \
  "fixtures/expected/money-basis-005.json" \
  "fixtures/expected/rate-unit-001.json" \
  "fixtures/expected/rate-unit-002.json" \
  "fixtures/expected/rate-unit-003.json" \
  "fixtures/expected/rate-unit-004.json" \
  "fixtures/expected/rate-unit-005.json" \
  "fixtures/input/money-basis-001.json" \
  "fixtures/input/money-basis-004.json" \
  "fixtures/input/money-basis-005.json" \
  "fixtures/manifest.yaml" \
  "fixtures/tools/manifest_contract.py" \
  "fixtures/tools/mutation_sweep_adversarial.py" \
  "fixtures/tools/mutation_sweep_targeted.py" \
  "gradle/libs.versions.toml" \
  "milestone-1.md" \
  "shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt"

# A(이 slice 가 신설) — 삭제
git rm -f -- \
  "app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt" \
  "reports/evidence/m1/1b-c/commands.md" \
  "reports/evidence/m1/1b-c/fixtures.md" \
  "reports/evidence/m1/1b-c/golden-manifest.json" \
  "reports/evidence/m1/1b-c/rollback.md" \
  "reports/evidence/m1/1b-c/scope.md" \
  "reports/evidence/m1/1b-c/checklist.md" \
  "shared-kernel/src/test/resources/compile-fixtures/mutant-12-rate-undeclared-unit-typo.kt.txt" \
  "shared-kernel/src/test/resources/compile-fixtures/negative-12-rate-undeclared-unit.kt.txt" \
  "shared-kernel/src/test/resources/compile-fixtures/positive-12-rate-declared-unit.kt.txt"
```

## 임시 clone 실측 (evidence-pack 2026-09-04 의무)

```bash
CLONE=/tmp/1bc-rollback-rehearsal
rm -rf "$CLONE"
git clone --no-local /Users/harris/Development/private/bid-vector-v2 "$CLONE"
cd "$CLONE"
# 위 restore·rm 명령을 그대로 실행
git status --short   # in_scope 경로 전건이 staged 로 반영됐는지 확인
git diff --cached --stat -- $(git diff --name-only HEAD~0 2>/dev/null)  # 육안 대조
```

실측(2026-09-05): 위 두 블록을 임시 clone(`/tmp/1bc-rollback-rehearsal`)에서 그대로
실행 — `restore` 명령 exit 0(신규 경로가 섞이지 않아 `1A 16차 high` 가 잡은 「base 에
없는 경로에 대한 pathspec 오류」재현 없음), `rm -f` exit 0. 복원 뒤 `git status --short`
가 위 전건을 staged 변경/삭제로 보였고, `fixtures/manifest.yaml`·`rate-unit-00[1-5].json`
등의 내용이 base(`a5ea955`) 값(예: `rate-unit-001.json` 의 `outcome: Accepted` 류 옛
토큰)으로 돌아온 것을 `git show a5ea955:fixtures/expected/rate-unit-001.json` 과 diff 로
대조했다. 임시 clone 은 실측 뒤 삭제했다.

## 범위 밖 — rollback 이 되돌리지 않는 것

- `shared-kernel/src/main/**`(out_of_scope, 무접촉이라 되돌릴 것도 없다).
- `docs/discovery/data-dictionary.md`(in_scope 로 승격됐으나 이 slice 는 실제로 건드리지
  않았다 — `git diff a5ea955..HEAD` 에 없음).
- 하네스 레인 커밋(`b3d34cd` scope.md 갱신 등) — `scope.md` 「하네스 레인 변경」절이 별도로
  가른다. rollback 은 slice 산출물(in_scope 경로의 변경)만 대상이다.
