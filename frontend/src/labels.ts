import type { BikeStatus, BikeType, UserRole } from './types';

export const STATUS_LABELS: Record<BikeStatus, string> = {
  available: 'Elérhető',
  in_use: 'Használatban',
  maintenance: 'Karbantartás alatt',
};

export const TYPE_LABELS: Record<BikeType, string> = {
  city: 'Városi',
  mountain: 'Hegyi',
  electric: 'Elektromos',
};

export const BIKE_TYPES: BikeType[] = ['city', 'mountain', 'electric'];

export const ROLE_LABELS: Record<UserRole, string> = {
  manager: 'Állomásvezető',
  renter: 'Bérlő',
};
