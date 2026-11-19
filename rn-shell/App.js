import React from 'react';
import { Platform, ScrollView, StyleSheet, Text, View } from 'react-native';

// S1 shell: no plan engine, no data, no network. It only proves the build chain and shows what it runs on.
var rn = (Platform.constants && Platform.constants.reactNativeVersion) || {};
var rnText = rn.major !== undefined ? rn.major + '.' + rn.minor + '.' + rn.patch : 'unknown';
var hermes = typeof global.HermesInternal !== 'undefined' && global.HermesInternal != null;
var started = new Date().toLocaleString('en-GB');

function Row(props) {
  return (
    <View style={styles.row}>
      <Text style={styles.label}>{props.label}</Text>
      <Text style={styles.value}>{props.value}</Text>
    </View>
  );
}

export default function App() {
  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>Slice S1: app shell</Text>
      <Text style={styles.note}>
        A throwaway test screen. It holds no plan engine, no runner data and no network access.
      </Text>
      <Row label="React Native" value={rnText} />
      <Row label="Hermes engine running" value={hermes ? 'yes' : 'no'} />
      <Row label="Platform" value={Platform.OS + ' ' + Platform.Version} />
      <Row label="Screen opened" value={started} />
    </ScrollView>
  );
}

var styles = StyleSheet.create({
  container: { padding: 24, paddingTop: 72, backgroundColor: '#ffffff', flexGrow: 1 },
  title: { fontSize: 24, fontWeight: '700', marginBottom: 12 },
  note: { fontSize: 15, color: '#444444', marginBottom: 24 },
  row: { marginBottom: 16 },
  label: { fontSize: 13, color: '#666666' },
  value: { fontSize: 18, fontWeight: '600' },
});
