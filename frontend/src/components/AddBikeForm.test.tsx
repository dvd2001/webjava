import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AddBikeForm } from './AddBikeForm';

function renderForm(options: { busy?: boolean; result?: boolean } = {}) {
  const onAdd = vi.fn<(name: string, type: string) => Promise<boolean>>().mockResolvedValue(options.result ?? true);
  render(<AddBikeForm busy={options.busy ?? false} onAdd={onAdd} />);
  return { onAdd };
}

describe('AddBikeForm', () => {
  it('megjeleníti a címet, a mezőket és a hozzáadás gombot', () => {
    renderForm();
    expect(screen.getByRole('heading', { name: 'Új bicikli felvétele' })).toBeInTheDocument();
    expect(screen.getByLabelText('Név / azonosító')).toHaveValue('');
    expect(screen.getByLabelText('Típus')).toHaveValue('city');
    expect(screen.getByRole('button', { name: 'Hozzáadás' })).toBeEnabled();
  });

  it('a típusválasztó mindhárom magyar nevű típust kínálja', () => {
    renderForm();
    const options = screen.getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['Városi', 'Hegyi', 'Elektromos']);
  });

  it('a névmező kötelező, legalább két karakteres', () => {
    renderForm();
    const input = screen.getByLabelText('Név / azonosító');
    expect(input).toBeRequired();
    expect(input).toHaveAttribute('minlength', '2');
  });

  it('beküldéskor a beírt névvel és az alapértelmezett típussal hívja az onAdd-ot', async () => {
    const user = userEvent.setup();
    const { onAdd } = renderForm();
    await user.type(screen.getByLabelText('Név / azonosító'), 'Városi-07');
    await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));
    expect(onAdd).toHaveBeenCalledTimes(1);
    expect(onAdd).toHaveBeenCalledWith('Városi-07', 'city');
  });

  it('a kiválasztott típust továbbítja', async () => {
    const user = userEvent.setup();
    const { onAdd } = renderForm();
    await user.type(screen.getByLabelText('Név / azonosító'), 'Villám-01');
    await user.selectOptions(screen.getByLabelText('Típus'), 'electric');
    await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));
    expect(onAdd).toHaveBeenCalledWith('Villám-01', 'electric');
  });

  it('az Enter lenyomása a névmezőben is beküldi az űrlapot', async () => {
    const user = userEvent.setup();
    const { onAdd } = renderForm();
    await user.type(screen.getByLabelText('Név / azonosító'), 'Hegyi-05{Enter}');
    expect(onAdd).toHaveBeenCalledWith('Hegyi-05', 'city');
  });

  it('sikeres hozzáadás után törli a nevet, a típust megtartja', async () => {
    const user = userEvent.setup();
    renderForm({ result: true });
    await user.type(screen.getByLabelText('Név / azonosító'), 'Hegyi-05');
    await user.selectOptions(screen.getByLabelText('Típus'), 'mountain');
    await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));
    await waitFor(() => expect(screen.getByLabelText('Név / azonosító')).toHaveValue(''));
    expect(screen.getByLabelText('Típus')).toHaveValue('mountain');
  });

  it('sikertelen hozzáadás után megtartja a beírt nevet', async () => {
    const user = userEvent.setup();
    const { onAdd } = renderForm({ result: false });
    await user.type(screen.getByLabelText('Név / azonosító'), 'Hegyi-05');
    await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));
    await waitFor(() => expect(onAdd).toHaveBeenCalledTimes(1));
    expect(screen.getByLabelText('Név / azonosító')).toHaveValue('Hegyi-05');
  });

  it('üres névvel nem hívja az onAdd-ot', async () => {
    const user = userEvent.setup();
    const { onAdd } = renderForm();
    await user.click(screen.getByRole('button', { name: 'Hozzáadás' }));
    expect(onAdd).not.toHaveBeenCalled();
  });

  it('foglalt (busy) állapotban letiltja a hozzáadás gombot', async () => {
    const user = userEvent.setup();
    const { onAdd } = renderForm({ busy: true });
    const button = screen.getByRole('button', { name: 'Hozzáadás' });
    expect(button).toBeDisabled();
    await user.type(screen.getByLabelText('Név / azonosító'), 'Hegyi-05');
    await user.click(button);
    expect(onAdd).not.toHaveBeenCalled();
  });
});
