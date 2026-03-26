import { StyleSheet, TouchableOpacity, ScrollView, View, TextInput } from 'react-native';
import { useEffect, useState } from 'react';
import { useRouter } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import ReveilaModule from '@/modules/reveila';
import { IconSymbol } from '@/components/ui/icon-symbol';

export default function HomeScreen() {
  const router = useRouter();
  const [isRunning, setIsRunning] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [autoRestart, setAutoRestart] = useState(false);
  const [logs, setLogs] = useState<any[]>([]);
  const [isSetupComplete, setIsSetupComplete] = useState(true);
  const [promptText, setPromptText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [startingTimestamp, setStartingTimestamp] = useState<number | null>(null);
  const [selectedModel, setSelectedModel] = useState('Local: Gemma-3-1b');
  const [useLocalModel, setUseLocalModel] = useState<boolean | null>(null);
  const [agentResponse, setAgentResponse] = useState<string | null>(null);

  const availableModels = [
    'Local: Gemma-3-1b',
    'Remote: Gemini 1.5 Flash',
    'Remote: OpenAI GPT-4o-mini'
  ];

  useEffect(() => {
    const checkSetupAndStatus = async () => {
      try {
        // 1. Check if the user has completed the native hardware onboarding
        if (ReveilaModule.isSetupComplete) {
          const setupStatus = await ReveilaModule.isSetupComplete();
          setIsSetupComplete(setupStatus);
        }
        
        // 2. Check Service Status
        const running = await ReveilaModule.isRunning();
        setIsRunning(running);
        
        if (running) {
          setIsStarting(false);
          setStartingTimestamp(null);
        } else if (isStarting && startingTimestamp && Date.now() - startingTimestamp > 300000) {
          // If starting for more than 5 minutes, assume failure and reset
          console.log('Starting timeout reached. Resetting starting state.');
          setIsStarting(false);
          setStartingTimestamp(null);
        }

        // WATCHDOG LOGIC: Only restart if not already starting and watchdog is on
        if (autoRestart && !running && !isStarting && isSetupComplete) {
          console.log('Watchdog: Service died, restarting...');
          handleStart();
        }
      } catch (e) {
        // Module might not be available yet
      }
    };
    
    checkSetupAndStatus();
    const interval = setInterval(checkSetupAndStatus, 5000); // Slower interval
    return () => clearInterval(interval);
  }, [autoRestart, isStarting, isSetupComplete, startingTimestamp]);

  const handleStart = async () => {
    if (isStarting) return;
    setIsStarting(true);
    setStartingTimestamp(Date.now());
    try {
      // Allow overriding system home for development if needed.
      await ReveilaModule.startService(undefined);
      setAutoRestart(true); // Enable watchdog once started manually
    } catch (e) {
      console.error(e);
      setIsStarting(false);
      setStartingTimestamp(null);
    }
  };

  const handleFetchLogs = async () => {
    if (!isRunning || isStarting) return;
    try {
      const componentName = 'DataService';
      const methodName = 'search';
      const params = [{
        entityType: 'AuditLog',
        page: 0,
        size: 5
      }];
      
      const result = await ReveilaModule.invoke(componentName, methodName, params);
      
      if (result && result.content) {
        setLogs(result.content);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleSendPrompt = async () => {
    if (!promptText.trim() || !isRunning) return;
    setIsProcessing(true);
    setAgentResponse(null); // Clear previous response
    
    // Simulating prompt processing via Reveila Agentic Fabric
    setTimeout(() => {
      const mockResponses: { [key: string]: string } = {
        'Local: Gemma-3-1b': "I have analyzed your request locally. Based on the documents in your Sovereign Memory, the identified risk factor is within acceptable parameters.",
        'Remote: Gemini 1.5 Flash': "Gemini Flash response: I've processed your command. The requested data has been synthesized and the summary is ready for review.",
        'Remote: OpenAI GPT-4o-mini': "OpenAI GPT-4o-mini response: Your prompt has been executed. I've successfully connected the requested entities and updated the flight recorder."
      };
      
      const response = mockResponses[selectedModel] || "Command executed successfully.";
      
      setLogs(prev => [{ attributes: { action: 'PROMPT', details: promptText } }, ...prev]);
      setAgentResponse(response);
      setPromptText('');
      setIsProcessing(false);
    }, 1500);
  };

  if (!isSetupComplete) {
    return (
      <ThemedView style={styles.container}>
        <ThemedView style={styles.header}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
            <ThemedText type="title">
              <ThemedText style={{ color: '#00E5FF' }}>REVEILA</ThemedText> Personal
            </ThemedText>
            <TouchableOpacity onPress={() => router.push('/settings')}>
              <IconSymbol name="gearshape.fill" color="#fff" size={24} />
            </TouchableOpacity>
          </View>
        </ThemedView>
        <ScrollView contentContainerStyle={styles.content}>
          <ThemedView style={styles.card}>
            <ThemedText type="subtitle" style={{marginBottom: 12}}>Provision Sovereign AI</ThemedText>
            
            {useLocalModel === null ? (
              <View>
                <ThemedText style={styles.description}>
                  Would you like to download and run a local AI model? This ensures total privacy and offline capability, but requires significant device resources.
                </ThemedText>
                
                <View style={{ gap: 12, marginTop: 24 }}>
                  <TouchableOpacity 
                    style={[styles.button, {backgroundColor: '#00E5FF'}]} 
                    onPress={() => setUseLocalModel(true)}
                  >
                    <ThemedText style={[styles.buttonText, {color: '#0f172a'}]}>Yes, Provision Local Model</ThemedText>
                  </TouchableOpacity>
                  
                  <TouchableOpacity 
                    style={[styles.button, {backgroundColor: '#1e293b'}]} 
                    onPress={() => setUseLocalModel(false)}
                  >
                    <ThemedText style={styles.buttonText}>No, Use Remote Providers Only</ThemedText>
                  </TouchableOpacity>
                </View>

                <ThemedText style={[styles.helperText, {marginTop: 16}]}>
                  * Recommended for devices with 12GB+ RAM and NPU support.
                </ThemedText>
              </View>
            ) : useLocalModel ? (
              <View>
                <ThemedText type="defaultSemiBold" style={{marginBottom: 8}}>Local AI Provisioning:</ThemedText>
                <ThemedText style={styles.description}>{"1. Click the button below to start the secure indexing and model download."}</ThemedText>
                <ThemedText style={styles.description}>{"2. Grant necessary permissions for local storage."}</ThemedText>
                <ThemedText style={styles.description}>{"3. Wait for the local AI models to be fully provisioned (approx. 1.2GB)."}</ThemedText>
                
                <TouchableOpacity 
                  style={[styles.button, {backgroundColor: '#00E5FF', marginTop: 24}]} 
                  onPress={() => ReveilaModule.startSovereignSetup()}
                >
                  <ThemedText style={[styles.buttonText, {color: '#0f172a'}]}>Start Local Provisioning</ThemedText>
                </TouchableOpacity>

                <TouchableOpacity 
                  style={{ marginTop: 12, alignItems: 'center' }} 
                  onPress={() => setUseLocalModel(null)}
                >
                  <ThemedText type="link">Change Selection</ThemedText>
                </TouchableOpacity>
              </View>
            ) : (
              <View>
                <ThemedText type="defaultSemiBold" style={{marginBottom: 8}}>Remote Provider Setup:</ThemedText>
                <ThemedText style={styles.description}>{"To use Reveila without a local model, you must configure at least one remote AI provider. Your data will be sent to the selected provider's API."}</ThemedText>
                
                <View style={{ gap: 12, marginTop: 20 }}>
                  <ThemedView style={styles.providerCard}>
                    <View style={styles.row}>
                      <ThemedText type="defaultSemiBold">Google Gemini</ThemedText>
                      <TouchableOpacity onPress={() => router.push('/settings')}>
                        <ThemedText type="link">Configure</ThemedText>
                      </TouchableOpacity>
                    </View>
                  </ThemedView>
                  
                  <ThemedView style={styles.providerCard}>
                    <View style={styles.row}>
                      <ThemedText type="defaultSemiBold">OpenAI (GPT-4)</ThemedText>
                      <TouchableOpacity onPress={() => router.push('/settings')}>
                        <ThemedText type="link">Configure</ThemedText>
                      </TouchableOpacity>
                    </View>
                  </ThemedView>
                </View>

                <TouchableOpacity 
                  style={[styles.button, {backgroundColor: '#0f172a', marginTop: 24}]} 
                  onPress={() => setIsSetupComplete(true)}
                >
                  <ThemedText style={styles.buttonText}>Finish Setup</ThemedText>
                </TouchableOpacity>

                <TouchableOpacity 
                  style={{ marginTop: 12, alignItems: 'center' }} 
                  onPress={() => setUseLocalModel(null)}
                >
                  <ThemedText type="link">Back to Model Selection</ThemedText>
                </TouchableOpacity>
              </View>
            )}

            <ThemedView style={{ padding: 12, backgroundColor: '#fffbeb', borderRadius: 8, borderWidth: 1, borderColor: '#fef3c7', marginTop: 24 }}>
              <ThemedText style={{ color: '#b45309', fontWeight: 'bold', marginBottom: 4 }}>Security Notice</ThemedText>
              <ThemedText style={{ color: '#92400e', fontSize: 13 }}>
                {"Sovereign AI is designed for maximum privacy. Remote providers may store and use your data for training. Check provider terms before configuring."}
              </ThemedText>
            </ThemedView>
          </ThemedView>
        </ScrollView>
      </ThemedView>
    );
  }
 
  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <ThemedText type="title">
            <ThemedText style={{ color: '#ff6600' }}>REVEILA</ThemedText> Dashboard
          </ThemedText>
          <TouchableOpacity onPress={() => router.push('/settings')}>
            <IconSymbol name="gearshape.fill" color="#fff" size={24} />
          </TouchableOpacity>
        </View>
      </ThemedView>
 
      <ScrollView contentContainerStyle={styles.content}>
        
        {/* Compact Status Area */}
        <ThemedView style={styles.compactStatusCard}>
          <View style={styles.statusBadgeRow}>
            <View style={[styles.badge, { backgroundColor: isStarting ? '#eab308' : (isRunning ? '#22c55e' : '#ef4444') }]}>
               <ThemedText style={styles.badgeText}>{isStarting ? 'ENGINE STARTING' : (isRunning ? 'ENGINE ONLINE' : 'ENGINE OFFLINE')}</ThemedText>
            </View>
            {isRunning && (
              <TouchableOpacity onPress={() => setAutoRestart(!autoRestart)}>
                <View style={[styles.badge, { backgroundColor: autoRestart ? '#3b82f6' : '#64748b' }]}>
                  <ThemedText style={styles.badgeText}>WATCHDOG {autoRestart ? 'ON' : 'OFF'}</ThemedText>
                </View>
              </TouchableOpacity>
            )}
          </View>
          
          {!isRunning && !isStarting && (
            <TouchableOpacity style={[styles.button, {marginTop: 12}]} onPress={handleStart}>
              <ThemedText style={styles.buttonText}>Start Reveila Engine</ThemedText>
            </TouchableOpacity>
          )}
        </ThemedView>
 
        {/* Direct Agent Command Input with Model Picker */}
        <ThemedView style={styles.card}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <ThemedText type="subtitle">Sovereign Agent Command</ThemedText>
            <View style={{ backgroundColor: '#f1f5f9', borderRadius: 8, paddingHorizontal: 8, paddingVertical: 4 }}>
              <ThemedText style={{ fontSize: 11, color: '#64748b', fontWeight: 'bold' }}>MODEL:</ThemedText>
            </View>
          </View>
          
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 12 }}>
            {availableModels.map(model => (
              <TouchableOpacity 
                key={model} 
                style={[
                  styles.modelChip, 
                  selectedModel === model && styles.modelChipActive
                ]}
                onPress={() => setSelectedModel(model)}
              >
                <ThemedText style={[
                  styles.modelChipText,
                  selectedModel === model && styles.modelChipTextActive
                ]}>
                  {model.split(': ')[1]}
                </ThemedText>
              </TouchableOpacity>
            ))}
          </ScrollView>

          <TextInput
            style={styles.textInput}
            multiline
            numberOfLines={4}
            placeholder={`Type a prompt for ${selectedModel.split(': ')[1]}...`}
            placeholderTextColor="#94a3b8"
            value={promptText}
            onChangeText={setPromptText}
            editable={isRunning && !isProcessing}
            textAlignVertical="top"
          />
          <TouchableOpacity 
            style={[styles.button, { marginTop: 12, opacity: (isRunning && promptText.trim()) ? 1 : 0.5 }]}
            disabled={!isRunning || isProcessing || !promptText.trim()}
            onPress={handleSendPrompt}
          >
            <ThemedText style={styles.buttonText}>{isProcessing ? 'Processing...' : `Execute via ${selectedModel.split(': ')[1]}`}</ThemedText>
          </TouchableOpacity>
        </ThemedView>

        {/* Agent Response Area */}
        {(agentResponse || isProcessing) && (
          <ThemedView style={[styles.card, { borderColor: '#00E5FF', borderLeftWidth: 4 }]}>
            <ThemedText type="defaultSemiBold" style={{ color: '#00E5FF', marginBottom: 4 }}>
              Agent Response
            </ThemedText>
            {isProcessing ? (
              <ThemedText style={{ fontStyle: 'italic', color: '#94a3b8' }}>Reasoning in progress...</ThemedText>
            ) : (
              <ThemedText style={styles.responseText}>{agentResponse}</ThemedText>
            )}
          </ThemedView>
        )}
 
        {/* Flight Recorder */}
        <ThemedView style={styles.section}>
          <ThemedView style={styles.sectionHeader}>
            <ThemedView style={{backgroundColor: 'transparent'}}>
              <ThemedText type="subtitle">Flight Recorder</ThemedText>
            </ThemedView>
            <TouchableOpacity onPress={handleFetchLogs} disabled={!isRunning}>
              <ThemedText style={{ color: isRunning ? '#3b82f6' : '#94a3b8' }}>Refresh</ThemedText>
            </TouchableOpacity>
          </ThemedView>
 
          <ThemedView style={styles.tableCard}>
            {logs.length === 0 ? (
              <ThemedText style={styles.emptyText}>No logs available. Start engine and refresh.</ThemedText>
            ) : (
              logs.map((log, i) => (
                <ThemedView key={i} style={[styles.tableRow, i === logs.length - 1 && { borderBottomWidth: 0 }]}>
                  <ThemedText style={styles.logAction}>{log.attributes?.action || log.attributes?.details || 'Unknown Action'}</ThemedText>
                  <ThemedText style={styles.logTime}>{log.attributes?.timestamp || new Date().toLocaleTimeString()}</ThemedText>
                </ThemedView>
              ))
            )}
          </ThemedView>
        </ThemedView>
      </ScrollView>
    </ThemedView>
  );
}
 
const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f1f5f9',
  },
  header: {
    backgroundColor: '#0f172a',
    paddingTop: 60,
    paddingBottom: 20,
    paddingHorizontal: 20,
  },
  content: {
    padding: 20,
    gap: 20,
  },
  section: {
    backgroundColor: 'transparent',
    gap: 8,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    backgroundColor: 'transparent',
    marginBottom: 8,
  },
  description: {
    fontSize: 14,
    color: '#64748b',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  providerCard: {
    backgroundColor: '#f8fafc',
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  compactStatusCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  statusBadgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  badge: {
    paddingVertical: 4,
    paddingHorizontal: 12,
    borderRadius: 16,
  },
  badgeText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: 'bold',
  },
  tableCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
    overflow: 'hidden',
  },
  button: {
    backgroundColor: '#0f172a',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  modelChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#f1f5f9',
    marginRight: 8,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  modelChipActive: {
    backgroundColor: '#0f172a',
    borderColor: '#0f172a',
  },
  modelChipText: {
    fontSize: 12,
    color: '#64748b',
    fontWeight: '600',
  },
  modelChipTextActive: {
    color: '#fff',
  },
  responseText: {
    fontSize: 15,
    color: '#1e293b',
    lineHeight: 22,
  },
  textInput: {
    backgroundColor: '#f8fafc',
    borderColor: '#e2e8f0',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    color: '#0f172a',
    minHeight: 100,
  },
  helperText: {
    fontSize: 11,
    color: '#94a3b8',
    marginTop: 12,
    fontStyle: 'italic',
  },
  tableRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
    backgroundColor: 'transparent',
  },
  logAction: {
    fontWeight: '600',
    color: '#1e293b',
    flexShrink: 1,
    marginRight: 10,
  },
  logTime: {
    fontSize: 12,
    color: '#94a3b8',
  },
  emptyText: {
    padding: 20,
    textAlign: 'center',
    color: '#94a3b8',
  }
});
