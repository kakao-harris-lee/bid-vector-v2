# M13 / ux-copy (13A) — rollback

되돌림은 **range revert 가 아니라 in_scope 경로 한정 복원**이다.

```
git restore --source=bd5cd039eaf70f6423cf85e3bf1923bb367fa16e --staged --worktree -- <복원 목록>
```

**복원 목록은 기계 산출이다** — `git diff --name-status <base>..<실측 HEAD>` 그대로이고 손으로
적지 않는다. 라운드마다 재산출한다.

**실측 HEAD: `a19af571fd9a2511784c5527cffe524708578200`**

**수정 라운드 1 에서 목록을 다시 산출했다**(v L-3). 앞 판은 23 경로를 **이 파일이 생기기 전**
커밋에서 산출해, 그 목록을 판정 SHA 에서 돌리면 이 파일이 남았다 — ③ 「추적 트리 전체가 base 와
같다」가 그 자리에서는 거짓이었다. 지금 목록은 **마지막 산출물 커밋에서 기계로 산출**했고 이 파일
자신을 담는다. 같은 range 에 병합 커밋은 **0** 이다.

실측 뒤에 이 파일을 다시 쓰는 커밋이 하나 온다(이 문장을 담은 커밋이다). 그래서 **실측 HEAD 와
판정 SHA 사이에서 움직이는 복원 대상은 원천에 없는 신설 파일뿐**이라는 전칭식이 여전히 이
목록의 유효 조건이고, 그 열거와 원천 부재 확인은 verifier 가 한다. 앞 판과 달라진 것은 그
신설 파일이 **목록 안에** 있다는 점이다 — 그래서 복원이 그것을 지우고 트리가 base 와 같아진다.

## 복원 목록 — 24 경로(신설 7 · 수정 17 · 삭제 0)

신설 여섯은 복원이 **지우는** 것이고, 수정 열일곱은 base 내용으로 **되돌리는** 것이다.

| 종류 | 경로 |
| --- | --- |
| 신설(A) | `reports/evidence/m13/ux-copy/` 의 scope · commands · design-review · checklist · **이 파일** |
| 신설(A) | 문면 게이트 두 층 — `web/scripts/` 의 소스 층 · `web/src/screens/` 의 렌더 층 |
| 수정(M) | `web/config/gate-tests.txt` |
| 수정(M) | `web/e2e/journey/` 의 여정 spec 넷 |
| 수정(M) | `web/src/RouteError.tsx` · `web/src/ScreenPlaceholder.tsx` · `web/src/routes.tsx` |
| 수정(M) | `web/src/display/` 의 resultDisplay · worksheetDisplay · myBidResultDisplay |
| 수정(M) | `web/src/screens/` 의 Inbox·Onboarding·Settings 화면 셋과 그 짝 test 셋 |

`web/src/display/moneyDisplay.ts` 는 계약이 겹침으로 적었으나 **편집 0** 으로 끝나 목록에 없다.
금지 꼴의 데이터 파일 한 개는 같은 range 안에서 생겼다 사라졌으므로 목록에 없다(§계약 갱신 (3)).

## 실측 — 버릴 clone 에서 ①~⑥

저장소를 `--no-hardlinks` 로 clone 해 실측 HEAD 를 체크아웃하고, 설치본만 하드링크로 들여
복원을 돌렸다(그 clone 은 버린다).

| 걸음 | 결과 |
| --- | --- |
| ① 명령 종료 코드 | **0** |
| ② 바뀐 파일 수 | **D 7 · M 17 · 합 24** — 복원 목록과 같다 |
| ③ base 와의 diff | **0 바이트**. 더해 **추적 트리 전체**가 base 와 같다(`git diff <base> --stat` 이 0 줄) — 갈음이 아니라 트리 동일성이고, **판정 SHA 자신에서** 그렇다(앞 판의 L-3 이 그 자리였다) |
| ④ 컴파일 | `npm run typecheck` **exit 0** |
| ⑤ test | `npm run test` **exit 0** · **51 파일 · 1169 test**(이 slice 가 더한 2 파일 · **32 test** 가 전부 사라진다 — 수정 라운드 1 이 대조 셋을 더했다) |
| ⑥ 게이트 | `npm run lint` **0** · `npm run gate:src-tree` **0** · `npm run gate:tests` **0** |

⑥ 이 초록인 것이 이 slice 의 되돌림에서 특히 중요하다: 게이트 test 둘이 사라지면서
`web/config/gate-tests.txt` 의 두 줄도 함께 되돌아가므로, **등록부와 실행 결과의 등식**이 그대로
선다(한쪽만 되돌리면 그 등식이 붉는다).

## 되돌리면 사라지는 것

- 문면 게이트 **두 층 전부**(소스 층의 전수 판정·포함 단언·접두사 등식·라우트 경계 축, 렌더 층의
  DOM 스윕·결합 대조·음성/양성 대조·fixture 어휘 단언·도달 단언·라벨 술어).
- 다섯 자리의 개발 코드와 고친 자리의 내부 용어가 **옛 문면으로 돌아온다**.
- 라우트 경계가 **던진 값의 원문을 다시 그리고** 「다시 시도」가 사라진다.
- 자리 표 라벨의 머리 글자가 돌아오고, 여정 locator 의 `exact: true` 도 함께 되돌아간다
  (둘은 짝이다 — 한쪽만 되돌리면 여정이 붉는다).
- 수정 라운드 1 이 세운 것도 함께 사라진다: 요소 안 보간을 합치는 읽기와 그 양방향 대조 ·
  맨 slice 토막 꼴과 그 양성·음성 대조 · 영구 부재 단언의 축어 잠금.

## 머지 뒤 되돌림

머지 뒤에는 이 복원을 쓰지 않는다 — **PR revert** 가 그 자리다. 공유 파일
(`web/config/gate-tests.txt` · 여정 spec 넷)은 다른 레인도 같은 range 에서 만지므로, 머지 뒤
경로 한정 복원은 남의 줄까지 되돌린다.
