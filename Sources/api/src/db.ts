import Database from 'better-sqlite3';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import type { FridayUser } from './types';

const DB_PATH = process.env.FRIDAY_DB_PATH ?? './data/friday.db';

mkdirSync(dirname(DB_PATH), { recursive: true });

const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

db.exec(`
  CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    email TEXT NOT NULL,
    avatar_url TEXT NOT NULL,
    plan TEXT NOT NULL,
    created_at INTEGER NOT NULL
  )
`);

const upsertStmt = db.prepare(`
  INSERT INTO users (id, display_name, email, avatar_url, plan, created_at)
  VALUES (@id, @displayName, @email, @avatarUrl, @plan, @createdAt)
  ON CONFLICT(id) DO UPDATE SET
    display_name = excluded.display_name,
    email = excluded.email,
    avatar_url = excluded.avatar_url,
    plan = excluded.plan
`);

const selectStmt = db.prepare(`SELECT * FROM users WHERE id = ?`);

export function upsertUser(user: FridayUser): void {
  upsertStmt.run({ ...user, createdAt: Date.now() });
}

export function getUser(id: string): FridayUser | null {
  const row = selectStmt.get(id) as Record<string, unknown> | undefined;
  if (!row) return null;
  return {
    id: row.id as string,
    displayName: row.display_name as string,
    email: row.email as string,
    avatarUrl: row.avatar_url as string,
    plan: row.plan as string,
  };
}
