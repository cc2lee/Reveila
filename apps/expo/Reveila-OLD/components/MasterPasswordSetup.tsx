import React, { useState } from 'react';
import { StyleSheet, TextInput, TouchableOpacity, View, Alert, ScrollView } from 'react-native';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';
import { ReveilaHeader } from './ReveilaHeader';
import ReveilaModule from '../modules/reveila';

interface Props {
  onComplete: (password: string) => void;
}

export function MasterPasswordSetup({ onComplete }: Props) {
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleSetup = async () => {
    if (password.length < 16 || password.length > 32) {
      Alert.alert('Invalid Password', 'Password must be between 16 and 32 characters long.');
      return;
    }

    if (password !== confirmPassword) {
      Alert.alert('Mismatch', 'Passwords do not match.');
      return;
    }

    try {
      // Setup the master password - this generates a new DEK and stores encrypted copies
      await ReveilaModule.setupMasterPassword(password);
      onComplete(password);
    } catch (e: any) {
      Alert.alert('Setup Error', e.message);
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
      <ReveilaHeader subtitle="Private" color="#00E5FF" />
      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        <ThemedText type="title" style={styles.title}>Master Password Setup</ThemedText>
        
        <View style={styles.warningBox}>
          <ThemedText style={styles.warningTitle}>🔐 CRITICAL SECURITY NOTICE</ThemedText>
          <ThemedText style={styles.warningText}>
            Your master password is the sole cryptographic key protecting your encrypted data. Reveila uses zero-knowledge architecture—your password is never transmitted or stored on any server.{'\n\n'}
            <ThemedText style={[styles.warningText, {fontWeight: '700'}]}>
              If you lose this password, data recovery is cryptographically impossible.
            </ThemedText> There are no backdoors, reset mechanisms, or password recovery options.
          </ThemedText>
        </View>

        <View style={styles.infoBox}>
          <ThemedText style={styles.infoTitle}>Password Requirements</ThemedText>
          <ThemedText style={styles.infoText}>
            • Length: 16-32 characters{'\n'}
            • Recommended: Use a passphrase with multiple words{'\n'}
            • Store securely: Use a password manager or secure offline storage{'\n'}
            • Avoid: Personal information, common words, or predictable patterns
          </ThemedText>
        </View>

        <ThemedText style={styles.label}>Master Password</ThemedText>
        <TextInput
          style={styles.input}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          placeholder="Enter your master password (16-32 characters)"
          placeholderTextColor="#94a3b8"
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={32}
        />

        <ThemedText style={styles.label}>Confirm Password</ThemedText>
        <TextInput
          style={styles.input}
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          secureTextEntry
          placeholder="Re-enter password to confirm"
          placeholderTextColor="#94a3b8"
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={32}
        />

        <TouchableOpacity style={styles.button} onPress={handleSetup}>
          <ThemedText style={styles.buttonText}>Create Master Password</ThemedText>
        </TouchableOpacity>

        <ThemedText style={styles.disclaimer}>
          By proceeding, you acknowledge that you understand the irreversible nature of password loss and accept full responsibility for secure password management.
        </ThemedText>

        <TouchableOpacity style={styles.resetButton} onPress={handleReset}>
          <ThemedText style={styles.resetButtonText}>Reset Application</ThemedText>
        </TouchableOpacity>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scrollContent: { padding: 20, flexGrow: 1, justifyContent: 'center' },
  title: { marginBottom: 30, textAlign: 'center' },
  warningBox: {
    backgroundColor: '#fee2e2',
    padding: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ef4444',
    marginBottom: 24,
  },
  warningTitle: { color: '#b91c1c', fontWeight: '900', fontSize: 12, marginBottom: 8, letterSpacing: 1 },
  warningText: { color: '#7f1d1d', fontSize: 13, lineHeight: 20 },
  infoBox: {
    backgroundColor: '#dbeafe',
    padding: 16,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#3b82f6',
    marginBottom: 24,
  },
  infoTitle: { color: '#1e40af', fontWeight: '700', fontSize: 12, marginBottom: 8, letterSpacing: 0.5 },
  infoText: { color: '#1e3a8a', fontSize: 13, lineHeight: 20 },
  label: { fontSize: 14, color: '#64748b', marginBottom: 8, fontWeight: '600' },
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
    backgroundColor: '#0f172a',
    padding: 16,
    borderRadius: 10,
    alignItems: 'center',
    marginTop: 10
  },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 15 },
  resetButton: {
    padding: 16,
    alignItems: 'center',
    marginTop: 20
  },
  resetButtonText: { color: '#ef4444', fontWeight: '700', fontSize: 14 },
  disclaimer: {
    fontSize: 11,
    color: '#64748b',
    textAlign: 'center',
    marginTop: 16,
    lineHeight: 16,
    fontStyle: 'italic'
  }
});
