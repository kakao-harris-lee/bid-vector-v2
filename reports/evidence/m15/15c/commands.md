# M15 / 15c — 실행 명령과 종료 코드

> **마지막 HEAD 의 결과 정본은 이 파일이 아니다** — evidence 는 자기 마지막 커밋의 post-state 를
> 담을 수 없다(그 줄을 적으려면 커밋이 하나 더 필요하고 그 커밋이 다시 같은 줄을 요구한다).
> 마지막 HEAD 의 게이트 결과는 **verifier 와 PR 조치 코멘트**가 든다.

## 해소된 사실 둘 — 결론만 남긴다

- **번호 결번**: 15B 가 main 에 V29 를 들이면서 V28 · V29 · V30 이 **연속**이 됐고
  `migrationOrderGate` · `MigrationOrderTest` 가 초록이다(scope.md 알려진 제한 6 의 종결).
- **임시 연속 번호 측정**: 결번 동안의 전건은 **번호만** 연속으로 바꾼 임시 트리에서 쟀다. 바꾼
  자리는 둘(마이그레이션 파일 이름 · 그 번호를 드는 래칫 한 줄)이고 **어느 것도 커밋하지 않았으며**,
  측정이 끝난 트리는 `git status --porcelain` 빈 출력으로 확인했다. 그 측정은 아래 최종 전건이
  **같은 트리에서 결번 없이** 다시 내므로 이 파일은 결과를 중복해 싣지 않는다.

## 변이 — 저장된 번호가 입력칸 밖에 그려지는 경로

| 변이 | 결과 |
| --- | --- |
| `ProfileDetailsCard` 읽기 전용 줄에 저장된 번호를 `3-2-5` 로 그린다(v W3 재현) | **RED** — 설정 화면 test 「저장된 사업자번호는 입력칸 밖 어디에도 그려지지 않는다」 |
| 같은 변이를 되돌린다 | GREEN — 붉음이 심은 줄 때문임을 쌍이 말한다 |

변이는 파일 사본으로 심고 사본으로 되돌렸다(`git checkout --` 을 쓰지 않는다 — 같은 트리에
미커밋 산출물이 있었다). `git diff --numstat` 으로 **줄이 실제로 바뀐 것**을 먼저 확인했다.

## 수정 중 고친 자기 결함 둘

- 새 여정이 `내 입찰함` 을 `exact: true` 로 집다 120초 타임아웃으로 멈췄다. 그 자리의 접근성
  이름은 안 읽음 수를 품어 **「내 입찰함 3」**이고 그 수는 앞 여정들이 남긴 상태다 — 탐색으로
  좁혀 부분 일치로 집는 꼴(여정 ①·결과 여정의 선례)로 고쳤다.
- 수정 라운드가 **표적 `test` 태스크만** 돌아 `ktlint`·`detekt` 가 한 번도 돌지 않았고, 다음
  라운드가 두 줄을 잡았다(길이 한도 · KDoc 둘 · 들여쓰기). 「부분 게이트는 안 돌린 것과 같다」의
  실물이다.

## 2026-11-21T20:30Z(KST 11-22 05:30) — 15A 병합

main `d2c0513db`(15A 머지)를 **보통 병합**으로 들였다(부모 둘). 충돌은 하나 — 잠금 상한 관례
lint 의 번호 집합이고 **합집합**(28 과 30 둘 다 기존 표를 ALTER 한다)으로 풀었다.

### 병합 합집합 점검

| 축 | 결과 |
| --- | --- |
| 계약 파일 중복 키(파서로 전수) | **0** · `Profile` 12 속성 · `BusinessDetailsRequest` 6 속성 |
| 계약 파일에서 main 쪽 줄이 지워졌나 | 삭제 3 줄 전부 **내 의도된 교체**(요약·문면·머리말) |
| 생성 타입 바이트 등식 | 통과 — 15A 의 편집은 `security:` 뿐이라 생성 바이트를 바꾸지 않는다 |
| 산출물 diff(main 대비) | 내 slice 20 파일뿐 — 15A 의 줄이 접힌 자리 **0** |
| 옛 `AccountId` import 잔존 | **0**(15A 가 `bidvector.sharedkernel` 로 옮겼다) |

### 공유 build cache 의 낡은 산출물 — 통제 셋으로 갈랐다

이 병합의 첫 전건에서 `app:test` 11 건이 붉었고 **산출물 결함이 아니었다**. 통제 셋 —
main `d2c0513db` 에서 같은 세 클래스 19 test **통과(실행)** · 내 병합 커밋의 **새 clone** 에서 같은
실패 재현 · 같은 clone 에서 모듈 `build/` 삭제 + `--no-build-cache` 로 **통과**. 서버 쪽 예외는
`AbstractMethodError: … does not define or inherit … current(bidvector.sharedkernel.AccountId)`
— 15A 가 타입을 **옮겨서** 생긴 낡은 `.class` 의 서명이다(저장소 교훈 「지운 타입의 낡은 .class」가
이 모양을 미리 적어 둔 자리). 그래서 아래 최종 전건은 `--no-build-cache` 로 돈다.

## 2026-11-22T04:20Z(KST 13:20) — 15B 병합 · **최종 전건**

main `036fbda91`(15B 머지)를 **보통 병합**으로 들였다(부모 둘). 충돌은 **하나** — 잠금 상한 관례
lint 의 번호 집합이고 **합집합**(28 · 29 · 30 이 전부 기존 표를 ALTER 한다)으로 풀었다.

### 병합 합집합 점검

| 축 | 결과 |
| --- | --- |
| 계약 파일 중복 키(파서로 전수) | **0** · 스키마 42 · 경로 38 |
| 15B 표지 생존 | `/api/accounts` 경로 **있음** · `SignUpRequest` 스키마 **있음** |
| 내 표지 생존 | `Profile` 12 속성 · `BusinessDetailsRequest` 6 속성(둘 다 사업자번호 포함) |
| 전 트리에서 main 쪽 줄이 접혔나 | 삭제 69 줄 **전부 이 slice 의 의도된 교체**(어휘 대조로 전수 확인) |
| 생성 타입 바이트 등식(W-5) | 통과 — 202,953 바이트(15B 추가분 반영) |
| 여정 하네스 · 등록부 · capability map | 삭제 17 줄 전부 이 slice 의 옛 누출 게이트 코드 |
| 마이그레이션 번호 | 최대 **V30** · 결번(은퇴 10·11 제외) **0** |
| web typecheck · eslint | 각 **0** |

### acceptance — `./gradlew --no-daemon --no-build-cache check --continue`

- exit **0** · **BUILD SUCCESSFUL in 35m 13s** · 붉은 task **0**
- `migrationOrderGate` · `build-logic:test` 초록(결번 해소) · 앞서 고친 ktlint·detekt 초록
- `--no-build-cache` 와 모듈 `build/` 삭제를 쓴 이유는 위 절에서 실측한 **공유 cache 의 낡은
  산출물** 때문이다. CI 는 cache 가 비어 있어 같은 조건이다 — PR CI 가 나머지의 정본이다.
- 같은 트리의 `web` job 단계 전수(W-1~W-13)도 exit 0 이다 — vitest · Playwright 스모크 · 여정 e2e ·
  `api:types:check` 바이트 등식.

### 되돌리기 재측정

원천을 **병합한 main `036fbda91`** 로 옮기고 ①~⑥ 을 전부 다시 냈다. 단언 셋과 수치는
`rollback.md` 가 정본이다 — 요약: 목록 35 · 복원 exit 0 · D 2/M 33 · 원천 대비 diff 0 ·
**내 표지 0 · 남의 표지 8 파일 보존** · 되돌린 트리 `check` exit **0**.

## 비밀값 스캔

- cmd: `grep -rniE -f config/quality/leak-patterns.txt reports/evidence/m15/15c/`
- exit: 1 (매치 없음 = 통과)
- 육안: **실 사업자의 사업자번호 0** · Telegram id · 운영 credential 을 evidence 에 적지 않았다.
  **합성 번호는 `scope.md` 의 「받는 꼴」 표에 축어로 있다** — 그것이 알려진 제한 10 의 전제다
  (`leak-patterns.txt` 에 이 꼴을 더하면 그 축어가 붉어지므로 합성 allowlist 가 함께 와야 한다).
  test 에 쓴 번호도 전부 합성이다.

## 낡는 좌표 — 역방향 파급

- cmd: `grep -rnE '(ProfileDetailsCard|SettingsScreen|OnboardingScreen|profileDisplay|ProfileRequests|ProfileResponses|BusinessProfileValues|OnboardingChecklist|bidvector-operator-api|approvedOperations|screen-vocabulary-registry)[a-z.]*:[0-9]+' --include='*.md' --include='*.kt' --include='*.ts' --include='*.tsx' --include='*.properties' --include='*.mjs' .`
- 결과 1건: M7 evidence 가 계약 파일의 **874-878** 줄을 가리킨다.
- cmd: `git diff 99f2fb25..HEAD -U0 -- openapi/bidvector-operator-api.yaml | grep -E '^@@'`
- 핵심 결과: 이 slice 의 편집은 전부 **2093 줄 이후**다 → 그 좌표는 밀리지 않는다.

## clean-tree 게이트 (리뷰 요청 조건)

- cmd: `git status --porcelain -- <in_scope 경로 개별 인자>`
- exit: 0, 출력 없음
- 양성 대조: `web/e2e/journey/privateValues.ts` 에 한 줄을 더하면 그 경로가 `M` 으로 잡히고,
  **비파괴 절삭**(`head -n <원래 줄수>`)으로 되돌렸다(`checkout --` 을 쓰지 않는다).
