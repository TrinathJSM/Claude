import React, { useCallback, useEffect, useRef, useState } from 'react';
import { View, Text, Pressable, StyleSheet, ActivityIndicator, Alert } from 'react-native';
import * as Location from 'expo-location';
import { useAuth } from '../context/AuthContext';
import { getStatus, punch, ApiError } from '../api/client';
import { distanceMeters } from '../utils/geo';

const LOCATION_OPTIONS = {
  accuracy: Location.Accuracy.High,
  distanceInterval: 5,
  timeInterval: 4000,
};

export default function PunchScreen() {
  const { session, logout } = useAuth();
  const [permissionStatus, setPermissionStatus] = useState('checking');
  const [coords, setCoords] = useState(null);
  const [office, setOffice] = useState(null);
  const [nextAction, setNextAction] = useState('IN');
  const [lastEvent, setLastEvent] = useState(null);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [punching, setPunching] = useState(false);
  const [error, setError] = useState(null);
  const watchSubscription = useRef(null);

  const loadStatus = useCallback(async () => {
    try {
      const data = await getStatus(session.token);
      setOffice(data.office);
      setNextAction(data.nextAction);
      setLastEvent(data.lastEvent);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoadingStatus(false);
    }
  }, [session.token]);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  useEffect(() => {
    let isMounted = true;

    (async () => {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (!isMounted) return;
      setPermissionStatus(status);

      if (status === 'granted') {
        watchSubscription.current = await Location.watchPositionAsync(LOCATION_OPTIONS, (loc) => {
          setCoords(loc.coords);
        });
      }
    })();

    return () => {
      isMounted = false;
      watchSubscription.current?.remove();
    };
  }, []);

  const distance =
    coords && office ? distanceMeters(coords.latitude, coords.longitude, office.lat, office.lng) : null;
  const withinFence = distance != null && office ? distance <= office.radiusMeters : false;

  const onPunch = async () => {
    if (!coords) return;
    setPunching(true);
    setError(null);
    try {
      const result = await punch(session.token, {
        lat: coords.latitude,
        lng: coords.longitude,
        accuracyMeters: coords.accuracy ?? undefined,
      });
      setLastEvent({ type: result.type, timestamp: result.timestamp });
      setNextAction(result.type === 'IN' ? 'OUT' : 'IN');
      Alert.alert(
        result.type === 'IN' ? 'Punched In' : 'Punched Out',
        `Recorded at ${new Date(result.timestamp.replace(' ', 'T') + 'Z').toLocaleTimeString()}`
      );
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        Alert.alert('Session expired', 'Please log in again.');
        logout();
        return;
      }
      setError(err.message);
    } finally {
      setPunching(false);
    }
  };

  if (permissionStatus === 'checking' || loadingStatus) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#1d4ed8" />
      </View>
    );
  }

  if (permissionStatus !== 'granted') {
    return (
      <View style={styles.center}>
        <Text style={styles.title}>Location permission required</Text>
        <Text style={styles.subtitle}>
          JSM Attendance needs location access to verify you are physically at the office before
          you can punch in or out. Please enable location permissions in your device settings.
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.employeeName}>{session.employee.name}</Text>
      <Text style={styles.employeeId}>{session.employee.employeeId}</Text>

      <View style={[styles.statusCard, withinFence ? styles.statusOk : styles.statusBad]}>
        <Text style={styles.statusHeading}>
          {coords == null
            ? 'Locating…'
            : withinFence
            ? 'You are at the office'
            : 'Outside office geofence'}
        </Text>
        {distance != null && office && (
          <Text style={styles.statusDetail}>
            {Math.round(distance)} m from {office.name} (allowed radius {office.radiusMeters} m)
          </Text>
        )}
      </View>

      {lastEvent && (
        <Text style={styles.lastEvent}>
          Last punch: {lastEvent.type} at{' '}
          {new Date(lastEvent.timestamp.replace(' ', 'T') + 'Z').toLocaleString()}
        </Text>
      )}

      {error ? <Text style={styles.error}>{error}</Text> : null}

      <Pressable
        style={[
          styles.punchButton,
          nextAction === 'IN' ? styles.punchIn : styles.punchOut,
          (!withinFence || punching || !coords) && styles.punchDisabled,
        ]}
        disabled={!withinFence || punching || !coords}
        onPress={onPunch}
      >
        {punching ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.punchButtonText}>
            {nextAction === 'IN' ? 'Punch In' : 'Punch Out'}
          </Text>
        )}
      </Pressable>

      <Pressable style={styles.logoutLink} onPress={logout}>
        <Text style={styles.logoutText}>Log out</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, backgroundColor: '#fff', paddingTop: 60 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  title: { fontSize: 18, fontWeight: '700', marginBottom: 8, textAlign: 'center' },
  subtitle: { fontSize: 14, color: '#64748b', textAlign: 'center' },
  employeeName: { fontSize: 22, fontWeight: '700', color: '#0f172a' },
  employeeId: { fontSize: 14, color: '#64748b', marginTop: 2, marginBottom: 24 },
  statusCard: { borderRadius: 14, padding: 18, marginBottom: 16 },
  statusOk: { backgroundColor: '#dcfce7' },
  statusBad: { backgroundColor: '#fee2e2' },
  statusHeading: { fontSize: 17, fontWeight: '700', color: '#0f172a' },
  statusDetail: { fontSize: 13, color: '#475569', marginTop: 4 },
  lastEvent: { fontSize: 13, color: '#64748b', marginBottom: 16 },
  error: { color: '#dc2626', marginBottom: 12 },
  punchButton: { borderRadius: 12, paddingVertical: 18, alignItems: 'center', marginTop: 'auto' },
  punchIn: { backgroundColor: '#16a34a' },
  punchOut: { backgroundColor: '#dc2626' },
  punchDisabled: { opacity: 0.4 },
  punchButtonText: { color: '#fff', fontSize: 18, fontWeight: '700' },
  logoutLink: { marginTop: 20, alignItems: 'center' },
  logoutText: { color: '#64748b', fontSize: 14 },
});
