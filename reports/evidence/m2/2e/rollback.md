# M2/2E rollback.md

정본은 **in_scope 경로 한정 복원**이다(range revert 금지 — 2026-09-04 관례). 하네스 경로
(`CLAUDE.md`·`.claude/**`)는 대상이 아니다(이 slice에서 변경 없음, scope.md 「하네스 레인
변경」 절 — 착수 시 없음, 이후로도 없음).

**`milestone-2.md`·`reports/evidence/m2/2e/scope.md`는 되돌리지 않는다** — 2B 전례와 같은
경계(rollback은 기능 산출물을 대상으로 하고, 착수 계약·설계 문서는 「이 slice가 시도됐다」
기록으로 남긴다). base..HEAD 안에서 이 목록(밑 6개 편집 파일)은 **이 slice만 만졌다**
(`f94bddb`는 `milestone-2.md`·`scope.md`만 건드렸다 — 겹침 없음, 확인:
`git show --stat f94bddb`). 그래서 hunk 격리 없이 base 상태로 직접 복원해도 안전하다.

## 되돌리는 것

- 신규 파일(`--source=<base>`에 없음 → 복원 시 삭제):
  - `contracts/proto/bidvector/ml/v1/embedding.proto`
  - `contracts/testdata/embedding/**`(binpb 5 + json 5)
  - `adapters/src/test/kotlin/bidvector/adapters/contract/EmbeddingContractTest.kt`
  - `adapters/src/test/kotlin/bidvector/adapters/contract/EmbeddingTestdataCanonicalTest.kt`
    (verifier r1 F-1 수정 라운드 신설)
  - `ml-engine/tests/test_embedding_contract.py`
- 편집 파일(`--source=<base>`가 있음 → 4B-3 병합 상태로 복원):
  - `adapters/src/test/kotlin/bidvector/adapters/contract/MultiServiceContractTest.kt`
    (넷째 서비스 등록 제거 → 2B/2C 두 서비스 상태)
  - `config/quality/contract-policy.properties`(`embedding.*` 키 둘·`policy.version` 2→1)
  - `config/quality/gate-tests.properties`(`gate.tests.adapters`에서 `EmbeddingContractTest`
    행 제거)
  - `contracts/tools/breaking-mutations.sh`(`EMBEDDING` 변수·package-rename for-loop 추가분
    제거)
  - `docs/discovery/capability-map.md`(§14 OPEN-4D-LADDER-SCORE-SOURCE 분해 갱신·
    OPEN-2E-TEXT-SYNTHESIS·OPEN-2E-TEXT-MAX 두 행 제거)
  - `ml-engine/tests/crosslang_smoke_server.py`(`EmbeddingService` 등록 제거)

## 명령

```bash
BASE=8b50461016232386e456be532fab4eed4299fcfb
git restore --source="$BASE" --staged --worktree -- \
  contracts/proto/bidvector/ml/v1/embedding.proto \
  contracts/testdata/embedding \
  adapters/src/test/kotlin/bidvector/adapters/contract/EmbeddingContractTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/contract/EmbeddingTestdataCanonicalTest.kt \
  ml-engine/tests/test_embedding_contract.py \
  adapters/src/test/kotlin/bidvector/adapters/contract/MultiServiceContractTest.kt \
  config/quality/contract-policy.properties \
  config/quality/gate-tests.properties \
  contracts/tools/breaking-mutations.sh \
  docs/discovery/capability-map.md \
  ml-engine/tests/crosslang_smoke_server.py
```

`--source`에 없는 경로(신규 파일 4종, `contracts/testdata/embedding/` 디렉터리 통째)는
`git restore`가 작업 트리에서 삭제한다 — 별도 `git rm`이 필요 없다(2026-09-04 관례).

## 확인 지점

1. `git status --porcelain -- <위 11개 경로>`는 **빈 출력이 아니다**(staged 상태로 D/M이
   남는다, 2B rollback.md verifier r1 F-5와 같은 사실) — 복원 확인은 아래 2~5로 한다.
2. `git diff "$BASE" -- <편집 파일 6개>`가 빈 출력(작업 트리 내용이 base와 완전히 같음).
3. `(cd contracts && buf lint && buf build)` exit 0 — `embedding.proto` 없이도 계약 자족
   (5개 파일로 복귀, `EmbeddingService` 사라짐).
4. **되돌린 트리의 compile** — `./gradlew --offline :adapters:compileTestKotlin` exit 0
   (`EmbeddingContractTest.kt`·`MultiServiceContractTest.kt`의 embedding import가 남아
   컴파일이 깨지지 않는지 확인).
5. **되돌린 트리의 test** — `./gradlew --offline :adapters:test --tests
   '*MultiServiceContractTest*'` exit 0(2B/2C 두 서비스로 정상 축소).

## 예상 복구 시간

1분 미만(파일 삭제·`git restore` 한 번) + compile/test 확인 시 약 1~2분(캐시 활용 시).

## 실측(임시 clone)

`commands.md` 「rollback 실측(임시 clone)」 참고 — 위 명령을 `git clone --no-hardlinks`
임시 clone에서 그대로 실행해 exit 0·대상 경로 삭제/복원·확인 지점 2~5(diff 빈 출력·
buf lint/build·compileTestKotlin·MultiServiceContractTest)를 전부 확인했다.

**라운드마다 파일이 늘면 이 절차를 다시 돌린다** — 목록은
`git diff --name-status "$BASE"..HEAD`에서 기계적으로 낸다.
