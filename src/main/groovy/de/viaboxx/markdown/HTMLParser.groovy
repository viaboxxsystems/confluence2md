package de.viaboxx.markdown

import org.apache.xerces.parsers.AbstractSAXParser
import org.apache.xerces.xni.parser.XMLParserConfiguration
import org.cyberneko.html.HTMLConfiguration

/**
 * Description: SAX parser for HTML documents delivered by Confluence REST-API<br>
 * <p>
 * Date: 16.09.14<br>
 * </p>
 * @see org.cyberneko.html.parsers.SAXParser
 */
@SuppressWarnings("UnnecessaryQualifiedReference")
class HTMLParser extends AbstractSAXParser {
    /**
     * refer to <a href="http://nekohtml.sourceforge.net/settings.html">NekoHtml Settings</a>
     * @return a new configured instance
     */
    static HTMLParser build() {
        XMLParserConfiguration config = new HTMLConfiguration()
        // Specifies whether CDATA sections are reported as character content.
        // If set to false, CDATA sections are reported as comments.
        config.setFeature("http://cyberneko.org/html/features/scanner/cdata-sections", true)
        new HTMLParser(config)
    }

    HTMLParser(XMLParserConfiguration configuration) {
        super(configuration)
    }
}
