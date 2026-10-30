import type { ChangeEvent } from 'react';
import { BIKE_TYPES, STATUS_LABELS, TYPE_LABELS } from '../labels';
import { isSearchActive, type BikeStatus, type BikeType, type SearchCriteria } from '../types';

const BIKE_STATUSES: BikeStatus[] = ['available', 'in_use', 'maintenance'];
const MAX_TEXT_LENGTH = 50;

interface Props {
  value: SearchCriteria;
  onChange: (value: SearchCriteria) => void;
  /** A találatok összegzése (pl. "2 találat"); keresés nélkül üres. */
  summary: string | null;
}

function toBikeType(value: string): BikeType | '' {
  return BIKE_TYPES.find((t) => t === value) ?? '';
}

function toBikeStatus(value: string): BikeStatus | '' {
  return BIKE_STATUSES.find((s) => s === value) ?? '';
}

export function BikeSearchForm({ value, onChange, summary }: Props) {
  const handleText = (e: ChangeEvent<HTMLInputElement>) => onChange({ ...value, q: e.target.value });

  return (
    <section className="card search" role="search" aria-label="Bicikli keresése">
      <h2>Keresés</h2>
      <div className="form">
        <label>
          Név részlete
          <input
            type="search"
            value={value.q}
            onChange={handleText}
            maxLength={MAX_TEXT_LENGTH}
            placeholder="pl. city"
            autoComplete="off"
          />
        </label>
        <label>
          Típus
          <select aria-label="Típus szűrő" value={value.type} onChange={(e) => onChange({ ...value, type: toBikeType(e.target.value) })}>
            <option value="">Minden típus</option>
            {BIKE_TYPES.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Státusz
          <select
            aria-label="Státusz szűrő"
            value={value.status}
            onChange={(e) => onChange({ ...value, status: toBikeStatus(e.target.value) })}
          >
            <option value="">Minden státusz</option>
            {BIKE_STATUSES.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABELS[s]}
              </option>
            ))}
          </select>
        </label>
        {isSearchActive(value) && (
          <button type="button" className="secondary" onClick={() => onChange({ q: '', type: '', status: '' })}>
            Szűrők törlése
          </button>
        )}
      </div>
      <p className="muted" aria-live="polite">
        {summary}
      </p>
    </section>
  );
}
