import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  Pressable,
  StyleSheet,
  KeyboardAvoidingView,
  Platform,
  ActivityIndicator,
} from 'react-native';
import { useAuth } from '../context/AuthContext';

export default function LoginScreen() {
  const { login } = useAuth();
  const [employeeId, setEmployeeId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async () => {
    if (!employeeId || !password) {
      setError('Enter your employee ID and password.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await login(employeeId.trim(), password);
    } catch (err) {
      setError(err.message || 'Login failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <Text style={styles.title}>JSM Composites</Text>
      <Text style={styles.subtitle}>Employee Attendance</Text>

      <View style={styles.form}>
        <Text style={styles.label}>Employee ID</Text>
        <TextInput
          style={styles.input}
          value={employeeId}
          onChangeText={setEmployeeId}
          autoCapitalize="characters"
          autoCorrect={false}
          placeholder="e.g. EMP0123"
        />

        <Text style={styles.label}>Password</Text>
        <TextInput
          style={styles.input}
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          autoCapitalize="none"
          placeholder="••••••••"
        />

        {error ? <Text style={styles.error}>{error}</Text> : null}

        <Pressable
          style={[styles.button, submitting && styles.buttonDisabled]}
          onPress={onSubmit}
          disabled={submitting}
        >
          {submitting ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <Text style={styles.buttonText}>Log In</Text>
          )}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', padding: 24, backgroundColor: '#fff' },
  title: { fontSize: 28, fontWeight: '700', textAlign: 'center', color: '#0f172a' },
  subtitle: { fontSize: 15, textAlign: 'center', color: '#64748b', marginTop: 4, marginBottom: 32 },
  form: { gap: 4 },
  label: { fontSize: 13, fontWeight: '600', color: '#334155', marginTop: 12, marginBottom: 6 },
  input: {
    borderWidth: 1,
    borderColor: '#cbd5e1',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
  },
  error: { color: '#dc2626', marginTop: 12, fontSize: 14 },
  button: {
    marginTop: 24,
    backgroundColor: '#1d4ed8',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
});
