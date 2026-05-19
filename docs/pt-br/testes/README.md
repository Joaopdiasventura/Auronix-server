# Testes

## Frameworks

O projeto usa testes automatizados via Maven com dependencias de teste Spring Boot, JUnit, Mockito, AssertJ, MockMvc, H2, suporte de testes Spring Security, suporte Spring AMQP e Spring Cloud Stream test binder.

## Comandos

```bash
./mvnw test
```

No Windows:

```powershell
.\mvnw.cmd test
```

## Configuracao de Teste

Os testes usam `src/test/resources/application.properties`, que configura:

- Banco H2 em memoria com modo de compatibilidade PostgreSQL.
- Hibernate `create-drop`.
- Health checks de RabbitMQ e Redis desabilitados.
- Listeners RabbitMQ desabilitados.
- Configuracoes JWT e de cookie exclusivas de teste.

## Areas Cobertas

Os testes observados cobrem:

- Inicializacao do contexto Spring.
- Configuracao de CORS e RabbitMQ.
- Criacao, validacao, expiracao, assinatura de JWT e comportamento do filtro de autenticacao.
- Services de usuario, conta, transacao, cobranca e notificacao.
- Testes de controllers com MockMvc.
- Regras de validacao de DTOs.
- Comportamento auxiliar de entidades, como conversao para DTO de resposta e atribuicao de timestamps de lifecycle.
- Producers e consumers RabbitMQ com colaboradores mockados.
- Comportamento do registro SSE e entrega de notificacoes.
