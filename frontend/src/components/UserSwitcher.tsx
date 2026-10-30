import type { User } from '../types';
import { ROLE_LABELS } from '../labels';

interface Props {
  users: User[];
  currentUserId: string;
  onChange: (id: string) => void;
}

export function UserSwitcher({ users, currentUserId, onChange }: Props) {
  return (
    <label className="user-switcher">
      Aktív felhasználó:
      <select value={currentUserId} onChange={(e) => onChange(e.target.value)}>
        {users.map((u) => (
          <option key={u.id} value={u.id}>
            {u.name} ({ROLE_LABELS[u.role]})
          </option>
        ))}
      </select>
    </label>
  );
}
