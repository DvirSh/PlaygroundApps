import { db } from './db';
import { parseRecipe } from '../parsers';
import { ParsedRecipe } from '../parsers/types';

export async function fetchAndSave(url: string) {
  const response = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (compatible; RecipeBot/1.0)',
    },
  });
  if (!response.ok) {
    throw new Error(`Failed to fetch URL: ${response.status} ${response.statusText}`);
  }
  const html = await response.text();

  const parsed: ParsedRecipe | null = parseRecipe(html, url);
  if (!parsed) {
    throw new Error('Could not parse recipe from URL');
  }

  const { rows } = await db.query(
    `INSERT INTO recipes (title, description, image_url, source_url, ingredients, steps, tags)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING *`,
    [
      parsed.title,
      parsed.description || null,
      parsed.image_url || null,
      parsed.source_url,
      JSON.stringify(parsed.ingredients),
      JSON.stringify(parsed.steps),
      parsed.tags || [],
    ]
  );

  return rows[0];
}

export async function createManual(recipe: any) {
  const sourceUrl = recipe.source_url && recipe.source_url !== 'manual' && recipe.source_url !== 'scanned'
    ? recipe.source_url
    : `manual:${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const { rows } = await db.query(
    `INSERT INTO recipes (title, description, image_url, source_url, ingredients, steps, tags)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING *`,
    [
      recipe.title || 'Untitled',
      recipe.description || null,
      recipe.image_url || null,
      sourceUrl,
      JSON.stringify(recipe.ingredients || []),
      JSON.stringify(recipe.steps || []),
      recipe.tags || [],
    ]
  );

  return rows[0];
}

export async function createBatch(recipes: any[]) {
  const placeholders: string[] = [];
  const values: any[] = [];
  let idx = 1;

  for (let i = 0; i < recipes.length; i++) {
    const r = recipes[i];
    placeholders.push(`($${idx}, $${idx+1}, $${idx+2}, $${idx+3}, $${idx+4}, $${idx+5}, $${idx+6})`);
    values.push(
      r.title || 'Untitled',
      r.description || null,
      r.image_url || null,
      `manual:${Date.now()}-${i}-${Math.random().toString(36).slice(2, 8)}`,
      JSON.stringify(r.ingredients || []),
      JSON.stringify(r.steps || []),
      r.tags || [],
    );
    idx += 7;
  }

  const { rows } = await db.query(
    `INSERT INTO recipes (title, description, image_url, source_url, ingredients, steps, tags)
     VALUES ${placeholders.join(', ')}
     RETURNING *`,
    values
  );

  return rows;
}

export async function findByTitles(titles: string[]) {
  const { rows } = await db.query(
    `SELECT * FROM recipes WHERE title = ANY($1)`,
    [titles]
  );
  return rows;
}

export async function search(query?: string, tag?: string) {
  const { rows } = await db.query(
    `SELECT * FROM recipes ORDER BY created_at DESC`
  );

  let results = rows;

  if (tag) {
    results = results.filter((recipe: any) =>
      recipe.tags?.some((t: string) => t.toLowerCase() === tag.toLowerCase())
    );
  }

  if (query) {
    const q = query.toLowerCase();
    results = results.filter((recipe: any) => {
      if (recipe.title?.toLowerCase().includes(q)) return true;
      if (recipe.description?.toLowerCase().includes(q)) return true;
      const ingredientText = JSON.stringify(recipe.ingredients).toLowerCase();
      if (ingredientText.includes(q)) return true;
      return false;
    });
  }

  return results;
}

export async function update(id: string, updates: any) {
  const fields: string[] = [];
  const values: any[] = [];
  let idx = 1;

  if (updates.title !== undefined) { fields.push(`title = $${idx++}`); values.push(updates.title); }
  if (updates.description !== undefined) { fields.push(`description = $${idx++}`); values.push(updates.description); }
  if (updates.ingredients !== undefined) { fields.push(`ingredients = $${idx++}`); values.push(JSON.stringify(updates.ingredients)); }
  if (updates.steps !== undefined) { fields.push(`steps = $${idx++}`); values.push(JSON.stringify(updates.steps)); }
  if (updates.tags !== undefined) { fields.push(`tags = $${idx++}`); values.push(updates.tags); }
  if (updates.image_url !== undefined) { fields.push(`image_url = $${idx++}`); values.push(updates.image_url); }

  if (fields.length === 0) {
    throw new Error('No valid fields to update');
  }

  values.push(id);

  const { rows } = await db.query(
    `UPDATE recipes SET ${fields.join(', ')} WHERE id = $${idx} RETURNING *`,
    values
  );

  if (rows.length === 0) {
    throw new Error('Recipe not found');
  }

  return rows[0];
}

export async function remove(id: string) {
  const { rowCount } = await db.query(
    `DELETE FROM recipes WHERE id = $1`,
    [id]
  );

  if (rowCount === 0) {
    throw new Error('Recipe not found');
  }
}

export async function getAllTags() {
  const { rows } = await db.query(`SELECT tags FROM recipes`);

  const tagSet = new Set<string>();
  rows.forEach((row: any) => {
    (row.tags || []).forEach((t: string) => tagSet.add(t));
  });

  return Array.from(tagSet).sort();
}
