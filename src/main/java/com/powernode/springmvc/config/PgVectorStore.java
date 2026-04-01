package com.powernode.springmvc.config;

import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class PgVectorStore implements VectorStore{
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public PgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void add(List<Document> documents) {
        for (Document doc : documents) {
            // 1. 把文本转成向量
            float[] embedding = embeddingModel.embed(doc.getText());

            // 2. 存入PostgreSQL
            jdbcTemplate.execute((Connection conn) -> {
                PGvector.addVectorType(conn);
                var ps = conn.prepareStatement(
                        "INSERT INTO vector_store (id, content, metadata, embedding) VALUES (?, ?, ?::json, ?)"
                );
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, doc.getText());
                ps.setString(3, "{}");
                ps.setObject(4, new PGvector(embedding));
                ps.executeUpdate();
                ps.close();
                return null;
            });
        }
        log.info("存入向量库：{} 条", documents.size());
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        // 1. 把查询文本转成向量
        float[] queryEmbedding = embeddingModel.embed(request.getQuery());

        // 2. 用余弦相似度搜索
        return jdbcTemplate.execute((Connection conn) -> {
            PGvector.addVectorType(conn);
            var ps = conn.prepareStatement(
                    "SELECT content FROM vector_store " +
                            "ORDER BY embedding <=> ? LIMIT ?"
            );
            ps.setObject(1, new PGvector(queryEmbedding));
            ps.setInt(2, request.getTopK());
            var rs = ps.executeQuery();
            var results = new java.util.ArrayList<Document>();
            while (rs.next()) {
                results.add(new Document(rs.getString("content")));
            }
            rs.close();
            ps.close();
            return results;
        });
    }

    @Override
    public void delete(List<String> idList) {
        for (String id : idList) {
            jdbcTemplate.update("DELETE FROM vector_store WHERE id = ?::uuid", id);
        }
    }

    @Override
    public void delete(Filter.Expression filterExpression) {

    }

    private float[] toFloatArray(List<Double> embedding) {
        float[] result = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            result[i] = embedding.get(i).floatValue();
        }
        return result;
    }
}
