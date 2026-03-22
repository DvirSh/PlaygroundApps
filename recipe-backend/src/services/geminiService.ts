import { GoogleGenerativeAI } from '@google/generative-ai';
import { Ingredient } from '../parsers/types';

const genAI = new GoogleGenerativeAI(process.env.GEMINI_API_KEY || '');

const RECIPE_PROMPT = `Extract ALL recipes from the following text/content. If multiple recipes are present, extract every one. Return ONLY valid JSON with this exact structure, no markdown or extra text:
{
  "recipes": [
    {
      "title": "Recipe Title",
      "description": "Brief description or null",
      "ingredients": [
        {"amount": "2", "unit": "cups", "name": "flour"},
        {"amount": "1", "unit": "", "name": "egg"}
      ],
      "steps": [
        "First step description",
        "Second step description"
      ],
      "tags": ["tag1", "tag2"]
    }
  ]
}

Rules:
- Always use the "recipes" array wrapper, even for a single recipe
- Parse amounts like "1/2", "2-3" as strings, not numbers
- If there's no clear amount or unit, leave them as empty strings
- Steps should be clean sentences without numbering
- Tags should be relevant categories (e.g. "dessert", "chicken", "italian", "quick", "vegetarian")
- Support any language (English, Hebrew, etc.)
- If the text is messy or has OCR errors, do your best to interpret it correctly

Here is the content:
`;

const IMAGE_PROMPT = `Look at this image. Extract ALL recipes you can see. If multiple recipes are present, extract every one. Return ONLY valid JSON with this exact structure, no markdown or extra text:
{
  "recipes": [
    {
      "title": "Recipe Title",
      "description": "Brief description or null",
      "ingredients": [
        {"amount": "2", "unit": "cups", "name": "flour"},
        {"amount": "1", "unit": "", "name": "egg"}
      ],
      "steps": [
        "First step description",
        "Second step description"
      ],
      "tags": ["tag1", "tag2"]
    }
  ]
}

Rules:
- Always use the "recipes" array wrapper, even for a single recipe
- Parse amounts like "1/2", "2-3" as strings, not numbers
- If there's no clear amount or unit, leave them as empty strings
- Steps should be clean sentences without numbering
- Tags should be relevant categories
- Support any language (English, Hebrew, etc.)
- Do your best to read all text in the image accurately
`;

interface ParsedRecipeResult {
  title: string;
  description: string | null;
  ingredients: Ingredient[];
  steps: string[];
  tags?: string[];
}

function parseJsonResponse(text: string): ParsedRecipeResult[] {
  // Strip markdown code fences if present
  let cleaned = text.trim();
  if (cleaned.startsWith('```')) {
    cleaned = cleaned.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '');
  }
  console.log('AI response (first 500 chars):', cleaned.substring(0, 500));
  const parsed = JSON.parse(cleaned);
  // Handle: {recipes: [...]}, bare array, or single object
  if (parsed.recipes && Array.isArray(parsed.recipes)) {
    return parsed.recipes;
  }
  if (Array.isArray(parsed)) {
    return parsed;
  }
  return [parsed];
}

const MODEL_CONFIG = {
  model: 'gemini-2.5-flash',
  generationConfig: {
    responseMimeType: 'application/json',
    thinkingConfig: { thinkingBudget: 0 },
  },
};

const AI_TIMEOUT_MS = 45000;

function withTimeout<T>(promise: Promise<T>, ms: number): Promise<T> {
  return Promise.race([
    promise,
    new Promise<T>((_, reject) =>
      setTimeout(() => reject(new Error('AI request timed out')), ms)
    ),
  ]);
}

export async function parseTextWithAI(text: string): Promise<ParsedRecipeResult[]> {
  const model = genAI.getGenerativeModel(MODEL_CONFIG);
  const result = await withTimeout(model.generateContent(RECIPE_PROMPT + text), AI_TIMEOUT_MS);
  const response = result.response.text();
  return parseJsonResponse(response);
}

export async function parseImageWithAI(imageBuffer: Buffer, mimeType: string): Promise<ParsedRecipeResult[]> {
  const model = genAI.getGenerativeModel(MODEL_CONFIG);
  const imagePart = {
    inlineData: {
      data: imageBuffer.toString('base64'),
      mimeType,
    },
  };
  const result = await withTimeout(model.generateContent([IMAGE_PROMPT, imagePart]), AI_TIMEOUT_MS);
  const response = result.response.text();
  return parseJsonResponse(response);
}

export async function translateRecipe(recipe: ParsedRecipeResult, targetLang: string): Promise<ParsedRecipeResult> {
  const model = genAI.getGenerativeModel({
    model: 'gemini-2.5-flash',
    generationConfig: {
      responseMimeType: 'application/json',
      maxOutputTokens: 16384,
    } as any,
  });
  const prompt = `Translate this recipe to ${targetLang}. Keep the exact same JSON structure. Translate the title, description, ingredient names, ingredient units, steps, and tags. Do NOT translate or change amounts (numbers). Return ONLY valid JSON, no markdown:

${JSON.stringify(recipe)}`;
  const result = await withTimeout(model.generateContent(prompt), AI_TIMEOUT_MS);
  const response = result.response.text();
  const parsed = parseJsonResponse(response);
  return parsed[0];
}

export async function parsePdfWithAI(pdfBuffer: Buffer): Promise<ParsedRecipeResult[]> {
  const model = genAI.getGenerativeModel(MODEL_CONFIG);
  const pdfPart = {
    inlineData: {
      data: pdfBuffer.toString('base64'),
      mimeType: 'application/pdf',
    },
  };
  const result = await withTimeout(
    model.generateContent([RECIPE_PROMPT.replace('text/content', 'PDF'), pdfPart]),
    AI_TIMEOUT_MS
  );
  const response = result.response.text();
  return parseJsonResponse(response);
}
