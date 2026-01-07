/**
 * Message Handlers
 * Handle incoming WebSocket messages
 */

import {
  MessageType,
  ProtocolMessage,
  HelloMessage,
  HelloAckMessage,
  RequestRecipesMessage,
  RecipesResponseMessage,
  RequestChallengeMessage,
  ChallengeIssuedMessage,
  SubmitSolutionMessage,
  SolutionVerifiedMessage,
  SolutionRejectedMessage,
  PingMessage,
  PongMessage,
  ErrorMessage,
  ErrorCode,
  PROTOCOL_VERSION,
} from '@farmcraft/protocol';
import { generateToken } from '@farmcraft/pow-core';

import type { RecipeRegistry } from './recipes';
import type { SessionManager } from './sessions';
import type { ChallengeManager } from './challenges';

interface HandlerContext {
  recipeRegistry: RecipeRegistry;
  sessionManager: SessionManager;
  challengeManager: ChallengeManager;
}

export async function handleMessage(
  message: ProtocolMessage,
  sessionId: string,
  context: HandlerContext
): Promise<ProtocolMessage | null> {
  const { recipeRegistry, sessionManager, challengeManager } = context;

  switch (message.type) {
    case MessageType.HELLO:
      return handleHello(message as HelloMessage, sessionId, context);

    case MessageType.REQUEST_RECIPES:
      return handleRequestRecipes(message as RequestRecipesMessage, sessionId, context);

    case MessageType.REQUEST_CHALLENGE:
      return handleRequestChallenge(message as RequestChallengeMessage, sessionId, context);

    case MessageType.SUBMIT_SOLUTION:
      return handleSubmitSolution(message as SubmitSolutionMessage, sessionId, context);

    case MessageType.PING:
      return handlePing(message as PingMessage);

    default:
      return {
        type: MessageType.ERROR,
        code: ErrorCode.UNKNOWN,
        message: 'Unknown message type',
      } as ErrorMessage;
  }
}

function handleHello(
  message: HelloMessage,
  sessionId: string,
  context: HandlerContext
): HelloAckMessage {
  const { sessionManager } = context;

  // Update session with player info
  sessionManager.updateSession(sessionId, {
    playerId: message.playerId,
    playerName: message.playerName,
    capabilities: message.capabilities,
  });

  // Generate initial token for new players
  const initialToken = generateToken(message.playerId, 10, 24);

  return {
    type: MessageType.HELLO_ACK,
    serverVersion: '1.0.0',
    sessionId,
    serverCapabilities: {
      supportedWorkTypes: ['hash_challenge', 'protein_folding', 'entropy_generation', 'shader_compute'],
      maxConcurrentChallenges: 3,
      tokenExchangeEnabled: true,
    },
    initialToken,
  };
}

function handleRequestRecipes(
  message: RequestRecipesMessage,
  sessionId: string,
  context: HandlerContext
): RecipesResponseMessage | ErrorMessage {
  const { recipeRegistry, sessionManager } = context;

  const session = sessionManager.getSession(sessionId);
  if (!session) {
    return {
      type: MessageType.ERROR,
      code: ErrorCode.INVALID_SESSION,
      message: 'Session not found',
    };
  }

  // Check token
  const token = message.token || session.token;
  if (!token) {
    return {
      type: MessageType.ERROR,
      code: ErrorCode.INSUFFICIENT_TOKENS,
      message: 'No token available. Complete a challenge first.',
    };
  }

  try {
    let recipes = recipeRegistry.getRecipesWithToken(token);

    // Filter by categories if specified
    if (message.categories && message.categories.length > 0) {
      recipes = recipes.filter(r => message.categories!.includes(r.category));
    }

    // Filter by timestamp if specified
    if (message.sinceTimestamp) {
      recipes = recipes.filter(r => 
        !r.discoveredAt || r.discoveredAt > message.sinceTimestamp!
      );
    }

    return {
      type: MessageType.RECIPES_RESPONSE,
      success: true,
      recipes,
      tokensConsumed: 1,
      tokensRemaining: token.credits - 1,
      newChallengeAvailable: true,
    };
  } catch (error) {
    return {
      type: MessageType.ERROR,
      code: ErrorCode.INVALID_TOKEN,
      message: 'Token is invalid or expired',
    };
  }
}

function handleRequestChallenge(
  message: RequestChallengeMessage,
  sessionId: string,
  context: HandlerContext
): ChallengeIssuedMessage | ErrorMessage {
  const { sessionManager, challengeManager } = context;

  const session = sessionManager.getSession(sessionId);
  if (!session) {
    return {
      type: MessageType.ERROR,
      code: ErrorCode.INVALID_SESSION,
      message: 'Session not found',
    };
  }

  const challenge = challengeManager.createChallenge({
    playerId: session.playerId || sessionId,
    preferredType: message.preferredWorkType,
    maxDifficulty: message.maxDifficulty,
  });

  return {
    type: MessageType.CHALLENGE_ISSUED,
    challenge,
  };
}

function handleSubmitSolution(
  message: SubmitSolutionMessage,
  sessionId: string,
  context: HandlerContext
): SolutionVerifiedMessage | SolutionRejectedMessage {
  const { sessionManager, challengeManager } = context;

  const session = sessionManager.getSession(sessionId);
  if (!session) {
    return {
      type: MessageType.SOLUTION_REJECTED,
      challengeId: message.solution.challengeId,
      reason: 'Session not found',
      canRetry: false,
    };
  }

  const result = challengeManager.verifySolution(message.solution);

  if (result.valid) {
    // Update session with completion
    sessionManager.recordChallengeCompletion(sessionId, message.solution.computeTimeMs);
    
    // Store new token in session
    sessionManager.updateSession(sessionId, { token: result.token });

    return {
      type: MessageType.SOLUTION_VERIFIED,
      challengeId: message.solution.challengeId,
      tokensEarned: result.tokensEarned!,
      newToken: result.token!,
      bonusRecipes: result.bonusRecipes,
    };
  } else {
    return {
      type: MessageType.SOLUTION_REJECTED,
      challengeId: message.solution.challengeId,
      reason: result.reason || 'Unknown error',
      canRetry: result.canRetry || false,
    };
  }
}

function handlePing(message: PingMessage): PongMessage {
  return {
    type: MessageType.PONG,
    timestamp: message.timestamp,
    serverTime: Date.now(),
  };
}
