import { useState } from 'react';
import { TextInput, Button, StyleSheet, Platform, ScrollView, Text } from 'react-native';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import ReveilaExpoModule from '../../modules/my-java-module';

export default function HomeScreen() {
  const [inputValue, setInputValue] = useState('{"componentName":"system.echo","methodName":"echo","methodArguments":["Hello from UI"]}');
  const [result, setResult] = useState('');

  const handleInvoke = async () => {
    try {
      const response = await ReveilaExpoModule.invokeAsync(inputValue);
      setResult(response);
    } catch (e) {
      setResult(e.message);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedText type="title">Reveila Invoke</ThemedText>
      <TextInput
        style={styles.input}
        onChangeText={setInputValue}
        value={inputValue}
        placeholder="Enter JSON payload"
        multiline={true}
      />
      <Button
        title="Invoke"
        onPress={handleInvoke}
      />
      <ThemedText type="subtitle">Result:</ThemedText>
      <ScrollView style={styles.resultScrollView}>
        <Text style={styles.resultText}>{result}</Text>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 16,
  },
  input: {
    height: 100,
    margin: 12,
    borderWidth: 1,
    padding: 10,
    width: '100%',
    textAlignVertical: 'top',
  },
  resultScrollView: {
    flex: 1,
    width: '100%',
    borderWidth: 1,
    padding: 10,
    marginTop: 8,
  },
  resultText: {
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
  }
});