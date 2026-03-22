import { supabase } from './supabaseClient';
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

  const { data, error } = await supabase
    .from('recipes')
    .insert({
      title: parsed.title,
      description: parsed.description || null,
      image_url: parsed.image_url || null,
      source_url: parsed.source_url,
      ingredients: parsed.ingredients,
      steps: parsed.steps,
      tags: parsed.tags || [],
    })
    .select()
    .single();

  if (error) {
    throw new Error(`Failed to save recipe: ${error.message}`);
  }

  return data;
}

export async function createManual(recipe: any) {
  const { data, error } = await supabase
    .from('recipes')
    .insert({
      title: recipe.title || 'Untitled',
      description: recipe.description || null,
      image_url: recipe.image_url || null,
      source_url: recipe.source_url && recipe.source_url !== 'manual' && recipe.source_url !== 'scanned'
        ? recipe.source_url
        : `manual:${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      ingredients: recipe.ingredients || [],
      steps: recipe.steps || [],
      tags: recipe.tags || [],
    })
    .select()
    .single();

  if (error) {
    throw new Error(`Failed to save recipe: ${error.message}`);
  }

  return data;
}

export async function createBatch(recipes: any[]) {
  const rows = recipes.map((recipe: any, i: number) => ({
    title: recipe.title || 'Untitled',
    description: recipe.description || null,
    image_url: recipe.image_url || null,
    source_url: `manual:${Date.now()}-${i}-${Math.random().toString(36).slice(2, 8)}`,
    ingredients: recipe.ingredients || [],
    steps: recipe.steps || [],
    tags: recipe.tags || [],
  }));

  const { data, error } = await supabase
    .from('recipes')
    .insert(rows)
    .select();

  if (error) {
    throw new Error(`Failed to save recipes: ${error.message}`);
  }

  return data;
}

export async function findByTitles(titles: string[]) {
  const { data, error } = await supabase
    .from('recipes')
    .select('*')
    .in('title', titles);

  if (error) {
    throw new Error(`Failed to check duplicates: ${error.message}`);
  }

  return data || [];
}

export async function search(query?: string, tag?: string) {
  const { data, error } = await supabase
    .from('recipes')
    .select('*')
    .order('created_at', { ascending: false });

  if (error) {
    throw new Error(`Failed to search recipes: ${error.message}`);
  }

  let results = data || [];

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
  const allowed: any = {};
  if (updates.title !== undefined) allowed.title = updates.title;
  if (updates.description !== undefined) allowed.description = updates.description;
  if (updates.ingredients !== undefined) allowed.ingredients = updates.ingredients;
  if (updates.steps !== undefined) allowed.steps = updates.steps;
  if (updates.tags !== undefined) allowed.tags = updates.tags;
  if (updates.image_url !== undefined) allowed.image_url = updates.image_url;

  const { data, error } = await supabase
    .from('recipes')
    .update(allowed)
    .eq('id', id)
    .select()
    .single();

  if (error) {
    throw new Error(`Failed to update recipe: ${error.message}`);
  }

  return data;
}

export async function remove(id: string) {
  const { error } = await supabase
    .from('recipes')
    .delete()
    .eq('id', id);

  if (error) {
    throw new Error(`Failed to delete recipe: ${error.message}`);
  }
}

export async function getAllTags() {
  const { data, error } = await supabase
    .from('recipes')
    .select('tags');

  if (error) {
    throw new Error(`Failed to fetch tags: ${error.message}`);
  }

  const tagSet = new Set<string>();
  (data || []).forEach((row: any) => {
    (row.tags || []).forEach((t: string) => tagSet.add(t));
  });

  return Array.from(tagSet).sort();
}
