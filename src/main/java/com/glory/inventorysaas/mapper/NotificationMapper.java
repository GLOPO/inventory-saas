package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.NotificationResponse;
import com.glory.inventorysaas.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "isRead", source = "read")
    NotificationResponse toResponse(Notification notification);
}
