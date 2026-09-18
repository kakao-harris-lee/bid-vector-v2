# M4 종결 판정 — slice 계약 (2026-09-18)

```yaml
milestone: M4
slice: closure-judgment
base_sha: 8652893   # origin/main (PR #33 = 6B-1 병합 이후)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - reports/evidence/m4/closure/**          # 신설 — scope.md · checklist.md · open-inventory.md (M5 종결 전례와 같은 셋)
  - milestone-4.md                          # 종결 문단(완료 조건 판정 요약 + 알려진 제한의 현재 상태)
  # docs/discovery/capability-map.md 는 in_scope 에서 **제거**됐다 — 운영자 결정 2026-09-18 ①(최소 종결)이
  # 문면 정정을 각 계열 소유 slice 로 넘겼다. 이 slice 는 그 파일을 편집하지 않는다(실제 diff 도 0).
out_of_scope:
  - M4 코드 변경                            # 이것은 **판정**이고 구현이 아니다. 미충족·좁혀짐이 나오면 후속 slice 로 등재하고 이 slice 가 고치지 않는다
  - M6 문서·slice                           # 6F·6A 계열은 다른 레인 소관(milestone-6.md 무편집)
  - 6F·6A 계열 OPEN 행                       # 그 레인이 각 slice scope.md 와 milestone-6.md 에 등재한다
  - push · PR · 병합                        # 사용자 승인 사항
acceptance_commands:
  - "./gradlew --no-daemon check"           # C-1 — 전건. 완료 조건 ①②③④⑦ 의 근거가 여기서 나온다
  - "./gradlew --no-daemon :workflow:test --tests '*EvaluateCandidatesUseCaseIsolationTest*' --tests '*EvaluateCandidatesUseCaseTest*' --rerun-tasks"   # C-2 — 조건 ②(fake port, DB·network 없음)
  - "./gradlew --no-daemon :workflow:test --tests '*InboxDedupPropertyTest*' --tests '*OutboxTransitionTableTest*' --tests '*EditSessionIdempotencyPropertyTest*' --tests '*EditSessionTransitionTableTest*' --rerun-tasks"   # C-3 — 조건 ①③
  - "./gradlew --no-daemon :adapters:test --tests '*DeadlineCancellationRetryTest*' --tests '*EmbeddingDeadlineCancellationRetryTest*' --rerun-tasks"   # C-4 — 조건 ④(ML deadline·취소·재시도)
  - "git status --porcelain -- reports/evidence/m4/closure milestone-4.md"   # C-5 — clean-tree 게이트(빈 출력 + 양성 대조 1회). capability-map 경로는 **제거**했다(운영자 결정 ① 로 범위 밖 — verifier r2 LOW)
rollback: |
  in_scope 경로 한정 복원이다(range revert 아님). 목록은 라운드마다
  `git diff --name-status 8652893..HEAD` 로 **기계 산출**하고, 현재 값은 `M milestone-4.md` 와
  `A reports/evidence/m4/closure/{scope,checklist,open-inventory}.md` 넷이다.
  `git restore --source=8652893 --staged --worktree -- milestone-4.md`
  + `reports/evidence/m4/closure/` 삭제(신설 디렉터리).
  **공유 파일이 없다** — `capability-map.md` 는 운영자 결정 ①(최소 종결)로 in_scope 에서 빠졌고
  실제 diff 도 0 이다(verifier r1 LOW: 초판 rollback 이 diff 에 없는 파일을 가리켰다). 그래서
  「남의 줄 남음」 대조는 이 slice 에 해당 항목이 없고, 되돌림 확인은 **트리 동일성**으로만 한다.
  코드 변경이 없으므로 컴파일·test 재실행은 갈음 대상이 아니다.
```

작성: 2026-09-18, 세션 모델 단독(CLAUDE.md 「목표·마일스톤·로드맵·스팩은 세션 모델 하나가 단독으로
쓴다」). 착수 근거: 사용자 지시 2026-09-18 「다른 세션과 병렬로 작업 가능한 M6·M7 작업 확인해서 진행」.

## 왜 이 slice 가 있는가

`reports/evidence/m4/` 에 slice evidence **17건**이 있고 전부 사용자 승인 기록을 갖는데
**`closure/` 만 없다.** M5 는 같은 자리에 `scope.md`·`checklist.md`·`open-inventory.md` 셋을
갖는다. 즉 M4 는 slice 는 닫혔고 **마일스톤 판정만 빠져 있다.**

**M7 은 존재하지 않는다** — 저장소에 `milestone-0` ~ `milestone-6` 만 있다. 사용자 지시의 「M7」은
정의된 범위가 아니므로 이 slice 가 만들지 않는다(로드맵 신설은 운영자 목표 입력이 선행).

## 판정 기준 — 세 축

1. **완료 조건 7 대조** — `milestone-4.md` 「완료 조건」의 일곱 항목 각각에 대해 **무엇이 그것을
   재는가**를 지목하고, **base 시점에 지금도 서는지**를 실측한다. 「M4 당시 통과했다」는 근거가
   아니다 — 그 뒤 M5·M6 가 트리를 크게 움직였다.
2. **OPEN 배타 처분** — M4 계열 `OPEN-*` 전수를 `종결 / 이월 / 미결 / 상충` 네 갈래로 **배타**
   분류한다. 갈래별 개수가 전수와 같아야 한다.
3. **알려진 제한 일곱의 현재 상태** — M4 가 종결 시점에 선언한 제한 일곱 중 **그 뒤 닫힌 것**과
   **아직 열린 것**을 가른다. 이 축은 M5·M6 가 M4 의 제한을 실제로 닫았는지를 판정한다.

## 판정 어휘

M5 종결이 쓴 셋을 그대로 쓴다 — **충족**(그것을 재는 게이트가 있고 초록) · **좁혀진**(부분만
재고 나머지는 구조적으로 못 잰다, 사유 명시) · **미측정**(재는 것이 없다). **「통과했다」를
게이트 없이 쓰지 않는다.**

## 병행 레인 — 겹침

이 slice 는 M6 두 레인(6F-1·6A-1, 다른 세션)과 **병렬로 산다.** 겹침을 착수 시 실측했다.

| 경로 | 겹침 |
| --- | --- |
| `milestone-4.md` | 6F-1·6A-1 in_scope 에 **없음**(실측) |
| `reports/evidence/m4/closure/**` | 신설 — 겹칠 것이 없다 |
| `docs/discovery/capability-map.md` | **해당 없음** — 병행 레인이 겹침 0 을 실측해 회신했으나(만질 계획 없음, 생기면 먼저 알린다고 확인), 운영자 결정 ①(최소 종결)로 **이 slice 가 편집하지 않는다** |

`_workspace/m4-closure/` 는 gitignore 라 커밋되지 않는다.

## 하네스 레인 변경

상시 절이다. 리뷰 요청 시점마다 `git log --oneline 8652893..HEAD -- CLAUDE.md .claude/ docs/harness/`
로 재산출해 등재한다.

| 재산출 시점 | 값 |
| --- | --- |
| 착수 | 없음 |
| verifier r1 요청 전 | 없음 |
| verifier r2 요청 전(정정 커밋 둘 뒤) | **없음** — 명령 재실행 결과 0건 |
| 승인 전 일괄 뒤 | **없음** — 이 절을 고치는 커밋 자신이 `reports/evidence/` 안이므로 값이 바뀌지 않는다 |

**verifier r2 LOW 처분**: r2 요청 때 「재산출했다」고 보고했으나 **절의 문면이 r1 과 바이트
동일**이었다(값은 0 이 맞았다). 재산출은 명령 실행이고 등재는 문면이다 — 둘을 같은 것으로 적은
것이 결함이므로 이 표로 바꿔 **시점별 값**을 남긴다.

## 리뷰 레인

**코드 변경이 0 이므로 `code-reviewer` 는 적용 대상이 아니다**(M5 종결이 같은 이유로 생략했다 —
diff 에 코드가 없다). `verifier` 는 **적용한다** — 판정 문서의 주장이 실측과 맞는지, 배타 분류가
전수와 같은지, 「충족」이 게이트 없이 쓰이지 않았는지가 검증 대상이다. 게이트 셋
(`privacy-gate`·`contract-keeper`·`migration-reviewer`)은 **해당 없음**(인증·계약 파일·마이그레이션
무편집). Codex 는 기획 문서 계획 검토 대상이 될 수 있으나 **유료 외부 호출이므로 운영자가 범위·비용을
승인할 때만** 건다 — 이 slice 가 자동으로 걸지 않는다.
