import { ChatOllama } from '@langchain/community/chat_models/ollama';
import { StringOutputParser } from '@langchain/core/output_parsers';
import { ChatPromptTemplate } from '@langchain/core/prompts';
import { FARMCRAFT_DOCS, QUICK_ANSWERS, MOD_METADATA } from './knowledge-base';

export interface LLMConfig {
  model: string;
  baseUrl: string;
  temperature: number;
}

export class DocsAssistant {
  private llm: ChatOllama;
  private parser: StringOutputParser;
  private prompt: ChatPromptTemplate;

  constructor(config: Partial<LLMConfig> = {}) {
    const fullConfig: LLMConfig = {
      model: config.model || 'gpt-oss',
      baseUrl: config.baseUrl || 'http://localhost:11434',
      temperature: config.temperature ?? 0.7,
    };

    this.llm = new ChatOllama({
      model: fullConfig.model,
      baseUrl: fullConfig.baseUrl,
      temperature: fullConfig.temperature,
    });

    this.parser = new StringOutputParser();

    this.prompt = ChatPromptTemplate.fromMessages([
      [
        'system',
        `You are a helpful assistant for the FarmCraft Minecraft mod. Answer questions clearly and concisely based on the documentation and mod metadata provided.

Mod Information:
{modMetadata}

Documentation Knowledge Base:
{knowledgeBase}

Guidelines:
- Be concise but complete (2-4 sentences for simple questions)
- Use Minecraft terminology
- Provide specific examples with items, blocks, or commands
- If unsure, say so and suggest using /farmcraft help [topic]
- Format responses for in-game chat (keep lines short, avoid excessive breaks)
- Focus on actionable information players can use immediately
- When mentioning recipes, include the actual materials needed
- Reference specific fertilizer types, power foods, or tools by name`,
      ],
      ['human', '{question}'],
    ]);
  }

  /**
   * Answer a question about FarmCraft using LLM
   */
  async ask(question: string): Promise<string> {
    try {
      // Check for quick answers first
      const quickAnswer = this.findQuickAnswer(question);
      if (quickAnswer) {
        return quickAnswer;
      }

      const chain = this.prompt.pipe(this.llm as any).pipe(this.parser as any);

      const response = await chain.invoke({
        modMetadata: this.formatModMetadata(),
        knowledgeBase: this.formatKnowledgeBase(),
        question,
      });

      return String(response).trim();
    } catch (error) {
      console.error('LLM query failed:', error);
      return "Sorry, I couldn't process your question. Try /farmcraft help [topic] instead.";
    }
  }

  /**
   * Get documentation for a specific topic
   */
  async getTopic(topic: string): Promise<string> {
    const topicKey = topic.toLowerCase().replace(/\s+/g, '');
    const docSection = (FARMCRAFT_DOCS as any)[topicKey];

    if (!docSection) {
      return `Topic '${topic}' not found. Available topics: ${Object.keys(FARMCRAFT_DOCS).join(', ')}`;
    }

    return docSection;
  }

  /**
   * Search documentation for relevant sections
   */
  async search(query: string): Promise<string[]> {
    const results: string[] = [];
    const lowerQuery = query.toLowerCase();

    for (const [topic, content] of Object.entries(FARMCRAFT_DOCS)) {
      if (content.toLowerCase().includes(lowerQuery)) {
        results.push(topic);
      }
    }

    return results;
  }

  /**
   * Generate a summary of key features
   */
  async getSummary(): Promise<string> {
    return FARMCRAFT_DOCS.overview;
  }

  /**
   * Check for quick answers to common questions
   */
  private findQuickAnswer(question: string): string | null {
    const lowerQuestion = question.toLowerCase();

    for (const [keyword, answer] of Object.entries(QUICK_ANSWERS)) {
      if (lowerQuestion.includes(keyword)) {
        return answer;
      }
    }

    return null;
  }

  /**
   * Format mod metadata for LLM prompt
   */
  private formatModMetadata(): string {
    return `
Mod: ${MOD_METADATA.modName} v${MOD_METADATA.version}
Minecraft: ${MOD_METADATA.minecraftVersion} (Forge ${MOD_METADATA.forgeVersion})
Description: ${MOD_METADATA.description}

Key Features:
${MOD_METADATA.features.map((f) => `- ${f}`).join('\n')}

Fertilizers:
- Basic: ${MOD_METADATA.fertilizers.basic.join(', ')}
- Enhanced: ${MOD_METADATA.fertilizers.enhanced.join(', ')}
- Superior: ${MOD_METADATA.fertilizers.superior.join(', ')}

Power Foods:
${Object.entries(MOD_METADATA.powerFoods)
  .map(([name, effect]) => `- ${name}: ${effect}`)
  .join('\n')}

Tools: ${MOD_METADATA.tools.join(', ')}

Server Ports:
${Object.entries(MOD_METADATA.servers)
  .map(([name, port]) => `- ${name}: ${port}`)
  .join('\n')}
`.trim();
  }

  /**  /**
   * Format knowledge base for LLM prompt
   */
  private formatKnowledgeBase(): string {
    return Object.entries(FARMCRAFT_DOCS)
      .map(([topic, content]) => `## ${topic}\n${content}`)
      .join('\n\n');
  }

  /**
   * Check if Ollama is available
   */
  async healthCheck(): Promise<boolean> {
    try {
      await this.llm.invoke([['human', 'test']]);
      return true;
    } catch {
      return false;
    }
  }
}

export default DocsAssistant;
