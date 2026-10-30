import { USERS } from '../data';
import { ROLE_LABELS } from '../labels';

interface Props {
  currentUserId: string;
  onChange: (id: string) => void;
}

export function UserSwitcher({ currentUserId, onChange }: Props) {
  return (
    <label className="user-switcher">
      Aktív felhasználó:
      <select value={currentUserId} onChange={(e) => onChange(e.target.value)}>
        {USERS.map((u) => (
          <option key={u.id} value={u.id}>
            {u.name} ({ROLE_LABELS[u.role]})
          </option>
        ))}
      </select>
    </label>
  );
}
