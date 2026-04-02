package astranewin.dev.realtime_collaborative_editor.document.edit.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

@NoArgsConstructor
@Getter
@Setter
public class DocumentState {
    private String content;
    private int version;
    private List<Operation> history;
    private boolean dirty;
    private ScheduledFuture<?> flushTask;

    public DocumentState(String content) {
        this.content = content;
        this.version = 0;
        this.history = new CopyOnWriteArrayList<>();
        this.dirty = false;
    }

    @Override
    public String toString() {
        return "DocumentState{" +
                "content='" + content + '\'' +
                ", version=" + version +
                '}';
    }
}
