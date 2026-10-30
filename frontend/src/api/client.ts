import type {
  Bike,
  BikeSearchResult,
  BikeStatus,
  BikeType,
  LoginResponse,
  Rental,
  SearchCriteria,
  Tariffs,
  User,
} from '../types';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH';
  body?: unknown;
  /** A bejelentkezés maga nem küld tokent, és a 401 sem jelent lejárt munkamenetet. */
  authenticated?: boolean;
}

// A token szándékosan csak memóriában él (nincs localStorage/sessionStorage): XSS esetén sem marad meg az oldal újratöltése után.
let accessToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setAuthToken(token: string | null): void {
  accessToken = token;
}

/** A hitelesített kérésre kapott 401 (lejárt vagy érvénytelen token) esetén hívódik. */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

async function request<T>(path: string, { method = 'GET', body, authenticated = true }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (authenticated && accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'A szerver nem érhető el.');
  }

  if (!response.ok) {
    const error = readError(await response.json().catch(() => null));
    const fallback =
      response.status >= 500 ? 'A szerver jelenleg nem érhető el.' : `A kérés sikertelen (${response.status}).`;
    if (response.status === 401 && authenticated && accessToken) unauthorizedHandler?.();
    throw new ApiError(response.status, error.code ?? 'UNKNOWN_ERROR', error.message ?? fallback);
  }
  return response.json();
}

/** A hibatörzsből csak a szövegesen megadott code és message mezőt veszi át; bármilyen más tartalmat figyelmen kívül hagy. */
function readError(body: unknown): { code?: string; message?: string } {
  if (typeof body !== 'object' || body === null) return {};
  const code = 'code' in body && typeof body.code === 'string' ? body.code : undefined;
  const message = 'message' in body && typeof body.message === 'string' ? body.message : undefined;
  return { code, message };
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/api/auth/login', { method: 'POST', body: { username, password }, authenticated: false }),
  listUsers: () => request<User[]>('/api/users'),
  listTariffs: () => request<Tariffs>('/api/tariffs'),
  listBikes: () => request<Bike[]>('/api/bikes'),
  searchBikes: (criteria: SearchCriteria) => {
    const params = new URLSearchParams();
    if (criteria.q.trim() !== '') params.set('q', criteria.q.trim());
    if (criteria.type !== '') params.set('type', criteria.type);
    if (criteria.status !== '') params.set('status', criteria.status);
    const query = params.toString();
    return request<BikeSearchResult>(query ? `/api/bikes/search?${query}` : '/api/bikes/search');
  },
  createBike: (name: string, type: BikeType) => request<Bike>('/api/bikes', { method: 'POST', body: { name, type } }),
  changeBikeStatus: (bikeId: string, status: BikeStatus) =>
    request<Bike>(`/api/bikes/${encodeURIComponent(bikeId)}/status`, { method: 'PATCH', body: { status } }),
  listRentals: () => request<Rental[]>('/api/rentals'),
  createRental: (bikeId: string) => request<Rental>('/api/rentals', { method: 'POST', body: { bikeId } }),
  returnRental: (rentalId: string) =>
    request<Rental>(`/api/rentals/${encodeURIComponent(rentalId)}/return`, { method: 'POST' }),
};
