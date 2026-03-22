import { Ingredient } from './types';

const UNITS_PATTERN = /^([\d\s\/.½¼¾⅓⅔⅛]+)\s*(cup|cups|tbsp|tsp|tablespoon|tablespoons|teaspoon|teaspoons|oz|ounce|ounces|lb|lbs|pound|pounds|g|gram|grams|kg|ml|liter|liters|pinch|dash|clove|cloves|can|cans|package|packages|bunch|bunches|slice|slices|piece|pieces|stick|sticks|head|heads|sprig|sprigs|handful|handfuls|כוס|כוסות|כף|כפית|גרם|ק"ג|מ"ל|ליטר|קורט|שן|שיני|חבילה|פרוסה|פרוסות|יחידה|יחידות)\s+(.+)/i;

const QUANTITY_PATTERN = /^([\d\s\/.½¼¾⅓⅔⅛]+)\s+(.+)/;

interface ParsedText {
  title: string;
  description: string | null;
  ingredients: Ingredient[];
  steps: string[];
}

type Section = 'unknown' | 'ingredients' | 'steps';

function isIngredientHeader(line: string): boolean {
  return /^(ingredients|מצרכים|מרכיבים|חומרים)\s*:?\s*$/i.test(line);
}

function isStepsHeader(line: string): boolean {
  return /^(instructions|directions|steps|method|preparation|אופן ההכנה|הוראות הכנה|הכנה|הכנות|שלבים)\s*:?\s*$/i.test(line);
}

function looksLikeIngredient(line: string): boolean {
  if (UNITS_PATTERN.test(line)) return true;
  if (QUANTITY_PATTERN.test(line)) return true;
  // Short line starting with a number
  if (/^\d/.test(line) && line.length < 80) return true;
  return false;
}

function looksLikeStep(line: string): boolean {
  // Starts with a number followed by period/paren, or is a long line
  if (/^\d+[\.\)]\s/.test(line)) return true;
  if (line.length > 60) return true;
  return false;
}

function parseIngredient(text: string): Ingredient {
  const cleaned = text.replace(/^[-•*]\s*/, '').replace(/^\d+[\.\)]\s*/, '').trim();
  const unitMatch = cleaned.match(UNITS_PATTERN);
  if (unitMatch) {
    return {
      amount: unitMatch[1].trim(),
      unit: unitMatch[2].trim(),
      name: unitMatch[3].trim(),
    };
  }
  const qtyMatch = cleaned.match(QUANTITY_PATTERN);
  if (qtyMatch) {
    return {
      amount: qtyMatch[1].trim(),
      unit: '',
      name: qtyMatch[2].trim(),
    };
  }
  return { amount: '', unit: '', name: cleaned };
}

function cleanOcrText(text: string): string {
  return text
    // Fix common OCR fraction misreads: "Ve " or "V2 " at start of line → "1/2 "
    .replace(/(?:^|\n)\s*Ve\s/gm, '\n1/2 ')
    .replace(/(?:^|\n)\s*V1\/2\s/gm, '\n1/2 ')
    .replace(/(?:^|\n)\s*V½\s/gm, '\n1/2 ')
    // "Va " → "1/4 "
    .replace(/(?:^|\n)\s*Va\s/gm, '\n1/4 ')
    // Unicode fractions that OCR might produce
    .replace(/½/g, '1/2')
    .replace(/¼/g, '1/4')
    .replace(/¾/g, '3/4')
    .replace(/⅓/g, '1/3')
    .replace(/⅔/g, '2/3')
    .replace(/⅛/g, '1/8')
    // "l " at start could be "1 " (OCR confuses 1 and l)
    .replace(/(?:^|\n)\s*l\s+(?=cup|cups|tbsp|tsp|tablespoon|teaspoon|oz|lb|g |gram|kg|ml|כוס|כף|כפית|גרם)/gim, '\n1 ')
    // "O" at start could be "0" in "0.5"
    .replace(/(?:^|\n)\s*O\./gm, '\n0.');
}

export function parseRecipeText(text: string): ParsedText {
  const cleaned = cleanOcrText(text);
  const lines = cleaned.split('\n').map(l => l.trim()).filter(Boolean);

  if (lines.length === 0) {
    return { title: 'Untitled', description: null, ingredients: [], steps: [] };
  }

  // First non-trivial line is likely the title
  let title = lines[0];
  let startIdx = 1;

  // If title is very short (like a number), skip it
  if (title.length < 3 && lines.length > 1) {
    title = lines[1];
    startIdx = 2;
  }

  let currentSection: Section = 'unknown';
  const ingredients: Ingredient[] = [];
  const steps: string[] = [];
  const descriptionLines: string[] = [];

  for (let i = startIdx; i < lines.length; i++) {
    const line = lines[i];

    // Check for section headers
    if (isIngredientHeader(line)) {
      currentSection = 'ingredients';
      continue;
    }
    if (isStepsHeader(line)) {
      currentSection = 'steps';
      continue;
    }

    if (currentSection === 'ingredients') {
      if (line.length > 3) {
        ingredients.push(parseIngredient(line));
      }
    } else if (currentSection === 'steps') {
      if (line.length > 5) {
        const cleaned = line.replace(/^\d+[\.\)]\s*/, '').trim();
        steps.push(cleaned);
      }
    } else {
      // Unknown section - try to auto-detect
      if (looksLikeIngredient(line) && !looksLikeStep(line)) {
        currentSection = 'ingredients';
        ingredients.push(parseIngredient(line));
      } else if (looksLikeStep(line)) {
        currentSection = 'steps';
        const cleaned = line.replace(/^\d+[\.\)]\s*/, '').trim();
        steps.push(cleaned);
      } else {
        descriptionLines.push(line);
      }
    }
  }

  return {
    title,
    description: descriptionLines.length > 0 ? descriptionLines.join(' ') : null,
    ingredients,
    steps,
  };
}
