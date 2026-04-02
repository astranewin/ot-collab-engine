package astranewin.dev.realtime_collaborative_editor.document.edit.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class SyncFullMessage {
    private String type = "fullsync";
    String content;
    int serverVersion;

    public SyncFullMessage(int serverVersion, String content) {
        this.serverVersion = serverVersion;
        this.content = content;
    }
}
