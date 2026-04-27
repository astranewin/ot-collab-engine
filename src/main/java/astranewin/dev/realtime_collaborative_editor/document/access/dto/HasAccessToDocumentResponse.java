package astranewin.dev.realtime_collaborative_editor.document.access.dto;

import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
public class HasAccessToDocumentResponse{
    private AccessType effectiveAccess;
    private AccessType explicitAccess;
}
