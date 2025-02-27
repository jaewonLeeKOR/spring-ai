package com.example.demo.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.huggingface.HuggingfaceChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static com.example.demo.ai.PromptMessage.*;

@RestController
public class AiController {
    private static final Logger log = LoggerFactory.getLogger(AiController.class);
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

    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private VectorStore vectorStore;
    @PostMapping(value = "/pgvector/store", produces = "text/event-stream")
    public Flux<Boolean> pgVectorStore(@RequestBody String chatMessage) {
        List<Document> documents = List.of(chatMessage).stream().map(message -> Document.builder().withContent(message).build()).toList();
        for(Document document : documents) {
            System.out.println(document.getContent());
            document.setEmbedding(embeddingModel.embed(document.getContent()));
            for(Float f : document.getEmbedding())
                System.out.print(f + " ");
            System.out.println();
        }

        vectorStore.add(documents);
        return Flux.fromArray(new Boolean[]{true});
    }

    @PostMapping(value = "/pgvector/generate", produces = "text/event-stream")
    public Flux<String> generateAnswer(@RequestBody String userQuestion) {
        List<Document> retrievedDocs = vectorStore.similaritySearch(SearchRequest.query(userQuestion).withTopK(2));
        List<String> retrievedStrings = retrievedDocs.stream().map(Document::getContent).toList();
        for(String string : retrievedStrings)
            log.info("retrieved : " + string);
        String prompt = """
            다음은 검색된 문서들입니다:
            %s
            위 문서를 참고하여 질문에 답해주세요:
            "%s"
            """.formatted(String.join("\n", retrievedStrings), userQuestion);
        return Flux.just(openAiChatModel.call(prompt));
    }

    record EmbeddingPdfRequest(String fileName, String userName) {}

    @PostMapping(value = "/pdf/embedding")
    public Integer embeddingPdf(@RequestBody EmbeddingPdfRequest request) {
        Resource pdfResource = new ClassPathResource(String.format("pdf/%s", request.fileName));

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
            .withPageTopMargin(0)
            .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                .withNumberOfTopTextLinesToDelete(0)
                .build())
            .withPagesPerDocument(1)
            .build();
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource, config);
        List<Document> documents = pdfReader.get();
        TokenTextSplitter splitter = new TokenTextSplitter(1000, 400, 10, 5000, true);
        List<Document> splitDocuments = splitter.apply(documents);
        for (Document document : splitDocuments) {
            document.getMetadata().put("user_name", request.userName);
        }
        vectorStore.accept(splitDocuments);
        return splitDocuments.size();
    }

    record TechRecruitmentRequest(String companyName, String fileName, String userName) {}
    record TechRecruitmentResponse(
        String technicalCapabilityKeywords,
        String projectExperiences,
        String specialty,
        String mattersRequiringVerificationByTheApplicant,
        String reasonsForTheApplicantsInadequacy
    ) {}

    @PostMapping(value = "/pdf/retrieve")
    public TechRecruitmentResponse retrievePdfResult(@RequestBody TechRecruitmentRequest request) throws Exception {
        int documentAmount = 8;
        String userQuestion = TechRecruitment_UserQuestion.message;

        List<Document> retrievedDocs = vectorStore.similaritySearch(SearchRequest
            .query(userQuestion)
            .withTopK(documentAmount)
            .withFilterExpression((new FilterExpressionBuilder()).eq("file_name", request.fileName).build())
            .withFilterExpression((new FilterExpressionBuilder()).eq("user_name", request.userName).build())
        );
        for (Document doc : retrievedDocs) {
            log.info(doc.toString());
        }
        List<String> retrievedStrings = retrievedDocs.stream().map(Document::getContent).toList();

        String res = ChatClient.builder(openAiChatModel).build()
            .prompt(new Prompt("", OpenAiChatOptions.builder().withModel(OpenAiApi.ChatModel.GPT_4_O).build()))
            .system(TechRecruitment_System.message)
            .user(TechRecruitment_UserMessagePrompt.message.formatted(String.join("\n", retrievedStrings), userQuestion))
            .call().content();

        log.info(res);

        ObjectMapper objectMapper = new ObjectMapper();
        TechRecruitmentResponse techRecruitmentResponse = objectMapper.readValue(res, TechRecruitmentResponse.class);
        log.info(techRecruitmentResponse.toString());

        return techRecruitmentResponse;
    }
}