import dotenv from 'dotenv';
dotenv.config();

import express from 'express';
import recipeRoutes from './routes/recipes';

const app = express();
const PORT = process.env.PORT || 3000;

// CORS enabled
app.use((_req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept');
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  if (_req.method === 'OPTIONS') {
    res.sendStatus(200);
    return;
  }
  next();
});

// JSON body parsing
app.use(express.json());

// Mount recipe routes
app.use('/recipes', recipeRoutes);

app.listen(Number(PORT), '0.0.0.0', () => {
  console.log(`Recipe backend running on 0.0.0.0:${PORT}`);
});
