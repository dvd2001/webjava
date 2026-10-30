export function formatDuration(totalSecondsInput: number): string {
  const totalSeconds = Math.max(0, Math.floor(totalSecondsInput));
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  const pad = (n: number) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

export function formatFt(amount: number): string {
  return `${amount.toLocaleString('hu-HU')} Ft`;
}

export function formatDateTime(timestamp: number): string {
  return new Date(timestamp).toLocaleString('hu-HU');
}
