import { useState, type FormEvent } from 'react';
import type { BikeType } from '../types';
import { TYPE_LABELS } from '../labels';

interface Props {
  onAdd: (name: string, type: BikeType) => boolean;
}

export function AddBikeForm({ onAdd }: Props) {
  const [name, setName] = useState('');
  const [type, setType] = useState<BikeType>('city');

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (onAdd(name, type)) setName('');
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
          <select value={type} onChange={(e) => setType(e.target.value as BikeType)}>
            {(Object.keys(TYPE_LABELS) as BikeType[]).map((t) => (
              <option key={t} value={t}>
                {TYPE_LABELS[t]}
              </option>
            ))}
          </select>
        </label>
        <button type="submit">Hozzáadás</button>
      </form>
    </section>
  );
}
