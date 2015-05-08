package de.viaboxx.markdown

/**
 * Description: <br>
 * <p>
 * Date: 16.09.14<br>
 * </p>
 */
interface Walker {
    String intoString(Closure process)

    void walkThrough(def parent)

    void writeln()

    void write(String text)

    void writeRaw(String text)
}
