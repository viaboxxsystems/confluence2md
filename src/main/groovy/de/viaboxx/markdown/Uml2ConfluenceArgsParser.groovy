package de.viaboxx.markdown

/**
 * Description: <br>
 * <p>
 * Date: 24.10.14<br>
 * </p>
 */
class Uml2ConfluenceArgsParser {
    final Uml2Confluence target

    Uml2ConfluenceArgsParser(Uml2Confluence target) {
        this.target = target
    }

    String usage = """
      Usage:
       java ${target.class.name} -server [url] &lt;pageId&;

      options:
      +quiet for non-verbose output   (default: verbose)
      -u user:password to use HTTP-Basic-Auth to request the URL (default: no auth)
      -depth -1..n the depth to follow down the child-pages hierarchy. -1=infinte, 0=no children (default: -1)
      -server URL of confluence server. (default: https://viaboxx.atlassian.net/wiki)
      -a download folder for attachments (default: attachments)
      -no-caching file caching disabled
      -updateAttachments false  boolean to enable/disable attaching images to the page
      -updatePage false         boolean to enable/disable that the page content is changed (to add section and image tags)
      +caching file caching enabled     (default: disabled)
      +dryRun do not change confluence  (default: false)

      last parameter: the file to read (-m file) or the URL to get (-m url) or the pageId to start with (-m wiki)
      -? print for this help
  """

    boolean parseArgs(String[] args) {
        if (!args) {
            println usage
            return false
        }
        String format = "wiki"
        target.with {
            for (int i = 0; i < args.length - 1; i++) {
                String arg = args[i]
                switch (arg) {
                    case "-?":
                        println usage
                        break
                    case "-updateAttachments":
                        updateAttachments = Boolean.parseBoolean(args[++i])
                        break
                    case "-updatePage":
                        updatePage = Boolean.parseBoolean(args[++i])
                        break
                    case "-u":
                        userPassword = args[++i]
                        break
                    case "-no-caching":
                        caching = false
                        break
                    case "+dryRun":
                        dryRun = true
                        break
                    case "+caching":
                        caching = true
                        break
                    case "+quiet":
                        verbose = false
                        break
                    case "-server":
                        wikiServerUrl = args[++i]
                        break
                    case "-depth":
                        maxDepth = Integer.parseInt(args[++i])
                        break
                    case "-a":
                        downloadFolder = new File(args[++i])
                        break
                }
            }
            switch (format) {
                case "wiki":
                    rootPageId = args[-1]
                    log("Starting page '${rootPageId}'")
                    input = openInput(wikiServerUrl + GET_PAGE_BODY(rootPageId), rootPageId + ".body")
                    break
                case "url":
                    input = openInput(args[-1])
                    log("Input url '${args[-1]}'")
                    break
                case "file":
                    input = new File(args[-1])
                    log("Input file '$input'")
                    break
                default:
                    throw new IllegalArgumentException("unknown format (-f) $format")
            }
        }
        return true
    }
}
