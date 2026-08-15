import React, { useCallback, useEffect, useState } from 'react';
import { View, Text, FlatList, StyleSheet, ActivityIndicator, RefreshControl } from 'react-native';
import { useAuth } from '../context/AuthContext';
import { getHistory } from '../api/client';

function formatTimestamp(ts) {
  return new Date(ts.replace(' ', 'T') + 'Z').toLocaleString();
}

export default function HistoryScreen() {
  const { session } = useAuth();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
      const data = await getHistory(session.token);
      setEvents(data.events);
      setError(null);
    } catch (err) {
      setError(err.message);
    }
  }, [session.token]);

  useEffect(() => {
    load().finally(() => setLoading(false));
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#1d4ed8" />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <FlatList
        data={events}
        keyExtractor={(item, index) => `${item.timestamp}-${index}`}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        contentContainerStyle={events.length === 0 && styles.emptyContainer}
        ListEmptyComponent={<Text style={styles.emptyText}>No attendance records yet.</Text>}
        renderItem={({ item }) => (
          <View style={styles.row}>
            <View style={[styles.badge, item.type === 'IN' ? styles.badgeIn : styles.badgeOut]}>
              <Text style={styles.badgeText}>{item.type}</Text>
            </View>
            <View style={styles.rowText}>
              <Text style={styles.rowTime}>{formatTimestamp(item.timestamp)}</Text>
              <Text style={styles.rowDistance}>{Math.round(item.distanceMeters)} m from office</Text>
            </View>
          </View>
        )}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', paddingTop: 60, paddingHorizontal: 16 },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  error: { color: '#dc2626', marginBottom: 12 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: '#e2e8f0',
  },
  badge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6, marginRight: 14 },
  badgeIn: { backgroundColor: '#dcfce7' },
  badgeOut: { backgroundColor: '#fee2e2' },
  badgeText: { fontWeight: '700', fontSize: 12, color: '#0f172a' },
  rowText: { flex: 1 },
  rowTime: { fontSize: 15, color: '#0f172a', fontWeight: '600' },
  rowDistance: { fontSize: 12, color: '#64748b', marginTop: 2 },
  emptyContainer: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  emptyText: { color: '#64748b' },
});
