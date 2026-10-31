package com.aiagent.assistant.domain.ai

object AiContext {

    /** দীর্ঘ চ্যাটে শুধু সাম্প্রতিক অংশ পাঠায় (অক্ষরের সীমা), কিন্তু শেষ বার্তা সবসময় থাকে */
    fun trim(turns: List<AiTurn>, maxChars: Int = 24_000): List<AiTurn> {
        var total = 0
        val kept = ArrayDeque<AiTurn>()
        for (turn in turns.asReversed()) {
            val size = turn.text.length + 400 * turn.images.size
            if (kept.isNotEmpty() && total + size > maxChars) break
            kept.addFirst(turn)
            total += size
        }
        return kept.toList()
    }
}
