package com.hl.hybridsearch.analyzer

interface MorphologyAnalyzer {
    fun analyze(text: String): QueryFeatures
}
