import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import { setAuthToken, setUnauthorizedHandler } from './api/client';
import { DEMO_PASSWORD } from './demoUsers';
import { createFakeServer, type FakeServer } from './test/fakeServer';

const SESSION_RENEWED = 'A munkamenet lejárt, újra bejelentkeztünk. Próbáld újra a műveletet.';
const LOGIN_PATH = '/api/auth/login';

let server: FakeServer;

function startServer(options?: Parameters<typeof createFakeServer>[0]) {
  server = createFakeServer(options);
  vi.stubGlobal('fetch', server.fetch);
}

function realClock() {
  return userEvent.setup();
}

function fakeClock() {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  return userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

type User = ReturnType<typeof userEvent.setup>;

const switcher = () => screen.getByRole('combobox', { name: 'Aktív felhasználó:' });

async function waitForLists() {
  await screen.findByRole('heading', { name: 'Biciklik' });
}

async function startApp(user: User) {
  render(<App />);
  await waitForLists();
  return user;
}

async function switchTo(user: User, username: string) {
  await user.selectOptions(switcher(), username);
}

async function switchAndWait(user: User, username: string) {
  const issued = server.tokensOf(username).length;
  await switchTo(user, username);
  await waitFor(() => expect(server.tokensOf(username).length).toBeGreaterThan(issued));
  await waitForLists();
}

function bikeRow(name: RegExp | string) {
  const section = screen.getByRole('heading', { name: 'Biciklik' }).closest('section');
  if (!section) throw new Error('Hiányzik a biciklilista szakasz');
  return within(section).getByRole('row', { name });
}

function field(body: unknown, key: string): unknown {
  return typeof body === 'object' && body !== null ? Reflect.get(body, key) : undefined;
}

function loginRequests() {
  return server.requests.filter((r) => r.method === 'POST' && r.path === LOGIN_PATH);
}

function loggedInUsernames() {
  return loginRequests().map((r) => field(r.body, 'username'));
}

function requestsAfterLastLogin() {
  const index = server.requests.map((r) => r.path).lastIndexOf(LOGIN_PATH);
  return server.requests.slice(index + 1);
}

function lastRentalPost() {
  return server.requests.filter((r) => r.method === 'POST' && r.path === '/api/rentals').at(-1);
}

function tooManyRequests() {
  return new Response(JSON.stringify({ code: 'TOO_MANY_REQUESTS', message: 'Túl sok bejelentkezési kísérlet.' }), {
    status: 429,
  });
}

beforeEach(() => {
  startServer();
});

afterEach(() => {
  vi.useRealTimers();
  setAuthToken(null);
  setUnauthorizedHandler(null);
  vi.unstubAllGlobals();
});

describe('App – automatikus bejelentkezés induláskor', () => {
  it('induláskor Betöltés… állapotot mutat, a váltón a péter van kiválasztva, majd betölt', async () => {
    render(<App />);

    expect(screen.getByRole('status')).toHaveTextContent('Betöltés…');
    expect(switcher()).toHaveValue('peter');
    expect(screen.getByRole('heading', { level: 1, name: /Kerékpárkölcsönző állomás/ })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Biciklik' })).not.toBeInTheDocument();

    await waitForLists();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('a login kérés a DEMO_PASSWORD-del megy token nélkül, a többi kérés Bearer fejlécet küld', async () => {
    await startApp(realClock());

    const [login, ...others] = server.requests;
    expect(login).toMatchObject({ method: 'POST', path: LOGIN_PATH, authorization: null });
    expect(login.body).toEqual({ username: 'peter', password: DEMO_PASSWORD });
    expect(loginRequests()).toHaveLength(1);

    expect(others.map((r) => r.path).sort()).toEqual(['/api/bikes', '/api/rentals', '/api/tariffs']);
    for (const request of others) expect(request.authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
    expect(server.countRequests('GET', '/api/users')).toBe(0);
  });

  it('bérlőként betölti a tarifákat, a biciklilistát és a saját bérléseket', async () => {
    await startApp(realClock());

    expect(screen.getByRole('heading', { name: 'Tarifák' })).toBeInTheDocument();
    expect(screen.getByText(/Városi:/).closest('li')).toHaveTextContent('300 Ft kezdődíj + 15 Ft/megkezdett perc');
    expect(bikeRow(/City-01/)).toHaveTextContent('Elérhető');
    expect(bikeRow(/Hegyi-02/)).toHaveTextContent('Karbantartás alatt');
    expect(bikeRow(/Villám-03/)).toHaveTextContent('Használatban');

    const rentalsSection = screen.getByRole('heading', { name: 'Saját bérléseim' }).closest('section');
    if (!rentalsSection) throw new Error('Hiányzik a bérléseket tartalmazó szakasz');
    const rentals = within(rentalsSection);
    expect(rentals.getByRole('row', { name: /Hegyi-02/ })).toHaveTextContent('600 Ft');
    expect(rentals.queryByText('Villám-03')).not.toBeInTheDocument();
    expect(rentals.queryByRole('columnheader', { name: 'Bérlő' })).not.toBeInTheDocument();
    expect(screen.getByText('Nincs aktív bérlés.')).toBeInTheDocument();
  });

  it('bérlőnek nincs új bicikli űrlapja és nincs státuszválasztója, csak a felhasználóváltó', async () => {
    await startApp(realClock());

    expect(screen.queryByRole('heading', { name: 'Új bicikli felvétele' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Hozzáadás' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('combobox')).toEqual([switcher(), screen.getByRole('combobox', { name: 'Típus szűrő' }), screen.getByRole('combobox', { name: 'Státusz szűrő' })]);
  });

  it('csak az elérhető biciklinél engedélyezi a Bérlés gombot', async () => {
    await startApp(realClock());

    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Hegyi-02 bérlése' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Villám-03 bérlése' })).toBeDisabled();
  });
});

describe('App – bérlő műveletei', () => {
  it('bérléskor sikerüzenetet (role=status) mutat, újratölti az adatokat, és aktív bérlést jelenít meg', async () => {
    const user = await startApp(realClock());
    const bikeLoads = server.countRequests('GET', '/api/bikes');
    const rentalLoads = server.countRequests('GET', '/api/rentals');

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));

    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
    expect(await screen.findByRole('button', { name: 'City-01 visszavétele' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeDisabled();
    expect(bikeRow(/City-01/)).toHaveTextContent('Használatban');
    expect(screen.queryByText('Nincs aktív bérlés.')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    const post = lastRentalPost();
    expect(post?.body).toEqual({ bikeId: 'b1' });
    expect(post?.authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
    expect(server.countRequests('GET', '/api/bikes')).toBeGreaterThan(bikeLoads);
    expect(server.countRequests('GET', '/api/rentals')).toBeGreaterThan(rentalLoads);
  });

  it('ütköző bérlésnél (409) hibaüzenetet mutat role=alert elemben, és frissíti a listát', async () => {
    const user = await startApp(realClock());
    const stale = server.state.bikes.find((b) => b.id === 'b1');
    if (!stale) throw new Error('Hiányzó tesztbicikli');
    stale.status = 'in_use';

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ez a bicikli jelenleg nem bérelhető.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeDisabled());
    expect(bikeRow(/City-01/)).toHaveTextContent('Használatban');
    expect(loginRequests()).toHaveLength(1);
  });

  it('visszavételkor megjeleníti a fizetendő összeget, és az aktív bérlés lezárttá válik', async () => {
    const user = await startApp(realClock());
    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await user.click(await screen.findByRole('button', { name: 'City-01 visszavétele' }));

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('City-01 visszavéve. Fizetendő: 315 Ft.'),
    );
    await waitFor(() => expect(screen.getByText('Nincs aktív bérlés.')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'City-01 visszavétele' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled();
    expect(screen.getByRole('row', { name: /City-01.*01:00.*315 Ft/ })).toBeInTheDocument();
    const post = server.requests.find((r) => r.method === 'POST' && r.path === '/api/rentals/r10/return');
    expect(post?.authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
  });
});

describe('App – váltás állomásvezetőre', () => {
  it('Betöltés… állapotot mutat és törli az előző adatokat, amíg az új bejelentkezés tart', async () => {
    const user = await startApp(realClock());
    const release = server.holdNext('POST', LOGIN_PATH);

    await switchTo(user, 'anna');

    expect(screen.getByRole('status')).toHaveTextContent('Betöltés…');
    expect(switcher()).toHaveValue('anna');
    expect(screen.queryByRole('heading', { name: 'Biciklik' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'City-01 bérlése' })).not.toBeInTheDocument();

    release();
    await waitForLists();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('új, anna nevű bejelentkezést indít a DEMO_PASSWORD-del, és az új tokent használja', async () => {
    const user = await startApp(realClock());

    await switchAndWait(user, 'anna');

    const logins = loginRequests();
    expect(logins).toHaveLength(2);
    expect(logins[1].body).toEqual({ username: 'anna', password: DEMO_PASSWORD });
    expect(logins[1].authorization).toBeNull();
    const afterSwitch = requestsAfterLastLogin();
    expect(afterSwitch.length).toBeGreaterThan(0);
    for (const request of afterSwitch) expect(request.authorization).toBe(`Bearer ${server.tokenFor('anna')}`);
    expect(server.tokenFor('anna')).not.toBe(server.tokenFor('peter'));
  });

  it('megjelenik az új bicikli űrlap, a bérlő neve, és eltűnik a Bérlés gomb', async () => {
    const user = await startApp(realClock());
    await switchAndWait(user, 'anna');

    expect(screen.getByRole('heading', { name: 'Új bicikli felvétele' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Bérlések' })).toBeInTheDocument();
    expect(server.countRequests('GET', '/api/users')).toBe(1);
    expect(screen.queryByRole('button', { name: /bérlése$/ })).not.toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Villám-03.*Szabó Eszter/ })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Hegyi-02.*Nagy Péter/ })).toBeInTheDocument();
    expect(screen.getByText('Visszavételig nem módosítható')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'City-01 státusza' })).toHaveValue('available');
  });

  it('az új bicikli felvétele sikerüzenetet ad, megjelenik a listában, és kiürül a név mező', async () => {
    const user = await startApp(realClock());
    await switchAndWait(user, 'anna');

    await user.type(screen.getByLabelText('Név / azonosító'), 'Hegyi-09');
    const addForm = screen.getByRole('heading', { name: 'Új bicikli felvétele' }).closest('section');
    if (!addForm) throw new Error('Hiányzik az új bicikli űrlap');
    await user.selectOptions(within(addForm).getByLabelText('Típus'), 'mountain');
    await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Hegyi-09 felvéve.');
    expect(await screen.findByRole('combobox', { name: 'Hegyi-09 státusza' })).toHaveValue('available');
    await waitFor(() => expect(screen.getByLabelText('Név / azonosító')).toHaveValue(''));
    const post = server.requests.find((r) => r.method === 'POST' && r.path === '/api/bikes');
    expect(post?.body).toEqual({ name: 'Hegyi-09', type: 'mountain' });
    expect(post?.authorization).toBe(`Bearer ${server.tokenFor('anna')}`);
  });

  it('már létező bicikli nevénél hibaüzenetet (role=alert) mutat, és a név megmarad', async () => {
    const user = await startApp(realClock());
    await switchAndWait(user, 'anna');

    await user.type(screen.getByLabelText('Név / azonosító'), 'city-01');
    await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ilyen nevű bicikli már létezik.');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Hozzáadás' })).toBeEnabled());
    expect(screen.getByLabelText('Név / azonosító')).toHaveValue('city-01');
  });

  it('a státuszváltás elküldi az új státuszt, sikerüzenetet mutat, és frissíti a listát', async () => {
    const user = await startApp(realClock());
    await switchAndWait(user, 'anna');

    await user.selectOptions(screen.getByRole('combobox', { name: 'City-01 státusza' }), 'maintenance');

    expect(await screen.findByRole('status')).toHaveTextContent('City-01 állapota módosítva.');
    await waitFor(() => expect(bikeRow(/City-01/)).toHaveTextContent('Karbantartás alatt'));
    expect(screen.getByRole('combobox', { name: 'City-01 státusza' })).toHaveValue('maintenance');
    const patch = server.requests.find((r) => r.method === 'PATCH' && r.path === '/api/bikes/b1/status');
    expect(patch?.body).toEqual({ status: 'maintenance' });
    expect(patch?.authorization).toBe(`Bearer ${server.tokenFor('anna')}`);
    expect(server.state.bikes.find((b) => b.id === 'b1')?.status).toBe('maintenance');
  });

  it('a bérlő bérlése megjelenik az állomásvezetőnek a bérlő nevével', async () => {
    const user = await startApp(realClock());
    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await screen.findByRole('button', { name: 'City-01 visszavétele' });

    await switchAndWait(user, 'anna');

    expect(await screen.findByRole('row', { name: /City-01.*Nagy Péter.*00:0\d/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'City-01 visszavétele' })).toBeEnabled();
  });
});

describe('App – váltás vissza bérlőre', () => {
  it('az előző felhasználó adata nem látszik, az űrlap eltűnik, és a kérések az új tokent használják', async () => {
    const user = await startApp(realClock());
    await switchAndWait(user, 'anna');
    expect(screen.getByRole('heading', { name: 'Új bicikli felvétele' })).toBeInTheDocument();

    await switchAndWait(user, 'eszter');

    expect(switcher()).toHaveValue('eszter');
    expect(screen.queryByRole('heading', { name: 'Új bicikli felvétele' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'City-01 státusza' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Saját bérléseim' })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Bérlő' })).not.toBeInTheDocument();
    expect(screen.queryByRole('row', { name: /Hegyi-02.*600 Ft/ })).not.toBeInTheDocument();
    expect(screen.getByText('Nincs lezárt bérlés.')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Villám-03 visszavétele' })).toBeEnabled();

    const afterSwitch = requestsAfterLastLogin();
    expect(afterSwitch.length).toBeGreaterThan(0);
    for (const request of afterSwitch) expect(request.authorization).toBe(`Bearer ${server.tokenFor('eszter')}`);
    expect(loggedInUsernames()).toEqual(['peter', 'anna', 'eszter']);
  });

  it('a váltás törli az előző felhasználó üzenetét', async () => {
    const user = await startApp(realClock());
    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');

    await switchAndWait(user, 'gabor');

    expect(screen.queryByText('City-01 kibérelve.')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('az újra bérlőként belépett felhasználó a saját nevében bérel', async () => {
    const user = await startApp(realClock());
    await switchAndWait(user, 'anna');
    await switchAndWait(user, 'gabor');

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));

    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
    expect(lastRentalPost()?.authorization).toBe(`Bearer ${server.tokenFor('gabor')}`);
    expect(server.state.rentals.at(-1)?.userId).toBe('u4');
  });
});

describe('App – elavult válaszok váltás közben', () => {
  it('a váltás közbeni lassú login válasz nem írja felül az újabb váltást', async () => {
    const user = await startApp(realClock());
    const releaseSlowAnna = server.holdNext('POST', LOGIN_PATH);

    await switchTo(user, 'anna');
    await switchTo(user, 'gabor');
    await waitFor(() => expect(server.tokensOf('gabor')).toHaveLength(1));
    await waitForLists();
    expect(server.tokensOf('anna')).toHaveLength(1);

    await act(async () => {
      releaseSlowAnna();
      await new Promise((resolve) => setTimeout(resolve, 20));
    });

    expect(switcher()).toHaveValue('gabor');
    expect(screen.queryByRole('heading', { name: 'Új bicikli felvétele' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Saját bérléseim' })).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
    expect(lastRentalPost()?.authorization).toBe(`Bearer ${server.tokenFor('gabor')}`);
    expect(server.requests.filter((r) => r.authorization === `Bearer ${server.tokenFor('anna')}`)).toEqual([]);
  });

  it('a hibával végződő, elavult login nem írja felül az újabb váltást', async () => {
    const user = await startApp(realClock());
    server.override('POST', LOGIN_PATH, () => new Response('', { status: 503 }));
    const releaseSlow = server.holdNext('POST', LOGIN_PATH);

    await switchTo(user, 'anna');
    server.clearOverride('POST', LOGIN_PATH);
    await switchTo(user, 'eszter');
    await waitForLists();

    await act(async () => {
      releaseSlow();
      await new Promise((resolve) => setTimeout(resolve, 20));
    });

    expect(switcher()).toHaveValue('eszter');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Biciklik' })).toBeInTheDocument();
  });

  it('a váltás előtt indított, később érkező frissítés nem írja felül az új felhasználó adatait', async () => {
    const user = fakeClock();
    render(<App />);
    await waitForLists();

    const releaseStale = server.holdNext('GET', '/api/rentals');
    await advance(5_000);
    await switchTo(user, 'anna');
    await waitFor(() => expect(server.tokensOf('anna')).toHaveLength(1));
    expect(await screen.findByRole('row', { name: /Villám-03.*Szabó Eszter/ })).toBeInTheDocument();

    await act(async () => {
      releaseStale();
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(screen.getByRole('row', { name: /Villám-03.*Szabó Eszter/ })).toBeInTheDocument();
  });

  it('a váltás előtt indított művelet utáni frissítés lassú válasza nem írja felül az új felhasználó adatait', async () => {
    const user = await startApp(realClock());
    const releaseStale = server.holdNext('GET', '/api/rentals');

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
    await switchAndWait(user, 'anna');
    expect(await screen.findByRole('row', { name: /Villám-03.*Szabó Eszter/ })).toBeInTheDocument();

    await act(async () => {
      releaseStale();
      await new Promise((resolve) => setTimeout(resolve, 20));
    });

    expect(screen.getByRole('row', { name: /Villám-03.*Szabó Eszter/ })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Hegyi-02.*Nagy Péter/ })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Bérlések' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Saját bérléseim' })).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('a váltás előtt indított művelet utáni frissítés hibás, későn érkező válasza nem jelenik meg az új felhasználónál', async () => {
    const user = await startApp(realClock());
    server.override('GET', '/api/rentals', () => new Response('', { status: 503 }));
    const releaseStale = server.holdNext('GET', '/api/rentals');

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await waitFor(() => expect(server.countRequests('GET', '/api/rentals')).toBe(2));
    server.clearOverride('GET', '/api/rentals');
    await switchAndWait(user, 'anna');

    await act(async () => {
      releaseStale();
      await new Promise((resolve) => setTimeout(resolve, 20));
    });

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Biciklik' })).toBeInTheDocument();
  });

  it('a lassú tarifa/felhasználó betöltés elavult válasza nem írja felül az új felhasználó adatait', async () => {
    const releaseStale = server.holdNext('GET', '/api/tariffs');
    const user = await startApp(realClock());

    await switchAndWait(user, 'anna');
    expect(await screen.findByRole('row', { name: /Villám-03.*Szabó Eszter/ })).toBeInTheDocument();
    expect(screen.getByText(/Városi:/)).toBeInTheDocument();

    await act(async () => {
      releaseStale();
      await new Promise((resolve) => setTimeout(resolve, 20));
    });

    expect(screen.getByRole('row', { name: /Villám-03.*Szabó Eszter/ })).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Hegyi-02.*Nagy Péter/ })).toBeInTheDocument();
    expect(screen.getByText(/Városi:/)).toBeInTheDocument();
  });

  it('a lassú tarifa betöltés elavult hibája nem jelenik meg az új felhasználónál', async () => {
    server.override('GET', '/api/tariffs', () => new Response('', { status: 503 }));
    const releaseStale = server.holdNext('GET', '/api/tariffs');
    const user = realClock();
    render(<App />);
    await screen.findByRole('heading', { name: 'Biciklik' });
    server.clearOverride('GET', '/api/tariffs');

    await switchAndWait(user, 'anna');
    await act(async () => {
      releaseStale();
      await new Promise((resolve) => setTimeout(resolve, 20));
    });

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByText(/Városi:/)).toBeInTheDocument();
  });

  it('a váltás előtt indított, még futó művelet nem tiltja le az új felhasználó felületét', async () => {
    const user = await startApp(realClock());
    const releaseStale = server.holdNext('GET', '/api/rentals');
    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await screen.findByRole('status');

    await switchAndWait(user, 'anna');

    expect(screen.getByRole('button', { name: 'Hozzáadás' })).toBeEnabled();
    expect(screen.getByRole('combobox', { name: 'Hegyi-02 státusza' })).toBeEnabled();
    releaseStale();
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Hegyi-02 státusza' })).toBeEnabled());
  });

  it('a váltás előtt indított, később befejeződő művelet sikerüzenete nem jelenik meg az új felhasználónál', async () => {
    const user = await startApp(realClock());
    const releaseStale = server.holdNext('POST', '/api/rentals');

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await waitFor(() => expect(server.countRequests('POST', '/api/rentals')).toBe(1));
    await switchAndWait(user, 'gabor');

    await act(async () => {
      releaseStale();
      await new Promise((resolve) => setTimeout(resolve, 50));
    });

    expect(screen.queryByText('City-01 kibérelve.')).not.toBeInTheDocument();
  });
});

describe('App – lejárt munkamenet (401)', () => {
  it('a háttérfrissítésre kapott 401 után csendben újra bejelentkezik, és üzenetet mutat', async () => {
    const user = fakeClock();
    await startApp(user);
    const oldToken = server.tokenFor('peter');

    server.expireSessions();
    await advance(5_000);

    expect(await screen.findByRole('alert')).toHaveTextContent(SESSION_RENEWED);
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(screen.getByRole('heading', { name: 'Biciklik' })).toBeInTheDocument();
    expect(switcher()).toHaveValue('peter');
    expect(loginRequests()).toHaveLength(2);
    expect(loginRequests()[1].body).toEqual({ username: 'peter', password: DEMO_PASSWORD });
    expect(server.tokensOf('peter')).toHaveLength(2);
    expect(server.tokenFor('peter')).not.toBe(oldToken);
  });

  it('egyszerre több 401 is csak egyetlen újra-bejelentkezést indít, és az új tokent használja tovább', async () => {
    const user = fakeClock();
    await startApp(user);
    const oldToken = server.tokenFor('peter');

    server.expireSessions();
    await advance(5_000);
    await screen.findByRole('alert');
    expect(loginRequests()).toHaveLength(2);

    const before = server.requests.length;
    await advance(5_000);

    const later = server.requests.slice(before);
    expect(later.length).toBeGreaterThan(0);
    for (const request of later) expect(request.authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
    expect(later.some((r) => r.authorization === `Bearer ${oldToken}`)).toBe(false);
    expect(loginRequests()).toHaveLength(2);
    expect(screen.getByRole('alert')).toHaveTextContent(SESSION_RENEWED);
  });

  it('felhasználói műveletre kapott 401 után pontosan a megújulás üzenet látszik, a művelet nem fut le, és nincs régi tokennel indított lekérés', async () => {
    const user = await startApp(realClock());
    const oldToken = server.tokenFor('peter');
    const rentalsBefore = server.state.rentals.length;

    server.expireSessions();
    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));

    const alert = await screen.findByRole('alert');
    expect(alert.textContent).toBe(SESSION_RENEWED);
    await waitFor(() => expect(server.tokensOf('peter')).toHaveLength(2));
    await waitFor(() => expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled());
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(screen.getByRole('alert').textContent).toBe(SESSION_RENEWED);
    expect(server.state.rentals).toHaveLength(rentalsBefore);
    expect(loginRequests()).toHaveLength(2);

    const postIndex = server.requests.map((r) => `${r.method} ${r.path}`).lastIndexOf('POST /api/rentals');
    const afterPost = server.requests.slice(postIndex + 1).filter((r) => r.path !== LOGIN_PATH);
    for (const request of afterPost) expect(request.authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
    expect(afterPost.some((r) => r.authorization === `Bearer ${oldToken}`)).toBe(false);
  });

  it('az újra-bejelentkezés után a művelet megismételhető', async () => {
    const user = await startApp(realClock());
    server.expireSessions();
    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await screen.findByRole('alert');
    await waitFor(() => expect(server.tokensOf('peter')).toHaveLength(2));
    await waitFor(() => expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled());

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));

    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
    expect(server.state.rentals.at(-1)).toMatchObject({ bikeId: 'b1', userId: 'u2' });
  });

  it('az újra-bejelentkezés az aktív (váltott) felhasználóval történik', async () => {
    const user = fakeClock();
    await startApp(user);
    await switchAndWait(user, 'anna');

    server.expireSessions();
    await advance(5_000);

    expect(await screen.findByRole('alert')).toHaveTextContent(SESSION_RENEWED);
    expect(loggedInUsernames()).toEqual(['peter', 'anna', 'anna']);
    expect(screen.getByRole('heading', { name: 'Új bicikli felvétele' })).toBeInTheDocument();
  });
});

describe('App – a token csendes megújítása', () => {
  it('a lejárat előtt 60 másodperccel új bejelentkezést indít, és az új tokent használja', async () => {
    startServer({ sessionLengthMs: 90_000 });
    const user = fakeClock();
    await startApp(user);
    const firstToken = server.tokenFor('peter');
    expect(loginRequests()).toHaveLength(1);

    await advance(25_000);
    expect(loginRequests()).toHaveLength(1);

    await advance(10_000);
    await waitFor(() => expect(loginRequests()).toHaveLength(2));
    expect(loginRequests()[1].body).toEqual({ username: 'peter', password: DEMO_PASSWORD });
    expect(server.tokenFor('peter')).not.toBe(firstToken);

    const before = server.requests.length;
    await advance(5_000);
    const later = server.requests.slice(before).filter((r) => r.path !== LOGIN_PATH);
    expect(later.length).toBeGreaterThan(0);
    for (const request of later) expect(request.authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
  });

  it('a megújítás nem zavarja meg a nézetet, és nem mutat hibaüzenetet', async () => {
    startServer({ sessionLengthMs: 90_000 });
    const user = fakeClock();
    await startApp(user);

    await advance(35_000);
    await waitFor(() => expect(loginRequests()).toHaveLength(2));

    expect(screen.getByRole('heading', { name: 'Biciklik' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(switcher()).toHaveValue('peter');
  });

  it('folyamatosan megújul az új token lejárata előtt is', async () => {
    startServer({ sessionLengthMs: 90_000 });
    const user = fakeClock();
    await startApp(user);

    await advance(35_000);
    await waitFor(() => expect(loginRequests()).toHaveLength(2));
    await advance(30_000);
    await waitFor(() => expect(loginRequests()).toHaveLength(3));

    expect(screen.getByRole('heading', { name: 'Biciklik' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('váltás után a váltott felhasználónak újít meg', async () => {
    startServer({ sessionLengthMs: 90_000 });
    const user = fakeClock();
    await startApp(user);
    await switchAndWait(user, 'anna');

    await advance(35_000);

    await waitFor(() => expect(loginRequests()).toHaveLength(3));
    expect(loggedInUsernames()).toEqual(['peter', 'anna', 'anna']);
  });

  it('sikertelen megújításnál hibaüzenetet és Újrapróbálás gombot mutat', async () => {
    startServer({ sessionLengthMs: 90_000 });
    const user = fakeClock();
    await startApp(user);
    server.override('POST', LOGIN_PATH, tooManyRequests);

    await advance(35_000);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Túl sok bejelentkezési kísérlet.');
    expect(within(alert).getByRole('button', { name: 'Újrapróbálás' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Biciklik' })).not.toBeInTheDocument();
  });

  it('a lejárat előtti megújítás közben futó művelet sikerüzenete megjelenik, és a felület nem marad letiltva', async () => {
    startServer({ sessionLengthMs: 90_000 });
    const user = fakeClock();
    await startApp(user);
    const releaseSlow = server.holdNext('POST', '/api/rentals');

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await waitFor(() => expect(server.countRequests('POST', '/api/rentals')).toBe(1));
    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeDisabled();

    await advance(35_000);
    await waitFor(() => expect(loginRequests()).toHaveLength(2));
    await act(async () => {
      releaseSlow();
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
    expect(await screen.findByRole('button', { name: 'City-01 visszavétele' })).toBeEnabled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(server.state.rentals.at(-1)).toMatchObject({ bikeId: 'b1', userId: 'u2' });
  });

  it('a 401 utáni újra-bejelentkezés közben futó művelet sikerüzenete megjelenik, és a felület nem marad letiltva', async () => {
    const user = fakeClock();
    await startApp(user);
    const releaseSlow = server.holdNext('POST', '/api/rentals');

    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await waitFor(() => expect(server.countRequests('POST', '/api/rentals')).toBe(1));
    server.expireSessions();
    await advance(5_000);
    await waitFor(() => expect(server.tokensOf('peter')).toHaveLength(2));
    await act(async () => {
      releaseSlow();
      await vi.advanceTimersByTimeAsync(0);
    });

    expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
    expect(await screen.findByRole('button', { name: 'City-01 visszavétele' })).toBeEnabled();
    expect(screen.getByRole('heading', { name: 'Saját bérléseim' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: 'City-01 visszavétele' })).toBeEnabled());
  });

  it('a megújítás után a következő művelet is használható (nincs elakadt busy állapot)', async () => {
    startServer({ sessionLengthMs: 90_000 });
    const user = fakeClock();
    await startApp(user);
    const releaseSlow = server.holdNext('POST', '/api/rentals');
    await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
    await waitFor(() => expect(server.countRequests('POST', '/api/rentals')).toBe(1));
    await advance(35_000);
    await act(async () => {
      releaseSlow();
      await vi.advanceTimersByTimeAsync(0);
    });
    await user.click(await screen.findByRole('button', { name: 'City-01 visszavétele' }));

    await waitFor(() =>
      expect(screen.getByRole('status')).toHaveTextContent('City-01 visszavéve. Fizetendő: 315 Ft.'),
    );
    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled();
  });
});

describe('App – bejelentkezési hiba', () => {
  it('429-es válasznál hibaüzenetet és Újrapróbálás gombot mutat, a váltó látszik és használható', async () => {
    server.override('POST', LOGIN_PATH, tooManyRequests);
    render(<App />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Túl sok bejelentkezési kísérlet.');
    expect(within(alert).getByRole('button', { name: 'Újrapróbálás' })).toBeEnabled();
    expect(switcher()).toBeEnabled();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Biciklik' })).not.toBeInTheDocument();
    expect(server.requests.map((r) => r.path)).toEqual([LOGIN_PATH]);
  });

  it('hálózati hibánál a szerver nem érhető el üzenetet mutatja', async () => {
    server.failWithNetworkError('POST', LOGIN_PATH);
    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('A szerver nem érhető el.');
    expect(screen.getByRole('button', { name: 'Újrapróbálás' })).toBeInTheDocument();
  });

  it('üres törzsű 5xx hibánál az általános szerverhiba üzenetet mutatja', async () => {
    server.override('POST', LOGIN_PATH, () => new Response('', { status: 503 }));
    render(<App />);

    expect(await screen.findByRole('alert')).toHaveTextContent('A szerver jelenleg nem érhető el.');
  });

  it('hibás hitelesítő adatoknál a szerver üzenetét mutatja újra-bejelentkezési üzenet nélkül', async () => {
    server.override(
      'POST',
      LOGIN_PATH,
      () =>
        new Response(JSON.stringify({ code: 'INVALID_CREDENTIALS', message: 'Hibás felhasználónév vagy jelszó.' }), {
          status: 401,
        }),
    );
    render(<App />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Hibás felhasználónév vagy jelszó.');
    expect(alert).not.toHaveTextContent(SESSION_RENEWED);
    expect(loginRequests()).toHaveLength(1);
  });

  it('az Újrapróbálás gomb a szerver helyreállása után bejelentkeztet és betölti az adatokat', async () => {
    server.failWithNetworkError('POST', LOGIN_PATH);
    const user = realClock();
    render(<App />);
    await screen.findByRole('alert');

    server.clearOverride('POST', LOGIN_PATH);
    await user.click(screen.getByRole('button', { name: 'Újrapróbálás' }));

    await waitForLists();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(loginRequests()).toHaveLength(2);
    expect(loginRequests()[1].body).toEqual({ username: 'peter', password: DEMO_PASSWORD });
    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled();
  });

  it('az Újrapróbálás újra hibát mutat, ha a szerver még nem érhető el', async () => {
    server.failWithNetworkError('POST', LOGIN_PATH);
    const user = realClock();
    render(<App />);
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: 'Újrapróbálás' }));

    await waitFor(() => expect(loginRequests()).toHaveLength(2));
    expect(await screen.findByRole('alert')).toHaveTextContent('A szerver nem érhető el.');
  });

  it('a hibaállapotban a másik felhasználóra váltás új bejelentkezést indít, és elmúlik a hiba', async () => {
    server.failWithNetworkError('POST', LOGIN_PATH);
    const user = realClock();
    render(<App />);
    await screen.findByRole('alert');

    server.clearOverride('POST', LOGIN_PATH);
    await switchTo(user, 'anna');

    await waitForLists();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(loggedInUsernames()).toEqual(['peter', 'anna']);
    expect(screen.getByRole('heading', { name: 'Új bicikli felvétele' })).toBeInTheDocument();
  });

  it('a sikertelen váltás után a régi felhasználó adata nem látszik', async () => {
    const user = await startApp(realClock());
    server.failWithNetworkError('POST', LOGIN_PATH);

    await switchTo(user, 'anna');

    expect(await screen.findByRole('alert')).toHaveTextContent('A szerver nem érhető el.');
    expect(screen.queryByRole('heading', { name: 'Biciklik' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'City-01 bérlése' })).not.toBeInTheDocument();
    expect(switcher()).toHaveValue('anna');
  });
});

describe('App – betöltési hibák és háttérfrissítés', () => {
  it('a kezdeti adatbetöltés hibájánál hibaüzenetet és Újrapróbálás gombot mutat, a váltó látszik', async () => {
    server.override('GET', '/api/bikes', () => new Response('', { status: 503 }));
    render(<App />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('A szerver jelenleg nem érhető el.');
    expect(within(alert).getByRole('button', { name: 'Újrapróbálás' })).toBeEnabled();
    expect(screen.queryByRole('heading', { name: 'Biciklik' })).not.toBeInTheDocument();
    expect(switcher()).toHaveValue('peter');
  });

  it('az Újrapróbálás gomb a szerver helyreállása után betölti az adatokat új bejelentkezés nélkül', async () => {
    server.failWithNetworkError('GET', '/api/bikes');
    const user = realClock();
    render(<App />);
    expect(await screen.findByRole('alert')).toHaveTextContent('A szerver nem érhető el.');

    server.clearOverride('GET', '/api/bikes');
    await user.click(screen.getByRole('button', { name: 'Újrapróbálás' }));

    await waitForLists();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled();
    expect(screen.getByText(/Városi:/)).toBeInTheDocument();
    expect(loginRequests()).toHaveLength(1);
  });

  it('az Újrapróbálás újra hibát mutat, ha a szerver még nem érhető el', async () => {
    server.override('GET', '/api/rentals', () => new Response('', { status: 500 }));
    const user = realClock();
    render(<App />);
    await screen.findByRole('alert');
    const attempts = server.countRequests('GET', '/api/rentals');

    await user.click(screen.getByRole('button', { name: 'Újrapróbálás' }));

    await waitFor(() => expect(server.countRequests('GET', '/api/rentals')).toBeGreaterThan(attempts));
    expect(await screen.findByRole('alert')).toHaveTextContent('A szerver jelenleg nem érhető el.');
    expect(screen.queryByRole('heading', { name: 'Biciklik' })).not.toBeInTheDocument();
  });

  it('5 másodpercenként újratölti a biciklikat és a bérléseket, és megjeleníti az új adatot', async () => {
    const user = fakeClock();
    await startApp(user);
    const bikeLoads = server.countRequests('GET', '/api/bikes');
    const rentalLoads = server.countRequests('GET', '/api/rentals');

    server.state.bikes.push({ id: 'b7', name: 'Elektro-07', type: 'electric', status: 'available' });
    await advance(5_000);

    expect(await screen.findByRole('button', { name: 'Elektro-07 bérlése' })).toBeEnabled();
    expect(server.countRequests('GET', '/api/bikes')).toBeGreaterThan(bikeLoads);
    expect(server.countRequests('GET', '/api/rentals')).toBeGreaterThan(rentalLoads);
  });

  it('a háttérfrissítés hibájánál figyelmeztet, a régi adatokat megtartja, helyreálláskor eltünteti a hibát', async () => {
    const user = fakeClock();
    await startApp(user);

    server.failWithNetworkError('GET', '/api/bikes');
    await advance(5_000);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'A szerverrel nem sikerült frissíteni az adatokat: A szerver nem érhető el.',
    );
    expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeInTheDocument();

    server.clearOverride('GET', '/api/bikes');
    await advance(5_000);

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });
});

function searchClock() {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  return userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms), delay: null });
}

const nameFilter = () => screen.getByRole('searchbox', { name: 'Név részlete' });
const typeFilter = () => screen.getByRole('combobox', { name: 'Típus szűrő' });
const statusFilter = () => screen.getByRole('combobox', { name: 'Státusz szűrő' });
const searchArea = () => screen.getByRole('search', { name: 'Bicikli keresése' });
const clearFiltersButton = () => screen.queryByRole('button', { name: 'Szűrők törlése' });

function summaryText() {
  return searchArea().querySelector('[aria-live="polite"]')?.textContent;
}

function listedBikes() {
  const section = screen.getByRole('heading', { name: 'Biciklik' }).closest('section');
  if (!section) throw new Error('Hiányzik a biciklilista szakasz');
  return within(section)
    .queryAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[0].textContent);
}

function seedBikes() {
  server.state.bikes.push(
    { id: 'b4', name: 'City-04', type: 'city', status: 'maintenance' },
    { id: 'b5', name: 'Hegyi-05', type: 'mountain', status: 'available' },
  );
}

const ALL_BIKES = ['City-01', 'City-04', 'Hegyi-02', 'Hegyi-05', 'Villám-03'];

async function startSearchApp() {
  const user = searchClock();
  await startApp(user);
  return user;
}

describe('App – bicikli keresés', () => {
  beforeEach(() => {
    seedBikes();
  });

  describe('szűrő nélkül', () => {
    it('a teljes listát mutatja, üres összegzéssel, és nem indít keresést', async () => {
      await startSearchApp();

      expect([...listedBikes()].sort()).toEqual(ALL_BIKES);
      expect(summaryText()).toBe('');
      expect(nameFilter()).toHaveValue('');
      expect(typeFilter()).toHaveValue('');
      expect(statusFilter()).toHaveValue('');
      expect(clearFiltersButton()).not.toBeInTheDocument();

      await advance(1_000);
      expect(server.searchRequests()).toHaveLength(0);
    });
  });

  describe('debounce', () => {
    it('több billentyű után is csak egyetlen keresést indít, a gépelés befejezése után', async () => {
      const user = await startSearchApp();

      await user.type(nameFilter(), 'city');
      expect(nameFilter()).toHaveValue('city');
      expect(server.searchRequests()).toHaveLength(0);

      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));

      expect(server.searchRequests()).toHaveLength(1);
      expect(server.searchRequests()[0].query).toEqual({ q: 'city' });
      expect(server.searchRequests()[0].authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
    });

    it('a következő billentyű újraindítja a várakozást', async () => {
      const user = await startSearchApp();

      await user.type(nameFilter(), 'ci');
      await advance(200);
      expect(server.searchRequests()).toHaveLength(0);
      await user.type(nameFilter(), 'ty');
      await advance(200);
      expect(server.searchRequests()).toHaveLength(0);

      await advance(100);
      await waitFor(() => expect(server.searchRequests()).toHaveLength(1));
      expect(server.searchRequests()[0].query).toEqual({ q: 'city' });
    });

    it('a szűrők gyors egymás utáni módosítása is egyetlen keresést indít az összes feltétellel', async () => {
      const user = await startSearchApp();

      await user.type(nameFilter(), 'i');
      await user.selectOptions(typeFilter(), 'mountain');
      await user.selectOptions(statusFilter(), 'available');
      expect(server.searchRequests()).toHaveLength(0);
      await advance(300);

      await waitFor(() => expect(listedBikes()).toEqual(['Hegyi-05']));
      expect(server.searchRequests()).toHaveLength(1);
      expect(server.searchRequests()[0].query).toEqual({ q: 'i', type: 'mountain', status: 'available' });
    });
  });

  describe('szűrők', () => {
    it('a név részletére kis-nagybetűtől és a két végén lévő szóközöktől függetlenül szűr', async () => {
      const user = await startSearchApp();

      await user.type(nameFilter(), '  CITY ');
      await advance(300);

      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));
      expect(server.searchRequests()[0].query).toEqual({ q: 'CITY' });
    });

    it('a típusszűrő csak az adott típusú biciklit mutatja', async () => {
      const user = await startSearchApp();

      await user.selectOptions(typeFilter(), 'mountain');
      await advance(300);

      await waitFor(() => expect(listedBikes()).toEqual(['Hegyi-02', 'Hegyi-05']));
      expect(server.searchRequests()[0].query).toEqual({ type: 'mountain' });
      expect(summaryText()).toBe('2 találat');
    });

    it('a státuszszűrő csak az adott státuszú biciklit mutatja', async () => {
      const user = await startSearchApp();

      await user.selectOptions(statusFilter(), 'available');
      await advance(300);

      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'Hegyi-05']));
      expect(server.searchRequests()[0].query).toEqual({ status: 'available' });
    });

    it('a név és a státusz kombinálva szűkíti a találatokat', async () => {
      const user = await startSearchApp();

      await user.type(nameFilter(), 'hegyi');
      await user.selectOptions(statusFilter(), 'available');
      await advance(300);

      await waitFor(() => expect(listedBikes()).toEqual(['Hegyi-05']));
      expect(server.searchRequests()[0].query).toEqual({ q: 'hegyi', status: 'available' });
      expect(summaryText()).toBe('1 találat');
    });

    it('a típus és a státusz kombinálva szűkíti a találatokat', async () => {
      const user = await startSearchApp();

      await user.selectOptions(typeFilter(), 'city');
      await user.selectOptions(statusFilter(), 'maintenance');
      await advance(300);

      await waitFor(() => expect(listedBikes()).toEqual(['City-04']));
      expect(server.searchRequests()[0].query).toEqual({ type: 'city', status: 'maintenance' });
    });

    it('a Minden típus visszaválasztása megszünteti a típusszűrést', async () => {
      const user = await startSearchApp();
      await user.selectOptions(typeFilter(), 'electric');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['Villám-03']));

      await user.selectOptions(typeFilter(), 'Minden típus');
      await advance(300);

      await waitFor(() => expect([...listedBikes()].sort()).toEqual(ALL_BIKES));
      expect(summaryText()).toBe('');
    });
  });

  describe('összegzés és üres találat', () => {
    it('a találatok számát mutatja', async () => {
      const user = await startSearchApp();

      await user.type(nameFilter(), 'city');
      await advance(300);

      await waitFor(() => expect(summaryText()).toBe('2 találat'));
    });

    it('csonkolt találatnál megjelenített és összes számot is mutat (a szerver limitje)', async () => {
      startServer({ searchLimit: 2 });
      seedBikes();
      const user = await startSearchApp();

      await user.type(nameFilter(), '-0');
      await advance(300);

      await waitFor(() => expect(summaryText()).toBe('2 találat megjelenítve, összesen 5'));
      expect(listedBikes()).toEqual(['City-01', 'City-04']);
    });

    it('az alapértelmezett 50-es limitnél több találatot csonkolva jelzi', async () => {
      for (let i = 1; i <= 52; i++) {
        server.state.bikes.push({ id: `t${i}`, name: `Tömeg-${String(i).padStart(2, '0')}`, type: 'city', status: 'available' });
      }
      const user = await startSearchApp();

      await user.type(nameFilter(), 'tömeg');
      await advance(300);

      await waitFor(() => expect(summaryText()).toBe('50 találat megjelenítve, összesen 52'));
      expect(listedBikes()).toHaveLength(50);
      expect(listedBikes()[0]).toBe('Tömeg-01');
    });

    it('a pontosan a limitnek megfelelő találatszámnál nem jelez csonkolást', async () => {
      startServer({ searchLimit: 2 });
      seedBikes();
      const user = await startSearchApp();

      await user.selectOptions(typeFilter(), 'city');
      await advance(300);

      await waitFor(() => expect(summaryText()).toBe('2 találat'));
    });

    it('találat nélkül a keresésre vonatkozó üres üzenetet mutatja táblázat nélkül', async () => {
      const user = await startSearchApp();

      await user.type(nameFilter(), 'nincs-ilyen');
      await advance(300);

      expect(await screen.findByText('Nincs a keresésnek megfelelő bicikli.')).toBeInTheDocument();
      expect(screen.queryByText('Még nincs bicikli az állomáson.')).not.toBeInTheDocument();
      expect(listedBikes()).toEqual([]);
      expect(summaryText()).toBe('0 találat');
      expect(clearFiltersButton()).toBeInTheDocument();
    });
  });

  describe('szűrők törlése', () => {
    it('visszaállítja a teljes listát és az üres összegzést, további keresés nélkül', async () => {
      const user = await startSearchApp();
      await user.type(nameFilter(), 'city');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));
      expect(server.searchRequests()).toHaveLength(1);

      const button = clearFiltersButton();
      if (!button) throw new Error('A Szűrők törlése gomb hiányzik');
      await user.click(button);
      await advance(300);

      await waitFor(() => expect([...listedBikes()].sort()).toEqual(ALL_BIKES));
      expect(summaryText()).toBe('');
      expect(nameFilter()).toHaveValue('');
      expect(clearFiltersButton()).not.toBeInTheDocument();
      expect(server.searchRequests()).toHaveLength(1);
    });

    it('a találat nélküli állapotból is visszaállít', async () => {
      const user = await startSearchApp();
      await user.type(nameFilter(), 'nincs-ilyen');
      await advance(300);
      await screen.findByText('Nincs a keresésnek megfelelő bicikli.');

      const button = clearFiltersButton();
      if (!button) throw new Error('A Szűrők törlése gomb hiányzik');
      await user.click(button);
      await advance(300);

      await waitFor(() => expect(listedBikes()).toHaveLength(5));
      expect(screen.queryByText('Nincs a keresésnek megfelelő bicikli.')).not.toBeInTheDocument();
    });
  });

  describe('frissítés közben és műveletek után', () => {
    it('a háttérfrissítés közben megmarad a keresés, és az új találatok megjelennek', async () => {
      const user = await startSearchApp();
      await user.type(nameFilter(), 'city');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));
      const before = server.searchRequests().length;

      server.state.bikes.push(
        { id: 'b6', name: 'City-06', type: 'city', status: 'available' },
        { id: 'b7', name: 'Hegyi-07', type: 'mountain', status: 'available' },
      );
      await advance(5_000);

      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04', 'City-06']));
      expect(summaryText()).toBe('3 találat');
      expect(nameFilter()).toHaveValue('city');
      expect(server.searchRequests().length).toBeGreaterThan(before);
      for (const request of server.searchRequests()) expect(request.query).toEqual({ q: 'city' });
    });

    it('a bérlések bicikli-nevei szűrt lista mellett is látszanak', async () => {
      const user = await startSearchApp();

      await user.selectOptions(typeFilter(), 'city');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));

      const rentalsSection = screen.getByRole('heading', { name: 'Saját bérléseim' }).closest('section');
      if (!rentalsSection) throw new Error('Hiányzik a bérléseket tartalmazó szakasz');
      const finished = within(rentalsSection).getByRole('row', { name: /Hegyi-02.*600 Ft/ });
      expect(within(finished).queryByText('b2')).not.toBeInTheDocument();
      expect(listedBikes()).not.toContain('Hegyi-02');
    });

    it('a bérlés után a találatok frissülnek, és a szűrő megmarad', async () => {
      const user = await startSearchApp();
      await user.selectOptions(statusFilter(), 'available');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'Hegyi-05']));

      await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));

      expect(await screen.findByRole('status')).toHaveTextContent('City-01 kibérelve.');
      await waitFor(() => expect(listedBikes()).toEqual(['Hegyi-05']));
      expect(summaryText()).toBe('1 találat');
      expect(statusFilter()).toHaveValue('available');
      expect(server.searchRequests().at(-1)?.query).toEqual({ status: 'available' });
      expect(await screen.findByRole('button', { name: 'City-01 visszavétele' })).toBeEnabled();
    });

    it('a státuszváltás után a találatok frissülnek', async () => {
      const user = await startSearchApp();
      await switchAndWait(user, 'anna');
      await user.selectOptions(statusFilter(), 'maintenance');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-04', 'Hegyi-02']));

      await user.selectOptions(screen.getByRole('combobox', { name: 'Hegyi-02 státusza' }), 'available');

      expect(await screen.findByRole('status')).toHaveTextContent('Hegyi-02 állapota módosítva.');
      await waitFor(() => expect(listedBikes()).toEqual(['City-04']));
      expect(summaryText()).toBe('1 találat');
    });

    it('az új bicikli felvétele után a keresés találatai frissülnek', async () => {
      const user = await startSearchApp();
      await switchAndWait(user, 'anna');
      await user.type(nameFilter(), 'city');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));

      await user.type(screen.getByLabelText('Név / azonosító'), 'City-09');
      await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));

      expect(await screen.findByRole('status')).toHaveTextContent('City-09 felvéve.');
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04', 'City-09']));
      expect(summaryText()).toBe('3 találat');
    });
  });

  describe('felhasználóváltás', () => {
    it('törli a szűrőket, és az új felhasználónál a teljes lista látszik keresés nélkül', async () => {
      const user = await startSearchApp();
      await user.type(nameFilter(), 'city');
      await user.selectOptions(statusFilter(), 'available');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01']));

      await switchAndWait(user, 'anna');
      await advance(1_000);

      expect(nameFilter()).toHaveValue('');
      expect(typeFilter()).toHaveValue('');
      expect(statusFilter()).toHaveValue('');
      expect(clearFiltersButton()).not.toBeInTheDocument();
      expect(summaryText()).toBe('');
      expect([...listedBikes()].sort()).toEqual(ALL_BIKES);
      expect(requestsAfterLastLogin().filter((r) => r.path === '/api/bikes/search')).toEqual([]);
    });

    it('a váltás közben beírt, még nem alkalmazott szöveg sem indít keresést az új felhasználónál', async () => {
      const user = await startSearchApp();
      await user.type(nameFilter(), 'city');
      const searchesBefore = server.searchRequests().length;

      await switchAndWait(user, 'eszter');
      await advance(1_000);

      expect(nameFilter()).toHaveValue('');
      expect(server.searchRequests()).toHaveLength(searchesBefore);
    });
  });

  describe('elavult válaszok és hibák', () => {
    it('a gyors módosításnál a régebbi, később érkező válasz nem írja felül az újabbat', async () => {
      const user = await startSearchApp();
      const releaseOld = server.holdNext('GET', '/api/bikes/search');

      await user.type(nameFilter(), 'city');
      await advance(300);
      await waitFor(() => expect(server.searchRequests()).toHaveLength(1));

      await user.clear(nameFilter());
      await user.type(nameFilter(), 'hegyi');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['Hegyi-02', 'Hegyi-05']));
      expect(summaryText()).toBe('2 találat');
      expect(server.searchRequests().map((r) => r.query)).toEqual([{ q: 'city' }, { q: 'hegyi' }]);

      await act(async () => {
        releaseOld();
        await vi.advanceTimersByTimeAsync(0);
      });

      expect(listedBikes()).toEqual(['Hegyi-02', 'Hegyi-05']);
      expect(summaryText()).toBe('2 találat');
      expect(nameFilter()).toHaveValue('hegyi');
    });

    it('a szűrő törlése után a korábbi, később érkező keresési válasz nem állítja vissza a szűrt listát', async () => {
      const user = await startSearchApp();
      const releaseOld = server.holdNext('GET', '/api/bikes/search');
      await user.type(nameFilter(), 'city');
      await advance(300);
      await waitFor(() => expect(server.searchRequests()).toHaveLength(1));

      const button = clearFiltersButton();
      if (!button) throw new Error('A Szűrők törlése gomb hiányzik');
      await user.click(button);
      await advance(300);
      await waitFor(() => expect(listedBikes()).toHaveLength(5));

      await act(async () => {
        releaseOld();
        await vi.advanceTimersByTimeAsync(0);
      });

      expect([...listedBikes()].sort()).toEqual(ALL_BIKES);
      expect(summaryText()).toBe('');
    });

    it('a keresési hiba hibasávot mutat, a korábbi adatok megmaradnak, és a helyreállás után eltűnik', async () => {
      const user = await startSearchApp();
      server.override('GET', '/api/bikes/search', () => new Response('', { status: 503 }));

      await user.type(nameFilter(), 'city');
      await advance(300);

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'A szerverrel nem sikerült frissíteni az adatokat: A szerver jelenleg nem érhető el.',
      );
      expect([...listedBikes()].sort()).toEqual(ALL_BIKES);
      expect(nameFilter()).toHaveValue('city');

      server.clearOverride('GET', '/api/bikes/search');
      await advance(5_000);

      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
      expect(summaryText()).toBe('2 találat');
    });

    it('a keresés hálózati hibájánál a szerver nem érhető el üzenetet mutatja', async () => {
      const user = await startSearchApp();
      server.failWithNetworkError('GET', '/api/bikes/search');

      await user.selectOptions(typeFilter(), 'city');
      await advance(300);

      expect(await screen.findByRole('alert')).toHaveTextContent('A szerver nem érhető el.');
      expect([...listedBikes()].sort()).toEqual(ALL_BIKES);
    });

    it('a keresésre kapott 401 után csendben újra bejelentkezik, és a szűrő megmarad', async () => {
      const user = await startSearchApp();
      await user.type(nameFilter(), 'city');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));

      server.expireSessions();
      await advance(5_000);

      expect(await screen.findByRole('alert')).toHaveTextContent(SESSION_RENEWED);
      await waitFor(() => expect(server.tokensOf('peter')).toHaveLength(2));
      await waitFor(() => expect(listedBikes()).toEqual(['City-01', 'City-04']));
      expect(nameFilter()).toHaveValue('city');
      expect(server.searchRequests().at(-1)?.authorization).toBe(`Bearer ${server.tokenFor('peter')}`);
    });

    it('a szűrő módosítása előtt indított művelet utáni frissítés nem írja felül a közben módosított szűrő találatait', async () => {
      const user = await startSearchApp();
      const releaseSlow = server.holdNext('POST', '/api/rentals');
      await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
      await waitFor(() => expect(server.countRequests('POST', '/api/rentals')).toBe(1));

      await user.selectOptions(typeFilter(), 'mountain');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['Hegyi-02', 'Hegyi-05']));

      await act(async () => {
        releaseSlow();
        await vi.advanceTimersByTimeAsync(0);
      });
      await screen.findByRole('status');
      await advance(100);

      expect(listedBikes()).toEqual(['Hegyi-02', 'Hegyi-05']);
      expect(summaryText()).toBe('2 találat');
    });

    it('a szűrő törlése előtt indított művelet utáni frissítés a teljes listát mutatja, nem a régi találatokat', async () => {
      const user = await startSearchApp();
      await user.selectOptions(typeFilter(), 'mountain');
      await advance(300);
      await waitFor(() => expect(listedBikes()).toEqual(['Hegyi-02', 'Hegyi-05']));
      const releaseSlow = server.holdNext('POST', '/api/rentals');
      await user.click(screen.getByRole('button', { name: 'Hegyi-05 bérlése' }));
      await waitFor(() => expect(server.countRequests('POST', '/api/rentals')).toBe(1));

      const button = clearFiltersButton();
      if (!button) throw new Error('A Szűrők törlése gomb hiányzik');
      await user.click(button);
      await advance(300);
      await waitFor(() => expect(listedBikes()).toHaveLength(5));

      await act(async () => {
        releaseSlow();
        await vi.advanceTimersByTimeAsync(0);
      });
      await screen.findByRole('status');
      await advance(100);

      expect(screen.getByRole('status')).toHaveTextContent('Hegyi-05 kibérelve.');
      expect([...listedBikes()].sort()).toEqual(ALL_BIKES);
      expect(summaryText()).toBe('');
      expect(nameFilter()).toHaveValue('');
      expect(typeFilter()).toHaveValue('');
      expect(server.searchRequests()).toHaveLength(1);
      expect(screen.getByRole('button', { name: 'Hegyi-05 visszavétele' })).toBeEnabled();
    });
  });
});
