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

import static org.springframework.ai.openai.api.OpenAiApi.ChatModel.GPT_4_O;

@Component
public class SimpleOpenAiChatHandler implements WebSocketHandler {
    @Autowired
    OpenAiChatModel openAiChatModel;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        Flux<String> streamData = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .flatMap(chatMessage -> {
                System.out.println(chatMessage);
                return Mono.just(openAiChatModel.call(
                        new Prompt(chatMessage, OpenAiChatOptions.builder().withModel(GPT_4_O).withUser("바보탱이").build()))
                    .getResult().getOutput().getContent());
            });
        return session.send(
            streamData.map(session::textMessage)
        );
    }
}
