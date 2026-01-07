/**
 * Session Manager
 * Manages WebSocket sessions and player connections
 */

import type { WebSocket } from 'ws';
import type { WorkToken } from '@farmcraft/types';

export interface Session {
  id: string;
  playerId?: string;
  playerName?: string;
  ws: WebSocket;
  createdAt: number;
  lastActivity: number;
  token?: WorkToken;
  challengesCompleted: number;
  computeTimeMs: number;
  capabilities: {
    supportsShaderCompute: boolean;
    gpuVendor?: string;
    gpuModel?: string;
  };
}

// Global session storage for function-based access
const sessions: Map<string, Session> = new Map();
const playerSessions: Map<string, string> = new Map();

/**
 * Get a session by ID (function export for use in handlers)
 */
export function getSession(sessionId: string): Session | undefined {
  return sessions.get(sessionId);
}

/**
 * Update a session (function export for use in handlers)
 */
export function updateSession(sessionId: string, updates: Partial<Session>): void {
  const session = sessions.get(sessionId);
  if (session) {
    Object.assign(session, updates, { lastActivity: Date.now() });
    if (updates.playerId) {
      playerSessions.set(updates.playerId, sessionId);
    }
  }
}

export class SessionManager {
  createSession(sessionId: string, ws: WebSocket): Session {
    const session: Session = {
      id: sessionId,
      ws,
      createdAt: Date.now(),
      lastActivity: Date.now(),
      challengesCompleted: 0,
      computeTimeMs: 0,
      capabilities: {
        supportsShaderCompute: false,
      },
    };

    sessions.set(sessionId, session);
    return session;
  }

  getSession(sessionId: string): Session | undefined {
    return sessions.get(sessionId);
  }

  updateSession(sessionId: string, updates: Partial<Session>): void {
    updateSession(sessionId, updates);
  }

  removeSession(sessionId: string): void {
    const session = sessions.get(sessionId);
    if (session?.playerId) {
      playerSessions.delete(session.playerId);
    }
    sessions.delete(sessionId);
  }

  getSessionByPlayerId(playerId: string): Session | undefined {
    const sessionId = playerSessions.get(playerId);
    return sessionId ? sessions.get(sessionId) : undefined;
  }

  getActiveSessionCount(): number {
    return sessions.size;
  }

  getContributingPlayerCount(): number {
    let count = 0;
    for (const session of sessions.values()) {
      if (session.challengesCompleted > 0) {
        count++;
      }
    }
    return count;
  }

  broadcastMessage(message: object): void {
    const data = JSON.stringify(message);
    for (const session of sessions.values()) {
      if (session.ws.readyState === 1) { // OPEN
        session.ws.send(data);
      }
    }
  }

  recordChallengeCompletion(sessionId: string, computeTimeMs: number): void {
    const session = sessions.get(sessionId);
    if (session) {
      session.challengesCompleted++;
      session.computeTimeMs += computeTimeMs;
      session.lastActivity = Date.now();
    }
  }

  // Cleanup stale sessions
  cleanupStaleSessions(maxIdleMs: number = 30 * 60 * 1000): number {
    const now = Date.now();
    let cleaned = 0;

    for (const [sessionId, session] of sessions) {
      if (now - session.lastActivity > maxIdleMs) {
        session.ws.close(1000, 'Session timeout');
        this.removeSession(sessionId);
        cleaned++;
      }
    }

    return cleaned;
  }
}
