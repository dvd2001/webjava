import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from './api/client';
import { BIKE_TYPES, TYPE_LABELS } from './labels';
import { formatFt } from './format';
import { useNow } from './useNow';
import type { Bike, BikeStatus, BikeType, Rental, Tariffs, User } from './types';
import { UserSwitcher } from './components/UserSwitcher';
import { BikeList } from './components/BikeList';
import { AddBikeForm } from './components/AddBikeForm';
import { RentalsPanel } from './components/RentalsPanel';

interface Notice {
  ok: boolean;
  text: string;
}

interface Snapshot {
  bikes: Bike[];
  rentals: Rental[];
  fetchedAt: number;
}

const POLL_INTERVAL_MS = 5000;

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Váratlan hiba történt.';
}

export default function App() {
  const [users, setUsers] = useState<User[]>([]);
  const [tariffs, setTariffs] = useState<Tariffs>({});
  const [userId, setUserId] = useState<string | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const now = useNow();

  const loadReferenceData = useCallback(async () => {
    try {
      const [loadedUsers, loadedTariffs] = await Promise.all([api.listUsers(), api.listTariffs()]);
      setUsers(loadedUsers);
      setTariffs(loadedTariffs);
      setUserId((current) => current ?? (loadedUsers.find((u) => u.role === 'renter') ?? loadedUsers[0])?.id ?? null);
      setLoadError(null);
    } catch (error) {
      setLoadError(messageOf(error));
    }
  }, []);

  useEffect(() => {
    void loadReferenceData();
  }, [loadReferenceData]);

  const refresh = useCallback(async () => {
    if (userId === null) return;
    try {
      const [bikes, rentals] = await Promise.all([api.listBikes(), api.listRentals(userId)]);
      setSnapshot({ bikes, rentals, fetchedAt: Date.now() });
      setLoadError(null);
    } catch (error) {
      setLoadError(messageOf(error));
    }
  }, [userId]);

  // A felhasználóváltáskor a korábbi felhasználó adatai nem maradhatnak látszani
  useEffect(() => {
    setSnapshot(null);
    void refresh();
  }, [refresh]);

  // A bérlések díja a szerveren számolódik, ezért időnként frissítjük
  useEffect(() => {
    const id = setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [refresh]);

  const act = async (action: () => Promise<string>): Promise<boolean> => {
    setBusy(true);
    let ok = false;
    try {
      setNotice({ ok: true, text: await action() });
      ok = true;
    } catch (error) {
      setNotice({ ok: false, text: messageOf(error) });
    }
    try {
      await refresh();
    } finally {
      setBusy(false);
    }
    return ok;
  };

  const user = users.find((u) => u.id === userId);

  if (!user || !snapshot) {
    return (
      <div className="app">
        <h1>🚲 Kerékpárkölcsönző állomás</h1>
        {loadError ? (
          <div className="notice notice-error" role="alert">
            {loadError}{' '}
            <button
              onClick={() => {
                void loadReferenceData();
                void refresh();
              }}
            >
              Újrapróbálás
            </button>
          </div>
        ) : (
          <p className="muted" role="status">
            Betöltés…
          </p>
        )}
      </div>
    );
  }

  const rent = (bike: Bike) =>
    act(async () => {
      await api.createRental(user.id, bike.id);
      return `${bike.name} kibérelve.`;
    });

  const giveBack = (rental: Rental) =>
    act(async () => {
      const finished = await api.returnRental(user.id, rental.id);
      const name = snapshot.bikes.find((b) => b.id === rental.bikeId)?.name ?? rental.bikeId;
      return `${name} visszavéve. Fizetendő: ${formatFt(finished.fee ?? finished.currentFee)}.`;
    });

  const changeStatus = (bike: Bike, status: BikeStatus) =>
    act(async () => {
      await api.changeBikeStatus(user.id, bike.id, status);
      return `${bike.name} állapota módosítva.`;
    });

  const addBike = (name: string, type: BikeType) =>
    act(async () => {
      const created = await api.createBike(user.id, name, type);
      return `${created.name} felvéve.`;
    });

  return (
    <div className="app">
      <header>
        <h1>🚲 Kerékpárkölcsönző állomás</h1>
        <UserSwitcher
          users={users}
          currentUserId={user.id}
          onChange={(id) => {
            setUserId(id);
            setNotice(null);
          }}
        />
      </header>

      {loadError && (
        <div className="notice notice-error" role="alert">
          A szerverrel nem sikerült frissíteni az adatokat: {loadError}
        </div>
      )}
      {notice && (
        <div className={`notice ${notice.ok ? 'notice-ok' : 'notice-error'}`} role={notice.ok ? 'status' : 'alert'}>
          {notice.text}
        </div>
      )}

      <section className="card">
        <h2>Tarifák</h2>
        <ul className="tariffs">
          {BIKE_TYPES.map((type) => {
            const tariff = tariffs[type];
            return tariff ? (
              <li key={type}>
                <strong>{TYPE_LABELS[type]}:</strong> {formatFt(tariff.baseFee)} kezdődíj + {formatFt(tariff.perMinute)}
                /megkezdett perc
              </li>
            ) : null;
          })}
        </ul>
      </section>

      <BikeList
        bikes={snapshot.bikes}
        tariffs={tariffs}
        user={user}
        busy={busy}
        onRent={(bike) => void rent(bike)}
        onChangeStatus={(bike, status) => void changeStatus(bike, status)}
      />

      {user.role === 'manager' && <AddBikeForm busy={busy} onAdd={addBike} />}

      <RentalsPanel
        rentals={snapshot.rentals}
        bikes={snapshot.bikes}
        users={users}
        user={user}
        busy={busy}
        fetchedAt={snapshot.fetchedAt}
        now={now}
        onReturn={(rental) => void giveBack(rental)}
      />
    </div>
  );
}
