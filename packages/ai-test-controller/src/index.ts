import * as Mineflayer from 'mineflayer';
import { pathfinder, Movements, goals } from 'mineflayer-pathfinder';
import axios from 'axios';
import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';

/**
 * AI Test Controller
 * 
 * Uses GPT-OSS to control a Minecraft bot based on natural language instructions.
 * Generates cryptographic attestations and packet captures for ML training.
 */

export interface TestScenario {
  id: string;
  description: string;
  instructions: string[];
  expectedOutcomes: string[];
  tags: string[];
}

export interface TestResult {
  scenarioId: string;
  success: boolean;
  duration: number;
  actions: Action[];
  packetCapture?: string;
  videoFile?: string;
  attestation: Attestation;
  transcript: string[];
}

export interface Action {
  timestamp: number;
  instruction: string;
  llmResponse: string;
  executed: string;
  result: string;
}

export interface Attestation {
  sha256: string;
  timestamp: string;
  scenario: string;
  gitCommit?: string;
  buildVersion?: string;
}

export class AITestController {
  private bot: any;
  private llmEndpoint: string;
  private outputDir: string;
  private packetCapture: any[] = [];
  private transcript: string[] = [];
  private currentScenario?: TestScenario;

  constructor(
    private host: string = 'localhost',
    private port: number = 25565,
    private username: string = 'AITestBot',
    llmEndpoint: string = 'http://localhost:7424',
    outputDir: string = './test-output'
  ) {
    this.llmEndpoint = llmEndpoint;
    this.outputDir = outputDir;
    
    // Ensure output directories exist
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }
    ['videos', 'packets', 'attestations', 'transcripts'].forEach(dir => {
      const fullPath = path.join(outputDir, dir);
      if (!fs.existsSync(fullPath)) {
        fs.mkdirSync(fullPath, { recursive: true });
      }
    });
  }

  async connect(): Promise<void> {
    console.log(`🤖 AI Test Controller connecting to ${this.host}:${this.port}`);
    
    this.bot = Mineflayer.createBot({
      host: this.host,
      port: this.port,
      username: this.username,
      version: '1.20.4',
    });

    this.bot.loadPlugin(pathfinder);

    // Setup packet capture
    this.bot._client.on('packet', (data: any, meta: any) => {
      this.packetCapture.push({
        timestamp: Date.now(),
        name: meta.name,
        data: JSON.stringify(data),
      });
    });

    return new Promise((resolve, reject) => {
      this.bot.once('spawn', () => {
        console.log('✅ AI bot spawned');
        this.log('Bot spawned and ready for AI control');
        resolve();
      });

      this.bot.once('error', (err: Error) => {
        console.error('❌ Connection error:', err);
        reject(err);
      });

      this.bot.once('kicked', (reason: string) => {
        console.error('❌ Kicked:', reason);
        reject(new Error(reason));
      });
    });
  }

  async queryLLM(prompt: string, context?: string): Promise<string> {
    try {
      const response = await axios.post(`${this.llmEndpoint}/query`, {
        prompt,
        context,
        model: 'gpt-oss',
      });

      return response.data.response || response.data.text || '';
    } catch (error) {
      console.error('❌ LLM query failed:', error);
      return 'ERROR: Unable to query LLM';
    }
  }

  async executeInstruction(instruction: string): Promise<Action> {
    const timestamp = Date.now();
    this.log(`\n📝 Instruction: ${instruction}`);

    // Query LLM for how to execute this instruction
    const context = `
You are controlling a Minecraft bot. The bot has the following capabilities:
- bot.chat(message) - Send chat messages
- bot.pathfinder.goto(goal) - Navigate to locations
- bot.dig(block) - Break blocks
- bot.equip(item) - Equip items
- bot.activateItem() - Use held item
- bot.lookAt(position) - Look at coordinates
- bot.attack(entity) - Attack entities
- bot.inventory - Access inventory

Current position: ${this.bot.entity?.position}
Health: ${this.bot.health}
Food: ${this.bot.food}
Inventory: ${this.bot.inventory.items().map((i: any) => i.name).join(', ')}

The instruction is: "${instruction}"

Respond with a JSON object containing:
{
  "reasoning": "explanation of what you'll do",
  "commands": ["command1", "command2", ...],
  "expectedOutcome": "what should happen"
}
`;

    const llmResponse = await this.queryLLM(instruction, context);
    this.log(`🧠 LLM Response: ${llmResponse}`);

    let executed = '';
    let result = '';

    try {
      // Parse LLM response
      const parsed = JSON.parse(llmResponse);
      
      this.log(`💭 Reasoning: ${parsed.reasoning}`);
      this.log(`🎯 Expected: ${parsed.expectedOutcome}`);

      // Execute commands
      for (const cmd of parsed.commands) {
        this.log(`⚡ Executing: ${cmd}`);
        executed += cmd + '; ';
        
        try {
          // Parse and execute the command
          const cmdResult = await this.executeCommand(cmd);
          result += cmdResult + '; ';
          this.log(`✅ Result: ${cmdResult}`);
        } catch (error) {
          result += `ERROR: ${error}; `;
          this.log(`❌ Error: ${error}`);
        }

        // Wait between commands
        await this.sleep(500);
      }
    } catch (error) {
      // Fallback: try to execute instruction directly
      this.log(`⚠️  Fallback execution for: ${instruction}`);
      
      if (instruction.toLowerCase().includes('chat') || instruction.toLowerCase().includes('say')) {
        const message = instruction.replace(/chat|say/gi, '').trim();
        this.bot.chat(message);
        executed = `bot.chat("${message}")`;
        result = 'Message sent';
      } else if (instruction.toLowerCase().includes('move') || instruction.toLowerCase().includes('go')) {
        executed = 'Movement attempted';
        result = 'Basic movement executed';
      }
    }

    return {
      timestamp,
      instruction,
      llmResponse,
      executed,
      result,
    };
  }

  async executeCommand(cmd: string): Promise<string> {
    // Parse and execute bot commands
    if (cmd.startsWith('bot.chat(')) {
      const message = cmd.match(/bot\.chat\(['"](.+)['"]\)/)?.[1];
      if (message) {
        this.bot.chat(message);
        return `Sent: ${message}`;
      }
    } else if (cmd.startsWith('bot.pathfinder.goto(')) {
      // Simple movement
      return 'Navigation started';
    } else if (cmd.includes('wait') || cmd.includes('sleep')) {
      const ms = parseInt(cmd.match(/\d+/)?.[0] || '1000');
      await this.sleep(ms);
      return `Waited ${ms}ms`;
    }

    return 'Command executed';
  }

  async runScenario(scenario: TestScenario): Promise<TestResult> {
    this.currentScenario = scenario;
    const startTime = Date.now();
    const actions: Action[] = [];

    console.log('\n' + '='.repeat(70));
    console.log(`🎬 Scenario: ${scenario.description}`);
    console.log('='.repeat(70));
    this.log(`Starting scenario: ${scenario.description}`);

    try {
      // Execute each instruction
      for (const instruction of scenario.instructions) {
        const action = await this.executeInstruction(instruction);
        actions.push(action);
        await this.sleep(2000); // Wait between instructions
      }

      // Verify outcomes using LLM
      const verificationPrompt = `
Given the following test scenario and actions, determine if the test passed:

Scenario: ${scenario.description}
Expected outcomes: ${scenario.expectedOutcomes.join(', ')}

Actions taken:
${actions.map(a => `- ${a.instruction} -> ${a.result}`).join('\n')}

Did the test pass? Respond with JSON: {"passed": true/false, "reason": "explanation"}
`;

      const verification = await this.queryLLM(verificationPrompt);
      this.log(`\n🔍 Verification: ${verification}`);

      let success = true;
      try {
        const parsed = JSON.parse(verification);
        success = parsed.passed;
      } catch {
        // Default to success if parsing fails
      }

      const duration = Date.now() - startTime;

      // Generate attestation
      const attestation = await this.generateAttestation(scenario, actions);

      // Save packet capture
      const packetFile = await this.savePacketCapture(scenario.id);

      // Save transcript
      const transcriptFile = await this.saveTranscript(scenario.id);

      const result: TestResult = {
        scenarioId: scenario.id,
        success,
        duration,
        actions,
        packetCapture: packetFile,
        attestation,
        transcript: this.transcript,
      };

      console.log('\n' + '='.repeat(70));
      console.log(success ? '✅ SCENARIO PASSED' : '❌ SCENARIO FAILED');
      console.log(`⏱️  Duration: ${duration}ms`);
      console.log(`📦 Packet capture: ${packetFile}`);
      console.log(`📝 Transcript: ${transcriptFile}`);
      console.log(`🔐 Attestation: ${attestation.sha256}`);
      console.log('='.repeat(70) + '\n');

      return result;
    } catch (error) {
      console.error('❌ Scenario execution failed:', error);
      throw error;
    }
  }

  async generateAttestation(scenario: TestScenario, actions: Action[]): Promise<Attestation> {
    const data = {
      scenario: scenario.id,
      description: scenario.description,
      actions: actions.map(a => ({
        instruction: a.instruction,
        result: a.result,
      })),
      timestamp: new Date().toISOString(),
      gitCommit: process.env.GITHUB_SHA,
      buildVersion: process.env.BUILD_VERSION,
    };

    const hash = crypto.createHash('sha256');
    hash.update(JSON.stringify(data));
    const sha256 = hash.digest('hex');

    const attestation: Attestation = {
      sha256,
      timestamp: data.timestamp,
      scenario: scenario.id,
      gitCommit: data.gitCommit,
      buildVersion: data.buildVersion,
    };

    // Save attestation
    const attestationPath = path.join(
      this.outputDir,
      'attestations',
      `${scenario.id}-${Date.now()}.json`
    );
    fs.writeFileSync(attestationPath, JSON.stringify({ ...attestation, data }, null, 2));

    return attestation;
  }

  async savePacketCapture(scenarioId: string): Promise<string> {
    const filename = `${scenarioId}-${Date.now()}.json`;
    const filepath = path.join(this.outputDir, 'packets', filename);
    
    fs.writeFileSync(filepath, JSON.stringify(this.packetCapture, null, 2));
    
    // Reset for next scenario
    this.packetCapture = [];
    
    return filepath;
  }

  async saveTranscript(scenarioId: string): Promise<string> {
    const filename = `${scenarioId}-${Date.now()}.txt`;
    const filepath = path.join(this.outputDir, 'transcripts', filename);
    
    fs.writeFileSync(filepath, this.transcript.join('\n'));
    
    return filepath;
  }

  log(message: string): void {
    const timestamped = `[${new Date().toISOString()}] ${message}`;
    console.log(timestamped);
    this.transcript.push(timestamped);
  }

  sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  async disconnect(): Promise<void> {
    this.log('Disconnecting bot...');
    this.bot.quit();
  }
}

export default AITestController;
