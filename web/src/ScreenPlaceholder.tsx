import type { ScreenSlot } from './routes';

/**
 * 빈 화면 자리. 셸이 뜨는 것까지만 책임진다.
 *
 * **오늘 이 자리에 닿는 길은 알 수 없는 주소 하나다** — 자리 표의 줄은 모두 자기 화면을 들었고,
 * 라우터가 이 컴포넌트를 쓰는 곳은 catch-all 뿐이다. 그래서 문면도 그 사실을 말한다. 자리 표에
 * 화면 없는 줄이 다시 생기는 날 그 자리에 맞는 말은 그 slice 가 고른다.
 */
export function ScreenPlaceholder({ slot }: { slot: ScreenSlot }) {
  return (
    <section data-testid={`screen-${slot.id}`} className="p-6">
      <h2 className="text-lg font-semibold">{slot.label}</h2>
      <p className="mt-2 text-sm text-ink-soft">
        이 주소에 해당하는 화면이 없습니다. 주소를 확인하거나 메뉴에서 다시 골라 주십시오.
      </p>
    </section>
  );
}
