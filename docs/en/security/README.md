# Security

## Authentication

Authentication is based on an HttpOnly cookie named `access_token`. User creation and login create a JWT and set the cookie. Logout clears the cookie. The `GET /user` endpoint decodes the current token and issues a refreshed cookie.

JWTs are signed with HMAC SHA-256 using `JWT_SECRET`. The payload includes subject, issued-at, and expiration timestamps. Passwords are hashed with Spring Security's Argon2 password encoder.

## Authorization

Spring Security permits:

- `/actuator/health`
- `POST /user`
- `POST /user/login`
- `POST /user/logout`
- All `OPTIONS` requests

All other routes require authentication through the JWT filter.

## Cookies and CORS

The authentication cookie is HttpOnly, path `/`, and configurable through:

- `COOKIE_SECURE`
- `COOKIE_SAME_SITE`

CORS origins are configured through `CLIENT_URLS`, separated by semicolons. Allowed methods are `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`; credentials are allowed.

## Secrets

Sensitive configuration is expected through environment variables or Kubernetes Secret keys. The Kubernetes manifest defines the expected secret key names for database credentials, RabbitMQ credentials, and JWT signing. Production-like deployments should provide environment-specific secret values through a secure secret management process.

## Operational Considerations

- CSRF is disabled in the observed Spring Security configuration, which is a common API tradeoff when authentication and client behavior are controlled intentionally.
- For HTTPS deployments, set `COOKIE_SECURE` to true so browsers only send the auth cookie over secure transport.
- Use a strong environment-specific `JWT_SECRET`; do not reuse development placeholders.
- Keep CORS origins limited to trusted client applications.
