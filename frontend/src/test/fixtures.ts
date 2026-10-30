import type { Bike, Rental, Tariffs, User } from '../types';

export const renter: User = { id: 'u1', name: 'Kiss Réka', role: 'renter' };
export const manager: User = { id: 'u2', name: 'Nagy Péter', role: 'manager' };

export const tariffs: Tariffs = {
  city: { baseFee: 300, perMinute: 15 },
  mountain: { baseFee: 500, perMinute: 25 },
  electric: { baseFee: 1000, perMinute: 40 },
};

export function makeBike(overrides: Partial<Bike> = {}): Bike {
  return { id: 'b1', name: 'City-01', type: 'city', status: 'available', ...overrides };
}

export function makeRental(overrides: Partial<Rental> = {}): Rental {
  return {
    id: 'r1',
    bikeId: 'b1',
    userId: 'u1',
    startedAt: new Date(2025, 4, 10, 9, 15, 0).getTime(),
    elapsedSeconds: 0,
    currentFee: 300,
    ...overrides,
  };
}
