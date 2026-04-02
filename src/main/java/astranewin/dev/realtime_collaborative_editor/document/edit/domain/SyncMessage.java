package astranewin.dev.realtime_collaborative_editor.document.edit.domain;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Setter
public class SyncMessage {
    SyncType type;
    List<Operation> operations;
    String content;
    int version;

    @Override
    public String toString() {
        return "SyncMessage{" +
                "type='" + type + '\'' +
                ", operations=" + operations +
                ", version=" + version +
                '}';
    }
}
