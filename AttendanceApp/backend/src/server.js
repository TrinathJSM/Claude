require('dotenv').config();
const express = require('express');
const cors = require('cors');
const authRoutes = require('./routes/auth');
const attendanceRoutes = require('./routes/attendance');
const adminRoutes = require('./routes/admin');

if (!process.env.JWT_SECRET) {
  console.error('JWT_SECRET is not set. Copy .env.example to .env and configure it.');
  process.exit(1);
}

const app = express();
app.use(cors());
app.use(express.json());

app.get('/api/health', (req, res) => res.json({ ok: true }));
app.use('/api/auth', authRoutes);
app.use('/api/attendance', attendanceRoutes);
app.use('/api/admin', adminRoutes);

app.use((req, res) => res.status(404).json({ error: 'Not found' }));

const PORT = process.env.PORT || 4000;
app.listen(PORT, () => {
  console.log(`JSM Attendance API listening on port ${PORT}`);
});
