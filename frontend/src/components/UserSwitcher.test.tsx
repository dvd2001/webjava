import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DEMO_USERS, type DemoUser } from '../demoUsers';
import { UserSwitcher } from './UserSwitcher';

function renderSwitcher(currentUsername = 'peter', users: DemoUser[] = DEMO_USERS) {
  const onChange = vi.fn<(username: string) => void>();
  render(<UserSwitcher users={users} currentUsername={currentUsername} onChange={onChange} />);
  return { onChange, select: screen.getByRole('combobox', { name: 'Aktív felhasználó:' }) };
}

describe('UserSwitcher', () => {
  it('az "Aktív felhasználó:" felirattal összekötött választót jelenít meg', () => {
    const { select } = renderSwitcher();
    expect(select).toBeInTheDocument();
    expect(screen.getByLabelText('Aktív felhasználó:')).toBe(select);
  });

  it('minden demó felhasználót "Név (Szerepkör)" feliratú opcióként kínál, a megadott sorrendben', () => {
    const { select } = renderSwitcher();
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Kovács Anna (Állomásvezető)',
      'Nagy Péter (Bérlő)',
      'Szabó Eszter (Bérlő)',
      'Tóth Gábor (Bérlő)',
    ]);
  });

  it('az opciók értéke a felhasználónév', () => {
    const { select } = renderSwitcher();
    const values = within(select)
      .getAllByRole('option')
      .map((o) => o.getAttribute('value'));
    expect(values).toEqual(['anna', 'peter', 'eszter', 'gabor']);
  });

  it('az aktuális felhasználót jelöli ki', () => {
    const { select } = renderSwitcher('eszter');
    expect(select).toHaveValue('eszter');
    expect(screen.getByRole('option', { name: 'Szabó Eszter (Bérlő)' })).toHaveProperty('selected', true);
    expect(screen.getByRole('option', { name: 'Nagy Péter (Bérlő)' })).toHaveProperty('selected', false);
  });

  it('a megjelenítéssel nem hívja az onChange-et', () => {
    const { onChange } = renderSwitcher();
    expect(onChange).not.toHaveBeenCalled();
  });

  it('másik felhasználó kiválasztásakor a felhasználónévvel hívja az onChange-et', async () => {
    const user = userEvent.setup();
    const { onChange, select } = renderSwitcher('peter');
    await user.selectOptions(select, 'anna');
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith('anna');
  });

  it('az opció feliratával is kiválasztható', async () => {
    const user = userEvent.setup();
    const { onChange, select } = renderSwitcher('peter');
    await user.selectOptions(select, 'Tóth Gábor (Bérlő)');
    expect(onChange).toHaveBeenCalledWith('gabor');
  });

  it('a kiválasztott érték a szülő által átadott felhasználónevet követi', () => {
    const onChange = vi.fn();
    const { rerender } = render(<UserSwitcher users={DEMO_USERS} currentUsername="peter" onChange={onChange} />);
    expect(screen.getByRole('combobox', { name: 'Aktív felhasználó:' })).toHaveValue('peter');
    rerender(<UserSwitcher users={DEMO_USERS} currentUsername="gabor" onChange={onChange} />);
    expect(screen.getByRole('combobox', { name: 'Aktív felhasználó:' })).toHaveValue('gabor');
  });

  it('billentyűzettel (Tab) fókuszálható', async () => {
    const user = userEvent.setup();
    const { select } = renderSwitcher('peter');
    await user.tab();
    expect(select).toHaveFocus();
  });

  it('üres felhasználólistával is megjelenik opciók nélkül', () => {
    const { select } = renderSwitcher('', []);
    expect(within(select).queryAllByRole('option')).toHaveLength(0);
  });
});
