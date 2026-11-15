package com.innovation313.roshankhata.data

/**
 * A reading as the owner's lists and the review sets name it: "Figure:TO_GET", "Help:screen_zakat", "Voice".
 * One spelling for every test that compares readings (lesson 128: no second copy).
 */
object HudhudShown {
    fun of(a: Hudhud.Ask): String = when (a) {
        is Hudhud.Ask.Guessed -> of(a.ask)
        // "As it stands now" (15 Nov 2026) only adds a note to the card; what was understood is the ask inside it.
        is Hudhud.Ask.Now -> of(a.ask)
        is Hudhud.Ask.DidYouMean -> a.options.firstOrNull()?.let { of(it) } ?: "DidYouMean"
        is Hudhud.Ask.Help -> "Help:" + a.key
        is Hudhud.Ask.Figure -> "Figure:" + a.kind
        is Hudhud.Ask.Count -> "Count:" + a.thing + ":" + a.window
        is Hudhud.Ask.Status -> "Status:" + a.of
        is Hudhud.Ask.Person -> "Person:" + a.facet
        is Hudhud.Ask.Goods -> "Goods:" + a.facet
        is Hudhud.Ask.Shop -> "Shop:" + a.facet
        is Hudhud.Ask.About -> "About:" + a.topic
        is Hudhud.Ask.Chat -> "Chat:" + a.talk
        is Hudhud.Ask.NeedName -> "NeedName:" + a.facet
        is Hudhud.Ask.Period -> "Period:" + a.measure
        else -> a.javaClass.simpleName
    }
}
