import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { Bike, Tariffs, User } from '../types';
import { makeBike, manager, renter, tariffs } from '../test/fixtures';
import { BikeList } from './BikeList';

const bikes: Bike[] = [
  makeBike({ id: 'b1', name: 'City-01', type: 'city', status: 'available' }),
  makeBike({ id: 'b2', name: 'Hegyi-02', type: 'mountain', status: 'in_use' }),
  makeBike({ id: 'b3', name: 'Villám-03', type: 'electric', status: 'maintenance' }),
];

function renderList(
  user: User,
  options: { bikes?: Bike[]; tariffs?: Tariffs; busy?: boolean } = {},
) {
  const onRent = vi.fn();
  const onChangeStatus = vi.fn();
  render(
    <BikeList
      bikes={options.bikes ?? bikes}
      tariffs={options.tariffs ?? tariffs}
      user={user}
      busy={options.busy ?? false}
      onRent={onRent}
      onChangeStatus={onChangeStatus}
    />,
  );
  return { onRent, onChangeStatus };
}

describe('BikeList', () => {
  describe('közös megjelenítés', () => {
    it('üres listánál tájékoztató szöveget mutat táblázat nélkül', () => {
      renderList(renter, { bikes: [] });
      expect(screen.getByText('Még nincs bicikli az állomáson.')).toBeInTheDocument();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('a biciklik nevét, magyar típusát és státuszát megjeleníti', () => {
      renderList(renter);
      const row = screen.getByRole('row', { name: /Hegyi-02/ });
      expect(within(row).getByText('Hegyi')).toBeInTheDocument();
      expect(within(row).getByText('Használatban')).toBeInTheDocument();
      expect(screen.getByRole('row', { name: /Villám-03/ })).toHaveTextContent('Elektromos');
      expect(screen.getByRole('row', { name: /Villám-03/ })).toHaveTextContent('Karbantartás alatt');
    });

    it('biciklinként egy sort jelenít meg a fejléc mellett', () => {
      renderList(renter);
      expect(screen.getAllByRole('row')).toHaveLength(bikes.length + 1);
      expect(screen.getByRole('columnheader', { name: 'Státusz' })).toBeInTheDocument();
    });

    it('a tarifát kezdődíj + percdíj formában írja ki', () => {
      renderList(renter);
      expect(screen.getByRole('row', { name: /City-01/ })).toHaveTextContent('300 Ft + 15 Ft/perc');
      expect(screen.getByRole('row', { name: /Hegyi-02/ })).toHaveTextContent('500 Ft + 25 Ft/perc');
    });

    it('ismeretlen tarifánál kötőjelet mutat', () => {
      renderList(renter, { bikes: [bikes[0]], tariffs: {} });
      expect(screen.getByRole('row', { name: /City-01/ })).toHaveTextContent(/-/);
      expect(screen.queryByText(/Ft/)).not.toBeInTheDocument();
    });
  });

  describe('bérlő', () => {
    it('minden biciklihez Bérlés gombot mutat, állapotszerkesztő nélkül', () => {
      renderList(renter);
      expect(screen.getAllByRole('button', { name: /bérlése$/ })).toHaveLength(bikes.length);
      expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    });

    it('csak az elérhető biciklinél engedélyezi a Bérlés gombot', () => {
      renderList(renter);
      expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeEnabled();
      expect(screen.getByRole('button', { name: 'Hegyi-02 bérlése' })).toBeDisabled();
      expect(screen.getByRole('button', { name: 'Villám-03 bérlése' })).toBeDisabled();
    });

    it('a Bérlés gomb a megfelelő biciklivel hívja az onRent-et', async () => {
      const user = userEvent.setup();
      const { onRent } = renderList(renter);
      await user.click(screen.getByRole('button', { name: 'City-01 bérlése' }));
      expect(onRent).toHaveBeenCalledTimes(1);
      expect(onRent).toHaveBeenCalledWith(bikes[0]);
    });

    it('a letiltott Bérlés gomb kattintásra nem hív visszahívást', async () => {
      const user = userEvent.setup();
      const { onRent } = renderList(renter);
      await user.click(screen.getByRole('button', { name: 'Hegyi-02 bérlése' }));
      expect(onRent).not.toHaveBeenCalled();
    });

    it('foglalt (busy) állapotban az elérhető bicikli Bérlés gombja is le van tiltva', () => {
      renderList(renter, { busy: true });
      expect(screen.getByRole('button', { name: 'City-01 bérlése' })).toBeDisabled();
    });
  });

  describe('állomásvezető', () => {
    it('nem mutat Bérlés gombot', () => {
      renderList(manager);
      expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });

    it('az elérhető és a karbantartás alatti biciklihez státuszválasztót mutat a jelenlegi értékkel', () => {
      renderList(manager);
      expect(screen.getByRole('combobox', { name: 'City-01 státusza' })).toHaveValue('available');
      expect(screen.getByRole('combobox', { name: 'Villám-03 státusza' })).toHaveValue('maintenance');
    });

    it('a státuszválasztó két magyar nevű opciót kínál', () => {
      renderList(manager);
      const select = screen.getByRole('combobox', { name: 'City-01 státusza' });
      expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual([
        'Elérhető',
        'Karbantartás alatt',
      ]);
    });

    it('a használatban lévő biciklinél nem enged módosítást', () => {
      renderList(manager);
      expect(screen.getByText('Visszavételig nem módosítható')).toBeInTheDocument();
      expect(screen.queryByRole('combobox', { name: 'Hegyi-02 státusza' })).not.toBeInTheDocument();
    });

    it('a státusz módosítása az új státusszal hívja az onChangeStatus-t', async () => {
      const user = userEvent.setup();
      const { onChangeStatus } = renderList(manager);
      await user.selectOptions(screen.getByRole('combobox', { name: 'City-01 státusza' }), 'maintenance');
      expect(onChangeStatus).toHaveBeenCalledTimes(1);
      expect(onChangeStatus).toHaveBeenCalledWith(bikes[0], 'maintenance');
    });

    it('a karbantartásból elérhetőre állítás is továbbítódik', async () => {
      const user = userEvent.setup();
      const { onChangeStatus } = renderList(manager);
      await user.selectOptions(screen.getByRole('combobox', { name: 'Villám-03 státusza' }), 'available');
      expect(onChangeStatus).toHaveBeenCalledWith(bikes[2], 'available');
    });

    it('foglalt (busy) állapotban a státuszválasztók le vannak tiltva', () => {
      renderList(manager, { busy: true });
      expect(screen.getByRole('combobox', { name: 'City-01 státusza' })).toBeDisabled();
      expect(screen.getByRole('combobox', { name: 'Villám-03 státusza' })).toBeDisabled();
    });
  });
});
