import type { Bike, Rental, Tariff, User } from '../types';
import { calculateFee, formatDateTime, formatDuration, formatFt } from '../pricing';
import { USERS } from '../data';

interface Props {
  rentals: Rental[];
  bikes: Bike[];
  tariffs: Record<Bike['type'], Tariff>;
  user: User;
  now: number;
  onReturn: (rentalId: string) => void;
}

export function RentalsPanel({ rentals, bikes, tariffs, user, now, onReturn }: Props) {
  const bikeOf = (id: string) => bikes.find((b) => b.id === id);
  const userName = (id: string) => USERS.find((u) => u.id === id)?.name ?? id;
  const visible = user.role === 'manager' ? rentals : rentals.filter((r) => r.userId === user.id);
  const active = visible.filter((r) => r.endedAt === undefined);
  const finished = visible.filter((r) => r.endedAt !== undefined).slice().reverse();

  return (
    <section className="card">
      <h2>{user.role === 'manager' ? 'Bérlések' : 'Saját bérléseim'}</h2>

      <h3>Aktív</h3>
      {active.length === 0 ? (
        <p className="muted">Nincs aktív bérlés.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Bicikli</th>
              {user.role === 'manager' && <th>Bérlő</th>}
              <th>Kezdés</th>
              <th>Eltelt idő</th>
              <th>Eddigi díj</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {active.map((r) => {
              const bike = bikeOf(r.bikeId);
              return (
                <tr key={r.id}>
                  <td>{bike?.name}</td>
                  {user.role === 'manager' && <td>{userName(r.userId)}</td>}
                  <td>{formatDateTime(r.startedAt)}</td>
                  <td>{formatDuration(r.startedAt, now)}</td>
                  <td>{bike ? formatFt(calculateFee(tariffs[bike.type], r.startedAt, now)) : '-'}</td>
                  <td>
                    <button onClick={() => onReturn(r.id)}>Visszavétel</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      <h3>Lezárt</h3>
      {finished.length === 0 ? (
        <p className="muted">Nincs lezárt bérlés.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Bicikli</th>
              {user.role === 'manager' && <th>Bérlő</th>}
              <th>Kezdés</th>
              <th>Időtartam</th>
              <th>Fizetendő</th>
            </tr>
          </thead>
          <tbody>
            {finished.map((r) => (
              <tr key={r.id}>
                <td>{bikeOf(r.bikeId)?.name}</td>
                {user.role === 'manager' && <td>{userName(r.userId)}</td>}
                <td>{formatDateTime(r.startedAt)}</td>
                <td>{formatDuration(r.startedAt, r.endedAt!)}</td>
                <td>{formatFt(r.fee ?? 0)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
