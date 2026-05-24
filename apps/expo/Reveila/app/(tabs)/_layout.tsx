// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app\(tabs)\_layout.tsx
import { Tabs } from 'expo-router';
import React from 'react';
import { useColorScheme } from 'react-native';

export default function TabLayout() {
  const colorScheme = useColorScheme() ?? 'light';

  // Define fallback enterprise active tints without relying on missing constants files
  const activeTintColor = colorScheme === 'dark' ? '#ffffff' : '#1a1a1a';

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: activeTintColor,
        // Hides the bottom navigation tab bar visually as requested to optimize screen space
        tabBarStyle: { display: 'none' }, 
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
        }}
      />
      <Tabs.Screen
        name="explore"
        options={{
          title: 'Explore',
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: 'Settings',
        }}
      />
    </Tabs>
  );
}