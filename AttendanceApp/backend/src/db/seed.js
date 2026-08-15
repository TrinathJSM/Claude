require('dotenv').config();
const bcrypt = require('bcryptjs');
const db = require('./index');

function seed() {
  const officeCount = db.prepare('SELECT COUNT(*) AS n FROM office_locations').get().n;
  if (officeCount === 0) {
    db.prepare(
      `INSERT INTO office_locations (name, lat, lng, radius_meters, active)
       VALUES (?, ?, ?, ?, 1)`
    ).run(
      'JSM Composites Head Office',
      Number(process.env.OFFICE_LAT || 19.060146260998735),
      Number(process.env.OFFICE_LNG || 73.02593298023692),
      Number(process.env.OFFICE_RADIUS_METERS || 150)
    );
    console.log('Seeded office location.');
  }

  const adminEmployeeId = process.env.ADMIN_EMPLOYEE_ID || 'ADMIN001';
  const existingAdmin = db
    .prepare('SELECT id FROM employees WHERE employee_id = ?')
    .get(adminEmployeeId);

  if (!existingAdmin) {
    const passwordHash = bcrypt.hashSync(process.env.ADMIN_PASSWORD || 'ChangeMe123!', 10);
    db.prepare(
      `INSERT INTO employees (employee_id, name, password_hash, role, active)
       VALUES (?, ?, ?, 'admin', 1)`
    ).run(adminEmployeeId, process.env.ADMIN_NAME || 'Administrator', passwordHash);
    console.log(`Seeded admin account: ${adminEmployeeId}`);
  } else {
    console.log('Admin account already exists, skipping.');
  }
}

seed();
