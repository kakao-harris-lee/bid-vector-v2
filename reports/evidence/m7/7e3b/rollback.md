# M7/7E-3b 되돌림

**실측 HEAD: `45afd981`**(이 slice 의 마지막 산출물 커밋 — 라운드마다 재산출·재실측한다). 이
뒤의 커밋은 evidence 뿐이고 **복원 목록 안의 경로를 건드리지 않는다** — 유효성 확인은 §4.

복원 기준 **`ac806a51`**(main, PR #109 7C⑤-b 머지). 전제 「그 SHA 에 이 slice 없음」: 이 브랜치의
첫 커밋이 `8dff5931`(착수 계약)이고 그 부모가 `ac806a51` 이다 — base 에 이 slice 의 커밋이
하나도 없다. base 는 **머지된 main 의 고정 SHA** 이므로 공유 파일(요구표 · `gate-tests.txt` ·
`package.json` · `results.spec.ts` · `api/*` · `openingResultDisplay.ts`)도 같은 자리에서
복원한다(움직이는 `origin/main` 을 쓰지 않는다).

**hunk 격리가 필요 없다.** 이 range 에서 공유 파일을 만진 커밋을 파일마다 나열한 결과
**전부 이 slice 의 커밋 하나씩**이었다(다른 레인의 줄이 섞이지 않았다) — 그래서 `base..HEAD`
전체 복원이 남의 줄을 걷지 않는다. 병렬 레인 M7/7C③-b 가 소유한 `results.spec.ts` 의 알림함
단언 블록과 `worksheetDisplay.ts` 는 **이 브랜치에서 한 줄도 바뀌지 않았다**.

## 1. 복원 목록 — 기계 산출

```
git diff --name-status ac806a51..HEAD | grep -v $'\t'reports/evidence/
```

**35 경로 = 복원(M) 14 + 삭제(A) 21.** `reports/evidence/m7/7e3b/**` 는 되돌림 대상이 아니다
(그 안의 `scope.md` 는 팀장 파일이다).

| 복원(M) 14 |
| --- |
| `docs/discovery/m7-screen-data-requirements.md` |
| `web/config/gate-tests.txt` · `web/package.json` |
| `web/e2e/journey/results.spec.ts` |
| `web/src/api/approvedOperations.ts` · `queries.ts` · `queryKeys.ts` |
| `web/src/display/openingResultDisplay.ts` · `resultDisplay.ts` · `resultDisplay.test.ts` |
| `web/src/screens/ResultCard.tsx` · `ResultsScreen.tsx` · `ResultsScreen.test.tsx` |
| `web/src/screens/WorksheetRecord.tsx` |

| 삭제(A) 21 |
| --- |
| `docs/usecase/portal-service-description.md` |
| `docs/usecase/captures/manifest.json` + **PNG 12 장** |
| `web/e2e/capture/usecase-captures.mjs` |
| `web/src/display/moneyDisplay.ts` · `moneyDisplay.test.ts` |
| `web/src/display/myBidResultDisplay.ts` · `myBidResultDisplay.test.ts` |
| `web/src/screens/ResultsAggregate.tsx` · `ResultsAggregate.test.tsx` |

`WorksheetRecord.tsx` 는 **화면 C 의 파일**이라 이 slice 가 처음 건드리는 자리다(과세 표를
한 출처로 모으면서). base 가 머지된 main 의 고정 SHA 이므로 복원 자리는 같고, 이 range 에서
그 파일을 만진 커밋은 이 slice 의 것 하나뿐이다.

**새 바이너리도 삭제 목록 안에 있다** — 캡처 PNG 12 장과 manifest 가 A 로 잡히므로 아래 `git rm`
이 그것까지 지운다(별도 처리가 필요 없다).

**목록 ↔ 기계 산출 등식**: `comm -23` · `comm -13` 둘 다 **빈 출력**(실측). 이 등식이 공허하지
않은 근거는 목록을 손으로 적지 않았다는 것이 아니라, **evidence 를 뺀 뒤의 두 집합을 서로
대조**한다는 것이다 — 내가 커밋하지 않아도 경로가 들어오면 `comm -13` 이 그것을 낸다.

## 2. 되돌리는 명령

```bash
BASE=ac806a51
git diff --name-status "$BASE"..HEAD | grep -v $'\t'reports/evidence/ > /tmp/rb-all.txt
awk -F'\t' '$1=="M"{print $2}' /tmp/rb-all.txt | sort > /tmp/rb-modified.txt
awk -F'\t' '$1=="A"{print $2}' /tmp/rb-all.txt | sort > /tmp/rb-added.txt

xargs -a /tmp/rb-modified.txt git restore --source="$BASE" --staged --worktree --
xargs -a /tmp/rb-added.txt git rm -f --
```

새 파일은 `rm` 이 아니라 **`git rm`** 이다 — `rm` 만 하면 인덱스에 남아 트리가 base 와 같아지지
않는다(7E-3a 가 실측한 자리).

마이그레이션 0 · 배포물 0 · 외부 호출 0 · DB 쓰기 0 이므로 **배포 뒤 되돌림 경로가 따로 없다**.
이 slice 는 `web/` 과 문서만 건드린다. `.github/workflows/ci.yml` 과 `tools/one-command-check.sh`
는 한 줄도 바뀌지 않았다 — 캡처 명령을 CI 에 붙이지 않았으므로 두 파일의 축어 일치가 그대로다.

## 3. 버릴 clone 에서의 ①~⑥ 실측

`git clone --no-hardlinks` 로 뜬 트리에서 위 명령을 돌리고 **`npm ci` 로 의존을 진짜 설치**했다
(심볼릭 링크 `node_modules` 는 `gate:src-tree` 가 거부한다 — 7E-3a 가 실측한 함정).

| 축 | 결과 |
| --- | --- |
| ① 명령 exit | `git restore` **0** · `git rm` **0** |
| ② 계수 | 복원 **14** · 삭제 **21** · `comm` 양쪽 빈 출력 |
| ③ 트리 동일성 | 작업 트리 vs base **0 줄** · **인덱스** vs base **0 줄** |
| ④ compile | `tsc --noEmit` exit **0** |
| ⑤ test | `vitest run` exit **0** — **606 passed**(HEAD 의 667 에서 이 slice 의 61 이 빠진 수) |
| ⑥ 게이트 | `gate:src-tree` **0** · `api:types:check` **0** · `gate:tests` **0** · `gate:boundary-canary` **0** · `eslint --max-warnings 0` **0** |

**갈음은 「HEAD 초록」이 아니라 트리 동일성으로만** 했다 — ③ 의 두 줄이 그것이고, ④⑤⑥ 은 그
동일한 트리가 실제로 서는지의 확인이다. ⑥ 에 `gate:tests` 가 든 것이 중요하다: 이 slice 는
등록부(`gate-tests.txt`)에 두 줄을 더했으므로, 되돌림이 **등록부와 test 파일 중 하나만** 걷으면
거기서 붉어진다.

## 4. 실측 HEAD 가 아직 유효한가

evidence 커밋은 언제나 실측 뒤에 오므로 「실측 HEAD == 판정 SHA」는 영원히 성립하지 않는다. 볼
것은 **그 사이에 되돌림 대상이 움직였는가**다:

```
git diff --name-only 45afd981..<판정 SHA> -- <§1 의 35 경로>
```

이 출력이 비어 있으면 위 실측이 그대로 유효하다. evidence 커밋은 `reports/evidence/m7/7e3b/**`
만 건드리므로 비어 있어야 하고, 한 줄이라도 나오면 **재측정이다** — 등록부 한 줄을 고치는
커밋도 마찬가지다.
