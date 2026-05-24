// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app\(tabs)\index.tsx
import { SplashScreen, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, TextInput, TouchableOpacity, useColorScheme, View } from 'react-native';

// Import our clean abstracted bridge matrix
import { ReveilaBridge } from '../../ReveilaBridge';

// Hold layout rendering until the static runtime validates our environment state flags
SplashScreen.preventAutoHideAsync();

export default function HomeScreen() {
  const router = useRouter();
  const colorScheme = useColorScheme() ?? 'light';
  const isDark = colorScheme === 'dark';

  // --- ACTIVE CORE STATE ---
  const [isEngineReady, setIsEngineReady] = useState(false);
  const [promptText, setPromptText] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [activeMessages, setActiveMessages] = useState<any[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [isCloudMode, setIsCloudMode] = useState(false);
  const scrollViewRef = useRef<ScrollView>(null);

  const theme = {
    bg: isDark ? '#121212' : '#f1f5f9',
    card: isDark ? '#1e1e1e' : '#ffffff',
    text: isDark ? '#ffffff' : '#0f172a',
    subText: isDark ? '#94a3b8' : '#64748b',
    border: isDark ? '#334155' : '#e2e8f0',
    tabBar: isDark ? '#0f172a' : '#1e293b'
  };

  // --- LEAN STATIC STATUS POLL LOOP ---
  useEffect(() => {
    let isMounted = true;

    const checkStatus = async () => {
      try {
        // Direct, non-blocking evaluation of the class loader allocation
        const state = await ReveilaBridge.getEngineStatus();

        console.log(`[Static Bridge Sync] Engine Alive: ${state.initialized} | Status: ${state.status}`);

        if (state.initialized && isMounted) {
          setIsEngineReady(true);

          // Dismount the splash overlay safely
          SplashScreen.hideAsync();
          clearInterval(pollInterval);
        }
      } catch (error) {
        console.log("[Check Reveila Status Error]: " + error);
      }
    };

    // Poll the lightweight static method every 1000ms
    checkStatus();
    const pollInterval = setInterval(checkStatus, 1000);

    return () => {
      isMounted = false;
      clearInterval(pollInterval);
    };
  }, []);

  // --- AUTO SCROLL TO BOTTOM ---
  useEffect(() => {
    if (activeMessages.length > 0) {
      setTimeout(() => { scrollViewRef.current?.scrollToEnd({ animated: true }); }, 100);
    }
  }, [activeMessages]);

  // --- PIPELINE EXECUTION HUB ---
  const handleSendPrompt = async () => {
    if (!promptText.trim() || isProcessing) return;

    const currentPrompt = promptText;
    setPromptText('');
    setIsProcessing(true);

    setActiveMessages(prev => [...prev, { role: 'USER', content: currentPrompt }]);

    try {
      let prevSummary = null;
      if (!activeSessionId && activeMessages.length > 0 && activeMessages[0].role === 'SYSTEM') {
        prevSummary = activeMessages[0].content;
      }

      let result = await ReveilaBridge.invoke('AgenticFabric', 'askAgent', [currentPrompt, activeSessionId || "", prevSummary || ""]);
      if (result) {
        if (result.nameValuePairs) result = result.nameValuePairs;
        if (result.sessionId) setActiveSessionId(result.sessionId);
        setActiveMessages(prev => [...prev, { role: 'ASSISTANT', content: result.answer || JSON.stringify(result) }]);
      }
    } catch (e: any) {
      setActiveMessages(prev => [...prev, { role: 'SYSTEM', content: `Communication Failure: ${e.message}` }]);
    } finally {
      setIsProcessing(false);
    }
  };

  // Keep the UI container unrendered until the static liveness matrix clears
  if (!isEngineReady) {
    return null;
  }

  return (
    <View style={[styles.container, { backgroundColor: theme.bg }]}>
      <View style={styles.header}>
        <View style={styles.headerRow}>
          <Text style={styles.headerTitle}>Reveila Sovereign Matrix</Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            <View style={[styles.miniBadge, { backgroundColor: '#22c55e' }]}>
              <Text style={styles.miniBadgeText}>ONLINE</Text>
            </View>
            <TouchableOpacity onPress={() => setIsCloudMode(!isCloudMode)} style={[styles.miniBadge, { backgroundColor: isCloudMode ? '#3b82f6' : '#64748b' }]}>
              <Text style={styles.miniBadgeText}>{isCloudMode ? 'CLOUD' : 'LOCAL'}</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => router.push('/settings')}>
              <Text style={{ color: '#fff', fontSize: 11, fontWeight: '700' }}>SETTINGS</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>

      <ScrollView contentContainerStyle={styles.content} style={{ flex: 1 }}>
        {(activeMessages.length > 0 || isProcessing) && (
          <View style={[styles.responseCard, { backgroundColor: theme.card, borderColor: isCloudMode ? '#3b82f6' : '#22c55e', borderLeftWidth: 4 }]}>
            <ScrollView ref={scrollViewRef} style={{ maxHeight: 300 }} nestedScrollEnabled={true}>
              <View style={{ gap: 16 }}>
                {activeMessages.map((msg, i) => (
                  <View key={i} style={[styles.msgLine, { borderBottomColor: theme.border }]}>
                    <Text style={{ fontSize: 10, fontWeight: '900', color: msg.role === 'USER' ? '#3b82f6' : '#64748b', marginBottom: 4 }}>{msg.role}</Text>
                    <Text selectable={true} style={[styles.responseText, { color: theme.text }]}>{msg.content}</Text>
                  </View>
                ))}
                {isProcessing && <ActivityIndicator size="small" color="#ff6600" style={{ alignSelf: 'flex-start' }} />}
              </View>
            </ScrollView>
          </View>
        )}

        <View style={[styles.inputCard, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <TextInput
            style={[styles.textInput, { color: theme.text }]}
            multiline
            placeholder={isCloudMode ? "Query decentralized layers..." : "Talk to private storage agent..."}
            placeholderTextColor="#94a3b8"
            value={promptText}
            onChangeText={setPromptText}
            editable={!isProcessing}
            textAlignVertical="top"
          />
          <TouchableOpacity
            style={[styles.sendButton, { opacity: (promptText.trim()) ? 1 : 0.5 }]}
            disabled={isProcessing || !promptText.trim()}
            onPress={handleSendPrompt}
          >
            <Text style={styles.buttonText}>GO</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { backgroundColor: '#0f172a', paddingTop: 60, paddingBottom: 15, paddingHorizontal: 20 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  headerTitle: { color: '#fff', fontSize: 16, fontWeight: '800', letterSpacing: 0.5 },
  content: { padding: 16, gap: 16 },
  miniBadge: { paddingVertical: 3, paddingHorizontal: 8, borderRadius: 4 },
  miniBadgeText: { color: '#fff', fontSize: 9, fontWeight: '900' },
  responseCard: { borderRadius: 8, padding: 16, elevation: 1, minHeight: 140 },
  msgLine: { borderBottomWidth: 1, paddingBottom: 10, marginBottom: 4 },
  responseText: { fontSize: 14, lineHeight: 22, fontWeight: '400' },
  inputCard: { borderRadius: 12, padding: 8, flexDirection: 'row', alignItems: 'flex-end', gap: 8, borderWidth: 1 },
  textInput: { flex: 1, backgroundColor: 'transparent', padding: 8, fontSize: 16, maxHeight: 120 },
  sendButton: { backgroundColor: '#ff6600', paddingHorizontal: 18, paddingVertical: 12, borderRadius: 10, justifyContent: 'center' },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 14 }
});