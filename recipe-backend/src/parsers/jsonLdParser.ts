import { parse as parseHTML } from 'node-html-parser';
import { RecipeParser, ParsedRecipe, Ingredient } from './types';

const INGREDIENT_PATTERN =
  /^([\d\s\/.½¼¾⅓⅔⅛]+)?\s*(cup|cups|tbsp|tsp|tablespoon|tablespoons|teaspoon|teaspoons|oz|ounce|ounces|lb|lbs|pound|pounds|g|gram|grams|kg|ml|liter|liters|pinch|dash|clove|cloves|can|cans|package|packages|bunch|bunches|slice|slices|piece|pieces|stick|sticks|head|heads|sprig|sprigs|handful|handfuls)?\s*(?:of\s+)?(.+)/i;

function parseIngredient(text: string): Ingredient {
  const match = text.trim().match(INGREDIENT_PATTERN);
  if (match) {
    return {
      amount: (match[1] || '').trim(),
      unit: (match[2] || '').trim(),
      name: (match[3] || '').trim(),
    };
  }
  return { amount: '', unit: '', name: text.trim() };
}

function extractImage(image: any): string | undefined {
  if (!image) return undefined;
  if (typeof image === 'string') return image;
  if (Array.isArray(image)) return typeof image[0] === 'string' ? image[0] : image[0]?.url;
  if (image.url) return image.url;
  return undefined;
}

function stripHtml(text: string): string {
  return text.replace(/<[^>]+>/g, '').trim();
}

function splitAndClean(text: string): string[] {
  return text
    .split(/<br\s*\/?>/i)
    .map(s => stripHtml(s).trim())
    .filter(Boolean);
}

function extractSteps(instructions: any): string[] {
  if (!instructions) return [];
  if (!Array.isArray(instructions)) instructions = [instructions];

  const steps: string[] = [];
  for (const item of instructions) {
    if (typeof item === 'string') {
      steps.push(...splitAndClean(item));
    } else if (item['@type'] === 'HowToStep') {
      const text = item.text || item.name || '';
      steps.push(...splitAndClean(text));
    } else if (item['@type'] === 'HowToSection' && Array.isArray(item.itemListElement)) {
      for (const subItem of item.itemListElement) {
        if (typeof subItem === 'string') {
          steps.push(...splitAndClean(subItem));
        } else if (subItem.text || subItem.name) {
          steps.push(...splitAndClean(subItem.text || subItem.name || ''));
        }
      }
    }
  }
  return steps.filter(Boolean);
}

function findRecipeInData(data: any): any | null {
  if (!data) return null;

  if (Array.isArray(data)) {
    for (const item of data) {
      const found = findRecipeInData(item);
      if (found) return found;
    }
    return null;
  }

  if (typeof data === 'object') {
    // Check @graph arrays
    if (data['@graph'] && Array.isArray(data['@graph'])) {
      return findRecipeInData(data['@graph']);
    }

    const type = data['@type'];
    if (type === 'Recipe' || (Array.isArray(type) && type.includes('Recipe'))) {
      return data;
    }
  }

  return null;
}

export const jsonLdParser: RecipeParser = {
  name: 'jsonLdParser',

  canParse(html: string, _url: string): boolean {
    return html.includes('application/ld+json');
  },

  parse(html: string, url: string): ParsedRecipe | null {
    const root = parseHTML(html);
    const scripts = root.querySelectorAll('script[type="application/ld+json"]');

    for (const script of scripts) {
      try {
        const json = JSON.parse(script.textContent || '');
        const recipe = findRecipeInData(json);

        if (recipe) {
          const ingredients: Ingredient[] = (recipe.recipeIngredient || []).map(
            (ing: string) => parseIngredient(ing)
          );

          return {
            title: recipe.name || 'Untitled Recipe',
            description: recipe.description || undefined,
            image_url: extractImage(recipe.image),
            source_url: url,
            ingredients,
            steps: extractSteps(recipe.recipeInstructions),
            tags: recipe.keywords
              ? typeof recipe.keywords === 'string'
                ? recipe.keywords.split(',').map((k: string) => k.trim())
                : recipe.keywords
              : undefined,
          };
        }
      } catch (e) {
        // Invalid JSON, skip this script tag
        continue;
      }
    }

    return null;
  },
};
