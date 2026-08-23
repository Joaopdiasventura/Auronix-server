package dev.joaopdias.auronix.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import dev.joaopdias.auronix.shared.notification.RealtimeNotificationSubscriber;

@Configuration
public class RedisPubSubConfig {
    @Bean
    public ChannelTopic realtimeNotificationsTopic() {
        return new ChannelTopic("auronix.realtime.notifications");
    }

    @Bean
    @ConditionalOnProperty(name = "app.realtime.redis-subscribe-enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(
        RedisConnectionFactory connectionFactory,
        RealtimeNotificationSubscriber subscriber,
        ChannelTopic realtimeNotificationsTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, realtimeNotificationsTopic);
        return container;
    }
}
