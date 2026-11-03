# M6 종결 판정 — slice 계약 (2026-11-02)

```yaml
milestone: M6
slice: closure-judgment
base_sha: 4d5d3d73   # origin/main (PR #115 = M7 종결 표시 이후)
head_sha: 리뷰 요청 시점의 `git rev-parse HEAD`(값을 박지 않는다)
in_scope:
  - reports/evidence/m6/closure/**   # 신설 — scope.md · checklist.md · open-inventory.md (M4·M5 종결 전례와 같은 셋)
  - milestone-6.md                   # 종결 문단 하나(완료 조건 판정 요약 · 6G 분리 상태 · 넘기는 것)
out_of_scope:
  - M6 코드·게이트·스키마 변경        # 판정이다. 미충족·좁혀짐이 나오면 후속으로 등재하고 고치지 않는다
  - 6G 종결(실수집 표본 · 백테스트 판정)  # 운영자 결정 2026-10-03 으로 M6 완료 조건과 분리 — 이 slice 는 현재 상태만 적는다
  - 6G 수집 cron · 수집 기록 브랜치(`m6-6g-collection/2026-10-02`) # host·다른 레인 소관. 상태만 기록
  - capability-map 문면                # 각 계열 소유 slice 로 넘긴다(M4 전례 ①)
acceptance_commands:
  - "./tools/one-command-check.sh"     # 조건 1 — 운영자 결정 2026-10-15 로 Kotlin·Python·web 전건. **버릴 clone** 에서 돈다
  - "./gradlew --no-daemon check"      # 조건 1·3·5·8 의 근거 — one-command 가 포함하면 그 실행으로 갈음(중복 실행 금지)
  - "git status --porcelain -- reports/evidence/m6/closure milestone-6.md"   # clean-tree 게이트(빈 출력 + 양성 대조 1회)
rollback: |
  in_scope 경로 한정. 목록은 `git diff --name-status 4d5d3d73..HEAD` 기계 산출.
  `git restore --source=4d5d3d73 --staged --worktree -- milestone-6.md` + `reports/evidence/m6/closure/` 삭제.
  공유 파일 없음. 코드 변경 0 이라 확인은 트리 동일성으로만.
```

작성: 2026-11-02, 세션 모델 단독. 착수 근거: 사용자 지시 2026-11-02 「추천 순서대로 진행해」(① M6 종결 판정).

## 왜 이 slice 가 있는가

M7 은 운영자 예외(각 M7 slice 착수 계약 · `milestone-7.md` 「선행 조건」)로 M6 종결을 기다리지 않고 열렸고, 2026-11-02
종결됐다(PR #114·#115). M6 의 완료 조건 9(verifier 최종 ready-for-review + 사용자 release 승인)를 판정한 기록은 아직 없다.
`milestone-6.md` 는 M6 종결의 선행을 「7C② 의 「아직 산출 안 함」 상태가 선 뒤」로 적었고, 그것은 7E-1(#102)로 섰다.

## 판정 규칙

- 조건 아홉을 **지금의 main 에서 다시 잰다** — 「당시 통과」를 근거로 쓰지 않는다(M4 전례). 문면 그대로 충족 · 좁혀짐(운영자
  결정으로 범위가 조정됨 — 결정 인용 필수) · 미측정 · 미충족 넷으로 가른다.
- 조건 3 의 판정 대상은 운영자 결정 2026-10-15 · 2026-10-24 이 정한 **성립 15 + G1 8**(COL-02 · COL-03 · DEC-06 · OPS-01 ·
  DEC-05 · DEC-02 · DEC-10 · SET-01; ML-01 은 6G 종속으로 분리). 각 G1 행은 닫은 PR·test 이름과 그 test 가 지금 main 에서
  도는지(gate-tests 등재 포함)로 판정한다. 선언 예외(ⓒ 유지 등)는 결정 문면을 인용한다.
- 조건 4(dry-run) · 6(재현) · 7(rehearsal) 은 M7/7F 의 운영 반입 리허설과 실반입 기록(`reports/evidence/m7/close/checklist.md`)을
  근거로 쓸 수 있다 — 단 M6 조건 문면과 어긋나는 부분은 좁혀짐으로 적는다.
- 6G 는 판정하지 않는다. 현재 상태(수집 cron 이 TOTAL 예산으로 day20 이후 매일 수집 0 · 기록 브랜치 미병합 · 백테스트 판정 없음)를
  open-inventory 에 적고 「6G 종결」을 넘긴다.

## 결정

- D-M6C-1 6G 분리 유지(운영자 결정 2026-10-03). M6 종결은 6G 를 기다리지 않는다.
- D-M6C-2 ML-01 은 6G 종속(운영자 결정 2026-10-24) — 조건 3 모수에서 빠지고 `OPEN-ML-ANALYSIS-WIRING` 로 넘긴다.
- D-M6C-3 미충족이 나오면 이 slice 에서 고치지 않고 후속 slice 로 등재해 사용자에게 보고한다(조건부 종결 여부는 사용자 결정).

## 하네스 레인 변경

없음.
