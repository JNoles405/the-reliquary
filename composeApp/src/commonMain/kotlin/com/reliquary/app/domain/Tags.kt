package com.reliquary.app.domain

fun parseTags(raw: String?): List<String> =
    raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.distinct().orEmpty()
