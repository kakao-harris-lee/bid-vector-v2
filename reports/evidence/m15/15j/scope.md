# M15 / 15J — 예외 칸 모집단 하한을 구별되는 이름 수로

```yaml
milestone: M15
slice: 15j
base_sha: 65ea475e0af3965b85706746c101724763c75d94  # main, PR #197 머지 커밋
head_sha: <리뷰 요청 시점에 기입>
in_scope:
  - web/e2e/journey/settings.spec.ts
  - docs/discovery/capability-map.md
  - milestone-15.md                          # 수정 라운드 1 (cr M-3)
  - reports/evidence/m15/15j/
out_of_scope:
  - 술어(privateValues.ts)와 그 밖의 여정 spec — 하한이 세는 방식만 바꾼다
  - 그 밖의 OPEN 행(EVAL-COST ⓑⓒ · SIGNUP-HARDENING · 훑기 실패 경보)
acceptance_commands:
  # CI 의 `web` job 전건 — 명령은 `.github/workflows/ci.yml` 그대로(15I scope 와 같은 열)
  - cd web && npm ci && npm run lint && npm run typecheck && npm run gate:src-tree
  - cd web && npm run api:types:check && npm run test && npm run gate:tests
  - cd web && npm run build && npm run gate:boundary-canary
  - cd web && npx playwright install chromium && npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar && cd web && npm run test:e2e:journey
rollback: reports/evidence/m15/15j/rollback.md
```

## 무엇을 닫는가

`OPEN-M15-TEST-RESIDUE` ⓕ(PR #196 verifier L-r1-1). `assertExemptScanPopulationMatches` 의
하한 둘이 `length` 를 세서, 리터럴과 술어 모집단을 **같은 속성 네 벌**로 바꾸면 하한(4 ≥ 4)과
정렬 집합 등식이 함께 초록이었다. 하한을 `new Set(...).size` 로 센다. 등식은 그대로다.

## 측정

- 술어 대조(축 = 하한·등식의 판정식만, 브라우저 없이): 변이 넷의 판정
  - 실제 목록 넷 — 구판 GREEN · 신판 GREEN
  - **같은 속성 네 벌(양쪽)** — 구판 GREEN · 신판 **RED** (= ⓕ 의 재현과 닫힘)
  - 빈 목록 둘 — 둘 다 RED (15I r1 M-2 가 세운 비공허 유지)
  - 술어에 다섯째 — 둘 다 RED (ⓑ 의 등식 유지)
- `npx eslint e2e/journey/settings.spec.ts --max-warnings 0` exit 0 · `npm run typecheck` exit 0
  (단 `tsconfig.json` 의 include 는 `src` 라 e2e 는 Playwright 변환이 타입 검사 없이 싣는다)
- 여정 실물 실행(`test:e2e:journey`)은 이 호스트에서 돌리지 않았다 — 정본은 PR head 의 CI `web` job.

## 알려진 제한

- 술어 대조는 판정식을 옮겨 적은 사본으로 쟀다. 실물 spec 의 「네 벌」 변이 RED 는 여정 실행이
  필요하고 그 실측은 verifier 몫이다.

## 하네스 레인 변경

없음 — `<base>..HEAD` 에 `CLAUDE.md`·`.claude/**` 커밋이 없다.

## 수정 라운드 1 (장부층만 — spec 무편집)

PR #198 code-reviewer 지적 처리. 반영: M-3(`milestone-15.md` 의 ⓕ 줄) · M-4(`commands.md` 신설) ·
M-5(위 절) · L-2(rollback 유효성 술어에 evidence 경로). 합리적 거부: M-1(양쪽 목록 동시 치환) 과
verifier L-1(대소문자 변형 네 벌) — 둘 다 리터럴(= 계약 문면) 자체를 바꾸는 변이라 이 대조의 위협
모델 밖이다. 같은 리터럴을 둘째 상수로 복제해도 같은 꼴의 사본일 뿐이다. 보류: L-1(head_sha
자리표시자 — 15I 관례) · L-3(실패 문면 — spec 을 다시 열 가치가 없다) · L-4(touchMeasure.ts 타입
누락 — 범위 밖, 오늘 거동 불변).
