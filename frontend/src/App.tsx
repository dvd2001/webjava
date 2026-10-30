import { useState } from 'react';
import { USERS, createInitialState } from './data';
import { addBike, changeStatus, rentBike, returnBike, type Result } from './store';
import { useNow } from './useNow';
import type { AppState, BikeStatus, BikeType } from './types';
import { TYPE_LABELS } from './labels';
import { formatFt } from './pricing';
import { UserSwitcher } from './components/UserSwitcher';
import { BikeList } from './components/BikeList';
import { AddBikeForm } from './components/AddBikeForm';
import { RentalsPanel } from './components/RentalsPanel';

interface Notice {
  ok: boolean;
  text: string;
}

export default function App() {
  const [state, setState] = useState<AppState>(() => createInitialState());
  const [userId, setUserId] = useState(USERS[1].id);
  const [notice, setNotice] = useState<Notice | null>(null);
  const now = useNow();

  const user = USERS.find((u) => u.id === userId)!;

  const apply = (result: Result): boolean => {
    if (result.ok) setState(result.state);
    setNotice({ ok: result.ok, text: result.message });
    return result.ok;
  };

  return (
    <div className="app">
      <header>
        <h1>🚲 Kerékpárkölcsönző állomás</h1>
        <UserSwitcher
          currentUserId={userId}
          onChange={(id) => {
            setUserId(id);
            setNotice(null);
          }}
        />
      </header>

      {notice && (
        <div className={`notice ${notice.ok ? 'notice-ok' : 'notice-error'}`} role="status">
          {notice.text}
        </div>
      )}

      <section className="card">
        <h2>Tarifák</h2>
        <ul className="tariffs">
          {(Object.keys(state.tariffs) as BikeType[]).map((t) => (
            <li key={t}>
              <strong>{TYPE_LABELS[t]}:</strong> {formatFt(state.tariffs[t].baseFee)} kezdődíj +{' '}
              {formatFt(state.tariffs[t].perMinute)}/megkezdett perc
            </li>
          ))}
        </ul>
      </section>

      <BikeList
        bikes={state.bikes}
        tariffs={state.tariffs}
        user={user}
        onRent={(id) => apply(rentBike(state, user, id, Date.now()))}
        onChangeStatus={(id, status: BikeStatus) => apply(changeStatus(state, user, id, status))}
      />

      {user.role === 'manager' && (
        <AddBikeForm onAdd={(name, type) => apply(addBike(state, user, name, type))} />
      )}

      <RentalsPanel
        rentals={state.rentals}
        bikes={state.bikes}
        tariffs={state.tariffs}
        user={user}
        now={now}
        onReturn={(id) => apply(returnBike(state, user, id, Date.now()))}
      />

      <footer>
        <button className="secondary" onClick={() => { setState(createInitialState()); setNotice(null); }}>
          Példaadatok visszaállítása
        </button>
      </footer>
    </div>
  );
}
