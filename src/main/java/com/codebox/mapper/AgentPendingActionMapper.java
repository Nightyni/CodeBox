package com.codebox.mapper;

import com.codebox.entity.AgentPendingAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentPendingActionMapper {

    int insert(AgentPendingAction action);

    AgentPendingAction findById(@Param("id") Long id);

    /**
     * Conditional state transition.
     *
     * Guarded by {@code status = 'PENDING'} so that two concurrent confirmations can
     * never both execute the same action - only the first update returns 1.
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("fromStatus") String fromStatus);
}
