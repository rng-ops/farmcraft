/**
 * FarmCraft Protocol
 * Wire protocol definitions for client-server communication
 */

import type {
  Recipe,
  RecipeCategory,
  WorkChallenge,
  WorkSolution,
  WorkToken,
  FoldingWorkUnit,
  FoldingResult,
  ServerStatus,
} from '@farmcraft/types';

// ============================================================================
// Protocol Version
// ============================================================================

export const PROTOCOL_VERSION = '1.0.0';
export const MIN_COMPATIBLE_VERSION = '1.0.0';

// ============================================================================
// Message Types
// ============================================================================

export enum MessageType {
  // Handshake
  HELLO = 0x01,
  HELLO_ACK = 0x02,
  
  // Recipe Operations
  REQUEST_RECIPES = 0x10,
  RECIPES_RESPONSE = 0x11,
  RECIPE_UPDATE = 0x12,
  
  // Proof of Work
  REQUEST_CHALLENGE = 0x20,
  CHALLENGE_ISSUED = 0x21,
  SUBMIT_SOLUTION = 0x22,
  SOLUTION_VERIFIED = 0x23,
  SOLUTION_REJECTED = 0x24,
  
  // Token Operations
  REQUEST_TOKEN_BALANCE = 0x30,
  TOKEN_BALANCE = 0x31,
  TOKEN_TRANSFER = 0x32,
  
  // Folding Work Units
  REQUEST_FOLDING_WORK = 0x40,
  FOLDING_WORK_UNIT = 0x41,
  SUBMIT_FOLDING_RESULT = 0x42,
  FOLDING_RESULT_ACK = 0x43,
  
  // Status
  SERVER_STATUS = 0x50,
  PING = 0x51,
  PONG = 0x52,
  
  // Errors
  ERROR = 0xFF,
}

// ============================================================================
// Message Definitions
// ============================================================================

export interface HelloMessage {
  type: MessageType.HELLO;
  protocolVersion: string;
  clientVersion: string;
  playerId: string;
  playerName: string;
  capabilities: ClientCapabilities;
}

export interface ClientCapabilities {
  supportsShaderCompute: boolean;
  gpuVendor?: string;
  gpuModel?: string;
  maxWorkgroupSize?: number;
  supportedWorkTypes: string[];
}

export interface HelloAckMessage {
  type: MessageType.HELLO_ACK;
  serverVersion: string;
  sessionId: string;
  serverCapabilities: ServerCapabilities;
  initialToken?: WorkToken;
}

export interface ServerCapabilities {
  supportedWorkTypes: string[];
  maxConcurrentChallenges: number;
  tokenExchangeEnabled: boolean;
}

export interface RequestRecipesMessage {
  type: MessageType.REQUEST_RECIPES;
  sessionId: string;
  token?: WorkToken;
  categories?: RecipeCategory[];
  sinceTimestamp?: number;
}

export interface RecipesResponseMessage {
  type: MessageType.RECIPES_RESPONSE;
  success: boolean;
  recipes: Recipe[];
  tokensConsumed: number;
  tokensRemaining: number;
  newChallengeAvailable: boolean;
}

export interface RequestChallengeMessage {
  type: MessageType.REQUEST_CHALLENGE;
  sessionId: string;
  preferredWorkType?: string;
  maxDifficulty?: number;
}

export interface ChallengeIssuedMessage {
  type: MessageType.CHALLENGE_ISSUED;
  challenge: WorkChallenge;
}

export interface SubmitSolutionMessage {
  type: MessageType.SUBMIT_SOLUTION;
  sessionId: string;
  solution: WorkSolution;
}

export interface SolutionVerifiedMessage {
  type: MessageType.SOLUTION_VERIFIED;
  challengeId: string;
  tokensEarned: number;
  newToken: WorkToken;
  bonusRecipes?: Recipe[];
}

export interface SolutionRejectedMessage {
  type: MessageType.SOLUTION_REJECTED;
  challengeId: string;
  reason: string;
  canRetry: boolean;
}

export interface RequestFoldingWorkMessage {
  type: MessageType.REQUEST_FOLDING_WORK;
  sessionId: string;
  preferredProjectId?: string;
  maxComplexity?: number;
}

export interface FoldingWorkUnitMessage {
  type: MessageType.FOLDING_WORK_UNIT;
  workUnit: FoldingWorkUnit;
  estimatedTimeSeconds: number;
  rewardTokens: number;
}

export interface SubmitFoldingResultMessage {
  type: MessageType.SUBMIT_FOLDING_RESULT;
  sessionId: string;
  result: FoldingResult;
}

export interface FoldingResultAckMessage {
  type: MessageType.FOLDING_RESULT_ACK;
  workUnitId: string;
  accepted: boolean;
  tokensEarned: number;
  contributionStats: {
    totalWorkUnitsCompleted: number;
    rank: number;
    percentile: number;
  };
}

export interface ServerStatusMessage {
  type: MessageType.SERVER_STATUS;
  status: ServerStatus;
}

export interface PingMessage {
  type: MessageType.PING;
  timestamp: number;
}

export interface PongMessage {
  type: MessageType.PONG;
  timestamp: number;
  serverTime: number;
}

export interface ErrorMessage {
  type: MessageType.ERROR;
  code: ErrorCode;
  message: string;
  details?: unknown;
}

export enum ErrorCode {
  UNKNOWN = 0,
  INVALID_PROTOCOL_VERSION = 1,
  INVALID_SESSION = 2,
  INVALID_TOKEN = 3,
  INSUFFICIENT_TOKENS = 4,
  CHALLENGE_EXPIRED = 5,
  INVALID_SOLUTION = 6,
  RATE_LIMITED = 7,
  SERVER_OVERLOADED = 8,
  MAINTENANCE = 9,
}

// ============================================================================
// Message Union Type
// ============================================================================

export type ProtocolMessage =
  | HelloMessage
  | HelloAckMessage
  | RequestRecipesMessage
  | RecipesResponseMessage
  | RequestChallengeMessage
  | ChallengeIssuedMessage
  | SubmitSolutionMessage
  | SolutionVerifiedMessage
  | SolutionRejectedMessage
  | RequestFoldingWorkMessage
  | FoldingWorkUnitMessage
  | SubmitFoldingResultMessage
  | FoldingResultAckMessage
  | ServerStatusMessage
  | PingMessage
  | PongMessage
  | ErrorMessage;

// ============================================================================
// Serialization Helpers
// ============================================================================

export function serializeMessage(message: ProtocolMessage): Buffer {
  const json = JSON.stringify(message);
  const length = Buffer.byteLength(json, 'utf8');
  const buffer = Buffer.alloc(4 + length);
  buffer.writeUInt32BE(length, 0);
  buffer.write(json, 4, 'utf8');
  return buffer;
}

export function deserializeMessage(buffer: Buffer): ProtocolMessage {
  const length = buffer.readUInt32BE(0);
  const json = buffer.toString('utf8', 4, 4 + length);
  return JSON.parse(json) as ProtocolMessage;
}

export function isValidMessage(message: unknown): message is ProtocolMessage {
  if (typeof message !== 'object' || message === null) {
    return false;
  }
  const msg = message as Record<string, unknown>;
  return typeof msg.type === 'number' && msg.type in MessageType;
}
