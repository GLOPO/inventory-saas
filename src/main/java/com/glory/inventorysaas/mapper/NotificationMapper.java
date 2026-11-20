package com.glory.inventorysaas.mapper;

import com.glory.inventorysaas.dto.response.NotificationResponse;
import com.glory.inventorysaas.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
