## DR-06–10 — Account, Authentication and Vehicle Profile v1.0

### Account lifecycle

`PENDING_VERIFICATION → ACTIVE → SUSPENDED → DELETION_PENDING → DELETED`

Temporary security lockouts are tracked separately. Only `ACTIVE` accounts may create bookings or sessions.

### DR-06 — Registration and email verification

1. Driver submits email, password and required policy consent.
2. Identity system creates an unverified account.
3. A single-use, expiring verification link is emailed.
4. Successful verification activates the account.

Rules:

- Public driver self-registration is allowed.
- Duplicate-email and recovery responses must not reveal whether an account exists.
- Verification links are single-use and resendable with rate limits.
- Essential consent records include policy version and timestamp.
- Social login and passkeys are deferred but supported by the identity design.

### DR-07 — Sign-in, sign-out and recovery

- Use OpenID Connect with Authorization Code + PKCE.
- Credentials are handled by a dedicated identity provider, not EV microservices.
- Logout revokes the current application session.
- “Logout all devices” revokes all sessions.
- Password recovery uses an expiring, single-use email link.
- Sensitive changes require recent reauthentication.
- Operators and administrators require MFA; drivers may enable it voluntarily.

This follows current OAuth guidance: PKCE is used for browser clients, implicit flow is avoided, and passwords are not sent directly to application services. ([rfc-editor.org](https://www.rfc-editor.org/info/rfc9700/?utm_source=openai))

### DR-08/09 — Profile, vehicles and compatibility

Driver profile:

- Display name
- Email
- Preferred language
- Notification preferences
- Time-zone/display preferences

Saved vehicle:

- User-defined nickname
- Make/model/year, optional
- Supported connector types
- Maximum AC/DC charging power
- Battery capacity, optional
- Default vehicle designation

VIN, registration plate and precise home location are excluded because they are unnecessary for booking.

A driver may book without saving a vehicle by selecting connector requirements manually. Compatibility assists discovery but does not guarantee successful physical charging.

### DR-10 — Session management

Drivers can view and revoke active login sessions showing:

- Device/browser description
- Approximate sign-in time
- Last activity
- Current-session indicator

Session identifiers and tokens must never be exposed. Sessions use secure expiry, rotation and revocation controls. ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html?utm_source=openai))

### Security and acceptance rules

- Generic authentication errors prevent account enumeration.
- Login, recovery and verification endpoints are rate-limited.
- No periodic forced password changes without evidence of compromise. ([cheatsheetseries.owasp.org](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html?utm_source=openai))
- Suspended or deleted accounts cannot authenticate or book.
- Account deletion revokes sessions and queues personal-data anonymization.
- Active bookings/sessions must first be resolved.
- Authentication events and privileged profile changes are audited.

Next: **Operator use-case catalogue and organization/staff model**.