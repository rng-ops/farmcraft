import express, { Request, Response } from 'express';
import cors from 'cors';
import { DocsAssistant } from './assistant';

export interface ServerConfig {
  port: number;
  ollamaUrl?: string;
  ollamaModel?: string;
}

export class DocsServer {
  private app: express.Application;
  private assistant: DocsAssistant;
  private config: ServerConfig;

  constructor(config: ServerConfig) {
    this.config = config;
    this.app = express();
    this.assistant = new DocsAssistant({
      baseUrl: config.ollamaUrl || 'http://localhost:11434',
      model: config.ollamaModel || 'gpt-oss',
    });

    this.setupMiddleware();
    this.setupRoutes();
  }

  private setupMiddleware() {
    this.app.use(cors());
    this.app.use(express.json());
  }

  private setupRoutes() {
    // Health check
    this.app.get('/health', async (req: Request, res: Response) => {
      const ollamaHealthy = await this.assistant.healthCheck();
      res.json({
        status: 'ok',
        ollama: ollamaHealthy ? 'connected' : 'disconnected',
        timestamp: new Date().toISOString(),
      });
    });

    // Ask a question
    this.app.post('/ask', async (req: Request, res: Response): Promise<void> => {
      try {
        const { question } = req.body;

        if (!question) {
          res.status(400).json({ error: 'Question is required' });
          return;
        }

        const answer = await this.assistant.ask(question);
        res.json({ question, answer });
      } catch (error) {
        console.error('Error processing question:', error);
        res.status(500).json({ error: 'Failed to process question' });
      }
    });

    // Get documentation topic
    this.app.get('/docs/:topic', async (req: Request, res: Response) => {
      try {
        const { topic } = req.params;
        const content = await this.assistant.getTopic(topic);
        res.json({ topic, content });
      } catch (error) {
        console.error('Error fetching topic:', error);
        res.status(500).json({ error: 'Failed to fetch documentation' });
      }
    });

    // Search documentation
    this.app.get('/search', async (req: Request, res: Response): Promise<void> => {
      try {
        const { q } = req.query;

        if (!q || typeof q !== 'string') {
          res.status(400).json({ error: "Query parameter 'q' is required" });
          return;
        }

        const results = await this.assistant.search(q);
        res.json({ query: q, results });
      } catch (error) {
        console.error('Error searching:', error);
        res.status(500).json({ error: 'Failed to search documentation' });
      }
    });

    // Get overview/summary
    this.app.get('/summary', async (req: Request, res: Response) => {
      try {
        const summary = await this.assistant.getSummary();
        res.json({ summary });
      } catch (error) {
        console.error('Error fetching summary:', error);
        res.status(500).json({ error: 'Failed to fetch summary' });
      }
    });

    // List all topics
    this.app.get('/topics', (req: Request, res: Response) => {
      const topics = [
        'overview',
        'commands',
        'recipeSystem',
        'proofOfWork',
        'fertilizers',
        'powerFoods',
        'troubleshooting',
        'configuration',
        'development',
      ];
      res.json({ topics });
    });
  }

  async start(): Promise<void> {
    return new Promise((resolve) => {
      this.app.listen(this.config.port, () => {
        console.log(`FarmCraft Docs Server running on port ${this.config.port}`);
        console.log(`Ollama: ${this.config.ollamaUrl || 'http://localhost:11434'}`);
        console.log(`Model: ${this.config.ollamaModel || 'gpt-oss'}`);
        resolve();
      });
    });
  }
}

export default DocsServer;
