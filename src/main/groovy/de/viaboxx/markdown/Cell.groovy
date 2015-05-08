package de.viaboxx.markdown

import groovy.util.slurpersupport.Node

/**
 * Description: <br>
 * <p>
 * Date: 16.09.14<br>
 * </p>
 */
class Cell {
    final Node node
    private String value

    Cell(Node node) {
        this.node = node
    }

    void render(Confluence2MD walker) {
        value = walker.intoString {
            walker.walkThrough(node)
        }
        // optimize: cut obsolete trailing line-break (from obsolete <br> tags in confluence HTML)
        if (value.endsWith('\\\n')) value = value.substring(0, value.length() - 2)
        walker.writeRaw(value)
    }

    int length() {
        return value != null ? value.length() : node.text().length()
    }
}
