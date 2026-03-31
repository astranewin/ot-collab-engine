package astranewin.dev.realtime_collaborative_editor.document.edit.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@NoArgsConstructor
@Getter
@Setter
public class SyncMessage {
    private String type = "sync";
    List<Operation> operations;
    int version;

    public SyncMessage(List<Operation> operations, int version) {
        this.operations = operations;
        this.version = version;
    }

    @Override
    public String toString() {
        return "SyncMessage{" +
                "type='" + type + '\'' +
                ", operations=" + operations +
                ", version=" + version +
                '}';
    }
}
