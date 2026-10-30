import type { components } from './api/schema';

type Schemas = components['schemas'];

export type Bike = Schemas['Bike'];
export type BikeStatus = Schemas['BikeStatus'];
export type BikeType = Schemas['BikeType'];
export type User = Schemas['User'];
export type UserRole = Schemas['UserRole'];
export type Rental = Schemas['Rental'];
export type Tariff = Schemas['Tariff'];

/** A tarifák biciklitípus szerint kulcsolva (a szerződés additionalProperties típusa). */
export type Tariffs = Record<string, Tariff>;
