package com.johnnyblabs.openspec.ai.safety;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** Provider text must not cause JEditorPane to fetch remote images or render raw HTML. */
public final class SafeResponseMarkdown {
    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();
    private SafeResponseMarkdown() {}

    public static String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        Node document = PARSER.parse(markdown);
        document.accept(new AbstractVisitor() {
            @Override public void visit(Image image) {
                image.insertBefore(new Text("[Image omitted]"));
                image.unlink();
            }
        });
        return RENDERER.render(document);
    }
}
