package astranewin.dev.realtime_collaborative_editor.document.edit.domain;

import lombok.*;

@AllArgsConstructor
@Builder
@NoArgsConstructor
@Getter
@Setter
public class Operation {
    private String operationId;
    private String senderId;
    private OperationType type;
    private int position;
    private String text;
    private int version;
    private int length;

    @Override
    public String toString() {
        return "Operation{" +
                "type=" + type +
                ", position=" + position +
                ", text='" + text + '\'' +
                ", version=" + version +
                ", length=" + length +
                '}';
    }
}
