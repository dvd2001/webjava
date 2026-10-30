import { useState, type FormEvent } from 'react';
import type { BikeType } from '../types';
import { BIKE_TYPES, TYPE_LABELS } from '../labels';

interface Props {
  busy: boolean;
  onAdd: (name: string, type: BikeType) => Promise<boolean>;
}

function isBikeType(value: string): value is BikeType {
  return BIKE_TYPES.some((t) => t === value);
}

export function AddBikeForm({ busy, onAdd }: Props) {
  const [name, setName] = useState('');
  const [type, setType] = useState<BikeType>('city');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (await onAdd(name, type)) setName('');
  };

  return (
    <section className="card">
      <h2>Új bicikli felvétele</h2>
      <form onSubmit={handleSubmit} className="form">
        <label>
          Név / azonosító
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="pl. City-03" required minLength={2} />
        </label>
        <label>
          Típus
          <select
            value={type}
            onChange={(e) => {
              if (isBikeType(e.target.value)) setType(e.target.value);
            }}
          >
            {BIKE_TYPES.map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={busy}>
          Hozzáadás
        </button>
      </form>
    </section>
  );
}
