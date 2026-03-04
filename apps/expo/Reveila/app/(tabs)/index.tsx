import { StyleSheet, TouchableOpacity, ScrollView, View } from 'react-native';
import { useEffect, useState } from 'react';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import ReveilaModule from '@/modules/reveila';

export default function HomeScreen() {
  const [isRunning, setIsRunning] = useState(false);
  const [isStarting, setIsStarting] = useState(false);
  const [autoRestart, setAutoRestart] = useState(false);
  const [logs, setLogs] = useState<any[]>([]);

  useEffect(() => {
    const checkStatus = () => {
      try {
        const running = ReveilaModule.isRunning();
        setIsRunning(running);
        
        if (running) {
          setIsStarting(false);
        }

        // WATCHDOG LOGIC: Only restart if not already starting and watchdog is on
        if (autoRestart && !running && !isStarting) {
          console.log('Watchdog: Service died, restarting...');
          handleStart();
        }
      } catch (e) {
        // Module might not be available yet
      }
    };
    checkStatus();
    const interval = setInterval(checkStatus, 5000); // Slower interval
    return () => clearInterval(interval);
  }, [autoRestart, isStarting]);

  const handleStart = async () => {
    if (isStarting) return;
    setIsStarting(true);
    try {
      // Allow overriding system home for development if needed.
      // The path provided by the user is a Windows path, which only works if bridged (e.g. on certain emulators/debug setups)
      // otherwise it falls back to internal storage.
      const customPath = "C:\\IDE\\Projects\\Reveila-Suite\\apps\\expo\\Reveila\\assets\\reveila\\system";
      await ReveilaModule.startService(customPath);
      setAutoRestart(true); // Enable watchdog once started manually
    } catch (e) {
      console.error(e);
      setIsStarting(false);
    }
  };

  const handleFetchLogs = async () => {
    if (!isRunning || isStarting) return;
    try {
      const payload = JSON.stringify({
        componentName: 'DataService',
        methodName: 'search',
        methodArguments: [{
          entityType: 'AuditLog',
          page: 0,
          size: 5
        }]
      });
      const result = await ReveilaModule.invoke(payload);
      const parsed = JSON.parse(result);
      if (parsed && parsed.content) {
        setLogs(parsed.content);
      }
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <ThemedView style={styles.container}>
      <ThemedView style={styles.header}>
        <ThemedText type="title">
          <ThemedText style={{ color: '#ff6600' }}>REVEILA</ThemedText> Control Center
        </ThemedText>
      </ThemedView>

      <ScrollView contentContainerStyle={styles.content}>
        <ThemedView style={styles.section}>
          <ThemedText type="subtitle">Agent Sovereignty</ThemedText>
          <ThemedText style={styles.description}>Instant perimeter enforcement and hardware-level control</ThemedText>
          
          <ThemedView style={styles.card}>
            <ThemedView style={styles.statusRow}>
              <ThemedText>System Status: </ThemedText>
              <ThemedText style={{ 
                color: isStarting ? '#eab308' : (isRunning ? '#22c55e' : '#ef4444'), 
                fontWeight: 'bold' 
              }}>
                {isStarting ? 'STARTING...' : (isRunning ? 'RUNNING' : 'STOPPED')}
              </ThemedText>
            </ThemedView>

            <ThemedView style={styles.statusRow}>
              <ThemedText>Watchdog Mode: </ThemedText>
              <ThemedText style={{ color: autoRestart ? '#3b82f6' : '#64748b', fontWeight: 'bold' }}>
                {autoRestart ? 'ACTIVE' : 'INACTIVE'}
              </ThemedText>
            </ThemedView>
            
            {!isRunning && !isStarting && (
              <TouchableOpacity style={styles.button} onPress={handleStart}>
                <ThemedText style={styles.buttonText}>Start Reveila Engine</ThemedText>
              </TouchableOpacity>
            )}

            {isStarting && (
              <ThemedText style={styles.startingText}>
                Engine is initializing background components. This may take a few seconds...
              </ThemedText>
            )}
            
            {isRunning && (
              <TouchableOpacity 
                style={[styles.button, { backgroundColor: autoRestart ? '#334155' : '#0f172a' }]} 
                onPress={() => setAutoRestart(!autoRestart)}
              >
                <ThemedText style={styles.buttonText}>
                  {autoRestart ? 'Disable Watchdog' : 'Enable Watchdog'}
                </ThemedText>
              </TouchableOpacity>
            )}
          </ThemedView>
        </ThemedView>

        <ThemedView style={styles.section}>
          <ThemedView style={styles.sectionHeader}>
            <ThemedView style={{backgroundColor: 'transparent'}}>
              <ThemedText type="subtitle">Flight Recorder</ThemedText>
              <ThemedText style={styles.description}>Real-time audit logs of fabric invocations</ThemedText>
            </ThemedView>
            <TouchableOpacity onPress={handleFetchLogs} disabled={!isRunning}>
              <ThemedText style={{ color: isRunning ? '#3b82f6' : '#94a3b8' }}>Refresh</ThemedText>
            </TouchableOpacity>
          </ThemedView>

          <ThemedView style={styles.tableCard}>
            {logs.length === 0 ? (
              <ThemedText style={styles.emptyText}>No logs available. Start engine and refresh.</ThemedText>
            ) : (
              logs.map((log, i) => (
                <ThemedView key={i} style={[styles.tableRow, i === logs.length - 1 && { borderBottomWidth: 0 }]}>
                  <ThemedText style={styles.logAction}>{log.attributes?.action || 'Unknown Action'}</ThemedText>
                  <ThemedText style={styles.logTime}>{new Date().toLocaleTimeString()}</ThemedText>
                </ThemedView>
              ))
            )}
          </ThemedView>
        </ThemedView>
      </ScrollView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f1f5f9',
  },
  header: {
    backgroundColor: '#0f172a',
    paddingTop: 60,
    paddingBottom: 20,
    paddingHorizontal: 20,
  },
  content: {
    padding: 20,
    gap: 20,
  },
  section: {
    backgroundColor: 'transparent',
    gap: 8,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    backgroundColor: 'transparent',
  },
  description: {
    fontSize: 14,
    color: '#64748b',
    marginBottom: 8,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
  },
  tableCard: {
    backgroundColor: '#fff',
    borderRadius: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
    overflow: 'hidden',
  },
  statusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
    backgroundColor: 'transparent',
  },
  button: {
    backgroundColor: '#0f172a',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  buttonText: {
    color: '#fff',
    fontWeight: 'bold',
  },
  startingText: {
    fontSize: 12,
    color: '#eab308',
    fontStyle: 'italic',
    marginTop: 8,
  },
  tableRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
    backgroundColor: 'transparent',
  },
  logAction: {
    fontWeight: '600',
    color: '#1e293b',
  },
  logTime: {
    fontSize: 12,
    color: '#94a3b8',
  },
  emptyText: {
    padding: 20,
    textAlign: 'center',
    color: '#94a3b8',
  }
});
