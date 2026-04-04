import { StyleSheet, TouchableOpacity, ScrollView, View, TextInput, ActivityIndicator, Alert, Modal } from 'react-native';
import { useEffect, useState, useRef } from 'react';
import { useRouter } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import ReveilaModule from '@/modules/reveila';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { MasterPasswordSetup } from '@/components/MasterPasswordSetup';
import { LockScreen } from '@/components/LockScreen';
import { ChangePasswordModal } from '@/components/ChangePasswordModal';

export default function HomeScreen() {
  const router = useRouter();
  const [isRunning, setIsRunning] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [autoRestart, setAutoRestart] = useState(true);
  const [logs, setLogs] = useState<any[]>([]);
  const [isSetupComplete, setIsSetupComplete] = useState(true);
  const [promptText, setPromptText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [startingTimestamp, setStartingTimestamp] = useState<number | null>(null);
  const [selectedModel, setSelectedModel] = useState('Local: Gemma-3-1b');
  const [agentResponse, setAgentResponse] = useState<string | null>(null);
  const [isCloudMode, setIsCloudMode] = useState(false);
  const setupCompleteRef = useRef(false);

  // Auth & Session State
  const [isLocked, setIsLocked] = useState(false);
  const [needsIdentitySetup, setNeedsIdentitySetup] = useState(false);
  const [showChangePassword, setShowChangePassword] = useState(false);

  const availableModels = isCloudMode ? [
    'Remote: Gemini 1.5 Flash',
    'Remote: OpenAI GPT-4o-mini'
  ] : [
    'Local: Gemma-3-1b'
  ];

  useEffect(() => {
    const checkSession = async () => {
      try {
        if (isRunning && !isStarting) {
          const valid = await ReveilaModule.isSessionValid();
          setIsLocked(!valid);
        }
      } catch (e) {}
    };
    const interval = setInterval(checkSession, 10000);
    return () => clearInterval(interval);
  }, [isRunning, isStarting]);

  useEffect(() => {
    if (isCloudMode) {
      setSelectedModel('Remote: Gemini 1.5 Flash');
    } else {
      setSelectedModel('Local: Gemma-3-1b');
    }
  }, [isCloudMode]);

  useEffect(() => {
    const checkSetupAndStatus = async () => {
      try {
        if (ReveilaModule.isSetupComplete) {
          const setupStatus = await ReveilaModule.isSetupComplete();
          setIsSetupComplete(setupStatus);
          if (setupStatus && !setupCompleteRef.current && !isRunning && !isStarting) {
            handleStart();
            setupCompleteRef.current = true;
          }
        }
        const running = await ReveilaModule.isRunning();
        setIsRunning(running);
        if (running) {
          setIsStarting(false);
          setStartingTimestamp(null);
        } else if (isStarting && startingTimestamp && Date.now() - startingTimestamp > 300000) {
          setIsStarting(false);
          setStartingTimestamp(null);
        }
        if (autoRestart && !running && !isStarting && isSetupComplete) {
          handleStart();
        }
      } catch (e) { }
    };
    checkSetupAndStatus();
    const interval = setInterval(checkSetupAndStatus, 5000);
    return () => clearInterval(interval);
  }, [autoRestart, isStarting, isSetupComplete, startingTimestamp, isRunning]);

  const handleStart = async () => {
    if (isStarting) return;
    setIsStarting(true);
    setStartingTimestamp(Date.now());
    try {
      await ReveilaModule.startService(undefined);
    } catch (e) {
      setIsStarting(false);
      setStartingTimestamp(null);
    }
  };

  const handleFetchLogs = async () => {
    if (!isRunning || isStarting) return;
    try {
      const result = await ReveilaModule.invoke('DataService', 'search', [{ entityType: 'AuditLog', page: 0, size: 5 }]);
      if (result && result.content) setLogs(result.content);
    } catch (e) { }
  };

  const handleSendPrompt = async () => {
    if (!promptText.trim() || !isRunning) return;
    setIsProcessing(true);
    setAgentResponse(null);

    try {
      // Call the real AgenticFabric
      const result = await ReveilaModule.invoke('AgenticFabric', 'askAgent', [promptText, null]);
      setLogs(prev => [{ attributes: { action: 'PROMPT', details: promptText } }, ...prev]);
      setAgentResponse(typeof result === 'string' ? result : JSON.stringify(result));
      setPromptText('');
    } catch (e: any) {
      const errorMsg = `Error communicating with AI: ${e.message}`;
      const stack = e.stack || "No JS stack available";

      // Log to terminal (via expo-cli)
      console.error(errorMsg, stack);

      // Display in the UI response card
      setAgentResponse(`${errorMsg}\n\n[JS STACK]:\n${stack}`);
    } finally {
      setIsProcessing(false);
    }
  };

  if (!isSetupComplete) {
    return (
      <ThemedView style={styles.container}>
        <ThemedView style={styles.header}>
          <View style={styles.headerRow}>
            <ThemedText type="title"><ThemedText style={{ color: '#00E5FF' }}>REVEILA</ThemedText> Private</ThemedText>
          </View>
        </ThemedView>
        <ScrollView contentContainerStyle={styles.content}>
          <ThemedView style={styles.card}>
            <ThemedText type="subtitle" style={{ marginBottom: 12 }}>Setup Private AI</ThemedText>
            <ThemedText style={styles.description}>Start the secure setup and download to enable private, local AI features.</ThemedText>
            <TouchableOpacity style={[styles.button, { backgroundColor: '#00E5FF', marginTop: 24 }]} onPress={() => ReveilaModule.startSovereignSetup()}>
              <ThemedText style={[styles.buttonText, { color: '#0f172a' }]}>Start Private Setup</ThemedText>
            </TouchableOpacity>
          </ThemedView>
        </ScrollView>
      </ThemedView>
    );
  }

  if (needsIdentitySetup) {
    return <MasterPasswordSetup onComplete={() => setNeedsIdentitySetup(false)} />;
  }

  if (isLocked) {
    return <LockScreen onUnlock={() => setIsLocked(false)} />;
  }

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <View style={styles.headerRow}>
          <ThemedText type="title"><ThemedText style={{ color: '#ff6600' }}>REVEILA</ThemedText></ThemedText>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <TouchableOpacity onPress={() => setIsCloudMode(!isCloudMode)}>
              <View style={[styles.miniBadge, { backgroundColor: isCloudMode ? '#3b82f6' : '#22c55e', flexDirection: 'row', alignItems: 'center', gap: 4 }]}>
                <ThemedText style={styles.miniBadgeText}>{isCloudMode ? 'CLOUD' : 'PRIVATE'}</ThemedText>
              </View>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => router.push('/settings')}>
              <IconSymbol name="gearshape.fill" color="#fff" size={20} />
            </TouchableOpacity>
          </View>
        </View>
      </ThemedView>
      <View style={{ flex: 1 }}>
        <ScrollView contentContainerStyle={styles.content} style={{ flex: 1 }}>
          {(agentResponse || isProcessing) && (
            <ThemedView style={[styles.responseCard, { borderColor: isCloudMode ? '#3b82f6' : '#22c55e', borderLeftWidth: 4 }]}>
              <ThemedText type="defaultSemiBold" style={{ color: isCloudMode ? '#3b82f6' : '#22c55e', marginBottom: 8, fontSize: 11 }}>
                {isCloudMode ? 'CLOUD RESPONSE' : 'LOCAL REASONING'}
              </ThemedText>
              {isProcessing ? (
                <ThemedText style={{ fontStyle: 'italic', color: '#94a3b8' }}>Generating answer...</ThemedText>
              ) : (
                <ThemedText selectable={true} style={styles.responseText}>{agentResponse}</ThemedText>
              )}
            </ThemedView>
          )}
          {!isRunning && !isStarting && (
            <TouchableOpacity style={styles.button} onPress={handleStart}>
              <ThemedText style={styles.buttonText}>Restart AI System</ThemedText>
            </TouchableOpacity>
          )}
          <View style={styles.modelSelectorContainer}>
            <ThemedText style={styles.sectionLabel}>{isCloudMode ? 'REMOTE MODELS' : 'LOCAL MODELS'}</ThemedText>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipScroll}>
              {availableModels.map(model => (
                <TouchableOpacity key={model} style={[styles.modelChip, selectedModel === model && styles.modelChipActive]} onPress={() => setSelectedModel(model)}>
                  <ThemedText style={[styles.modelChipText, selectedModel === model && styles.modelChipTextActive]}>{model.split(': ')[1]}</ThemedText>
                </TouchableOpacity>
              ))}
            </ScrollView>
          </View>
          
          {!isRunning && !isStarting && (
            <TouchableOpacity style={{ marginTop: 10, padding: 10 }} onPress={() => setShowChangePassword(true)}>
              <ThemedText style={{ color: '#ff6600', fontSize: 13, fontWeight: '700', textAlign: 'center' }}>Change Master Password</ThemedText>
            </TouchableOpacity>
          )}

          <Modal visible={showChangePassword} animationType="slide" transparent>
            <View style={{ flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center' }}>
              <ChangePasswordModal onCancel={() => setShowChangePassword(false)} onSuccess={() => setShowChangePassword(false)} />
            </View>
          </Modal>

          <ThemedView style={styles.inputCard}>
            <TextInput
              style={styles.textInput}
              multiline
              placeholder={isCloudMode ? "Ask the cloud..." : "Talk to private agent..."}
              placeholderTextColor="#94a3b8"
              value={promptText}
              onChangeText={setPromptText}
              editable={isRunning && !isProcessing}
              textAlignVertical="top"
            />
            <TouchableOpacity
              style={[styles.sendButton, { opacity: (isRunning && promptText.trim()) ? 1 : 0.5 }]}
              disabled={!isRunning || isProcessing || !promptText.trim()}
              onPress={handleSendPrompt}
            >
              <ThemedText style={styles.buttonText}>{isProcessing ? '...' : 'GO'}</ThemedText>
            </TouchableOpacity>
          </ThemedView>
          <View style={styles.minimalRecorder}>
            <View style={styles.row}>
              <ThemedText style={styles.sectionLabel}>ACTIVITY HISTORY</ThemedText>
              <TouchableOpacity onPress={handleFetchLogs} disabled={!isRunning}>
                <ThemedText style={{ color: '#3b82f6', fontSize: 10, fontWeight: '700' }}>REFRESH</ThemedText>
              </TouchableOpacity>
            </View>
            <ThemedView style={styles.miniTable}>
              {logs.length > 0 ? logs.slice(0, 3).map((log, i) => (
                <View key={i} style={styles.miniTableRow}>
                  <ThemedText numberOfLines={1} style={styles.miniLogText}>{log.attributes?.action || log.attributes?.details}</ThemedText>
                </View>
              )) : (
                <ThemedText style={[styles.miniLogText, { padding: 8, fontStyle: 'italic' }]}>No recent activity found.</ThemedText>
              )}
            </ThemedView>
          </View>
        </ScrollView>
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f1f5f9' },
  header: { backgroundColor: '#0f172a', paddingTop: 50, paddingBottom: 15, paddingHorizontal: 20 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  content: { padding: 16, gap: 16 },
  miniBadge: { paddingVertical: 2, paddingHorizontal: 8, borderRadius: 4 },
  miniBadgeText: { color: '#fff', fontSize: 9, fontWeight: '900' },
  statusDot: { width: 4, height: 4, borderRadius: 2 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, elevation: 2 },
  responseCard: { backgroundColor: '#fff', borderRadius: 8, padding: 16, elevation: 1, minHeight: 140 },
  inputCard: { backgroundColor: '#fff', borderRadius: 12, padding: 8, flexDirection: 'row', alignItems: 'flex-end', gap: 8, borderWidth: 1, borderColor: '#e2e8f0' },
  modelSelectorContainer: { gap: 8 },
  sectionLabel: { fontSize: 10, fontWeight: '900', color: '#64748b', letterSpacing: 1 },
  chipScroll: { flexGrow: 0 },
  modelChip: { paddingHorizontal: 12, paddingVertical: 5, borderRadius: 12, backgroundColor: '#e2e8f0', marginRight: 8 },
  modelChipActive: { backgroundColor: '#0f172a' },
  modelChipText: { fontSize: 12, color: '#475569', fontWeight: '700' },
  modelChipTextActive: { color: '#fff' },
  textInput: { flex: 1, backgroundColor: 'transparent', padding: 8, fontSize: 16, color: '#0f172a', maxHeight: 120 },
  sendButton: { backgroundColor: '#ff6600', paddingHorizontal: 18, paddingVertical: 12, borderRadius: 10, justifyContent: 'center' },
  button: { backgroundColor: '#0f172a', padding: 14, borderRadius: 10, alignItems: 'center' },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 14 },
  responseText: { fontSize: 15, color: '#1e293b', lineHeight: 22 },
  minimalRecorder: { marginTop: 4, gap: 6 },
  miniTable: { backgroundColor: '#fff', borderRadius: 8, overflow: 'hidden', borderWidth: 1, borderColor: '#e2e8f0' },
  miniTableRow: { padding: 10, borderBottomWidth: 1, borderBottomColor: '#f1f5f9' },
  miniLogText: { fontSize: 12, color: '#64748b', fontWeight: '500' },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  description: { fontSize: 14, color: '#64748b' }
});
