import { vi } from 'vitest';
import { DEMO_PASSWORD, DEMO_USERS } from '../demoUsers';
import type { Bike, BikeStatus, BikeType, Rental, Tariffs, User } from '../types';

export interface RecordedRequest {
  method: string;
  path: string;
  query: Record<string, string>;
  authorization: string | null;
  body: unknown;
}

type ResponseFactory = () => Response | Promise<Response>;

const RETURN_FEE = 315;
const SESSION_LENGTH_MS = 60 * 60 * 1000;
const DEFAULT_SEARCH_LIMIT = 50;

const BIKE_TYPES: BikeType[] = ['city', 'mountain', 'electric'];
const BIKE_STATUSES: BikeStatus[] = ['available', 'in_use', 'maintenance'];

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function apiError(status: number, code: string, message: string): Response {
  return json({ code, message }, status);
}

function field(body: unknown, key: string): unknown {
  return typeof body === 'object' && body !== null ? Reflect.get(body, key) : undefined;
}

export function createFakeServer({
  sessionLengthMs = SESSION_LENGTH_MS,
  searchLimit = DEFAULT_SEARCH_LIMIT,
}: { sessionLengthMs?: number; searchLimit?: number } = {}) {
  const accounts = DEMO_USERS.map((demo, index) => {
    const user: User = { id: `u${index + 1}`, name: demo.name, role: demo.role };
    return { username: demo.username, user };
  });
  const users = accounts.map((a) => a.user);
  const tariffs: Tariffs = {
    city: { baseFee: 300, perMinute: 15 },
    mountain: { baseFee: 500, perMinute: 25 },
    electric: { baseFee: 1000, perMinute: 40 },
  };

  const idOf = (username: string) => accounts.find((a) => a.username === username)?.user.id ?? 'ismeretlen';

  const initialBikes: Bike[] = [
    { id: 'b1', name: 'City-01', type: 'city', status: 'available' },
    { id: 'b2', name: 'Hegyi-02', type: 'mountain', status: 'maintenance' },
    { id: 'b3', name: 'Villám-03', type: 'electric', status: 'in_use' },
  ];
  const initialRentals: Rental[] = [
    {
      id: 'r1',
      bikeId: 'b2',
      userId: idOf('peter'),
      startedAt: new Date(2025, 4, 1, 10, 0, 0).getTime(),
      endedAt: new Date(2025, 4, 1, 10, 20, 0).getTime(),
      fee: 600,
      elapsedSeconds: 1200,
      currentFee: 600,
    },
    {
      id: 'r2',
      bikeId: 'b3',
      userId: idOf('eszter'),
      startedAt: new Date(2025, 4, 2, 9, 0, 0).getTime(),
      elapsedSeconds: 300,
      currentFee: 1200,
    },
  ];

  const state = { bikes: initialBikes, rentals: initialRentals };

  const requests: RecordedRequest[] = [];
  const overrides = new Map<string, ResponseFactory>();
  const holds = new Map<string, Promise<void>[]>();
  const issuedTokens = new Map<string, User>();
  const tokensByUsername = new Map<string, string[]>();
  const revokedTokens = new Set<string>();
  let nextBikeId = 10;
  let nextRentalId = 10;
  let nextTokenId = 1;

  function currentUser(authorization: string | null): User | null {
    if (!authorization?.startsWith('Bearer ')) return null;
    const token = authorization.slice('Bearer '.length);
    return revokedTokens.has(token) ? null : (issuedTokens.get(token) ?? null);
  }

  function searchBikes(query: Record<string, string>): Response {
    const q = (query.q ?? '').trim();
    const type = query.type;
    const status = query.status;
    const limitText = query.limit;
    const limit = limitText === undefined ? searchLimit : Number(limitText);
    const invalid =
      q.length > 50 ||
      (type !== undefined && !BIKE_TYPES.some((t) => t === type)) ||
      (status !== undefined && !BIKE_STATUSES.some((s) => s === status)) ||
      !Number.isInteger(limit) ||
      limit < 1 ||
      limit > 100;
    if (invalid) return apiError(400, 'VALIDATION_ERROR', 'Érvénytelen keresési feltétel.');

    const needle = q.toLowerCase();
    const matches = state.bikes
      .filter((b) => b.name.toLowerCase().includes(needle))
      .filter((b) => type === undefined || b.type === type)
      .filter((b) => status === undefined || b.status === status)
      .sort((a, b) => a.name.localeCompare(b.name, 'hu'));
    return json({ items: matches.slice(0, limit), total: matches.length });
  }

  function handle(method: string, path: string, query: Record<string, string>, body: unknown, user: User | null): Response {
    if (method === 'POST' && path === '/api/auth/login') {
      const username = String(field(body, 'username'));
      const account = accounts.find((a) => a.username === username);
      if (!account || field(body, 'password') !== DEMO_PASSWORD) {
        return apiError(401, 'INVALID_CREDENTIALS', 'Hibás felhasználónév vagy jelszó.');
      }
      const token = `token-${username}-${nextTokenId++}`;
      issuedTokens.set(token, account.user);
      tokensByUsername.set(username, [...(tokensByUsername.get(username) ?? []), token]);
      return json({ accessToken: token, tokenType: 'Bearer', expiresAt: Date.now() + sessionLengthMs, user: account.user });
    }

    if (!user) return apiError(401, 'UNAUTHENTICATED', 'Hiányzó, érvénytelen vagy lejárt hitelesítés.');
    const forbidden = () => apiError(403, 'FORBIDDEN', 'Ehhez a művelethez nincs jogosultságod.');

    if (method === 'GET' && path === '/api/users') return user.role === 'manager' ? json(users) : forbidden();
    if (method === 'GET' && path === '/api/tariffs') return json(tariffs);
    if (method === 'GET' && path === '/api/bikes') return json(state.bikes);
    if (method === 'GET' && path === '/api/bikes/search') return searchBikes(query);

    if (method === 'POST' && path === '/api/bikes') {
      if (user.role !== 'manager') return forbidden();
      const name = field(body, 'name');
      const type = field(body, 'type');
      const validType = BIKE_TYPES.find((t) => t === type);
      if (typeof name !== 'string' || name.trim().length < 2 || !validType) {
        return apiError(400, 'VALIDATION_ERROR', 'Érvénytelen kérés.');
      }
      if (state.bikes.some((b) => b.name.toLowerCase() === name.trim().toLowerCase())) {
        return apiError(409, 'BIKE_NAME_TAKEN', 'Ilyen nevű bicikli már létezik.');
      }
      const bike: Bike = { id: `b${nextBikeId++}`, name: name.trim(), type: validType, status: 'available' };
      state.bikes.push(bike);
      return json(bike, 201);
    }

    const statusMatch = /^\/api\/bikes\/([^/]+)\/status$/.exec(path);
    if (method === 'PATCH' && statusMatch) {
      if (user.role !== 'manager') return forbidden();
      const bike = state.bikes.find((b) => b.id === decodeURIComponent(statusMatch[1]));
      if (!bike) return apiError(404, 'BIKE_NOT_FOUND', 'A bicikli nem található.');
      const status = BIKE_STATUSES.find((s) => s === field(body, 'status'));
      if (!status) return apiError(400, 'VALIDATION_ERROR', 'Érvénytelen kérés.');
      if (bike.status === 'in_use') return apiError(409, 'BIKE_IN_USE', 'A használatban lévő bicikli státusza nem módosítható.');
      bike.status = status;
      return json(bike);
    }

    if (method === 'GET' && path === '/api/rentals') {
      return json(user.role === 'manager' ? state.rentals : state.rentals.filter((r) => r.userId === user.id));
    }

    if (method === 'POST' && path === '/api/rentals') {
      if (user.role !== 'renter') return forbidden();
      const bike = state.bikes.find((b) => b.id === field(body, 'bikeId'));
      if (!bike) return apiError(404, 'BIKE_NOT_FOUND', 'A bicikli nem található.');
      if (bike.status !== 'available') {
        return apiError(409, 'BIKE_NOT_AVAILABLE', 'Ez a bicikli jelenleg nem bérelhető.');
      }
      bike.status = 'in_use';
      const rental: Rental = {
        id: `r${nextRentalId++}`,
        bikeId: bike.id,
        userId: user.id,
        startedAt: Date.now(),
        elapsedSeconds: 0,
        currentFee: tariffs[bike.type].baseFee,
      };
      state.rentals.push(rental);
      return json(rental, 201);
    }

    const returnMatch = /^\/api\/rentals\/([^/]+)\/return$/.exec(path);
    if (method === 'POST' && returnMatch) {
      const rental = state.rentals.find((r) => r.id === decodeURIComponent(returnMatch[1]));
      if (!rental) return apiError(404, 'RENTAL_NOT_FOUND', 'A bérlés nem található.');
      if (user.role === 'renter' && rental.userId !== user.id) return forbidden();
      if (rental.endedAt !== undefined) return apiError(409, 'RENTAL_ALREADY_RETURNED', 'A bérlés már lezárult.');
      rental.endedAt = Date.now();
      rental.fee = RETURN_FEE;
      rental.currentFee = RETURN_FEE;
      rental.elapsedSeconds = 60;
      const bike = state.bikes.find((b) => b.id === rental.bikeId);
      if (bike) bike.status = 'available';
      return json(rental);
    }

    return apiError(404, 'NOT_FOUND', 'A végpont nem található.');
  }

  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const method = init?.method ?? 'GET';
    const url = new URL(String(input), 'http://localhost');
    const path = url.pathname;
    const query = Object.fromEntries(url.searchParams);
    const key = `${method} ${path}`;
    const headers = new Headers(init?.headers);
    const body: unknown = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined;
    const authorization = headers.get('Authorization');
    requests.push({ method, path, query, authorization, body });

    const override = overrides.get(key);
    // A válasz a kérés pillanatában készül el, a visszatartás csak a kézbesítését késlelteti
    const response = override ? await override() : handle(method, path, query, body, currentUser(authorization));
    const gate = holds.get(key)?.shift();
    if (gate) await gate;
    return response;
  });

  return {
    fetch: fetchMock,
    state,
    requests,
    countRequests: (method: string, path: string) => requests.filter((r) => r.method === method && r.path === path).length,
    searchRequests: () => requests.filter((r) => r.method === 'GET' && r.path === '/api/bikes/search'),
    override: (method: string, path: string, factory: ResponseFactory) => {
      overrides.set(`${method} ${path}`, factory);
    },
    failWithNetworkError: (method: string, path: string) => {
      overrides.set(`${method} ${path}`, () => {
        throw new TypeError('Failed to fetch');
      });
    },
    clearOverride: (method: string, path: string) => {
      overrides.delete(`${method} ${path}`);
    },
    /** A következő egyező kérés válaszát addig tartja vissza, amíg a visszaadott függvényt nem hívják. */
    holdNext: (method: string, path: string): (() => void) => {
      let release: () => void = () => {};
      const gate = new Promise<void>((resolve) => {
        release = resolve;
      });
      const key = `${method} ${path}`;
      holds.set(key, [...(holds.get(key) ?? []), gate]);
      return release;
    },
    /** Minden eddig kiadott tokent érvénytelenít; az ezután kiadottak érvényesek. */
    expireSessions: () => {
      for (const token of issuedTokens.keys()) revokedTokens.add(token);
    },
    tokenFor: (username: string): string => {
      const token = tokensByUsername.get(username)?.at(-1);
      if (!token) throw new Error(`Nem lett token kiadva ehhez: ${username}`);
      return token;
    },
    tokensOf: (username: string): string[] => tokensByUsername.get(username) ?? [],
  };
}

export type FakeServer = ReturnType<typeof createFakeServer>;
