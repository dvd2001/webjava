import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { BikeStatus } from '../types';
import { StatusBadge } from './StatusBadge';

describe('StatusBadge', () => {
  it.each<[BikeStatus, string]>([
    ['available', 'Elérhető'],
    ['in_use', 'Használatban'],
    ['maintenance', 'Karbantartás alatt'],
  ])('a(z) %s státuszt "%s" felirattal jeleníti meg', (status, label) => {
    render(<StatusBadge status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it.each<BikeStatus>(['available', 'in_use', 'maintenance'])('a(z) %s státusz a megfelelő CSS osztályokat kapja', (status) => {
    render(<StatusBadge status={status} />);
    const badge = screen.getByText(/.+/);
    expect(badge).toHaveClass('badge', `badge-${status}`);
  });
});
