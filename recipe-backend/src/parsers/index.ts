import { ParsedRecipe, RecipeParser } from './types';
import { jsonLdParser } from './jsonLdParser';
import { htmlParser } from './htmlParser';
import { aiParser } from './aiParser';

const parsers: RecipeParser[] = [jsonLdParser, htmlParser, aiParser];

export function parseRecipe(html: string, url: string): ParsedRecipe | null {
  let bestResult: ParsedRecipe | null = null;

  for (const parser of parsers) {
    if (parser.canParse(html, url)) {
      const result = parser.parse(html, url);
      if (result) {
        if (!bestResult) {
          bestResult = result;
        } else {
          // Supplement: if previous result had few steps, use this parser's steps
          if (bestResult.steps.length < 3 && result.steps.length > bestResult.steps.length) {
            bestResult.steps = result.steps;
          }
          // Supplement: if previous result had no ingredients, use this parser's
          if (bestResult.ingredients.length === 0 && result.ingredients.length > 0) {
            bestResult.ingredients = result.ingredients;
          }
        }
        // If we have a complete result, return it
        if (bestResult.steps.length >= 3 && bestResult.ingredients.length > 0) {
          return bestResult;
        }
      }
    }
  }

  return bestResult;
}
