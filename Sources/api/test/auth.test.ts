import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { FastifyInstance } from 'fastify';

const tmp = mkdtempSync(join(tmpdir(), 'friday-api-'));
process.env.FRIDAY_DB_PATH = join(tmp, 'test.db');
process.env.FRIDAY_JWT_SECRET = 'test-secret';

let app: FastifyInstance;

beforeAll(async () => {
  const { buildServer } = await import('../src/server');
  app = buildServer();
  await app.ready();
});

afterAll(async () => {
  await app.close();
  rmSync(tmp, { recursive: true, force: true });
});

describe('friday-api auth', () => {
  it('GET /health returns ok', async () => {
    const res = await app.inject({ method: 'GET', url: '/health' });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ ok: true, service: 'friday-api' });
  });

  it('POST /v1/auth/google with empty body returns 400', async () => {
    const res = await app.inject({ method: 'POST', url: '/v1/auth/google', payload: {} });
    expect(res.statusCode).toBe(400);
  });

  it('POST /v1/auth/google with idToken returns token + user', async () => {
    const res = await app.inject({
      method: 'POST',
      url: '/v1/auth/google',
      payload: { idToken: 'dev_abc_123' },
    });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(typeof body.token).toBe('string');
    expect(body.user.id).toMatch(/^usr_/);
  });

  it('GET /v1/me without bearer returns 401', async () => {
    const res = await app.inject({ method: 'GET', url: '/v1/me' });
    expect(res.statusCode).toBe(401);
  });

  it('GET /v1/me with bearer returns the same user', async () => {
    const auth = await app.inject({
      method: 'POST',
      url: '/v1/auth/google',
      payload: { idToken: 'dev_abc_123' },
    });
    const { token, user } = auth.json();
    const res = await app.inject({
      method: 'GET',
      url: '/v1/me',
      headers: { authorization: `Bearer ${token}` },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual(user);
  });
});
