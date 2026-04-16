package astranewin.dev.realtime_collaborative_editor.document.access;

import astranewin.dev.realtime_collaborative_editor.document.access.dto.AccessResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccessMapping {
    @Mapping(target = "documentId", source = "document.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "accessType", source = "access")
    AccessResponse toAccessResponse(DocumentAccessEntity entity);
}
