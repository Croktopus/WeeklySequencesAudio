package com.chris.wsa.audio

object LwParser {
    // Extracts the post ID from a LessWrong URL
    fun extractPostId(url: String): String? {
        // Matches URLs like:
        // https://www.lesswrong.com/posts/46qnWRSR7L2eyNbMA/the-simple-truth
        // https://www.lesswrong.com/s/5g5TkQTe9rmPS5vvM/p/46qnWRSR7L2eyNbMA
        val regex = Regex(".*/(?:posts|p)/([a-zA-Z0-9]{17})")
        val match = regex.find(url)
        return match?.groupValues?.get(1)
    }
}
