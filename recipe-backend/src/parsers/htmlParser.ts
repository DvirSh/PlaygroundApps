import { parse as parseHTML } from 'node-html-parser';
import { RecipeParser, ParsedRecipe, Ingredient } from './types';

function stripHtml(text: string): string {
  return text.replace(/<[^>]+>/g, '').replace(/&\w+;/g, ' ').trim();
}

function splitOnBr(text: string): string[] {
  return text
    .split(/<br\s*\/?>/i)
    .map(s => stripHtml(s).trim())
    .filter(Boolean);
}

function extractStepsFromText(lines: string[]): string[] {
  const steps: string[] = [];
  let inSteps = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;

    // Check for preparation header
    if (/אופן ההכנה|הוראות הכנה|הכנות:|Instructions|Directions|Preparation|Method/i.test(line)) {
      inSteps = true;
      continue;
    }

    if (!inSteps) continue;

    // Skip pure numbers (step numbers like "01", "02")
    if (/^\d{1,2}$/.test(line)) continue;

    // Strip leading "1." or "1)" etc.
    const cleaned = line.replace(/^\d+[\.\)]\s*/, '').trim();

    // Stop at footer-like content (short lines after steps, or common footer patterns)
    if (steps.length > 0 && cleaned.length < 10 && !/\*/.test(cleaned)) {
      // Could be a tag or footer, check if next lines are also short
      const nextFew = lines.slice(i + 1, i + 4).map(l => l.trim());
      const allShort = nextFew.every(l => l.length < 20 || !l);
      if (allShort) break;
    }

    if (cleaned.length > 10) {
      steps.push(cleaned);
    }
  }

  return steps;
}

export const htmlParser: RecipeParser = {
  name: 'htmlParser',

  canParse(_html: string, _url: string): boolean {
    return true;
  },

  parse(html: string, url: string): ParsedRecipe | null {
    const root = parseHTML(html);

    // Title
    const h1 = root.querySelector('h1');
    const title = h1 ? stripHtml(h1.innerHTML) : null;
    if (!title) return null;

    // Image
    const ogImage = root.querySelector('meta[property="og:image"]');
    const image_url = ogImage?.getAttribute('content') || undefined;

    // Description
    const ogDesc = root.querySelector('meta[property="og:description"]');
    const description = ogDesc?.getAttribute('content') || undefined;

    // Ingredients
    const ingredients: Ingredient[] = [];
    const ingredientSelectors = [
      '.ingredients-list',
      '.recipe-ingredients',
      '.wprm-recipe-ingredients',
      '[class*="ingredient"]',
    ];

    for (const selector of ingredientSelectors) {
      const container = root.querySelector(selector);
      if (!container) continue;

      const items = container.querySelectorAll('li');
      if (items.length > 0) {
        items.forEach(li => {
          const text = stripHtml(li.innerHTML).trim();
          if (text) ingredients.push({ amount: '', unit: '', name: text });
        });
        break;
      }

      // Try <p> with <br> splits
      const paras = container.querySelectorAll('p');
      for (const p of paras) {
        const lines = splitOnBr(p.innerHTML);
        for (const line of lines) {
          if (line) ingredients.push({ amount: '', unit: '', name: line });
        }
      }
      if (ingredients.length > 0) break;
    }

    // Steps - strip scripts/styles, convert HTML to plain text lines and extract
    const cleaned = html
      .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
      .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, '')
      .replace(/<noscript[^>]*>[\s\S]*?<\/noscript>/gi, '');
    const plainText = cleaned.replace(/<[^>]+>/g, '\n').replace(/&\w+;/g, ' ');
    const lines = plainText.split('\n').map(l => l.trim()).filter(Boolean);
    const steps = extractStepsFromText(lines);

    if (ingredients.length === 0 && steps.length === 0) return null;

    return {
      title,
      description,
      image_url,
      source_url: url,
      ingredients,
      steps,
    };
  },
};
