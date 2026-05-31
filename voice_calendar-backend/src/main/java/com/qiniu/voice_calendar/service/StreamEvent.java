package com.qiniu.voice_calendar.service;

/** Events emitted by {@link AiService} during streaming chat. */
public class StreamEvent {

    public enum Type { CONTENT, TOOL_CALL, DONE, ERROR }

    private final Type type;
    private final String content;
    private final String toolCallId;
    private final String toolName;
    private final String arguments;

    private StreamEvent(Type type, String content, String toolCallId, String toolName, String arguments) {
        this.type = type;
        this.content = content;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.arguments = arguments;
    }

    public static StreamEvent content(String text) {
        return new StreamEvent(Type.CONTENT, text, null, null, null);
    }

    public static StreamEvent toolCall(String id, String name, String arguments) {
        return new StreamEvent(Type.TOOL_CALL, null, id, name, arguments);
    }

    public static StreamEvent done() {
        return new StreamEvent(Type.DONE, null, null, null, null);
    }

    public static StreamEvent error(String message) {
        return new StreamEvent(Type.ERROR, message, null, null, null);
    }

    public Type type() { return type; }
    public String content() { return content; }
    public String toolCallId() { return toolCallId; }
    public String toolName() { return toolName; }
    public String arguments() { return arguments; }
}
