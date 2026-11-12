package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.UserResponse;
import com.glory.inventorysaas.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
        UserResponse toResponse(User user);
}
