import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useNow } from './useNow';

describe('useNow', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('kezdetben az aktuális időt adja vissza', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000);
    const { result } = renderHook(() => useNow());
    expect(result.current).toBe(1_000_000);
  });

  it('alapértelmezés szerint másodpercenként frissül', () => {
    vi.useFakeTimers();
    vi.setSystemTime(1_000_000);
    const { result } = renderHook(() => useNow());

    act(() => {
      vi.advanceTimersByTime(999);
    });
    expect(result.current).toBe(1_000_000);

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe(1_001_000);

    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(result.current).toBe(1_003_000);
  });

  it('a megadott intervallum szerint tickel', () => {
    vi.useFakeTimers();
    vi.setSystemTime(5_000);
    const { result } = renderHook(() => useNow(250));

    act(() => {
      vi.advanceTimersByTime(249);
    });
    expect(result.current).toBe(5_000);

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(result.current).toBe(5_250);

    act(() => {
      vi.advanceTimersByTime(500);
    });
    expect(result.current).toBe(5_750);
  });

  it('az intervallum változásakor az újat használja', () => {
    vi.useFakeTimers();
    vi.setSystemTime(0);
    const { result, rerender } = renderHook(({ interval }) => useNow(interval), { initialProps: { interval: 1000 } });
    rerender({ interval: 100 });

    act(() => {
      vi.advanceTimersByTime(100);
    });
    expect(result.current).toBe(100);
  });

  it('unmountkor leállítja az időzítőt', () => {
    vi.useFakeTimers();
    const { unmount } = renderHook(() => useNow(500));
    expect(vi.getTimerCount()).toBe(1);

    unmount();
    expect(vi.getTimerCount()).toBe(0);
  });
});
