import { describe, expect, it } from 'vitest';
import { EMPTY_SEARCH, isSearchActive, type SearchCriteria } from './types';

describe('EMPTY_SEARCH', () => {
  it('minden feltétele üres', () => {
    expect(EMPTY_SEARCH).toEqual({ q: '', type: '', status: '' });
  });

  it('nem aktív szűrő', () => {
    expect(isSearchActive(EMPTY_SEARCH)).toBe(false);
  });
});

describe('isSearchActive', () => {
  it('üres feltételeknél nem aktív', () => {
    expect(isSearchActive({ q: '', type: '', status: '' })).toBe(false);
  });

  it.each([' ', '   ', '\t', '\n', ' \t\n '])('a csak szóközt tartalmazó (%j) név nem számít aktív szűrőnek', (q) => {
    expect(isSearchActive({ q, type: '', status: '' })).toBe(false);
  });

  it('a név megadása aktív szűrő', () => {
    expect(isSearchActive({ q: 'city', type: '', status: '' })).toBe(true);
  });

  it('a szóközökkel körbevett név is aktív szűrő', () => {
    expect(isSearchActive({ q: '  c ', type: '', status: '' })).toBe(true);
  });

  it.each<SearchCriteria['type']>(['city', 'mountain', 'electric'])('a(z) %s típus aktív szűrő', (type) => {
    expect(isSearchActive({ q: '', type, status: '' })).toBe(true);
  });

  it.each<SearchCriteria['status']>(['available', 'in_use', 'maintenance'])('a(z) %s státusz aktív szűrő', (status) => {
    expect(isSearchActive({ q: '', type: '', status })).toBe(true);
  });

  it('szóközös névvel együtt a típus vagy státusz akkor is aktívvá teszi', () => {
    expect(isSearchActive({ q: '  ', type: 'city', status: '' })).toBe(true);
    expect(isSearchActive({ q: '  ', type: '', status: 'available' })).toBe(true);
  });

  it('több feltétel együtt is aktív', () => {
    expect(isSearchActive({ q: 'a', type: 'city', status: 'available' })).toBe(true);
  });

  it('nem módosítja a kapott feltételt', () => {
    const criteria: SearchCriteria = { q: ' x ', type: '', status: '' };
    isSearchActive(criteria);
    expect(criteria).toEqual({ q: ' x ', type: '', status: '' });
  });
});
