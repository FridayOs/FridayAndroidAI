import type { FastifyInstance } from 'fastify';
import type { FridayJwtPayload } from '../types';
import { requireAuth } from '../auth';
import { getUser } from '../db';

export async function meRoutes(app: FastifyInstance): Promise<void> {
  app.get('/me', { preHandler: requireAuth }, async (request, reply) => {
    const claims = (request as typeof request & { claims: FridayJwtPayload }).claims;
    const user = getUser(claims.sub);
    if (!user) {
      return reply.code(404).send({ error: 'user_not_found' });
    }
    return reply.send(user);
  });
}
