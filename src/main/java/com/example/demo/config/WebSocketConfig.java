package com.example.demo.config;

import com.example.demo.ai.SimpleOpenAiChatHandler;
import com.example.demo.ai.SimpleOpenAiChatStreamHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Collections;

@Configuration
public class WebSocketConfig {
    @Autowired
    private SimpleOpenAiChatStreamHandler openaiStreamHandlerSimple;
    @Autowired
    private SimpleOpenAiChatHandler openaiHandlerSimple;

    @Bean
    public WebSocketHandlerAdapter handlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
    public SimpleUrlHandlerMapping webSocketHandlerMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Collections.singletonMap("/ws/openai", openaiHandlerSimple));
        mapping.setUrlMap(Collections.singletonMap("/ws/openai/stream", openaiStreamHandlerSimple));
        mapping.setOrder(10);
        return mapping;
    }
}
