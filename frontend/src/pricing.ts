import type { Tariff } from './types';

const MS_PER_MINUTE = 60_000;

/** Megkezdett percek száma, legalább 1. */
export function billableMinutes(startedAt: number, endedAt: number): number {
  const elapsed = Math.max(0, endedAt - startedAt);
  return Math.max(1, Math.ceil(elapsed / MS_PER_MINUTE));
}

export function calculateFee(tariff: Tariff, startedAt: number, endedAt: number): number {
  return tariff.baseFee + billableMinutes(startedAt, endedAt) * tariff.perMinute;
}

export function formatDuration(startedAt: number, endedAt: number): string {
  const totalSeconds = Math.max(0, Math.floor((endedAt - startedAt) / 1000));
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

export function formatFt(amount: number): string {
  return `${amount.toLocaleString('hu-HU')} Ft`;
}

export function formatDateTime(ts: number): string {
  return new Date(ts).toLocaleString('hu-HU');
}
