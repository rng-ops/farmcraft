/**
 * HTTP API for MCP Recipe Server
 *
 * Provides REST endpoints for:
 * - Adding ingredients/recipes via natural language
 * - Querying the append-only event log
 * - Distributing configs to clients via DRM
 */

import express from 'express';
import { createHash } from 'crypto';
import { RecipeStore, Ingredient, Recipe } from './store';
import { EventLog, SignedEvent } from './event-log';
import { RecipeParser } from './parser';
import { ConfigGenerator } from './config-generator';
import { createDistributionServer } from './distribution-server';

export interface HTTPServerOptions {
  port?: number;
  distributionPort?: number;
  enableAuth?: boolean;
  adminToken?: string;
}

export async function createHTTPServer(options: HTTPServerOptions = {}): Promise<{
  app: express.Application;
  store: RecipeStore;
  eventLog: EventLog;
}> {
  const port = options.port || 7422;
  const adminToken = options.adminToken || process.env.ADMIN_TOKEN || 'farmcraft_admin';

  const app = express();
  app.use(express.json());

  // Initialize components
  const store = new RecipeStore('./data/recipes');
  const eventLog = new EventLog('./data/events');
  const parser = new RecipeParser();
  const configGen = new ConfigGenerator();

  await store.initialize();
  await eventLog.initialize();

  // Auth middleware
  const requireAdmin = (
    req: express.Request,
    res: express.Response,
    next: express.NextFunction
  ) => {
    const token = req.headers['x-admin-token'] as string;
    if (options.enableAuth && token !== adminToken) {
      res.status(401).json({ error: 'Unauthorized' });
      return;
    }
    next();
  };

  // ═══════════════════════════════════════════════════════════════════
  // Health & Status
  // ═══════════════════════════════════════════════════════════════════

  app.get('/health', (req, res) => {
    res.json({ status: 'healthy', service: 'mcp-recipe-server' });
  });

  app.get('/status', async (req, res) => {
    const state = await store.getFullState();
    const logState = eventLog.getState();

    res.json({
      ingredients: state.ingredients.length,
      recipes: state.recipes.length,
      effects: state.effects.length,
      eventSequence: logState.sequence,
      lastEventHash: logState.lastHash,
    });
  });

  // ═══════════════════════════════════════════════════════════════════
  // Natural Language API
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Add ingredient via natural language
   * POST /api/ingredients/add
   * Body: { description: string, category: string }
   */
  app.post('/api/ingredients/add', requireAdmin, async (req, res) => {
    try {
      const { description, category } = req.body;

      if (!description || !category) {
        res.status(400).json({ error: 'description and category required' });
        return;
      }

      const ingredient = await parser.parseIngredient(description, category);
      await store.addIngredient(ingredient);

      const event = await eventLog.appendEvent({
        type: 'ingredient_added',
        data: ingredient,
        timestamp: Date.now(),
      });

      res.json({
        success: true,
        ingredient,
        event: {
          id: event.id,
          sequence: event.sequence,
          hash: event.hash,
          signature: event.signature,
        },
      });
    } catch (error) {
      res.status(500).json({ error: (error as Error).message });
    }
  });

  /**
   * Create recipe via natural language
   * POST /api/recipes/add
   * Body: { description: string, type?: string }
   */
  app.post('/api/recipes/add', requireAdmin, async (req, res) => {
    try {
      const { description, type } = req.body;

      if (!description) {
        res.status(400).json({ error: 'description required' });
        return;
      }

      const recipe = await parser.parseRecipe(description, type || 'crafting');

      // Validate
      const validation = await store.validateRecipe(recipe);
      if (!validation.valid) {
        res.status(400).json({
          error: 'Invalid recipe - missing ingredients',
          missing: validation.missing,
        });
        return;
      }

      await store.addRecipe(recipe);

      const event = await eventLog.appendEvent({
        type: 'recipe_created',
        data: recipe,
        timestamp: Date.now(),
      });

      res.json({
        success: true,
        recipe,
        event: {
          id: event.id,
          sequence: event.sequence,
          hash: event.hash,
          signature: event.signature,
        },
      });
    } catch (error) {
      res.status(500).json({ error: (error as Error).message });
    }
  });

  // ═══════════════════════════════════════════════════════════════════
  // Query API
  // ═══════════════════════════════════════════════════════════════════

  app.get('/api/ingredients', async (req, res) => {
    const category = req.query.category as string | undefined;
    const ingredients = await store.getIngredients(category);
    res.json({ ingredients });
  });

  app.get('/api/ingredients/:id', async (req, res) => {
    const ingredient = await store.getIngredient(req.params.id);
    if (!ingredient) {
      res.status(404).json({ error: 'Ingredient not found' });
      return;
    }
    res.json({ ingredient });
  });

  app.get('/api/recipes', async (req, res) => {
    const output = req.query.output as string | undefined;
    const recipes = await store.getRecipes(output);
    res.json({ recipes });
  });

  app.get('/api/recipes/:id', async (req, res) => {
    const recipe = await store.getRecipe(req.params.id);
    if (!recipe) {
      res.status(404).json({ error: 'Recipe not found' });
      return;
    }
    res.json({ recipe });
  });

  // ═══════════════════════════════════════════════════════════════════
  // Event Log API
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Get events since sequence
   * GET /api/events?since=<sequence>&limit=<n>
   */
  app.get('/api/events', async (req, res) => {
    const since = parseInt(req.query.since as string) || 0;
    const limit = parseInt(req.query.limit as string) || 100;
    const type = req.query.type as string | undefined;

    const events = await eventLog.query({
      afterSequence: since,
      limit,
      type,
    });

    res.json({
      events,
      currentSequence: eventLog.getState().sequence,
      chainHash: eventLog.getState().lastHash,
    });
  });

  /**
   * Verify event
   * GET /api/events/:id/verify
   */
  app.get('/api/events/:id/verify', async (req, res) => {
    const result = await eventLog.verifyEvent(req.params.id);
    res.json(result);
  });

  /**
   * Verify entire chain
   * GET /api/chain/verify
   */
  app.get('/api/chain/verify', async (req, res) => {
    const result = await eventLog.verifyChain();
    res.json(result);
  });

  /**
   * Export events for distribution
   * POST /api/events/export
   * Body: { sinceSequence?: number }
   */
  app.post('/api/events/export', async (req, res) => {
    const { sinceSequence } = req.body;
    const distribution = await eventLog.exportForDistribution(sinceSequence || 0);
    res.json(distribution);
  });

  // ═══════════════════════════════════════════════════════════════════
  // Snapshot API
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Create snapshot
   * POST /api/snapshots
   * Body: { version: string, notes?: string }
   */
  app.post('/api/snapshots', requireAdmin, async (req, res) => {
    try {
      const { version, notes } = req.body;

      if (!version) {
        res.status(400).json({ error: 'version required' });
        return;
      }

      const snapshot = await store.createSnapshot(version, notes || '');

      const event = await eventLog.appendEvent({
        type: 'snapshot_created',
        data: {
          version,
          hash: snapshot.hash,
          ingredientCount: snapshot.ingredients.length,
          recipeCount: snapshot.recipes.length,
        },
        timestamp: Date.now(),
      });

      res.json({
        success: true,
        version,
        hash: snapshot.hash,
        eventId: event.id,
        signature: event.signature,
      });
    } catch (error) {
      res.status(500).json({ error: (error as Error).message });
    }
  });

  app.get('/api/snapshots', async (req, res) => {
    const snapshots = await store.listSnapshots();
    res.json({ snapshots });
  });

  app.get('/api/snapshots/:version', async (req, res) => {
    const snapshot = await store.getSnapshot(req.params.version);
    if (!snapshot) {
      res.status(404).json({ error: 'Snapshot not found' });
      return;
    }
    res.json({ snapshot });
  });

  // ═══════════════════════════════════════════════════════════════════
  // Config Generation
  // ═══════════════════════════════════════════════════════════════════

  /**
   * Generate config
   * GET /api/config?format=json&target=forge
   */
  app.get('/api/config', async (req, res) => {
    const format = (req.query.format as string) || 'json';
    const target = (req.query.target as string) || 'forge';

    const state = await store.getFullState();
    const config = await configGen.generate(state, format, target);

    // Sign the config
    const signature = createHash('sha256')
      .update(config + eventLog.getState().lastHash)
      .digest('hex');

    if (format === 'json') {
      res.json({
        config: JSON.parse(config),
        chainHash: eventLog.getState().lastHash,
        signature,
      });
    } else {
      res.type('text/plain').send(config);
    }
  });

  // Start server
  app.listen(port, () => {
    console.log(`MCP Recipe HTTP Server running on port ${port}`);
  });

  // Also start distribution server
  if (options.distributionPort) {
    createDistributionServer(store, eventLog, options.distributionPort);
  }

  return { app, store, eventLog };
}

// CLI entry point
if (require.main === module) {
  createHTTPServer({
    port: parseInt(process.env.PORT || '7422'),
    distributionPort: parseInt(process.env.DIST_PORT || '7423'),
    enableAuth: process.env.ENABLE_AUTH === 'true',
    adminToken: process.env.ADMIN_TOKEN,
  })
    .then(() => {
      console.log('Server started');
    })
    .catch(console.error);
}
