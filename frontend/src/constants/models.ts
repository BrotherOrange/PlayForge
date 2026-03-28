export const PROVIDER_COLORS: Record<string, string> = {
  openai: '#00d4ff',
  anthropic: '#a78bfa',
  gemini: '#34d399',
};

export const PROVIDER_LABELS: Record<string, string> = {
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  gemini: 'Gemini',
};

export const AVAILABLE_MODELS = [
  { provider: 'openai', modelName: 'gpt-5.4-mini', displayName: 'GPT-5.4 Mini' },
  { provider: 'anthropic', modelName: 'claude-sonnet-4-6', displayName: 'Claude Sonnet 4.6' },
  { provider: 'gemini', modelName: 'gemini-3.1-flash-lite-preview', displayName: 'Gemini 3.1 Flash Lite' },
];
