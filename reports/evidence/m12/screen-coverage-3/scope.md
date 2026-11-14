# M12 / screen-coverage-3 — slice 계약

```yaml
milestone: M12
slice: screen-coverage-3
base_sha: 10d208d2664c6bb87cd7082a2853c08c2c0d3236   # 착수 커밋에서 기계로 확인했다(아래 「base 확인」)
head_sha: 리뷰 시점의 PR head      # 이 파일을 담은 커밋이 그 SHA 라 자기 안에 적을 수 없다
in_scope:
  - web/scripts/screen-card-coverage.mjs          # ⓐⓑⓒⓓ 판정과 모집단
  - web/scripts/screen-vocabulary-registry.mjs    # 신설 — 씨앗과 분류 장부(데이터). §계약 갱신 (1)
  - web/scripts/screen-card-coverage.test.mjs     # 그 판정의 양성·음성 대조
  - web/src/screens/screenVocabulary.test.tsx     # 마운트 목록 +2 와 DOM 게이트(계약이 승인한 의무 증가)
  - web/config/gate-tests.txt                     # 새 게이트 test **파일**이 생기면 같은 커밋에서 등재(오늘 계획상 0)
  - docs/discovery/capability-map.md              # §12.3 의 하위 절 하나 — 글자는 머지 차례에 정한다
  - reports/evidence/m12/screen-coverage-3/
out_of_scope:
  - web/src/screens/*.tsx (test 아닌 것) · web/src/display/**   # 출하 코드를 한 줄도 바꾸지 않는다
  - web/src/components/**                 # 모집단 판정이 이 디렉터리를 **읽어도** 파일 편집은 0
  - web/vite.config.ts · web/eslint.config.js    # 12C·12D 와 공유하는 자리
  - web/scripts/kotlin-*.mjs · web/scripts/kotlin-read-guard*   # 12C 의 축
  - web/scripts/money-vocab-cross-lang.test.mjs · web/scripts/kotlin-source-ownership.test.mjs  # 12D 의 축(예열 예산)
  - web/e2e/**                            # 12D 의 축(여정 ③ 경합)
  - adapters/** · config/quality/architecture-policy.properties · config/quality/gate-tests.properties  # 12A
acceptance_commands:   # ci.yml `web` job 전건 W-1~W-13 그대로 (`web/` 에서 돈다)
  - npm ci · npm run lint · npm run typecheck · npm run gate:src-tree
  - npm run api:types:check · npm run test · npm run gate:tests · npm run build
  - npm run gate:boundary-canary · npx playwright install chromium · npm run test:e2e
  - ./gradlew --no-daemon :app:bootJar · npm run test:e2e:journey
  - ./gradlew check   # evidence 가 누출 패턴 게이트의 스캔 뿌리에 들어 경로가 닿는다 — **머지 차례에** 돈다(팀장 브리프)
rollback: rollback.md
```

## 받는 항목과 처분

| # | 항목 | 이 slice 의 처분 |
| --- | --- | --- |
| 2 | `OPEN-M11-SCREEN-COVERAGE-CROSS-FILE-VOCAB`(ⓐⓑⓒⓓ) | 착수 보고의 네 후보 — 팀장 답과 설계 검토 판정으로 고정한다 |
| 3 | `OPEN-M11-SCREEN-COVERAGE-POPULATION-DIR` | 「마운트할 수 있는 카드」의 뜻을 먼저 고정하고 설계 검토가 판정한다 |

처분 문면은 팀장 답을 받은 뒤 이 절에 고정한다(범위 밖 판정은 팀장만 낸다). 착수 실측의 수치와
근거는 `commands.md` 가 정본이고 여기 옮겨 적지 않는다.

## 승인 경계 (계약 「승인 경계」 절의 이 slice 몫)

- 마운트 의무 **+2**(`StrategyFieldRow` · `StrategyEditPanel`)를 마운트 목록과 DOM 게이트에 더하는
  것은 **계약이 미리 승인했다**(test 추가뿐, 출하 코드 0).
- **그 밖의 의무 증가는 멈추고 보고한다.** 새 축이 오늘의 화면·표시 코드를 붉히면 구현하지 않는다.
- 게이트 장부(분류 표)의 행이 늘어나는 것은 의무 증가가 아니지만, 행 수가 두 자리로 늘어나는
  설계는 팀장 판정 대상이다(착수 보고 ⓑ).

## base 확인

`git rev-parse 10d208d2` · `git merge-base --is-ancestor 10d208d2 HEAD` 를 착수 커밋에서 기계로
확인했다(위 yaml 의 40자는 손으로 적은 값이라 그 자체가 근거가 아니다).

## 하네스 레인 변경

`git log --oneline <base>..HEAD --no-merges -- CLAUDE.md .claude/` — **착수 때 없음.** 이 절에 줄이
서면 그것은 이 slice 의 산출물이 아니며 in_scope 밖이고, 운영자 승인 하에 같은 range 에 있다.
귀속은 라운드마다 기계로 재산출해 확인한다.

## 계약 갱신

**(1) 새 파일 하나 — 장부를 데이터로 뺀다.** 씨앗과 분류 표가 26 행 늘어 판정 코드와 한 파일에
두면 파일이 권고 한도를 넘고, 사람이 고치는 자리와 기계가 세는 자리가 섞인다. 그래서
`web/scripts/screen-vocabulary-registry.mjs` 를 신설해 **데이터만** 담고 판정 코드는 그것을 들여온다
(크기 회피용 기계적 분할이 아니라 역할 경계다). 게이트 test **파일**이 아니므로 `gate-tests.txt`
편집은 없다.

**(2) 씨앗 하나 더 — 명령 축**(팀장 판정 대기, 추천안으로 진행). DOM 게이트는 명령 토큰도
금지하는데 씨앗 여덟은 전부 금액·시각이었다. 그래서 명령 축의 입구를 「어휘가 아니다」로 분류하면
그 사유가 거짓이 된다. 실측상 그것을 씨앗으로 더해도 **마운트 의무는 7 그대로**다(그것을 부르는
카드가 모두 이미 의무다). 팀장이 거절하면 그 한 행만 되돌린다.

## 갱신 이력

- 2026-11-14 초판(착수 보고와 함께).
- 2026-11-14 설계 검토 승인 뒤 — §계약 갱신 (1)(2) · in_scope 에 장부 파일 하나.
