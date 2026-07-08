import jwt from 'jsonwebtoken';
import type { FastifyReply, FastifyRequest } from 'fastify';
import type { FridayJwtPayload } from './types';

const SECRET = process.env.FRIDAY_JWT_SECRET ?? 'dev-secret-do-not-use-in-prod';
const TOKEN_TTL_SECONDS = 60 * 60 * 24 * 30;

export function sign(payload: { sub: string; name: string; email: string; plan: string }): string {
  return jwt.sign(payload, SECRET, { expiresIn: TOKEN_TTL_SECONDS });
}

export function verify(token: string): FridayJwtPayload | null {
  try {
    return jwt.verify(token, SECRET) as FridayJwtPayload;
  } catch {
    return null;
  }
}

export async function requireAuth(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const header = request.headers.authorization;
  if (!header || !header.startsWith('Bearer ')) {
    await reply.code(401).send({ error: 'missing_bearer' });
    return;
  }
  const claims = verify(header.slice('Bearer '.length).trim());
  if (!claims) {
    await reply.code(401).send({ error: 'invalid_token' });
    return;
  }
  (request as FastifyRequest & { claims: FridayJwtPayload }).claims = claims;
}
