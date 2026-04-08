import React, { useState, useEffect } from 'react';
import { StyleSheet, TextInput, TouchableOpacity, Alert, ScrollView, View } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';
import { ReveilaHeader } from './ReveilaHeader';
import ReveilaModule from '../modules/reveila';

interface Props {
  onUnlock: () => void;
}

export function LockScreen({ onUnlock }: Props) {
  const [password, setPassword] = useState('');
  const [showPasswordModal, setShowPasswordModal] = useState(false);

  useEffect(() => {
    AsyncStorage.getItem('use_biometrics').then(val => {
      if (val === 'true') {
        handleBiometricUnlock();
      } else {
        setShowPasswordModal(true);
      }
    });
  }, []);

  const handleBiometricUnlock = async () => {
    try {
      const result = await ReveilaModule.authenticateBiometric();
      if (result.success) {
        onUnlock();
      } else {
        setShowPasswordModal(true);
      }
    } catch (e) {
      setShowPasswordModal(true);
    }
  };

  const handleUnlock = async () => {
    try {
      const success = await ReveilaModule.unlockWithMasterPassword(password);
      if (success) {
        onUnlock();
      }
    } catch (e: any) {
      console.error('Unlock error:', e);
      if (e.code === 'E_FULL_PWD_REQUIRED') {
        Alert.alert('Security Cycle', 'Your 30-day convenience window has expired. Please enter your FULL master password.');
      } else if (e.code === 'E_NOT_READY') {
        Alert.alert('System Not Ready', 'The Reveila engine is not initialized. Please wait a moment and try again.');
      } else if (e.code === 'E_INVALID_LENGTH') {
        Alert.alert('Invalid Password', e.message);
      } else {
        Alert.alert('Unlock Failed', `Error: ${e.message || 'Incorrect password'}`);
      }
    }
  };

  const handleReset = async () => {
    Alert.alert(
      'Reset Application',
      'This will delete all local configuration and encryption keys. You will need to start the setup from scratch. Continue?',
      [
        { text: 'Cancel', style: 'cancel' },
        { 
          text: 'Reset Everything', 
          style: 'destructive',
          onPress: async () => {
            try {
              await ReveilaModule.resetApplication();
              Alert.alert('System Reset', 'The application has been reset. Please restart the app.');
            } catch (e: any) {
              Alert.alert('Reset Error', e.message);
            }
          }
        }
      ]
    );
  };

  return (
    <ThemedView style={styles.container}>
      <ReveilaHeader />
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <ThemedText type="title" style={styles.title}>System Locked</ThemedText>
        <ThemedText style={styles.description}>
          {showPasswordModal 
            ? "Enter master password (or first 4 characters if eligible) to continue."
            : "Authenticating with biometrics..."}
        </ThemedText>

        {showPasswordModal ? (
          <>
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              placeholder="Master password"
              placeholderTextColor="#94a3b8"
              autoFocus
              maxLength={32}
            />

            <TouchableOpacity style={styles.button} onPress={handleUnlock}>
              <ThemedText style={styles.buttonText}>Unlock</ThemedText>
            </TouchableOpacity>
          </>
        ) : (
          <TouchableOpacity style={styles.button} onPress={handleBiometricUnlock}>
            <ThemedText style={styles.buttonText}>Retry Biometrics</ThemedText>
          </TouchableOpacity>
        )}

        <TouchableOpacity style={styles.resetButton} onPress={handleReset}>
          <ThemedText style={styles.resetButtonText}>Reset Application</ThemedText>
        </TouchableOpacity>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f1f5f9' },
  scrollContent: { padding: 20, flexGrow: 1, justifyContent: 'center' },
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
    color: '#0f172a',
    height: 50,
  },
  button: {
    backgroundColor: '#ff6600',
    padding: 16,
    borderRadius: 10,
    alignItems: 'center'
  },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 15 },
  resetButton: {
    padding: 16,
    alignItems: 'center',
    marginTop: 20
  },
  resetButtonText: { color: '#ef4444', fontWeight: '700', fontSize: 14 }
});
