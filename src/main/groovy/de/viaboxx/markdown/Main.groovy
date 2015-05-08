package de.viaboxx.markdown

/**
 * Description: Dispatcher class<br>
 * <p>
 * Date: 24.10.14<br>
 * </p>
 */
class Main {
    static void main(String[] args) {
        if (args[0] == "Uml2Confluence") {
            Uml2Confluence.main(reduced(args))
        } else if (args[0] == "Confluence2MD") {
            Confluence2MD.main(reduced(args))
        } else {
            Confluence2MD.main(args)
        }
    }

    private static String[] reduced(String[] args) {
        String[] dest = new String[args.length - 1]
        System.arraycopy(args, 1, dest, 0, dest.length)
        dest
    }
}
