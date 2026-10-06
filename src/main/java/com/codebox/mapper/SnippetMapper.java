package com.codebox.mapper;

import com.codebox.entity.Snippet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SnippetMapper {

    Snippet findById(@Param("id") Long id, @Param("userId") Long userId);

    List<Snippet> search(@Param("userId") Long userId,
                         @Param("keyword") String keyword,
                         @Param("language") String language,
                         @Param("tags") String tags,
                         @Param("offset") int offset,
                         @Param("pageSize") int pageSize);

    long countSearch(@Param("userId") Long userId,
                     @Param("keyword") String keyword,
                     @Param("language") String language,
                     @Param("tags") String tags);

    List<Snippet> findAllByUser(@Param("userId") Long userId);

    int insert(Snippet snippet);

    int update(Snippet snippet);

    int delete(@Param("id") Long id, @Param("userId") Long userId);

    /** Only the owner may bump the counter - enforced in SQL, not just in Java. */
    int incrementUseCount(@Param("id") Long id, @Param("userId") Long userId);
}
