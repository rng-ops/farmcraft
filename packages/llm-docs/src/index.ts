import { DocsServer } from './server';

const PORT = parseInt(process.env.PORT || '7424', 10);
const OLLAMA_URL = process.env.OLLAMA_URL || 'http://localhost:11434';
const OLLAMA_MODEL = process.env.OLLAMA_MODEL || 'gpt-oss';

async function main() {
  console.log('Starting FarmCraft LLM Documentation Server...');

  const server = new DocsServer({
    port: PORT,
    ollamaUrl: OLLAMA_URL,
    ollamaModel: OLLAMA_MODEL,
  });

  await server.start();

  console.log('\nEndpoints:');
  console.log(`  POST http://localhost:${PORT}/ask - Ask a question`);
  console.log(`  GET  http://localhost:${PORT}/docs/:topic - Get documentation`);
  console.log(`  GET  http://localhost:${PORT}/search?q=query - Search docs`);
  console.log(`  GET  http://localhost:${PORT}/summary - Get overview`);
  console.log(`  GET  http://localhost:${PORT}/topics - List all topics`);
  console.log(`  GET  http://localhost:${PORT}/health - Health check`);
}

main().catch((error) => {
  console.error('Failed to start server:', error);
  process.exit(1);
});
