import { describe, expect, it } from 'vitest';
import { formatDateTime, formatDuration, formatFt } from './format';

describe('formatDuration', () => {
  it('nullát 00:00-ként jelenít meg', () => {
    expect(formatDuration(0)).toBe('00:00');
  });

  it('másodperceket nullával kitöltve jelenít meg', () => {
    expect(formatDuration(5)).toBe('00:05');
    expect(formatDuration(59)).toBe('00:59');
  });

  it('a percet és a másodpercet pp:mm formában adja vissza', () => {
    expect(formatDuration(60)).toBe('01:00');
    expect(formatDuration(125)).toBe('02:05');
    expect(formatDuration(3599)).toBe('59:59');
  });

  it('egy óra felett óra:perc:másodperc formátumot használ', () => {
    expect(formatDuration(3600)).toBe('1:00:00');
    expect(formatDuration(3661)).toBe('1:01:01');
    expect(formatDuration(36000 + 120 + 7)).toBe('10:02:07');
  });

  it('a negatív értéket nullára korlátozza', () => {
    expect(formatDuration(-1)).toBe('00:00');
    expect(formatDuration(-3600)).toBe('00:00');
  });

  it('a tört értéket lefelé kerekíti', () => {
    expect(formatDuration(59.9)).toBe('00:59');
    expect(formatDuration(0.999)).toBe('00:00');
    expect(formatDuration(60.5)).toBe('01:00');
    expect(formatDuration(-0.5)).toBe('00:00');
  });
});

describe('formatFt', () => {
  const normalize = (text: string) => text.replace(/\s/g, ' ');

  it('kis összeget elválasztó nélkül, Ft felirattal ír ki', () => {
    expect(formatFt(0)).toBe('0 Ft');
    expect(formatFt(300)).toBe('300 Ft');
  });

  it('az ezreseket magyar (szóköz jellegű) elválasztóval tagolja', () => {
    expect(normalize(formatFt(12345))).toBe('12 345 Ft');
    expect(normalize(formatFt(1234567))).toBe('1 234 567 Ft');
  });

  it('az ezres elválasztó nem sima szóköz, a Ft előtt viszont sima szóköz áll', () => {
    const text = formatFt(12345);
    expect(text).toMatch(/^12\s345 Ft$/);
    expect(text.charAt(2)).not.toBe(' ');
  });

  it('a negatív összeget mínuszjellel írja ki', () => {
    expect(formatFt(-500)).toMatch(/^[-\u2212]500 Ft$/);
  });
});

describe('formatDateTime', () => {
  it('a helyi időt magyar dátum- és időformátumban jeleníti meg', () => {
    const timestamp = new Date(2024, 2, 15, 12, 30, 5).getTime();
    expect(formatDateTime(timestamp)).toMatch(/^2024\.\s?03\.\s?15\.\s?12:30:05$/);
  });

  it('a nap elején és végén is helyesen formáz', () => {
    expect(formatDateTime(new Date(2025, 0, 1, 0, 0, 0).getTime())).toMatch(/^2025\.\s?01\.\s?01\.\s?0?0:00:00$/);
    expect(formatDateTime(new Date(2025, 11, 31, 23, 59, 59).getTime())).toMatch(
      /^2025\.\s?12\.\s?31\.\s?23:59:59$/,
    );
  });
});
