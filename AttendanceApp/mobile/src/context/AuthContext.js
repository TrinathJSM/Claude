import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import * as SecureStore from 'expo-secure-store';
import { login as apiLogin } from '../api/client';

const SESSION_KEY = 'jsm_attendance_session';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null); // { token, employee }
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const raw = await SecureStore.getItemAsync(SESSION_KEY);
        if (raw) setSession(JSON.parse(raw));
      } finally {
        setIsLoading(false);
      }
    })();
  }, []);

  const login = useCallback(async (employeeId, password) => {
    const data = await apiLogin(employeeId, password);
    await SecureStore.setItemAsync(SESSION_KEY, JSON.stringify(data));
    setSession(data);
    return data;
  }, []);

  const logout = useCallback(async () => {
    await SecureStore.deleteItemAsync(SESSION_KEY);
    setSession(null);
  }, []);

  return (
    <AuthContext.Provider value={{ session, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
