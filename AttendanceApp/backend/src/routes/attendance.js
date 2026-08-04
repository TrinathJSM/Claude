const express = require('express');
const db = require('../db');
const { distanceMeters } = require('../utils/geo');
const { requireAuth } = require('../middleware/auth');

const router = express.Router();

function getActiveOffice() {
  return db.prepare('SELECT * FROM office_locations WHERE active = 1 ORDER BY id LIMIT 1').get();
}

function getLastEvent(employeeId) {
  return db
    .prepare(
      'SELECT * FROM attendance_events WHERE employee_id = ? ORDER BY id DESC LIMIT 1'
    )
    .get(employeeId);
}

// GET /api/attendance/status - last punch state + office geofence config (for the map/radius UI)
router.get('/status', requireAuth, (req, res) => {
  const office = getActiveOffice();
  const lastEvent = getLastEvent(req.user.sub);

  return res.json({
    office: office
      ? { name: office.name, lat: office.lat, lng: office.lng, radiusMeters: office.radius_meters }
      : null,
    lastEvent: lastEvent
      ? { type: lastEvent.type, timestamp: lastEvent.timestamp }
      : null,
    nextAction: !lastEvent || lastEvent.type === 'OUT' ? 'IN' : 'OUT',
  });
});

// POST /api/attendance/punch { lat, lng, accuracyMeters }
// Server re-validates the geofence — never trust the client's pass/fail claim.
router.post('/punch', requireAuth, (req, res) => {
  const { lat, lng, accuracyMeters } = req.body || {};

  if (typeof lat !== 'number' || typeof lng !== 'number') {
    return res.status(400).json({ error: 'lat and lng (numbers) are required' });
  }

  const office = getActiveOffice();
  if (!office) {
    return res.status(500).json({ error: 'No office location configured' });
  }

  const distance = distanceMeters(lat, lng, office.lat, office.lng);
  const withinFence = distance <= office.radius_meters;

  if (!withinFence) {
    return res.status(403).json({
      error: 'You must be within the office geofence to punch in/out',
      distanceMeters: Math.round(distance),
      radiusMeters: office.radius_meters,
    });
  }

  // Reasonable accuracy sanity check: reject wildly imprecise fixes that could
  // mask a mocked/incorrect location, but don't require lab-grade GPS.
  if (typeof accuracyMeters === 'number' && accuracyMeters > 100) {
    return res.status(422).json({
      error: 'GPS accuracy too low to verify your location, please try again outdoors',
      accuracyMeters,
    });
  }

  const lastEvent = getLastEvent(req.user.sub);
  const nextType = !lastEvent || lastEvent.type === 'OUT' ? 'IN' : 'OUT';

  const insert = db.prepare(
    `INSERT INTO attendance_events
      (employee_id, type, lat, lng, accuracy_meters, distance_from_office_meters, office_location_id)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  );
  const result = insert.run(
    req.user.sub,
    nextType,
    lat,
    lng,
    accuracyMeters ?? null,
    distance,
    office.id
  );

  const event = db.prepare('SELECT * FROM attendance_events WHERE id = ?').get(result.lastInsertRowid);

  return res.status(201).json({
    type: event.type,
    timestamp: event.timestamp,
    distanceMeters: Math.round(distance),
  });
});

// GET /api/attendance/history?limit=50 - the caller's own punch history
router.get('/history', requireAuth, (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 50, 200);
  const events = db
    .prepare(
      `SELECT type, timestamp, distance_from_office_meters AS distanceMeters
       FROM attendance_events
       WHERE employee_id = ?
       ORDER BY id DESC
       LIMIT ?`
    )
    .all(req.user.sub, limit);

  return res.json({ events });
});

module.exports = router;
