import { Router, Request, Response } from 'express';
import multer from 'multer';
import { v2 as cloudinary } from 'cloudinary';
import * as recipeService from '../services/recipeService';
import { parseRecipeText } from '../parsers/textParser';
import { parseTextWithAI, parseImageWithAI, parsePdfWithAI, translateRecipe } from '../services/geminiService';

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 10 * 1024 * 1024 } });

const router = Router();

// POST / - receives {url}, fetches and saves recipe
router.post('/', async (req: Request, res: Response) => {
  try {
    const { url } = req.body;
    if (!url) {
      res.status(400).json({ error: 'URL is required' });
      return;
    }
    const savedRecipe = await recipeService.fetchAndSave(url);
    res.status(201).json(savedRecipe);
  } catch (error: any) {
    console.error('Error saving recipe:', error);
    if (error.message === 'Could not parse recipe from URL') {
      res.status(400).json({ error: error.message });
    } else {
      res.status(500).json({ error: error.message || 'Internal server error' });
    }
  }
});

// GET / - optional ?q= and ?tag= params
router.get('/', async (req: Request, res: Response) => {
  try {
    const query = req.query.q as string | undefined;
    const tag = req.query.tag as string | undefined;
    const recipes = await recipeService.search(query, tag);
    res.json(recipes);
  } catch (error: any) {
    console.error('Error searching recipes:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

// POST /manual - create a recipe directly (no URL parsing)
router.post('/manual', async (req: Request, res: Response) => {
  try {
    const saved = await recipeService.createManual(req.body);
    res.status(201).json(saved);
  } catch (error: any) {
    console.error('Error creating recipe:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

// POST /check-duplicates - check if recipes with given titles exist
router.post('/check-duplicates', async (req: Request, res: Response) => {
  try {
    const { titles } = req.body;
    if (!Array.isArray(titles) || titles.length === 0) {
      res.json({ duplicates: [] });
      return;
    }
    const duplicates = await recipeService.findByTitles(titles);
    res.json({ duplicates });
  } catch (error: any) {
    console.error('Error checking duplicates:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

// POST /translate - translate a recipe to a target language
router.post('/translate', async (req: Request, res: Response) => {
  try {
    const { recipe, targetLang } = req.body;
    if (!recipe || !targetLang) {
      res.status(400).json({ error: 'recipe and targetLang are required' });
      return;
    }
    const translated = await translateRecipe(recipe, targetLang);
    res.json(translated);
  } catch (error: any) {
    console.error('Error translating recipe:', error);
    res.status(500).json({ error: error.message || 'Translation failed' });
  }
});

// POST /manual/batch - create multiple recipes at once
router.post('/manual/batch', async (req: Request, res: Response) => {
  try {
    const { recipes } = req.body;
    if (!Array.isArray(recipes) || recipes.length === 0) {
      res.status(400).json({ error: 'recipes array is required' });
      return;
    }
    const saved = await recipeService.createBatch(recipes);
    res.status(201).json(saved);
  } catch (error: any) {
    console.error('Error batch creating recipes:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

// POST /parse-text - Tier 1: heuristic, Tier 2: AI fallback
router.post('/parse-text', async (req: Request, res: Response) => {
  try {
    const { text } = req.body;
    if (!text) {
      res.status(400).json({ error: 'Text is required' });
      return;
    }

    // Tier 1: heuristic parser
    const parsed = parseRecipeText(text);
    const isGoodParse = parsed.ingredients.length >= 2 && parsed.steps.length >= 1;

    if (isGoodParse) {
      res.json(parsed);
      return;
    }

    // Tier 2: AI fallback
    console.log('Tier 1 parse insufficient, falling back to AI...');
    try {
      const aiParsed = await parseTextWithAI(text);
      res.json(aiParsed[0]);
    } catch (aiError: any) {
      console.error('AI fallback failed:', aiError.message);
      // Return Tier 1 result even if poor
      res.json(parsed);
    }
  } catch (error: any) {
    console.error('Error parsing text:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

// POST /parse-image - parse recipe directly from image with AI
router.post('/parse-image', upload.single('image'), async (req: Request, res: Response) => {
  try {
    if (!req.file) {
      res.status(400).json({ error: 'No image provided' });
      return;
    }
    const recipes = await parseImageWithAI(req.file.buffer, req.file.mimetype);
    res.json({ recipes });
  } catch (error: any) {
    console.error('Error parsing image:', error);
    res.status(500).json({ error: error.message || 'Failed to parse image' });
  }
});

// POST /parse-pdf - Tier 1: pdf-parse + heuristic, Tier 2: AI fallback
router.post('/parse-pdf', upload.single('file'), async (req: Request, res: Response) => {
  try {
    if (!req.file) {
      res.status(400).json({ error: 'No file provided' });
      return;
    }

    // Tier 1: extract text and parse heuristically
    let tier1Result = null;
    try {
      const pdfParse = require('pdf-parse');
      const pdfData = await pdfParse(req.file.buffer);
      const text = pdfData.text;
      if (text && text.trim().length > 0) {
        tier1Result = parseRecipeText(text);
      }
    } catch (e) {
      console.log('PDF text extraction failed, trying AI...');
    }

    const isGoodParse = tier1Result &&
      tier1Result.ingredients.length >= 2 &&
      tier1Result.steps.length >= 1;

    if (isGoodParse) {
      res.json({ recipes: [tier1Result] });
      return;
    }

    // Tier 2: send PDF directly to Gemini
    console.log('Tier 1 PDF parse insufficient, falling back to AI...');
    try {
      const aiParsed = await parsePdfWithAI(req.file.buffer);
      res.json({ recipes: aiParsed });
    } catch (aiError: any) {
      console.error('AI PDF fallback failed:', aiError.message);
      if (tier1Result) {
        res.json({ recipes: [tier1Result] });
      } else {
        res.status(400).json({ error: 'Could not extract recipe from PDF' });
      }
    }
  } catch (error: any) {
    console.error('Error parsing PDF:', error);
    res.status(500).json({ error: error.message || 'Failed to parse PDF' });
  }
});

// POST /upload-image - upload scanned image to Cloudinary
router.post('/upload-image', upload.single('image'), async (req: Request, res: Response) => {
  try {
    if (!req.file) {
      res.status(400).json({ error: 'No image provided' });
      return;
    }

    const result = await new Promise<any>((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        { folder: 'recipe-scans' },
        (error, result) => {
          if (error) reject(new Error(`Upload failed: ${error.message}`));
          else resolve(result);
        }
      );
      stream.end(req.file!.buffer);
    });

    res.json({ url: result.secure_url });
  } catch (error: any) {
    console.error('Error uploading image:', error);
    res.status(500).json({ error: error.message || 'Upload failed' });
  }
});

// GET /tags - get all unique tags (must be before /:id)
router.get('/tags', async (_req: Request, res: Response) => {
  try {
    const tags = await recipeService.getAllTags();
    res.json(tags);
  } catch (error: any) {
    console.error('Error fetching tags:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

// PUT /:id - update a recipe
router.put('/:id', async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const updates = req.body;
    const updated = await recipeService.update(id, updates);
    res.json(updated);
  } catch (error: any) {
    console.error('Error updating recipe:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

// DELETE /:id - delete a recipe
router.delete('/:id', async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    await recipeService.remove(id);
    res.status(204).send();
  } catch (error: any) {
    console.error('Error deleting recipe:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

export default router;
