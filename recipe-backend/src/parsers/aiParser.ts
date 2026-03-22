import { RecipeParser, ParsedRecipe } from './types';

export const aiParser: RecipeParser = {
  name: 'aiParser',

  canParse(_html: string, _url: string): boolean {
    return true; // Fallback parser
  },

  parse(_html: string, _url: string): ParsedRecipe | null {
    console.log('AI parser not yet implemented');
    return null;
  },
};
