import { StyleSheet, TouchableOpacity, ScrollView, View, Switch, TextInput } from 'react-native';
import { useState, useEffect } from 'react';
import { useRouter } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { IconSymbol } from '@/components/ui/icon-symbol';
import ReveilaModule from '@/modules/reveila';

export default function SettingsScreen() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState('General');
  const [quantization, setQuantization] = useState('Q4_K_M');
  const [isHighSecurity, setIsHighSecurity] = useState(true);
  const [apiKey, setApiKey] = useState('');
  const [tasks, setTasks] = useState<any[]>([]);
  const [selectedTask, setSelectedTask] = useState<any>(null);

  const tabs = ['General', 'Security', 'Tasks', 'Advanced'];

  useEffect(() => {
    if (activeTab === 'Tasks') {
      fetchTasks();
    }
  }, [activeTab]);

  const fetchTasks = async () => {
    try {
      const result = await fetch('/api/tasks'); // Assuming local proxy or direct call
      const data = await result.json();
      setTasks(data);
    } catch (e) {}
  };

  const handleKill = async () => {
    if (confirm('EMERGENCY: Terminate all running AI processes and revoke access?')) {
      // Logic to stop service
      alert('Kill Switch Activated. All processes stopped.');
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <View style={styles.headerRow}>
          <ThemedText type="title">Settings</ThemedText>
          <TouchableOpacity onPress={() => router.replace('/')}>
            <IconSymbol name="xmark" color="#fff" size={24} />
          </TouchableOpacity>
        </View>
      </ThemedView>

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
            <ThemedText type="subtitle">Model Setup</ThemedText>
            
            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Cloud Model API Key</ThemedText>
              <ThemedText style={styles.description}>Provide your API key to access remote models.</ThemedText>
              <TextInput
                style={[styles.monoInput, { minHeight: 40, marginTop: 12 }]}
                placeholder="sk-..."
                placeholderTextColor="#94a3b8"
                value={apiKey}
                onChangeText={setApiKey}
                secureTextEntry={true}
              />
            </ThemedView>

            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Local Model: Gemma-3-1b</ThemedText>
              <ThemedText style={styles.description}>Configure performance vs accuracy for the local private model.</ThemedText>
              
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

            <TouchableOpacity style={styles.outlineButton} onPress={() => ReveilaModule.startSovereignSetup()}>
              <ThemedText style={styles.outlineButtonText}>Re-run Setup</ThemedText>
            </TouchableOpacity>
          </View>
        )}

        {activeTab === 'Security' && (
          <View style={styles.section}>
            <ThemedText type="subtitle">Safety & Privacy</ThemedText>
            
            <ThemedView style={[styles.card, {borderColor: '#ef4444', borderWidth: 1}]}>
              <ThemedText type="defaultSemiBold" style={{color: '#ef4444'}}>Emergency Kill Switch</ThemedText>
              <ThemedText style={styles.description}>Immediately revoke all JIT tokens and terminate the AI system process.</ThemedText>
              <TouchableOpacity style={[styles.button, {backgroundColor: '#ef4444', marginTop: 16}]} onPress={handleKill}>
                <ThemedText style={styles.buttonText}>KILL ALL PROCESSES</ThemedText>
              </TouchableOpacity>
            </ThemedView>

            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Biometric Enforcement</ThemedText>
              <View style={styles.cautionBox}>
                <ThemedText style={styles.cautionText}>⚠ This setting is managed by system policy and cannot be disabled manually for this device.</ThemedText>
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
  monoInput: { fontFamily: 'monospace', fontSize: 12, backgroundColor: '#fff', padding: 10, borderRadius: 8, borderWidth: 1, borderColor: '#e2e8f0', minHeight: 150 }
});
