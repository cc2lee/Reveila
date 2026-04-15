with open('../apps/expo/Reveila/app/(tabs)/settings.tsx', 'r') as f:
    code = f.read()

# Replace the provider states with worker and governance
import re

code = re.sub(
    r"const LLM_PROVIDERS.*?;",
    """const LLM_PROVIDERS: any[] = [
  { name: 'OpenAI', defaultEndpoint: 'https://api.openai.com/v1', apiKey: '' },
  { name: 'Anthropic', defaultEndpoint: 'https://api.anthropic.com', apiKey: '' },
  { name: 'Google Gemini', defaultEndpoint: 'https://generativelanguage.googleapis.com', apiKey: '' },
  { name: 'Gemma-3-1b (Local)', defaultEndpoint: 'http://localhost:11434', apiKey: '', quantization: 'Q4_K_M', quantization_options: ['Q4_K_M', 'F16'] },
  { name: 'Custom', defaultEndpoint: '', apiKey: '' }
];

const PROVIDER_TO_CLASS: Record<string, string> = {
  'OpenAI': 'OpenAiProvider',
  'Anthropic': 'AnthropicProvider',
  'Google Gemini': 'GeminiProvider',
  'Gemma-3-1b (Local)': 'OllamaProvider',
  'Custom': 'CustomProvider',
  'Disable': ''
};

const CLASS_TO_PROVIDER: Record<string, string> = {
  'OpenAiProvider': 'OpenAI',
  'AnthropicProvider': 'Anthropic',
  'GeminiProvider': 'Google Gemini',
  'OllamaProvider': 'Gemma-3-1b (Local)',
  'CustomProvider': 'Custom',
  '': 'Disable'
};""",
    code,
    flags=re.DOTALL
)

# This is a big rewrite, writing a static version is safer.
