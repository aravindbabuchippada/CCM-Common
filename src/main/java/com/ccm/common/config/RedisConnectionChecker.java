package com.ccm.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisConnectionChecker implements CommandLineRunner {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionChecker.class);

    @Override
    public void run(String... args) throws Exception {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            if(logger.isDebugEnabled()) {
                logger.debug("Redis Ping: " + new String(connection.ping()));
            }
        } catch (Exception e) {
            logger.error("Redis connection failed: " + e.getMessage());
        }
    }
}
