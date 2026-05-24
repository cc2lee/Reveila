// C:\IDE\Projects\Reveila-Suite\apps\expo\Reveila\app\_layout.tsx
import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import { Stack } from 'expo-router';
import React from 'react';
import { useColorScheme } from 'react-native';

// Ensures deep-linking or direct reloads anchor back onto your core tabs structural flow
export const unstable_settings = {
  anchor: '(tabs)',
};

export default function RootLayout() {
  // Use native color scheme evaluation to remove the missing @/ hooks alias dependency
  const colorScheme = useColorScheme();

  return (
    <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
      <Stack>
        {/* Mounts your active tab group and hides the header wrapper */}
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        
        {/* Declares the slide-up modal overlay screen configurations */}
        <Stack.Screen name="modal" options={{ presentation: 'modal', title: 'System Message' }} />
      </Stack>
    </ThemeProvider>
  );
}