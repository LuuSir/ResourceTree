package com.example.resouretree.domain.model

/** Web actions accept a single web address, never executable or local-file schemes. */
fun webUrl(text: String): String? = runCatching {
    val value = text.trim()
    val uri = java.net.URI(value)
    require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
    require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null)
    require(uri.port == -1 || uri.port in 1..65535)
    value
}.getOrNull()
