package de.viaboxx.markdown;

/**
 * <pre>
 * Usage:
 * java de.viaboxx.markdown.Uml2Confluence -server [url] &lt;pageId&;
 *
 * options:
 * +quiet for non-verbose output   (default: verbose)
 * -u user:password to use HTTP-Basic-Auth to request the URL (default: no auth)
 * -depth -1..n the depth to follow down the child-pages hierarchy. -1=infinte, 0=no children (default: -1)
 * -server URL of confluence server. (default: https://viaboxx.atlassian.net/wiki)
 * -a download folder for attachments (default: attachments)
 * -no-caching file caching disabled
 * -updateAttachments false  boolean to enable/disable attaching images to the page
 * -updatePage false         boolean to enable/disable that the page content is changed (to add section and image tags)
 * +caching file caching enabled     (default: disabled)
 * +dryRun do not change confluence  (default: false)
 *
 * last parameter: the file to read (-m file) or the URL to get (-m url) or the pageId to start with (-m wiki)
 * -? print for this help
 * </pre>
 * <p/>
 * <p>
 * Date: 24.10.14<br>
 * </p>
 */
public class Uml2Confluence_doc {
    /**
     * calls Uml2Confluence.main(args);
     *
     * @param args
     */
    public static void main(String[] args) {
        Uml2Confluence.main(args);
    }
}
