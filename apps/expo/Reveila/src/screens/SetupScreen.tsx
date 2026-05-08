import React, { useState } from 'react';
import { View, Text, TextInput, Button, StyleSheet } from 'react-native';
import { useReveila } from '../hooks/useReveila';

export const SetupScreen = () => {
    const { invoke, checkStatus } = useReveila();
    const [configPath, setConfigPath] = useState('/sdcard/Reveila');

    const handleSave = async () => {
        // Instead of a Kotlin Activity saving this, the JS Bridge does it
        await invoke('ReveilaModule', 'saveConfig', [configPath]);
        alert('Config Saved & Engine Notified');
        checkStatus();
    };

    return (
        <View style={styles.container}>
            <Text style={styles.label}>Reveila Core Path:</Text>
            <TextInput 
                style={styles.input} 
                value={configPath} 
                onChangeText={setConfigPath} 
            />
            <Button title="Initialize Engine" onPress={handleSave} />
        </View>
    );
};

const styles = StyleSheet.create({
    container: { padding: 20, flex: 1, justifyContent: 'center' },
    label: { fontWeight: 'bold', marginBottom: 5 },
    input: { borderBottomWidth: 1, marginBottom: 20, padding: 5 }
});