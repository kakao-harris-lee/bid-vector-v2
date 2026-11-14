# M12 / web-test-stability — slice 계약

```yaml
milestone: M12
slice: web-test-stability
base_sha: 10d208d2664c6bb87cd7082a2853c08c2c0d3236   # 착수 커밋에서 기계로 확인한다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/e2e/journey/journey.spec.ts        # ① 여정 ③ 타임라인 단언의 기준점 · 여정 ④ 의 목록 포착
  - web/e2e/journey/shared.ts              # ① 「쓰기 뒤에 출발한 읽기」 헬퍼의 자리 (팀장 승인)
  - web/e2e/journey/onboarding.spec.ts     # ① 같은 idiom 의 기존 사본 — 공유 헬퍼로 (팀장 승인)
  - web/e2e/journey/results.spec.ts        # ① 「확정 뒤 화면 E」도 같은 헬퍼로 (팀장 승인)
  - docs/discovery/capability-map.md       # §12.3 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m12/web-test-stability/
out_of_scope:
  - web/src/**                             # 출하 코드 0 — 화면 거동을 바꾸지 않는다
  - web/scripts/kotlin-source-ownership.test.mjs     # ② 는 이 파일을 편집하지 않는다(팀장 판정) — 12C 의 축
  - web/scripts/money-vocab-cross-lang.test.mjs      # ② 측정만 — 처방의 대상이 없다(checklist ②)
  - web/vite.config.ts                     # 12C 와 공유하는 자리. 예산을 늘리는 것은 처방이 아니다(11E 실측)
  - web/config/gate-tests.txt              # 새 test 파일 0 — 등재할 것이 없다
  - web/e2e/journey/settings.spec.ts       # 전수 확인에서 「이미 소진된 재조회」로 판정(checklist §전수의 나머지 ⓒ)
  - web/scripts/screen-card-coverage*.mjs · web/src/screens/screenVocabulary.test.tsx   # 12B
  - web/scripts/kotlin-source.mjs · web/scripts/kotlin-read-guard*.mjs · web/eslint.config.js   # 12C
  - adapters/**/*AdapterDependencyTest.kt · config/quality/architecture-policy.properties   # 12A
  - .github/workflows/ci.yml               # 게이트의 명령·상한을 바꾸지 않는다
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 leak-pattern 게이트의 스캔 뿌리에 들어 경로가 닿는다
rollback: rollback.md
```

## 받는 항목과 처분 (팀장 판정으로 고정 2026-11-14)

| # | 항목 | 처분 |
| --- | --- | --- |
| 4 | `OPEN-M11-GATE-TEST-WARMUP-BUDGET` | **이미 닫힘** — 후보 둘의 첫 칸은 기동 비용을 지지 않는다. 처방을 적용하지 않는 사유는 「처방의 **대상이 없다**」다(재현 측정의 부재가 아니다). 근거는 측정. `checklist.md` ② 가 정본 |
| 6 | 여정 e2e ③ 타임라인 항목 수 경합(OPEN 없음) | **닫힘** — 경합을 재현하는 대조를 먼저 세우고, 기준점을 「쓰기 응답 **뒤에 출발한** 읽기」로 옮기고 그 응답이 그 기록을 담았다는 등식을 함께 단언한다. 주장은 유지된다. `checklist.md` 항목 6 이 정본 |

## 계약 갱신 (팀장 2026-11-14)

1. ① 의 헬퍼를 `shared.ts` 한 자리로 올린다 — `onboarding.spec.ts` 가 그것을 쓴다. 주입 대조는
   **두 쪽을 다** 재고(구판 RED · 신판 GREEN) 주입은 suite 밖에 둔다.
2. 여정 ④ 와 `results.spec.ts` 「확정 뒤 화면 E」에도 같은 헬퍼. `results.spec.ts` in_scope 추가.
3. ② 는 `kotlin-source-ownership.test.mjs` 를 편집하지 않는다(in_scope 에서 뺀다).
   **머지 순서 12C → 12D.**
4. 그 파일들의 ② 측정은 **12C 머지 뒤 그 main 에서** 다시 잰다 — 지금 측정은 예비다(PR #161 리뷰).
5. 무거운 칸의 문턱은 예산의 **3배**. 그 밑이면 신설 OPEN, 받는 자리는 그 파일을 소유하는 slice.
6. 날 `waitForResponse` 금지 구조 게이트는 범위 밖 유지 · 신설 OPEN 등재.
7. 트레일러는 `Claude Opus 5.5`(브리프가 정본).
8. **전수 확인에서 넷째 자리가 나왔다**(구현 중 실측) — `ensureInBidBox` 의 담기 쓰기. (1)(2)가
   승인한 같은 계열·같은 헬퍼·같은 파일이고 출하 코드 0 이라 같은 처방으로 닫고 동결 보고에 올렸다.

## 처분과 근거의 자리

**이 문서는 계약이다.** 처분·변이·알려진 제한·신설 OPEN 은 `checklist.md`, 명령과 종료 코드는
`commands.md` 가 정본이다. 착수 보고가 이 자리에 적었던 ①② 서술은 그 두 문서가 실측과 함께
받았고, 그 가운데 「같은 포착 술어의 다른 자리는 전부 탐색 전후로 보인다」는 **전수 확인에서
틀렸다** — 정정은 `checklist.md` §전수의 나머지다.

## base 확인

`git merge-base --is-ancestor 10d208d2 HEAD` · `git rev-parse 10d208d2` 를 착수 커밋에서 기계로
확인한다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 갱신 이력

- 2026-11-14 초판(착수 보고와 함께).
- 2026-11-14 팀장 판정 고정 — §받는 항목과 처분 · §계약 갱신 (1)~(8). 착수 보고가 제안으로
  적었던 ①② 서술은 `checklist.md`·`commands.md` 로 옮기고 이 문서는 계약만 든다.
