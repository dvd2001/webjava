import type { Bike, BikeStatus, Tariff, User } from '../types';
import { TYPE_LABELS } from '../labels';
import { formatFt } from '../pricing';
import { StatusBadge } from './StatusBadge';

interface Props {
  bikes: Bike[];
  tariffs: Record<Bike['type'], Tariff>;
  user: User;
  onRent: (bikeId: string) => void;
  onChangeStatus: (bikeId: string, status: BikeStatus) => void;
}

export function BikeList({ bikes, tariffs, user, onRent, onChangeStatus }: Props) {
  return (
    <section className="card">
      <h2>Biciklik</h2>
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
                <td>
                  {formatFt(tariff.baseFee)} + {formatFt(tariff.perMinute)}/perc
                </td>
                <td>
                  <StatusBadge status={bike.status} />
                </td>
                <td>
                  {user.role === 'renter' && (
                    <button disabled={bike.status !== 'available'} onClick={() => onRent(bike.id)}>
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
                        onChange={(e) => onChangeStatus(bike.id, e.target.value as BikeStatus)}
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
    </section>
  );
}
