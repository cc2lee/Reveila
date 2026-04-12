import { StyleSheet, TouchableOpacity, ScrollView, View, Switch, TextInput, Alert, Modal, FlatList } from 'react-native';
import { useState, useEffect } from 'react';
import { useRouter } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Linking from 'expo-linking';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { IconSymbol } from '@/components/ui/icon-symbol';
import { ReveilaHeader } from '@/components/ReveilaHeader';
import { ChangePasswordModal } from '@/components/ChangePasswordModal';
import ReveilaModule from '@/modules/reveila';

const LLM_PROVIDERS = [
  { name: 'OpenAI', defaultEndpoint: 'https://api.openai.com/v1' },
  { name: 'Anthropic', defaultEndpoint: 'https://api.anthropic.com' },
  { name: 'Google Gemini', defaultEndpoint: 'https://generativelanguage.googleapis.com' },
  { name: 'Ollama (Local)', defaultEndpoint: 'http://localhost:11434' },
  { name: 'Custom', defaultEndpoint: '' }
];

export default function SettingsScreen() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState('General');
  const [quantization, setQuantization] = useState('Q4_K_M');
  const [isHighSecurity, setIsHighSecurity] = useState(true);
  const [isBiometricEnabled, setIsBiometricEnabled] = useState(false);
  const [apiKey, setApiKey] = useState('');
  
  const [providersList, setProvidersList] = useState(LLM_PROVIDERS);
  const [provider, setProvider] = useState(LLM_PROVIDERS[0].name);
  const [endpoint, setEndpoint] = useState(LLM_PROVIDERS[0].defaultEndpoint);
  const [isProviderModalVisible, setProviderModalVisible] = useState(false);
  const [isCustomProvider, setIsCustomProvider] = useState(false);
  const [isChangePasswordVisible, setIsChangePasswordVisible] = useState(false);
  
  const [tasks, setTasks] = useState<any[]>([]);
  const [selectedTask, setSelectedTask] = useState<any>(null);

  const tabs = ['General', 'Security', 'Tasks', 'Advanced'];

  useEffect(() => {
    if (activeTab === 'Tasks') {
      fetchTasks();
    }
  }, [activeTab]);

  useEffect(() => {
    // Load preference on startup
    AsyncStorage.getItem('use_biometrics').then(val => {
      setIsBiometricEnabled(val === 'true');
    });

    // Load official LLM config from the engine
    ReveilaModule.invoke('ConfigurationManager', 'getSettings', ['llm.json']).then((res: string) => {
      if (res) {
        try {
          const config = JSON.parse(res);
          if (config.onboarded_providers && Array.isArray(config.onboarded_providers)) {
            setProvidersList(config.onboarded_providers);
          }
          if (config.provider) {
            setProvider(config.provider);
            setIsCustomProvider(!LLM_PROVIDERS.find(p => p.name === config.provider));
          }
          if (config.endpoint) setEndpoint(config.endpoint);
          if (config.quantization) setQuantization(config.quantization);
        } catch (e) {}
      }
    }).catch(() => {
        // Fallback to AsyncStorage if engine call fails
        AsyncStorage.getItem('custom_providers').then(val => {
          if (val) {
            try {
              const parsed = JSON.parse(val);
              if (Array.isArray(parsed)) {
                setProvidersList(prev => [...prev, ...parsed]);
              }
            } catch (e) {}
          }
        });
    });
  }, []);

  const toggleBiometrics = async (value: boolean) => {
    setIsBiometricEnabled(value);
    await AsyncStorage.setItem('use_biometrics', value ? 'true' : 'false');
  };

  const fetchTasks = async () => {
    try {
      const result = await fetch('/api/tasks'); // Assuming local proxy or direct call
      const data = await result.json();
      setTasks(data);
    } catch (e) {}
  };

  const handleKill = async () => {
    Alert.alert(
      'Confirm Kill',
      'EMERGENCY: Terminate all running AI processes and revoke access?',
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'OK', onPress: () => {
            // Logic to stop service
            alert('Kill Switch Activated. All processes stopped.');
          }
        }
      ]
    );
  };

  const handleSaveLLM = async () => {
    try {
      // Check if it's a new custom provider to add to the onboarded list
      let updatedList = [...providersList];
      const existing = providersList.find(p => p.name === provider);
      if (!existing && provider && provider.trim() !== '' && provider !== 'Custom') {
        updatedList.push({ name: provider, defaultEndpoint: endpoint });
        setProvidersList(updatedList);
        
        // Also keep local sync for redundancy
        const customOnly = updatedList.filter(p => !LLM_PROVIDERS.find(orig => orig.name === p.name));
        await AsyncStorage.setItem('custom_providers', JSON.stringify(customOnly));
      }

      const config = {
        provider,
        endpoint,
        apiKey,
        quantization,
        onboarded_providers: updatedList
      };

      await ReveilaModule.invoke('ConfigurationManager', 'saveSettings', ['llm.json', JSON.stringify(config)]);
      Alert.alert('Success', 'LLM Configuration saved successfully.');
    } catch (error: any) {
      Alert.alert('Error', 'Failed to save configuration: ' + error.message);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ReveilaHeader subtitle="Settings">
        <TouchableOpacity onPress={() => router.replace('/')}>
          <IconSymbol name="xmark" color="#fff" size={24} />
        </TouchableOpacity>
      </ReveilaHeader>

      <View style={styles.tabBar}>
        {tabs.map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tabItem, activeTab === tab && styles.activeTabItem]}
            onPress={() => setActiveTab(tab)}
          >
            <ThemedText style={[styles.tabText, activeTab === tab && styles.activeTabText]}>{tab}</ThemedText>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {activeTab === 'General' && (
          <View style={styles.section}>
            <ThemedText type="subtitle">LLM Model Setup</ThemedText>
            
            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Provider</ThemedText>
              {isCustomProvider ? (
                <View style={[styles.monoInput, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 8, padding: 0 }]}>
                   <TextInput 
                     style={{ flex: 1, color: '#000', padding: 10, fontFamily: 'monospace', fontSize: 12 }}
                     value={provider === 'Custom' ? '' : provider}
                     placeholder="Enter custom provider name"
                     onChangeText={setProvider}
                   />
                   <TouchableOpacity style={{ padding: 10 }} onPress={() => setProviderModalVisible(true)}>
                     <IconSymbol name="chevron.down" size={16} color="#64748b" />
                   </TouchableOpacity>
                </View>
              ) : (
                <TouchableOpacity 
                  style={[styles.monoInput, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginTop: 8 }]} 
                  onPress={() => setProviderModalVisible(true)}
                >
                  <ThemedText style={{ color: '#000' }}>{provider}</ThemedText>
                  <IconSymbol name="chevron.down" size={16} color="#64748b" />
                </TouchableOpacity>
              )}

              <ThemedText type="defaultSemiBold" style={{ marginTop: 12 }}>End Point</ThemedText>
              <TextInput
                style={[styles.monoInput, { marginTop: 8 }]}
                placeholder="https://api.example.com"
                placeholderTextColor="#94a3b8"
                value={endpoint}
                onChangeText={setEndpoint}
              />

              <ThemedText type="defaultSemiBold" style={{ marginTop: 12 }}>API Key</ThemedText>
              <TextInput
                style={[styles.monoInput, { marginTop: 8 }]}
                placeholder="sk-..."
                placeholderTextColor="#94a3b8"
                value={apiKey}
                onChangeText={setApiKey}
                secureTextEntry={true}
              />
              
              <TouchableOpacity style={[styles.button, { marginTop: 16 }]} onPress={handleSaveLLM}>
                <ThemedText style={styles.buttonText}>Save Configuration</ThemedText>
              </TouchableOpacity>
            </ThemedView>

            <Modal visible={isProviderModalVisible} transparent={true} animationType="fade">
              <View style={styles.modalOverlay}>
                <View style={styles.modalContent}>
                  <ThemedText type="subtitle" style={{ marginBottom: 16 }}>Select Provider</ThemedText>
                  <FlatList
                    data={providersList}
                    keyExtractor={(item) => item.name}
                    renderItem={({ item }) => (
                      <TouchableOpacity 
                        style={styles.providerItem}
                        onPress={() => {
                          if (item.name === 'Custom') {
                            setIsCustomProvider(true);
                            setProvider('Custom');
                            setEndpoint('');
                          } else {
                            setIsCustomProvider(false);
                            setProvider(item.name);
                            setEndpoint(item.defaultEndpoint);
                          }
                          setProviderModalVisible(false);
                        }}
                      >
                        <ThemedText>{item.name}</ThemedText>
                      </TouchableOpacity>
                    )}
                  />
                  <TouchableOpacity style={styles.outlineButton} onPress={() => setProviderModalVisible(false)}>
                    <ThemedText style={styles.outlineButtonText}>Cancel</ThemedText>
                  </TouchableOpacity>
                </View>
              </View>
            </Modal>

            <Modal visible={isChangePasswordVisible} transparent={true} animationType="slide">
              <View style={styles.modalOverlay}>
                <ChangePasswordModal 
                  onCancel={() => setIsChangePasswordVisible(false)} 
                  onSuccess={() => setIsChangePasswordVisible(false)}
                />
              </View>
            </Modal>

            <ThemedText type="subtitle" style={{ marginTop: 16 }}>Local Model: Gemma-3-1b</ThemedText>
            <ThemedView style={styles.card}>
              <ThemedText style={styles.description}>Choose your preference, performance vs accuracy, for the local private model.</ThemedText>
              
              <View style={styles.radioGroup}>
                <TouchableOpacity 
                  style={[styles.radioButton, quantization === 'Q4_K_M' && styles.radioActive]}
                  onPress={() => setQuantization('Q4_K_M')}
                >
                  <ThemedText style={[styles.radioText, quantization === 'Q4_K_M' && styles.radioTextActive]}>Q4_K_M (Fast)</ThemedText>
                </TouchableOpacity>
                <TouchableOpacity 
                  style={[styles.radioButton, quantization === 'F16' && styles.radioActive]}
                  onPress={() => setQuantization('F16')}
                >
                  <ThemedText style={[styles.radioText, quantization === 'F16' && styles.radioTextActive]}>F16 (Accurate)</ThemedText>
                </TouchableOpacity>
              </View>
            </ThemedView>
          </View>
        )}

        {activeTab === 'Security' && (
          <View style={styles.section}>
            <ThemedText type="subtitle">Safety & Privacy</ThemedText>
            
            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Master Password</ThemedText>
              <ThemedText style={styles.description}>Update your local master password. This is only stored on this device.</ThemedText>
              <TouchableOpacity style={[styles.button, {marginTop: 16}]} onPress={() => setIsChangePasswordVisible(true)}>
                <ThemedText style={styles.buttonText}>CHANGE MASTER PASSWORD</ThemedText>
              </TouchableOpacity>
            </ThemedView>

            <ThemedView style={[styles.card, {borderColor: '#ef4444', borderWidth: 1}]}>
              <ThemedText type="defaultSemiBold" style={{color: '#ef4444'}}>Emergency Kill Switch</ThemedText>
              <ThemedText style={styles.description}>Immediately revoke all JIT tokens and terminate the AI system process.</ThemedText>
              <TouchableOpacity style={[styles.button, {backgroundColor: '#ef4444', marginTop: 16}]} onPress={handleKill}>
                <ThemedText style={styles.buttonText}>KILL ALL PROCESSES</ThemedText>
              </TouchableOpacity>
            </ThemedView>

            <ThemedView style={styles.card}>
              <View style={styles.row}>
                <View style={{flex: 1}}>
                  <ThemedText type="defaultSemiBold">Biometric Authentication</ThemedText>
                  <ThemedText style={styles.description}>Use fingerprint or face ID to unlock the app.</ThemedText>
                </View>
                <Switch 
                  value={isBiometricEnabled} 
                  onValueChange={toggleBiometrics}
                  trackColor={{ false: "#cbd5e1", true: "#00E5FF" }}
                  thumbColor={isBiometricEnabled ? "#fff" : "#f4f3f4"}
                />
              </View>
            </ThemedView>
          </View>
        )}

        {activeTab === 'Tasks' && (
          <View style={styles.section}>
            <View style={[styles.row, {marginBottom: 12}]}>
              <ThemedText type="subtitle">Recurring Tasks</ThemedText>
              <TouchableOpacity style={styles.addBtnSmall} onPress={() => {
                setSelectedTask({
                  filename: 'new-task.json',
                  content: JSON.stringify({
                    taskId: 'new-task-id',
                    intent: 'general.task',
                    prompt: 'Enter instructions here...',
                    frequency: 'daily'
                  }, null, 2)
                });
              }}>
                <ThemedText style={{color: '#fff', fontSize: 12, fontWeight: '700'}}>+ New Task</ThemedText>
              </TouchableOpacity>
            </View>

            {tasks.length === 0 && !selectedTask ? (
              <ThemedText style={styles.description}>No recurring tasks found.</ThemedText>
            ) : (
              tasks.map(task => (
                <ThemedView key={task.filename} style={styles.taskCard}>
                  <ThemedText type="defaultSemiBold">{task.filename}</ThemedText>
                  <TouchableOpacity onPress={() => setSelectedTask(task)}>
                    <ThemedText type="link">Edit</ThemedText>
                  </TouchableOpacity>
                </ThemedView>
              ))
            )}
            
            {selectedTask && (
              <ThemedView style={styles.editorCard}>
                <TextInput 
                  style={styles.monoInput} 
                  multiline 
                  value={selectedTask.content}
                  onChangeText={(text) => setSelectedTask({...selectedTask, content: text})}
                />
                <View style={styles.row}>
                  <TouchableOpacity style={styles.button} onPress={() => setSelectedTask(null)}><ThemedText style={styles.buttonText}>Save</ThemedText></TouchableOpacity>
                  <TouchableOpacity style={styles.outlineButton} onPress={() => setSelectedTask(null)}><ThemedText style={styles.outlineButtonText}>Cancel</ThemedText></TouchableOpacity>
                </View>
              </ThemedView>
            )}
          </View>
        )}

        {activeTab === 'Advanced' && (
          <View style={styles.section}>
            <ThemedText type="subtitle">Advanced</ThemedText>
            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">System Path</ThemedText>
              <ThemedText style={styles.monoText}>/data/user/0/com.reveila.android/files</ThemedText>
            </ThemedView>

            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Knowledge Vault Sync</ThemedText>
              <ThemedText style={styles.description}>Manually trigger a delta-scan of your authorized Knowledge Vault to pick up new or modified files.</ThemedText>
              <TouchableOpacity style={[styles.button, {marginTop: 16}]} onPress={async () => {
                try {
                  await ReveilaModule.triggerVaultScan();
                  alert('Vault scan initiated in the background.');
                } catch (e: any) {
                  alert('Scan Failed: ' + e.message);
                }
              }}>
                <ThemedText style={styles.buttonText}>FORCE RE-SCAN NOW</ThemedText>
              </TouchableOpacity>
            </ThemedView>

            <ThemedView style={[styles.card, {borderColor: '#ef4444', borderWidth: 1, marginTop: 20}]}>
              <ThemedText type="defaultSemiBold" style={{color: '#ef4444'}}>Reset Application</ThemedText>
              <ThemedText style={styles.description}>This will delete all application data. You will need to set up the application from scratch.</ThemedText>
              <TouchableOpacity style={[styles.button, {backgroundColor: '#ef4444', marginTop: 16}]} onPress={() => {
                Alert.alert(
                  'Confirm Reset',
                  'Are you absolutely sure you want to reset the application? This cannot be undone.',
                  [
                    { text: 'Cancel', style: 'cancel' },
                    { text: 'Reset', style: 'destructive', onPress: () => {
                        ReveilaModule.resetApplication().then(() => alert('Application Reset Successfully. Please restart the app.'));
                      }
                    }
                  ]
                );
              }}>
                <ThemedText style={styles.buttonText}>RESET EVERYTHING</ThemedText>
              </TouchableOpacity>
            </ThemedView>
          </View>
        )}
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f1f5f9' },
  header: { backgroundColor: '#0f172a', paddingTop: 50, paddingBottom: 15, paddingHorizontal: 20 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  tabBar: { flexDirection: 'row', backgroundColor: '#1e293b' },
  tabItem: { paddingVertical: 12, paddingHorizontal: 16, borderBottomWidth: 2, borderBottomColor: 'transparent' },
  activeTabItem: { borderBottomColor: '#00E5FF' },
  tabText: { color: '#94a3b8', fontSize: 13, fontWeight: '700' },
  activeTabText: { color: '#00E5FF' },
  content: { padding: 16 },
  section: { gap: 12, marginBottom: 24 },
  card: { backgroundColor: '#fff', borderRadius: 12, padding: 16, elevation: 1 },
  description: { fontSize: 13, color: '#64748b', marginTop: 4 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 },
  radioGroup: { flexDirection: 'row', marginTop: 12, gap: 10 },
  radioButton: { flex: 1, padding: 10, borderRadius: 8, borderWidth: 1, borderColor: '#e2e8f0', alignItems: 'center' },
  radioActive: { borderColor: '#00E5FF', backgroundColor: '#ecfeff' },
  radioText: { fontSize: 11, fontWeight: '700', color: '#64748b' },
  radioTextActive: { color: '#0891b2' },
  button: { padding: 12, borderRadius: 8, alignItems: 'center', backgroundColor: '#0f172a' },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 13 },
  addBtnSmall: { backgroundColor: '#0f172a', paddingHorizontal: 12, paddingVertical: 6, borderRadius: 6 },
  outlineButton: { padding: 12, borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#cbd5e1' },
  outlineButtonText: { color: '#475569', fontWeight: '700', fontSize: 13 },
  cautionBox: { marginTop: 12, padding: 10, backgroundColor: '#fff7ed', borderRadius: 6, borderWidth: 1, borderColor: '#ffedd5' },
  cautionText: { color: '#9a3412', fontSize: 12, fontWeight: '600' },
  monoText: { fontFamily: 'monospace', fontSize: 11, color: '#64748b', marginTop: 8 },
  taskCard: { flexDirection: 'row', justifyContent: 'space-between', padding: 12, backgroundColor: '#fff', borderRadius: 8 },
  editorCard: { marginTop: 12, gap: 10 },
  monoInput: { fontFamily: 'monospace', fontSize: 12, backgroundColor: '#fff', padding: 10, borderRadius: 8, borderWidth: 1, borderColor: '#e2e8f0', minHeight: 40 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', alignItems: 'center', padding: 20 },
  modalContent: { backgroundColor: '#fff', width: '100%', borderRadius: 12, padding: 20, maxHeight: '80%' },
  providerItem: { paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#f1f5f9' }
});
