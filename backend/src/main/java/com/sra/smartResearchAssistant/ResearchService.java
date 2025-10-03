package com.sra.smartResearchAssistant;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ResearchService {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public ResearchService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    public String processContent(ResearchRequest request) {
        String prompt = buildPrompt(request);

        // Gemini request body
        Map<String, Object> requestBody = Map.of(
            "contents", new Object[]{
                Map.of("parts", new Object[]{
                    Map.of("text", prompt)
                })
            }
        );

        try {
            // Send request to Gemini API
            String response = webClient.post()
                    .uri(geminiApiUrl)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // Debug log
            System.out.println("Gemini Raw Response: " + response);

            // Parse and extract text
            return extractTextFromResponse(response);

        } catch (Exception e) {
            e.printStackTrace(); // log stacktrace
            return "Error calling Gemini API: " + e.getMessage();
        }
    }

    private String extractTextFromResponse(String response) {
        try {
            GeminiResponse geminiResponse = objectMapper.readValue(response, GeminiResponse.class);

            if (geminiResponse.getCandidates() != null && !geminiResponse.getCandidates().isEmpty()) {
                GeminiResponse.Candidate firstCandidate = geminiResponse.getCandidates().get(0);

                if (firstCandidate.getContent() != null
                        && firstCandidate.getContent().getParts() != null
                        && !firstCandidate.getContent().getParts().isEmpty()) {

                    return firstCandidate.getContent().getParts().get(0).getText();
                }
            }

            return "No text found in response.";
        } catch (Exception e) {
            return "Error Parsing Response: " + e.getMessage();
        }
    }

    private String buildPrompt(ResearchRequest request) {
        StringBuilder prompt = new StringBuilder();

        switch (request.getOperation().toLowerCase()) {
            case "summarize":
                prompt.append("Provide a clear and concise summary of the following text:\n\n");
                break;
            case "suggest":
                prompt.append("Based on the following content, suggest related topics with headings and bullet points:\n\n");
                break;
            default:
                throw new IllegalArgumentException("Invalid operation: " + request.getOperation());
        }

        prompt.append(request.getContent());
        return prompt.toString();
    }
}
