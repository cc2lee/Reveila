// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app\(tabs)\index.tsx
import AsyncStorage from '@react-native-async-storage/async-storage';
import { SplashScreen, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  useColorScheme,
  View
} from 'react-native';

import { ReveilaBridge } from '../../ReveilaBridge';
import SetupWizard from '../../components/SetupWizard';

// Hold layout rendering until the static runtime validates our environment state flags
SplashScreen.preventAutoHideAsync();

export default function HomeScreen() {
  const router = useRouter();
  const colorScheme = useColorScheme() ?? 'dark';
  const isDark = colorScheme === 'dark';

  // --- ACTIVE CORE STATE ---
  const [isEngineReady, setIsEngineReady] = useState(false);
  const [showSetupWizard, setShowSetupWizard] = useState(false);
  const [promptText, setPromptText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [activeMessages, setActiveMessages] = useState<any[]>([
    {
      role: 'ASSISTANT',
      content: 'Welcome to Reveila Sovereign AI. Your local document vault and on-device agentic fabric are active. Ask a question or search your documents.'
    }
  ]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [isCloudMode, setIsCloudMode] = useState(false);
  const [activeLlmName, setActiveLlmName] = useState('On-Device Model');
  const [vaultStats, setVaultStats] = useState({ documentCount: 0, chunkCount: 0, folder: 'ReveilaVault' });
  const scrollViewRef = useRef<ScrollView>(null);

  const theme = {
    bg: isDark ? '#090d16' : '#f1f5f9',
    card: isDark ? '#131b2e' : '#ffffff',
    text: isDark ? '#f8fafc' : '#0f172a',
    subText: isDark ? '#94a3b8' : '#64748b',
    border: isDark ? '#1e293b' : '#e2e8f0',
    primary: '#ff6600',
    success: '#10b981',
    accent: '#3b82f6',
  };

  // --- ENGINE STATUS POLL LOOP ---
  useEffect(() => {
    let isMounted = true;

    const checkStatus = async () => {
      try {
        const state = await ReveilaBridge.getEngineStatus();
        if (state.initialized && isMounted) {
          setIsEngineReady(true);
          SplashScreen.hideAsync();
          clearInterval(pollInterval);
          refreshVaultStats();
        }
      } catch (error) {
        console.log('[Check Reveila Status Error]:', error);
      }
    };

    checkStatus();
    const pollInterval = setInterval(checkStatus, 1000);

    return () => {
      isMounted = false;
      clearInterval(pollInterval);
    };
  }, []);

  // --- FIRST-LAUNCH SETUP WIZARD DETECTION ---
  useEffect(() => {
    AsyncStorage.getItem('reveila_setup_completed').then((completed) => {
      if (completed !== 'true') {
        setShowSetupWizard(true);
      }
    });

    AsyncStorage.getItem('selected_llm_mode').then((mode) => {
      if (mode && mode !== 'local') {
        setIsCloudMode(true);
        setActiveLlmName(mode === 'gemini' ? 'Google Gemini' : 'OpenAI');
      } else {
        setIsCloudMode(false);
        setActiveLlmName('On-Device Model');
      }
    });
  }, []);

  // Auto-scroll chat to latest message when messages update
  useEffect(() => {
    if (activeMessages.length > 0) {
      const timer = setTimeout(() => {
        scrollViewRef.current?.scrollToEnd({ animated: true });
      }, 100);
      return () => clearTimeout(timer);
    }
  }, [activeMessages.length]);

  const refreshVaultStats = async () => {
    try {
      const stats = await ReveilaBridge.getVaultStats();
      if (stats) setVaultStats(stats);
    } catch (e) {}
  };

  // --- AUTO SCROLL TO BOTTOM ---
  useEffect(() => {
    if (activeMessages.length > 0) {
      setTimeout(() => { scrollViewRef.current?.scrollToEnd({ animated: true }); }, 100);
    }
  }, [activeMessages]);

  const toggleLlmMode = async () => {
    const nextCloud = !isCloudMode;
    setIsCloudMode(nextCloud);
    const newProvider = nextCloud ? 'Google Gemini' : 'On-Device Model';
    setActiveLlmName(newProvider);
    await AsyncStorage.setItem('selected_llm_mode', nextCloud ? 'gemini' : 'local');
    await AsyncStorage.setItem('selected_llm_provider', newProvider);

    try {
      await ReveilaBridge.invoke('UiController', 'setSettings', ['llm.json', JSON.stringify({
        'ai.worker.llm': newProvider,
        'ai.governance.llm': newProvider
      })]);
    } catch (e) {}
  };

  // --- PIPELINE EXECUTION HUB ---
  const handleSendPrompt = async () => {
    if (!promptText.trim() || isProcessing) return;

    const currentPrompt = promptText.trim();
    setPromptText('');
    setIsProcessing(true);

    setActiveMessages(prev => [...prev, { role: 'USER', content: currentPrompt }]);

    try {
      let prevSummary = null;
      if (!activeSessionId && activeMessages.length > 0 && activeMessages[0].role === 'SYSTEM') {
        prevSummary = activeMessages[0].content;
      }

      let result = await ReveilaBridge.invoke('AgenticFabric', 'askAgent', [currentPrompt, activeSessionId || '', prevSummary || '']);
      if (result) {
        if (result.nameValuePairs) result = result.nameValuePairs;
        if (result.sessionId) setActiveSessionId(result.sessionId);
        setActiveMessages(prev => [...prev, { role: 'ASSISTANT', content: result.answer || JSON.stringify(result) }]);
      }
    } catch (e: any) {
      setActiveMessages(prev => [...prev, { role: 'SYSTEM', content: `Communication Failure: ${e.message}` }]);
    } finally {
      setIsProcessing(false);
      refreshVaultStats();
    }
  };

  // Display loading status while the engine boots up
  if (!isEngineReady) {
    return (
      <View style={[styles.container, { backgroundColor: theme.bg, justifyContent: 'center', alignItems: 'center', padding: 24 }]}>
        <ActivityIndicator size="large" color={theme.primary} />
        <Text style={{ color: theme.text, fontSize: 18, fontWeight: '800', marginTop: 16 }}>
          Connecting to Reveila Sovereign Engine...
        </Text>
        <Text style={{ color: theme.subText, fontSize: 13, marginTop: 8, textAlign: 'center' }}>
          Initializing Android Foreground Service, Sovereign Vector Database, and Agentic Fabric
        </Text>
      </View>
    );
  }

  const vaultDisplayName = vaultStats.folder
    ? (vaultStats.folder.split('/').filter(Boolean).pop() || vaultStats.folder)
    : 'ReveilaVault';

  return (
    <View style={[styles.container, { backgroundColor: theme.bg }]}>
      {/* Top Header */}
      <View style={styles.header}>
        <View style={styles.headerRow}>
          <View>
            <Text style={styles.brandSubtitle}>SOVEREIGN FABRIC</Text>
            <Text style={styles.headerTitle}>Reveila Intelligence</Text>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <View style={[styles.miniBadge, { backgroundColor: theme.success }]}>
              <Text style={styles.miniBadgeText}>ONLINE</Text>
            </View>
            <TouchableOpacity onPress={toggleLlmMode} style={[styles.miniBadge, { backgroundColor: isCloudMode ? theme.accent : theme.primary }]}>
              <Text style={styles.miniBadgeText}>{isCloudMode ? 'CLOUD' : 'LOCAL'}</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => router.push('/settings')} style={styles.settingsBtn}>
              <Text style={{ color: '#fff', fontSize: 11, fontWeight: '800' }}>SETTINGS</Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* Vault & Model Status Banner - Non-overlapping layout */}
        <View style={styles.statusBar}>
          <TouchableOpacity
            style={styles.statusChip}
            onPress={() => router.push('/settings')}
            activeOpacity={0.7}
          >
            <Text style={{ color: '#94a3b8', fontSize: 11, flexShrink: 0 }}>📁 Vault: </Text>
            <Text
              style={{ color: '#f8fafc', fontSize: 11, fontWeight: '800', flexShrink: 1 }}
              numberOfLines={1}
              ellipsizeMode="middle"
            >
              {vaultDisplayName}
            </Text>
            <View style={styles.statsPill}>
              <Text style={{ color: theme.success, fontSize: 10, fontWeight: '900' }}>
                {vaultStats.chunkCount} vectors
              </Text>
            </View>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.wizardLaunchBtn}
            onPress={() => setShowSetupWizard(true)}
            activeOpacity={0.7}
          >
            <Text style={{ color: theme.primary, fontSize: 11, fontWeight: '800' }}>⚡ Wizard</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Main Chat Stream & Anchored Input Area */}
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView
          ref={scrollViewRef}
          style={styles.chatScroll}
          contentContainerStyle={styles.chatContent}
          showsVerticalScrollIndicator={true}
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          nestedScrollEnabled={true}
        >
          {activeMessages.map((msg, i) => {
            const isUser = msg.role === 'USER';
            const isSystem = msg.role === 'SYSTEM';
            return (
              <View
                key={i}
                style={[
                  styles.messageBubble,
                  {
                    backgroundColor: isUser
                      ? (isDark ? '#1e293b' : '#e2e8f0')
                      : theme.card,
                    borderColor: isUser
                      ? (isDark ? '#334155' : '#cbd5e1')
                      : (isCloudMode ? theme.accent : theme.primary),
                    borderLeftWidth: isUser ? 1 : 4,
                    alignSelf: isUser ? 'flex-end' : 'flex-start',
                    maxWidth: isUser ? '88%' : '95%',
                  }
                ]}
              >
                <View style={styles.bubbleHeader}>
                  <Text
                    style={{
                      fontSize: 10,
                      fontWeight: '900',
                      letterSpacing: 0.5,
                      color: isUser
                        ? theme.accent
                        : isSystem
                        ? '#ef4444'
                        : theme.primary,
                    }}
                  >
                    {isUser
                      ? 'YOU'
                      : isSystem
                      ? 'SYSTEM'
                      : `REVEILA AGENT (${activeLlmName})`}
                  </Text>
                </View>
                <Text style={[styles.responseText, { color: theme.text }]}>
                  {msg.content}
                </Text>
              </View>
            );
          })}

          {isProcessing && (
            <View
              style={[
                styles.messageBubble,
                {
                  backgroundColor: theme.card,
                  borderColor: theme.border,
                  borderLeftWidth: 4,
                  borderLeftColor: isCloudMode ? theme.accent : theme.primary,
                  alignSelf: 'flex-start',
                  maxWidth: '92%',
                }
              ]}
            >
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 4 }}>
                <ActivityIndicator size="small" color={isCloudMode ? theme.accent : theme.primary} />
                <Text style={{ color: theme.subText, fontSize: 12 }}>
                  {isCloudMode
                    ? 'Querying cloud model with guardrails...'
                    : 'Executing on-device reasoning and vector search...'}
                </Text>
              </View>
            </View>
          )}
        </ScrollView>

        {/* Anchored Prompt Input Box */}
        <View style={[styles.bottomBar, { backgroundColor: isDark ? '#090d16' : '#ffffff', borderTopColor: theme.border }]}>
          <View style={[styles.inputCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <TextInput
              style={[styles.textInput, { color: theme.text }]}
              multiline
              placeholder={
                isCloudMode
                  ? 'Ask cloud agent (Google Gemini / OpenAI)...'
                  : 'Ask sovereign on-device agent (indexed vault)...'
              }
              placeholderTextColor="#64748b"
              value={promptText}
              onChangeText={setPromptText}
              editable={!isProcessing}
              textAlignVertical="top"
            />
            <TouchableOpacity
              style={[
                styles.sendButton,
                {
                  backgroundColor: isCloudMode ? theme.accent : theme.primary,
                  opacity: promptText.trim() && !isProcessing ? 1 : 0.4,
                }
              ]}
              disabled={isProcessing || !promptText.trim()}
              onPress={handleSendPrompt}
            >
              <Text style={styles.buttonText}>SEND</Text>
            </TouchableOpacity>
          </View>
        </View>
      </KeyboardAvoidingView>

      {/* Setup Wizard Modal for First Launch */}
      <SetupWizard
        visible={showSetupWizard}
        onComplete={() => {
          setShowSetupWizard(false);
          refreshVaultStats();
          AsyncStorage.getItem('selected_llm_mode').then((mode) => {
            setIsCloudMode(mode === 'gemini' || mode === 'openai');
            setActiveLlmName(mode === 'gemini' ? 'Google Gemini' : (mode === 'openai' ? 'OpenAI' : 'On-Device Model'));
          });
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    backgroundColor: '#090d16',
    paddingTop: 54,
    paddingBottom: 14,
    paddingHorizontal: 18,
    borderBottomWidth: 1,
    borderBottomColor: '#1e293b',
  },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  brandSubtitle: { color: '#ff6600', fontSize: 10, fontWeight: '900', letterSpacing: 1.2 },
  headerTitle: { color: '#fff', fontSize: 18, fontWeight: '900' },
  miniBadge: { paddingVertical: 4, paddingHorizontal: 8, borderRadius: 6 },
  miniBadgeText: { color: '#fff', fontSize: 10, fontWeight: '900' },
  settingsBtn: {
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#334155',
  },
  statusBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 12,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#1e293b',
    gap: 8,
  },
  statusChip: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    marginRight: 6,
    overflow: 'hidden',
  },
  statsPill: {
    backgroundColor: 'rgba(16, 185, 129, 0.15)',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginLeft: 6,
    flexShrink: 0,
  },
  wizardLaunchBtn: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(255, 102, 0, 0.4)',
    backgroundColor: 'rgba(255, 102, 0, 0.1)',
    flexShrink: 0,
  },
  chatScroll: {
    flex: 1,
  },
  chatContent: {
    padding: 16,
    paddingBottom: 28,
    gap: 12,
  },
  messageBubble: {
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    elevation: 2,
  },
  bubbleHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  responseText: {
    fontSize: 14,
    lineHeight: 22,
    fontWeight: '400',
  },
  bottomBar: {
    paddingHorizontal: 16,
    paddingTop: 8,
    paddingBottom: 16,
    borderTopWidth: 1,
  },
  inputCard: {
    borderRadius: 14,
    padding: 8,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
    borderWidth: 1,
  },
  textInput: {
    flex: 1,
    backgroundColor: 'transparent',
    padding: 8,
    fontSize: 15,
    maxHeight: 120,
  },
  sendButton: {
    paddingHorizontal: 18,
    paddingVertical: 12,
    borderRadius: 10,
    justifyContent: 'center',
  },
  buttonText: {
    color: '#fff',
    fontWeight: '900',
    fontSize: 13,
  },
});