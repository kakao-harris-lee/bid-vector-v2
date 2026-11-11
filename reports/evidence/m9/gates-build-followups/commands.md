# M9/gates-build-followups — 명령과 실측

실행 자리: worktree `bid-vector-v2-m9-gates-build-followups`(이 slice 전용) · 빌드는 `bv-build.sh` 경유
(호스트 동시 둘, 다른 후속 레인과 자리를 나눈다) · rollback 실측만 버릴 clone.

## 통제군 — 새 단언 둘이 돈다

```bash
./gradlew --no-daemon :build-logic:test --tests '*SourceReferencePolicyTest*' --rerun \
                      :adapters:test    --tests '*EvaluationAllowedRootsParityTest*' --rerun
```

exit 0 · 두 test task **실행 확인**(FROM-CACHE 아님) · XML: `SourceReferencePolicyTest` 25/0 ·
`EvaluationAllowedRootsParityTest` 1/0. 새 test case 이름이 XML 에 있다.

## 변이 — 바꿔치우기 다섯 + 선언 입력 음성 대조 하나

모두 커밋된 트리 위에서 변이 → 실행 → `git checkout --` 로 되돌림(미커밋 산출물 0 을 먼저 확인했다).
test task 는 `--rerun`(M1·M6 만 제외 — 그 둘은 **UP-TO-DATE 여부 자체가 측정 대상**이다).

| # | 변이(무엇을 무엇으로) | 결과 |
| --- | --- | --- |
| 통제 | 없음 | exit 0 · 위 통제군 |
| M1 | 이름 층 값의 포트 뿌리 → **어댑터 뿌리**(`--rerun` 없이) | **RED** exit 1 — 등식 test 와 이름 층 음성 단언 **둘 다**. `:adapters:test` 가 **UP-TO-DATE 가 아니었다**(선언 입력이 섰다) |
| M2 | 같은 값 → **접두 위장**(`…notificationinboxadapter`) | **RED** exit 1 — 둘 다(`뿌리.` 경계) |
| M3 | **바이트코드 층** 목록의 그 뿌리 → 형제 패키지(`workflow.notification`) | **RED** exit 1 — 등식(갈린 쪽이 반대여도 붉다) |
| M4 | 축을 이름 층 **판정 모집단**(`source.scoped-packages`)에서 제거 | **RED** exit 1 — 「Collection should contain element …evaluation」(공허해지는 길 ①) |
| M5 | 이름 층 목록을 **비움** | **RED** exit 1 — 「Set should not be empty」(공허해지는 길 ②) |
| M6-a | `inputs.file` 한 줄 제거 · 정책 원본 | exit 0(기준선 — task 실행됨) |
| M6-b | 그 상태에서 M1 변이 | **exit 0 · `:adapters:test` UP-TO-DATE** — **거짓 초록**. 선언 입력 한 줄이 막는 것이 이것이다 |

되돌림 뒤 `git status --porcelain` 은 이 slice 의 evidence 편집만 남았다(변이 잔존 0).

M3 이 보이는 것: 등식은 **방향이 없다**. 상류가 바이트코드 층만 넓히면 이름 층 게이트가 붉지만(여덟째
병합에서 실제로 그랬다) 반대 방향은 아무 게이트도 붉지 않는다 — M3 이 그 조용한 방향이고 이제 붉다.

## 여덟 축 대조 — 오늘 전부 일치, 지켜 주는 단언은 하나

범위 축 여덟의 이름 층 값과 각 축 dependency test 의 `ALLOWED_ROOTS` 를 항목까지 대조했다: 여덟 축 모두
**일치**(1·10·5·7·7·6·8·7). 각 test 의 선언이 **순수 리터럴 `setOf` 하나뿐**임을 전수 확인했으므로 이
대조는 정적 근사가 아니다(변수·이어붙이기 항목 0 · 파일마다 선언 1). 등식 단언이 있는 축은 `evaluation`
하나다 — 남은 일곱은 `OPEN-SCOPED-ROOTS-PARITY-REST`.

## 원 slice evidence 정정 셋의 실측

| 정정 | 실측 |
| --- | --- |
| 겹친 경로 **열둘** | `comm -12` 로 두 쪽 기여의 교집합 = **12**(merge-base `07140729`, `--no-renames`). 명령을 정정 인용에 적었다 |
| 전건 `check` 좌표 | `git diff --name-only 2f8b24b0..5f8ea75f` = **evidence 넷뿐** → 그 두 트리는 코드·정책·빌드가 같다. CI run `42949195162` → `headSha=5f8ea75f…` · `conclusion=success` · job **5/5 success**(실측) |
| 표 접힘 계수 **18** | 표 본문 줄의 반복 다중도(총 − 서로 다른 수)로 세 판 모두 **18**(branch·main·병합). 같은 수를 내는 다른 정의도 있어 **재구성임을 명시**했다. 같은 문장의 제목 160·중복 0·표 줄 591/584/593 도 재현 |

## acceptance — 전건 `check`

CI `check` job 의 명령 그대로. 실측 HEAD `bf5bc105`(이 slice 의 **마지막 산출물 커밋**):

```bash
./gradlew --no-daemon check
```

**exit 0 · BUILD SUCCESSFUL in 38m 37s · 433 actionable(263 executed · 129 from cache · 41 up-to-date) ·
FAILED 0.** `:adapters:test`·`:build-logic:test` 둘 다 **실행**(FROM-CACHE 아님)이고 새 등식 test 의 XML 이
그 실행 시각을 갖는다(1/0). `gateExecutionGate`·`gateRegistrationGate` 가 그 실행에서 돌았다 — 새 test
클래스의 **등재 등식은 전건에서만 붉는 축**이라 표적 실행이 갈음하지 못하는 자리다.

CPD 관찰 리포트에 새 파일은 **duplication 블록 없이 분석 파일 목록에만** 나타난다(그 축은 `main` source
set 만 실패로 세고 이 파일은 test 다).

## leak 스캔

`grep -rniE -f config/quality/leak-patterns.txt` 를 이 slice 의 evidence 디렉터리에 참조형으로 돌려
**매치 0**. 어휘는 적지 않는다(참조형으로만 호출).
