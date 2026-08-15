# JSM Composites Attendance App

A mobile app for JSM Composites employees to punch in and punch out, with
geofencing that only allows a punch when the employee is physically at the
office.

The geofence is enforced **twice**: the phone checks it live to gray out the
punch button, and the server independently re-checks the submitted GPS
coordinates against the office location before writing an attendance record.
The server check is the one that counts — a modified or GPS-spoofed client
cannot bypass it, since every punch is re-validated with the coordinates and
accuracy the phone actually reported.

## Structure

```
AttendanceApp/
  backend/   Node.js + Express + SQLite API (auth, punch, history, admin)
  mobile/    Expo (React Native) app for employees
```

## How geofencing works

1. On the Punch screen, the app watches the device's GPS position
   (`expo-location`) and computes the distance to the office coordinates.
2. The Punch In / Punch Out button is disabled whenever the employee is
   outside the configured radius.
3. When pressed, the app sends the current lat/lng (and GPS accuracy) to
   `POST /api/attendance/punch`.
4. The server recomputes the distance to the office itself (Haversine
   formula) and rejects the request with `403` if it's outside the radius,
   or `422` if the GPS fix is too imprecise (>100m accuracy) to trust.
   Only a request that passes both checks is written to the attendance log.

## Backend setup

```bash
cd backend
npm install
cp .env.example .env
# Edit .env: set OFFICE_LAT/OFFICE_LNG to JSM Composites' real office
# coordinates, OFFICE_RADIUS_METERS to the allowed radius, and a real
# JWT_SECRET.
npm run seed   # creates the office location + one admin account
npm start      # listens on PORT (default 4000)
```

Admin API (requires the seeded admin's JWT) to manage employees and the
office location:

- `GET/POST /api/admin/employees` — list / create employee logins
- `PATCH /api/admin/employees/:id` — activate/deactivate an employee
- `GET/PUT /api/admin/office-location` — view / update the geofence center + radius

Employee-facing API:

- `POST /api/auth/login` — `{ employeeId, password }` → JWT
- `GET /api/attendance/status` — office config, last punch, next expected action
- `POST /api/attendance/punch` — `{ lat, lng, accuracyMeters }`, geofence-checked
- `GET /api/attendance/history` — the caller's own punch history

## Mobile app setup

```bash
cd mobile
npm install
# Point the app at your backend. For a phone on the same Wi-Fi as your
# dev machine, use its LAN IP, not localhost:
EXPO_PUBLIC_API_BASE_URL=http://192.168.1.20:4000 npx expo start
```

Scan the QR code with Expo Go (Android/iOS) to run it on a device — GPS-based
geofencing needs a real device, not a simulator without location support.

The app asks for location permission ("when in use") on first launch, so it
can confirm the employee is at the office at the moment they punch in or out.
No background location tracking is used — location is only read when the
Punch screen is open.

### Screens

- **Login** — employee ID + password issued by an admin
- **Punch** — shows live distance from the office and a Punch In/Out button
  that's only enabled inside the geofence
- **History** — the employee's own punch in/out log

## Deploying for real use

- Host `backend/` anywhere that runs Node (Render, Fly.io, a small VM, etc.)
  and set real values for `JWT_SECRET`, `OFFICE_LAT`/`OFFICE_LNG`,
  `OFFICE_RADIUS_METERS`, and the admin seed credentials via environment
  variables — swap SQLite for a managed Postgres if you need multi-instance
  scaling.
- Build the mobile app for distribution with `eas build` (Expo Application
  Services) once `EXPO_PUBLIC_API_BASE_URL` points at the deployed backend.
- Create employee accounts via the admin API rather than the seed script.
