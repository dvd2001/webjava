export type BikeStatus = 'available' | 'in_use' | 'maintenance';

export type BikeType = 'city' | 'mountain' | 'electric';

export interface Bike {
  id: string;
  name: string;
  type: BikeType;
  status: BikeStatus;
}

export type UserRole = 'manager' | 'renter';

export interface User {
  id: string;
  name: string;
  role: UserRole;
}

export interface Rental {
  id: string;
  bikeId: string;
  userId: string;
  startedAt: number;
  endedAt?: number;
  fee?: number;
}

export interface Tariff {
  /** Egyszeri kezdődíj (Ft) */
  baseFee: number;
  /** Percdíj (Ft), megkezdett percenként */
  perMinute: number;
}

export interface AppState {
  bikes: Bike[];
  rentals: Rental[];
  tariffs: Record<BikeType, Tariff>;
}
