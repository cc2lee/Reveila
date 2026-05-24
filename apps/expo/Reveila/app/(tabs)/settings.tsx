// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app\(tabs)\settings.tsx
import { StyleSheet, TouchableOpacity, ScrollView, View, Switch, TextInput, Alert, Modal, FlatList, Text, useColorScheme } from 'react-native';
import { useState, useEffect } from 'react';
import { useRouter } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';

// Injected Universal Client via your protocol-agnostic target bridge core
class ReveilaClient {
  private baseURL: string;
  private transport: any;

  constructor(config: any = {}) {
    this.baseURL = config.baseURL || '';
    this.transport = config.transport || null;
  }

  async invoke(componentName: string, methodName: string, args: any[] = []) {
    if (this.transport) {
      return this.transport(componentName, methodName, args);
    }
    const response = await fetch(`${this.baseURL}/api/components/${componentName}/invoke`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ methodName, args }),
    });
    return response.json();
  }
}

// Instantiate universal agnostic instance mapped directly onto your architecture bridge
const client = new ReveilaClient({ baseURL: 'http://localhost:8080' });

function transformProviderConfig(providers: any[]): any[] {
  return providers.map(p => ({
    name: p.name,
    type: p.type,
    endpoint: p.endpoint,
    model: p.model,
    model_options: p['model.options'],
    temperature: p.temperature,
    apiKey: p['api.key'] || p.apiKey || ''
  }));
}

export default function SettingsScreen() {
  const router = useRouter();
  const colorScheme = useColorScheme() ?? 'light';
  const isDark = colorScheme === 'dark';

  const [activeTab, setActiveTab] = useState('General');
  const [isBiometricEnabled, setIsBiometricEnabled] = useState(false);
  const [providersError, setProvidersError] = useState<string | null>(null);
  const [isLoadingProviders, setIsLoadingProviders] = useState(true);
  const [providersList, setProvidersList] = useState<any[]>([]);

  // Selection States
  const [workerProvider, setWorkerProvider] = useState('');
  const [governanceProvider, setGovernanceProvider] = useState('Disable');

  // Modals
  const [isWorkerModalVisible, setWorkerModalVisible] = useState(false);
  const [isGovModalVisible, setGovModalVisible] = useState(false);
  
  // Edit Provider Modal State
  const [isEditModalVisible, setIsEditModalVisible] = useState(false);
  const [editData, setEditData] = useState<any>({});
  
  const tabs = ['General', 'Security', 'Advanced'];

  const [isRunning, setIsRunning] = useState(false);
  const [isSuspended, setIsSuspended] = useState(false);

  // Dynamic Theme Adaptations
  const theme = {
    bg: isDark ? '#121212' : '#f1f5f9',
    card: isDark ? '#1e1e1e' : '#ffffff',
    text: isDark ? '#ffffff' : '#0f172a',
    subText: isDark ? '#94a3b8' : '#64748b',
    border: isDark ? '#334155' : '#e2e8f0',
    tabBar: isDark ? '#0f172a' : '#1e293b'
  };

  useEffect(() => {
    const checkRunning = async () => {
      try {
        const running = await client.invoke('OrchestrationService', 'isRunning', []);
        setIsRunning(running);
        if (running) {
          const suspended = await client.invoke('OrchestrationService', 'isSuspended', []);
          setIsSuspended(suspended);
        }
      } catch (e) {
        // Fallback flags for local container runtime verification
        setIsRunning(true);
      }
    };
    checkRunning();
    const interval = setInterval(checkRunning, 5000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    AsyncStorage.getItem('use_biometrics').then(val => {
      setIsBiometricEnabled(val === 'true');
    });
  }, []);

  useEffect(() => {
    if (!isRunning) return;

    client.invoke('ConfigurationManager', 'getSettings', ['llm.json']).then((res: any) => {
      if (res) {
        try {
          const config = typeof res === 'string' ? JSON.parse(res) : res;
          const onboarded = config['onboarded.providers'] || config.onboarded_providers || [];
          const transformedProviders = transformProviderConfig(onboarded);
          setProvidersList([...transformedProviders, { name: 'Custom', endpoint: '', model: '', apiKey: '' }]);
          setProvidersError(null);

          const legacyWorkerMap: Record<string, string> = { 'OpenAiProvider': 'OpenAI', 'AnthropicProvider': 'Anthropic', 'GeminiProvider': 'Google Gemini', 'OnDeviceProvider': 'On-Device Model' };
          const legacyGovMap: Record<string, string> = { ...legacyWorkerMap, '': 'Disable' };
            
          let wProvider = config['ai.worker.llm'] || '';
          if (legacyWorkerMap[wProvider]) wProvider = legacyWorkerMap[wProvider];
            
          let gProvider = config['ai.governance.llm'] || 'Disable';
          if (legacyGovMap[gProvider]) gProvider = legacyGovMap[gProvider];

          setWorkerProvider(wProvider || (transformedProviders.length > 0 ? transformedProviders[0].name : ''));
          setGovernanceProvider(gProvider);
        } catch (e) {
          setProvidersError('Failed to parse LLM providers configuration');
          setProvidersList([]);
          setWorkerProvider('');
        }
      } else {
        setProvidersError('Failed to retrieve LLM providers list from Configuration Manager');
        setProvidersList([]);
        setWorkerProvider('');
      }
    }).catch(() => {
        setProvidersError('Failed to retrieve LLM providers list from Configuration Manager');
        setProvidersList([]);
        setWorkerProvider('');
        
        AsyncStorage.getItem('custom_providers').then(val => {
          if (val) {
            try {
              const parsed = JSON.parse(val);
              if (Array.isArray(parsed)) {
                setProvidersList(prev => {
                   const map = new Map(prev.map(p => [p.name, p]));
                   parsed.forEach(p => map.set(p.name, p));
                   return Array.from(map.values());
                });
              }
            } catch (e) {}
          }
        });
      }).finally(() => {
      setIsLoadingProviders(false);
    });
  }, [isRunning]);

  const toggleBiometrics = async (value: boolean) => {
    setIsBiometricEnabled(value);
    await AsyncStorage.setItem('use_biometrics', value ? 'true' : 'false');
  };

  const handleToggleSuspend = async (value: boolean) => {
    try {
      const success = await client.invoke('OrchestrationService', 'toggleSuspend', [value]);
      if (success) {
        setIsSuspended(value);
        Alert.alert(value ? 'System Suspended' : 'System Resumed', value ? 'All background AI tasks are paused.' : 'System restored to normal operation.');
      }
    } catch (e: any) {
      Alert.alert('Error', 'Failed to toggle suspend state: ' + e.message);
    }
  };

  const openEditModal = (providerName: string) => {
    if (providerName === 'Disable' || providerName === 'Custom') return;
    const provider = providersList.find(p => p.name === providerName);
    if (provider) {
        setEditData({ ...provider });
        setIsEditModalVisible(true);
    }
  };

  const handleSaveProviderEdit = () => {
    setProvidersList(prev => {
        const updated = [...prev];
        const idx = updated.findIndex(p => p.name === editData.name);
        if (idx >= 0) {
            updated[idx] = { ...editData };
        } else {
            updated.push({ ...editData });
        }
        return updated;
    });
    setIsEditModalVisible(false);
  };

  const handleDeleteProvider = () => {
    Alert.alert(
      'Confirm Delete',
      `Are you sure you want to delete ${editData.name}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        { 
          text: 'Delete', 
          style: 'destructive',
          onPress: () => {
            setProvidersList(prev => prev.filter(p => p.name !== editData.name));
            if (workerProvider === editData.name) setWorkerProvider(providersList[0]?.name || '');
            if (governanceProvider === editData.name) setGovernanceProvider('Disable');
            setIsEditModalVisible(false);
          }
        }
      ]
    );
  };

  const handleSaveLLM = async () => {
    try {
      const customOnly = providersList.filter(p => {
        const knownNames = ['OpenAI', 'Anthropic', 'Google Gemini', 'On-Device Model'];
        return !knownNames.includes(p.name);
      });
      await AsyncStorage.setItem('custom_providers', JSON.stringify(customOnly));

      const config = {
        'ai.worker.llm': workerProvider,
        'ai.governance.llm': governanceProvider === 'Disable' ? '' : governanceProvider,
        'onboarded.providers': providersList
      };

      await client.invoke('ConfigurationManager', 'saveSettings', ['llm.json', JSON.stringify(config)]);
      Alert.alert('Success', 'LLM Configuration saved successfully.');
    } catch (error: any) {
      Alert.alert('Error', 'Failed to save configuration: ' + error.message);
    }
  };

  const isProviderConfigured = (pName: string) => {
    if (isLoadingProviders) return false; 
    if (pName === 'Disable' || pName === 'Custom') return false;
    const p = providersList.find(x => x.name === pName);
    if (!p) return false;

    const endpoint = p.endpoint;
    const apiKey = p['api.key'] || p.apiKey;

    if (p.name.startsWith('Gemma') || p.name.includes('Ollama')) {
      return !!(endpoint && endpoint.trim().length > 0);
    }
    
    if (p.name === 'OpenAI' || p.name === 'Google Gemini' || p.name === 'Anthropic') {
      return !!(apiKey && apiKey.trim().length > 0);
    }

    return !!(endpoint && endpoint.trim().length > 0 && apiKey && apiKey.trim().length > 0);
  };

  const renderProviderSelector = (label: string, description: string, selectedProvider: string, isWorker: boolean) => (
      <View style={{ marginBottom: 16 }}>
        <Text style={[styles.sectionLabel, { color: theme.text, marginBottom: 4 }]}>{label}</Text>
        <Text style={[styles.description, { color: theme.subText, marginBottom: 8, marginTop: 0 }]}>{description}</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}>
            {selectedProvider === 'Custom' ? (
                <View style={[styles.monoInput, { flex: 1, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 0 }]}>
                   <TextInput 
                     style={{ flex: 1, color: '#000', padding: 10, fontFamily: 'monospace', fontSize: 12 }}
                     value={selectedProvider === 'Custom' ? '' : selectedProvider}
                     placeholder="Enter custom provider name"
                     onChangeText={(text) => {
                         if (isWorker) setWorkerProvider(text);
                         else setGovernanceProvider(text);
                     }}
                   />
                   <TouchableOpacity style={{ padding: 10 }} onPress={() => isWorker ? setWorkerModalVisible(true) : setGovModalVisible(true)}>
                     <Text style={{ color: '#64748b', fontSize: 12 }}>▼</Text>
                   </TouchableOpacity>
                </View>
            ) : (
                <TouchableOpacity 
                  style={[styles.monoInput, { flex: 1, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]} 
                  onPress={() => isWorker ? setWorkerModalVisible(true) : setGovModalVisible(true)}
                >
                  <Text style={{ color: '#000', fontWeight: '500' }}>{selectedProvider}</Text>
                  <Text style={{ color: '#64748b', fontSize: 12 }}>▼</Text>
                </TouchableOpacity>
            )}
            
            {selectedProvider !== 'Disable' && selectedProvider !== 'Custom' && (
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  {isLoadingProviders ? (
                    <View style={{ width: 10, height: 10, borderRadius: 5, marginRight: 8, backgroundColor: '#94a3b8' }} />
                  ) : (
                    <View style={{ 
                      width: 10, height: 10, borderRadius: 5, marginRight: 8,
                      backgroundColor: isProviderConfigured(selectedProvider) ? '#22c55e' : '#ef4444'
                    }} />
                  )}
                  <TouchableOpacity style={styles.editButton} onPress={() => openEditModal(selectedProvider)}>
                      <Text style={{ color: '#fff', fontSize: 12, fontWeight: '700' }}>Edit</Text>
                  </TouchableOpacity>
                </View>
            )}
        </View>
      </View>
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.bg }]}>
      <View style={styles.header}>
        <View style={styles.headerRow}>
          <Text style={styles.headerTitle}>Engine Configuration</Text>
          <TouchableOpacity onPress={() => router.replace('/')}>
            <Text style={{ color: '#fff', fontWeight: '700', fontSize: 14 }}>CLOSE</Text>
          </TouchableOpacity>
        </View>
      </View>

      <View style={[styles.tabBar, { backgroundColor: theme.tabBar }]}>
        {tabs.map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tabItem, activeTab === tab && styles.activeTabItem]}
            onPress={() => setActiveTab(tab)}
          >
            <Text style={[styles.tabText, activeTab === tab && styles.activeTabText]}>{tab}</Text>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {activeTab === 'General' && (
          <View style={styles.section}>
            <Text style={[styles.title, { color: theme.text }]}>AI Model (LLM) Settings</Text>
            <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
              
              {renderProviderSelector("Worker Provider", "This provider is used to perform all regular AI reasoning.", workerProvider, true)}
              {renderProviderSelector("Governance Provider", "Optional pre-processor to scan prompts for security risks and privacy violations.", governanceProvider, false)}

              <TouchableOpacity style={[styles.button, { marginTop: 16 }]} onPress={handleSaveLLM}>
                <Text style={styles.buttonText}>Save Configuration</Text>
              </TouchableOpacity>
            </View>

            {/* Selection Modals */}
            <Modal visible={isWorkerModalVisible} transparent={true} animationType="fade">
              <View style={styles.modalOverlay}>
                <View style={[styles.modalContent, { backgroundColor: theme.card }]}>
                  <Text style={[styles.title, { color: theme.text, marginBottom: 16 }]}>Select Worker Provider</Text>
                  <FlatList
                    data={providersList}
                    keyExtractor={(item) => item.name}
                    renderItem={({ item }) => (
                      <TouchableOpacity 
                        style={[styles.providerItem, { borderBottomColor: theme.border }]}
                        onPress={() => {
                          setWorkerProvider(item.name);
                          setWorkerModalVisible(false);
                        }}
                      >
                        <Text style={{ color: theme.text, fontSize: 14 }}>{item.name}</Text>
                      </TouchableOpacity>
                    )}
                  />
                  <TouchableOpacity style={[styles.outlineButton, { marginTop: 12 }]} onPress={() => setWorkerModalVisible(false)}>
                    <Text style={[styles.outlineButtonText, { color: theme.subText }]}>Cancel</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </Modal>

            <Modal visible={isGovModalVisible} transparent={true} animationType="fade">
              <View style={styles.modalOverlay}>
                <View style={[styles.modalContent, { backgroundColor: theme.card }]}>
                  <Text style={[styles.title, { color: theme.text, marginBottom: 16 }]}>Select Governance Provider</Text>
                  <FlatList
                    data={[{ name: 'Disable' }, ...providersList]}
                    keyExtractor={(item) => item.name}
                    renderItem={({ item }) => (
                      <TouchableOpacity 
                        style={[styles.providerItem, { borderBottomColor: theme.border }]}
                        onPress={() => {
                          setGovernanceProvider(item.name);
                          setGovModalVisible(false);
                        }}
                      >
                        <Text style={{ color: theme.text, fontSize: 14 }}>{item.name}</Text>
                      </TouchableOpacity>
                    )}
                  />
                  <TouchableOpacity style={[styles.outlineButton, { marginTop: 12 }]} onPress={() => setGovModalVisible(false)}>
                    <Text style={[styles.outlineButtonText, { color: theme.subText }]}>Cancel</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </Modal>

            {/* Edit Endpoint Attributes Matrix Modal */}
            <Modal visible={isEditModalVisible} transparent={true} animationType="slide">
              <View style={styles.modalOverlay}>
                <View style={[styles.modalContent, { backgroundColor: theme.card }]}>
                  <Text style={[styles.title, { color: theme.text, marginBottom: 16 }]}>Edit {editData.name}</Text>
                  
                  <ScrollView style={{ maxHeight: '60%' }}>
                    <View style={{ gap: 12, paddingBottom: 10 }}>
                      {Object.keys(editData).filter(key => key !== 'name' && !key.endsWith('options') && !key.endsWith('.options')).map(key => {
                        const optionsKey = `${key}_options`;
                        const optionsKeyAlt = `${key}.options`;
                        const options = editData[optionsKey] || editData[optionsKeyAlt];
                        
                        if (Array.isArray(options) && options.length > 0) {
                            return (
                                <View key={key} style={{ marginTop: 8 }}>
                                  <Text style={[styles.sectionLabel, { color: theme.text }]}>{key.toUpperCase()}</Text>
                                  <View style={[styles.monoInput, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}>
                                    <Text style={{ color: '#000' }}>{editData[key] || ''}</Text>
                                    <TouchableOpacity style={{ padding: 10 }} onPress={() => {
                                      const currentIndex = options.indexOf(editData[key]);
                                      const nextIndex = (currentIndex + 1) % options.length;
                                      setEditData({...editData, [key]: options[nextIndex] || ''});
                                    }}>
                                      <Text style={{ color: '#475569', fontSize: 11 }}>CHANGE</Text>
                                    </TouchableOpacity>
                                  </View>
                                </View>
                            );
                        }
                        
                        const isSecret = key.toLowerCase().includes('key') || key.toLowerCase().includes('password');
                        return (
                          <View key={key} style={{ marginTop: 8 }}>
                            <Text style={[styles.sectionLabel, { color: theme.text }]}>{key.toUpperCase()}</Text>
                            <TextInput
                              style={[styles.monoInput, { marginTop: 6 }]}
                              value={String(editData[key] || '')}
                              onChangeText={(text) => setEditData({...editData, [key]: text})}
                              secureTextEntry={isSecret}
                              placeholderTextColor="#94a3b8"
                            />
                          </View>
                        );
                      })}
                    </View>
                  </ScrollView>

                  <View style={{ flexDirection: 'row', gap: 10, marginTop: 24 }}>
                      <TouchableOpacity style={[styles.button, { flex: 1 }]} onPress={handleSaveProviderEdit}>
                        <Text style={styles.buttonText}>Save</Text>
                      </TouchableOpacity>
                      <TouchableOpacity style={[styles.outlineButton, { flex: 1 }]} onPress={() => setIsEditModalVisible(false)}>
                        <Text style={[styles.outlineButtonText, { color: theme.subText }]}>Cancel</Text>
                      </TouchableOpacity>
                  </View>
                  
                  <TouchableOpacity style={[styles.outlineButton, { marginTop: 12, borderColor: '#ef4444' }]} onPress={handleDeleteProvider}>
                      <Text style={{ color: '#ef4444', fontWeight: '700', fontSize: 13 }}>Delete Custom Provider</Text>
                  </TouchableOpacity>
                </View>
              </View>
            </Modal>
          </View>
        )}

        {activeTab === 'Security' && (
          <View style={styles.section}>
            <Text style={[styles.title, { color: theme.text }]}>Safety & Security Core</Text>

            <View style={[styles.card, { backgroundColor: theme.card, borderColor: '#f59e0b', borderWidth: 1 }]}>
              <View style={styles.row}>
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: 14, fontWeight: '700', color: '#f59e0b' }}>Suspend System Execution</Text>
                  <Text style={[styles.description, { color: theme.subText }]}>Pause all operational agent tasks and runtime context scanning.</Text>
                </View>
                <Switch 
                  value={isSuspended} 
                  onValueChange={handleToggleSuspend}
                  trackColor={{ false: "#cbd5e1", true: "#f59e0b" }}
                  thumbColor={isSuspended ? "#fff" : "#f4f3f4"}
                />
              </View>
            </View>

            <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
              <View style={styles.row}>
                <View style={{ flex: 1 }}>
                  <Text style={{ fontSize: 14, fontWeight: '700', color: theme.text }}>Biometric Integration</Text>
                  <Text style={[styles.description, { color: theme.subText }]}>Enforce secure local hardware verification paths upon bootstrap initialization.</Text>
                </View>
                <Switch 
                  value={isBiometricEnabled} 
                  onValueChange={toggleBiometrics}
                  trackColor={{ false: "#cbd5e1", true: "#00E5FF" }}
                  thumbColor={isBiometricEnabled ? "#fff" : "#f4f3f4"}
                />
              </View>
            </View>
          </View>
        )}

        {activeTab === 'Advanced' && (
          <View style={styles.section}>
            <Text style={[styles.title, { color: theme.text }]}>Advanced Diagnostics</Text>
            
            <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
              <Text style={{ fontSize: 14, fontWeight: '700', color: theme.text }}>System Storage Mount Path</Text>
              <Text style={styles.monoText}>/data/user/0/com.reveila.app/files</Text>
            </View>

            <View style={[styles.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
              <Text style={{ fontSize: 14, fontWeight: '700', color: theme.text }}>Knowledge Vault Integrity Sync</Text>
              <Text style={[styles.description, { color: theme.subText }]}>Force-trigger an explicit delta processing pass across authorized multi-tenant storage nodes.</Text>
              <TouchableOpacity style={[styles.button, { marginTop: 16 }]} onPress={async () => {
                try {
                  await client.invoke('OrchestrationService', 'triggerVaultScan', []);
                  Alert.alert('Vault scan initiated successfully in the background.');
                } catch (e: any) {
                  Alert.alert('Scan Failed: ' + e.message);
                }
              }}>
                <Text style={styles.buttonText}>FORCE RE-SCAN NOW</Text>
              </TouchableOpacity>
            </View>

            <View style={[styles.card, { backgroundColor: theme.card, borderColor: '#ef4444', borderWidth: 1, marginTop: 20 }]}>
              <Text style={{ fontSize: 14, fontWeight: '700', color: '#ef4444' }}>Reset Application Environment</Text>
              <Text style={[styles.description, { color: theme.subText }]}>Wipes all local state, storage parameters, and keys. This operation cannot be rolled back.</Text>
              <TouchableOpacity style={[styles.button, { backgroundColor: '#ef4444', marginTop: 16 }]} onPress={() => {
                Alert.alert(
                  'Confirm Destructive Reset',
                  'Are you absolutely sure you want to reset the environment? All configuration parameters will clear.',
                  [
                    { text: 'Cancel', style: 'cancel' },
                    { text: 'Reset Platform', style: 'destructive', onPress: () => {
                        client.invoke('OrchestrationService', 'resetApplication', []).then(() => {
                          Alert.alert('Application reset complete. Force restart the application workspace context.');
                        });
                      }
                    }
                  ]
                );
              }}>
                <Text style={styles.buttonText}>RESET EVERYTHING</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { backgroundColor: '#0f172a', paddingTop: 60, paddingBottom: 15, paddingHorizontal: 20 },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  headerTitle: { color: '#fff', fontSize: 16, fontWeight: '800', letterSpacing: 0.5 },
  tabBar: { flexDirection: 'row' },
  tabItem: { paddingVertical: 14, paddingHorizontal: 20, borderBottomWidth: 2, borderBottomColor: 'transparent' },
  activeTabItem: { borderBottomColor: '#00E5FF' },
  tabText: { color: '#94a3b8', fontSize: 13, fontWeight: '700' },
  activeTabText: { color: '#00E5FF' },
  content: { padding: 16 },
  section: { gap: 12, marginBottom: 24 },
  title: { fontSize: 16, fontWeight: '700', marginBottom: 4 },
  card: { borderRadius: 12, padding: 16, borderHorizontalWidth: 1, borderVerticalWidth: 1, elevation: 1 },
  description: { fontSize: 13, lineHeight: 18 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 10 },
  button: { padding: 12, borderRadius: 8, alignItems: 'center', backgroundColor: '#0f172a' },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 13 },
  editButton: { backgroundColor: '#0ea5e9', paddingHorizontal: 16, paddingVertical: 10, borderRadius: 8, marginLeft: 10 },
  outlineButton: { padding: 12, borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#cbd5e1' },
  outlineButtonText: { fontWeight: '700', fontSize: 13 },
  sectionLabel: { fontSize: 10, fontWeight: '900', letterSpacing: 1 },
  monoText: { fontFamily: 'monospace', fontSize: 11, marginTop: 8 },
  monoInput: { backgroundColor: '#fff', padding: 10, borderRadius: 8, borderWidth: 1, borderColor: '#e2e8f0', minHeight: 44, justifyContent: 'center' },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', alignItems: 'center', padding: 24 },
  modalContent: { width: '100%', borderRadius: 16, padding: 24, maxHeight: '80%' },
  providerItem: { paddingVertical: 16, borderBottomWidth: 1 }
});