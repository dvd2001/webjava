import { describe, expect, it } from 'vitest';
import { DEFAULT_DEMO_USERNAME, DEMO_PASSWORD, DEMO_USERS } from './demoUsers';
import { ROLE_LABELS } from './labels';

describe('DEMO_USERS', () => {
  it('pontosan a négy várt demó felhasználót tartalmazza névvel és szerepkörrel', () => {
    expect(DEMO_USERS).toEqual([
      { username: 'anna', name: 'Kovács Anna', role: 'manager' },
      { username: 'peter', name: 'Nagy Péter', role: 'renter' },
      { username: 'eszter', name: 'Szabó Eszter', role: 'renter' },
      { username: 'gabor', name: 'Tóth Gábor', role: 'renter' },
    ]);
  });

  it('a felhasználónevek egyediek, kisbetűsek és nem üresek', () => {
    const usernames = DEMO_USERS.map((u) => u.username);
    expect(new Set(usernames).size).toBe(usernames.length);
    for (const username of usernames) expect(username).toMatch(/^[a-z]+$/);
  });

  it('a megjelenített nevek egyediek', () => {
    const names = DEMO_USERS.map((u) => u.name);
    expect(new Set(names).size).toBe(names.length);
  });

  it('pontosan egy állomásvezetője van, a többiek bérlők', () => {
    expect(DEMO_USERS.filter((u) => u.role === 'manager')).toHaveLength(1);
    expect(DEMO_USERS.filter((u) => u.role === 'renter')).toHaveLength(DEMO_USERS.length - 1);
  });

  it('minden szerepkörnek van magyar felirata', () => {
    for (const user of DEMO_USERS) expect(ROLE_LABELS[user.role]).toBeTruthy();
  });
});

describe('DEFAULT_DEMO_USERNAME', () => {
  it('az alapértelmezett felhasználó létezik a demó felhasználók között', () => {
    expect(DEMO_USERS.map((u) => u.username)).toContain(DEFAULT_DEMO_USERNAME);
  });

  it('az alapértelmezett felhasználó bérlő', () => {
    expect(DEMO_USERS.find((u) => u.username === DEFAULT_DEMO_USERNAME)?.role).toBe('renter');
  });
});

describe('DEMO_PASSWORD', () => {
  it('nem üres szöveg', () => {
    expect(typeof DEMO_PASSWORD).toBe('string');
    expect(DEMO_PASSWORD.length).toBeGreaterThan(0);
  });
});
