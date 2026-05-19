# Seguranca

## Autenticacao

A autenticacao e baseada em um cookie HttpOnly chamado `access_token`. Criacao de usuario e login criam um JWT e definem o cookie. Logout limpa o cookie. O endpoint `GET /user` decodifica o token atual e emite um cookie renovado.

JWTs sao assinados com HMAC SHA-256 usando `JWT_SECRET`. O payload inclui subject, issued-at e timestamps de expiracao. Senhas sao armazenadas com hash pelo password encoder Argon2 do Spring Security.

## Autorizacao

O Spring Security permite:

- `/actuator/health`
- `POST /user`
- `POST /user/login`
- `POST /user/logout`
- Todas as requisicoes `OPTIONS`

Todas as demais rotas exigem autenticacao pelo filtro JWT.

## Cookies e CORS

O cookie de autenticacao e HttpOnly, path `/` e configuravel por:

- `COOKIE_SECURE`
- `COOKIE_SAME_SITE`

As origens CORS sao configuradas por `CLIENT_URLS`, separadas por ponto e virgula. Os metodos permitidos sao `GET`, `POST`, `PUT`, `PATCH`, `DELETE` e `OPTIONS`; credenciais sao permitidas.

## Secrets

Configuracoes sensiveis sao esperadas por variaveis de ambiente ou chaves Kubernetes Secret. O manifest Kubernetes define os nomes de chaves esperados para credenciais de banco, credenciais RabbitMQ e assinatura JWT. Deploys similares a producao devem fornecer valores especificos do ambiente por um processo seguro de gestao de segredos.

## Consideracoes Operacionais

- CSRF esta desabilitado na configuracao Spring Security observada, um tradeoff comum em APIs quando autenticacao e comportamento do cliente sao controlados de forma intencional.
- Para deploys HTTPS, configure `COOKIE_SECURE` como true para que navegadores enviem o cookie de autenticacao apenas por transporte seguro.
- Use um `JWT_SECRET` forte e especifico do ambiente; nao reutilize placeholders de desenvolvimento.
- Mantenha origens CORS limitadas a aplicacoes cliente confiaveis.
