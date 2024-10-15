package com.example.demo.ai;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_O;

@Component
public class SimpleOpenAiChatStreamHandler implements WebSocketHandler {
    @Autowired
    OpenAiChatModel openAiChatModel;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Flux<String> streamData = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .flatMap(chatMessage -> {
                System.out.println(chatMessage);
                return openAiChatModel
                    .stream(new Prompt(chatMessage, OpenAiChatOptions.builder().withModel(GPT_4_O).withUser("바보탱이").build()))
                    .map(chatResponse -> chatResponse.getResult().getOutput().getContent());
            })
            .timeout(Duration.ofSeconds(10));

        return session.send(
            streamData.map(session::textMessage)
        );
    }
}
