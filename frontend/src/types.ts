import type { components } from './api/schema';

type Schemas = components['schemas'];

export type Bike = Schemas['Bike'];
export type BikeStatus = Schemas['BikeStatus'];
export type BikeType = Schemas['BikeType'];
export type User = Schemas['User'];
export type UserRole = Schemas['UserRole'];
export type Rental = Schemas['Rental'];
export type Tariff = Schemas['Tariff'];
export type LoginResponse = Schemas['LoginResponse'];
export type BikeSearchResult = Schemas['BikeSearchResult'];

/** A keresőűrlap állapota; az üres érték "nincs szűrés". */
export interface SearchCriteria {
  q: string;
  type: BikeType | '';
  status: BikeStatus | '';
}

export const EMPTY_SEARCH: SearchCriteria = { q: '', type: '', status: '' };

export function isSearchActive(criteria: SearchCriteria): boolean {
  return criteria.q.trim() !== '' || criteria.type !== '' || criteria.status !== '';
}

/** A tarifák biciklitípus szerint kulcsolva (a szerződés additionalProperties típusa). */
export type Tariffs = Record<string, Tariff>;
