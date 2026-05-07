import React, { useState } from 'react';
import { StyleSheet, TextInput, TouchableOpacity, Alert } from 'react-native';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';
import ReveilaModule from '../modules/reveila';

export function ChangePasswordModal({ onCancel, onSuccess }: { onCancel: () => void, onSuccess: () => void }) {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isChanging, setIsChanging] = useState(false);

  const handleChange = async () => {
    if (newPassword.length < 16 || newPassword.length > 32) {
      Alert.alert('Invalid Password', 'New password must be between 16 and 32 characters long.');
      return;
    }

    if (newPassword !== confirmPassword) {
      Alert.alert('Mismatch', 'New passwords do not match.');
      return;
    }

    setIsChanging(true);
    try {
      await ReveilaModule.changeMasterPassword(oldPassword, newPassword);
      Alert.alert('Success', 'Master password changed successfully. No data re-encryption was needed!');
      onSuccess();
    } catch (e: any) {
      Alert.alert('Change Failed', e.message);
    } finally {
      setIsChanging(false);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title" style={styles.title}>Change Master Password</ThemedText>
      
      <TextInput
        style={styles.input}
        value={oldPassword}
        onChangeText={setOldPassword}
        secureTextEntry
        placeholder="Current Master Password"
        placeholderTextColor="#94a3b8"
      />

      <TextInput
        style={styles.input}
        value={newPassword}
        onChangeText={setNewPassword}
        secureTextEntry
        placeholder="New Master Password (16-32 chars)"
        placeholderTextColor="#94a3b8"
      />

      <TextInput
        style={styles.input}
        value={confirmPassword}
        onChangeText={setConfirmPassword}
        secureTextEntry
        placeholder="Confirm New Password"
        placeholderTextColor="#94a3b8"
      />

      <TouchableOpacity 
        style={[styles.button, { opacity: isChanging ? 0.5 : 1 }]} 
        onPress={handleChange}
        disabled={isChanging}
      >
        <ThemedText style={styles.buttonText}>{isChanging ? 'Changing...' : 'Change Password'}</ThemedText>
      </TouchableOpacity>

      <TouchableOpacity style={[styles.button, styles.cancelButton]} onPress={onCancel}>
        <ThemedText style={styles.cancelText}>Cancel</ThemedText>
      </TouchableOpacity>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 20, backgroundColor: '#f1f5f9', borderRadius: 12, margin: 16 },
  title: { marginBottom: 20, textAlign: 'center' },
  input: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    marginBottom: 16,
    color: '#0f172a'
  },
  button: {
    backgroundColor: '#0f172a',
    padding: 16,
    borderRadius: 10,
    alignItems: 'center',
    marginBottom: 10
  },
  buttonText: { color: '#fff', fontWeight: '800', fontSize: 15 },
  cancelButton: {
    backgroundColor: 'transparent',
    borderWidth: 1,
    borderColor: '#cbd5e1'
  },
  cancelText: { color: '#475569', fontWeight: '600' }
});
