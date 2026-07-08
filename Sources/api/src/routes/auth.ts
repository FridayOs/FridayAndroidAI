import { createHash } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import type { AuthGoogleBody, FridayUser } from '../types';
import { sign } from '../auth';
import { upsertUser } from '../db';

// M1 stub contract: the Google ID token signature is not verified here. A
// non-empty idToken deterministically derives a user id so the full Android
// pipeline (GoogleSignIn -> exchangeGoogle -> persist) is exercised end to end.
// M1-04 replaces this body with real google-auth-library verification.
export async function authRoutes(app: FastifyInstance): Promise<void> {
  app.post('/auth/google', async (request, reply) => {
    const body = (request.body ?? {}) as AuthGoogleBody;
    const idToken = body.idToken?.trim();
    if (!idToken) {
      return reply.code(400).send({ error: 'missing_id_token' });
    }
    const hash = createHash('sha256').update(idToken).digest('hex');
    const user: FridayUser = {
      id: `usr_${hash.slice(0, 12)}`,
      displayName: 'Friday User',
      email: `${hash.slice(0, 8)}@friday.local`,
      avatarUrl: '',
      plan: 'Free plan',
    };
    upsertUser(user);
    const token = sign({ sub: user.id, name: user.displayName, email: user.email, plan: user.plan });
    return reply.send({ token, user });
  });
}
