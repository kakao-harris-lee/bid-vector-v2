# M7/7C⑤-0 — rollback

base `a61ddff1` · **실측 HEAD `73f31710`**(검토 r1 수정 뒤의 마지막 산출물 커밋). 앞 라운드의 실측
(`662129a0`, 19 경로)은 그 SHA 의 것이고, 이 라운드가 산출물을 더해 목록이 **21 경로**로 늘었다 —
라운드마다 재산출한다.

## 되돌리는 법

in_scope 경로 한정이고 range revert 가 아니다. 목록은 **기계 산출**이다 —
`git diff --name-status a61ddff1..662129a0` 에서 `reports/evidence/` 를 뺀 것이고, 라운드마다 재산출한다.

```
git restore --source=a61ddff1 --staged --worktree -- <복원 목록 M 4건>
rm -f <삭제 목록 A 15건>
```

### 복원(M 4건) — 공유 파일(라운드 간 불변)

| 경로 | 이 slice 가 더한 것 |
|---|---|
| `config/quality/architecture-policy.properties` | 러너 참조자 · use case 생성 쌍 · ① 층·③ 층 분류 · 로그 출구 집합 · 전송 표면 쌍 둘 · `workflow.collection` 허용 타입 여섯 |
| `config/quality/gate-tests.properties` | 승격 test 일곱 등재 |
| `app/src/test/kotlin/bidvector/app/architecture/AppHttpDependencyGateTest.kt` | 면제 수 래칫 62→65 와 그 사유 |
| `docs/discovery/capability-map.md` | COL-02 · SET-02 의 승격 경로 경계 |

**공유 파일의 전제**: 네 파일 모두 base `a61ddff1` 에 이 slice 가 **없다**(첫 산출물 커밋
`6af1a083` 이 base 뒤에 온다). 머지 뒤에 되돌릴 일이 생기면 이 복원이 아니라 **PR revert** 다 —
그때는 다른 레인(7B·7C①b·7C③·g1c03)이 같은 키를 올려 두었을 수 있고, base 복원이 그들의 줄을
함께 지운다. 면제 수는 뒤에 rebase 하는 쪽이 합집합으로 다시 계산한다(팀장 D-7C5P-9).

### 삭제(A 17건) — 이 slice 가 만든 파일

procurement 셋(`OpeningPromotion.kt`·`OpeningPromotionSlots.kt`·`OpeningPromotionFieldReader.kt`)과
그 test 넷 · workflow use case 와 그 test · adapters 읽기 어댑터와 그 실 DB test **둘**(저장소 왕복 ·
출하 조립) · app 러너·배선과 그 test **셋**(줄 · 잠금 · 배선). 검토 r1 이 둘을 더해 17 이다.
(evidence 는 삭제 목록에 **없다** — 되돌림 대상이 아니다.)

## 배포 뒤 비활성화 — 한 줄

`bidvector.opening-promote.mode` 속성을 떼면 배선 전체가 올라오지 않는다: 러너도, 읽기 어댑터도,
canonical 저장소도 서지 않는다. **코드를 되돌리지 않고 끄는 경로가 그 한 줄이다**(배선 조건 test 가
「속성이 없으면 이 배선이 빈을 하나도 기여하지 않는다」를 잰다).

이미 쓴 canonical 행은 **원문에서 다시 만들 수 있는 파생 데이터**다 — 승격은 순수 함수이고 입력은
`raw_observation` 의 행들과 시도 원장이며, 둘 다 승격이 건드리지 않는다(원문 표는 DB 트리거로 추가
전용이고 원장은 읽기만 한다). 그래서 행을 지우고 다시 돌리면 같은 값이 선다. 다만 **지우는 권한은
앱 역할에 없다**(`opening_result` 에 DELETE GRANT 가 없다) — 행 삭제는 소유자 경로이고 이 slice 의
범위가 아니다.

## ①~⑥ 실측 — 버릴 clone (작업 트리를 건드리지 않았다)

| 단계 | 재는 것 | 결과 |
|---|---|---|
| ① | 되돌림 명령의 종료 코드 | `restore` exit **0** · `rm` exit **0** |
| ② | 되돌림 뒤 D/M 계수 | **17 D + 4 M = 21 줄** — 기계 산출 목록과 수가 같다 |
| ③ | **트리 동일성** — `git diff a61ddff1 -- <21 경로>` | **EMPTY**. 완료 판정은 종료 코드가 아니라 이것이다 |
| ③b | 남의 표지 보존 — 되돌림 밖 경로 | **EMPTY** — 되돌림이 목록 밖을 건드리지 않았다 |
| ④⑤⑥ | 되돌린 트리에서 compile · test · **게이트 전부**(`./gradlew --no-daemon check`) | exit **0** · `BUILD SUCCESSFUL in 9m 56s` |

④⑤⑥ 을 한 명령으로 재는 이유: 부분 게이트는 안 돌린 것과 같다. 되돌린 트리에서 `check` 가 초록인
것은 **게이트 등재까지 함께 되돌아갔다**는 뜻이다 — 복원한 네 파일이 base 로 돌아갔는데 test 등재와
실제 test 집합이 다시 등식을 이루고(`gateRegistrationGate`), 면제 수 래칫도 base 값과 맞는다.

## 실측 유효성 — 「실측 HEAD == 판정 SHA」가 아니다

evidence 커밋은 언제나 산출물 뒤에 오므로 두 SHA 는 영원히 다르다. 재야 하는 것은 **그 사이에
되돌림 대상이 움직였는가**다.

```
git diff --name-only 73f31710..<판정 SHA> -- <위 21 경로>
```

이 출력이 비면 유효하고, 한 줄이라도 나오면 미검증이다. 이 slice 가 실측 뒤에 올리는 커밋은
`reports/evidence/m7/7c5p/**` 뿐이고 그 경로는 21 목록에 없다 — 그래서 비어야 한다.

**이 등식은 두 SHA 가 다를 때만 뜻이 있다.** 같은 SHA 를 양쪽에 넣으면 무엇이든 비어 나오고, 그
빈 출력은 건전성의 증거가 아니다(실측 중 한 번 그렇게 재고 폐기했다 — 그때 HEAD 가 아직 실측
HEAD 와 같았다).
