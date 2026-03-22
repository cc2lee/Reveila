import { useState, useEffect } from 'react';
import { View, Text, TextInput, Button, StyleSheet, ScrollView, TouchableOpacity } from 'react-native';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import ReveilaExpoModule from '../../modules/my-java-module';

export default function SettingsScreen() {
  const [settings, setSettings] = useState<Record<string, any>>({});
  const [activeTab, setActiveTab] = useState<string | null>(null);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const responseStr = await ReveilaExpoModule.getSettingsConfigs();
      const rawSettings = JSON.parse(responseStr);
      const parsedSettings: Record<string, any> = {};
      for (const [key, val] of Object.entries(rawSettings)) {
        try {
          parsedSettings[key] = JSON.parse(val as string);
        } catch {
          parsedSettings[key] = { value: val };
        }
      }
      setSettings(parsedSettings);
      if (Object.keys(parsedSettings).length > 0) {
        setActiveTab(Object.keys(parsedSettings)[0]);
      }
    } catch (e: any) {
      console.error("Failed to load settings:", e);
    }
  };

  const handleSave = async () => {
    try {
      const payload: Record<string, string> = {};
      for (const [key, val] of Object.entries(settings)) {
        payload[key] = JSON.stringify(val, null, 2);
      }
      await ReveilaExpoModule.saveSettingsConfigs(JSON.stringify(payload));
      alert('Settings saved successfully');
    } catch (e: any) {
      console.error("Failed to save settings:", e);
      alert('Error saving settings');
    }
  };

  const handleInputChange = (tabKey: string, fieldKey: string, newValue: string) => {
    setSettings(prev => ({
      ...prev,
      [tabKey]: {
        ...prev[tabKey],
        [fieldKey]: newValue
      }
    }));
  };

  const tabs = Object.keys(settings);

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.header}>Dynamic Settings</ThemedText>
      
      {tabs.length > 0 && (
        <View style={styles.tabContainer}>
          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            {tabs.map(tab => (
              <TouchableOpacity
                key={tab}
                style={[styles.tabButton, activeTab === tab && styles.activeTabButton]}
                onPress={() => setActiveTab(tab)}
              >
                <Text style={styles.tabText}>{tab}</Text>
              </TouchableOpacity>
            ))}
          </ScrollView>
        </View>
      )}

      <ScrollView style={styles.contentContainer}>
        {activeTab && settings[activeTab] && Object.entries(settings[activeTab]).map(([key, value]) => (
          <View key={key} style={styles.inputGroup}>
            <Text style={styles.label}>{key}</Text>
            <TextInput
              style={styles.input}
              value={String(value)}
              onChangeText={(text) => handleInputChange(activeTab, key, text)}
            />
          </View>
        ))}
      </ScrollView>

      <View style={styles.buttonContainer}>
        <Button title="Reload" onPress={loadSettings} />
        <Button title="Save Settings" onPress={handleSave} />
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  header: {
    marginBottom: 16,
  },
  tabContainer: {
    flexDirection: 'row',
    marginBottom: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#ccc',
  },
  tabButton: {
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderBottomWidth: 2,
    borderBottomColor: 'transparent',
  },
  activeTabButton: {
    borderBottomColor: '#007bff',
  },
  tabText: {
    fontWeight: 'bold',
  },
  contentContainer: {
    flex: 1,
  },
  inputGroup: {
    marginBottom: 16,
  },
  label: {
    marginBottom: 4,
    fontWeight: '500',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ccc',
    padding: 10,
    borderRadius: 4,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 16,
  }
});