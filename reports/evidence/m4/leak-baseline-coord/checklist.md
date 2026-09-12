# checklist.md — leak-baseline-coord 수용 기준 충족 근거

| # | 기준 | 충족 근거 |
| --- | --- | --- |
| S-1 | 매치 집합 불변 — **두 트리에 공통으로 존재하는 파일**에 대해 매치한 줄 집합이 동일(정정 2026-09-12, verifier F-2 — 최초 문면은 「변경 전후 동일」로 head 전체(299)와 어긋났었다) | commands.md "S-1 실측" — base(a6ab6a8) worktree 와 head 트리에서 각각 `leakMatchesInFile` 로 매치 좌표를 산출해 `diff` 대조, 공통 파일 한정 290줄 완전 일치(차이 없음). head 전체는 이 slice 의 신규 evidence 를 더해 299줄이다 |
| S-2 | 변환 뒤 `leakPatternGate` 가 new 0 · stale 0 | commands.md "baseline 교체 뒤" — 실행 보고서 `matches=290 keys=268 new=0 stale_baseline=0`(공통 파일 기준, 이 slice 자신의 evidence 를 추가하기 전) |
| S-3 | 위쪽 줄 삽입이 승인을 무효화하지 않는다 | commands.md "S-3~S-6" 첫 항목 — `test-discovery-guard/checklist.md` 맨 위에 줄 삽입(좌표가 밀림) 후 게이트 재실행, 여전히 `new=0`. **정정(verifier F-3)**: 단위 test `같은 파일에서 위쪽에 줄이 삽입돼도 승인된 매치의 키는 그대로다`는 자체 헬퍼로 키를 만들어 Task 의 실제 배선(`matches.map { leakBaselineKey(it.path, it.content) }`)을 지나지 않는다 — 그 배선을 줄 번호 인자로 되돌리는 변이에도 이 test 는 살아남는다. **S-3 를 실제로 고정하는 것은 단위 test 가 아니라 `check` 에 포함된 `leakPatternGate` 게이트 실행**이다(같은 변이에서 게이트는 `new=299` 로 전면 실패한다) |
| S-4 | 다른 파일에 같은 글자를 넣으면 막힌다 | commands.md "S-3~S-6" 두 번째 항목 — 승인된 매치 줄을 신규 파일에 복사, `new:` 로 잡혀 게이트 실패. 단위 test `같은 글자라도 다른 파일이면 다른 키가 된다` |
| S-5 | 옛 형식 항목이 남으면 실패한다 | commands.md "S-3~S-6" 세 번째 항목 — baseline 에 `경로:숫자` 한 줄 추가 후 게이트가 사유를 담아 명시 실패. 단위 test `옛 형식 항목이 있으면 사유를 담은 legacy 위반을 낸다` 등 |
| S-6 | 그 줄의 글자가 바뀌면 다시 본다 | commands.md "S-3~S-6" 네 번째 항목 — 승인된 매치 줄의 어휘를 바꾼 뒤 게이트가 새 키로 잡음. 단위 test `같은 자리 글자가 바뀌면 키도 바뀐다` |
| S-7 | 기존 test 14 가 새 규격으로 갱신되고 전부 통과 | `LeakPatternGateChecksTest.kt` — 정책 파싱·매치 판정·정규화·키 산출·baseline diff·legacy 검출·위반 메시지·보고서까지 30 tests(F-6 시정 후 회귀 test 1건 추가), commands.md "구현 후 — 단위 test"·"최종 acceptance" 두 지점에서 0 failed 확인 |
| S-8 | 전건 통과 | commands.md "최종 acceptance" — `./gradlew --no-daemon check` `BUILD SUCCESSFUL`(job 단위 축약 없이 전건) |

## 매치 판정 불변 확인 (경계 준수)

`leakMatchesInFile` 의 매치 판정(어떤 줄이 패턴에 매치하는가) 자체는 base 대비 한 글자도
바꾸지 않았다 — 반환 타입만 `List<String>`(좌표 문자열)에서 `List<LeakMatch>`(좌표+원문
내용)로 넓혔다. 필터 조건(`patterns.any { it.containsMatchIn(line) }`)은 그대로다. S-1 의
좌표 대조가 이 불변을 직접 증명한다.

## D-LBC-4 마이그레이션 수치 (설계 검토 예측과 일치)

- 매치 줄: 290 (예측 290)
- 접힌 키: 268 (예측 268)
- 2줄 이상 덮는 키: 9 (예측 9)
- 그 키들이 덮는 줄 합계: 31 (예측 31)

commands.md "D-LBC-4 마이그레이션" 항목 — 임시 생성기(프로덕션 함수 `leakMatchesInFile`·
`leakBaselineKey` 를 그대로 호출, 손으로 옮겨 적지 않음)의 산출. **이 수치는 base 와 공통인
파일 기준**이다 — 이 slice 의 신규 evidence 문서 자신은 스캔 명령·패턴 어휘를 인용해 별도
자기매치를 내므로(아래 "evidence 자기매치" 절), head 의 실제 게이트 수치는 이보다 크다.
정정 시점 실측: `baseline=276 matches=299 keys=276 new=0 stale_baseline=0`.

## detekt/ktlint 래칫

TooManyFunctions(11)·ThrowsCount(2) 기존 래칫을 낮추거나 예외 처리하지 않고, 함수 구조
재배치(해시 계산을 `leakBaselineKey` 내부로 흡수, legacy 필터를 `leakBaselineLegacyFormatViolation`
내부로 흡수, 별도 확장 함수 제거, Task 의 throw 두 곳을 `?:` 체인 한 곳으로 통합)로 통과시켰다
— commands.md "detekt / ktlint 래칫" 항목.

## secret 스캔

commands.md "secret 스캔" 항목 — 문서 작성 전엔 매치 없음, 작성 후엔 이 문서 자신의
스캔 명령·절 제목 인용으로 4건 매치(카테고리 a/b, 실유출 아님) — 검토 후
`leak-pattern-baseline.txt` 에 등재해 `leakPatternGate` 를 `new=0` 으로 복귀시켰다.
육안 확인 병기(telegram id·사업자 정보 없음).

## 알려진 제한

- **경로도 좌표다** — 파일을 rename 하면 그 파일의 baseline 항목 전부가 stale 이 되고
  매치는 new 가 되어 게이트가 붉어진다(설계 검토 D-LBC-1 명시 한계). evidence 파일은
  rename 이 드물어 실무 영향은 낮다고 판단했지만, rename 이 필요한 slice 는 baseline
  갱신을 동반해야 한다.
- **같은 파일 안에서는 승인이 위치·횟수 무관·영구로 넓어진다** — 승인된 매치 줄을 같은
  파일 안에 복제하거나, 지웠다 다른 위치에 되살려도 게이트는 통과한다(설계가 의도한 것 —
  scope.md 「승인의 의미가 넓어진다」 절, verifier U-1). 오탐을 한 번 잘못 승인하면 그
  파일 안에서 그 문자열은 위치·횟수 무관하게 영구 허용된다.
- **옛 형식 검출은 정규 형태(`경로:숫자`)에만 걸린다** — 행말 주석(`경로:26  # 주석`)·
  범위 표기(`경로:26-30`)·경로만 있는 항목은 legacy 위반으로 안 잡히고 조용히 stale 로
  죽는다(verifier F-4). 현재 baseline 276 항목은 전수 정규 형태라 실해는 없다.
- **crafted 해시 선등재가 게이트를 연다** — 아직 없는 줄의 정규화 내용 해시를 손으로
  계산해 baseline 에 미리 넣으면, 그 줄이 실제로 삽입되는 순간 stale 흔적이 사라지고
  조용히 통과한다(verifier F-5·U-2). 회귀는 아니다 — 옛 체계도 좌표 선등재로 그 자리에
  오는 어떤 내용이든 승인했다. 새 체계는 정확한 내용을 알아야 하므로 오히려 좁지만,
  설계 검토가 「여는 방향이 아니다」로 처분한 우회 하나는 이 형태를 담지 못했다.
- **리뷰 표면이 줄었다** — 옛 좌표 표기는 사람이 가서 읽을 수 있었지만 `경로#해시` 는
  항목만 보고는 무엇을 승인했는지 알 수 없다(게이트를 돌려야 좌표가 나온다). 대가로
  얻은 것은 위쪽 편집에 안 밀리는 안정성이고, 진단 좌표는 게이트 보고서가 낸다.
- **게이트 보고서가 CI·리뷰에 남지 않는다** — 실행 보고서는 gitignore 대상 디렉터리에
  쓰인다. `matches`/`keys` 접힘 수의 드리프트가 어디에도 저장되지 않는다(verifier L-5).
  `OPEN-LEAK-GATE-REPORT-DURABILITY` — 이 slice 가 닫지 않는다.
- **마이그레이션 생성기는 커밋되지 않은 임시 test**로 실행하고 삭제했다 — 재현이
  필요하면 이 evidence 의 코드 스니펫(commands.md 참조)으로 동일하게 재구성 가능하나,
  저장소에 반복 실행 가능한 `--write-baseline` 류 task 는 두지 않았다(설계 검토가
  명시적으로 out_of_scope 로 처리한 항목).
- **evidence 자기매치의 재귀** — 이 게이트를 다루는 evidence 산문을 쓸수록 그 산문 자신이
  패턴 어휘를 인용해 새 자기매치를 낳고, baseline 에 등재해야 끝난다(이 slice 안에서
  누적 여러 건 — commands.md 참조). M0 의 「스캐너가 자기 출력을 스캔한다」와 같은
  갈래이고, 이 slice 가 닫지 않는다.
- **rollback 은 게이트까지 복원하지 못한다** — in_scope 4개 파일만 base 로 되돌리면
  compile·단위 test 는 서지만 `leakPatternGate`(따라서 `check`)는 그대로 붉다 — 이
  slice 의 evidence 디렉터리 자체가 base 에 없어 legacy baseline 에 대응 항목이 없기
  때문이다(재현: rollback.md "알려진 제한" 절, 첫 evidence 커밋부터 재현됨). 이 slice 가
  닫지 않는다.

## N/A

- differential.json — 이 slice 는 Python/V2 판정 차이를 다루지 않는다(build-logic 게이트
  메커니즘 변경). N/A.
- golden-manifest.json — fixture 를 사용하지 않는다(baseline 자체가 실제 저장소
  `reports/evidence/` 매치에서 기계 산출됨, commands.md "D-LBC-4"). N/A.
