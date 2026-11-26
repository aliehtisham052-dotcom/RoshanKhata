package com.innovation313.roshankhata.data

/**
 * The harness's stand-in for the answer layer's platform door (the real AnswerEnv.kt reads the book through KhataDao, which
 * needs Room). Only its name is needed here: [AnswerCard.spoken] takes one, and the shared engine's tests never call it.
 */
interface AnswerEnv
