import type { BikeStatus } from '../types';
import { STATUS_LABELS } from '../labels';

export function StatusBadge({ status }: { status: BikeStatus }) {
  return <span className={`badge badge-${status}`}>{STATUS_LABELS[status]}</span>;
}
