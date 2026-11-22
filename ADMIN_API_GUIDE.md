# GoTogether Admin API additions

All endpoints below require a valid ADMIN JWT. Existing `/api/admin/auth/login` and driver verification endpoints are unchanged.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/admin/overview` | Counts for users, drivers, rides, bookings and simulated booking value |
| GET | `/api/admin/users?search=` | Safe user directory (no password/hash fields) |
| GET | `/api/admin/rides?status=&search=` | Ride list with driver, route, departure, seat and GPS status |
| GET | `/api/admin/bookings?status=&search=` | Booking list with passenger, ride, driver and payment metadata |

These endpoints read existing entities and do not add database tables or mutate booking/ride/user state.

`simulatedBookingValue` includes booking amounts whose payment state is `SIMULATED_PAID` or `SETTLEMENT_PENDING`. It is not actual revenue: the current booking service explicitly simulates payment.

## CORS

`SecurityConfig` reads comma-separated `APP_CORS_ALLOWED_ORIGINS`. For local web development, if the variable is not set, localhost ports 5173 are allowed. For a deployed admin web frontend, set the variable in the backend hosting environment to the exact trusted origin(s), e.g.:

```text
APP_CORS_ALLOWED_ORIGINS=https://your-admin-domain.example
```

Do not use `*` for production.
