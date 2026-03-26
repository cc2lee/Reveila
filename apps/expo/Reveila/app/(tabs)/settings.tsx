import { StyleSheet, TouchableOpacity, ScrollView, View, Switch } from 'react-native';
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

  const tabs = ['General', 'Security', 'Advanced'];

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <ThemedText type="title">Settings</ThemedText>
          <TouchableOpacity onPress={() => router.replace('/')}>
            <IconSymbol name="xmark" color="#fff" size={24} />
          </TouchableOpacity>
        </View>
      </ThemedView>

      {/* Settings Tabs */}
      <View style={styles.tabBar}>
        {tabs.map((tab) => (
          <TouchableOpacity
            key={tab}
            style={[styles.tabItem, activeTab === tab && styles.activeTabItem]}
            onPress={() => setActiveTab(tab)}
          >
            <ThemedText style={[styles.tabText, activeTab === tab && styles.activeTabText]}>
              {tab}
            </ThemedText>
          </TouchableOpacity>
        ))}
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {activeTab === 'General' && (
          <ThemedView style={styles.section}>
            <ThemedText type="subtitle" style={styles.sectionTitle}>Model Configuration</ThemedText>
            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Quantization Level</ThemedText>
              <ThemedText style={styles.description}>
                Higher quantization (F16) requires more RAM but provides better accuracy.
              </ThemedText>
              
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
                  <ThemedText style={[styles.radioText, quantization === 'F16' && styles.radioTextActive]}>F16 (High Quality)</ThemedText>
                </TouchableOpacity>
              </View>
            </ThemedView>

            <TouchableOpacity 
              style={styles.outlineButton} 
              onPress={() => ReveilaModule.startSovereignSetup()}
            >
              <ThemedText style={styles.outlineButtonText}>Re-run Hardware Profiling</ThemedText>
            </TouchableOpacity>
          </ThemedView>
        )}

        {activeTab === 'Security' && (
          <ThemedView style={styles.section}>
            <ThemedText type="subtitle" style={styles.sectionTitle}>Sovereign Security</ThemedText>
            <ThemedView style={styles.card}>
              <View style={styles.row}>
                <View style={{ flex: 1 }}>
                  <ThemedText type="defaultSemiBold">Hardware Kill Switch</ThemedText>
                  <ThemedText style={styles.description}>
                    Immediately terminate all agent processes if unauthorized behavior is detected.
                  </ThemedText>
                </View>
                <Switch 
                  value={isHighSecurity} 
                  onValueChange={setIsHighSecurity}
                  trackColor={{ false: '#767577', true: '#ef4444' }}
                />
              </View>
            </ThemedView>

            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">Biometric Enforcement</ThemedText>
              <ThemedText style={styles.description}>
                Require biometric authentication for high-risk agent operations.
              </ThemedText>
              <ThemedText style={{ color: '#22c55e', fontSize: 12, marginTop: 8 }}>✓ Active (Hardware Locked)</ThemedText>
            </ThemedView>
          </ThemedView>
        )}

        {activeTab === 'Advanced' && (
          <ThemedView style={styles.section}>
            <ThemedText type="subtitle" style={styles.sectionTitle}>Developer Tools</ThemedText>
            <ThemedView style={styles.card}>
              <ThemedText type="defaultSemiBold">System Home</ThemedText>
              <ThemedText style={styles.monoText}>/data/user/0/com.reveila.android/files</ThemedText>
            </ThemedView>

            <TouchableOpacity 
              style={[styles.button, { backgroundColor: '#ef4444' }]}
              onPress={() => {}}
            >
              <ThemedText style={styles.buttonText}>Wipe Sovereign Memory</ThemedText>
            </TouchableOpacity>
          </ThemedView>
        )}
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
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#1e293b',
    paddingHorizontal: 10,
  },
  tabItem: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  activeTabItem: {
    borderBottomColor: '#00E5FF',
  },
  tabText: {
    color: '#94a3b8',
    fontSize: 14,
    fontWeight: '600',
  },
  activeTabText: {
    color: '#00E5FF',
  },
  content: {
    padding: 20,
  },
  section: {
    gap: 16,
  },
  sectionTitle: {
    marginBottom: 4,
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
  description: {
    fontSize: 13,
    color: '#64748b',
    marginTop: 4,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  radioGroup: {
    flexDirection: 'row',
    marginTop: 16,
    gap: 10,
  },
  radioButton: {
    flex: 1,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e2e8f0',
    alignItems: 'center',
  },
  radioActive: {
    borderColor: '#00E5FF',
    backgroundColor: '#ecfeff',
  },
  radioText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#64748b',
  },
  radioTextActive: {
    color: '#0891b2',
  },
  button: {
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  outlineButton: {
    padding: 14,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#cbd5e1',
    marginTop: 8,
  },
  outlineButtonText: {
    color: '#475569',
    fontWeight: '600',
  },
  monoText: {
    fontFamily: 'monospace',
    fontSize: 12,
    color: '#475569',
    marginTop: 8,
    backgroundColor: '#f8fafc',
    padding: 8,
    borderRadius: 4,
  }
});
