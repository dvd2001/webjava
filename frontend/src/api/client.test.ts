import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, setAuthToken, setUnauthorizedHandler } from './client';

const fetchMock = vi.fn<typeof fetch>();

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function respondWith(body: unknown, status = 200) {
  fetchMock.mockImplementation(async () => jsonResponse(body, status));
}

function lastCall(): { url: string; init: RequestInit; headers: Headers } {
  const call = fetchMock.mock.calls.at(-1);
  if (!call) throw new Error('A fetch nem lett meghívva.');
  const [input, init = {}] = call;
  return { url: String(input), init, headers: new Headers(init.headers) };
}

async function rejectionOf(promise: Promise<unknown>): Promise<unknown> {
  try {
    await promise;
  } catch (error) {
    return error;
  }
  throw new Error('A hívásnak hibával kellett volna végződnie.');
}

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  setAuthToken(null);
  setUnauthorizedHandler(null);
  vi.unstubAllGlobals();
});

describe('fejlécek', () => {
  it('minden kérés Accept: application/json fejlécet küld', async () => {
    respondWith([]);
    await api.listBikes();
    expect(lastCall().headers.get('Accept')).toBe('application/json');
  });

  it('törzs nélküli kérésnél nem küld Content-Type fejlécet', async () => {
    respondWith([]);
    await api.listBikes();
    expect(lastCall().headers.has('Content-Type')).toBe(false);
    expect(lastCall().init.body).toBeUndefined();
  });

  it('törzses kérésnél JSON Content-Type fejlécet küld', async () => {
    respondWith({ id: 'b1', name: 'City-01', type: 'city', status: 'available' }, 201);
    await api.createBike('City-01', 'city');
    expect(lastCall().headers.get('Content-Type')).toBe('application/json');
  });

  it('token nélkül nem küld Authorization fejlécet', async () => {
    respondWith([]);
    await api.listBikes();
    expect(lastCall().headers.has('Authorization')).toBe(false);
  });

  it('beállított tokennel Bearer Authorization fejlécet küld a hitelesített kérésekre', async () => {
    setAuthToken('titkos-token');
    respondWith([]);
    await api.listBikes();
    expect(lastCall().headers.get('Authorization')).toBe('Bearer titkos-token');
  });

  it('a bejelentkezés nem küld tokent akkor sem, ha van beállított token', async () => {
    setAuthToken('titkos-token');
    respondWith({ accessToken: 'uj', tokenType: 'Bearer', expiresAt: 1, user: { id: 'u1', name: 'Péter', role: 'renter' } });
    await api.login('peter', 'jelszo');
    expect(lastCall().headers.has('Authorization')).toBe(false);
    expect(lastCall().headers.get('Content-Type')).toBe('application/json');
  });

  it('a setAuthToken(null) után már nincs Authorization fejléc', async () => {
    setAuthToken('titkos-token');
    respondWith([]);
    await api.listBikes();
    expect(lastCall().headers.has('Authorization')).toBe(true);

    setAuthToken(null);
    await api.listBikes();
    expect(lastCall().headers.has('Authorization')).toBe(false);
  });

  it('a token cseréje után az új tokent küldi', async () => {
    setAuthToken('elso');
    setAuthToken('masodik');
    respondWith([]);
    await api.listRentals();
    expect(lastCall().headers.get('Authorization')).toBe('Bearer masodik');
  });
});

describe('végpontok', () => {
  it('login: POST /api/auth/login a felhasználónévvel és a jelszóval', async () => {
    const payload = { accessToken: 'tok', tokenType: 'Bearer', expiresAt: 123, user: { id: 'u1', name: 'Péter', role: 'renter' } };
    respondWith(payload);

    await expect(api.login('peter', 'árvíztűrő')).resolves.toEqual(payload);

    const { url, init } = lastCall();
    expect(url).toBe('/api/auth/login');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ username: 'peter', password: 'árvíztűrő' });
  });

  it('listUsers: GET /api/users', async () => {
    const users = [{ id: 'u1', name: 'Péter', role: 'manager' }];
    respondWith(users);

    await expect(api.listUsers()).resolves.toEqual(users);

    expect(lastCall().url).toBe('/api/users');
    expect(lastCall().init.method).toBe('GET');
  });

  it('listTariffs: GET /api/tariffs', async () => {
    const tariffs = { city: { baseFee: 300, perMinute: 15 } };
    respondWith(tariffs);

    await expect(api.listTariffs()).resolves.toEqual(tariffs);

    expect(lastCall().url).toBe('/api/tariffs');
    expect(lastCall().init.method).toBe('GET');
  });

  it('listBikes: GET /api/bikes', async () => {
    const bikes = [{ id: 'b1', name: 'City-01', type: 'city', status: 'available' }];
    respondWith(bikes);

    await expect(api.listBikes()).resolves.toEqual(bikes);

    expect(lastCall().url).toBe('/api/bikes');
    expect(lastCall().init.method).toBe('GET');
  });

  it('createBike: POST /api/bikes névvel és típussal', async () => {
    const bike = { id: 'b9', name: 'Hegyi-09', type: 'mountain', status: 'available' };
    respondWith(bike, 201);

    await expect(api.createBike('Hegyi-09', 'mountain')).resolves.toEqual(bike);

    const { url, init } = lastCall();
    expect(url).toBe('/api/bikes');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ name: 'Hegyi-09', type: 'mountain' });
  });

  it('changeBikeStatus: PATCH /api/bikes/{id}/status az új státusszal', async () => {
    const bike = { id: 'b1', name: 'City-01', type: 'city', status: 'maintenance' };
    respondWith(bike);

    await expect(api.changeBikeStatus('b1', 'maintenance')).resolves.toEqual(bike);

    const { url, init } = lastCall();
    expect(url).toBe('/api/bikes/b1/status');
    expect(init.method).toBe('PATCH');
    expect(JSON.parse(String(init.body))).toEqual({ status: 'maintenance' });
  });

  it('listRentals: GET /api/rentals', async () => {
    respondWith([]);

    await expect(api.listRentals()).resolves.toEqual([]);

    expect(lastCall().url).toBe('/api/rentals');
    expect(lastCall().init.method).toBe('GET');
  });

  it('createRental: POST /api/rentals a bicikli azonosítójával', async () => {
    const rental = { id: 'r1', bikeId: 'b1', userId: 'u1', startedAt: 1, elapsedSeconds: 0, currentFee: 300 };
    respondWith(rental, 201);

    await expect(api.createRental('b1')).resolves.toEqual(rental);

    const { url, init } = lastCall();
    expect(url).toBe('/api/rentals');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ bikeId: 'b1' });
  });

  it('returnRental: POST /api/rentals/{id}/return törzs és Content-Type nélkül', async () => {
    const rental = { id: 'r1', bikeId: 'b1', userId: 'u1', startedAt: 1, endedAt: 2, fee: 315, elapsedSeconds: 60, currentFee: 315 };
    respondWith(rental);

    await expect(api.returnRental('r1')).resolves.toEqual(rental);

    const { url, init, headers } = lastCall();
    expect(url).toBe('/api/rentals/r1/return');
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
    expect(headers.has('Content-Type')).toBe(false);
  });

  it('a hitelesített végpontok mind elküldik a tokent, a hívásonként pontosan egy kérést indítva', async () => {
    setAuthToken('tok');
    respondWith({});

    const calls = [
      () => api.listUsers(),
      () => api.listTariffs(),
      () => api.listBikes(),
      () => api.createBike('Város-01', 'city'),
      () => api.changeBikeStatus('b1', 'available'),
      () => api.listRentals(),
      () => api.createRental('b1'),
      () => api.returnRental('r1'),
    ];
    for (const [index, call] of calls.entries()) {
      await call();
      expect(fetchMock).toHaveBeenCalledTimes(index + 1);
      expect(lastCall().headers.get('Authorization')).toBe('Bearer tok');
    }
  });
});

describe('azonosítók kódolása az URL-ben', () => {
  it('changeBikeStatus: a speciális karaktereket kódolja', async () => {
    respondWith({});
    await api.changeBikeStatus('a/b?c#d e', 'available');
    expect(lastCall().url).toBe(`/api/bikes/${encodeURIComponent('a/b?c#d e')}/status`);
    expect(lastCall().url).toBe('/api/bikes/a%2Fb%3Fc%23d%20e/status');
  });

  it('returnRental: a speciális karaktereket kódolja', async () => {
    respondWith({});
    await api.returnRental('../admin/x');
    expect(lastCall().url).toBe('/api/rentals/..%2Fadmin%2Fx/return');
  });

  it('az ékezetes azonosítót százalékkódolja', async () => {
    respondWith({});
    await api.returnRental('bérlés-ő');
    expect(lastCall().url).toBe(`/api/rentals/${encodeURIComponent('bérlés-ő')}/return`);
  });
});

describe('hibakezelés', () => {
  it('a {code, message} hibatörzsből ApiError-t készít', async () => {
    respondWith({ code: 'BIKE_NOT_AVAILABLE', message: 'Ez a bicikli jelenleg nem bérelhető.' }, 409);

    const error = await rejectionOf(api.createRental('b1'));

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toBeInstanceOf(Error);
    expect(error).toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'BIKE_NOT_AVAILABLE',
      message: 'Ez a bicikli jelenleg nem bérelhető.',
    });
  });

  it('kód és üzenet nélküli JSON hibatörzsnél általános üzenetet és UNKNOWN_ERROR kódot ad', async () => {
    respondWith({}, 404);

    const error = await rejectionOf(api.listBikes());

    expect(error).toMatchObject({ status: 404, code: 'UNKNOWN_ERROR', message: 'A kérés sikertelen (404).' });
  });

  it('nem JSON hibatörzsnél is ApiError-t dob', async () => {
    fetchMock.mockImplementation(async () => new Response('<html>Hiba</html>', { status: 400 }));

    const error = await rejectionOf(api.listBikes());

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: 'UNKNOWN_ERROR', message: 'A kérés sikertelen (400).' });
  });

  it('üres törzsű 5xx hibánál a szerver nem érhető el üzenetet adja', async () => {
    fetchMock.mockImplementation(async () => new Response('', { status: 503 }));

    const error = await rejectionOf(api.listBikes());

    expect(error).toMatchObject({
      status: 503,
      code: 'UNKNOWN_ERROR',
      message: 'A szerver jelenleg nem érhető el.',
    });
  });

  it('az 5xx hibánál a szerver által küldött üzenetet részesíti előnyben', async () => {
    respondWith({ code: 'INTERNAL', message: 'Belső hiba.' }, 500);

    const error = await rejectionOf(api.listBikes());

    expect(error).toMatchObject({ status: 500, code: 'INTERNAL', message: 'Belső hiba.' });
  });

  it('hálózati hibánál 0-s státuszú ApiError-t dob', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await rejectionOf(api.listBikes());

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR', message: 'A szerver nem érhető el.' });
  });

  it('hálózati hibánál a bejelentkezés is ApiError-t dob', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const error = await rejectionOf(api.login('peter', 'jelszo'));

    expect(error).toMatchObject({ status: 0, code: 'NETWORK_ERROR' });
  });

  it.each(['null', '42', '"szöveges hiba"', '[1, 2]', 'true', '["code", "message"]'])(
    'a(z) %s JSON hibatörzsnél is ApiError-t dob UNKNOWN_ERROR kóddal és általános üzenettel',
    async (rawBody) => {
      fetchMock.mockImplementation(async () => new Response(rawBody, { status: 500 }));

      const error = await rejectionOf(api.listBikes());

      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        status: 500,
        code: 'UNKNOWN_ERROR',
        message: 'A szerver jelenleg nem érhető el.',
      });
    },
  );

  it('a code és a message mezőt csak string típusként veszi át', async () => {
    respondWith({ code: 42, message: { részlet: 'x' } }, 400);
    await expect(rejectionOf(api.listBikes())).resolves.toMatchObject({
      status: 400,
      code: 'UNKNOWN_ERROR',
      message: 'A kérés sikertelen (400).',
    });

    respondWith({ code: 'VALIDATION_ERROR', message: 5 }, 400);
    await expect(rejectionOf(api.listBikes())).resolves.toMatchObject({
      code: 'VALIDATION_ERROR',
      message: 'A kérés sikertelen (400).',
    });

    respondWith({ code: null, message: 'Csak üzenet.' }, 400);
    await expect(rejectionOf(api.listBikes())).resolves.toMatchObject({
      code: 'UNKNOWN_ERROR',
      message: 'Csak üzenet.',
    });
  });

  it('a nem string code/message nem kerül át az ApiError-ba, 401 esetén sem', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setAuthToken('tok');
    respondWith([1, 2, 3], 401);

    const error = await rejectionOf(api.listBikes());

    expect(error).toMatchObject({ status: 401, code: 'UNKNOWN_ERROR', message: 'A kérés sikertelen (401).' });
    expect(handler).toHaveBeenCalledTimes(1);
  });
});

describe('401 kezelése', () => {
  it('tokennel, hitelesített kérésre kapott 401 meghívja az unauthorized handlert, és ApiError-t dob', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setAuthToken('lejart');
    respondWith({ code: 'UNAUTHORIZED', message: 'Lejárt a token.' }, 401);

    const error = await rejectionOf(api.listBikes());

    expect(handler).toHaveBeenCalledTimes(1);
    expect(error).toMatchObject({ status: 401, code: 'UNAUTHORIZED', message: 'Lejárt a token.' });
  });

  it('a nem JSON törzsű 401 is meghívja a handlert', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setAuthToken('lejart');
    fetchMock.mockImplementation(async () => new Response('', { status: 401 }));

    await rejectionOf(api.listRentals());

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('token nélkül kapott 401 nem hívja meg a handlert', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    respondWith({ code: 'UNAUTHORIZED', message: 'Nincs token.' }, 401);

    await rejectionOf(api.listBikes());

    expect(handler).not.toHaveBeenCalled();
  });

  it('a bejelentkezés 401-e nem hívja meg a handlert, még tokennel sem', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setAuthToken('valami');
    respondWith({ code: 'INVALID_CREDENTIALS', message: 'Hibás felhasználónév vagy jelszó.' }, 401);

    const error = await rejectionOf(api.login('peter', 'rossz'));

    expect(handler).not.toHaveBeenCalled();
    expect(error).toMatchObject({ status: 401, code: 'INVALID_CREDENTIALS', message: 'Hibás felhasználónév vagy jelszó.' });
  });

  it.each([400, 403, 404, 409, 500])('a(z) %i státusz nem hívja meg a handlert', async (status) => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setAuthToken('tok');
    respondWith({ code: 'X', message: 'Hiba' }, status);

    await rejectionOf(api.listBikes());

    expect(handler).not.toHaveBeenCalled();
  });

  it('handler nélkül a 401 is simán ApiError-t eredményez', async () => {
    setAuthToken('tok');
    respondWith({ code: 'UNAUTHORIZED', message: 'Lejárt.' }, 401);

    await expect(api.listBikes()).rejects.toBeInstanceOf(ApiError);
  });

  it('a setUnauthorizedHandler(null) után a korábbi handler nem hívódik', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setUnauthorizedHandler(null);
    setAuthToken('tok');
    respondWith({ code: 'UNAUTHORIZED', message: 'Lejárt.' }, 401);

    await rejectionOf(api.listBikes());

    expect(handler).not.toHaveBeenCalled();
  });

  it('a setAuthToken(null) után kapott 401 nem hívja meg a handlert', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    setAuthToken('tok');
    setAuthToken(null);
    respondWith({ code: 'UNAUTHORIZED', message: 'Nincs token.' }, 401);

    await rejectionOf(api.listBikes());

    expect(handler).not.toHaveBeenCalled();
  });
});
