import { StyleSheet, TouchableOpacity, ScrollView, View, TextInput } from 'react-native';
import { useEffect, useState } from 'react';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import ReveilaModule from '@/modules/reveila';

export default function HomeScreen() {
  const [isRunning, setIsRunning] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [autoRestart, setAutoRestart] = useState(false);
  const [logs, setLogs] = useState<any[]>([]);
  const [isSetupComplete, setIsSetupComplete] = useState(true);
  const [promptText, setPromptText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

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
  }, [autoRestart, isStarting, isSetupComplete]);

  const handleStart = async () => {
    if (isStarting) return;
    setIsStarting(true);
    try {
      // Allow overriding system home for development if needed.
      await ReveilaModule.startService(undefined);
      setAutoRestart(true); // Enable watchdog once started manually
    } catch (e) {
      console.error(e);
      setIsStarting(false);
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
    
    // Simulating prompt processing via Reveila Agentic Fabric
    setTimeout(() => {
      setLogs(prev => [{ attributes: { action: 'PROMPT', details: promptText } }, ...prev]);
      setPromptText('');
      setIsProcessing(false);
    }, 1500);
  };

  if (!isSetupComplete) {
    return (
      <ThemedView style={styles.container}>
        <ThemedView style={styles.header}>
          <ThemedText type="title">
            <ThemedText style={{ color: '#00E5FF' }}>REVEILA</ThemedText> Personal
          </ThemedText>
        </ThemedView>
        <ScrollView contentContainerStyle={styles.content}>
          <ThemedView style={styles.card}>
            <ThemedText type="subtitle" style={{marginBottom: 12}}>Welcome to your Sovereign AI</ThemedText>
            <ThemedText style={styles.description}>
              {"Reveila Personal Edition runs entirely on your local hardware. Your agent's memory and data never leave this device."}
            </ThemedText>
            <ThemedText style={[styles.description, {marginBottom: 24, marginTop: 8}]}>
              {"Please click the Setup button below to securely initialize your local \"Brain\" and authorize your Knowledge Vault."}
            </ThemedText>
            <TouchableOpacity style={[styles.button, {backgroundColor: '#00E5FF'}]} onPress={() => ReveilaModule.startSovereignSetup()}>
              <ThemedText style={[styles.buttonText, {color: '#0f172a'}]}>Initialize Sovereign Core</ThemedText>
            </TouchableOpacity>
          </ThemedView>
        </ScrollView>
      </ThemedView>
    );
  }

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <ThemedText type="title">
          <ThemedText style={{ color: '#ff6600' }}>REVEILA</ThemedText> Dashboard
        </ThemedText>
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

        {/* Direct Agent Command Input */}
        <ThemedView style={styles.card}>
          <ThemedText type="subtitle" style={{marginBottom: 8}}>Direct Agent Command</ThemedText>
          <TextInput
            style={styles.textInput}
            multiline
            numberOfLines={4}
            placeholder="Type a prompt for the Sovereign AI..."
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
            <ThemedText style={styles.buttonText}>{isProcessing ? 'Processing...' : 'Execute Command'}</ThemedText>
          </TouchableOpacity>
          <ThemedText style={styles.helperText}>
            * Future updates will integrate directly with WhatsApp and Telegram for native conversational access.
          </ThemedText>
        </ThemedView>

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
