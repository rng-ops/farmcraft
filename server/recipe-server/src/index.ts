/**
 * FarmCraft Recipe Server
 * Main entry point for the recipe distribution server
 */

import express from 'express';
import { WebSocketServer, WebSocket } from 'ws';
import { createServer } from 'http';
import { v4 as uuidv4 } from 'uuid';
import dotenv from 'dotenv';

import { RecipeRegistry } from './recipes';
import { SessionManager } from './sessions';
import { ChallengeManager } from './challenges';
import { handleMessage } from './handlers';
import {
  handleDRMInit,
  handleDRMChallengeRequest,
  handleDRMResponse,
  handleDRMResourceRequest,
  getDRMStats,
} from './drm-handlers';

dotenv.config();

const PORT = parseInt(process.env.PORT || '7420', 10);
const WS_PORT = parseInt(process.env.WS_PORT || '7421', 10);

// ============================================================================
// Express App (REST API)
// ============================================================================

const app = express();
app.use(express.json());

// Initialize managers
const recipeRegistry = new RecipeRegistry();
const sessionManager = new SessionManager();
const challengeManager = new ChallengeManager();

// Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'healthy',
    version: '1.0.0',
    uptime: process.uptime(),
    connections: sessionManager.getActiveSessionCount(),
  });
});

// Server status
app.get('/status', (req, res) => {
  const drmStats = getDRMStats();
  res.json({
    online: true,
    version: '1.0.0',
    totalRecipes: recipeRegistry.getRecipeCount(),
    activeChallenges: challengeManager.getActiveChallengeCount(),
    connectedClients: sessionManager.getActiveSessionCount(),
    foldingStats: {
      workUnitsCompleted: challengeManager.getCompletedWorkUnits(),
      contributingPlayers: sessionManager.getContributingPlayerCount(),
      totalComputeHours: challengeManager.getTotalComputeHours(),
    },
    drm: {
      activeClients: drmStats.activeClients,
      activeChallenges: drmStats.activeChallenges,
      averageTrustScore: drmStats.averageTrustScore,
    },
  });
});

// Get all recipe categories
app.get('/recipes/categories', (req, res) => {
  res.json(recipeRegistry.getCategories());
});

// REST endpoint for recipe lookup (requires token in header)
app.get('/recipes', (req, res) => {
  const token = req.headers['x-work-token'] as string;

  if (!token) {
    res.status(401).json({
      error: 'No work token provided',
      message: 'Complete a proof-of-work challenge to get a token',
    });
    return;
  }

  try {
    const parsedToken = JSON.parse(Buffer.from(token, 'base64').toString());
    const recipes = recipeRegistry.getRecipesWithToken(parsedToken);
    res.json({
      success: true,
      recipes,
      tokensRemaining: parsedToken.credits - 1,
    });
  } catch (error) {
    res.status(400).json({
      error: 'Invalid token',
      message: 'Token could not be parsed or verified',
    });
  }
});

// Request a new challenge
app.post('/challenge/request', (req, res) => {
  const { playerId, preferredType, maxDifficulty } = req.body;

  if (!playerId) {
    res.status(400).json({ error: 'Player ID required' });
    return;
  }

  const challenge = challengeManager.createChallenge({
    playerId,
    preferredType,
    maxDifficulty,
  });

  res.json({
    success: true,
    challenge,
  });
});

// Submit challenge solution
app.post('/challenge/submit', (req, res) => {
  const { solution } = req.body;

  if (!solution) {
    res.status(400).json({ error: 'Solution required' });
    return;
  }

  const result = challengeManager.verifySolution(solution);

  if (result.valid) {
    res.json({
      success: true,
      tokensEarned: result.tokensEarned,
      token: result.token,
      bonusRecipes: result.bonusRecipes,
    });
  } else {
    res.status(400).json({
      success: false,
      reason: result.reason,
      canRetry: result.canRetry,
    });
  }
});

// ============================================================================
// WebSocket Server (Real-time Communication)
// ============================================================================

const httpServer = createServer(app);
const wss = new WebSocketServer({ port: WS_PORT });

wss.on('connection', (ws: WebSocket, req) => {
  const sessionId = uuidv4();

  console.log(`New WebSocket connection: ${sessionId}`);

  sessionManager.createSession(sessionId, ws);

  ws.on('message', async (data: Buffer) => {
    try {
      const message = JSON.parse(data.toString());

      // Handle DRM-specific messages
      if (message.type?.startsWith('drm_')) {
        switch (message.type) {
          case 'drm_init':
            handleDRMInit(ws, sessionId, message.payload);
            return;
          case 'drm_challenge_request':
            handleDRMChallengeRequest(ws, sessionId, message.payload || {});
            return;
          case 'drm_response':
            handleDRMResponse(ws, sessionId, message.payload);
            return;
          case 'drm_resource_request':
            handleDRMResourceRequest(ws, sessionId, message.payload);
            return;
        }
      }

      // Handle standard protocol messages
      const response = await handleMessage(message, sessionId, {
        recipeRegistry,
        sessionManager,
        challengeManager,
      });

      if (response) {
        ws.send(JSON.stringify(response));
      }
    } catch (error) {
      console.error('Error handling message:', error);
      ws.send(
        JSON.stringify({
          type: 0xff, // ERROR
          code: 0,
          message: 'Internal server error',
        })
      );
    }
  });

  ws.on('close', () => {
    console.log(`Connection closed: ${sessionId}`);
    sessionManager.removeSession(sessionId);
  });

  ws.on('error', (error) => {
    console.error(`WebSocket error for ${sessionId}:`, error);
  });
});

// ============================================================================
// Start Server
// ============================================================================

httpServer.listen(PORT, () => {
  console.log(`FarmCraft Recipe Server running on port ${PORT}`);
  console.log(`WebSocket server running on port ${WS_PORT}`);
  console.log(`Total recipes loaded: ${recipeRegistry.getRecipeCount()}`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('Shutting down...');
  wss.close();
  httpServer.close();
  process.exit(0);
});
