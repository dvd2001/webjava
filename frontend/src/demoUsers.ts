import type { UserRole } from './types';

export interface DemoUser {
  username: string;
  name: string;
  role: UserRole;
}

/**
 * CSAK DEMÓHOZ: a felhasználóváltó ezekkel a példafelhasználókkal jelentkezik be csendben a háttérrendszerbe
 * (a backend minden kérést tokennel hitelesít). Valódi alkalmazásban itt bejelentkező űrlap lenne,
 * és a jelszó nem kerülhetne a kliens kódjába.
 */
export const DEMO_USERS: DemoUser[] = [
  { username: 'anna', name: 'Kovács Anna', role: 'manager' },
  { username: 'peter', name: 'Nagy Péter', role: 'renter' },
  { username: 'eszter', name: 'Szabó Eszter', role: 'renter' },
  { username: 'gabor', name: 'Tóth Gábor', role: 'renter' },
];

export const DEFAULT_DEMO_USERNAME = 'peter';

/** A backend fejlesztői demo jelszava; a VITE_DEMO_PASSWORD környezeti változóval felülírható. */
export const DEMO_PASSWORD: string = import.meta.env.VITE_DEMO_PASSWORD ?? 'Demo-Kerekpar-2026';
