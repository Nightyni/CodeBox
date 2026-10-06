package com.codebox.mapper;

import com.codebox.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    int insert(User user);

    int countByUsername(@Param("username") String username);

    /** Used by the startup indexer to walk every user's library. */
    List<Long> findAllIds();
}
