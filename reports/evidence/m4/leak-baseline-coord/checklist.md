# checklist.md — leak-baseline-coord 수용 기준 충족 근거

| # | 기준 | 충족 근거 |
| --- | --- | --- |
| S-1 | 매치 집합 불변 — 매치한 줄 290 이 변경 전후 동일 | commands.md "S-1 실측" — base(a6ab6a8) worktree 와 head 트리에서 각각 `leakMatchesInFile` 로 매치 좌표를 산출해 `diff` 대조, 290줄 각각 완전 일치(차이 없음) |
| S-2 | 변환 뒤 `leakPatternGate` 가 new 0 · stale 0 | commands.md "baseline 교체 뒤" — 실행 보고서 `matches=290 keys=268 new=0 stale_baseline=0` |
| S-3 | 위쪽 줄 삽입이 승인을 무효화하지 않는다 | commands.md "S-3~S-6" 첫 항목 — `test-discovery-guard/checklist.md` 맨 위에 줄 삽입(좌표가 밀림) 후 게이트 재실행, 여전히 `new=0`. 단위 test `같은 파일에서 위쪽에 줄이 삽입돼도 승인된 매치의 키는 그대로다`(LeakPatternGateChecksTest.kt)로도 고정 |
| S-4 | 다른 파일에 같은 글자를 넣으면 막힌다 | commands.md "S-3~S-6" 두 번째 항목 — 승인된 매치 줄을 신규 파일에 복사, `new:` 로 잡혀 게이트 실패. 단위 test `같은 글자라도 다른 파일이면 다른 키가 된다` |
| S-5 | 옛 형식 항목이 남으면 실패한다 | commands.md "S-3~S-6" 세 번째 항목 — baseline 에 `경로:숫자` 한 줄 추가 후 게이트가 사유를 담아 명시 실패. 단위 test `옛 형식 항목이 있으면 사유를 담은 legacy 위반을 낸다` 등 |
| S-6 | 그 줄의 글자가 바뀌면 다시 본다 | commands.md "S-3~S-6" 네 번째 항목 — 승인된 매치 줄의 어휘를 바꾼 뒤 게이트가 새 키로 잡음. 단위 test `같은 자리 글자가 바뀌면 키도 바뀐다` |
| S-7 | 기존 test 14 가 새 규격으로 갱신되고 전부 통과 | `LeakPatternGateChecksTest.kt` — 정책 파싱·매치 판정·정규화·키 산출·baseline diff·legacy 검출·위반 메시지·보고서까지 29 tests, commands.md "구현 후 — 단위 test"·"최종 acceptance" 두 지점에서 0 failed 확인 |
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
`leakBaselineKey` 를 그대로 호출, 손으로 옮겨 적지 않음)의 산출.

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
- **`leakGateViolation`/`leakGateReportText` 가 같은 `groupBy` 를 각자 다시 계산**한다
  (Task 에서 한 번 계산해 재사용하지 않음) — 이 규모(수백 줄)에서는 성능 영향이 없고,
  두 함수가 각각 순수 함수로 독립 테스트 가능해야 한다는 기존 관례(파일 상단 주석)를
  따르기 위한 선택이다. 필요하면 다음 라운드에서 Task 쪽에서 한 번만 계산해 두 함수에
  넘기는 리팩터가 가능하다.
- **마이그레이션 생성기는 커밋되지 않은 임시 test**로 실행하고 삭제했다 — 재현이
  필요하면 이 evidence 의 코드 스니펫(commands.md 참조)으로 동일하게 재구성 가능하나,
  저장소에 반복 실행 가능한 `--write-baseline` 류 task 는 두지 않았다(설계 검토가
  명시적으로 out_of_scope 로 처리한 항목).

## N/A

- differential.json — 이 slice 는 Python/V2 판정 차이를 다루지 않는다(build-logic 게이트
  메커니즘 변경). N/A.
- golden-manifest.json — fixture 를 사용하지 않는다(baseline 자체가 실제 저장소
  `reports/evidence/` 매치에서 기계 산출됨, commands.md "D-LBC-4"). N/A.
