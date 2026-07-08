import Fastify from 'fastify';
import cors from '@fastify/cors';
import { healthRoutes } from './routes/health';
import { authRoutes } from './routes/auth';
import { meRoutes } from './routes/me';

export function buildServer() {
  const app = Fastify({ logger: false });
  app.register(cors, { origin: true });
  app.register(healthRoutes);
  app.register(
    async (v1) => {
      await authRoutes(v1);
      await meRoutes(v1);
    },
    { prefix: '/v1' },
  );
  return app;
}

if (require.main === module) {
  const port = Number(process.env.PORT ?? 3101);
  const host = process.env.HOST ?? '0.0.0.0';
  buildServer()
    .listen({ port, host })
    .then((address) => {
      // eslint-disable-next-line no-console
      console.log(`friday-api listening on ${address}`);
    })
    .catch((err) => {
      // eslint-disable-next-line no-console
      console.error(err);
      process.exit(1);
    });
}
