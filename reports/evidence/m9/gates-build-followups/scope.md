# M9 / gates-build-followups — PR #138 여덟째 병합 라운드 후속

- base: main `6e6f953cf0e3d1ca36c1ccac896c1e57297bbecf`(= PR #138 병합 커밋) · 브랜치 `chore/m9-gates-build-followups`
- 입력: PR #138 「팀장 처분 — 여덟째 병합 라운드」(판정 SHA `5f8ea75f`) · 그 위 verifier r8(`ready-for-review`,
  차단 0 · low 2) · code-reviewer(`approve`, 차단 0 · medium 2 · low 4). PR 은 머지됐고 **장부층 지적만** 넘어왔다 —
  산출물 거동을 바꾸는 지적은 하나도 없다.

## 대상 (팀장 지시 2026-11-11)

1. cr M-1 — `architecture-policy.properties` 의 `policy.version` 13 → **14** + 판 이력 한 줄.
2. cr M-2 — 원 slice 「전건 `check` — 병합 트리」 절에 **실측 HEAD 좌표**(정정 인용, 이력 되쓰지 않음).
3. v r8 L-1 · cr L-1 — 「겹친 경로 열셋」 → **열둘** + 계수 명령(evidence 두 자리).
4. cr L-2 · L-3 — 두 층 허용 뿌리의 **집합 등식** test + 이름 층의 알림함 **어댑터 거부** 음성 단언.
5. cr L-4 — 「표 접힘 계수 18」의 **정의**와 계수 명령.
6. v r8 L-2 · cr OQ-2 — §4 rollback 술어 문면 정렬: **하네스 편집이라 하지 않는다**(아래 OPEN).

앞 라운드에서 넘긴 v r6 L-1 · M-3 · M-4 는 **등재 유지만** 한다(원 slice `scope.md`·`checklist.md` 의 그 단락
무편집).

## in_scope

| 경로 | 무엇 |
| --- | --- |
| `config/quality/architecture-policy.properties` | `policy.version` 값 + 주석(판 이력 · 「알려진 한계」). **키·목록 값 무편집** |
| `config/quality/gate-tests.properties` | 새 test 클래스 한 줄 등재(정렬 유지) |
| `adapters/src/test/.../evaluation/EvaluationAllowedRootsParityTest.kt` | 신설(등식 단언) |
| `adapters/src/test/.../evaluation/EvaluationAdapterDependencyTest.kt` | `ALLOWED_ROOTS` `private` → `internal` + 사유 |
| `adapters/build.gradle.kts` | `tasks.test` 에 정책 **선언 입력** + system property |
| `build-logic/src/test/.../SourceReferencePolicyTest.kt` | 음성 단언 test 하나 |
| `reports/evidence/m9/gates-build/{commands,scope}.md` | 정정 인용 셋(#2·#3·#5) |
| `reports/evidence/m9/gates-build-followups/**` | 이 slice 의 장부 |

## out_scope

- **게이트 술어 변경 0** — 더한 것은 단언과 그 단언의 입력 선언뿐이다. 바꾼 정책 값은 `policy.version` 하나다
  (허용 뿌리 목록은 무편집).
- 남은 일곱 축의 같은 등식 · 두 층 **단일 출처화** · §4 술어 문면 정렬(셋 다 아래 OPEN) · v r6 L-1·M-3·M-4.
- 원 slice 병합 커밋 메시지의 「열셋」 — 이력이라 되쓰지 않는다.

## acceptance

CI `check` job 의 명령 그대로 — `./gradlew --no-daemon check`. 표적 실행(`--tests`)은 변이 측정에만 쓰고
판정 근거로 쓰지 않는다(새 test 클래스의 등재 게이트는 전건에서만 붉는다).

## rollback

in_scope 경로 한정 `git restore --source=6e6f953c…` — 목록·전제·①~⑥ 실측은 `rollback.md`.

## 하네스 레인 변경

**없다.** `CLAUDE.md`·`.claude/**` 커밋 0. 이 slice 가 만지는 정책 파일 둘은 **게이트 입력**이고 하네스 문서가
아니다. 위 #6 은 하네스 편집이라 OPEN 으로 남기고, 이 slice 의 rollback 은 그 완화 문면에 기대지 않는다.

## OPEN

- `OPEN-SCOPED-ROOTS-PARITY-REST` — 범위 축 여덟 중 **일곱**(`audit`·`notice`·`opening`·`profile`·
  `qualification`·`relay`·`strategy`)에 두 층 등식 단언이 없다. 오늘은 여덟 축 모두 두 목록이 일치하지만
  (실측) 그 일치를 지켜 주는 것이 없는 축이 일곱이다. 넓히는 비용은 축마다 한 단어(`private` → `internal`),
  대안은 단일 출처화(두 층의 독립성을 버리는 거래 — ADR 자리).
- `OPEN-ROLLBACK-PREDICATE-WORDING`(= v r8 L-2 · cr OQ-2) — CLAUDE.md 요약·evidence-pack 의 §4 술어
  (「빈 출력」)와 원 slice `rollback.md` 의 전칭 확장이 갈린다. 다음 하네스 편집 자리.
