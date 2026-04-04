import React, { useState } from 'react';
import { StyleSheet, TextInput, TouchableOpacity, Alert } from 'react-native';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';
import ReveilaModule from '../modules/reveila';

interface Props {
  onUnlock: () => void;
}

export function LockScreen({ onUnlock }: Props) {
  const [password, setPassword] = useState('');

  const handleUnlock = async () => {
    try {
      const success = await ReveilaModule.unlockWithMasterPassword(password);
      if (success) {
        onUnlock();
      }
    } catch (e: any) {
      if (e.code === 'E_FULL_PWD_REQUIRED') {
        Alert.alert('Security Cycle', 'Your 30-day convenience window has expired. Please enter your FULL master password.');
      } else {
        Alert.alert('Unlock Failed', 'Incorrect password.');
      }
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>System Locked</ThemedText>
      <ThemedText style={styles.description}>Your session has timed out. Enter your master password (or first 4 characters if eligible) to continue.</ThemedText>

      <TextInput
        style={styles.input}
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        placeholder="Master password"
        placeholderTextColor="#94a3b8"
        autoFocus
      />

      <TouchableOpacity style={styles.button} onPress={handleUnlock}>
        <ThemedText style={styles.buttonText}>Unlock Fabric</ThemedText>
      </TouchableOpacity>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 20, flex: 1, justifyContent: 'center', backgroundColor: '#f1f5f9' },
  title: { marginBottom: 12, textAlign: 'center' },
  description: { textAlign: 'center', color: '#64748b', marginBottom: 30, fontSize: 14 },
  input: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    marginBottom: 20,
    color: '#0f172a'
  },
  button: {
    backgroundColor: '#ff6600',
    padding: 16,
    borderRadius: 10,
    alignItems: 'center'
  },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 15 }
});
