import { DocsAssistant } from './assistant';

async function demo() {
  console.log('FarmCraft LLM Docs Assistant Demo\n');

  const assistant = new DocsAssistant({
    model: 'gpt-oss',
    baseUrl: 'http://localhost:11434',
  });

  // Check Ollama connection
  console.log('Checking Ollama connection...');
  const healthy = await assistant.healthCheck();
  console.log(`Ollama status: ${healthy ? '✓ Connected' : '✗ Not available'}\n`);

  if (!healthy) {
    console.log('Make sure Ollama is running: ollama serve');
    console.log('And gpt-oss model is installed: ollama pull gpt-oss');
    return;
  }

  // Test questions
  const questions = [
    'How do I unlock new recipes?',
    'What are power foods?',
    'How does the proof-of-work system work?',
    "My fertilizer isn't working, what should I do?",
  ];

  for (const question of questions) {
    console.log(`Q: ${question}`);
    const answer = await assistant.ask(question);
    console.log(`A: ${answer}\n`);
  }

  // Get a specific topic
  console.log("Fetching 'commands' documentation...");
  const commands = await assistant.getTopic('commands');
  console.log(commands.substring(0, 300) + '...\n');

  // Search documentation
  console.log("Searching for 'fertilizer'...");
  const results = await assistant.search('fertilizer');
  console.log(`Found in topics: ${results.join(', ')}\n`);
}

demo().catch(console.error);
