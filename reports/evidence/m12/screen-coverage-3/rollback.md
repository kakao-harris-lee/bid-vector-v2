# M12 / screen-coverage-3 — rollback

**실측 HEAD: `221d0166`** (머지 차례의 **마지막 산출물 커밋** — 기계로 골랐다: 범위의 커밋마다
`reports/evidence/` 밖 파일 수를 세어 마지막으로 0 이 아닌 것. 그 뒤는 evidence 뿐이다).
**복원 원천: `2de8bc4e`** — 이 PR 이 병합한 **main 의 고정 SHA** 다(움직이는 `origin/main` 이
아니다). 착수 base 는 `10d208d2664c6bb87cd7082a2853c08c2c0d3236` 이고, 아래 **셋째 단언**이 그 둘의
차이를 잰다.

되돌림은 **range revert 가 아니다** — in_scope 경로를 원천의 내용으로 복원한다.

```
git restore --source=2de8bc4e --staged --worktree -- \
  web/scripts/screen-card-coverage.mjs \
  web/scripts/screen-vocabulary-registry.mjs \
  web/scripts/screen-card-coverage.test.mjs \
  web/src/screens/screenVocabulary.test.tsx \
  web/config/gate-tests.txt \
  docs/discovery/capability-map.md \
  reports/evidence/m12/screen-coverage-3
```

복원 목록은 **기계 산출**이다(`git diff --name-only <원천>..<실측 HEAD>`), 그리고 그 집합과 위
목록의 차집합이 빈 것을 `comm -23` 으로 확인했다. `web/config/gate-tests.txt` 는 이 slice 가
편집하지 않았으므로 기계 산출에 없고(복원이 무편집), in_scope 라서 목록에 둔다. 라운드마다
재산출하며, **머지 차례에는 원천이 바뀌므로 다시 산출했다**.

## 실측 — 버릴 clone 에서 ①~⑥

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ⓷ | **셋째 단언** — 복원 대상 blob 이 착수 base 와 새 원천에서 같은가 | 판정 파일 셋과 등록부는 **같다**. 둘이 다르다 — ⓐ 장부 모듈은 **양쪽 모두에 없다**(이 slice 의 신설 파일. 둘 다 부재라 셋째 단언이 문제 삼는 「원천이 바뀌어 복원 내용이 달라지는」 경우가 아니다) ⓑ **capability map 은 실제로 다르다**(`01fb6965` → `bfc8e33d` — main 이 §9.1·G3 를 고쳤다). 그래서 복원은 **새 원천 기준**으로 셌다 |
| ① | 복원 명령의 종료 코드 | **exit 0** |
| ② | 복원이 바꾼 경로의 계수 | **D 6 · M 4 = 10** (신설 파일 여섯이 사라지고 편집 넷이 되돌아간다 — 착수 때보다 하나 많은 것이 capability map 이다) |
| ③ | 원천과의 diff | **0 바이트** — 복원 뒤 그 경로들이 원천과 바이트까지 같다 |
| ③ | **내 표지 0** | §12.3 의 **(p) 절이 0 개**(복원 전 1). 어휘 grep 이 아니라 **절 표제**로 센다 |
| ③ | **남의 표지 보존** | §12.3 하위 절 **15**(= (a)~(o), 내 것만 빠졌다) · main 이 이번에 더한 **G3 채택 결정 문단 1**(그 파일을 통째로 착수 base 로 되돌렸다면 0 이 됐을 자리다 — 셋째 단언이 가리킨 바로 그 위험) |
| ④ | 컴파일 | `npm run typecheck` **exit 0** |
| ⑤ | test | `npm run test` **exit 0** · 987 passed(③ 이 원천 동일성을 보였으므로 이 수는 원천의 수다) |
| ⑥ | 게이트 | `gate:src-tree` **exit 0** · `gate:tests` **exit 0** · `eslint` **exit 0** |

**⑥ 은 앞선 두 라운드에서 붉었고 그 원인은 되돌림이 아니라 측정 하네스였다.** 설치본을 레인에서
심볼릭 링크로 빌려 쓰자 `gate:src-tree` 가 그 링크를 보고 멈췄다(그 게이트의 술어가 「`web/` 안
심볼릭 링크 0」이다 — 게이트가 제 일을 했다). 두 번 같은 자리에서 멈췄으므로 **측정 스크립트를
하드링크 사본으로 고쳤고**, 이 최종 실측은 보완 없이 처음부터 여섯이 초록이다. 초록이 아닌 채로
넘긴 적은 없다.

## 실측 HEAD 와 판정 SHA 사이

evidence 커밋은 언제나 실측 뒤에 오므로 둘은 같지 않다. 중요한 것은 **그 사이에 되돌림 대상이
움직였는가**이고, 움직인 경로는 **전부 base 에 없는 신설 파일**이다(이 slice 가 만든
`reports/evidence/m12/screen-coverage-3/` 아래 파일들) — 2026-11-11 에 세운 예외가 그 자리다.
수정 라운드가 판정 파일을 다시 움직일 때마다 **실측을 그 라운드의 마지막 산출물 커밋에서 다시
돌린다**(앞 라운드의 실측을 옮기지 않는다 — r1 에서 두 번 그렇게 했다: v 처방 뒤와 cr 처방 뒤).
열거와 「원천 부재」 확인은 verifier 가 한다. 실측 HEAD 와 판정 SHA 사이의 **병합 커밋은 0**
(`git rev-list --merges` 빈 출력)이다.

원천이 `2de8bc4e` 인 전제는 **그 SHA 에 이 slice 가 없다**는 것이고, 위 「내 표지 0」이 그것을
보인다(복원 뒤 (p) 절이 0 개). 머지 뒤에 되돌릴 일이 생기면 그때는 **PR revert** 다.

## 비활성화

게이트만 끄려면 `web/config/gate-tests.txt` 에서 두 줄(모집단 판정의 대조 · DOM 어휘 게이트)을
빼면 W-7 이 그 파일들을 요구하지 않는다. 다만 W-6 는 여전히 돌리므로, **판정을 느슨하게 하려면
되돌림이 맞다** — 끄는 길을 남기는 것이 이 게이트의 목적에 어긋난다.
