# M6/6D-1 — 실행 명령과 실측 (구현 레인)

base `fd4629fe` · 브랜치 `m6-6d/2026-10-05` · 마지막 산출물 커밋 `aa99e987`.

## acceptance — CI `check` job 명령 그대로

| 명령 | 결과 |
|---|---|
| `./gradlew --no-daemon :adapters:test --rerun check` | BUILD SUCCESSFUL (1m 40s) |
| `./gradlew --no-daemon qualityBaseline` | BUILD SUCCESSFUL |

`--rerun` 을 붙인다 — 붙이지 않으면 `:adapters:test` 가 FROM-CACHE 로 끝나 **실행 증거가 아니다**.
E2E 는 기본 `check` 안에서 돈다: 조건 애노테이션도 `Test.filter` 제외도 쓰지 않고, 새 test 클래스
다섯은 `config/quality/gate-tests.properties` 의 `gate.tests.adapters` 에 등재했으며 **제외는 0** 이다.

## 표적 실행 — `--tests 'bidvector.adapters.e2e.*'`

| test 클래스 | tests | skipped | failures | errors |
|---|---|---|---|---|
| `PipelineOneLineE2ETest` | 3 | 0 | 0 | 0 |
| `PipelineFailureInjectionE2ETest` | 4 | 0 | 0 | 0 |
| `PipelineContractRejectionE2ETest` | 3 | 0 | 0 | 0 |
| `PipelineLadderBoundaryE2ETest` | 1 | 0 | 0 | 0 |
| `PipelineReproducibilityE2ETest` | 2 | 0 | 0 | 0 |
| 합계 | 13 | 0 | 0 | 0 |

## 축별 변이 — 주입을 바꿔치우고 표적 test 재실행, 저장 바이트로 복원

변이는 **더하기가 아니라 바꿔치우기**다. 각 행은 적용 직후 `git diff --numstat` 으로 실제 적용을
확인했고, 복원 뒤 같은 명령이 빈 출력임을 확인했다(`git checkout --` 을 쓰지 않는다). 아래 열셋은
test 만 바꾸고, 마지막 둘은 production 을 바꾸므로 **버릴 clone 에서만** 돌렸다.

| 축 | 변이 | 판정 |
|---|---|---|
| ① relay 구간 | relay 호출 제거 | RED |
| ① 면허 gate | 막힌 공고의 요건을 보유 면허로 교체 | RED |
| ① production 조립 | 전략·후보 소스 배선을 위임 대역으로 교체 | RED |
| ① 사다리 임계 | 승격·검토 임계를 둘 다 0 으로(유효한 전략) | RED |
| ① 사다리 임계 | 낮은 match 주입 제거(두 후보가 같은 임베딩) | RED |
| ② 중복 공고 | 둘째 평가 제거 | RED |
| ② ML timeout | 지연을 0 으로 교체 | RED |
| ② DB conflict | 둘째 claim 을 첫 워커 종료 **뒤**로 옮김(완전 순차) | RED |
| ② DB conflict | 경합 대상 행을 만들지 않음 | RED |
| ② malformed contract | 정의 밖 필드를 붙이지 않은 골든으로 교체 | RED |
| ④ schema 거부 | 거부 골든을 성공 응답으로 교체 | RED |
| ④ rollback | 응답자가 선택자와 무관하게 LATEST 를 반환 | RED |
| ⑤ 재현 | 둘째 run 의 release 를 직전 release 로 교체 | RED |
| ② DB conflict (production, clone) | 전이 질의에서 `SKIP LOCKED` 제거 | RED — 둘째 claim 이 막혀 대기 반환값이 거짓이 된다 |
| ② ML timeout (production, clone) | 출하 예측 예산을 1시간으로 교체 | RED — 서버를 부르기 전 예산 고정점 단언에서 |

열다섯 전부 RED 다. **production 쪽 둘이 이 표의 핵심**이다 — 그 둘이 초록이면 해당 축은 자기
production 경로를 재지 않는다는 뜻이고, 그래서 두 축의 술어를 바꿨다.

## 그 밖의 게이트 실측

| 항목 | 명령 | 결과 |
|---|---|---|
| clean-tree | `git status --porcelain -- <in_scope 개별 인자>` | 빈 출력 |
| clean-tree 양성 대조 | 같은 경로에 비파괴 절삭(한 줄) 후 같은 명령 | `M` 한 줄 — 게이트가 잡는다. 저장 바이트로 복원 |
| 비밀값 스캔 | `grep -rniE -f config/quality/leak-patterns.txt <신규 경로들>` | exit 1(일치 0) |
| 범위 혼입 | `git diff --name-only fd4629fe..aa99e987` ∖ in_scope | 빈 출력 |
| production 불변 | `git diff --name-only fd4629fe..HEAD -- '*/src/main/*'` | 빈 출력 |

## 실행하지 않은 것

실 KONEPS·실 LLM·실 발송·실 ML 서버 호출 0. DB 는 Testcontainers 가 만드는 일회성 컨테이너뿐이고
기존 개발·legacy 컨테이너에는 어떤 명령도 보내지 않았다.
