# M6/6G Kotlin 수집 레인 — rollback

- base `678c6ed7` · **실측 HEAD `d9e0e985`**(이 레인의 마지막 산출물 커밋 시점의 트리)
- 되돌림은 range revert 가 아니라 **in_scope 경로 한정 복원**이다.

## 되돌리는 경로 (기계 산출 — `git diff --name-status 678c6ed7..d9e0e985 -- <아래 경로들>`)

신규 **57** · 수정 **25**. 경로 목록은 아래 디렉터리 인자가 덮는다 — 파일 이름을 여기 옮겨 적지 않는다(라운드마다 낡는다).

## 절차

```
git restore --source=678c6ed7 --staged --worktree -- \
  adapters/src/main/kotlin/bidvector/adapters/koneps \
  adapters/src/main/kotlin/bidvector/adapters/persistence \
  adapters/src/main/kotlin/bidvector/adapters/snapshot \
  adapters/src/test/kotlin/bidvector/adapters/koneps \
  adapters/src/test/kotlin/bidvector/adapters/persistence \
  adapters/src/test/kotlin/bidvector/adapters/snapshot \
  app/src/main/kotlin/bidvector/app/collection \
  app/src/main/kotlin/bidvector/app/wiring \
  app/src/test/kotlin/bidvector/app/collection \
  app/src/test/kotlin/bidvector/app/wiring \
  app/src/test/kotlin/bidvector/app/architecture \
  procurement/src/main/kotlin/bidvector/procurement \
  procurement/src/test/kotlin/bidvector/procurement \
  workflow/src/main/kotlin/bidvector/workflow/collection \
  workflow/src/test/kotlin/bidvector/workflow/collection \
  config/quality/architecture-policy.properties \
  config/quality/gate-tests.properties \
  ml-engine/tests/evaluation/fixtures/m6-6g-golden
```

**evidence 는 이 술어에서 뺀다**(D-6G-34, verifier L-2) — `reports/evidence/m6/6g/` 는 되돌림 대상이
아니다. 그것을 목록에 넣으면 「실측 HEAD 이후 되돌림 대상이 움직였는가」가 evidence 를 쓸 때마다
붉어져, 정작 산출물이 움직인 것을 가린다. 산출물 경로만 센다.

경로는 개별 인자다(`-A`·`.` 아님). 디렉터리를 주는 이유는 신규 파일의 **삭제**까지 복원이 지게
하기 위해서다 — 파일 하나씩 주면 base 에 없던 파일이 `pathspec` 불일치로 남는다.

### 공유 파일 — 다른 레인이 뒤에 손대면 위 절차를 쓰지 않는다

공유 파일은 셋이다: `config/quality/architecture-policy.properties` · `config/quality/gate-tests.properties` ·
**`milestone-6.md`**(착수 문단, D-6G-34 등재).

`config/quality/architecture-policy.properties`·`gate-tests.properties` 는 slice 를 가로지르는 공유
파일이다. 이 레인의 범위 안에서는 내 커밋들만 이 둘을 만졌으므로 위 경로 복원이 정확하다.
**그 뒤 다른 커밋이 같은 파일을 만졌다면** 경로 복원이 남의 줄까지 되돌린다 — 그때는 커밋 해시 hunk
격리를 쓴다:

```
git diff <그 커밋>~1..<그 커밋> -- config/quality/architecture-policy.properties | git apply -R
```

`--3way` 도 자동 해소에 실패할 수 있다 — 실패하면 수동으로 세 자리(수집 use case 의 procurement 참조
집합 한 줄, 대분류 타입 멤버 접근 쌍 두 줄, 그 앞의 설명 문단)를 지운다. 확인은 **둘 다**다:
「내 줄이 사라졌는가」와 「남의 줄이 남았는가」.

`gate-tests.properties` 는 등재 줄들(workflow · adapters · app)이다 — workflow 쪽은
`WorkflowGateRegistrationTest` 가 양방향 등재를 요구하므로 test 파일 삭제와 **짝으로만** 성립한다.
adapters 쪽(koneps·snapshot·persistence)은 그 패키지에 등재 test 가 **없어** 짝 제약이 없다(아래 알려진 제한).

`milestone-6.md` 는 위 복원 목록에 **없다** — 다른 slice 의 문단이 같은 파일에 있어 경로 복원이 남의
줄까지 되돌린다. 이 레인이 그 파일에 더한 것은 착수 문단 하나(D-6G-34 등재)이고, 되돌림은 그것을 담은
커밋의 hunk 격리다:

```
git diff <착수 문단 커밋>~1..<착수 문단 커밋> -- milestone-6.md | git apply -R
```

`--3way` 가 자동 해소에 실패하면 수동으로 그 문단(6G 착수 줄과 그 아래 in_scope 요약)만 지운다.
확인은 **둘 다**다: 내 문단이 사라졌는가, 다른 slice 의 문단이 남았는가.

## 실측 (임시 clone, 저장소 밖)

`d9e0e985` 를 checkout 한 clone 에서 위 절차를 그대로 실행했다.

| 축 | 결과 |
|---|---|
| ① 명령 exit | 0 |
| ② D/M 수 | 삭제 **57** · 수정 **25** — 위 기계 산출과 **같다** |
| ③ diff 빈 것 | `git diff 678c6ed7 -- <경로들>` **0 줄** — 되돌린 트리가 base 와 바이트 동일(갈음은 「HEAD 초록」이 아니라 이 트리 동일성이다) |
| ④ 컴파일 | 통과(`check` 안) |
| ⑤ test | 통과(`check` 안) |
| ⑥ 게이트 | 통과 — `./gradlew --no-daemon check` BUILD SUCCESSFUL |

## 두 레인의 실측 HEAD 가 다르다

이 절의 `d9e0e985` 는 **이 레인의 경로 집합**(koneps·snapshot·procurement·workflow/collection·
app/collection·app/wiring·config/quality)을 되돌려 잰 값이다. Python 레인은 자기 경로 집합(`ml-engine/**`)을 자기 HEAD 에서
따로 잰다(아래 그 레인의 절). 두 집합은 겹치지 않으므로 한 값으로 합치지 않는다 — 합치면 어느 경로가
어느 시점에 검증됐는지가 사라진다.

## 실측 HEAD 이후 되돌림 대상이 움직였는가

`git diff --name-only <실측 HEAD>..<판정 SHA> -- <위 경로들>` 이 빈 출력이어야 이 실측이 유효하다.
evidence 커밋은 언제나 뒤에 오므로 「실측 HEAD == 판정 SHA」를 요구하지 않는다 — 보는 것은
**그 사이에 되돌림 대상이 움직였는가**다.

**앞 라운드 이 절은 「evidence 경로가 위 목록에 있다」고 적었다 — 거짓이었다.** 위 복원 목록에
`reports/` 는 없고, 바로 앞 절이 그것을 뺀 이유를 적고 있다. 두 문장이 서로를 부정했다. 참인 쪽은
**뺐다**이고, 그래서 evidence 를 더 쓰는 것만으로는 이 실측이 무효가 되지 않는다 — 산출물 경로가
움직였을 때만 다시 돈다. 그 성질이 evidence 를 목록에서 뺀 이유 그 자체다.

## 알려진 제한

`bidvector.adapters.koneps`·`bidvector.adapters.snapshot` 패키지에는 **등재 test 가 없다**(audit·event·
evaluation 등 다른 adapters 패키지에는 있다). 그래서 이 두 패키지의 test 는 `gate-tests.properties` 에
빠져 있어도 게이트가 잡지 못한다 — 이 slice 가 만든 셋은 등재했고, 기존 `KonepsOpeningCompleteSourceTest`
가 미등재인 것을 그 과정에서 발견했다(이 slice 가 만든 것이 아니라 **기존 사각**이다). 후속 후보.

## 데이터

이 레인은 DB 에 쓰지 않았고 실 KONEPS 호출을 하지 않았다 — 되돌릴 데이터가 없다.

---

# Python 레인 (ml-engine) — rollback

> 이 절은 Python 레인의 것이다. 위 절은 Kotlin 레인이 쓴다. 두 레인의 in_scope 경로는 겹치지 않는다
> (`ml-engine/**` + `ml-engine/policy/**` ↔ `adapters/**`·`app/**`·`workflow/**`·`procurement/**`).

## 되돌리는 경로 (기계 산출 — `git diff --name-status <base>..HEAD -- ml-engine`)

추가 41 · 수정 4. 수정 넷만 적는다(추가는 되돌리면 사라진다):

- `ml-engine/src/ml_engine/evaluation/policy.py` — 5C-2 의 평탄 인덱스 판독기 여섯을 public 이름으로
  올린 것(재사용). 되돌리면 그 여섯이 다시 비공개가 되고 backtest 정책 로더가 사라지므로 정합하다.
- `ml-engine/tests/evaluation/test_evaluation_no_stray_numeric_literals.py` — 숫자 리터럴 게이트의
  하위 패키지 보강, 문자열 숫자 술어, 조립 근 뿌리, 허용 목록. 되돌리면 게이트가 직계만 보고
  문자열에 숨긴 수를 놓치던 상태로 돌아간다.
- `ml-engine/src/ml_engine/evaluation/verdict.py` — 공유 사유 어휘에 `NO_BASELINE_WIN` 한 줄.
  되돌리면 6G 판정이 그 사유를 낼 수 없고 5C-2 의 GBM 게이트는 영향이 없다(그 게이트는 이
  사유를 내지 않는다).
- `ml-engine/src/ml_engine/adapters/__init__.py` — 스냅숏 파일 판독기 재수출 네 줄.

## 절차

range revert 가 아니라 **in_scope 경로 한정 복원**이다:

```
git restore --source=<base> --staged --worktree -- \
  ml-engine/src/ml_engine/evaluation/backtest \
  ml-engine/src/ml_engine/evaluation/policy.py \
  ml-engine/src/ml_engine/evaluation/verdict.py \
  ml-engine/src/ml_engine/adapters \
  ml-engine/src/ml_engine/app/backtest_distribution.py \
  ml-engine/src/ml_engine/app/backtest_job.py \
  ml-engine/policy/strategy-backtest-v1.yaml \
  ml-engine/tests/evaluation ml-engine/tests/app/test_backtest_job.py \
  ml-engine/tests/app/test_backtest_golden.py
```

`ml-engine/src/ml_engine/evaluation/backtest/` 와 `tests/evaluation/fixtures/backtest-snapshot/` 은
이 slice 가 만든 디렉터리라 복원 뒤 비게 된다 — 빈 디렉터리는 git 이 추적하지 않으므로 별도 처리가
필요 없다.

## 공유 파일

`reports/evidence/m6/6g/commands.md` 와 `rollback.md` 는 **두 레인이 함께 쓴다**. 되돌릴 때 range
revert 를 쓰면 Kotlin 레인의 줄까지 사라진다 — 커밋 해시 단위로 hunk 를 격리한다:

```
git diff <이 레인의 evidence 커밋>~1..<그 커밋> -- reports/evidence/m6/6g/commands.md | git apply -R
```

`--3way` 는 같은 파일 끝에 두 레인이 덧붙인 구조에서 자동 해소에 실패하므로 기대하지 않는다.
확인은 **둘 다** 본다: 「내 줄이 사라졌는가」와 「남의 줄이 남았는가」.

## 실측

**실측 HEAD: `a39997c8`**(이 레인의 마지막 산출물 커밋 — evidence 커밋 앞).

임시 clone 에서 위 복원을 돌리고 ①~⑥ 을 쟀다. **Gradle 축(④⑤⑥ 중 Kotlin `check`)은 이 레인이
돌리지 않는다**(호스트 무거운 빌드 1개 규율) — Python 축으로 같은 여섯을 쟀다.

| 축 | 결과 |
|---|---|
| ① 명령 exit | 0 |
| ② A/M 수 | 추가 41 · 수정 4 — 위 기계 산출과 같다 |
| ③ diff 빈 것 | `git diff 678c6ed7 -- ml-engine` **0 줄** — 되돌린 트리가 base 와 바이트 동일 |
| ④ 형 검사 | `uv run mypy --strict src/ml_engine` — 소스 72개, 오류 0(base 상태) |
| ⑤ test | `uv run python -m pytest tests -q` — **958 passed**(base 상태, 6G 추가분 없음) |
| ⑥ 게이트 | `ruff` · `lint-imports` · `design_ratchet` 셋 다 통과(base 상태) |

갈음은 「HEAD 초록」이 아니라 **트리 동일성**(③)이다.

## 실측 HEAD 이후 되돌림 대상이 움직였는가

`git diff --name-only a39997c8..<판정 SHA> -- <위 경로들>` 이 빈 출력이어야 이 실측이 유효하다.
이 레인의 evidence 커밋은 `reports/evidence/m6/6g/` 안이고 그 경로는 위 목록에 **없다** — 그러므로
evidence 를 더 써도 이 실측은 유효하다.

## 왕복 golden 은 **두 레인의 공동 산출물**이다

`ml-engine/tests/evaluation/fixtures/m6-6g-golden/` 은 생산 레인이 쓰고 이 레인이 읽는다. 위 복원 경로에
`ml-engine/tests/evaluation` 이 들어 있어 **이 명령이 그 디렉터리도 지운다** — 6G 산출물이므로 지워지는
것이 맞고(base 에 없던 것이다), 실측에서 `fixtures/` 아래에 `backtest-snapshot` 도 `m6-6g-golden` 도 남지
않음을 확인했다. 어느 레인의 명령이 먼저 돌든 같은 base 에 닿는다(D-6G-34 의 「종결 전 두 레인 rollback 을
한 판으로 합친다」가 이 중복을 정리한다).

## 데이터

이 레인은 DB 에 쓰지 않았고 실 KONEPS 호출도 실 데이터 실행도 하지 않았다 — 되돌릴 데이터가 없다.
스냅숏 fixture 는 합성이고 저장소 안(`ml-engine/tests/evaluation/fixtures/`)에 있다.
