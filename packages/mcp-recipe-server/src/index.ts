/**
 * @farmcraft/mcp-recipe-server
 * 
 * MCP Server for natural language recipe and ingredient management.
 * Uses LangChain for processing English descriptions into game data.
 * 
 * Features:
 * - Add ingredients via natural language
 * - Create recipes from descriptions
 * - Snapshot and version control
 * - Generate configs for client distribution
 * - Cryptographically signed append-only log
 */

import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
  ListResourcesRequestSchema,
  ReadResourceRequestSchema,
} from '@modelcontextprotocol/sdk/types.js';

import { RecipeStore } from './store';
import { EventLog } from './event-log';
import { RecipeParser } from './parser';
import { ConfigGenerator } from './config-generator';
import { v4 as uuidv4 } from 'uuid';

// Initialize components
const store = new RecipeStore('./data/recipes');
const eventLog = new EventLog('./data/events');
const parser = new RecipeParser();
const configGen = new ConfigGenerator();

// Create MCP server
const server = new Server(
  {
    name: 'farmcraft-recipe-server',
    version: '1.0.0',
  },
  {
    capabilities: {
      tools: {},
      resources: {},
    },
  }
);

// ============================================================================
// Tool Definitions
// ============================================================================

server.setRequestHandler(ListToolsRequestSchema, async () => {
  return {
    tools: [
      {
        name: 'add_ingredient',
        description: 'Add a new ingredient to the game. Describe the ingredient in natural language including its name, type (fertilizer, food, material), effects, and any special properties.',
        inputSchema: {
          type: 'object',
          properties: {
            description: {
              type: 'string',
              description: 'Natural language description of the ingredient',
            },
            category: {
              type: 'string',
              enum: ['fertilizer', 'power_food', 'material', 'crop', 'tool'],
              description: 'Category of the ingredient',
            },
          },
          required: ['description', 'category'],
        },
      },
      {
        name: 'create_recipe',
        description: 'Create a new crafting recipe. Describe what ingredients are needed and what the output should be.',
        inputSchema: {
          type: 'object',
          properties: {
            description: {
              type: 'string',
              description: 'Natural language description of the recipe (e.g., "combine 4 tuff dust with bone meal to make enhanced tuff fertilizer")',
            },
            type: {
              type: 'string',
              enum: ['crafting', 'smelting', 'brewing', 'fertilizing'],
              description: 'Type of recipe',
            },
          },
          required: ['description'],
        },
      },
      {
        name: 'add_effect',
        description: 'Add a new potion effect or buff that can be applied by power foods.',
        inputSchema: {
          type: 'object',
          properties: {
            name: {
              type: 'string',
              description: 'Name of the effect',
            },
            description: {
              type: 'string',
              description: 'What the effect does',
            },
            duration: {
              type: 'number',
              description: 'Duration in seconds',
            },
            amplifier: {
              type: 'number',
              description: 'Effect strength (0-255)',
            },
          },
          required: ['name', 'description'],
        },
      },
      {
        name: 'create_snapshot',
        description: 'Create a versioned snapshot of all current recipes and ingredients.',
        inputSchema: {
          type: 'object',
          properties: {
            version: {
              type: 'string',
              description: 'Version string (e.g., "1.0.0")',
            },
            notes: {
              type: 'string',
              description: 'Release notes for this snapshot',
            },
          },
          required: ['version'],
        },
      },
      {
        name: 'generate_config',
        description: 'Generate client configuration files from current state.',
        inputSchema: {
          type: 'object',
          properties: {
            format: {
              type: 'string',
              enum: ['json', 'toml', 'yaml'],
              description: 'Output format',
            },
            target: {
              type: 'string',
              enum: ['forge', 'fabric', 'both'],
              description: 'Target mod loader',
            },
          },
        },
      },
      {
        name: 'query_events',
        description: 'Query the append-only event log for changes.',
        inputSchema: {
          type: 'object',
          properties: {
            since: {
              type: 'string',
              description: 'ISO timestamp to query from',
            },
            type: {
              type: 'string',
              enum: ['ingredient_added', 'recipe_created', 'snapshot_created', 'all'],
              description: 'Filter by event type',
            },
            limit: {
              type: 'number',
              description: 'Maximum events to return',
            },
          },
        },
      },
      {
        name: 'verify_signature',
        description: 'Verify the cryptographic signature of an event or snapshot.',
        inputSchema: {
          type: 'object',
          properties: {
            eventId: {
              type: 'string',
              description: 'Event ID to verify',
            },
          },
          required: ['eventId'],
        },
      },
      {
        name: 'list_ingredients',
        description: 'List all registered ingredients with optional filtering.',
        inputSchema: {
          type: 'object',
          properties: {
            category: {
              type: 'string',
              description: 'Filter by category',
            },
          },
        },
      },
      {
        name: 'list_recipes',
        description: 'List all registered recipes.',
        inputSchema: {
          type: 'object',
          properties: {
            output: {
              type: 'string',
              description: 'Filter by output item',
            },
          },
        },
      },
    ],
  };
});

// ============================================================================
// Tool Handlers
// ============================================================================

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  switch (name) {
    case 'add_ingredient': {
      const { description, category } = args as { description: string; category: string };
      
      // Parse natural language into structured ingredient
      const ingredient = await parser.parseIngredient(description, category);
      
      // Store ingredient
      await store.addIngredient(ingredient);
      
      // Log event with signature
      const event = await eventLog.appendEvent({
        type: 'ingredient_added',
        data: ingredient,
        timestamp: Date.now(),
      });
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: true,
              ingredient,
              eventId: event.id,
              signature: event.signature,
            }, null, 2),
          },
        ],
      };
    }

    case 'create_recipe': {
      const { description, type } = args as { description: string; type?: string };
      
      // Parse recipe from natural language
      const recipe = await parser.parseRecipe(description, type || 'crafting');
      
      // Validate ingredients exist
      const validation = await store.validateRecipe(recipe);
      if (!validation.valid) {
        return {
          content: [
            {
              type: 'text',
              text: JSON.stringify({
                success: false,
                error: 'Invalid recipe',
                missingIngredients: validation.missing,
              }, null, 2),
            },
          ],
        };
      }
      
      // Store recipe
      await store.addRecipe(recipe);
      
      // Log event
      const event = await eventLog.appendEvent({
        type: 'recipe_created',
        data: recipe,
        timestamp: Date.now(),
      });
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: true,
              recipe,
              eventId: event.id,
              signature: event.signature,
            }, null, 2),
          },
        ],
      };
    }

    case 'add_effect': {
      const { name: effectName, description, duration, amplifier } = args as {
        name: string;
        description: string;
        duration?: number;
        amplifier?: number;
      };
      
      const effect = {
        id: effectName.toLowerCase().replace(/\s+/g, '_'),
        name: effectName,
        description,
        duration: duration || 30,
        amplifier: amplifier || 0,
      };
      
      await store.addEffect(effect);
      
      const event = await eventLog.appendEvent({
        type: 'effect_added',
        data: effect,
        timestamp: Date.now(),
      });
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: true,
              effect,
              eventId: event.id,
            }, null, 2),
          },
        ],
      };
    }

    case 'create_snapshot': {
      const { version, notes } = args as { version: string; notes?: string };
      
      // Get current state
      const state = await store.getFullState();
      
      // Create snapshot
      const snapshot = await store.createSnapshot(version, notes || '');
      
      // Log event
      const event = await eventLog.appendEvent({
        type: 'snapshot_created',
        data: {
          version,
          notes,
          ingredientCount: state.ingredients.length,
          recipeCount: state.recipes.length,
          hash: snapshot.hash,
        },
        timestamp: Date.now(),
      });
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify({
              success: true,
              snapshot: {
                version,
                hash: snapshot.hash,
                eventId: event.id,
                signature: event.signature,
              },
            }, null, 2),
          },
        ],
      };
    }

    case 'generate_config': {
      const { format, target } = args as { format?: string; target?: string };
      
      const state = await store.getFullState();
      const config = await configGen.generate(state, format || 'json', target || 'forge');
      
      return {
        content: [
          {
            type: 'text',
            text: config,
          },
        ],
      };
    }

    case 'query_events': {
      const { since, type, limit } = args as {
        since?: string;
        type?: string;
        limit?: number;
      };
      
      const events = await eventLog.query({
        since: since ? new Date(since).getTime() : undefined,
        type: type !== 'all' ? type : undefined,
        limit: limit || 100,
      });
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify(events, null, 2),
          },
        ],
      };
    }

    case 'verify_signature': {
      const { eventId } = args as { eventId: string };
      
      const result = await eventLog.verifyEvent(eventId);
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify(result, null, 2),
          },
        ],
      };
    }

    case 'list_ingredients': {
      const { category } = args as { category?: string };
      
      const ingredients = await store.getIngredients(category);
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify(ingredients, null, 2),
          },
        ],
      };
    }

    case 'list_recipes': {
      const { output } = args as { output?: string };
      
      const recipes = await store.getRecipes(output);
      
      return {
        content: [
          {
            type: 'text',
            text: JSON.stringify(recipes, null, 2),
          },
        ],
      };
    }

    default:
      throw new Error(`Unknown tool: ${name}`);
  }
});

// ============================================================================
// Resource Handlers
// ============================================================================

server.setRequestHandler(ListResourcesRequestSchema, async () => {
  const snapshots = await store.listSnapshots();
  
  return {
    resources: [
      {
        uri: 'farmcraft://state/current',
        name: 'Current State',
        description: 'Current ingredients, recipes, and effects',
        mimeType: 'application/json',
      },
      {
        uri: 'farmcraft://events/log',
        name: 'Event Log',
        description: 'Append-only event log with signatures',
        mimeType: 'application/json',
      },
      ...snapshots.map((s) => ({
        uri: `farmcraft://snapshot/${s.version}`,
        name: `Snapshot ${s.version}`,
        description: s.notes || `Snapshot created ${new Date(s.timestamp).toISOString()}`,
        mimeType: 'application/json',
      })),
    ],
  };
});

server.setRequestHandler(ReadResourceRequestSchema, async (request) => {
  const { uri } = request.params;
  
  if (uri === 'farmcraft://state/current') {
    const state = await store.getFullState();
    return {
      contents: [
        {
          uri,
          mimeType: 'application/json',
          text: JSON.stringify(state, null, 2),
        },
      ],
    };
  }
  
  if (uri === 'farmcraft://events/log') {
    const events = await eventLog.query({ limit: 1000 });
    return {
      contents: [
        {
          uri,
          mimeType: 'application/json',
          text: JSON.stringify(events, null, 2),
        },
      ],
    };
  }
  
  if (uri.startsWith('farmcraft://snapshot/')) {
    const version = uri.replace('farmcraft://snapshot/', '');
    const snapshot = await store.getSnapshot(version);
    return {
      contents: [
        {
          uri,
          mimeType: 'application/json',
          text: JSON.stringify(snapshot, null, 2),
        },
      ],
    };
  }
  
  throw new Error(`Unknown resource: ${uri}`);
});

// ============================================================================
// Start Server
// ============================================================================

async function main() {
  await store.initialize();
  await eventLog.initialize();
  
  const transport = new StdioServerTransport();
  await server.connect(transport);
  
  console.error('FarmCraft MCP Recipe Server running');
}

main().catch(console.error);

export { server, store, eventLog };
