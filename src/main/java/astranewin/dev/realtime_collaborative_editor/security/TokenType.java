package astranewin.dev.realtime_collaborative_editor.security;

import lombok.Getter;

@Getter
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh"),
    WEBSOCKET("websocket");

    private final String value;

    TokenType(String value) { this.value = value; };
}
