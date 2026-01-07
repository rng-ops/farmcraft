/**
 * HTTP Distribution Server
 * 
 * Serves config snapshots to clients via the DRM system.
 * Integrates with the append-only event log for signed distributions.
 */

import express from 'express';
import { createHash } from 'crypto';
import { EventLog } from './event-log';
import { RecipeStore } from './store';
import { ConfigGenerator } from './config-generator';

export interface DistributionRequest {
  clientId: string;
  accessToken: string;
  lastSequence?: number;
  requestedVersion?: string;
  format?: 'json' | 'toml' | 'yaml';
  target?: 'forge' | 'fabric';
}

export interface DistributionResponse {
  success: boolean;
  config?: string;
  events?: unknown[];
  chainHash?: string;
  signature?: string;
  error?: string;
}

export function createDistributionServer(
  store: RecipeStore,
  eventLog: EventLog,
  port = 3002
): express.Application {
  const app = express();
  const configGen = new ConfigGenerator();
  
  app.use(express.json());
  
  // ═══════════════════════════════════════════════════════════════════
  // Config Distribution Endpoints
  // ═══════════════════════════════════════════════════════════════════
  
  /**
   * Get current config
   * Requires valid DRM access token
   */
  app.post('/api/config/current', async (req, res) => {
    const { clientId, accessToken, format, target } = req.body as DistributionRequest;
    
    // Validate access token (integrate with DRM)
    if (!validateAccessToken(accessToken, clientId)) {
      res.status(401).json({
        success: false,
        error: 'Invalid access token',
      });
      return;
    }
    
    try {
      const state = await store.getFullState();
      const config = await configGen.generate(state, format || 'json', target || 'forge');
      const logState = eventLog.getState();
      
      // Sign the response
      const responseData = JSON.stringify({
        config,
        sequence: logState.sequence,
        timestamp: Date.now(),
      });
      const signature = signResponse(responseData);
      
      res.json({
        success: true,
        config,
        chainHash: logState.lastHash,
        sequence: logState.sequence,
        signature,
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        error: 'Failed to generate config',
      });
    }
  });
  
  /**
   * Get events since last sync
   */
  app.post('/api/events/sync', async (req, res) => {
    const { clientId, accessToken, lastSequence } = req.body as DistributionRequest;
    
    if (!validateAccessToken(accessToken, clientId)) {
      res.status(401).json({
        success: false,
        error: 'Invalid access token',
      });
      return;
    }
    
    try {
      const distribution = await eventLog.exportForDistribution(lastSequence || 0);
      
      res.json({
        success: true,
        events: distribution.events,
        chainHash: distribution.chainHash,
        signature: distribution.serverSignature,
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        error: 'Failed to export events',
      });
    }
  });
  
  /**
   * Get specific snapshot
   */
  app.get('/api/snapshot/:version', async (req, res) => {
    const { version } = req.params;
    const accessToken = req.headers['x-access-token'] as string;
    const clientId = req.headers['x-client-id'] as string;
    
    if (!validateAccessToken(accessToken, clientId)) {
      res.status(401).json({
        success: false,
        error: 'Invalid access token',
      });
      return;
    }
    
    try {
      const snapshot = await store.getSnapshot(version);
      
      if (!snapshot) {
        res.status(404).json({
          success: false,
          error: 'Snapshot not found',
        });
        return;
      }
      
      // Sign snapshot
      const signature = signResponse(JSON.stringify(snapshot));
      
      res.json({
        success: true,
        snapshot,
        signature,
      });
    } catch (error) {
      res.status(500).json({
        success: false,
        error: 'Failed to fetch snapshot',
      });
    }
  });
  
  /**
   * Verify event signature
   */
  app.post('/api/verify', async (req, res) => {
    const { eventId } = req.body as { eventId: string };
    
    try {
      const result = await eventLog.verifyEvent(eventId);
      res.json(result);
    } catch (error) {
      res.status(500).json({
        valid: false,
        error: 'Verification failed',
      });
    }
  });
  
  /**
   * Get chain status
   */
  app.get('/api/chain/status', async (req, res) => {
    const state = eventLog.getState();
    const chainVerification = await eventLog.verifyChain();
    
    res.json({
      sequence: state.sequence,
      lastHash: state.lastHash,
      chainValid: chainVerification.valid,
      verifiedCount: chainVerification.verifiedCount,
      errors: chainVerification.errors,
    });
  });
  
  /**
   * List available snapshots
   */
  app.get('/api/snapshots', async (req, res) => {
    const snapshots = await store.listSnapshots();
    res.json({ snapshots });
  });
  
  // Start server
  app.listen(port, () => {
    console.log(`Distribution server running on port ${port}`);
  });
  
  return app;
}

// Helper functions

function validateAccessToken(token: string, clientId: string): boolean {
  // In production, validate against DRM system
  // For now, accept tokens that are 64 hex characters
  return token && token.length === 64 && /^[a-f0-9]+$/i.test(token);
}

function signResponse(data: string): string {
  const secret = process.env.DISTRIBUTION_SECRET || 'farmcraft_distribution_key';
  return createHash('sha256')
    .update(data + secret)
    .digest('hex');
}

export { createDistributionServer as createServer };
