package com.johnnyblabs.openspec.ai.safety;

import java.io.IOException;

/** Logical document contents use LF; normal IDE saving preserves the existing file separator. */
public final class ArtifactTextPolicy {
    private ArtifactTextPolicy() { }
    public static String canonicalContent(String raw) throws IOException {
        if (raw == null) throw new IOException("Missing artifact text");
        ArtifactResultValidator.utf8(raw);
        boolean crlf = false;
        boolean lf = false;
        for (int i = 0; i < raw.length(); i++) {
            char value = raw.charAt(i);
            if (value == '\r') {
                if (i + 1 >= raw.length() || raw.charAt(i + 1) != '\n') throw new IOException("Lone CR artifact separators are unsupported; use consistent LF or CRLF");
                crlf = true;
                i++;
            } else if (value == '\n') lf = true;
        }
        if (crlf && lf) throw new IOException("Mixed artifact line separators are unsupported; use consistent LF or CRLF");
        return crlf ? raw.replace("\r\n", "\n") : raw;
    }
}
