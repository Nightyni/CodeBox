package com.codebox.mapper;

import com.codebox.entity.SnippetEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SnippetEmbeddingMapper {

    int upsert(SnippetEmbedding embedding);

    List<SnippetEmbedding> findByUser(@Param("userId") Long userId);

    int deleteBySnippetId(@Param("snippetId") Long snippetId);
}
