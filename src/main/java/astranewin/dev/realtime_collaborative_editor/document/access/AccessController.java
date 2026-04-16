package astranewin.dev.realtime_collaborative_editor.document.access;

import astranewin.dev.realtime_collaborative_editor.document.access.dto.AccessResponse;
import astranewin.dev.realtime_collaborative_editor.document.access.dto.UpdateAccessRequest;
import astranewin.dev.realtime_collaborative_editor.user.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doc/{docId}/access")
@RequiredArgsConstructor
public class AccessController {
    private final AccessService accessService;

    @PatchMapping
    public AccessResponse updateAccess(
            @PathVariable Long docId,
            @RequestBody @Valid UpdateAccessRequest request,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return accessService.updateAccess(userDetails.getUserEntity(), docId, request);
    }
}
