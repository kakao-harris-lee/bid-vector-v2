# rollback — M1 / 1B-c

정본. `scope.md` 의 `rollback` 절이 가리키는 자리(evidence-pack 규격, 1B 관례 승계).
경로 한정 복원 + 신규 경로 삭제. **range revert 금지** — in_scope 경로에만 적용한다.

base_sha: `a5ea955c44a935c3245288e6a72c830eeeee64b8`
head_sha(이 문서 정본 시점): `ae0d9ad`

## 변경 파일 전건 (`git diff --name-status a5ea955c..HEAD`, in_scope 경로만)

`M` = base 에도 있던 파일(내용만 복원), `A` = 이 slice 가 신설(파일째 삭제).

```
M  app/build.gradle.kts
A  app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt
A  app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt
M  config/quality/gate-tests.properties
M  docs/adr/0007-test-pyramid-and-ratchet.md
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
M  fixtures/input/money-basis-006.json
M  fixtures/manifest.yaml
M  fixtures/tools/manifest_contract.py
M  fixtures/tools/mutation_sweep_adversarial.py
M  fixtures/tools/mutation_sweep_targeted.py
M  gradle/libs.versions.toml
M  milestone-1.md
A  reports/evidence/m1/1b-c/checklist.md
A  reports/evidence/m1/1b-c/commands.md
A  reports/evidence/m1/1b-c/fixtures.md
A  reports/evidence/m1/1b-c/golden-manifest.json
A  reports/evidence/m1/1b-c/rollback.md
A  reports/evidence/m1/1b-c/scope.md
M  shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt
A  shared-kernel/src/test/resources/compile-fixtures/mutant-12-rate-undeclared-unit-typo.kt.txt
A  shared-kernel/src/test/resources/compile-fixtures/negative-12-rate-undeclared-unit.kt.txt
A  shared-kernel/src/test/resources/compile-fixtures/positive-12-rate-declared-unit.kt.txt
```

`shared-kernel/build.gradle.kts` 와 `shared-kernel/src/testFixtures/**` 는 D5(d) 전환으로
**이미 무접촉** — `git diff HEAD -- shared-kernel/build.gradle.kts` 가 빈 것을 커밋 전
확인했다(`commands.md` 참고). 롤백 목록에 없다. `app/src/test/.../SharedKernelCorpusConformanceTest.kt`
는 verifier r1 이후 `CorpusExecutors.kt` 와 둘로 갈렸다(크기 한도, 500줄) — 둘 다 신규(`A`)다.

## 복원 명령 (경로 개별 인자 — 변수 확장 금지, 1A 교훈)

```bash
BASE=a5ea955c44a935c3245288e6a72c830eeeee64b8

# M(base 에도 있던 파일) — 내용 복원
git restore --source="$BASE" --staged --worktree -- \
  "app/build.gradle.kts" \
  "config/quality/gate-tests.properties" \
  "docs/adr/0007-test-pyramid-and-ratchet.md" \
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
  "fixtures/input/money-basis-006.json" \
  "fixtures/manifest.yaml" \
  "fixtures/tools/manifest_contract.py" \
  "fixtures/tools/mutation_sweep_adversarial.py" \
  "fixtures/tools/mutation_sweep_targeted.py" \
  "gradle/libs.versions.toml" \
  "milestone-1.md" \
  "shared-kernel/src/test/kotlin/bidvector/sharedkernel/CompileFailureHarnessTest.kt"

# A(이 slice 가 신설) — 삭제
git rm -f -- \
  "app/src/test/kotlin/bidvector/app/conformance/CorpusExecutors.kt" \
  "app/src/test/kotlin/bidvector/app/conformance/SharedKernelCorpusConformanceTest.kt" \
  "reports/evidence/m1/1b-c/checklist.md" \
  "reports/evidence/m1/1b-c/commands.md" \
  "reports/evidence/m1/1b-c/fixtures.md" \
  "reports/evidence/m1/1b-c/golden-manifest.json" \
  "reports/evidence/m1/1b-c/rollback.md" \
  "reports/evidence/m1/1b-c/scope.md" \
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
git diff --cached "$BASE" -- <복원 대상 전체>   # 빈 출력이어야 base 와 바이트 단위 일치
```

**실측(2026-09-05, HEAD `ae0d9ad` — verifier r1 수정 라운드 반영 최종본)**: 위 두 블록을
임시 clone(`/tmp/1bc-rollback-rehearsal`, `--no-local`)에서 그대로 실행 — `restore`
명령 exit 0, `git rm -f` 도 exit 0(대상 11개 전부 실제 삭제 로그 확인, `docs/adr/0007`
포함해 신규 경로가 base 에 없어 나는 pathspec 오류 재현 없음). `git status --short` 가
위 전건을 정확히 `M`(복원 24)/`D`(삭제 11)로 보였고, `git diff --cached a5ea955 --
<복원 대상 24 개>` 가 **빈 출력**이었다 — 복원된 내용이 base 와 바이트 단위로 같음을
그렇게 확인했다. 임시 clone 은 실측 뒤 삭제했다.

**앞선 리허설(`2049fd9` 기준, verifier r1 이전)과 달라진 자리**: `docs/adr/0007-test-pyramid-and-ratchet.md`
이 M-4 사후 승격으로 복원 목록에 새로 들었다(아래 「되돌리지 않는 것」 참고 — 이 파일은
**복원하되 완전히 되돌리지는 않는다**, 그 절 참고). `SharedKernelCorpusConformanceTest.kt`
하나였던 신규 경로가 verifier r1 이후 `CorpusExecutors.kt` 와 둘로 갈렸다.

## 범위 밖 — rollback 이 되돌리지 않는 것

- `shared-kernel/src/main/**`(out_of_scope, 무접촉이라 되돌릴 것도 없다).
- `docs/discovery/data-dictionary.md`(in_scope 로 승격됐으나 이 slice 는 실제로 건드리지
  않았다 — `git diff a5ea955..HEAD` 에 없음).
- **`docs/adr/0007-test-pyramid-and-ratchet.md`·`docs/discovery/capability-map.md` 의
  `OPEN-ADR-06` 행(verifier r1 M-4)** — 세션 모델이 운영자 결정 2026-09-05 를 등재한
  계약 갱신 커밋(`8b393ce`)이다. 이 slice 의 산출물이 아니라 **운영자 결정의 정본
  기록**이라 위 복원 명령이 `docs/adr/0007` 을 base 로 되돌리더라도(파일 자체는 M-4 로
  in_scope 승격됐으므로 명령에 들어 있다) **그 결정 자체를 무효화하는 것은 아니다** —
  운영자가 그 결정을 되돌리려면 별도 명시 결정이 필요하고, 이 rollback 은 slice ④
  (conformance runner)를 되돌리는 것이 목적이지 그 결정의 철회가 아니다. `capability-map.md`
  는 `OPEN-1B-CONTRACT`·`OPEN-1B-CORPUS`·`OPEN-ADR-06` 세 행을 함께 담고 있어 파일
  단위 복원이 셋을 함께 되돌린다 — **행 단위 선택 복원은 지원하지 않는다**(git 파일
  단위 도구의 한계, 알려진 제한).
- 하네스 레인 커밋(`b3d34cd`·`977083b`·`8b393ce`·`94009f4` 등 scope.md 갱신) —
  `scope.md` 「하네스 레인 변경」절이 별도로 가른다. rollback 은 slice 산출물(in_scope
  경로의 변경)만 대상이다.
- **레인 혼입 커밋 `51f820c`** — corpus 레인의 evidence 편집(fixtures.md·golden-manifest.json·
  commands.md)이 ④ 레인 커밋에 실렸던 사고(`fixtures.md` §2 「레인 혼입 선언」, `38e0640` 이
  선언). 이력을 되쓰지 않으므로 rollback 도 그 커밋을 특정해 되돌리지 않는다 — 위 파일
  단위 복원이 결과적으로 같은 효과를 낸다.
