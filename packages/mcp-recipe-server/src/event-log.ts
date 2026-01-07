/**
 * Append-only Event Log with Cryptographic Signatures
 *
 * Each event is signed using the PoW chain, creating
 * a verifiable history of all changes.
 */

import { createHash, randomBytes } from 'crypto';
import { v4 as uuidv4 } from 'uuid';
import { Level } from 'level';

export interface EventData {
  type: string;
  data: unknown;
  timestamp: number;
}

export interface SignedEvent {
  id: string;
  sequence: number;
  type: string;
  data: unknown;
  timestamp: number;
  previousHash: string;
  hash: string;
  signature: string;
  powNonce: number;
  powDifficulty: number;
}

export interface EventQuery {
  since?: number;
  type?: string;
  limit?: number;
  afterSequence?: number;
}

export interface VerificationResult {
  valid: boolean;
  eventId: string;
  hashValid: boolean;
  signatureValid: boolean;
  chainValid: boolean;
  powValid: boolean;
  errors: string[];
}

export class EventLog {
  private db!: Level<string, string>;
  private sequence = 0;
  private lastHash = '0'.repeat(64);
  private serverSecret: string;
  private powDifficulty = 2; // Required leading zeros

  constructor(private dbPath: string) {
    this.serverSecret = process.env.EVENT_LOG_SECRET || randomBytes(32).toString('hex');
  }

  async initialize(): Promise<void> {
    this.db = new Level(this.dbPath, { valueEncoding: 'json' });
    await this.db.open();

    // Recover state from existing events
    try {
      const meta = await this.db.get('_meta');
      const parsed = JSON.parse(meta);
      this.sequence = parsed.sequence || 0;
      this.lastHash = parsed.lastHash || '0'.repeat(64);
    } catch {
      // No existing state
      await this.db.put(
        '_meta',
        JSON.stringify({
          sequence: 0,
          lastHash: '0'.repeat(64),
          createdAt: Date.now(),
        })
      );
    }
  }

  /**
   * Append a new event to the log
   */
  async appendEvent(event: EventData): Promise<SignedEvent> {
    const id = uuidv4();
    const sequence = ++this.sequence;
    const previousHash = this.lastHash;

    // Compute content hash
    const contentData = JSON.stringify({
      id,
      sequence,
      type: event.type,
      data: event.data,
      timestamp: event.timestamp,
      previousHash,
    });

    // Find nonce that satisfies PoW requirement
    const { nonce, hash } = this.findValidNonce(contentData);

    // Sign the hash
    const signature = this.signHash(hash);

    const signedEvent: SignedEvent = {
      id,
      sequence,
      type: event.type,
      data: event.data,
      timestamp: event.timestamp,
      previousHash,
      hash,
      signature,
      powNonce: nonce,
      powDifficulty: this.powDifficulty,
    };

    // Store event
    await this.db.put(`event:${sequence}`, JSON.stringify(signedEvent));
    await this.db.put(`id:${id}`, String(sequence));

    // Update meta
    this.lastHash = hash;
    await this.db.put(
      '_meta',
      JSON.stringify({
        sequence: this.sequence,
        lastHash: this.lastHash,
        updatedAt: Date.now(),
      })
    );

    return signedEvent;
  }

  /**
   * Query events with optional filtering
   */
  async query(options: EventQuery = {}): Promise<SignedEvent[]> {
    const events: SignedEvent[] = [];
    const startSeq = options.afterSequence ?? 0;
    const limit = options.limit ?? 100;

    for (let seq = startSeq + 1; seq <= this.sequence && events.length < limit; seq++) {
      try {
        const eventData = await this.db.get(`event:${seq}`);
        const event = JSON.parse(eventData) as SignedEvent;

        // Apply filters
        if (options.since && event.timestamp < options.since) continue;
        if (options.type && event.type !== options.type) continue;

        events.push(event);
      } catch {
        // Event not found, skip
      }
    }

    return events;
  }

  /**
   * Get a specific event by ID
   */
  async getEvent(eventId: string): Promise<SignedEvent | null> {
    try {
      const seqStr = await this.db.get(`id:${eventId}`);
      const eventData = await this.db.get(`event:${seqStr}`);
      return JSON.parse(eventData) as SignedEvent;
    } catch {
      return null;
    }
  }

  /**
   * Verify an event's integrity
   */
  async verifyEvent(eventId: string): Promise<VerificationResult> {
    const errors: string[] = [];
    const event = await this.getEvent(eventId);

    if (!event) {
      return {
        valid: false,
        eventId,
        hashValid: false,
        signatureValid: false,
        chainValid: false,
        powValid: false,
        errors: ['Event not found'],
      };
    }

    // Verify hash
    const contentData = JSON.stringify({
      id: event.id,
      sequence: event.sequence,
      type: event.type,
      data: event.data,
      timestamp: event.timestamp,
      previousHash: event.previousHash,
    });

    const expectedHash = createHash('sha256')
      .update(contentData + event.powNonce)
      .digest('hex');

    const hashValid = expectedHash === event.hash;
    if (!hashValid) errors.push('Hash mismatch');

    // Verify PoW
    let powValid = true;
    for (let i = 0; i < event.powDifficulty; i++) {
      if (event.hash[i] !== '0') {
        powValid = false;
        errors.push('PoW difficulty not met');
        break;
      }
    }

    // Verify signature
    const expectedSig = this.signHash(event.hash);
    const signatureValid = expectedSig === event.signature;
    if (!signatureValid) errors.push('Invalid signature');

    // Verify chain continuity
    let chainValid = true;
    if (event.sequence > 1) {
      try {
        const prevEventData = await this.db.get(`event:${event.sequence - 1}`);
        const prevEvent = JSON.parse(prevEventData) as SignedEvent;
        if (prevEvent.hash !== event.previousHash) {
          chainValid = false;
          errors.push('Chain broken: previousHash mismatch');
        }
      } catch {
        chainValid = false;
        errors.push('Previous event not found');
      }
    }

    return {
      valid: hashValid && signatureValid && chainValid && powValid,
      eventId,
      hashValid,
      signatureValid,
      chainValid,
      powValid,
      errors,
    };
  }

  /**
   * Verify entire chain integrity
   */
  async verifyChain(): Promise<{
    valid: boolean;
    verifiedCount: number;
    errors: { sequence: number; error: string }[];
  }> {
    const errors: { sequence: number; error: string }[] = [];
    let previousHash = '0'.repeat(64);

    for (let seq = 1; seq <= this.sequence; seq++) {
      try {
        const eventData = await this.db.get(`event:${seq}`);
        const event = JSON.parse(eventData) as SignedEvent;

        if (event.previousHash !== previousHash) {
          errors.push({ sequence: seq, error: 'Chain broken' });
        }

        previousHash = event.hash;
      } catch {
        errors.push({ sequence: seq, error: 'Event missing' });
      }
    }

    return {
      valid: errors.length === 0,
      verifiedCount: this.sequence,
      errors,
    };
  }

  /**
   * Export events for HTTP distribution
   */
  async exportForDistribution(sinceSequence = 0): Promise<{
    events: SignedEvent[];
    chainHash: string;
    serverSignature: string;
  }> {
    const events = await this.query({ afterSequence: sinceSequence, limit: 1000 });

    // Create bundle signature
    const bundleData = JSON.stringify({
      events: events.map((e) => e.hash),
      lastSequence: this.sequence,
      timestamp: Date.now(),
    });

    const bundleHash = createHash('sha256').update(bundleData).digest('hex');
    const serverSignature = this.signHash(bundleHash);

    return {
      events,
      chainHash: this.lastHash,
      serverSignature,
    };
  }

  /**
   * Get current log state
   */
  getState(): { sequence: number; lastHash: string } {
    return {
      sequence: this.sequence,
      lastHash: this.lastHash,
    };
  }

  // Private methods

  private findValidNonce(data: string): { nonce: number; hash: string } {
    let nonce = 0;
    while (true) {
      const hash = createHash('sha256')
        .update(data + nonce)
        .digest('hex');

      let valid = true;
      for (let i = 0; i < this.powDifficulty; i++) {
        if (hash[i] !== '0') {
          valid = false;
          break;
        }
      }

      if (valid) {
        return { nonce, hash };
      }
      nonce++;
    }
  }

  private signHash(hash: string): string {
    return createHash('sha256')
      .update(hash + this.serverSecret)
      .digest('hex');
  }
}
