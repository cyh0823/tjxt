package com.tianji.aigc.controller;

import cn.hutool.core.collection.CollStreamUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/embedding")
@Slf4j
@RequiredArgsConstructor
public class EmbeddingController {

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    @PostMapping
    public void saveDocument(@RequestParam("messages")List<String> messages) {
        log.info("将消息保存到向量数据库中，{}", messages);
        List<Document> documents = CollStreamUtil.toList(messages, message -> Document.builder().text(message).build());
        vectorStore.add(documents);
        log.info("消息保存成功！");
    }


    @GetMapping
    public EmbeddingResponse embedding(@RequestParam("message")String message) {
        return embeddingModel.embedForResponse(List.of(message));
    }

    @GetMapping("/search")
    public List<Document> search(@RequestParam("message")String message) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(message).topK(5).build());
    }

    @GetMapping("/search/all")
    public List<Document> searchAll(){
        return vectorStore.similaritySearch(SearchRequest.builder().topK(999).build());
    }

    @DeleteMapping
    public void deleteByIds(@RequestParam("ids") List<String> ids) {
        vectorStore.delete(ids);
    }
}
