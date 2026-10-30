import { describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { EMPTY_SEARCH, type SearchCriteria } from '../types';
import { BikeSearchForm } from './BikeSearchForm';

function Harness({
  initial,
  summary,
  onChange,
}: {
  initial: SearchCriteria;
  summary: string | null;
  onChange: (value: SearchCriteria) => void;
}) {
  const [value, setValue] = useState(initial);
  return (
    <BikeSearchForm
      value={value}
      summary={summary}
      onChange={(next) => {
        setValue(next);
        onChange(next);
      }}
    />
  );
}

function renderForm(initial: SearchCriteria = EMPTY_SEARCH, summary: string | null = null) {
  const onChange = vi.fn<(value: SearchCriteria) => void>();
  render(<Harness initial={initial} summary={summary} onChange={onChange} />);
  return {
    onChange,
    user: userEvent.setup(),
    nameInput: screen.getByRole('searchbox', { name: 'Név részlete' }),
    typeSelect: screen.getByRole('combobox', { name: 'Típus szűrő' }),
    statusSelect: screen.getByRole('combobox', { name: 'Státusz szűrő' }),
  };
}

const clearButton = () => screen.queryByRole('button', { name: 'Szűrők törlése' });

describe('BikeSearchForm', () => {
  describe('megjelenítés és hozzáférhetőség', () => {
    it('"Bicikli keresése" nevű search tájékozódási pontot jelenít meg', () => {
      renderForm();
      const search = screen.getByRole('search', { name: 'Bicikli keresése' });
      expect(within(search).getByRole('heading', { name: 'Keresés' })).toBeInTheDocument();
    });

    it('a mezők a search területen belül vannak, nevük alapján elérhetők', () => {
      renderForm();
      const search = screen.getByRole('search', { name: 'Bicikli keresése' });
      expect(within(search).getByRole('searchbox', { name: 'Név részlete' })).toBeInTheDocument();
      expect(within(search).getByRole('combobox', { name: 'Típus szűrő' })).toBeInTheDocument();
      expect(within(search).getByRole('combobox', { name: 'Státusz szűrő' })).toBeInTheDocument();
    });

    it('a szövegmező search típusú, 50 karakterre korlátozott, és kezdetben üres', () => {
      const { nameInput } = renderForm();
      expect(nameInput).toHaveAttribute('type', 'search');
      expect(nameInput).toHaveAttribute('maxlength', '50');
      expect(nameInput).toHaveValue('');
    });

    it('a Név részlete címkére kattintva a szövegmezőt éri el', () => {
      const { nameInput } = renderForm();
      expect(screen.getByLabelText('Név részlete')).toBe(nameInput);
    });

    it('a típusszűrő opciói magyar feliratúak, az első a "Minden típus"', () => {
      const { typeSelect } = renderForm();
      expect(within(typeSelect).getAllByRole('option').map((o) => o.textContent)).toEqual([
        'Minden típus',
        'Városi',
        'Hegyi',
        'Elektromos',
      ]);
      expect(typeSelect).toHaveValue('');
    });

    it('a státuszszűrő opciói magyar feliratúak, az első a "Minden státusz"', () => {
      const { statusSelect } = renderForm();
      expect(within(statusSelect).getAllByRole('option').map((o) => o.textContent)).toEqual([
        'Minden státusz',
        'Elérhető',
        'Használatban',
        'Karbantartás alatt',
      ]);
      expect(statusSelect).toHaveValue('');
    });

    it('az opciók értékei az API enumértékei', () => {
      const { typeSelect, statusSelect } = renderForm();
      const values = (select: HTMLElement) => within(select).getAllByRole('option').map((o) => o.getAttribute('value'));
      expect(values(typeSelect)).toEqual(['', 'city', 'mountain', 'electric']);
      expect(values(statusSelect)).toEqual(['', 'available', 'in_use', 'maintenance']);
    });

    it('a megadott feltételeket jeleníti meg a mezőkben', () => {
      const { nameInput, typeSelect, statusSelect } = renderForm({ q: 'hegyi', type: 'mountain', status: 'in_use' });
      expect(nameInput).toHaveValue('hegyi');
      expect(typeSelect).toHaveValue('mountain');
      expect(statusSelect).toHaveValue('in_use');
    });
  });

  describe('változtatások', () => {
    it('gépeléskor minden billentyű után a teljes aktuális feltétellel hívja az onChange-et', async () => {
      const { onChange, user, nameInput } = renderForm();
      await user.type(nameInput, 'ci');
      expect(onChange).toHaveBeenCalledTimes(2);
      expect(onChange).toHaveBeenNthCalledWith(1, { q: 'c', type: '', status: '' });
      expect(onChange).toHaveBeenNthCalledWith(2, { q: 'ci', type: '', status: '' });
    });

    it('a típus kiválasztása megőrzi a többi feltételt', async () => {
      const { onChange, user, typeSelect } = renderForm({ q: 'x', type: '', status: 'available' });
      await user.selectOptions(typeSelect, 'electric');
      expect(onChange).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenCalledWith({ q: 'x', type: 'electric', status: 'available' });
    });

    it('a státusz kiválasztása megőrzi a többi feltételt', async () => {
      const { onChange, user, statusSelect } = renderForm({ q: 'x', type: 'city', status: '' });
      await user.selectOptions(statusSelect, 'maintenance');
      expect(onChange).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenCalledWith({ q: 'x', type: 'city', status: 'maintenance' });
    });

    it('az opció feliratával is választható, és "Minden típus" üres értéket ad', async () => {
      const { onChange, user, typeSelect } = renderForm({ q: '', type: 'mountain', status: '' });
      await user.selectOptions(typeSelect, 'Minden típus');
      expect(onChange).toHaveBeenCalledWith({ q: '', type: '', status: '' });
    });

    it('a "Minden státusz" üres státuszt ad', async () => {
      const { onChange, user, statusSelect } = renderForm({ q: '', type: '', status: 'in_use' });
      await user.selectOptions(statusSelect, 'Minden státusz');
      expect(onChange).toHaveBeenCalledWith({ q: '', type: '', status: '' });
    });

    it('a név 50 karakterre korlátozódik gépeléskor', async () => {
      const { onChange, user, nameInput } = renderForm();
      await user.type(nameInput, 'a'.repeat(60));
      expect(nameInput).toHaveValue('a'.repeat(50));
      expect(onChange).toHaveBeenLastCalledWith({ q: 'a'.repeat(50), type: '', status: '' });
    });

    it('a név törlése üres értéket ad', async () => {
      const { onChange, user, nameInput } = renderForm({ q: 'ab', type: '', status: '' });
      await user.clear(nameInput);
      expect(onChange).toHaveBeenLastCalledWith({ q: '', type: '', status: '' });
    });

    it('a megjelenítés önmagában nem hív onChange-et', () => {
      const { onChange } = renderForm({ q: 'a', type: 'city', status: 'available' });
      expect(onChange).not.toHaveBeenCalled();
    });
  });

  describe('Szűrők törlése gomb', () => {
    it('szűrő nélkül nem látszik', () => {
      renderForm();
      expect(clearButton()).not.toBeInTheDocument();
    });

    it('a csak szóközt tartalmazó névnél nem látszik', () => {
      renderForm({ q: '   ', type: '', status: '' });
      expect(clearButton()).not.toBeInTheDocument();
    });

    it.each<[string, SearchCriteria]>([
      ['névnél', { q: 'city', type: '', status: '' }],
      ['típusnál', { q: '', type: 'city', status: '' }],
      ['státusznál', { q: '', type: '', status: 'available' }],
    ])('aktív szűrő esetén (%s) megjelenik', (_name, criteria) => {
      renderForm(criteria);
      expect(clearButton()).toBeEnabled();
    });

    it('a mező kitöltése után jelenik meg, és az utolsó szűrő törlése után eltűnik', async () => {
      const { user, nameInput } = renderForm();
      await user.type(nameInput, 'a');
      expect(clearButton()).toBeInTheDocument();
      await user.clear(nameInput);
      expect(clearButton()).not.toBeInTheDocument();
    });

    it('kattintásra üres feltételt ad, és kiüríti a mezőket', async () => {
      const { onChange, user, nameInput, typeSelect, statusSelect } = renderForm({
        q: 'hegyi',
        type: 'mountain',
        status: 'available',
      });
      const button = clearButton();
      if (!button) throw new Error('A Szűrők törlése gomb hiányzik');

      await user.click(button);

      expect(onChange).toHaveBeenCalledTimes(1);
      expect(onChange).toHaveBeenCalledWith({ q: '', type: '', status: '' });
      expect(nameInput).toHaveValue('');
      expect(typeSelect).toHaveValue('');
      expect(statusSelect).toHaveValue('');
      expect(clearButton()).not.toBeInTheDocument();
    });

    it('nem küld űrlapot (button type=button)', () => {
      renderForm({ q: 'a', type: '', status: '' });
      expect(clearButton()).toHaveAttribute('type', 'button');
    });
  });

  describe('összegzés', () => {
    it('megjeleníti a kapott összegzést', () => {
      renderForm({ q: 'a', type: '', status: '' }, '2 találat');
      expect(screen.getByText('2 találat')).toBeInTheDocument();
    });

    it('a csonkolt összegzést is változtatás nélkül mutatja', () => {
      renderForm({ q: 'a', type: '', status: '' }, '50 találat megjelenítve, összesen 120');
      expect(screen.getByText('50 találat megjelenítve, összesen 120')).toBeInTheDocument();
    });

    it('udvarias (polite) élő régióban jelenik meg, hogy a képernyőolvasó felolvassa', () => {
      renderForm({ q: 'a', type: '', status: '' }, '3 találat');
      expect(screen.getByText('3 találat')).toHaveAttribute('aria-live', 'polite');
    });

    it('összegzés nélkül az élő régió üres', () => {
      renderForm();
      const search = screen.getByRole('search', { name: 'Bicikli keresése' });
      const region = search.querySelector('[aria-live="polite"]');
      expect(region).toBeInTheDocument();
      expect(region).toBeEmptyDOMElement();
    });
  });

  describe('billentyűzettel való elérhetőség', () => {
    it('Tab-bal sorban a szövegmező, a típus- és a státuszszűrő érhető el', async () => {
      const { user, nameInput, typeSelect, statusSelect } = renderForm();
      await user.tab();
      expect(nameInput).toHaveFocus();
      await user.tab();
      expect(typeSelect).toHaveFocus();
      await user.tab();
      expect(statusSelect).toHaveFocus();
    });

    it('aktív szűrőnél a státuszszűrő után a Szűrők törlése gomb is fókuszálható, Enterrel működik', async () => {
      const { onChange, user, statusSelect } = renderForm({ q: 'a', type: '', status: '' });
      await user.tab();
      await user.tab();
      await user.tab();
      expect(statusSelect).toHaveFocus();
      await user.tab();
      expect(clearButton()).toHaveFocus();
      await user.keyboard('{Enter}');
      expect(onChange).toHaveBeenCalledWith({ q: '', type: '', status: '' });
    });
  });
});
