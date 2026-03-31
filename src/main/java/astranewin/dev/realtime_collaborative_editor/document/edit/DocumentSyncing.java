package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class DocumentSyncing {
    private final ObjectMapper mapper = new ObjectMapper();
    // TODO: add fallback if clientVersion is out of range of history
    public TextMessage sync(List<Operation> history, int clientVersion, int serverVersion) {
        List<Operation> missed = history.subList(
                clientVersion,
                serverVersion
        );

        SyncMessage sync = new SyncMessage(missed, serverVersion);
        return new TextMessage(mapper.writeValueAsString(sync));
    }
}
