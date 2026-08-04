const express = require('express');
const bcrypt = require('bcryptjs');
const db = require('../db');
const { requireAuth, requireAdmin } = require('../middleware/auth');

const router = express.Router();
router.use(requireAuth, requireAdmin);

// GET /api/admin/employees
router.get('/employees', (req, res) => {
  const employees = db
    .prepare('SELECT id, employee_id AS employeeId, name, role, active FROM employees ORDER BY name')
    .all();
  return res.json({ employees });
});

// POST /api/admin/employees { employeeId, name, password, role }
router.post('/employees', (req, res) => {
  const { employeeId, name, password, role } = req.body || {};

  if (!employeeId || !name || !password) {
    return res.status(400).json({ error: 'employeeId, name and password are required' });
  }

  const existing = db.prepare('SELECT id FROM employees WHERE employee_id = ?').get(employeeId);
  if (existing) {
    return res.status(409).json({ error: 'employeeId already exists' });
  }

  const passwordHash = bcrypt.hashSync(password, 10);
  const result = db
    .prepare(
      `INSERT INTO employees (employee_id, name, password_hash, role, active)
       VALUES (?, ?, ?, ?, 1)`
    )
    .run(employeeId, name, passwordHash, role === 'admin' ? 'admin' : 'employee');

  return res.status(201).json({ id: result.lastInsertRowid, employeeId, name });
});

// PATCH /api/admin/employees/:id { active }
router.patch('/employees/:id', (req, res) => {
  const { active } = req.body || {};
  if (typeof active !== 'boolean') {
    return res.status(400).json({ error: 'active (boolean) is required' });
  }
  db.prepare('UPDATE employees SET active = ? WHERE id = ?').run(active ? 1 : 0, req.params.id);
  return res.json({ ok: true });
});

// GET /api/admin/office-location
router.get('/office-location', (req, res) => {
  const office = db
    .prepare('SELECT * FROM office_locations WHERE active = 1 ORDER BY id LIMIT 1')
    .get();
  return res.json({ office });
});

// PUT /api/admin/office-location { name, lat, lng, radiusMeters }
router.put('/office-location', (req, res) => {
  const { name, lat, lng, radiusMeters } = req.body || {};
  if (!name || typeof lat !== 'number' || typeof lng !== 'number' || typeof radiusMeters !== 'number') {
    return res.status(400).json({ error: 'name, lat, lng, radiusMeters are required' });
  }

  db.prepare('UPDATE office_locations SET active = 0 WHERE active = 1').run();
  const result = db
    .prepare(
      `INSERT INTO office_locations (name, lat, lng, radius_meters, active)
       VALUES (?, ?, ?, ?, 1)`
    )
    .run(name, lat, lng, radiusMeters);

  return res.status(201).json({ id: result.lastInsertRowid, name, lat, lng, radiusMeters });
});

module.exports = router;
