import { describe, expect, it } from 'vitest';
import { BIKE_TYPES, ROLE_LABELS, STATUS_LABELS, TYPE_LABELS } from './labels';
import type { BikeStatus, BikeType, UserRole } from './types';

const statuses: BikeStatus[] = ['available', 'in_use', 'maintenance'];
const types: BikeType[] = ['city', 'mountain', 'electric'];
const roles: UserRole[] = ['manager', 'renter'];

describe('STATUS_LABELS', () => {
  it.each(statuses)('a(z) %s státusznak van nem üres magyar felirata', (status) => {
    expect(STATUS_LABELS[status]).toBeTruthy();
  });

  it('a státuszfeliratok pontosak és egyediek', () => {
    expect(STATUS_LABELS).toEqual({
      available: 'Elérhető',
      in_use: 'Használatban',
      maintenance: 'Karbantartás alatt',
    });
    expect(new Set(Object.values(STATUS_LABELS)).size).toBe(statuses.length);
  });
});

describe('TYPE_LABELS', () => {
  it.each(types)('a(z) %s típusnak van nem üres magyar felirata', (type) => {
    expect(TYPE_LABELS[type]).toBeTruthy();
  });

  it('a típusfeliratok pontosak', () => {
    expect(TYPE_LABELS).toEqual({ city: 'Városi', mountain: 'Hegyi', electric: 'Elektromos' });
  });
});

describe('ROLE_LABELS', () => {
  it.each(roles)('a(z) %s szerepkörnek van nem üres magyar felirata', (role) => {
    expect(ROLE_LABELS[role]).toBeTruthy();
  });

  it('a szerepkörfeliratok pontosak', () => {
    expect(ROLE_LABELS).toEqual({ manager: 'Állomásvezető', renter: 'Bérlő' });
  });
});

describe('BIKE_TYPES', () => {
  it('az összes biciklitípust tartalmazza pontosan egyszer', () => {
    expect([...BIKE_TYPES].sort()).toEqual([...types].sort());
  });

  it('minden elemének van felirata', () => {
    for (const type of BIKE_TYPES) expect(TYPE_LABELS[type]).toBeTruthy();
  });

  it('a feliratok kulcsai megegyeznek a BIKE_TYPES elemeivel', () => {
    expect(Object.keys(TYPE_LABELS).sort()).toEqual([...BIKE_TYPES].sort());
  });
});
