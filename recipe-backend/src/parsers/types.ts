export interface Ingredient {
  amount: string;
  unit: string;
  name: string;
}

export interface ParsedRecipe {
  title: string;
  description?: string;
  image_url?: string;
  source_url: string;
  ingredients: Ingredient[];
  steps: string[];
  tags?: string[];
}

export interface RecipeParser {
  name: string;
  canParse(html: string, url: string): boolean;
  parse(html: string, url: string): ParsedRecipe | null;
}
