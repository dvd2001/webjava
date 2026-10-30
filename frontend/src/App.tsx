import { useCallback, useEffect, useRef, useState } from 'react';
import { api, ApiError, setAuthToken, setUnauthorizedHandler } from './api/client';
import { DEFAULT_DEMO_USERNAME, DEMO_PASSWORD, DEMO_USERS } from './demoUsers';
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

interface Session {
  user: User;
  /** A token lejárata (epoch ezredmásodperc); lejárat előtt csendben megújul. */
  expiresAt: number;
}

interface Snapshot {
  bikes: Bike[];
  rentals: Rental[];
  fetchedAt: number;
}

const POLL_INTERVAL_MS = 5000;
const RENEW_BEFORE_EXPIRY_MS = 60_000;
const SESSION_RENEWED = 'A munkamenet lejárt, újra bejelentkeztünk. Próbáld újra a műveletet.';

function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Váratlan hiba történt.';
}

export default function App() {
  const [activeUsername, setActiveUsername] = useState(DEFAULT_DEMO_USERNAME);
  const [session, setSession] = useState<Session | null>(null);
  const [authError, setAuthError] = useState<string | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [tariffs, setTariffs] = useState<Tariffs>({});
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [busy, setBusy] = useState(false);
  const now = useNow();

  const activeUsernameRef = useRef(activeUsername);
  const signInTicket = useRef(0);
  const signingIn = useRef(false);
  // Minden felhasználó- vagy tokenváltáskor nő; a régebbi kor alatt indult lekérések válasza elavult, eldobjuk
  const dataEpoch = useRef(0);
  // Csak felhasználóváltáskor nő (a tokenmegújítás nem); a régi felhasználó műveletének eredménye nem szivároghat át
  const userEpoch = useRef(0);

  // A demo felhasználó csendes bejelentkezése; a régebbi, később érkező válasz nem írhat felül újabbat
  const signIn = useCallback(async (username: string) => {
    const ticket = ++signInTicket.current;
    signingIn.current = true;
    try {
      const response = await api.login(username, DEMO_PASSWORD);
      if (ticket !== signInTicket.current) return;
      dataEpoch.current++;
      setAuthToken(response.accessToken);
      setSession({ user: response.user, expiresAt: response.expiresAt });
      setAuthError(null);
    } catch (error) {
      if (ticket !== signInTicket.current) return;
      setAuthToken(null);
      setSession(null);
      setAuthError(messageOf(error));
    } finally {
      if (ticket === signInTicket.current) signingIn.current = false;
    }
  }, []);

  const switchUser = (username: string) => {
    activeUsernameRef.current = username;
    setActiveUsername(username);
  };

  // Felhasználóváltáskor az előző felhasználó adatai és tokenje nem maradhat meg
  useEffect(() => {
    dataEpoch.current++;
    userEpoch.current++;
    setBusy(false);
    setAuthToken(null);
    setSession(null);
    setSnapshot(null);
    setUsers([]);
    setLoadError(null);
    setNotice(null);
    void signIn(activeUsername);
  }, [activeUsername, signIn]);

  // Hitelesített kérésre kapott 401 (lejárt vagy érvénytelen token): csendes újra-bejelentkezés
  useEffect(() => {
    setUnauthorizedHandler(() => {
      if (signingIn.current) return;
      setNotice({ ok: false, text: SESSION_RENEWED });
      void signIn(activeUsernameRef.current);
    });
    return () => setUnauthorizedHandler(null);
  }, [signIn]);

  // A token lejárta előtt csendben megújítjuk
  useEffect(() => {
    if (!session) return;
    const delay = Math.max(0, session.expiresAt - Date.now() - RENEW_BEFORE_EXPIRY_MS);
    const timeout = setTimeout(() => void signIn(activeUsernameRef.current), delay);
    return () => clearTimeout(timeout);
  }, [session, signIn]);

  const loadReferenceData = useCallback(async (current: Session) => {
    const epoch = dataEpoch.current;
    try {
      const [loadedTariffs, loadedUsers] = await Promise.all([
        api.listTariffs(),
        current.user.role === 'manager' ? api.listUsers() : Promise.resolve([current.user]),
      ]);
      if (epoch !== dataEpoch.current) return;
      setTariffs(loadedTariffs);
      setUsers(loadedUsers);
    } catch (error) {
      if (epoch === dataEpoch.current) setLoadError(messageOf(error));
    }
  }, []);

  const refresh = useCallback(async () => {
    if (!session) return;
    const epoch = dataEpoch.current;
    try {
      const [bikes, rentals] = await Promise.all([api.listBikes(), api.listRentals()]);
      if (epoch !== dataEpoch.current) return;
      setSnapshot({ bikes, rentals, fetchedAt: Date.now() });
      setLoadError(null);
    } catch (error) {
      if (epoch === dataEpoch.current) setLoadError(messageOf(error));
    }
  }, [session]);

  useEffect(() => {
    if (!session) return;
    void loadReferenceData(session);
    void refresh();
  }, [session, loadReferenceData, refresh]);

  // A bérlések díja a szerveren számolódik, ezért időnként frissítjük
  useEffect(() => {
    if (!session) return;
    const id = setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => clearInterval(id);
  }, [session, refresh]);

  const act = async (action: () => Promise<string>): Promise<boolean> => {
    const epoch = userEpoch.current;
    const sameUser = () => epoch === userEpoch.current;
    setBusy(true);
    let ok = false;
    try {
      const message = await action();
      if (!sameUser()) return false;
      setNotice({ ok: true, text: message });
      ok = true;
    } catch (error) {
      if (!sameUser()) return false;
      if (error instanceof ApiError && error.status === 401) {
        // a kliens már újra bejelentkezett, a megújulás üzenetét nem írjuk felül
        setBusy(false);
        return false;
      }
      setNotice({ ok: false, text: messageOf(error) });
    }
    try {
      await refresh();
    } finally {
      if (sameUser()) setBusy(false);
    }
    return ok;
  };

  const switcher = <UserSwitcher users={DEMO_USERS} currentUsername={activeUsername} onChange={switchUser} />;

  if (!session || !snapshot) {
    return (
      <div className="app">
        <header>
          <h1>🚲 Kerékpárkölcsönző állomás</h1>
          {switcher}
        </header>
        {authError || loadError ? (
          <div className="notice notice-error" role="alert">
            {authError ?? loadError}{' '}
            <button
              onClick={() => {
                if (session) {
                  void loadReferenceData(session);
                  void refresh();
                } else {
                  void signIn(activeUsername);
                }
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

  const user = session.user;

  const rent = (bike: Bike) =>
    act(async () => {
      await api.createRental(bike.id);
      return `${bike.name} kibérelve.`;
    });

  const giveBack = (rental: Rental) =>
    act(async () => {
      const finished = await api.returnRental(rental.id);
      const name = snapshot.bikes.find((b) => b.id === rental.bikeId)?.name ?? rental.bikeId;
      return `${name} visszavéve. Fizetendő: ${formatFt(finished.fee ?? finished.currentFee)}.`;
    });

  const changeStatus = (bike: Bike, status: BikeStatus) =>
    act(async () => {
      await api.changeBikeStatus(bike.id, status);
      return `${bike.name} állapota módosítva.`;
    });

  const addBike = (name: string, type: BikeType) =>
    act(async () => {
      const created = await api.createBike(name, type);
      return `${created.name} felvéve.`;
    });

  return (
    <div className="app">
      <header>
        <h1>🚲 Kerékpárkölcsönző állomás</h1>
        {switcher}
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
