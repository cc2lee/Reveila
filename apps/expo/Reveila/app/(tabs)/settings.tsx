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

/**
 * Transform LLM config from system-home format to app format
 * Maps: api.key -> apiKey, model.options -> model_options
 */
function transformProviderConfig(providers: any[]): any[] {
  return providers.map(p => ({
    name: p.name,
    type: p.type,
    endpoint: p.endpoint,
    model: p.model,
    model_options: p['model.options'],
    temperature: p.temperature,
    apiKey: p['api.key'] || ''
  }));
}

export default function SettingsScreen() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState('General');
  
  const [isBiometricEnabled, setIsBiometricEnabled] = useState(false);
  const [isChangePasswordVisible, setIsChangePasswordVisible] = useState(false);
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

  useEffect(() => {
    const checkRunning = async () => {
      try {
        const running = await ReveilaModule.isRunning();
        setIsRunning(running);
        if (running) {
          const suspended = await ReveilaModule.isSuspended();
          setIsSuspended(suspended);
        }
      } catch (e) {}
    };
    checkRunning();
    const interval = setInterval(checkRunning, 3000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    AsyncStorage.getItem('use_biometrics').then(val => {
      setIsBiometricEnabled(val === 'true');
    });
  }, []);

  useEffect(() => {
    if (!isRunning) return;

    ReveilaModule.invoke('ConfigurationManager', 'getSettings', ['llm.json']).then((res: string) => {
      if (res) {
        try {
          const config = JSON.parse(res);
          // Transform providers from system-home format to app format
          const onboarded = config['onboarded.providers'] || config.onboarded_providers || [];
          const transformedProviders = transformProviderConfig(onboarded);
          // Add Custom option for user-defined providers
          setProvidersList([...transformedProviders, { name: 'Custom', endpoint: '', model: '', apiKey: '' }]);
          setProvidersError(null); // Clear any previous error

          const legacyWorkerMap: Record<string, string> = { 'OpenAiProvider': 'OpenAI', 'AnthropicProvider': 'Anthropic', 'GeminiProvider': 'Google Gemini', 'OnDeviceProvider': 'On-Device Model' };
          const legacyGovMap: Record<string, string> = { ...legacyWorkerMap, '': 'Disable' };
           
          let wProvider = config['ai.worker.llm'] || '';
          if (legacyWorkerMap[wProvider]) wProvider = legacyWorkerMap[wProvider];
           
          let gProvider = config['ai.governance.llm'] || 'Disable';
          if (legacyGovMap[gProvider]) gProvider = legacyGovMap[gProvider];

          setWorkerProvider(wProvider || (transformedProviders.length > 0 ? transformedProviders[0].name : ''));
          setGovernanceProvider(gProvider);
        } catch (e) {
          // Show error on parse failure
          setProvidersError('Failed to parse LLM providers configuration');
          setProvidersList([]);
          setWorkerProvider('');
        }
      } else {
        // Show error when no config returned
        setProvidersError('Failed to retrieve LLM providers list from Configuration Manager');
        setProvidersList([]);
        setWorkerProvider('');
      }
    }).catch(() => {
        // Show error on invocation failure
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
      const success = await ReveilaModule.toggleSuspend(value);
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
        // Filter out known provider names to identify custom ones
        const knownNames = ['OpenAI', 'Anthropic', 'Google Gemini', 'On-Device Model'];
        return !knownNames.includes(p.name);
      });
      await AsyncStorage.setItem('custom_providers', JSON.stringify(customOnly));

      const config = {
        'ai.worker.llm': workerProvider,
        'ai.governance.llm': governanceProvider === 'Disable' ? '' : governanceProvider,
        'onboarded.providers': providersList
      };

      await ReveilaModule.invoke('ConfigurationManager', 'saveSettings', ['llm.json', JSON.stringify(config)]);
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
        <ThemedText type="defaultSemiBold" style={{ marginBottom: 4 }}>{label}</ThemedText>
        <ThemedText style={[styles.description, { marginBottom: 8, marginTop: 0 }]}>{description}</ThemedText>
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
                     <IconSymbol name="chevron.down" size={16} color="#64748b" />
                   </TouchableOpacity>
                </View>
            ) : (
                <TouchableOpacity 
                  style={[styles.monoInput, { flex: 1, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]} 
                  onPress={() => isWorker ? setWorkerModalVisible(true) : setGovModalVisible(true)}
                >
                  <ThemedText style={{ color: '#000' }}>{selectedProvider}</ThemedText>
                  <IconSymbol name="chevron.down" size={16} color="#64748b" />
                </TouchableOpacity>
            )}
            
            {selectedProvider !== 'Disable' && selectedProvider !== 'Custom' && (
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  {isLoadingProviders ? (
                    <View style={{ width: 10, height: 10, borderRadius: 5, marginRight: 8, backgroundColor: '#94a3b8' }} />
                  ) : (
                    <View style={{ 
                      width: 10, height: 10, borderRadius: 5, marginRight: 8,
                      backgroundColor: isProviderConfigured(selectedProvider) ? '#22c55e' : '#ef4444',
                      shadowColor: isProviderConfigured(selectedProvider) ? '#22c55e' : '#ef4444',
                      shadowOffset: { width: 0, height: 0 },
                      shadowOpacity: 0.8,
                      shadowRadius: 4,
                      elevation: 3
                    }} />
                  )}
                  <TouchableOpacity style={styles.editButton} onPress={() => openEditModal(selectedProvider)}>
                      <ThemedText style={{ color: '#fff', fontSize: 12, fontWeight: '700' }}>Edit</ThemedText>
                  </TouchableOpacity>
                </View>
            )}
        </View>
      </View>
  );

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
            
            <ThemedText type="subtitle">AI Model (LLM) Setup</ThemedText>
            <ThemedView style={styles.card}>
              
              {renderProviderSelector("Worker Provider", "This provider is used to perform all regular AI reasoning.", workerProvider, true)}
              {renderProviderSelector("Governance Provider", "Optional, if set, this provider is used to pre-process prompts to scan for potential security risks like prompt injection and privacy violations.", governanceProvider, false)}

              <TouchableOpacity style={[styles.button, { marginTop: 16 }]} onPress={handleSaveLLM}>
                <ThemedText style={styles.buttonText}>Save Configuration</ThemedText>
              </TouchableOpacity>
            </ThemedView>

            {/* Provider List Modals */}
            <Modal visible={isWorkerModalVisible} transparent={true} animationType="fade">
              <View style={styles.modalOverlay}>
                <View style={styles.modalContent}>
                  <ThemedText type="subtitle" style={{ marginBottom: 16 }}>Select Worker Provider</ThemedText>
                  <FlatList
                    data={providersList}
                    keyExtractor={(item) => item.name}
                    renderItem={({ item }) => (
                      <TouchableOpacity 
                        style={styles.providerItem}
                        onPress={() => {
                          setWorkerProvider(item.name);
                          setWorkerModalVisible(false);
                        }}
                      >
                        <ThemedText>{item.name}</ThemedText>
                      </TouchableOpacity>
                    )}
                  />
                  <TouchableOpacity style={styles.outlineButton} onPress={() => setWorkerModalVisible(false)}>
                    <ThemedText style={styles.outlineButtonText}>Cancel</ThemedText>
                  </TouchableOpacity>
                </View>
              </View>
            </Modal>

            <Modal visible={isGovModalVisible} transparent={true} animationType="fade">
              <View style={styles.modalOverlay}>
                <View style={styles.modalContent}>
                  <ThemedText type="subtitle" style={{ marginBottom: 16 }}>Select Governance Provider</ThemedText>
                  <FlatList
                    data={[{ name: 'Disable' }, ...providersList]}
                    keyExtractor={(item) => item.name}
                    renderItem={({ item }) => (
                      <TouchableOpacity 
                        style={styles.providerItem}
                        onPress={() => {
                          setGovernanceProvider(item.name);
                          setGovModalVisible(false);
                        }}
                      >
                        <ThemedText>{item.name}</ThemedText>
                      </TouchableOpacity>
                    )}
                  />
                  <TouchableOpacity style={styles.outlineButton} onPress={() => setGovModalVisible(false)}>
                    <ThemedText style={styles.outlineButtonText}>Cancel</ThemedText>
                  </TouchableOpacity>
                </View>
              </View>
            </Modal>

            {/* Edit Provider Modal */}
            <Modal visible={isEditModalVisible} transparent={true} animationType="slide">
              <View style={styles.modalOverlay}>
                <View style={styles.modalContent}>
                  <ThemedText type="subtitle" style={{ marginBottom: 16 }}>Edit {editData.name}</ThemedText>
                  
                  <ScrollView style={{ maxHeight: '70%' }} contentContainerStyle={{ gap: 12 }}>
                    {Object.keys(editData).filter(key => key !== 'name' && !key.endsWith('options') && !key.endsWith('.options')).map(key => {
                      const optionsKey = `${key}_options`;
                      const optionsKeyAlt = `${key}.options`;
                      const options = editData[optionsKey] || editData[optionsKeyAlt];
                      
                      if (Array.isArray(options) && options.length > 0) {
                          // Render as dropdown for key-name.options pairs
                          return (
                              <View key={key} style={{ marginTop: 8 }}>
                                <ThemedText type="defaultSemiBold">{key.charAt(0).toUpperCase() + key.slice(1)}</ThemedText>
                                <View style={[styles.monoInput, { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }]}>
                                  <ThemedText style={{ color: '#000' }}>{editData[key] || ''}</ThemedText>
                                  <TouchableOpacity style={{ padding: 10 }} onPress={() => {
                                    // Simple implementation: cycle through options for now
                                    const currentIndex = options.indexOf(editData[key]);
                                    const nextIndex = (currentIndex + 1) % options.length;
                                    setEditData({...editData, [key]: options[nextIndex] || ''});
                                  }}>
                                    <IconSymbol name="chevron.down" size={16} color="#64748b" />
                                  </TouchableOpacity>
                                </View>
                              </View>
                          );
                      }
                      
                      const isSecret = key.toLowerCase().includes('key') || key.toLowerCase().includes('password');
                      return (
                        <View key={key} style={{ marginTop: 8 }}>
                          <ThemedText type="defaultSemiBold">{key}</ThemedText>
                          <TextInput
                            style={[styles.monoInput, { marginTop: 8 }]}
                            value={String(editData[key] || '')}
                            onChangeText={(text) => setEditData({...editData, [key]: text})}
                            secureTextEntry={isSecret}
                            placeholderTextColor="#94a3b8"
                          />
                        </View>
                      );
                    })}
                  </ScrollView>

                  <View style={{ flexDirection: 'row', gap: 10, marginTop: 24 }}>
                      <TouchableOpacity style={[styles.button, { flex: 1 }]} onPress={handleSaveProviderEdit}>
                        <ThemedText style={styles.buttonText}>Save Changes</ThemedText>
                      </TouchableOpacity>
                      <TouchableOpacity style={[styles.outlineButton, { flex: 1 }]} onPress={() => setIsEditModalVisible(false)}>
                        <ThemedText style={styles.outlineButtonText}>Cancel</ThemedText>
                      </TouchableOpacity>
                  </View>
                  
                  <TouchableOpacity style={[styles.outlineButton, { marginTop: 12, borderColor: '#ef4444' }]} onPress={handleDeleteProvider}>
                      <ThemedText style={{ color: '#ef4444', fontWeight: '700', fontSize: 13 }}>Delete Custom Provider</ThemedText>
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

            <ThemedView style={[styles.card, {borderColor: '#f59e0b', borderWidth: 1}]}>
              <View style={styles.row}>
                <View style={{flex: 1}}>
                  <ThemedText type="defaultSemiBold" style={{color: '#f59e0b'}}>Suspend System</ThemedText>
                  <ThemedText style={styles.description}>Pause all background processes and plugin executions.</ThemedText>
                </View>
                <Switch 
                  value={isSuspended} 
                  onValueChange={handleToggleSuspend}
                  trackColor={{ false: "#cbd5e1", true: "#f59e0b" }}
                  thumbColor={isSuspended ? "#fff" : "#f4f3f4"}
                />
              </View>
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
                  Alert.alert('Vault scan initiated in the background.');
                } catch (e: any) {
                  Alert.alert('Scan Failed: ' + e.message);
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
                        ReveilaModule.resetApplication().then(() => Alert.alert('Application Reset Successfully. Please restart the app.'));
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
  editButton: { backgroundColor: '#0ea5e9', paddingHorizontal: 16, paddingVertical: 12, borderRadius: 8, marginLeft: 10 },
  outlineButton: { padding: 12, borderRadius: 8, alignItems: 'center', borderWidth: 1, borderColor: '#cbd5e1' },
  outlineButtonText: { color: '#475569', fontWeight: '700', fontSize: 13 },
  cautionBox: { marginTop: 12, padding: 10, backgroundColor: '#fff7ed', borderRadius: 6, borderWidth: 1, borderColor: '#ffedd5' },
  cautionText: { color: '#9a3412', fontSize: 12, fontWeight: '600' },
  monoText: { fontFamily: 'monospace', fontSize: 11, color: '#64748b', marginTop: 8 },
  monoInput: { fontFamily: 'monospace', fontSize: 12, backgroundColor: '#fff', padding: 10, borderRadius: 8, borderWidth: 1, borderColor: '#e2e8f0', minHeight: 40 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', alignItems: 'center', padding: 20 },
  modalContent: { backgroundColor: '#fff', width: '100%', borderRadius: 12, padding: 20, maxHeight: '80%' },
  providerItem: { paddingVertical: 15, borderBottomWidth: 1, borderBottomColor: '#f1f5f9' }
});
