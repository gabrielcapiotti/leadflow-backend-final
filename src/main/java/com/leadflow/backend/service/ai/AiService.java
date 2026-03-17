package com.leadflow.backend.service.ai;

import java.util.Map;
import java.util.UUID;

public interface AiService {

    /**
     * Executa um prompt no modelo de IA configurado.
     */
    String generate(String prompt);

    /**
     * Gera um resumo do lead baseado em sua conversa e histórico.
     */
    String generateSummary(UUID leadId);

    /**
     * Sugere um título para o lead baseado em seu contexto ou ID.
     */
    String suggestTitle(UUID leadId);

    /**
     * Sugere um título para uma mensagem ou contexto de texto.
     */
    String suggestTitle(String context);

    /**
     * Refina uma mensagem do usuário melhorando seu conteúdo e clareza.
     */
    String refineMessage(String message);

    /**
     * Analisa o sentimento de um lead baseado em sua conversa.
     */
    Map<String, Object> analyzeSentiment(UUID leadId);

    /**
     * Classifica um lead em categorias (hot, warm, cold, etc).
     */
    Map<String, Object> classifyLead(UUID leadId);

    /**
     * Gera uma resposta para um prompt específico relacionado a um lead.
     */
    String generateResponse(UUID leadId, String prompt);
}