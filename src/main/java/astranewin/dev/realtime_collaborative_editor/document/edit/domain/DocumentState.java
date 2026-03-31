package astranewin.dev.realtime_collaborative_editor.document.edit.domain;

import lombok.*;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class DocumentState {
    private String content;
    private int version;
    private List<Operation> history;
}
