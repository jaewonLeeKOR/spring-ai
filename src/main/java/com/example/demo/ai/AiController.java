package com.example.demo.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
public class AiController {
    @Autowired
    private OpenAiChatModel openAiChatModel;

    @PostMapping(value = "/openai/chat")
    public Flux<String> openAi(@RequestBody String chatMessage) {
        ChatClient chatClient = ChatClient.builder(openAiChatModel).build();
        String responseContent = chatClient.prompt().user(chatMessage).call().content();
        return Flux.just(responseContent);
    }

    @PostMapping(value = "/openai/chat/stream", produces = "text/event-stream")
    public Flux<String> openAiStream(@RequestBody String chatMessage) {
        return openAiChatModel.stream(
                new Prompt(chatMessage))
            .map(chatResponse -> chatResponse.getResult().getOutput().getContent())
            .timeout(Duration.ofSeconds(10));
    }
}
