import { Pool } from 'pg';

const SUPABASE_DB_URL = process.env.SUPABASE_DB_URL;
const NEON_DB_URL = process.env.NEON_DB_URL;

if (!SUPABASE_DB_URL || !NEON_DB_URL) {
  console.error('Usage: SUPABASE_DB_URL="..." NEON_DB_URL="..." npx ts-node scripts/migrate-to-neon.ts');
  process.exit(1);
}

const supaPool = new Pool({ connectionString: SUPABASE_DB_URL, ssl: { rejectUnauthorized: false } });
const neonPool = new Pool({ connectionString: NEON_DB_URL, ssl: { rejectUnauthorized: false } });

async function migrate() {
  console.log('Creating schema in Neon...');
  await neonPool.query(`
    CREATE TABLE IF NOT EXISTS recipes (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      title TEXT NOT NULL,
      description TEXT,
      image_url TEXT,
      source_url TEXT NOT NULL,
      ingredients JSONB NOT NULL DEFAULT '[]'::jsonb,
      steps JSONB NOT NULL DEFAULT '[]'::jsonb,
      tags TEXT[] NOT NULL DEFAULT '{}'
    );
  `);
  console.log('Schema ready.');

  console.log('Reading recipes from Supabase...');
  const { rows } = await supaPool.query('SELECT * FROM recipes ORDER BY created_at ASC');
  console.log(`Found ${rows.length} recipes.`);

  if (rows.length === 0) {
    console.log('Nothing to migrate.');
    return;
  }

  let success = 0;
  let failed = 0;

  for (const row of rows) {
    try {
      await neonPool.query(
        `INSERT INTO recipes (id, created_at, title, description, image_url, source_url, ingredients, steps, tags)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
         ON CONFLICT (id) DO NOTHING`,
        [
          row.id,
          row.created_at,
          row.title,
          row.description,
          row.image_url,
          row.source_url,
          JSON.stringify(row.ingredients),
          JSON.stringify(row.steps),
          row.tags,
        ]
      );
      success++;
    } catch (err: any) {
      console.error(`Failed to migrate recipe "${row.title}": ${err.message}`);
      failed++;
    }
  }

  console.log(`\nMigration complete: ${success} succeeded, ${failed} failed.`);
}

migrate()
  .catch((err) => {
    console.error('Migration failed:', err);
    process.exit(1);
  })
  .finally(async () => {
    await supaPool.end();
    await neonPool.end();
  });
