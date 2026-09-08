# Rollback — M3/3C 문서/LLM extraction adapter

정본: `git restore --source=<base_sha> --staged --worktree -- <경로>` (경로는 개별 인자,
`git checkout <base> -- <경로>` 는 쓰지 않는다 — base 에 없는 신규 경로마다 pathspec 오류로
exit 1).

## 되돌릴 경로 (in_scope, 신규 파일만 — base 에 존재하지 않음)

```
procurement/src/main/kotlin/bidvector/procurement/AttachmentDocumentPort.kt
procurement/src/main/kotlin/bidvector/procurement/RequirementExtractionPort.kt
procurement/src/test/kotlin/bidvector/procurement/AttachmentDocumentPortTest.kt
procurement/src/test/kotlin/bidvector/procurement/RequirementExtractionPortTest.kt
adapters/src/main/kotlin/bidvector/adapters/extraction/
adapters/src/main/resources/schema/requirement-extraction.v1.json
adapters/src/main/resources/prompts/requirement-extraction.v1.txt
adapters/src/test/kotlin/bidvector/adapters/extraction/
```

## 되돌릴 변경 (in_scope, base 에 이미 존재 — 3C 가 추가한 줄만 걷는다)

```
adapters/build.gradle.kts                # qualification·strategy·resilience4j-circuitbreaker/
                                          # timelimiter·json-schema-validator·pdfbox 의존.
                                          # **구조적 변경(L-3, verifier r1)**: 기존 단일
                                          # dependencies{} 블록을 둘로 쪼갰다(M2/2A gRPC 좌표
                                          # 여섯 줄을 두 번째 블록으로 옮김, sizeGate 의 .kts
                                          # 람다 50줄 축 회피). 되돌릴 때는 3C 가 더한 의존
                                          # 줄만 지우는 것이 아니라 **그 이동도 되돌려
                                          # 단일 dependencies{} 블록으로 합쳐야** base 상태다.
gradle/libs.versions.toml                # json-schema-validator·pdfbox 버전/좌표,
                                          # resilience4j-circuitbreaker/timelimiter 좌표
config/quality/gate-tests.properties     # gate.tests.adapters 의 extraction.* 11개,
                                          # gate.tests.procurement 의 AttachmentDocumentPortTest·
                                          # RequirementExtractionPortTest 2개
```

이 세 파일은 `git restore --source=<base> ...`로 파일 전체를 되돌리면 3D 등 다른 레인이
같은 파일에 낸 base 이후 변경도 함께 사라진다 — **파일 전체 restore 가 아니라 3C 가 추가한
줄만 수동으로 제거**한다(위 세 파일은 병렬 레인 공유 파일, scope.md 「병렬 레인 경계」 참고).
`adapters/build.gradle.kts`는 위 구조적 변경 때문에 단순 줄 삭제로 끝나지 않는다 — 두
번째 `dependencies{}` 블록의 M2/2A 내용을 첫 블록으로 옮겨 합친 뒤에 3C 전용 줄을 지운다.

## 하네스 레인 변경

`git log --oneline <base>..HEAD -- CLAUDE.md .claude/` — 3C 작업 구간에서 없음. rollback
대상이 아니다.

## in_scope 이지만 3C 커밋이 만들지 않아 되돌리지 않는 것 (L-9)

`milestone-3.md`는 scope.md 의 in_scope 목록에 있으나, 그 파일에 실제로 손을 댄 것은
착수 커밋 `9089bd4`(세션 모델, 3C 착수 문단)와 3D 레인의 종결 커밋들이지 이 문서가 다루는
3C 구현 커밋(`8b6ea7f`..`ac470ca`, F/N/L 수정 라운드 포함) 어디에도 없다(`git log --stat
8b6ea7f..HEAD -- milestone-3.md` 로 실측 — 3C 구현 커밋의 변경 파일 목록에 0건). 그래서
위 두 「되돌릴」 목록 어디에도 이 파일이 없다 — 되돌릴 것이 없기 때문이다.

## 실측

임시 clone 에서 위 절차(경로 restore + 공유 파일 수동 되돌림)를 그대로 실행해 확인했다:
`adapters/build.gradle.kts`·`gradle/libs.versions.toml`은 `git diff <base>` 결과 0줄(base
완전 복원), `config/quality/gate-tests.properties`는 3D 레인이 base 이후 같은 파일에 더한
독립적인 두 줄(`CleanMigrationTriggerTest`·`PrecedenceLabelColumnTest`, 커밋 `15f3325`
소유)만 남고 3C 가 더한 줄은 전부 걷혔다 — 이는 「3C 줄만 걷혔다」는 판단이 실측으로
확인된 것이지 우연한 잔여가 아니다. 되돌린 트리에서 `./gradlew --no-build-cache clean
check` 는 exit 0(348 actionable tasks 전부 executed) — M2/2A gRPC 배선·3D 배선 모두 생존.

## 복구 시간

파일 삭제 + 세 공유 파일의 되돌린 줄 제거는 수 분 내(사람이 diff 검토하며 수행). 자동
one-shot 스크립트는 두지 않는다 — 공유 파일의 "3C가 추가한 줄만" 판별은 diff 검토가
필요하다(자동 삭제가 3D 등 병행 레인의 변경을 오삭제할 위험).

## 알려진 제한

- `OPEN-3C-ATTACHMENT-FIELD-CONTRACT` — 실제 `ntceSpecDocUrl1` 필드 계약 등재는
  `CollectionPolicy.kt`(3A 소유, in_scope 밖)의 몫이라 이 slice 는 등재하지 않았다.
  프로덕션에서 `AttachmentUrl.from(observation, contract)`을 실제 첨부 URL 필드로 호출할
  방법은 이 slice 만으로는 없다 — 후속 slice(3B 확장 또는 4B 배선)가 그 계약을 등록해야
  한다. 이 slice의 test는 이미 등재된 `bidNtceNo` 계약을 메커니즘 증명용으로 재사용했다
  (procurement 가 발급한 계약이 아니면 `AttachmentUrl`을 만들 수 없다는 모듈 경계 자체가
  방어 대상이지, 그 계약의 concept 이 실제 URL 필드인지는 이 slice 의 방어 범위 밖 —
  scope.md 「위협 모델」 「방어하지 않는다」와 같은 종류의 경계).
