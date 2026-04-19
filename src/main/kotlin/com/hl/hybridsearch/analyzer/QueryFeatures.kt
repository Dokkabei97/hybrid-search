package com.hl.hybridsearch.analyzer

data class QueryFeatures(
    val raw: String,
    val tokens: List<Token>,
) {
    val tokenCount: Int get() = tokens.size
    val nounCount: Int get() = tokens.count { it.pos == Pos.NOUN }
    val verbCount: Int get() = tokens.count { it.pos == Pos.VERB }
    val adjectiveCount: Int get() = tokens.count { it.pos == Pos.ADJECTIVE }
    val josaCount: Int get() = tokens.count { it.pos == Pos.JOSA }
    val eomiCount: Int get() = tokens.count { it.pos == Pos.EOMI }
    val particleCount: Int get() = josaCount + eomiCount

    val nounRatio: Double
        get() = if (tokenCount == 0) 0.0 else nounCount.toDouble() / tokenCount

    data class Token(
        val surface: String,
        val pos: Pos,
    )

    enum class Pos {
        NOUN,
        VERB,
        ADJECTIVE,
        ADVERB,
        JOSA,
        EOMI,
        PUNCTUATION,
        OTHER,
    }
}
