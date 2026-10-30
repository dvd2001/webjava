import type { Bike, BikeStatus, BikeType, Rental, Tariffs, User } from '../types';

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
  userId?: string;
  body?: unknown;
}

async function request<T>(path: string, { method = 'GET', userId, body }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (userId) headers['X-User-Id'] = userId;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'A szerver nem érhető el.');
  }

  if (!response.ok) {
    const error: { code?: string; message?: string } = await response.json().catch(() => ({}));
    const fallback =
      response.status >= 500 ? 'A szerver jelenleg nem érhető el.' : `A kérés sikertelen (${response.status}).`;
    throw new ApiError(response.status, error.code ?? 'UNKNOWN_ERROR', error.message ?? fallback);
  }
  return response.json();
}

export const api = {
  listUsers: () => request<User[]>('/api/users'),
  listTariffs: () => request<Tariffs>('/api/tariffs'),
  listBikes: () => request<Bike[]>('/api/bikes'),
  createBike: (userId: string, name: string, type: BikeType) =>
    request<Bike>('/api/bikes', { method: 'POST', userId, body: { name, type } }),
  changeBikeStatus: (userId: string, bikeId: string, status: BikeStatus) =>
    request<Bike>(`/api/bikes/${encodeURIComponent(bikeId)}/status`, {
      method: 'PATCH',
      userId,
      body: { status },
    }),
  listRentals: (userId: string) => request<Rental[]>('/api/rentals', { userId }),
  createRental: (userId: string, bikeId: string) =>
    request<Rental>('/api/rentals', { method: 'POST', userId, body: { bikeId } }),
  returnRental: (userId: string, rentalId: string) =>
    request<Rental>(`/api/rentals/${encodeURIComponent(rentalId)}/return`, { method: 'POST', userId }),
};
