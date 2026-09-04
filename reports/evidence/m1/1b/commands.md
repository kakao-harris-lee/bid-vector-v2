# commands — M1 / 1B

실행 명령과 종료 코드. 출력 전문을 붙이지 않는다 — 핵심 결과는 한 줄이다(`evidence-pack`
SKILL). 라운드 이력 절과 자기 검사 하네스는 만들지 않는다 — 그 기록은 git log 와 리뷰
verdict 가 갖는다.

## C-0 — 리뷰 range (1A 정정 문면 승계)

Codex 리뷰 요청 시점의 전체 diff 는 아래 명령이 낸다. **range 자체가 slice 산출물의 정의는
아니다** — `scope.md` 의 「하네스 레인 변경」 절이 하네스 경로를 가른다(evidence-pack SKILL
2026-09-04).

```
git diff --stat 66c1ab79af4c5a68145811a9e87008dfdb10da3c..HEAD
```

---

## Phase 3 — acceptance (`scope.md`의 `acceptance_commands`)

### 2026-09-04T00:00:00Z (UTC 시각은 근사 — 라운드 순서만 의미가 있다)

- cmd: `./gradlew :shared-kernel:check --no-daemon --no-build-cache` (커밋별 반복 실행, 4회 —
  각 커밋이 독립적으로 이 명령을 통과함을 확인)
- exit: 0 (매 회)
- 핵심 결과: 게이트 전건(`domainApiTypeGate`·`domainSourceReferenceGate`·`packageOwnershipGate`·
  `jarContentGate`·`sizeGate`·ktlint·detekt·`sourceSetLayoutGate`·`gateExecutionGate`·kover)
  통과, test 31건 0 실패 0 skip

- cmd: `git worktree add --detach <dir> HEAD && (cd <dir> && ./gradlew --no-build-cache clean check)`  (B-0)
- exit: 0
- 핵심 결과: 격리 worktree(HEAD `2318328`)에서 전 모듈 `check` 통과, `app:compatibilitySmoke`
  포함 261 task 전건 실행(캐시 없음)

- cmd: `./gradlew --no-build-cache clean check --no-daemon`  (B-1, 작업 트리)
- exit: 0
- 핵심 결과: 전 모듈 통과. `app:test`(ArchitectureGate 포함)가 새 `shared-kernel` 도메인
  타입 위에서 위반 0을 낸다 — 1B가 처음으로 실제 도메인 코드 위에서 그 검증을 실행한다

- cmd: `./gradlew :app:test --tests '*ArchitectureGate*' --no-daemon`  (B-2)
- exit: 0
- 핵심 결과: 1A 승계 게이트 test 전건 통과(`--no-build-cache clean check`에 포함되어 별도
  독립 실행으로도 재확인)

- cmd: `./gradlew :build-logic:test --no-daemon`  (B-3)
- exit: 0
- 핵심 결과: `--no-build-cache clean check`에 포함되어 재확인됨(build-logic 판정 순수 함수
  test 전건 통과)

- cmd: `./gradlew :shared-kernel:test --no-daemon`  (B-4)
- exit: 0
- 핵심 결과: 31 test, 0 실패, 0 skip(`MoneyTest` 5·`RateTest` 6·`PolicyTest` 4·
  `ArithmeticTest` 11·`RegressionExampleTest` 5)

- cmd: `./gradlew qualityBaseline --no-daemon`  (B-5)
- exit: 0
- 핵심 결과: `--no-build-cache clean check`에 포함되어 재확인됨. `shared-kernel`의 실측값이
  `OPEN-ADR-06` 입력으로 처음 갱신됨(그린필드 바닥값이 아닌 실제 도메인 코드 위의 값) —
  결정 시점은 1B 종료(C12)

- cmd: `./gradlew :shared-kernel:domainApiTypeGate :shared-kernel:domainSourceReferenceGate --no-daemon --no-build-cache`  (B-6·B-7 단독 실행)
- exit: 0
- 핵심 결과: 1B가 실제 public API 표면(`Money`·`Rate`·`Measurement`·`Fact` 등) 위에서 이 둘을
  처음 실효시켰다 — 1A는 committed fixture 위에서만 쟀다(`reports/evidence/m1/1a/checklist.md`
  알려진 제한 43)

### secret 스캔

- cmd: `grep -rniE "(api[_-]?key|secret|token|password|Bearer |BEGIN (RSA|EC|OPENSSH))" reports/evidence/m1/1b/ shared-kernel/src/`
- exit: 0 — 매치는 이 절 자신(스캔 명령 문자열이 `secret`·`token`·`password`를 문자 그대로
  담는다)뿐이고, 코드·다른 evidence 문서에는 매치가 없다. **판독**: `codex-review-gate` SKILL의
  누출 검사 판독 규칙과 같은 부류 — developer가 인용한 패턴 문자열은 실제 비밀값이 아니다.
  육안 확인으로 실제 비밀값 부재를 재확인했다

### 양성 대조 — clean-tree 게이트가 실제로 in_scope 변경을 잡는지

- cmd: `echo "// probe" >> shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt && git status --porcelain -- shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt reports/evidence/m1/1b/scope.md reports/evidence/m1/1b/checklist.md reports/evidence/m1/1b/commands.md reports/evidence/m1/1b/rollback.md && git checkout -- shared-kernel/src/main/kotlin/bidvector/sharedkernel/Basis.kt`
- exit: 0
- 핵심 결과: 의도적 변경 1건이 잡혔고(`M shared-kernel/.../Basis.kt`), 원복 뒤 `git status`가
  다시 비었다 — clean-tree 판정이 공허하게 통과하는 것이 아님을 확인
