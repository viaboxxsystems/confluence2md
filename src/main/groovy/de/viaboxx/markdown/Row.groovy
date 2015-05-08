package de.viaboxx.markdown

/**
 * Description: <br>
 * <p>
 * Date: 16.09.14<br>
 * </p>
 */
class Row {
    List<Cell> cells = []

    Cell getCell() { cells[-1] }

    void renderSeparator(Walker out) {
        cells.each { cell ->
            out.writeRaw("|")
            cell.length().times {
                out.writeRaw('-')
            }
        }
        out.writeRaw("|\n")
    }
}
