import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import {
  listConstructionCapacities,
  removeConstructionCapacity,
  upsertConstructionCapacity,
  type ConstructionCapacity,
} from '../api/queries';
import { Button } from '../components/Button';
import { ErrorFace } from '../components/ScreenFaces';
import { isUnauthorized } from '../components/WriteFailure';
import { capacityBodyOf, type CapacityForm } from '../display/profileDisplay';

import { attemptOf, CAPACITY_QUERY_KEY, ProfileWriteFailure, useProfileWrite } from './profileWrites';

const EMPTY_CAPACITY: CapacityForm = { licenseName: '', amountWon: '', assessmentYear: '' };

/**
 * 시공능력평가액 — **행 단위**이고 기준 연도가 필수다(연 1회 갱신되는 값이다).
 *
 * 체크리스트 항목 `CONSTRUCTION_CAPACITY` 하나가 이 카드다. 그 항목은 나라장터 등록분야에 공사가
 * 있을 때만 걸리고, 분야를 아직 모르면 **선행 대기**다 — 그 판정은 서버가 한다(7C④-3).
 */
export function ProfileCapacityCard({ onSaved }: { readonly onSaved?: () => void }) {
  const [capacity, setCapacity] = useState<CapacityForm>(EMPTY_CAPACITY);
  const [missing, setMissing] = useState(false);

  const capacities = useQuery({ queryKey: CAPACITY_QUERY_KEY, queryFn: listConstructionCapacities });
  const save = useProfileWrite(upsertConstructionCapacity, CAPACITY_QUERY_KEY, () => {
    setCapacity(EMPTY_CAPACITY);
    onSaved?.();
  });
  const drop = useProfileWrite(removeConstructionCapacity, CAPACITY_QUERY_KEY, onSaved);

  return (
    <>
      <ProfileWriteFailure attempts={[attemptOf(save), attemptOf(drop)]} />
      <div data-testid="capacity-card" className="rounded-md border border-line p-3">
        <span className="text-sm font-medium">시공능력평가액</span>
        {capacities.isError && !isUnauthorized(capacities.error) ? (
          <ErrorFace testId="capacity-error" onRetry={() => void capacities.refetch()} />
        ) : null}
        {capacities.isSuccess && capacities.data.length === 0 ? (
          <p data-testid="capacity-empty" className="mt-2 text-xs text-ink-soft">
            신고한 행이 없습니다.
          </p>
        ) : null}
        <ul className="mt-2 flex flex-col gap-2">
          {(capacities.data ?? []).map((row: ConstructionCapacity) => (
            <li
              key={row.licenseName}
              data-testid={`capacity-${row.licenseName}`}
              className="flex flex-wrap items-center gap-2 text-sm"
            >
              <span>{row.licenseName}</span>
              <span className="font-numeric">{row.amountWon.toLocaleString('ko-KR')}원</span>
              <span className="text-xs text-ink-soft">{row.assessmentYear}년 평가</span>
              <Button onClick={() => drop.mutate({ licenseName: row.licenseName })}>지우기</Button>
            </li>
          ))}
        </ul>
        {/*
          **보이는 라벨 셋**(cr D3 · v M-3). 앞 판은 이름이 `aria-label` 에만 있어서 눈으로 보는
          운영자에게는 **똑같이 생긴 빈 상자 셋**이었다 — 어느 칸에 무엇을 적는지 알 길이 없고,
          틀리면 돌아오는 것은 어느 칸이 틀렸는지 말하지 않는 한 줄뿐이다. 같은 화면의 형제 카드
          전부가 이미 이 꼴을 쓴다(이 카드만 안 쓰고 있었다).

          금액 칸은 **형식 요건을 라벨에 싣는다**: 앞 판은 그 요건을 틀린 **뒤에야** 경고로
          말했다(WCAG 3.3.2 — 적기 전에 알 수 없었다).
        */}
        <div className="mt-2 grid gap-2 sm:grid-cols-3">
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">면허명</span>
            <input
              value={capacity.licenseName}
              onChange={(event) => setCapacity({ ...capacity, licenseName: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 text-sm"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">금액(원 · 자리 구분 없이)</span>
            <input
              inputMode="numeric"
              value={capacity.amountWon}
              onChange={(event) => setCapacity({ ...capacity, amountWon: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 font-numeric text-sm"
            />
          </label>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-ink-soft">평가 연도</span>
            <input
              inputMode="numeric"
              value={capacity.assessmentYear}
              onChange={(event) => setCapacity({ ...capacity, assessmentYear: event.target.value })}
              className="min-h-11 rounded-md border border-control-line bg-card px-3 font-numeric text-sm"
            />
          </label>
        </div>
        {missing ? (
          <p data-testid="capacity-missing" role="alert" className="mt-2 text-xs text-bad-ink">
            면허명과 자리 구분 없는 정수 금액·평가 연도가 필요합니다 — 쉼표·단위가 있으면 보내지
            않습니다(잘린 금액이 저장되지 않게).
          </p>
        ) : null}
        <div className="mt-2">
          <Button
            tone="primary"
            disabled={save.isPending}
            onClick={() => {
            // 셋 다 **문자열 전체가 모양에 맞아야** 보낸다 — 0·올해를 지어내지 않고, 앞자리만
            // 읽어 잘린 금액을 보내지도 않는다(CR M-1 — 여기는 확인 단계가 없어 더 조용하다).
            const body = capacityBodyOf(capacity);
            if (body === null) {
              setMissing(true);
              return;
            }
            setMissing(false);
              save.mutate(body);
            }}
          >
            시평액 저장
          </Button>
        </div>
      </div>
    </>
  );
}
