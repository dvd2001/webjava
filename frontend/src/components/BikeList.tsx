import type { Bike, BikeStatus, Tariffs, User } from '../types';
import { TYPE_LABELS } from '../labels';
import { formatFt } from '../format';
import { StatusBadge } from './StatusBadge';

interface Props {
  bikes: Bike[];
  tariffs: Tariffs;
  user: User;
  busy: boolean;
  /** Üres lista esetén mutatott szöveg (keresésnél más, mint az üres állomásnál). */
  emptyMessage?: string;
  onRent: (bike: Bike) => void;
  onChangeStatus: (bike: Bike, status: BikeStatus) => void;
}

export function BikeList({
  bikes,
  tariffs,
  user,
  busy,
  emptyMessage = 'Még nincs bicikli az állomáson.',
  onRent,
  onChangeStatus,
}: Props) {
  return (
    <section className="card">
      <h2>Biciklik</h2>
      {bikes.length === 0 ? (
        <p className="muted">{emptyMessage}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Név</th>
              <th>Típus</th>
              <th>Tarifa</th>
              <th>Státusz</th>
              <th>Művelet</th>
            </tr>
          </thead>
          <tbody>
            {bikes.map((bike) => {
              const tariff = tariffs[bike.type];
              return (
                <tr key={bike.id}>
                  <td>{bike.name}</td>
                  <td>{TYPE_LABELS[bike.type]}</td>
                  <td>{tariff ? `${formatFt(tariff.baseFee)} + ${formatFt(tariff.perMinute)}/perc` : '-'}</td>
                  <td>
                    <StatusBadge status={bike.status} />
                  </td>
                  <td>
                    {user.role === 'renter' && (
                      <button
                        disabled={busy || bike.status !== 'available'}
                        aria-label={`${bike.name} bérlése`}
                        onClick={() => onRent(bike)}
                      >
                        Bérlés
                      </button>
                    )}
                    {user.role === 'manager' &&
                      (bike.status === 'in_use' ? (
                        <span className="muted">Visszavételig nem módosítható</span>
                      ) : (
                        <select
                          aria-label={`${bike.name} státusza`}
                          value={bike.status}
                          disabled={busy}
                          onChange={(e) => {
                            const status = e.target.value;
                            if (status === 'available' || status === 'maintenance') onChangeStatus(bike, status);
                          }}
                        >
                          <option value="available">Elérhető</option>
                          <option value="maintenance">Karbantartás alatt</option>
                        </select>
                      ))}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
