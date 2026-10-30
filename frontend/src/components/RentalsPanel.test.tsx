import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { Bike, Rental, User } from '../types';
import { formatDateTime } from '../format';
import { makeBike, makeRental, manager, renter } from '../test/fixtures';
import { RentalsPanel } from './RentalsPanel';

const bikes: Bike[] = [
  makeBike({ id: 'b1', name: 'City-01' }),
  makeBike({ id: 'b2', name: 'Hegyi-02', type: 'mountain' }),
];
const users: User[] = [renter, manager];

const FETCHED_AT = 1_700_000_000_000;

const active = makeRental({ id: 'r1', bikeId: 'b1', userId: 'u1', elapsedSeconds: 125, currentFee: 450 });
const finishedOlder = makeRental({
  id: 'r2',
  bikeId: 'b2',
  userId: 'u1',
  startedAt: new Date(2025, 4, 9, 8, 0, 0).getTime(),
  endedAt: new Date(2025, 4, 9, 8, 30, 0).getTime(),
  elapsedSeconds: 1800,
  currentFee: 1000,
  fee: 12345,
});
const finishedNewer = makeRental({
  id: 'r3',
  bikeId: 'b1',
  userId: 'u1',
  startedAt: new Date(2025, 4, 9, 10, 0, 0).getTime(),
  endedAt: new Date(2025, 4, 9, 10, 1, 0).getTime(),
  elapsedSeconds: 60,
  currentFee: 315,
  fee: 315,
});

function renderPanel(
  user: User,
  options: { rentals?: Rental[]; bikes?: Bike[]; busy?: boolean; now?: number } = {},
) {
  const onReturn = vi.fn();
  render(
    <RentalsPanel
      rentals={options.rentals ?? [active, finishedOlder, finishedNewer]}
      bikes={options.bikes ?? bikes}
      users={users}
      user={user}
      busy={options.busy ?? false}
      fetchedAt={FETCHED_AT}
      now={options.now ?? FETCHED_AT}
      onReturn={onReturn}
    />,
  );
  return { onReturn };
}

describe('RentalsPanel', () => {
  describe('címek és üres állapotok', () => {
    it('bérlőnek "Saját bérléseim" címet mutat', () => {
      renderPanel(renter);
      expect(screen.getByRole('heading', { level: 2, name: 'Saját bérléseim' })).toBeInTheDocument();
    });

    it('állomásvezetőnek "Bérlések" címet mutat', () => {
      renderPanel(manager);
      expect(screen.getByRole('heading', { level: 2, name: 'Bérlések' })).toBeInTheDocument();
    });

    it('bérlések nélkül mindkét üres állapot szövegét mutatja táblázat nélkül', () => {
      renderPanel(renter, { rentals: [] });
      expect(screen.getByText('Nincs aktív bérlés.')).toBeInTheDocument();
      expect(screen.getByText('Nincs lezárt bérlés.')).toBeInTheDocument();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('csak aktív bérlésnél a lezártaknál mutat üres állapotot', () => {
      renderPanel(renter, { rentals: [active] });
      expect(screen.queryByText('Nincs aktív bérlés.')).not.toBeInTheDocument();
      expect(screen.getByText('Nincs lezárt bérlés.')).toBeInTheDocument();
      expect(screen.getAllByRole('table')).toHaveLength(1);
    });

    it('csak lezárt bérlésnél az aktívaknál mutat üres állapotot', () => {
      renderPanel(renter, { rentals: [finishedNewer] });
      expect(screen.getByText('Nincs aktív bérlés.')).toBeInTheDocument();
      expect(screen.queryByText('Nincs lezárt bérlés.')).not.toBeInTheDocument();
      expect(screen.getAllByRole('table')).toHaveLength(1);
    });
  });

  describe('aktív bérlések', () => {
    it('megjeleníti a bicikli nevét, a kezdést, az eltelt időt és az eddigi díjat', () => {
      renderPanel(renter);
      const [activeTable] = screen.getAllByRole('table');
      const row = within(activeTable).getByRole('row', { name: /City-01/ });
      expect(row).toHaveTextContent(formatDateTime(active.startedAt));
      expect(row).toHaveTextContent('02:05');
      expect(row).toHaveTextContent('450 Ft');
    });

    it('az eltelt időt a lista beérkezése óta eltelt idővel növeli', () => {
      renderPanel(renter, { rentals: [active], now: FETCHED_AT + 30_000 });
      expect(screen.getByRole('row', { name: /City-01/ })).toHaveTextContent('02:35');
    });

    it('a tört másodperceket lefelé kerekíti', () => {
      renderPanel(renter, { rentals: [active], now: FETCHED_AT + 1_900 });
      expect(screen.getByRole('row', { name: /City-01/ })).toHaveTextContent('02:06');
    });

    it('a Visszavétel gomb hozzáférhető névvel rendelkezik és a bérléssel hívja az onReturn-t', async () => {
      const user = userEvent.setup();
      const { onReturn } = renderPanel(renter);
      await user.click(screen.getByRole('button', { name: 'City-01 visszavétele' }));
      expect(onReturn).toHaveBeenCalledTimes(1);
      expect(onReturn).toHaveBeenCalledWith(active);
    });

    it('foglalt (busy) állapotban letiltja a Visszavétel gombot', async () => {
      const user = userEvent.setup();
      const { onReturn } = renderPanel(renter, { busy: true });
      const button = screen.getByRole('button', { name: 'City-01 visszavétele' });
      expect(button).toBeDisabled();
      await user.click(button);
      expect(onReturn).not.toHaveBeenCalled();
    });

    it('ismeretlen biciklinél az azonosítót mutatja névként', () => {
      renderPanel(renter, { rentals: [makeRental({ bikeId: 'ismeretlen-9' })], bikes: [] });
      expect(screen.getByRole('button', { name: 'ismeretlen-9 visszavétele' })).toBeInTheDocument();
    });
  });

  describe('lezárt bérlések', () => {
    it('a végső díjat (fee) mutatja, és nincs rajtuk Visszavétel gomb', () => {
      renderPanel(renter, { rentals: [finishedOlder] });
      const row = screen.getByRole('row', { name: /Hegyi-02/ });
      expect(row).toHaveTextContent('30:00');
      expect(row).toHaveTextContent(/12\s345 Ft/);
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('fee hiányában az aktuális díjat (currentFee) mutatja', () => {
      renderPanel(renter, { rentals: [makeRental({ endedAt: FETCHED_AT, elapsedSeconds: 90, currentFee: 777 })] });
      expect(screen.getByRole('row', { name: /City-01/ })).toHaveTextContent('777 Ft');
    });

    it('a lezárt bérléseket fordított sorrendben (legutóbbi felül) listázza', () => {
      renderPanel(renter, { rentals: [finishedOlder, finishedNewer] });
      const rows = screen.getAllByRole('row').slice(1);
      expect(rows[0]).toHaveTextContent('City-01');
      expect(rows[1]).toHaveTextContent('Hegyi-02');
    });

    it('nem módosítja a kapott bérlések tömbjét', () => {
      const rentals = [finishedOlder, finishedNewer];
      renderPanel(renter, { rentals });
      expect(rentals).toEqual([finishedOlder, finishedNewer]);
    });
  });

  describe('szerepkör szerinti oszlopok', () => {
    it('állomásvezetőnek Bérlő oszlopot mutat a bérlő nevével mindkét táblában', () => {
      renderPanel(manager);
      expect(screen.getAllByRole('columnheader', { name: 'Bérlő' })).toHaveLength(2);
      expect(screen.getByRole('row', { name: /City-01.*Kiss Réka.*02:05/ })).toBeInTheDocument();
      expect(screen.getByRole('row', { name: /Hegyi-02.*Kiss Réka/ })).toBeInTheDocument();
    });

    it('bérlőnek nincs Bérlő oszlopa', () => {
      renderPanel(renter);
      expect(screen.queryByRole('columnheader', { name: 'Bérlő' })).not.toBeInTheDocument();
    });

    it('ismeretlen bérlőnél az azonosítót mutatja', () => {
      renderPanel(manager, { rentals: [makeRental({ userId: 'u-ismeretlen' })] });
      expect(screen.getByRole('row', { name: /City-01.*u-ismeretlen/ })).toBeInTheDocument();
    });

    it('az állomásvezető is visszaveheti a bérlést', async () => {
      const user = userEvent.setup();
      const { onReturn } = renderPanel(manager);
      await user.click(screen.getByRole('button', { name: 'City-01 visszavétele' }));
      expect(onReturn).toHaveBeenCalledWith(active);
    });
  });
});
