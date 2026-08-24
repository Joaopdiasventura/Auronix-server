# Security

## Authentication

Authentication is based on an HttpOnly cookie named `access_token`. User creation and login create a JWT and set the cookie. Logout clears the cookie. `GET /user` decodes the current token and issues a refreshed cookie.

JWTs are signed with HMAC SHA-256 using `JWT_SECRET`. The payload includes subject, issued-at, and expiration timestamps. Passwords are hashed with Spring Security's Argon2 password encoder defaults.

## Authorization

Spring Security permits:

- `/actuator/health`
- `/actuator/health/**`, including liveness and readiness groups
- `POST /user`
- `POST /user/login`
- `POST /user/logout`
- all `OPTIONS` requests

All other routes require authentication through the JWT filter.

## Cookies and CORS

The authentication cookie is HttpOnly, path `/`, and configurable through:

- `COOKIE_SECURE`
- `COOKIE_SAME_SITE`

CORS origins are configured through `CLIENT_URLS`, separated by semicolons. The configured methods are `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`; credentials are allowed; exposed headers are `Authorization` and `Content-Disposition`.

## Secrets

Sensitive values are expected through environment variables or Kubernetes Secret keys. The manifests define keys for database credentials, RabbitMQ credentials, and JWT signing. Production-like environments should replace all placeholders and should use a strong environment-specific `JWT_SECRET`.

## Operational Considerations

- CSRF is disabled in the observed Spring Security configuration.
- For HTTPS deployments, set `COOKIE_SECURE=true`.
- Keep `CLIENT_URLS` limited to trusted client origins.
- Health endpoints are public because Kubernetes and container checks need unauthenticated access.
- Production dependency credentials are not created by Terraform in the current repository.
