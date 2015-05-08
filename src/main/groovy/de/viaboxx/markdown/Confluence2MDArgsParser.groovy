package de.viaboxx.markdown

/**
 * Description: Parse the command line args and set options into Confluence2MD<br>
 * <p>
 * Date: 15.10.14<br>
 * </p>
 */
class Confluence2MDArgsParser {
    final Confluence2MD target

    Confluence2MDArgsParser(Confluence2MD target) {
        this.target = target
    }

    String usage = """
    Usage:
     java ${target.class.name} -m wiki -o [outputfile] -server [url] &lt;pageId&;
     java ${target.class.name} -m file &lt;input file&gt;  &gt; [outputfile]
     java ${target.class.name} -m url  &lt;input URL&gt;   &gt; [outputfile]

    options:
    -m wiki|file|url specify input format/processing mode (default: wiki)
    -o file specify output format, charset=UTF-8  (default: stdout, charset=file.encoding of plaform)
    -oa file specify output format, charset=UTF-8 - open for append!
    +quiet for non-verbose output   (default: verbose)
    -u user:password to use HTTP-Basic-Auth to request the URL (default: no auth)
    -depth -1..n the depth to follow down the child-pages hierarchy. -1=infinte, 0=no children (default: -1)
    -server URL of confluence server. used in wiki-mode (default: https://viaboxx.atlassian.net/wiki)
    -plantuml  turn off integrated run of PlantUML to render diagrams (default is to call PlantUML automatically)
    -a download folder for attachments (default: attachments)
    +H true/false true: document hierarchy used to generate page header format type (child document => h2 etc) (default: true)
    +T true/false true: title transformation ON (cut everything before first -) (default: true)
    +RootPageTitle true/false true: generate header for root page, false: omit header of root page (default: true)
    +FootNotes true/false true:generate foot notes, false: no foot notes (default: true)
    -maxHeaderDepth 1..n the maximum header depth that will be rendered as a header, deeper will only rendered as bold title (default: 5)
    -no-caching file caching disabled (default: enabled)
    +caching file caching enabled

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
                    case "-no-caching":
                        caching = false
                        break
                    case "+caching":
                        caching = true
                        break
                    case "+FootNotes":
                        footnotes = Boolean.parseBoolean(args[++i])
                        break
                    case "-m":
                        format = args[++i]
                        break
                    case "-maxHeaderDepth":
                        maxHeaderDepth = Integer.parseInt(args[++i])
                        break
                    case "-o":
                        outFile = new File(args[++i])
                        log("Creating output file '${outFile}'")
                        if (outFile.parentFile && !outFile.parentFile.exists()) outFile.parentFile.mkdirs()
                        out = new PrintStream(new FileOutputStream(outFile), false, "UTF-8")
                        break
                    case "-oa":
                        outFile = new File(args[++i])
                        log("Appending to output file '${outFile}'")
                        if (outFile.parentFile && !outFile.parentFile.exists()) outFile.parentFile.mkdirs()
                        out = new PrintStream(new FileOutputStream(outFile, true), false, "UTF-8")
                        newLine = false
                        blankLine = false
                        break
                    case "-u":
                        userPassword = args[++i]
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
                    case "-plantuml":
                        runPlantUml = false
                        break
                    case "-a":
                        downloadFolder = new File(args[++i])
                        break
                    case "+H":
                        docHierarchy = Boolean.parseBoolean(args[++i])
                        break
                    case "+T":
                        titleTransformation = Boolean.parseBoolean(args[++i])
                        break
                    case "+RootPageTitle":
                        titleRootPage = Boolean.parseBoolean(args[++i])
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
