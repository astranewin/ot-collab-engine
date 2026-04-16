package astranewin.dev.realtime_collaborative_editor.document;

import astranewin.dev.realtime_collaborative_editor.document.dto.DocumentRequest;
import astranewin.dev.realtime_collaborative_editor.document.dto.DocumentResponse;
import astranewin.dev.realtime_collaborative_editor.document.snapshot.domain.SnapshotListResponse;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import astranewin.dev.realtime_collaborative_editor.user.UserEntity;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@SecurityRequirement(name = "bearerAuth")

@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService service;

    @PostMapping()
    public DocumentResponse create(
            @RequestBody @Valid DocumentRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        UserEntity userEntity = userDetails.getUserEntity();
        return service.create(request, userEntity);
    }

    @PatchMapping("/{docId}/revert/{snapshotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revertChangesBySnapshot(
            @PathVariable Long docId,
            @PathVariable Long snapshotId
    ) throws IOException {
        service.revertChanges(docId, snapshotId);
    }
}
