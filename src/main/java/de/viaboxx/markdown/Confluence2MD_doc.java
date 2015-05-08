package de.viaboxx.markdown;

/**
 * class Confluence2MD: convert a confluence Json page(s)/space to a markdown for pandoc
 * <pre>
 * Usage:
 * java de.viaboxx.markdown.Confluence2MD -m wiki -o [outputfile] -server [url] &lt;pageId&;
 * java de.viaboxx.markdown.Confluence2MD -m file &lt;input file&gt;  &gt; [outputfile]
 * java de.viaboxx.markdown.Confluence2MD -m url  &lt;input URL&gt;   &gt; [outputfile]
 *
 * options:
 * -m wiki|file|url specify input format/processing mode (default: wiki)
 * -o file specify output format, charset=UTF-8  (default: stdout, charset=file.encoding of plaform)
 * -oa file specify output format, charset=UTF-8 - open for append!
 * +quiet for non-verbose output   (default: verbose)
 * -u user:password to use HTTP-Basic-Auth to request the URL (default: no auth)
 * -depth -1..n the depth to follow down the child-pages hierarchy. -1=infinte, 0=no children (default: -1)
 * -server URL of confluence server. used in wiki-mode (default: https://viaboxx.atlassian.net/wiki)
 * -plantuml  turn off integrated run of PlantUML to render diagrams (default is to call PlantUML automatically)
 * -a download folder for attachments (default: attachments)
 * +H true/false true: document hierarchy used to generate page header format type (child document => h2 etc) (default: true)
 * +T true/false true: title transformation ON (cut everything before first -) (default: true)
 * +RootPageTitle true/false true: generate header for root page, false: omit header of root page (default: true)
 * +FootNotes true/false true:generate foot notes, false: no foot notes (default: true)
 * -maxHeaderDepth 1..n the maximum header depth that will be rendered as a header, deeper will only rendered as bold title (default: 5)
 * -no-caching file caching disabled
 * +caching file caching enabled
 *
 * last parameter: the file to read (-m file) or the URL to get (-m url) or the pageId to start with (-m wiki)
 * -? print for this help
 * </pre>
 */
public class Confluence2MD_doc {
    /**
     * calls Confluence2MD.main(args);
     *
     * @param args
     */
    public static void main(String[] args) {
        Confluence2MD.main(args);
    }
}
