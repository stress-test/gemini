package com.example.bbsolver

data class Move(val pi: Int, val r: Int, val c: Int, val cells: List<IntArray>, val lines: Int)
class Solution(val moves: List<Move>, val complete: Boolean, val total: Int)

/** Tìm chuỗi nước đi tối ưu cho tối đa 3 mảnh; xét mọi thứ tự đặt và dọn hàng/cột đồng thời. */
object Solver {
    private data class StateKey(val rows: List<Int>, val used: Int)
    private data class MemoVal(val cleared: Int, val heuristic: Double)

    private fun toMasks(cells: List<IntArray>): IntArray {
        if (cells.isEmpty()) return IntArray(0)
        val minR = cells.minOf { it[0] }
        val minC = cells.minOf { it[1] }
        val h = cells.maxOf { it[0] } - minR + 1
        val masks = IntArray(h)
        for (p in cells) {
            val r = p[0] - minR
            val c = p[1] - minC
            if (r in masks.indices && c in 0..7) {
                masks[r] = masks[r] or (1 shl c)
            }
        }
        return masks
    }

    private fun width(mask: IntArray): Int =
        if (mask.isEmpty()) 0 else mask.maxOf { 32 - Integer.numberOfLeadingZeros(it) }

    private fun clearCompleted(input: IntArray): Pair<IntArray, Int> {
        var fullRows = 0
        var fullCols = 255
        for (r in 0 until 8) {
            val rowMask = input[r] and 255
            if (rowMask == 255) fullRows++
            fullCols = fullCols and rowMask
        }
        val colCount = Integer.bitCount(fullCols)
        val out = IntArray(8) { r ->
            val rowMask = input[r] and 255
            if (rowMask == 255) 0
            else rowMask and fullCols.inv() and 255
        }
        return out to (fullRows + colCount)
    }

    private fun canPlace(rows: IntArray, mask: IntArray, w: Int, r: Int, c: Int): Boolean {
        if (mask.isEmpty() || w <= 0 || r < 0 || r + mask.size > 8 || c < 0 || c + w > 8) return false
        for (i in mask.indices) {
            if ((rows[r + i] and (mask[i] shl c)) != 0) return false
        }
        return true
    }

    /** Đánh giá độ thoáng và tiềm năng dọn bàn của bàn cờ hiện tại. */
    private fun boardHeuristic(rows: IntArray): Double {
        var empty = 0
        var holes = 0
        var roughness = 0
        var nearCompleteBonus = 0.0

        fun occupied(r: Int, c: Int): Int =
            if (r !in 0..7 || c !in 0..7) 1 else (rows[r] ushr c) and 1

        for (r in 0 until 8) {
            val cnt = Integer.bitCount(rows[r] and 255)
            if (cnt == 7) nearCompleteBonus += 14.0
            else if (cnt == 6) nearCompleteBonus += 5.0
        }

        for (c in 0 until 8) {
            var colFilled = 0
            for (r in 0 until 8) colFilled += occupied(r, c)
            if (colFilled == 7) nearCompleteBonus += 14.0
            else if (colFilled == 6) nearCompleteBonus += 5.0
        }

        for (r in 0 until 8) {
            for (c in 0 until 8) {
                if (occupied(r, c) == 0) {
                    empty++
                    val blocked = occupied(r - 1, c) + occupied(r + 1, c) +
                        occupied(r, c - 1) + occupied(r, c + 1)
                    if (blocked == 4) holes++
                }
                if (c < 7 && occupied(r, c) != occupied(r, c + 1)) roughness++
                if (r < 7 && occupied(r, c) != occupied(r + 1, c)) roughness++
            }
        }

        return empty * 11.0 + nearCompleteBonus - holes * 24.0 - roughness * 0.45
    }

    private fun movesScore(movesCount: Int, stepClearBonus: Double, cleared: Int, hNow: Double): Double =
        movesCount * 1_000_000.0 + stepClearBonus + cleared * 40.0 + hNow

    fun solve(board: IntArray, pieces: List<List<IntArray>>): Solution? {
        if (board.size != 8 || pieces.size != 3) return null
        val start = IntArray(8) { board[it] and 255 }
        val masks = pieces.map(::toMasks)
        val widths = masks.map(::width)
        val active = pieces.indices.filter { masks[it].isNotEmpty() && widths[it] in 1..8 && masks[it].size <= 8 }
        if (active.isEmpty()) return null

        var bestMoves: List<Move> = emptyList()
        var bestScore = Double.NEGATIVE_INFINITY
        val memo = HashMap<StateKey, MemoVal>()

        fun visit(
            rows: IntArray,
            used: Int,
            path: ArrayList<Move>,
            cleared: Int,
            stepClearBonus: Double
        ) {
            val hNow = boardHeuristic(rows)
            val currentScore = movesScore(path.size, stepClearBonus, cleared, hNow)
            if (currentScore > bestScore) {
                bestScore = currentScore
                bestMoves = ArrayList(path)
            }
            if (path.size == active.size) return

            val key = StateKey(rows.toList(), used)
            val prev = memo[key]
            if (prev != null) {
                val dominated = prev.cleared > cleared || (prev.cleared == cleared && prev.heuristic >= hNow + 0.01)
                if (dominated) return
            }
            memo[key] = MemoVal(cleared, hNow)

            for (pi in active) {
                if ((used and (1 shl pi)) != 0) continue
                val shape = masks[pi]
                val w = widths[pi]
                for (r in 0..(8 - shape.size)) {
                    for (c in 0..(8 - w)) {
                        if (!canPlace(rows, shape, w, r, c)) continue
                        val placed = rows.copyOf()
                        for (i in shape.indices) {
                            placed[r + i] = placed[r + i] or (shape[i] shl c)
                        }
                        val (next, removed) = clearCompleted(placed)
                        val bonus = if (removed > 0) removed * removed * 180.0 else 0.0
                        path.add(Move(pi, r, c, pieces[pi], removed))
                        visit(next, used or (1 shl pi), path, cleared + removed, stepClearBonus + bonus)
                        path.removeAt(path.lastIndex)
                    }
                }
            }
        }

        visit(start, 0, ArrayList(3), 0, 0.0)
        return Solution(bestMoves, bestMoves.size == active.size, active.size)
    }
}