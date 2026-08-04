const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const db = require('../db');

const router = express.Router();

router.post('/login', (req, res) => {
  const { employeeId, password } = req.body || {};

  if (!employeeId || !password) {
    return res.status(400).json({ error: 'employeeId and password are required' });
  }

  const employee = db
    .prepare('SELECT * FROM employees WHERE employee_id = ? AND active = 1')
    .get(employeeId);

  if (!employee || !bcrypt.compareSync(password, employee.password_hash)) {
    return res.status(401).json({ error: 'Invalid employee ID or password' });
  }

  const token = jwt.sign(
    { sub: employee.id, employeeId: employee.employee_id, role: employee.role },
    process.env.JWT_SECRET,
    { expiresIn: '12h' }
  );

  return res.json({
    token,
    employee: {
      id: employee.id,
      employeeId: employee.employee_id,
      name: employee.name,
      role: employee.role,
    },
  });
});

module.exports = router;
