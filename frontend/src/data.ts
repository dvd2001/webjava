import type { AppState, User } from './types';

export const USERS: User[] = [
  { id: 'u1', name: 'Kovács Anna', role: 'manager' },
  { id: 'u2', name: 'Nagy Péter', role: 'renter' },
  { id: 'u3', name: 'Szabó Eszter', role: 'renter' },
  { id: 'u4', name: 'Tóth Gábor', role: 'renter' },
];

export function createInitialState(now: number = Date.now()): AppState {
  return {
    bikes: [
      { id: 'b1', name: 'City-01', type: 'city', status: 'available' },
      { id: 'b2', name: 'City-02', type: 'city', status: 'in_use' },
      { id: 'b3', name: 'Hegyi-01', type: 'mountain', status: 'available' },
      { id: 'b4', name: 'Hegyi-02', type: 'mountain', status: 'maintenance' },
      { id: 'b5', name: 'E-bike-01', type: 'electric', status: 'available' },
      { id: 'b6', name: 'E-bike-02', type: 'electric', status: 'in_use' },
    ],
    rentals: [
      { id: 'r1', bikeId: 'b2', userId: 'u2', startedAt: now - 25 * 60_000 },
      { id: 'r2', bikeId: 'b6', userId: 'u3', startedAt: now - 70 * 60_000 },
      {
        id: 'r0',
        bikeId: 'b1',
        userId: 'u4',
        startedAt: now - 24 * 3600_000,
        endedAt: now - 24 * 3600_000 + 40 * 60_000,
        fee: 300 + 40 * 15,
      },
    ],
    tariffs: {
      city: { baseFee: 300, perMinute: 15 },
      mountain: { baseFee: 400, perMinute: 20 },
      electric: { baseFee: 500, perMinute: 35 },
    },
  };
}
