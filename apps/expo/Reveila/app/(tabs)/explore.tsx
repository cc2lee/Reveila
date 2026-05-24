// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app\(tabs)\explore.tsx
import React from 'react';
import { ScrollView, StyleSheet, Text, View, useColorScheme } from 'react-native';

export default function TabTwoScreen() {
  const colorScheme = useColorScheme() ?? 'light';
  const isDark = colorScheme === 'dark';

  // Dynamic style adaptation for basic dark/light modes
  const themeStyles = {
    backgroundColor: isDark ? '#121212' : '#ffffff',
    textColor: isDark ? '#ffffff' : '#1a1a1a',
    subTextColor: isDark ? '#a0a0a0' : '#666666',
    cardBackground: isDark ? '#1e1e1e' : '#f5f5f5',
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: themeStyles.backgroundColor }]}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: themeStyles.textColor }]}>Explore Environment</Text>
      </View>

      <View style={styles.content}>
        <Text style={[styles.description, { color: themeStyles.subTextColor }]}>
          Reveila-Suite runtime client is functional. This screen displays current sandbox configurations.
        </Text>

        {/* Feature Context Card 1 */}
        <View style={[styles.card, { backgroundColor: themeStyles.cardBackground }]}>
          <Text style={[styles.cardTitle, { color: themeStyles.textColor }]}>File-Based Runtime</Text>
          <Text style={[styles.cardBody, { color: themeStyles.subTextColor }]}>
            Active routes are bound directly to the file hierarchy. The entry interface maps cleanly onto <Text style={styles.bold}>app/(tabs)/index.tsx</Text>.
          </Text>
        </View>

        {/* Feature Context Card 2 */}
        <View style={[styles.card, { backgroundColor: themeStyles.cardBackground }]}>
          <Text style={[styles.cardTitle, { color: themeStyles.textColor }]}>Cross-Platform Architecture</Text>
          <Text style={[styles.cardBody, { color: themeStyles.subTextColor }]}>
            The JavaScript communication layer translates actions directly down into your modular native architecture binary.
          </Text>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingTop: 60,
    paddingHorizontal: 24,
    paddingBottom: 20,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
  },
  content: {
    paddingHorizontal: 24,
    gap: 20,
  },
  description: {
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 10,
  },
  card: {
    padding: 20,
    borderRadius: 12,
    gap: 8,
  },
  cardTitle: {
    fontSize: 16,
    fontWeight: '600',
  },
  cardBody: {
    fontSize: 14,
    lineHeight: 20,
  },
  bold: {
    fontWeight: '600',
  },
});