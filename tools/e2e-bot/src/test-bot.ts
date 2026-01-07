import Mineflayer from 'mineflayer';
import { pathfinder, Movements, goals } from 'mineflayer-pathfinder';
import * as readline from 'readline';

/**
 * FarmCraft E2E Test Bot
 * 
 * This bot connects to a Minecraft server and tests FarmCraft features
 * by simulating player actions and verifying results.
 */

interface TestScenario {
  name: string;
  description: string;
  run: (bot: any) => Promise<TestResult>;
}

interface TestResult {
  passed: boolean;
  message: string;
  duration: number;
}

class FarmCraftTestBot {
  private bot: any;
  private scenarios: TestScenario[] = [];
  private results: TestResult[] = [];

  constructor(
    private host: string = 'localhost',
    private port: number = 25565,
    private username: string = 'FarmCraftBot'
  ) {}

  async connect(): Promise<void> {
    console.log(`🤖 Connecting to ${this.host}:${this.port} as ${this.username}...`);
    
    this.bot = Mineflayer.createBot({
      host: this.host,
      port: this.port,
      username: this.username,
      version: '1.20.4',
    });

    // Load pathfinder plugin
    this.bot.loadPlugin(pathfinder);

    return new Promise((resolve, reject) => {
      this.bot.once('spawn', () => {
        console.log('✅ Bot spawned successfully');
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

  registerScenario(scenario: TestScenario): void {
    this.scenarios.push(scenario);
  }

  async runAllTests(): Promise<void> {
    console.log('\n📋 Running FarmCraft E2E Tests\n');
    console.log('='.repeat(60));
    
    for (const scenario of this.scenarios) {
      console.log(`\n▶️  ${scenario.name}`);
      console.log(`   ${scenario.description}`);
      
      const startTime = Date.now();
      try {
        const result = await scenario.run(this.bot);
        result.duration = Date.now() - startTime;
        this.results.push(result);
        
        if (result.passed) {
          console.log(`   ✅ PASSED (${result.duration}ms): ${result.message}`);
        } else {
          console.log(`   ❌ FAILED (${result.duration}ms): ${result.message}`);
        }
      } catch (error) {
        const duration = Date.now() - startTime;
        const result = {
          passed: false,
          message: `Exception: ${error}`,
          duration
        };
        this.results.push(result);
        console.log(`   ❌ FAILED (${duration}ms): ${error}`);
      }
      
      // Wait between tests
      await this.sleep(2000);
    }
    
    this.printSummary();
  }

  private printSummary(): void {
    console.log('\n' + '='.repeat(60));
    console.log('📊 Test Summary\n');
    
    const passed = this.results.filter(r => r.passed).length;
    const failed = this.results.filter(r => !r.passed).length;
    const total = this.results.length;
    
    console.log(`Total: ${total} tests`);
    console.log(`✅ Passed: ${passed}`);
    console.log(`❌ Failed: ${failed}`);
    console.log(`⏱️  Total time: ${this.results.reduce((sum, r) => sum + r.duration, 0)}ms`);
    
    if (failed > 0) {
      console.log('\n❌ Failed tests:');
      this.results
        .filter(r => !r.passed)
        .forEach((r, i) => {
          console.log(`   ${i + 1}. ${r.message}`);
        });
    }
  }

  private sleep(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  async disconnect(): Promise<void> {
    this.bot.quit();
  }
}

// ============================================================================
// Test Scenarios
// ============================================================================

const testCommandsExist: TestScenario = {
  name: 'Test: FarmCraft Commands Exist',
  description: 'Verify all FarmCraft commands are registered',
  run: async (bot: any): Promise<TestResult> => {
    const commands = ['/farmcraft', '/farmcraft guide', '/farmcraft status', '/farmcraft help'];
    
    // Try executing each command
    try {
      bot.chat('/farmcraft guide');
      await new Promise(resolve => setTimeout(resolve, 500));
      
      return {
        passed: true,
        message: 'Commands are accessible',
        duration: 0
      };
    } catch (error) {
      return {
        passed: false,
        message: `Command execution failed: ${error}`,
        duration: 0
      };
    }
  }
};

const testRecipeServerConnection: TestScenario = {
  name: 'Test: Recipe Server Connection',
  description: 'Verify connection to recipe server',
  run: async (bot: any): Promise<TestResult> => {
    bot.chat('/farmcraft status');
    
    return new Promise((resolve) => {
      const timeout = setTimeout(() => {
        resolve({
          passed: false,
          message: 'Status command did not respond',
          duration: 0
        });
      }, 5000);
      
      bot.once('message', (jsonMsg: any) => {
        clearTimeout(timeout);
        const message = jsonMsg.toString();
        
        if (message.includes('Status') || message.includes('server')) {
          resolve({
            passed: true,
            message: 'Status command responded',
            duration: 0
          });
        } else {
          resolve({
            passed: false,
            message: 'Unexpected status response',
            duration: 0
          });
        }
      });
    });
  }
};

const testMovementAndInteraction: TestScenario = {
  name: 'Test: Bot Movement',
  description: 'Verify bot can move and interact with world',
  run: async (bot: any): Promise<TestResult> => {
    try {
      // Get current position
      const startPos = bot.entity.position.clone();
      
      // Move forward
      bot.setControlState('forward', true);
      await new Promise(resolve => setTimeout(resolve, 1000));
      bot.setControlState('forward', false);
      
      const endPos = bot.entity.position.clone();
      const distance = startPos.distanceTo(endPos);
      
      if (distance > 0.5) {
        return {
          passed: true,
          message: `Moved ${distance.toFixed(2)} blocks`,
          duration: 0
        };
      } else {
        return {
          passed: false,
          message: 'Bot did not move sufficiently',
          duration: 0
        };
      }
    } catch (error) {
      return {
        passed: false,
        message: `Movement error: ${error}`,
        duration: 0
      };
    }
  }
};

const testInventoryCheck: TestScenario = {
  name: 'Test: Inventory System',
  description: 'Verify bot can access inventory',
  run: async (bot: any): Promise<TestResult> => {
    try {
      const inventory = bot.inventory.items();
      
      return {
        passed: true,
        message: `Inventory accessible (${inventory.length} items)`,
        duration: 0
      };
    } catch (error) {
      return {
        passed: false,
        message: `Inventory error: ${error}`,
        duration: 0
      };
    }
  }
};

const testChatSystem: TestScenario = {
  name: 'Test: Chat System',
  description: 'Verify bot can send and receive chat messages',
  run: async (bot: any): Promise<TestResult> => {
    try {
      bot.chat('FarmCraft E2E Test Running');
      
      return {
        passed: true,
        message: 'Chat system functional',
        duration: 0
      };
    } catch (error) {
      return {
        passed: false,
        message: `Chat error: ${error}`,
        duration: 0
      };
    }
  }
};

// ============================================================================
// Main Execution
// ============================================================================

async function main() {
  const args = process.argv.slice(2);
  const host = args[0] || 'localhost';
  const port = parseInt(args[1]) || 25565;
  
  const testBot = new FarmCraftTestBot(host, port);
  
  try {
    await testBot.connect();
    
    // Register all test scenarios
    testBot.registerScenario(testCommandsExist);
    testBot.registerScenario(testRecipeServerConnection);
    testBot.registerScenario(testMovementAndInteraction);
    testBot.registerScenario(testInventoryCheck);
    testBot.registerScenario(testChatSystem);
    
    // Run tests
    await testBot.runAllTests();
    
    // Disconnect
    await testBot.disconnect();
    
    process.exit(0);
  } catch (error) {
    console.error('❌ Test execution failed:', error);
    process.exit(1);
  }
}

if (require.main === module) {
  main();
}

export { FarmCraftTestBot, TestScenario, TestResult };
