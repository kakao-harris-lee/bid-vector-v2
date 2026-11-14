# M12 / screen-coverage-3 — rollback

**실측 HEAD: `59a1cd7059956650c9035fb28914368eecc3519e`** (이 slice 의 마지막 산출물 커밋).
base: `10d208d2664c6bb87cd7082a2853c08c2c0d3236`.

되돌림은 **range revert 가 아니다** — in_scope 경로를 base 의 내용으로 복원한다.

```
git restore --source=10d208d2664c6bb87cd7082a2853c08c2c0d3236 --staged --worktree -- \
  web/scripts/screen-card-coverage.mjs \
  web/scripts/screen-vocabulary-registry.mjs \
  web/scripts/screen-card-coverage.test.mjs \
  web/src/screens/screenVocabulary.test.tsx \
  web/config/gate-tests.txt \
  reports/evidence/m12/screen-coverage-3
```

복원 목록은 **기계 산출**이다(`git diff --name-only <base>..<실측 HEAD>`), 그리고 그 집합과 위
목록의 차집합이 빈 것을 `comm -23` 으로 확인했다 — **빈 출력**. `web/config/gate-tests.txt` 는
이 slice 가 편집하지 않았으므로 기계 산출에 없고(복원이 무편집), in_scope 라서 목록에 둔다.
라운드마다 재산출한다.

## 실측 — 버릴 clone 에서 ①~⑥

| # | 재는 것 | 결과 |
| --- | --- | --- |
| ① | 복원 명령의 종료 코드 | **exit 0** |
| ② | 복원이 바꾼 경로의 계수 | **D 4 · M 3 = 7** (신설 파일 넷이 사라지고 편집 셋이 되돌아간다) |
| ③ | base 와의 diff | **0 바이트** — 복원 뒤 그 경로들이 base 와 바이트까지 같다. 「내 표지 0」(복원된 판정 파일이 이 slice 의 장부 모듈을 더 이상 참조하지 않는다) · 「남의 표지 보존」(추적 파일 3204) |
| ④ | 컴파일 | `npm run typecheck` **exit 0** |
| ⑤ | test | `npm run test` **exit 0** · 987 passed(③ 이 base 동일성을 보였으므로 이 수는 base 의 수다) |
| ⑥ | 게이트 | `gate:src-tree` **exit 0** · `gate:tests` **exit 0** · `eslint` **exit 0** |

**⑥ 은 한 번 붉었고 그 원인은 되돌림이 아니라 측정 하네스였다.** 설치본을 레인에서 심볼릭
링크로 빌려 쓰자 `gate:src-tree` 가 그 링크를 보고 멈췄다(그 게이트의 술어가 「`web/` 안 심볼릭
링크 0」이다 — 게이트가 제 일을 했다). 하드링크 사본으로 바꿔 **보완 경로까지 실행**했고 그 뒤
여섯이 모두 초록이다. 초록이 아닌 채로 넘기지 않았다.

## 실측 HEAD 와 판정 SHA 사이

evidence 커밋은 언제나 실측 뒤에 오므로 둘은 같지 않다. 중요한 것은 **그 사이에 되돌림 대상이
움직였는가**이고, 움직인 경로는 **전부 base 에 없는 신설 파일**이다(이 slice 가 만든
`reports/evidence/m12/screen-coverage-3/` 아래 파일들) — 2026-11-11 에 세운 예외가 그 자리다.
열거와 「원천 부재」 확인은 verifier 가 한다. 실측 HEAD 와 판정 SHA 사이의 **병합 커밋은 0**
(`git rev-list --merges` 빈 출력)이다.

main 병합 뒤에 되돌릴 때는 원천이 **병합된 main 의 고정 SHA** 이고(움직이는 `origin/main` 이
아니다), 그 SHA 에 이 slice 가 없음을 전제로 센다. 그 경우 복원 대상 blob 이 착수 base 와 새
원천에서 같은지(셋째 단언)를 다시 잰다.

## 비활성화

게이트만 끄려면 `web/config/gate-tests.txt` 에서 두 줄(모집단 판정의 대조 · DOM 어휘 게이트)을
빼면 W-7 이 그 파일들을 요구하지 않는다. 다만 W-6 는 여전히 돌리므로, **판정을 느슨하게 하려면
되돌림이 맞다** — 끄는 길을 남기는 것이 이 게이트의 목적에 어긋난다.
