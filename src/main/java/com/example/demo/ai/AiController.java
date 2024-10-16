package com.example.demo.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.huggingface.HuggingfaceChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
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
    private final ChatMemory chatMemory = new InMemoryChatMemory();

    @PostMapping(value = "/openai/chat/interviewer/cto")
    public Flux<String> openAiInterviewerCto(@RequestBody String chatMessage) {
        ChatClient chatClient = ChatClient.builder(openAiChatModel).build();
        String responseContent = chatClient
            .prompt(new Prompt("", OpenAiChatOptions.builder().withModel(OpenAiApi.ChatModel.GPT_4_O).build()))
            .advisors(new MessageChatMemoryAdvisor(chatMemory, "InterviewerCto", 100))
            .system(PromptMessage.InterviewerCto.message)
            .user(chatMessage)
            .call().content();
        return Flux.just(responseContent);
    }

    //TODO 특정 식당에 대한 메뉴판 데이터베이스를 저장, RAG 통해 메뉴 추천 받도록 구현 계획
    @PostMapping(value = "/openai/chat/diet-manager")
    public Flux<String> openAiDietManager(@RequestBody String chatMessage) {
        ChatClient chatClient = ChatClient.builder(openAiChatModel).build();
        String responseContent = chatClient
            .prompt(new Prompt("", OpenAiChatOptions.builder().withModel(OpenAiApi.ChatModel.GPT_4_O).build()))
            .advisors(new MessageChatMemoryAdvisor(chatMemory, "diet-manager", 100))
            .system(PromptMessage.DietManager.message)
            .user(chatMessage)
            .call().content();
        return Flux.just(responseContent);
    }

    //TODO openAI 이용한 구현 완료 후 huggingface에서 모델 선정과 추가 구현 계획
    @PostMapping(value = "/huggingface/chat")
    public Flux<String> huggingFace(@RequestBody String chatMessage) {
        String apiToken = "", basePath = "";
        HuggingfaceChatModel huggingfaceChatModel = new HuggingfaceChatModel(apiToken, basePath);
        ChatClient chatClient = ChatClient.builder(huggingfaceChatModel).build();
        String responseContent = chatClient
            .prompt()
            .advisors(new MessageChatMemoryAdvisor(chatMemory, "InterviewerCto-huggingface", 100))
            .system("대한민국의 5살인 떡볶이를 좋아하는 미취학 아동이야 기본적인 지식이 많이 없고 노는걸 제일 좋아해. 기본적인 교육을 받지 못했고 궁금한게 많은 아이야")
            .user(chatMessage)
            .call().content();
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
