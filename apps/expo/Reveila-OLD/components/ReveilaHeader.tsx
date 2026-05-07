import React from 'react';
import { StyleSheet, View } from 'react-native';
import { ThemedText } from './themed-text';
import { ThemedView } from './themed-view';

interface Props {
  subtitle?: string;
  color?: string;
  children?: React.ReactNode;
}

export function ReveilaHeader({ subtitle, color = '#ff6600', children }: Props) {
  return (
    <ThemedView style={styles.header}>
      <View style={styles.headerRow}>
        <ThemedText type="title">
          <ThemedText style={{ color }}>REVEILA</ThemedText>
          {subtitle ? ` ${subtitle}` : ''}
        </ThemedText>
        {children}
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  header: { 
    backgroundColor: '#0f172a', 
    paddingTop: 50, 
    paddingBottom: 15, 
    paddingHorizontal: 20 
  },
  headerRow: { 
    flexDirection: 'row', 
    justifyContent: 'space-between', 
    alignItems: 'center' 
  }
});
