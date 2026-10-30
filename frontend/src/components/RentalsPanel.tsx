import type { Bike, Rental, User } from '../types';
import { formatDateTime, formatDuration, formatFt } from '../format';

interface Props {
  rentals: Rental[];
  bikes: Bike[];
  users: User[];
  user: User;
  busy: boolean;
  /** Mikor érkezett a bérlések listája; az eltelt idő a szerver által küldött értékből számolódik tovább. */
  fetchedAt: number;
  now: number;
  onReturn: (rental: Rental) => void;
}

export function RentalsPanel({ rentals, bikes, users, user, busy, fetchedAt, now, onReturn }: Props) {
  const bikeName = (id: string) => bikes.find((b) => b.id === id)?.name ?? id;
  const userName = (id: string) => users.find((u) => u.id === id)?.name ?? id;
  const active = rentals.filter((r) => r.endedAt === undefined);
  const finished = rentals.filter((r) => r.endedAt !== undefined).slice().reverse();
  const isManager = user.role === 'manager';

  return (
    <section className="card">
      <h2>{isManager ? 'Bérlések' : 'Saját bérléseim'}</h2>

      <h3>Aktív</h3>
      {active.length === 0 ? (
        <p className="muted">Nincs aktív bérlés.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Bicikli</th>
              {isManager && <th>Bérlő</th>}
              <th>Kezdés</th>
              <th>Eltelt idő</th>
              <th>Eddigi díj</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {active.map((r) => (
              <tr key={r.id}>
                <td>{bikeName(r.bikeId)}</td>
                {isManager && <td>{userName(r.userId)}</td>}
                <td>{formatDateTime(r.startedAt)}</td>
                <td>{formatDuration(r.elapsedSeconds + (now - fetchedAt) / 1000)}</td>
                <td>{formatFt(r.currentFee)}</td>
                <td>
                  <button disabled={busy} aria-label={`${bikeName(r.bikeId)} visszavétele`} onClick={() => onReturn(r)}>
                    Visszavétel
                  </button>
                </td>
              </tr>
            ))}
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
              {isManager && <th>Bérlő</th>}
              <th>Kezdés</th>
              <th>Időtartam</th>
              <th>Fizetendő</th>
            </tr>
          </thead>
          <tbody>
            {finished.map((r) => (
              <tr key={r.id}>
                <td>{bikeName(r.bikeId)}</td>
                {isManager && <td>{userName(r.userId)}</td>}
                <td>{formatDateTime(r.startedAt)}</td>
                <td>{formatDuration(r.elapsedSeconds)}</td>
                <td>{formatFt(r.fee ?? r.currentFee)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
