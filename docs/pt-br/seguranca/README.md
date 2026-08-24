# Segurança

## Autenticação

A autenticação é baseada em um cookie HttpOnly chamado `access_token`. Criação de usuário e login criam um JWT e definem o cookie. Logout limpa o cookie. `GET /user` decodifica o token atual e emite um cookie renovado.

JWTs são assinados com HMAC SHA-256 usando `JWT_SECRET`. O payload inclui subject, issued-at e timestamps de expiração. Senhas são armazenadas com hash pelos defaults do password encoder Argon2 do Spring Security.

## Autorização

O Spring Security permite:

- `/actuator/health`
- `/actuator/health/**`, incluindo grupos de liveness e readiness
- `POST /user`
- `POST /user/login`
- `POST /user/logout`
- todas as requisições `OPTIONS`

Todas as demais rotas exigem autenticação pelo filtro JWT.

## Cookies e CORS

O cookie de autenticação é HttpOnly, usa path `/` e é configurável por:

- `COOKIE_SECURE`
- `COOKIE_SAME_SITE`

As origens CORS são configuradas por `CLIENT_URLS`, separadas por ponto e vírgula. Os métodos configurados são `GET`, `POST`, `PUT`, `PATCH`, `DELETE` e `OPTIONS`; credenciais são permitidas; os headers expostos são `Authorization` e `Content-Disposition`.

## Secrets

Valores sensíveis são esperados por variáveis de ambiente ou chaves Kubernetes Secret. Os manifests definem chaves para credenciais de banco, credenciais RabbitMQ e assinatura JWT. Ambientes similares a produção devem substituir todos os placeholders e usar um `JWT_SECRET` forte e específico do ambiente.

## Considerações Operacionais

- CSRF está desabilitado na configuração Spring Security observada.
- Para deploys HTTPS, configure `COOKIE_SECURE=true`.
- Mantenha `CLIENT_URLS` limitado a origens de clientes confiáveis.
- Endpoints de health são públicos porque Kubernetes e checks de container precisam de acesso sem autenticação.
- Credenciais de dependências de produção não são criadas pelo Terraform no repositório atual.
