import type { AppState, Bike, BikeStatus, BikeType, Rental, User } from './types';
import { calculateFee } from './pricing';

export type Result = { ok: true; state: AppState; message: string } | { ok: false; message: string };

const fail = (message: string): Result => ({ ok: false, message });

export function addBike(state: AppState, actor: User, name: string, type: BikeType): Result {
  if (actor.role !== 'manager') return fail('Új biciklit csak az állomásvezető vehet fel.');
  const trimmed = name.trim();
  if (trimmed.length < 2) return fail('A bicikli neve legalább 2 karakter legyen.');
  if (state.bikes.some((b) => b.name.toLowerCase() === trimmed.toLowerCase())) {
    return fail('Már létezik ilyen nevű bicikli.');
  }
  const bike: Bike = { id: `b${Date.now()}`, name: trimmed, type, status: 'available' };
  return { ok: true, state: { ...state, bikes: [...state.bikes, bike] }, message: `${trimmed} felvéve.` };
}

export function changeStatus(
  state: AppState,
  actor: User,
  bikeId: string,
  status: BikeStatus,
): Result {
  if (actor.role !== 'manager') return fail('Az állapotot csak az állomásvezető módosíthatja.');
  const bike = state.bikes.find((b) => b.id === bikeId);
  if (!bike) return fail('A bicikli nem található.');
  if (bike.status === 'in_use') return fail('Használatban lévő bicikli állapota csak visszavétellel változtatható.');
  if (status === 'in_use') return fail('A "használatban" állapot csak bérléssel állítható be.');
  return {
    ok: true,
    state: { ...state, bikes: state.bikes.map((b) => (b.id === bikeId ? { ...b, status } : b)) },
    message: `${bike.name} állapota módosítva.`,
  };
}

export function rentBike(state: AppState, actor: User, bikeId: string, now: number): Result {
  if (actor.role !== 'renter') return fail('Bérelni csak bérlőként lehet.');
  const bike = state.bikes.find((b) => b.id === bikeId);
  if (!bike) return fail('A bicikli nem található.');
  if (bike.status !== 'available') return fail('Ez a bicikli jelenleg nem bérelhető.');
  const rental: Rental = { id: `r${now}`, bikeId, userId: actor.id, startedAt: now };
  return {
    ok: true,
    state: {
      ...state,
      bikes: state.bikes.map((b) => (b.id === bikeId ? { ...b, status: 'in_use' } : b)),
      rentals: [...state.rentals, rental],
    },
    message: `${bike.name} kibérelve.`,
  };
}

export function returnBike(state: AppState, actor: User, rentalId: string, now: number): Result {
  const rental = state.rentals.find((r) => r.id === rentalId);
  if (!rental || rental.endedAt !== undefined) return fail('Nincs ilyen aktív bérlés.');
  if (actor.role !== 'manager' && rental.userId !== actor.id) {
    return fail('Csak a saját bérlésedet zárhatod le.');
  }
  const bike = state.bikes.find((b) => b.id === rental.bikeId);
  if (!bike) return fail('A bicikli nem található.');
  const fee = calculateFee(state.tariffs[bike.type], rental.startedAt, now);
  return {
    ok: true,
    state: {
      ...state,
      bikes: state.bikes.map((b) => (b.id === bike.id ? { ...b, status: 'available' } : b)),
      rentals: state.rentals.map((r) => (r.id === rentalId ? { ...r, endedAt: now, fee } : r)),
    },
    message: `${bike.name} visszavéve.`,
  };
}
