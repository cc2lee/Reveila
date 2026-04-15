import json
import os

with open('../system-home/standard/configs/settings/llm.json', 'r') as f:
    config = json.load(f)

# Remove legacy keys
if 'provider' in config: del config['provider']
if 'endpoint' in config: del config['endpoint']
if 'apiKey' in config: del config['apiKey']
if 'quantization' in config: del config['quantization']

config['ai.worker.llm'] = 'OpenAiProvider'
config['ai.governance.llm'] = 'GeminiProvider'

with open('../system-home/standard/configs/settings/llm.json', 'w') as f:
    json.dump(config, f, indent=4)
