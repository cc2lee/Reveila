// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app\modal.tsx
import { Link } from 'expo-router';
import React from 'react';
import { StyleSheet, Text, View, useColorScheme } from 'react-native';

export default function ModalScreen() {
  const colorScheme = useColorScheme() ?? 'light';
  const isDark = colorScheme === 'dark';

  // Quick sandbox adaptive theme tokens
  const theme = {
    bg: isDark ? '#121212' : '#f8fafc',
    text: isDark ? '#ffffff' : '#0f172a',
    link: isDark ? '#3b82f6' : '#0284c7'
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.bg }]}>
      <Text style={[styles.title, { color: theme.text }]}>Sovereign Security Portal</Text>
      <Text style={[styles.body, { color: isDark ? '#94a3b8' : '#64748b' }]}>
        This context modal handles authorization prompts and multi-tenant sandboxing checks for your private data operations.
      </Text>
      
      <Link href="/" dismissTo style={styles.link}>
        <Text style={[styles.linkText, { color: theme.link }]}>Return to Home Dashboard</Text>
      </Link>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    gap: 8
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    marginBottom: 8,
  },
  body: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
    marginBottom: 16,
    paddingHorizontal: 10
  },
  link: {
    marginTop: 10,
    paddingVertical: 12,
    paddingHorizontal: 20,
  },
  linkText: {
    fontSize: 15,
    fontWeight: '600',
    textDecorationLine: 'underline'
  }
});