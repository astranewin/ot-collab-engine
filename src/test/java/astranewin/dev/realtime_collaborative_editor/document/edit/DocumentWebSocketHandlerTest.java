package astranewin.dev.realtime_collaborative_editor.document.edit;

import astranewin.dev.realtime_collaborative_editor.document.DocumentAccessPolicy;
import astranewin.dev.realtime_collaborative_editor.document.access.AccessType;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.DocumentState;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.Operation;
import astranewin.dev.realtime_collaborative_editor.document.edit.domain.SyncMessage;
import astranewin.dev.realtime_collaborative_editor.document.edit.dto.DocumentHandleOperationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentWebSocketHandlerTest {
    @InjectMocks
    private DocumentWebSocketHandler underTest;

    @Mock
    private Validator validator;

    @Mock
    private DocumentOperationService documentOperationService;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private Map<String, Object> session1Attributes;
    private Map<String, Object> session2Attributes;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        session1Attributes = new HashMap<>();
        session2Attributes = new HashMap<>();

        lenient().when(session1.getAttributes()).thenReturn(session1Attributes);
        lenient().when(session2.getAttributes()).thenReturn(session2Attributes);
        lenient().when(session1.isOpen()).thenReturn(true);
        lenient().when(session2.isOpen()).thenReturn(true);
        lenient().when(session1.getId()).thenReturn("session-1");
        lenient().when(session2.getId()).thenReturn("session-2");
    }

    @Nested
    class connection {
        @Test
        void afterConnectionEstablished_Success() throws Exception {
            session1Attributes.put("docId", "doc-1");
            session1Attributes.put("username", "user1");

            DocumentState docState = new DocumentState();
            docState.setContent("Hello world");
            docState.setVersion(0);

            when(documentOperationService.initDocument("doc-1")).thenReturn(docState);

            underTest.afterConnectionEstablished(session1);

            verify(documentOperationService).initDocument("doc-1");
            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session1).sendMessage(messageCaptor.capture());

            String payload = messageCaptor.getValue().getPayload();
            assertTrue(payload.contains("\"type\":\"init\""));
            assertTrue(payload.contains("\"content\":\"Hello world\""));
        }

        @Test
        void afterConnectionEstablished_MissingAttributes_CloseSession() throws Exception {
            underTest.afterConnectionEstablished(session1);

            verify(session1).close(CloseStatus.SERVER_ERROR);
            verifyNoInteractions(documentOperationService);
        }

        @Test
        void afterConnectionClosen_RemoveSession() throws Exception {
            session1Attributes.put("docId", "doc-1");
            session1Attributes.put("username", "user1");

            when(documentOperationService.initDocument("doc-1")).thenReturn(new DocumentState("Hello world"));
            underTest.afterConnectionEstablished(session1);

            underTest.afterConnectionClosed(session1, CloseStatus.NORMAL);

            // Verifying that user won't get any new text messages
            underTest.forceSyncAll("doc-1", new SyncMessage());
            verify(session1, times(1)).sendMessage(any(TextMessage.class));
        }
    }

    @Nested
    class message {
        @Test
        void NoAccess_ReturnsError() throws Exception {
            session1Attributes.put("docId", "doc-1");
            session1Attributes.put("effectiveAccess", AccessType.READ);

            TextMessage incomingMsg = new TextMessage("{\"type\": \"INSERT\", \"position\": 0, \"text\": \"Hello world\"}");
            underTest.handleMessage(session1, incomingMsg);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session1).sendMessage(messageCaptor.capture());

            assertTrue(messageCaptor.getValue().getPayload().contains("No access to edit content"));
            verifyNoInteractions(documentOperationService);
        }

        @Test
        void ValidationFails_ReturnsError() throws Exception {
            session1Attributes.put("docId", "doc-1");
            session1Attributes.put("effectiveAccess", AccessType.WRITE);

            TextMessage incomingMsg = new TextMessage("{\"type\": \"INERT\"");

            underTest.handleMessage(session1, incomingMsg);

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session1).sendMessage(messageCaptor.capture());

            System.out.println(messageCaptor.getValue().getPayload());
            assertTrue(messageCaptor.getValue().getPayload().contains("Invalid JSON format"));
        }

        @Test
        void ValidOperation_BroadcastToOthers() throws Exception {
            session1Attributes.put("docId", "doc-1");
            session1Attributes.put("username", "user1");
            session1Attributes.put("effectiveAccess", AccessType.WRITE);

            session2Attributes.put("docId", "doc-1");
            session2Attributes.put("username", "user2");

            when(documentOperationService.initDocument("doc-1")).thenReturn(new DocumentState("Hello world"));
            underTest.afterConnectionEstablished(session1);
            underTest.afterConnectionEstablished(session2);

            reset(session1, session2);
            when(session1.isOpen()).thenReturn(true);
            when(session2.isOpen()).thenReturn(true);
            when(session1.getId()).thenReturn("session-1");
            when(session2.getId()).thenReturn("session-2");
            when(session1.getAttributes()).thenReturn(session1Attributes);

            Operation op = new Operation();
            String opJson = mapper.writeValueAsString(op);

            DocumentHandleOperationResponse response = new DocumentHandleOperationResponse();
            response.setOp(op);
            when(documentOperationService.handle(eq("doc-1"), any(Operation.class))).thenReturn(response);

            underTest.handleMessage(session1, new TextMessage(opJson));

            verify(documentOperationService).handle(eq("doc-1"), any(Operation.class));

            verify(session1, never()).sendMessage(any(TextMessage.class));

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session2).sendMessage(messageCaptor.capture());
            assertEquals(opJson, messageCaptor.getValue().getPayload());
        }
    }

    @Nested
    class updateAccess {
        @Test
        void InSession_UpdatesToNone_ClosesSession() throws Exception {
            session1Attributes.put("docId", "doc-1");
            session1Attributes.put("username", "user1");

            when(documentOperationService.initDocument("doc-1")).thenReturn(new DocumentState("Hello world"));
            underTest.afterConnectionEstablished(session1);

            DocumentAccessPolicy mockPolicy = mock(DocumentAccessPolicy.class);
            when(mockPolicy.toAccessType()).thenReturn(AccessType.NONE);

            underTest.updateEffectiveAccessInSession("user1", AccessType.NONE, mockPolicy);

            verify(session1).close(CloseStatus.POLICY_VIOLATION);
        }

        @Test
        void LowersAccessFloor() throws Exception {
            session1Attributes.put("docId", "doc-1");
            session1Attributes.put("username", "user1");
            session1Attributes.put("explicitAccess", AccessType.NONE);
            session1Attributes.put("effectiveAccess", AccessType.WRITE);

            when(documentOperationService.initDocument("doc-1")).thenReturn(new DocumentState("Hello world"));
            underTest.afterConnectionEstablished(session1);

            underTest.updateEffectiveAccess("doc-1", AccessType.READ);

            assertEquals(AccessType.READ, session1Attributes.get("effectiveAccess"));

            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session1, times(2)).sendMessage(messageCaptor.capture());

            String lastMessage = messageCaptor.getAllValues().get(1).getPayload();
            assertTrue(lastMessage.contains("\"type\": \"ACCESS_UPDATE\""));
            assertTrue(lastMessage.contains("\"level\": \"READ\""));
        }
    }
}