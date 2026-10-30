import type { DemoUser } from '../demoUsers';
import { ROLE_LABELS } from '../labels';

interface Props {
  users: DemoUser[];
  currentUsername: string;
  onChange: (username: string) => void;
}

export function UserSwitcher({ users, currentUsername, onChange }: Props) {
  return (
    <label className="user-switcher">
      Aktív felhasználó:
      <select value={currentUsername} onChange={(e) => onChange(e.target.value)}>
        {users.map((u) => (
          <option key={u.username} value={u.username}>
            {u.name} ({ROLE_LABELS[u.role]})
          </option>
        ))}
      </select>
    </label>
  );
}
