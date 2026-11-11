# M9/gates-build-followups — rollback

**전제**: 이 slice 는 아직 main 에 **없다**. 머지 뒤의 되돌림은 이 문서가 아니라 그 PR 의 revert 다.

**복원 원천은 `6e6f953c`** — 착수 base 이자 이 브랜치가 담은 마지막 main 커밋이다(main 병합 **0** 회, 고정
기재 — 움직이는 `origin/main` 을 쓰지 않는다). 전제 둘을 단언했다: ① HEAD 의 **조상**이다
(`merge-base --is-ancestor` rc=0) ② 첫 산출물 커밋 `bafe2969` 가 **그 SHA 에 없다**(rc=1). ② 가 성립하고
range 에 병합이 없으므로 전체 복원이 남의 줄을 떨어뜨릴 수 없다 — hunk 격리가 필요 없다(목록의 여덟을
`6e6f953c..HEAD` 에서 만진 커밋은 전부 이 slice 의 산출물 커밋 셋이다).

**실측 HEAD: `bf5bc105`** — 이 slice 의 **마지막 산출물 커밋**이고 목록 밖인 자기 장부
(`reports/evidence/m9/gates-build-followups/**`)를 담기 **전**이다. 목록이 자기를 담은 커밋을 가리키지
않는다. 라운드가 늘면 이 자리에서 다시 잰다(앞 측정을 옮기지 않는다).

## 복원 명령 (in_scope 경로 한정 · 경로는 개별 인자)

```bash
git restore --source=6e6f953c --staged --worktree -- \
  adapters/build.gradle.kts \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAdapterDependencyTest.kt \
  adapters/src/test/kotlin/bidvector/adapters/evaluation/EvaluationAllowedRootsParityTest.kt \
  build-logic/src/test/kotlin/bidvector/buildlogic/SourceReferencePolicyTest.kt \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  reports/evidence/m9/gates-build/commands.md \
  reports/evidence/m9/gates-build/scope.md
```

**신규는 복원이 아니라 삭제다** — 목록의 신규 하나(등식 test)는 원천에 없어 이 명령이 지운다(실측
porcelain `D` 1). 장부 넷은 목록 밖이고 함께 지우려면 `git rm -r reports/evidence/m9/gates-build-followups`.

목록은 **기계 산출**이다: `git log --no-merges --format= --name-only HEAD ^6e6f953c | sort -u` 와
`git diff --name-only 6e6f953c..HEAD` 가 각각 **8**, `comm -3` **양방향 빈 출력**, `--name-status` **A 1 · M 7**.
실측 HEAD 에는 장부가 아직 없어 두 집합이 같다. **빈 등식이 건전성의 증거는 아니므로** 반대도 봤다 —
여덟은 전부 내가 편집해서 들어온 경로이고, 내가 커밋하지 않아도 들어오는 경로는 0 이다(이 range 에 다른
레인의 커밋 0). 예상 복구 시간: 명령 하나.

## 되돌리면 무엇이 꺼지는가

- **두 층 등식 단언이 사라진다** — 특히 이름 층만 넓어지는 **조용한 방향**에 아무 단언도 서지 않게 된다.
  등록부 한 줄도 함께 빠지므로 등재 게이트는 붉지 않다. 이름 층의 알림함 **어댑터 거부** 음성 단언도
  사라진다(바이트코드 층의 같은 단언은 base 에 있어 남는다).
- `:adapters:test` 의 정책 **선언 입력**이 빠진다(그 상태의 거짓 초록을 M6-b 가 실측했다). 되돌린 뒤에는
  읽을 정책 단언 자체가 없어 base 와 같은 상태다.
- 판 번호가 13 으로, 원 evidence 가 「열셋」·좌표 없는 전건 절·정의 없는 접힘 계수로 돌아간다.

**운영 영향 0** — 저장소 파일만 움직인다. production 소스 0 줄, 운영 host·DB·외부 API 무접촉.

## 실측 (버릴 clone `rb-clone`, @`bf5bc105`)

| # | 확인 | 결과 |
|---|---|---|
| — | 목록 등식 | 기계 산출 **8** · 문서 목록 **8** · `comm -3` **0 줄** · `A 1 · M 7` |
| ① | 복원 명령 | **rc=0** |
| ② | `git status --porcelain -- <8 경로>` | **`D` 1 · `M` 7** · 목록 밖 변화 **0**(전체 porcelain 도 같은 여덟 줄) |
| ③ | `git diff 6e6f953c -- <8 경로>` | **0 줄** |
| ④⑤⑥ | 컴파일·test·게이트 | **트리 동일성으로 갈음한다**(「HEAD 초록」이 아니다): 복원 뒤 `git write-tree` 가 **`6e6f953c^{tree}` 와 같다**(`2c906412…`). 되돌린 트리는 CI 가 통과한 main 트리 **그 자체**이므로(그 커밋의 push run `42955742413` **success**) 모든 게이트 입력이 base 와 바이트 동일하다 |
| 계수 | 내 표지 0 · 남의 표지 보존 | 내 표지 **일곱 전부 0**(판 14 · 등식 test 파일 · 그 등재 줄 · `internal` 선언 · 정책 선언 입력 · 이름 층 음성 단언 · evidence 정정 인용) · 남의 표지 **일곱 전부 보존**(판 13 · `private` 선언 · 상류 허용 뿌리 어휘 24 회 · 바이트코드 축 등재 줄 · 원 evidence 「열셋」 3 회 · 원 전건 절 수치 · adapters 의 기존 선언 입력 둘) |

**판정 SHA 에서의 차이 하나**: 그 커밋에는 장부가 있고 목록 밖이라 복원이 남긴다 — 그래서 트리가 base 와
**장부 디렉터리 하나만큼** 다르다. 그것을 입력으로 삼는 게이트는 `leakPatternGate` 하나이고 그 술어를
참조형으로 돌려 **매치 0** 을 쟀다(`grep -rniE -f config/quality/leak-patterns.txt` — 어휘는 적지 않는다).
baseline 파일은 이 slice 가 만지지 않았으므로(커밋 0) 복원이 baseline 만 걷어 붉어지는 모양도 없다.

## 유효성 확인 (감사자용)

`git diff --name-only bf5bc105..<판정 SHA> -- <위 복원 목록 8 경로>` 가 **빈 출력**이면 실측 뒤 되돌림
대상이 움직이지 않았다는 뜻이고 이 실측이 유효하다. 이 slice 는 실측 뒤 **자기 장부만** 커밋했고 장부는
목록 밖이므로 그 출력은 비어 있다 — CLAUDE.md 요약의 **좁은 술어**를 그대로 만족시킨다(원 slice 의 전칭
확장에 기대지 않는다, `OPEN-ROLLBACK-PREDICATE-WORDING`).
